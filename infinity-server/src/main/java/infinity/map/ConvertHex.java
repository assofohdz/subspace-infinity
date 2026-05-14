// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.map;

/** Byte ↔ hex string helpers. */
public final class ConvertHex {

    private ConvertHex() { /* utility */ }

    public static String byteToHex(final byte b) {
        final String result;
        if ((b & 0xf0) == 0) {
            result = 0 + Integer.toHexString(b & 0xFF);
        } else {
            result = Integer.toHexString(b & 0xFF);
        }
        return result;
    }

    public static String byteToHex(final int theByte) {
        final String result;
        if ((theByte & 0x00F0) == 0) {
            result = 0 + Integer.toHexString(theByte & 0xFF);
        } else {
            result = Integer.toHexString(theByte & 0xFF);
        }
        return result;
    }

    public static byte[] hexStringToByteArray(final String s) {
        final int len = s.length();
        final byte[] data = new byte[len / 2];

        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4) + Character.digit(s.charAt(i + 1), 16));
        }

        return data;
    }
}
