// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.settings;

/**
 * Shared validation guards for typed Groovy settings adapters. Exists to
 * collapse the ~16 duplicate NaN/Inf / non-negative / unit-conversion
 * checks that previously lived inline in {@code BombAdapter} /
 * {@code PrizeAdapter} / {@code SpawnAdapter} etc. Each helper produces an
 * {@link IllegalArgumentException} with a uniform message shape so authors
 * see a consistent error in script-eval logs.
 *
 * <p>Centisecond → millisecond conversions stay <b>at the loader
 * boundary</b> per {@code .claude/rules/settings-pipeline.md}; this helper
 * provides a {@link #centisecondsToMs} convenience that bundles the
 * non-negative guard with the {@code × 10L} promotion.
 */
final class Validators {

  private Validators() {}

  /**
   * Coerce a possibly-null {@link Number} to a primitive {@code double},
   * rejecting NaN / Infinity / negative values. Used for tunables that are
   * authored in tiles (or other floating-point world units) — Groovy passes
   * integer literals as {@code Integer} and decimal literals as
   * {@code BigDecimal}, both of which {@code Number.doubleValue()} converts
   * losslessly.
   */
  static double finiteNonNegativeDouble(final String name, final Number value) {
    if (value == null) {
      throw new IllegalArgumentException(name + " requires a number");
    }
    final double v = value.doubleValue();
    if (Double.isNaN(v) || Double.isInfinite(v) || v < 0.0) {
      throw new IllegalArgumentException(
          name + " must be a finite value >= 0; got " + value);
    }
    return v;
  }

  /** Reject negative {@code int} values. Returns the input unchanged. */
  static int nonNegativeInt(final String name, final int value) {
    if (value < 0) {
      throw new IllegalArgumentException(name + " must be >= 0; got " + value);
    }
    return value;
  }

  /**
   * Convert {@code centiseconds} to milliseconds (Subspace VIE convention,
   * × 10), rejecting negative input. Boundary-only — consumers always
   * read milliseconds from typed {@code *Config} records.
   */
  static long centisecondsToMs(final String name, final int centiseconds) {
    if (centiseconds < 0) {
      throw new IllegalArgumentException(name + " must be >= 0; got " + centiseconds);
    }
    return centiseconds * 10L;
  }
}
