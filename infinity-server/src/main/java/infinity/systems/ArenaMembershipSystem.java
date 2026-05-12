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

/** Tracks per-ship arena membership via MOSS sensor contacts; writes {@link ArenaId} on entry, removes on leave (silence-window exit). */
public class ArenaMembershipSystem extends AbstractGameSystem
    implements ContactListener<EntityId, MBlockShape> {

  private static final Logger log = LoggerFactory.getLogger(ArenaMembershipSystem.class);

  // ~1 s at 60 Hz — contact-silence window before declaring a leave.
  private static final long EXIT_GRACE_FRAMES = 60;

  private EntityData ed;

  private final Map<EntityId, EntityId> currentArena = new HashMap<>();

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

  /** Warp-driven enter; bypasses the contact path (warp sleeps the body, stopping contact-gen). */
  public void markEntered(final EntityId shipId, final EntityId arenaEntityId) {
    applyMembership(shipId, arenaEntityId);
  }

  /** Warp-driven leave; fires immediately instead of waiting out the exit-grace window. */
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

  private boolean isArenaSensor(final EntityId entityId) {
    return ed.getComponent(entityId, Sensor.class) != null
        && ed.getComponent(entityId, ArenaId.class) != null;
  }

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
    if (log.isInfoEnabled()) {
      log.info(
          "Ship {} entered arena {} (entity {}, frame {})",
          shipId, arenaIdComp.getArena(), arenaEntityId, currentFrame);
    }
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
