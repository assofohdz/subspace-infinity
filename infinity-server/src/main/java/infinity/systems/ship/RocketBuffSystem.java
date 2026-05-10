// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems.ship;

import com.simsilica.es.Entity;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.sim.SimTime;
import infinity.es.Parent;
import infinity.es.ship.Speed;
import infinity.es.ship.Thrust;
import infinity.es.ship.actions.RocketActive;
import infinity.es.ship.actions.RocketBuff;
import infinity.es.ship.actions.RocketSnapshot;
import infinity.systems.BaseInfinitySystem;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Owns the lifecycle hooks for rocket-buff entities. The buff entity
 * itself is created by {@code ConsumableSystem.actOut} when a player
 * fires a rocket; this system reacts to its appearance + disappearance
 * to keep the parent ship's {@link Thrust} / {@link Speed} swapped to
 * the rocket-active values, plus maintains the {@link RocketActive}
 * marker as a denormalized "ship is currently rocketing" cache.
 *
 * <p>Pattern:
 *
 * <ul>
 *   <li><b>Buff entity created</b> ({@code [RocketBuff, Parent,
 *       RocketSnapshot]} — the snapshot carries the ship's pre-buff
 *       Thrust + Speed). This system's added-handler swaps the ship's
 *       components to the rocket-active values supplied on the buff
 *       entity's {@link RocketSnapshot} (which also doubles as the
 *       carrier of the post-swap target — see ConsumableSystem) and
 *       installs {@link RocketActive}.
 *   <li><b>Buff entity expires</b> via {@link com.simsilica.es.common.Decay}
 *       (the canonical reaper deletes it at deadline). The system
 *       cached the {@code (shipId, originalThrust, originalSpeed)} at
 *       buff-creation time, keyed by the buff entity id; on remove we
 *       look it up, restore the ship's components, then strip
 *       {@link RocketActive}. (Zay-ES {@code getRemovedEntities()}
 *       returns the entity id but does not preserve component values
 *       on already-deleted entities, so we cache rather than reading
 *       the snapshot off the removed entity.)
 * </ul>
 *
 * <p>Lifecycle owner is the buff entity, not this system. If something
 * else removes the buff entity (admin command, ship despawn, etc.)
 * the same revert path runs — there is exactly one removal seam.
 */
public final class RocketBuffSystem extends BaseInfinitySystem {

  private static final Logger log = LoggerFactory.getLogger(RocketBuffSystem.class);

  private EntityData ed;
  private EntitySet buffs;
  /** {@code buffEntityId → (shipId, originalThrust, originalSpeed)}. */
  private final Map<EntityId, ActiveBuff> activeByBuff = new HashMap<>();

  @Override
  protected void initialize() {
    ed = requireSystem(EntityData.class);
    buffs = ed.getEntities(RocketBuff.class, Parent.class, RocketSnapshot.class);
  }

  @Override
  protected void terminate() {
    if (buffs != null) {
      buffs.release();
      buffs = null;
    }
    activeByBuff.clear();
  }

  @Override
  public void update(final SimTime time) {
    buffs.applyChanges();
    for (final Entity added : buffs.getAddedEntities()) {
      onBuffAdded(added);
    }
    for (final Entity removed : buffs.getRemovedEntities()) {
      onBuffRemoved(removed.getId());
    }
  }

  private void onBuffAdded(final Entity buff) {
    final Parent parent = buff.get(Parent.class);
    final RocketSnapshot snapshot = buff.get(RocketSnapshot.class);
    if (parent == null || snapshot == null) {
      return;
    }
    final EntityId shipId = parent.getParentEntityId();

    // Cache the revert data — Zay-ES does not preserve component values on
    // entities returned by getRemovedEntities, so we can't read the snapshot
    // off the removed buff entity. Stash it here at add-time instead.
    activeByBuff.put(
        buff.getId(),
        new ActiveBuff(shipId, snapshot.getOriginalThrust(), snapshot.getOriginalSpeed()));

    // ConsumableSystem already swapped the ship's Thrust + Speed to the
    // rocket-active values before creating this buff entity. This hook just
    // stamps RocketActive as a denormalized "ship is currently rocketing"
    // marker for hot-path consumers (HUD, AI, etc.).
    ed.setComponent(shipId, new RocketActive());
    if (log.isInfoEnabled()) {
      log.info(
          "Rocket buff active on ship {} (snapshot pre-buff thrust={}, speed={})",
          shipId,
          snapshot.getOriginalThrust(),
          snapshot.getOriginalSpeed());
    }
  }

  private void onBuffRemoved(final EntityId buffId) {
    final ActiveBuff active = activeByBuff.remove(buffId);
    if (active == null) {
      return;
    }
    ed.setComponent(active.shipId, new Thrust(active.originalThrust));
    ed.setComponent(active.shipId, new Speed(active.originalSpeed));
    ed.removeComponent(active.shipId, RocketActive.class);
    log.info(
        "Rocket buff expired on ship {} — reverted thrust={}, speed={}",
        active.shipId,
        active.originalThrust,
        active.originalSpeed);
  }

  private record ActiveBuff(EntityId shipId, int originalThrust, int originalSpeed) {}
}
