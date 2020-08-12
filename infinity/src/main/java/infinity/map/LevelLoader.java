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
import java.io.BufferedInputStream;
import java.io.IOException;
import java.util.Vector;

import com.jme3.asset.AssetInfo;
import com.jme3.asset.AssetLoader;
import com.jme3.asset.AssetManager;

/**
 *
 * @author Asser
 */
public class LevelLoader implements AssetLoader {

    private String m_file;
    private BitMap m_bitmap;
    private BufferedInputStream m_stream;
    private boolean m_containsBM;
    private boolean hasELVLData;

    // eLVL ATTR tags... vector of vector of Strings
    public Vector eLvlAttrs = new Vector();
    public static final int DEFAULT_TAG_COUNT = 6;

    // Vector of loaded reginons
    public Vector regions;

    // unkownn ELVL chunks read in on load
    public Vector unknownELVLData = new Vector();

    // the actual data we're going to save, as a Vector of Bytes... saved by
    // makeELvlDataForSaving
    public Vector eLVLData;

    private String m_type;
    private int m_size;
    private int m_offset;
    private int m_width;
    private int m_height;
    private int m_bitCount;
    private int m_compressionType;
    private int m_colorsUsed;
    protected Image m_tileset;
    protected Image[] m_tiles;
    protected short[][] m_map;

    private short[][] m_level = new short[1024][1024];
    private AssetManager am;
    // The levelfile
    public LevelFile m_lvlFile;

    @Override
    public LevelFile load(AssetInfo assetInfo) throws IOException {

        am = assetInfo.getManager();
        m_file = assetInfo.getKey().getName();

        String errorWithELVL = null;

        try {
            BitMap bmp = new BitMap(new BufferedInputStream(assetInfo.openStream()));

            bmp.readBitMap(false);

            BufferedInputStream bis = new BufferedInputStream(assetInfo.openStream());

            if (bmp.isBitMap()) {

                m_lvlFile = new LevelFile(bis, bmp, true, bmp.hasELVL, m_file);
            } else {
                bmp = loadDefaultTileset();
                m_lvlFile = new LevelFile(bis, bmp, false, bmp.hasELVL, m_file);
            }
            errorWithELVL = m_lvlFile.readLevel();

            if (errorWithELVL != null) {

                m_lvlFile = new LevelFile(bis, bmp, false, false, m_file); // attempt load without meta data
                String error = m_lvlFile.readLevel();

                if (error != null) { // I give up
                    System.out.println("First error = " + errorWithELVL);
                    System.out.println("NON eLVL Load: " + error);

                    throw new IOException("Corrupt LVL File");
                } else {
                    System.out.println("NON eLVL Load sucessful! Previous error: " + errorWithELVL);
                }
            }

            m_tileset = m_lvlFile.getTileSet();

            m_map = m_lvlFile.getMap();
            m_tiles = m_lvlFile.getTiles();

            if (errorWithELVL != null) {
                System.out.println("Error with eLVL Data!");
            }

        } catch (IOException e) {
            // Create our lvl file
            BitMap bmp = loadDefaultTileset();
            m_lvlFile = new LevelFile(bmp);

            m_tileset = m_lvlFile.getTileSet();
            m_map = m_lvlFile.getMap();
            m_tiles = m_lvlFile.getTiles();
        }

        return m_lvlFile;
    }

    private BitMap loadDefaultTileset() {
        am.registerLoader(BitMapLoader.class, "bmp");
        BitMap bmp = (BitMap) am.loadAsset("Textures/Tilesets/default.bmp");
        return bmp;
    }

}
