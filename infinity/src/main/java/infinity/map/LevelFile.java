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

import java.awt.Image;
import java.awt.image.MemoryImageSource;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Vector;

import javax.swing.JOptionPane;
import javax.swing.JPanel;

public class LevelFile extends JPanel {
    private static final long serialVersionUID = -4658344536954311587L;
    public String m_file;
    private final BitMap m_bitmap;
    private BufferedInputStream m_stream;
    private boolean m_containsBM;
    private boolean hasELVLData;

    // eLVL ATTR tags... vector of vector of Strings
    public Vector<Vector<String>> eLvlAttrs = new Vector<>();
    public static final int DEFAULT_TAG_COUNT = 6;

    // Vector of loaded regions
    public Vector<Region> loadedRegions;

    // unknown ELVL chunks read in on load
    public Vector<Byte> unknownELVLData = new Vector<>();

    // the actual data we're going to save, as a Vector of Bytes... saved by
    // makeELvlDataForSaving
    public Vector<Byte> eLVLData;

    // private String m_type;
    // private int m_size;
    // private int m_offset;
    // private int m_width;
    // private int m_height;
    // private int m_bitCount;
    // private int m_compressionType;
    // private int m_colorsUsed;

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
     * Add the default eLvl tags to this level file.
     */
    public void addDefaultELvLTags() {
        String userName = System.getProperty("user.name");
        if (userName == null) {
            userName = "User";
        }

        Vector<String> row = new Vector<>();
        row.add("NAME");
        row.add("Unnamed");
        eLvlAttrs.add(row);

        row = new Vector<>();
        row.add("VERSION");
        row.add("1.0");
        eLvlAttrs.add(row);

        row = new Vector<>();
        row.add("ZONE");
        row.add(userName + "'s Zone");
        eLvlAttrs.add(row);

        row = new Vector<>();
        row.add("MAPCREATOR");
        row.add(userName);
        eLvlAttrs.add(row);

        row = new Vector<>();
        row.add("TILESETCREATOR");
        row.add(userName);
        eLvlAttrs.add(row);

        row = new Vector<>();
        row.add("PROGRAM");
        row.add("Continuum Level Ini Tool");
        eLvlAttrs.add(row);
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
            final ByteArray headerArray = new ByteArray(header);
            ByteArray curData;

            if (!headerArray.readString(0, 4).equals("elvl")) {
                error = "The elvl header tag was not detected at the start of " + " the eLVL data section.";
            } else {
                final int size = headerArray.readLittleEndianInt(4); // total size of the metadata section
                int current = 12; // current number of bytes read

                while (current < size && error == null) {
                    if (available(8)) {
                        curData = new ByteArray(readIn(8));
                        current += 8;
                        final String type = curData.readString(0, 4);
                        final int chunkLength = curData.readLittleEndianInt(4);

                        if (!available(chunkLength)) {
                            error = "EOF while reading in a eLVL chunk of type " + type;

                            break;
                        }

                        if (type.equals("ATTR")) { // attribute chunk
                            current += chunkLength;
                            curData = new ByteArray(readIn(chunkLength));
                            final String attr = curData.readString(0, chunkLength);
                            final String[] keyTag = attr.split("=");
                            if (keyTag.length != 2) {
                                error = "ATTR tag does not contain exactly " + "one '=' sign: " + attr;
                                break;
                            }

                            final Vector<String> row = new Vector<>();
                            row.add(keyTag[0]);
                            row.add(keyTag[1]);
                            eLvlAttrs.add(row);
                        } else if (type.equals("REGN")) { // region chunk
                            curData = new ByteArray(readIn(chunkLength));
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
                            curData = new ByteArray(readIn(chunkLength));

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
                                final byte b = curData.readByte(c);
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
                                JOptionPane.showMessageDialog(null, "EOF while reading eLVL chunk padding.");
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
                final ByteArray array = new ByteArray(b);
                final int i = array.readLittleEndianInt(0);
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

    /**
     * Save the level file with a different file name
     *
     * @param where   the new file to save it as
     * @param tileset the tileset to save
     * @param map     the map array[][] to save
     * @param regions the vector of regions to save
     */
    public void saveLevelAs(final String where, final Image tileset, final short[][] map,
            final Vector<Region> regions) {
        m_file = where;
        saveLevel(tileset, map, regions);
    }

    /**
     * make the eLVL data and store it as a Vector of Bytes in eLVLData, not
     * including the header
     *
     * @param regions the vector of Regions
     */
    private void makeELvlDataForSaving(final Vector<Region> regions) {
        eLVLData = new Vector<>();

        // first save the ATTR tags
        final int size = eLvlAttrs.size();
        for (int x = 0; x < size; ++x) {
            final Vector<String> row = eLvlAttrs.get(x);
            final String one = row.get(0).replace('=', '-');
            final String two = row.get(1).replace('=', '-');
            final String save = one + "=" + two;

            // save chunk header
            eLVLData.add(Byte.valueOf((byte) 'A'));
            eLVLData.add(Byte.valueOf((byte) 'T'));
            eLVLData.add(Byte.valueOf((byte) 'T'));
            eLVLData.add(Byte.valueOf((byte) 'R'));

            final int chunkLength = save.length();
            final byte[] chunkSizeBytes = BitmapSaving.toDWORD(chunkLength);
            for (int c = 0; c < 4; ++c) {
                eLVLData.add(Byte.valueOf(chunkSizeBytes[c]));
            }

            final int len = save.length();
            for (int c = 0; c < len; ++c) {
                final byte letter = (byte) save.charAt(c);
                eLVLData.add(Byte.valueOf(letter));
            }

            // padding
            final int padding = 4 - (chunkLength % 4);
            if (padding != 4) {
                for (int c = 0; c < padding; ++c) {
                    eLVLData.add(Byte.valueOf((byte) 0)); // padding byte
                }
            }
        }

        // now the REGN tags
        for (final Object region : regions) {
            final Region r = (Region) region;

            eLVLData.add(Byte.valueOf((byte) 'R'));
            eLVLData.add(Byte.valueOf((byte) 'E'));
            eLVLData.add(Byte.valueOf((byte) 'G'));
            eLVLData.add(Byte.valueOf((byte) 'N'));

            final Vector<Byte> curRegionEncoded = r.getEncodedRegion();
            final byte[] dword = BitmapSaving.toDWORD(curRegionEncoded.size());
            for (int c = 0; c < 4; ++c) {
                eLVLData.add(Byte.valueOf(dword[c]));
            }

            eLVLData.addAll(curRegionEncoded);
        }

        // now any unknown tags we enocuntered while loading
        eLVLData.addAll(unknownELVLData);
    }

    /**
     * Save the ELVL data to the current position in the stream. It's stored in a
     * vector of Bytes in eLVLData
     *
     * @param out the output stream to save to
     */
    private void saveELvlData(final BufferedOutputStream out) throws IOException {
        final int size = eLVLData.size();
        final byte[] array = new byte[size];
        byte[] dword = new byte[4];
        final byte[] word = new byte[2];
        word[0] = word[1] = 0;

        // save two bytes padding
        out.write(word);

        // save header
        dword[0] = (byte) 'e';
        dword[1] = (byte) 'l';
        dword[2] = (byte) 'v';
        dword[3] = (byte) 'l';
        out.write(dword);

        dword = BitmapSaving.toDWORD(size + 12); // size
        out.write(dword);

        dword = BitmapSaving.toDWORD(0); // reserved
        out.write(dword);

        // save data
        for (int x = 0; x < size; ++x) {
            array[x] = eLVLData.get(x).byteValue();
        }

        out.write(array);

        eLVLData.clear();
    }

    /**
     * Actually save the .lvl file
     *
     * @param tileset the tileset to save it with
     * @param map     the map aray[][] to save
     * @param regions list of regions
     */
    public void saveLevel(final Image tileset, final short[][] map, final Vector<Region> regions) {
        try (FileOutputStream fos = new FileOutputStream(m_file);
                BufferedOutputStream out = new BufferedOutputStream(fos)) {
            // final boolean containsELVLData = eLvlAttrs.size() > 0;
            makeELvlDataForSaving(regions);

            // save bitmap
            BitmapSaving.saveAs256ColorBitmap(out, tileset, eLVLData.size());

            // save eLVL data
            if (eLVLData.size() > 0) {
                saveELvlData(out);
            }

            // save tiles
            for (int y = 0; y < 1024; ++y) {
                for (int x = 0; x < 1024; ++x) {
                    final int tile = map[x][y];

                    if (tile == 0 || tile == -1) {
                        continue;
                    }

                    final int intstruct = (tile << 24) | (y << 12) | x;
                    final byte[] ar = BitmapSaving.toDWORD(intstruct);
                    out.write(ar);
                }
            }

            out.close();
        } catch (final IOException e) {
            JOptionPane.showMessageDialog(null, e.toString());
        }

    }

    public byte[] readIn(final int n) {
        final byte[] b = new byte[n];
        try {
            m_stream.read(b);
            return b;
        } catch (final IOException e) {
            System.out.println(e);
            return new byte[0];
        }
    }

    public boolean available(final int n) {
        try {
            return m_stream.available() >= n;
        } catch (final IOException e) {
            System.out.println(e);
            return false;
        }
    }

    public Image getTileSet() {
        return m_bitmap.getImage();
    }

    public Image[] getTiles() {

        final int[] m_image = m_bitmap.getImageData();

        final Image[] tiles = new Image[190];
        for (int i = 0; i < 190; i++) {

            final int yOffset = (int) Math.floor(i / 19) * 4864;
            final int xOffset = (int) (i - Math.floor(i / 19) * 19) * 16;

            final int thisTile[] = new int[256];
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    thisTile[y * 16 + x] = m_image[(yOffset + xOffset) + y * 304 + x];
                }
            }
            tiles[i] = createImage(new MemoryImageSource(16, 16, thisTile, 0, 16));
        }
        return tiles;
    }

    public short[][] getMap() {
        return m_level;
    }
}
