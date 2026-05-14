// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.systems.ship.applier;

import com.simsilica.es.EntityId;
import com.simsilica.es.base.DefaultEntityData;
import org.junit.Test;

/**
 * STUB-family pin: MultiPrize not yet wired (REFERENCE.md {@code ## Prize}
 * {@code MultiPrizeCount} + {@code ## PrizeWeight} {@code MultiPrize}).
 * Replace when the recursive-roll path lands.
 */
public class MultiPrizePrizeApplierTest {

  @Test(expected = UnsupportedOperationException.class)
  public void apply_stubThrows() {
    final DefaultEntityData ed = new DefaultEntityData();
    final EntityId ship = ed.createEntity();

    new MultiPrizePrizeApplier().apply(ship, new PrizeApplierContext(ed, null, null));
  }
}
