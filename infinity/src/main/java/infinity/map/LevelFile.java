// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.map;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Vector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LevelFile {

    private static final Logger log = LoggerFactory.getLogger(LevelFile.class);

    public String m_file;
    private final BitMap m_bitmap;
    private BufferedInputStream m_stream;
    private boolean m_containsBM;
    private boolean hasELVLData;
    private String mapName;

    public String getMapName() {
        return mapName;
    }

    public void setMapName(String mapName) {
        this.mapName = mapName;
    }

    // eLVL ATTR tags... vector of vector of Strings
    public List<List<String>> eLvlAttrs = new Vector<>();
    public static final int DEFAULT_TAG_COUNT = 6;

    // Vector of loaded regions
    public List<Region> loadedRegions;

    // unknown ELVL chunks read in on load
    public List<Byte> unknownELVLData = new Vector<>();

    private final short[][] m_level = new short[1024][1024];

    /**
     * Reads in a *.lvl file.
     *
     * @param bufferedStream The file to load/save to
     * @param b              The tileset bitmap (note read in default bitmap if lvl
     *                       file does not contain bitmap portion)
     * @param hasBMP         if the file has bmp information
     * @param hasELVL        if the file has the extended lvz information
     * @param file           string representation of the file (path)
     */
    public LevelFile(final BufferedInputStream bufferedStream, final BitMap b, final boolean hasBMP,
            final boolean hasELVL, final String file) {

        m_bitmap = b;
        m_containsBM = hasBMP;
        hasELVLData = hasELVL;
        m_stream = bufferedStream;
        m_file = file;
    }

    /**
     * Creates a new default lvl file
     *
     * @param b The tileset bitmap (note read in default bitmap if lvl file does not
     *          contain bitmap portion)
     */
    public LevelFile(final BitMap b) {
        m_bitmap = b;
    }

    /**
     * Read in the eLVL data starting at the current position
     *
     * @return null if no error or the error string
     */
    private String readELvlData() {
        String error = null;
        loadedRegions = new Vector<>();

        if (!available(12)) {
            error = "File ended before we could read the eLVL header.";
        } else {
            // read header
            final byte[] header = readIn(12);
            final ByteBuffer headerArray = LvlBinUtil.wrapLE(header);
            ByteBuffer curData;

            if (!LvlBinUtil.readString(headerArray, 0, 4).equals("elvl")) {
                error = "The elvl header tag was not detected at the start of " + " the eLVL data section.";
            } else {
                final int size = headerArray.getInt(4); // total size of the metadata section
                int current = 12; // current number of bytes read

                while (current < size && error == null) {
                    if (available(8)) {
                        curData = LvlBinUtil.wrapLE(readIn(8));
                        current += 8;
                        final String type = LvlBinUtil.readString(curData, 0, 4);
                        final int chunkLength = curData.getInt(4);

                        if (!available(chunkLength)) {
                            error = "EOF while reading in a eLVL chunk of type " + type;

                            break;
                        }

                        if (type.equals("ATTR")) { // attribute chunk
                            current += chunkLength;
                            curData = LvlBinUtil.wrapLE(readIn(chunkLength));
                            final String attr = LvlBinUtil.readString(curData, 0, chunkLength);
                            final String[] keyTag = attr.split("=");
                            if (keyTag.length != 2) {
                                error = "ATTR tag does not contain exactly " + "one '=' sign: " + attr;
                                break;
                            }

                            final List<String> row = new Vector<>();
                            row.add(keyTag[0]);
                            row.add(keyTag[1]);
                            eLvlAttrs.add(row);
                        } else if (type.equals("REGN")) { // region chunk
                            curData = LvlBinUtil.wrapLE(readIn(chunkLength));
                            current += chunkLength;

                            final Region r = new Region();
                            final String rv = r.decodeRegion(curData);

                            if (rv != null) {
                                error = rv;
                                break;
                            }

                            loadedRegions.add(r);
                        } else // unknown chunk
                        {
                            // System.out.println("unknown chunk: " + type);
                            curData = LvlBinUtil.wrapLE(readIn(chunkLength));
                            current += chunkLength;

                            // encode header
                            unknownELVLData.add(Byte.valueOf((byte) type.charAt(0)));
                            unknownELVLData.add(Byte.valueOf((byte) type.charAt(1)));
                            unknownELVLData.add(Byte.valueOf((byte) type.charAt(2)));
                            unknownELVLData.add(Byte.valueOf((byte) type.charAt(3)));
                            final byte[] dword = BitmapSaving.toDWORD(chunkLength);
                            for (int c = 0; c < 4; ++c) {
                                unknownELVLData.add(Byte.valueOf(dword[c]));
                            }

                            // encode data
                            for (int c = 0; c < chunkLength; ++c) {
                                final byte b = curData.get(c);
                                unknownELVLData.add(Byte.valueOf(b));
                            }

                            // encode padding
                            final int padding = 4 - chunkLength % 4;
                            if (padding != 4) {
                                unknownELVLData.add(Byte.valueOf((byte) 0));
                            }
                        }

                        // read in padding up to 4 byte boundry
                        final int padding = 4 - (chunkLength % 4);
                        if (padding != 4) {
                            if (available(padding)) {
                                readIn(padding);
                                current += padding;
                            } else {
                                log.warn("EOF while reading eLVL chunk padding (file={}).", m_file);
                            }
                        }
                    } else {
                        error = "File ended while expecting a generic chunk header.";
                    }
                }
            }
        }

        return error;
    }

    /**
     * Actually do the read on the .lvl file, ignoring the already loaded bitmap
     * part
     *
     * @return null or the error message
     */
    public String readLevel() throws IOException {
        if (hasELVLData) {
            readIn(m_bitmap.ELvlOffset);
        } else if (m_containsBM) {
            readIn(m_bitmap.getFileSize());
        }

        String error = null;

        // right now we're at our tile data, or our eLVL Data
        if (hasELVLData) {
            error = readELvlData();
        }

        if (error == null) {
            while (available(4)) {
                final byte[] b = readIn(4);
                final ByteBuffer array = LvlBinUtil.wrapLE(b);
                final int i = array.getInt(0);
                final int tile = i >> 24 & 0x00ff;
                final int y = (i >> 12) & 0x03FF;
                final int x = i & 0x03FF;
                m_level[x][y] = (short) tile;
            }
        }

        // Close the stream so it doesn't remain opened.
        m_stream.close();

        return error;
    }

    public byte[] readIn(final int n) {
        try {
            // readNBytes loops until n bytes are read or EOF is reached. The bare read() method
            // returns as soon as *any* bytes are available and can short-read on large requests
            // (e.g. skipping past a 24-bit BMP + eLVL, which can be ~146KB), silently leaving the
            // stream mid-chunk and causing NegativeArraySize downstream.
            return m_stream.readNBytes(n);
        } catch (final IOException e) {
            log.warn("readIn failed (file={})", m_file, e);
            return new byte[0];
        }
    }

    public boolean available(final int n) {
        try {
            return m_stream.available() >= n;
        } catch (final IOException e) {
            log.warn("available() failed (file={})", m_file, e);
            return false;
        }
    }

    /**
     * Snapshot of the BMP tileset's decoded pixel buffer. Replaces the legacy
     * {@code getTileSet(): java.awt.Image} + {@code getTiles(): Image[]} pair —
     * consumers slice individual 16×16 tile crops on demand via
     * {@link BitmapData#subRegion(int, int, int, int)}.
     */
    public BitmapData getTileset() {
        return m_bitmap.getBitmap();
    }

    public short[][] getMap() {
        return m_level;
    }
}
