// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.es;

import com.simsilica.es.EntityComponent;

/** Marker opting an entity into {@code RepelSystem}'s impulse scan; stamped by ship + bomb spawn projections per {@code ShipConfig#repellable} / {@code BombConfig#repellable}. */
public final class Repellable implements EntityComponent {

  public Repellable() {
    // marker — no fields
  }
}
