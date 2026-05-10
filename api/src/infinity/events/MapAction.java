// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.events;

/**
 * Action selector for the {@link infinity.net.GameSession#map(MapAction,
 * com.simsilica.mathd.Vec3d)} client &rarr; server RMI command. Identifies
 * whether the client wants to place ({@link #CREATE}) or remove
 * ({@link #DELETE}) a block at the supplied world coordinate.
 *
 * <p>Lives in {@code api/} so {@link infinity.net.GameSession} can name the
 * parameter type without leaking server-side {@code infinity.systems.MapSystem}
 * into the client / api dependency closure (see
 * {@code .claude/rules/api-contracts.md}).
 *
 * <p>Pre-cleanup the same selector lived as {@code static final byte CREATE} /
 * {@code DELETE} constants on {@code MapSystem}; the client read them
 * directly and only slipped past {@code LayerDependencyTest} because the Java
 * compiler inlines {@code public static final} byte constants at the call
 * site. The enum closes that layering hole.
 */
public enum MapAction {
  /** Place a block at the supplied world coordinate. */
  CREATE,
  /** Remove a block at the supplied world coordinate. */
  DELETE
}
