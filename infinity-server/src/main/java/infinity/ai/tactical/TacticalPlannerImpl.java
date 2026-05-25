// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.tactical;

import infinity.ai.brain.Blackboard;
import infinity.config.ZoneBotAiConfig;
import java.util.List;
import java.util.function.Supplier;
import javax.annotation.Nullable;

/**
 * Additive-utility planner per ADR-0013: among behaviours whose derived weight clears the adaptive
 * {@code maxWeight × minFraction} floor, picks the candidate with the max {@code intrinsicScore ×
 * weight}, then applies an additive stickiness margin so the running goal is only preempted when a
 * rival beats it by {@link ZoneBotAiConfig#stickinessMargin}. Stateless across bots; zone knobs read
 * live from {@code configSupplier} so reloads take effect on the next cycle.
 */
public final class TacticalPlannerImpl implements TacticalPlanner {

  private final List<Behaviour> behaviours;
  private final Supplier<ZoneBotAiConfig> configSupplier;

  public TacticalPlannerImpl(
      final List<Behaviour> behaviours, final Supplier<ZoneBotAiConfig> configSupplier) {
    this.behaviours = List.copyOf(behaviours);
    this.configSupplier = configSupplier;
  }

  @Override
  @Nullable
  public TacticalGoal select(final Blackboard bb, final ArchetypeConfig archetype) {
    final ZoneBotAiConfig cfg = this.configSupplier.get();
    final TacticalGoal current = bb.currentGoal();
    final Scored scored =
        scan(bb, archetype, enumerationThreshold(archetype, cfg.minFraction()), current);

    final TacticalGoal best = scored.best;
    if (best == null) {
      return null; // nothing offered — caller keeps the running goal
    }
    // Sticky: keep the running goal unless the winner beats it by the margin. Only protects a goal
    // still re-enumerated this cycle (currentScore set); an invalidated goal loses its protection.
    final boolean withinMargin =
        current != null
            && !best.equals(current)
            && scored.currentScore > Double.NEGATIVE_INFINITY
            && scored.bestScore <= scored.currentScore + cfg.stickinessMargin();
    return withinMargin ? current : best;
  }

  /** Scores every eligible behaviour's candidates; tracks the global max + the running goal's score. */
  private Scored scan(
      final Blackboard bb,
      final ArchetypeConfig archetype,
      final double threshold,
      @Nullable final TacticalGoal current) {
    final Scored out = new Scored();
    for (final Behaviour behaviour : this.behaviours) {
      final double weight = archetype.weightOf(behaviour.name());
      if (weight <= 0.0 || weight < threshold) {
        continue; // sub-threshold (or unlisted) — not enumerated this cycle
      }
      for (final TacticalGoal goal : behaviour.enumerate(bb)) {
        out.offer(goal, behaviour.intrinsicScore(goal, bb) * weight, current);
      }
    }
    return out;
  }

  /** Mutable accumulator for the scan: best candidate so far + the running goal's re-scored value. */
  private static final class Scored {
    @Nullable private TacticalGoal best;
    private double bestScore = Double.NEGATIVE_INFINITY;
    private double currentScore = Double.NEGATIVE_INFINITY;

    void offer(final TacticalGoal goal, final double score, @Nullable final TacticalGoal current) {
      if (goal.equals(current)) {
        this.currentScore = score;
      }
      if (score > this.bestScore) {
        this.bestScore = score;
        this.best = goal;
      }
    }
  }

  /** Adaptive floor: {@code maxWeight × minFraction}; {@code 0} when the archetype lists nothing. */
  private double enumerationThreshold(final ArchetypeConfig archetype, final double minFraction) {
    double max = 0.0;
    for (final double w : archetype.behaviourWeights().values()) {
      if (w > max) {
        max = w;
      }
    }
    return max * minFraction;
  }
}
