package infinity.client;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.texture.Texture;
import com.simsilica.lemur.Container;
import com.simsilica.lemur.GuiGlobals;
import com.simsilica.lemur.component.BorderLayout;
import com.simsilica.lemur.component.QuadBackgroundComponent;
import com.simsilica.lemur.input.InputMapper;
import infinity.CoreGameConstants;
import infinity.Main;
import infinity.MainGameFunctions;
import infinity.client.view.ModelViewState;
import infinity.client.view.ModelViewState.Mob;
import infinity.client.view.ModelViewState.MobContainer;
import infinity.client.view.ModelViewState.ModelContainer;
import infinity.client.view.PaintableTexture;
import java.awt.Color;
import java.util.HashSet;
import org.dyn4j.geometry.Vector2;

/**
 * For information on how to do this, check out:
 * https://www.unknowncheats.me/forum/general-programming-and-reversing/135529-implement-simple-radar.html
 * or look at page 212 in the book:
 * 'Game-Programming-Algorithms-and-Techniques-A-Platform-Agnostic-Approach'
 * TODO: Should be done via an alternate viewport and scene (with
 * EntityComponents that hold RadarSymbol-information)
 *
 * @author Asser Fahrenholz
 */
public class RadarStateTexture extends BaseAppState {

    //Needed these to initialize GUI element
    private Container window;
    private QuadBackgroundComponent bg;
    int radarWidth = 300;
    int radarHeight = 300;

    Vector2 radarCenter = new Vector2(radarWidth / 2, radarWidth / 2);

    private int camHeight;
    private int camWidth;

    private HashSet<Vector2> testCoords = new HashSet<>();

    //Needed these to draw texture
    private ModelViewState mvs;
    ModelContainer models;
    MobContainer mobs;
    Spatial playerSpatial;

    //Texture to draw on
    private PaintableTexture texture;

    public static final float RADARRANGE = CoreGameConstants.GRID_CELL_SIZE;
    private final float radarRadius = 150;
    private Vector2 playerCoords;

    //Colors:
    private final Color colorMap = Color.WHITE;
    private final Color colorMobs = Color.WHITE;
    private final Color colorMe = Color.WHITE;
    private final Color colorBackground = Color.DARK_GRAY;

    //ColorRGBAs:
    private final ColorRGBA colorMapRGBA = ColorRGBA.White;
    private final ColorRGBA colorMobsRGBA = ColorRGBA.White;
    private final ColorRGBA colorMeRGBA = ColorRGBA.White;
    private final ColorRGBA colorBackgroundRGBA = ColorRGBA.Green;//this.copyWithAlpha(ColorRGBA.LightGray, 0);

    public RadarStateTexture() {
        setEnabled(false);
    }

    public void close() {
        setEnabled(false);
    }

    public void toggleEnabled() {
        setEnabled(!isEnabled());
    }

    @Override
    protected void initialize(Application app) {

        mvs = getState(ModelViewState.class);
        models = mvs.getModelContainer();
        mobs = mvs.getMobContainer();
        //playerSpatial = mvs.getPlayerSpatial();

        camWidth = getApplication().getCamera().getWidth();
        camHeight = getApplication().getCamera().getHeight();

        InputMapper inputMapper = GuiGlobals.getInstance().getInputMapper();
        inputMapper.addDelegate(MainGameFunctions.F_RADAR, this, "toggleEnabled");
        window = new Container(new BorderLayout());

        //Label title = window.addChild(new Label("Radar", new ElementId("title")));
        //title.setInsets(new Insets3f(2, 2, 0, 2
        this.resetTexture();

        bg = new QuadBackgroundComponent(texture.getTexture());

        window.setBackground(bg);

        //Can be used to set alpha on entire map gui element
        window.setAlpha(0.5f, true);

        //loadTestBlips();
    }

    @Override
    protected void cleanup(Application app) {
        InputMapper inputMapper = GuiGlobals.getInstance().getInputMapper();
        inputMapper.removeDelegate(MainGameFunctions.F_RADAR, this, "toggleEnabled");
    }

    @Override
    protected void onEnable() {
        // Setup the panel for display
        Node gui = ((Main) getApplication()).getGuiNode();

        window.setPreferredSize(new Vector3f(radarWidth, radarHeight, 0));

        window.setLocalTranslation(camWidth - radarWidth, radarHeight,
                0);

        gui.attachChild(window);
        GuiGlobals.getInstance().requestFocus(window);
    }

    @Override
    protected void onDisable() {
        window.removeFromParent();
    }

    @Override
    public void update(float tpf) {
        if (playerSpatial == null) {
            playerSpatial = mvs.getPlayerSpatial();
            if (playerSpatial == null) {
                return;
            }
        }
        playerCoords = convertToVec2(playerSpatial.getWorldTranslation());

        this.resetTexture();

        for (Spatial s : models.getArray()) {
            Vector2 vec = this.convertToVec2(s.getWorldTranslation());

            Vector2 playerToBlip = vec.copy().subtract(playerCoords);
            if (playerToBlip.getMagnitude() <= RADARRANGE) {
                Vector2 scaledBlipToPlayerVec = this.getScaledBlipToPlayerVector(playerToBlip.copy());
                addBlipToRadar(scaledBlipToPlayerVec, colorMap);
            }
        }

        for (Mob m : mobs.getArray()) {
            //Visibility on a mob defines whether it's in range related to ZONES in the network distributor of positions
            //It does not relate to x-radar/stealth etc.
            if (m.isVisible()) {

                Vector2 vec = this.convertToVec2(m.getSpatial().getWorldTranslation());

                Vector2 playerToBlip = vec.copy().subtract(playerCoords);
                if (playerToBlip.getMagnitude() <= RADARRANGE) {
                    Vector2 scaledBlipToPlayerVec = this.getScaledBlipToPlayerVector(playerToBlip.copy());
                    addBlipToRadar(scaledBlipToPlayerVec, colorMobs);
                }
            }
        }

        for (Vector2 vec : testCoords) {
            Vector2 playerToBlip = vec.copy().subtract(playerCoords);
            if (playerToBlip.getMagnitude() <= RADARRANGE) {
                Vector2 scaledBlipToPlayerVec = this.getScaledBlipToPlayerVector(playerToBlip.copy());
                addBlipToRadar(scaledBlipToPlayerVec, colorMe);
            }
        }
        bg.setTexture(texture.getTexture());
    }

    private Vector2 getScaledBlipToPlayerVector(Vector2 playerToBlip) {

        playerToBlip.multiply(1f / RADARRANGE);
        playerToBlip.multiply(radarRadius);
        return playerToBlip;
    }

    private void addBlipToRadar(Vector2 scaledRelativeBlip, Color color) {

        texture.setPixel((int) (radarCenter.x + scaledRelativeBlip.x), (int) (radarCenter.y + scaledRelativeBlip.y), color);

    }

    private void loadTestBlips() {
        testCoords.add(new Vector2(10, 10));
        testCoords.add(new Vector2(20, 20));
        testCoords.add(new Vector2(10, 30));
        testCoords.add(new Vector2(10, -10));
        testCoords.add(new Vector2(-10, -20));
    }

    private Vector2 convertToVec2(Vector3f vec3f) {
        return new Vector2(vec3f.x, vec3f.y);
    }

    private void resetTexture() {
        texture = new PaintableTexture(radarWidth, radarHeight);
        texture.setBackground(colorBackground);
        texture.setMagFilter(Texture.MagFilter.Bilinear);
    }

    private ColorRGBA copyWithAlpha(ColorRGBA color, float alpha) {
        ColorRGBA newColor = color.clone();
        newColor.set(color.r, color.g, color.b, alpha);
        return newColor;
    }
}
