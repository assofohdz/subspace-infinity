// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.map;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;

import com.jme3.asset.AssetInfo;
import com.jme3.asset.AssetLoader;
import com.jme3.asset.AssetManager;

/** JME {@link AssetLoader} producing {@link LevelFile} from {@code .lvl}/{@code .lvz}. */
public class LevelLoader implements AssetLoader {

    private AssetManager am;
    public LevelFile m_lvlFile;

    @Override
    public LevelFile load(final AssetInfo assetInfo) throws IOException {

        am = assetInfo.getManager();
        final String m_file = assetInfo.getKey().getName();

        String errorWithELVL;
        BitMap bmp;

        try (InputStream is = assetInfo.openStream(); BufferedInputStream bis = new BufferedInputStream(is)) {
            bmp = new BitMap(bis);
            bmp.readBitMap(false);
        }
        try (InputStream is = assetInfo.openStream(); BufferedInputStream bis = new BufferedInputStream(is)) {
            if (bmp.isBitMap()) {
                m_lvlFile = new LevelFile(bis, bmp, true, bmp.hasELVL, m_file);
            } else {
                bmp = loadDefaultTileset();
                m_lvlFile = new LevelFile(bis, bmp, false, bmp.hasELVL, m_file);
            }
            errorWithELVL = m_lvlFile.readLevel();

            if (errorWithELVL != null) {

                m_lvlFile = new LevelFile(bis, bmp, false, false, m_file); // attempt load without meta data
                final String error = m_lvlFile.readLevel();

                if (error != null) { // I give up
                    System.out.println("First error = " + errorWithELVL);
                    System.out.println("NON eLVL Load: " + error);
                    throw new IOException("Corrupt LVL File");
                }
                System.out.println("NON eLVL Load sucessful! Previous error: " + errorWithELVL);
            }

            if (errorWithELVL != null) {
                System.out.println("Error with eLVL Data!");
            }

        } catch (@SuppressWarnings("unused") final IOException e) {
            // Create our lvl file
            bmp = loadDefaultTileset();
            m_lvlFile = new LevelFile(bmp);
        }

        return m_lvlFile;
    }

    private BitMap loadDefaultTileset() {
        am.registerLoader(BitMapLoader.class, "bmp");
        final BitMap bmp = (BitMap) am.loadAsset("Textures/Tilesets/default.bmp");
        return bmp;
    }

}
