/*
 * Copyright (c) 2018-2026, Asser Fahrenholz
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

package infinity.settings;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;

import com.jme3.asset.AssetInfo;
import com.jme3.asset.AssetLoader;

/**
 *
 * @author Asser Fahrenholz
 */
public class SSSLoader implements AssetLoader {

    @SuppressWarnings("unused")
    private static final int GROUP = 1;
    @SuppressWarnings("unused")
    private static final int KEY = 2;
    @SuppressWarnings("unused")
    private static final int VALUE = 3;
    @SuppressWarnings("unused")
    private static final int MIN = 4;
    @SuppressWarnings("unused")
    private static final int MAX = 5;
    @SuppressWarnings("unused")
    private static final int DESC = 6;

    @Override
    public ArrayList<String[]> load(final AssetInfo assetInfo) throws IOException {

        final ArrayList<String[]> result = new ArrayList<>();

        final InputStream stream = assetInfo.openStream();

        final InputStreamReader streamReader = new InputStreamReader(stream);

        try (BufferedReader br = new BufferedReader(streamReader)) {
            String line;

            while ((line = br.readLine()) != null) {
                final String[] values = line.split(":");
                /*
                 * for (String str : values) { System.out.println(str); }
                 */
                result.add(values);
            }
        }

        return result;
    }
}
