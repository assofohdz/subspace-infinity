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

import com.jme3.material.Material;
import com.jme3.material.RenderState.BlendMode;
import com.jme3.renderer.queue.RenderQueue.Bucket;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.VertexBuffer;
import com.simsilica.mblock.CellArray;
import com.simsilica.mblock.CellData;
import com.simsilica.mblock.Direction;
import com.simsilica.mblock.LightUtils;
import com.simsilica.mblock.geom.ColliderlessMesh;
import com.simsilica.mblock.geom.DefaultPartBuffer;
import com.simsilica.mblock.geom.MaterialType;
import java.nio.FloatBuffer;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Creates JME geometry for a block array using the configured material registry
 * and the global {@link com.simsilica.mblock.BlockTypeIndex}. Pure-function
 * helpers (mesh-buffer building, light-axis sampling) live in
 * {@link BlockMeshBuilder}; this class keeps instance state
 * ({@code materials}, {@code allowCollisions}) and the JME-side wiring.
 *
 * @author Paul Speed
 */
public class InfinityGeometryFactory {

    static Logger log = LoggerFactory.getLogger(InfinityGeometryFactory.class);

    private Map<String, Material> materials;
    private boolean allowCollisions;

    public InfinityGeometryFactory( final Map<String, Material> materials ) {
        this(true, materials);
    }

    public InfinityGeometryFactory( final boolean allowCollisions, final Map<String, Material> materials ) {
        this.allowCollisions = allowCollisions;
        this.materials = materials;
    }

    /** Clears {@code target}'s children, then attaches one Geometry per material in {@code cells}. */
    public Node generateBlocks( final Node target, final CellArray cells, final CellData lightData, final boolean smoothLighting ) {
        log.info("Generating blocks");
        long start = System.nanoTime();
        Node result = target;
        result.detachAllChildren();

        DefaultPartBuffer buffer = new DefaultPartBuffer();
        BlockMeshBuilder.populateBlockBuffer(buffer, cells);

        LightGradient gradient =
                smoothLighting ? calculateLightGradient(cells, lightData) : new NoLightGradient();
        renderBuffer(result, buffer, gradient, lightData);

        long end = System.nanoTime();
        if( log.isTraceEnabled() ) {
            log.trace("Generated in:{} ms", (end - start) / 1000000.0);
        }
        return result;
    }

    /** Same shape as {@link #generateBlocks} but for fluid cells. */
    public Node generateFluid( final Node target, final CellArray fluid, final CellArray cells, final CellData lightData, final boolean smoothLighting ) {
        if( fluid == null ) {
            return target;
        }

        long start = System.nanoTime();
        Node result = target;
        result.detachAllChildren();

        DefaultPartBuffer buffer = new DefaultPartBuffer();
        BlockMeshBuilder.populateFluidBuffer(buffer, fluid, cells);

        LightGradient gradient =
                smoothLighting ? calculateLightGradient(fluid, lightData) : new NoLightGradient();
        renderBuffer(result, buffer, gradient, lightData);

        long end = System.nanoTime();
        if( log.isTraceEnabled() ) {
            log.trace("Generated in:{} ms", (end - start) / 1000000.0);
        }
        return result;
    }

    protected void renderBuffer( final Node target, final DefaultPartBuffer buffer,
                                 final LightGradient gradient, final CellData lightData) {
        // Resolve the GeomParts into actual JME mesh data
        for( final DefaultPartBuffer.PartList list : buffer.getPartLists() ) {
            if( list.list.isEmpty() ) {
                continue;
            }
            final MaterialType mt = list.materialType;
            final BlockMeshBuilder.MeshBuffers buffers =
                    BlockMeshBuilder.buildMeshBuffers(mt, list.vertCount, list.triCount);
            int baseIndex = 0;
            for( final DefaultPartBuffer.PartEntry entry : list.list ) {
                baseIndex = BlockMeshBuilder.emitPart(buffers, mt, entry, gradient, lightData, baseIndex);
            }
            final Mesh mesh = assembleMesh(buffers);
            attachGeometryToTarget(target, mt, list, mesh);
        }
    }

    private Mesh assembleMesh(final BlockMeshBuilder.MeshBuffers buffers) {
        final Mesh mesh = new ColliderlessMesh("block", allowCollisions);
        buffers.pos.applyToMesh(mesh, VertexBuffer.Type.Position, 3);
        buffers.texes.applyToMesh(mesh, VertexBuffer.Type.TexCoord, 2);
        BlockMeshBuilder.attachIndexBuffer(mesh, buffers.indexes);
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
        // NOTE: redundant bound recalc — buffer-fill loop above already has
        // the min/max corners and could stamp the bound directly.
        mesh.updateBound();
        return mesh;
    }

    // Falls back to a "bad" material if {@code mt} isn't registered; alpha materials route to the transparent bucket.
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
        // NOTE: any alpha-blended material auto-routes to the transparent queue;
        // explicit bucket selection by material type would be cleaner.
        if (geom.getMaterial().getAdditionalRenderState().getBlendMode() == BlendMode.Alpha) {
            log.debug("Putting in transparent bucket:{}", geom);
            geom.setQueueBucket(Bucket.Transparent);
        }
        target.attachChild(geom);
    }

    // These are lighting specific methods and classes that could be moved
    // out of this class into separate gradient classes.
    //----------------------------------------------------------------------

    private static int average( final int... lights ) {
        int s = 0;
        int r = 0;
        int g = 0;
        int b = 0;
        int count = 0;
        for( final int i : lights ) {
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

    private LightGradient calculateLightGradient( final CellArray cells, final CellData lightData ) {
        int xSize = cells.getSizeX();
        int ySize = cells.getSizeY();
        int zSize = cells.getSizeZ();

        CellArray corners = new CellArray(xSize + 1, ySize + 1, zSize + 1);

        for( int x = 0; x <= xSize; x++ ) {
            for( int y = 0; y <= ySize; y++ ) {
                for( int z = 0; z <= zSize; z++ ) {
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

    interface LightGradient {
        // Per-vertex light sampler; param shape mirrors the call site in BlockMeshBuilder.emitVertices.
        @SuppressWarnings("PMD.ExcessiveParameterList")
        void appendLight( CellData lights, int i, int j, int k, float x, float y, float z, Direction dir, FloatBuffer colors );
    }

    private static class NoLightGradient implements LightGradient {

        NoLightGradient() {}

        // Signature fixed by LightGradient interface.
        @SuppressWarnings("PMD.ExcessiveParameterList")
        @Override
        public void appendLight( final CellData lights, final int i, final int j, final int k, final float x, final float y, final float z, final Direction dir, final FloatBuffer colors ) {
            // If the vertex sits on the border facing outward (most common case),
            // the sample point steps one cell along that axis to the cell behind
            // the visible face. Each axis is independent — see BlockMeshBuilder.{x,y,z}Offset.
            final int li = i + BlockMeshBuilder.xOffset(dir, x);
            final int lj = j + BlockMeshBuilder.yOffset(dir, y);
            final int lk = k + BlockMeshBuilder.zOffset(dir, z);

            int l = lights.getCell(li, lj, lk, 0xf000);

            int s = (l >> 12) & 0xf;
            int r = (l >> 8) & 0xf;
            int g = (l >> 4) & 0xf;
            int b = l & 0xf;

            colors.put(r/15f).put(g/15f).put(b/15f).put(s/15f);
        }
    }

    private static class SmoothLightGradient implements LightGradient {
        private final CellArray corners;

        SmoothLightGradient( final CellArray corners ) {
            this.corners = corners;
        }

        private static float interp( final float x, final float x1, final float x2 ) {
            return x1 + (x2 - x1) * x;
        }

        private static float bilinearInterp( final float x, final float y, final float nw, final float ne, final float se, final float sw ) {
            float n = interp(x, nw, ne);
            float s = interp(x, sw, se);
            return interp(y, n, s);
        }

        // Math kernel — 11 params are the sample point + 8 corner lights; record-wrapping just shifts verbosity to call sites.
        @SuppressWarnings("PMD.ExcessiveParameterList")
        private static float trilinearInterp( final float x, final float y, final float z,
                                       final float dnw, final float dne, final float dse, final float dsw,
                                       final float unw, final float une, final float use, final float usw ) {
            float d = bilinearInterp(x, y, dnw, dne, dse, dsw);
            float u = bilinearInterp(x, y, unw, une, use, usw);
            return interp(z, d, u);
        }

        private static float accumToFloat( final int accum ) {
            // The accumulator is x 8, so divide by 8 to average it
            int result = accum;
            // Then make it from 0..1
            return result/15f;
        }

        private static float accToRed( final int spread ) {
            return accumToFloat(LightUtils.red(spread));
        }
        private static float accToGreen( final int spread ) {
            return accumToFloat(LightUtils.green(spread));
        }
        private static float accToBlue( final int spread ) {
            return accumToFloat(LightUtils.blue(spread));
        }
        private static float accToSun( final int spread ) {
            return accumToFloat(LightUtils.sun(spread));
        }

        // Signature fixed by LightGradient interface.
        @SuppressWarnings("PMD.ExcessiveParameterList")
        @Override
        public void appendLight( final CellData lights, final int i, final int j, final int k, final float x, final float y, final float z, final Direction dir, final FloatBuffer colors ) {
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
