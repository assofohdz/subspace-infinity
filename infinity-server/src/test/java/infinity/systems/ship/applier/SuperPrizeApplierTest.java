// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.systems.ship.applier;

import com.simsilica.es.EntityId;
import com.simsilica.es.base.DefaultEntityData;
import org.junit.Test;

/**
 * STUB applier — pins {@link UnsupportedOperationException} until the timed-buff implementation lands.
 * Subspace canon: per-ship {@code SuperTime} (centiseconds) — REFERENCE.md
 * "Ship physical properties" line 429; canonical prize name is {@code AllWeapons}
 * — {@code ## PrizeWeight} ({@code AllWeapons} = "Super!").
 */
public class SuperPrizeApplierTest {

  @Test(expected = UnsupportedOperationException.class)
  public void apply_throwsUntilImplemented() {
    final DefaultEntityData ed = new DefaultEntityData();
    final EntityId ship = ed.createEntity();

    new SuperPrizeApplier().apply(ship, new PrizeApplierContext(ed, null, null));
  }
}
