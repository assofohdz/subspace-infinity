// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.es;

import com.simsilica.es.EntityComponent;

/**
 * Marks a damage-bearing entity (today: bomb projectiles) as area-of-effect.
 * When present alongside {@link Damage} on the contacting projectile, the
 * damage path in {@code WeaponsSystem.newContact} switches from "single-target
 * point damage at contact" to "scan all {@code Health}-bearing bodies inside
 * {@code radiusWorldUnits} of the contact point and damage each one (subject
 * to friendly-fire gating)."
 *
 * <p>Server-only — never read by client code, so no
 * {@code Serializer.registerClass} entry is required (per
 * {@code .claude/rules/components.md}).
 *
 * <p>Stamped at fire time by the spawn path
 * ({@code WeaponsSystem.createProjectileBomb}); the spawn-path projection
 * applies the firing ship's bomb-level multiplier and the Subspace
 * {@code 16 px / tile} conversion before constructing this component, so by
 * the time the contact listener reads it, the radius is already the correct
 * world-unit value to compare with body distances.
 */
public final class SplashDamage implements EntityComponent {

  private final double radiusWorldUnits;

  public SplashDamage() {
    this(0.0);
  }

  public SplashDamage(final double radiusWorldUnits) {
    this.radiusWorldUnits = radiusWorldUnits;
  }

  /** Blast radius in world units (1 unit ≈ 1 tile). */
  public double getRadiusWorldUnits() {
    return radiusWorldUnits;
  }
}
