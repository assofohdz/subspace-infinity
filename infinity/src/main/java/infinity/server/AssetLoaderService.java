// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.server;

import com.jme3.asset.AssetKey;
import com.jme3.asset.AssetLoader;
import com.jme3.asset.AssetManager;
import com.jme3.network.service.AbstractHostedService;
import com.jme3.network.service.HostedServiceManager;
import com.jme3.system.JmeSystem;

/**
 * Centralized server asset system. Used to loads settings files and resources
 * (like maps) that the server needs to create the game
 *
 * @author Asser Fahrenholz
 */
public class AssetLoaderService extends AbstractHostedService {

    private AssetManager am;

    @Override
    protected void onInitialize(final HostedServiceManager serviceManager) {
        // Need to register our own AssetManager because this is run server side so
        // there's no jMonkeyEngine running
        am = JmeSystem.newAssetManager(
                Thread.currentThread().getContextClassLoader().getResource("com/jme3/asset/Desktop.cfg"));
    }

    @Override
    public void terminate(final HostedServiceManager serviceManager) {
        return;
    }

    @Override
    public void start() {
        return;
    }

    @Override
    public void stop() {
        return;
    }

    public void registerLoader(final Class<? extends AssetLoader> loaderClass, final String... extensions) {
        am.registerLoader(loaderClass, extensions);
    }

    public <T> T loadAsset(final AssetKey<T> key) {
        return am.loadAsset(key);
    }

    public Object loadAsset(final String name) {
        return am.loadAsset(name);
    }

}
