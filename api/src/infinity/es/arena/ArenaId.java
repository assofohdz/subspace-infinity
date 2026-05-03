// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.es.arena;

import com.simsilica.es.EntityComponent;
import com.simsilica.es.EntityId;

/**
 * Component tying an entity to a specific arena instance.
 *
 * @author Asser
 */
public class ArenaId implements EntityComponent {

  private final String arena;
  private final EntityId owner;

  // For serialization
  public ArenaId() {
    this(null, null);
  }

  public ArenaId(final String arenaId, final EntityId owner) {
    this.arena = arenaId;
    this.owner = owner;
  }

  public String getArena() {
    return arena;
  }

  public EntityId getOwner() {
    return owner;
  }
}
