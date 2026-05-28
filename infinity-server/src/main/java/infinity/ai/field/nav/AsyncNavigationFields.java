// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.field.nav;

import infinity.ai.field.DistanceField;
import infinity.ai.field.FieldGradient;
import infinity.ai.field.GradientField;
import infinity.ai.field.NavigationFields;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.LongSupplier;

/**
 * Production {@link NavigationFields} (ADR-0011): each goal's {@link DijkstraDistanceField} builds on
 * a worker {@link Executor}; reads are non-blocking via {@code future.getNow(}{@link DistanceField#EMPTY}{@code )}
 * — pre-completion returns the zero-gradient default so steering falls through to reactive avoidance
 * with no crash, and the field is ready on a later read. Failed builds leave the future failed; the
 * next request evicts + rebuilds. Pinned (static) goals survive eviction; transient goals are dropped
 * by TTL idle-timeout and an LRU size cap.
 *
 * <p>Door invalidation is coarse here — {@link #evictAll()} on any door change in the arena (the
 * holder subscribes to {@code DoorStateChanged}). Per-field door-tile tracking (rebuild only the
 * fields whose Dijkstra pass crossed the changed door) is the ADR-0011 optimization, deferred: doors
 * are rare and a full arena rebuild on a worker is acceptable.
 *
 * <p>Cache access is single-threaded (the bot system tick); the worker thread only completes futures.
 */
public final class AsyncNavigationFields implements NavigationFields {

  // Raw passability (physical walls) for line-of-sight + WallRepulsion; the route grid is the
  // footprint-eroded grid (NavGrids.erodeFootprint) the Dijkstra fields build on so the diameter-2
  // hull only routes where it fits. See ADR-0011.
  private final boolean[][] passable;
  private final boolean[][] routePassable;
  // Soft-clearance routing (bot-ai-v3 B8 + B11): cells-to-nearest-wall per cell; passed to each
  // DijkstraDistanceField so wall-adjacent cells stay navigable but cost extra. null = flat cost
  // (back-compat for tests).
  private final int[][] clearance;
  private final int hullFootprint;
  private final double clearancePenalty;
  private final Executor builder;
  private final LongSupplier nowNanos;
  private final long ttlNanos;
  private final int maxTransient;
  // Goal-snap radius (tile cells) — when a target sits in a non-navigable slot, snap to the nearest
  // route-navigable cell within this radius. Sourced from ZoneBotAiConfig at construction.
  private final int goalSnapRadius;
  private final Map<Long, Entry> cache = new ConcurrentHashMap<>();

  /** Convenience: no hull erosion (route grid == raw), no soft-clearance. Used by tests. */
  public AsyncNavigationFields(
      final boolean[][] passable,
      final Executor builder,
      final LongSupplier nowNanos,
      final long ttlNanos,
      final int maxTransient,
      final int goalSnapRadius) {
    this(passable, passable, null, 0, 0.0, builder, nowNanos, ttlNanos, maxTransient, goalSnapRadius);
  }

  @SuppressWarnings("PMD.ExcessiveParameterList") // internal one-call-site ctor; threads cfg values.
  public AsyncNavigationFields(
      final boolean[][] passable,
      final boolean[][] routePassable,
      final int[][] clearance,
      final int hullFootprint,
      final double clearancePenalty,
      final Executor builder,
      final LongSupplier nowNanos,
      final long ttlNanos,
      final int maxTransient,
      final int goalSnapRadius) {
    this.passable = passable;
    this.routePassable = routePassable;
    this.clearance = clearance;
    this.hullFootprint = hullFootprint;
    this.clearancePenalty = clearancePenalty;
    this.builder = builder;
    this.nowNanos = nowNanos;
    this.ttlNanos = ttlNanos;
    this.maxTransient = maxTransient;
    this.goalSnapRadius = goalSnapRadius;
  }

  @Override
  public boolean lineOfSight(final int ax, final int ay, final int bx, final int by) {
    return infinity.ai.field.NavGrids.lineOfSight(this.passable, ax, ay, bx, by);
  }

  @Override
  public boolean passableAt(final int x, final int y) {
    return infinity.ai.field.NavGrids.passable(this.passable, x, y);
  }

  @Override
  public DistanceField fieldFor(final int rawGoalX, final int rawGoalY) {
    // Snap the goal to the nearest route-navigable (hull-fitting) cell so the Dijkstra computes and
    // bots head to the closest cell they fit — otherwise a flag in a tight slot is all-∞ ("unreach").
    final int[] g =
        infinity.ai.field.NavGrids.nearestPassable(
            this.routePassable, rawGoalX, rawGoalY, this.goalSnapRadius);
    final int goalX = g[0];
    final int goalY = g[1];
    final long k = key(goalX, goalY);
    Entry entry = this.cache.get(k);
    if (entry == null || entry.future.isCompletedExceptionally()) {
      entry = newEntry(goalX, goalY);
      this.cache.put(k, entry);
      enforceCap();
    }
    entry.lastAccessNanos = this.nowNanos.getAsLong();
    return snapshot(entry);
  }

  @Override
  public GradientField gradientFor(final int goalX, final int goalY) {
    return new FieldGradient(fieldFor(goalX, goalY));
  }

  /** Build (if absent) + pin a static goal so it survives TTL/LRU eviction (arena-lifetime field). */
  public void pin(final int goalX, final int goalY) {
    final long k = key(goalX, goalY);
    Entry entry = this.cache.get(k);
    if (entry == null || entry.future.isCompletedExceptionally()) {
      entry = newEntry(goalX, goalY);
      this.cache.put(k, entry);
    }
    entry.pinned = true;
    entry.lastAccessNanos = this.nowNanos.getAsLong();
  }

  @Override
  public void evict(final int goalX, final int goalY) {
    this.cache.remove(key(goalX, goalY));
  }

  /** Drop every cached field — used on {@code .lvl} reload and (coarse) door-state change. */
  public void evictAll() {
    this.cache.clear();
  }

  /** TTL sweep: drop non-pinned fields idle longer than the TTL. Call on a cadence. */
  public void evictExpired() {
    final long now = this.nowNanos.getAsLong();
    this.cache
        .entrySet()
        .removeIf(e -> !e.getValue().pinned && now - e.getValue().lastAccessNanos > this.ttlNanos);
  }

  /** Live cached-field count (pinned + transient); for tests + telemetry. */
  public int size() {
    return this.cache.size();
  }

  private Entry newEntry(final int goalX, final int goalY) {
    final CompletableFuture<DistanceField> future =
        CompletableFuture.supplyAsync(
            () ->
                new DijkstraDistanceField(
                    goalX,
                    goalY,
                    this.routePassable,
                    this.clearance,
                    this.hullFootprint,
                    this.clearancePenalty),
            this.builder);
    return new Entry(future, this.nowNanos.getAsLong());
  }

  /** Drop least-recently-accessed transient entries until within {@link #maxTransient}. */
  private void enforceCap() {
    long transient0 = this.cache.values().stream().filter(e -> !e.pinned).count();
    while (transient0 > this.maxTransient) {
      this.cache.entrySet().stream()
          .filter(e -> !e.getValue().pinned)
          .min(Comparator.comparingLong(e -> e.getValue().lastAccessNanos))
          .map(Map.Entry::getKey)
          .ifPresent(this.cache::remove);
      transient0--;
    }
  }

  /** {@link DistanceField#EMPTY} until the build completes; also on a failed future (rebuilt next request). */
  private static DistanceField snapshot(final Entry entry) {
    try {
      return entry.future.getNow(DistanceField.EMPTY);
    } catch (final RuntimeException ex) {
      return DistanceField.EMPTY;
    }
  }

  private static long key(final int goalX, final int goalY) {
    return (((long) goalX) << 32) ^ (goalY & 0xffffffffL);
  }

  /** Per-goal cache slot: the build future + last-access stamp (TTL/LRU) + pinned (static) flag. */
  private static final class Entry {
    private final CompletableFuture<DistanceField> future;
    private volatile long lastAccessNanos;
    private volatile boolean pinned;

    Entry(final CompletableFuture<DistanceField> future, final long nowNanos) {
      this.future = future;
      this.lastAccessNanos = nowNanos;
    }
  }
}
