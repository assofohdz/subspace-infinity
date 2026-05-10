// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.config;

/**
 * Per-arena Thor projectile tuning. Read at projectile-creation time by
 * {@code ConsumableSystem.createProjectileThor}.
 *
 * @param damage damage applied on Thor hit (legacy default {@code 10})
 * @param decayMs Thor projectile lifetime in ms (legacy default
 *     {@code 1500} — split out from a previously-shared bullet-decay
 *     reference to allow independent tuning)
 */
public record ThorConfig(int damage, long decayMs) {

  public static final ThorConfig DEFAULTS = new ThorConfig(10, 1500L);
}
