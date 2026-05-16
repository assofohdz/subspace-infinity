// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.sim;

/** Opt-in companion to {@link ArenaModule} for live config reload; modules without it stay simple. */
public interface Reloadable<C> {

  void onConfigReloaded(C newConfig);
}
