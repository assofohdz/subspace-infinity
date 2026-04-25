/*
 * Copyright (c) 2018-2026, Asser Fahrenholz
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package infinity.settings;

import com.simsilica.es.Entity;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.sim.AbstractGameSystem;
import com.simsilica.sim.SimTime;
import infinity.Ship;
import infinity.config.ShipConfig;
import infinity.config.ShipStat;
import infinity.es.arena.ArenaId;
import infinity.es.ship.BounceRestitution;
import infinity.es.ship.DragFactor;
import infinity.es.ship.Energy;
import infinity.es.ship.EnergyMax;
import infinity.es.ship.EnergyUpgrade;
import infinity.es.ship.Health;
import infinity.es.ship.Recharge;
import infinity.es.ship.RechargeMax;
import infinity.es.ship.RechargeUpgrade;
import infinity.es.ship.Rotation;
import infinity.es.ship.RotationMax;
import infinity.es.ship.RotationUpgrade;
import infinity.es.ship.ShipType;
import infinity.es.ship.Speed;
import infinity.es.ship.SpeedMax;
import infinity.es.ship.SpeedUpgrade;
import infinity.es.ship.Thrust;
import infinity.es.ship.ThrustMax;
import infinity.es.ship.ThrustUpgrade;
import infinity.es.ship.TurnResponsiveness;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Projects {@link ShipConfig} templates from {@link ConfigRegistrySystem}
 * onto ship entities at spawn time, writing the matching stat components
 * (current / max / upgrade triples for thrust, speed, rotation, recharge,
 * energy).
 *
 * <p>The spawn trigger is the appearance of a {@link ShipType} component on
 * an entity. Each newly-observed ship is looked up in the per-arena config
 * snapshot; if a template is found, its values are projected to per-entity
 * components. Missing template or missing arena — components stay at
 * whatever defaults earlier code (if any) supplied.
 *
 * <p><b>Arena resolution (Option R2, temporary):</b> ships do not yet carry
 * their own {@link ArenaId} component (see TODO in
 * {@code GameEntities.createShip}). This system resolves the arena
 * ambiently: if exactly one arena is loaded, use its config; if zero or more
 * than one, log a warning and skip stat projection for the spawn. Multi-arena
 * correctness depends on giving each ship its own {@code ArenaId} and
 * reading from the ship's arena directly.
 */
public class ShipSpawnSystem extends AbstractGameSystem {

  private static final Logger log = LoggerFactory.getLogger(ShipSpawnSystem.class);

  /**
   * Subspace convention: {@code MaximumRotation=400} means one full rotation
   * per second. Convert the raw INI/Groovy integer to radians per second.
   * Verified against SubspaceServer's {@code ClientSettingsConfig.cs} line 388
   * ("400 = full rotation in 1 second").
   */
  private static final double ROTATION_UNITS_TO_RAD_SEC = (2.0 * Math.PI) / 400.0;

  /**
   * Subspace convention: {@code MaximumRecharge} is "amount of energy recharge
   * in 10 seconds" (per SubspaceServer {@code TeamVersusStats.cs:4398}), or
   * equivalently {@code energy/tick = MaximumRecharge / 1000} at the classic
   * 100 Hz tick. Convert to energy/second by dividing by 10.
   */
  private static final double RECHARGE_UNITS_TO_PER_SEC = 1.0 / 10.0;

  private EntityData ed;
  private ConfigRegistrySystem configRegistry;

  private EntitySet arenas;
  private EntitySet ships;

  @Override
  protected void initialize() {
    ed = getSystem(EntityData.class);
    configRegistry = getSystem(ConfigRegistrySystem.class);

    arenas = ed.getEntities(ArenaId.class);
    ships = ed.getEntities(ShipType.class);
  }

  @Override
  protected void terminate() {
    arenas.release();
    arenas = null;
    ships.release();
    ships = null;
  }

  @Override
  public void update(final SimTime time) {
    arenas.applyChanges();
    ships.applyChanges();

    for (final Entity spawned : ships.getAddedEntities()) {
      applyConfigTo(spawned);
    }
    // Also re-project on ShipType change (e.g. player swaps ships via key 1-8). The
    // AvatarSystem remove+set pattern on ShipType actually surfaces here as an add
    // event, but treating changes the same keeps us robust to future mutation patterns.
    for (final Entity changed : ships.getChangedEntities()) {
      applyConfigTo(changed);
    }
  }

  /**
   * Re-projects {@link ShipConfig} stats onto every ship currently in scope by
   * directly writing the latest component values. Use after a hot-reload of
   * the per-arena Groovy config so all ships pick up the new tuning, not just
   * whoever triggered the reload.
   *
   * <p>Thread-safe to call from any thread (RMI, chat, sim) — this only
   * mutates ECS components via {@link EntityData#setComponent}, which is
   * already thread-safe in Zay-ES. The temporary {@code EntitySet} is
   * thread-local and released before return.
   *
   * @return number of ships re-projected
   */
  public int reprojectAll() {
    int n = 0;
    final EntitySet allShips = ed.getEntities(ShipType.class);
    try {
      allShips.applyChanges();
      for (final Entity ship : allShips) {
        applyConfigTo(ship);
        n++;
      }
    } finally {
      allShips.release();
    }
    log.debug("reprojectAll: re-projected {} ship(s)", n);
    return n;
  }

  private void applyConfigTo(final Entity shipEntity) {
    final ShipType shipType = shipEntity.get(ShipType.class);
    if (shipType == null || shipType.getType() == null) {
      log.warn("Ship {} has null ShipType; skipping config projection", shipEntity.getId());
      return;
    }

    final ArenaId arena = resolveAmbientArena(shipType.getType(), shipEntity.getId());
    if (arena == null) {
      return;
    }

    final ConfigRegistry snapshot = configRegistry.forArena(arena);
    final ShipConfig cfg = snapshot.getShip(shipType.getType());
    if (cfg == null) {
      log.debug(
          "No ShipConfig for {} in arena {}; leaving defaults",
          shipType.getType(),
          arena.getArena());
      return;
    }

    project(shipEntity.getId(), cfg);
    if (log.isDebugEnabled()) {
      log.debug(
          "Projected ShipConfig for {} onto entity {} in arena {}: thrust={} speed={} rotation={} recharge={} energy={} drag={} turn={} bounce={}",
          shipType.getType(),
          shipEntity.getId(),
          arena.getArena(),
          cfg.thrust(),
          cfg.speed(),
          cfg.rotation(),
          cfg.recharge(),
          cfg.energy(),
          cfg.dragFactor(),
          cfg.turnResponsiveness(),
          cfg.bounceRestitution());
    }
  }

  /**
   * Return the single loaded arena's ID, or {@code null} if zero or multiple
   * arenas are loaded (Option R2 — loud refusal rather than silent guess).
   */
  @Nullable
  private ArenaId resolveAmbientArena(final Ship shipType, final EntityId shipId) {
    final int arenaCount = arenas.size();
    if (arenaCount == 0) {
      log.warn(
          "ShipSpawnSystem: no arenas loaded when spawning ship {} (type {}); skipping config",
          shipId,
          shipType);
      return null;
    }
    if (arenaCount > 1) {
      log.warn(
          "ShipSpawnSystem: {} arenas loaded; ambient arena lookup refuses (R2). Spawn {} "
              + "(type {}) left without config. Fix by giving ships their own ArenaId "
              + "component (see TODO in GameEntities.createShip).",
          arenaCount,
          shipId,
          shipType);
      return null;
    }
    return arenas.iterator().next().get(ArenaId.class);
  }

  private void project(final EntityId shipId, final ShipConfig cfg) {
    projectThrust(shipId, cfg.thrust());
    projectSpeed(shipId, cfg.speed());
    projectRotation(shipId, cfg.rotation());
    projectRecharge(shipId, cfg.recharge());
    projectEnergy(shipId, cfg.energy());
    projectFeel(shipId, cfg);
  }

  private void projectThrust(final EntityId shipId, final ShipStat stat) {
    ed.setComponent(shipId, new Thrust(stat.initial()));
    ed.setComponent(shipId, new ThrustMax(stat.max()));
    ed.setComponent(shipId, new ThrustUpgrade(stat.upgrade()));
  }

  private void projectSpeed(final EntityId shipId, final ShipStat stat) {
    ed.setComponent(shipId, new Speed(stat.initial()));
    ed.setComponent(shipId, new SpeedMax(stat.max()));
    ed.setComponent(shipId, new SpeedUpgrade(stat.upgrade()));
  }

  private void projectRotation(final EntityId shipId, final ShipStat stat) {
    ed.setComponent(shipId, new Rotation(stat.initial() * ROTATION_UNITS_TO_RAD_SEC));
    ed.setComponent(shipId, new RotationMax(stat.max() * ROTATION_UNITS_TO_RAD_SEC));
    ed.setComponent(shipId, new RotationUpgrade(stat.upgrade() * ROTATION_UNITS_TO_RAD_SEC));
  }

  private void projectRecharge(final EntityId shipId, final ShipStat stat) {
    ed.setComponent(shipId, new Recharge(stat.initial() * RECHARGE_UNITS_TO_PER_SEC));
    ed.setComponent(shipId, new RechargeMax(stat.max() * RECHARGE_UNITS_TO_PER_SEC));
    ed.setComponent(shipId, new RechargeUpgrade(stat.upgrade() * RECHARGE_UNITS_TO_PER_SEC));
  }

  private void projectEnergy(final EntityId shipId, final ShipStat stat) {
    // Pattern 4 split: Health is the live pool (depletes from damage / weapon
    // costs, regens via Recharge up to Energy); Energy is the upgradeable cap;
    // EnergyMax is the absolute hard cap on Energy.
    ed.setComponent(shipId, new Health(stat.initial()));
    ed.setComponent(shipId, new Energy(stat.initial()));
    ed.setComponent(shipId, new EnergyMax(stat.max()));
    ed.setComponent(shipId, new EnergyUpgrade(stat.upgrade()));
  }

  private void projectFeel(final EntityId shipId, final ShipConfig cfg) {
    ed.setComponent(shipId, new DragFactor(cfg.dragFactor()));
    ed.setComponent(shipId, new TurnResponsiveness(cfg.turnResponsiveness()));
    ed.setComponent(shipId, new BounceRestitution(cfg.bounceRestitution()));
  }
}
