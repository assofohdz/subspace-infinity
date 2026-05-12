// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.config;

/** Per-arena gravity-bomb wormhole-phase tuning; damage/decay inherited from {@link BombConfig}. Infinity extension — no canonical {@code [GravBomb]} section. */
public record GravBombConfig(long delayMs, double wormholeForce) {

  public static final GravBombConfig DEFAULTS = new GravBombConfig(1000L, 5000.0);
}
