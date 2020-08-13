package infinity.client.view;

import com.jme3.app.Application;
import com.jme3.app.SimpleApplication;
import com.jme3.light.AmbientLight;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.scene.Node;

/**
 *
 *
 * @author Paul Speed
 */
public class AmbientLightState extends com.jme3.app.state.BaseAppState {

    // public static final ColorRGBA DEFAULT_DIFFUSE = ColorRGBA.White.mult(2);
    public static final ColorRGBA DEFAULT_AMBIENT = ColorRGBA.White.mult(2);

    // private VersionedHolder<Vector3f> lightDir = new VersionedHolder<Vector3f>();

    // private ColorRGBA sunColor;
    // private DirectionalLight sun;
    private final ColorRGBA ambientColor;
    private AmbientLight ambient;
    // private float timeOfDay = FastMath.atan2(1, 0.3f) / FastMath.PI;
    // private float inclination = FastMath.HALF_PI - FastMath.atan2(1, 0.4f);
    // private float orientation = 0; //FastMath.HALF_PI;

    private Node rootNode; // the one we added the lights to

    public AmbientLightState() {
        this(FastMath.atan2(1, 0.3f) / FastMath.PI);
    }

    public AmbientLightState(@SuppressWarnings("unused") final float time) {
        // lightDir.setObject(new Vector3f(-0.2f, -1, -0.3f).normalizeLocal());
        ambientColor = DEFAULT_AMBIENT.clone();
    }

    public void setAmbient(final ColorRGBA ambient) {
        ambientColor.set(ambient);
    }

    public ColorRGBA getAmbient() {
        return ambientColor;
    }

    @Override
    protected void initialize(final Application app) {
        ambient = new AmbientLight();
        ambient.setColor(ambientColor);

        // setTimeOfDay(0.05f);
    }

    @Override
    protected void cleanup(final Application app) {
        return;
    }

    @Override
    protected void onEnable() {
        rootNode = ((SimpleApplication) getApplication()).getRootNode();
        rootNode.addLight(ambient);
    }

    @Override
    protected void onDisable() {
        rootNode.removeLight(ambient);
    }
}
