// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.map;

/**
 * AWT-free bitmap pixel container produced by {@link BitMap} / {@link LevelFile}.
 * ARGB packed; row-major top-to-bottom (index 0 = top-left, alpha in the high
 * byte).
 *
 * <p>Replaces the legacy {@code java.awt.Image} return type so the BMP-decode
 * path no longer pulls in AWT/Swing peers — which deadlocks against
 * {@code -XstartOnFirstThread} on macOS (GLFW grabs the main thread; AWT/AppKit
 * wants it too). Consumers that need a JME texture build a {@code BufferedImage}
 * from {@code argb} and pass it to {@code AWTLoader} directly; {@link BufferedImage}
 * itself is offscreen-only and headless-safe.
 */
public record BitmapData(int width, int height, int[] argb) {

  /**
   * Returns a sub-region as a fresh {@code BitmapData} sized {@code w × h} with
   * its top-left at {@code (x, y)} of this bitmap. Used to slice individual tile
   * crops out of a tileset on demand instead of pre-allocating one
   * {@code BitmapData} per tile up front.
   */
  public BitmapData subRegion(final int x, final int y, final int w, final int h) {
    final int[] sub = new int[w * h];
    for (int row = 0; row < h; row++) {
      System.arraycopy(argb, (y + row) * width + x, sub, row * w, w);
    }
    return new BitmapData(w, h, sub);
  }
}
