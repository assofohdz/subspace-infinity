// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es;

import com.simsilica.es.EntityComponent;

/** Value-replacement payload for {@link Frequency}; pairs with {@link ChangeTarget}. Drained by {@code FrequencySystem}. See ADR 0001. */
public record FrequencyChange(int newFrequency) implements EntityComponent {

  public FrequencyChange() {
    this(0);
  }
}
