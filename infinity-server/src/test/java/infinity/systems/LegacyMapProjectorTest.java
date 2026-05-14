// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.systems;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.simsilica.es.EntityId;
import com.simsilica.es.base.DefaultEntityData;
import com.simsilica.mathd.Grid;
import com.simsilica.mathd.Vec3d;
import com.simsilica.mathd.Vec3i;
import com.simsilica.mblock.CellArray;
import com.simsilica.mblock.phys.MBlockShape;
import com.simsilica.mphys.PhysicsSpace;
import com.simsilica.mworld.DataVersion;
import com.simsilica.mworld.LeafData;
import com.simsilica.mworld.LeafId;
import com.simsilica.mworld.LeafInfo;
import com.simsilica.mworld.db.LeafDb;
import infinity.InfinityConstants;
import infinity.config.EngineConfig;
import infinity.es.Door;
import infinity.es.Flag;
import infinity.es.GravityWell;
import infinity.map.BitMap;
import infinity.map.LevelFile;
import infinity.map.MapTypes;
import infinity.sim.internal.InfinityDefaultLeafWorld;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit coverage for {@link LegacyMapProjector} — the per-tile projection
 * strategy carved out of {@code MapSystem} as an "independently testable
 * unit" (per the class Javadoc). Pins the contract Javadoc claims:
 *
 * <ul>
 *   <li>Cell-backed visible tiles (1..{@link InfinityConstants#MAX_VISIBLE_TILE})
 *       become a single world cell at Y=1 with block type
 *       {@code arenaTileBase + tileId - 1}.</li>
 *   <li>Out-of-range tile ids (incl. unmapped 191..215, station 219, internal
 *       ids ≥ 221) fall through to {@link InfinityConstants#INVISIBLE_BLOCK_TYPE}.</li>
 *   <li>Entity-backed tiles (turf flags, asteroids, doors, wormholes) spawn
 *       their {@code MapFactory} entity and leave the world cell empty.</li>
 *   <li>Every touched location is included in the returned coordinate set
 *       (entity positions + cell-write positions) so the unload pass can zero
 *       the slots cleanly.</li>
 *   <li>The {@code arenaOffset} is added to every emitted location.</li>
 * </ul>
 *
 * <p>Coordinate-frame caveat: the projector reads
 * {@code tiles[extentX - xpos][extentZ - zpos]} (so iteration var {@code xpos}
 * becomes the world X). Test fixtures place tiles via {@link #placeTile} which
 * inverts that indexing so callers can think in world coordinates.
 */
public class LegacyMapProjectorTest {

  /**
   * Mask matches {@code MaskUtils.TYPE_MASK} — {@link com.simsilica.mblock.MaskUtils}
   * packs side-mask bits into the upper bits of a stored cell value, so reads back
   * via {@link com.simsilica.mworld.World#getWorldCell} need this mask to recover
   * the bare type. Matches the mask used in {@link MapSwapReproducerTest}.
   */
  private static final int TYPE_MASK = 0x000fffff;
  private static final int LVL_SIZE = 1024;
  private static final int ARENA_TILE_BASE = InfinityConstants.arenaTileBase(0);
  private static final int TEST_GRID_SPACING = 1024;

  private DefaultEntityData ed;
  private InfinityDefaultLeafWorld world;
  private LegacyMapProjector projector;

  @Before
  public void setUp() {
    ed = new DefaultEntityData();
    final PhysicsSpace<EntityId, MBlockShape> phys =
        new PhysicsSpace<>(new Grid(TEST_GRID_SPACING));
    world = new InfinityDefaultLeafWorld(new InMemoryLeafDb(), 10);
    projector = new LegacyMapProjector(ed, phys, world, () -> EngineConfig.DEFAULTS);
  }

  @Test
  public void project_visibleTile_writesArenaTileBaseBlockType() {
    final LevelFile lvl = newEmptyLvl();
    placeTile(lvl, /*worldX=*/0, /*worldZ=*/0, (short) 50);

    final Set<Vec3d> coords = projector.project(lvl, Vec3d.ZERO, ARENA_TILE_BASE, 0L);

    final Vec3d cell = new Vec3d(0, 1, 0);
    assertTrue("returned coordinate set must include the written cell", coords.contains(cell));
    assertEquals(
        "visible tile id 50 must project to arenaTileBase + tileId - 1",
        ARENA_TILE_BASE + 49, world.getWorldCell(cell) & TYPE_MASK);
  }

  @Test
  public void project_outOfRangeTileId_writesInvisibleBlockType() {
    final LevelFile lvl = newEmptyLvl();
    // VIE_STATION (id 219) — not entity-backed in the projector AND > MAX_VISIBLE_TILE,
    // so falls to the INVISIBLE_BLOCK_TYPE fallback per writeTileCell's contract.
    placeTile(lvl, /*worldX=*/5, /*worldZ=*/7, MapTypes.VIE_STATION);

    projector.project(lvl, Vec3d.ZERO, ARENA_TILE_BASE, 0L);

    final Vec3d cell = new Vec3d(5, 1, 7);
    assertEquals(
        "out-of-range tile id must project to INVISIBLE_BLOCK_TYPE",
        InfinityConstants.INVISIBLE_BLOCK_TYPE, world.getWorldCell(cell) & TYPE_MASK);
  }

  @Test
  public void project_turfFlag_spawnsFlagEntityAndLeavesCellEmpty() {
    final LevelFile lvl = newEmptyLvl();
    placeTile(lvl, /*worldX=*/3, /*worldZ=*/4, MapTypes.VIE_TURF_FLAG);

    final Set<Vec3d> coords = projector.project(lvl, Vec3d.ZERO, ARENA_TILE_BASE, 100L);

    final Vec3d location = new Vec3d(3, 1, 4);
    assertTrue("entity-backed tile must still register its location", coords.contains(location));
    assertEquals(
        "entity-backed tile must NOT write a world cell",
        0, world.getWorldCell(location) & TYPE_MASK);
    assertEquals(
        "exactly one Flag entity must be spawned for the turf flag tile",
        1, ed.getEntities(Flag.class).size());
  }

  @Test
  public void project_doorAndWormhole_spawnEntitiesNotCells() {
    final LevelFile lvl = newEmptyLvl();
    placeTile(lvl, /*worldX=*/10, /*worldZ=*/10, MapTypes.VIE_V_DOOR_START);
    placeTile(lvl, /*worldX=*/20, /*worldZ=*/20, MapTypes.VIE_WORMHOLE);

    projector.project(lvl, Vec3d.ZERO, ARENA_TILE_BASE, 100L);

    assertEquals("door tile must spawn one Door entity", 1, ed.getEntities(Door.class).size());
    // Wormhole stamps a GravityWell on the wormhole entity and a separate WarpTouch
    // sensor — see MapFactory.createWormhole. The single GravityWell uniquely
    // counts the wormhole entity itself.
    assertEquals(
        "wormhole tile must spawn one GravityWell entity",
        1, ed.getEntities(GravityWell.class).size());
    assertEquals(
        "door cell must remain empty (entity-backed tile)",
        0, world.getWorldCell(new Vec3d(10, 1, 10)) & TYPE_MASK);
    assertEquals(
        "wormhole cell must remain empty (entity-backed tile)",
        0, world.getWorldCell(new Vec3d(20, 1, 20)) & TYPE_MASK);
  }

  @Test
  public void project_arenaOffsetApplied_returnedCoordsAndCellWritesShifted() {
    final LevelFile lvl = newEmptyLvl();
    placeTile(lvl, /*worldX=*/0, /*worldZ=*/0, (short) 75);
    final Vec3d offset = new Vec3d(2048, 0, 4096);

    final Set<Vec3d> coords = projector.project(lvl, offset, ARENA_TILE_BASE, 0L);

    final Vec3d shifted = new Vec3d(0, 1, 0).add(offset);
    assertTrue(
        "returned coordinate set must reflect the arena offset",
        coords.contains(shifted));
    assertEquals(
        "cell write must land at the offset-translated coordinate",
        ARENA_TILE_BASE + 74, world.getWorldCell(shifted) & TYPE_MASK);
  }

  @Test
  public void project_emptyMap_returnsEmptyCoordinatesAndSpawnsNothing() {
    final LevelFile lvl = newEmptyLvl();

    final Set<Vec3d> coords = projector.project(lvl, Vec3d.ZERO, ARENA_TILE_BASE, 0L);

    assertNotNull("project must always return a non-null coordinate set", coords);
    assertTrue("empty map must produce no touched coordinates", coords.isEmpty());
    assertEquals(
        "empty map must spawn no Flag entities", 0, ed.getEntities(Flag.class).size());
    assertEquals(
        "empty map must spawn no Door entities", 0, ed.getEntities(Door.class).size());
    assertEquals(
        "empty map must spawn no GravityWell entities",
        0, ed.getEntities(GravityWell.class).size());
  }

  /* -------------------------- fixture helpers ----------------------------- */

  /**
   * Construct an empty 1024×1024 {@link LevelFile} suitable for direct
   * {@link LevelFile#getMap} mutation. We bypass the file-IO constructor
   * (no stream needed) and use the {@code BitMap}-only constructor; the
   * tile array is allocated in the field initializer to all-zero, which
   * means every cell is {@code VIE_NO_TILE} (skipped by the projector).
   */
  private static LevelFile newEmptyLvl() {
    final LevelFile lvl = new LevelFile(new BitMap(null));
    lvl.setMapName("test.lvl");
    return lvl;
  }

  /**
   * Place {@code tileId} at world coordinate {@code (worldX, worldZ)}.
   * Inverts the projector's {@code tiles[extentX - xpos][extentZ - zpos]}
   * read order so callers can reason in world coordinates.
   */
  private static void placeTile(
      final LevelFile lvl, final int worldX, final int worldZ, final short tileId) {
    lvl.getMap()[LVL_SIZE - 1 - worldX][LVL_SIZE - 1 - worldZ] = tileId;
  }

  /**
   * Minimal in-memory {@link LeafDb} for tests; mirrors the same-named helper
   * in {@link MapSwapReproducerTest}.
   */
  private static final class InMemoryLeafDb implements LeafDb {
    private final Map<LeafId, LeafData> leaves = new ConcurrentHashMap<>();

    @Override
    public LeafData loadLeaf(final LeafId leafId) {
      return leaves.computeIfAbsent(leafId, this::createEmpty);
    }

    @Override
    public void storeLeaf(final LeafData leaf) {
      leaves.put(leaf.getInfo().leafId, leaf);
    }

    private LeafData createEmpty(final LeafId leafId) {
      final Vec3i worldOrigin = leafId.getWorld(null);
      final CellArray cells = new CellArray(LeafId.SIZE);
      return new LeafData(
          new LeafInfo(worldOrigin, leafId, new DataVersion(0)), cells, LeafId.CELL_COUNT);
    }
  }
}
