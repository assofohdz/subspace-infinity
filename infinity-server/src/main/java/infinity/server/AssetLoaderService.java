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

/**
 * Centralized server asset system. Used to loads settings files and resources
 * (like maps) that the server needs to create the game.
 *
 * <p>Asset locator strategy: registers {@link FileLocator}s pointing at
 * {@code ./assets/} and {@code ./zone/} relative to the JVM working dir
 * (= project root in dev with {@code workingDir = rootProject.projectDir},
 * = dist root in production where the gradle application dist places these
 * dirs alongside {@code bin/} and {@code lib/}). External-path locators win
 * over the classpath fallback so operator-edited zone Groovy presets pick
 * up via hot-reload without rebuilding.
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
        registerExternalLocators(am);
    }

    /**
     * Register file-system locators for the external {@code assets/} and
     * {@code zone/} dirs (working-dir relative). Skipped silently when the
     * dirs aren't present — falls back to classpath only (e.g. unit tests
     * that don't unpack a dist).
     */
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
