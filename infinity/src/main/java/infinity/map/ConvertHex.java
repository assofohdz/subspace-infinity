// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.map;

/**
 * General utility class for byte to hex conversions. All methods are referenced
 * in a static context.
 */
public class ConvertHex {

    /**
     * Given a byte, return a String containing the hexadecimal equivalent.
     *
     * @param b byte to process
     * @return String containing the hexadecimal equivalent of the provided byte
     */
    public static String byteToHex(final byte b) {
        final String result;
        if ((b & 0xf0) == 0) {
            result = 0 + Integer.toHexString(b & 0xFF);
        } else {
            result = Integer.toHexString(b & 0xFF);
        }
        return result;
    }

    /**
     * Given an integer representation of a byte, return a String containing the
     * hexadecimal equivalent.
     *
     * @param theByte byte to process
     * @return String containing the hexadecimal equivalent of the provided byte
     */
    public static String byteToHex(final int theByte) {
        final String result;
        if ((theByte & 0x00F0) == 0) {
            result = 0 + Integer.toHexString(theByte & 0xFF);
        } else {
            result = Integer.toHexString(theByte & 0xFF);
        }
        return result;
    }

    /**
     * Given a string containing hexadecimal characters, returns the byte equivalent
     * in a byte array
     *
     * @param s Source string to be decoded.
     * @return Byte array representing the original hex values in the string.
     */
    public static byte[] hexStringToByteArray(final String s) {
        final int len = s.length();
        final byte[] data = new byte[len / 2];

        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4) + Character.digit(s.charAt(i + 1), 16));
        }

        return data;
    }
}
