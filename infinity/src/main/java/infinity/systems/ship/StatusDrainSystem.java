// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems.ship;

import com.simsilica.es.Entity;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntitySet;
import com.simsilica.sim.AbstractGameSystem;
import com.simsilica.sim.SimTime;
import infinity.es.ship.Health;
import infinity.es.ship.toggles.Cloak;
import infinity.es.ship.toggles.CloakEnergy;
import infinity.es.ship.toggles.Stealth;
import infinity.es.ship.toggles.StealthEnergy;
import infinity.sim.util.InfinityRunTimeException;

/**
 * Drains Health from ships with active Status-family toggles
 * ({@link Cloak}, {@link Stealth}). Subspace canonical conversion
 * (REFERENCE.md "Ship abilities"): the {@code *Energy} value is
 * <em>1000ths of an energy unit per centisecond</em> — so the per-tick
 * Health drain is {@code (energy / 1000) * (tpfSeconds * 100)} =
 * {@code energy * tpfSeconds / 10}.
 *
 * <p>Slice 6a wires Cloak + Stealth; Slice 6b extends the same pattern
 * to XRadar + AntiWarp by adding two more EntitySets and per-tick
 * drain calls.
 *
 * <p><b>Behaviour notes (Subspace canon):</b>
 * <ul>
 *   <li>Drain runs unconditionally while a toggle is active —
 *       deliberately including when {@code Health} is at or near zero.
 *       {@link EnergySystem} handles the death transition; the drain
 *       system doesn't auto-disable the toggle on energy floor.
 *   <li>The toggle is turned <em>on</em> by the corresponding prize
 *       applier (or by spawn projection when {@code *Status == 2}).
 *       Player-initiated toggle-off is not wired in Slice 6a (no input
 *       binding yet); deferred to a later input slice.
 *   <li>The drain accumulates through {@link EnergySystem#damage}
 *       (negative delta), so it interleaves correctly with recharge,
 *       weapon-fire cost, and contact damage in the same tick.
 * </ul>
 */
public class StatusDrainSystem extends AbstractGameSystem {

  private EntityData ed;
  private EnergySystem energySystem;
  private EntitySet cloakDrainers;
  private EntitySet stealthDrainers;

  @Override
  protected void initialize() {
    ed = getSystem(EntityData.class);
    if (ed == null) {
      throw new InfinityRunTimeException(
          getClass().getName() + " system requires an EntityData object.");
    }
    energySystem = getSystem(EnergySystem.class);
    if (energySystem == null) {
      throw new InfinityRunTimeException(
          getClass().getName() + " system requires the EnergySystem.");
    }
    cloakDrainers = ed.getEntities(Cloak.class, CloakEnergy.class, Health.class);
    stealthDrainers = ed.getEntities(Stealth.class, StealthEnergy.class, Health.class);
  }

  @Override
  protected void terminate() {
    cloakDrainers.release();
    cloakDrainers = null;
    stealthDrainers.release();
    stealthDrainers = null;
  }

  @Override
  public void update(final SimTime time) {
    cloakDrainers.applyChanges();
    stealthDrainers.applyChanges();

    final double tpf = time.getTpf();

    for (final Entity e : cloakDrainers) {
      if (!e.get(Cloak.class).isEnabled()) {
        continue;
      }
      final int rate = e.get(CloakEnergy.class).getEnergy();
      final int drain = perTickDrain(rate, tpf);
      if (drain > 0) {
        energySystem.damage(e.getId(), -drain);
      }
    }

    for (final Entity e : stealthDrainers) {
      if (!e.get(Stealth.class).isEnabled()) {
        continue;
      }
      final int rate = e.get(StealthEnergy.class).getEnergy();
      final int drain = perTickDrain(rate, tpf);
      if (drain > 0) {
        energySystem.damage(e.getId(), -drain);
      }
    }
  }

  /**
   * Convert Subspace's {@code 1000ths-per-centisecond} encoding to a
   * per-tick integer Health delta. Visible for tests.
   *
   * @param energyDrainPer1000Cs raw {@code *Energy} value (0..32000)
   * @param tpfSeconds tick length in seconds
   * @return Health units to drain this tick (rounded; never negative)
   */
  static int perTickDrain(final int energyDrainPer1000Cs, final double tpfSeconds) {
    if (energyDrainPer1000Cs <= 0 || tpfSeconds <= 0.0) {
      return 0;
    }
    // (energy / 1000) energy-units-per-cs × (tpf × 100) cs-per-tick =
    // energy × tpf / 10. Compute as double, round half-up to int.
    return (int) Math.round((double) energyDrainPer1000Cs * tpfSeconds / 10.0);
  }
}
