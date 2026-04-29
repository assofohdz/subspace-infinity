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
package infinity.client.view;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.material.RenderState.FaceCullMode;
import com.jme3.math.ColorRGBA;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.VertexBuffer;
import com.jme3.util.BufferUtils;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Resolves a {@code RadarShapeInfo.shapeName} to a fresh, flat 2D blip
 * {@link Geometry} for the off-screen radar viewport. Mirrors the
 * shape-name → spatial pattern in {@code SISpatialFactory}, but specialised
 * for radar: every produced geometry is a top-down silhouette in the XZ
 * plane, uses an unshaded material, and is intentionally cheap (≤ 4 verts).
 *
 * <p>Each call returns a <b>new</b> {@code Geometry} with a <b>new</b>
 * {@link Material}, so callers can mutate per-blip color (e.g. frequency-
 * based team colour) without disturbing other blips.
 *
 * <p>Unknown shape names fall back to the default ship dot, matching the
 * spirit of {@code SISpatialFactory.setDefaultFactory} — adding a new ship
 * class doesn't require a registry entry; only specialised non-ship blips
 * (flag, prize, etc.) need their own factory. Ships use a dot rather than a
 * directional shape because the radar stays north-up; a triangle would
 * mislead the eye as the player rotates.
 *
 * @author Asser Fahrenholz
 */
public final class RadarBlipFactory {

    private static final float SHIP_DOT_RADIUS = 5f;
    private static final int DOT_SEGMENTS = 16;
    private static final float STATIC_BLIP_HALF_SIZE = 10f;

    private final AssetManager assetManager;
    private final Map<String, Function<AssetManager, Mesh>> meshFactories = new HashMap<>();
    private final Function<AssetManager, Mesh> defaultMeshFactory;

    public RadarBlipFactory(final AssetManager assetManager) {
        this.assetManager = assetManager;
        this.defaultMeshFactory = am -> dot(SHIP_DOT_RADIUS);
        meshFactories.put("flag-blip", am -> square(STATIC_BLIP_HALF_SIZE));
        meshFactories.put("prize-blip", am -> diamond(STATIC_BLIP_HALF_SIZE));
    }

    /**
     * Build a blip geometry for the given shape name, coloured uniformly with
     * {@code initialColor}. The returned geometry has its own {@link Material},
     * so subsequent {@code geom.getMaterial().setColor("Color", ...)} calls
     * affect only that blip.
     */
    public Geometry create(final String shapeName, final ColorRGBA initialColor) {
        final Function<AssetManager, Mesh> factory =
                meshFactories.getOrDefault(shapeName, defaultMeshFactory);
        final Mesh mesh = factory.apply(assetManager);
        final Geometry geom = new Geometry("Blip[" + shapeName + "]", mesh);
        final Material mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        mat.setColor("Color", initialColor.clone());
        // Blips are flat XZ-plane silhouettes viewed from straight above. Their
        // mesh winding produces a -Y normal, which would back-face-cull against
        // the down-looking radar camera. Drawing both sides is cheaper than
        // re-winding the meshes and keeps the registry symmetric.
        mat.getAdditionalRenderState().setFaceCullMode(FaceCullMode.Off);
        geom.setMaterial(mat);
        return geom;
    }

    private static Mesh dot(final float radius) {
        // Filled disc in the XZ plane (Y=0), built as a triangle fan around a
        // centre vertex. Used for ship blips because the radar doesn't rotate
        // with the player heading — a directional shape (like a triangle) would
        // mislead the eye whenever the ship turns.
        final int segments = DOT_SEGMENTS;
        final int vertCount = segments + 1; // + 1 for the centre vertex
        final float[] positions = new float[vertCount * 3];
        // Centre at (0, 0, 0)
        for (int i = 0; i < segments; i++) {
            final double angle = i * 2.0 * Math.PI / segments;
            positions[(i + 1) * 3]     = (float) (Math.cos(angle) * radius);
            positions[(i + 1) * 3 + 1] = 0f;
            positions[(i + 1) * 3 + 2] = (float) (Math.sin(angle) * radius);
        }
        final short[] indices = new short[segments * 3];
        for (int i = 0; i < segments; i++) {
            indices[i * 3]     = (short) 0;
            indices[i * 3 + 1] = (short) (i + 1);
            indices[i * 3 + 2] = (short) (((i + 1) % segments) + 1);
        }
        final Mesh mesh = new Mesh();
        mesh.setBuffer(VertexBuffer.Type.Position, 3, BufferUtils.createFloatBuffer(positions));
        mesh.setBuffer(VertexBuffer.Type.Index, 3, BufferUtils.createShortBuffer(indices));
        mesh.updateBound();
        return mesh;
    }

    private static Mesh square(final float halfSize) {
        // Axis-aligned square in the XZ plane.
        final Mesh mesh = new Mesh();
        mesh.setBuffer(VertexBuffer.Type.Position, 3, BufferUtils.createFloatBuffer(
                -halfSize, 0f, -halfSize,
                +halfSize, 0f, -halfSize,
                +halfSize, 0f, +halfSize,
                -halfSize, 0f, +halfSize));
        mesh.setBuffer(VertexBuffer.Type.Index, 3, BufferUtils.createShortBuffer(
                (short) 0, (short) 1, (short) 2,
                (short) 0, (short) 2, (short) 3));
        mesh.updateBound();
        return mesh;
    }

    private static Mesh diamond(final float halfSize) {
        // 45° rotated square: vertices on the axes.
        final Mesh mesh = new Mesh();
        mesh.setBuffer(VertexBuffer.Type.Position, 3, BufferUtils.createFloatBuffer(
                0f,        0f, -halfSize,
                +halfSize, 0f, 0f,
                0f,        0f, +halfSize,
                -halfSize, 0f, 0f));
        mesh.setBuffer(VertexBuffer.Type.Index, 3, BufferUtils.createShortBuffer(
                (short) 0, (short) 1, (short) 2,
                (short) 0, (short) 2, (short) 3));
        mesh.updateBound();
        return mesh;
    }
}
