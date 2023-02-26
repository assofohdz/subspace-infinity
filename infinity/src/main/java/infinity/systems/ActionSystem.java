/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package infinity.systems;

import com.simsilica.es.Entity;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
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
import infinity.es.Damage;
import infinity.es.ShapeNames;
import infinity.es.ship.actions.Thor;
import infinity.es.ship.actions.ThorCurrentCount;
import infinity.es.ship.actions.ThorFireDelay;
import infinity.sim.CoreGameConstants;
import infinity.sim.CorePhysicsConstants;
import infinity.sim.CoreViewConstants;
import infinity.sim.GameEntities;
import infinity.sim.GameSounds;
import infinity.sim.util.InfinityRunTimeException;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentHashMap.KeySetView;

/**
 * This system handles all the actions that can be performed by the player.
 *
 * @author AFahrenholz
 */
public class ActionSystem extends AbstractGameSystem
    implements ContactListener<EntityId, MBlockShape> {

  public static final byte PLACEBRICK = 0x0;
  public static final byte FIREBURST = 0x1;
  public static final byte PLACEDECOY = 0x2;
  public static final byte PLACEPORTAL = 0x3;
  public static final byte REPEL = 0x4;
  public static final byte FIREROCKET = 0x5;
  public static final byte FIRETHOR = 0x6;
  public static final byte WARP = 0x7;
  private final KeySetView<Action, Boolean> sessionActionCreations = ConcurrentHashMap.newKeySet();
  private EntitySet thorOwners;
  private SimTime time;
  private EntityData ed;
  private PhysicsSpace<EntityId, MBlockShape> physicsSpace;
  private MPhysSystem<MBlockShape> physics;
  private EntitySet thorProjectiles;

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
    // Here we find the ships that have a thor weapon
    thorOwners = ed.getEntities(ThorCurrentCount.class);
    thorProjectiles = ed.getEntities(Thor.class);

    getSystem(ContactSystem.class).addListener(this);
  }

  @Override
  protected void terminate() {

    thorOwners.release();
    thorOwners = null;

    thorProjectiles.release();
    thorProjectiles = null;

    getSystem(ContactSystem.class).removeListener(this);
  }

  @Override
  public void update(final SimTime tpf) {
    time = tpf;

    thorOwners.applyChanges();
    thorProjectiles.applyChanges();
    /*
     * Default pattern to let multiple sessions call methods and then process them
     * one by one
     *
     * Not sure if this is needed or there is a queue system already in place by the session
     * framework. 25-02-2023: Maybe the right way is to create "attack entities" that are
     * then handled through an entityset.
     */
    final Iterator<Action> iterator = sessionActionCreations.iterator();
    while (iterator.hasNext()) {
      final Action a = iterator.next();

      Entity requester = ed.getEntity(a.getOwner());

      actOut(requester, a.getWeaponType(), time.getTime());

      iterator.remove();
    }
  }

  /**
   * This method is called from the gamesession and acts as a queue entry.
   *
   * @param attacker the attacking entity
   * @param flag the weapon of choice
   */
  public void sessionAct(final EntityId attacker, final byte flag) {
    sessionActionCreations.add(new Action(attacker, flag));
  }

  private void actOut(final Entity requester, final byte flag, long time) {

    boolean canAttack = canAct(requester, flag);
    if (canAttack) {
      boolean cooldownSet = setCoolDown(requester, flag);
      if (cooldownSet) {
        boolean costDeducted = deductCostOfAction(requester, flag);
        if (costDeducted) {
          final ActionPosition info = getActionPosition(requester, flag);
          act(requester, flag, time, info);
          createSound(requester, flag, time, info);
        }
      }
    }
  }

  private void act(Entity requesterEntity, final byte flag, long time, ActionPosition info) {
    if (flag == FIRETHOR) {
      createThor(requesterEntity, time, info);
    } else {
      throw new IllegalArgumentException("Unknown flag: " + flag);
    }
  }

  private void createThor(Entity requesterEntity, final long time, ActionPosition info) {
    EntityId requester = requesterEntity.getId();

    EntityId gunProjectile;
    gunProjectile =
        GameEntities.createThor(
            ed,
            requester,
            physicsSpace,
            time,
            info.location,
            info.attackVelocity,
            CoreGameConstants.BULLETDECAY);

    ed.setComponent(gunProjectile, new Damage(CoreViewConstants.EXPLOSION1DECAY, CoreGameConstants.THORDAMAGE, ShapeInfo.create(
        ShapeNames.EXPLODE_1, 1, ed)));
  }

  private boolean createSound(Entity requesterEntity, byte flag, long time, ActionPosition info) {
    EntityId requester = requesterEntity.getId();
    if (flag == FIRETHOR) {
      GameSounds.createThorSound(ed, time, requester, info.location, physicsSpace);
      return true;
    }
    throw new IllegalArgumentException("Unknown flag: " + flag);
  }

  private boolean deductCostOfAction(final Entity requester, final byte flag) {
    if (requester == null) {
      return false;
    }
    if (flag == FIRETHOR) {
      return deductCostOfActionThor(requester);
    }
    return false;
  }

  private boolean deductCostOfActionThor(final Entity requester) {
    EntityId requesterId = requester.getId();
    ThorCurrentCount tcc = ed.getComponent(requesterId, ThorCurrentCount.class);
    ed.setComponent(requesterId, tcc.subtract(1));
    return true;
  }

  private boolean canAct(Entity requester, byte actionType) {
    if (requester == null) {
      return false;
    }
    if (actionType == FIRETHOR) {
      return canFireThor(requester);
    }
    return false;
  }

  /**
   * Checks if an entity can fire a thor. For now the only check is to make sure the entity has a
   * ThorCurrentCount component with count > 0.
   *
   * @param requester The entity that is requesting to fire a thor
   * @return true if the entity can fire a thor, false otherwise
   */
  private boolean canFireThor(Entity requester) {
    EntityId requesterId = requester.getId();

    if (thorOwners.containsId(requesterId)) {
      ThorCurrentCount tcc = ed.getComponent(requesterId, ThorCurrentCount.class);
      ThorFireDelay tfd = ed.getComponent(requesterId, ThorFireDelay.class);
      return tcc.getCount() > 0 && tfd.getPercent() >= 1;
    }

    return false;
  }

  private boolean setCoolDown(final Entity requester, final byte flag) {

    if (requester == null) {
      return false;
    }
    if (flag == FIRETHOR) {
      return setCoolDownThor(requester);
    }
    return false;
  }

  private boolean setCoolDownThor(final Entity requester) {
    EntityId requesterId = requester.getId();
    final ThorFireDelay gfd = ed.getComponent(requesterId, ThorFireDelay.class);
    ed.setComponent(requesterId, gfd.copy());
    return true;
  }

  /**
   * Find the velocity and the position of the projectile.
   *
   * @param attackerEntity requesting entity
   * @param weaponFlag the weapon type
   */
  private ActionPosition getActionPosition(final Entity attackerEntity, final byte weaponFlag) {
    EntityId attacker = attackerEntity.getId();
    // Default vector for projectiles (z=forward):
    Vec3d projectileVelocity = new Vec3d(0, 0, 1);

    final RigidBody<?, ?> shipBody = physics.getPhysicsSpace().getBinIndex().getRigidBody(attacker);

    // Step 1: Scale the velocity based on weapon type, weapon level and ship type
    // TODO: Look these settings up in SettingsSystem
    if (weaponFlag == FIRETHOR) {
      projectileVelocity.addLocal(0, 0, 50);
    } else {
      throw new AssertionError("Flag :" + weaponFlag + " not recognized");
    }

    // Step 2: Rotate the scaled velocity
    final Quatd shipRotation = new Quatd(shipBody.orientation);
    final Vec3d shipVelocity = shipBody.getLinearVelocity();
    projectileVelocity = shipRotation.mult(projectileVelocity);

    // Step 3: Add ship velocity:
    projectileVelocity.addLocal(shipVelocity);

    // Step 4: Find the translation
    final Vec3d shipPosition = new Vec3d(shipBody.position);

    Vec3d projectilePosition = new Vec3d(0, 0, 0);
    // Offset with the radius of the projectile
    if (weaponFlag == FIRETHOR) {
      projectilePosition.addLocal(0, 0, CorePhysicsConstants.THORSIZERADIUS);
    } else {
      throw new AssertionError();
    }
    // Rotate the projectile position just as the ship is rotated
    projectilePosition = shipRotation.mult(projectilePosition);
    // Translate by ship position
    projectilePosition = projectilePosition.add(shipPosition);

    return new ActionPosition(projectilePosition, projectileVelocity);
  }

  @Override
  public void newContact(Contact<EntityId, MBlockShape> contact) {
    RigidBody<EntityId, MBlockShape> body1 = contact.body1;
    AbstractBody<EntityId, MBlockShape> body2 = contact.body2;

    // We want to allow a Thor to pass through the world. Remember to put the "rarest" condition
    // first here
    if (thorProjectiles.containsId(body1.id) && body2 == null) {
      contact.disable();
    }
  }

  public boolean isThor(EntityId idOne) {
    return thorProjectiles.containsId(idOne);
  }

  /** A class that holds the position information needed to create an attack. */
  private static class ActionPosition {

    private final Vec3d location;
    private Vec3d attackVelocity;

    public ActionPosition(final Vec3d location, final Vec3d attackVelocity) {
      this.location = location;
      this.attackVelocity = attackVelocity;
    }

    public ActionPosition(ActionPosition source) {
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

  /** A class that holds the information needed to perform an action. */
  public class Action {

    final EntityId owner;
    final byte flag;

    public Action(final EntityId owner, final byte flag) {
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
