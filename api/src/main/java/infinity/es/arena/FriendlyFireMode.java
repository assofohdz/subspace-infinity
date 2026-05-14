// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.es.arena;

import com.simsilica.es.EntityComponent;

/**
 * Per-ship snapshot of arena's {@code friendlyFire} tri-state ({@code 0}=off, {@code 1}=splash-only, {@code 2}=all);
 * read by weapons damage logic at detonation. Projected at ship spawn by {@code ShipSpawnSystem} per ADR-0002.
 */
public record FriendlyFireMode(int mode) implements EntityComponent {

  public FriendlyFireMode() {
    this(0);
  }
}
