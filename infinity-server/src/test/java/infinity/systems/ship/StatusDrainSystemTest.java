// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.systems.ship;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * Pins the per-tick drain math used by {@link StatusDrainSystem#perTickDrain}. With ADR 0001
 * the per-aspect {@code *Stats} bundles {@code energyDrainPerSecond} (energy units / sec) so the
 * runtime arithmetic is just {@code drain = ratePerSecond × tpf} (rounded half-up).
 *
 * <p>The Subspace 1000ths-per-centisecond conversion is applied at the spawn projector boundary
 * ({@link ShipStatusProjector}), not here — {@code raw / 10.0} → energy/sec. E.g. WEASEL
 * {@code CloakEnergy=1250} (raw) projects to {@code energyDrainPerSecond=125.0}.
 */
public class StatusDrainSystemTest {

  @Test
  public void perTickDrain_canonicalRate10_30hzTick() {
    // 10 energy/sec × (1/30)s ≈ 0.333 → rounded → 0
    assertEquals(0, StatusDrainSystem.perTickDrain(10.0, 1.0 / 30.0));
  }

  @Test
  public void perTickDrain_fullSecond_equalsRatePerSecond() {
    // 1.0s tick — drain equals the rate. WEASEL CloakEnergy raw 1250 → 125/sec.
    assertEquals(125, StatusDrainSystem.perTickDrain(125.0, 1.0));
    assertEquals(40, StatusDrainSystem.perTickDrain(40.0, 1.0));
    assertEquals(100, StatusDrainSystem.perTickDrain(100.0, 1.0));
  }

  @Test
  public void perTickDrain_zeroRate_noDrain() {
    assertEquals(0, StatusDrainSystem.perTickDrain(0.0, 1.0));
  }

  @Test
  public void perTickDrain_zeroTpf_noDrain() {
    assertEquals(0, StatusDrainSystem.perTickDrain(100.0, 0.0));
  }

  @Test
  public void perTickDrain_negativeInputs_clampToZero() {
    assertEquals(0, StatusDrainSystem.perTickDrain(-50.0, 1.0));
    assertEquals(0, StatusDrainSystem.perTickDrain(100.0, -0.5));
  }

  @Test
  public void perTickDrain_halfSecond_halvesPerSecondRate() {
    assertEquals(50, StatusDrainSystem.perTickDrain(100.0, 0.5));
  }

  @Test
  public void perTickDrain_roundsHalfUp() {
    // 10 energy/sec × 0.1s = 1.0 → 1
    assertEquals(1, StatusDrainSystem.perTickDrain(10.0, 0.1));
    // 1.4 energy/sec × 0.1s = 0.14 → 0
    assertEquals(0, StatusDrainSystem.perTickDrain(1.4, 0.1));
    // 5.1 energy/sec × 0.1s = 0.51 → 1
    assertEquals(1, StatusDrainSystem.perTickDrain(5.1, 0.1));
  }
}
