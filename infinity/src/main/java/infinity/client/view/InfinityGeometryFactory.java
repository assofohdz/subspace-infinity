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

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.nio.*;
import java.util.HashMap;
import java.util.Map;

import com.jme3.asset.DesktopAssetManager;
import com.jme3.texture.Image;
import com.jme3.texture.Texture2D;
import com.jme3.texture.plugins.AWTLoader;
import com.simsilica.mblock.geom.*;
import infinity.map.LevelFile;
import infinity.map.LevelLoader;
import infinity.systems.MapSystem;
import org.slf4j.*;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.material.RenderState.BlendMode;
import com.jme3.renderer.queue.RenderQueue.Bucket;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.VertexBuffer;
import com.jme3.scene.mesh.IndexBuffer;
import com.jme3.util.BufferUtils;

import com.simsilica.mathd.*;

import com.simsilica.mblock.*;


/**
 *  Creates JME geometry for a block array using the configured
 *  material registry and global block type index.
 *
 *  @author    Paul Speed
 */
public class InfinityGeometryFactory {
    private static int TILEID_MASK = 0x000000ff;
    private static int MAPID_MASK = 0x000fff00;
    // Map from tilekey to image
    private final HashMap<Integer, Image> tileKeyToImageMap = new HashMap<>();
    private final Map<Integer, LevelFile> mapIdToLevels = new HashMap<>();
    private final Map<Integer, BlockType> tileKeyToBlockTypeMap = new HashMap<>();
    private final Map<Integer, MaterialType> tileKeyToMaterialType = new HashMap<>();
    // private boolean logged;
    private final AWTLoader imgLoader;
    private DesktopAssetManager am;

    static Logger log = LoggerFactory.getLogger(InfinityGeometryFactory.class);

    private Map<Integer, Material> IntegerMaterials;

    private Map<String, Material> materials;
    private Map<Integer, Material> tileMaterials;
    private boolean allowCollisions;

    public InfinityGeometryFactory( Map<String, Material> materials ) {
        this(true, materials);
    }

    public InfinityGeometryFactory( boolean allowCollisions, Map<String, Material> materials ) {
        this.allowCollisions = allowCollisions;
        this.materials = materials;
        this.imgLoader = new AWTLoader();
        this.tileMaterials = new HashMap<>();
        this.am = new DesktopAssetManager(true);
        this.am.registerLoader(LevelLoader.class, "lvl");
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

        int xSize = cells.getSizeX();
        int ySize = cells.getSizeY();
        int zSize = cells.getSizeZ();

        for( int x = 0; x < xSize; x++ ) {
            for( int y = 0; y < ySize; y++ ) {
                for( int z = 0; z < zSize; z++ ) {
                    int val = cells.getCell(x, y, z);
                    int type = MaskUtils.getType(val);
                    if( type == 0 ) {
                        continue;
                    }
                    BlockType blockType = BlockTypeIndex.get(type);
                    if( blockType == null ) {
                        continue;
                    }
                    int sideMask = MaskUtils.getSideMask(val);
                    blockType.getFactory().addGeometryToBuffer(buffer, x, y, z, x, y, z,
                            sideMask, cells, blockType);
                }
            }
        }

        // Resolve a light gradient implementation based on the
        // smooth lighting flag.  Ultimately, if we ever have more than
        // two implementations or can think of reasons why this should be
        // customized then we should break it out as a parameter.  Might also
        // consider supporting pregenerated part buffers though I have no
        // strong reason why today.  2020-11-22
        LightGradient gradient = null;
        if( smoothLighting ) {
            gradient = calculateLightGradient(cells, lightData);
        } else {
            gradient = new NoLightGradient(lightData);
        }
        renderBuffer(result, buffer, gradient, lightData);

        long end = System.nanoTime();
        if( log.isTraceEnabled() ) {
            log.trace("Generated in:" + ((end - start)/1000000.0) + " ms");
        }
        return result;
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

        int xSize = fluid.getSizeX();
        int ySize = fluid.getSizeY();
        int zSize = fluid.getSizeZ();

        for( int x = 0; x < xSize; x++ ) {
            for( int y = 0; y < ySize; y++ ) {
                for( int z = 0; z < zSize; z++ ) {
                    int val = fluid.getCell(x, y, z);
                    int type = FluidUtils.getType(val);
                    if( type == 0 ) {
                        continue;
                    }
                    FluidType fluidType = FluidTypeIndex.get(type);
                    if( fluidType == null ) {
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

        // Resolve a light gradient implementation based on the
        // smooth lighting flag.  Ultimately, if we ever have more than
        // two implementations or can think of reasons why this should be
        // customized then we should break it out as a parameter.  Might also
        // consider supporting pregenerated part buffers though I have no
        // strong reason why today.  2020-11-22
        LightGradient gradient = null;
        if( smoothLighting ) {
            gradient = calculateLightGradient(fluid, lightData);
        } else {
            gradient = new NoLightGradient(lightData);
        }
        renderBuffer(result, buffer, gradient, lightData);

        long end = System.nanoTime();
        if( log.isTraceEnabled() ) {
            log.trace("Generated in:" + ((end - start)/1000000.0) + " ms");
        }
        return result;
    }

    protected void renderBuffer( Node target, DefaultPartBuffer buffer,
                                 LightGradient gradient, CellData lightData) {

        // Resolve the GeomParts into actual JME mesh data
        for( DefaultPartBuffer.PartList list : buffer.getPartLists() ) {
            if( list.list.isEmpty() ) {
                continue;
            }
            MaterialType mt = list.materialType;

            // We know we have simplified geometry so our mesh generation
            // can also be simplified
            int vertCount = list.vertCount;
            ScaledBuffer pos;
            if( mt.requires(GeomReq.LoResPositions) ) {
                pos = ScaledBuffer.createScaledBuffer(vertCount * 3, 0, 32);
            } else {
                pos = ScaledBuffer.createUnscaledBuffer(vertCount * 3);
            }
            ScaledBuffer texes;
            if( mt.requires(GeomReq.LoResTexCoords) ) {
                texes = ScaledBuffer.createScaledBuffer(vertCount * 2, 0, 1);
            } else {
                texes = ScaledBuffer.createUnscaledBuffer(vertCount * 2);
            }
            // The 3-vertex assumption may not hold for point-sprite parts
            // The 'vertCount' part is weird here... it's just informational to do
            // some bounds checking, I think.
            IndexBuffer indexes = IndexBuffer.createIndexBuffer(vertCount, list.triCount * 3);

            // We'll use the color buffer for lighting like Mythruna-proper did
            // but some of Mythruna's materials also used color for other things
            // so it might be less confusing to pick an unused tex coord.
            // Or for the old Mythruna things that used color, we can pick something
            // else like an unused TexCoord#.  Though their usage of "color" probably
            // really is 'color'.
            FloatBuffer colors = BufferUtils.createFloatBuffer(vertCount * 4);  // for lighting

            ScaledBuffer nb = null;
            ScaledBuffer tb = null;
            ByteBuffer dirB = null;
            if( mt.requires(GeomReq.IndexedNormals) ) {
                dirB = BufferUtils.createByteBuffer(vertCount);
            } else {
                // It may require other buffers
                if( mt.requires(GeomReq.Normals) ) {
                    if( mt.requires(GeomReq.LoResNormals) ) {
                        nb = ScaledBuffer.createScaledBuffer(vertCount * 3, -1, 1);
                    } else {
                        nb = ScaledBuffer.createUnscaledBuffer(vertCount * 3);
                    }
                }
                if( mt.requires(GeomReq.Tangents) ) {
                    if( mt.requires(GeomReq.LoResTangents) ) {
                        tb = ScaledBuffer.createScaledBuffer(vertCount * 3, -1, 1);
                    } else {
                        tb = ScaledBuffer.createUnscaledBuffer(vertCount * 3);
                    }
                }
            }

            int baseIndex = 0;
            for( DefaultPartBuffer.PartEntry entry : list.list ) {

                int i = entry.i;
                int j = entry.j;
                int k = entry.k;
                GeomPart part = entry.part;

                byte dir = (byte)part.getDirection();
                if( dirB != null && dir < 0 ) {
                    throw new RuntimeException("Entry for material:" + mt + " has invalid dir:" + dir);
                }
                Direction dirEnum = dir >= 0 ? Direction.values()[dir] : null;
                int size = part.getVertexCount();
                float[] verts = part.getCoords();
                float[] norms = part.getNormals();
                float[] tangents = part.getTangents();

                int vIndex = 0;
                for( int v = 0; v < size; v++ ) {
                    float x = verts[vIndex++];
                    float y = verts[vIndex++];
                    float z = verts[vIndex++];
                    pos.put(i + x);
                    pos.put(j + y);
                    pos.put(k + z);

                    if( dirB != null ) {
                        dirB.put(dir);
                    }

                    // Apply lighting data to this face as required
                    // Note: original Mythruna-new did this too for its lighting
                    // implementation (more or less).  It's interesting that we
                    // don't check the material or anything considering that a comment
                    // above indicates that some block types use color for different
                    // things.  This line seems to indicate that 'colors' is the standard
                    // buffer for lighting data and nothing can 'opt out'.
                    gradient.appendLight(lightData, i, j, k, x, y, z, dirEnum, colors);
                }

                if( nb != null && norms != null ) {
                    nb.put(norms);
                }
                if( tb != null && tangents != null ) {
                    tb.put(tangents);
                }

                float[] texArray = part.getTexCoords();
                for( int t = 0; t < texArray.length; t++ ) {
                    // I don't know why I put this check in because
                    // there could be valid reasons for wanting shifted
                    // coordinates. 2020-12-24
                    //if( texArray[t] < 0 || texArray[t] > 1 ) {
                    //    throw new RuntimeException("Entry has out of bounds texcoord:" + texArray[t]
                    //                                + " type:" + part.getMaterialType());
                    //}
                    // Removing the above because sometimes we want wrapping
                    // like for cylinders.  Not sure how it could work otherwise
                    // without a lot of repeated vertexes
                    texes.put(texArray[t]);
                }

                // The indexes need to be offset also
                for( short s : part.getIndexes() ) {
                    indexes.put(baseIndex + s);
                }
                baseIndex += size;
            }

            // Use a mesh with no collision
            Mesh mesh = new ColliderlessMesh(allowCollisions);
            pos.applyToMesh(mesh, VertexBuffer.Type.Position, 3);
            texes.applyToMesh(mesh, VertexBuffer.Type.TexCoord, 2);

            // Just an otherwise convenient class to have to do this BS
            switch( indexes.getFormat() ) {
                case UnsignedInt:
                    mesh.setBuffer(VertexBuffer.Type.Index, 3, (IntBuffer)indexes.getBuffer());
                    break;
                case UnsignedShort:
                    mesh.setBuffer(VertexBuffer.Type.Index, 3, (ShortBuffer)indexes.getBuffer());
                    break;
                case UnsignedByte:
                    mesh.setBuffer(VertexBuffer.Type.Index, 3, (ByteBuffer)indexes.getBuffer());
                    break;
            }
            if( dirB != null && dirB.position() != 0 ) {
                mesh.setBuffer(VertexBuffer.Type.Size, 1, dirB);
            }
            if( nb != null && nb.position() != 0 ) {
                nb.applyToMesh(mesh, VertexBuffer.Type.Normal, 3);
            }
            if( tb != null && tb.position() != 0 ) {
                tb.applyToMesh(mesh, VertexBuffer.Type.Tangent, 3);
            }
            mesh.setBuffer(VertexBuffer.Type.Color, 4, colors);
            mesh.setStatic();

            // FIXME: we do not need to calculate a bound because we could have
            // collected that information above.
            mesh.updateBound();

            Geometry geom = new Geometry("mesh:" + mt + ":" + list.primitiveType, mesh);
            Material mat = materials.get(mt.getId());

            log.info("MaterialType getId():"+mt.getId());

            if( mat == null ) {
                // Try not to crash at least
                MaterialType bad = new MaterialType("bad", mt.getGeomReqs());
                mat = materials.get(bad.getId());
            }
            if( mat == null ) {
                log.debug("all keys:" + materials.keySet());
                throw new RuntimeException("Materal not found for:" + mt.getId());
            }
            geom.setMaterial(mat);

            // This is kind of a hack... not sure what the better way is. FIXME: Bucket.Transparent
            if( geom.getMaterial().getAdditionalRenderState().getBlendMode() == BlendMode.Alpha ) {
                log.debug("Putting in transparent bucket:" + geom);
                geom.setQueueBucket(Bucket.Transparent);
            }
            target.attachChild(geom);
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

    private class NoLightGradient implements LightGradient {
        CellData lightData;

        public NoLightGradient( CellData lightData ) {
            this.lightData = lightData;
        }

        public void appendLight( CellData lights, int i, int j, int k, float x, float y, float z, Direction dir, FloatBuffer colors ) {

            // If it's on the border facing out (most common case) then we need
            // to move our light sampling point.
            switch( dir ) {
                case North:
                    if( z == 0 ) {
                        k--;
                    }
                    break;
                case South:
                    if( z == 1 ) {
                        k++;
                    }
                    break;
                case East:
                    if( x == 1 ) {
                        i++;
                    }
                    break;
                case West:
                    if( x == 0 ) {
                        i--;
                    }
                    break;
                case Up:
                    if( y == 1 ) {
                        j++;
                    }
                    break;
                case Down:
                    if( y == 0 ) {
                        j--;
                    }
                    break;
            }

            int l = lights.getCell(i,j,k, 0xf000);

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

    private BlockFactory createFactory(final int tileKey, @SuppressWarnings("unused") final int color) {
        final InfinityBlockFactory result = InfinityBlockFactory.createCube(0, getMaterialType(tileKey));
        return result;
    }

    private BlockType getBlockType(final int tileKey) {
        BlockType type = tileKeyToBlockTypeMap.get(Integer.valueOf(tileKey));

        if (type == null) {
            type = new BlockType(new BlockName("", ""), createFactory(tileKey, 1));

            tileKeyToBlockTypeMap.put(Integer.valueOf(tileKey), type);
        } else {
            // log.info("Found cached BlockType: "+type);
        }
        return type;
    }

    protected LevelFile loadMap(final String tileSet) {
        final LevelFile localMap = (LevelFile) am.loadAsset(tileSet);

        return localMap;
    }

    // First type of information:
    private BlockType getBlockType(final int tileId, final int mapId) {

        // Check to see if we have loaded this map before
        if (!mapIdToLevels.containsKey(Integer.valueOf(mapId))) {
            // TODO: Lookup stringname based on mapId
            //For now, use same mapname as server side
            final LevelFile level = loadMap(MapSystem.MAPNAME);
            mapIdToLevels.put(Integer.valueOf(mapId), level);
        }

        final int tileKey = tileId | (mapId << 8);
        // log.info("getBlockType:: tileKey = " + tileKey + " <= (Tile,Map) = (" +
        // tileId + "," + mapId + ")");

        return getBlockType(tileKey);
    }
    // Second type of info:
    private MaterialType getMaterialType(final int tileKey) {

        MaterialType matType = tileKeyToMaterialType.get(Integer.valueOf(tileKey));

        if (matType == null) {

            // final int tileId = tileKey & TILEID_MASK;
            // final int mapId = (tileKey & MAPID_MASK) >> 8;
            // TODO: Lookup the levelname, using the id:

            // log.info("getMaterialType:: tileKey = " + tileKey + " => (Tile,Map) = (" +
            // tileId + "," + mapId + ")");

            // final String mapName = "aswz.lvl";

            // matType = new MaterialType("",tileKey);
            matType = new MaterialType(String.valueOf(tileKey), false, false, false);

            tileKeyToMaterialType.put(Integer.valueOf(tileKey), matType);
        }

        return matType;
    }
    // Third type of information:
    private Material getMaterial(final int tileKey) {
        // int tileKey = tileId | (mapId << 16);
        final int tileId = tileKey & TILEID_MASK;
        final int mapId = (tileKey & MAPID_MASK) >> 8;

        Material mat = materials.get(Integer.valueOf(tileKey));

        if (mat == null) {
            mat = new Material(am, "MatDefs/BlackTransparentShader.j3md");

            // int key = tileIndex | (mapId << 16);
            Image jmeOutputImage = tileKeyToImageMap.get(Integer.valueOf(tileKey));
            if (jmeOutputImage == null) {
                final java.awt.Image awtInputImage = mapIdToLevels.get(Integer.valueOf(mapId)).getTiles()[tileId - 1];
                jmeOutputImage = imgLoader.load(toBufferedImage(awtInputImage), true);

                tileKeyToImageMap.put(Integer.valueOf(tileKey), jmeOutputImage);
                // log.info("Put tile: "+tileIndex+" image into map");
            }
            final Texture2D tex2D = new Texture2D(jmeOutputImage);
            mat.setTexture("ColorMap", tex2D);
            // mat = globals.createMaterial(texture, false).getMaterial();
            tileMaterials.put(tileKey, mat);

            jmeOutputImage.dispose();
        } else {
            // log.info("Found cached material: "+mat+" for tileKey = " + tileKey + " <=
            // (Tile,Map) = (" + tileId + "," + mapId + ")");
        }
        return mat;
    }

    /**
     * Converts a given Image into a BufferedImage
     *
     * @param img The Image to be converted
     * @return The converted BufferedImage
     */
    private BufferedImage toBufferedImage(final java.awt.Image img) {
        if (img instanceof BufferedImage) {
            return (BufferedImage) img;
        }

        final int width = img.getWidth(null);
        final int height = img.getHeight(null);

        // Create a buffered image with transparency
        BufferedImage bimage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

        // Draw the image on to the buffered image
        final Graphics2D bGr = bimage.createGraphics();
        bGr.drawImage(img, 0, 0, null); // No flip
        // bGr.drawImage(img, 0 + width, 0, -width, height, null); //Horisontal flip
        // bGr.drawImage(img, 0, 0 + height, width, -height, null); //Vertical flip
        // bGr.drawImage(img, height, 0, -width, height, null);

        bGr.dispose();

        final AffineTransform tx = AffineTransform.getScaleInstance(-11, -1);
        tx.translate(-bimage.getWidth(null), -bimage.getHeight(null));
        final AffineTransformOp op = new AffineTransformOp(tx, AffineTransformOp.TYPE_NEAREST_NEIGHBOR);
        bimage = op.filter(bimage, null);

        // Return the buffered image
        return bimage;
    }

}




