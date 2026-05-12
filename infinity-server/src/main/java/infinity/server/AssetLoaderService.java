// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.server;

import com.jme3.asset.AssetKey;
import com.jme3.asset.AssetLoader;
import com.jme3.asset.AssetManager;
import com.jme3.asset.plugins.FileLocator;
import com.jme3.network.service.AbstractHostedService;
import com.jme3.network.service.HostedServiceManager;
import com.jme3.system.JmeSystem;
import java.io.File;

/** Server-side asset manager; registers {@code ./assets/} + {@code ./zone/} as file locators so operator edits hot-reload over the classpath. */
public class AssetLoaderService extends AbstractHostedService {

    private AssetManager am;

    @Override
    protected void onInitialize(final HostedServiceManager serviceManager) {
        // Server-side AssetManager — no jMonkeyEngine renderer.
        am = JmeSystem.newAssetManager(
                Thread.currentThread().getContextClassLoader().getResource("com/jme3/asset/Desktop.cfg"));
        registerExternalLocators(am);
    }

    private static void registerExternalLocators(final AssetManager mgr) {
        final File assetsDir = new File("assets");
        if (assetsDir.isDirectory()) {
            mgr.registerLocator(assetsDir.getAbsolutePath(), FileLocator.class);
        }
        final File zoneDir = new File("zone");
        if (zoneDir.isDirectory()) {
            mgr.registerLocator(zoneDir.getAbsolutePath(), FileLocator.class);
        }
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
