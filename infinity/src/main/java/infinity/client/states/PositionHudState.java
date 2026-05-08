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
 * Top-left HUD overlay showing the player avatar's current world coord and (if the avatar
 * is inside a loaded arena) its arena-local coord. Both are reported in the game's world
 * space — there is no client-local coordinate transform applied.
 *
 * <p>World position is read from the {@link BlackboardState} key {@code "position"} —
 * published by {@code AvatarMovementState} once it has resolved the avatar entity and
 * picked up an interpolated {@link com.simsilica.bpos.BodyPosition} buffer. Lazy lookup in
 * {@link #update}, so this state's relative attach order vs. {@code AvatarMovementState}
 * doesn't matter — we just wait until the value shows up on the blackboard.
 *
 * <p>Per the arena-local convention defined in {@code ArenaSystem.arenaToWorld}: arena-local
 * {@code (0, 0)} is the {@code NW} corner and {@code (TILE_SIZE, TILE_SIZE)} is {@code SE}.
 * The render flips world {@code (max-X, max-Z)} onto the screen NW, so the inverse of the
 * spawn formula gives {@code arena = max - world} on each axis.
 *
 * <p>Read-only by design: the client never mutates arena bounds or avatar positions; both
 * arrive via the same Zay-ES / SimEthereal channels other client states already use.
 *
 * @author Asser Fahrenholz
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
    // No-op: GUI children are torn down with the parent on disable.
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
    posRef = null;
    if (avatarWatch != null) {
      avatarWatch.release();
      avatarWatch = null;
    }
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

  /**
   * Lazy-bind the position reference and return the avatar's current world position,
   * or {@code null} if the avatar is not yet observable. Updates the world/arena
   * placeholder labels as a side-effect for the not-ready states.
   */
  private Vec3d resolveAvatarWorld() {
    // Lazy-bind the position reference: AvatarMovementState publishes the avatar's
    // smoothed world position to BlackboardState["position"] in its initialize(), but
    // by the time it arrives depends on AppState ordering and on the avatar entity
    // being resolved server-side. Retrying every frame until the value appears keeps
    // this state oblivious to that timing.
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

  /**
   * Lazy-resolve the avatar entity id (GameSessionState fetches via RMI; may not
   * have arrived yet at this state's initialize), then lazy-bind the watch.
   * ed.getComponent on the client-side network proxy is unreliable for "not
   * currently observed" component values — ed.watchEntity is the canonical pattern
   * used elsewhere in the client (see AvatarMovementState for BodyPosition,
   * InfinityCameraState for the same).
   */
  private void updateArenaLabel(final Vec3d world) {
    if (avatarEntityId == null) {
      final EntityId id = getState(GameSessionState.class).getAvatarEntityId();
      if (id != null && !EntityId.NULL_ID.equals(id)) {
        avatarEntityId = id;
      }
    }
    if (avatarWatch == null && avatarEntityId != null) {
      avatarWatch = ed.watchEntity(avatarEntityId, ArenaId.class);
    }
    if (avatarWatch != null) {
      avatarWatch.applyChanges();
    }
    final ArenaId arenaId = avatarWatch != null ? avatarWatch.get(ArenaId.class) : null;
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
}
