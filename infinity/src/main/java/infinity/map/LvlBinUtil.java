// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.map;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * String-decode helpers for the {@code .lvl} / {@code .lvz} binary format
 * readers. Use alongside {@link ByteBuffer#getInt(int)} /
 * {@link ByteBuffer#getShort(int)} / {@link ByteBuffer#get(int)} (with the
 * buffer's {@link ByteOrder#LITTLE_ENDIAN little-endian} byte order set —
 * see {@link #wrapLE(byte[])}). The .lvl format is little-endian throughout;
 * strings are ISO-8859-1.
 */
final class LvlBinUtil {

  private LvlBinUtil() {}

  /**
   * Wraps {@code bytes} in a {@link ByteBuffer} configured for little-endian
   * reads — the .lvl/.lvz native byte order. Equivalent to
   * {@code ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)}; named
   * here so the format-specific intent reads at the call site.
   */
  static ByteBuffer wrapLE(final byte[] bytes) {
    return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
  }

  /**
   * Reads {@code length} bytes at absolute {@code offset} as an ISO-8859-1
   * string. Does not advance the buffer's position.
   */
  static String readString(final ByteBuffer buf, final int offset, final int length) {
    final byte[] tmp = new byte[length];
    for (int i = 0; i < length; i++) {
      tmp[i] = buf.get(offset + i);
    }
    return new String(tmp, StandardCharsets.ISO_8859_1);
  }

  /**
   * Reads a null-terminated ISO-8859-1 string starting at absolute
   * {@code offset}. Returns the chars up to (but not including) the first
   * {@code 0x00} byte, or up to {@link ByteBuffer#limit() limit()} if no
   * terminator is found.
   */
  static String readNullTerminatedString(final ByteBuffer buf, final int offset) {
    int end = offset;
    while (end < buf.limit() && buf.get(end) != 0) {
      end++;
    }
    return readString(buf, offset, end - offset);
  }
}
