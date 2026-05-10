// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.sim;

/**
 * Manager interface for arena state lookup.
 *
 * @author Asser Fahrenholz
 */
public interface ArenaManager {

    String getDefaultArenaId();

    String[] getActiveArenas();
}
