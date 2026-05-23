// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.settings;

import infinity.config.BotBrainConfig;

/** Typed adapter for {@code botBrain {…}} blocks → {@link BotBrainConfig}. See ADR-0009 / ADR-0010. */
public final class BotBrainAdapter
    extends SingleClosureAdapter<BotBrainConfig, BotBrainAdapter.BotBrainBuilder> {

  public static final BotBrainAdapter INSTANCE = new BotBrainAdapter();

  private BotBrainAdapter() {
    super("botBrain", BotBrainConfig.DEFAULTS);
  }

  @Override
  protected BotBrainBuilder newBuilder() {
    return new BotBrainBuilder();
  }

  @Override
  public BotBrainConfig extract(final BotBrainBuilder accumulator) {
    return accumulator.build();
  }

  /** Delegate for the {@code botBrain { ... }} block. */
  public static final class BotBrainBuilder {

    private String archetypeName = BotBrainConfig.DEFAULTS.archetypeName();
    private double perceptionRadius = BotBrainConfig.DEFAULTS.perceptionRadius();
    private double engageRange = BotBrainConfig.DEFAULTS.engageRange();
    private double orbitRadius = BotBrainConfig.DEFAULTS.orbitRadius();
    private double evadeEnergyFraction = BotBrainConfig.DEFAULTS.evadeEnergyFraction();
    private double leadPredictionSeconds = BotBrainConfig.DEFAULTS.leadPredictionSeconds();
    private double aimConeDegrees = BotBrainConfig.DEFAULTS.aimConeDegrees();

    BotBrainBuilder() {}

    /** Archetype to instantiate from {@code BrainRegistry}; defaults to {@code "Brawler"}. */
    public void archetypeName(final String value) {
      if (value != null && !value.isBlank()) {
        this.archetypeName = value;
      }
    }

    /** Perception sphere radius (world units); bot considers ships + obstacles inside this. */
    public void perceptionRadius(final Number value) {
      this.perceptionRadius = Validators.finiteNonNegativeDouble("perceptionRadius", value);
    }

    /** Max distance for {@code InWeaponRange} — beyond, bot pursues; within, bot orbits + fires. */
    public void engageRange(final Number value) {
      this.engageRange = Validators.finiteNonNegativeDouble("engageRange", value);
    }

    /** Desired orbit distance for {@code OrbitTarget}. */
    public void orbitRadius(final Number value) {
      this.orbitRadius = Validators.finiteNonNegativeDouble("orbitRadius", value);
    }

    /** {@code 0..1}: flee when current energy drops below {@code fraction × maxEnergy}. */
    public void evadeEnergyFraction(final Number value) {
      final double v = Validators.finiteNonNegativeDouble("evadeEnergyFraction", value);
      if (v > 1.0) {
        throw new IllegalArgumentException(
            "evadeEnergyFraction must be in [0, 1]; got " + value);
      }
      this.evadeEnergyFraction = v;
    }

    /** Reynolds pursue / evade lead-prediction window (seconds). */
    public void leadPredictionSeconds(final Number value) {
      this.leadPredictionSeconds = Validators.finiteNonNegativeDouble("leadPredictionSeconds", value);
    }

    /** Half-angle of the firing cone (degrees); {@code FireWeapon} gated by {@code InAimRange}. */
    public void aimConeDegrees(final Number value) {
      this.aimConeDegrees = Validators.finiteNonNegativeDouble("aimConeDegrees", value);
    }

    BotBrainConfig build() {
      return new BotBrainConfig(
          archetypeName,
          perceptionRadius,
          engageRange,
          orbitRadius,
          evadeEnergyFraction,
          leadPredictionSeconds,
          aimConeDegrees);
    }
  }
}
