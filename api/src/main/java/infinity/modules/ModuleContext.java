// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.modules;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import infinity.es.arena.ArenaId;
import infinity.sim.ChatHostedPoster;
import infinity.sim.PhysicsManager;
import javax.annotation.Nullable;

/**
 * Construction-time bundle for modules. {@code arenaEntity} is the {@link EntityId}
 * of the arena entity the module is hosted on — modules use it to stamp per-arena
 * state ({@code RoundNumber}, {@code RoundEndPending}, {@code ScoreReset}).
 * {@code chat} + {@code physics} are {@code @Nullable} so unit tests can pass
 * {@code null} when the dependency isn't under test; production wiring always
 * supplies real impls.
 *
 * <p>F1 minimum was {@code (arenaId, arenaEntity, ed)}; extend as concrete modules
 * need additional services.
 */
public record ModuleContext(
    ArenaId arenaId,
    EntityId arenaEntity,
    EntityData ed,
    @Nullable ChatHostedPoster chat,
    @Nullable PhysicsManager physics,
    @Nullable ArenaModuleSetLookup modules) {

  /** Test-friendly constructor — defaults {@code modules} to {@code null}. */
  public ModuleContext(
      final ArenaId arenaId,
      final EntityId arenaEntity,
      final EntityData ed,
      @Nullable final ChatHostedPoster chat,
      @Nullable final PhysicsManager physics) {
    this(arenaId, arenaEntity, ed, chat, physics, null);
  }
}
