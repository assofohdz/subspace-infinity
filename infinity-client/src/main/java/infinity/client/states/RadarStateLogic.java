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

/** Pure-static helpers extracted from {@link RadarState}'s render path. */
final class RadarStateLogic {

    private RadarStateLogic() {
        // utility — pure static helpers, do not instantiate.
    }

    /** Triangulated interior fill for a closed convex polygon via fan from {@code verts[0]}; concave needs ear-clipping (not impl'd). */
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

    /** Closed line-loop outline for a polygon using {@code Mesh.Mode.Lines}; GL-default 1px width. */
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

    /** Self → self colour; no-freq → neutral; same team → friendly; otherwise enemy. */
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

    /** Ray-casting point-in-polygon in the X-Z plane; works for any closed convex/concave polygon. */
    static boolean pointInPolygon(final double x, final double z, final Vec3d[] verts) {
        if (verts == null || verts.length < 3) {
            return false;
        }
        boolean inside = false;
        final int n = verts.length;
        for (int i = 0, j = n - 1; i < n; j = i++) {
            final double xi = verts[i].x;
            final double zi = verts[i].z;
            final double xj = verts[j].x;
            final double zj = verts[j].z;
            final boolean rayCrosses = (zi > z) != (zj > z)
                    && x < (xj - xi) * (z - zi) / (zj - zi) + xi;
            if (rayCrosses) {
                inside = !inside;
            }
        }
        return inside;
    }
}
