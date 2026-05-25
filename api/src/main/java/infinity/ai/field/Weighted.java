// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.field;

/** A {@link ScalarField} paired with a blend coefficient for {@link FieldBlend}. See ADR-0012. */
public record Weighted(ScalarField field, double weight) {

  public static Weighted of(final ScalarField field, final double weight) {
    return new Weighted(field, weight);
  }
}
