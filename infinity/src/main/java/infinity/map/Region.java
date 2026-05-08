// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.map;

import java.awt.Color;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

/**
 * This class represents a region defined by several things: name - rNam isBase
 * - rBSE isNoFlags - rNFL isNoWeps - rNWP isNoAnti - rNAW isAutoWarp - rAWP
 *
 * autoWarp x, y, and arena (in case of autowarp) arena is max 15 letters
 *
 * vector of rectangles - rTIL
 *
 * @author baks
 */
public class Region {
    // constants

    private static int SMALL_EMPTY_RUN = 0;
    private static int LONG_EMPTY_RUN = 1;
    private static int SMALL_PRESENT_RUN = 2;
    private static int LONG_PRESENT_RUN = 3;
    private static int SMALL_EMPTY_ROWS = 4;
    private static int LONG_EMPTY_ROWS = 5;
    private static int SMALL_REPEAT = 6;
    private static int LONG_REPEAT = 7;

    public Color color;
    public String name;

    public boolean isBase = false;
    public boolean isNoFlags = false;
    public boolean isNoWeps = false;
    public boolean isNoAnti = false;
    public boolean isAutoWarp = false;

    // autowarp
    public int x = 512, y = 512;
    public String arena = "";

    public List<Rectangle> rects = new ArrayList<>();
    public List<Byte> unknownBytes = new ArrayList<>(); // region bytes loaded... but unknown or unused by the program

    public Region() {
        name = "@THIS_IS_A_BUG->ERROR"; // the user should never see this
        color = getRandomColor();
    }

    public Region(final String newName, final Color newColor) {
        name = newName;
        color = newColor;
    }

    /**
     * Get the encoding for this region, save the header
     *
     * @return a Vector of Bytes representing this region
     */
    public List<Byte> getEncodedRegion() {
        final List<Byte> encoding = new ArrayList<>();
        if (isBase) {
            appendFlagHeader(encoding, 'r', 'B', 'S', 'E');
        }
        if (isNoFlags) {
            appendFlagHeader(encoding, 'r', 'N', 'F', 'L');
        }
        if (isNoWeps) {
            appendFlagHeader(encoding, 'r', 'N', 'W', 'P');
        }
        if (isNoAnti) {
            appendFlagHeader(encoding, 'r', 'N', 'A', 'W');
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
     * Append a 4-char flag chunk header (e.g. "rBSE") followed by a 4-byte zero
     * length field. Used by the no-payload region flags (isBase, isNoFlags,
     * isNoWeps, isNoAnti) which all share the same on-wire shape.
     */
    private static void appendFlagHeader(
            final List<Byte> encoding, final char a, final char b, final char c, final char d) {
        encoding.add(Byte.valueOf((byte) a));
        encoding.add(Byte.valueOf((byte) b));
        encoding.add(Byte.valueOf((byte) c));
        encoding.add(Byte.valueOf((byte) d));
        encoding.add(Byte.valueOf((byte) 0));
        encoding.add(Byte.valueOf((byte) 0));
        encoding.add(Byte.valueOf((byte) 0));
        encoding.add(Byte.valueOf((byte) 0));
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
        final int padding = 4 - len % 4;
        if (padding != 4) {
            for (int c = 0; c < padding; ++c) {
                encoding.add(Byte.valueOf((byte) 0));
            }
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
        final int padding = 4 - tileData.size() % 4;
        if (padding != 4) {
            for (int c = 0; c < padding; ++c) {
                encoding.add(Byte.valueOf((byte) 0));
            }
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

        final RleEncoderState state = new RleEncoderState();
        for (int curRow = 0; curRow < 1024; ++curRow) {
            if (isRowEmpty(rgn, curRow)) {
                processEmptyRow(state, bytes, curRow);
            } else {
                processNonEmptyRow(state, bytes, rgn, curRow);
            }
        }
        return bytes;
    }

    /**
     * Empty-row branch of the RLE row loop. Bumps the empty-row run counter,
     * flushes any pending repeat-last-row marker (since an empty row breaks
     * the same-row sequence), and on the final row flushes the empty-row run
     * itself.
     */
    private static void processEmptyRow(
            final RleEncoderState state, final List<Byte> bytes, final int curRow) {
        state.emptyRowCount++;
        if (state.lastRowSameCount > 0) {
            bytes.addAll(encodeRepeatLastRow(state.lastRowSameCount));
        }
        state.lastRow = null;
        state.lastRowSameCount = 0;
        if (curRow == 1023) {
            bytes.addAll(encodeEmptyRows(state.emptyRowCount));
        }
    }

    /**
     * Non-empty-row branch: flush any pending empty-row run, encode this row's
     * RLE bytes, and either count it as a repeat of the previous row or emit
     * it fresh (flushing the prior repeat counter first).
     */
    private static void processNonEmptyRow(
            final RleEncoderState state,
            final List<Byte> bytes,
            final boolean[][] rgn,
            final int curRow) {
        if (state.emptyRowCount > 0) {
            bytes.addAll(encodeEmptyRows(state.emptyRowCount));
            state.emptyRowCount = 0;
        }
        final List<Byte> encodedRow = encodeRowRleRuns(rgn, curRow);
        if (rowsEqual(state.lastRow, encodedRow)) {
            state.lastRowSameCount++;
            if (curRow == 1023) {
                bytes.addAll(encodeRepeatLastRow(state.lastRowSameCount));
            }
        } else {
            if (state.lastRowSameCount != 0) {
                bytes.addAll(encodeRepeatLastRow(state.lastRowSameCount));
                state.lastRowSameCount = 0;
            }
            bytes.addAll(encodedRow);
            state.lastRow = encodedRow;
        }
    }

    /** Cross-iteration state for {@link #getCompressedRGN}'s row-by-row RLE encoder. */
    private static final class RleEncoderState {
        List<Byte> lastRow;
        int lastRowSameCount;
        int emptyRowCount;
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

    /** Returns true if every cell in {@code rgn[curRow]} is {@code false}. */
    private static boolean isRowEmpty(final boolean[][] rgn, final int curRow) {
        for (int curY = 0; curY < 1024; ++curY) {
            if (rgn[curRow][curY]) {
                return false;
            }
        }
        return true;
    }

    /**
     * RLE-encode one row of {@code rgn} into a fresh byte list — each maximal
     * run of like-tiles becomes one {@link #encodeRun} payload.
     */
    private static List<Byte> encodeRowRleRuns(final boolean[][] rgn, final int curRow) {
        final List<Byte> encodedRow = new ArrayList<>();
        int curY = 0;
        while (curY < 1024) {
            final boolean encodingTiles = rgn[curRow][curY];
            int count = 0;
            for (; curY < 1024; ++count, ++curY) {
                if (rgn[curRow][curY] != encodingTiles) {
                    break;
                }
            }
            encodedRow.addAll(encodeRun(count, encodingTiles));
        }
        return encodedRow;
    }

    /**
     * Return true if both row encodings have the same byte sequence. {@code null}
     * {@code lastRow} (no previous row tracked) compares as not-equal so the
     * caller emits the row fresh rather than emitting a repeat-last-row marker.
     */
    private static boolean rowsEqual(final List<Byte> lastRow, final List<Byte> encodedRow) {
        if (lastRow == null || lastRow.size() != encodedRow.size()) {
            return false;
        }
        for (int v = 0; v < lastRow.size(); ++v) {
            if (!lastRow.get(v).equals(encodedRow.get(v))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Encode a number of empty rows
     *
     * @return a Vector of bytes containing the encoding
     */
    private static List<Byte> encodeEmptyRows(final int count) {
        /*
         * first, rows that contain no tiles at all can (optionally) be encoded
         * specially:
         *
         * 100n nnnn - n+1 (1-32) rows of all empty 1010 00nn nnnn nnnn - n+1 (1-1024)
         * rows of all empty
         */

        final List<Byte> code = new ArrayList<>();

        int i = count;
        if (i <= 32) {
            i--; // cause it's 1-32 not 0-31
            final byte encode = (byte) (i | 0x80);
            code.add(Byte.valueOf(encode));

        } else {
            i--;
            final byte one = (byte) ((i >> 8) | 0xA0);
            final byte two = (byte) ((i & 0x00FF));

            code.add(Byte.valueOf(one));
            code.add(Byte.valueOf(two));

        }

        return code;
    }

    /**
     * Encode a run
     *
     * @param i        the number of tiles to cover
     * @param inRegion is this a run of region tiles? (or empty spaces)
     * @return the Vector of encodedBytes for this run
     */
    private static List<Byte> encodeRun(final int count, final boolean inRegion) {
        /*
         * for each row, split it into runs of empty tiles and present tiles. for each
         * run, output one of these bit sequences:
         *
         * 000n nnnn - n+1 (1-32) empty tiles in a row 0010 00nn nnnn nnnn - n+1
         * (1-1024) empty tiles in a row 010n nnnn - n+1 (1-32) present tiles in a row
         * 0110 00nn nnnn nnnn - n+1 (1-1024) present tiles in a row
         */

        final List<Byte> code = new ArrayList<>();
        int i = count;

        if (i <= 32) {
            --i;
            byte one;

            if (!inRegion) {
                one = (byte) i;
            } else {
                one = (byte) (i | 0x40);
            }

            code.add(Byte.valueOf(one));

        } else {
            --i;
            byte one, two;

            if (!inRegion) // empty tiles
            {
                one = (byte) ((i >> 8) | 0x20);
                two = (byte) (i & 0x00FF);
            } else // present tiles
            {
                one = (byte) ((i >> 8) | 0x60);
                two = (byte) (i & 0x00FF);
            }

            code.add(Byte.valueOf(one));
            code.add(Byte.valueOf(two));

        }

        return code;
    }

    /**
     * encode a repeat of the last row
     *
     * @param count the number of times we repeated
     * @return a Vector of Bytes containing the encoding of this repetition
     */
    private static List<Byte> encodeRepeatLastRow(final int count) {
        /*
         * if the same pattern of tiles appears in more than one consecutive row, you
         * can use these special codes to save more space:
         *
         * 110n nnnn - repeat last row n+1 (1-32) times 1110 00nn nnnn nnnn - repeat
         * last row n+1 (1-1024) times
         */

        final List<Byte> code = new ArrayList<>();
        int i = count;
        if (i <= 32) {
            i--; // cause it's 1-32 not 0-31
            final byte encode = (byte) (i | 0xC0);
            code.add(Byte.valueOf(encode));
        } else {
            i--;
            final byte one = (byte) ((i >> 8) | 0xE0);
            final byte two = (byte) ((i & 0x00FF));

            code.add(Byte.valueOf(one));
            code.add(Byte.valueOf(two));
        }

        return code;
    }

    /**
     * Get this byte in binary
     *
     * @param b the byte to convert
     */
    @SuppressWarnings("unused")
    private static String getBinaryStringOfByte(final byte b) {
        int mask = 0x00000080;
        final StringBuilder rv = new StringBuilder();

        for (int x = 0; x < 8; ++x) {
            rv.append((b & mask) == 0 ? "0" : "1");
            mask = mask >> 1;
        }

        return rv.toString();
    }

    /**
     * Load the data in this ByteBuffer into this region.
     *
     * @param encoding an eLVL REGN chunk, without the header (must be little-endian)
     * @return the error String, or null
     */
    public String decodeRegion(final java.nio.ByteBuffer encoding) {
        String error = null;
        final int superChunkLen = encoding.limit();
        int cur = 0;
        while (cur < superChunkLen) {
            if (superChunkLen - cur < 8) {
                error = "Not enogh bytes to make a subchunk header in REGN superchunk.";
                break;
            }
            final String type = LvlBinUtil.readString(encoding, cur, 4);
            cur += 4;
            final int len = encoding.getInt(cur);
            cur += 4;
            if (markFlagChunkIfRecognised(type, len)) {
                continue;
            }
            if (type.equals("rAWP")) {
                cur = parseAwpChunk(encoding, cur, len);
            } else if (type.equals("rNAM")) {
                cur = parseNameChunk(encoding, cur, len);
            } else if (type.equals("rTIL")) {
                error = decodeTiles(encoding.array(), cur, len);
                if (error != null) {
                    break;
                }
                cur += len + alignmentPad(len);
            } else {
                cur = appendUnknownChunk(type, len, encoding, cur);
            }
        }
        if (error == null && cur != superChunkLen) {
            error = "REGN chunk eLVL data went past encoded length, cur = " + cur + ", superChunkLength = "
                    + superChunkLen;
        }
        return error;
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
        return cur + len + alignmentPad(len);
    }

    /** Bytes of zero padding to align an N-byte payload to a 4-byte boundary, or 0 if aligned. */
    private static int alignmentPad(final int len) {
        final int padding = 4 - len % 4;
        return padding == 4 ? 0 : padding;
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
        final int padding = 4 - len % 4;
        if (padding != 4) {
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
     * @param len    the length to read
     * @return the error String
     */
    private String decodeTiles(final byte[] data, final int offset, final int size) {
        final boolean[][] rgn = new boolean[1024][1024];
        // (Default-initialised — Java new boolean[][] is all false; explicit reset removed.)
        final TileDecodeCursor cursor = new TileDecodeCursor();
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
     * via {@link #applyRleInstruction}. Returns null on success or a
     * descriptive error string when the stream goes out of range.
     * On success {@link TileDecodeCursor#curY} reaches 1024.
     */
    private String decodeRleIntoGrid(
            final byte[] data, final int offset, final int size,
            final boolean[][] rgn, final TileDecodeCursor cursor) {
        int o = offset;
        final int endByte = o + size;
        while (o < endByte) {
            final byte typeByte = data[o];
            final int type = getEncodedType(typeByte);
            final int len = getEncodedLength(data, o, type);
            final String error = applyRleInstruction(rgn, cursor, type, len);
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
     * Apply one decoded RLE instruction (type+length) to the grid + cursor.
     * Each branch handles one of the four instruction families
     * (empty-run, present-run, empty-rows, repeat). Returns an error string
     * if the instruction would advance past the grid bounds, otherwise null.
     */
    private static String applyRleInstruction(
            final boolean[][] rgn, final TileDecodeCursor cursor, final int type, final int len) {
        if (type == SMALL_EMPTY_RUN || type == LONG_EMPTY_RUN) {
            return applyEmptyRun(cursor, len);
        }
        if (type == SMALL_PRESENT_RUN || type == LONG_PRESENT_RUN) {
            return applyPresentRun(rgn, cursor, len);
        }
        if (type == SMALL_EMPTY_ROWS || type == LONG_EMPTY_ROWS) {
            return applyEmptyRows(cursor, len);
        }
        if (type == SMALL_REPEAT || type == LONG_REPEAT) {
            return applyRepeat(rgn, cursor, len);
        }
        return null;
    }

    /** Skip {@code len} cells along the current row; bounds-check first. */
    private static String applyEmptyRun(final TileDecodeCursor cursor, final int len) {
        if (len + cursor.curX > 1024) {
            return "empty run extends past end";
        }
        cursor.curX += len;
        return null;
    }

    /** Set {@code len} contiguous cells starting at the cursor; bounds-check first. */
    private static String applyPresentRun(
            final boolean[][] rgn, final TileDecodeCursor cursor, final int len) {
        if (len + cursor.curX > 1024) {
            return "present run extends past end";
        }
        final int stopX = cursor.curX + len;
        for (int xPos = cursor.curX; xPos < stopX; ++xPos) {
            rgn[cursor.curY][xPos] = true;
        }
        cursor.curX += len;
        return null;
    }

    /** Skip {@code len} fully-empty rows; the cursor must be at column 0. */
    private static String applyEmptyRows(final TileDecodeCursor cursor, final int len) {
        if (cursor.curX != 0) {
            return "empty row occured before a run was over, curX = " + cursor.curX;
        }
        cursor.curY += len;
        return null;
    }

    /** Copy the previous row across the next {@code len} rows; cursor must be at column 0 and not row 0. */
    private static String applyRepeat(
            final boolean[][] rgn, final TileDecodeCursor cursor, final int len) {
        if (cursor.curX != 0) {
            return "repeat occured before a run was over.";
        }
        if (cursor.curY == 0) {
            return "repeat occured in the first row.";
        }
        final int stopY = cursor.curY + len;
        final int copyY = cursor.curY - 1;
        for (int xPos = 0; xPos < 1024; ++xPos) {
            for (int yPos = cursor.curY; yPos < stopY; ++yPos) {
                rgn[yPos][xPos] = rgn[copyY][xPos];
            }
        }
        cursor.curY += len;
        return null;
    }

    /** Mutable {@code (curX, curY)} cursor threaded through {@link #decodeRleIntoGrid}. */
    private static final class TileDecodeCursor {
        int curX;
        int curY;
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
                final Rectangle r = carveRectangleAt(rgn, curX, curY);
                rects.add(r);
            }
            if (++curX == 1024) {
                curX = 0;
                ++curY;
            }
        }
    }

    /**
     * Carve the largest axis-aligned rectangle anchored at {@code (curX, curY)}
     * inside {@code rgn} (greedy width-first, then height extending down only
     * while every cell in the row matches), clear it from the grid, and return
     * it.
     */
    private static Rectangle carveRectangleAt(final boolean[][] rgn, final int curX, final int curY) {
        final Rectangle r = new Rectangle();
        r.x = curX;
        r.y = curY;
        int w = 1;
        for (int xPos = curX + 1; xPos < 1024; ++xPos) {
            if (!rgn[curY][xPos]) {
                break;
            }
            ++w;
        }
        r.width = w;
        int h = 1;
        for (int yPos = r.y + 1; yPos < 1024; ++yPos) {
            if (!isRowFullyMatching(rgn, yPos, r.x, r.x + r.width)) {
                break;
            }
            ++h;
        }
        r.height = h;
        clearRectangleInGrid(rgn, r);
        return r;
    }

    /** Returns true if every cell in {@code rgn[yPos][startX..endX)} is set. */
    private static boolean isRowFullyMatching(
            final boolean[][] rgn, final int yPos, final int startX, final int endX) {
        for (int xPos = startX; xPos < endX; ++xPos) {
            if (!rgn[yPos][xPos]) {
                return false;
            }
        }
        return true;
    }

    /** Mark every cell inside {@code r} as {@code false} (this part has been processed). */
    private static void clearRectangleInGrid(final boolean[][] rgn, final Rectangle r) {
        final int endX = r.x + r.width;
        final int endY = r.y + r.height;
        for (int yPos = r.y; yPos < endY; ++yPos) {
            for (int xPos = r.x; xPos < endX; ++xPos) {
                rgn[yPos][xPos] = false;
            }
        }
    }

    /**
     * Get a random Color
     *
     * @return a random Color
     */
    private static Color getRandomColor() {
        int r = (int) (Math.random() * 255);
        int g = (int) (Math.random() * 255);
        int b = (int) (Math.random() * 255);

        if (r + b + g < 40) {
            r += 20;
            g += 20;
            b += 20;
        }

        return new Color(r, g, b);
    }

    /**
     * Get an encoded length for this rTil entry
     *
     * @param data   the data array
     * @param offset the current offset
     * @param type   the type of the fragment at offset in data
     * @return the length that's encoded
     */
    private static int getEncodedLength(final byte[] data, final int offset, final int type) {
        if (type % 2 == 0) {
            return getEncodedLength(data[offset]);
        }
        return getEncodedLength(data[offset], data[offset + 1]);
    }

    /**
     * Get the encoded length in this SMALL one byte rTIL encoding
     *
     * @param one the byte for this encoding
     * @return an in 1-32 represeting the length that's currently encoded
     */
    private static int getEncodedLength(final byte one) {
        return getBitFragment(one, 4, 8) + 1;
    }

    /**
     * Get the encoded length in this LONG two byte rTIL encoding
     *
     * @param one the first byte for this encoding
     * @param two the second byte for this encoding
     * @return an in 1-1024 represeting the length that's currently encoded
     */
    private static int getEncodedLength(final byte one, final byte two) {
        final int highByte = getBitFragment(one, 7, 8) << 8;
        return (highByte | (0xFF & two)) + 1;
    }

    /**
     * Get the type of encoding this is for eLVL rTIL encoding... will be a constant
     * such as Region.SMALL_EMPTY_RUN or Region.LONG_REPEAT
     *
     * @param typeByte
     * @return
     */
    private static int getEncodedType(final byte typeByte) {
        return getBitFragment(typeByte, 1, 3);
    }

    /**
     * get the bit fragment from startIndex to endIndex
     *
     * @param extractFrom the byte to extract from
     * @param startIndex  the inclusive leftbound index: 1234 5678
     * @param endIndex    the inclusive rightbound index 1234 5678 and startIndex
     * @return the int extracted from the requested bits
     */
    public static int getBitFragment(final byte extractFrom, final int startIndex, final int endIndex) {
        final int shift = 8 - endIndex;
        final int numBits = endIndex - startIndex + 1;
        final byte mask = (byte) ((0x01 << numBits) - 1);

        return (extractFrom >> shift) & mask;
    }

}
