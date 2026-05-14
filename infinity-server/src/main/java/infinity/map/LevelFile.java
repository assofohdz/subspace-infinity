// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.map;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LevelFile {

    private static final Logger log = LoggerFactory.getLogger(LevelFile.class);

    private final String file;
    private final BitMap bitmap;
    private BufferedInputStream stream;
    private boolean containsBm;
    private boolean hasELVLData;
    private String mapName;

    public String getFile() {
        return file;
    }

    public String getMapName() {
        return mapName;
    }

    public void setMapName(String mapName) {
        this.mapName = mapName;
    }

    // eLVL ATTR tags... vector of vector of Strings
    private final List<List<String>> eLvlAttrs = new ArrayList<>();
    public static final int DEFAULT_TAG_COUNT = 6;

    // Vector of loaded regions
    private List<Region> loadedRegions;

    // unknown ELVL chunks read in on load
    private final List<Byte> unknownELVLData = new ArrayList<>();

    public List<List<String>> getELvlAttrs() {
        return eLvlAttrs;
    }

    public List<Region> getLoadedRegions() {
        return loadedRegions;
    }

    public List<Byte> getUnknownELVLData() {
        return unknownELVLData;
    }

    private final short[][] level = new short[1024][1024];

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

        bitmap = b;
        containsBm = hasBMP;
        hasELVLData = hasELVL;
        stream = bufferedStream;
        this.file = file;
    }

    /**
     * Creates a new default lvl file
     *
     * @param b The tileset bitmap (note read in default bitmap if lvl file does not
     *          contain bitmap portion)
     */
    public LevelFile(final BitMap b) {
        bitmap = b;
        this.file = null;
    }

    /**
     * Read in the eLVL data starting at the current position
     *
     * @return null if no error or the error string
     */
    private String readELvlData() {
        loadedRegions = new ArrayList<>();
        if (!available(12)) {
            return "File ended before we could read the eLVL header.";
        }
        final byte[] header = readIn(12);
        final ByteBuffer headerArray = LvlBinUtil.wrapLE(header);
        if (!LvlBinUtil.readString(headerArray, 0, 4).equals("elvl")) {
            return "The elvl header tag was not detected at the start of " + " the eLVL data section.";
        }
        final int size = headerArray.getInt(4);
        int current = 12;
        String error = null;
        while (current < size && error == null) {
            if (!available(8)) {
                error = "File ended while expecting a generic chunk header.";
                break;
            }
            final ByteBuffer headerBuf = LvlBinUtil.wrapLE(readIn(8));
            current += 8;
            final String type = LvlBinUtil.readString(headerBuf, 0, 4);
            final int chunkLength = headerBuf.getInt(4);
            if (!available(chunkLength)) {
                error = "EOF while reading in a eLVL chunk of type " + type;
                break;
            }
            final ByteBuffer curData = LvlBinUtil.wrapLE(readIn(chunkLength));
            current += chunkLength;
            error = dispatchELvlChunk(type, chunkLength, curData);
            if (error != null) {
                break;
            }
            current = consumeChunkPadding(chunkLength, current);
        }
        return error;
    }

    /**
     * Dispatch one eLVL sub-chunk by type. Recognised types ({@code ATTR},
     * {@code REGN}) are parsed in place and the decoded data appended to the
     * matching field; unknown types are buffered verbatim into
     * {@link #unknownELVLData} so the file can be re-emitted byte-for-byte.
     * Returns an error string to abort the eLVL read, or {@code null} on success.
     */
    private String dispatchELvlChunk(final String type, final int chunkLength, final ByteBuffer curData) {
        if (type.equals("ATTR")) {
            return parseAttrChunk(curData, chunkLength);
        }
        if (type.equals("REGN")) {
            return parseRegnChunk(curData);
        }
        appendUnknownELvlChunk(type, chunkLength, curData);
        return null;
    }

    /** Parse an ATTR sub-chunk's "key=value" payload and append to {@link #eLvlAttrs}. */
    private String parseAttrChunk(final ByteBuffer curData, final int chunkLength) {
        final String attr = LvlBinUtil.readString(curData, 0, chunkLength);
        final String[] keyTag = attr.split("=");
        if (keyTag.length != 2) {
            return "ATTR tag does not contain exactly " + "one '=' sign: " + attr;
        }
        final List<String> row = new ArrayList<>();
        row.add(keyTag[0]);
        row.add(keyTag[1]);
        eLvlAttrs.add(row);
        return null;
    }

    /** Parse a REGN sub-chunk into a fresh {@link Region} and append to {@link #loadedRegions}. */
    private String parseRegnChunk(final ByteBuffer curData) {
        final Region r = new Region();
        final String rv = r.decodeRegion(curData);
        if (rv != null) {
            return rv;
        }
        loadedRegions.add(r);
        return null;
    }

    /**
     * Buffer an unrecognised eLVL sub-chunk into {@link #unknownELVLData} —
     * 4-char type, 4-byte length, payload bytes, and 4-byte alignment padding —
     * so {@link #readELvlData} can round-trip the file byte-for-byte.
     */
    private void appendUnknownELvlChunk(final String type, final int chunkLength, final ByteBuffer curData) {
        unknownELVLData.add(Byte.valueOf((byte) type.charAt(0)));
        unknownELVLData.add(Byte.valueOf((byte) type.charAt(1)));
        unknownELVLData.add(Byte.valueOf((byte) type.charAt(2)));
        unknownELVLData.add(Byte.valueOf((byte) type.charAt(3)));
        final byte[] dword = BitmapSaving.toDWORD(chunkLength);
        for (int c = 0; c < 4; ++c) {
            unknownELVLData.add(Byte.valueOf(dword[c]));
        }
        for (int c = 0; c < chunkLength; ++c) {
            unknownELVLData.add(Byte.valueOf(curData.get(c)));
        }
        final int padding = 4 - chunkLength % 4;
        if (padding != 4) {
            unknownELVLData.add(Byte.valueOf((byte) 0));
        }
    }

    /** Skip the 4-byte alignment padding after an eLVL chunk; returns the advanced cursor. */
    private int consumeChunkPadding(final int chunkLength, final int current) {
        final int padding = 4 - (chunkLength % 4);
        if (padding == 4) {
            return current;
        }
        if (available(padding)) {
            readIn(padding);
            return current + padding;
        }
        log.warn("EOF while reading eLVL chunk padding (file={}).", file);
        return current;
    }

    /**
     * Actually do the read on the .lvl file, ignoring the already loaded bitmap
     * part
     *
     * @return null or the error message
     */
    public String readLevel() throws IOException {
        if (hasELVLData) {
            readIn(bitmap.getELvlOffset());
        } else if (containsBm) {
            readIn(bitmap.getFileSize());
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
                level[x][y] = (short) tile;
            }
        }

        // Close the stream so it doesn't remain opened.
        stream.close();

        return error;
    }

    public byte[] readIn(final int n) {
        try {
            // readNBytes loops until n bytes are read or EOF is reached. The bare read() method
            // returns as soon as *any* bytes are available and can short-read on large requests
            // (e.g. skipping past a 24-bit BMP + eLVL, which can be ~146KB), silently leaving the
            // stream mid-chunk and causing NegativeArraySize downstream.
            return stream.readNBytes(n);
        } catch (final IOException e) {
            log.warn("readIn failed (file={})", file, e);
            return new byte[0];
        }
    }

    public boolean available(final int n) {
        try {
            return stream.available() >= n;
        } catch (final IOException e) {
            log.warn("available() failed (file={})", file, e);
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
        return bitmap.getBitmap();
    }

    public short[][] getMap() {
        return level;
    }
}
