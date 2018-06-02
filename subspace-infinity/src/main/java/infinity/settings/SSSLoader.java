/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package infinity.settings;

import com.jme3.asset.AssetInfo;
import com.jme3.asset.AssetLoader;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import org.ini4j.Ini;

/**
 *
 * @author Asser Fahrenholz
 */
public class SSSLoader implements AssetLoader {

    private static final int GROUP = 1;
    private static final int KEY = 2;
    private static final int VALUE = 3;
    private static final int MIN = 4;
    private static final int MAX = 5;
    private static final int DESC = 6;

    @Override
    public ArrayList<String[]> load(AssetInfo assetInfo) throws IOException {

        ArrayList<String[]> result = new ArrayList<>();

        InputStream stream = assetInfo.openStream();

        InputStreamReader streamReader = new InputStreamReader(stream);

        try (BufferedReader br = new BufferedReader(streamReader)) {
            String line;

            while ((line = br.readLine()) != null) {
                String[] values = line.split(":");
                for (String str : values) {
                    System.out.println(str);
                }

                result.add(values);
            }
        }

        return result;
    }
}
