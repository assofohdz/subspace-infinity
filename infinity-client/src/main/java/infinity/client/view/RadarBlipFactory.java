// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
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
 * Resolves a {@code RadarShapeInfo.shapeName} to a fresh top-down 2D blip
 * {@link Geometry} for the offscreen radar viewport. Mirrors the shape-name →
 * spatial pattern in {@link infinity.client.states.SISpatialFactory} but
 * specialised for radar: every geometry is an XZ-plane silhouette with an
 * unshaded material and ≤ 4 verts.
 *
 * <p>Each call returns a <b>new</b> {@code Geometry} with a <b>new</b>
 * {@code Material}, so per-blip colour mutations (frequency-based team colour)
 * don't disturb other blips.
 *
 * <p>Unknown shape names fall back to the default ship dot — adding a new ship
 * class doesn't require a registry entry. Ships use a dot rather than a
 * triangle because the radar stays north-up; a directional shape would
 * mislead the eye whenever the ship turns.
 */
public final class RadarBlipFactory {

    private final AssetManager assetManager;
    private final Map<String, Function<AssetManager, Mesh>> meshFactories = new HashMap<>();
    private final Function<AssetManager, Mesh> defaultMeshFactory;

    public RadarBlipFactory(final AssetManager assetManager, final RadarTheme theme) {
        this.assetManager = assetManager;
        // Meshes are baked at theme sizes; a theme swap requires recreating the factory.
        final float shipDotRadius = theme.shipDotRadius();
        final int dotSegments = theme.dotSegments();
        final float staticHalfSize = theme.staticBlipHalfSize();
        this.defaultMeshFactory = am -> dot(shipDotRadius, dotSegments);
        meshFactories.put("flag-blip", am -> square(staticHalfSize));
        meshFactories.put("prize-blip", am -> diamond(staticHalfSize));
    }

    public Geometry create(final String shapeName, final ColorRGBA initialColor) {
        final Function<AssetManager, Mesh> factory =
                meshFactories.getOrDefault(shapeName, defaultMeshFactory);
        final Mesh mesh = factory.apply(assetManager);
        final Geometry geom = new Geometry("Blip[" + shapeName + "]", mesh);
        final Material mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        mat.setColor("Color", initialColor.clone());
        // Mesh winding produces -Y normals; the down-looking radar cam would back-face-cull, so disable culling.
        mat.getAdditionalRenderState().setFaceCullMode(FaceCullMode.Off);
        geom.setMaterial(mat);
        return geom;
    }

    // Disc rather than triangle because the radar stays north-up — a directional shape would mislead as the ship turns.
    private static Mesh dot(final float radius, final int segments) {
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
