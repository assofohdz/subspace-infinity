// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.capability;

import infinity.config.ShipConfig;
import java.util.Collection;

/**
 * Derives {@link ArenaCapabilityNorms} + per-ship {@link CapabilityProfile} from {@code ShipConfig}.
 * Implements the v1 formulas in {@code docs/bot-ai/capability-derivation.md} (level-as-damage proxy;
 * bounce + attachReceive placeholder-derive false). Coefficients are first-cut constants here;
 * they migrate to {@code engine-bot-ai.groovy} when its loader lands (ADR-0014 engine tier).
 */
public final class CapabilityDeriver {

  // First-cut area-damage radius² × count proxies (move to engine-bot-ai.groovy). See the doc.
  private static final double GRAVBOMB_AREA = 9.0;
  private static final double BURST_AREA = 1.0;
  private static final double THOR_AREA = 16.0;
  private static final double SUSTAIN_RECHARGE_SCALE = 1000.0;

  private CapabilityDeriver() {}

  /** Per-arena maxima across {@code ships}; 0 for a dimension no ship has. */
  public static ArenaCapabilityNorms deriveNorms(final Collection<ShipConfig> ships) {
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
      maxSustain = Math.max(maxSustain, sustainedRaw(s));
      maxArea = Math.max(maxArea, areaRaw(s));
      maxRange = Math.max(maxRange, rangeRaw(s));
    }
    return new ArenaCapabilityNorms(
        maxSpeed, maxRot, maxThrust, maxEnergy, maxRecharge,
        maxBurst, maxSustain, maxArea, maxRange, maxRecharge);
  }

  /** Normalized profile for {@code s} against {@code n}. */
  public static CapabilityProfile derive(final ShipConfig s, final ArenaCapabilityNorms n) {
    final double mobility =
        norm(
            s.speed().max() + s.rotation().max() + s.thrust().max(),
            n.maxSpeed() + n.maxRotation() + n.maxThrust());
    final double tankiness =
        norm(
            geomean(s.energy().max(), s.recharge().max()),
            geomean(n.maxEnergy(), n.maxRecharge()));
    return new CapabilityProfile(
        s.type(),
        mobility,
        norm(burstRaw(s), n.maxBurstDpsRaw()),
        norm(sustainedRaw(s), n.maxSustainedDpsRaw()),
        norm(areaRaw(s), n.maxAreaDamageRaw()),
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

  private static double sustainedRaw(final ShipConfig s) {
    return directRate(s) * (s.recharge().max() / SUSTAIN_RECHARGE_SCALE);
  }

  private static double areaRaw(final ShipConfig s) {
    double a = 0;
    if (s.bombs() != null) {
      final double radius = s.bombs().max().level;
      a += radius * radius;
    }
    if (s.gravBombs() != null) {
      a += s.gravBombs().max() * GRAVBOMB_AREA;
    }
    if (s.bursts() != null) {
      a += s.bursts().max() * BURST_AREA;
    }
    if (s.thors() != null) {
      a += s.thors().max() * THOR_AREA;
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
