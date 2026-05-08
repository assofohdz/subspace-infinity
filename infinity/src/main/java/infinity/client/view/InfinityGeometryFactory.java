/*
 * $Id$
 *
 * Copyright (c) 2020, Simsilica, LLC
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its
 *    contributors may be used to endorse or promote products derived
 *    from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
 * FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package infinity.client.view;

import java.nio.*;
import java.util.Map;

import com.simsilica.mblock.geom.*;
import org.slf4j.*;

import com.jme3.material.Material;
import com.jme3.material.RenderState.BlendMode;
import com.jme3.renderer.queue.RenderQueue.Bucket;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.VertexBuffer;
import com.jme3.scene.mesh.IndexBuffer;
import com.jme3.util.BufferUtils;

import com.simsilica.mblock.*;


/**
 *  Creates JME geometry for a block array using the configured
 *  material registry and global block type index.
 *
 *  @author    Paul Speed
 */
public class InfinityGeometryFactory {

    static Logger log = LoggerFactory.getLogger(InfinityGeometryFactory.class);

    private Map<String, Material> materials;
    private boolean allowCollisions;

    public InfinityGeometryFactory( Map<String, Material> materials ) {
        this(true, materials);
    }

    public InfinityGeometryFactory( boolean allowCollisions, Map<String, Material> materials ) {
        this.allowCollisions = allowCollisions;
        this.materials = materials;
    }

    /**
     *  Generates Geometry objects for the specified cells CellArray and lightData
     *  cellArray.  The Geometry objects are added to the 'target' Node.  The
     *  target node's existing children are cleared during this process.
     *  Each material represented in the cells data is a separate Geometry child
     *  in the final Node child list.
     */
    public Node generateBlocks( Node target, CellArray cells, CellData lightData, boolean smoothLighting ) {
        // For now we'll still choose lighting implementation internally
        log.info("Generating blocks");
        long start = System.nanoTime();
        Node result = target;
        result.detachAllChildren();

        // Collect the visible GeomParts by type
        DefaultPartBuffer buffer = new DefaultPartBuffer();
        populateBlockBuffer(buffer, cells);

        // Resolve a light gradient implementation based on the
        // smooth lighting flag.  Ultimately, if we ever have more than
        // two implementations or can think of reasons why this should be
        // customized then we should break it out as a parameter.  Might also
        // consider supporting pregenerated part buffers though I have no
        // strong reason why today.  2020-11-22
        LightGradient gradient =
                smoothLighting ? calculateLightGradient(cells, lightData) : new NoLightGradient(lightData);
        renderBuffer(result, buffer, gradient, lightData);

        long end = System.nanoTime();
        if( log.isTraceEnabled() ) {
            log.trace("Generated in:" + ((end - start)/1000000.0) + " ms");
        }
        return result;
    }

    /**
     * Triple-loops over every cell in {@code cells} and asks each cell's
     * {@link BlockType} factory to push its visible geometry parts into
     * {@code buffer}. Cells with type 0 (empty) and unknown types are skipped.
     */
    private static void populateBlockBuffer(final DefaultPartBuffer buffer, final CellArray cells) {
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



    /**
     *  Generates Geometry objects for the specified fluid, cells, and lightData cell arrays.
     *  The Geometry objects are added to the 'target' Node.  The
     *  target node's existing children are cleared during this process.
     *  Each material represented in the cells data is a separate Geometry child
     *  in the final Node child list.
     */
    public Node generateFluid( Node target, CellArray fluid, CellArray cells, CellData lightData, boolean smoothLighting ) {
        // For now we'll still choose lighting implementation internally
        if( fluid == null ) {
            return target;
        }

        long start = System.nanoTime();
        Node result = target;
        result.detachAllChildren();

        // Collect the visible GeomParts by type
        DefaultPartBuffer buffer = new DefaultPartBuffer();
        populateFluidBuffer(buffer, fluid, cells);

        // Resolve a light gradient implementation based on the
        // smooth lighting flag.  Ultimately, if we ever have more than
        // two implementations or can think of reasons why this should be
        // customized then we should break it out as a parameter.  Might also
        // consider supporting pregenerated part buffers though I have no
        // strong reason why today.  2020-11-22
        LightGradient gradient =
                smoothLighting ? calculateLightGradient(fluid, lightData) : new NoLightGradient(lightData);
        renderBuffer(result, buffer, gradient, lightData);

        long end = System.nanoTime();
        if( log.isTraceEnabled() ) {
            log.trace("Generated in:" + ((end - start)/1000000.0) + " ms");
        }
        return result;
    }

    /**
     * Triple-loops over every cell in {@code fluid}, looks up the matching
     * {@link FluidType}, and pushes its geometry parts into {@code buffer}. The
     * neighbouring solid {@code cells} array is forwarded to the factory so
     * fluid faces can be culled against adjacent walls. Empty/unknown types skipped.
     */
    private static void populateFluidBuffer(
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

    protected void renderBuffer( Node target, DefaultPartBuffer buffer,
                                 LightGradient gradient, CellData lightData) {

        // Resolve the GeomParts into actual JME mesh data
        for( DefaultPartBuffer.PartList list : buffer.getPartLists() ) {
            if( list.list.isEmpty() ) {
                continue;
            }
            final MaterialType mt = list.materialType;
            final MeshBuffers buffers = buildMeshBuffers(mt, list.vertCount, list.triCount);
            int baseIndex = 0;
            for( DefaultPartBuffer.PartEntry entry : list.list ) {
                baseIndex = emitPart(buffers, mt, entry, gradient, lightData, baseIndex);
            }
            final Mesh mesh = assembleMesh(buffers);
            attachGeometryToTarget(target, mt, list, mesh);
        }
    }

    /**
     * Allocate the per-MaterialType vertex / texcoord / index / color / normal /
     * tangent / direction buffers required by {@code mt}'s {@link GeomReq} set
     * and pack them into a {@link MeshBuffers} struct. Hi-res vs lo-res scaling
     * is selected per-buffer by the matching {@code LoRes*} requirement.
     */
    private static MeshBuffers buildMeshBuffers(final MaterialType mt, final int vertCount, final int triCount) {
        final ScaledBuffer pos = mt.requires(GeomReq.LoResPositions)
                ? ScaledBuffer.createScaledBuffer(vertCount * 3, 0, 32)
                : ScaledBuffer.createUnscaledBuffer(vertCount * 3);
        final ScaledBuffer texes = mt.requires(GeomReq.LoResTexCoords)
                ? ScaledBuffer.createScaledBuffer(vertCount * 2, 0, 1)
                : ScaledBuffer.createUnscaledBuffer(vertCount * 2);
        // The 3-vertex assumption may not hold for point-sprite parts
        // The 'vertCount' part is weird here... it's just informational to do
        // some bounds checking, I think.
        final IndexBuffer indexes = IndexBuffer.createIndexBuffer(vertCount, triCount * 3);
        // We'll use the color buffer for lighting like Mythruna-proper did
        // but some of Mythruna's materials also used color for other things
        // so it might be less confusing to pick an unused tex coord.
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

    /**
     * Append one {@link DefaultPartBuffer.PartEntry}'s vertex / lighting /
     * texcoord / index data into {@code buffers}. Returns the next baseIndex
     * (callers chain a running offset across the part list).
     *
     * <p>Throws if {@code dirB} is requested by the material but the entry's
     * part has no valid direction — the caller (per-MaterialType list) is the
     * one that promised to supply only directional parts.
     */
    private static int emitPart(
            final MeshBuffers buffers,
            final MaterialType mt,
            final DefaultPartBuffer.PartEntry entry,
            final LightGradient gradient,
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

    /**
     * Per-vertex inner loop: emit position (offset by entry origin), the per-vertex
     * direction byte if the material requires it, and forward the lighting query
     * to {@code gradient}. {@code gradient} writes into {@link MeshBuffers#colors}
     * directly — every face contributes lighting (no opt-out).
     */
    private static void emitVertices(
            final MeshBuffers buffers,
            final int i, final int j, final int k,
            final byte dir, final Direction dirEnum,
            final float[] verts, final int size,
            final LightGradient gradient, final CellData lightData) {
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

    /**
     * Copy this part's normal / tangent / texcoord arrays into the matching
     * mesh buffers. Each is gated by the buffer being non-null (material
     * required it) AND the part actually having data (normals / tangents may
     * be absent on point-sprite parts; texcoords always present).
     * Out-of-bounds-texcoord check intentionally removed (2020-12-24): wrapping
     * coordinates (cylinders, etc.) are valid.
     */
    private static void copyAuxBuffers(final MeshBuffers buffers, final GeomPart part) {
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

    /**
     * Stamp every populated buffer in {@code buffers} into a fresh
     * {@link ColliderlessMesh} and return it. Index format is dispatched on
     * the {@link IndexBuffer.Format} discriminator (Int / Short / Byte); the
     * direction / normal / tangent buffers are skipped if they're either
     * absent (null) or never written to (position == 0).
     */
    private Mesh assembleMesh(final MeshBuffers buffers) {
        final Mesh mesh = new ColliderlessMesh("block", allowCollisions);
        buffers.pos.applyToMesh(mesh, VertexBuffer.Type.Position, 3);
        buffers.texes.applyToMesh(mesh, VertexBuffer.Type.TexCoord, 2);
        attachIndexBuffer(mesh, buffers.indexes);
        if (buffers.dirB != null && buffers.dirB.position() != 0) {
            mesh.setBuffer(VertexBuffer.Type.Size, 1, buffers.dirB);
        }
        if (buffers.nb != null && buffers.nb.position() != 0) {
            buffers.nb.applyToMesh(mesh, VertexBuffer.Type.Normal, 3);
        }
        if (buffers.tb != null && buffers.tb.position() != 0) {
            buffers.tb.applyToMesh(mesh, VertexBuffer.Type.Tangent, 3);
        }
        mesh.setBuffer(VertexBuffer.Type.Color, 4, buffers.colors);
        mesh.setStatic();
        // FIXME: we do not need to calculate a bound because we could have
        // collected that information above.
        mesh.updateBound();
        return mesh;
    }

    /**
     * Bind {@code indexes} to {@code mesh} as the {@link VertexBuffer.Type#Index}
     * buffer, dispatching on the {@link IndexBuffer.Format} discriminator
     * (Int / Short / Byte). Throws {@link IllegalStateException} for any other
     * format — extracted so {@link #assembleMesh} stays under the cyclomatic
     * threshold; behaviour preserved exactly.
     */
    private static void attachIndexBuffer(final Mesh mesh, final IndexBuffer indexes) {
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

    /**
     * Wrap {@code mesh} in a {@link Geometry}, look up the matching
     * {@link Material} (falling back to a "bad" placeholder if missing),
     * route alpha-blending materials to {@link Bucket#Transparent}, and
     * attach to {@code target}. Throws if no fallback material exists either.
     */
    private void attachGeometryToTarget(
            final Node target, final MaterialType mt, final DefaultPartBuffer.PartList list, final Mesh mesh) {
        final Geometry geom = new Geometry("mesh:" + mt + ":" + list.primitiveType, mesh);
        Material mat = materials.get(mt.getId());
        if (log.isInfoEnabled()) {
            log.info("MaterialType getId():{}", mt.getId());
        }
        if (mat == null) {
            // Try not to crash at least
            final MaterialType bad = new MaterialType("bad", mt.getGeomReqs());
            mat = materials.get(bad.getId());
        }
        if (mat == null) {
            if (log.isDebugEnabled()) {
                log.debug("all keys:{}", materials.keySet());
            }
            throw new IllegalStateException("Materal not found for:" + mt.getId());
        }
        geom.setMaterial(mat);
        // FIXME: Bucket.Transparent — kind of a hack; not sure what the better way is.
        if (geom.getMaterial().getAdditionalRenderState().getBlendMode() == BlendMode.Alpha) {
            log.debug("Putting in transparent bucket:{}", geom);
            geom.setQueueBucket(Bucket.Transparent);
        }
        target.attachChild(geom);
    }

    /**
     * Per-MaterialType buffer bundle threaded through {@link #buildMeshBuffers},
     * {@link #emitPart}, and {@link #assembleMesh}. Fields are nullable when
     * the originating {@link GeomReq} set didn't request them.
     */
    private static final class MeshBuffers {
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



    // These are lighting specific methods and classes that could be moved
    // out of this class into separate gradient classes.
    //----------------------------------------------------------------------

    /**
     *  Calculates the average r,g,b,sun value over all of the specified
     *  light bits values.
     */
    private int average( int... lights ) {
        int s = 0;
        int r = 0;
        int g = 0;
        int b = 0;
        int count = 0;
        for( int i : lights ) {
            if( i == LightUtils.SOLID ) {
                continue;
            }
            s += LightUtils.sun(i);
            r += LightUtils.red(i);
            g += LightUtils.green(i);
            b += LightUtils.blue(i);
            count++;
        }
        if( count == 0 ) {
            return 0;
        }
        return LightUtils.toLight(s/count, r/count, g/count, b/count);
    }

    /**
     *  Builds a SmoothLightGradient from the specified light data.
     */
    private LightGradient calculateLightGradient( CellArray cells, CellData lightData ) {
        int xSize = cells.getSizeX();
        int ySize = cells.getSizeY();
        int zSize = cells.getSizeZ();

        CellArray corners = new CellArray(xSize + 1, ySize + 1, zSize + 1);

        for( int x = 0; x <= xSize; x++ ) {
            for( int y = 0; y <= ySize; y++ ) {
                for( int z = 0; z <= zSize; z++ ) {
                    // Rereading this today, I think there is a mismatch between
                    // how I'm building the corners array and how I'm using the
                    // corners array.  I also half-remember that there might be a
                    // clever collapsing that happens here that makes it work.
                    // ...because it does seem to be working.
                    // TODO: refigure out what's going on here and document it.
                    //
                    // Ok, thinking about this again, I think it's a matter of
                    // remembering what the grid coordinate means versus the cell
                    // coordinate.
                    //
                    // aa --- ba --- ca --- da
                    // |      |      |      |
                    // |  AA  |  BA  |  CA  |
                    // |      |      |      |
                    // ab --- bb --- cb --- db
                    // |      |      |      |
                    // |  AB  |  BB  |  CB  |
                    // |      |      |      |
                    // ac --- bc --- cc --- dc
                    //
                    // We are averaging cell values into the shared corners.
                    // So for corner 'bb' we need to sample AA, BA, AB, BB (in 3d)
                    // which is what the code below is doing. x,y,z in 'cell space'
                    // means something slightly different than in 'corner space'.
                    //

                    int l1 = lightData.getCell(x-1, y-1, z-1);
                    int l2 = lightData.getCell(x, y-1, z-1);
                    int l3 = lightData.getCell(x, y, z-1);
                    int l4 = lightData.getCell(x-1, y, z-1);
                    int l5 = lightData.getCell(x-1, y-1, z);
                    int l6 = lightData.getCell(x, y-1, z);
                    int l7 = lightData.getCell(x, y, z);
                    int l8 = lightData.getCell(x-1, y, z);

                    corners.setCell(x, y, z, average(l1, l2, l3, l4, l5, l6, l7, l8));
                }
            }
        }

        return new SmoothLightGradient(corners);
    }

    private interface LightGradient {
        public void appendLight( CellData lights, int i, int j, int k, float x, float y, float z, Direction dir, FloatBuffer colors );
    }

    /** Per-axis cell-offset for outward-facing border vertices: East/West affect X. */
    private static int xOffset(final Direction dir, final float x) {
        if (dir == Direction.East && x == 1) {
            return 1;
        }
        if (dir == Direction.West && x == 0) {
            return -1;
        }
        return 0;
    }

    /** Per-axis cell-offset for outward-facing border vertices: Up/Down affect Y. */
    private static int yOffset(final Direction dir, final float y) {
        if (dir == Direction.Up && y == 1) {
            return 1;
        }
        if (dir == Direction.Down && y == 0) {
            return -1;
        }
        return 0;
    }

    /** Per-axis cell-offset for outward-facing border vertices: South/North affect Z. */
    private static int zOffset(final Direction dir, final float z) {
        if (dir == Direction.South && z == 1) {
            return 1;
        }
        if (dir == Direction.North && z == 0) {
            return -1;
        }
        return 0;
    }

    private class NoLightGradient implements LightGradient {
        CellData lightData;

        public NoLightGradient( CellData lightData ) {
            this.lightData = lightData;
        }

        public void appendLight( CellData lights, final int i, final int j, final int k, float x, float y, float z, Direction dir, FloatBuffer colors ) {

            // If the vertex sits on the border facing outward (most common case),
            // the sample point steps one cell along that axis to the cell behind
            // the visible face. Each axis is independent, so we can compute its
            // delta in isolation.
            final int li = i + xOffset(dir, x);
            final int lj = j + yOffset(dir, y);
            final int lk = k + zOffset(dir, z);

            int l = lights.getCell(li,lj,lk, 0xf000);

            int s = (l >> 12) & 0xf;
            int r = (l >> 8) & 0xf;
            int g = (l >> 4) & 0xf;
            int b = (l) & 0xf;

            colors.put(r/15f).put(g/15f).put(b/15f).put(s/15f);
        }
    }

    private class SmoothLightGradient implements LightGradient {
        CellArray corners;

        public SmoothLightGradient( CellArray corners ) {
            this.corners = corners;
        }

        private float interp( float x, float x1, float x2 ) {
            return x1 + (x2 - x1) * x;
        }

        private float bilinearInterp( float x, float y, float nw, float ne, float se, float sw ) {
            float n = interp(x, nw, ne);
            float s = interp(x, sw, se);
            return interp(y, n, s);
        }

        private float trilinearInterp( float x, float y, float z,
                                       float dnw, float dne, float dse, float dsw,
                                       float unw, float une, float use, float usw ) {
            float d = bilinearInterp(x, y, dnw, dne, dse, dsw);
            float u = bilinearInterp(x, y, unw, une, use, usw);
            return interp(z, d, u);
        }

        private float accumToFloat( int accum ) {
            // The accumulator is x 8, so divide by 8 to average it
            int result = accum; // >> 3;
            // Then make it from 0..1
            return result/15f;
        }

        private float accToRed( int spread ) {
            return accumToFloat(LightUtils.red(spread));
        }
        private float accToGreen( int spread ) {
            return accumToFloat(LightUtils.green(spread));
        }
        private float accToBlue( int spread ) {
            return accumToFloat(LightUtils.blue(spread));
        }
        private float accToSun( int spread ) {
            return accumToFloat(LightUtils.sun(spread));
        }

        public void appendLight( CellData lights, int i, int j, int k, float x, float y, float z, Direction dir, FloatBuffer colors ) {

            int dnw = corners.getCell(i, j, k);
            int dne = corners.getCell(i + 1, j, k);
            int dse = corners.getCell(i + 1, j + 1, k);
            int dsw = corners.getCell(i, j + 1, k);
            int unw = corners.getCell(i, j, k+1);
            int une = corners.getCell(i + 1, j, k+1);
            int use = corners.getCell(i + 1, j + 1, k+1);
            int usw = corners.getCell(i, j + 1, k+1);

            float red = trilinearInterp(x, y, z,
                    accToRed(dnw), accToRed(dne), accToRed(dse), accToRed(dsw),
                    accToRed(unw), accToRed(une), accToRed(use), accToRed(usw));
            float green = trilinearInterp(x, y, z,
                    accToGreen(dnw), accToGreen(dne), accToGreen(dse), accToGreen(dsw),
                    accToGreen(unw), accToGreen(une), accToGreen(use), accToGreen(usw));
            float blue = trilinearInterp(x, y, z,
                    accToBlue(dnw), accToBlue(dne), accToBlue(dse), accToBlue(dsw),
                    accToBlue(unw), accToBlue(une), accToBlue(use), accToBlue(usw));
            float sun = trilinearInterp(x, y, z,
                    accToSun(dnw), accToSun(dne), accToSun(dse), accToSun(dsw),
                    accToSun(unw), accToSun(une), accToSun(use), accToSun(usw));

            colors.put(red).put(green).put(blue).put(sun);
        }
    }

}
