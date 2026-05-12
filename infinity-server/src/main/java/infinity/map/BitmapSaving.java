// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.map;

/** Little-endian DWORD/WORD byte-array writers for eLVL chunk headers + region-encoded payloads. */
public final class BitmapSaving {

    private BitmapSaving() {
    }

    public static byte[] toDWORD(final int number) {
        int i = number;
        final byte[] DWORD = new byte[4];

        DWORD[0] = (byte) (i & 0xff);
        i = i >> 8;

        DWORD[1] = (byte) (i & 0xff);
        i = i >> 8;

        DWORD[2] = (byte) (i & 0xff);
        i = i >> 8;

        DWORD[3] = (byte) (i & 0xff);

        return DWORD;
    }

    public static byte[] toWORD(final int number) {
        int i = number;
        final byte[] WORD = new byte[2];

        WORD[0] = (byte) (i & 0xff);
        i = i >> 8;

        WORD[1] = (byte) (i & 0xff);

        return WORD;
    }
}
