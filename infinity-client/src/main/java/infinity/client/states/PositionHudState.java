// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.client.states;

import com.jme3.app.Application;
import com.jme3.app.SimpleApplication;
import com.jme3.app.state.BaseAppState;
import com.jme3.scene.Node;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.WatchedEntity;
import com.simsilica.lemur.Container;
import com.simsilica.lemur.Insets3f;
import com.simsilica.lemur.Label;
import com.simsilica.mathd.Vec3d;
import com.simsilica.lemur.core.VersionedObject;
import com.simsilica.lemur.core.VersionedReference;
import com.simsilica.state.BlackboardState;
import infinity.client.ConnectionState;
import infinity.client.GameSessionState;
import infinity.es.arena.ArenaId;

/**
 * Top-left HUD: avatar world coord + arena-local coord.
 *
 * <p>World position is read from {@code BlackboardState["position"]} (published by
 * {@link infinity.client.AvatarMovementState} once its {@link com.simsilica.bpos.BodyPosition}
 * buffer resolves — never poll {@code ed.getComponent}; that's unreliable for the proxy).
 *
 * <p>Arena-local follows the convention from {@code ArenaSystem.arenaToWorld}:
 * arena-local {@code (0, 0)} is the NW corner, so {@code arena = max - world}
 * (per axis) inverts the render flip.
 */
public class PositionHudState extends BaseAppState {

  private EntityData ed;
  private EntityId avatarEntityId;
  private ArenaRegistryState arenaRegistry;
  private BlackboardState blackboard;
  private VersionedReference<Vec3d> posRef;
  private WatchedEntity avatarWatch;
  private Container hud;
  private Label worldLabel;
  private Label arenaLabel;

  @Override
  protected void initialize(final Application app) {
    ed = getState(ConnectionState.class).getEntityData();
    arenaRegistry = getState(ArenaRegistryState.class);
    blackboard = getState(BlackboardState.class, true);
    // avatarEntityId is resolved lazily in update() — GameSessionState fetches it via
    // an RMI roundtrip on connect, and depending on AppState attach order this state
    // can initialize before that result arrives. AvatarMovementState uses the same
    // lazy-resolve pattern; reading the id here would cache a null forever.

    hud = new Container();
    worldLabel = hud.addChild(new Label("world: -"));
    worldLabel.setInsets(new Insets3f(2, 6, 2, 6));
    arenaLabel = hud.addChild(new Label("arena: -"));
    arenaLabel.setInsets(new Insets3f(2, 6, 2, 6));
  }

  @Override
  protected void cleanup(final Application app) {
    if (avatarWatch != null) {
      avatarWatch.release();
      avatarWatch = null;
    }
  }

  @Override
  protected void onEnable() {
    final Node gui = ((SimpleApplication) getApplication()).getGuiNode();
    final int height = getApplication().getCamera().getHeight();
    // Top-left, 10px in from each edge, slightly above mid-Z layer.
    hud.setLocalTranslation(10f, height - 10f, 100f);
    gui.attachChild(hud);
  }

  @Override
  protected void onDisable() {
    hud.removeFromParent();
    // posRef is cleared so the next enable cycle re-binds against the
    // current blackboard publisher (defensive — the published
    // VersionedObject may be replaced while we're disabled).
    posRef = null;
  }

  @Override
  public void update(final float tpf) {
    final Vec3d world = resolveAvatarWorld();
    if (world == null) {
      return;
    }
    worldLabel.setText(String.format("world:  %.0f, %.0f", world.x, world.z));
    updateArenaLabel(world);
  }

  // Returns null if the avatar is not yet observable; also updates the placeholder labels for the not-ready state.
  private Vec3d resolveAvatarWorld() {
    // Lazy-bind: AvatarMovementState publishes "position" timing depends on attach order + RMI roundtrip.
    if (posRef == null && blackboard != null) {
      @SuppressWarnings("unchecked")
      final VersionedObject<Vec3d> holder =
          (VersionedObject<Vec3d>) blackboard.get("position");
      if (holder != null) {
        posRef = holder.createReference();
      }
    }
    if (posRef == null) {
      worldLabel.setText("world: (waiting for avatar)");
      arenaLabel.setText("arena: -");
      return null;
    }
    posRef.update();
    final Vec3d world = posRef.get();
    if (world == null) {
      worldLabel.setText("world: (loading)");
      arenaLabel.setText("arena: -");
      return null;
    }
    return world;
  }

  private void updateArenaLabel(final Vec3d world) {
    final ArenaId arenaId = resolveArenaId();
    if (arenaId == null || arenaRegistry == null) {
      arenaLabel.setText("arena: (no-arena void)");
      return;
    }
    final ArenaRegistryState.ArenaSnapshot snap = arenaRegistry.byName(arenaId.getArena());
    if (snap == null) {
      arenaLabel.setText("arena: " + arenaId.getArena() + " (bounds not yet synced)");
      return;
    }
    // Inverse of ArenaSystem.arenaToWorld — arena = max - world (per axis).
    final double localX = snap.max.x - world.x;
    final double localZ = snap.max.z - world.z;
    arenaLabel.setText(
        String.format("arena:  %s  (%.0f, %.0f)", snap.arenaName, localX, localZ));
  }

  // Lazy-resolves avatar id (RMI) and binds a one-component watch on its {@link ArenaId}.
  private ArenaId resolveArenaId() {
    if (avatarEntityId == null) {
      final EntityId id = getState(GameSessionState.class).getAvatarEntityId();
      if (id != null && !EntityId.NULL_ID.equals(id)) {
        avatarEntityId = id;
      }
    }
    if (avatarWatch == null && avatarEntityId != null) {
      avatarWatch = ed.watchEntity(avatarEntityId, ArenaId.class);
    }
    if (avatarWatch == null) {
      return null;
    }
    avatarWatch.applyChanges();
    return avatarWatch.get(ArenaId.class);
  }
}
