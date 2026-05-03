// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.settings;

import infinity.config.PrizeConfig;
import infinity.systems.SettingsSystem;

/**
 * Phase B bridge: derive per-section {@code *Config} records from the
 * per-arena merged Groovy fragment store ({@link SettingsSystem}). One
 * public method per section; {@link ConfigRegistrySystem#load} calls each
 * and threads the result into a {@link ConfigRegistry} via the per-slot
 * {@code with*} updaters.
 *
 * <p>This is the legacy {@code Ini}-routed compat shim. Slice B1b adds
 * per-section typed adapters that bypass the {@code Ini} entirely; B4
 * deletes this loader once every section has its typed adapter.
 *
 * <p>Subspace fragment-key conventions used here:
 * <ul>
 *   <li>{@code *DamageLevel} — base damage on hit (raw integer, no scale).
 *   <li>{@code *AliveTime} / {@code PrizeMaxExist} — durations in
 *       <em>centiseconds</em> (1cs = 10ms), per Subspace VIE convention.
 *       Multiplied by 10 to get ms.
 * </ul>
 */
public final class GroovyWeaponsLoader {

  /** {@code Prize} section name in the merged fragment store. */
  static final String PRIZE_SECTION = "Prize";

  /**
   * Build a {@link PrizeConfig} for {@code arenaName}. Today only
   * {@code PrizeMaxExist} maps to an existing {@link PrizeConfig} field;
   * other Subspace {@code [Prize]} keys ({@code PrizeFactor},
   * {@code PrizeDelay}, {@code MultiPrizeCount}, …) need new
   * {@link PrizeConfig} fields + consumer wiring before they can be picked
   * up here.
   */
  public PrizeConfig loadPrize(final SettingsSystem settings, final String arenaName) {
    final int maxExistCs =
        settings.getInt(
            arenaName,
            PRIZE_SECTION,
            "PrizeMaxExist",
            (int) (PrizeConfig.DEFAULTS.defaultDecayMs() / 10L));
    return new PrizeConfig(
        maxExistCs * 10L,
        PrizeConfig.DEFAULTS.defaultMaxCount(),
        PrizeConfig.DEFAULTS.bountyValue());
  }
}
