// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.map;

import java.awt.Color;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

/**
 * Pure-function RLE codec helpers extracted from {@link Region} so the
 * encode/decode primitives stay under a sane class-level cyclomatic-complexity
 * ceiling. Every method here is {@code static}, has no Region / ECS dependency,
 * and operates on raw {@code boolean[][]} grids + {@code byte} streams.
 *
 * <p>The split follows the encode-vs-decode dichotomy implicit in the eLVL
 * region format (REGN superchunk, rTIL sub-chunk):
 *
 * <ul>
 *   <li>Encoding: {@link #encodeEmptyRows}, {@link #encodeRun},
 *       {@link #encodeRepeatLastRow}, {@link #encodeRowRleRuns},
 *       {@link #rowsEqual}, {@link #isRowEmpty},
 *       {@link #processEmptyRow}, {@link #processNonEmptyRow}, plus the
 *       {@link RleEncoderState} state holder.
 *   <li>Decoding: {@link #applyRleInstruction} and its 4 family helpers
 *       ({@link #applyEmptyRun}, {@link #applyPresentRun},
 *       {@link #applyEmptyRows}, {@link #applyRepeat}), plus
 *       {@link TileDecodeCursor}, plus the bit-arithmetic helpers
 *       {@link #getEncodedType}, {@link #getEncodedLength(byte[], int, int)},
 *       {@link #getBitFragment}.
 *   <li>Grid utilities: {@link #carveRectangleAt}, {@link #isRowFullyMatching},
 *       {@link #clearRectangleInGrid}.
 *   <li>Misc: {@link #appendFlagHeader}, {@link #alignmentPad},
 *       {@link #getRandomColor}.
 * </ul>
 *
 * <p>This is the round-22 escape hatch from the class-level CC ceiling on
 * {@link Region}: when the per-method CCs are all small but their sum is
 * huge, pull the truly-context-free helpers here. Region keeps the instance
 * state (name / color / flags / rects / unknownBytes) and the dispatcher
 * methods that mutate that state.
 */
final class RegionRleCodec {

    // -----------------------------------------------------------------
    // RLE instruction-type constants — shared between encoder and decoder
    // -----------------------------------------------------------------
    static final int SMALL_EMPTY_RUN = 0;
    static final int LONG_EMPTY_RUN = 1;
    static final int SMALL_PRESENT_RUN = 2;
    static final int LONG_PRESENT_RUN = 3;
    static final int SMALL_EMPTY_ROWS = 4;
    static final int LONG_EMPTY_ROWS = 5;
    static final int SMALL_REPEAT = 6;
    static final int LONG_REPEAT = 7;

    private RegionRleCodec() {
        // utility class — instantiation prevented
    }

    // -----------------------------------------------------------------
    // Encoder state holder
    // -----------------------------------------------------------------

    /** Cross-iteration state for the row-by-row RLE encoder. */
    static final class RleEncoderState {
        List<Byte> lastRow;
        int lastRowSameCount;
        int emptyRowCount;
    }

    // -----------------------------------------------------------------
    // Decoder cursor
    // -----------------------------------------------------------------

    /** Mutable {@code (curX, curY)} cursor threaded through the rTIL decoder. */
    static final class TileDecodeCursor {
        int curX;
        int curY;
    }

    // -----------------------------------------------------------------
    // Encoder helpers
    // -----------------------------------------------------------------

    /**
     * Append a 4-char flag chunk header (e.g. "rBSE") followed by a 4-byte zero
     * length field. Used by the no-payload region flags (isBase, isNoFlags,
     * isNoWeps, isNoAnti) which all share the same on-wire shape.
     */
    static void appendFlagHeader(
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
     * Empty-row branch of the RLE row loop. Bumps the empty-row run counter,
     * flushes any pending repeat-last-row marker (since an empty row breaks
     * the same-row sequence), and on the final row flushes the empty-row run
     * itself.
     */
    static void processEmptyRow(
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
    static void processNonEmptyRow(
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

    /** Returns true if every cell in {@code rgn[curRow]} is {@code false}. */
    static boolean isRowEmpty(final boolean[][] rgn, final int curRow) {
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
    static List<Byte> encodeRowRleRuns(final boolean[][] rgn, final int curRow) {
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
     * Return true if both row encodings have the same byte sequence.
     * {@code null} {@code lastRow} (no previous row tracked) compares as not-equal
     * so the caller emits the row fresh rather than emitting a repeat-last-row
     * marker.
     */
    static boolean rowsEqual(final List<Byte> lastRow, final List<Byte> encodedRow) {
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
     * Encode a number of empty rows.
     *
     * <pre>
     * 100n nnnn          - n+1 (1-32) rows of all empty
     * 1010 00nn nnnn nnnn - n+1 (1-1024) rows of all empty
     * </pre>
     */
    static List<Byte> encodeEmptyRows(final int count) {
        final List<Byte> code = new ArrayList<>();
        int i = count;
        if (i <= 32) {
            i--; // 1-32 not 0-31
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
     * Encode a run of empty or present tiles within a row.
     *
     * <pre>
     * 000n nnnn          - n+1 (1-32) empty tiles in a row
     * 0010 00nn nnnn nnnn - n+1 (1-1024) empty tiles in a row
     * 010n nnnn          - n+1 (1-32) present tiles in a row
     * 0110 00nn nnnn nnnn - n+1 (1-1024) present tiles in a row
     * </pre>
     */
    static List<Byte> encodeRun(final int count, final boolean inRegion) {
        final List<Byte> code = new ArrayList<>();
        int i = count;
        if (i <= 32) {
            --i;
            final byte one = inRegion ? (byte) (i | 0x40) : (byte) i;
            code.add(Byte.valueOf(one));
        } else {
            --i;
            final byte one;
            final byte two = (byte) (i & 0x00FF);
            if (inRegion) {
                one = (byte) ((i >> 8) | 0x60);
            } else {
                one = (byte) ((i >> 8) | 0x20);
            }
            code.add(Byte.valueOf(one));
            code.add(Byte.valueOf(two));
        }
        return code;
    }

    /**
     * Encode a repeat of the last row.
     *
     * <pre>
     * 110n nnnn          - repeat last row n+1 (1-32) times
     * 1110 00nn nnnn nnnn - repeat last row n+1 (1-1024) times
     * </pre>
     */
    static List<Byte> encodeRepeatLastRow(final int count) {
        final List<Byte> code = new ArrayList<>();
        int i = count;
        if (i <= 32) {
            i--; // 1-32 not 0-31
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

    // -----------------------------------------------------------------
    // Decoder helpers
    // -----------------------------------------------------------------

    /**
     * Apply one decoded RLE instruction (type+length) to the grid + cursor.
     * Each branch handles one of the four instruction families
     * (empty-run, present-run, empty-rows, repeat). Returns an error string
     * if the instruction would advance past the grid bounds, otherwise null.
     */
    static String applyRleInstruction(
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
    static String applyEmptyRun(final TileDecodeCursor cursor, final int len) {
        if (len + cursor.curX > 1024) {
            return "empty run extends past end";
        }
        cursor.curX += len;
        return null;
    }

    /** Set {@code len} contiguous cells starting at the cursor; bounds-check first. */
    static String applyPresentRun(
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
    static String applyEmptyRows(final TileDecodeCursor cursor, final int len) {
        if (cursor.curX != 0) {
            return "empty row occured before a run was over, curX = " + cursor.curX;
        }
        cursor.curY += len;
        return null;
    }

    /**
     * Copy the previous row across the next {@code len} rows; cursor must be at
     * column 0 and not row 0.
     */
    static String applyRepeat(
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

    // -----------------------------------------------------------------
    // Grid utilities (rectangle extraction phase of rTIL decode)
    // -----------------------------------------------------------------

    /**
     * Carve the largest axis-aligned rectangle anchored at {@code (curX, curY)}
     * inside {@code rgn} (greedy width-first, then height extending down only
     * while every cell in the row matches), clear it from the grid, and return
     * it.
     */
    static Rectangle carveRectangleAt(final boolean[][] rgn, final int curX, final int curY) {
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
    static boolean isRowFullyMatching(
            final boolean[][] rgn, final int yPos, final int startX, final int endX) {
        for (int xPos = startX; xPos < endX; ++xPos) {
            if (!rgn[yPos][xPos]) {
                return false;
            }
        }
        return true;
    }

    /** Mark every cell inside {@code r} as {@code false} (this part has been processed). */
    static void clearRectangleInGrid(final boolean[][] rgn, final Rectangle r) {
        final int endX = r.x + r.width;
        final int endY = r.y + r.height;
        for (int yPos = r.y; yPos < endY; ++yPos) {
            for (int xPos = r.x; xPos < endX; ++xPos) {
                rgn[yPos][xPos] = false;
            }
        }
    }

    // -----------------------------------------------------------------
    // Bit-arithmetic helpers (rTIL encoding/decoding)
    // -----------------------------------------------------------------

    /**
     * Read the encoded length for an rTIL entry, dispatching SHORT (1 byte) vs
     * LONG (2 byte) based on the type's even/odd parity.
     */
    static int getEncodedLength(final byte[] data, final int offset, final int type) {
        if (type % 2 == 0) {
            return getEncodedLength(data[offset]);
        }
        return getEncodedLength(data[offset], data[offset + 1]);
    }

    /** SHORT one-byte encoding length (1-32). */
    static int getEncodedLength(final byte one) {
        return getBitFragment(one, 4, 8) + 1;
    }

    /** LONG two-byte encoding length (1-1024). */
    static int getEncodedLength(final byte one, final byte two) {
        final int highByte = getBitFragment(one, 7, 8) << 8;
        return (highByte | (0xFF & two)) + 1;
    }

    /**
     * Get the type of encoding this is for eLVL rTIL encoding... will be a constant
     * such as {@link #SMALL_EMPTY_RUN} or {@link #LONG_REPEAT}.
     */
    static int getEncodedType(final byte typeByte) {
        return getBitFragment(typeByte, 1, 3);
    }

    /**
     * Get the bit fragment from {@code startIndex} to {@code endIndex} (inclusive,
     * 1-based MSB-first labelling: 1234 5678).
     */
    static int getBitFragment(final byte extractFrom, final int startIndex, final int endIndex) {
        final int shift = 8 - endIndex;
        final int numBits = endIndex - startIndex + 1;
        final byte mask = (byte) ((0x01 << numBits) - 1);
        return (extractFrom >> shift) & mask;
    }

    // -----------------------------------------------------------------
    // Misc
    // -----------------------------------------------------------------

    /** Bytes of zero padding to align an N-byte payload to a 4-byte boundary, or 0 if aligned. */
    static int alignmentPad(final int len) {
        final int padding = 4 - len % 4;
        return padding == 4 ? 0 : padding;
    }

    /** Returns a random Color, slightly biased away from very-dark values. */
    static Color getRandomColor() {
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
}
