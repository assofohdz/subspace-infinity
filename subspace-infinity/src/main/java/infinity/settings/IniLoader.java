/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package infinity.settings;

import com.jme3.asset.AssetInfo;
import com.jme3.asset.AssetLoader;
import java.io.IOException;
import org.ini4j.Ini;

/**
 *
 * @author Asser Fahrenholz
 */
public class IniLoader implements AssetLoader {

    @Override
    public Ini load(AssetInfo assetInfo) throws IOException {

        Ini result = new Ini(assetInfo.openStream());

        return result;
    }
}
