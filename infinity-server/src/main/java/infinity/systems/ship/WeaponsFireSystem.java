// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems.ship;

import com.jme3.math.FastMath;
import com.simsilica.es.Entity;
import com.simsilica.es.EntityComponent;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.ext.mphys.MPhysSystem;
import com.simsilica.ext.mphys.ShapeInfo;
import com.simsilica.mathd.Quatd;
import com.simsilica.mathd.Vec3d;
import com.simsilica.mblock.phys.MBlockShape;
import com.simsilica.mphys.PhysicsSpace;
import com.simsilica.mphys.RigidBody;
import com.simsilica.sim.SimTime;
import infinity.config.EngineConfig;
import infinity.es.Damage;
import infinity.es.GravityWell;
import infinity.es.ProximityFuse;
import infinity.es.ShapeNames;
import infinity.es.SplashDamage;
import infinity.es.arena.ArenaId;
import infinity.es.ship.actions.Burst;
import infinity.es.ship.actions.BurstStats;
import infinity.es.ship.weapons.BombCurrentLevel;
import infinity.es.ship.weapons.BombFireDelay;
import infinity.es.ship.weapons.BombStats;
import infinity.es.ship.weapons.BulletCurrentLevel;
import infinity.es.ship.weapons.BulletFireDelay;
import infinity.es.ship.weapons.BulletStats;
import infinity.es.ship.weapons.GravityBomb;
import infinity.es.ship.weapons.GravityBombCost;
import infinity.es.ship.weapons.GravityBombFireDelay;
import infinity.es.ship.weapons.MineCurrentLevel;
import infinity.es.ship.weapons.MineFireDelay;
import infinity.es.ship.weapons.MineStats;
import infinity.es.ship.weapons.WeaponType;
import infinity.settings.ConfigRegistry;
import infinity.settings.ConfigRegistrySystem;
import infinity.settings.EngineConfigSystem;
import infinity.sim.CoreViewConstants;
import infinity.sim.WeaponFactory;
import infinity.sim.GameSounds;
import infinity.systems.BaseInfinitySystem;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Fire-side of the weapons pipeline — eligibility, cost, projectile spawn, sound. Contact lives in {@link WeaponsImpactSystem}/{@link WeaponsReaperSystem}. */
public class WeaponsFireSystem extends BaseInfinitySystem {

  // Weapons that lay down in place rather than tag along (Subspace canon: mines).
  private static final Set<Byte> INERT_DROPS = Set.of(WeaponType.MINE);

  private static final String BOMB_LEVEL_PREFIX = "bomb_l";
  private static final String BULLET_LEVEL_PREFIX = "bullet_l";
  private static final String MINE_LEVEL_PREFIX = "mine_l";

  static final Logger log = LoggerFactory.getLogger(WeaponsFireSystem.class);

  private final Set<Attack> sessionAttackCreations = ConcurrentHashMap.newKeySet();
  private EntityData ed;
  private MPhysSystem<MBlockShape> physics;
  private PhysicsSpace<EntityId, MBlockShape> physicsSpace;
  private ConfigRegistrySystem configRegistry;
  private EngineConfigSystem engineConfigSystem;
  private EnergySystem energySystem;

  private EntitySet bullets;
  private EntitySet bombs;
  private EntitySet gravityBombs;
  private EntitySet mines;
  private EntitySet bursts;
  private EntitySet energyEntities;

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
    physics = requireSystem(MPhysSystem.class);
    physicsSpace = physics.getPhysicsSpace();
    energySystem = requireSystem(EnergySystem.class);
    configRegistry = requireSystem(ConfigRegistrySystem.class);
    engineConfigSystem = requireSystem(EngineConfigSystem.class);

    bullets = ed.getEntities(BulletCurrentLevel.class, BulletFireDelay.class, BulletStats.class);
    bombs = ed.getEntities(BombCurrentLevel.class, BombFireDelay.class, BombStats.class);
    bursts = ed.getEntities(Burst.class);
    gravityBombs =
        ed.getEntities(GravityBomb.class, GravityBombFireDelay.class, GravityBombCost.class);
    mines = ed.getEntities(MineCurrentLevel.class, MineFireDelay.class, MineStats.class);

    energyEntities = ed.getEntities(infinity.es.ship.Energy.class);
  }

  @Override
  protected void terminate() {
    bullets.release();
    bullets = null;
    bombs.release();
    bombs = null;
    gravityBombs.release();
    gravityBombs = null;
    mines.release();
    mines = null;
    bursts.release();
    bursts = null;
    energyEntities.release();
    energyEntities = null;
  }

  @Override
  public void update(final SimTime tpf) {
    bullets.applyChanges();
    bombs.applyChanges();
    gravityBombs.applyChanges();
    mines.applyChanges();
    bursts.applyChanges();
    energyEntities.applyChanges();

    final long now = tpf.getTime();
    final Iterator<Attack> iterator = sessionAttackCreations.iterator();
    while (iterator.hasNext()) {
      final Attack a = iterator.next();
      final Entity requester = ed.getEntity(a.getOwner());
      attack(requester, a.getWeaponType(), now);
      iterator.remove();
    }
  }

  private boolean canAttack(final Entity requester, final byte weaponType) {
    return WeaponsEligibility.canAttack(
        ed, configRegistry, physicsSpace, energySystem,
        bullets, bombs, gravityBombs, mines, bursts, energyEntities,
        requester, weaponType);
  }

  private boolean setCoolDown(final Entity requester, final byte flag) {
    return WeaponsEligibility.setCoolDown(
        ed, bullets, bombs, gravityBombs, mines, bursts, requester, flag);
  }

  private boolean deductCostOfAttack(final Entity requester, final byte flag) {
    return WeaponsEligibility.deductCostOfAttack(
        ed, energySystem, bullets, bombs, gravityBombs, mines, bursts, requester, flag);
  }

  /** Queue entry for the game session — one of {@link WeaponType}. */
  public void sessionAttack(final EntityId attacker, final byte flag) {
    sessionAttackCreations.add(new Attack(attacker, flag));
  }

  private void attack(final Entity requester, final byte flag, final long now) {
    if (!canAttack(requester, flag)) {
      return;
    }
    if (!setCoolDown(requester, flag)) {
      return;
    }
    if (!deductCostOfAttack(requester, flag)) {
      return;
    }
    final AttackPosition info = getAttackInfo(requester, flag);
    createProjectile(requester, flag, now, info);
    createSound(requester, flag, now, info);
  }

  private void createProjectileBullet(
      final Entity requesterEntity, final long now, final AttackPosition info) {
    final EntityId requester = requesterEntity.getId();
    final BulletCurrentLevel bulletCurrentLevel =
        bullets.getEntity(requester).get(BulletCurrentLevel.class);
    final String bulletShape = BULLET_LEVEL_PREFIX + bulletCurrentLevel.getLevel().level;

    final ConfigRegistry cfg = weaponsFor(requester);
    final EntityId gunProjectile =
        WeaponFactory.createBullet(
            ed,
            new infinity.sim.specs.BulletSpec(
                requester,
                physicsSpace,
                now,
                info.location,
                info.attackVelocity,
                cfg.bullet().decayMs(),
                bulletShape,
                engineConfigSystem.get().bulletRadius()));

    ed.setComponent(
        gunProjectile,
        new Damage(
            CoreViewConstants.EXPLOSION0DECAY,
            cfg.bullet().damageAtLevel(bulletCurrentLevel.getLevel().level),
            ShapeInfo.create(ShapeNames.EXPLODE_0, CoreViewConstants.EXPLOSION0SIZE, ed)));
  }

  private void createProjectileBomb(
      final Entity requesterEntity, final long now, final AttackPosition info) {
    final EntityId requester = requesterEntity.getId();
    final BombCurrentLevel bombCurrentLevel =
        bombs.getEntity(requester).get(BombCurrentLevel.class);
    final int bombLevel = bombCurrentLevel.getLevel().level;
    final String bombShape = BOMB_LEVEL_PREFIX + bombLevel;

    final ConfigRegistry cfg = weaponsFor(requester);
    final EntityId bombProjectile =
        WeaponFactory.createBomb(
            ed,
            new infinity.sim.specs.BombSpec(
                requester,
                physicsSpace,
                now,
                info.getLocation(),
                info.getAttackVelocity(),
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

    applyBombRecoil(requester);
  }

  private void createProjectileGravBomb(
      final Entity requesterEntity, final long now, final AttackPosition info) {
    final EntityId requester = requesterEntity.getId();
    final GravityBomb gravityBomb = gravityBombs.getEntity(requester).get(GravityBomb.class);

    final ConfigRegistry cfg = weaponsFor(requester);
    final Set<EntityComponent> delayedComponents = new HashSet<>();
    delayedComponents.add(
        new GravityWell(5, cfg.gravBomb().wormholeForce(), GravityWell.PULL));

    final EntityId projectile =
        WeaponFactory.createDelayedBomb(
            ed,
            new infinity.sim.specs.DelayedBombSpec(
                requester,
                physicsSpace,
                now,
                info.getLocation(),
                info.getAttackVelocity(),
                cfg.bomb().decayMs(),
                cfg.gravBomb().delayMs(),
                delayedComponents,
                BOMB_LEVEL_PREFIX + gravityBomb.getLevel(),
                engineConfigSystem.get().bombRadius()));

    ed.setComponent(
        projectile,
        new Damage(
            CoreViewConstants.EXPLOSION1DECAY,
            cfg.bomb().damage(),
            ShapeInfo.create(ShapeNames.EXPLODE_1, CoreViewConstants.EXPLOSION1SIZE, ed)));

    applyBombRecoil(requester);
  }

  private void createProjectileBurst(final Entity requesterEntity, final long now) {
    Quatd orientation = new Quatd();

    final ConfigRegistry cfg = weaponsFor(requesterEntity.getId());
    final long burstCount = cfg.burst().projectileCount();
    final double angle = (360d / burstCount) * FastMath.DEG_TO_RAD;

    final AttackPosition infoOrig = getAttackInfo(requesterEntity, WeaponType.BURST);
    for (int i = 0; i < burstCount; i++) {
      final AttackPosition info = new AttackPosition(infoOrig);
      orientation = orientation.fromAngles(0, angle * i, 0);

      Vec3d newVelocity = info.getAttackVelocity();
      newVelocity = orientation.mult(newVelocity);
      info.setAttackVelocity(newVelocity);

      final EntityId projectile =
          WeaponFactory.createBurst(
              ed,
              new infinity.sim.specs.BurstSpec(
                  requesterEntity.getId(),
                  physicsSpace,
                  now,
                  info.getLocation(),
                  info.getAttackVelocity(),
                  cfg.burst().decayMs(),
                  engineConfigSystem.get().burstRadius()));
      ed.setComponent(
          projectile,
          new Damage(
              CoreViewConstants.EXPLOSION0DECAY,
              cfg.burst().damage(),
              ShapeInfo.create(ShapeNames.EXPLODE_0, CoreViewConstants.EXPLOSION0SIZE, ed)));
    }
  }

  private void createProjectileMine(
      final Entity requesterEntity, final long now, final AttackPosition info) {
    final EntityId requester = requesterEntity.getId();
    final MineCurrentLevel mineCurrentLevel =
        mines.getEntity(requester).get(MineCurrentLevel.class);
    final MineStats mineStats = mines.getEntity(requester).get(MineStats.class);

    final String mineShape = MINE_LEVEL_PREFIX + mineCurrentLevel.getLevel().level;

    final ConfigRegistry cfg = weaponsFor(requester);
    final EntityId mineProjectile =
        WeaponFactory.createMine(
            ed,
            new infinity.sim.specs.MineSpec(
                requester,
                physicsSpace,
                now,
                info.getLocation(),
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

  private void createProjectile(
      final Entity requesterEntity, final byte flag, final long now, final AttackPosition info) {
    switch (flag) {
      case WeaponType.BULLET:
        createProjectileBullet(requesterEntity, now, info);
        break;
      case WeaponType.BOMB:
        createProjectileBomb(requesterEntity, now, info);
        break;
      case WeaponType.GRAVBOMB:
        createProjectileGravBomb(requesterEntity, now, info);
        break;
      case WeaponType.MINE:
        createProjectileMine(requesterEntity, now, info);
        break;
      case WeaponType.BURST:
        createProjectileBurst(requesterEntity, now);
        break;
      default:
        throw new IllegalArgumentException("Unknown flag: " + flag);
    }
  }

  private void createSound(
      final Entity requesterEntity, final byte flag, final long now, final AttackPosition info) {
    final EntityId requester = requesterEntity.getId();
    switch (flag) {
      case WeaponType.BULLET:
        final BulletCurrentLevel bulletCurrentLevel =
            bullets.getEntity(requester).get(BulletCurrentLevel.class);
        GameSounds.createBulletSound(
            ed, requester, physicsSpace, now, info.location, bulletCurrentLevel.getLevel());
        break;
      case WeaponType.BOMB:
        final BombCurrentLevel bombCurrentLevel =
            bombs.getEntity(requester).get(BombCurrentLevel.class);
        GameSounds.createBombSound(
            ed, requester, physicsSpace, now, info.location, bombCurrentLevel.getLevel());
        break;
      case WeaponType.GRAVBOMB:
        final GravityBomb entityGravBomb = gravityBombs.getEntity(requester).get(GravityBomb.class);
        GameSounds.createBombSound(
            ed, requester, physicsSpace, now, info.location, entityGravBomb.getLevel());
        break;
      case WeaponType.MINE:
        final MineCurrentLevel mineCurrentLevel =
            mines.getEntity(requester).get(MineCurrentLevel.class);
        GameSounds.createMineSound(
            ed, requester, physicsSpace, now, info.location, mineCurrentLevel.getLevel());
        break;
      case WeaponType.BURST:
        break;
      default:
        throw new IllegalArgumentException("Unknown flag: " + flag);
    }
  }

  private AttackPosition getAttackInfo(final Entity attackerEntity, final byte weaponFlag) {
    final EntityId attacker = attackerEntity.getId();
    Vec3d projectileVelocity = new Vec3d(0, 0, 1);

    final RigidBody<?, ?> shipBody = physics.getPhysicsSpace().getBinIndex().getRigidBody(attacker);

    final EngineConfig engineCfg = engineConfigSystem.get();
    applyWeaponSpeedScale(
        projectileVelocity,
        attacker,
        weaponFlag,
        engineCfg.subspaceVelocityScale(),
        engineCfg.maxProjectileSpeedJme());

    final Quatd shipRotation = new Quatd(shipBody.orientation);
    final Vec3d shipVelocity = shipBody.getLinearVelocity();
    projectileVelocity = shipRotation.mult(projectileVelocity);

    if (!INERT_DROPS.contains(weaponFlag)) {
      projectileVelocity.addLocal(shipVelocity);
    }

    final Vec3d shipPosition = new Vec3d(shipBody.position);

    Vec3d projectilePosition = new Vec3d(0, 0, 0);
    WeaponsLogic.applyProjectileRadiusOffset(
        projectilePosition, weaponFlag, engineCfg.bulletRadius(), engineCfg.bombRadius());
    projectilePosition = shipRotation.mult(projectilePosition);
    projectilePosition = projectilePosition.add(shipPosition);

    return new AttackPosition(projectilePosition, projectileVelocity);
  }

  /** Reads per-weapon speed from *Stats records, scales subspace→jME, projects into z. GRAVBOMB starts at rest. */
  private void applyWeaponSpeedScale(
      final Vec3d projectileVelocity,
      final EntityId attacker,
      final byte weaponFlag,
      final double scale,
      final double maxJme) {
    switch (weaponFlag) {
      case WeaponType.BULLET:
        projectileVelocity.addLocal(
            0, 0,
            WeaponsLogic.effectiveProjectileSpeed(
                ed.getComponent(attacker, BulletStats.class).speed(), scale, maxJme));
        break;
      case WeaponType.BOMB:
        projectileVelocity.addLocal(
            0, 0,
            WeaponsLogic.effectiveProjectileSpeed(
                ed.getComponent(attacker, BombStats.class).speed(), scale, maxJme));
        break;
      case WeaponType.BURST:
        projectileVelocity.addLocal(
            0, 0,
            WeaponsLogic.effectiveProjectileSpeed(
                ed.getComponent(attacker, BurstStats.class).speed(), scale, maxJme));
        break;
      case WeaponType.MINE:
        final MineStats mineStats = ed.getComponent(attacker, MineStats.class);
        if (mineStats != null) {
          projectileVelocity.addLocal(
              0, 0,
              WeaponsLogic.effectiveProjectileSpeed(mineStats.speed(), scale, maxJme));
        }
        break;
      case WeaponType.GRAVBOMB:
        // Starts from rest.
        break;
      default:
        throw new AssertionError("Flag :" + weaponFlag + " not recognized");
    }
  }

  private void applyBombRecoil(final EntityId shipId) {
    WeaponsDamageLogic.applyBombRecoil(ed, physicsSpace, engineConfigSystem, shipId);
  }

  private static class AttackPosition {

    private final Vec3d location;
    private Vec3d attackVelocity;

    AttackPosition(final Vec3d location, final Vec3d attackVelocity) {
      this.location = location;
      this.attackVelocity = attackVelocity;
    }

    AttackPosition(final AttackPosition source) {
      this.location = source.location;
      this.attackVelocity = source.attackVelocity;
    }

    Vec3d getLocation() {
      return location;
    }

    Vec3d getAttackVelocity() {
      return attackVelocity;
    }

    void setAttackVelocity(final Vec3d attackVelocity) {
      this.attackVelocity = attackVelocity;
    }
  }

  /** Attack request queue entry. */
  public static final class Attack {

    private final EntityId owner;
    private final byte flag;

    public Attack(final EntityId owner, final byte flag) {
      this.owner = owner;
      this.flag = flag;
    }

    public EntityId getOwner() {
      return owner;
    }

    public byte getWeaponType() {
      return flag;
    }
  }

}
