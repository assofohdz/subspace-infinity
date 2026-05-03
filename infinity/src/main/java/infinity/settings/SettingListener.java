// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.settings;

import infinity.es.arena.ArenaId;

/**
 *
 * @author Asser Fahrenholz
 */
public interface SettingListener {

    void arenaSettingsChange(ArenaId arenaId, String section, String setting);

}
