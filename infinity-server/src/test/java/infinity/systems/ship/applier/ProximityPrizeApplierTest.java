// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.systems.ship.applier;

import com.simsilica.es.EntityId;
import com.simsilica.es.base.DefaultEntityData;
import org.junit.Test;

/**
 * STUB applier — pins {@link UnsupportedOperationException} until the BOMB-mode flip-toggle lands.
 * Subspace canon: REFERENCE.md {@code ## Bomb} ({@code ProximityDistance}) +
 * {@code ## PrizeWeight} ({@code Proximity}).
 */
public class ProximityPrizeApplierTest {

  @Test(expected = UnsupportedOperationException.class)
  public void apply_throwsUntilImplemented() {
    final DefaultEntityData ed = new DefaultEntityData();
    final EntityId ship = ed.createEntity();

    new ProximityPrizeApplier().apply(ship, new PrizeApplierContext(ed, null, null));
  }
}
