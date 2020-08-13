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
import java.awt.image.ImageObserver;
import java.awt.image.PixelGrabber;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.util.Vector;

import javax.swing.JOptionPane;

public class BitmapSaving {
    private static final int imageWidth = 304;
    private static final int imageHeight = 160;

    public static void saveAs256ColorBitmap(final BufferedOutputStream bos, final Image i, final int eLVLDataSize)
            throws IOException {
        final BitmapSaving bs = new BitmapSaving();
        bs.saveBitmap(bos, i, eLVLDataSize);
    }

    public void saveBitmap(final BufferedOutputStream bos, final Image i, final int eLVLDataSize) throws IOException {
        final BitmapFileHeader bfh = new BitmapFileHeader(eLVLDataSize);

        bfh.save(bos);

        new BitmapInfoHeader().save(bos);

        // before we can get a color table we need to make our image... sounds weird but
        // how else would we know
        // what do use for our color table
        final int[] allPixels = getAllColors(i);
        final byte[] bitmapData = new byte[imageWidth * imageHeight];
        int counter = 0;

        for (int y = imageHeight - 1; y >= 0; --y) {
            for (int x = 0; x < imageWidth; ++x) {
                final int[] rgb = getRGB(allPixels[x + y * imageWidth]);

                final RGBQuad col = new RGBQuad((byte) rgb[0], (byte) rgb[1], (byte) rgb[2]);
                final byte colorEntry = getColorEntry(col);
                bitmapData[counter++] = colorEntry;
            }
        }

        padColorTable(); // pad up to 256 entries, ss requirement

        // 54 + 256 * 4 = 1078; 1078 / 4 = 269.5 = NOT on 32 bit boundry, why does this
        // work??
        // 304 = 76 * 4, always on 32 bit boundry

        // now color table
        for (final Object element : colorTable) {
            final RGBQuad item = (RGBQuad) element;
            item.save(bos);
        }

        // now save bytes
        bos.write(bitmapData);
    }

    public void padColorTable() {
        for (int x = colorTable.size(); x < 256; ++x) {
            colorTable.add(new RGBQuad((byte) 0, (byte) 0, (byte) 0));
        }
    }

    Vector<RGBQuad> colorTable = new Vector<>();

    // gets the color entry for the color col, adding if necessary
    byte getColorEntry(final RGBQuad col) {
        for (int x = 0; x < colorTable.size(); ++x) {
            final RGBQuad item = colorTable.get(x);

            if (item.equals(col)) {
                return (byte) x;
            }
        }

        if (colorTable.size() == 256) {
            System.out.println("WARNING: Tileset tried to exceed 256 colors, using color 255.");
            return (byte) 255;
        }

        colorTable.add(col);
        return (byte) (colorTable.size() - 1);

    }

    /*
     * public static void debug(BufferedInputStream bis) throws IOException { byte
     * []pal = new byte[256 * 4]; bis.read(pal,0,54); bis.read(pal,0,256 * 4);
     *
     * for (int x = 0; x < 256; ++x) { System.out.println("red = " + pal[x * 4] +
     * ", blue = " + pal[x * 4 + 1] + ", green = " + pal[x * 4 + 2]); } }
     */

    public static byte[] toDWORD(final int number) {
        int i = number;
        final byte[] DWORD = new byte[4];

        DWORD[0] = (byte) (i & 0xff);
        i = i >> 8;

        DWORD[1] = (byte) (i & 0xff);
        i = i >> 8;

        DWORD[2] = (byte) (i & 0xff);
        i = i >> 8;

        DWORD[3] = (byte) (i & 0xff);

        return DWORD;
    }

    public static byte[] toWORD(final int number) {
        int i = number;
        final byte[] WORD = new byte[2];

        WORD[0] = (byte) (i & 0xff);
        i = i >> 8;

        WORD[1] = (byte) (i & 0xff);
        i = i >> 8;

        return WORD;
    }

    public static int[] getAllColors(final Image theImage) {
        final int w = theImage.getWidth(null);
        final int h = theImage.getHeight(null);

        int[] storeHere = new int[w * h];
        final PixelGrabber pg = new PixelGrabber(theImage, 0, 0, w, h, storeHere, 0, w);
        try {
            pg.grabPixels();
        } catch (@SuppressWarnings("unused") final InterruptedException e) {
            JOptionPane.showMessageDialog(null, "interrupted waiting for pixels!");
            storeHere = null;
            return null;
        }
        if ((pg.getStatus() & ImageObserver.ABORT) != 0) {
            JOptionPane.showMessageDialog(null, "image fetch aborted or errored");
            storeHere = null;
            return null;
        }

        return storeHere;
    }

    public static int[] getRGB(final int pixel) {
        final int[] rgb = new int[3];

        rgb[0] = (pixel >> 16) & 0xff;
        rgb[1] = (pixel >> 8) & 0xff;
        rgb[2] = (pixel) & 0xff;

        return rgb;
    }

    public class BitmapFileHeader {
        public final static int SIZE = 14;
        byte[] bfType = new byte[2]; // 2 Bytes, Specifies the file type, must be BM
        byte[] bfSize = new byte[4]; // 4 Bytes, Specifies the size, in bytes, of the bitmap file
        byte[] bfReserved1 = new byte[2]; // 2 Bytes, Reserved; must be zero.
        byte[] bfReserved2 = new byte[2]; // 2 Bytes, Reserved; must be zero.
        byte[] bfOffBits = new byte[4]; // 4 Bytes, Specifies the offset, in bytes, from the beginning of
                                        // the BITMAPFILEHEADER structure to the bitmap bits.

        /**
         * Make a new bitmap file header
         *
         * @param eLVLDataSize the size of the ELVL data section
         */
        public BitmapFileHeader(final int eLVLDataSize) {
            bfType[0] = 'B';
            bfType[1] = 'M';

            int size = BitmapFileHeader.SIZE + BitmapInfoHeader.SIZE + 4 * 256 + imageWidth * imageHeight;

            if (eLVLDataSize > 0) {
                // add 2 padding, the header size, and the data size
                size += 2 + 12 + eLVLDataSize;
            }

            bfSize = toDWORD(size);

            if (eLVLDataSize == 0) {
                bfReserved1[0] = 0;
                bfReserved1[1] = 0;
            } else {
                bfReserved1 = toWORD(49720);
            }

            bfReserved2[0] = 0;
            bfReserved2[1] = 0;

            final int offset = BitmapFileHeader.SIZE + BitmapInfoHeader.SIZE + 4 * 256;
            bfOffBits = toDWORD(offset);
        }

        public void save(final BufferedOutputStream bos) throws IOException {
            bos.write(bfType);
            bos.write(bfSize);
            bos.write(bfReserved1);
            bos.write(bfReserved2);
            bos.write(bfOffBits);
        }
    }

    public class RGBQuad {
        byte rgbBlue;
        byte rgbGreen;
        byte rgbRed;
        byte rgbReserved;

        public RGBQuad(final byte r, final byte g, final byte b) {
            rgbRed = r;
            rgbGreen = g;
            rgbBlue = b;
            rgbReserved = 0;
        }

        public boolean equals(final RGBQuad other) {
            return (rgbBlue == other.rgbBlue && rgbRed == other.rgbRed && rgbGreen == other.rgbGreen);
        }

        public void save(final BufferedOutputStream bof) throws IOException {
            final byte[] ar = new byte[4];
            ar[0] = rgbBlue;
            ar[1] = rgbGreen;
            ar[2] = rgbRed;
            ar[3] = rgbReserved;

            bof.write(ar);
        }
    }

    public class BitmapInfoHeader {
        public static final int SIZE = 40;

        byte[] biSize = new byte[4]; // Specifies the number of bytes required by the structure.
        byte[] biWidth = new byte[4]; // Specifies the width of the bitmap, in pixels.
        byte[] biHeight = new byte[4]; // Specifies the height of the bitmap, in pixels.
                                       // If biHeight is positive, the bitmap is a bottom-up DIB
                                       // and its origin is the lower-left corner.
        byte[] biPlanes = new byte[2]; // Specifies the number of planes for the target device.
                                       // This value must be set to 1.

        byte[] biBitCount = new byte[2]; // Specifies the number of bits-per-pixel. 8 for 256 color
        byte[] biCompression = new byte[4]; // Specifies the type of compression,
                                            // 0 = BI_RGB = uncompressed
        byte[] biSizeImage = new byte[4]; // Specifies the size, in bytes, of the image.
                                          // This may be set to zero for BI_RGB bitmaps.
        byte[] biXPelsPerMeter = new byte[4]; // Specifies the horizontal resolution, in pixels-per-meter,
                                              // of the target device for the bitmap.
        byte[] biYPelsPerMeter = new byte[4]; // Specifies the vertical resolution, in pixels-per-meter,
                                              // of the target device for the bitmap.

        byte[] biClrUsed = new byte[4]; // Specifies the number of color indexes in the color table
                                        // that are actually used by the bitmap. If this value is
                                        // zero, the bitmap uses the maximum number of colors
                                        // corresponding to the value of the biBitCount member for
                                        // the compression mode specified by biCompression.
        byte[] biClrImportant = new byte[4]; // Specifies the number of color indexes that are required
                                             // for displaying the bitmap. If this value is zero, all
                                             // colors are required.

        public BitmapInfoHeader() {
            biSize = toDWORD(SIZE);
            biWidth = toDWORD(imageWidth);
            biHeight = toDWORD(imageHeight);
            biPlanes = toWORD(1);
            biBitCount = toWORD(8);
            biCompression = toDWORD(0);
            biSizeImage = toDWORD(0);
            biXPelsPerMeter = toDWORD(0);
            biYPelsPerMeter = toDWORD(0);
            biClrUsed = toDWORD(256);
            biClrImportant = toDWORD(256);
        }

        public void save(final BufferedOutputStream bos) throws IOException {
            bos.write(biSize);
            bos.write(biWidth);
            bos.write(biHeight);
            bos.write(biPlanes);
            bos.write(biBitCount);
            bos.write(biCompression);
            bos.write(biSizeImage);
            bos.write(biXPelsPerMeter);
            bos.write(biYPelsPerMeter);
            bos.write(biClrUsed);
            bos.write(biClrImportant);
        }

    }
}