// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.field.nav;

import infinity.ai.field.DistanceField;
import infinity.ai.field.FieldGradient;
import infinity.ai.field.GradientField;
import infinity.ai.field.NavigationFields;
import java.util.HashMap;
import java.util.Map;

/**
 * Synchronous {@link NavigationFields}: builds a {@link DijkstraDistanceField} per goal cell
 * on first request and caches it for the arena's lifetime. Async build + TTL eviction +
 * door invalidation are slice #03; this is the eager substrate. The grid is the loaded .lvl
 * passability ({@code passable[y][x]}, one tile = one world unit). See ADR-0011.
 */
public final class CachingNavigationFields implements NavigationFields {

  private final boolean[][] passable;
  private final Map<Long, DistanceField> cache = new HashMap<>();

  public CachingNavigationFields(final boolean[][] passable) {
    this.passable = passable;
  }

  @Override
  public DistanceField fieldFor(final int goalX, final int goalY) {
    return this.cache.computeIfAbsent(
        key(goalX, goalY), k -> new DijkstraDistanceField(goalX, goalY, this.passable));
  }

  @Override
  public GradientField gradientFor(final int goalX, final int goalY) {
    return new FieldGradient(fieldFor(goalX, goalY));
  }

  @Override
  public void evict(final int goalX, final int goalY) {
    this.cache.remove(key(goalX, goalY));
  }

  @Override
  public boolean lineOfSight(final int ax, final int ay, final int bx, final int by) {
    return infinity.ai.field.NavGrids.lineOfSight(this.passable, ax, ay, bx, by);
  }

  @Override
  public boolean passableAt(final int x, final int y) {
    return infinity.ai.field.NavGrids.passable(this.passable, x, y);
  }

  private static Long key(final int goalX, final int goalY) {
    return (((long) goalX) << 32) ^ (goalY & 0xffffffffL);
  }
}
