// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai;

import com.simsilica.es.Entity;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.mblock.phys.MBlockShape;
import com.simsilica.mphys.PhysicsSpace;
import com.simsilica.mphys.RigidBody;
import infinity.ai.field.ArenaNav;
import infinity.ai.field.ArenaSpatialFields;
import infinity.ai.field.BlendedFlow;
import infinity.ai.field.DistanceField;
import infinity.ai.field.FieldGradient;
import infinity.ai.field.TileScored;
import infinity.ai.objective.ArenaObjective;
import infinity.ai.objective.GoalTile;
import infinity.ai.tactical.ServerBotAiArenaContext;
import infinity.config.ZoneBotAiConfig;
import infinity.es.Dead;
import infinity.es.FlowFieldDebug;
import infinity.es.Frequency;
import infinity.es.arena.ArenaId;
import infinity.es.ship.PlayerShip;
import infinity.math.Vec2d;
import infinity.modules.ArenaModuleSystem;
import infinity.systems.MapSystem;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import javax.annotation.Nullable;

/**
 * Dev-only: samples the blended bot-steering flow ({@link BlendedFlow}) in a square patch around
 * each player ship and writes it to {@link FlowFieldDebug} for the client overlay
 * (ADR-0011/0012). Gated by {@link ZoneBotAiConfig#flowFieldDebug()} so production pays nothing;
 * driven on the density cadence by {@code BotBrainSystem} (which owns the per-arena
 * {@link ArenaSpatialFields} this reads). Canonical writer of {@link FlowFieldDebug}; extracted
 * from {@code BotBrainSystem} to keep the brain free of the player-debug plumbing.
 */
final class FlowFieldDebugSampler {

  private static final long UNSAMPLED = Long.MIN_VALUE;

  private final EntityData ed;
  private final PhysicsSpace<EntityId, MBlockShape> space;
  private final MapSystem mapSystem;
  private final ArenaModuleSystem arenaModuleSystem;
  private final ArenaSpatialFields spatialFields;
  private final Supplier<ZoneBotAiConfig> zoneCfg;

  private EntitySet players;
  private long lastSampleNanos = UNSAMPLED;

  FlowFieldDebugSampler(
      final EntityData ed,
      final PhysicsSpace<EntityId, MBlockShape> space,
      final MapSystem mapSystem,
      final ArenaModuleSystem arenaModuleSystem,
      final ArenaSpatialFields spatialFields,
      final Supplier<ZoneBotAiConfig> zoneCfg) {
    this.ed = ed;
    this.space = space;
    this.mapSystem = mapSystem;
    this.arenaModuleSystem = arenaModuleSystem;
    this.spatialFields = spatialFields;
    this.zoneCfg = zoneCfg;
  }

  void start() {
    this.players = this.ed.getEntities(PlayerShip.class, ArenaId.class, Frequency.class);
  }

  void stop() {
    if (this.players != null) {
      this.players.release();
      this.players = null;
    }
    this.lastSampleNanos = UNSAMPLED;
  }

  /** Sample + write every player's flow patch on the density cadence; no-op when the gate is off. */
  void refresh(final long nowNanos) {
    final ZoneBotAiConfig cfg = this.zoneCfg.get();
    if (!cfg.flowFieldDebug() || this.players == null) {
      return;
    }
    final long cadenceNanos = cfg.densityCadenceMillis() * 1_000_000L;
    if (this.lastSampleNanos != UNSAMPLED && nowNanos - this.lastSampleNanos < cadenceNanos) {
      return;
    }
    this.lastSampleNanos = nowNanos;
    this.players.applyChanges();
    final Map<String, ServerBotAiArenaContext> ctxByArena = new HashMap<>();
    for (final Entity player : this.players) {
      sampleForPlayer(player, cfg, ctxByArena);
    }
  }

  /** Sample + write one player's {@link FlowFieldDebug} patch (skips dead / not-yet-spawned ships). */
  private void sampleForPlayer(
      final Entity player,
      final ZoneBotAiConfig cfg,
      final Map<String, ServerBotAiArenaContext> ctxByArena) {
    final EntityId id = player.getId();
    if (this.ed.getComponent(id, Dead.class) != null) {
      return;
    }
    final RigidBody<EntityId, MBlockShape> body = this.space.getBinIndex().getRigidBody(id);
    if (body == null) {
      return; // body not attached yet
    }
    final ArenaId arenaId = player.get(ArenaId.class);
    final ServerBotAiArenaContext ctx =
        ctxByArena.computeIfAbsent(arenaId.getArena(), a -> buildContext(arenaId));
    if (ctx == null || ctx.navigation() == null) {
      return;
    }
    final int radius = cfg.flowFieldDebugRadius();
    final int cols = 2 * radius + 1;
    final int playerCellX = (int) Math.floor(body.position.x);
    final int playerCellZ = (int) Math.floor(body.position.z);
    final byte[] dirs =
        samplePatch(
            ctx,
            cfg,
            player.get(Frequency.class).getFrequency(),
            playerCellX - ctx.originCellX(),
            playerCellZ - ctx.originCellZ(),
            radius,
            cols);
    this.ed.setComponent(
        id, new FlowFieldDebug(playerCellX - radius, playerCellZ - radius, cols, cols, dirs));
  }

  /** Quantized blended-flow directions over the {@code cols×cols} patch (NO_FLOW where there is none). */
  private static byte[] samplePatch(
      final ServerBotAiArenaContext ctx,
      final ZoneBotAiConfig cfg,
      final int freq,
      final int relX,
      final int relY,
      final int radius,
      final int cols) {
    final byte[] dirs = new byte[cols * cols];
    Arrays.fill(dirs, FlowFieldDebug.NO_FLOW);
    final int[] goal = nearestPinnedGoal(ctx, relX, relY);
    if (goal == null) {
      return dirs; // arena has no pinned goals
    }
    final DistanceField field = ctx.navigation().fieldFor(goal[0], goal[1]);
    if (field.width() == 0) {
      return dirs; // still building — show nothing this cadence
    }
    final FieldGradient grad = new FieldGradient(field);
    for (int row = 0; row < cols; row++) {
      for (int col = 0; col < cols; col++) {
        final int cx = relX - radius + col;
        final int cy = relY - radius + row;
        final Vec2d navDir = grad.directionAt(cx, cy);
        if (navDir.lengthSq() < 1e-9) {
          continue; // wall / unreachable / at-goal → NO_FLOW
        }
        final Vec2d blended =
            BlendedFlow.blendNav(
                navDir, ctx, freq, cx, cy, cfg.navThreatWeight(), cfg.navOpportunityWeight());
        final Vec2d dir = blended.lengthSq() < 1e-9 ? navDir : blended.normalize();
        dirs[row * cols + col] = FlowFieldDebug.encodeAngle(Math.atan2(dir.y, dir.x));
      }
    }
    return dirs;
  }

  /** Nearest pinned static goal (chokepoint top-N + objective tiles) to {@code (relX,relY)}, arena-relative; null if none. */
  private static int[] nearestPinnedGoal(
      final ServerBotAiArenaContext ctx, final int relX, final int relY) {
    int[] best = null;
    double bestDsq = Double.POSITIVE_INFINITY;
    for (final TileScored c : ctx.chokepoints()) {
      final double dsq = dist2(c.x(), c.y(), relX, relY);
      if (dsq < bestDsq) {
        bestDsq = dsq;
        best = new int[] {c.x(), c.y()};
      }
    }
    for (final GoalTile g : ctx.objective().staticGoalTiles()) {
      final int gx = g.worldCellX() - ctx.originCellX();
      final int gy = g.worldCellZ() - ctx.originCellZ();
      final double dsq = dist2(gx, gy, relX, relY);
      if (dsq < bestDsq) {
        bestDsq = dsq;
        best = new int[] {gx, gy};
      }
    }
    return best;
  }

  private static double dist2(final int ax, final int ay, final int bx, final int by) {
    final double dx = (double) ax - bx;
    final double dy = (double) ay - by;
    return dx * dx + dy * dy;
  }

  /**
   * Per-arena field bundle for the sampler; {@code null} until the map's passability loads.
   * norms/synergy are left null — the flow sampler reads only the spatial fields + objective goals.
   */
  @Nullable
  private ServerBotAiArenaContext buildContext(final ArenaId arenaId) {
    final boolean[][] passable = this.mapSystem.passability(arenaId.getArena());
    if (passable == null) {
      return null;
    }
    final ArenaObjective objective = this.arenaModuleSystem.objectiveFor(arenaId);
    final ArenaNav nav =
        this.spatialFields.forArena(arenaId.getArena(), passable, objective.staticGoalTiles());
    return new ServerBotAiArenaContext(
        null,
        null,
        nav.fields(),
        nav.originX(),
        nav.originZ(),
        nav.density(),
        nav.threat(),
        nav.opportunity(),
        nav.combat(),
        nav.chokepointPool(),
        this.zoneCfg.get().chokepointDensityWeight(),
        objective);
  }
}
