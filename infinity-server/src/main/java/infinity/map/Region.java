// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.map;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

/** eLVL region: rNam name, rBSE/rNFL/rNWP/rNAW flags, rAWP autoWarp (x, y, arena ≤15 chars), rTIL rectangles. Codec primitives in {@link RegionRleCodec}. */
public class Region {

    private String name;

    private boolean isBase;
    private boolean isNoFlags;
    private boolean isNoWeps;
    private boolean isNoAnti;
    private boolean isAutoWarp;

    // autowarp
    private int x = 512;
    private int y = 512;
    private String arena = "";

    private final List<Rectangle> rects = new ArrayList<>();
    // region bytes loaded... but unknown or unused by the program
    private final List<Byte> unknownBytes = new ArrayList<>();

    public Region() {
        name = "@THIS_IS_A_BUG->ERROR"; // the user should never see this
    }

    /**
     * Get the encoding for this region, save the header
     *
     * @return a Vector of Bytes representing this region
     */
    public List<Byte> getEncodedRegion() {
        final List<Byte> encoding = new ArrayList<>();
        if (isBase) {
            RegionRleCodec.appendFlagHeader(encoding, 'r', 'B', 'S', 'E');
        }
        if (isNoFlags) {
            RegionRleCodec.appendFlagHeader(encoding, 'r', 'N', 'F', 'L');
        }
        if (isNoWeps) {
            RegionRleCodec.appendFlagHeader(encoding, 'r', 'N', 'W', 'P');
        }
        if (isNoAnti) {
            RegionRleCodec.appendFlagHeader(encoding, 'r', 'N', 'A', 'W');
        }
        if (isAutoWarp) {
            appendAutoWarpSection(encoding);
        }
        // encode unknown bytes
        for (final Byte unknownByte : unknownBytes) {
            encoding.add(unknownByte);
        }
        appendNameSection(encoding);
        appendTileSection(encoding);
        return encoding;
    }

    /**
     * Append the rAWP (auto-warp) chunk. Two payload shapes — with arena
     * (size=20: x, y, arena[16]) and without arena (size=4: x, y) — both
     * branches share the rAWP header and the x/y encoding.
     */
    private void appendAutoWarpSection(final List<Byte> encoding) {
        encoding.add(Byte.valueOf((byte) 'r'));
        encoding.add(Byte.valueOf((byte) 'A'));
        encoding.add(Byte.valueOf((byte) 'W'));
        encoding.add(Byte.valueOf((byte) 'P'));
        final boolean withArena = !arena.equals("");
        final byte[] dword = BitmapSaving.toDWORD(withArena ? 20 : 4);
        for (int c = 0; c < 4; ++c) {
            encoding.add(Byte.valueOf(dword[c]));
        }
        byte[] word = BitmapSaving.toWORD(x);
        for (int c = 0; c < 2; ++c) {
            encoding.add(Byte.valueOf(word[c]));
        }
        word = BitmapSaving.toWORD(y);
        for (int c = 0; c < 2; ++c) {
            encoding.add(Byte.valueOf(word[c]));
        }
        if (withArena) {
            if (arena.length() > 15) {
                arena = arena.substring(15);
            }
            final int len = arena.length();
            for (int c = 0; c < len; ++c) {
                encoding.add(Byte.valueOf((byte) arena.charAt(c)));
            }
            for (int c = len; c < 16; ++c) {
                encoding.add(Byte.valueOf((byte) 0));
            }
        }
    }

    /** Append the rNAM chunk: header + length + name bytes + 4-byte alignment padding. */
    private void appendNameSection(final List<Byte> encoding) {
        encoding.add(Byte.valueOf((byte) 'r'));
        encoding.add(Byte.valueOf((byte) 'N'));
        encoding.add(Byte.valueOf((byte) 'A'));
        encoding.add(Byte.valueOf((byte) 'M'));
        final int len = name.length();
        final byte[] dword = BitmapSaving.toDWORD(len);
        for (int c = 0; c < 4; ++c) {
            encoding.add(Byte.valueOf(dword[c]));
        }
        for (int c = 0; c < len; ++c) {
            encoding.add(Byte.valueOf((byte) name.charAt(c)));
        }
        final int padding = RegionRleCodec.alignmentPad(len);
        for (int c = 0; c < padding; ++c) {
            encoding.add(Byte.valueOf((byte) 0));
        }
    }

    /** Append the rTIL chunk: header + length + RLE-compressed tile data + 4-byte alignment padding. */
    private void appendTileSection(final List<Byte> encoding) {
        encoding.add(Byte.valueOf((byte) 'r'));
        encoding.add(Byte.valueOf((byte) 'T'));
        encoding.add(Byte.valueOf((byte) 'I'));
        encoding.add(Byte.valueOf((byte) 'L'));
        final List<Byte> tileData = getCompressedRGN();
        final byte[] dword = BitmapSaving.toDWORD(tileData.size());
        for (int c = 0; c < 4; ++c) {
            encoding.add(Byte.valueOf(dword[c]));
        }
        for (final Byte element : tileData) {
            encoding.add(element);
        }
        final int padding = RegionRleCodec.alignmentPad(tileData.size());
        for (int c = 0; c < padding; ++c) {
            encoding.add(Byte.valueOf((byte) 0));
        }
    }

    /**
     * Get the compressed tiledata as a vector of Bytes
     *
     * @return the vector of bytes representing the encoding of this tiledata
     */
    private List<Byte> getCompressedRGN() {
        final List<Byte> bytes = new ArrayList<>();
        final boolean[][] rgn = new boolean[1024][1024];
        paintRectanglesIntoGrid(rgn);

        final RegionRleCodec.RleEncoderState state = new RegionRleCodec.RleEncoderState();
        for (int curRow = 0; curRow < 1024; ++curRow) {
            if (RegionRleCodec.isRowEmpty(rgn, curRow)) {
                RegionRleCodec.processEmptyRow(state, bytes, curRow);
            } else {
                RegionRleCodec.processNonEmptyRow(state, bytes, rgn, curRow);
            }
        }
        return bytes;
    }

    /** Paint each {@link Rectangle} in {@link #rects} as {@code true} cells in {@code rgn}. */
    private void paintRectanglesIntoGrid(final boolean[][] rgn) {
        for (final Rectangle r : rects) {
            final int endX = r.x + r.width;
            final int endY = r.y + r.height;
            for (int yPos = r.y; yPos < endY; ++yPos) {
                for (int xPos = r.x; xPos < endX; ++xPos) {
                    rgn[yPos][xPos] = true;
                }
            }
        }
    }

    /**
     * Load the data in this ByteBuffer into this region.
     *
     * @param encoding an eLVL REGN chunk, without the header (must be little-endian)
     * @return the error String, or null
     */
    public String decodeRegion(final java.nio.ByteBuffer encoding) {
        final int superChunkLen = encoding.limit();
        int cur = 0;
        String error = null;
        while (cur < superChunkLen) {
            if (superChunkLen - cur < 8) {
                error = "Not enogh bytes to make a subchunk header in REGN superchunk.";
                break;
            }
            final String type = LvlBinUtil.readString(encoding, cur, 4);
            cur += 4;
            final int len = encoding.getInt(cur);
            cur += 4;
            final ChunkOutcome outcome = dispatchSubChunk(type, len, encoding, cur);
            cur = outcome.newCur;
            if (outcome.error != null) {
                error = outcome.error;
                break;
            }
        }
        if (error == null && cur != superChunkLen) {
            error = "REGN chunk eLVL data went past encoded length, cur = " + cur + ", superChunkLength = "
                    + superChunkLen;
        }
        return error;
    }

    /**
     * Dispatch one already-read REGN sub-chunk header to the right parser
     * (flag / rAWP / rNAM / rTIL / unknown) and return the cursor advance plus
     * any tile-decode error. The flag-chunk path is no-op on cursor (the
     * 4+4 header is the entire payload). Only rTIL can produce an error;
     * other branches always return {@code error == null}.
     */
    private ChunkOutcome dispatchSubChunk(
            final String type, final int len, final java.nio.ByteBuffer encoding, final int cur) {
        if (markFlagChunkIfRecognised(type, len)) {
            return new ChunkOutcome(cur, null);
        }
        if (type.equals("rAWP")) {
            return new ChunkOutcome(parseAwpChunk(encoding, cur, len), null);
        }
        if (type.equals("rNAM")) {
            return new ChunkOutcome(parseNameChunk(encoding, cur, len), null);
        }
        if (type.equals("rTIL")) {
            final String err = decodeTiles(encoding.array(), cur, len);
            if (err != null) {
                return new ChunkOutcome(cur, err);
            }
            return new ChunkOutcome(cur + len + RegionRleCodec.alignmentPad(len), null);
        }
        return new ChunkOutcome(appendUnknownChunk(type, len, encoding, cur), null);
    }

    /** Result of {@link #dispatchSubChunk}: the advanced cursor and (rTIL only) error. */
    private static final class ChunkOutcome {
        final int newCur;
        final String error;

        ChunkOutcome(final int newCur, final String error) {
            this.newCur = newCur;
            this.error = error;
        }
    }

    /**
     * Recognise the no-payload region flag chunks (rBSE / rNAW / rNWP / rNFL),
     * flip the matching {@code is*} field, and tell the caller to skip the
     * larger-chunk dispatch. Returns {@code true} iff this chunk was a flag.
     */
    private boolean markFlagChunkIfRecognised(final String type, final int len) {
        if (len != 0) {
            return false;
        }
        if (type.equals("rBSE")) {
            isBase = true;
            return true;
        }
        if (type.equals("rNAW")) {
            isNoAnti = true;
            return true;
        }
        if (type.equals("rNWP")) {
            isNoWeps = true;
            return true;
        }
        if (type.equals("rNFL")) {
            isNoFlags = true;
            return true;
        }
        return false;
    }

    /** Parse the rAWP (auto-warp) chunk body: x, y, optional 16-byte arena. Returns advanced cursor. */
    private int parseAwpChunk(final java.nio.ByteBuffer encoding, final int cur, final int len) {
        isAutoWarp = true;
        int newCur = cur;
        x = encoding.getShort(newCur);
        newCur += 2;
        y = encoding.getShort(newCur);
        newCur += 2;
        if (len == 20) {
            arena = LvlBinUtil.readNullTerminatedString(encoding, newCur);
            newCur += 16;
        }
        return newCur;
    }

    /** Parse the rNAM (region name) chunk body and step past the 4-byte alignment padding. */
    private int parseNameChunk(final java.nio.ByteBuffer encoding, final int cur, final int len) {
        name = LvlBinUtil.readString(encoding, cur, len);
        return cur + len + RegionRleCodec.alignmentPad(len);
    }

    /**
     * Round-trip an unrecognised eLVL sub-chunk through {@link #unknownBytes} so
     * {@link #getEncodedRegion()} can re-emit it verbatim. Reads {@code len}
     * payload bytes starting at {@code cur} from {@code encoding}, copies them
     * (with header + 4-byte alignment padding) into {@code unknownBytes}, and
     * returns the new cursor position.
     */
    private int appendUnknownChunk(
            final String type, final int len, final java.nio.ByteBuffer encoding, final int cur) {
        // encode header
        unknownBytes.add(Byte.valueOf((byte) type.charAt(0)));
        unknownBytes.add(Byte.valueOf((byte) type.charAt(1)));
        unknownBytes.add(Byte.valueOf((byte) type.charAt(2)));
        unknownBytes.add(Byte.valueOf((byte) type.charAt(3)));
        final byte[] dword = BitmapSaving.toDWORD(len);
        for (int c = 0; c < 4; ++c) {
            unknownBytes.add(Byte.valueOf(dword[c]));
        }
        // encode data
        int newCur = cur;
        final int endIndex = newCur + len;
        for (int c = newCur; c < endIndex; ++c) {
            unknownBytes.add(Byte.valueOf(encoding.get(c)));
        }
        newCur += len;
        // encode padding
        final int padding = RegionRleCodec.alignmentPad(len);
        if (padding != 0) {
            unknownBytes.add(Byte.valueOf((byte) 0));
            newCur += padding;
        }
        return newCur;
    }

    /**
     * decode the rTIL section into the vector of rectangles
     *
     * @param data   the byte[] of data loaded
     * @param offset the offset to start reading
     * @param size   the length to read
     * @return the error String
     */
    private String decodeTiles(final byte[] data, final int offset, final int size) {
        final boolean[][] rgn = new boolean[1024][1024];
        // (Default-initialised — Java new boolean[][] is all false; explicit reset removed.)
        final RegionRleCodec.TileDecodeCursor cursor = new RegionRleCodec.TileDecodeCursor();
        String error = decodeRleIntoGrid(data, offset, size, rgn, cursor);
        if (error == null && cursor.curY != 1024) {
            error = "Encoded rTIL does NOT contain 1024 rows... it has " + cursor.curY;
        }
        if (error == null) {
            extractRectanglesFromGrid(rgn);
        }
        return error;
    }

    /**
     * Phase-1 of rTIL decode: walk the RLE byte stream from {@code offset} for
     * {@code size} bytes, painting set/repeat instructions into {@code rgn}
     * via {@link RegionRleCodec#applyRleInstruction}. Returns null on success or a
     * descriptive error string when the stream goes out of range. On success
     * the cursor's {@code curY} reaches 1024.
     */
    private String decodeRleIntoGrid(
            final byte[] data, final int offset, final int size,
            final boolean[][] rgn, final RegionRleCodec.TileDecodeCursor cursor) {
        int o = offset;
        final int endByte = o + size;
        while (o < endByte) {
            final byte typeByte = data[o];
            final int type = RegionRleCodec.getEncodedType(typeByte);
            final int len = RegionRleCodec.getEncodedLength(data, o, type);
            final String error = RegionRleCodec.applyRleInstruction(rgn, cursor, type, len);
            if (error != null) {
                return error;
            }
            if (cursor.curX == 1024) {
                ++cursor.curY;
                cursor.curX = 0;
            }
            o += (type % 2 == 0) ? 1 : 2;
        }
        return null;
    }

    /**
     * Phase-2 of rTIL decode: scan {@code rgn} cell-by-cell, and for each
     * still-set cell carve out the largest axis-aligned rectangle of
     * contiguous set cells, append it to {@link #rects}, and clear the
     * carved region. Iteration continues until every cell is processed.
     */
    private void extractRectanglesFromGrid(final boolean[][] rgn) {
        int curX = 0;
        int curY = 0;
        while (curY < 1024) {
            if (rgn[curY][curX]) {
                final Rectangle r = RegionRleCodec.carveRectangleAt(rgn, curX, curY);
                rects.add(r);
            }
            curX++;
            if (curX == 1024) {
                curX = 0;
                ++curY;
            }
        }
    }
}
