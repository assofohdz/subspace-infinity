// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems.ship;

import com.simsilica.es.Entity;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntitySet;
import com.simsilica.sim.SimTime;
import infinity.es.ship.Energy;
import infinity.es.ship.toggles.AntiwarpActive;
import infinity.es.ship.toggles.AntiwarpStats;
import infinity.es.ship.toggles.CloakActive;
import infinity.es.ship.toggles.CloakStats;
import infinity.es.ship.toggles.StealthActive;
import infinity.es.ship.toggles.StealthStats;
import infinity.es.ship.toggles.XRadarActive;
import infinity.es.ship.toggles.XRadarStats;
import infinity.systems.BaseInfinitySystem;

/** Per-tick Energy drain for ships with active Status-family toggles (Cloak/Stealth/XRadar/Antiwarp). */
public class StatusDrainSystem extends BaseInfinitySystem {

  private EnergySystem energySystem;
  private EntitySet cloakDrainers;
  private EntitySet stealthDrainers;
  private EntitySet xradarDrainers;
  private EntitySet antiwarpDrainers;

  @Override
  protected void initialize() {
    final EntityData ed = requireSystem(EntityData.class);
    energySystem = requireSystem(EnergySystem.class);
    cloakDrainers = ed.getEntities(CloakActive.class, CloakStats.class, Energy.class);
    stealthDrainers = ed.getEntities(StealthActive.class, StealthStats.class, Energy.class);
    xradarDrainers = ed.getEntities(XRadarActive.class, XRadarStats.class, Energy.class);
    antiwarpDrainers = ed.getEntities(AntiwarpActive.class, AntiwarpStats.class, Energy.class);
  }

  @Override
  protected void terminate() {
    cloakDrainers.release();
    cloakDrainers = null;
    stealthDrainers.release();
    stealthDrainers = null;
    xradarDrainers.release();
    xradarDrainers = null;
    antiwarpDrainers.release();
    antiwarpDrainers = null;
  }

  @Override
  public void update(final SimTime time) {
    cloakDrainers.applyChanges();
    stealthDrainers.applyChanges();
    xradarDrainers.applyChanges();
    antiwarpDrainers.applyChanges();

    final double tpf = time.getTpf();

    drainCloak(tpf);
    drainStealth(tpf);
    drainXRadar(tpf);
    drainAntiwarp(tpf);
  }

  private void drainCloak(final double tpf) {
    for (final Entity e : cloakDrainers) {
      if (!e.get(CloakActive.class).isActive()) {
        continue;
      }
      applyDrain(e, e.get(CloakStats.class).energyDrainPerSecond(), tpf);
    }
  }

  private void drainStealth(final double tpf) {
    for (final Entity e : stealthDrainers) {
      if (!e.get(StealthActive.class).isActive()) {
        continue;
      }
      applyDrain(e, e.get(StealthStats.class).energyDrainPerSecond(), tpf);
    }
  }

  private void drainXRadar(final double tpf) {
    for (final Entity e : xradarDrainers) {
      if (!e.get(XRadarActive.class).isActive()) {
        continue;
      }
      applyDrain(e, e.get(XRadarStats.class).energyDrainPerSecond(), tpf);
    }
  }

  private void drainAntiwarp(final double tpf) {
    for (final Entity e : antiwarpDrainers) {
      if (!e.get(AntiwarpActive.class).isActive()) {
        continue;
      }
      applyDrain(e, e.get(AntiwarpStats.class).energyDrainPerSecond(), tpf);
    }
  }

  private void applyDrain(final Entity e, final double ratePerSecond, final double tpf) {
    final int drain = perTickDrain(ratePerSecond, tpf);
    if (drain > 0) {
      energySystem.damage(e.getId(), -drain);
    }
  }

  /** Per-tick integer Energy drain from energy/sec × tpf, rounded half-up. */
  static int perTickDrain(final double energyDrainPerSecond, final double tpfSeconds) {
    if (energyDrainPerSecond <= 0.0 || tpfSeconds <= 0.0) {
      return 0;
    }
    return (int) Math.round(energyDrainPerSecond * tpfSeconds);
  }
}
