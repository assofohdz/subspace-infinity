// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.mblock.phys.MBlockShape;
import com.simsilica.mphys.AbstractBody;
import com.simsilica.mphys.Contact;
import com.simsilica.mphys.ContactListener;
import com.simsilica.mphys.RigidBody;
import com.simsilica.sim.AbstractGameSystem;
import com.simsilica.sim.SimTime;
import infinity.es.Sensor;
import infinity.es.arena.ArenaId;
import infinity.es.ship.ShipType;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tracks per-ship arena membership and fires enter / leave events derived from MOSS
 * sensor contacts (the ghost-sphere bodies {@link ArenaSystem} attaches to each loaded
 * arena, marked with {@link Sensor}). Listens to {@link ContactSystem} via the standard
 * {@link ContactListener} fan-out; the contact-disable in {@link ContactSystem#newContact}
 * runs first so the resolver never sees these contacts (ship motion is unaffected).
 *
 * <p>MOSS only fires {@code newContact} (no end-contact callback). Exit detection is a
 * silence-window: each {@code (ship, arena)} pair stores its last observed frame, and
 * the per-tick {@link #update} sweep declares a leave once the frame gap exceeds
 * {@link #EXIT_GRACE_FRAMES}. This handles Paul's "fires inside but maybe not
 * consistently" caveat — a few skipped frames don't fire spurious leaves.
 *
 * <p>Multi-arena design notes:
 * <ul>
 *   <li>Ships are allowed to roam in no-arena void — leaving an arena clears the ship's
 *       {@link ArenaId} component but doesn't kill / bounce / warp.
 *   <li>{@link ArenaId} is rewritten on entry / removed on leave. {@code ShipSpawnSystem}
 *       watches a {@code (ShipType, ArenaId)} EntitySet and re-projects the per-arena
 *       {@code ShipConfig} on the resulting add / change events — no direct call from
 *       this system into {@code ShipSpawnSystem}.
 * </ul>
 *
 * <p><b>Rejected alternatives</b> (don't re-investigate without new evidence):
 * <ul>
 *   <li><i>Polling fallback</i> — periodic point-in-bounds checks for every ship. Verbose
 *       and adds latency on the leave side; sensor contacts already fire reliably as long
 *       as the cube is in a coarse static-only bin index (see {@code LargeObject} marker
 *       on the arena entity). Tried during early Pattern 4 #14 work; abandoned.
 *   <li><i>Per-ship {@code ControlDriver}</i> — push membership updates from the driver
 *       loop. Same downside as polling plus tighter coupling to physics internals; the
 *       contact-driven path keeps membership a pure ECS observer.
 * </ul>
 *
 * @author Asser Fahrenholz
 */
public class ArenaMembershipSystem extends AbstractGameSystem
    implements ContactListener<EntityId, MBlockShape> {

  private static final Logger log = LoggerFactory.getLogger(ArenaMembershipSystem.class);

  /**
   * Frames of contact-silence before declaring a ship has left an arena. ~1 second at
   * the standard 60 Hz sim tick. Tune up if MOSS turns out to skip many consecutive
   * frames for at-rest interpenetrating bodies; tune down if exit latency feels sluggish.
   */
  private static final long EXIT_GRACE_FRAMES = 60;

  private EntityData ed;

  /** Ship → arena entity id of the arena the ship is currently considered "in". */
  private final Map<EntityId, EntityId> currentArena = new HashMap<>();

  /**
   * Ship → (arena entity id → last sim frame the contact was observed). Used by the
   * per-tick exit sweep — contacts older than {@link #EXIT_GRACE_FRAMES} fire a leave.
   */
  private final Map<EntityId, Map<EntityId, Long>> lastSeenFrame = new HashMap<>();

  private long currentFrame;

  @Override
  protected void initialize() {
    ed = getSystem(EntityData.class);
    getSystem(ContactSystem.class).addListener(this);
  }

  @Override
  protected void terminate() {
    getSystem(ContactSystem.class).removeListener(this);
    currentArena.clear();
    lastSeenFrame.clear();
  }

  @Override
  public void update(final SimTime time) {
    currentFrame = time.getFrame();

    // Sweep stale (ship, arena) pairs and fire leave events. Iterating a copy keeps the
    // map mutation in this loop simple.
    final Iterator<Map.Entry<EntityId, Map<EntityId, Long>>> shipIt =
        lastSeenFrame.entrySet().iterator();
    while (shipIt.hasNext()) {
      final Map.Entry<EntityId, Map<EntityId, Long>> shipEntry = shipIt.next();
      final EntityId shipId = shipEntry.getKey();
      final Map<EntityId, Long> arenaToFrame = shipEntry.getValue();

      final Iterator<Map.Entry<EntityId, Long>> arenaIt = arenaToFrame.entrySet().iterator();
      while (arenaIt.hasNext()) {
        final Map.Entry<EntityId, Long> arenaEntry = arenaIt.next();
        if (currentFrame - arenaEntry.getValue() > EXIT_GRACE_FRAMES) {
          arenaIt.remove();
          fireLeftArena(shipId, arenaEntry.getKey());
        }
      }
      if (arenaToFrame.isEmpty()) {
        shipIt.remove();
      }
    }
  }

  @Override
  public void newContact(final Contact<EntityId, MBlockShape> contact) {
    final RigidBody<EntityId, MBlockShape> bodyOne = contact.body1;
    final AbstractBody<EntityId, MBlockShape> bodyTwo = contact.body2;
    if (bodyTwo == null) {
      return;
    }

    final EntityId one = bodyOne.id;
    final EntityId two = bodyTwo.id;

    // We only care about sensor contacts where the sensor is an arena and the other body
    // is a ship. Other sensor types (future safe-zones / gravity wells) and non-sensor
    // body-vs-body contacts aren't our concern.
    final EntityId arenaEntityId;
    final EntityId shipId;
    if (isArenaSensor(one) && hasShipType(two)) {
      arenaEntityId = one;
      shipId = two;
    } else if (isArenaSensor(two) && hasShipType(one)) {
      arenaEntityId = two;
      shipId = one;
    } else {
      return;
    }

    applyMembership(shipId, arenaEntityId);
  }

  /**
   * Warp-driven membership update: mirrors what {@link #newContact} would do if a
   * contact had fired between {@code shipId} and the arena sensor at
   * {@code arenaEntityId}. Used by {@link WarpSystem} so a teleport that drops a ship
   * into an arena keeps {@link #currentArena} / {@link #lastSeenFrame} aligned without
   * waiting for the body to wake up — the warp zeros velocity, the body sleeps, and
   * contact-gen stops firing for it until movement resumes. Without this, the per-tick
   * exit-grace sweep fires a redundant "left arena" log a second after every spawn-warp.
   */
  public void markEntered(final EntityId shipId, final EntityId arenaEntityId) {
    applyMembership(shipId, arenaEntityId);
  }

  /**
   * Warp-driven counterpart to {@link #markEntered} — the ship was teleported into
   * no-arena void, so any current memberships should fire a leave immediately rather
   * than waiting out the exit-grace window. Does nothing if the ship has no tracked
   * memberships.
   */
  public void markLeft(final EntityId shipId) {
    final EntityId previousArena = currentArena.remove(shipId);
    final Map<EntityId, Long> arenaToFrame = lastSeenFrame.remove(shipId);
    if (previousArena != null) {
      fireLeftArena(shipId, previousArena);
    }
    if (arenaToFrame != null) {
      for (final EntityId staleArena : arenaToFrame.keySet()) {
        if (!staleArena.equals(previousArena)) {
          fireLeftArena(shipId, staleArena);
        }
      }
    }
  }

  private void applyMembership(final EntityId shipId, final EntityId arenaEntityId) {
    // Touch the last-seen tick so the exit sweep keeps this membership alive.
    lastSeenFrame
        .computeIfAbsent(shipId, k -> new HashMap<>())
        .put(arenaEntityId, currentFrame);

    // Detect membership change and fire enter (after firing leave for the prior arena).
    final EntityId previous = currentArena.get(shipId);
    if (!arenaEntityId.equals(previous)) {
      if (previous != null) {
        // Drop the abandoned arena's lastSeenFrame entry so the per-tick exit
        // sweep doesn't fire a redundant leave for it 60 frames later. Without
        // this, every cross between arenas produces a duplicate "left arena"
        // log + a stale fireLeftArena that would clobber the freshly-set
        // ArenaId (see fireLeftArena's conditional guard for the second half
        // of the safety net).
        final Map<EntityId, Long> arenaToFrame = lastSeenFrame.get(shipId);
        if (arenaToFrame != null) {
          arenaToFrame.remove(previous);
        }
        fireLeftArena(shipId, previous);
      }
      currentArena.put(shipId, arenaEntityId);
      fireEnteredArena(shipId, arenaEntityId);
    }
  }

  /** True if the entity has both {@link Sensor} and {@link ArenaId} (i.e. an arena). */
  private boolean isArenaSensor(final EntityId entityId) {
    return ed.getComponent(entityId, Sensor.class) != null
        && ed.getComponent(entityId, ArenaId.class) != null;
  }

  /** True if the entity carries a {@link ShipType} component. */
  private boolean hasShipType(final EntityId entityId) {
    return ed.getComponent(entityId, ShipType.class) != null;
  }

  private void fireEnteredArena(final EntityId shipId, final EntityId arenaEntityId) {
    final ArenaId arenaIdComp = ed.getComponent(arenaEntityId, ArenaId.class);
    if (arenaIdComp == null) {
      // Sensor lost ArenaId between contact and event — should be rare; safe-skip.
      return;
    }
    // Write the ship's ArenaId. ShipSpawnSystem watches (ShipType, ArenaId) and will
    // re-project the ship's ShipConfig from the resulting add/change event — no direct
    // call needed from here.
    ed.setComponent(shipId, arenaIdComp);
    log.info(
        "Ship {} entered arena {} (entity {}, frame {})",
        shipId, arenaIdComp.getArena(), arenaEntityId, currentFrame);
  }

  private void fireLeftArena(final EntityId shipId, final EntityId arenaEntityId) {
    currentArena.remove(shipId, arenaEntityId);
    // Read the leaving arena's name; fall back to entity id if the ArenaId
    // component is gone (rare race during arena teardown).
    final ArenaId leavingArenaId = ed.getComponent(arenaEntityId, ArenaId.class);
    // Only clear the ship's ArenaId if it still names the arena we're leaving.
    // Stale exit-grace sweeps for an abandoned arena fire AFTER the ship has
    // already moved into a new arena and had its ArenaId rewritten; clearing
    // unconditionally there would clobber the new value and break stat
    // reprojection (see the lastSeenFrame purge in newContact for the first
    // half of the safety net).
    final ArenaId currentShipArenaId = ed.getComponent(shipId, ArenaId.class);
    final boolean shouldClear = leavingArenaId != null
        && currentShipArenaId != null
        && leavingArenaId.getArena().equals(currentShipArenaId.getArena());
    if (shouldClear) {
      ed.removeComponent(shipId, ArenaId.class);
    }
    final String arenaName = leavingArenaId != null ? leavingArenaId.getArena() : "<unknown>";
    log.info(
        "Ship {} left arena {} (entity {}, frame {})",
        shipId, arenaName, arenaEntityId, currentFrame);
  }
}
