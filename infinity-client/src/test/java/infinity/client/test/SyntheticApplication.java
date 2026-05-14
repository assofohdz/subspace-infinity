// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.client.test;

import com.jme3.app.Application;
import com.jme3.app.LostFocusBehavior;
import com.jme3.app.state.AppStateManager;
import com.jme3.asset.AssetManager;
import com.jme3.asset.DesktopAssetManager;
import com.jme3.audio.AudioRenderer;
import com.jme3.audio.Listener;
import com.jme3.input.InputManager;
import com.jme3.profile.AppProfiler;
import com.jme3.renderer.Camera;
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.Renderer;
import com.jme3.renderer.ViewPort;
import com.jme3.system.AppSettings;
import com.jme3.system.JmeContext;
import com.jme3.system.Timer;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;

/**
 * Headless {@link Application} stub for {@code BaseAppState} lifecycle tests.
 *
 * <p>Implements only the subset of {@link Application} that the touched client
 * states actually call: {@link #getStateManager()}, {@link #getCamera()},
 * {@link #getAssetManager()}, {@link #getAudioRenderer()}, and the two
 * {@link #enqueue} overloads (which run synchronously for test determinism).
 * Everything else returns {@code null} or no-ops; expand on demand as new
 * states are tested. See {@code .claude/rules/entity-sets.md} for the leak
 * shape this harness exists to verify.
 */
public final class SyntheticApplication implements Application {

  private final AppStateManager stateManager;
  private final Camera camera;
  private final AssetManager assetManager;
  private final AudioRenderer audioRenderer;

  private SyntheticApplication(final Builder builder) {
    this.stateManager = new AppStateManager(this);
    this.camera = builder.camera;
    this.assetManager = builder.assetManager;
    this.audioRenderer = builder.audioRenderer;
  }

  /** New builder; defaults: 800x600 camera, real {@link DesktopAssetManager}, null audio renderer. */
  public static Builder builder() {
    return new Builder();
  }

  // --- methods the touched client states actually call ---------------------

  @Override
  public AppStateManager getStateManager() {
    return stateManager;
  }

  @Override
  public Camera getCamera() {
    return camera;
  }

  @Override
  public AssetManager getAssetManager() {
    return assetManager;
  }

  @Override
  public AudioRenderer getAudioRenderer() {
    return audioRenderer;
  }

  /** Synchronous to keep tests deterministic; jME's real impl marshals to the render thread. */
  @Override
  public <V> Future<V> enqueue(final Callable<V> callable) {
    final FutureTask<V> task = new FutureTask<>(callable);
    task.run();
    return task;
  }

  @Override
  public void enqueue(final Runnable runnable) {
    runnable.run();
  }

  // --- everything else is a no-op / null stub ------------------------------

  @Override public LostFocusBehavior getLostFocusBehavior() { return LostFocusBehavior.Disabled; }
  @Override public void setLostFocusBehavior(final LostFocusBehavior lostFocusBehavior) { /* no-op */ }
  @Override public boolean isPauseOnLostFocus() { return false; }
  @Override public void setPauseOnLostFocus(final boolean pauseOnLostFocus) { /* no-op */ }
  @Override public void setSettings(final AppSettings settings) { /* no-op */ }
  @Override public void setTimer(final Timer timer) { /* no-op */ }
  @Override public Timer getTimer() { return null; }
  @Override public InputManager getInputManager() { return null; }
  @Override public RenderManager getRenderManager() { return null; }
  @Override public Renderer getRenderer() { return null; }
  @Override public Listener getListener() { return null; }
  @Override public JmeContext getContext() { return null; }
  @Override public void start() { /* no-op */ }
  @Override public void start(final boolean waitFor) { /* no-op */ }
  @Override public void setAppProfiler(final AppProfiler prof) { /* no-op */ }
  @Override public AppProfiler getAppProfiler() { return null; }
  @Override public void restart() { /* no-op */ }
  @Override public void stop() { /* no-op */ }
  @Override public void stop(final boolean waitFor) { /* no-op */ }
  @Override public ViewPort getGuiViewPort() { return null; }
  @Override public ViewPort getViewPort() { return null; }

  /** Fluent builder; null-or-real per axis lets a test pick the minimum surface it needs. */
  public static final class Builder {
    private Camera camera = new Camera(800, 600);
    private AssetManager assetManager = new DesktopAssetManager();
    private AudioRenderer audioRenderer;

    private Builder() { /* use SyntheticApplication.builder() */ }

    /** Replace the default 800x600 camera; pass {@code null} to omit. */
    public Builder camera(final Camera value) {
      this.camera = value;
      return this;
    }

    /** Replace the default {@link DesktopAssetManager}; pass {@code null} to omit. */
    public Builder assetManager(final AssetManager value) {
      this.assetManager = value;
      return this;
    }

    /** Default is {@code null} — exercises the AudioState "no audio renderer, disable" branch. */
    public Builder audioRenderer(final AudioRenderer value) {
      this.audioRenderer = value;
      return this;
    }

    public SyntheticApplication build() {
      return new SyntheticApplication(this);
    }
  }
}
