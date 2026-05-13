// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.systems;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.simsilica.mathd.Vec3d;
import com.simsilica.mathd.Vec3i;
import com.simsilica.mblock.CellArray;
import com.simsilica.mworld.DataVersion;
import com.simsilica.mworld.LeafData;
import com.simsilica.mworld.LeafId;
import com.simsilica.mworld.LeafInfo;
import com.simsilica.mworld.db.LeafDb;
import infinity.InfinityConstants;
import infinity.map.MapTypes;
import infinity.sim.CoreViewConstants;
import infinity.sim.internal.InfinityDefaultLeafWorld;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.Test;

/**
 * Unit coverage for {@link WallLightDecorator} — the wall-run light-emitter
 * decorator extracted from {@code MapSystem} as an "independently testable
 * strategy" (per the class Javadoc). Pins the contract Javadoc claims:
 * straight wall runs of {@link CoreViewConstants#WALL_LIGHT_MIN_RUN}+ tiles
 * (horizontal or vertical) emit {@link InfinityConstants#LIGHT_EMITTER_BLOCK_TYPE}
 * cells at the emitter plane, spaced approximately
 * {@link CoreViewConstants#WALL_LIGHT_SPACING} tiles apart along the run, with
 * non-wall tiles and short runs producing no emitters.
 *
 * <p>Coordinate-frame caveat: {@link WallLightDecorator#decorate} reads
 * {@code tiles[extentX - x][extentZ - z]} (matching {@link LegacyMapProjector}'s
 * inverted indexing), so test fixtures place wall tiles at
 * {@code tiles[size - 1 - worldX][size - 1 - worldZ]} to land at world
 * coordinate {@code (worldX, _, worldZ)}.
 */
public class WallLightDecoratorTest {

  private static final int TYPE_MASK = 0x000fffff;
  private static final int LIGHT_TYPE = InfinityConstants.LIGHT_EMITTER_BLOCK_TYPE;
  private static final int FIXTURE_SIZE = 64;
  private static final int LIGHT_Y = (int) Math.round(CoreViewConstants.WALL_LIGHT_PLANE_Y);

  @Test
  public void decorate_emptyTileGrid_emitsNoLightsAndTouchesNoCoordinates() {
    final short[][] tiles = new short[FIXTURE_SIZE][FIXTURE_SIZE];
    final InfinityDefaultLeafWorld world = newWorld();
    final Set<Vec3d> coordinates = new HashSet<>();

    WallLightDecorator.decorate(tiles, Vec3d.ZERO, world, coordinates);

    assertTrue("empty tile grid must not record any cell coordinates", coordinates.isEmpty());
  }

  @Test
  public void decorate_runShorterThanMinimum_emitsNoLights() {
    // 12-tile horizontal run — one short of WALL_LIGHT_MIN_RUN (13).
    final int runLength = CoreViewConstants.WALL_LIGHT_MIN_RUN - 1;
    final short[][] tiles = horizontalWallRun(FIXTURE_SIZE, /*z=*/10, /*startX=*/5, runLength);
    final InfinityDefaultLeafWorld world = newWorld();
    final Set<Vec3d> coordinates = new HashSet<>();

    WallLightDecorator.decorate(tiles, Vec3d.ZERO, world, coordinates);

    assertTrue(
        "sub-threshold run must produce no emitter coordinates",
        coordinates.isEmpty());
  }

  @Test
  public void decorate_horizontalRunAtThreshold_emitsExactlyOneLightOnPlane() {
    // 13-tile horizontal run starting at x=5 along z=10; spacing 25 → exactly one light.
    final int runLength = CoreViewConstants.WALL_LIGHT_MIN_RUN;
    final int startX = 5;
    final int rowZ = 10;
    final short[][] tiles = horizontalWallRun(FIXTURE_SIZE, rowZ, startX, runLength);
    final InfinityDefaultLeafWorld world = newWorld();
    final Set<Vec3d> coordinates = new HashSet<>();

    WallLightDecorator.decorate(tiles, Vec3d.ZERO, world, coordinates);

    assertEquals("threshold horizontal run must emit one light", 1, coordinates.size());
    final Vec3d light = coordinates.iterator().next();
    assertEquals("light Y must sit on WALL_LIGHT_PLANE_Y", LIGHT_Y, (int) light.y);
    assertEquals("light Z must match the wall row", rowZ, (int) light.z);
    assertTrue(
        "light X must fall within the run span [startX..startX+len-1]",
        light.x >= startX && light.x < startX + runLength);
    assertEquals(
        "world cell at the emitter coord must carry LIGHT_EMITTER_BLOCK_TYPE",
        LIGHT_TYPE, world.getWorldCell(light) & TYPE_MASK);
  }

  @Test
  public void decorate_verticalRunAtThreshold_emitsExactlyOneLightOnPlane() {
    // 13-tile vertical run at x=20, z=4..16 — exercises the column-scan branch.
    final int runLength = CoreViewConstants.WALL_LIGHT_MIN_RUN;
    final int colX = 20;
    final int startZ = 4;
    final short[][] tiles = verticalWallRun(FIXTURE_SIZE, colX, startZ, runLength);
    final InfinityDefaultLeafWorld world = newWorld();
    final Set<Vec3d> coordinates = new HashSet<>();

    WallLightDecorator.decorate(tiles, Vec3d.ZERO, world, coordinates);

    assertEquals("threshold vertical run must emit one light", 1, coordinates.size());
    final Vec3d light = coordinates.iterator().next();
    assertEquals("light X must match the wall column", colX, (int) light.x);
    assertEquals("light Y must sit on WALL_LIGHT_PLANE_Y", LIGHT_Y, (int) light.y);
    assertTrue(
        "light Z must fall within the run span [startZ..startZ+len-1]",
        light.z >= startZ && light.z < startZ + runLength);
  }

  @Test
  public void decorate_longHorizontalRun_emitsMultipleLightsAlongTheRun() {
    // 50-tile run at spacing 25 → round(50/25) = 2 emitters.
    final int runLength = CoreViewConstants.WALL_LIGHT_SPACING * 2;
    final int startX = 0;
    final int rowZ = 8;
    final short[][] tiles = horizontalWallRun(FIXTURE_SIZE, rowZ, startX, runLength);
    final InfinityDefaultLeafWorld world = newWorld();
    final Set<Vec3d> coordinates = new HashSet<>();

    WallLightDecorator.decorate(tiles, Vec3d.ZERO, world, coordinates);

    assertEquals(
        "long run must emit ceil(len / spacing) lights along the run",
        2, coordinates.size());
    for (final Vec3d light : coordinates) {
      assertEquals("each emitter must sit on the wall row", rowZ, (int) light.z);
      assertEquals("each emitter must sit on WALL_LIGHT_PLANE_Y", LIGHT_Y, (int) light.y);
      assertTrue(
          "each emitter X must fall within the run span",
          light.x >= startX && light.x < startX + runLength);
      assertEquals(
          "each emitter cell must carry LIGHT_EMITTER_BLOCK_TYPE",
          LIGHT_TYPE, world.getWorldCell(light) & TYPE_MASK);
    }
  }

  @Test
  public void decorate_arenaOffsetApplied_emittersTranslatedByOffset() {
    final int runLength = CoreViewConstants.WALL_LIGHT_MIN_RUN;
    final int startX = 5;
    final int rowZ = 10;
    final short[][] tiles = horizontalWallRun(FIXTURE_SIZE, rowZ, startX, runLength);
    final InfinityDefaultLeafWorld world = newWorld();
    final Set<Vec3d> coordinates = new HashSet<>();
    final Vec3d offset = new Vec3d(1024, 0, 2048);

    WallLightDecorator.decorate(tiles, offset, world, coordinates);

    assertEquals(1, coordinates.size());
    final Vec3d light = coordinates.iterator().next();
    assertTrue(
        "light X must fall within the offset-translated run span",
        light.x >= startX + offset.x && light.x < startX + runLength + offset.x);
    assertEquals(
        "light Z must equal rowZ + offset.z (offset is added to the cell-frame coord)",
        rowZ + offset.z, light.z, 0.0);
    assertEquals(
        "light Y must equal WALL_LIGHT_PLANE_Y + offset.y",
        LIGHT_Y + offset.y, light.y, 0.0);
  }

  @Test
  public void decorate_nonWallTilesIgnored_noEmittersFromGoalsOrFlags() {
    // Place a 13-tile "run" of vieGoalArea tiles (172) — outside the wall range
    // [vieNormalStart..vieNormalEnd] = [1..161], so the wall mask stays empty
    // and no emitter is produced even though the geometry would otherwise qualify.
    final int runLength = CoreViewConstants.WALL_LIGHT_MIN_RUN;
    final short[][] tiles = new short[FIXTURE_SIZE][FIXTURE_SIZE];
    final int rowZ = 10;
    final int startX = 5;
    for (int i = 0; i < runLength; i++) {
      placeTile(tiles, startX + i, rowZ, MapTypes.vieGoalArea);
    }
    final InfinityDefaultLeafWorld world = newWorld();
    final Set<Vec3d> coordinates = new HashSet<>();

    WallLightDecorator.decorate(tiles, Vec3d.ZERO, world, coordinates);

    assertTrue(
        "non-wall tiles must not contribute to wall runs",
        coordinates.isEmpty());
    assertFalse(
        "no emitter cell should have been written into the world",
        coordinates.iterator().hasNext());
  }

  /* -------------------------- fixture helpers ----------------------------- */

  /**
   * Build a tile grid of {@code size×size} with a horizontal wall run of
   * {@code length} cells starting at {@code (startX, z)} in <em>world</em>
   * coordinates. Wall tile id is {@link MapTypes#vieNormalStart} (id 1, in the
   * wall range). Indexing is inverted to match the decorator's
   * {@code tiles[extent - x][extent - z]} read order.
   */
  private static short[][] horizontalWallRun(
      final int size, final int z, final int startX, final int length) {
    final short[][] tiles = new short[size][size];
    for (int i = 0; i < length; i++) {
      placeTile(tiles, startX + i, z, MapTypes.vieNormalStart);
    }
    return tiles;
  }

  /** Mirror of {@link #horizontalWallRun} for the column-scan code path. */
  private static short[][] verticalWallRun(
      final int size, final int x, final int startZ, final int length) {
    final short[][] tiles = new short[size][size];
    for (int i = 0; i < length; i++) {
      placeTile(tiles, x, startZ + i, MapTypes.vieNormalStart);
    }
    return tiles;
  }

  private static void placeTile(
      final short[][] tiles, final int worldX, final int worldZ, final short tileId) {
    final int extentX = tiles.length - 1;
    final int extentZ = tiles[0].length - 1;
    tiles[extentX - worldX][extentZ - worldZ] = tileId;
  }

  private static InfinityDefaultLeafWorld newWorld() {
    return new InfinityDefaultLeafWorld(new InMemoryLeafDb(), 10);
  }

  /**
   * Minimal in-memory {@link LeafDb} for tests; mirrors the same-named helper
   * in {@link MapSwapReproducerTest}. Lazily creates empty leaves on demand
   * and keeps them in memory; mutations persist across {@code loadLeaf} calls
   * (same-reference semantics, matching real {@code ColumnDb} / leaf-cache).
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
      final Vec3i world = leafId.getWorld(null);
      final CellArray cells = new CellArray(LeafId.SIZE);
      return new LeafData(
          new LeafInfo(world, leafId, new DataVersion(0)), cells, LeafId.CELL_COUNT);
    }
  }
}
