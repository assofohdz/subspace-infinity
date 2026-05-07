// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.map;

/**
 * Little-endian byte-array helpers for writing eLVL chunk headers and
 * region-encoded payloads. Historically this class also held a 256-color BMP
 * save path (`saveAs256ColorBitmap` + a {@code PixelGrabber} pipeline + a
 * `JOptionPane` error surface), but the save path had no live callers and
 * required AWT/Swing peers that deadlocked against {@code -XstartOnFirstThread}
 * on macOS — deleted in the BitMap-decoupling refactor.
 *
 * <p>Class name kept ("BitmapSaving") to avoid a noisy rename across
 * {@link Region} and {@link LevelFile}'s import sites; the helpers are still
 * specifically about writing little-endian DWORD/WORD prefixes inside the
 * Subspace .lvl save format.
 */
public final class BitmapSaving {

    private BitmapSaving() {
        // Utility class — no instances.
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
