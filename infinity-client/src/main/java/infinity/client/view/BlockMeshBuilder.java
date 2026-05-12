// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.client.view;

import com.jme3.scene.Mesh;
import com.jme3.scene.VertexBuffer;
import com.jme3.scene.mesh.IndexBuffer;
import com.jme3.util.BufferUtils;
import com.simsilica.mblock.BlockType;
import com.simsilica.mblock.BlockTypeIndex;
import com.simsilica.mblock.CellArray;
import com.simsilica.mblock.CellData;
import com.simsilica.mblock.Direction;
import com.simsilica.mblock.FluidType;
import com.simsilica.mblock.FluidTypeIndex;
import com.simsilica.mblock.FluidUtils;
import com.simsilica.mblock.MaskUtils;
import com.simsilica.mblock.geom.DefaultPartBuffer;
import com.simsilica.mblock.geom.GeomPart;
import com.simsilica.mblock.geom.GeomReq;
import com.simsilica.mblock.geom.MaterialType;
import com.simsilica.mblock.geom.ScaledBuffer;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;

/** Pure-function block-mesh-building helpers extracted from {@link InfinityGeometryFactory}. */
final class BlockMeshBuilder {

    private BlockMeshBuilder() {
        // utility class — instantiation prevented
    }

    // Fields nullable when the originating {@link GeomReq} set didn't request them.
    static final class MeshBuffers {
        final ScaledBuffer pos;
        final ScaledBuffer texes;
        final IndexBuffer indexes;
        final FloatBuffer colors;
        final ScaledBuffer nb;
        final ScaledBuffer tb;
        final ByteBuffer dirB;

        MeshBuffers(
                final ScaledBuffer pos, final ScaledBuffer texes,
                final IndexBuffer indexes, final FloatBuffer colors,
                final ScaledBuffer nb, final ScaledBuffer tb, final ByteBuffer dirB) {
            this.pos = pos;
            this.texes = texes;
            this.indexes = indexes;
            this.colors = colors;
            this.nb = nb;
            this.tb = tb;
            this.dirB = dirB;
        }
    }

    /** Push every cell's visible geometry into {@code buffer}; skip empty/unknown types. */
    static void populateBlockBuffer(final DefaultPartBuffer buffer, final CellArray cells) {
        final int xSize = cells.getSizeX();
        final int ySize = cells.getSizeY();
        final int zSize = cells.getSizeZ();
        for (int x = 0; x < xSize; x++) {
            for (int y = 0; y < ySize; y++) {
                for (int z = 0; z < zSize; z++) {
                    int val = cells.getCell(x, y, z);
                    int type = MaskUtils.getType(val);
                    if (type == 0) {
                        continue;
                    }
                    BlockType blockType = BlockTypeIndex.get(type);
                    if (blockType == null) {
                        continue;
                    }
                    int sideMask = MaskUtils.getSideMask(val);
                    blockType.getFactory().addGeometryToBuffer(buffer, x, y, z, x, y, z,
                            sideMask, cells, blockType);
                }
            }
        }
    }

    /** Fluid counterpart to {@link #populateBlockBuffer}; forwards solid {@code cells} so fluid faces can cull against walls. */
    static void populateFluidBuffer(
            final DefaultPartBuffer buffer, final CellArray fluid, final CellArray cells) {
        final int xSize = fluid.getSizeX();
        final int ySize = fluid.getSizeY();
        final int zSize = fluid.getSizeZ();
        for (int x = 0; x < xSize; x++) {
            for (int y = 0; y < ySize; y++) {
                for (int z = 0; z < zSize; z++) {
                    int val = fluid.getCell(x, y, z);
                    int type = FluidUtils.getType(val);
                    if (type == 0) {
                        continue;
                    }
                    FluidType fluidType = FluidTypeIndex.get(type);
                    if (fluidType == null) {
                        continue;
                    }
                    int level = FluidUtils.getLevel(val);
                    int sideMask = FluidUtils.getSideMask(val);
                    fluidType.getFactory().addGeometryToBuffer(buffer, x, y, z, x, y, z,
                            sideMask, level,
                            cells, fluid, fluidType);
                }
            }
        }
    }

    // Allocates only the buffers required by {@code mt}'s {@link GeomReq} set; hi-res vs lo-res per LoRes* flag.
    static MeshBuffers buildMeshBuffers(
            final MaterialType mt, final int vertCount, final int triCount) {
        final ScaledBuffer pos = mt.requires(GeomReq.LoResPositions)
                ? ScaledBuffer.createScaledBuffer(vertCount * 3, 0, 32)
                : ScaledBuffer.createUnscaledBuffer(vertCount * 3);
        final ScaledBuffer texes = mt.requires(GeomReq.LoResTexCoords)
                ? ScaledBuffer.createScaledBuffer(vertCount * 2, 0, 1)
                : ScaledBuffer.createUnscaledBuffer(vertCount * 2);
        final IndexBuffer indexes = IndexBuffer.createIndexBuffer(vertCount, triCount * 3);
        final FloatBuffer colors = BufferUtils.createFloatBuffer(vertCount * 4);
        ScaledBuffer nb = null;
        ScaledBuffer tb = null;
        ByteBuffer dirB = null;
        if (mt.requires(GeomReq.IndexedNormals)) {
            dirB = BufferUtils.createByteBuffer(vertCount);
        } else {
            if (mt.requires(GeomReq.Normals)) {
                nb = mt.requires(GeomReq.LoResNormals)
                        ? ScaledBuffer.createScaledBuffer(vertCount * 3, -1, 1)
                        : ScaledBuffer.createUnscaledBuffer(vertCount * 3);
            }
            if (mt.requires(GeomReq.Tangents)) {
                tb = mt.requires(GeomReq.LoResTangents)
                        ? ScaledBuffer.createScaledBuffer(vertCount * 3, -1, 1)
                        : ScaledBuffer.createUnscaledBuffer(vertCount * 3);
            }
        }
        return new MeshBuffers(pos, texes, indexes, colors, nb, tb, dirB);
    }

    /** Returns the next baseIndex; throws if {@code dirB} is requested but the entry has no valid direction. */
    static int emitPart(
            final MeshBuffers buffers,
            final MaterialType mt,
            final DefaultPartBuffer.PartEntry entry,
            final InfinityGeometryFactory.LightGradient gradient,
            final CellData lightData,
            final int baseIndex) {
        final GeomPart part = entry.part;
        final byte dir = (byte) part.getDirectionIndex();
        if (buffers.dirB != null && dir < 0) {
            throw new IllegalStateException("Entry for material:" + mt + " has invalid dir:" + dir);
        }
        final Direction dirEnum = dir >= 0 ? Direction.values()[dir] : null;
        final int size = part.getVertexCount();
        emitVertices(buffers, entry.i, entry.j, entry.k, dir, dirEnum, part.getCoords(), size, gradient, lightData);
        copyAuxBuffers(buffers, part);
        for (final short s : part.getIndexes()) {
            buffers.indexes.put(baseIndex + s);
        }
        return baseIndex + size;
    }

    static void emitVertices(
            final MeshBuffers buffers,
            final int i, final int j, final int k,
            final byte dir, final Direction dirEnum,
            final float[] verts, final int size,
            final InfinityGeometryFactory.LightGradient gradient, final CellData lightData) {
        int vIndex = 0;
        for (int v = 0; v < size; v++) {
            final float x = verts[vIndex++];
            final float y = verts[vIndex++];
            final float z = verts[vIndex++];
            buffers.pos.put(i + x);
            buffers.pos.put(j + y);
            buffers.pos.put(k + z);
            if (buffers.dirB != null) {
                buffers.dirB.put(dir);
            }
            gradient.appendLight(lightData, i, j, k, x, y, z, dirEnum, buffers.colors);
        }
    }

    // Each aux array copied only if the buffer is non-null AND the part has data.
    static void copyAuxBuffers(final MeshBuffers buffers, final GeomPart part) {
        final float[] norms = part.getNormals();
        if (buffers.nb != null && norms != null) {
            buffers.nb.put(norms);
        }
        final float[] tangents = part.getTangents();
        if (buffers.tb != null && tangents != null) {
            buffers.tb.put(tangents);
        }
        for (final float t : part.getTexCoords()) {
            buffers.texes.put(t);
        }
    }

    static void attachIndexBuffer(final Mesh mesh, final IndexBuffer indexes) {
        switch (indexes.getFormat()) {
            case UnsignedInt:
                mesh.setBuffer(VertexBuffer.Type.Index, 3, (IntBuffer) indexes.getBuffer());
                break;
            case UnsignedShort:
                mesh.setBuffer(VertexBuffer.Type.Index, 3, (ShortBuffer) indexes.getBuffer());
                break;
            case UnsignedByte:
                mesh.setBuffer(VertexBuffer.Type.Index, 3, (ByteBuffer) indexes.getBuffer());
                break;
            default:
                throw new IllegalStateException("Unexpected: " + indexes.getFormat());
        }
    }

    /** Per-axis cell-offset for outward-facing border vertices: East/West affect X. */
    static int xOffset(final Direction dir, final float x) {
        if (dir == Direction.East && x == 1) {
            return 1;
        }
        if (dir == Direction.West && x == 0) {
            return -1;
        }
        return 0;
    }

    /** Per-axis cell-offset for outward-facing border vertices: Up/Down affect Y. */
    static int yOffset(final Direction dir, final float y) {
        if (dir == Direction.Up && y == 1) {
            return 1;
        }
        if (dir == Direction.Down && y == 0) {
            return -1;
        }
        return 0;
    }

    /** Per-axis cell-offset for outward-facing border vertices: South/North affect Z. */
    static int zOffset(final Direction dir, final float z) {
        if (dir == Direction.South && z == 1) {
            return 1;
        }
        if (dir == Direction.North && z == 0) {
            return -1;
        }
        return 0;
    }
}
