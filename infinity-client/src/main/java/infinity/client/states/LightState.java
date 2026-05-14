// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.client.states;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.jme3.light.PointLight;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.simsilica.bpos.BodyPosition;
import com.simsilica.es.Entity;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.es.common.Decay;
import infinity.Main;
import infinity.client.ConnectionState;
import infinity.es.PointLightComponent;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages dynamic jME {@link PointLight}s attached to moving entities (ships,
 * decaying effects).
 *
 * <p>Static world lighting (walls, sun) is <em>not</em> handled here — it's
 * baked as vertex colours via MOSS voxel lighting. See
 * {@link infinity.client.view.LanternBlockFactory}, {@code TileLit.frag}, and
 * the {@link com.simsilica.mblock.LightUtils#recalculateLighting} call in
 * {@link LocalViewState}. The tile shader reads those vertex colours directly
 * and ignores jME lights, so jME {@code PointLight}s added by this state only
 * affect non-tile scene geometry.
 *
 * <p>Decaying lights dim per-frame against {@code Decay.getPercentRemaining}.
 */
public class LightState extends BaseAppState {

  static Logger log = LoggerFactory.getLogger(LightState.class);
  private final Map<EntityId, PointLight> pointLightMap = new HashMap<>();
  private EntitySet movingPointLights;
  private EntitySet decayingPointLights;
  private Node rootNode;
  private TimeState timeState;

  public LightState() {
    log.debug("Constructed LightState");
  }

  @Override
  protected void initialize(final Application app) {
    final EntityData ed = getState(ConnectionState.class).getEntityData();
    movingPointLights = ed.getEntities(PointLightComponent.class, BodyPosition.class);
    decayingPointLights = ed.getEntities(PointLightComponent.class, Decay.class);
    timeState = getState(TimeState.class);
  }

  @Override
  protected void cleanup(final Application app) {
    movingPointLights.release();
    movingPointLights = null;
    decayingPointLights.release();
    decayingPointLights = null;
  }

  @Override
  protected void onEnable() {
    rootNode = ((Main) getApplication()).getRootNode();
  }

  @Override
  protected void onDisable() {
    // no-op: BaseAppState lifecycle slot; EntitySets are released in cleanup() per entity-sets.md
  }

  @Override
  public void update(final float tpf) {
    final long time = timeState.getTime();

    if (movingPointLights.applyChanges()) {
      for (final Entity e : movingPointLights.getAddedEntities()) {
        createLight(e);
      }
      for (final Entity e : movingPointLights.getRemovedEntities()) {
        removeLight(e.getId());
      }
    }

    // Track the light to its entity's spatial each frame (conveyor-adjusted).
    for (final Entity e : movingPointLights) {
      final PointLight pl = pointLightMap.get(e.getId());
      if (pl == null) {
        continue;
      }
      final Spatial s = getState(ModelViewState.class).getModel(e.getId());
      if (s == null) {
        continue;
      }
      final PointLightComponent plc = e.get(PointLightComponent.class);
      pl.setPosition(s.getWorldTranslation().add(plc.getOffset().toVector3f()));
    }

    decayingPointLights.applyChanges();
    for (final Entity e : decayingPointLights) {
      final PointLight pl = pointLightMap.get(e.getId());
      if (pl == null) {
        continue;
      }
      final PointLightComponent plc = e.get(PointLightComponent.class);
      final Decay d = e.get(Decay.class);
      final float percentageRemFloat = (float) d.getPercentRemaining(time);
      pl.setColor(plc.getColor().mult(percentageRemFloat));
    }
  }

  private void createLight(final Entity e) {
    final PointLightComponent plc = e.get(PointLightComponent.class);
    final PointLight pl = new PointLight();
    pl.setColor(plc.getColor());
    pl.setRadius(plc.getRadius());

    final Spatial s = getState(ModelViewState.class).getModel(e.getId());
    if (s != null) {
      pl.setPosition(s.getWorldTranslation().add(plc.getOffset().toVector3f()));
    }

    pointLightMap.put(e.getId(), pl);
    rootNode.addLight(pl);
  }

  private void removeLight(final EntityId id) {
    final PointLight pl = pointLightMap.remove(id);
    if (pl != null) {
      rootNode.removeLight(pl);
    }
  }
}
