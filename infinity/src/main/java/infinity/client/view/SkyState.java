// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.client.view;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.jme3.scene.Spatial;
import com.jme3.texture.Texture;
import com.jme3.util.SkyFactory;

import infinity.Main;

/**
 *
 *
 * @author Paul Speed
 */
public class SkyState extends BaseAppState {

    static Logger log = LoggerFactory.getLogger(SkyState.class);
    private Spatial sky;

    public SkyState() {
        log.debug("Constructed SkyState");
    }

    @Override
    protected void initialize(final Application app) {

        final Texture texture1 = app.getAssetManager().loadTexture("Textures/galaxy+Z.jpg");
        final Texture texture2 = app.getAssetManager().loadTexture("Textures/galaxy-Z.jpg");
        final Texture texture3 = app.getAssetManager().loadTexture("Textures/galaxy+X.jpg");
        final Texture texture4 = app.getAssetManager().loadTexture("Textures/galaxy-X.jpg");
        final Texture texture5 = app.getAssetManager().loadTexture("Textures/galaxy+Y.jpg");
        final Texture texture6 = app.getAssetManager().loadTexture("Textures/galaxy-Y.jpg");

        sky = SkyFactory.createSky(app.getAssetManager(), texture1, texture2, texture3, texture4, texture5, texture6);
    }

    @Override
    protected void cleanup(final Application app) {
        return;
    }

    @Override
    protected void onEnable() {
        ((Main) getApplication()).getRootNode().attachChild(sky);
    }

    @Override
    protected void onDisable() {
        sky.removeFromParent();
    }
}
