// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems;

import com.simsilica.es.ComponentFilter;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.es.common.Decay;
import com.simsilica.es.filter.FieldFilter;
import com.simsilica.mblock.phys.MBlockShape;
import com.simsilica.mphys.AbstractBody;
import com.simsilica.mphys.Contact;
import com.simsilica.mphys.ContactListener;
import com.simsilica.mphys.PhysicsSpace;
import com.simsilica.mphys.RigidBody;
import com.simsilica.sim.SimTime;
import infinity.es.CollisionCategory;
import infinity.es.PrizeType;
import infinity.es.PrizeTypes;
import infinity.es.ship.PlayerShip;
import infinity.server.chat.InfinityChatHostedService;
import infinity.sim.AccessLevel;
import infinity.sim.CollisionFilters;
import infinity.sim.CommandTriFunction;
import infinity.sim.GameSounds;
import infinity.systems.ship.EnergySystem;
import infinity.systems.ship.WarpSystem;
import infinity.systems.ship.applier.AntiWarpPrizeApplier;
import infinity.systems.ship.applier.BombPrizeApplier;
import infinity.systems.ship.applier.BouncingBulletsPrizeApplier;
import infinity.systems.ship.applier.BrickPrizeApplier;
import infinity.systems.ship.applier.BurstPrizeApplier;
import infinity.systems.ship.applier.CloakPrizeApplier;
import infinity.systems.ship.applier.CompositePrizeApplier;
import infinity.systems.ship.applier.DecoyPrizeApplier;
import infinity.systems.ship.applier.DudPrizeApplier;
import infinity.systems.ship.applier.EnergyPrizeApplier;
import infinity.systems.ship.applier.GluePrizeApplier;
import infinity.systems.ship.applier.GunPrizeApplier;
import infinity.systems.ship.applier.MinePrizeApplier;
import infinity.systems.ship.applier.MultiFirePrizeApplier;
import infinity.systems.ship.applier.MultiPrizePrizeApplier;
import infinity.systems.ship.applier.PortalPrizeApplier;
import infinity.systems.ship.applier.PrizeApplier;
import infinity.systems.ship.applier.PrizeApplierContext;
import infinity.systems.ship.applier.ProximityPrizeApplier;
import infinity.systems.ship.applier.QuickChargePrizeApplier;
import infinity.systems.ship.applier.RechargePrizeApplier;
import infinity.systems.ship.applier.RepelPrizeApplier;
import infinity.systems.ship.applier.RocketPrizeApplier;
import infinity.systems.ship.applier.RotationPrizeApplier;
import infinity.systems.ship.applier.ShieldsPrizeApplier;
import infinity.systems.ship.applier.ShrapnelPrizeApplier;
import infinity.systems.ship.applier.StealthPrizeApplier;
import infinity.systems.ship.applier.SuperPrizeApplier;
import infinity.systems.ship.applier.ThorPrizeApplier;
import infinity.systems.ship.applier.ThrusterPrizeApplier;
import infinity.systems.ship.applier.TopSpeedPrizeApplier;
import infinity.systems.ship.applier.WarpPrizeApplier;
import infinity.systems.ship.applier.XRadarPrizeApplier;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Ship-vs-prize {@link ContactListener} + applier dispatch table; admin {@code ~prize} command. See ADR 0003 Channel C. */
public class PrizeConsumptionSystem extends BaseInfinitySystem
    implements ContactListener<EntityId, MBlockShape> {

  static Logger log = LoggerFactory.getLogger(PrizeConsumptionSystem.class);

  private final PhysicsSpace<EntityId, MBlockShape> phys;
  private final Pattern grantPrizeCommand = Pattern.compile("\\~prize\\s+(\\w+)");

  private EntityData ed;
  /**
   * Registry of prize-type-name → applier. One entry per Subspace prize type,
   * with composite appliers wired for {@code BOMB} (bomb+mine) and
   * {@code ALLWEAPONS} (bomb+burst+bullet+mine). Stub appliers throw
   * {@link UnsupportedOperationException}; the catch in
   * {@link #applyPrizeByName} downgrades that to a log warning so unimplemented
   * prize types degrade to a visible no-op rather than crashing the contact loop.
   */
  private Map<String, PrizeApplier> appliers;
  private PrizeApplierContext applierContext;
  private EntitySet ships;
  private EntitySet prizes;
  private SimTime ourTime;

  public PrizeConsumptionSystem(final PhysicsSpace<EntityId, MBlockShape> phys) {
    this.phys = phys;
  }

  @Override
  protected void initialize() {
    ed = requireSystem(EntityData.class);

    final ComponentFilter<?> shipColliderFilter =
        FieldFilter.create(
            CollisionCategory.class, "filter", CollisionFilters.FILTER_CATEGORY_DYNAMIC_PLAYERS);
    final ComponentFilter<?> prizeColliderFilter =
        FieldFilter.create(
            CollisionCategory.class, "filter", CollisionFilters.FILTER_CATEGORY_DYNAMIC_MAPOBJECTS);
    ships = ed.getEntities(shipColliderFilter, PlayerShip.class);
    prizes = ed.getEntities(prizeColliderFilter, PrizeType.class);

    final EnergySystem energySystem = requireSystem(EnergySystem.class);
    final WarpSystem warpSystem = requireSystem(WarpSystem.class);
    applierContext = new PrizeApplierContext(ed, energySystem, warpSystem);

    final BombPrizeApplier bomb = new BombPrizeApplier();
    final BurstPrizeApplier burst = new BurstPrizeApplier();
    final GunPrizeApplier bullet = new GunPrizeApplier();
    final MinePrizeApplier mine = new MinePrizeApplier();
    appliers = new HashMap<>();
    appliers.put(PrizeTypes.ALLWEAPONS, new CompositePrizeApplier(bomb, burst, bullet, mine));
    appliers.put(PrizeTypes.ANTIWARP, new AntiWarpPrizeApplier());
    appliers.put(PrizeTypes.BOMB, new CompositePrizeApplier(bomb, mine));
    appliers.put(PrizeTypes.BOUNCINGBULLETS, new BouncingBulletsPrizeApplier());
    appliers.put(PrizeTypes.BRICK, new BrickPrizeApplier());
    appliers.put(PrizeTypes.BURST, burst);
    appliers.put(PrizeTypes.CLOAK, new CloakPrizeApplier());
    appliers.put(PrizeTypes.DECOY, new DecoyPrizeApplier());
    appliers.put(PrizeTypes.DUD, new DudPrizeApplier());
    appliers.put(PrizeTypes.ENERGY, new EnergyPrizeApplier());
    appliers.put(PrizeTypes.GLUE, new GluePrizeApplier());
    appliers.put(PrizeTypes.GUN, bullet);
    appliers.put(PrizeTypes.MULTIFIRE, new MultiFirePrizeApplier());
    appliers.put(PrizeTypes.MULTIPRIZE, new MultiPrizePrizeApplier());
    appliers.put(PrizeTypes.PORTAL, new PortalPrizeApplier());
    appliers.put(PrizeTypes.PROXIMITY, new ProximityPrizeApplier());
    appliers.put(PrizeTypes.QUICKCHARGE, new QuickChargePrizeApplier());
    appliers.put(PrizeTypes.RECHARGE, new RechargePrizeApplier());
    appliers.put(PrizeTypes.REPEL, new RepelPrizeApplier());
    appliers.put(PrizeTypes.ROCKET, new RocketPrizeApplier());
    appliers.put(PrizeTypes.ROTATION, new RotationPrizeApplier());
    appliers.put(PrizeTypes.SHIELDS, new ShieldsPrizeApplier());
    appliers.put(PrizeTypes.SHRAPNEL, new ShrapnelPrizeApplier());
    appliers.put(PrizeTypes.STEALTH, new StealthPrizeApplier());
    appliers.put(PrizeTypes.SUPER, new SuperPrizeApplier());
    appliers.put(PrizeTypes.THOR, new ThorPrizeApplier());
    appliers.put(PrizeTypes.THRUSTER, new ThrusterPrizeApplier());
    appliers.put(PrizeTypes.TOPSPEED, new TopSpeedPrizeApplier());
    appliers.put(PrizeTypes.WARP, new WarpPrizeApplier());
    appliers.put(PrizeTypes.XRADAR, new XRadarPrizeApplier());

    // Register AFTER WeaponsImpactSystem per ContactSystem class Javadoc — kill-credit before
    // prize-consumption side-effects on the same contact frame.
    requireSystem(ContactSystem.class).addListener(this);

    getSystem(InfinityChatHostedService.class)
        .registerPatternTriConsumer(
            grantPrizeCommand,
            "Grant a prize to your avatar (case-insensitive name match): ~prize <name>",
            new CommandTriFunction<>(AccessLevel.PLAYER_LEVEL, this::commandGrantPrize));
  }

  @Override
  protected void terminate() {
    prizes.release();
    prizes = null;
    ships.release();
    ships = null;
    requireSystem(ContactSystem.class).removeListener(this);
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
    this.ourTime = time;
    ships.applyChanges();
    prizes.applyChanges();
  }

  /** Admin/test command: {@code ~prize <name>} — grants one prize to the issuing avatar. */
  public String commandGrantPrize(
      final EntityId entityId, final EntityId avatarId, final Matcher matcher) {
    final String requested = matcher.group(1);
    final String matched = appliers.keySet().stream()
        .filter(k -> k.equalsIgnoreCase(requested))
        .findFirst()
        .orElse(null);
    if (matched == null) {
      return "Unknown prize: " + requested + ". Known: " + appliers.keySet();
    }
    applyPrizeByName(matched, avatarId);
    return "Granted prize: " + matched + " to avatar " + avatarId;
  }

  @Override
  public void newContact(final Contact contact) {
    final RigidBody<EntityId, MBlockShape> body1 = contact.body1;
    final AbstractBody<EntityId, MBlockShape> body2 = contact.body2;

    if (!(body2 instanceof RigidBody)) {
      return;
    }
    final EntityId idOne = body1.id;
    final EntityId idTwo = body2.id;

    final EntityId prizeId;
    final EntityId shipId;
    if (prizes.containsId(idTwo) && ships.containsId(idOne)) {
      prizeId = idTwo;
      shipId = idOne;
    } else if (prizes.containsId(idOne) && ships.containsId(idTwo)) {
      prizeId = idOne;
      shipId = idTwo;
    } else {
      return;
    }

    GameSounds.createPrizeSound(ed, ourTime.getTime(), shipId, body1.position, phys);

    final PrizeType pt = prizes.getEntity(prizeId).get(PrizeType.class);
    applyPrizeByName(pt.getTypeName(ed), shipId);
    // Defer entity removal to the next-tick Decay reaper: removeEntity here would clear
    // BodyPosition mid-integrate while the mphys body lives on until the next binManager
    // pass — BodyPositionPublisher.update would then see a null BodyPosition (bot-ai-v3 B9).
    final long now = ourTime.getTime();
    ed.setComponent(prizeId, new Decay(now, now));
    contact.disable();
  }

  /**
   * Single applier-dispatch path shared by the collision-driven pickup and the
   * {@code ~prize} admin command. Centralizes logging, null-applier handling,
   * and stub-applier {@link UnsupportedOperationException} catch.
   */
  private void applyPrizeByName(final String name, final EntityId ship) {
    log.info("Ship {} picked up prize: {}", ship, name);
    final PrizeApplier applier = appliers.get(name);
    if (applier == null) {
      log.warn("Prize type {} has no registered applier; skipping", name);
      return;
    }
    try {
      applier.apply(ship, applierContext);
    } catch (final UnsupportedOperationException e) {
      if (log.isWarnEnabled()) {
        log.warn("Prize type {} not yet implemented: {}", name, e.getMessage());
      }
    }
  }
}
