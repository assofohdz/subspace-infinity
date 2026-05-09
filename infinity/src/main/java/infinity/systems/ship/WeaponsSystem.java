// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems.ship;

import com.jme3.math.FastMath;
import com.simsilica.es.Entity;
import com.simsilica.es.EntityComponent;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.es.common.Decay;
import com.simsilica.ext.mphys.MPhysSystem;
import com.simsilica.ext.mphys.ShapeInfo;
import com.simsilica.mathd.Quatd;
import com.simsilica.mathd.Vec3d;
import com.simsilica.mblock.phys.MBlockShape;
import com.simsilica.mphys.AbstractBody;
import com.simsilica.mphys.Contact;
import com.simsilica.mphys.ContactListener;
import com.simsilica.mphys.PhysicsSpace;
import com.simsilica.mphys.RigidBody;
import com.simsilica.sim.AbstractGameSystem;
import com.simsilica.sim.SimTime;
import infinity.systems.ContactSystem;
import infinity.config.EngineConfig;
import infinity.settings.EngineConfigSystem;
import infinity.es.Damage;
import infinity.es.Frequency;
import infinity.es.GravityWell;
import infinity.es.ProximityArmed;
import infinity.es.ProximityFuse;
import infinity.es.ShapeNames;
import infinity.es.SplashDamage;
import infinity.es.arena.ArenaId;
import infinity.systems.ArenaSystem;
import infinity.settings.ConfigRegistry;
import infinity.settings.ConfigRegistrySystem;
import infinity.es.ship.Health;
import infinity.es.ship.actions.Burst;
import infinity.es.ship.actions.Thor;
import infinity.es.ship.weapons.BombCost;
import infinity.es.ship.weapons.BombCurrentLevel;
import infinity.es.ship.weapons.BombFireDelay;
import infinity.es.ship.weapons.BombSpeed;
import infinity.es.ship.weapons.Bounce;
import infinity.es.ship.weapons.BurstSpeed;
import infinity.es.ship.weapons.GravityBomb;
import infinity.es.ship.weapons.GravityBombCost;
import infinity.es.ship.weapons.GravityBombFireDelay;
import infinity.es.ship.weapons.BulletCost;
import infinity.es.ship.weapons.BulletCurrentLevel;
import infinity.es.ship.weapons.BulletFireDelay;
import infinity.es.ship.weapons.BulletSpeed;
import infinity.es.ship.weapons.MineCost;
import infinity.es.ship.weapons.MineCurrentLevel;
import infinity.es.ship.weapons.MineFireDelay;
import infinity.es.ship.weapons.MineSpeed;
import infinity.sim.CoreViewConstants;
import infinity.sim.GameEntities;
import infinity.sim.GameSounds;
import infinity.sim.util.InfinityRunTimeException;
import java.util.HashSet;
import java.util.Set;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This system handles the creation of projectiles and the application of damage to entities.
 *
 * @author AFahrenholz
 */
public class WeaponsSystem extends AbstractGameSystem
    implements ContactListener<EntityId, MBlockShape> {

  public static final byte BULLET = 0x0;
  public static final byte BOMB = 0x1;
  public static final byte GRAVBOMB = 0x2;
  public static final byte MINE = 0x3;
  public static final byte BURST = 0x4;

  /**
   * Weapon flags whose projectiles do NOT inherit the firing ship's velocity at fire time —
   * they "lay down" rather than "tag along". Subspace canon: mines drop in place.
   */
  private static final Set<Byte> INERT_DROPS = Set.of(MINE);

  // Bomb / bullet / mine spatial-name prefixes — combined with the
  // current-level int produces the ShapeNames the client maps to spatials.
  // Framework convention; not a Pattern 4 candidate.
  private static final String BOMB_LEVEL_PREFIX = "bomb_l";
  private static final String BULLET_LEVEL_PREFIX = "bullet_l";
  private static final String MINE_LEVEL_PREFIX = "mine_l";

  static Logger log = LoggerFactory.getLogger(WeaponsSystem.class);
  private final Set<Attack> sessionAttackCreations = ConcurrentHashMap.newKeySet();
  private EntityData ed;
  private MPhysSystem<MBlockShape> physics;
  private PhysicsSpace<EntityId, MBlockShape> physicsSpace;
  private ConfigRegistrySystem configRegistry;
  private EntitySet mines;
  private EntitySet gravityBombs;
  private EntitySet bursts;
  private EntitySet bombs;
  private EntitySet bullets;
  private EntitySet frequencies;

  private SimTime time;
  private EnergySystem energySystem;
  private ArenaSystem arenaSystem;
  private EngineConfigSystem engineConfigSystem;
  private EntitySet damageEntities;
  private EntitySet energyEntities;

  /**
   * Per-arena config lookup. The attacker's {@link ArenaId} keys into
   * {@link ConfigRegistrySystem}; arenas with no config get
   * {@link ConfigRegistry#EMPTY} (each weapon-projectile slot defaults to
   * its sub-record's {@code DEFAULTS}). Falls back to {@code EMPTY} when
   * the attacker has no {@code ArenaId} (no-arena void).
   */
  private ConfigRegistry weaponsFor(final EntityId attacker) {
    final ArenaId arenaId = ed.getComponent(attacker, ArenaId.class);
    if (arenaId == null) {
      return ConfigRegistry.EMPTY;
    }
    return configRegistry.forArena(arenaId);
  }

  @Override
  protected void initialize() {
    ed = getSystem(EntityData.class);
    if (ed == null) {
      throw new InfinityRunTimeException(
          getClass().getName() + " system requires an EntityData object.");
    }
    physics = getSystem(MPhysSystem.class);
    if (physics == null) {
      throw new InfinityRunTimeException(
          getClass().getName() + " system requires the MPhysSystem system.");
    }

    physicsSpace = physics.getPhysicsSpace();
    energySystem = getSystem(EnergySystem.class);
    arenaSystem = getSystem(ArenaSystem.class);
    configRegistry = getSystem(ConfigRegistrySystem.class);
    engineConfigSystem = getSystem(EngineConfigSystem.class);
    if (engineConfigSystem == null) {
      throw new InfinityRunTimeException(
          getClass().getName() + " system requires the EngineConfigSystem.");
    }
    bullets = ed.getEntities(BulletCurrentLevel.class, BulletFireDelay.class, BulletCost.class);
    bombs = ed.getEntities(BombCurrentLevel.class, BombFireDelay.class, BombCost.class);
    bursts = ed.getEntities(Burst.class);
    gravityBombs =
        ed.getEntities(GravityBomb.class, GravityBombFireDelay.class, GravityBombCost.class);
    mines = ed.getEntities(MineCurrentLevel.class, MineFireDelay.class, MineCost.class);

    damageEntities = ed.getEntities(Damage.class);
    energyEntities = ed.getEntities(Health.class);

    frequencies = ed.getEntities(Frequency.class);

    getSystem(ContactSystem.class).addListener(this);
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

    frequencies.release();
    frequencies = null;

    damageEntities.release();
    damageEntities = null;

    energyEntities.release();
    energyEntities = null;

    getSystem(ContactSystem.class).removeListener(this);
  }

  @Override
  public void update(final SimTime tpf) {
    time = tpf;

    // Update who has
    bullets.applyChanges();
    bombs.applyChanges();
    gravityBombs.applyChanges();
    mines.applyChanges();
    bursts.applyChanges();

    energyEntities.applyChanges();
    damageEntities.applyChanges();

    /*
     * Default pattern to let multiple sessions call methods and then process them
     * one by one
     *
     * Not sure if this is needed or there is a queue system already in place by the session
     * framework. 25-02-2023: Maybe the right way is to create "attack entities" that are
     * then handled through an entityset.
     */
    final Iterator<Attack> iterator = sessionAttackCreations.iterator();
    while (iterator.hasNext()) {
      final Attack a = iterator.next();

      Entity requester = ed.getEntity(a.getOwner());

      attack(requester, a.getWeaponType(), time.getTime());

      iterator.remove();
    }
  }

  /**
   * Pre-fire eligibility — see {@link WeaponsEligibility#canAttack} and
   * its per-weapon helpers. WeaponsSystem captures the local field set
   * (EntitySets + services) and forwards.
   */
  private boolean canAttack(Entity requester, byte weaponType) {
    return WeaponsEligibility.canAttack(
        ed, configRegistry, physicsSpace, energySystem,
        bullets, bombs, gravityBombs, mines, bursts, energyEntities,
        requester, weaponType);
  }

  /** Stamps the matching FireDelay component — see {@link WeaponsEligibility#setCoolDown}. */
  private boolean setCoolDown(final Entity requester, final byte flag) {
    return WeaponsEligibility.setCoolDown(
        ed, bullets, bombs, gravityBombs, mines, bursts, requester, flag);
  }

  /** Debits the matching Cost from Health — see {@link WeaponsEligibility#deductCostOfAttack}. */
  private boolean deductCostOfAttack(final Entity requester, final byte flag) {
    return WeaponsEligibility.deductCostOfAttack(
        ed, energySystem, bullets, bombs, gravityBombs, mines, bursts, requester, flag);
  }

  private void createProjectileBullet(Entity requesterEntity, final long time, AttackPosition info) {
    EntityId requester = requesterEntity.getId();

    BulletCurrentLevel bulletCurrentLevel = this.bullets.getEntity(requester).get(BulletCurrentLevel.class);
    final String bulletShape =
        BULLET_LEVEL_PREFIX + bulletCurrentLevel.getLevel().level;

    final ConfigRegistry cfg = weaponsFor(requester);
    EntityId gunProjectile;
    gunProjectile =
        GameEntities.createBullet(
            ed,
            requester,
            physicsSpace,
            time,
            info.location,
            info.attackVelocity,
            cfg.bullet().decayMs(),
            bulletShape,
            engineConfigSystem.get().bulletRadius());

    ed.setComponent(
        gunProjectile,
        new Damage(
            CoreViewConstants.EXPLOSION0DECAY,
            cfg.bullet().damageAtLevel(bulletCurrentLevel.getLevel().level),
            ShapeInfo.create(ShapeNames.EXPLODE_0, CoreViewConstants.EXPLOSION0SIZE, ed)));
  }

  private void createProjectileBomb(Entity requesterEntity, long time, AttackPosition info) {
    EntityId requester = requesterEntity.getId();
    BombCurrentLevel bombCurrentLevel = this.bombs.getEntity(requester).get(BombCurrentLevel.class);

    final int bombLevel = bombCurrentLevel.getLevel().level;
    final String bombShape = BOMB_LEVEL_PREFIX + bombLevel;

    final ConfigRegistry cfg = weaponsFor(requester);
    final EntityId bombProjectile =
        GameEntities.createBomb(
            ed,
            requester,
            physicsSpace,
            time,
            info.getLocation(),
            info.getAttackVelocity(),
            cfg.bomb().decayMs(),
            bombShape,
            engineConfigSystem.get().bombRadius());
    ed.setComponent(
        bombProjectile,
        new Damage(
            CoreViewConstants.EXPLOSION1DECAY,
            cfg.bomb().damage(),
            ShapeInfo.create(ShapeNames.EXPLODE_1, CoreViewConstants.EXPLOSION1SIZE, ed)));

    // Slice 9a — project [Bomb] BombExplodePixels onto a SplashDamage marker
    // using the firing ship's bomb level: L1=1×, L2=2×, L3=3×, L4=4× per
    // REFERENCE.md ## Bomb. BombConfig.explodeRadius is already in tiles /
    // world units (Infinity-native; no pixel conversion at the consumer).
    final double splashRadius = WeaponsLogic.splashRadiusForLevel(cfg.bomb().explodeRadius(), bombLevel);
    if (splashRadius > 0.0) {
      ed.setComponent(bombProjectile, new SplashDamage(splashRadius));
    }

    // Slice 9b — project [Bomb] ProximityDistance + BombExplodeDelay onto a
    // ProximityFuse marker. Per-level additive scaling (+1 per level above L1)
    // per REFERENCE.md ## Bomb — distinct from splash's multiplicative scaling.
    // Both knobs must be > 0 to engage proximity arming; either being 0 falls
    // back to direct-contact detonation (preserving 9a behaviour for arenas
    // that haven't opted in).
    final int proxBase = cfg.bomb().proximityDistance();
    final long fuseMs = cfg.bomb().explodeDelayMs();
    if (proxBase > 0 && fuseMs > 0L) {
      final double proxRadius = WeaponsLogic.proximityRadiusForLevel(proxBase, bombLevel);
      ed.setComponent(bombProjectile, new ProximityFuse(proxRadius, fuseMs));
    }

    // Slice S5 — opt this bomb into the repel-impulse scan when the firing
    // arena's BombConfig.repellable is true (Subspace canon: bombs are
    // repellable, the iconic defensive use case).
    if (cfg.bomb().repellable()) {
      ed.setComponent(bombProjectile, new infinity.es.Repellable());
    }

    applyBombRecoil(requester);
  }

  private void createProjectileGravBomb(Entity requesterEntity, long time, AttackPosition info) {
    EntityId requester = requesterEntity.getId();
    GravityBomb gravityBomb = this.gravityBombs.getEntity(requester).get(GravityBomb.class);

    final ConfigRegistry cfg = weaponsFor(requester);
    EntityId projectile;
    final Set<EntityComponent> delayedComponents = new HashSet<>();
    delayedComponents.add(
        new GravityWell(
            5, cfg.gravBomb().wormholeForce(), GravityWell.PULL)); // Suck everything in

    // Subspace VIE: gravbombs are level-3 bombs; damage / decay come from [Bomb].
    projectile =
        GameEntities.createDelayedBomb(
            ed,
            requester,
            physicsSpace,
            time,
            info.getLocation(),
            info.getAttackVelocity(),
            cfg.bomb().decayMs(),
            cfg.gravBomb().delayMs(),
            delayedComponents,
            BOMB_LEVEL_PREFIX + gravityBomb.getLevel(),
            engineConfigSystem.get().bombRadius());

    ed.setComponent(
        projectile,
        new Damage(
            CoreViewConstants.EXPLOSION1DECAY,
            cfg.bomb().damage(),
            ShapeInfo.create(ShapeNames.EXPLODE_1, CoreViewConstants.EXPLOSION1SIZE, ed)));

    applyBombRecoil(requester);
  }

  private void createProjectileBurst(Entity requesterEntity, long time) {
    Quatd orientation = new Quatd();

    final ConfigRegistry cfg = weaponsFor(requesterEntity.getId());
    final long burstCount = cfg.burst().projectileCount();
    final double angle = (360d / burstCount) * FastMath.DEG_TO_RAD;

    final AttackPosition infoOrig = getAttackInfo(requesterEntity, WeaponsSystem.BURST);
    for (int i = 0; i < burstCount; i++) {
      final AttackPosition info = new AttackPosition(infoOrig);
      orientation = orientation.fromAngles(0, angle * i, 0);

      Vec3d newVelocity = info.getAttackVelocity();

      // Rotate:
      newVelocity = orientation.mult(newVelocity);

      info.setAttackVelocity(newVelocity);

      EntityId projectile;
      projectile =
          GameEntities.createBurst(
              ed,
              requesterEntity.getId(),
              physicsSpace,
              time,
              info.getLocation(),
              info.getAttackVelocity(),
              cfg.burst().decayMs(),
              engineConfigSystem.get().burstRadius());
      ed.setComponent(
          projectile,
          new Damage(
              CoreViewConstants.EXPLOSION0DECAY,
              cfg.burst().damage(),
              ShapeInfo.create(ShapeNames.EXPLODE_0, CoreViewConstants.EXPLOSION0SIZE, ed)));
    }
  }

  private void createProjectile(
      Entity requesterEntity, final byte flag, long time, AttackPosition info) {
    switch (flag) {
      case BULLET:
        createProjectileBullet(requesterEntity, time, info);
        break;
      case BOMB:
        createProjectileBomb(requesterEntity, time, info);
        break;
      case GRAVBOMB:
        createProjectileGravBomb(requesterEntity, time, info);
        break;
      case MINE:
        createProjectileMine(requesterEntity, time, info);
        break;
      case BURST:
        createProjectileBurst(requesterEntity, time);
        break;
      default:
        throw new IllegalArgumentException("Unknown flag: " + flag);
    }
  }

  private boolean createProjectileMine(Entity requesterEntity, long time, AttackPosition info) {
    EntityId requester = requesterEntity.getId();
    MineCurrentLevel mineCurrentLevel = this.mines.getEntity(requester).get(MineCurrentLevel.class);
    MineCost mc = this.mines.getEntity(requester).get(MineCost.class);

    final String mineShape =
        MINE_LEVEL_PREFIX + mineCurrentLevel.getLevel().level;

    final ConfigRegistry cfg = weaponsFor(requester);
    final EntityId mineProjectile =
        GameEntities.createMine(
            ed,
            requester,
            physicsSpace,
            time,
            info.getLocation(),
            cfg.mine().decayMs(),
            mineShape,
            engineConfigSystem.get().mineRadius());
    ed.setComponent(
        mineProjectile,
        new Damage(
            CoreViewConstants.EXPLOSION1DECAY,
            mc.getCost(),
            ShapeInfo.create(ShapeNames.EXPLODE_1, CoreViewConstants.EXPLOSION1SIZE, ed)));
    return true;
  }

  private boolean createSound(Entity requesterEntity, byte flag, long time, AttackPosition info) {
    EntityId requester = requesterEntity.getId();
    switch (flag) {
      case BULLET:
        BulletCurrentLevel bulletCurrentLevel = this.bullets.getEntity(requester).get(BulletCurrentLevel.class);
        GameSounds.createBulletSound(
            ed, requester, physicsSpace, time, info.location, bulletCurrentLevel.getLevel());
        return true;
      case BOMB:
        BombCurrentLevel bombCurrentLevel =
            this.bombs.getEntity(requester).get(BombCurrentLevel.class);
        GameSounds.createBombSound(
            ed, requester, physicsSpace, time, info.location, bombCurrentLevel.getLevel());
        return true;
      case GRAVBOMB:
        GravityBomb entityGravBomb = this.gravityBombs.getEntity(requester).get(GravityBomb.class);
        GameSounds.createBombSound(
            ed, requester, physicsSpace, time, info.location, entityGravBomb.getLevel());
        return true;
      case MINE:
        MineCurrentLevel mineCurrentLevel =
            this.mines.getEntity(requester).get(MineCurrentLevel.class);
        GameSounds.createMineSound(
            ed, requester, physicsSpace, time, info.location, mineCurrentLevel.getLevel());
        break;
      case BURST:
        break;
      default:
        throw new IllegalArgumentException("Unknown flag: " + flag);
    }
    return false;
  }

  /**
   * A request to attack with a weapon.
   *
   * @param requester the requesting entity
   * @param flag the weapon type to attack with
   */
  private void attack(final Entity requester, final byte flag, long time) {

    boolean canAttack = canAttack(requester, flag);
    if (canAttack) {
      boolean cooldownSet = setCoolDown(requester, flag);
      if (cooldownSet) {
        boolean costDeducted = deductCostOfAttack(requester, flag);
        if (costDeducted) {
          final AttackPosition info = getAttackInfo(requester, flag);
          createProjectile(requester, flag, time, info);
          createSound(requester, flag, time, info);
        }
      }
    }
  }

  /**
   * Find the velocity and the position of the projectile.
   *
   * @param attackerEntity requesting entity
   * @param weaponFlag the weapon type
   */
  private AttackPosition getAttackInfo(final Entity attackerEntity, final byte weaponFlag) {
    EntityId attacker = attackerEntity.getId();
    // Default vector for projectiles (z=forward):
    Vec3d projectileVelocity = new Vec3d(0, 0, 1);

    final RigidBody<?, ?> shipBody = physics.getPhysicsSpace().getBinIndex().getRigidBody(attacker);

    // Step 1: Scale the projectile velocity. Per-ship knobs come from the
    // BulletSpeed/BombSpeed/BurstSpeed components stamped at spawn (slice 10);
    // engine-tier knobs (subspaceVelocityScale + maxProjectileSpeedJme)
    // bridge Subspace velocity units → jME world units / sec.
    final EngineConfig engineCfg = engineConfigSystem.get();
    applyWeaponSpeedScale(projectileVelocity, attacker, weaponFlag,
        engineCfg.subspaceVelocityScale(), engineCfg.maxProjectileSpeedJme());

    // Step 2: Rotate the scaled velocity
    final Quatd shipRotation = new Quatd(shipBody.orientation);
    final Vec3d shipVelocity = shipBody.getLinearVelocity();
    projectileVelocity = shipRotation.mult(projectileVelocity);

    // Step 3: Add ship velocity (inert drops lay down — no inheritance).
    if (!INERT_DROPS.contains(weaponFlag)) {
      projectileVelocity.addLocal(shipVelocity);
    }

    // Step 4: Find the translation
    final Vec3d shipPosition = new Vec3d(shipBody.position);

    Vec3d projectilePosition = new Vec3d(0, 0, 0);
    WeaponsLogic.applyProjectileRadiusOffset(
        projectilePosition, weaponFlag, engineCfg.bulletRadius(), engineCfg.bombRadius());
    // Rotate the projectile position just as the ship is rotated
    projectilePosition = shipRotation.mult(projectilePosition);
    // Translate by ship position
    projectilePosition = projectilePosition.add(shipPosition);

    return new AttackPosition(projectilePosition, projectileVelocity);
  }

  /**
   * Step 1 of the attack-info pipeline: add the per-weapon speed component (BulletSpeed,
   * BombSpeed, BurstSpeed, MineSpeed) scaled through the engine's subspace→jME bridge
   * into the projectile velocity's z. GRAVBOMB starts from rest. MINE reads
   * {@link MineSpeed}; if absent (older spawn paths) it stays at zero, preserving the
   * pre-S7 inert-drop behaviour.
   */
  private void applyWeaponSpeedScale(
      final Vec3d projectileVelocity,
      final EntityId attacker,
      final byte weaponFlag,
      final double scale,
      final double maxJme) {
    switch (weaponFlag) {
      case WeaponsSystem.BULLET:
        projectileVelocity.addLocal(
            0, 0, WeaponsLogic.effectiveProjectileSpeed(ed.getComponent(attacker, BulletSpeed.class).getSpeed(), scale, maxJme));
        break;
      case WeaponsSystem.BOMB:
        projectileVelocity.addLocal(
            0, 0, WeaponsLogic.effectiveProjectileSpeed(ed.getComponent(attacker, BombSpeed.class).getSpeed(), scale, maxJme));
        break;
      case WeaponsSystem.BURST:
        projectileVelocity.addLocal(
            0, 0, WeaponsLogic.effectiveProjectileSpeed(ed.getComponent(attacker, BurstSpeed.class).getSpeed(), scale, maxJme));
        break;
      case WeaponsSystem.MINE:
        final MineSpeed mineSpeed = ed.getComponent(attacker, MineSpeed.class);
        if (mineSpeed != null) {
          projectileVelocity.addLocal(
              0, 0, WeaponsLogic.effectiveProjectileSpeed(
                  mineSpeed.getSpeed(), scale, maxJme));
        }
        break;
      case WeaponsSystem.GRAVBOMB:
        break;
      default:
        throw new AssertionError("Flag :" + weaponFlag + " not recognized");
    }
  }


  /**
   * This method is called from the gamesession and acts as a queue entry.
   *
   * @param attacker the attacking entity
   * @param flag the weapon of choice
   */
  public void sessionAttack(final EntityId attacker, final byte flag) {
    sessionAttackCreations.add(new Attack(attacker, flag));
  }

  /**
   * The case of our bomb hitting ourselves is handled in the ContactSystem. Here we want to handle
   * the case where an entity that has a Damage component hits an entity that has a Health
   * component. We also want the handle the case where a projectile hits the world.
   *
   * <p>We cannot be sure that the entitysets are updated, so we have to inquire about the entities
   * here and now.
   *
   * <p>Slice 9a — when the damage-bearing entity carries a {@link SplashDamage} marker (today:
   * bombs), the damage path switches from "single-target point damage at the contact" to "scan
   * all {@code Health}-bearing bodies inside the splash radius and damage each one, gated by the
   * arena's {@code friendlyFire} mode." Direct-hit damage on entities without {@link SplashDamage}
   * (bullets, burst, mines, gravity-bomb fall-through) goes through the same friendly-fire gate
   * but with the stricter "mode 2 only" rule.
   *
   * @param contact the contact
   */
  @Override
  public void newContact(Contact contact) {
    RigidBody<EntityId, MBlockShape> body1 = contact.body1;
    AbstractBody<EntityId, MBlockShape> body2 = contact.body2;

    EntityId idOne = body1.id;
    Entity entity1 = ed.getEntity(idOne, Damage.class, Bounce.class, Thor.class, Health.class);

    if (body2 instanceof RigidBody) {
      handleProjectileVsBody(contact, body1, (RigidBody<EntityId, MBlockShape>) body2, entity1);
    } else if (body2 == null
        && entity1.get(Damage.class) != null
        && entity1.get(Thor.class) == null) {
      handleProjectileVsWorld(contact, idOne, entity1);
    }
  }

  /** Body-vs-body branch: pair damage+health entities, gate on proximity fuse, detonate. */
  private void handleProjectileVsBody(
      final Contact contact,
      final RigidBody<EntityId, MBlockShape> body1,
      final RigidBody<EntityId, MBlockShape> body2,
      final Entity entity1) {
    EntityId idTwo = body2.id;
    Entity entity2 = ed.getEntity(idTwo, Damage.class, Health.class);

    log.debug("WeaponsSystem contact detected between: {} and {}", body1.id, body2.id);

    Entity damageEntity;
    Entity energyEntity;
    if (entity1.get(Damage.class) != null && entity2.get(Health.class) != null) {
      damageEntity = entity1;
      energyEntity = entity2;
    } else if (entity2.get(Damage.class) != null && entity1.get(Health.class) != null) {
      damageEntity = entity2;
      energyEntity = entity1;
    } else {
      return;
    }

    // Slice 9b — proximity-fuse bombs that haven't yet armed must NOT detonate
    // on direct body contact with a ship; arming + fuse + detonation flow
    // through ProximityFuseSystem instead. Disabling the contact here means
    // the projectile glides past the ship until the per-tick proximity scan
    // fires. Wall hits + contacts on already-armed bombs fall through to the
    // standard detonation path below.
    final ProximityFuse fuse = ed.getComponent(damageEntity.getId(), ProximityFuse.class);
    final boolean alreadyArmed =
        fuse != null && ed.getComponent(damageEntity.getId(), ProximityArmed.class) != null;
    if (fuse != null && !alreadyArmed) {
      contact.disable();
      return;
    }

    final Damage damage = damageEntity.get(Damage.class);
    detonateProjectile(
        damageEntity.getId(),
        damage,
        contact.contactPoint,
        energyEntity.getId(),
        time.getTime());
    contact.disable();
  }

  /** body2==null branch: projectile hit the world. Bounce if marked, else detonate. */
  private void handleProjectileVsWorld(
      final Contact contact, final EntityId idOne, final Entity entity1) {
    // body2 = null means that body1 is hitting the world
    // thors are handled in the action system
    Damage damage = entity1.get(Damage.class);

    Bounce bounce = ed.getComponent(idOne, Bounce.class);
    if (bounce != null) {
      // Retain all energy in the contact
      contact.restitution = 1;
      // Remove all friction so ingoing angle and outgoing angle are the same
      contact.friction = 0;
      if (bounce.getBounces() == 1) {
        ed.removeComponent(idOne, Bounce.class);
      } else {
        ed.setComponent(idOne, bounce.decreaseBounces());
      }
    } else {
      // Wall-hit detonation: walls bypass the proximity-fuse gate (canonical
      // Subspace — bombs detonate immediately on wall contact regardless of
      // arm state) and fall straight through to the splash / direct path.
      detonateProjectile(idOne, damage, contact.contactPoint, null, time.getTime());
      contact.disable();
    }
  }

  /**
   * Shared detonation path used by both the contact handler and
   * {@code ProximityFuseSystem} (slice 9b) when a proximity fuse expires.
   * Applies damage (splash if {@link SplashDamage} is present, direct otherwise),
   * spawns the visual explosion, and stamps {@code Decay(now, 0)} so the
   * canonical reaper removes the projectile next tick.
   *
   * @param damageEntityId the projectile entity that's detonating
   * @param damage the projectile's {@link Damage} component (already resolved
   *     by the caller)
   * @param explosionPoint the world-space detonation point — for contact
   *     detonation this is the contact point; for proximity-fuse detonation
   *     this is the projectile's body position
   * @param directVictimId the entity that triggered a direct-contact
   *     detonation, or {@code null} for wall-hit / proximity-fuse paths where
   *     no single victim exists. Splash bombs ignore this argument and damage
   *     everything in {@link SplashDamage#getRadiusWorldUnits()}.
   * @param nowSimNanos current simulation time — passed in (rather than read
   *     from this system's {@code time} field) so callers in other systems
   *     don't depend on update-order timing.
   */
  public void detonateProjectile(
      final EntityId damageEntityId,
      final Damage damage,
      final Vec3d explosionPoint,
      final EntityId directVictimId,
      final long nowSimNanos) {
    final SplashDamage splash = ed.getComponent(damageEntityId, SplashDamage.class);
    if (splash != null) {
      applySplashDamage(damageEntityId, damage, splash, explosionPoint, nowSimNanos);
    } else if (directVictimId != null) {
      applyDirectHitDamage(damageEntityId, damage, directVictimId, nowSimNanos);
    }
    GameEntities.createExplosion(
        ed,
        EntityId.NULL_ID,
        physicsSpace,
        nowSimNanos,
        explosionPoint,
        damage.getExplosionDecay(),
        damage.getExplosionShape());
    ed.setComponent(damageEntityId, Decay.duration(nowSimNanos, 0));
  }

  /** Delegates to {@link WeaponsDamageLogic#applyDirectHitDamage} — see that helper for behaviour. */
  private void applyDirectHitDamage(
      final EntityId damageEntityId,
      final Damage damage,
      final EntityId victimId,
      final long nowSimNanos) {
    WeaponsDamageLogic.applyDirectHitDamage(
        ed, arenaSystem, configRegistry, energySystem,
        damageEntityId, damage, victimId, nowSimNanos);
  }

  /** Delegates to {@link WeaponsDamageLogic#applySplashDamage} — see that helper for behaviour. */
  private void applySplashDamage(
      final EntityId damageEntityId,
      final Damage damage,
      final SplashDamage splash,
      final Vec3d explosionPoint,
      final long nowSimNanos) {
    WeaponsDamageLogic.applySplashDamage(
        ed, energyEntities, physicsSpace, arenaSystem, configRegistry, energySystem,
        damageEntityId, damage, splash, explosionPoint, nowSimNanos);
  }

  /** Delegates to {@link WeaponsDamageLogic#applyBombRecoil} — see that helper for behaviour. */
  private void applyBombRecoil(final EntityId shipId) {
    WeaponsDamageLogic.applyBombRecoil(ed, physicsSpace, engineConfigSystem, shipId);
  }

  /** A class that holds the position information needed to create an attack. */
  private static class AttackPosition {

    private final Vec3d location;
    private Vec3d attackVelocity;

    public AttackPosition(final Vec3d location, final Vec3d attackVelocity) {
      this.location = location;
      this.attackVelocity = attackVelocity;
    }

    public AttackPosition(AttackPosition source) {
      this.location = source.location;
      this.attackVelocity = source.attackVelocity;
    }

    public Vec3d getLocation() {
      return location;
    }

    public Vec3d getAttackVelocity() {
      return attackVelocity;
    }

    // This is used when creating a burst attack, because we need to calculate, set and use new
    // angles
    public void setAttackVelocity(final Vec3d attackVelocity) {
      this.attackVelocity = attackVelocity;
    }
  }

  /**
   * A class that holds the information needed to create an attack. This is called from the game
   * session.
   */
  public class Attack {

    final EntityId owner;
    final byte flag;

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
