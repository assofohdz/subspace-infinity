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
import infinity.ai.steer.SeekDirection;
import infinity.ai.steer.Wander;
import infinity.ai.tactical.BotAiArenaContext;
import infinity.ai.tactical.TacticalGoal;
import infinity.sim.WeaponsFiring;
import javax.annotation.Nullable;

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
  // Flow-field heading consumer for the NavigateToTile goal; full-thrust like the other primitives.
  // Field-initialized (not ctor-injected) so existing 4-arg callers are untouched; dormant until #03.
  private final SeekDirection seek = new SeekDirection(1.0);

  private EntityId selfId;
  private MoverState self;
  private PerceptionSnapshot perception;
  private NearbyShip target;
  private String lastBranch = "";
  private String navDiag = "";
  // The bot's own frequency; -1 = not yet sampled. Lets a behaviour read enemy density/threat as the
  // blend of activeTeamFreqs() minus own (relativity in the consumer, not the arena-scoped field).
  private int ownFreq = -1;
  private double navThreatWeight;
  private double navOpportunityWeight;
  // World-space (XZ) unit direction away from nearby walls, or null. Set per-tick by the brain system;
  // blended into the flow heading by SteerToGoalTile so the hull eases off walls while still following
  // the route (cooperative — replaces the old reverse-override). See WallRepulsion#repulsion.
  @Nullable private Vec3d wallAvoid;
  // The bot's role behaviour-bias (ADR-0015), set per-tick from its BotRole + the registry; the planner
  // multiplies it onto derived × objectiveBias. Empty = identity (no role bias).
  private java.util.Map<String, Double> roleBias = java.util.Map.of();
  private WeaponsFiring firing;
  // -1 sentinels = "not yet sampled" (e.g. Energy / EnergyStats components absent).
  private int currentEnergy = -1;
  private int maxEnergy = -1;
  @Nullable private TacticalGoal currentGoal;
  @Nullable private BotAiArenaContext arenaContext;

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

  /** Why the flow-field approach did/didn't steer this tick (debug HUD); set by {@code SteerApproachTarget}. */
  public String navDiag() {
    return this.navDiag;
  }

  public void setNavDiag(final String navDiag) {
    this.navDiag = navDiag;
  }

  public MoverState self() {
    return this.self;
  }

  public void setSelf(final MoverState self) {
    this.self = self;
  }

  /** The bot's own frequency, or {@code -1} if not yet sampled this tick. */
  public int ownFreq() {
    return this.ownFreq;
  }

  public void setOwnFreq(final int ownFreq) {
    this.ownFreq = ownFreq;
  }

  /** Weight on threat-avoidance / opportunity-seeking blended into the flow-field nav heading. */
  public double navThreatWeight() {
    return this.navThreatWeight;
  }

  public double navOpportunityWeight() {
    return this.navOpportunityWeight;
  }

  /** World-space (XZ) unit push away from nearby walls this tick, or {@code null} if none close. */
  @Nullable
  public Vec3d wallAvoid() {
    return this.wallAvoid;
  }

  public void setWallAvoid(@Nullable final Vec3d wallAvoid) {
    this.wallAvoid = wallAvoid;
  }

  /** The bot's role behaviour-bias this tick (ADR-0015); empty = identity. */
  public java.util.Map<String, Double> roleBias() {
    return this.roleBias;
  }

  public void setRoleBias(final java.util.Map<String, Double> roleBias) {
    this.roleBias = roleBias;
  }

  public void setNavBlendWeights(final double threat, final double opportunity) {
    this.navThreatWeight = threat;
    this.navOpportunityWeight = opportunity;
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

  /** Flow-field heading consumer for {@code NavigateToTile}; dormant until production nav (slice #03). */
  public SeekDirection seek() {
    return this.seek;
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

  /** Goal chosen by the {@code TacticalPlanner}; the BT dispatches on its type. Null until first select. */
  @Nullable
  public TacticalGoal currentGoal() {
    return this.currentGoal;
  }

  public void setCurrentGoal(@Nullable final TacticalGoal currentGoal) {
    this.currentGoal = currentGoal;
  }

  /** Per-arena bot-AI services (navigation, norms, synergy); null in api-only tests + until injected. */
  @Nullable
  public BotAiArenaContext arenaContext() {
    return this.arenaContext;
  }

  public void setArenaContext(@Nullable final BotAiArenaContext arenaContext) {
    this.arenaContext = arenaContext;
  }
}
