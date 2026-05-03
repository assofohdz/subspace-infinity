// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.config;

import infinity.Ship;
import javax.annotation.Nullable;

/**
 * Immutable template describing one ship type's baseline tuning for a given
 * arena. Produced by the config layer (Groovy script / legacy INI) and looked
 * up by consumers via a per-arena registry keyed on {@link Ship}.
 *
 * <p>At ship-spawn time, these template values are projected into per-entity
 * ECS components ({@code Thrust}/{@code ThrustMax}/{@code ThrustUpgrade},
 * etc.) so consumers like the movement driver never touch the template
 * directly — they watch the components, which can diverge from the template
 * via upgrades, damage, or status effects.
 *
 * <p>Weapon and inventory fields ({@code bombs}, {@code guns}, {@code mines},
 * {@code bursts}, {@code thors}, {@code repels}) are nullable: a {@code null}
 * means "this ship type doesn't carry / can't acquire this weapon at all".
 * {@code ShipSpawnSystem} skips the corresponding projection block on null,
 * so the ship spawns without {@code BombMaxLevel} / {@code BurstMax} / etc.,
 * which the prize appliers interpret as "not allowed" (component-absence as
 * the disallow signal). Today every existing {@code ships.groovy} relies on
 * the {@code GroovyShipLoader.DEFAULT_*} permissive fallbacks — explicit
 * {@code null} expressions in script form are a future authoring extension.
 *
 * @param type the ship this template applies to
 * @param rotation rotation-rate triple (initial / max / per-upgrade)
 * @param thrust thrust triple
 * @param speed speed triple
 * @param recharge recharge-rate triple
 * @param energy energy-pool triple
 * @param dragFactor coast-drag fraction of {@code Thrust} when no thrust intent
 *     ({@code 0} = pure coast, {@code 1} = decelerate as fast as full thrust)
 * @param turnResponsiveness rate constant (1/sec) for the angular-velocity
 *     ease-toward-target ({@code 8.0} ≈ 95% of target in ~0.4 sec)
 * @param bounceRestitution wall-bounce restitution ({@code 1} = perfectly
 *     elastic, {@code 0} = stick)
 * @param radarRange radius (world units) around the ship that the client
 *     radar viewport displays
 * @param bombs starting + max bomb level, fire cost, fire-delay; {@code null}
 *     = no bombs
 * @param guns starting + max gun level, fire cost, fire-delay; {@code null}
 *     = no guns
 * @param mines starting + max mine level (reuses BombLevel enum), drop cost,
 *     drop-delay; {@code null} = no mines
 * @param bursts starting + max burst inventory count; {@code null} = no bursts
 * @param thors starting + max thor inventory count + per-fire delay;
 *     {@code null} = no thors
 * @param repels starting + max repel inventory count; {@code null} = no repels
 */
public record ShipConfig(
    Ship type,
    ShipStat rotation,
    ShipStat thrust,
    ShipStat speed,
    ShipStat recharge,
    ShipStat energy,
    double dragFactor,
    double turnResponsiveness,
    double bounceRestitution,
    double radarRange,
    @Nullable BombStats bombs,
    @Nullable GunStats guns,
    @Nullable MineStats mines,
    @Nullable CountStats bursts,
    @Nullable CountWithDelayStats thors,
    @Nullable CountStats repels) {}
