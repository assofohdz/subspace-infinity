/*
 * Copyright (c) 2018-2026, Asser Fahrenholz
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package infinity.config;

import infinity.Ship;

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
 * <p>MVP scope: movement + energy + physics-feel knobs. Expand with weapons /
 * ammo / special fields as consumers are wired.
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
    double bounceRestitution) {}
