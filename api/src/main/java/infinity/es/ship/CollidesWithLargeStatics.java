// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.es.ship;

import com.simsilica.es.EntityComponent;

/** Opt-in marker for moss's coarse large-static contact pass; today only ships need it. */
public class CollidesWithLargeStatics implements EntityComponent {
  public CollidesWithLargeStatics() {
    // empty constructor for serialization
  }
}
