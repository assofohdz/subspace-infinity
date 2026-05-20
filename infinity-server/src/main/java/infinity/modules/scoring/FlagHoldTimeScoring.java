// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.modules.scoring;

import com.simsilica.es.Entity;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.sim.SimTime;
import infinity.config.FlagHoldTimeConfig;
import infinity.es.ChangeTarget;
import infinity.es.Flag;
import infinity.es.FlagOwnership;
import infinity.es.Frequency;
import infinity.es.arena.ArenaId;
import infinity.es.score.TeamScoreChange;
import infinity.es.team.TeamEntity;
import infinity.es.team.TeamFlagHoldTicks;
import infinity.modules.ModuleContext;
import infinity.modules.RoundOutcome;
import infinity.modules.ScoringModule;
import java.util.HashMap;
import java.util.Map;

/**
 * Per-tick: counts owned flags per freq; accumulates {@code count * perSecondPerFlag * tpf}
 * into a float per-freq counter; whenever a counter crosses 1.0 emits a
 * {@link TeamScoreChange} intent for the matching {@link TeamEntity} and bumps
 * {@link TeamFlagHoldTicks} (canonical writer) by the floor delta. {@link TeamFlagHoldTicks}
 * is zeroed on round-end so {@code MostFlagOccupancyWinCondition} sees per-round occupancy.
 *
 * <p>Composes with {@code KillPointsScoring}: both write the same {@code TeamScoreChange} stream,
 * the coordinator sums per-team in one drain pass. {@link TeamFlagHoldTicks} is the dedicated
 * occupancy signal — the win condition reads it instead of the score so kills don't conflate
 * with occupancy.
 */
public final class FlagHoldTimeScoring implements ScoringModule {

  private final EntityData ed;
  private final ArenaId arenaId;
  private final int perSecondPerFlag;
  private final EntitySet ownedFlags;
  private final EntitySet teams;
  private final Map<Integer, Double> accumulatorByFreq = new HashMap<>();

  public FlagHoldTimeScoring(final ModuleContext ctx, final FlagHoldTimeConfig config) {
    this.ed = ctx.ed();
    this.arenaId = ctx.arenaId();
    this.perSecondPerFlag = config.effectivePerSecondPerFlag();
    this.ownedFlags = ed.getEntities(Flag.class, FlagOwnership.class, ArenaId.class);
    this.teams = ed.getEntities(TeamEntity.class, Frequency.class, ArenaId.class);
  }

  @Override
  public void onArenaUnload(final ArenaId unloadedArenaId) {
    ownedFlags.release();
    teams.release();
    accumulatorByFreq.clear();
  }

  @Override
  public void onRoundEnd(
      final ArenaId arenaIdParam, final int roundNumber, final RoundOutcome outcome) {
    accumulatorByFreq.clear();
    teams.applyChanges();
    for (final Entity team : teams) {
      if (arenaId.equals(team.get(ArenaId.class))) {
        ed.setComponent(team.getId(), new TeamFlagHoldTicks(0));
      }
    }
  }

  @Override
  public void tickContributions(final ArenaId arenaIdParam, final SimTime time) {
    if (perSecondPerFlag == 0 || time.getTpf() <= 0.0) {
      return;
    }
    ownedFlags.applyChanges();
    teams.applyChanges();

    final Map<Integer, Integer> ownedCountByFreq = countOwnedFlagsByFreq();
    if (ownedCountByFreq.isEmpty()) {
      return;
    }
    for (final Map.Entry<Integer, Integer> e : ownedCountByFreq.entrySet()) {
      accumulate(e.getKey(), e.getValue(), time.getTpf());
    }
  }

  private Map<Integer, Integer> countOwnedFlagsByFreq() {
    final Map<Integer, Integer> counts = new HashMap<>();
    for (final Entity flag : ownedFlags) {
      if (!arenaId.equals(flag.get(ArenaId.class))) {
        continue;
      }
      final int freq = flag.get(FlagOwnership.class).freq();
      if (freq < 0) {
        continue;
      }
      counts.merge(freq, 1, Integer::sum);
    }
    return counts;
  }

  /** Add {@code flagCount * perSecondPerFlag * tpf} to {@code freq}'s accumulator; emit whole-number deltas. */
  private void accumulate(final int freq, final int flagCount, final double tpf) {
    final double prev = accumulatorByFreq.getOrDefault(freq, 0d);
    final double next = prev + flagCount * perSecondPerFlag * tpf;
    final int wholeDelta = (int) Math.floor(next);
    if (wholeDelta > 0) {
      accumulatorByFreq.put(freq, next - wholeDelta);
      applyDelta(freq, wholeDelta);
    } else {
      accumulatorByFreq.put(freq, next);
    }
  }

  /** Locates the {@link TeamEntity} for {@code freq} in this arena; emits {@link TeamScoreChange} + bumps {@link TeamFlagHoldTicks}. */
  private void applyDelta(final int freq, final int delta) {
    final EntityId teamId = findTeamForFreq(freq);
    if (teamId == null) {
      return;
    }
    final EntityId holder = ed.createEntity();
    ed.setComponents(holder, new ChangeTarget(teamId, teamId), new TeamScoreChange(delta));

    final TeamFlagHoldTicks prev = ed.getComponent(teamId, TeamFlagHoldTicks.class);
    final int prevTicks = prev == null ? 0 : prev.ticks();
    ed.setComponent(teamId, new TeamFlagHoldTicks(prevTicks + delta));
  }

  private EntityId findTeamForFreq(final int freq) {
    for (final Entity team : teams) {
      if (!arenaId.equals(team.get(ArenaId.class))) {
        continue;
      }
      if (team.get(Frequency.class).getFrequency() == freq) {
        return team.getId();
      }
    }
    return null;
  }
}
