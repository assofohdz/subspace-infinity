// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.capability;

import infinity.config.BotDerivationConfig;
import infinity.config.ShipConfig;
import java.util.Collection;

/**
 * Derives {@link ArenaCapabilityNorms} + per-ship {@link CapabilityProfile} from {@code ShipConfig}.
 * Implements the v1 formulas in {@code docs/bot-ai/capability-derivation.md} (level-as-damage proxy;
 * bounce + attachReceive placeholder-derive false). Tunable coefficients live on the supplied
 * {@link BotDerivationConfig} (engine-bot-ai.groovy {@code derivation { }} block, ADR-0014). Callers
 * that don't have a config handy can pass {@link BotDerivationConfig#DEFAULTS}.
 */
public final class CapabilityDeriver {

  private CapabilityDeriver() {}

  /** Per-arena maxima across {@code ships}; 0 for a dimension no ship has. */
  public static ArenaCapabilityNorms deriveNorms(
      final Collection<ShipConfig> ships, final BotDerivationConfig cfg) {
    double maxSpeed = 0;
    double maxRot = 0;
    double maxThrust = 0;
    double maxEnergy = 0;
    double maxRecharge = 0;
    double maxBurst = 0;
    double maxSustain = 0;
    double maxArea = 0;
    double maxRange = 0;
    for (final ShipConfig s : ships) {
      maxSpeed = Math.max(maxSpeed, s.speed().max());
      maxRot = Math.max(maxRot, s.rotation().max());
      maxThrust = Math.max(maxThrust, s.thrust().max());
      maxEnergy = Math.max(maxEnergy, s.energy().max());
      maxRecharge = Math.max(maxRecharge, s.recharge().max());
      maxBurst = Math.max(maxBurst, burstRaw(s));
      maxSustain = Math.max(maxSustain, sustainedRaw(s, cfg));
      maxArea = Math.max(maxArea, areaRaw(s, cfg));
      maxRange = Math.max(maxRange, rangeRaw(s));
    }
    return new ArenaCapabilityNorms(
        maxSpeed, maxRot, maxThrust, maxEnergy, maxRecharge,
        maxBurst, maxSustain, maxArea, maxRange, maxRecharge);
  }

  /** Normalized profile for {@code s} against {@code n} using {@code cfg} coefficients. */
  public static CapabilityProfile derive(
      final ShipConfig s, final ArenaCapabilityNorms n, final BotDerivationConfig cfg) {
    final double ws = cfg.mobilitySpeedWeight();
    final double wr = cfg.mobilityRotationWeight();
    final double wt = cfg.mobilityThrustWeight();
    final double mobility =
        norm(
            ws * s.speed().max() + wr * s.rotation().max() + wt * s.thrust().max(),
            ws * n.maxSpeed() + wr * n.maxRotation() + wt * n.maxThrust());
    final double tankiness =
        norm(
            geomean(s.energy().max(), s.recharge().max()),
            geomean(n.maxEnergy(), n.maxRecharge()));
    return new CapabilityProfile(
        s.type(),
        mobility,
        norm(burstRaw(s), n.maxBurstDpsRaw()),
        norm(sustainedRaw(s, cfg), n.maxSustainedDpsRaw()),
        norm(areaRaw(s, cfg), n.maxAreaDamageRaw()),
        tankiness,
        norm(s.recharge().max(), n.maxRechargeEconomy()),
        norm(rangeRaw(s), n.maxRange()),
        false, // bulletBounce — no ShipConfig source yet (placeholder)
        false, // bombBounce — no ShipConfig source yet (placeholder)
        statusCapable(s.stealth()),
        statusCapable(s.cloak()),
        statusCapable(s.xradar()),
        statusCapable(s.antiwarp()),
        false, // attachReceive — no ShipConfig source yet (placeholder)
        s.mines() != null ? 1 : 0, // has-mines gate (no true count cap in ShipConfig)
        s.repels() != null ? s.repels().max() : 0,
        s.bursts() != null ? s.bursts().max() : 0,
        s.decoys() != null ? s.decoys().max() : 0,
        s.portals() != null ? s.portals().max() : 0,
        s.thors() != null ? s.thors().max() : 0,
        s.bricks() != null ? s.bricks().max() : 0);
  }

  // --- raw metrics (shared by norms + derive so normalization is consistent) ---

  private static double directRate(final ShipConfig s) {
    double r = 0;
    if (s.bullets() != null) {
      r += s.bullets().max().level * shotsPerSec(s.bullets().fireDelayCs());
    }
    if (s.bombs() != null) {
      r += s.bombs().max().level * shotsPerSec(s.bombs().fireDelayCs());
    }
    return r;
  }

  private static double burstRaw(final ShipConfig s) {
    return directRate(s);
  }

  private static double sustainedRaw(final ShipConfig s, final BotDerivationConfig cfg) {
    return directRate(s) * (s.recharge().max() / cfg.sustainRechargeScale());
  }

  private static double areaRaw(final ShipConfig s, final BotDerivationConfig cfg) {
    double a = 0;
    if (s.bombs() != null) {
      final double radius = s.bombs().max().level;
      a += radius * radius;
    }
    if (s.gravBombs() != null) {
      a += s.gravBombs().max() * cfg.gravBombArea();
    }
    if (s.bursts() != null) {
      a += s.bursts().max() * cfg.burstArea();
    }
    if (s.thors() != null) {
      a += s.thors().max() * cfg.thorArea();
    }
    return a;
  }

  private static double rangeRaw(final ShipConfig s) {
    final double bullet = s.bullets() != null ? s.bullets().speed() : 0;
    final double bomb = s.bombs() != null ? s.bombs().speed() : 0;
    return Math.max(bullet, bomb);
  }

  private static double shotsPerSec(final long fireDelayCs) {
    return fireDelayCs <= 0 ? 0 : 100.0 / fireDelayCs; // 1 s = 100 cs
  }

  private static boolean statusCapable(final infinity.config.StatusStats st) {
    return st != null && st.status() > 0;
  }

  private static double geomean(final double a, final double b) {
    return Math.sqrt(Math.max(0, a) * Math.max(0, b));
  }

  private static double norm(final double v, final double max) {
    if (max <= 0) {
      return 0;
    }
    return Math.max(0, Math.min(1, v / max));
  }
}
