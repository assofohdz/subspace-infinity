// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.tactical;

import infinity.ai.brain.Blackboard;
import infinity.config.ZoneBotAiConfig;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Additive-utility planner per ADR-0013: among behaviours whose effective weight clears the adaptive
 * {@code maxWeight × minFraction} floor, picks the candidate with the max {@code intrinsicScore ×
 * weight}, then applies an additive stickiness margin so the running goal is only preempted when a
 * rival beats it by {@link ZoneBotAiConfig#stickinessMargin}. Effective weight is the
 * capability-derived weight times the arena objective's multiplicative {@code behaviourBias}
 * (ADR-0015); role bias is a later increment. Stateless across bots; zone knobs read live from
 * {@code configSupplier} so reloads take effect on the next cycle.
 */
public final class TacticalPlannerImpl implements TacticalPlanner {

  private static final Logger log = LoggerFactory.getLogger(TacticalPlannerImpl.class);

  private final List<Behaviour> behaviours;
  private final Supplier<ZoneBotAiConfig> configSupplier;

  public TacticalPlannerImpl(
      final List<Behaviour> behaviours, final Supplier<ZoneBotAiConfig> configSupplier) {
    this.behaviours = List.copyOf(behaviours);
    this.configSupplier = configSupplier;
  }

  // The flip log below is guarded by the `trace` local (= log.isDebugEnabled()); PMD only recognises
  // a direct isDebugEnabled() guard, so the GuardLogStatement flag here is a false positive.
  @Override
  @Nullable
  @SuppressWarnings("PMD.GuardLogStatement")
  public TacticalGoal select(final Blackboard bb, final ArchetypeConfig archetype) {
    final ZoneBotAiConfig cfg = this.configSupplier.get();
    final Map<String, Double> objectiveBias = objectiveBias(bb);
    final TacticalGoal current = bb.currentGoal();
    final boolean trace = log.isDebugEnabled();
    final Scored scored =
        scan(
            bb,
            archetype,
            objectiveBias,
            enumerationThreshold(archetype, objectiveBias, cfg.minFraction()),
            current,
            trace);

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
    final TacticalGoal chosen = withinMargin ? current : best;
    // Diagnostic (DEBUG-gated): log each goal flip with the competing behaviour scores so the
    // intermittency between hold-position / engage / search can be tuned from real numbers.
    if (trace && !chosen.equals(current)) {
      log.debug("bot {} goal {} -> {} | {}", bb.selfId(), current, chosen, scored.formatScores());
    }
    return chosen;
  }

  /** The arena objective's multiplicative behaviour bias (ADR-0015); identity when no context/objective. */
  private static Map<String, Double> objectiveBias(final Blackboard bb) {
    final BotAiArenaContext ctx = bb.arenaContext();
    if (ctx == null || ctx.objective() == null) {
      return Map.of();
    }
    return ctx.objective().behaviourBias();
  }

  /** Capability-derived weight × objective bias (ADR-0015); {@code 0} stays {@code 0} (hard mute). */
  private static double effectiveWeight(
      final ArchetypeConfig archetype, final Map<String, Double> objectiveBias, final String name) {
    return archetype.weightOf(name) * objectiveBias.getOrDefault(name, 1.0);
  }

  /** Scores every eligible behaviour's candidates; tracks the global max + the running goal's score. */
  private Scored scan(
      final Blackboard bb,
      final ArchetypeConfig archetype,
      final Map<String, Double> objectiveBias,
      final double threshold,
      @Nullable final TacticalGoal current,
      final boolean trace) {
    final Scored out = new Scored(trace);
    for (final Behaviour behaviour : this.behaviours) {
      final double weight = effectiveWeight(archetype, objectiveBias, behaviour.name());
      if (weight <= 0.0 || weight < threshold) {
        continue; // sub-threshold (or unlisted) — not enumerated this cycle
      }
      double behaviourBest = Double.NEGATIVE_INFINITY;
      for (final TacticalGoal goal : behaviour.enumerate(bb)) {
        final double score = behaviour.intrinsicScore(goal, bb) * weight;
        out.offer(goal, score, current);
        behaviourBest = Math.max(behaviourBest, score);
      }
      out.record(behaviour.name(), behaviourBest);
    }
    return out;
  }

  /** Mutable accumulator for the scan: best candidate so far + the running goal's re-scored value. */
  private static final class Scored {
    @Nullable private TacticalGoal best;
    private double bestScore = Double.NEGATIVE_INFINITY;
    private double currentScore = Double.NEGATIVE_INFINITY;
    // Per-behaviour best score, populated only when tracing (DEBUG) — for the goal-flip diagnostic.
    @Nullable private final Map<String, Double> scores;

    Scored(final boolean trace) {
      this.scores = trace ? new LinkedHashMap<>() : null;
    }

    void offer(final TacticalGoal goal, final double score, @Nullable final TacticalGoal current) {
      if (goal.equals(current)) {
        this.currentScore = score;
      }
      if (score > this.bestScore) {
        this.bestScore = score;
        this.best = goal;
      }
    }

    void record(final String behaviour, final double behaviourBest) {
      if (this.scores != null && behaviourBest > Double.NEGATIVE_INFINITY) {
        this.scores.put(behaviour, behaviourBest);
      }
    }

    String formatScores() {
      if (this.scores == null) {
        return "";
      }
      return this.scores.entrySet().stream()
          .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
          .map(e -> String.format("%s=%.3f", e.getKey(), e.getValue()))
          .reduce((a, b) -> a + " " + b)
          .orElse("-");
    }
  }

  /** Adaptive floor: {@code maxEffectiveWeight × minFraction}; {@code 0} when the archetype lists nothing. */
  private double enumerationThreshold(
      final ArchetypeConfig archetype,
      final Map<String, Double> objectiveBias,
      final double minFraction) {
    double max = 0.0;
    for (final Map.Entry<String, Double> e : archetype.behaviourWeights().entrySet()) {
      final double eff = e.getValue() * objectiveBias.getOrDefault(e.getKey(), 1.0);
      if (eff > max) {
        max = eff;
      }
    }
    return max * minFraction;
  }
}
