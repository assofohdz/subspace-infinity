// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.tools;

import infinity.map.BitMap;
import infinity.map.LevelFile;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/** Batch tile-ID survey across {@code .lvl} files. Run via {@code :infinity:surveyMaps}; no args → recursively scans {@code infinity/assets/Maps/}. */
@SuppressWarnings("java:S106") // CLI tool: System.out/err is the user-facing report channel, not a logger
public final class MapTileSurvey {

  private static final int SIZE = 1024;
  private static final int MAX_TILE_ID = 256;

  /** Ranges our current {@code LegacyMapProjector.project} handles meaningfully. */
  private static final boolean[] HANDLED = new boolean[MAX_TILE_ID];

  static {
    for (int i = 1; i <= 190; i++) HANDLED[i] = true; // visible tiles (incl. doors/flags/fly*)
    HANDLED[216] = true; // asteroidSmall
    HANDLED[217] = true; // asteroidMedium
    HANDLED[218] = true; // asteroidEnd / over5
    HANDLED[219] = true; // station (currently falls to INVISIBLE; still "known")
    HANDLED[220] = true; // wormhole
  }

  private MapTileSurvey() {}

  public static void main(final String[] args) {
    final List<File> files = collectFiles(args);
    if (files.isEmpty()) {
      System.err.println("No .lvl files found.");
      System.exit(1);
    }

    final Survey survey = new Survey();
    System.out.printf("Surveying %d .lvl file(s)%n%n", files.size());

    surveyFiles(files, survey);

    printPerMapTable(survey);
    System.out.println();
    printGlobalHistogram(survey);
    System.out.println();
    printGapAnalysis(survey);
    System.out.println();
    printCoverage(survey);
    if (!survey.failures.isEmpty()) {
      System.out.println();
      System.out.println("=== Failures (" + survey.failures.size() + ") ===");
      survey.failures.forEach(s -> System.out.println("  " + s));
    }
  }

  /**
   * Resolve the input arguments into the list of {@code .lvl} files to
   * survey. With no args, walks {@code infinity/assets/Maps} (exits if
   * missing). With args, each arg is resolved as a file or directory; missing
   * entries print "Skipping" and continue. Extracted from {@link #main} as a
   * complexity ratchet.
   */
  private static List<File> collectFiles(final String[] args) {
    final List<File> files = new ArrayList<>();
    if (args.length == 0) {
      final File root = resolveDir("infinity/assets/Maps");
      if (root == null) {
        System.err.println("No Maps directory found and no paths given.");
        System.exit(1);
      }
      collectLvls(root, files);
      return files;
    }
    for (final String arg : args) {
      final File f = resolveArg(arg);
      if (f == null) {
        System.err.println("Skipping (not found): " + arg);
        continue;
      }
      if (f.isDirectory()) {
        collectLvls(f, files);
      } else {
        files.add(f);
      }
    }
    return files;
  }

  /**
   * Run {@link #analyzeTiles} on each file and ingest the result; failures
   * are logged to stderr and added to {@code survey.failures}. Extracted from
   * {@link #main} as a complexity ratchet.
   */
  private static void surveyFiles(final List<File> files, final Survey survey) {
    for (final File file : files) {
      try {
        survey.ingest(analyzeTiles(file));
      } catch (final Exception e) {
        System.err.printf("  FAIL  %-48s  %s%n", file.getName(), e.getMessage());
        survey.failures.add(file.getName() + ": " + e.getMessage());
      }
    }
  }

  /* ------------------------------------------------------------ */
  /* Per-file tile analysis                                       */
  /* ------------------------------------------------------------ */

  private static MapStats analyzeTiles(final File file) throws IOException {
    final BitMap bmp;
    try (InputStream is = Files.newInputStream(file.toPath());
        BufferedInputStream bis = new BufferedInputStream(is)) {
      bmp = new BitMap(bis);
      bmp.readBitMap(false);
    }
    final LevelFile lvl;
    try (InputStream is = Files.newInputStream(file.toPath());
        BufferedInputStream bis = new BufferedInputStream(is)) {
      lvl = new LevelFile(bis, bmp, bmp.isBitMap(), bmp.hasELVL, file.getName());
      final String err = lvl.readLevel();
      if (err != null) {
        throw new IllegalStateException("readLevel failed: " + err);
      }
    }

    final short[][] tiles = lvl.getMap();
    final MapStats stats = new MapStats(file.getName());
    for (int x = 0; x < SIZE; x++) {
      for (int z = 0; z < SIZE; z++) {
        final short s = tiles[SIZE - x - 1][SIZE - z - 1];
        if (s == 0) continue;
        stats.total++;
        stats.idCounts.merge((int) s, 1, Integer::sum);
      }
    }
    return stats;
  }

  /* ------------------------------------------------------------ */
  /* Output                                                       */
  /* ------------------------------------------------------------ */

  private static void printPerMapTable(final Survey s) {
    System.out.println("=== Per-map summary (top 30 by tile count) ===");
    System.out.printf("  %-32s  %8s  %6s  %10s%n",
        "map", "total", "unique", "%unhandled");
    s.perMap.stream()
        .sorted((a, b) -> Integer.compare(b.total, a.total))
        .limit(30)
        .forEach(
            m -> {
              final int unhandled = m.idCounts.entrySet().stream()
                  .filter(e -> !HANDLED[e.getKey()])
                  .mapToInt(Map.Entry::getValue)
                  .sum();
              final double pct = m.total == 0 ? 0 : 100.0 * unhandled / m.total;
              System.out.printf(
                  "  %-32s  %8d  %6d  %9.1f%%%n",
                  truncate(m.name, 32), m.total, m.idCounts.size(), pct);
            });
    if (s.perMap.size() > 30) {
      System.out.printf("  ... (%d more)%n", s.perMap.size() - 30);
    }
  }

  private static void printGlobalHistogram(final Survey s) {
    System.out.println("=== Global tile-ID histogram (across all maps) ===");
    System.out.printf("  %-4s  %12s  %9s  %s%n", "id", "occurrences", "in maps", "handled?");
    s.globalCounts.entrySet().stream()
        .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
        .limit(40)
        .forEach(
            e -> {
              final int id = e.getKey();
              System.out.printf(
                  "  %-4d  %12d  %6d/%d  %s%n",
                  id,
                  e.getValue(),
                  s.idMapCount.getOrDefault(id, 0),
                  s.perMap.size(),
                  HANDLED[id] ? "yes" : "NO");
            });
    if (s.globalCounts.size() > 40) {
      System.out.printf("  ... (%d more distinct ids)%n", s.globalCounts.size() - 40);
    }
  }

  private static void printGapAnalysis(final Survey s) {
    System.out.println("=== Gap analysis — IDs not handled by LegacyMapProjector.project ===");
    final List<Map.Entry<Integer, Long>> gaps = new ArrayList<>();
    for (final Map.Entry<Integer, Long> e : s.globalCounts.entrySet()) {
      if (!HANDLED[e.getKey()]) gaps.add(e);
    }
    if (gaps.isEmpty()) {
      System.out.println("  (none — every ID in these maps is handled)");
      return;
    }
    gaps.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
    long totalGap = 0;
    long totalTiles = 0;
    for (final MapStats m : s.perMap) totalTiles += m.total;
    for (final Map.Entry<Integer, Long> e : gaps) totalGap += e.getValue();
    System.out.printf(
        "  %d unhandled IDs account for %d/%d tiles (%.1f%% of all surveyed)%n",
        gaps.size(), totalGap, totalTiles, totalTiles == 0 ? 0 : 100.0 * totalGap / totalTiles);
    System.out.println();
    System.out.printf("  %-4s  %12s  %9s%n", "id", "occurrences", "in maps");
    for (final Map.Entry<Integer, Long> e : gaps.subList(0, Math.min(gaps.size(), 30))) {
      final int id = e.getKey();
      System.out.printf(
          "  %-4d  %12d  %6d/%d%n",
          id, e.getValue(), s.idMapCount.getOrDefault(id, 0), s.perMap.size());
    }
  }

  private static void printCoverage(final Survey s) {
    System.out.println("=== ID ranges seen ===");
    System.out.printf("  %-14s  %10s  %s%n", "range", "distinct", "total-occurrences");
    printRange(s, "1..161",   1, 161);
    printRange(s, "162..190", 162, 190);
    printRange(s, "191..215", 191, 215);
    printRange(s, "216..220", 216, 220);
    printRange(s, "221..239", 221, 239);
    printRange(s, "240..255", 240, 255);
  }

  private static void printRange(final Survey s, final String label, final int lo, final int hi) {
    long occ = 0;
    int distinct = 0;
    for (int id = lo; id <= hi; id++) {
      final Long c = s.globalCounts.get(id);
      if (c != null) {
        occ += c;
        distinct++;
      }
    }
    System.out.printf("  %-14s  %10d  %d%n", label, distinct, occ);
  }

  /* ------------------------------------------------------------ */
  /* Helpers                                                      */
  /* ------------------------------------------------------------ */

  private static void collectLvls(final File dir, final List<File> out) {
    final File[] kids = dir.listFiles();
    if (kids == null) return;
    for (final File f : kids) {
      if (f.isDirectory()) collectLvls(f, out);
      else if (f.getName().toLowerCase(Locale.ROOT).endsWith(".lvl")) out.add(f);
    }
  }

  private static File resolveArg(final String pathArg) {
    final String[] candidates = new String[] {
        pathArg,
        "assets/Maps/" + pathArg,
        "infinity/assets/Maps/" + pathArg,
        "../infinity/assets/Maps/" + pathArg,
    };
    for (final String c : candidates) {
      final File f = new File(c);
      if (f.exists()) return f;
    }
    return null;
  }

  private static File resolveDir(final String rel) {
    final String[] candidates = new String[] {rel, "../" + rel, "../../" + rel};
    for (final String c : candidates) {
      final File f = new File(c);
      if (f.isDirectory()) return f;
    }
    return null;
  }

  private static String truncate(final String s, final int len) {
    return s.length() <= len ? s : s.substring(0, len - 1) + "…";
  }

  /* ------------------------------------------------------------ */
  /* Data                                                         */
  /* ------------------------------------------------------------ */

  private static final class MapStats {
    final String name;
    int total;
    final Map<Integer, Integer> idCounts = new TreeMap<>();

    MapStats(final String name) {
      this.name = name;
    }
  }

  private static final class Survey {
    final List<MapStats> perMap = new ArrayList<>();
    final Map<Integer, Long> globalCounts = new TreeMap<>();
    final Map<Integer, Integer> idMapCount = new TreeMap<>();
    final List<String> failures = new ArrayList<>();

    void ingest(final MapStats m) {
      perMap.add(m);
      for (final Map.Entry<Integer, Integer> e : m.idCounts.entrySet()) {
        globalCounts.merge(e.getKey(), (long) e.getValue(), Long::sum);
        idMapCount.merge(e.getKey(), 1, Integer::sum);
      }
      Collections.sort(perMap, (a, b) -> a.name.compareTo(b.name));
    }
  }
}
