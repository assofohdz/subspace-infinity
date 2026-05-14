// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems;

import com.simsilica.es.ComponentFilter;
import com.simsilica.es.Entity;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.es.common.Decay;
import com.simsilica.es.filter.FieldFilter;
import com.simsilica.ext.mphys.SpawnPosition;
import com.simsilica.sim.SimTime;
import infinity.es.PrizeType;
import infinity.es.PrizeWeightsOverride;
import infinity.es.Spawner;
import infinity.es.arena.ArenaId;
import infinity.es.arena.ArenaMap;
import infinity.es.ship.ShipType;
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
 * World-state diagnostic chat commands. Read-only.
 * <ul>
 *   <li>{@code ~checkprizes} — audits Spawner config + Decay projection on
 *       live prizes.
 *   <li>{@code ~checkarenas} — loaded arenas with per-arena ship/spawner
 *       counts.
 *   <li>{@code ~checkdecay} — flags past-deadline (reaper lag) or
 *       absurd-future Decay deadlines; surfaces decay-ttl.md violations.
 * </ul>
 */
public class ChecksWorldSystem extends BaseInfinitySystem {

  static final Logger log = LoggerFactory.getLogger(ChecksWorldSystem.class);

  private final Pattern checkPrizesCommand = Pattern.compile("\\~checkprizes");
  private final Pattern checkArenasCommand = Pattern.compile("\\~checkarenas");
  private final Pattern checkDecayCommand = Pattern.compile("\\~checkdecay");

  private EntityData ed;
  // Read-only copy; ChecksShipsSystem owns its own EntitySet for the ships command family.
  private EntitySet ships;
  private EntitySet prizeSpawners;
  private EntitySet prizes;
  private EntitySet arenas;
  private EntitySet decayingEntities;
  private InfinityTimeSystem timeSystem;

  @Override
  protected void initialize() {
    ed = requireSystem(EntityData.class);
    timeSystem = requireSystem(InfinityTimeSystem.class);

    ships = ed.getEntities(ShipType.class);
    final ComponentFilter<?> prizeSpawnerFilter =
        FieldFilter.create(Spawner.class, "type", Spawner.SpawnType.Prizes);
    prizeSpawners =
        ed.getEntities(prizeSpawnerFilter, Spawner.class, SpawnPosition.class);
    prizes = ed.getEntities(PrizeType.class);
    arenas = ed.getEntities(ArenaId.class, ArenaMap.class);
    decayingEntities = ed.getEntities(Decay.class);

    final ChatHostedPoster chat = requireSystem(InfinityChatHostedService.class);
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
  }

  @Override
  protected void terminate() {
    ships.release();
    prizeSpawners.release();
    prizes.release();
    arenas.release();
    decayingEntities.release();
    ships = null;
    prizeSpawners = null;
    prizes = null;
    arenas = null;
    decayingEntities = null;
  }

  @Override
  public void update(final SimTime tpf) {
    ships.applyChanges();
    prizeSpawners.applyChanges();
    prizes.applyChanges();
    arenas.applyChanges();
    decayingEntities.applyChanges();
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
  private String checkPrizes(
      final EntityId playerEntityId, final EntityId avatarEntityId, final Matcher matcher) {
    prizeSpawners.applyChanges();
    prizes.applyChanges();
    final StringBuilder sb = new StringBuilder("checkprizes: ")
        .append(prizeSpawners.size()).append(" spawner(s), ")
        .append(prizes.size()).append(" prize(s) alive\n");

    appendSpawnerAudit(sb);
    appendPrizeDecayStats(sb);
    return sb.toString();
  }

  private void appendSpawnerAudit(final StringBuilder sb) {
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
  }

  private void appendPrizeDecayStats(final StringBuilder sb) {
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
