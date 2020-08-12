/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
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
    protected void onInitialize(HostedServiceManager serviceManager) {
        // Need to register our own AssetManager because this is run server side so
        // there's no jMonkeyEngine running
        am = JmeSystem.newAssetManager(
                Thread.currentThread().getContextClassLoader().getResource("com/jme3/asset/Desktop.cfg"));
    }

    @Override
    public void terminate(HostedServiceManager serviceManager) {

    }

    @Override
    public void start() {
    }

    @Override
    public void stop() {
    }

    public void registerLoader(Class<? extends AssetLoader> loaderClass, String... extensions) {
        am.registerLoader(loaderClass, extensions);
    }

    public <T> T loadAsset(AssetKey<T> key) {
        return am.loadAsset(key);
    }

    public Object loadAsset(String name) {
        return am.loadAsset(name);
    }

}
