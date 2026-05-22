// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es;

import com.simsilica.es.EntityComponent;

/**
 * Server-only holder for the per-bot brain instance + blackboard. v1: marker only; projected
 * fields (archetype name, tunables snapshot) land in a later slice. See ADR-0009.
 */
public final class BotBrain implements EntityComponent {

  public BotBrain() {
    // marker — projected fields land in a later slice
  }
}
