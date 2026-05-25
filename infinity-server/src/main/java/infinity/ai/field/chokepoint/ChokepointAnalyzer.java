// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.field.chokepoint;

import infinity.ai.field.NavGrids;
import infinity.ai.field.TileScored;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Load-time width-narrow detector over the arena passability grid (ADR-0012) — the <em>geometric
 * half</em> of a chokepoint. A tactical chokepoint is a narrow space to pass <em>combined with a
 * traffic heatmap</em>: a tight corridor nobody uses isn't a chokepoint; a tight corridor everyone
 * funnels through is. This class finds the geometry (the static "narrow space"); the heatmap combo
 * (geometric score × live traffic/combat density at the tile) is applied at runtime by the consumer
 * — see {@code ServerBotAiArenaContext.chokepoints()} re-ranking. A pure tile-list producer, not a
 * runtime field.
 *
 * <p>A candidate is a passable cell whose open run along one axis is tight ({@code <= maxWidth})
 * while the perpendicular run is notably more open: a corridor pinch, not an open intersection or a
 * small pocket. Both runs are capped so wide-open cells self-prune cheaply (the cap makes them read
 * "equally open" → skipped). Candidates are scored by pinch ratio ({@code wide / narrow}) and
 * spatially deduped so a long corridor yields a few representative tiles, not every cell along it.
 * Arena-relative cells; feeds {@link infinity.ai.field.NavigationFields} static-goal registration
 * (ADR-0011 §"Goal registration") and runtime heatmap re-ranking.
 */
public final class ChokepointAnalyzer {

  private final boolean[][] passable;
  private final int width;
  private final int height;
  private final int maxWidth;
  private final int cap;

  public ChokepointAnalyzer(final boolean[][] passable, final int maxWidth) {
    this.passable = passable;
    this.height = passable.length;
    this.width = this.height == 0 ? 0 : passable[0].length;
    this.maxWidth = Math.max(1, maxWidth);
    // Cap runs a bit past maxWidth so a real passage's long axis reads clearly "more open".
    this.cap = 2 * this.maxWidth + 2;
  }

  /** Top-{@code n} chokepoint tiles by pinch ratio, deduped to one per {@code 2·maxWidth} neighbourhood. */
  public List<TileScored> hottest(final int n) {
    final List<TileScored> candidates = new ArrayList<>();
    for (int z = 0; z < this.height; z++) {
      for (int x = 0; x < this.width; x++) {
        if (!this.passable[z][x]) {
          continue;
        }
        final int hSpan = openRun(x, z, 1, 0);
        final int vSpan = openRun(x, z, 0, 1);
        final int narrow = Math.min(hSpan, vSpan);
        final int wide = Math.max(hSpan, vSpan);
        // Tight on the narrow axis, clearly more open on the wide axis = a passage pinch.
        if (narrow <= this.maxWidth && wide >= narrow + 2) {
          candidates.add(new TileScored(x, z, (double) wide / narrow));
        }
      }
    }
    candidates.sort(Comparator.comparingDouble(TileScored::score).reversed());
    return dedupe(candidates, n, 2 * this.maxWidth);
  }

  /** Open passable run through (x,z) along (dx,dz), capped at {@link #cap}; out-of-bounds = wall. */
  private int openRun(final int x, final int z, final int dx, final int dz) {
    int run = 1;
    for (int s = 1; s <= this.cap && NavGrids.passable(this.passable, x + dx * s, z + dz * s); s++) {
      run++;
    }
    for (int s = 1; s <= this.cap && NavGrids.passable(this.passable, x - dx * s, z - dz * s); s++) {
      run++;
    }
    return Math.min(run, this.cap);
  }

  /** Greedily keep the highest-scoring tiles, skipping any within {@code radius} (Chebyshev) of a kept one. */
  private static List<TileScored> dedupe(
      final List<TileScored> sorted, final int n, final int radius) {
    final List<TileScored> kept = new ArrayList<>();
    for (final TileScored t : sorted) {
      if (kept.size() >= n) {
        break;
      }
      boolean near = false;
      for (final TileScored k : kept) {
        if (Math.max(Math.abs(t.x() - k.x()), Math.abs(t.y() - k.y())) <= radius) {
          near = true;
          break;
        }
      }
      if (!near) {
        kept.add(t);
      }
    }
    return kept;
  }
}
