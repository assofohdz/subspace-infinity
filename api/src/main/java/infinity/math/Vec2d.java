// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.math;

import com.simsilica.mathd.Vec3d;

/** Immutable 2D double vector for tile-grid field math; bridges to the Vec3d XZ steering plane. See ADR-0011. */
public final class Vec2d {

  public static final Vec2d ZERO = new Vec2d(0.0, 0.0);

  public final double x;
  public final double y;

  public Vec2d(final double x, final double y) {
    this.x = x;
    this.y = y;
  }

  /** From the XZ plane of a Vec3d (y is dropped — top-down). */
  public static Vec2d fromXz(final Vec3d v) {
    return new Vec2d(v.x, v.z);
  }

  /** To the XZ plane of a Vec3d (y = 0). */
  public Vec3d toXz() {
    return new Vec3d(this.x, 0.0, this.y);
  }

  public Vec2d add(final Vec2d o) {
    return new Vec2d(this.x + o.x, this.y + o.y);
  }

  public Vec2d subtract(final Vec2d o) {
    return new Vec2d(this.x - o.x, this.y - o.y);
  }

  public Vec2d mult(final double s) {
    return new Vec2d(this.x * s, this.y * s);
  }

  public double dot(final Vec2d o) {
    return this.x * o.x + this.y * o.y;
  }

  public double lengthSq() {
    return this.x * this.x + this.y * this.y;
  }

  public double length() {
    return Math.sqrt(lengthSq());
  }

  /** Unit vector, or {@link #ZERO} when too short to normalize safely. */
  public Vec2d normalize() {
    final double len = length();
    return len < 1e-9 ? ZERO : new Vec2d(this.x / len, this.y / len);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof Vec2d)) {
      return false;
    }
    final Vec2d v = (Vec2d) o;
    return Double.compare(this.x, v.x) == 0 && Double.compare(this.y, v.y) == 0;
  }

  @Override
  public int hashCode() {
    return Double.hashCode(this.x) * 31 + Double.hashCode(this.y);
  }

  @Override
  public String toString() {
    return "Vec2d[" + this.x + ", " + this.y + "]";
  }
}
