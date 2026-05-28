// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.systems;

import com.simsilica.mathd.Vec3d;
import com.simsilica.mathd.Vec3i;
import com.simsilica.mblock.CellArray;
import com.simsilica.mworld.DataVersion;
import com.simsilica.mworld.LeafData;
import com.simsilica.mworld.LeafId;
import com.simsilica.mworld.LeafInfo;
import com.simsilica.mworld.db.LeafDb;
import infinity.map.BitMap;
import infinity.map.LevelFile;
import infinity.map.MapTypes;
import infinity.sim.internal.InfinityDefaultLeafWorld;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Isolates the map-swap cell-persistence bug. Replicates MapSystem's
 * createBlocksFromLegacyMap write pattern against an in-memory world, tracks
 * every written coordinate, then clears them via setWorldCell(pos, 0) and
 * asserts every tracked coordinate reads back as type 0.
 *
 * Entity-backed tiles (doors, asteroids, wormholes, flags) are skipped — the
 * test only exercises the world-cell paths that MapSystem's clear is supposed
 * to cover, so a failure here is a tracking miss, not an entity issue.
 */
public class MapSwapReproducerTest {

  private static final int SIZE = 1024;
  private static final int TILE_TYPE_BASE = 100;
  private static final int MAX_VISIBLE_TILE = 190;
  private static final int INVISIBLE_BLOCK_TYPE = 11;
  private static final int ANIMATED_ASTEROID_SMALL_BLOCK_TYPE = 13;
  private static final int ANIMATED_ASTEROID_MEDIUM_BLOCK_TYPE = 14;
  private static final int ANIMATED_ASTEROID_END_BLOCK_TYPE = 15;
  private static final int TYPE_MASK = 0x000fffff;

  @Test
  public void trenchLoadThenClear_allTrackedCellsReadZero() throws Exception {
    LevelFile map = loadLvl(resolveMap("trench.lvl"));
    runLoadThenClearTest("trench.lvl", map);
  }

  @Test
  public void trench2LoadThenClear_allTrackedCellsReadZero() throws Exception {
    LevelFile map = loadLvl(resolveMap("trench2.lvl"));
    runLoadThenClearTest("trench2.lvl", map);
  }

  private void runLoadThenClearTest(final String label, final LevelFile map) {
    InMemoryLeafDb leafDb = new InMemoryLeafDb();
    InfinityDefaultLeafWorld world = new InfinityDefaultLeafWorld(leafDb, 10);

    Vec3d arenaOffset = new Vec3d(0, 0, 0);
    Set<Vec3d> tracked = writeMapCells(world, map, arenaOffset);

    System.out.println("[" + label + "] tracked cells written: " + tracked.size());

    // Clear everything tracked
    for (final Vec3d pos : tracked) {
      world.setWorldCell(pos, 0);
    }

    // Verify: every tracked cell must now be zero-type
    int lingering = 0;
    int shown = 0;
    for (final Vec3d pos : tracked) {
      int raw = world.getWorldCell(pos);
      int type = raw & TYPE_MASK;
      if (type != 0) {
        if (shown < 10) {
          System.out.println("[" + label + "] still non-zero at " + pos
              + " type=" + type + " raw=0x" + Integer.toHexString(raw));
          shown++;
        }
        lingering++;
      }
    }

    if (lingering > 0) {
      fail("[" + label + "] expected 0 lingering cells, found "
          + lingering + " / " + tracked.size());
    }
  }

  /**
   * Reproduces the DoorSystem-style race: one thread clears a cell while another
   * simulates an entity-driven writer (e.g. {@link infinity.systems.DoorSystem})
   * continuously rewriting the same cell. We interleave clears with small
   * sleeps so the writer reliably gets scheduled between them, then count how
   * often the writer re-filled a cell we had just cleared. Any non-zero count
   * demonstrates the hazard that a "cleared" cell can be re-occupied before
   * MapSystem's swap completes.
   */
  @Test
  public void concurrentWriteDuringClear_writerRefillsClearedCells() throws Exception {
    InMemoryLeafDb leafDb = new InMemoryLeafDb();
    InfinityDefaultLeafWorld world = new InfinityDefaultLeafWorld(leafDb, 10);

    Vec3d target = new Vec3d(42, 1, 42);
    int writerType = 10;

    AtomicBoolean writerRun = new AtomicBoolean(true);
    CountDownLatch writerStarted = new CountDownLatch(1);
    Thread writer = new Thread(() -> {
      writerStarted.countDown();
      while (writerRun.get()) {
        world.setWorldCell(target, writerType);
      }
    }, "writer");
    writer.start();
    writerStarted.await();

    int rounds = 100;
    int refilled = 0;
    for (int i = 0; i < rounds; i++) {
      world.setWorldCell(target, 0);
      Thread.sleep(2);
      int typeAfter = world.getWorldCell(target) & TYPE_MASK;
      if (typeAfter == writerType) {
        refilled++;
      }
    }

    writerRun.set(false);
    writer.join(2000);

    System.out.println("[door-race] " + refilled + " / " + rounds
        + " cleared cells were re-filled by the concurrent writer before we could observe zero");

    assertTrue(
        "expected the concurrent writer to re-fill cleared cells at least once; got "
            + refilled + " / " + rounds,
        refilled > 0);
  }

  /**
   * Stress test for the mask-recalculation race. Multiple threads write to
   * adjacent cells in the same leaf; each thread sets its own cell to a unique
   * type. Since {@link com.simsilica.mblock.MaskUtils#recalculateSideMasks}
   * reads a neighbor's value and writes it back with updated mask bits, a read
   * on thread A can be clobbered by thread B's write, producing a cell whose
   * final type doesn't match either thread's last intended value.
   */
  @Test
  public void concurrentAdjacentWrites_maskRecalcCanClobberType() throws Exception {
    InMemoryLeafDb leafDb = new InMemoryLeafDb();
    InfinityDefaultLeafWorld world = new InfinityDefaultLeafWorld(leafDb, 10);

    int threadCount = 4;
    int iterations = 20_000;
    int[] types = {201, 202, 203, 204};
    Vec3d[] cells = {
        new Vec3d(10, 2, 10),
        new Vec3d(11, 2, 10),
        new Vec3d(10, 2, 11),
        new Vec3d(11, 2, 11),
    };

    CountDownLatch start = new CountDownLatch(1);
    CountDownLatch done = new CountDownLatch(threadCount);

    for (int i = 0; i < threadCount; i++) {
      final int idx = i;
      new Thread(() -> {
        try {
          start.await();
          for (int j = 0; j < iterations; j++) {
            world.setWorldCell(cells[idx], types[idx]);
          }
        } catch (final InterruptedException e) {
          Thread.currentThread().interrupt();
        } finally {
          done.countDown();
        }
      }, "writer-" + i).start();
    }

    start.countDown();
    assertTrue("threads did not finish in time", done.await(30, TimeUnit.SECONDS));

    StringBuilder mismatches = new StringBuilder();
    for (int i = 0; i < threadCount; i++) {
      int raw = world.getWorldCell(cells[i]);
      int type = raw & TYPE_MASK;
      System.out.println("[mask-race] " + cells[i] + " type=" + type
          + " expected=" + types[i]);
      if (type != types[i]) {
        mismatches.append(" cell=").append(cells[i])
            .append(" got=").append(type)
            .append(" expected=").append(types[i]);
      }
    }
    if (mismatches.length() > 0) {
      fail("concurrent mask-recalc clobbered one or more cell types:" + mismatches);
    }
  }

  @Test
  public void singleCellRoundTrip_writeThenClearReturnsZero() {
    InMemoryLeafDb leafDb = new InMemoryLeafDb();
    InfinityDefaultLeafWorld world = new InfinityDefaultLeafWorld(leafDb, 10);

    Vec3d pos = new Vec3d(5, 2, 7);
    world.setWorldCell(pos, TILE_TYPE_BASE + 50);
    assertEquals(TILE_TYPE_BASE + 50, world.getWorldCell(pos) & TYPE_MASK);

    world.setWorldCell(pos, 0);
    assertEquals(0, world.getWorldCell(pos) & TYPE_MASK);
  }

  /**
   * Mirrors the cell-writing portion of
   * {@link infinity.systems.MapSystem#createBlocksFromLegacyMap}. Entity-backed
   * tiles (turfFlag, asteroid-medium, asteroid-end, wormholes, doors) do not produce
   * world-cells and are skipped. Asteroid-small writes an mworld cell (block type
   * {@link #ANIMATED_ASTEROID_SMALL_BLOCK_TYPE}). Border tile
   * short-circuit is preserved.
   */
  // reproducer test enumerates all cell layouts
  @SuppressWarnings({"PMD.CognitiveComplexity", "PMD.CyclomaticComplexity", "PMD.NPathComplexity"})
  private Set<Vec3d> writeMapCells(
      final InfinityDefaultLeafWorld world, final LevelFile map, final Vec3d arenaOffset) {
    Set<Vec3d> coordinates = new HashSet<>();
    short[][] tiles = map.getMap();

    for (int xpos = 0; xpos < SIZE; xpos++) {
      for (int zpos = 0; zpos < SIZE; zpos++) {
        short s = tiles[SIZE - xpos - 1][SIZE - zpos - 1];
        if (s == 0) {
          continue;
        }
        int y = 1;
        if (isCorner3x3(xpos, zpos)) {
          y = 5;
        }

        Vec3d location = new Vec3d(xpos, y, zpos).add(arenaOffset);
        coordinates.add(location);

        // Entity-backed tiles: MapSystem continues without writing cells; match that.
        if (s == MapTypes.VIE_TURF_FLAG
            || (s >= MapTypes.VIE_V_DOOR_START && s <= MapTypes.VIE_H_DOOR_END)
            || s == MapTypes.VIE_WORMHOLE) {
          continue;
        }

        // Asteroid-end is a 4×4 decoration on the Y=2 overlay layer (no collider).
        if (s == MapTypes.VIE_ASTEROID_END) {
          final Vec3d overlay = new Vec3d(location.x, location.y + 1, location.z);
          world.setWorldCell(overlay, ANIMATED_ASTEROID_END_BLOCK_TYPE);
          coordinates.add(overlay);
          continue;
        }

        // Asteroid-small writes a 1×1 animated cell.
        if (s == MapTypes.VIE_ASTEROID_SMALL) {
          world.setWorldCell(location, ANIMATED_ASTEROID_SMALL_BLOCK_TYPE);
          continue;
        }

        // Asteroid-medium: animated visual cell + 3 invisible spillover cells for 2×2 collision.
        if (s == MapTypes.VIE_ASTEROID_MEDIUM) {
          world.setWorldCell(location, ANIMATED_ASTEROID_MEDIUM_BLOCK_TYPE);
          final Vec3d[] spillover = {
              new Vec3d(location.x + 1, location.y, location.z),
              new Vec3d(location.x,     location.y, location.z + 1),
              new Vec3d(location.x + 1, location.y, location.z + 1)
          };
          for (final Vec3d sp : spillover) {
            world.setWorldCell(sp, INVISIBLE_BLOCK_TYPE);
            coordinates.add(sp);
          }
          continue;
        }

        if (s == MapTypes.VIE_BORDER) {
          world.setWorldCell(location, INVISIBLE_BLOCK_TYPE);
        }

        int tileId = Short.toUnsignedInt(s);

        if (tileId >= 1 && tileId <= MAX_VISIBLE_TILE) {
          boolean isPassThrough =
              (tileId >= MapTypes.VIE_FLY_OVER_START && tileId <= MapTypes.VIE_FLY_OVER_END)
                  || (tileId >= MapTypes.VIE_FLY_UNDER_START && tileId <= MapTypes.VIE_FLY_UNDER_END);
          if (!isPassThrough) {
            world.setWorldCell(location, INVISIBLE_BLOCK_TYPE);
          } else {
            world.setWorldCell(location, 0);
          }
          Vec3d tileLocation = new Vec3d(location.x, location.y + 1, location.z);
          int tileBlockType = TILE_TYPE_BASE + tileId - 1;
          world.setWorldCell(tileLocation, tileBlockType);
          coordinates.add(tileLocation);
        } else {
          world.setWorldCell(location, INVISIBLE_BLOCK_TYPE);
        }
      }
    }
    return coordinates;
  }

  private static boolean isCorner3x3(final int xpos, final int zpos) {
    boolean xLow = xpos >= 0 && xpos <= 2;
    boolean xHigh = xpos >= SIZE - 3 && xpos <= SIZE - 1;
    boolean zLow = zpos >= 0 && zpos <= 2;
    boolean zHigh = zpos >= SIZE - 3 && zpos <= SIZE - 1;
    return (xLow && zLow) || (xLow && zHigh) || (xHigh && zLow) || (xHigh && zHigh);
  }

  private static File resolveMap(final String name) {
    String[] candidates = {
        "assets/Maps/" + name,
        "infinity/assets/Maps/" + name,
        "../infinity/assets/Maps/" + name,
    };
    for (final String c : candidates) {
      File f = new File(c);
      if (f.isFile()) {
        return f;
      }
    }
    throw new IllegalStateException(
        "Cannot locate " + name + " (cwd=" + System.getProperty("user.dir") + ")");
  }

  private static LevelFile loadLvl(final File file) throws IOException {
    BitMap bmp;
    try (InputStream is = Files.newInputStream(file.toPath());
         BufferedInputStream bis = new BufferedInputStream(is)) {
      bmp = new BitMap(bis);
      bmp.readBitMap(false);
    }
    try (InputStream is = Files.newInputStream(file.toPath());
         BufferedInputStream bis = new BufferedInputStream(is)) {
      LevelFile lvl = new LevelFile(bis, bmp, bmp.isBitMap(), bmp.hasELVL(), file.getName());
      String err = lvl.readLevel();
      if (err != null) {
        throw new IllegalStateException("readLevel failed: " + err);
      }
      return lvl;
    }
  }

  /**
   * Minimal LeafDb for tests: lazily creates empty leaves on demand and keeps
   * them in memory. Same-reference semantics — mutations to leaves persist
   * across loadLeaf calls, matching the real ColumnDb/LeafDbCache behavior.
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
      Vec3i world = leafId.getWorld(null);
      CellArray cells = new CellArray(LeafId.SIZE);
      return new LeafData(new LeafInfo(world, leafId, new DataVersion(0)), cells, LeafId.CELL_COUNT);
    }
  }
}
