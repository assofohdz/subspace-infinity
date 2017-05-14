/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package example.view;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.jme3.collision.CollisionResult;
import com.jme3.collision.CollisionResults;
import com.jme3.math.Ray;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.scene.Spatial;
import com.simsilica.lemur.event.CursorButtonEvent;
import com.simsilica.lemur.event.CursorListener;
import com.simsilica.lemur.event.CursorMotionEvent;
import com.simsilica.mathd.Vec3i;
import example.ConnectionState;
import example.net.client.GameSessionClientService;
import static example.view.PlayerMovementState.log;

/**
 *
 * @author Asser
 */
public class MapEditorState extends BaseAppState implements CursorListener { //TODO: Could also listen for keyboard inputs related to map editing

    private float xDown;
    private float yDown;
    private GameSessionClientService session;

    @Override
    protected void initialize(Application app) {
        
        log.info("initialize()");
        // Grab the game session
        session = getState(ConnectionState.class).getService(GameSessionClientService.class);
        if (session == null) {
            throw new RuntimeException("PlayerMovementState requires an active game session.");
        }
    }

    @Override
    protected void cleanup(Application app) {
        //No cleanup yet
    }

    @Override
    protected void onEnable() {
        log.info("onEnable");
    }

    @Override
    protected void onDisable() {
        log.info("onDisable");
    }

    @Override
    public void update(float tpf) {

    }

    protected void click(CursorButtonEvent event, Spatial target, Spatial capture) {
        //MapEditorStates are only added as listeners for arena spatials
        //assertEquals("arena", target.getName());

        Camera cam = getState(CameraState.class).getCamera();

        Vector2f click2d = new Vector2f(event.getX(), event.getY());

        Vector3f click3d = cam.getWorldCoordinates(click2d.clone(), 0f).clone();
        Vector3f dir = cam.getWorldCoordinates(click2d.clone(), 1f).subtractLocal(click3d).normalizeLocal();

        Ray ray = new Ray(click3d, dir);
        CollisionResults results = new CollisionResults();
        target.collideWith(ray, results);
        if (results.size() != 1) {
            log.error("There should only be one collision with the arena when the user clicks it");
        }
        for (CollisionResult r : results) {
            Vector3f contactPoint = r.getContactPoint();
            session.editMap(contactPoint.x, contactPoint.y);
        }
    }

    @Override
    public void cursorButtonEvent(CursorButtonEvent event, Spatial target, Spatial capture) {
        event.setConsumed();

        if (event.isPressed()) {
            xDown = event.getX();
            yDown = event.getY();
        } else {
            float x = event.getX();
            float y = event.getY();
            if (Math.abs(x - xDown) < 3 && Math.abs(y - yDown) < 3) {
                click(event, target, capture);
            }
        }
    }

    @Override
    public void cursorEntered(CursorMotionEvent cme, Spatial sptl, Spatial sptl1) {
        //
    }

    @Override
    public void cursorExited(CursorMotionEvent cme, Spatial sptl, Spatial sptl1) {
        //
    }

    @Override
    public void cursorMoved(CursorMotionEvent cme, Spatial sptl, Spatial sptl1) {
        //
    }
}