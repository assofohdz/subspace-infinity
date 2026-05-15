// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems.ship;

import com.simsilica.es.Entity;
import com.simsilica.es.EntityComponent;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.ext.mphys.MPhysSystem;
import com.simsilica.ext.mphys.ShapeInfo;
import com.simsilica.mblock.phys.MBlockShape;
import com.simsilica.mphys.PhysicsSpace;
import com.simsilica.sim.SimTime;
import infinity.es.ChangeTarget;
import infinity.es.Damage;
import infinity.es.GravityWell;
import infinity.es.ProximityFuse;
import infinity.es.ShapeNames;
import infinity.es.SplashDamage;
import infinity.es.arena.ArenaId;
import infinity.es.ship.weapons.BombCurrentLevel;
import infinity.es.ship.weapons.BulletCurrentLevel;
import infinity.es.ship.weapons.FireRequest;
import infinity.es.ship.weapons.MineCurrentLevel;
import infinity.es.ship.weapons.MineStats;
import infinity.es.ship.weapons.WeaponType;
import infinity.settings.ConfigRegistry;
import infinity.settings.ConfigRegistrySystem;
import infinity.settings.EngineConfigSystem;
import infinity.sim.CoreViewConstants;
import infinity.sim.WeaponFactory;
import infinity.systems.BaseInfinitySystem;
import java.util.HashSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Drains {@link FireRequest} holders, spawns projectiles via {@link WeaponFactory}, stamps Damage/SplashDamage/ProximityFuse. See ADR 0001. */
public class WeaponsProjectileSpawnSystem extends BaseInfinitySystem {

  private static final String BOMB_LEVEL_PREFIX = "bomb_l";
  private static final String BULLET_LEVEL_PREFIX = "bullet_l";
  private static final String MINE_LEVEL_PREFIX = "mine_l";

  static final Logger log = LoggerFactory.getLogger(WeaponsProjectileSpawnSystem.class);

  private EntityData ed;
  private PhysicsSpace<EntityId, MBlockShape> physicsSpace;
  private ConfigRegistrySystem configRegistry;
  private EngineConfigSystem engineConfigSystem;

  private EntitySet fireRequests;

  private ConfigRegistry weaponsFor(final EntityId attacker) {
    final ArenaId arenaId = ed.getComponent(attacker, ArenaId.class);
    if (arenaId == null) {
      return ConfigRegistry.EMPTY;
    }
    return configRegistry.forArena(arenaId);
  }

  @Override
  @SuppressWarnings("unchecked")
  protected void initialize() {
    ed = requireSystem(EntityData.class);
    final MPhysSystem<MBlockShape> physics = requireSystem(MPhysSystem.class);
    physicsSpace = physics.getPhysicsSpace();
    configRegistry = requireSystem(ConfigRegistrySystem.class);
    engineConfigSystem = requireSystem(EngineConfigSystem.class);

    fireRequests = ed.getEntities(FireRequest.class, ChangeTarget.class);
  }

  @Override
  protected void terminate() {
    fireRequests.release();
    fireRequests = null;
  }

  @Override
  public void update(final SimTime tpf) {
    fireRequests.applyChanges();
    final long now = tpf.getTime();
    for (final Entity holder : fireRequests) {
      final FireRequest req = holder.get(FireRequest.class);
      final ChangeTarget tgt = holder.get(ChangeTarget.class);
      final EntityId attacker = tgt.target();
      createProjectile(attacker, req, now);
    }
  }

  private void createProjectile(final EntityId attacker, final FireRequest req, final long now) {
    switch (req.weaponType()) {
      case WeaponType.BULLET:
        createProjectileBullet(attacker, now, req);
        break;
      case WeaponType.BOMB:
        createProjectileBomb(attacker, now, req);
        break;
      case WeaponType.GRAVBOMB:
        createProjectileGravBomb(attacker, now, req);
        break;
      case WeaponType.MINE:
        createProjectileMine(attacker, now, req);
        break;
      case WeaponType.BURST:
        createProjectileBurst(attacker, now, req);
        break;
      default:
        throw new IllegalArgumentException("Unknown flag: " + req.weaponType());
    }
  }

  private void createProjectileBullet(
      final EntityId attacker, final long now, final FireRequest req) {
    final BulletCurrentLevel bulletCurrentLevel =
        ed.getComponent(attacker, BulletCurrentLevel.class);
    final String bulletShape = BULLET_LEVEL_PREFIX + bulletCurrentLevel.getLevel().level;

    final ConfigRegistry cfg = weaponsFor(attacker);
    final EntityId bulletProjectile =
        WeaponFactory.createBullet(
            ed,
            new infinity.sim.specs.BulletArgs(
                attacker,
                physicsSpace,
                now,
                req.location(),
                req.velocity(),
                cfg.bullet().decayMs(),
                bulletShape,
                engineConfigSystem.get().bulletRadius()));

    ed.setComponent(
        bulletProjectile,
        new Damage(
            CoreViewConstants.EXPLOSION0DECAY,
            cfg.bullet().damageAtLevel(bulletCurrentLevel.getLevel().level),
            ShapeInfo.create(ShapeNames.EXPLODE_0, CoreViewConstants.EXPLOSION0SIZE, ed)));
  }

  private void createProjectileBomb(
      final EntityId attacker, final long now, final FireRequest req) {
    final BombCurrentLevel bombCurrentLevel =
        ed.getComponent(attacker, BombCurrentLevel.class);
    final int bombLevel = bombCurrentLevel.getLevel().level;
    final String bombShape = BOMB_LEVEL_PREFIX + bombLevel;

    final ConfigRegistry cfg = weaponsFor(attacker);
    final EntityId bombProjectile =
        WeaponFactory.createBomb(
            ed,
            new infinity.sim.specs.BombArgs(
                attacker,
                physicsSpace,
                now,
                req.location(),
                req.velocity(),
                cfg.bomb().decayMs(),
                bombShape,
                engineConfigSystem.get().bombRadius()));
    ed.setComponent(
        bombProjectile,
        new Damage(
            CoreViewConstants.EXPLOSION1DECAY,
            cfg.bomb().damage(),
            ShapeInfo.create(ShapeNames.EXPLODE_1, CoreViewConstants.EXPLOSION1SIZE, ed)));

    final double splashRadius =
        WeaponsLogic.splashRadiusForLevel(cfg.bomb().explodeRadius(), bombLevel);
    if (splashRadius > 0.0) {
      ed.setComponent(bombProjectile, new SplashDamage(splashRadius));
    }

    // Proximity fuse engages only when both base distance and delay are > 0; else direct-contact.
    final int proxBase = cfg.bomb().proximityDistance();
    final long fuseMs = cfg.bomb().explodeDelayMs();
    if (proxBase > 0 && fuseMs > 0L) {
      final double proxRadius = WeaponsLogic.proximityRadiusForLevel(proxBase, bombLevel);
      ed.setComponent(bombProjectile, new ProximityFuse(proxRadius, fuseMs));
    }

    if (cfg.bomb().repellable()) {
      ed.setComponent(bombProjectile, new infinity.es.Repellable());
    }

    applyBombRecoil(attacker);
  }

  private void createProjectileGravBomb(
      final EntityId attacker, final long now, final FireRequest req) {
    // Gravbomb shape inherits the ship's current bomb level (Subspace canon:
    // gravbombs are level-3 bombs; Infinity ties it to BombCurrentLevel so
    // bomb upgrades scale gravbomb shape identically).
    final BombCurrentLevel bombLevel = ed.getComponent(attacker, BombCurrentLevel.class);
    final int level = bombLevel == null ? 1 : bombLevel.getLevel().level;

    final ConfigRegistry cfg = weaponsFor(attacker);
    final Set<EntityComponent> delayedComponents = new HashSet<>();
    delayedComponents.add(
        new GravityWell(5, cfg.gravBomb().wormholeForce(), GravityWell.PULL));

    final EntityId projectile =
        WeaponFactory.createDelayedBomb(
            ed,
            new infinity.sim.specs.DelayedBombArgs(
                attacker,
                physicsSpace,
                now,
                req.location(),
                req.velocity(),
                cfg.bomb().decayMs(),
                cfg.gravBomb().delayMs(),
                delayedComponents,
                BOMB_LEVEL_PREFIX + level,
                engineConfigSystem.get().bombRadius()));

    ed.setComponent(
        projectile,
        new Damage(
            CoreViewConstants.EXPLOSION1DECAY,
            cfg.bomb().damage(),
            ShapeInfo.create(ShapeNames.EXPLODE_1, CoreViewConstants.EXPLOSION1SIZE, ed)));

    applyBombRecoil(attacker);
  }

  // Burst fan-out happens in the eligibility system; each holder produces one projectile here.
  private void createProjectileBurst(
      final EntityId attacker, final long now, final FireRequest req) {
    final ConfigRegistry cfg = weaponsFor(attacker);
    final EntityId projectile =
        WeaponFactory.createBurst(
            ed,
            new infinity.sim.specs.BurstArgs(
                attacker,
                physicsSpace,
                now,
                req.location(),
                req.velocity(),
                cfg.burst().decayMs(),
                engineConfigSystem.get().burstRadius()));
    ed.setComponent(
        projectile,
        new Damage(
            CoreViewConstants.EXPLOSION0DECAY,
            cfg.burst().damage(),
            ShapeInfo.create(ShapeNames.EXPLODE_0, CoreViewConstants.EXPLOSION0SIZE, ed)));
  }

  private void createProjectileMine(
      final EntityId attacker, final long now, final FireRequest req) {
    final MineCurrentLevel mineCurrentLevel = ed.getComponent(attacker, MineCurrentLevel.class);
    final MineStats mineStats = ed.getComponent(attacker, MineStats.class);

    final String mineShape = MINE_LEVEL_PREFIX + mineCurrentLevel.getLevel().level;

    final ConfigRegistry cfg = weaponsFor(attacker);
    final EntityId mineProjectile =
        WeaponFactory.createMine(
            ed,
            new infinity.sim.specs.MineArgs(
                attacker,
                physicsSpace,
                now,
                req.location(),
                cfg.mine().decayMs(),
                mineShape,
                engineConfigSystem.get().mineRadius()));
    // Mine direct-hit damage = per-ship MineStats.dropCostEnergy (Subspace canon).
    ed.setComponent(
        mineProjectile,
        new Damage(
            CoreViewConstants.EXPLOSION1DECAY,
            mineStats.dropCostEnergy(),
            ShapeInfo.create(ShapeNames.EXPLODE_1, CoreViewConstants.EXPLOSION1SIZE, ed)));
  }

  private void applyBombRecoil(final EntityId shipId) {
    final infinity.config.EngineConfig engineCfg = engineConfigSystem.get();
    WeaponsDamageLogic.applyBombRecoil(
        ed, physicsSpace, shipId, engineCfg.bombThrustScale(), engineCfg.maxProjectileSpeedJme());
  }

}
