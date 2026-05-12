// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.map;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;

import com.jme3.asset.AssetInfo;
import com.jme3.asset.AssetLoader;

/** JME {@link AssetLoader} producing {@link BitMap}. */
public class BitMapLoader implements AssetLoader {

    @Override
    public BitMap load(final AssetInfo assetInfo) throws IOException {
        try (InputStream is = assetInfo.openStream(); BufferedInputStream bis = new BufferedInputStream(is)) {
            final BitMap bmp = new BitMap(bis);
            bmp.readBitMap(false);
            return bmp;
        }
    }
}
