// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.modules.scoring;

import infinity.es.arena.ArenaId;
import infinity.modules.ModuleContext;
import infinity.modules.ScoringModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Zero-config no-op {@link ScoringModule} — loads cleanly, emits nothing. Exists so
 * arena.groovy can declare a second {@code scoring} entry alongside {@code kill-points}
 * (validating the layered {@code List<ScoringModule>} path) and so the F3 hot-reload demo
 * can show "added module" diffs by appending {@code scoring 'bonus-points'} live.
 */
public final class BonusPointsScoring implements ScoringModule {

  private static final Logger log = LoggerFactory.getLogger(BonusPointsScoring.class);
  private final ArenaId arenaId;

  public BonusPointsScoring(final ModuleContext ctx) {
    this.arenaId = ctx.arenaId();
  }

  @Override
  public void onArenaLoad(final ArenaId loadedArenaId) {
    if (log.isInfoEnabled()) {
      log.info("BonusPointsScoring loaded for arena {} (no-op marker)", arenaId.getArena());
    }
  }
}
