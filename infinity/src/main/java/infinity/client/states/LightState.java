/*
 * Copyright (c) 2018-2026, Asser Fahrenholz
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages dynamic jME point lights attached to moving entities (ships, decaying effects).
 *
 * <p>Static world lighting (walls, sun) is baked as vertex colors via MOSS's voxel lighting
 * system — see {@code LanternBlockFactory}, {@code TileLit.frag}, and the
 * {@code LightUtils.recalculateLighting} call in {@code LocalViewState}. The tile shader
 * reads those vertex colors directly and ignores jME lights, so any point light added by
 * this state only affects non-tile scene geometry (if any exists in the future).
 *
 * @author Asser Fahrenholz
 */
public class LightState extends BaseAppState {

  static Logger log = LoggerFactory.getLogger(LightState.class);
  private final HashMap<EntityId, PointLight> pointLightMap = new HashMap<>();
  private EntityData ed;
  private EntitySet movingPointLights;
  private EntitySet decayingPointLights;
  private Node rootNode;
  private TimeState timeState;

  public LightState() {
    log.debug("Constructed LightState");
  }

  @Override
  protected void initialize(final Application app) {
    ed = getState(ConnectionState.class).getEntityData();
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
  protected void onDisable() {}

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
