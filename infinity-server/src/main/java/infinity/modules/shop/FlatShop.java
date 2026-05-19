// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.modules.shop;

import infinity.es.arena.ArenaId;
import infinity.modules.ModuleContext;
import infinity.modules.ShopModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Zero-config {@link ShopModule} placeholder satisfying the category for the F2
 * FFA-Deathmatch capstone arena. No buy mechanics yet — the Subspace {@code [Cost]}
 * per-prize cost table + the {@code !shop} / {@code !buy} chat flow are deferred to a
 * later slice. Loading this module is currently a no-op beyond an info log.
 *
 * <p>Future work (separate slice): map prize names to existing {@code *PrizeApplier} +
 * deduct point cost from the buyer's {@code Gold} or {@code PlayerRoundScore}.
 */
public final class FlatShop implements ShopModule {

  private static final Logger log = LoggerFactory.getLogger(FlatShop.class);
  private final ArenaId arenaId;

  public FlatShop(final ModuleContext ctx) {
    this.arenaId = ctx.arenaId();
  }

  @Override
  public void onArenaLoad(final ArenaId loadedArenaId) {
    if (log.isInfoEnabled()) {
      log.info("FlatShop loaded for arena {} (buy mechanics not yet implemented)",
          arenaId.getArena());
    }
  }
}
