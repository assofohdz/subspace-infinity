// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.field;

/** Per-arena access to flow fields keyed by goal cell; built + cached on request. See ADR-0011. */
public interface NavigationFields {

  /** Distance field for the goal cell, built on first request. */
  DistanceField fieldFor(int goalX, int goalY);

  /** Gradient (descent toward goal) of {@link #fieldFor(int, int)}. */
  GradientField gradientFor(int goalX, int goalY);

  /** Drop a cached field (transient goals). */
  void evict(int goalX, int goalY);
}
