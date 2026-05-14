// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.systems.ship.applier;

import com.simsilica.es.EntityId;
import com.simsilica.es.base.DefaultEntityData;
import org.junit.Test;

/**
 * STUB applier — pins {@link UnsupportedOperationException} until the timed-buff implementation lands.
 * Subspace canon: per-ship {@code ShieldsTime} (centiseconds) — REFERENCE.md "Ship physical properties"
 * line 430; {@code ## PrizeWeight} ({@code Shields}); {@code ## Cost} ({@code Shield}).
 */
public class ShieldsPrizeApplierTest {

  @Test(expected = UnsupportedOperationException.class)
  public void apply_throwsUntilImplemented() {
    final DefaultEntityData ed = new DefaultEntityData();
    final EntityId ship = ed.createEntity();

    new ShieldsPrizeApplier().apply(ship, new PrizeApplierContext(ed, null, null));
  }
}
