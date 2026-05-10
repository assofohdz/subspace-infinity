// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.client.audio;

import com.jme3.app.Application;
import com.jme3.app.SimpleApplication;
import com.jme3.app.state.BaseAppState;
import com.jme3.asset.AssetManager;
import com.jme3.audio.AudioNode;
import com.jme3.audio.plugins.WAVLoader;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.simsilica.es.Entity;
import com.simsilica.es.EntityContainer;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.ext.mphys.SpawnPosition;
import infinity.client.ConnectionState;
import infinity.es.AudioType;
import infinity.es.AudioTypes;
import infinity.es.Parent;
import infinity.sim.util.InfinityRunTimeException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Asser
 */
public class AudioState extends BaseAppState {

  static Logger log = LoggerFactory.getLogger(AudioState.class);
  private final SIAudioFactory factory;
  private EntityData ed;
  private AudioContainer sounds;
  private Map<EntityId, AudioNode> soundIndex = new HashMap<>();
  private Node soundRoot;

  /**
   * Lookup table mapping {@link AudioTypes} string ids to the factory method
   * that builds the matching {@link AudioNode}. Replaces a 19-case switch in
   * {@link #createAudio} (the four per-level FIRE_BOMBS / FIRE_GUNS /
   * FIRE_MINE entries point at the same factory each). Bound to {@code this}
   * because every factory uses the {@link #factory} instance field.
   */
  private final Map<String, Function<Entity, AudioNode>> factories = Map.ofEntries(
      Map.entry(AudioTypes.FIRE_THOR, this::createFireThor),
      Map.entry(AudioTypes.PICKUP_PRIZE, this::createPickUpPrize),
      Map.entry(AudioTypes.FIRE_BOMBS_L1, this::createFireBombs),
      Map.entry(AudioTypes.FIRE_BOMBS_L2, this::createFireBombs),
      Map.entry(AudioTypes.FIRE_BOMBS_L3, this::createFireBombs),
      Map.entry(AudioTypes.FIRE_BOMBS_L4, this::createFireBombs),
      Map.entry(AudioTypes.FIRE_GRAVBOMB, this::createFireGravBomb),
      Map.entry(AudioTypes.FIRE_GUNS_L1, this::createFireGuns),
      Map.entry(AudioTypes.FIRE_GUNS_L2, this::createFireGuns),
      Map.entry(AudioTypes.FIRE_GUNS_L3, this::createFireGuns),
      Map.entry(AudioTypes.FIRE_GUNS_L4, this::createFireGuns),
      Map.entry(AudioTypes.EXPLOSION2, this::createExplosion2),
      Map.entry(AudioTypes.BURST, this::createBurst),
      Map.entry(AudioTypes.REPEL, this::createRepel),
      Map.entry(AudioTypes.FLAG, this::createFlag),
      Map.entry(AudioTypes.FIRE_MINE_L1, this::createMine),
      Map.entry(AudioTypes.FIRE_MINE_L2, this::createMine),
      Map.entry(AudioTypes.FIRE_MINE_L3, this::createMine),
      Map.entry(AudioTypes.FIRE_MINE_L4, this::createMine));

  public AudioState(final SIAudioFactory factory) {
    this.factory = factory;

    log.debug("Constructed AudioState");
  }

  @Override
  protected void initialize(final Application app) {
    if (app.getAudioRenderer() == null) {
      log.info("No audio renderer available - AudioState disabled");
      setEnabled(false);
      return;
    }

    factory.setState(this);
    ed = getState(ConnectionState.class).getEntityData();

    // Get asset manager to be able to retrieve the sounds
    AssetManager assets = app.getAssetManager();
    // Register default wave loader with the wa2 extension
    assets.registerLoader(WAVLoader.class, "wa2");

    soundRoot = new Node();
    soundIndex = new HashMap<>();
  }

  @Override
  protected void cleanup(final Application app) {
    // Nothing to do
  }

  @Override
  protected void onEnable() {
    if (ed == null) return;
    sounds = new AudioContainer(ed);
    sounds.start();
    ((SimpleApplication) getApplication()).getRootNode().attachChild(soundRoot);
  }

  @Override
  protected void onDisable() {
    if (sounds == null) return;
    sounds.stop();
    sounds = null;
  }

  @Override
  public void update(final float tpf) {
    if (sounds == null) return;
    sounds.update();
  }

  protected void removeSound(final Spatial spatial, final Entity entity) {
    soundIndex.remove(entity.getId());
    spatial.removeFromParent();
  }

  protected void updateSound(
      final Spatial spatial, final Entity entity, final boolean updatePosition) {
    if (updatePosition) {
      final Parent p = entity.get(Parent.class);
      if (p.getParentEntityId().getId() != 0L) {
        final SpawnPosition pos = entity.get(SpawnPosition.class);
        spatial.setLocalTranslation(pos.getLocation().toVector3f());
      }
    }
  }

  protected Spatial createAudio(final Entity entity) {

    // Check to see if one already exists
    AudioNode result = soundIndex.get(entity.getId());
    if (result != null) {
      result.playInstance();
      return result;
    }

    // Else figure out what type to create...
    final AudioType type = entity.get(AudioType.class);
    final String typeName = type.getTypeName(ed);
    final Function<Entity, AudioNode> factoryFn = factories.get(typeName);
    if (factoryFn == null) {
      throw new InfinityRunTimeException("Unknown audio type:" + typeName);
    }
    result = factoryFn.apply(entity);

    // Add it to the index
    soundIndex.put(entity.getId(), result);
    soundRoot.attachChild(result);

    if (result != null) {

      // result.setref
      result.playInstance();
    }
    return result;
  }

  private AudioNode createMine(Entity entity) {
    // Node information:
    final Node result = new Node("fireMine:" + entity.getId());
    result.setUserData("fireMineId", Long.valueOf(entity.getId().getId()));

    // Spatial information:
    final AudioNode an = factory.createAudio(entity);
    result.attachChild(an);

    return an;
  }

  private AudioNode createPickUpPrize(final Entity entity) {
    // Node information:
    final Node result = new Node("pickupPrize:" + entity.getId());
    result.setUserData("pickupPrizeId", Long.valueOf(entity.getId().getId()));

    // Spatial information:
    final AudioNode an = factory.createAudio(entity);
    result.attachChild(an);

    return an;
  }

  private AudioNode createFireThor(final Entity entity) {
    // Node information:
    final Node result = new Node("fireThor:" + entity.getId());
    result.setUserData("fireThorId", Long.valueOf(entity.getId().getId()));

    // Spatial information:
    final AudioNode an = factory.createAudio(entity);
    result.attachChild(an);

    return an;
  }

  private AudioNode createFireBombs(final Entity entity) {
    // Node information:
    final Node result = new Node("fireBombs:" + entity.getId());
    result.setUserData("fireBombsId", Long.valueOf(entity.getId().getId()));

    // Spatial information:
    final AudioNode an = factory.createAudio(entity);
    result.attachChild(an);

    return an;
  }

  private AudioNode createFireGravBomb(final Entity entity) {
    // Node information:
    final Node result = new Node("fireGravBomb:" + entity.getId());
    result.setUserData("fireGravBombId", Long.valueOf(entity.getId().getId()));

    // Spatial information:
    final AudioNode an = factory.createAudio(entity);
    result.attachChild(an);

    return an;
  }

  //Method to create the flag sound
  private AudioNode createFlag(final Entity entity) {
    // Node information:
    final Node result = new Node("flag:" + entity.getId());
    result.setUserData("flagId", Long.valueOf(entity.getId().getId()));

    // Spatial information:
    final AudioNode an = factory.createAudio(entity);
    result.attachChild(an);

    return an;
  }

  private AudioNode createRepel(final Entity entity) {
    // Node information:
    final Node result = new Node("repel:" + entity.getId());
    result.setUserData("repelId", Long.valueOf(entity.getId().getId()));

    // Spatial information:
    final AudioNode an = factory.createAudio(entity);
    result.attachChild(an);

    return an;
  }

  private AudioNode createFireGuns(final Entity entity) {
    // Node information:
    final Node result = new Node("fireGuns:" + entity.getId());
    result.setUserData("fireGunsId", Long.valueOf(entity.getId().getId()));

    // Spatial information:
    final AudioNode an = factory.createAudio(entity);
    result.attachChild(an);

    return an;
  }

  private AudioNode createExplosion2(final Entity entity) {
    // Node information:
    final Node result = new Node("explosion2:" + entity.getId());
    result.setUserData("explosion2Id", Long.valueOf(entity.getId().getId()));

    // Spatial information:
    final AudioNode an = factory.createAudio(entity);
    result.attachChild(an);

    return an;
  }

  private AudioNode createBurst(final Entity entity) {
    // Node information:
    final Node result = new Node("burstSound:" + entity.getId());
    result.setUserData("burstSoundId", Long.valueOf(entity.getId().getId()));

    // Spatial information:
    final AudioNode an = factory.createAudio(entity);
    result.attachChild(an);

    return an;
  }

  /** Contains all playing and future sounds */
  private class AudioContainer extends EntityContainer<Spatial> {

    @SuppressWarnings("unchecked")
    public AudioContainer(final EntityData ed) {
      super(ed, AudioType.class, Parent.class, SpawnPosition.class);
    }

    @Override
    protected Spatial addObject(final Entity e) {
      final Spatial result = createAudio(e);
      updateObject(result, e);
      return result;
    }

    @Override
    protected void updateObject(final Spatial object, final Entity e) {
      updateSound(object, e, true);
    }

    @Override
    protected void removeObject(final Spatial object, final Entity e) {
      removeSound(object, e);
    }
  }
}
