// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.config;

/**
 * Physics fallback constants for the no-arena / EMPTY case. Lives in {@code api/}
 * so server-tier classes that need a physics default (e.g. {@code ContactSystem}
 * for entities not yet bound to an arena) can read it without importing a
 * {@code *Config} record. Per ADR-0002 hot-path-import discipline.
 */
public final class PhysicsDefaults {

  /** Frictionless walls (Subspace canon); also the {@code ArenaConfig.EMPTY} default. */
  public static final double DEFAULT_WALL_FRICTION = 0.0;

  private PhysicsDefaults() {}
}
