// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.map;

/** AWT-free ARGB pixel container, row-major top-to-bottom. Avoids AWT/Swing peers (macOS {@code -XstartOnFirstThread} deadlock). */
public record BitmapData(int width, int height, int[] argb) {

  /** Fresh {@code w × h} sub-region at {@code (x, y)}. */
  public BitmapData subRegion(final int x, final int y, final int w, final int h) {
    final int[] sub = new int[w * h];
    for (int row = 0; row < h; row++) {
      System.arraycopy(argb, (y + row) * width + x, sub, row * w, w);
    }
    return new BitmapData(w, h, sub);
  }
}
