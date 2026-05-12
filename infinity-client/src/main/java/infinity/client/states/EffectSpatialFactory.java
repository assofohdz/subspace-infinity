// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.client.states;

import com.jme3.asset.AssetManager;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Spatial;
import com.jme3.scene.VertexBuffer;
import com.jme3.scene.shape.Quad;
import com.jme3.system.Timer;
import com.jme3.util.BufferUtils;
import infinity.es.ShapeNames;
import infinity.sim.CoreViewConstants;
import infinity.sim.util.InfinityRunTimeException;
import java.util.Map;
import java.util.function.DoubleFunction;

/** Visual-only effect spatials (overlays, wormhole/warp/repel/burst rings, explosion tiers) — delegated from {@link SISpatialFactory}. */
public class EffectSpatialFactory {

  // Use to flip between using the lights and using unshaded textures
  private static final boolean UNSHADED = false;
  private static final String STARTTIME = "StartTime";

  private final AssetManager assets;
  private final Timer timer;

  private final Map<String, DoubleFunction<Spatial>> effectShapeFactories = Map.ofEntries(
      Map.entry(ShapeNames.BURST, scale -> createBurst()),
      Map.entry(ShapeNames.EXPLODE_0, scale -> createExplosion0()),
      Map.entry(ShapeNames.EXPLODE_1, scale -> createExplosion1()),
      Map.entry(ShapeNames.EXPLODE_2, scale -> createExplosion2()),
      Map.entry(ShapeNames.OVER1, scale -> createOver1()),
      Map.entry(ShapeNames.OVER2, scale -> createOver2()),
      Map.entry(ShapeNames.OVER5, scale -> createOver5()),
      Map.entry(ShapeNames.WORMHOLE, scale -> createWormhole()),
      Map.entry(ShapeNames.WARP, scale -> createWarp()),
      Map.entry(ShapeNames.REPEL, scale -> createRepel()));

  EffectSpatialFactory(final AssetManager assets, final Timer timer) {
    this.assets = assets;
    this.timer = timer;
  }

  public boolean handles(final String shapeName) {
    return effectShapeFactories.containsKey(shapeName);
  }

  /** Throws {@link InfinityRunTimeException} when no factory is registered for {@code shapeName}. */
  public Spatial createEffect(final String shapeName, final double scale) {
    final DoubleFunction<Spatial> factory = effectShapeFactories.get(shapeName);
    if (factory == null) {
      throw new InfinityRunTimeException("Unknown shape name: " + shapeName);
    }
    return factory.apply(scale);
  }

  private Spatial createExplosion0() {
    return createTimedQuad(
        CoreViewConstants.EXPLOSION0SIZE,
        "Bomb",
        "Materials/Explode0MaterialUnshaded.j3m",
        "Materials/Explode0MaterialLight.j3m");
  }

  private Spatial createExplosion1() {
    return createTimedQuad(
        CoreViewConstants.EXPLOSION1SIZE,
        "Bomb",
        "Materials/Explode1MaterialUnshaded.j3m",
        "Materials/Explode1MaterialLight.j3m");
  }

  private Spatial createExplosion2() {
    return createTimedQuad(
        CoreViewConstants.EXPLOSION2SIZE,
        "Bomb",
        "Materials/Explode2MaterialUnshaded.j3m",
        "Materials/Explode2MaterialLight.j3m");
  }

  private Spatial createOver1() {
    return createTimedQuad(
        CoreViewConstants.OVER1SIZE,
        "Over1",
        "Materials/Over1MaterialUnshaded.j3m",
        "Materials/Over1MaterialLight.j3m");
  }

  private Spatial createOver2() {
    return createTimedQuad(
        CoreViewConstants.OVER2SIZE,
        "Over2",
        "Materials/Over2MaterialUnshaded.j3m",
        "Materials/Over2MaterialLight.j3m");
  }

  private Spatial createOver5() {
    // Geometry is named "Wormhole" because the over5 layer rendered the
    // wormhole halo in the original SISpatialFactory; preserved verbatim
    // during the split (see git blame).
    return createTimedQuad(
        CoreViewConstants.OVER5SIZE,
        "Wormhole",
        "Materials/Over5MaterialUnshaded.j3m",
        "Materials/Over5MaterialLight.j3m");
  }

  private Spatial createWormhole() {
    return createTimedQuad(
        CoreViewConstants.WORMHOLESIZE,
        "Wormhole",
        "Materials/WormholeMaterialUnshaded.j3m",
        "Materials/WormholeMaterialLight.j3m");
  }

  private Spatial createWarp() {
    return createTimedQuad(
        CoreViewConstants.WARPSIZE,
        "Warp",
        "Materials/WarpMaterialUnshaded.j3m",
        "Materials/WarpMaterialLight.j3m");
  }

  private Spatial createRepel() {
    return createTimedQuad(
        CoreViewConstants.REPELSIZE,
        "Repel",
        "Materials/RepelMaterialUnshaded.j3m",
        "Materials/RepelMaterialLight.j3m");
  }

  private Spatial createBurst() {
    return createTimedQuad(
        CoreViewConstants.BURSTSIZE,
        "Burst",
        "Materials/BurstMaterialUnshaded.j3m",
        "Materials/BurstMaterialLight.j3m");
  }

  // Stamps current time into the {@code StartTime} material param — drives shader-side animation.
  private Spatial createTimedQuad(
      final float size,
      final String name,
      final String unshadedMaterial,
      final String litMaterial) {
    final Quad quad = new Quad(size, size);
    final float halfSize = size * 0.5f;
    quad.setBuffer(VertexBuffer.Type.Position, 3, QuadMeshes.verticesQuad(halfSize));
    quad.setBuffer(VertexBuffer.Type.Normal, 3, BufferUtils.createFloatBuffer(QuadMeshes.normalsQuad()));
    quad.updateBound();
    final Geometry geom = new Geometry(name, quad);
    if (UNSHADED) {
      geom.setMaterial(assets.loadMaterial(unshadedMaterial));
    } else {
      geom.setMaterial(assets.loadMaterial(litMaterial));
    }
    geom.getMaterial().setFloat(STARTTIME, timer.getTimeInSeconds());
    geom.setQueueBucket(RenderQueue.Bucket.Transparent);
    return geom;
  }
}
