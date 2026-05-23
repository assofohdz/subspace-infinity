// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.brain;

import java.util.HashMap;
import java.util.Map;

/** Named lookup of {@link BrainArchetype}s. BotBrainSystem owns one instance. */
public final class BrainRegistry {

  private final Map<String, BrainArchetype> archetypes = new HashMap<>();

  public void register(final BrainArchetype archetype) {
    this.archetypes.put(archetype.name(), archetype);
  }

  public BrainArchetype get(final String name) {
    final BrainArchetype archetype = this.archetypes.get(name);
    if (archetype == null) {
      throw new IllegalArgumentException("Unknown brain archetype: " + name);
    }
    return archetype;
  }
}
