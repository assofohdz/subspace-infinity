// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.client.states;

import com.jme3.app.Application;
import com.jme3.app.SimpleApplication;
import com.jme3.app.state.BaseAppState;
import com.jme3.input.KeyInput;
import com.jme3.material.MatParam;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;

import com.simsilica.lemur.ActionButton;
import com.simsilica.lemur.CallMethodAction;
import com.simsilica.lemur.Container;
import com.simsilica.lemur.GuiGlobals;
import com.simsilica.lemur.Insets3f;
import com.simsilica.lemur.Label;
import com.simsilica.lemur.Slider;
import com.simsilica.lemur.component.SpringGridLayout;
import com.simsilica.lemur.core.VersionedReference;
import com.simsilica.lemur.input.FunctionId;
import com.simsilica.lemur.input.InputMapper;
import com.simsilica.lemur.style.ElementId;

import infinity.client.view.BlockGeometryIndex;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Live-tuning Lemur panel for ambient light and the TileLit shader knobs
 * (PoolGain, SunScale, Exposure, TextureGamma). Toggle with F8.
 *
 * @author Asser Fahrenholz
 */
public class LightingTunerState extends BaseAppState {

    private static final Logger log = LoggerFactory.getLogger(LightingTunerState.class);

    private static final String POOL_GAIN_PARAM = "PoolGain";
    private static final String SUN_SCALE_PARAM = "SunScale";
    private static final String EXPOSURE_PARAM = "Exposure";
    private static final String FORMAT_TWO_DECIMALS = "%.2f";

    public static final FunctionId F_LIGHTING_TUNER = new FunctionId("Lighting Tuner");

    private Container panel;

    // Ambient RGB (0–3 overdrive so the tonemap doesn't swallow it).
    private Slider rSlider;
    private Slider gSlider;
    private Slider bSlider;
    private Label rValue;
    private Label gValue;
    private Label bValue;
    private VersionedReference<Double> rRef;
    private VersionedReference<Double> gRef;
    private VersionedReference<Double> bRef;

    // TileLit shader knobs.
    private Slider poolGainSlider;
    private Slider sunScaleSlider;
    private Slider exposureSlider;
    private Slider textureGammaSlider;
    private Label poolGainValue;
    private Label sunScaleValue;
    private Label exposureValue;
    private Label textureGammaValue;
    private VersionedReference<Double> poolGainRef;
    private VersionedReference<Double> sunScaleRef;
    private VersionedReference<Double> exposureRef;
    private VersionedReference<Double> textureGammaRef;

    public LightingTunerState() {
        setEnabled(false);
    }

    public static void initializeDefaultMappings(final InputMapper inputMapper) {
        inputMapper.map(F_LIGHTING_TUNER, KeyInput.KEY_F8);
    }

    public void toggleEnabled() {
        setEnabled(!isEnabled());
    }

    @Override
    protected void initialize(final Application app) {
        GuiGlobals.getInstance().getInputMapper().addDelegate(F_LIGHTING_TUNER, this, "toggleEnabled");

        panel = new Container(new SpringGridLayout(), new ElementId("window"), "glass");
        panel.addChild(new Label("Lighting (F8)", new ElementId("title")));

        panel.addChild(new Label("Ambient"));
        final Container ambientRows = panel.addChild(new Container(new SpringGridLayout()));
        ambientRows.setInsets(new Insets3f(2, 10, 5, 10));

        rSlider = new Slider();
        gSlider = new Slider();
        bSlider = new Slider();
        rValue = new Label("");
        gValue = new Label("");
        bValue = new Label("");
        addSliderRow(ambientRows, "R", rSlider, rValue, 0, 3);
        addSliderRow(ambientRows, "G", gSlider, gValue, 0, 3);
        addSliderRow(ambientRows, "B", bSlider, bValue, 0, 3);

        panel.addChild(new Label("Tile Shader"));
        final Container shaderRows = panel.addChild(new Container(new SpringGridLayout()));
        shaderRows.setInsets(new Insets3f(2, 10, 5, 10));

        poolGainSlider = new Slider();
        sunScaleSlider = new Slider();
        exposureSlider = new Slider();
        textureGammaSlider = new Slider();
        poolGainValue = new Label("");
        sunScaleValue = new Label("");
        exposureValue = new Label("");
        textureGammaValue = new Label("");
        addSliderRow(shaderRows, POOL_GAIN_PARAM, poolGainSlider, poolGainValue, 0, 15);
        addSliderRow(shaderRows, SUN_SCALE_PARAM, sunScaleSlider, sunScaleValue, 0, 2);
        addSliderRow(shaderRows, EXPOSURE_PARAM, exposureSlider, exposureValue, 0.1, 5);
        addSliderRow(shaderRows, "TexGamma", textureGammaSlider, textureGammaValue, 0.3, 1.5);

        panel.addChild(new ActionButton(new CallMethodAction("Reset", this, "resetAll")))
                .setInsets(new Insets3f(5, 10, 10, 10));

        rRef = rSlider.getModel().createReference();
        gRef = gSlider.getModel().createReference();
        bRef = bSlider.getModel().createReference();
        poolGainRef = poolGainSlider.getModel().createReference();
        sunScaleRef = sunScaleSlider.getModel().createReference();
        exposureRef = exposureSlider.getModel().createReference();
        textureGammaRef = textureGammaSlider.getModel().createReference();
    }

    private void addSliderRow(final Container rows, final String name, final Slider slider,
                              final Label value, final double min, final double max) {
        rows.addChild(new Label(name));
        slider.getModel().setMinimum(min);
        slider.getModel().setMaximum(max);
        slider.setDelta((max - min) / 100.0);
        slider.setPreferredSize(new Vector3f(200, 20, 0));
        rows.addChild(slider, 1);
        rows.addChild(value, 2);
    }

    private Material getTileMaterial() {
        final LocalViewState lv = getState(LocalViewState.class);
        if (lv == null || lv.getGeomIndex() == null) {
            return null;
        }
        return lv.getGeomIndex().getTileMaterial();
    }

    private float readFloatParam(final Material mat, final String name, final float fallback) {
        final MatParam p = mat.getParam(name);
        if (p == null || !(p.getValue() instanceof Float)) {
            return fallback;
        }
        return (Float) p.getValue();
    }

    private void syncSlidersFromState() {
        final ColorRGBA c = getState(AmbientLightState.class).getAmbient();
        rSlider.getModel().setValue(c.r);
        gSlider.getModel().setValue(c.g);
        bSlider.getModel().setValue(c.b);
        rRef.update();
        gRef.update();
        bRef.update();
        updateAmbientLabels(c);

        final Material mat = getTileMaterial();
        final float pg = mat != null ? readFloatParam(mat, POOL_GAIN_PARAM, BlockGeometryIndex.DEFAULT_POOL_GAIN)
                : BlockGeometryIndex.DEFAULT_POOL_GAIN;
        final float ss = mat != null ? readFloatParam(mat, SUN_SCALE_PARAM, BlockGeometryIndex.DEFAULT_SUN_SCALE)
                : BlockGeometryIndex.DEFAULT_SUN_SCALE;
        final float ex = mat != null ? readFloatParam(mat, EXPOSURE_PARAM, BlockGeometryIndex.DEFAULT_EXPOSURE)
                : BlockGeometryIndex.DEFAULT_EXPOSURE;
        final float tg = mat != null ? readFloatParam(mat, "TextureGamma", BlockGeometryIndex.DEFAULT_TEXTURE_GAMMA)
                : BlockGeometryIndex.DEFAULT_TEXTURE_GAMMA;
        poolGainSlider.getModel().setValue(pg);
        sunScaleSlider.getModel().setValue(ss);
        exposureSlider.getModel().setValue(ex);
        textureGammaSlider.getModel().setValue(tg);
        poolGainRef.update();
        sunScaleRef.update();
        exposureRef.update();
        textureGammaRef.update();
        updateShaderLabels(pg, ss, ex, tg);
    }

    private void updateAmbientLabels(final ColorRGBA c) {
        rValue.setText(String.format("%.3f", c.r));
        gValue.setText(String.format("%.3f", c.g));
        bValue.setText(String.format("%.3f", c.b));
    }

    private void updateShaderLabels(final float pg, final float ss, final float ex, final float tg) {
        poolGainValue.setText(String.format(FORMAT_TWO_DECIMALS, pg));
        sunScaleValue.setText(String.format(FORMAT_TWO_DECIMALS, ss));
        exposureValue.setText(String.format(FORMAT_TWO_DECIMALS, ex));
        textureGammaValue.setText(String.format(FORMAT_TWO_DECIMALS, tg));
    }

    protected void resetAll() {
        getState(AmbientLightState.class).setAmbient(AmbientLightState.DEFAULT_AMBIENT);
        final Material mat = getTileMaterial();
        if (mat != null) {
            mat.setFloat(POOL_GAIN_PARAM, BlockGeometryIndex.DEFAULT_POOL_GAIN);
            mat.setFloat(SUN_SCALE_PARAM, BlockGeometryIndex.DEFAULT_SUN_SCALE);
            mat.setFloat(EXPOSURE_PARAM, BlockGeometryIndex.DEFAULT_EXPOSURE);
            mat.setFloat("TextureGamma", BlockGeometryIndex.DEFAULT_TEXTURE_GAMMA);
        }
        syncSlidersFromState();
    }

    @Override
    public void update(final float tpf) {
        final boolean ambientChanged = rRef.update() | gRef.update() | bRef.update();
        if (ambientChanged) {
            final AmbientLightState als = getState(AmbientLightState.class);
            final ColorRGBA c = als.getAmbient();
            final ColorRGBA next = new ColorRGBA(
                    (float) rSlider.getModel().getValue(),
                    (float) gSlider.getModel().getValue(),
                    (float) bSlider.getModel().getValue(),
                    c.a);
            als.setAmbient(next);
            updateAmbientLabels(next);
            log.info("ambient = ({}, {}, {})", next.r, next.g, next.b);
        }

        final boolean shaderChanged = poolGainRef.update() | sunScaleRef.update()
                | exposureRef.update() | textureGammaRef.update();
        if (shaderChanged) {
            final Material mat = getTileMaterial();
            if (mat == null) {
                return;
            }
            final float pg = (float) poolGainSlider.getModel().getValue();
            final float ss = (float) sunScaleSlider.getModel().getValue();
            final float ex = (float) exposureSlider.getModel().getValue();
            final float tg = (float) textureGammaSlider.getModel().getValue();
            mat.setFloat(POOL_GAIN_PARAM, pg);
            mat.setFloat(SUN_SCALE_PARAM, ss);
            mat.setFloat(EXPOSURE_PARAM, ex);
            mat.setFloat("TextureGamma", tg);
            updateShaderLabels(pg, ss, ex, tg);
            log.info("tile shader: PoolGain={}, SunScale={}, Exposure={}, TextureGamma={}", pg, ss, ex, tg);
        }
    }

    @Override
    protected void cleanup(final Application app) {
        GuiGlobals.getInstance().getInputMapper().removeDelegate(F_LIGHTING_TUNER, this, "toggleEnabled");
    }

    @Override
    protected void onEnable() {
        final Node gui = ((SimpleApplication) getApplication()).getGuiNode();
        syncSlidersFromState();

        final int width = getApplication().getCamera().getWidth();
        final int height = getApplication().getCamera().getHeight();
        final Vector3f pref = panel.getPreferredSize();
        panel.setLocalTranslation(width - pref.x - 10, height - 10, 100);

        gui.attachChild(panel);
        GuiGlobals.getInstance().requestCursorEnabled(this);
    }

    @Override
    protected void onDisable() {
        panel.removeFromParent();
        GuiGlobals.getInstance().releaseCursorEnabled(this);
    }
}
