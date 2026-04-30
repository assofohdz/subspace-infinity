/*
 * Copyright (c) 2018-2026, Asser Fahrenholz
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
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
