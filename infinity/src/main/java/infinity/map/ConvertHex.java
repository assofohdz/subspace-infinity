/*
 * Copyright (c) 2018, Asser Fahrenholz
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