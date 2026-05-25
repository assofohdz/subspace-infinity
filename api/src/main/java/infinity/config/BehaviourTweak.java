// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.config;

/**
 * One per-arena delta on a derived behaviour weight (ADR-0014): {@code behaviour op value}, e.g.
 * {@code ('anchor', MULTIPLY, 1.3)} or {@code ('engage', ADD, 0.2)}. Authored in {@code arena.groovy}
 * {@code bots { ship 'x', tweak: [['anchor', '*', 1.3]] }}; an overlay on the capability-derived
 * vector, never a from-scratch weight.
 */
public record BehaviourTweak(String behaviour, TweakOp op, double value) {

  /** Applies this tweak to a behaviour's derived weight. */
  public double apply(final double derivedWeight) {
    return op.apply(derivedWeight, value);
  }
}
