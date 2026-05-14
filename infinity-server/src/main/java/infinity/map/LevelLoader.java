// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.map;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jme3.asset.AssetInfo;
import com.jme3.asset.AssetLoader;
import com.jme3.asset.AssetManager;

/** JME {@link AssetLoader} producing {@link LevelFile} from {@code .lvl}/{@code .lvz}. */
public class LevelLoader implements AssetLoader {

    private static final Logger log = LoggerFactory.getLogger(LevelLoader.class);

    private AssetManager am;
    public LevelFile lvlFile;

    @Override
    public LevelFile load(final AssetInfo assetInfo) throws IOException {

        am = assetInfo.getManager();
        final String file = assetInfo.getKey().getName();

        String errorWithELVL;
        BitMap bmp;

        try (InputStream is = assetInfo.openStream(); BufferedInputStream bis = new BufferedInputStream(is)) {
            bmp = new BitMap(bis);
            bmp.readBitMap(false);
        }
        try (InputStream is = assetInfo.openStream(); BufferedInputStream bis = new BufferedInputStream(is)) {
            if (bmp.isBitMap()) {
                lvlFile = new LevelFile(bis, bmp, true, bmp.hasELVL, file);
            } else {
                bmp = loadDefaultTileset();
                lvlFile = new LevelFile(bis, bmp, false, bmp.hasELVL, file);
            }
            errorWithELVL = lvlFile.readLevel();

            if (errorWithELVL != null) {

                lvlFile = new LevelFile(bis, bmp, false, false, file); // attempt load without meta data
                final String error = lvlFile.readLevel();

                if (error != null) { // I give up
                    log.error("First error = {}", errorWithELVL);
                    log.error("NON eLVL Load: {}", error);
                    throw new IOException("Corrupt LVL File");
                }
                log.warn("NON eLVL Load sucessful! Previous error: {}", errorWithELVL);
            }

            if (errorWithELVL != null) {
                log.warn("Error with eLVL Data!");
            }

        } catch (@SuppressWarnings("unused") final IOException e) {
            // Create our lvl file
            bmp = loadDefaultTileset();
            lvlFile = new LevelFile(bmp);
        }

        return lvlFile;
    }

    private BitMap loadDefaultTileset() {
        am.registerLoader(BitMapLoader.class, "bmp");
        return (BitMap) am.loadAsset("Textures/Tilesets/default.bmp");
    }

}
