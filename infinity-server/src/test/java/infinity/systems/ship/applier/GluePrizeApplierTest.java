// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.systems.ship.applier;

import com.simsilica.es.EntityId;
import com.simsilica.es.base.DefaultEntityData;
import org.junit.Test;

/**
 * STUB-family pin: Glue ("Engine Shutdown") not yet wired (REFERENCE.md
 * {@code ## Prize} {@code EngineShutdownTime} + {@code ## PrizeWeight}
 * {@code Glue}). Replace when the timed-debuff path lands.
 */
public class GluePrizeApplierTest {

  @Test(expected = UnsupportedOperationException.class)
  public void apply_stubThrows() {
    final DefaultEntityData ed = new DefaultEntityData();
    final EntityId ship = ed.createEntity();

    new GluePrizeApplier().apply(ship, new PrizeApplierContext(ed, null, null));
  }
}
