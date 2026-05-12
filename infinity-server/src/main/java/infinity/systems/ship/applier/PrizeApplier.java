// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems.ship.applier;

import com.simsilica.es.EntityId;

/** Strategy applied per prize pickup; implementations must cite REFERENCE.md per {@code .claude/rules/prize-applier.md}. */
@FunctionalInterface
public interface PrizeApplier {
  /** Implementations own no-op-at-cap + missing-component guards; dispatcher handles missing-key only. */
  void apply(EntityId ship, PrizeApplierContext ctx);
}
