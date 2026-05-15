// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems;

import com.simsilica.es.Entity;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.mathd.Vec3d;
import com.simsilica.mblock.phys.MBlockShape;
import com.simsilica.mphys.PhysicsSpace;
import com.simsilica.sim.SimTime;
import infinity.es.ChangeTarget;
import infinity.es.PrizeSpawnIntent;
import infinity.es.arena.ArenaId;
import infinity.settings.ConfigRegistrySystem;
import infinity.settings.EngineConfigSystem;
import infinity.sim.MapFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Channel A drain for death-drop prizes — reads {@link PrizeSpawnIntent} + {@link ChangeTarget}
 * one-shot holder entities, spawns a single arena-weighted prize at the carried position. See
 * ADR 0001 and ADR 0003.
 *
 * <p>Per ADR 0007, lifetime flows through {@link com.simsilica.es.common.Decay} on the spawned
 * prize entity; the {@code deathPrizeTimeMs} arena knob is read here (Subspace canon — distinct
 * from {@code PrizeMinExist..PrizeMaxExist} used by default spawners).
 */
public class DeathPrizeSystem extends BaseInfinitySystem {

  static Logger log = LoggerFactory.getLogger(DeathPrizeSystem.class);

  private final PhysicsSpace<EntityId, MBlockShape> phys;

  private EntityData ed;
  private ConfigRegistrySystem configRegistry;
  private EngineConfigSystem engineConfigSystem;
  private PrizeSpawnerSystem prizeSpawnerSystem;
  private EntitySet intents;

  public DeathPrizeSystem(final PhysicsSpace<EntityId, MBlockShape> phys) {
    this.phys = phys;
  }

  @Override
  protected void initialize() {
    ed = requireSystem(EntityData.class);
    configRegistry = requireSystem(ConfigRegistrySystem.class);
    engineConfigSystem = requireSystem(EngineConfigSystem.class);
    prizeSpawnerSystem = requireSystem(PrizeSpawnerSystem.class);
    intents = ed.getEntities(PrizeSpawnIntent.class, ChangeTarget.class);
  }

  @Override
  protected void terminate() {
    intents.release();
    intents = null;
  }

  @Override
  public void start() {
    // no-op
  }

  @Override
  public void stop() {
    // no-op
  }

  @Override
  public void update(final SimTime time) {
    intents.applyChanges();
    for (final Entity added : intents.getAddedEntities()) {
      drainOne(added);
      ed.removeEntity(added.getId());
    }
  }

  /**
   * Resolves arena weights via {@link PrizeSpawnerSystem#pickPrizeTypeForArena} so the death-drop
   * shares the same per-arena selector cache + negative-roll logic as the cadence spawner.
   * No-op when the arena's {@code deathPrizeTimeMs} is zero (death-drops disabled).
   */
  private void drainOne(final Entity intentEntity) {
    final ChangeTarget ct = intentEntity.get(ChangeTarget.class);
    final PrizeSpawnIntent intent = intentEntity.get(PrizeSpawnIntent.class);
    final EntityId dyingShipId = ct.target();
    // Kill-credit: ct.source() == dyingShipId for unattributed deaths, else the killer EntityId.
    final EntityId killerId = ct.source();
    final Vec3d position = intent.position();
    if (position == null) {
      log.warn("PrizeSpawnIntent for {} has null position; skipping drop", dyingShipId);
      return;
    }
    final ArenaId arenaId = ed.getComponent(dyingShipId, ArenaId.class);
    final infinity.config.PrizeConfig prize =
        arenaId == null
            ? infinity.config.PrizeConfig.DEFAULTS
            : configRegistry.forArena(arenaId).prize();
    final long deathPrizeTimeMs = prize.deathPrizeTimeMs();
    if (deathPrizeTimeMs <= 0L) {
      return;
    }
    final String arenaName = arenaId == null ? null : arenaId.getArena();
    final String prizeType = prizeSpawnerSystem.pickPrizeTypeForArena(arenaName, prize);
    MapFactory.createPrize(
        ed,
        new infinity.sim.specs.PrizeArgs(
            phys,
            intent.timeNs(),
            position,
            prizeType,
            deathPrizeTimeMs,
            false,
            engineConfigSystem.get().prizeRadius()));
    if (log.isInfoEnabled()) {
      log.info(
          "Death-drop: ship {} died (killer={}) in arena '{}' at {} → spawned {} (lifetime={} ms)",
          dyingShipId,
          killerId,
          arenaName,
          position,
          prizeType,
          deathPrizeTimeMs);
    }
  }
}
