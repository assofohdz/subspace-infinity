// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.systems.ship.applier;

import com.simsilica.es.EntityId;
import com.simsilica.es.base.DefaultEntityData;
import org.junit.Test;

/**
 * STUB applier — pins {@link UnsupportedOperationException} until the inventory bump lands.
 * Subspace canon: per-ship {@code ShrapnelMax} / {@code ShrapnelRate} (REFERENCE.md
 * "Shrapnel &amp; burst"); {@code ## Shrapnel} for shrapnel-side knobs;
 * {@code ## PrizeWeight} ({@code Shrapnel}).
 */
public class ShrapnelPrizeApplierTest {

  @Test(expected = UnsupportedOperationException.class)
  public void apply_throwsUntilImplemented() {
    final DefaultEntityData ed = new DefaultEntityData();
    final EntityId ship = ed.createEntity();

    new ShrapnelPrizeApplier().apply(ship, new PrizeApplierContext(ed, null, null));
  }
}
