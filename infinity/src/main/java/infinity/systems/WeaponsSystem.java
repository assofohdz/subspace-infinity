/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package infinity.systems;

import com.jme3.math.FastMath;
import com.simsilica.es.*;
import com.simsilica.ext.mphys.MPhysSystem;
import com.simsilica.mathd.Quatd;
import com.simsilica.mathd.Vec3d;
import com.simsilica.mblock.phys.MBlockShape;
import com.simsilica.mphys.*;
import com.simsilica.sim.AbstractGameSystem;
import com.simsilica.sim.SimTime;
import infinity.es.Damage;
import infinity.es.GravityWell;
import infinity.es.ship.actions.Burst;
import infinity.es.ship.actions.Thor;
import infinity.es.ship.weapons.*;
import infinity.sim.CoreGameConstants;
import infinity.sim.CorePhysicsConstants;
import infinity.sim.GameEntities;
import infinity.sim.GameSounds;
import infinity.sim.util.InfinityRunTimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;

/**
 * @author AFahrenholz
 */
public class WeaponsSystem extends AbstractGameSystem
    implements ContactListener<EntityId, MBlockShape> {

  public static final byte GUN = 0x0;
  public static final byte BOMB = 0x1;
  public static final byte GRAVBOMB = 0x2;
  public static final byte MINE = 0x3;
  public static final byte BURST = 0x4;
  public static final byte THOR = 0x5;
  static Logger log = LoggerFactory.getLogger(WeaponsSystem.class);
  private final LinkedHashSet<Attack> sessionAttackCreations = new LinkedHashSet<>();
  private EntityData ed;
  private MPhysSystem<MBlockShape> physics;
  private PhysicsSpace<EntityId, MBlockShape> space;
  private EntitySet thors;
  private EntitySet mines;
  private EntitySet gravityBombs;
  private EntitySet bursts;
  private EntitySet bombs;
  private EntitySet guns;

  private SimTime time;
  private EnergySystem health;

  @Override
  protected void initialize() {
    ed = getSystem(EntityData.class);
    if (ed == null) {
      throw new InfinityRunTimeException(getClass().getName() + " system requires an EntityData object.");
    }
    physics = getSystem(MPhysSystem.class);
    if (physics == null) {
      throw new InfinityRunTimeException(getClass().getName() + " system requires the MPhysSystem system.");
    }

    space = physics.getPhysicsSpace();
    health = getSystem(EnergySystem.class);
    guns = ed.getEntities(Gun.class, GunFireDelay.class, GunCost.class);
    bombs = ed.getEntities(Bomb.class, BombFireDelay.class, BombCost.class);
    bursts = ed.getEntities(Burst.class);
    gravityBombs =
        ed.getEntities(GravityBomb.class, GravityBombFireDelay.class, GravityBombCost.class);
    mines = ed.getEntities(Mine.class, MineFireDelay.class, MineCost.class);
    thors = ed.getEntities(Thor.class);

    getSystem(ContactSystem.class).addListener(this);
  }

  @Override
  protected void terminate() {
    guns.release();
    guns = null;

    bombs.release();
    bombs = null;

    gravityBombs.release();
    gravityBombs = null;

    mines.release();
    mines = null;

    bursts.release();
    bursts = null;

    thors.release();
    thors = null;

    getSystem(ContactSystem.class).removeListener(this);
  }

  @Override
  public void update(final SimTime tpf) {
    time = tpf;

    // Update who has
    guns.applyChanges();
    bombs.applyChanges();
    gravityBombs.applyChanges();
    mines.applyChanges();
    bursts.applyChanges();
    thors.applyChanges();

    /*
     * Default pattern to let multiple sessions call methods and then process them
     * one by one
     *
     * Not sure if this is needed or there is a queue system already in place by the session framework
     */
    final Iterator<Attack> iterator = sessionAttackCreations.iterator();
    while (iterator.hasNext()) {
      final Attack a = iterator.next();

      Entity requester = ed.getEntity(a.getOwner());

      attack(requester, a.getWeaponType(), time.getTime());

      iterator.remove();
    }
  }

  private boolean canAttackGun(Entity requester) {
    EntityId requesterId = requester.getId();
    if (guns.contains(requester)) {
      final GunFireDelay gfd = ed.getComponent(requesterId, GunFireDelay.class);
      if (gfd.getPercent() < 1) {
        return false;
      }
      final GunCost gc = ed.getComponent(requesterId, GunCost.class);
      return gc.getCost() <= health.getHealth(requesterId);
    }
    return false;
  }

  private boolean canAttackBomb(Entity requester) {
    EntityId requesterId = requester.getId();
    if (bombs.contains(requester)) {
      final BombFireDelay bfd = ed.getComponent(requesterId, BombFireDelay.class);
      if (bfd.getPercent() < 1) {
        return false;
      }
      final BombCost bc = ed.getComponent(requesterId, BombCost.class);
      return bc.getCost() <= health.getHealth(requesterId);
    }
    return false;
  }

  private boolean canAttackGravityBomb(Entity requester) {
    EntityId requesterId = requester.getId();
    if (gravityBombs.contains(requester)) {
      final GravityBombFireDelay bfd = ed.getComponent(requesterId, GravityBombFireDelay.class);
      if (bfd.getPercent() < 1) {
        return false;
      }
      final GravityBombCost bc = ed.getComponent(requesterId, GravityBombCost.class);
      return bc.getCost() <= health.getHealth(requesterId);
    }
    return false;
  }

  private boolean canAttackMine(Entity requester) {
    EntityId requesterId = requester.getId();
    if (mines.contains(requester)) {
      final MineFireDelay bfd = ed.getComponent(requesterId, MineFireDelay.class);
      if (bfd.getPercent() < 1) {
        return false;
      }
      final MineCost bc = ed.getComponent(requesterId, MineCost.class);
      return bc.getCost() <= health.getHealth(requesterId);
    }
    return false;
  }

  private boolean canAttackBurst(Entity requester) {
    return bursts.contains(requester);
  }

  private boolean canAttackThor(Entity requester) {
    return thors.contains(requester);
  }

  private boolean canAttack(Entity requester, byte weaponType) {
    if (requester == null) {
      return false;
    }
    switch (weaponType) {
      case GUN:
        return canAttackGun(requester);
      case BOMB:
        return canAttackBomb(requester);
      case GRAVBOMB:
        return canAttackGravityBomb(requester);
      case MINE:
        return canAttackMine(requester);
      case BURST:
        return canAttackBurst(requester);
      case THOR:
        return canAttackThor(requester);
      default:
        return false;
    }
  }

  private boolean setCoolDownGun(final Entity requester) {
    EntityId requesterId = requester.getId();
    if (guns.contains(requester)) {
      final GunFireDelay gfd = ed.getComponent(requesterId, GunFireDelay.class);
      ed.setComponent(requesterId, gfd.copy());
      return true;
    }
    return false;
  }

  private boolean setCoolDownBomb(final Entity requester) {
    EntityId requesterId = requester.getId();
    if (bombs.contains(requester)) {
      final BombFireDelay bfd = ed.getComponent(requesterId, BombFireDelay.class);
      ed.setComponent(requesterId, bfd.copy());
      return true;
    }
    return false;
  }

  private boolean setCoolDownGravityBomb(final Entity requester) {
    EntityId requesterId = requester.getId();
    if (gravityBombs.contains(requester)) {
      final GravityBombFireDelay bfd = ed.getComponent(requesterId, GravityBombFireDelay.class);
      ed.setComponent(requesterId, bfd.copy());
      return true;
    }
    return false;
  }

  private boolean setCoolDownMine(final Entity requester) {
    EntityId requesterId = requester.getId();
    if (mines.contains(requester)) {
      final MineFireDelay bfd = ed.getComponent(requesterId, MineFireDelay.class);
      ed.setComponent(requesterId, bfd.copy());
      return true;
    }
    return false;
  }

  private boolean setCoolDown(final Entity requester, final byte flag) {

    if (requester == null) {
      return false;
    }
    if (flag == GUN) {
      return setCoolDownGun(requester);
    } else if (flag == BOMB) {
      return setCoolDownBomb(requester);
    } else if (flag == GRAVBOMB) {
      return setCoolDownGravityBomb(requester);
    } else if (flag == MINE) {
      return setCoolDownMine(requester);
    } else if (flag == BURST) {
      // No delay on this for now
      return bursts.contains(requester);
    } else if (flag == THOR) {
      // No delay on this for now
      return thors.contains(requester);
    }
    return false;
  }

  private boolean deductCostOfAttackGun(final Entity requester) {
    EntityId requesterId = requester.getId();
    if (guns.contains(requester)) {
      final GunCost gc = ed.getComponent(requesterId, GunCost.class);
      if (gc.getCost() > health.getHealth(requesterId)) {
        return false;
      }
      health.damage(requesterId, gc.getCost());
      return true;
    }
    return false;
  }

  private boolean deductCostOfAttackBomb(final Entity requester) {
    EntityId requesterId = requester.getId();
    if (bombs.contains(requester)) {
      final BombCost bc = ed.getComponent(requesterId, BombCost.class);
      if (bc.getCost() > health.getHealth(requesterId)) {
        return false;
      }
      health.damage(requesterId, bc.getCost());
      return true;
    }
    return false;
  }

  private boolean deductCostOfAttackGravityBomb(final Entity requester) {
    EntityId requesterId = requester.getId();
    if (gravityBombs.contains(requester)) {
      final GravityBombCost bc = ed.getComponent(requesterId, GravityBombCost.class);
      if (bc.getCost() > health.getHealth(requesterId)) {
        return false;
      }
      health.damage(requesterId, bc.getCost());
      return true;
    }
    return false;
  }

  private boolean deductCostOfAttackMine(final Entity requester) {
    EntityId requesterId = requester.getId();
    if (mines.contains(requester)) {
      final MineCost bc = ed.getComponent(requesterId, MineCost.class);
      if (bc.getCost() > health.getHealth(requesterId)) {
        return false;
      }
      health.damage(requesterId, bc.getCost());
      return true;
    }
    return false;
  }

  private boolean deductCostOfAttack(final Entity requester, final byte flag) {
    if (requester == null) {
      return false;
    }
    if (flag == GUN) {
      return deductCostOfAttackGun(requester);
    } else if (flag == BOMB) {
      return deductCostOfAttackBomb(requester);
    } else if (flag == GRAVBOMB) {
      return deductCostOfAttackGravityBomb(requester);
    } else if (flag == MINE) {
      return deductCostOfAttackMine(requester);
    } else if (flag == BURST) {
      // No cost on this for now
      //TODO: Add cost to burst
      return bursts.contains(requester);
    } else if (flag == THOR) {
      // No cost on this for now
      //TODO: Add cost to thor
      return thors.contains(requester);
    }
    return false;
  }

  private void createProjectileGun(
      Entity requesterEntity, final long time, AttackPosition info) {
    EntityId requester = requesterEntity.getId();

    Gun entityGun = this.guns.getEntity(requester).get(Gun.class);
    GunCost gc = this.guns.getEntity(requester).get(GunCost.class);
    final String bulletShape = "bullet_l" + entityGun.getLevel().level;

    EntityId gunProjectile;
    gunProjectile =
        GameEntities.createBullet(
            ed,
            requester,
            space,
            time,
            info.location,
            info.attackVelocity,
            CoreGameConstants.BULLETDECAY,
            bulletShape);

    ed.setComponent(gunProjectile, new Damage(gc.getCost()));
  }

  private void createProjectileBomb(
      Entity requesterEntity, long time, AttackPosition info) {
    EntityId requester = requesterEntity.getId();
    Bomb entityBomb = this.bombs.getEntity(requester).get(Bomb.class);
    BombCost bc = this.bombs.getEntity(requester).get(BombCost.class);

    final String bombShape = CoreGameConstants.BOMBLEVELPREPENDTEXT + entityBomb.getLevel().level;

    final EntityId bombProjectile =
        GameEntities.createBomb(
            ed,
            requester,
            space,
            time,
            info.getLocation(),
            info.getAttackVelocity(),
            CoreGameConstants.BULLETDECAY,
            bombShape);
    ed.setComponent(bombProjectile, new Damage(bc.getCost()));
  }

  private void createProjectileGravBomb(
      Entity requesterEntity, long time, AttackPosition info) {
    EntityId requester = requesterEntity.getId();
    GravityBomb gravityBomb = this.gravityBombs.getEntity(requester).get(GravityBomb.class);
    final GravityBombCost shipGravBombCost = ed.getComponent(requester, GravityBombCost.class);

    EntityId projectile;
    final HashSet<EntityComponent> delayedComponents = new HashSet<>();
    delayedComponents.add(
        new GravityWell(
            5, CoreGameConstants.GRAVBOMBWORMHOLEFORCE, GravityWell.PULL)); // Suck everything in

    projectile =
        GameEntities.createDelayedBomb(
            ed,
            requester,
            space,
            time,
            info.getLocation(),
            info.getAttackVelocity(),
            CoreGameConstants.GRAVBOMBDECAY,
            CoreGameConstants.GRAVBOMBDELAY,
            delayedComponents,
            CoreGameConstants.BOMBLEVELPREPENDTEXT + gravityBomb.getLevel());

    ed.setComponent(projectile, new Damage(shipGravBombCost.getCost()));
  }

  private void createProjectileThor(
      Entity requesterEntity, long time, AttackPosition info) {
    EntityId requester = requesterEntity.getId();

    EntityId thorProjectile;
    thorProjectile =
        GameEntities.createThor(
            ed,
            requester,
            space,
            time,
            info.getLocation(),
            info.getAttackVelocity(),
            CoreGameConstants.THORDECAY);
    ed.setComponent(thorProjectile, new Damage(20));
  }

  private void createProjectileBurst(
      Entity requesterEntity, long time) {
    Quatd orientation = new Quatd();

    final double angle = (360d / CoreGameConstants.BURSTPROJECTILECOUNT) * FastMath.DEG_TO_RAD;

    final AttackPosition infoOrig = getAttackInfo(requesterEntity, WeaponsSystem.BURST);
    for (int i = 0; i < CoreGameConstants.BURSTPROJECTILECOUNT; i++) {
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
              space,
              time,
              info.getLocation(),
              info.getAttackVelocity(),
              CoreGameConstants.BULLETDECAY);
      ed.setComponent(projectile, new Damage(20));
    }
  }

  private void createProjectile(
      Entity requesterEntity, final byte flag, long time, AttackPosition info) {
    switch (flag) {
      case GUN:
        createProjectileGun(requesterEntity, time, info);
        break;
      case BOMB:
        createProjectileBomb(requesterEntity, time, info);
        break;
      case GRAVBOMB:
        createProjectileGravBomb(requesterEntity, time, info);
        break;
      case MINE:
        log.info("TODO: MINE PROJECTILE");
        break;
      case BURST:
        createProjectileBurst(requesterEntity, time);
        break;
      case THOR:
        createProjectileThor(requesterEntity, time, info);
        break;
      default:
        throw new IllegalArgumentException("Unknown flag: " + flag);
    }
  }

  private boolean createSound(Entity requesterEntity, byte flag, long time, AttackPosition info) {
    EntityId requester = requesterEntity.getId();
    switch (flag) {
      case GUN:
        Gun entityGun = this.guns.getEntity(requester).get(Gun.class);
        GameSounds.createBulletSound(
            ed, requester, space, time, info.location, entityGun.getLevel());
        return true;
      case BOMB:
        Bomb entityBomb = this.bombs.getEntity(requester).get(Bomb.class);
        GameSounds.createBombSound(
            ed, requester, space, time, info.location, entityBomb.getLevel());
        return true;
      case GRAVBOMB:
        GravityBomb entityGravBomb = this.gravityBombs.getEntity(requester).get(GravityBomb.class);
        GameSounds.createBombSound(
            ed, requester, space, time, info.location, entityGravBomb.getLevel());
        return true;
      case MINE:
        Mine mine = this.mines.getEntity(requester).get(Mine.class);
        GameSounds.createMineSound(ed, requester, space, time, info.location, mine.getLevel());
        break;
      case BURST:
        break;
      case THOR:
        GameSounds.createThorSound(ed, time, requester, info.location, space);
        return true;
      default:
        throw new IllegalArgumentException("Unknown flag: " + flag);
    }
    return false;
  }

  /**
   * A request to attack with a weapon
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
   * Find the velocity and the position of the projectile
   *
   * @param attackerEntity requesting entity
   * @param weaponFlag
   */
  private AttackPosition getAttackInfo(final Entity attackerEntity, final byte weaponFlag) {
    EntityId attacker = attackerEntity.getId();
    // Default vector for projectiles (z=forward):
    Vec3d projectileVelocity = new Vec3d(0, 0, 1);

    final RigidBody<?, ?> shipBody = physics.getPhysicsSpace().getBinIndex().getRigidBody(attacker);

    // Step 1: Scale the velocity based on weapon type, weapon level and ship type
    // TODO: Look these settings up in SettingsSystem
    switch (weaponFlag) {
      case WeaponsSystem.GUN:
        projectileVelocity.addLocal(0, 0, 50);
        break;
      case WeaponsSystem.BOMB:
        projectileVelocity.addLocal(0, 0, 25);
        break;
      case WeaponsSystem.GRAVBOMB:
        break;
      case WeaponsSystem.MINE:
        break;
      default:
        throw new AssertionError("Flag :" + weaponFlag + " not recognized");
    }

    // Step 2: Rotate the scaled velocity
    final Quatd shipRotation = new Quatd(shipBody.orientation);
    final Vec3d shipVelocity = shipBody.getLinearVelocity();
    projectileVelocity = shipRotation.mult(projectileVelocity);

    // Step 3: Add ship velocity:
    projectileVelocity.addLocal(shipVelocity);

    // Step 4: Correct mines:
    if (weaponFlag == WeaponsSystem.MINE) {
      projectileVelocity.set(0, 0, 0);
    }

    // Step 4: Find the translation
    final Vec3d shipPosition = new Vec3d(shipBody.position);

    Vec3d projectilePosition = new Vec3d(0, 0, 0);
    // Offset with the radius of the projectile
    switch (weaponFlag) {
      case WeaponsSystem.GUN:
        projectilePosition.addLocal(0, 0, CorePhysicsConstants.BULLETSIZERADIUS);
        break;
      case WeaponsSystem.BOMB:
      case WeaponsSystem.GRAVBOMB:
      case WeaponsSystem.MINE:
        projectilePosition.addLocal(0, 0, CorePhysicsConstants.BOMBSIZERADIUS);
        break;
      default:
        throw new AssertionError();
    }
    // Rotate the projectile position just as the ship is rotated
    projectilePosition = shipRotation.mult(projectilePosition);
    // Translate by ship position
    projectilePosition = projectilePosition.add(shipPosition);

    return new AttackPosition(projectilePosition, projectileVelocity);
  }

  /**
   * This method is called from the gamesession and acts as a queue entry
   *
   * @param attacker the attacking entity
   * @param flag the weapon of choice
   */
  public void sessionAttack(final EntityId attacker, final byte flag) {
    sessionAttackCreations.add(new Attack(attacker, flag));
  }

  @Override
  public void newContact(Contact contact) {
    //log.debug("WeaponsSystem contact detected: {}", contact);
    RigidBody body1 = contact.body1;
    AbstractBody body2 = contact.body2;

    // body2 = null means that body1 is hitting the world
    // we first want to test that we are not hitting ourselves, so both are rigidbodies
    if (body2 instanceof RigidBody) {
      EntityId idOne = (EntityId) body1.id;
      EntityId idTwo = (EntityId) body2.id;

      // Only interact with collision if a ship collides with a prize or vice verca
      // We only need to test this way around for ships and prizes since the rigidbody (ship) will
      // always be body1
      /*
      if (prizes.containsId(idTwo) && .containsId(idOne)) {
        log.trace("Entitysets contact resolution found it to be valid");

        PrizeType pt = prizes.getEntity(idTwo).get(PrizeType.class);
        GameSounds.createPrizeSound(ed, ourTime.getTime(), idOne, body1.position, phys);

        this.handlePrizeAcquisition(pt, idOne);
        // Remove prize
        ed.removeEntity(idTwo);
        // Disable contact for further resolution
        contact.disable();
      } else {
        log.trace("Entitysets contact resolution found it to NOT be valid");
      }*/
    }
  }

  /** AttackInfo is where attacks originate (location and velocity) */
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
