// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.es.ship.actions;

import com.simsilica.es.EntityComponent;

/**
 * Wall length (in tiles) of a placed brick marker entity. Stamped on the
 * marker by {@code GameEntities.createBrick} from the per-arena
 * {@link infinity.config.BrickConfig#spanTiles}.
 *
 * <p>Today the consumer of this value is the (deferred) brick-geometry
 * slice that turns a single marker into N solid wall tiles along the
 * ship's perpendicular. Plumbing-only Slice 3 carries it forward so the
 * follow-up slice has the value at the seam where it needs it.
 *
 * @author Asser Fahrenholz
 */
public class BrickSpan implements EntityComponent {

  private final int tiles;

  public BrickSpan() {
    this(0);
  }

  public BrickSpan(final int tiles) {
    this.tiles = tiles;
  }

  public int getTiles() {
    return tiles;
  }

  @Override
  public String toString() {
    return "BrickSpan[" + tiles + " tiles]";
  }
}
