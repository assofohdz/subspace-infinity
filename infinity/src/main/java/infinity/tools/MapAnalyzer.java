// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.tools;

import infinity.map.BitMap;
import infinity.map.LevelFile;
import infinity.map.MapTypes;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Standalone map inspector — loads one or two Subspace .lvl files directly via
 * {@link LevelFile} (no JME AssetManager) and reports tile counts, category
 * breakdowns, and (for two maps) a position-level diff showing which cells are
 * unique to each map. Useful for diagnosing why a swapMap leaves cells behind:
 * the "unique to old" set is what must be cleared successfully.
 *
 * Run via: ./gradlew :infinity:analyzeMaps --args "trench.lvl trench2.lvl"
 */
public final class MapAnalyzer {

  private static final int SIZE = 1024;

  private MapAnalyzer() {}

  public static void main(String[] args) throws Exception {
    if (args.length < 1) {
      System.err.println("Usage: MapAnalyzer <map1.lvl> [map2.lvl]");
      System.err.println("Paths can be absolute, or filenames resolved under infinity/assets/Maps/");
      System.exit(1);
    }

    MapReport a = analyze(resolve(args[0]));
    printReport(a);

    if (args.length >= 2) {
      MapReport b = analyze(resolve(args[1]));
      System.out.println();
      printReport(b);
      System.out.println();
      printDiff(a, b);
    }
  }

  private static File resolve(String pathArg) {
    String[] candidates = new String[] {
        pathArg,
        "assets/Maps/" + pathArg,
        "infinity/assets/Maps/" + pathArg,
        "../infinity/assets/Maps/" + pathArg,
    };
    for (String c : candidates) {
      File f = new File(c);
      if (f.isFile()) {
        return f;
      }
    }
    throw new IllegalArgumentException(
        "Cannot find map file: " + pathArg + " (cwd=" + System.getProperty("user.dir") + ")");
  }

  private static MapReport analyze(File file) throws Exception {
    BitMap bmp;
    try (InputStream is = new FileInputStream(file);
         BufferedInputStream bis = new BufferedInputStream(is)) {
      bmp = new BitMap(bis);
      bmp.readBitMap(false);
    }

    LevelFile lvl;
    try (InputStream is = new FileInputStream(file);
         BufferedInputStream bis = new BufferedInputStream(is)) {
      if (bmp.isBitMap()) {
        lvl = new LevelFile(bis, bmp, true, bmp.hasELVL, file.getName());
      } else {
        lvl = new LevelFile(bis, bmp, false, bmp.hasELVL, file.getName());
      }
      String err = lvl.readLevel();
      if (err != null) {
        throw new RuntimeException("readLevel failed for " + file + ": " + err);
      }
    }

    short[][] tiles = lvl.getMap();
    MapReport r = new MapReport(file.getName());

    for (int xpos = 0; xpos < SIZE; xpos++) {
      for (int zpos = 0; zpos < SIZE; zpos++) {
        short s = tiles[SIZE - xpos - 1][SIZE - zpos - 1];
        if (s == 0) {
          continue;
        }
        r.total++;
        r.tileTypeCounts.merge((int) s, 1, Integer::sum);
        r.occupied.add(key(xpos, zpos));
        r.categoryCounts.merge(categorize(s), 1, Integer::sum);
      }
    }
    return r;
  }

  private static String categorize(short s) {
    if (s >= MapTypes.vieNormalStart && s <= MapTypes.vieNormalEnd) {
      if (s == MapTypes.vieBorder) return "border(20)";
      return "normal[1..161]";
    }
    if (s >= MapTypes.vieVDoorStart && s <= MapTypes.vieVDoorEnd) return "door-vertical[162..165]";
    if (s >= MapTypes.vieHDoorStart && s <= MapTypes.vieHDoorEnd) return "door-horizontal[166..169]";
    if (s == MapTypes.vieTurfFlag) return "turfFlag(170)";
    if (s == MapTypes.vieSafeZone) return "safeZone(171)";
    if (s == MapTypes.vieGoalArea) return "goalArea(172)";
    if (s >= MapTypes.vieFlyOverStart && s <= MapTypes.vieFlyOverEnd) return "flyOver[173..175]";
    if (s >= MapTypes.vieFlyUnderStart && s <= MapTypes.vieFlyUnderEnd) return "flyUnder[176..190]";
    if (s == MapTypes.vieAsteroidSmall) return "asteroidSmall(216)";
    if (s == MapTypes.vieAsteroidMedium) return "asteroidMedium(217)";
    if (s == MapTypes.vieAsteroidEnd) return "asteroidEnd(218)";
    if (s == MapTypes.vieStation) return "station(219)";
    if (s == MapTypes.vieWormhole) return "wormhole(220)";
    return "other(" + s + ")";
  }

  private static long key(int x, int z) {
    return ((long) x << 32) | (z & 0xffffffffL);
  }

  private static void printReport(MapReport r) {
    System.out.println("=== " + r.name + " ===");
    System.out.println("Total non-zero tiles: " + r.total);
    System.out.println();
    System.out.println("By category:");
    // TreeMap for stable ordering
    for (Map.Entry<String, Integer> e : new TreeMap<>(r.categoryCounts).entrySet()) {
      System.out.printf("  %-26s %8d%n", e.getKey(), e.getValue());
    }
    System.out.println();
    System.out.println("Top 15 individual tile IDs:");
    r.tileTypeCounts.entrySet().stream()
        .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
        .limit(15)
        .forEach(e -> System.out.printf("  id %-4d %8d%n", e.getKey(), e.getValue()));
  }

  private static void printDiff(MapReport a, MapReport b) {
    Set<Long> onlyA = new HashSet<>(a.occupied);
    onlyA.removeAll(b.occupied);
    Set<Long> onlyB = new HashSet<>(b.occupied);
    onlyB.removeAll(a.occupied);
    Set<Long> both = new HashSet<>(a.occupied);
    both.retainAll(b.occupied);

    System.out.println("=== Diff: " + a.name + " vs " + b.name + " ===");
    System.out.println("Cells only in " + a.name + ": " + onlyA.size()
        + "   <- these must be cleared on A -> B swap");
    System.out.println("Cells only in " + b.name + ": " + onlyB.size()
        + "   <- these are fresh writes by new map");
    System.out.println("Cells present in both:  " + both.size()
        + "   <- these are overwritten in place");
  }

  private static final class MapReport {
    final String name;
    int total;
    final Map<Integer, Integer> tileTypeCounts = new LinkedHashMap<>();
    final Map<String, Integer> categoryCounts = new LinkedHashMap<>();
    final Set<Long> occupied = new HashSet<>();

    MapReport(String name) {
      this.name = name;
    }
  }
}
