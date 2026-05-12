// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.settings;

/** Shared validation guards for typed Groovy adapters; cs → ms conversion lives at the loader boundary. */
final class Validators {

  private Validators() {}

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

  static int nonNegativeInt(final String name, final int value) {
    if (value < 0) {
      throw new IllegalArgumentException(name + " must be >= 0; got " + value);
    }
    return value;
  }

  /** Subspace VIE centiseconds × 10 → ms; boundary-only. */
  static long centisecondsToMs(final String name, final int centiseconds) {
    if (centiseconds < 0) {
      throw new IllegalArgumentException(name + " must be >= 0; got " + centiseconds);
    }
    return centiseconds * 10L;
  }
}
