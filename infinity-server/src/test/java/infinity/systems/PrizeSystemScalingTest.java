// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.systems;

import static org.junit.Assert.assertEquals;

import com.simsilica.es.EntityId;
import com.simsilica.es.base.DefaultEntityData;
import infinity.es.arena.ArenaId;
import java.util.List;
import org.junit.Test;

/**
 * Unit coverage for the Slice 8d math/scoping helpers extracted from
 * {@link PrizeSystem#update}. Each helper is a pure function on primitives
 * (or, for {@link PrizeSystem#countPlayersInArena}, on a tiny synthetic
 * {@link DefaultEntityData}), so no system manager fixture is needed.
 *
 * <p>Heavier integration coverage (full {@code PrizeSystem} update loop with
 * real spawners + ships + ConfigRegistry) sits with the broader
 * spawn-projection harness backlog — these helpers cover the seams most
 * likely to harbour bugs (per-arena scoping, off-by-one in regen-batch
 * capping).
 */
public class PrizeSystemScalingTest {

  private static final String ZONE_TRENCH = "trench";

  // ──────────────────────────────────────────────────────────────────────
  // computeEffectiveMaxCount — additive Slice 8d cap
  // ──────────────────────────────────────────────────────────────────────

  @Test
  public void effectiveMaxCount_noScalingFlat() {
    // countPerPlayer=0 collapses scaling regardless of player count.
    assertEquals(5, PrizeSystem.computeEffectiveMaxCount(5, 0, 100));
  }

  @Test
  public void effectiveMaxCount_zeroPlayers_returnsBase() {
    // Empty arena: scaling collapses to the base cap.
    assertEquals(5, PrizeSystem.computeEffectiveMaxCount(5, 2, 0));
  }

  @Test
  public void effectiveMaxCount_additive() {
    // base + perPlayer × players: 5 + 2×4 = 13.
    assertEquals(13, PrizeSystem.computeEffectiveMaxCount(5, 2, 4));
  }

  @Test
  public void effectiveMaxCount_zeroBase_pureScaling() {
    // Subspace canon-style "purely scaled": maxCount=0 → count = perPlayer × players.
    assertEquals(40, PrizeSystem.computeEffectiveMaxCount(0, 10, 4));
  }

  // ──────────────────────────────────────────────────────────────────────
  // computeEffectiveRadius — additive Slice 8d radius
  // ──────────────────────────────────────────────────────────────────────

  @Test
  public void effectiveRadius_noScalingFlat() {
    assertEquals(100.0, PrizeSystem.computeEffectiveRadius(100.0, 0.0, 100), 0.0);
  }

  @Test
  public void effectiveRadius_zeroPlayers_returnsBase() {
    assertEquals(100.0, PrizeSystem.computeEffectiveRadius(100.0, 50.0, 0), 0.0);
  }

  @Test
  public void effectiveRadius_additive() {
    assertEquals(300.0, PrizeSystem.computeEffectiveRadius(100.0, 50.0, 4), 0.0);
  }

  // ──────────────────────────────────────────────────────────────────────
  // computeRegenAmount — batch capping
  // ──────────────────────────────────────────────────────────────────────

  @Test
  public void regenAmount_capsAtBatch_whenDeficitLarger() {
    // alive=0, cap=20, batch=3 → spawn 3 (batch caps).
    assertEquals(3, PrizeSystem.computeRegenAmount(3, 0, 20));
  }

  @Test
  public void regenAmount_capsAtDeficit_whenBatchLarger() {
    // alive=5, cap=10, batch=20 → spawn 5 (deficit caps).
    assertEquals(5, PrizeSystem.computeRegenAmount(20, 5, 10));
  }

  @Test
  public void regenAmount_atCap_returnsZero() {
    // alive=10, cap=10 → no deficit, no spawn.
    assertEquals(0, PrizeSystem.computeRegenAmount(5, 10, 10));
  }

  @Test
  public void regenAmount_overCap_returnsZero() {
    // alive>cap (e.g. arena player count just dropped) → no spawn until
    // pickups/decay drain the surplus.
    assertEquals(0, PrizeSystem.computeRegenAmount(5, 12, 10));
  }

  @Test
  public void regenAmount_clampsBatchAtLeastOne() {
    // Misconfigured regenBatch=0: clamp up to 1 so the spawner still
    // trickles rather than freezing entirely.
    assertEquals(1, PrizeSystem.computeRegenAmount(0, 0, 10));
  }

  // ──────────────────────────────────────────────────────────────────────
  // countPlayersInArena — per-arena scoping
  // ──────────────────────────────────────────────────────────────────────

  @Test
  public void countPlayersInArena_nullArena_returnsZero() {
    // Legacy spawner without an ArenaId tag → no scaling, no count.
    final DefaultEntityData ed = new DefaultEntityData();
    final EntityId trench1 = ed.createEntity();
    ed.setComponent(trench1, new ArenaId(ZONE_TRENCH, EntityId.NULL_ID));

    assertEquals(0, PrizeSystem.countPlayersInArena(ed, List.of(trench1), null));
  }

  @Test
  public void countPlayersInArena_emptyShips_returnsZero() {
    final DefaultEntityData ed = new DefaultEntityData();
    assertEquals(
        0,
        PrizeSystem.countPlayersInArena(
            ed, List.of(), new ArenaId(ZONE_TRENCH, EntityId.NULL_ID)));
  }

  @Test
  public void countPlayersInArena_scopesByArenaName() {
    // The most important assertion: ships in a different arena MUST NOT
    // count toward this arena's spawner-scaling. Off-by-one or cross-arena
    // leak here would surface as "deva spawners scale with trench
    // playerload".
    final DefaultEntityData ed = new DefaultEntityData();
    final EntityId trench1 = ed.createEntity();
    final EntityId trench2 = ed.createEntity();
    final EntityId deva1 = ed.createEntity();
    final EntityId noArena = ed.createEntity();
    ed.setComponent(trench1, new ArenaId(ZONE_TRENCH, EntityId.NULL_ID));
    ed.setComponent(trench2, new ArenaId(ZONE_TRENCH, EntityId.NULL_ID));
    ed.setComponent(deva1, new ArenaId("deva", EntityId.NULL_ID));
    // noArena: no ArenaId → must not count anywhere.

    final List<EntityId> all = List.of(trench1, trench2, deva1, noArena);

    assertEquals(
        2, PrizeSystem.countPlayersInArena(ed, all, new ArenaId(ZONE_TRENCH, EntityId.NULL_ID)));
    assertEquals(
        1, PrizeSystem.countPlayersInArena(ed, all, new ArenaId("deva", EntityId.NULL_ID)));
    // Arena that no ship is in: zero, even though ships exist.
    assertEquals(
        0, PrizeSystem.countPlayersInArena(ed, all, new ArenaId("svs", EntityId.NULL_ID)));
  }

  @Test
  public void countPlayersInArena_arenaIdEntityIdIgnored() {
    // ArenaId carries (arenaName, arenaEntityId). Only the name matters for
    // scoping — the entity id field shouldn't gate equality. A spawner
    // tagged with one (arenaName, arenaEntityId=N) must still count ships
    // tagged with (arenaName, arenaEntityId=M).
    final DefaultEntityData ed = new DefaultEntityData();
    final EntityId ship1 = ed.createEntity();
    final EntityId ship2 = ed.createEntity();
    ed.setComponent(ship1, new ArenaId(ZONE_TRENCH, new EntityId(42)));
    ed.setComponent(ship2, new ArenaId(ZONE_TRENCH, new EntityId(99)));

    assertEquals(
        2,
        PrizeSystem.countPlayersInArena(
            ed, List.of(ship1, ship2), new ArenaId(ZONE_TRENCH, new EntityId(7))));
  }
}
