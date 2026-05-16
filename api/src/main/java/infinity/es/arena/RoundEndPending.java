// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es.arena;

import com.simsilica.es.EntityComponent;

/** Transient marker on the arena entity — signals {@code ArenaLifecycleDispatcherSystem} to run round-end on the next tick. Drained by the dispatcher. */
public final class RoundEndPending implements EntityComponent {

  public RoundEndPending() {
    // marker; empty ctor for serialization
  }
}
