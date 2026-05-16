// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.modules;

import java.util.List;

/** Outcome of {@code ModuleLoader.validate()}. Empty errors list = OK. */
public record ValidationResult(List<String> errors) {

  public ValidationResult {
    errors = List.copyOf(errors);
  }

  public static final ValidationResult OK = new ValidationResult(List.of());

  public boolean ok() {
    return errors.isEmpty();
  }
}
