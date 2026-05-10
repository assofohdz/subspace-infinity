// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.systems.ship;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * Pins the Subspace energy-drain conversion math used by
 * {@link StatusDrainSystem#perTickDrain}. Conversion from Subspace's
 * {@code 1000ths-per-centisecond} encoding to per-tick Health units:
 * {@code drain = energy × tpf / 10} (rounded half-up).
 *
 * <p>The applied-each-tick path (EntitySet iteration, {@code damage()}
 * dispatch into {@link EnergySystem}) is exercised by the integration
 * smoke at server bootstrap; this unit test pins the arithmetic so a
 * regression in the conversion shows up immediately.
 */
public class StatusDrainSystemTest {

  @Test
  public void perTickDrain_canonicalRate100_30hzTick() {
    // Energy=100 → 0.1 unit/cs → 10 unit/sec → ~0.333 unit per 1/30s tick.
    // Rounded half-up to int → 0 (sub-unit drains accumulate; tick rate is
    // independent of the conversion). At 30 Hz we expect 0; the system is
    // expected to either accumulate sub-unit drain across ticks or to be
    // configured at a lower tick rate. Pinning current behaviour:
    assertEquals(0, StatusDrainSystem.perTickDrain(100, 1.0 / 30.0));
  }

  @Test
  public void perTickDrain_fullSecond_yieldsEnergyOverTen() {
    // 1.0s tick: drain = energy × 1 / 10 = energy/10. WEASEL CloakEnergy 1250
    // → 125 units/sec.
    assertEquals(125, StatusDrainSystem.perTickDrain(1250, 1.0));
    assertEquals(40,  StatusDrainSystem.perTickDrain(400, 1.0));
    assertEquals(100, StatusDrainSystem.perTickDrain(1000, 1.0));
  }

  @Test
  public void perTickDrain_zeroRate_noDrain() {
    assertEquals(0, StatusDrainSystem.perTickDrain(0, 1.0));
  }

  @Test
  public void perTickDrain_zeroTpf_noDrain() {
    assertEquals(0, StatusDrainSystem.perTickDrain(1000, 0.0));
  }

  @Test
  public void perTickDrain_negativeInputs_clampToZero() {
    assertEquals(0, StatusDrainSystem.perTickDrain(-50, 1.0));
    assertEquals(0, StatusDrainSystem.perTickDrain(1000, -0.5));
  }

  @Test
  public void perTickDrain_halfSecond_halvesPerSecondRate() {
    // 0.5s tick: drain = energy × 0.5 / 10 = energy/20.
    assertEquals(50, StatusDrainSystem.perTickDrain(1000, 0.5));
  }

  @Test
  public void perTickDrain_roundsHalfUp() {
    // energy=100, tpf=0.1 → 100 × 0.1 / 10 = 1.0 → 1
    assertEquals(1, StatusDrainSystem.perTickDrain(100, 0.1));
    // energy=14, tpf=0.1 → 14 × 0.1 / 10 = 0.14 → 0
    assertEquals(0, StatusDrainSystem.perTickDrain(14, 0.1));
    // energy=51, tpf=0.1 → 51 × 0.1 / 10 = 0.51 → 1
    assertEquals(1, StatusDrainSystem.perTickDrain(51, 0.1));
  }
}
