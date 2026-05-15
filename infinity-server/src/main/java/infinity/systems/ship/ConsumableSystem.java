// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems.ship;

import com.simsilica.es.Entity;
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
import com.simsilica.sim.SimTime;
import infinity.config.BrickConfig;
import infinity.config.DecoyConfig;
import infinity.config.PortalConfig;
import infinity.config.RepelConfig;
import infinity.config.RocketConfig;
import infinity.config.ThorConfig;
import infinity.net.ConsumableTypeId;
import infinity.systems.BaseInfinitySystem;
import infinity.systems.ContactSystem;
import infinity.es.ChangeTarget;
import infinity.es.Damage;
import infinity.es.ShapeNames;
import infinity.es.ship.Speed;
import infinity.es.ship.SpeedChange;
import infinity.es.ship.Thrust;
import infinity.es.ship.ThrustChange;
import infinity.es.ship.actions.Brick;
import infinity.es.ship.actions.BrickChange;
import infinity.es.ship.actions.BrickStats;
import infinity.es.ship.actions.Decoy;
import infinity.es.ship.actions.DecoyChange;
import infinity.es.ship.actions.DecoyStats;
import infinity.es.ship.actions.Portal;
import infinity.es.ship.actions.PortalChange;
import infinity.es.ship.actions.PortalStats;
import infinity.es.ship.actions.Repel;
import infinity.es.ship.actions.RepelChange;
import infinity.es.ship.actions.RepelDistance;
import infinity.es.ship.actions.RepelSpeed;
import infinity.es.ship.actions.Rocket;
import infinity.es.ship.actions.RocketChange;
import infinity.es.ship.actions.RocketStats;
import infinity.es.ship.actions.Thor;
import infinity.settings.ConfigRegistrySystem;
import infinity.settings.EngineConfigSystem;
import infinity.es.ship.actions.ThorChange;
import infinity.es.ship.actions.ThorCurrentCount;
import infinity.es.ship.actions.ThorFireDelay;
import infinity.es.ship.actions.ThorStats;
import infinity.sim.CoreViewConstants;
import infinity.sim.MapFactory;
import infinity.sim.ShipFactory;
import infinity.sim.WeaponFactory;
import infinity.sim.GameSounds;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/** Player consumable actions (thor, repel, rocket-buff, brick/decoy/portal place). */
public class ConsumableSystem extends BaseInfinitySystem
    implements ContactListener<EntityId, MBlockShape> {

  private final Set<Action> sessionActionCreations = ConcurrentHashMap.newKeySet();
  private EntitySet thorOwners;
  private EntitySet repelOwners;
  private EntitySet rocketOwners;
  private EntitySet brickOwners;
  private EntitySet decoyOwners;
  private EntitySet portalOwners;
  private EntityData ed;
  private PhysicsSpace<EntityId, MBlockShape> physicsSpace;
  private MPhysSystem<MBlockShape> physics;
  private EntitySet thorProjectiles;
  private ConfigRegistrySystem configRegistry;
  private EngineConfigSystem engineConfigSystem;

  @Override
  protected void initialize() {
    ed = requireSystem(EntityData.class);
    physics = requireSystem(MPhysSystem.class);
    physicsSpace = physics.getPhysicsSpace();
    configRegistry = requireSystem(ConfigRegistrySystem.class);
    engineConfigSystem = requireSystem(EngineConfigSystem.class);
    thorOwners = ed.getEntities(ThorCurrentCount.class);
    thorProjectiles = ed.getEntities(Thor.class);
    repelOwners = ed.getEntities(Repel.class);
    rocketOwners = ed.getEntities(Rocket.class, RocketStats.class);
    brickOwners = ed.getEntities(Brick.class, BrickStats.class);
    decoyOwners = ed.getEntities(Decoy.class, DecoyStats.class);
    portalOwners = ed.getEntities(Portal.class, PortalStats.class);

    requireSystem(ContactSystem.class).addListener(this);
  }

  @Override
  protected void terminate() {

    thorOwners.release();
    thorOwners = null;

    thorProjectiles.release();
    thorProjectiles = null;

    repelOwners.release();
    repelOwners = null;

    rocketOwners.release();
    rocketOwners = null;

    brickOwners.release();
    brickOwners = null;

    decoyOwners.release();
    decoyOwners = null;

    portalOwners.release();
    portalOwners = null;

    requireSystem(ContactSystem.class).removeListener(this);
  }

  @Override
  public void update(final SimTime tpf) {
    final SimTime time = tpf;

    thorOwners.applyChanges();
    thorProjectiles.applyChanges();
    repelOwners.applyChanges();
    rocketOwners.applyChanges();
    brickOwners.applyChanges();
    decoyOwners.applyChanges();
    portalOwners.applyChanges();
    final Iterator<Action> iterator = sessionActionCreations.iterator();
    while (iterator.hasNext()) {
      final Action a = iterator.next();

      final Entity requester = ed.getEntity(a.getOwner());

      actOut(requester, a.getAction(), time.getTime());

      iterator.remove();
    }
  }

  /** Queue entry from the game session; wire byte per {@link ConsumableTypeId}. */
  public void sessionAct(final EntityId attacker, final byte flag) {
    sessionActionCreations.add(new Action(attacker, ConsumableTypeId.fromWireId(flag)));
  }

  private void actOut(final Entity requester, final ConsumableTypeId flag, final long time) {

    final boolean canAttack = canAct(requester, flag);
    if (canAttack) {
      final boolean cooldownSet = setCoolDown(requester, flag);
      if (cooldownSet) {
        final boolean costDeducted = deductCostOfAction(requester, flag);
        if (costDeducted) {
          final ActionPosition info = getActionPosition(requester, flag);
          act(requester, flag, time, info);
          createSound(requester, flag, time, info);
        }
      }
    }
  }

  private void act(
      final Entity requesterEntity,
      final ConsumableTypeId flag,
      final long time,
      final ActionPosition info) {
    switch (flag) {
      case FIRETHOR:
        createThor(requesterEntity, time, info);
        break;
      case REPEL:
        createRepel(requesterEntity, time, info);
        break;
      case FIREROCKET:
        createRocketBuff(requesterEntity, time);
        break;
      case PLACEBRICK:
        createBrick(requesterEntity, time);
        break;
      case PLACEDECOY:
        createDecoy(requesterEntity, time);
        break;
      case PLACEPORTAL:
        createPortal(requesterEntity, time);
        break;
      default:
        throw new IllegalArgumentException("Unknown action: " + flag);
    }
  }

  private void createThor(final Entity requesterEntity, final long time, final ActionPosition info) {
    EntityId requester = requesterEntity.getId();
    final ThorConfig cfg = ConsumableLogic.thorConfigFor(ed, configRegistry, requester);

    EntityId gunProjectile;
    gunProjectile =
        WeaponFactory.createThor(
            ed,
            new infinity.sim.specs.ThorArgs(
                requester,
                physicsSpace,
                time,
                info.location,
                info.attackVelocity,
                cfg.decayMs(),
                engineConfigSystem.get().thorRadius()));

    ed.setComponent(
        gunProjectile,
        new Damage(
            CoreViewConstants.EXPLOSION1DECAY,
            cfg.damage(),
            ShapeInfo.create(ShapeNames.EXPLODE_1, 1, ed)));
  }

  /** Stamps {@link RepelSpeed}/{@link RepelDistance} on the spawned effect — repel-impulse reads components, not config. */
  private void createRepel(final Entity requesterEntity, final long time, final ActionPosition info) {
    EntityId requester = requesterEntity.getId();
    final RepelConfig cfg = ConsumableLogic.repelConfigFor(ed, configRegistry, requester);

    final EntityId repelEffect =
        WeaponFactory.createRepel(
            ed,
            new infinity.sim.specs.RepelArgs(
                requester,
                physicsSpace,
                time,
                info.location,
                cfg.timeMs(),
                engineConfigSystem.get().repelRadius()));

    ed.setComponent(repelEffect, new RepelSpeed(cfg.speed()));
    ed.setComponent(repelEffect, new RepelDistance(cfg.distanceTiles()));
  }

  /** Plumbing-only: marker entity carries brick lifetime via {@link Decay}; geometry deferred. */
  private void createBrick(final Entity requesterEntity, final long time) {
    final EntityId ship = requesterEntity.getId();
    final BrickConfig cfg = ConsumableLogic.brickConfigFor(ed, configRegistry, ship);
    MapFactory.createBrick(ed, ship, time, cfg.spanTiles(), cfg.timeMs());
  }

  /** Plumbing-only: marker entity carries decoy lifetime via {@link Decay}; radar-fake deferred. */
  private void createDecoy(final Entity requesterEntity, final long time) {
    final EntityId ship = requesterEntity.getId();
    final DecoyConfig cfg = ConsumableLogic.decoyConfigFor(ed, configRegistry, ship);
    MapFactory.createDecoy(ed, ship, time, cfg.aliveTimeMs());
  }

  /** Plumbing-only: marker entity carries portal lifetime via {@link Decay}; warp-to-portal deferred. */
  private void createPortal(final Entity requesterEntity, final long time) {
    final EntityId ship = requesterEntity.getId();
    final PortalConfig cfg = ConsumableLogic.portalConfigFor(ed, configRegistry, ship);
    MapFactory.createPortal(ed, ship, time, cfg.activeTimeMs());
  }

  /** Fire-time rocket buff: emit Decay-bound Thrust/Speed deltas (writers apply on add, reverse on Decay-driven removal). */
  private void createRocketBuff(final Entity requesterEntity, final long time) {
    final EntityId ship = requesterEntity.getId();
    final RocketConfig cfg = ConsumableLogic.rocketConfigFor(ed, configRegistry, ship);
    final RocketStats rocketStats = ed.getComponent(ship, RocketStats.class);
    if (rocketStats == null) {
      // Belt-and-suspenders for ship-swap races; canAct already gates on RocketStats.
      return;
    }

    final Thrust currentThrust = ed.getComponent(ship, Thrust.class);
    final Speed currentSpeed = ed.getComponent(ship, Speed.class);
    final int originalThrust = currentThrust != null ? currentThrust.getThrust() : 0;
    final int originalSpeed = currentSpeed != null ? currentSpeed.getSpeed() : 0;

    final int deltaThrust = cfg.thrust() - originalThrust;
    final int deltaSpeed = cfg.speed() - originalSpeed;
    final long buffDurationMs = rocketStats.buffDurationMillis();
    final long activeNs =
        TimeUnit.NANOSECONDS.convert(buffDurationMs, TimeUnit.MILLISECONDS);

    if (deltaThrust != 0) {
      final EntityId thrustHolder = ed.createEntity();
      ed.setComponents(
          thrustHolder,
          ChangeTarget.self(ship),
          new ThrustChange(deltaThrust),
          new Decay(time, time + activeNs));
    }
    if (deltaSpeed != 0) {
      final EntityId speedHolder = ed.createEntity();
      ed.setComponents(
          speedHolder,
          ChangeTarget.self(ship),
          new SpeedChange(deltaSpeed),
          new Decay(time, time + activeNs));
    }

    // RocketActive lifecycle owned by the buff entity's Decay; RocketBuffSystem mirrors add/remove.
    ShipFactory.createRocketBuff(
        ed,
        new infinity.sim.specs.RocketBuffArgs(
            ship, time, buffDurationMs, originalThrust, originalSpeed));
  }

  private boolean createSound(
      final Entity requesterEntity,
      final ConsumableTypeId flag,
      final long time,
      final ActionPosition info) {
    final EntityId requester = requesterEntity.getId();
    switch (flag) {
      case FIRETHOR:
        GameSounds.createThorSound(ed, time, requester, info.location, physicsSpace);
        return true;
      case REPEL: // Repel audio composed onto the effect entity by WeaponFactory.createRepel.
      case FIREROCKET: // No SFX wired yet — polish-bag.
      case PLACEBRICK:
      case PLACEDECOY:
      case PLACEPORTAL:
        return true;
      default:
        throw new IllegalArgumentException("Unknown action: " + flag);
    }
  }

  private boolean deductCostOfAction(final Entity requester, final ConsumableTypeId flag) {
    if (requester == null) {
      return false;
    }
    switch (flag) {
      case FIRETHOR:
        return deductCostOfActionThor(requester);
      case REPEL:
        return deductCostOfActionRepel(requester);
      case FIREROCKET:
        return deductCostOfActionRocket(requester);
      case PLACEBRICK:
        return deductCostOfActionBrick(requester);
      case PLACEDECOY:
        return deductCostOfActionDecoy(requester);
      case PLACEPORTAL:
        return deductCostOfActionPortal(requester);
      default:
        return false;
    }
  }

  private boolean deductCostOfActionThor(final Entity requester) {
    emitInventoryDecrement(requester.getId(), new ThorChange(-1));
    return true;
  }

  private boolean deductCostOfActionRepel(final Entity requester) {
    emitInventoryDecrement(requester.getId(), new RepelChange(-1));
    return true;
  }

  private boolean deductCostOfActionRocket(final Entity requester) {
    emitInventoryDecrement(requester.getId(), new RocketChange(-1));
    return true;
  }

  private boolean deductCostOfActionBrick(final Entity requester) {
    emitInventoryDecrement(requester.getId(), new BrickChange(-1));
    return true;
  }

  private boolean deductCostOfActionDecoy(final Entity requester) {
    emitInventoryDecrement(requester.getId(), new DecoyChange(-1));
    return true;
  }

  private boolean deductCostOfActionPortal(final Entity requester) {
    emitInventoryDecrement(requester.getId(), new PortalChange(-1));
    return true;
  }

  /** Emit one-shot inventory-decrement Change-entity; drained by the per-type canonical writer. */
  private void emitInventoryDecrement(
      final EntityId ship, final com.simsilica.es.EntityComponent change) {
    final EntityId holder = ed.createEntity();
    ed.setComponents(holder, ChangeTarget.self(ship), change);
  }

  private boolean canAct(final Entity requester, final ConsumableTypeId actionType) {
    if (requester == null) {
      return false;
    }
    switch (actionType) {
      case FIRETHOR:
        return ConsumableLogic.canFireThor(ed, thorOwners, requester);
      case REPEL:
        return ConsumableLogic.canFireRepel(ed, repelOwners, requester);
      case FIREROCKET:
        return ConsumableLogic.canFireRocket(ed, rocketOwners, requester);
      case PLACEBRICK:
        return ConsumableLogic.canPlaceBrick(ed, brickOwners, requester);
      case PLACEDECOY:
        return ConsumableLogic.canPlaceDecoy(ed, decoyOwners, requester);
      case PLACEPORTAL:
        return ConsumableLogic.canPlacePortal(ed, portalOwners, requester);
      default:
        return false;
    }
  }

  private boolean setCoolDown(final Entity requester, final ConsumableTypeId flag) {

    if (requester == null) {
      return false;
    }
    switch (flag) {
      case FIRETHOR:
        return setCoolDownThor(requester);
      case REPEL: // Other actions use the entity's Decay as the only timing primitive.
      case FIREROCKET:
      case PLACEBRICK:
      case PLACEDECOY:
      case PLACEPORTAL:
        return true;
      default:
        return false;
    }
  }

  private boolean setCoolDownThor(final Entity requester) {
    final EntityId requesterId = requester.getId();
    final ThorStats stats = ed.getComponent(requesterId, ThorStats.class);
    if (stats == null) {
      return true;
    }
    ed.setComponent(requesterId, new ThorFireDelay(stats.fireDelayMillis()));
    return true;
  }

  // Actions whose ActionPosition is just ship-center with zero velocity (radial/self-buff/marker-only).
  private static final Set<ConsumableTypeId> CENTERED_NO_PROJECTILE =
      EnumSet.of(
          ConsumableTypeId.REPEL,
          ConsumableTypeId.FIREROCKET,
          ConsumableTypeId.PLACEBRICK,
          ConsumableTypeId.PLACEDECOY,
          ConsumableTypeId.PLACEPORTAL);

  private ActionPosition getActionPosition(
      final Entity attackerEntity, final ConsumableTypeId weaponFlag) {
    final EntityId attacker = attackerEntity.getId();
    Vec3d projectileVelocity = new Vec3d(0, 0, 1);

    final RigidBody<?, ?> shipBody = physics.getPhysicsSpace().getBinIndex().getRigidBody(attacker);

    if (CENTERED_NO_PROJECTILE.contains(weaponFlag)) {
      return new ActionPosition(new Vec3d(shipBody.position), new Vec3d(0, 0, 0));
    }

    if (weaponFlag == ConsumableTypeId.FIRETHOR) {
      final ThorConfig cfg = ConsumableLogic.thorConfigFor(ed, configRegistry, attacker);
      projectileVelocity.addLocal(0, 0, cfg.launchVelocity());
    } else {
      throw new AssertionError("Action :" + weaponFlag + " not recognized");
    }

    final Quatd shipRotation = new Quatd(shipBody.orientation);
    final Vec3d shipVelocity = shipBody.getLinearVelocity();
    projectileVelocity = shipRotation.mult(projectileVelocity);
    projectileVelocity.addLocal(shipVelocity);

    final Vec3d shipPosition = new Vec3d(shipBody.position);
    Vec3d projectilePosition = new Vec3d(0, 0, 0);
    if (weaponFlag == ConsumableTypeId.FIRETHOR) {
      projectilePosition.addLocal(0, 0, engineConfigSystem.get().thorRadius());
    } else {
      throw new AssertionError();
    }
    projectilePosition = shipRotation.mult(projectilePosition);
    projectilePosition = projectilePosition.add(shipPosition);

    return new ActionPosition(projectilePosition, projectileVelocity);
  }

  @Override
  public void newContact(final Contact<EntityId, MBlockShape> contact) {
    RigidBody<EntityId, MBlockShape> body1 = contact.body1;
    AbstractBody<EntityId, MBlockShape> body2 = contact.body2;

    // Thor passes through world geometry — rarest condition first.
    if (thorProjectiles.containsId(body1.id) && body2 == null) {
      contact.disable();
    }
  }

  public boolean isThor(final EntityId idOne) {
    return thorProjectiles.containsId(idOne);
  }

  private static class ActionPosition {

    private final Vec3d location;
    private Vec3d attackVelocity;

    public ActionPosition(final Vec3d location, final Vec3d attackVelocity) {
      this.location = location;
      this.attackVelocity = attackVelocity;
    }

    public ActionPosition(final ActionPosition source) {
      this.location = source.location;
      this.attackVelocity = source.attackVelocity;
    }

    public Vec3d getLocation() {
      return location;
    }

    public Vec3d getAttackVelocity() {
      return attackVelocity;
    }

    public void setAttackVelocity(final Vec3d attackVelocity) {
      this.attackVelocity = attackVelocity;
    }
  }

  /** Consumable action request queue entry. */
  public static final class Action {

    private final EntityId owner;
    private final ConsumableTypeId actionType;

    public Action(final EntityId owner, final ConsumableTypeId action) {
      this.owner = owner;
      this.actionType = action;
    }

    public EntityId getOwner() {
      return owner;
    }

    public ConsumableTypeId getAction() {
      return actionType;
    }
  }
}
