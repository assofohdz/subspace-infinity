// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems.ship;

import com.simsilica.es.Entity;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.sim.SimTime;
import infinity.es.Parent;
import infinity.es.ship.actions.RocketActive;
import infinity.es.ship.actions.RocketBuff;
import infinity.es.ship.actions.RocketBuffIntent;
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
 * to maintain the {@link RocketActive} marker on the parent ship and
 * emit revert intents when the buff expires.
 *
 * <p>Pattern:
 *
 * <ul>
 *   <li><b>Buff entity created</b> ({@code [RocketBuff, Parent,
 *       RocketSnapshot]} — the snapshot carries the ship's pre-buff
 *       Thrust + Speed). This system's added-handler installs
 *       {@link RocketActive}; {@code Thrust} / {@code Speed} writes are
 *       owned by {@code ShipSpawnSystem} via the {@link RocketBuffIntent}
 *       activate intent already emitted by {@code ConsumableSystem}.
 *   <li><b>Buff entity expires</b> via {@link com.simsilica.es.common.Decay}
 *       (the canonical reaper deletes it at deadline). The system
 *       cached the {@code (shipId, originalThrust, originalSpeed)} at
 *       buff-creation time, keyed by the buff entity id; on remove we
 *       look it up, emit a revert {@link RocketBuffIntent} (drained by
 *       {@code ShipSpawnSystem}), then strip {@link RocketActive}.
 *       (Zay-ES {@code getRemovedEntities()} returns the entity id but
 *       does not preserve component values on already-deleted entities,
 *       so we cache rather than reading the snapshot off the removed
 *       entity.)
 * </ul>
 *
 * <p>Lifecycle owner is the buff entity, not this system. If something
 * else removes the buff entity (admin command, ship despawn, etc.)
 * the same revert path runs — there is exactly one removal seam.
 *
 * <p>Replacement-as-Mutation (BACKLOG C1): direct {@code Thrust} /
 * {@code Speed} writes were removed in favour of {@link RocketBuffIntent}
 * emission, funnelling both the activate (ConsumableSystem) and revert
 * (this system) writes through {@code ShipSpawnSystem}'s canonical
 * drain. See {@code .claude/rules/replacement-as-mutation.md}.
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

    // ConsumableSystem emitted the activate RocketBuffIntent before
    // creating this buff entity; ShipSpawnSystem's drain writes the
    // ship's Thrust + Speed (same tick if ConsumableSystem ran earlier
    // in the registration order, next tick otherwise). This hook just
    // stamps RocketActive as a denormalized "ship is currently
    // rocketing" marker for hot-path consumers (HUD, AI, etc.).
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
    // Emit a revert RocketBuffIntent — ShipSpawnSystem drains and writes
    // Thrust/Speed (same tick if registered later in the GameSystemManager
    // order, next tick otherwise). RocketActive is owned by this system
    // and removed directly.
    final EntityId intent = ed.createEntity();
    ed.setComponent(
        intent, new RocketBuffIntent(active.shipId, active.originalThrust, active.originalSpeed));
    ed.removeComponent(active.shipId, RocketActive.class);
    log.info(
        "Rocket buff expired on ship {} — revert intent emitted thrust={}, speed={}",
        active.shipId,
        active.originalThrust,
        active.originalSpeed);
  }

  private record ActiveBuff(EntityId shipId, int originalThrust, int originalSpeed) {}
}
