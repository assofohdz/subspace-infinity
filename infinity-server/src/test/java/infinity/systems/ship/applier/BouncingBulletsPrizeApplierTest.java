// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.systems.ship.applier;

import com.simsilica.es.EntityId;
import com.simsilica.es.base.DefaultEntityData;
import org.junit.Test;

/**
 * STUB-family pin: BouncingBullets is not yet implemented (REFERENCE.md
 * {@code ## PrizeWeight} {@code BouncingBullets}). Test pins the stub-throw
 * behaviour; replace when the STATUS-family flag is wired.
 */
public class BouncingBulletsPrizeApplierTest {

  @Test(expected = UnsupportedOperationException.class)
  public void apply_stubThrows() {
    final DefaultEntityData ed = new DefaultEntityData();
    final EntityId ship = ed.createEntity();

    new BouncingBulletsPrizeApplier().apply(ship, new PrizeApplierContext(ed, null, null));
  }
}
