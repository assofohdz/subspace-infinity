// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.map;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

public class BitMap {
    public final static int BI_RGB = 0; // No compression
    public final static int BI_RLE8 = 1; // RLE 8-bit / pixel
    public final static int BI_RLE4 = 2; // RLE 4-bit / pixel
    public final static int BI_BITFIELDS = 3; // Bitfields

    private final BufferedInputStream m_stream;

    private byte[] fileHeader;
    private byte[] infoHeader;

    // private String fh_type;
    private int m_size;
    // private int m_offset;
    private int m_width;
    private int m_height;
    private int m_bitCount;
    private int m_compressionType;
    private int m_colorsUsed;

    private int[] m_colorTable;
    private int[] m_image;

    private boolean m_validBMP = false;
    public boolean hasELVL = false; // since ELVL headers are so interlocked with the bitmap, this is
    // the appropriate place
    public int ELvlOffset = -1;

    public BitMap(final BufferedInputStream stream) {
        m_stream = stream;
    }

    public void readBitMap(final boolean trans) {
        // Read 14 bytes for file header
        fileHeader = readIn(14);
        ByteBuffer array = LvlBinUtil.wrapLE(fileHeader);

        if (LvlBinUtil.readString(array, 0, 2).equals("BM")) {
            m_validBMP = true;
        } else {
            if (LvlBinUtil.readString(array, 0, 4).equals("elvl")) {
                hasELVL = true;
                ELvlOffset = 0;
            }

            return;
        }
        m_size = array.getInt(2);

        // The Subspace convention stores the eLVL section's offset in the BMP's 4-byte `reserved`
        // field (fileHeader[6..9]). Reference: SubspaceServer/src/Core/Map/BitmapHeader.cs — `Reserved`
        // is a uint. The old code read only 2 bytes and checked against the hardcoded 49720, which
        // works for classic 8-bit trench maps (fileSize=49718, eLVL at 49720 fits in 16 bits) but
        // truncates for larger 24-bit BMPs where the offset exceeds 65,535 (e.g. pub2025.lvl stores
        // 145,976 here).
        final int reservedOffset = array.getInt(6);
        if (reservedOffset != 0) {
            ELvlOffset = reservedOffset;
            hasELVL = true;
        }

        // Read 40 bytes for info header
        infoHeader = readIn(40);
        array = LvlBinUtil.wrapLE(infoHeader);
        m_width = array.getInt(4);
        m_height = array.getInt(8);
        m_bitCount = array.getShort(14);
        m_compressionType = array.getInt(16);
        m_colorsUsed = array.getInt(20);

        // Create our image container
        m_image = new int[m_width * m_height];

        // If it is 8 bits or less it has a color table
        if (m_bitCount <= 8) {
            // Define our color tables/colors used
            m_colorTable = new int[(int) Math.pow(2, m_bitCount)];
            m_colorsUsed = (int) Math.pow(2, m_bitCount);
            // Read in the color table
            for (int i = 0; i < m_colorsUsed; i++) {
                final byte c[] = readIn(4);
                array = LvlBinUtil.wrapLE(c);
                m_colorTable[i] = (array.getInt(0) & 0xffffff) + 0xff000000;

                // Make black transparent. SS specific need, will adjust to be dynamic
                if (m_colorTable[i] == 0xff000000 && trans) {
                    m_colorTable[i] = m_colorTable[i] & 0x00000000;
                }
            }
        }

        if (m_compressionType == BI_RGB && m_bitCount <= 8) {
            readInRGB();
        } else if (m_compressionType == BI_RLE8 && m_bitCount == 8) {
            readInRLE8();
        } else if (m_compressionType == BI_RGB && m_bitCount == 24) {
            readInRGB24(trans);
        }
    }

    public void readInRGB() {

        final int shift[] = new int[8 / m_bitCount];
        for (int i = 0; i < 8 / m_bitCount; i++) {
            shift[i] = 8 - ((i + 1) * m_bitCount);
        }

        // Create a mask for each pixel dependant on # of bitCount
        final int mask = (1 << m_bitCount) - 1;

        // How much padding after each line. Bitmaps pad to 32bits
        int pad = 4 - (int) Math.ceil(m_width * m_bitCount / 8.0) % 4;
        if (pad == 4) {
            pad = 0;
        }

        int y = m_height - 1;
        int x = 0;
        int bit = 0;

        int a = readByte();
        for (int i = 0; i < m_height * m_width; i++) {
            m_image[y * m_width + x] = m_colorTable[a >> shift[bit] & mask];

            bit++;
            x++;
            if (x >= m_width) {
                bit = 0;
                x = 0;
                y--;
                // Pad to 32 bits after each line
                for (int j = 0; j < pad; j++) {
                    readByte();
                }
                a = readByte();
            }
            if (bit >= 8 / m_bitCount) {
                bit = 0;
                a = readByte();
            }
        }

    }

    /**
     * Reads RLE 8 bit bitmaps
     */
    public void readInRLE8() {

        int y = m_height - 1;
        int x = 0;

        int a = readByte();
        int b = readByte();
        while (((a != 0) || (b != 1))) {

            if (a == 0) {
                if (b == 0) {
                    y--;
                    x = 0;
                } else if (b == 2) {
                    x += readByte();
                    y -= readByte();
                } else if (b >= 3) {
                    for (int i = 0; i < b; i++) {
                        m_image[y * m_width + x] = m_colorTable[readByte()];
                        x++;
                    }
                    if (Math.round(b / 2.0) != b / 2.0) {
                        readByte();
                    }
                }
            } else {
                for (int i = 0; i < a; i++) {
                    m_image[y * m_width + x] = m_colorTable[b];
                    x++;
                }
            }
            a = readByte();
            b = readByte();
        }
    }

    /**
     * Reads 24-bit uncompressed BMP pixel data. Layout: 3 bytes per pixel in BGR order (Windows
     * convention), rows stored bottom-up, each row padded to a 4-byte boundary. Output matches the
     * 8-bit paths: {@code m_image} is top-to-bottom ARGB where index 0 is the top-left pixel.
     *
     * @param trans if true, map pure-black pixels to alpha=0 (Subspace transparency convention)
     */
    public void readInRGB24(final boolean trans) {
        final int rowBytes = m_width * 3;
        final int pad = (4 - (rowBytes % 4)) % 4;
        for (int y = m_height - 1; y >= 0; y--) {
            for (int x = 0; x < m_width; x++) {
                final int b = readByte();
                final int g = readByte();
                final int r = readByte();
                final int alpha = (trans && r == 0 && g == 0 && b == 0) ? 0 : 0xff;
                m_image[y * m_width + x] = (alpha << 24) | (r << 16) | (g << 8) | b;
            }
            for (int j = 0; j < pad; j++) {
                readByte();
            }
        }
    }

    public byte[] readIn(final int n) {
        try {
            // readNBytes loops until n bytes are read or EOF (Java 9+). The bare read() method
            // returns as soon as *any* bytes are available and can silently short-read on large
            // requests, which misaligns every subsequent header/chunk read in the file.
            return m_stream.readNBytes(n);
        } catch (@SuppressWarnings("unused") final IOException e) {
            return new byte[0];
        }
    }

    public int readByte() {
        try {
            final byte[] b = new byte[1];
            m_stream.read(b);
            return b[0] & 255;
        } catch (@SuppressWarnings("unused") final IOException e) {
            return 0;
        }
    }

    /**
     * Snapshot of the decoded pixel buffer as a {@link BitmapData} record. ARGB
     * top-to-bottom; consumers that need a JME texture build a
     * {@code BufferedImage} from the buffer and feed it to {@code AWTLoader}.
     */
    public BitmapData getBitmap() {
        return new BitmapData(m_width, m_height, m_image);
    }

    public boolean isBitMap() {
        return m_validBMP;
    }

    public int getFileSize() {
        return m_size;
    }

    public int getWidth() {
        return m_width;
    }

    public int getHeight() {
        return m_height;
    }

    public int[] getImageData() {
        return m_image;
    }
}
