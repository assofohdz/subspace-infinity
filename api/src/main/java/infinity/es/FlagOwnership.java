// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es;

import com.simsilica.es.EntityComponent;

/**
 * On a flag entity: the freq currently holding the flag. Canonical writer is
 * {@code FlagSystem}; freq value follows the same convention as {@link Frequency}
 * on ships. Absent component = unowned (never touched). Non-record class because
 * jME3 {@code FieldSerializer} can't reflectively set record components (records
 * have JVM-level field finality that defeats {@code setAccessible(true)}).
 */
public final class FlagOwnership implements EntityComponent {

  private final int freq;

  public FlagOwnership() {
    this(-1);
  }

  public FlagOwnership(final int freq) {
    this.freq = freq;
  }

  public int freq() {
    return freq;
  }
}
