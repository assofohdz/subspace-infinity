// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.systems.ship.applier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.simsilica.es.EntityId;
import com.simsilica.es.base.DefaultEntityData;
import infinity.systems.ship.WarpSystem;
import org.junit.Test;

/**
 * INSTANT-family applier — delegates to {@link WarpSystem#warpToCenter(EntityId)}.
 * Subspace canon: {@code ## PrizeWeight} ({@code Warp}). Infinity divergence:
 * REFERENCE.md ({@code ## Spawn} {@code WarpRadiusLimit}) implies "warp to a random
 * arena spawn point"; Infinity warps to arena center until a multi-spawn-point
 * selector lands.
 */
public class WarpPrizeApplierTest {

  @Test
  public void apply_dispatchesToWarpSystemWithShipId() {
    final DefaultEntityData ed = new DefaultEntityData();
    final EntityId ship = ed.createEntity();
    final RecordingWarpSystem warpSystem = new RecordingWarpSystem();

    new WarpPrizeApplier().apply(ship, new PrizeApplierContext(ed, null, warpSystem));

    assertEquals("Applier called warpToCenter exactly once", 1, warpSystem.callCount);
    assertNotNull("Applier passed a non-null avatar id", warpSystem.lastAvatar);
    assertEquals("Applier passed the ship id", ship, warpSystem.lastAvatar);
  }

  /** Test double — captures the warpToCenter dispatch without exercising real WarpSystem wiring. */
  private static final class RecordingWarpSystem extends WarpSystem {
    int callCount;
    EntityId lastAvatar;

    @Override
    public String warpToCenter(final EntityId avatarId) {
      callCount++;
      lastAvatar = avatarId;
      return "recorded";
    }
  }
}
