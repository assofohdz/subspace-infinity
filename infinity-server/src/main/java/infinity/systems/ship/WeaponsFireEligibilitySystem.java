// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems.ship;

import com.jme3.math.FastMath;
import com.simsilica.es.Entity;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.ext.mphys.MPhysSystem;
import com.simsilica.mathd.Quatd;
import com.simsilica.mathd.Vec3d;
import com.simsilica.mblock.phys.MBlockShape;
import com.simsilica.mphys.PhysicsSpace;
import com.simsilica.mphys.RigidBody;
import com.simsilica.sim.SimTime;
import infinity.config.EngineConfig;
import infinity.es.ChangeTarget;
import infinity.es.ship.actions.Burst;
import infinity.es.ship.actions.BurstStats;
import infinity.es.ship.weapons.BombCurrentLevel;
import infinity.es.ship.weapons.BombFireDelay;
import infinity.es.ship.weapons.BombStats;
import infinity.es.ship.weapons.BulletCurrentLevel;
import infinity.es.ship.weapons.BulletFireDelay;
import infinity.es.ship.weapons.BulletStats;
import infinity.es.ship.weapons.FireRequest;
import infinity.es.ship.weapons.GravBomb;
import infinity.es.ship.weapons.GravBombStats;
import infinity.es.ship.weapons.GravityBombFireDelay;
import infinity.es.ship.weapons.MineCurrentLevel;
import infinity.es.ship.weapons.MineFireDelay;
import infinity.es.ship.weapons.MineStats;
import infinity.es.ship.weapons.WeaponType;
import infinity.settings.EngineConfigSystem;
import infinity.systems.BaseInfinitySystem;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Pre-fire gate — drains the {@code sessionAttack} queue, runs eligibility+cooldown+cost via {@link WeaponsEligibility}, emits {@link FireRequest} on pass. Implements {@link infinity.sim.WeaponsFiring} so bot BTs can fire through the same gate. See ADR 0001 / ADR 0009. */
public class WeaponsFireEligibilitySystem extends BaseInfinitySystem implements infinity.sim.WeaponsFiring {

  // Weapons that lay down in place rather than tag along (Subspace canon: mines).
  private static final Set<Byte> INERT_DROPS = Set.of(WeaponType.MINE);

  static final Logger log = LoggerFactory.getLogger(WeaponsFireEligibilitySystem.class);

  private final Set<Attack> sessionAttackCreations = ConcurrentHashMap.newKeySet();
  private EntityData ed;
  private MPhysSystem<MBlockShape> physics;
  private PhysicsSpace<EntityId, MBlockShape> physicsSpace;
  private EngineConfigSystem engineConfigSystem;
  private EnergySystem energySystem;
  private infinity.settings.ConfigRegistrySystem configRegistry;

  private EntitySet bullets;
  private EntitySet bombs;
  private EntitySet gravityBombs;
  private EntitySet mines;
  private EntitySet bursts;
  private EntitySet energyEntities;

  @Override
  @SuppressWarnings("unchecked")
  protected void initialize() {
    ed = requireSystem(EntityData.class);
    physics = requireSystem(MPhysSystem.class);
    physicsSpace = physics.getPhysicsSpace();
    energySystem = requireSystem(EnergySystem.class);
    engineConfigSystem = requireSystem(EngineConfigSystem.class);
    configRegistry = requireSystem(infinity.settings.ConfigRegistrySystem.class);

    bullets = ed.getEntities(BulletCurrentLevel.class, BulletFireDelay.class, BulletStats.class);
    bombs = ed.getEntities(BombCurrentLevel.class, BombFireDelay.class, BombStats.class);
    bursts = ed.getEntities(Burst.class);
    gravityBombs =
        ed.getEntities(GravBomb.class, GravityBombFireDelay.class, GravBombStats.class);
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

    final Iterator<Attack> iterator = sessionAttackCreations.iterator();
    while (iterator.hasNext()) {
      final Attack a = iterator.next();
      final Entity requester = ed.getEntity(a.getOwner());
      attempt(requester, a.getWeaponType());
      iterator.remove();
    }
  }

  /** Queue entry for the game session — one of {@link WeaponType}. */
  public void sessionAttack(final EntityId attacker, final byte flag) {
    sessionAttackCreations.add(new Attack(attacker, flag));
  }

  @Override
  public void requestFire(final EntityId attacker, final byte weaponType) {
    sessionAttack(attacker, weaponType);
  }

  private void attempt(final Entity requester, final byte flag) {
    if (!canAttack(requester, flag)) {
      return;
    }
    if (!setCoolDown(requester, flag)) {
      return;
    }
    if (!deductCostOfAttack(requester, flag)) {
      return;
    }
    if (flag == WeaponType.BURST) {
      emitBurstRequests(requester);
      return;
    }
    final AttackPosition info = getAttackInfo(requester, flag);
    emitFireRequest(requester.getId(), flag, info.location(), info.velocity());
  }

  private boolean canAttack(final Entity requester, final byte weaponType) {
    return WeaponsEligibility.canAttack(
        ed, physicsSpace, energySystem,
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

  // Burst fans out N projectiles; emit N FireRequest holders, one per orientation.
  private void emitBurstRequests(final Entity requesterEntity) {
    final infinity.settings.ConfigRegistry cfg =
        weaponsFor(requesterEntity.getId());
    final long burstCount = cfg.burst().projectileCount();
    final double angle = 360d / burstCount * FastMath.DEG_TO_RAD;
    final AttackPosition origInfo = getAttackInfo(requesterEntity, WeaponType.BURST);
    Quatd orientation = new Quatd();
    for (int i = 0; i < burstCount; i++) {
      orientation = orientation.fromAngles(0, angle * i, 0);
      final Vec3d rotated = orientation.mult(origInfo.velocity());
      emitFireRequest(
          requesterEntity.getId(), WeaponType.BURST, new Vec3d(origInfo.location()), rotated);
    }
  }

  private void emitFireRequest(
      final EntityId attacker, final byte flag, final Vec3d location, final Vec3d velocity) {
    final EntityId holder = ed.createEntity();
    ed.setComponents(
        holder, ChangeTarget.self(attacker), new FireRequest(flag, location, velocity));
  }

  private infinity.settings.ConfigRegistry weaponsFor(final EntityId attacker) {
    final infinity.es.arena.ArenaId arenaId = ed.getComponent(attacker, infinity.es.arena.ArenaId.class);
    if (arenaId == null) {
      return infinity.settings.ConfigRegistry.EMPTY;
    }
    return configRegistry.forArena(arenaId);
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

  /** Computed projectile spawn position + initial velocity at fire time. */
  private record AttackPosition(Vec3d location, Vec3d velocity) {
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
