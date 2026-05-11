// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.es.ship.actions;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import infinity.es.ship.Energy;
import infinity.es.ship.EnergyMax;
import infinity.es.ship.Recharge;
import infinity.es.ship.RechargeMax;
import infinity.es.ship.Rotation;
import infinity.es.ship.RotationMax;
import infinity.es.ship.Speed;
import infinity.es.ship.SpeedMax;
import infinity.es.ship.Thrust;
import infinity.es.ship.ThrustMax;

/**
 * Discriminator for the unified {@link CapBump} cap-bump payload.
 * Names the ship capability the cap-bump targets and carries the per-
 * field read/write/clamp dispatch on each enum constant.
 *
 * <p>Lives in {@code api/} alongside the {@link CapBump} payload it
 * discriminates. The per-constant {@link #apply(EntityData, EntityId,
 * double)} dispatch is factory-tier mutation routing (analogous to
 * {@code ShipFactory.create} composing a ship on {@link EntityData}) —
 * no tuning constants and no imports of server-side packages, just
 * "given a (target, delta) tuple, mutate the right component". Keeps
 * the canonical-writer drain in {@code ShipSpawnSystem} short and
 * pushes the per-field branching into a typed enum rather than a
 * 5-way switch.
 *
 * <p><b>Delta truncation.</b> {@link CapBump} carries a single
 * {@code double} delta to give every field the same emit shape. For
 * the int-backed capabilities ({@link #ENERGY} / {@link #THRUST} /
 * {@link #SPEED}) the apply routine rounds half-away-from-zero via
 * {@link Math#round(double)} before clamping. For the double-backed
 * capabilities ({@link #RECHARGE} / {@link #ROTATION}) the delta
 * passes through directly. Emitters that integerize at the source
 * (the cap-bump prize appliers, which read int-typed {@code *Upgrade}
 * components) lose nothing to this widening; the truncation is only
 * relevant if a future caller emits a fractional delta against an
 * int-backed field.
 *
 * <p><b>No-op skip.</b> Each constant compares the post-clamp value to
 * the pre-bump current value and skips the {@code setComponent} call
 * when they match, per RaM rule #6 (skip no-op replacements). A no-op
 * write would fire a {@code changed} event for every reactor and
 * waste work.
 *
 * @author Asser Fahrenholz
 */
public enum CapField {

  /** Bumps {@link Energy} (current cap), clamped at {@link EnergyMax}. */
  ENERGY {
    @Override
    public void apply(
        final EntityData ed, final EntityId target, final double totalDelta) {
      final Energy current = ed.getComponent(target, Energy.class);
      final EnergyMax max = ed.getComponent(target, EnergyMax.class);
      if (current == null || max == null) {
        return;
      }
      final int currentInt = current.getEnergy();
      final int next = Math.min(currentInt + roundToInt(totalDelta), max.getMaxEnergy());
      if (next != currentInt) {
        ed.setComponent(target, new Energy(next));
      }
    }
  },

  /** Bumps {@link Recharge} (current rate, energy/sec), clamped at {@link RechargeMax}. */
  RECHARGE {
    @Override
    public void apply(
        final EntityData ed, final EntityId target, final double totalDelta) {
      final Recharge current = ed.getComponent(target, Recharge.class);
      final RechargeMax max = ed.getComponent(target, RechargeMax.class);
      if (current == null || max == null) {
        return;
      }
      final double currentDouble = current.getRechargePerSecond();
      final double next = Math.min(currentDouble + totalDelta, max.getMaxRechargePerSecond());
      if (Double.compare(next, currentDouble) != 0) {
        ed.setComponent(target, new Recharge(next));
      }
    }
  },

  /** Bumps {@link Rotation} (current rate, rad/sec), clamped at {@link RotationMax}. */
  ROTATION {
    @Override
    public void apply(
        final EntityData ed, final EntityId target, final double totalDelta) {
      final Rotation current = ed.getComponent(target, Rotation.class);
      final RotationMax max = ed.getComponent(target, RotationMax.class);
      if (current == null || max == null) {
        return;
      }
      final double currentDouble = current.getRadSec();
      final double next = Math.min(currentDouble + totalDelta, max.getRadSecMax());
      if (Double.compare(next, currentDouble) != 0) {
        ed.setComponent(target, new Rotation(next));
      }
    }
  },

  /** Bumps {@link Thrust} (current), clamped at {@link ThrustMax}. */
  THRUST {
    @Override
    public void apply(
        final EntityData ed, final EntityId target, final double totalDelta) {
      final Thrust current = ed.getComponent(target, Thrust.class);
      final ThrustMax max = ed.getComponent(target, ThrustMax.class);
      if (current == null || max == null) {
        return;
      }
      final int currentInt = current.getThrust();
      final int next = Math.min(currentInt + roundToInt(totalDelta), max.getThrustMax());
      if (next != currentInt) {
        ed.setComponent(target, new Thrust(next));
      }
    }
  },

  /** Bumps {@link Speed} (current cap), clamped at {@link SpeedMax}. */
  SPEED {
    @Override
    public void apply(
        final EntityData ed, final EntityId target, final double totalDelta) {
      final Speed current = ed.getComponent(target, Speed.class);
      final SpeedMax max = ed.getComponent(target, SpeedMax.class);
      if (current == null || max == null) {
        return;
      }
      final int currentInt = current.getSpeed();
      final int next = Math.min(currentInt + roundToInt(totalDelta), max.getSpeedMax());
      if (next != currentInt) {
        ed.setComponent(target, new Speed(next));
      }
    }
  };

  /**
   * Apply the folded delta for this capability to {@code target}: read
   * current + max, clamp, skip no-op writes, replace component. No-op
   * (early return) when the target lacks either the current or max
   * component — defensive for races where the drain runs against a
   * ship that has not yet completed spawn projection.
   *
   * @param ed         entity data the canonical writer drains on
   * @param target     the ship entity whose capability should be bumped
   * @param totalDelta the pre-clamp folded delta (sum of same-tick
   *                   bumps for this {@code (target, field)} tuple)
   */
  public abstract void apply(EntityData ed, EntityId target, double totalDelta);

  /**
   * Round half-away-from-zero to {@code int} for the int-backed
   * capabilities. Pulled out of each constant's {@code apply} so the
   * truncation policy is a single point of change.
   */
  private static int roundToInt(final double d) {
    return Math.toIntExact(Math.round(d));
  }
}
