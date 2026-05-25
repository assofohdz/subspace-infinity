// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.config;

/** Operator for a per-arena behaviour-weight {@link BehaviourTweak} overlay (ADR-0014). */
public enum TweakOp {
  MULTIPLY,
  ADD;

  /** Maps the {@code arena.groovy} symbol ({@code '*'} / {@code '+'}) to an op. */
  public static TweakOp fromSymbol(final String symbol) {
    switch (symbol == null ? "" : symbol.trim()) {
      case "*":
        return MULTIPLY;
      case "+":
        return ADD;
      default:
        throw new IllegalArgumentException("tweak op must be '*' or '+'; got " + symbol);
    }
  }

  /** Applies this op to a derived weight. */
  public double apply(final double weight, final double value) {
    return this == MULTIPLY ? weight * value : weight + value;
  }
}
