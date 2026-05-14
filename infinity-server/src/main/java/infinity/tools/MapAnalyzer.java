// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.tools;

import infinity.map.BitMap;
import infinity.map.LevelFile;
import infinity.map.MapTypes;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Standalone .lvl inspector — tile counts + two-map diff. Run via {@code :infinity:analyzeMaps}. */
public final class MapAnalyzer {

  private static final int SIZE = 1024;

  private MapAnalyzer() {}

  public static void main(String[] args) throws IOException {
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

  private static MapReport analyze(File file) throws IOException {
    BitMap bmp;
    try (InputStream is = Files.newInputStream(file.toPath());
         BufferedInputStream bis = new BufferedInputStream(is)) {
      bmp = new BitMap(bis);
      bmp.readBitMap(false);
    }

    LevelFile lvl;
    try (InputStream is = Files.newInputStream(file.toPath());
         BufferedInputStream bis = new BufferedInputStream(is)) {
      if (bmp.isBitMap()) {
        lvl = new LevelFile(bis, bmp, true, bmp.hasELVL, file.getName());
      } else {
        lvl = new LevelFile(bis, bmp, false, bmp.hasELVL, file.getName());
      }
      String err = lvl.readLevel();
      if (err != null) {
        throw new IllegalStateException("readLevel failed for " + file + ": " + err);
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

  /**
   * Lookup table from tile-id (short) to category label. Built once at class
   * load via {@link #buildCategoryLookup()}; replaces the prior if/else chain
   * (NPath = 82944) with an O(1) array index.
   *
   * <p>Index is the tile-id treated as an unsigned byte (0..255). Null means
   * "no canonical category for this id" → caller falls through to the
   * dynamic {@code "other(N)"} label.
   */
  private static final String[] CATEGORIES = buildCategoryLookup();

  private static String[] buildCategoryLookup() {
    final String[] arr = new String[256];
    // Normal tiles (covers border too; border is overwritten below).
    for (int i = MapTypes.VIE_NORMAL_START; i <= MapTypes.VIE_NORMAL_END; i++) {
      arr[i] = "normal[1..161]";
    }
    arr[MapTypes.VIE_BORDER] = "border(20)";
    // Doors.
    for (int i = MapTypes.VIE_V_DOOR_START; i <= MapTypes.VIE_V_DOOR_END; i++) {
      arr[i] = "door-vertical[162..165]";
    }
    for (int i = MapTypes.VIE_H_DOOR_START; i <= MapTypes.VIE_H_DOOR_END; i++) {
      arr[i] = "door-horizontal[166..169]";
    }
    // Singletons.
    arr[MapTypes.VIE_TURF_FLAG] = "turfFlag(170)";
    arr[MapTypes.VIE_SAFE_ZONE] = "safeZone(171)";
    arr[MapTypes.VIE_GOAL_AREA] = "goalArea(172)";
    // Fly zones.
    for (int i = MapTypes.VIE_FLY_OVER_START; i <= MapTypes.VIE_FLY_OVER_END; i++) {
      arr[i] = "flyOver[173..175]";
    }
    for (int i = MapTypes.VIE_FLY_UNDER_START; i <= MapTypes.VIE_FLY_UNDER_END; i++) {
      arr[i] = "flyUnder[176..190]";
    }
    // Asteroid family + station + wormhole.
    arr[MapTypes.VIE_ASTEROID_SMALL] = "asteroidSmall(216)";
    arr[MapTypes.VIE_ASTEROID_MEDIUM] = "asteroidMedium(217)";
    arr[MapTypes.VIE_ASTEROID_END] = "asteroidEnd(218)";
    arr[MapTypes.VIE_STATION] = "station(219)";
    arr[MapTypes.VIE_WORMHOLE] = "wormhole(220)";
    return arr;
  }

  private static String categorize(short s) {
    if (s >= 0 && s < CATEGORIES.length) {
      final String label = CATEGORIES[s];
      if (label != null) {
        return label;
      }
    }
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
