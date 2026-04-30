/*
 * Copyright (c) 2018-2026, Asser Fahrenholz
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

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
import infinity.es.PrizeType;
import infinity.es.PrizeWeightsOverride;
import infinity.es.Spawner;
import infinity.es.arena.ArenaId;
import infinity.es.arena.ArenaMap;
import infinity.es.ship.Energy;
import infinity.es.ship.EnergyMax;
import infinity.es.ship.Player;
import infinity.es.ship.Recharge;
import infinity.es.ship.RechargeMax;
import infinity.es.ship.Rotation;
import infinity.es.ship.RotationMax;
import infinity.es.ship.ShipType;
import infinity.es.ship.Speed;
import infinity.es.ship.SpeedMax;
import infinity.es.ship.Thrust;
import infinity.es.ship.ThrustMax;
import infinity.es.ship.actions.BurstMax;
import infinity.es.ship.actions.ThorMaxCount;
import infinity.es.ship.weapons.BombCurrentLevel;
import infinity.es.ship.weapons.BombMaxLevel;
import infinity.es.ship.weapons.GunCurrentLevel;
import infinity.es.ship.weapons.GunMaxLevel;
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

      // Engine stats — Pattern 4 baseline
      final int missing = appendComponentMatrix(sb, id,
          Energy.class, EnergyMax.class,
          Recharge.class, RechargeMax.class,
          Rotation.class, RotationMax.class,
          Thrust.class, ThrustMax.class,
          Speed.class, SpeedMax.class,
          BombCurrentLevel.class, BombMaxLevel.class,
          GunCurrentLevel.class, GunMaxLevel.class,
          MineCurrentLevel.class, MineMaxLevel.class,
          BurstMax.class, ThorMaxCount.class);
      missingTotal += missing;
      sb.append("  ").append(missing == 0 ? "OK — all 18 components present" : "MISSING " + missing).append('\n');
    }
    sb.append(missingTotal == 0 ? "PASS" : "FAIL (" + missingTotal + " missing across all ships)");
    return sb.toString();
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
