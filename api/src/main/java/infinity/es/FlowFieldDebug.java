// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es;

import com.simsilica.es.EntityComponent;

/**
 * Debug snapshot of the blended bot-steering flow sampled in a square patch around a ship, written
 * by {@code BotBrainSystem} (gated by {@code ZoneBotAiConfig.flowFieldDebug}) for the client
 * flow-field overlay. Wire-crossing — see {@code components.md}; non-record because jME3
 * {@code FieldSerializer} can't reflectively set record components.
 *
 * <p>The patch is {@link #cols()}×{@link #rows()} cells with its {@code (0,0)} cell at world cell
 * ({@link #originCellX()}, {@link #originCellZ()}). {@link #dirs()} is row-major
 * ({@code i = row*cols + col}); each byte is a quantized flow angle (see {@link #decodeAngle(byte)})
 * or {@link #NO_FLOW} where there is no flow (wall / unreachable / at-goal / no pinned goal).
 */
public final class FlowFieldDebug implements EntityComponent {

  /** {@link #dirs()} sentinel: no flow at that cell. */
  public static final byte NO_FLOW = (byte) 0xFF;

  // Quantization steps over [-PI, PI). 250 keeps the byte clear of the NO_FLOW (255) sentinel and
  // gives ~1.44deg resolution — far finer than an arrow glyph needs.
  private static final int STEPS = 250;
  private static final double TWO_PI = 2.0 * Math.PI;

  private final int originCellX;
  private final int originCellZ;
  private final int cols;
  private final int rows;
  private final byte[] dirs;

  public FlowFieldDebug() {
    this(0, 0, 0, 0, new byte[0]);
  }

  public FlowFieldDebug(
      final int originCellX,
      final int originCellZ,
      final int cols,
      final int rows,
      final byte[] dirs) {
    this.originCellX = originCellX;
    this.originCellZ = originCellZ;
    this.cols = cols;
    this.rows = rows;
    this.dirs = dirs.clone();
  }

  public int originCellX() {
    return this.originCellX;
  }

  public int originCellZ() {
    return this.originCellZ;
  }

  public int cols() {
    return this.cols;
  }

  public int rows() {
    return this.rows;
  }

  public byte[] dirs() {
    return this.dirs.clone();
  }

  /** Quantize a flow angle (radians, {@code atan2(dirZ, dirX)}) into a {@link #dirs()} byte. */
  public static byte encodeAngle(final double radians) {
    final double norm = ((radians + Math.PI) % TWO_PI + TWO_PI) % TWO_PI; // [0, 2PI)
    final int q = (int) Math.round(norm / TWO_PI * STEPS) % STEPS;
    return (byte) q;
  }

  /** Decode a {@link #dirs()} byte back to a flow angle in radians; caller must skip {@link #NO_FLOW}. */
  public static double decodeAngle(final byte encoded) {
    final int q = encoded & 0xFF;
    return q / (double) STEPS * TWO_PI - Math.PI;
  }
}
