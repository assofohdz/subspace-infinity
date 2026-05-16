// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.modules;

import com.simsilica.es.EntityData;
import infinity.es.arena.ArenaId;

/** Construction-time bundle for modules. F1 minimum; extend as concrete modules need services. */
public record ModuleContext(ArenaId arenaId, EntityData ed) {}
