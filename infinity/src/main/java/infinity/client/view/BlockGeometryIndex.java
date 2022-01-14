/*
 * $Id$
 *
 * Copyright (c) 2017, Simsilica, LLC
 * All rights reserved.
 */

package infinity.client.view;

import com.simsilica.mblock.*;
import com.simsilica.mblock.config.MaterialRegistry;
import com.simsilica.mblock.io.BlockTypeData;
import com.simsilica.mblock.io.FluidTypeData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jme3.asset.AssetManager;
import com.jme3.scene.Node;
import com.jme3.material.Material;

import com.simsilica.mblock.config.DefaultBlockSet;
import com.simsilica.mblock.geom.GeometryFactory;

import java.util.Map;

/**
 *
 *
 * @author Paul Speed
 */
public class BlockGeometryIndex {

    static Logger log = LoggerFactory.getLogger(BlockGeometryIndex.class);

    private final GeometryFactory geomFactory;

    public BlockGeometryIndex(final AssetManager assets) {

        //blockSet = new DefaultBlockSet(assets, "Textures/palette1.png", "Textures/palette2.png", "Textures/bad.jpg");

        try {
            if( !BlockTypeIndex.isInitialized() ) {
                BlockTypeIndex.initialize(BlockTypeData.load("/blocks.bset"));
                FluidTypeIndex.initialize(FluidTypeData.load("/fluids.fset"));
            }

            Map<String, Material> materials = MaterialRegistry.loadCompiledMaterials(assets, "/materials.mset");
            geomFactory = new GeometryFactory(materials);
        } catch( Exception e ) {
            throw new RuntimeException("Error initializing block set configuration", e);
        }
    }

    public Node generateBlocks(final Node target, final CellArray cells) {
        return generateBlocks(target, cells, new ConstantCellData(LightUtils.DIRECT_SUN));
    }

    public Node generateBlocks(final Node target, final CellArray cells, final CellData lightData) {
        return generateBlocks(target, cells, lightData, true);
    }

    public Node generateBlocks(final Node target, final CellArray cells, final CellData lightData,
            final boolean smoothLighting) {
        return geomFactory.generateBlocks(target, cells, lightData, smoothLighting);
    }

//    private Map<MaterialType, Material> materials = new HashMap<>();
//
//    private MaterialType[] materialTypes;
//    private BlockType[] types;
//
//public static boolean debug = false;
//
//    public BlockGeometryIndex() {
//
//        // Just hard code some stuff for now
//        this.materialTypes = new MaterialType[] {
//                null,
//                new MaterialType("palette1", 1, true, false, false),
//            };
//
//        this.types = new BlockType[65];
//        for( int i = 0; i < 64; i++ ) {
//            types[i+1] = new BlockType(createFactory(1, i));
//        }
//    }
//
//    private BlockFactory createFactory( int mt, int color ) {
//        int x = color & 0x7;
//        int y = (color & 0x38) >> 3;
//
//        float textureScale = 0.125f; // 1/8th
//        float halfScale = textureScale * 0.5f;
//        float s = x * textureScale + halfScale;
//        float t = 1f - (y * textureScale + halfScale);
//        //float t = y * 0.125f;
//
//        DefaultBlockFactory result = DefaultBlockFactory.createCube(0, materialTypes[mt]);
//
//        for( PartFactory partFactory : result.getDirParts() ) {
//            for( GeomPart part : ((DefaultPartFactory)partFactory).getTemplates() ) {
//
//                // Convenient that we want all texture coordinates to be the same
//                float[] texes = part.getTexCoords();
//                for( int i = 0; i < texes.length; i+=2 ) {
//                    texes[i] = s;
//                    texes[i+1] = t;
//                }
//            }
//        }
//
//        return result;
//    }
//
//    private Material getMaterial( MaterialType type ) {
//        Material mat = materials.get(type);
//        if( mat == null ) {
//            GuiGlobals globals = GuiGlobals.getInstance();
//            Texture texture = globals.loadTexture("Textures/" + type.getName() + ".png", true, true);
//            mat = globals.createMaterial(texture, true).getMaterial();
//            //mat = globals.createMaterial(ColorRGBA.White, true).getMaterial();
//            mat.setColor("Diffuse", ColorRGBA.White);//.mult(0.75f));
//            mat.setColor("Ambient", ColorRGBA.White);//.mult(0.75f));
//            //mat.setColor("Ambient", ColorRGBA.Green);
//            mat.setBoolean("UseMaterialColors", true);
//
//            //mat = globals.createMaterial(texture, false).getMaterial();
//            materials.put(type, mat);
//        }
//        return mat;
//    }
//
//    public Node generateBlocks( Node target, CellArray cells ) {
//
//
////System.out.println("===============generateBlocks(" + target + ", " + cells + ")");
//
//        long start = System.nanoTime();
//        Node result = target;
//        result.detachAllChildren();
//
//        DefaultPartBuffer buffer = new DefaultPartBuffer();
//
//        int xSize = cells.getSizeX();
//        int ySize = cells.getSizeY();
//        int zSize = cells.getSizeZ();
//
//        for( int x = 0; x < xSize; x++ ) {
//            for( int y = 0; y < ySize; y++ ) {
//                for( int z = 0; z < zSize; z++ ) {
//                    int val = cells.getCell(x, y, z);
////System.out.println("[" + x + "][" + y + "][" + z + "] val:" + val);
//                    int type = MaskUtils.getType(val);
//                    if( type == 0 ) {
//                        continue;
//                    }
//                    BlockType blockType = types[type];
//                    if( blockType == null ) {
//                        continue;
//                    }
//                    // No masks for now so we'll force it
//                    int lightMask = 0;
//                    int sideMask = MaskUtils.getSideMask(val);
//if( debug && sideMask != 0 ) {
//    log.info("[" + x + "][" + y + "][" + z + "] val:" + val + " @" + type + " #" + Integer.toBinaryString(sideMask));
//}
//DefaultBlockFactory.debug = debug;
//                    blockType.getFactory().addGeometryToBuffer(buffer, x, y, z, x, y, z,
//                                                               sideMask, lightMask,
//                                                               cells,
//                                                               blockType);
//                }
//            }
//        }
//
//        for( DefaultPartBuffer.PartList list : buffer.getPartLists() ) {
////            System.out.println("Part list:" + list);
//
//            if( list.list.isEmpty() ) {
//                continue;
//            }
//
//            // We know we have simplified geometry so our mesh generation
//            // can also be simplified
//            int vertCount = list.vertCount;
//            FloatBuffer pos = BufferUtils.createFloatBuffer(vertCount * 3);
//            //ByteBuffer texes = BufferUtils.createByteBuffer(vertCount * 2);
//            FloatBuffer texes = BufferUtils.createFloatBuffer(vertCount * 2);
//            ShortBuffer indexes = BufferUtils.createShortBuffer(list.triCount * 3);
//            //ByteBuffer normalIndexes = BufferUtils.createByteBuffer(vertCount);
//
//            // We'll create real normals for now to avoid a custom material
//            FloatBuffer norms = BufferUtils.createFloatBuffer(vertCount * 3);
//
//            int baseIndex = 0;
//            for( DefaultPartBuffer.PartEntry entry : list.list ) {
//
//                int i = entry.i;
//                int j = entry.j;
//                int k = entry.k;
//                GeomPart part = entry.part;
//
//                int dir = part.getDirection();
////System.out.println("dir:" + dir);
//                int size = part.getVertexCount();
//                float[] verts = part.getCoords();
//                int vIndex = 0;
//                int tIndex = 0;
//                for( int v = 0; v < size; v++ ) {
//                    float x = verts[vIndex++];
//                    float y = verts[vIndex++];
//                    float z = verts[vIndex++];
////System.out.println("pos:" + (i + x) + ", " + (j + y) + ", " + (k + z));
//                    pos.put(i + x);
//                    pos.put(j + y);
//                    pos.put(k + z);
//
//                    switch( dir ) {
//                        case 0:
//                            norms.put(0).put(0).put(-1);
//                            break;
//                        case 1:
//                            norms.put(0).put(0).put(1);
//                            break;
//                        case 2:
//                            norms.put(1).put(0).put(0);
//                            break;
//                        case 3:
//                            norms.put(-1).put(0).put(0);
//                            break;
//                        case 4:
//                            norms.put(0).put(1).put(0);
//                            break;
//                        default:
//                        case 5:
//                            norms.put(0).put(-1).put(0);
//                            break;
//                    }
//                }
//
//
//
//                float[] texArray = part.getTexCoords();
//                for( int t = 0; t < texArray.length; t++ ) {
//                    if( texArray[t] < 0 || texArray[t] > 1 ) {
//                        throw new RuntimeException("Entry has out of bounds texcoord:" + texArray[t]
//                                                    + " type:" + part.getMaterialType());
//                    }
//                    //texes.put((byte)(texArray[t] * 255));
//                    texes.put(texArray[t]);
//                }
//
//
//                // The indexes need to be offset also
//                for( short s : part.getIndexes() ) {
//                    indexes.put((short)(baseIndex + s));
//                }
//                baseIndex += size;
//            }
////System.out.println("Index count:" + baseIndex);
//
//            Mesh mesh = new Mesh();
//            mesh.setBuffer(VertexBuffer.Type.Position, 3, pos);
//            mesh.setBuffer(VertexBuffer.Type.TexCoord, 2, texes);
//            mesh.setBuffer(VertexBuffer.Type.Index, 3, indexes);
//            mesh.setBuffer(VertexBuffer.Type.Normal, 3, norms);
//            mesh.setStatic();
//            mesh.updateBound();
//
//            Geometry geom = new Geometry("mesh:" + list.materialType + ":" + list.primitiveType, mesh);
//            geom.setMaterial(getMaterial(list.materialType));
//            result.attachChild(geom);
//        }
//
//        long end = System.nanoTime();
////        System.out.println("Generated in:" + ((end - start)/1000000.0) + " ms");
//        return result;
//    }
}
