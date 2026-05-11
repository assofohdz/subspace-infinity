// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.es.ship.actions;

import com.simsilica.es.EntityComponent;
import com.simsilica.es.EntityId;

/**
 * Replacement-as-Mutation intent for rocket-buff {@code Thrust} /
 * {@code Speed} writes. Lives on a short-lived intent holder entity that
 * the canonical writer ({@code ShipSpawnSystem.update}) drains and
 * deletes each tick.
 *
 * <p>Two emit sites:
 * <ul>
 *   <li><b>Activate</b> — {@code ConsumableSystem.createRocketBuff} emits
 *       an intent carrying the arena's {@code RocketConfig} thrust/speed
 *       overrides at the moment the player fires a rocket.
 *   <li><b>Revert</b> — {@code RocketBuffSystem.onBuffRemoved} emits an
 *       intent carrying the cached pre-buff snapshot values when the
 *       buff entity expires via {@link com.simsilica.es.common.Decay}.
 * </ul>
 *
 * <p><b>Design — Option C (value-replacement, no kind discriminator).</b>
 * Both activate and revert carry "set Thrust and Speed to these values
 * next drain" semantics; the drain has no need to behave differently
 * between the two paths, so a single value-replacement shape is the
 * cleanest fit. The drain folds intents per target ship with last-by-
 * entity-id-wins ordering per RaM rule #7 (stable ordering) — intent
 * EntityIds are monotonically increasing in Zay-ES, so the later-emitted
 * intent for the same target deterministically wins.
 *
 * <p>Closes the same-tick reproject + buff-revert race documented in
 * {@code .claude/rules/replacement-as-mutation.md} (BACKLOG C1). The
 * {@code ThrusterPrizeApplier} / {@code TopSpeedPrizeApplier} writers
 * still update {@code Thrust} / {@code Speed} directly today (BACKLOG
 * C2 territory); migrating those is a separate task — that work folds
 * the prize-upgrade race away in a single follow-up by routing both
 * appliers through the same intent path.
 *
 * <p>Server-only: this component never crosses the wire (intents are
 * drained the same or next tick by {@code ShipSpawnSystem}; clients see
 * the resulting {@code Thrust} / {@code Speed} replacements via the
 * existing Zay-ES sync). No serializer registration required.
 *
 * @author Asser Fahrenholz
 */
public final class RocketBuffIntent implements EntityComponent {

  private final EntityId target;
  private final int thrust;
  private final int speed;

  public RocketBuffIntent() {
    this(null, 0, 0);
  }

  public RocketBuffIntent(final EntityId target, final int thrust, final int speed) {
    this.target = target;
    this.thrust = thrust;
    this.speed = speed;
  }

  public EntityId getTarget() {
    return target;
  }

  public int getThrust() {
    return thrust;
  }

  public int getSpeed() {
    return speed;
  }

  @Override
  public String toString() {
    return "RocketBuffIntent[target=" + target + ", thrust=" + thrust + ", speed=" + speed + "]";
  }
}
