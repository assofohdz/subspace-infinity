// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.sim;

import com.simsilica.es.EntityId;
import com.simsilica.mathd.Vec3d;
import infinity.es.Damage;

/** In-tick detonation seam called by impact + fuse systems; server impl is {@code WeaponsReaperSystem}. See ADR-0003. */
public interface Detonator {

  /** {@code directVictimId == null} for projectile-vs-world detonations. */
  void detonate(
      EntityId damageEntityId,
      Damage damage,
      Vec3d explosionPoint,
      EntityId directVictimId,
      long nowSimNanos);
}
