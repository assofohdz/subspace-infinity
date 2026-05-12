// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.es;

import com.simsilica.es.EntityComponent;
import com.simsilica.es.EntityId;

/** Routing component on transient Change holder entities — names mutation target + source. See ADR 0001. */
public record ChangeTarget(EntityId target, EntityId source) implements EntityComponent {

  public ChangeTarget() {
    this(null, null);
  }

  /** Self-change shorthand — {@code target == source == self}. */
  public static ChangeTarget self(final EntityId self) {
    return new ChangeTarget(self, self);
  }
}
