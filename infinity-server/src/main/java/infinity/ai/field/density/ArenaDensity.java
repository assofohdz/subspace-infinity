// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.field.density;

import infinity.ai.field.ScalarField;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Per-arena owner of the {@link TeamDensityField}s, one per live frequency (ADR-0012). Rebuilt each
 * density cadence from the arena's ship cells grouped by team. Enemy-vs-ally is a per-bot concern:
 * the brain reads {@link #teamDensity(int)} for its own freq (allies) and blends the others
 * ({@link #freqs()} minus own) for enemies — the field stays omniscient + arena-scoped, the
 * relativity lives in the consumer.
 */
public final class ArenaDensity {

  private final int width;
  private final int height;
  private final int originX;
  private final int originZ;
  private final int kernelRadius;
  private final Map<Integer, TeamDensityField> byFreq = new HashMap<>();
  private final TeamDensityField empty;

  public ArenaDensity(
      final int width,
      final int height,
      final int originX,
      final int originZ,
      final int kernelRadius) {
    this.width = width;
    this.height = height;
    this.originX = originX;
    this.originZ = originZ;
    this.kernelRadius = kernelRadius;
    this.empty = newField();
  }

  /**
   * Rebuild every team's grid from this cadence's ship world-cells. Freqs absent from the snapshot
   * are dropped; present ones are cleared and re-splatted. Each cell is {@code int[]{worldX, worldZ}}.
   */
  public void rebuild(final Map<Integer, List<int[]>> shipCellsByFreq) {
    this.byFreq.keySet().retainAll(shipCellsByFreq.keySet());
    for (final Map.Entry<Integer, List<int[]>> e : shipCellsByFreq.entrySet()) {
      final TeamDensityField field = this.byFreq.computeIfAbsent(e.getKey(), k -> newField());
      field.clear();
      for (final int[] cell : e.getValue()) {
        field.splatWorld(cell[0], cell[1], 1.0);
      }
    }
  }

  /** This team's density field; a shared all-zero field if no ship of that freq is present. */
  public ScalarField teamDensity(final int freq) {
    final TeamDensityField field = this.byFreq.get(freq);
    return field != null ? field : this.empty;
  }

  /** Frequencies with at least one live ship as of the last {@link #rebuild}. */
  public Set<Integer> freqs() {
    return Set.copyOf(this.byFreq.keySet());
  }

  /** Total ship density across all teams at a cell — team-neutral "traffic" for chokepoint ranking. */
  public double totalAt(final int x, final int y) {
    double sum = 0.0;
    for (final TeamDensityField field : this.byFreq.values()) {
      sum += field.valueAt(x, y);
    }
    return sum;
  }

  private TeamDensityField newField() {
    return new TeamDensityField(this.width, this.height, this.originX, this.originZ, this.kernelRadius);
  }
}
