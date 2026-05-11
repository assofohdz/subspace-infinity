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
import infinity.es.ship.actions.BrickMax;
import infinity.es.ship.actions.Decoy;
import infinity.es.ship.actions.DecoyMax;
import infinity.es.ship.actions.Portal;
import infinity.es.ship.actions.PortalMax;
import infinity.es.ship.actions.Repel;
import infinity.es.ship.actions.RepelDistance;
import infinity.es.ship.actions.RepelSpeed;
import infinity.es.ship.actions.Rocket;
import infinity.es.ship.actions.RocketMax;
import infinity.es.ship.actions.RocketTime;
import infinity.es.ship.actions.Thor;
import infinity.settings.ConfigRegistrySystem;
import infinity.settings.EngineConfigSystem;
import infinity.es.ship.actions.ThorCurrentCount;
import infinity.es.ship.actions.ThorFireDelay;
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

/**
 * This system handles all the actions that can be performed by the player.
 *
 * @author AFahrenholz
 */
public class ConsumableSystem extends BaseInfinitySystem
    implements ContactListener<EntityId, MBlockShape> {

  // Consumable wire bytes (PLACEBRICK/FIREBURST/PLACEDECOY/PLACEPORTAL/
  // REPEL/FIREROCKET/FIRETHOR/WARP) live in api/ as
  // `infinity.net.ConsumableTypeId`. Internal dispatch below uses the enum
  // directly; the public byte entry-point `sessionAct(byte)` converts via
  // `ConsumableTypeId.fromWireId(byte)`.

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

  // Per-family config lookups + gate checks live in ConsumableLogic to keep
  // this class's cyclomatic-complexity sum under PMD's class threshold without
  // fragmenting Pattern-4 spawn projection.

  @Override
  protected void initialize() {
    ed = requireSystem(EntityData.class);
    physics = requireSystem(MPhysSystem.class);
    physicsSpace = physics.getPhysicsSpace();
    configRegistry = requireSystem(ConfigRegistrySystem.class);
    engineConfigSystem = requireSystem(EngineConfigSystem.class);
    // Here we find the ships that have a thor weapon
    thorOwners = ed.getEntities(ThorCurrentCount.class);
    thorProjectiles = ed.getEntities(Thor.class);
    // Ships allowed to fire repels (Repel inventory component projected
    // from per-ship `InitialRepel` at spawn).
    repelOwners = ed.getEntities(Repel.class);
    // Ships allowed to fire rockets (Rocket inventory + RocketMax + per-ship
    // RocketTime all projected from `RocketStats` at spawn).
    rocketOwners = ed.getEntities(Rocket.class, RocketMax.class, RocketTime.class);
    // Ships allowed to place bricks (Brick inventory + BrickMax projected from
    // `CountStats` at spawn). Brick lifetime is arena-global, not per-ship.
    brickOwners = ed.getEntities(Brick.class, BrickMax.class);
    // Ships allowed to place decoys (Decoy inventory + DecoyMax projected from
    // `CountStats` at spawn). Decoy lifetime is arena-global, not per-ship.
    decoyOwners = ed.getEntities(Decoy.class, DecoyMax.class);
    // Ships allowed to place portals (Portal inventory + PortalMax projected
    // from `CountStats` at spawn). Portal lifetime is arena-global, not per-ship.
    portalOwners = ed.getEntities(Portal.class, PortalMax.class);

    getSystem(ContactSystem.class).addListener(this);
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

    getSystem(ContactSystem.class).removeListener(this);
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

      final Entity requester = ed.getEntity(a.getOwner());

      actOut(requester, a.getAction(), time.getTime());

      iterator.remove();
    }
  }

  /**
   * This method is called from the gamesession and acts as a queue entry.
   *
   * @param attacker the attacking entity
   * @param flag the wire byte for the consumable action (see
   *     {@link ConsumableTypeId})
   */
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

  private void createThor(Entity requesterEntity, final long time, ActionPosition info) {
    EntityId requester = requesterEntity.getId();
    final ThorConfig cfg = ConsumableLogic.thorConfigFor(ed, configRegistry, requester);

    EntityId gunProjectile;
    gunProjectile =
        WeaponFactory.createThor(
            ed,
            new infinity.sim.specs.ThorSpec(
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

  /**
   * Pattern 4 spawn projection: read per-arena {@link RepelConfig}, project
   * {@code timeMs} into {@link com.simsilica.es.common.Decay} via
   * {@link WeaponFactory#createRepel}, and stamp {@code speed} /
   * {@code distanceTiles} as {@link RepelSpeed} / {@link RepelDistance}
   * components on the spawned effect entity. The repel-impulse system
   * reads those components — never the {@link RepelConfig} template —
   * per the hot-path-consumer rule in
   * {@code .claude/rules/config-pattern.md}.
   */
  private void createRepel(Entity requesterEntity, final long time, ActionPosition info) {
    EntityId requester = requesterEntity.getId();
    final RepelConfig cfg = ConsumableLogic.repelConfigFor(ed, configRegistry, requester);

    final EntityId repelEffect =
        WeaponFactory.createRepel(
            ed,
            new infinity.sim.specs.RepelSpec(
                requester,
                physicsSpace,
                time,
                info.location,
                cfg.timeMs(),
                engineConfigSystem.get().repelRadius()));

    ed.setComponent(repelEffect, new RepelSpeed(cfg.speed()));
    ed.setComponent(repelEffect, new RepelDistance(cfg.distanceTiles()));
  }

  /**
   * Plumbing-only brick placement: decrement {@link Brick} inventory and
   * compose a marker entity that owns the brick lifetime via
   * {@link com.simsilica.es.common.Decay}. The (deferred) brick-geometry
   * slice will consume the marker's
   * {@link infinity.es.ship.actions.BrickSpan} to spawn the per-tile
   * solid wall, register a brick collision filter, and add the client
   * visual.
   */
  private void createBrick(final Entity requesterEntity, final long time) {
    final EntityId ship = requesterEntity.getId();
    final BrickConfig cfg = ConsumableLogic.brickConfigFor(ed, configRegistry, ship);
    MapFactory.createBrick(ed, ship, time, cfg.spanTiles(), cfg.timeMs());
  }

  /**
   * Plumbing-only decoy placement: decrement {@link Decoy} inventory and
   * compose a marker entity that owns the decoy lifetime via
   * {@link com.simsilica.es.common.Decay}. The (deferred) decoy-as-radar-fake
   * slice will read from this marker to drive the canonical Subspace
   * mechanic (a phantom ship on enemy radar that mimics the placer's
   * heading).
   */
  private void createDecoy(final Entity requesterEntity, final long time) {
    final EntityId ship = requesterEntity.getId();
    final DecoyConfig cfg = ConsumableLogic.decoyConfigFor(ed, configRegistry, ship);
    MapFactory.createDecoy(ed, ship, time, cfg.aliveTimeMs());
  }

  /**
   * Plumbing-only portal placement: decrement {@link Portal} inventory and
   * compose a marker entity that owns the portal lifetime via
   * {@link com.simsilica.es.common.Decay}. The (deferred) "warp to placed
   * portal" slice will read from this marker + the per-arena
   * {@link PortalConfig#useRadiusLimit} to drive the canonical Subspace
   * warp-to-portal mechanic.
   */
  private void createPortal(final Entity requesterEntity, final long time) {
    final EntityId ship = requesterEntity.getId();
    final PortalConfig cfg = ConsumableLogic.portalConfigFor(ed, configRegistry, ship);
    MapFactory.createPortal(ed, ship, time, cfg.activeTimeMs());
  }

  /** Fire-time rocket buff: emit Decay-bound Thrust/Speed deltas (writers apply on add, reverse on Decay-driven removal). */
  private void createRocketBuff(final Entity requesterEntity, final long time) {
    final EntityId ship = requesterEntity.getId();
    final RocketConfig cfg = ConsumableLogic.rocketConfigFor(ed, configRegistry, ship);
    final RocketTime rocketTime = ed.getComponent(ship, RocketTime.class);
    if (rocketTime == null) {
      // canAct already gates on rocketOwners (requires RocketTime); belt-and-suspenders for ship-swap races.
      return;
    }

    final Thrust currentThrust = ed.getComponent(ship, Thrust.class);
    final Speed currentSpeed = ed.getComponent(ship, Speed.class);
    final int originalThrust = currentThrust != null ? currentThrust.getThrust() : 0;
    final int originalSpeed = currentSpeed != null ? currentSpeed.getSpeed() : 0;

    final int deltaThrust = cfg.thrust() - originalThrust;
    final int deltaSpeed = cfg.speed() - originalSpeed;
    final long activeNs =
        TimeUnit.NANOSECONDS.convert(rocketTime.getActiveTimeMs(), TimeUnit.MILLISECONDS);

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
        new infinity.sim.specs.RocketBuffSpec(
            ship, time, rocketTime.getActiveTimeMs(), originalThrust, originalSpeed));
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
      case REPEL:
        // Repel audio is composed onto the effect entity by WeaponFactory.createRepel
        // via AudioTypes.repel(ed) — no separate sound entity needed here.
      case FIREROCKET:
        // No rocket-fire SFX wired today — Subspace ships had a per-arena
        // sound but the audio asset isn't in the project yet. Polish-bag item.
      case PLACEBRICK:
        // No brick-place SFX wired today — Subspace had a brick sound but
        // the audio asset isn't in the project yet. Polish-bag item.
      case PLACEDECOY:
        // No decoy-place SFX wired today — Subspace had a decoy sound but
        // the audio asset isn't in the project yet. Polish-bag item.
      case PLACEPORTAL:
        // No portal-place SFX wired today — Subspace had a portal sound but
        // the audio asset isn't in the project yet. Polish-bag item.
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
    EntityId requesterId = requester.getId();
    ThorCurrentCount tcc = ed.getComponent(requesterId, ThorCurrentCount.class);
    ed.setComponent(requesterId, tcc.subtract(1));
    return true;
  }

  private boolean deductCostOfActionRepel(final Entity requester) {
    EntityId requesterId = requester.getId();
    final Repel curr = ed.getComponent(requesterId, Repel.class);
    ed.setComponent(requesterId, curr.decrement(1));
    return true;
  }

  private boolean deductCostOfActionRocket(final Entity requester) {
    final EntityId requesterId = requester.getId();
    final Rocket curr = ed.getComponent(requesterId, Rocket.class);
    ed.setComponent(requesterId, curr.decrement(1));
    return true;
  }

  private boolean deductCostOfActionBrick(final Entity requester) {
    final EntityId requesterId = requester.getId();
    final Brick curr = ed.getComponent(requesterId, Brick.class);
    ed.setComponent(requesterId, curr.decrement(1));
    return true;
  }

  private boolean deductCostOfActionDecoy(final Entity requester) {
    final EntityId requesterId = requester.getId();
    final Decoy curr = ed.getComponent(requesterId, Decoy.class);
    ed.setComponent(requesterId, curr.decrement(1));
    return true;
  }

  private boolean deductCostOfActionPortal(final Entity requester) {
    final EntityId requesterId = requester.getId();
    final Portal curr = ed.getComponent(requesterId, Portal.class);
    ed.setComponent(requesterId, curr.decrement(1));
    return true;
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
      case REPEL:
        // No per-ship fire-delay component for repel today.
      case FIREROCKET:
        // No per-ship fire-delay component for rocket today; the buff
        // entity's Decay is the only timing primitive.
      case PLACEBRICK:
        // No per-ship fire-delay component for brick today; the brick
        // entity's Decay is the only timing primitive.
      case PLACEDECOY:
        // No per-ship fire-delay component for decoy today; the decoy
        // entity's Decay is the only timing primitive.
      case PLACEPORTAL:
        // No per-ship fire-delay component for portal today; the portal
        // entity's Decay is the only timing primitive.
        return true;
      default:
        return false;
    }
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
  /**
   * Weapon flags whose ActionPosition is just the ship's own position with
   * zero velocity — used by mechanics that don't fire a projectile (REPEL is
   * a radial pulse; FIREROCKET is a self-buff; PLACEBRICK/PLACEDECOY/
   * PLACEPORTAL are plumbing-only markers whose position info is dropped by
   * the corresponding {@code create*} method).
   */
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
    // Default vector for projectiles (z=forward):
    Vec3d projectileVelocity = new Vec3d(0, 0, 1);

    final RigidBody<?, ?> shipBody = physics.getPhysicsSpace().getBinIndex().getRigidBody(attacker);

    // Mechanics that don't fire a projectile short-circuit to ship-center
    // with zero velocity — see CENTERED_NO_PROJECTILE Javadoc for which.
    if (CENTERED_NO_PROJECTILE.contains(weaponFlag)) {
      return new ActionPosition(new Vec3d(shipBody.position), new Vec3d(0, 0, 0));
    }

    // Step 1: Scale the velocity based on weapon type, weapon level and ship type
    // TODO: Look these settings up in SettingsSystem
    if (weaponFlag == ConsumableTypeId.FIRETHOR) {
      projectileVelocity.addLocal(0, 0, 50);
    } else {
      throw new AssertionError("Action :" + weaponFlag + " not recognized");
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
    if (weaponFlag == ConsumableTypeId.FIRETHOR) {
      projectilePosition.addLocal(0, 0, engineConfigSystem.get().thorRadius());
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
  public static final class Action {

    private final EntityId owner;
    private final ConsumableTypeId action;

    public Action(final EntityId owner, final ConsumableTypeId action) {
      this.owner = owner;
      this.action = action;
    }

    public EntityId getOwner() {
      return owner;
    }

    public ConsumableTypeId getAction() {
      return action;
    }
  }
}
