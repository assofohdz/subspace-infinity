// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.brain;

import com.simsilica.es.EntityId;
import com.simsilica.mathd.Vec3d;
import infinity.ai.MoverState;
import infinity.ai.NearbyShip;
import infinity.ai.PerceptionSnapshot;
import infinity.ai.steer.Evade;
import infinity.ai.steer.OrbitTarget;
import infinity.ai.steer.Pursue;
import infinity.ai.steer.Wander;
import infinity.sim.WeaponsFiring;

/**
 * Per-bot scratchpad shared between BT nodes for one tick. {@link infinity.ai.bt.Action}
 * leaves write to {@link #intent()}; conditions read {@link #target()} etc. Steering
 * instances ({@link #pursue()}, {@link #wander()}) hold per-bot state across ticks and
 * are owned by the blackboard (one set per bot, constructed by the archetype).
 * See ADR-0009.
 */
public final class Blackboard {

  private final Vec3d intent = new Vec3d();
  private final Pursue pursue;
  private final Wander wander;
  private final OrbitTarget orbit;
  private final Evade evade;

  private EntityId selfId;
  private MoverState self;
  private PerceptionSnapshot perception;
  private NearbyShip target;
  private String lastBranch = "";
  private WeaponsFiring firing;
  // -1 sentinels = "not yet sampled" (e.g. Energy / EnergyStats components absent).
  private int currentEnergy = -1;
  private int maxEnergy = -1;

  public Blackboard(
      final Pursue pursue,
      final Wander wander,
      final OrbitTarget orbit,
      final Evade evade) {
    this.pursue = pursue;
    this.wander = wander;
    this.orbit = orbit;
    this.evade = evade;
  }

  /** Last BT branch that wrote intent this tick — set by {@code Steer*} actions for debug HUD. */
  public String lastBranch() {
    return this.lastBranch;
  }

  public void setLastBranch(final String branch) {
    this.lastBranch = branch;
  }

  public MoverState self() {
    return this.self;
  }

  public void setSelf(final MoverState self) {
    this.self = self;
  }

  public PerceptionSnapshot perception() {
    return this.perception;
  }

  public void setPerception(final PerceptionSnapshot perception) {
    this.perception = perception;
  }

  public NearbyShip target() {
    return this.target;
  }

  public void setTarget(final NearbyShip target) {
    this.target = target;
  }

  public EntityId selfId() {
    return this.selfId;
  }

  public void setSelfId(final EntityId selfId) {
    this.selfId = selfId;
  }

  /** Server-side firing service, injected by BotBrainSystem; null in api-only tests. */
  public WeaponsFiring firing() {
    return this.firing;
  }

  public void setFiring(final WeaponsFiring firing) {
    this.firing = firing;
  }

  /** Mutable intent accumulator. Actions write via {@code intent().set(v)}. */
  public Vec3d intent() {
    return this.intent;
  }

  public void resetIntent() {
    this.intent.set(0.0, 0.0, 0.0);
  }

  public Pursue pursue() {
    return this.pursue;
  }

  public Wander wander() {
    return this.wander;
  }

  public OrbitTarget orbit() {
    return this.orbit;
  }

  public Evade evade() {
    return this.evade;
  }

  /** Current energy pool; {@code -1} when the bot has no {@code Energy} component yet. */
  public int currentEnergy() {
    return this.currentEnergy;
  }

  /** Max energy pool; {@code -1} when the bot has no {@code EnergyStats} component yet. */
  public int maxEnergy() {
    return this.maxEnergy;
  }

  public void setEnergy(final int currentEnergy, final int maxEnergy) {
    this.currentEnergy = currentEnergy;
    this.maxEnergy = maxEnergy;
  }
}
