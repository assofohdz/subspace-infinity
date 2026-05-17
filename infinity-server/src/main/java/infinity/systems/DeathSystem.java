// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.systems;

import com.simsilica.bpos.BodyPosition;
import com.simsilica.es.Entity;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.es.common.Decay;
import com.simsilica.event.EventBus;
import com.simsilica.mathd.Vec3d;
import com.simsilica.sim.SimTime;
import infinity.es.ChangeTarget;
import infinity.es.Dead;
import infinity.es.KilledBy;
import infinity.es.PrizeSpawnIntent;
import infinity.es.ship.weapons.WeaponType;
import infinity.events.arena.PlayerKilledEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Canonical writer for the death cycle's side effects per ADR-0001:
 * <ul>
 *   <li>Stamps {@link Decay}{@code (now, now)} so the SiO2 reaper despawns the entity next tick.</li>
 *   <li>Publishes {@link PlayerKilledEvent} on the global {@link EventBus} for non-ECS consumers
 *       (scoring modules, broadcast HUD).</li>
 *   <li>Emits a {@link PrizeSpawnIntent} holder with {@link ChangeTarget} attribution when the
 *       dying entity has a known {@link BodyPosition} (skips silently if not — mobs spawned mid-
 *       physics-frame may die before the position is stamped).</li>
 *   <li>Removes the transient {@link KilledBy} attribution sibling once consumed.</li>
 * </ul>
 *
 * <p>Runs before the SiO2 {@code DecaySystem} so the Decay it stamps is reaped in the same
 * tick. {@code EnergySystem} owns the Dead-stamp (death edge); this system owns the side-
 * effect channels (consolidates what used to be split between EnergySystem.handleDeath and
 * elsewhere).
 */
public class DeathSystem extends BaseInfinitySystem {

  private static final Logger log = LoggerFactory.getLogger(DeathSystem.class);

  private EntityData ed;
  private EntitySet dead;

  @Override
  protected void initialize() {
    ed = requireSystem(EntityData.class);
    dead = ed.getEntities(Dead.class);
  }

  @Override
  protected void terminate() {
    dead.release();
    dead = null;
  }

  @Override
  public void update(final SimTime tpf) {
    if (!dead.applyChanges()) {
      return;
    }
    final long now = tpf.getTime();
    for (final Entity e : dead.getAddedEntities()) {
      final EntityId victim = e.getId();
      publishKillEvent(victim);
      maybeEmitPrizeIntent(victim, now);
      ed.removeComponent(victim, KilledBy.class);
      ed.setComponent(victim, new Decay(now, now));
    }
  }

  /** Reads attribution from {@link KilledBy} (or null if unattributed) and broadcasts. */
  private void publishKillEvent(final EntityId victim) {
    final KilledBy attribution = ed.getComponent(victim, KilledBy.class);
    final EntityId killer = attribution == null ? null : attribution.killer();
    final byte weaponFlag = attribution == null ? WeaponType.NONE : attribution.weaponFlag();
    EventBus.publish(
        PlayerKilledEvent.playerKilled, new PlayerKilledEvent(victim, killer, weaponFlag));
  }

  /**
   * Drops a prize at the victim's last-known body position. Self-credit when the death was
   * unattributed (regen / env) — {@code ChangeTarget.source = victim}.
   */
  private void maybeEmitPrizeIntent(final EntityId victim, final long nowSimNanos) {
    final BodyPosition bp = ed.getComponent(victim, BodyPosition.class);
    if (bp == null) {
      if (log.isDebugEnabled()) {
        log.debug("No BodyPosition for {} at death; skipping prize-drop intent", victim);
      }
      return;
    }
    final Vec3d deathPosition = bp.getLastLocation();
    final KilledBy attribution = ed.getComponent(victim, KilledBy.class);
    final EntityId source =
        attribution == null || attribution.killer() == null ? victim : attribution.killer();
    final EntityId holder = ed.createEntity();
    ed.setComponents(
        holder,
        new ChangeTarget(victim, source),
        new PrizeSpawnIntent(deathPosition, nowSimNanos));
  }

  @Override
  public void start() {
    // no-op: lifecycle hook unused; EntitySet wiring happens in initialize()
  }

  @Override
  public void stop() {
    // no-op: lifecycle hook unused; cleanup happens in terminate()
  }
}
