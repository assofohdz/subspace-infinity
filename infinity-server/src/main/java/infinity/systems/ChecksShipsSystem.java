// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems;

import com.simsilica.es.Entity;
import com.simsilica.es.EntityComponent;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.sim.AbstractGameSystem;
import com.simsilica.sim.SimTime;
import infinity.es.Captain;
import infinity.es.Frequency;
import infinity.es.arena.ArenaId;
import infinity.es.ship.BounceRestitution;
import infinity.es.ship.Energy;
import infinity.es.ship.EnergyStats;
import infinity.es.ship.LinearDamping;
import infinity.es.ship.Player;
import infinity.es.ship.RadarRange;
import infinity.es.ship.Rotation;
import infinity.es.ship.RotationStats;
import infinity.es.ship.ShipType;
import infinity.es.ship.Speed;
import infinity.es.ship.SpeedStats;
import infinity.es.ship.Thrust;
import infinity.es.ship.ThrustStats;
import infinity.es.ship.TurnResponsiveness;
import infinity.es.ship.actions.Brick;
import infinity.es.ship.actions.BrickStats;
import infinity.es.ship.actions.Burst;
import infinity.es.ship.actions.BurstStats;
import infinity.es.ship.actions.Decoy;
import infinity.es.ship.actions.DecoyStats;
import infinity.es.ship.actions.Portal;
import infinity.es.ship.actions.PortalStats;
import infinity.es.ship.actions.Repel;
import infinity.es.ship.actions.RepelStats;
import infinity.es.ship.actions.Rocket;
import infinity.es.ship.actions.RocketStats;
import infinity.es.ship.actions.ThorCurrentCount;
import infinity.es.ship.actions.ThorStats;
import infinity.es.ship.weapons.BombCurrentLevel;
import infinity.es.ship.weapons.BombStats;
import infinity.es.ship.weapons.BulletCurrentLevel;
import infinity.es.ship.weapons.BulletStats;
import infinity.es.ship.weapons.MineCurrentLevel;
import infinity.es.ship.weapons.MineStats;
import infinity.server.chat.InfinityChatHostedService;
import infinity.sim.AccessLevel;
import infinity.sim.ChatHostedPoster;
import infinity.sim.CommandTriFunction;
import java.util.HashMap;
import java.util.Map;
import java.util.function.ToIntFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Ship / avatar diagnostic chat commands. Read-only.
 * <ul>
 *   <li>{@code ~checkships} — Pattern-4 component-coverage audit across
 *       all live ships (Energy/Stats × Rotation/Thrust/Speed + weapon
 *       current/max pairs; missing = projection bug).
 *   <li>{@code ~ship [id]} — deep-dump every projected component on one
 *       ship (default: caller's avatar).
 *   <li>{@code ~checkcaptains} — verifies {@code AvatarSystem.isCaptain}
 *       agrees with the {@link Captain} EntitySet.
 * </ul>
 */
@SuppressWarnings("PMD.CyclomaticComplexity") // dispatcher with many small per-check methods; flattening would just hide the table
public class ChecksShipsSystem extends AbstractGameSystem {

  static final Logger log = LoggerFactory.getLogger(ChecksShipsSystem.class);

  private static final String COST_PREFIX = "(cost=";

  private final Pattern checkShipsCommand = Pattern.compile("\\~checkships");
  /** {@code ~ship} = your avatar; {@code ~ship 17} = ship by EntityId. */
  private final Pattern checkShipCommand = Pattern.compile("\\~ship(?:\\s+(\\d+))?");
  private final Pattern checkCaptainsCommand = Pattern.compile("\\~checkcaptains");

  private EntityData ed;
  private EntitySet ships;
  private EntitySet captains;
  private AvatarSystem avatarSystem;

  @Override
  protected void initialize() {
    ed = getSystem(EntityData.class);
    avatarSystem = getSystem(AvatarSystem.class);

    ships = ed.getEntities(ShipType.class, Player.class);
    captains = ed.getEntities(Captain.class);

    final ChatHostedPoster chat = getSystem(InfinityChatHostedService.class);
    chat.registerPatternTriConsumer(
        checkShipsCommand,
        "~checkships — audits Pattern-4 component coverage on each live ship",
        new CommandTriFunction<>(AccessLevel.PLAYER_LEVEL, this::checkShips));
    chat.registerPatternTriConsumer(
        checkShipCommand,
        "~ship [id] — deep-dump every projected component on a ship (default: your avatar)",
        new CommandTriFunction<>(AccessLevel.PLAYER_LEVEL, this::checkShip));
    chat.registerPatternTriConsumer(
        checkCaptainsCommand,
        "~checkcaptains — verifies AvatarSystem.isCaptain agrees with the Captain EntitySet",
        new CommandTriFunction<>(AccessLevel.PLAYER_LEVEL, this::checkCaptains));
  }

  @Override
  protected void terminate() {
    ships.release();
    captains.release();
    ships = null;
    captains = null;
  }

  @Override
  public void update(final SimTime tpf) {
    ships.applyChanges();
    captains.applyChanges();
  }

  @Override
  public void start() {
    // no-op: lifecycle hook unused; command registration happens in initialize()
  }

  @Override
  public void stop() {
    // no-op: lifecycle hook unused; cleanup happens in terminate()
  }

  @SuppressWarnings("PMD.UnusedFormalParameter") // CommandTriFunction signature
  private String checkShips(
      final EntityId playerEntityId, final EntityId avatarEntityId, final Matcher matcher) {
    ships.applyChanges();
    if (ships.isEmpty()) {
      return "checkships: no ships alive";
    }
    final StringBuilder sb = new StringBuilder("checkships: ").append(ships.size()).append(" ship(s)\n");
    int missingTotal = 0;
    for (final Entity ship : ships) {
      final EntityId id = ship.getId();
      final ShipType type = ship.get(ShipType.class);
      final ArenaId arenaId = ed.getComponent(id, ArenaId.class);
      sb.append("- ship[").append(id.getId()).append("] type=")
          .append(type == null ? "?" : type.getType())
          .append(" arena=")
          .append(arenaId == null ? "<none>" : arenaId.getArena())
          .append('\n');

      // Engine stats — Pattern-4 baseline; missing here = projection bug.
      final int missing = appendComponentMatrix(sb, id,
          Energy.class, EnergyStats.class,
          Rotation.class, RotationStats.class,
          Thrust.class, ThrustStats.class,
          Speed.class, SpeedStats.class,
          BombCurrentLevel.class, BombStats.class,
          BulletCurrentLevel.class, BulletStats.class,
          MineCurrentLevel.class, MineStats.class,
          BurstStats.class, ThorStats.class);
      missingTotal += missing;
      sb.append("  ").append(missing == 0 ? "engine OK — all 16 components present" : "engine MISSING " + missing).append('\n');

      // Inventory absence is allowed (`*Stats.max 0` = disallowed); not counted as failure.
      sb.append("  inventory:");
      appendInventory(sb, id, "repel", Repel.class, RepelStats.class);
      appendInventory(sb, id, "burst", Burst.class, BurstStats.class);
      appendInventory(sb, id, "thor", ThorCurrentCount.class, ThorStats.class);
      appendInventory(sb, id, "brick", Brick.class, BrickStats.class);
      appendInventory(sb, id, "decoy", Decoy.class, DecoyStats.class);
      appendInventory(sb, id, "rocket", Rocket.class, RocketStats.class);
      appendInventory(sb, id, "portal", Portal.class, PortalStats.class);
      sb.append('\n');
    }
    sb.append(missingTotal == 0 ? "PASS" : "FAIL (" + missingTotal + " missing across all ships)");
    return sb.toString();
  }

  @SuppressWarnings("PMD.UnusedFormalParameter") // CommandTriFunction signature
  private String checkShip(
      final EntityId playerEntityId, final EntityId avatarEntityId, final Matcher matcher) {
    // Resolve target — explicit id arg wins, otherwise default to caller's avatar.
    final EntityId target;
    final String idArg = matcher.group(1);
    if (idArg != null) {
      try {
        target = new EntityId(Long.parseLong(idArg));
      } catch (final NumberFormatException ex) {
        return "~ship: bad id '" + idArg + "'";
      }
    } else if (avatarEntityId == null) {
      return "~ship: no avatar to inspect — pass an id (~ship <n>)";
    } else {
      target = avatarEntityId;
    }

    final ShipType type = ed.getComponent(target, ShipType.class);
    if (type == null) {
      return "~ship: entity[" + target.getId() + "] has no ShipType (not a ship)";
    }
    final ArenaId arenaId = ed.getComponent(target, ArenaId.class);
    final Frequency freq = ed.getComponent(target, Frequency.class);

    final StringBuilder sb = new StringBuilder("~ship[").append(target.getId()).append("] type=")
        .append(type.getType())
        .append(" arena=").append(arenaId == null ? "<none>" : arenaId.getArena())
        .append(" freq=").append(freq == null ? "-" : freq.getFrequency())
        .append('\n');

    // Engine — capability stat triples (current/max), live pool (Energy),
    // bundled EnergyStats (cap + recharge tuple).
    appendEnergyLine(sb, target);

    sb.append("  movement:");
    appendThrustLine(sb, target);
    appendSpeedLine(sb, target);
    appendRotationLine(sb, target);
    sb.append('\n');

    sb.append("  feel:");
    appendDouble(sb, target, "linDamp", LinearDamping.class);
    appendDouble(sb, target, "turnResp", TurnResponsiveness.class);
    appendDouble(sb, target, "bounce", BounceRestitution.class);
    appendDouble(sb, target, "radar", RadarRange.class);
    sb.append('\n');

    sb.append("  weapons:");
    appendWeapon(sb, target, "bomb");
    appendWeaponBullet(sb, target);
    appendWeaponMine(sb, target);
    sb.append('\n');

    sb.append("  inventory:");
    appendInventoryWithCounts(sb, target, "repel", Repel.class, RepelStats.class);
    appendBurstInventory(sb, target);
    appendInventoryWithCounts(sb, target, "thor", ThorCurrentCount.class, ThorStats.class);
    appendInventoryWithCounts(sb, target, "brick", Brick.class, BrickStats.class);
    appendInventoryWithCounts(sb, target, "decoy", Decoy.class, DecoyStats.class);
    appendInventoryWithCounts(sb, target, "rocket", Rocket.class, RocketStats.class);
    appendInventoryWithCounts(sb, target, "portal", Portal.class, PortalStats.class);
    sb.append('\n');

    return sb.toString();
  }

  @SuppressWarnings("PMD.UnusedFormalParameter") // CommandTriFunction signature
  private String checkCaptains(
      final EntityId playerEntityId, final EntityId avatarEntityId, final Matcher matcher) {
    captains.applyChanges();
    int agree = 0;
    int disagree = 0;
    for (final Entity e : captains) {
      if (avatarSystem.isCaptain(e.getId())) {
        agree++;
      } else {
        disagree++;
      }
    }
    final StringBuilder sb = new StringBuilder("checkcaptains: ")
        .append(captains.size()).append(" captain(s)\n")
        .append("- AvatarSystem.isCaptain agreement: ").append(agree)
        .append('/').append(captains.size()).append('\n');
    sb.append(disagree == 0 ? "PASS" : "FAIL (" + disagree + " disagreement(s))");
    return sb.toString();
  }

  private void appendEnergyLine(final StringBuilder sb, final EntityId target) {
    final Energy energy = ed.getComponent(target, Energy.class);
    final EnergyStats stats = ed.getComponent(target, EnergyStats.class);
    final String pool = energy == null ? "-" : Integer.toString(energy.getEnergy());
    final String cap = stats == null ? "-" : (stats.max() + "/" + stats.hardMax());
    final String recharge;
    if (stats == null) {
      recharge = "-";
    } else {
      recharge = formatDouble(stats.rechargePerSecond())
          + "/" + formatDouble(stats.rechargeMax());
    }
    sb.append("  energy: pool=").append(pool)
        .append(" cap=").append(cap)
        .append(" recharge=").append(recharge)
        .append('\n');
  }

  private void appendThrustLine(final StringBuilder sb, final EntityId id) {
    final Thrust t = ed.getComponent(id, Thrust.class);
    final ThrustStats stats = ed.getComponent(id, ThrustStats.class);
    final String curr = t == null ? "?" : Integer.toString(t.getThrust());
    final String max = stats == null ? "?" : Integer.toString(stats.max());
    sb.append(" thrust=").append(curr).append('/').append(max);
  }

  private void appendSpeedLine(final StringBuilder sb, final EntityId id) {
    final Speed s = ed.getComponent(id, Speed.class);
    final SpeedStats stats = ed.getComponent(id, SpeedStats.class);
    final String curr = s == null ? "?" : Integer.toString(s.getSpeed());
    final String max = stats == null ? "?" : Integer.toString(stats.max());
    sb.append(" speed=").append(curr).append('/').append(max);
  }

  private void appendRotationLine(final StringBuilder sb, final EntityId id) {
    final Rotation r = ed.getComponent(id, Rotation.class);
    final RotationStats stats = ed.getComponent(id, RotationStats.class);
    final String curr = r == null ? "?" : formatDouble(r.getRadSec());
    final String max = stats == null ? "?" : formatDouble(stats.max());
    sb.append(" rotation=").append(curr).append('/').append(max);
  }

  private void appendDouble(
      final StringBuilder sb,
      final EntityId id,
      final String label,
      final Class<? extends EntityComponent> c) {
    final EntityComponent comp = ed.getComponent(id, c);
    sb.append(' ').append(label).append('=');
    if (comp == null) {
      sb.append('-');
    } else if (comp instanceof LinearDamping ld) {
      sb.append(formatDouble(ld.getDamping()));
    } else if (comp instanceof TurnResponsiveness tr) {
      sb.append(formatDouble(tr.getRate()));
    } else if (comp instanceof BounceRestitution br) {
      sb.append(formatDouble(br.getRestitution()));
    } else if (comp instanceof RadarRange r) {
      sb.append(formatDouble(r.getRange()));
    } else {
      sb.append('?');
    }
  }

  private void appendWeapon(final StringBuilder sb, final EntityId id, final String label) {
    final BombCurrentLevel curr = ed.getComponent(id, BombCurrentLevel.class);
    final BombStats stats = ed.getComponent(id, BombStats.class);
    sb.append(' ').append(label).append('=');
    if (curr == null && stats == null) {
      sb.append('-');
      return;
    }
    sb.append(curr == null ? "?" : curr.getLevel().name())
        .append('/')
        .append(stats == null || stats.max() == null ? "?" : stats.max().name());
    if (stats != null) {
      sb.append(COST_PREFIX).append(stats.fireCostEnergy()).append(')');
    }
  }

  private void appendWeaponBullet(final StringBuilder sb, final EntityId id) {
    final BulletCurrentLevel curr = ed.getComponent(id, BulletCurrentLevel.class);
    final BulletStats stats = ed.getComponent(id, BulletStats.class);
    sb.append(" bullet=");
    if (curr == null && stats == null) {
      sb.append('-');
      return;
    }
    sb.append(curr == null ? "?" : curr.getLevel().name())
        .append('/')
        .append(stats == null || stats.max() == null ? "?" : stats.max().name());
    if (stats != null) {
      sb.append(COST_PREFIX).append(stats.fireCostEnergy()).append(')');
    }
  }

  private void appendWeaponMine(final StringBuilder sb, final EntityId id) {
    final MineCurrentLevel curr = ed.getComponent(id, MineCurrentLevel.class);
    final MineStats stats = ed.getComponent(id, MineStats.class);
    sb.append(" mine=");
    if (curr == null && stats == null) {
      sb.append('-');
      return;
    }
    sb.append(curr == null ? "?" : curr.getLevel().name())
        .append('/')
        .append(stats == null || stats.max() == null ? "?" : stats.max().name());
    if (stats != null) {
      sb.append(COST_PREFIX).append(stats.dropCostEnergy()).append(')');
    }
  }

  private void appendBurstInventory(final StringBuilder sb, final EntityId id) {
    final Burst burst = ed.getComponent(id, Burst.class);
    final BurstStats stats = ed.getComponent(id, BurstStats.class);
    sb.append(" burst=");
    if (burst == null && stats == null) {
      sb.append('-');
    } else {
      sb.append(burst == null ? "?" : burst.getCount())
          .append('/')
          .append(stats == null ? "?" : stats.max());
    }
  }

  private void appendInventoryWithCounts(
      final StringBuilder sb,
      final EntityId id,
      final String label,
      final Class<? extends EntityComponent> curr,
      final Class<? extends EntityComponent> max) {
    final EntityComponent c = ed.getComponent(id, curr);
    final EntityComponent m = ed.getComponent(id, max);
    sb.append(' ').append(label).append('=');
    if (c == null && m == null) {
      sb.append('-');
    } else {
      sb.append(intValue(c)).append('/').append(intValue(m));
    }
  }

  private static final Map<Class<? extends EntityComponent>, ToIntFunction<EntityComponent>> INT_GETTERS = buildIntGetters();

  private static Map<Class<? extends EntityComponent>, ToIntFunction<EntityComponent>> buildIntGetters() {
    final Map<Class<? extends EntityComponent>, ToIntFunction<EntityComponent>> m = new HashMap<>();
    m.put(Repel.class, c -> ((Repel) c).getCount());
    m.put(RepelStats.class, c -> ((RepelStats) c).max());
    m.put(Burst.class, c -> ((Burst) c).getCount());
    m.put(ThorCurrentCount.class, c -> ((ThorCurrentCount) c).getCount());
    m.put(ThorStats.class, c -> ((ThorStats) c).max());
    m.put(Brick.class, c -> ((Brick) c).getCount());
    m.put(BrickStats.class, c -> ((BrickStats) c).max());
    m.put(Decoy.class, c -> ((Decoy) c).getCount());
    m.put(DecoyStats.class, c -> ((DecoyStats) c).max());
    m.put(Rocket.class, c -> ((Rocket) c).getCount());
    m.put(RocketStats.class, c -> ((RocketStats) c).max());
    m.put(Portal.class, c -> ((Portal) c).getCount());
    m.put(PortalStats.class, c -> ((PortalStats) c).max());
    return Map.copyOf(m);
  }

  private static String intValue(final EntityComponent c) {
    if (c == null) return "?";
    final ToIntFunction<EntityComponent> fn = INT_GETTERS.get(c.getClass());
    return fn == null ? "?" : Integer.toString(fn.applyAsInt(c));
  }

  private static String formatDouble(final double d) {
    return String.format(java.util.Locale.ROOT, "%.2f", d);
  }

  /** {@code +} = both present, {@code -} = both absent, {@code ?} = half-pair (projection bug). */
  private void appendInventory(
      final StringBuilder sb,
      final EntityId id,
      final String label,
      final Class<? extends EntityComponent> curr,
      final Class<? extends EntityComponent> max) {
    final boolean hasCurr = ed.getComponent(id, curr) != null;
    final boolean hasMax = ed.getComponent(id, max) != null;
    final String marker;
    if (hasCurr && hasMax) {
      marker = "+";
    } else if (!hasCurr && !hasMax) {
      marker = "-";
    } else {
      marker = "?";
    }
    sb.append(' ').append(label).append('=').append(marker);
  }

  /** Returns the missing-count; appends a one-line per-component "name" or "name-MISSING" summary. */
  @SafeVarargs
  private final int appendComponentMatrix(
      final StringBuilder sb,
      final EntityId id,
      final Class<? extends EntityComponent>... expected) {
    sb.append("  ");
    int missing = 0;
    for (final Class<? extends EntityComponent> c : expected) {
      if (ed.getComponent(id, c) == null) {
        sb.append(c.getSimpleName()).append("-MISSING ");
        missing++;
      } else {
        sb.append(c.getSimpleName()).append(' ');
      }
    }
    sb.append('\n');
    return missing;
  }
}
