// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems;

import com.simsilica.es.ComponentFilter;
import com.simsilica.es.Entity;
import com.simsilica.es.EntityComponent;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.es.common.Decay;
import com.simsilica.es.filter.FieldFilter;
import com.simsilica.ext.mphys.SpawnPosition;
import com.simsilica.sim.AbstractGameSystem;
import com.simsilica.sim.SimTime;
import infinity.es.Captain;
import infinity.es.Frequency;
import infinity.es.PrizeType;
import infinity.es.PrizeWeightsOverride;
import infinity.es.Spawner;
import infinity.es.arena.ArenaId;
import infinity.es.arena.ArenaMap;
import infinity.es.ship.BounceRestitution;
import infinity.es.ship.DragFactor;
import infinity.es.ship.Energy;
import infinity.es.ship.EnergyMax;
import infinity.es.ship.Health;
import infinity.es.ship.Player;
import infinity.es.ship.RadarRange;
import infinity.es.ship.Recharge;
import infinity.es.ship.RechargeMax;
import infinity.es.ship.Rotation;
import infinity.es.ship.RotationMax;
import infinity.es.ship.ShipType;
import infinity.es.ship.Speed;
import infinity.es.ship.SpeedMax;
import infinity.es.ship.Thrust;
import infinity.es.ship.ThrustMax;
import infinity.es.ship.TurnResponsiveness;
import infinity.es.ship.actions.Brick;
import infinity.es.ship.actions.BrickMax;
import infinity.es.ship.actions.Burst;
import infinity.es.ship.actions.BurstMax;
import infinity.es.ship.actions.Decoy;
import infinity.es.ship.actions.DecoyMax;
import infinity.es.ship.actions.Portal;
import infinity.es.ship.actions.PortalMax;
import infinity.es.ship.actions.Repel;
import infinity.es.ship.actions.RepelMax;
import infinity.es.ship.actions.Rocket;
import infinity.es.ship.actions.RocketMax;
import infinity.es.ship.actions.ThorCurrentCount;
import infinity.es.ship.actions.ThorMaxCount;
import infinity.es.ship.weapons.BombCost;
import infinity.es.ship.weapons.BombCurrentLevel;
import infinity.es.ship.weapons.BombMaxLevel;
import infinity.es.ship.weapons.BulletCost;
import infinity.es.ship.weapons.BulletCurrentLevel;
import infinity.es.ship.weapons.BulletMaxLevel;
import infinity.es.ship.weapons.MineCost;
import infinity.es.ship.weapons.MineCurrentLevel;
import infinity.es.ship.weapons.MineMaxLevel;
import infinity.server.chat.InfinityChatHostedService;
import infinity.sim.AccessLevel;
import infinity.sim.ChatHostedPoster;
import infinity.sim.CommandTriFunction;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Diagnostic chat commands ({@code ~check*}) that walk live ECS state and
 * report invariants. Read-only — no entity mutation, no side effects on the
 * running session. Each command catches a specific class of regression we
 * worry about during refactors:
 *
 * <ul>
 *   <li>{@code ~checkships} — Pattern-4 spawn-projection coverage per ship
 *       (every ship should have Energy/EnergyMax/Recharge/RechargeMax/Rotation/
 *       RotationMax/Thrust/ThrustMax/Speed/SpeedMax + weapon current/max pairs).
 *   <li>{@code ~checkprizes} — prize-spawner DSL wiring (Spawner config,
 *       PrizeWeightsOverride sparse merge) and Decay projection from
 *       {@link Spawner#getSpawnedDecayMillis()} onto each spawned prize.
 *   <li>{@code ~checkarenas} — loaded arena lifecycle. Lists each ArenaId
 *       entity, its ArenaMap, and how many spawners / ships sit inside.
 *   <li>{@code ~checkdecay} — sweeps every Decay-tagged entity, groups by
 *       component fingerprint, flags deadlines in the past (reaper lag) or
 *       absurdly far future (likely bug). Surfaces violations of
 *       {@code .claude/rules/decay-ttl.md}.
 *   <li>{@code ~checkcaptains} — verifies the captains EntitySet agrees with
 *       {@link AvatarSystem#isCaptain(EntityId)}. Catches drift in the
 *       AvatarSystem.update applyChanges path.
 * </ul>
 */
public class ChecksSystem extends AbstractGameSystem {

  static final Logger log = LoggerFactory.getLogger(ChecksSystem.class);

  private final Pattern checkShipsCommand = Pattern.compile("\\~checkships");
  /** {@code ~ship} = your avatar; {@code ~ship 17} = ship by EntityId. */
  private final Pattern checkShipCommand = Pattern.compile("\\~ship(?:\\s+(\\d+))?");
  private final Pattern checkPrizesCommand = Pattern.compile("\\~checkprizes");
  private final Pattern checkArenasCommand = Pattern.compile("\\~checkarenas");
  private final Pattern checkDecayCommand = Pattern.compile("\\~checkdecay");
  private final Pattern checkCaptainsCommand = Pattern.compile("\\~checkcaptains");

  private EntityData ed;
  private EntitySet ships;
  private EntitySet prizeSpawners;
  private EntitySet prizes;
  private EntitySet arenas;
  private EntitySet decayingEntities;
  private EntitySet captains;
  private InfinityTimeSystem timeSystem;
  private AvatarSystem avatarSystem;

  @Override
  protected void initialize() {
    ed = getSystem(EntityData.class);
    timeSystem = getSystem(InfinityTimeSystem.class);
    avatarSystem = getSystem(AvatarSystem.class);

    ships = ed.getEntities(ShipType.class, Player.class);
    final ComponentFilter<?> prizeSpawnerFilter =
        FieldFilter.create(Spawner.class, "type", Spawner.SpawnType.Prizes);
    prizeSpawners =
        ed.getEntities(prizeSpawnerFilter, Spawner.class, SpawnPosition.class);
    prizes = ed.getEntities(PrizeType.class);
    arenas = ed.getEntities(ArenaId.class, ArenaMap.class);
    decayingEntities = ed.getEntities(Decay.class);
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
        checkPrizesCommand,
        "~checkprizes — audits prize spawners and the Decay component on each live prize",
        new CommandTriFunction<>(AccessLevel.PLAYER_LEVEL, this::checkPrizes));
    chat.registerPatternTriConsumer(
        checkArenasCommand,
        "~checkarenas — audits loaded arenas, their maps, and per-arena entity counts",
        new CommandTriFunction<>(AccessLevel.PLAYER_LEVEL, this::checkArenas));
    chat.registerPatternTriConsumer(
        checkDecayCommand,
        "~checkdecay — audits all Decay-tracked entities; flags expired or absurd-future deadlines",
        new CommandTriFunction<>(AccessLevel.PLAYER_LEVEL, this::checkDecay));
    chat.registerPatternTriConsumer(
        checkCaptainsCommand,
        "~checkcaptains — verifies AvatarSystem.isCaptain agrees with the Captain EntitySet",
        new CommandTriFunction<>(AccessLevel.PLAYER_LEVEL, this::checkCaptains));
  }

  @Override
  protected void terminate() {
    ships.release();
    prizeSpawners.release();
    prizes.release();
    arenas.release();
    decayingEntities.release();
    captains.release();
    ships = null;
    prizeSpawners = null;
    prizes = null;
    arenas = null;
    decayingEntities = null;
    captains = null;
  }

  @Override
  public void update(final SimTime tpf) {
    ships.applyChanges();
    prizeSpawners.applyChanges();
    prizes.applyChanges();
    arenas.applyChanges();
    decayingEntities.applyChanges();
    captains.applyChanges();
  }

  @Override
  public void start() {
    // Nothing to do
  }

  @Override
  public void stop() {
    // Nothing to do
  }

  /* ---------------------------------------------------------------- */
  /* Command handlers                                                 */
  /* ---------------------------------------------------------------- */

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

      // Engine stats — Pattern 4 baseline (missing here = projection bug).
      final int missing = appendComponentMatrix(sb, id,
          Energy.class, EnergyMax.class,
          Recharge.class, RechargeMax.class,
          Rotation.class, RotationMax.class,
          Thrust.class, ThrustMax.class,
          Speed.class, SpeedMax.class,
          BombCurrentLevel.class, BombMaxLevel.class,
          BulletCurrentLevel.class, BulletMaxLevel.class,
          MineCurrentLevel.class, MineMaxLevel.class,
          BurstMax.class, ThorMaxCount.class);
      missingTotal += missing;
      sb.append("  ").append(missing == 0 ? "engine OK — all 18 components present" : "engine MISSING " + missing).append('\n');

      // Inventory — absence is allowed (per-ship `*Max 0` = ship not allowed
      // that prize type), so we just report present/absent per pair without
      // contributing to the failure tally.
      sb.append("  inventory:");
      appendInventory(sb, id, "repel", Repel.class, RepelMax.class);
      appendInventory(sb, id, "burst", Burst.class, BurstMax.class);
      appendInventory(sb, id, "thor", ThorCurrentCount.class, ThorMaxCount.class);
      appendInventory(sb, id, "brick", Brick.class, BrickMax.class);
      appendInventory(sb, id, "decoy", Decoy.class, DecoyMax.class);
      appendInventory(sb, id, "rocket", Rocket.class, RocketMax.class);
      appendInventory(sb, id, "portal", Portal.class, PortalMax.class);
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

    // Engine — capability stat triples (current/max), live pool (Energy/Health),
    // converted-unit doubles (Recharge/Rotation in per-sec / rad-sec).
    sb.append("  energy: ");
    appendIntPair(sb, target, "energy", Energy.class, EnergyMax.class);
    appendInt(sb, target, "health", Health.class);
    appendDoublePair(sb, target, "recharge", Recharge.class, RechargeMax.class);
    sb.append('\n');

    sb.append("  movement:");
    appendIntPair(sb, target, "thrust", Thrust.class, ThrustMax.class);
    appendIntPair(sb, target, "speed", Speed.class, SpeedMax.class);
    appendDoublePair(sb, target, "rotation", Rotation.class, RotationMax.class);
    sb.append('\n');

    sb.append("  feel:");
    appendDouble(sb, target, "drag", DragFactor.class);
    appendDouble(sb, target, "turnResp", TurnResponsiveness.class);
    appendDouble(sb, target, "bounce", BounceRestitution.class);
    appendDouble(sb, target, "radar", RadarRange.class);
    sb.append('\n');

    sb.append("  weapons:");
    appendWeapon(sb, target, "bomb", BombCurrentLevel.class, BombMaxLevel.class, BombCost.class);
    appendWeapon(sb, target, "bullet", BulletCurrentLevel.class, BulletMaxLevel.class, BulletCost.class);
    appendWeapon(sb, target, "mine", MineCurrentLevel.class, MineMaxLevel.class, MineCost.class);
    sb.append('\n');

    sb.append("  inventory:");
    appendInventoryWithCounts(sb, target, "repel", Repel.class, RepelMax.class);
    appendInventoryWithCounts(sb, target, "burst", Burst.class, BurstMax.class);
    appendInventoryWithCounts(sb, target, "thor", ThorCurrentCount.class, ThorMaxCount.class);
    appendInventoryWithCounts(sb, target, "brick", Brick.class, BrickMax.class);
    appendInventoryWithCounts(sb, target, "decoy", Decoy.class, DecoyMax.class);
    appendInventoryWithCounts(sb, target, "rocket", Rocket.class, RocketMax.class);
    appendInventoryWithCounts(sb, target, "portal", Portal.class, PortalMax.class);
    sb.append('\n');

    return sb.toString();
  }

  /* ---------------------------------------------------------------- */
  /* ~ship formatters                                                 */
  /* ---------------------------------------------------------------- */
  // Each helper reads one component (or a pair) and appends "label=value"
  // (or "label=-" for absent). Reflection-via-known-getters keeps the dump
  // local to this file — adding a new component just means adding one line
  // to checkShip + one helper call.

  private void appendInt(
      final StringBuilder sb,
      final EntityId id,
      final String label,
      final Class<? extends EntityComponent> c) {
    final EntityComponent comp = ed.getComponent(id, c);
    sb.append(' ').append(label).append('=');
    if (comp == null) {
      sb.append('-');
    } else if (comp instanceof Health h) {
      sb.append(h.getHealth());
    } else if (comp instanceof Energy e) {
      sb.append(e.getEnergy());
    } else {
      sb.append('?');
    }
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
    } else if (comp instanceof DragFactor d) {
      sb.append(formatDouble(d.getFactor()));
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

  private void appendIntPair(
      final StringBuilder sb,
      final EntityId id,
      final String label,
      final Class<? extends EntityComponent> curr,
      final Class<? extends EntityComponent> max) {
    final EntityComponent c = ed.getComponent(id, curr);
    final EntityComponent m = ed.getComponent(id, max);
    sb.append(' ').append(label).append('=');
    sb.append(intValue(c)).append('/').append(intValue(m));
  }

  private void appendDoublePair(
      final StringBuilder sb,
      final EntityId id,
      final String label,
      final Class<? extends EntityComponent> curr,
      final Class<? extends EntityComponent> max) {
    final EntityComponent c = ed.getComponent(id, curr);
    final EntityComponent m = ed.getComponent(id, max);
    sb.append(' ').append(label).append('=');
    sb.append(doubleValue(c)).append('/').append(doubleValue(m));
  }

  private void appendWeapon(
      final StringBuilder sb,
      final EntityId id,
      final String label,
      final Class<? extends EntityComponent> curr,
      final Class<? extends EntityComponent> max,
      final Class<? extends EntityComponent> cost) {
    final EntityComponent c = ed.getComponent(id, curr);
    final EntityComponent m = ed.getComponent(id, max);
    final EntityComponent co = ed.getComponent(id, cost);
    sb.append(' ').append(label).append('=');
    if (c == null && m == null && co == null) {
      sb.append('-');
      return;
    }
    sb.append(weaponLevel(c)).append('/').append(weaponLevel(m));
    if (co instanceof BombCost bc) {
      sb.append("(cost=").append(bc.getCost()).append(')');
    } else if (co instanceof BulletCost gc) {
      sb.append("(cost=").append(gc.getCost()).append(')');
    } else if (co instanceof MineCost mc) {
      sb.append("(cost=").append(mc.getCost()).append(')');
    }
  }

  /**
   * Inventory pair with values. {@code repel=10/20} when projected,
   * {@code repel=-} when neither component is present (ship disallows
   * repels), {@code repel=?/N} or {@code repel=N/?} for half-pair
   * projection bugs.
   */
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

  /** Read getCount() across the whole inventory family + Energy/etc. as a flat int. */
  private static String intValue(final EntityComponent c) {
    if (c == null) return "?";
    if (c instanceof Repel r) return Integer.toString(r.getCount());
    if (c instanceof RepelMax r) return Integer.toString(r.getCount());
    if (c instanceof Burst b) return Integer.toString(b.getCount());
    if (c instanceof BurstMax b) return Integer.toString(b.getCount());
    if (c instanceof ThorCurrentCount t) return Integer.toString(t.getCount());
    if (c instanceof ThorMaxCount t) return Integer.toString(t.getCount());
    if (c instanceof Brick b) return Integer.toString(b.getCount());
    if (c instanceof BrickMax b) return Integer.toString(b.getCount());
    if (c instanceof Decoy d) return Integer.toString(d.getCount());
    if (c instanceof DecoyMax d) return Integer.toString(d.getCount());
    if (c instanceof Rocket r) return Integer.toString(r.getCount());
    if (c instanceof RocketMax r) return Integer.toString(r.getCount());
    if (c instanceof Portal p) return Integer.toString(p.getCount());
    if (c instanceof PortalMax p) return Integer.toString(p.getCount());
    if (c instanceof Energy e) return Integer.toString(e.getEnergy());
    if (c instanceof EnergyMax e) return Integer.toString(e.getMaxEnergy());
    if (c instanceof Thrust t) return Integer.toString(t.getThrust());
    if (c instanceof ThrustMax t) return Integer.toString(t.getThrustMax());
    if (c instanceof Speed s) return Integer.toString(s.getSpeed());
    if (c instanceof SpeedMax s) return Integer.toString(s.getSpeedMax());
    return "?";
  }

  private static String doubleValue(final EntityComponent c) {
    if (c == null) return "?";
    if (c instanceof Recharge r) return formatDouble(r.getRechargePerSecond());
    if (c instanceof RechargeMax r) return formatDouble(r.getMaxRechargePerSecond());
    if (c instanceof Rotation r) return formatDouble(r.getRadSec());
    if (c instanceof RotationMax r) return formatDouble(r.getRadSecMax());
    return "?";
  }

  private static String weaponLevel(final EntityComponent c) {
    if (c == null) return "?";
    if (c instanceof BombCurrentLevel b) return b.getLevel().name();
    if (c instanceof BombMaxLevel b) return b.getLevel().name();
    if (c instanceof BulletCurrentLevel g) return g.getLevel().name();
    if (c instanceof BulletMaxLevel g) return g.getLevel().name();
    if (c instanceof MineCurrentLevel m) return m.getLevel().name();
    if (c instanceof MineMaxLevel m) return m.getLevel().name();
    return "?";
  }

  private static String formatDouble(final double d) {
    return String.format(java.util.Locale.ROOT, "%.2f", d);
  }

  /**
   * Append a {@code label=+|-} marker to {@code sb} based on whether both the
   * current-counter and the {@code *Max} cap components are present on the
   * ship. {@code +} = both present (ship can use this prize type),
   * {@code -} = both absent (ship not configured for it). A half-pair is a
   * spawn-projection bug and surfaces as {@code label=?}.
   */
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

  @SuppressWarnings("PMD.UnusedFormalParameter") // CommandTriFunction signature
  private String checkPrizes(
      final EntityId playerEntityId, final EntityId avatarEntityId, final Matcher matcher) {
    prizeSpawners.applyChanges();
    prizes.applyChanges();
    final StringBuilder sb = new StringBuilder("checkprizes: ")
        .append(prizeSpawners.size()).append(" spawner(s), ")
        .append(prizes.size()).append(" prize(s) alive\n");

    // Per-spawner audit
    for (final Entity spawner : prizeSpawners) {
      final EntityId sid = spawner.getId();
      final Spawner s = spawner.get(Spawner.class);
      final ArenaId arenaId = ed.getComponent(sid, ArenaId.class);
      final PrizeWeightsOverride override = ed.getComponent(sid, PrizeWeightsOverride.class);
      sb.append("- spawner[").append(sid.getId()).append("] arena=")
          .append(arenaId == null ? "<none>" : arenaId.getArena())
          .append(" max=").append(s.getMaxCount())
          .append(" intervalMs=").append((long) s.getSpawnInterval())
          .append(" ttlMs=").append(s.getSpawnedDecayMillis())
          .append(" weights=").append(override == null || override.getOverrides().isEmpty()
              ? "arena-default"
              : "override(" + override.getOverrides().size() + " keys)")
          .append('\n');
    }

    // Decay sanity on every alive prize
    final long now = timeSystem.getTime();
    int prizesWithoutDecay = 0;
    int prizesExpired = 0;
    long minRemain = Long.MAX_VALUE;
    long maxRemain = 0L;
    long totalRemain = 0L;
    for (final Entity prize : prizes) {
      final Decay decay = ed.getComponent(prize.getId(), Decay.class);
      if (decay == null) {
        prizesWithoutDecay++;
        continue;
      }
      final long remainNs = decay.getTimeRemaining(now);
      if (remainNs <= 0L) {
        prizesExpired++;
      }
      if (remainNs < minRemain) {
        minRemain = remainNs;
      }
      if (remainNs > maxRemain) {
        maxRemain = remainNs;
      }
      totalRemain += remainNs;
    }
    if (prizes.size() == 0) {
      sb.append("  no prizes alive — TTL stats N/A\n");
    } else {
      final long avgMs = TimeUnit.NANOSECONDS.toMillis(totalRemain / Math.max(1, prizes.size()));
      sb.append("  TTL remaining min=").append(TimeUnit.NANOSECONDS.toMillis(minRemain))
          .append("ms avg=").append(avgMs)
          .append("ms max=").append(TimeUnit.NANOSECONDS.toMillis(maxRemain)).append("ms\n");
    }
    final boolean ok = prizesWithoutDecay == 0 && prizesExpired == 0;
    sb.append(ok ? "PASS" : "FAIL (")
        .append(ok ? "" : prizesWithoutDecay + " no-Decay, " + prizesExpired + " expired)");
    return sb.toString();
  }

  @SuppressWarnings("PMD.UnusedFormalParameter") // CommandTriFunction signature
  private String checkArenas(
      final EntityId playerEntityId, final EntityId avatarEntityId, final Matcher matcher) {
    arenas.applyChanges();
    ships.applyChanges();
    prizeSpawners.applyChanges();
    if (arenas.isEmpty()) {
      return "checkarenas: no arenas loaded";
    }
    // Tally entities by arena name in a single pass each.
    final Map<String, int[]> perArena = new HashMap<>();
    for (final Entity ship : ships) {
      final ArenaId aid = ed.getComponent(ship.getId(), ArenaId.class);
      if (aid != null) {
        perArena.computeIfAbsent(aid.getArena(), k -> new int[2])[0]++;
      }
    }
    for (final Entity sp : prizeSpawners) {
      final ArenaId aid = ed.getComponent(sp.getId(), ArenaId.class);
      if (aid != null) {
        perArena.computeIfAbsent(aid.getArena(), k -> new int[2])[1]++;
      }
    }

    final StringBuilder sb = new StringBuilder("checkarenas: ").append(arenas.size()).append(" arena(s)\n");
    for (final Entity arena : arenas) {
      final ArenaId aid = arena.get(ArenaId.class);
      final ArenaMap map = arena.get(ArenaMap.class);
      final int[] counts = perArena.getOrDefault(aid.getArena(), new int[2]);
      sb.append("- ").append(aid.getArena())
          .append(" entity=").append(arena.getId().getId())
          .append(" map.min=").append(map.getMin())
          .append(" ships=").append(counts[0])
          .append(" spawners=").append(counts[1])
          .append('\n');
    }
    sb.append("PASS");
    return sb.toString();
  }

  @SuppressWarnings("PMD.UnusedFormalParameter") // CommandTriFunction signature
  private String checkDecay(
      final EntityId playerEntityId, final EntityId avatarEntityId, final Matcher matcher) {
    decayingEntities.applyChanges();
    final long now = timeSystem.getTime();
    // Bucket "kind" = a string fingerprint based on which marker components an
    // entity carries. We use a small fixed list of known markers; entities with
    // none of them get bucketed as "other".
    final Map<String, int[]> byKind = new HashMap<>(); // [count, totalRemainNs scaled to ms-divisible]
    int expired = 0;
    int absurd = 0; // > 1 hour
    final long absurdThresholdNs = TimeUnit.HOURS.toNanos(1);
    for (final Entity e : decayingEntities) {
      final Decay decay = e.get(Decay.class);
      final long remainNs = decay.getTimeRemaining(now);
      if (remainNs <= 0L) {
        expired++;
      }
      if (remainNs > absurdThresholdNs) {
        absurd++;
      }
      final String kind = classify(e.getId());
      final int[] bucket = byKind.computeIfAbsent(kind, k -> new int[]{0, 0});
      bucket[0]++;
      bucket[1] += (int) Math.min(Integer.MAX_VALUE, TimeUnit.NANOSECONDS.toMillis(remainNs));
    }

    final StringBuilder sb = new StringBuilder("checkdecay: ")
        .append(decayingEntities.size()).append(" entity(ies)\n");
    for (final Map.Entry<String, int[]> entry : byKind.entrySet()) {
      final int[] v = entry.getValue();
      final long avgMs = v[0] == 0 ? 0L : (long) v[1] / v[0];
      sb.append("- ").append(entry.getKey()).append(": ").append(v[0])
          .append(" (avg TTL ").append(avgMs).append("ms)\n");
    }
    sb.append("expired=").append(expired)
        .append(" absurd(>1h)=").append(absurd).append('\n');
    final boolean ok = expired == 0 && absurd == 0;
    sb.append(ok ? "PASS" : "FAIL");
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

  /* ---------------------------------------------------------------- */
  /* Helpers                                                          */
  /* ---------------------------------------------------------------- */

  /**
   * Return the number of components in {@code expected} that are missing on
   * {@code id}, while appending a one-line "Comp1 Comp2-MISSING ..." summary
   * to {@code sb}.
   */
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

  /**
   * Best-effort kind label for a decaying entity. We probe for a small fixed
   * set of marker components rather than a full class scan; that keeps
   * checkdecay cheap and lets unknown kinds fall into the "other" bucket.
   */
  private String classify(final EntityId id) {
    if (ed.getComponent(id, PrizeType.class) != null) {
      return "Prize";
    }
    if (ed.getComponent(id, ShipType.class) != null) {
      return "Ship";
    }
    return "other";
  }
}
