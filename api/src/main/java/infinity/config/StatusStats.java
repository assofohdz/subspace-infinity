// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.config;

/**
 * Per-ship Status-family capability template (Cloak / Stealth / XRadar /
 * AntiWarp). Captures Subspace's per-ship {@code *Status} tri-state +
 * {@code *Energy} drain rate as one immutable pair, projected at spawn
 * time onto the per-aspect {@code *Stats} record (which bundles tier +
 * energy-per-second drain) and the {@code *Active} Continuous toggle —
 * see {@link infinity.es.ship.toggles.CloakStats} /
 * {@link infinity.es.ship.toggles.CloakActive} and siblings.
 *
 * <p>Subspace per-ship key encoding (REFERENCE.md "Ship abilities"):
 * <ul>
 *   <li>{@code *Status} (0..2): {@code 0} = forbidden (prize is a
 *       no-op); {@code 1} = acquirable via prize, default off;
 *       {@code 2} = acquirable + starts active at spawn.
 *   <li>{@code *Energy} (0..32000): energy drain in
 *       <em>1000ths-per-centisecond</em>. Actual drain rate per
 *       centisecond = {@code *Energy / 1000}. Stored raw — the
 *       drain-consumer system does the conversion at apply time.
 * </ul>
 *
 * <p>Pattern 4 spawn projection ({@code .claude/rules/config-pattern.md}):
 * the template carries the per-ship config; per-entity components are
 * the runtime state. Hot-path consumers (drain system, applier) read
 * the components, never the template.
 *
 * @param status tri-state capability gate ({@code 0..2}; outside-range
 *     values are clamped at the loader boundary, not here)
 * @param energyDrainPer1000Cs raw {@code *Energy} value
 *     ({@code 0..32000} per Subspace canon); converted to per-cs energy
 *     drain by the consumer
 */
public record StatusStats(int status, int energyDrainPer1000Cs) {}
