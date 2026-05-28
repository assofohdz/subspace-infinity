// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.tactical;

import infinity.ai.MoverSnapshot;
import infinity.ai.brain.Blackboard;
import infinity.ai.field.TileScored;
import infinity.config.ZoneBotAiConfig;
import java.util.List;
import java.util.function.Supplier;

/**
 * {@code follow-traffic} (ADR-0012): drift toward where the action is. Candidates are the arena's
 * chokepoint tiles — already ranked by the geometry × traffic-heatmap combo
 * ({@code pinch × (1 + combat + k·density)}) — re-scored here by {@code hotness / (1 + dist/decay)}
 * so the bot prefers the <em>nearest</em> hot chokepoint over a distant brawl, then emits a
 * cell-precise {@link NavigateToTile}. Chokepoint-biased by construction (candidates are pinches);
 * open-field hot-tile enumeration (a coarse heatmap scan) is a future refinement. See ADR-0013.
 */
public final class FollowTrafficBehaviour implements Behaviour {

  private static final String NAME = "follow-traffic";

  private final Supplier<ZoneBotAiConfig> cfg;

  public FollowTrafficBehaviour(final Supplier<ZoneBotAiConfig> cfg) {
    this.cfg = cfg;
  }

  @Override
  public String name() {
    return NAME;
  }

  @Override
  public List<TacticalGoal> enumerate(final Blackboard bb) {
    final BotAiArenaContext ctx = bb.arenaContext();
    if (ctx == null) {
      return List.of();
    }
    final List<TileScored> chokepoints = ctx.chokepoints();
    if (chokepoints.isEmpty()) {
      return List.of();
    }
    final MoverSnapshot self = bb.self();
    final int selfX = (int) Math.floor(self.position().x) - ctx.originCellX();
    final int selfY = (int) Math.floor(self.position().z) - ctx.originCellZ();

    final double distDecay = this.cfg.get().followTrafficDistDecayCells();
    TileScored best = null;
    double bestScore = -1.0;
    for (final TileScored t : chokepoints) {
      final double dist = Math.hypot((double) t.x() - selfX, (double) t.y() - selfY);
      final double score = t.score() / (1.0 + dist / distDecay);
      if (score > bestScore) {
        bestScore = score;
        best = t;
      }
    }
    // If every score was NaN (broken or future field), nothing beats -1.0 and best stays null.
    if (best == null) {
      return List.of();
    }
    // Chokepoint tiles are arena-relative; NavigateToTile carries world cells.
    return List.of(new NavigateToTile(best.x() + ctx.originCellX(), best.y() + ctx.originCellZ()));
  }

  @Override
  public double intrinsicScore(final TacticalGoal goal, final Blackboard bb) {
    final ZoneBotAiConfig c = this.cfg.get();
    return bb.target() == null ? c.followTrafficIdleFit() : c.followTrafficEngagedFit();
  }
}
