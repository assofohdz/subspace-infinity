// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.field.threat;

import infinity.ai.field.ScalarField;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Per-arena owner of the {@link ThreatField}s, one per live frequency (ADR-0012). Fed the same
 * per-freq ship-cell snapshot as the density fields — threat is "danger from this team", and the
 * brain blends the enemy freqs ({@link #freqs()} minus its own) into its incoming-threat surface.
 */
public final class ArenaThreat {

  private final int width;
  private final int height;
  private final int originX;
  private final int originZ;
  private final int radius;
  private final boolean[][] passable;
  private final Map<Integer, ThreatField> byFreq = new HashMap<>();
  private final ThreatField empty;

  public ArenaThreat(
      final int width,
      final int height,
      final int originX,
      final int originZ,
      final int radius,
      final boolean[][] passable) {
    this.width = width;
    this.height = height;
    this.originX = originX;
    this.originZ = originZ;
    this.radius = radius;
    this.passable = passable;
    this.empty = newField();
  }

  /** Rebuild every team's threat from this cadence's ship world-cells; absent freqs are dropped. */
  public void rebuild(final Map<Integer, List<int[]>> shipCellsByFreq) {
    this.byFreq.keySet().retainAll(shipCellsByFreq.keySet());
    for (final Map.Entry<Integer, List<int[]>> e : shipCellsByFreq.entrySet()) {
      final ThreatField field = this.byFreq.computeIfAbsent(e.getKey(), k -> newField());
      field.clear();
      for (final int[] cell : e.getValue()) {
        field.splatWorld(cell[0], cell[1], 1.0);
      }
    }
  }

  /** Danger posed by this team; a shared all-zero field if no ship of that freq is present. */
  public ScalarField threat(final int freq) {
    final ThreatField field = this.byFreq.get(freq);
    return field != null ? field : this.empty;
  }

  /** Frequencies with at least one live ship as of the last {@link #rebuild}. */
  public Set<Integer> freqs() {
    return Set.copyOf(this.byFreq.keySet());
  }

  private ThreatField newField() {
    return new ThreatField(
        this.width, this.height, this.originX, this.originZ, this.radius, this.passable);
  }
}
