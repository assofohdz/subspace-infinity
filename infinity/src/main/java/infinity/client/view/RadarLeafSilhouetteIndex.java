// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.client.view;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.material.RenderState.FaceCullMode;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.VertexBuffer;
import com.jme3.util.BufferUtils;
import com.simsilica.mblock.CellArray;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

/**
 * Builds the flat top-down silhouette mesh for one leaf's worth of cell data —
 * the radar-side analogue of {@link BlockGeometryIndex}, but without the tile
 * texture, neighbor lighting, or per-face mesh.
 *
 * <p>For each (x, z) column inside the leaf, if any cell along Y is solid (raw
 * cell value != 0), one 1×1 quad is emitted in the XZ plane at that column.
 * Subspace maps are effectively 2D so OR-ing along Y is the right projection;
 * the mesh stays sparse because most maps are mostly empty space. Cells are
 * placed at their leaf-local coordinates — the calling state positions the
 * containing node at the leaf's world origin.
 *
 * <p>The resulting geometry uses an unshaded material coloured from
 * {@link RadarTheme#blockColor()}, with face culling disabled (the radar
 * camera looks straight down so a -Y normal would back-face-cull, same
 * caveat as {@code RadarBlipFactory}).
 *
 * @author Asser Fahrenholz
 */
public final class RadarLeafSilhouetteIndex {

    private final Material material;

    public RadarLeafSilhouetteIndex(final AssetManager assetManager, final RadarTheme theme) {
        // Single shared material — no per-leaf tinting, one colour by design.
        // Created on the construction thread so we don't ask the asset manager
        // from a worker thread later.
        this.material = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        this.material.setColor("Color", theme.blockColor());
        this.material.getAdditionalRenderState().setFaceCullMode(FaceCullMode.Off);
    }

    /**
     * Build the silhouette mesh for {@code cells} and attach it under {@code target}.
     * Returns {@code target} for chaining. Returns {@code target} unchanged if the
     * leaf has no solid cells (no geometry attached, callers can drop it).
     *
     * <p>Safe to call from a worker thread: only allocates buffers and a
     * {@link Geometry} that holds a reference to the (already-loaded) shared
     * material — does not touch the asset manager.
     */
    public Node generate(final Node target, final CellArray cells) {
        final int sizeX = cells.getSizeX();
        final int sizeY = cells.getSizeY();
        final int sizeZ = cells.getSizeZ();

        // First pass: count solid columns so we can allocate exact-size buffers.
        int columnCount = 0;
        for (int cx = 0; cx < sizeX; cx++) {
            for (int cz = 0; cz < sizeZ; cz++) {
                if (anySolidInColumn(cells, cx, cz, sizeY)) {
                    columnCount++;
                }
            }
        }
        if (columnCount == 0) {
            return target;
        }

        final FloatBuffer positions = BufferUtils.createFloatBuffer(columnCount * 4 * 3);
        final ShortBuffer indices = BufferUtils.createShortBuffer(columnCount * 6);

        int vertexBase = 0;
        for (int cx = 0; cx < sizeX; cx++) {
            for (int cz = 0; cz < sizeZ; cz++) {
                if (!anySolidInColumn(cells, cx, cz, sizeY)) {
                    continue;
                }
                // 1×1 quad in the XZ plane at leaf-local (cx, 0, cz)..(cx+1, 0, cz+1).
                positions.put(cx).put(0f).put(cz);
                positions.put(cx + 1f).put(0f).put(cz);
                positions.put(cx + 1f).put(0f).put(cz + 1f);
                positions.put(cx).put(0f).put(cz + 1f);

                indices.put((short) (vertexBase));
                indices.put((short) (vertexBase + 1));
                indices.put((short) (vertexBase + 2));
                indices.put((short) (vertexBase));
                indices.put((short) (vertexBase + 2));
                indices.put((short) (vertexBase + 3));
                vertexBase += 4;
            }
        }
        positions.flip();
        indices.flip();

        final Mesh mesh = new Mesh();
        mesh.setBuffer(VertexBuffer.Type.Position, 3, positions);
        mesh.setBuffer(VertexBuffer.Type.Index, 3, indices);
        mesh.updateBound();

        final Geometry geom = new Geometry("LeafSilhouette", mesh);
        geom.setMaterial(material);
        target.attachChild(geom);
        return target;
    }

    private static boolean anySolidInColumn(final CellArray cells, final int cx, final int cz,
            final int sizeY) {
        for (int cy = 0; cy < sizeY; cy++) {
            // Cell value 0 = air; any non-zero (regardless of the type/light/fluid bits
            // packed into the upper part of the int) is "something there".
            if (cells.getCell(cx, cy, cz) != 0) {
                return true;
            }
        }
        return false;
    }
}
