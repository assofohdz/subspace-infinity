// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.client.states;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.material.RenderState.FaceCullMode;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.VertexBuffer;
import com.jme3.util.BufferUtils;
import com.simsilica.es.EntityId;
import com.simsilica.mathd.Vec3d;
import infinity.client.view.RadarTheme;

/**
 * Pure-static helpers extracted from {@link RadarState}'s render path.
 *
 * <p>Each method takes every piece of state it needs as an argument so the
 * helper has no access to {@code RadarState} instance fields or
 * {@code BaseAppState.getApplication()}. The split shaves cyclomatic
 * complexity off the {@code RadarState} class without changing any
 * behaviour — the production methods on {@code RadarState} are now thin
 * delegates that pull the required state off {@code this} and forward.
 */
final class RadarStateLogic {

    private RadarStateLogic() {
        // utility — pure static helpers, do not instantiate.
    }

    /**
     * Builds a triangulated interior fill mesh for a closed convex polygon
     * via fan triangulation from {@code verts[0]}. Convex-only — adequate for
     * arena bounds rectangles and any future convex shape; concave polygons
     * would need ear-clipping (not in scope today).
     *
     * @param verts polygon vertices in ring order (≥3 entries)
     * @param tintColor fill colour pulled from the active {@link RadarTheme}
     * @param am asset manager used to load the {@code Unshaded.j3md} material
     * @param fillY Y-coordinate the fill plane sits at (radar uses −2)
     */
    static Geometry buildFootprintFill(
            final Vec3d[] verts,
            final ColorRGBA tintColor,
            final AssetManager am,
            final float fillY) {
        final int n = verts.length;
        final Vector3f[] positions = new Vector3f[n];
        for (int i = 0; i < n; i++) {
            positions[i] = new Vector3f(
                (float) verts[i].x,
                fillY,
                (float) verts[i].z);
        }
        final int[] indices = new int[(n - 2) * 3];
        for (int i = 0; i < n - 2; i++) {
            indices[i * 3] = 0;
            indices[i * 3 + 1] = i + 1;
            indices[i * 3 + 2] = i + 2;
        }
        final Mesh mesh = new Mesh();
        mesh.setBuffer(VertexBuffer.Type.Position, 3, BufferUtils.createFloatBuffer(positions));
        mesh.setBuffer(VertexBuffer.Type.Index, 3, BufferUtils.createIntBuffer(indices));
        mesh.updateBound();

        final Geometry geom = new Geometry("ArenaFootprintFill", mesh);
        final Material mat = new Material(am, "Common/MatDefs/Misc/Unshaded.j3md");
        mat.setColor("Color", tintColor);
        // Top-down ortho camera could see either face depending on winding —
        // disable culling so the fill always renders regardless of vertex order.
        mat.getAdditionalRenderState().setFaceCullMode(FaceCullMode.Off);
        geom.setMaterial(mat);
        return geom;
    }

    /**
     * Builds a closed line-loop outline for a polygon using {@code Mesh.Mode.Lines}
     * with index pairs forming each edge — last edge connects {@code verts[n-1]}
     * back to {@code verts[0]}. Width is GL-default (1 pixel); upgrading to a
     * thicker outline would replace this with a quad strip.
     *
     * @param verts polygon vertices in ring order (≥3 entries)
     * @param outlineColor outline colour pulled from the active {@link RadarTheme}
     * @param am asset manager used to load the {@code Unshaded.j3md} material
     * @param outlineY Y-coordinate the outline plane sits at (radar uses −1)
     */
    static Geometry buildFootprintOutline(
            final Vec3d[] verts,
            final ColorRGBA outlineColor,
            final AssetManager am,
            final float outlineY) {
        final int n = verts.length;
        final Vector3f[] positions = new Vector3f[n];
        for (int i = 0; i < n; i++) {
            positions[i] = new Vector3f(
                (float) verts[i].x,
                outlineY,
                (float) verts[i].z);
        }
        final int[] indices = new int[n * 2];
        for (int i = 0; i < n; i++) {
            indices[i * 2] = i;
            indices[i * 2 + 1] = (i + 1) % n;
        }
        final Mesh mesh = new Mesh();
        mesh.setMode(Mesh.Mode.Lines);
        mesh.setBuffer(VertexBuffer.Type.Position, 3, BufferUtils.createFloatBuffer(positions));
        mesh.setBuffer(VertexBuffer.Type.Index, 2, BufferUtils.createIntBuffer(indices));
        mesh.updateBound();

        final Geometry geom = new Geometry("ArenaFootprintOutline", mesh);
        final Material mat = new Material(am, "Common/MatDefs/Misc/Unshaded.j3md");
        mat.setColor("Color", outlineColor);
        geom.setMaterial(mat);
        return geom;
    }

    /**
     * Resolve the radar-blip colour for an entity. Self → self colour;
     * no-frequency → neutral; same team as avatar → friendly; otherwise
     * enemy. Avatar id and avatar freq are pulled off {@link RadarState}
     * instance state at the call site.
     *
     * @param id the entity being coloured
     * @param entityFreq the entity's frequency, or {@code null} for "no team"
     * @param avatarEntityId the local avatar's entity id, or {@code null}
     * @param currentAvatarFreq the local avatar's freq, or {@code null}
     * @param theme the active radar theme (palette source)
     */
    static ColorRGBA colorFor(
            final EntityId id,
            final Integer entityFreq,
            final EntityId avatarEntityId,
            final Integer currentAvatarFreq,
            final RadarTheme theme) {
        if (id.equals(avatarEntityId)) {
            return theme.selfColor();
        }
        if (entityFreq == null) {
            return theme.neutralColor();
        }
        if (currentAvatarFreq != null && entityFreq.intValue() == currentAvatarFreq.intValue()) {
            return theme.friendlyColor();
        }
        return theme.enemyColor();
    }
}
