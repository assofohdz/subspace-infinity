// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.es;

import com.simsilica.es.EntityComponent;

/**
 * Marks a projectile (today: bomb) as proximity-armed: the projectile won't
 * detonate on direct body contact with an enemy ship; instead a per-tick
 * scan in {@code ProximityFuseSystem} arms the projectile when an enemy
 * enters {@link #getRadiusWorldUnits()}, then detonates after
 * {@link #getFuseMs()} milliseconds.
 *
 * <p>Stamped at fire time by {@code WeaponsSystem.createProjectileBomb}
 * when the firing arena's {@code [Bomb] ProximityDistance} and
 * {@code BombExplodeDelay} are both &gt; 0. The projection applies the
 * firing ship's bomb-level additive scaling (L1=base, L2=base+1, …) so
 * the radius is already the correct world-unit value when the system
 * reads it.
 *
 * <p>Server-only — never referenced by client code, so no
 * {@code Serializer.registerClass} entry is required (per
 * {@code .claude/rules/components.md}).
 *
 * <p>Subspace canon (REFERENCE.md ## Bomb): {@code ProximityDistance —
 * Radius of proximity trigger in tiles. Each level adds 1.}
 * {@code BombExplodeDelay — Delay after proximity sensor trigger before
 * bomb explodes (immediate if ship leaves trigger area)}. Infinity
 * currently runs the fuse to completion regardless of whether the ship
 * leaves the radius — that "leave-arms-immediate" edge case is a
 * polish-bag follow-up.
 */
public final class ProximityFuse implements EntityComponent {

  private final double radiusWorldUnits;
  private final long fuseMs;

  public ProximityFuse() {
    this(0.0, 0L);
  }

  public ProximityFuse(final double radiusWorldUnits, final long fuseMs) {
    this.radiusWorldUnits = radiusWorldUnits;
    this.fuseMs = fuseMs;
  }

  /** Proximity-arm radius in world units (1 unit ≈ 1 tile). */
  public double getRadiusWorldUnits() {
    return radiusWorldUnits;
  }

  /** Fuse delay in milliseconds between arming and detonation. */
  public long getFuseMs() {
    return fuseMs;
  }
}
