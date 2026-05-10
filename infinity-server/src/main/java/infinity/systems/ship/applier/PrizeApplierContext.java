// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems.ship.applier;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import infinity.systems.ship.EnergySystem;
import infinity.systems.ship.WarpSystem;

/**
 * Dependencies bundled for a {@link PrizeApplier} invocation. Built once in
 * {@code PrizeSystem.initialize()} and passed into every applier call —
 * keeps the {@link PrizeApplier#apply(EntityId, PrizeApplierContext)}
 * signature stable as new dependencies are added.
 *
 * <p>Holds {@link EntityData} for component reads/writes (the bulk of every
 * applier's work) plus a small handful of system references for the
 * appliers whose effect is "delegate to a system" rather than mutate a
 * component:
 * <ul>
 *   <li>{@link EnergySystem} — {@code QuickChargePrizeApplier} refills
 *       the live Health pool.
 *   <li>{@link WarpSystem} — {@code WarpPrizeApplier} teleports the ship.
 * </ul>
 *
 * <p>No {@link infinity.config.ShipConfig} access — applier hot-path
 * consumers read only components, per Pattern 4. Spawn-time projection by
 * {@code ShipSpawnSystem} is the sole boundary where templates touch
 * components.
 */
public record PrizeApplierContext(
    EntityData ed, EnergySystem energySystem, WarpSystem warpSystem) {}
