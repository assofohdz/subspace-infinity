/*
 * $Id$
 *
 * Copyright (c) 2017, Simsilica, LLC
 * All rights reserved.
 */

package infinity.client.states;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.simsilica.bpos.BodyPosition;
import com.simsilica.es.Entity;
import com.simsilica.es.EntityContainer;
import com.simsilica.es.EntityData;
import com.simsilica.es.Name;
import com.simsilica.ethereal.TimeSource;
import com.simsilica.lemur.Label;
import com.simsilica.lemur.style.ElementId;
import com.simsilica.mathd.Vec3i;
import com.simsilica.mathd.trans.PositionTransition3d;
import com.simsilica.mathd.trans.TransitionBuffer;
import infinity.Main;
import infinity.client.ConnectionState;
import infinity.client.GameSessionState;
import infinity.es.Frequency;
import infinity.sim.CoreViewConstants;

/**
 * Displays a HUD label for any entity with a BodyPosition and a Name.
 *
 * @author Paul Speed
 */
public class HudLabelState extends BaseAppState {

  private final Vec3i centerWorld = new Vec3i();
  private EntityData ed;
  private TimeSource timeSource;
  private Node hudLabelRoot;
  private Camera camera;
  private LabelContainer labels;
  private ModelViewState modelView;
  private int playerFrequency;

  @Override
  protected void initialize(Application app) {
    hudLabelRoot = new Node("HUD labels");

    this.camera = app.getCamera();
    this.modelView = getState(ModelViewState.class);

    // Retrieve the time source from the network connection
    // The time source will give us a time in recent history that we should be
    // viewing.  This currently defaults to -100 ms but could vary (someday) depending
    // on network connectivity.
    // For more information on this interpolation approach, see the Valve networking
    // articles at:
    // https://developer.valvesoftware.com/wiki/Source_Multiplayer_Networking
    // https://developer.valvesoftware.com/wiki/Latency_Compensating_Methods_in_Client/Server_In-game_Protocol_Design_and_Optimization
    this.timeSource = getState(ConnectionState.class).getRemoteTimeSource();

    this.ed = getState(ConnectionState.class).getEntityData();
  }

  @Override
  protected void cleanup(Application app) {
    //No cleanup to do
  }

  @Override
  protected void onEnable() {

    labels = new LabelContainer(ed);
    labels.start();

    ((Main) getApplication()).getGuiNode().attachChild(hudLabelRoot);
  }

  @Override
  protected void onDisable() {
    hudLabelRoot.removeFromParent();

    labels.stop();
    labels = null;
  }

  @Override
  public void update(float tpf) {

    // Grab a consistent time for this frame
    long time = timeSource.getTime();

    // Update all of the models
    labels.update();
    for (LabelHolder label : labels.getArray()) {
      label.update(time);
    }
  }

  /**
   * Holds the on-screen label and the transition buffer, etc necessary for managing the position
   * and state of the label. If not for the need to poll these once per frame for position updates,
   * we technically could have done all management in the EntityContainer and just returned Labels
   * directly.
   */
  private class LabelHolder {

    Entity entity;
    Label label;
    Vector3f labelOffset = new Vector3f(0, 0.1f, -CoreViewConstants.SHIPSIZE);
    boolean visible;
    boolean isPlayerEntity;
    Spatial modelSpatial;

    TransitionBuffer<? extends PositionTransition3d> buffer;

    public LabelHolder(Entity entity) {
      this.entity = entity;

      this.modelSpatial = modelView.getModelSpatial(entity.getId(), true);

      this.label = new Label("Ship", new ElementId("ship.label"));
      label.setColor(ColorRGBA.Green);
      label.setShadowColor(ColorRGBA.Black);

      BodyPosition bodyPos = entity.get(BodyPosition.class);
      // BodyPosition requires special management to make
      // sure all instances of BodyPosition are sharing the same
      // thread-safe history buffer.  Everywhere it's used, it should
      // be 'initialized'.
      bodyPos.initialize(entity.getId(), 12);
      buffer = bodyPos.getBuffer();

      // If this is the player's ship then we don't want the model
      // shown else it looks bad.  A) it's ugly.  B) the model will
      // always lag the player's turning.
      if (entity.getId().getId() == getState(GameSessionState.class).getAvatarEntityId().getId()) {
        this.isPlayerEntity = true;
      }

      // Pick up the current name
      updateComponents();
    }

    protected void updateLabelPos(Vector3f pos) {
      Vector3f loc = modelSpatial.getWorldTranslation();

      if (!visible || isPlayerEntity) {
        return;
      }
      Vector3f camRelative = pos.subtract(camera.getLocation());
      float distance = camera.getDirection().dot(camRelative);
      if (distance < 0) {
        // It's behind us
        label.removeFromParent();
        return;
      }

      // Calculate the ship's position on screen
      Vector3f screen2 = camera.getScreenCoordinates(loc.add(labelOffset));

      Vector3f pref = label.getPreferredSize();
      label.setLocalTranslation(
          screen2.x - pref.x * 0.5f - centerWorld.x, screen2.y + pref.y - centerWorld.z, screen2.z);

      if (label.getParent() == null) {
        hudLabelRoot.attachChild(label);
      }
    }

    public void update(long time) {

      // Look back in the brief history that we've kept and
      // pull an interpolated value.  To do this, we grab the
      // span of time that contains the time we want.  PositionTransition
      // represents a starting and an ending pos+rot over a span of time.
      PositionTransition3d trans = buffer.getTransition(time);
      if (trans != null) {
        Vector3f pos = trans.getPosition(time, true).toVector3f();
        setVisible(trans.getVisibility(time));
        updateLabelPos(pos);
      }
    }

    protected void updateComponents() {
      label.setText(entity.get(Name.class).getName());

      if (isPlayerEntity) {
        playerFrequency = entity.get(Frequency.class).getFrequency();
      } else if (playerFrequency != entity.get(Frequency.class).getFrequency()) {
        // Set the colour of the label to light blue
        label.setColor(ColorRGBA.Blue);
      } else {
        // Set the colour of the label to green
        label.setColor(ColorRGBA.Green);
      }
    }

    protected void setVisible(boolean f) {
      if (this.visible == f) {
        return;
      }
      this.visible = f;
      if (visible && !isPlayerEntity) {
        label.setCullHint(Spatial.CullHint.Inherit);
      } else {
        label.setCullHint(Spatial.CullHint.Always);
      }
    }

    public void dispose() {
      label.removeFromParent();
    }
  }

  private class LabelContainer extends EntityContainer<LabelHolder> {
    public LabelContainer(EntityData ed) {
      super(ed, Name.class, BodyPosition.class, Frequency.class);
    }

    @Override
    protected LabelHolder addObject(Entity e) {
      return new LabelHolder(e);
    }

    @Override
    protected void updateObject(LabelHolder object, Entity e) {
      object.updateComponents();
      // Update other labels if the player updates his frequency
      if (object.isPlayerEntity) {
        updateLabelColours();
      }
    }

    /**
     * Need to make this public so we can access it from the outer class
     * @return the array of LabelHolders
     */
    @Override
    public LabelHolder[] getArray() {
      return super.getArray();
    }

    public void updateLabelColours() {
      for (LabelHolder labelHolder : getArray()) {
        // Dont update if the label is the player's
        if (labelHolder.isPlayerEntity) {
          continue;
        }
        labelHolder.updateComponents();
      }
    }

    @Override
    protected void removeObject(LabelHolder object, Entity e) {
      object.dispose();
    }
  }
}
