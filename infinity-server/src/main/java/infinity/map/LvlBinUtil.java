// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.map;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/** {@code .lvl}/{@code .lvz} format helpers; the format is little-endian, strings are ISO-8859-1. */
final class LvlBinUtil {

  private LvlBinUtil() {}

  static ByteBuffer wrapLE(final byte[] bytes) {
    return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
  }

  /** ISO-8859-1; does not advance buffer position. */
  static String readString(final ByteBuffer buf, final int offset, final int length) {
    final byte[] tmp = new byte[length];
    for (int i = 0; i < length; i++) {
      tmp[i] = buf.get(offset + i);
    }
    return new String(tmp, StandardCharsets.ISO_8859_1);
  }

  /** ISO-8859-1, NUL-terminated; falls back to limit() if no NUL. */
  static String readNullTerminatedString(final ByteBuffer buf, final int offset) {
    int end = offset;
    while (end < buf.limit() && buf.get(end) != 0) {
      end++;
    }
    return readString(buf, offset, end - offset);
  }
}
