/*
 * $Id$
 * 
 * Copyright (c) 2017, Simsilica, LLC
 * All rights reserved.
 */
package infinity.client.view;

import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jme3.asset.DesktopAssetManager;
import com.jme3.material.Material;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.VertexBuffer;
import com.jme3.scene.shape.Quad;
import com.jme3.texture.Image;
import com.jme3.texture.Texture2D;
import com.jme3.texture.plugins.AWTLoader;
import com.jme3.util.BufferUtils;

import com.simsilica.mblock.BlockType;
import com.simsilica.mblock.CellArray;
import com.simsilica.mblock.CellData;
import com.simsilica.mblock.Direction;
import com.simsilica.mblock.MaskUtils;
import com.simsilica.mblock.geom.BlockFactory;
import com.simsilica.mblock.geom.DefaultPartBuffer;
import com.simsilica.mblock.geom.GeomPart;
import com.simsilica.mblock.geom.MaterialType;

import infinity.map.LevelFile;
import infinity.map.LevelLoader;

/**
 *
 *
 * @author Paul Speed
 */
public class BlockGeometryIndex {

    private static int TILEID_MASK = 0x000000ff;
    private static int MAPID_MASK = 0x000fff00;

    private static String INVISIBLE = "invisible";
    private static String TILE = "tile";

    public static int BOTTOM_TILE_LAYER = 1;
    public static int TOP_INVISIBLE_LAYER = 2;

    static Logger log = LoggerFactory.getLogger(BlockGeometryIndex.class);

    private Map<Integer, Material> materials = new HashMap<>();

    // private MaterialType[] materialTypes;
    // private BlockType[] types;
    private Map<Integer, BlockType> tileKeyToBlockTypeMap = new HashMap<>();
    private Map<Integer, MaterialType> tileKeyToMaterialType = new HashMap<>();

    public static boolean debug = false;
    private boolean logged;
    private AWTLoader imgLoader;

    private Map<Integer, LevelFile> mapIdToLevels = new HashMap<>();

    private final DesktopAssetManager am;

    // Map from tilekey to image
    private HashMap<Integer, Image> tileKeyToImageMap = new HashMap<>();

    public BlockGeometryIndex() {
        am = new DesktopAssetManager(true);
        am.registerLoader(LevelLoader.class, "lvl");
        imgLoader = new AWTLoader();

        // Just hard code some stuff for now
        /*
         * this.materialTypes = new MaterialType[]{ null, new MaterialType("palette1",
         * 1, true, false, false), new MaterialType("tunnelbase.lvl", 1, true, false,
         * false) };
         */
        /*
         * this.types = new BlockType[257]; for (int i = 0; i < 64; i++) { types[i + 1]
         * = new BlockType(createFactory(1, i)); }
         */
    }

    private BlockFactory createFactory(int tileKey, int color) {
        int x = color & 0x7;
        int y = (color & 0x38) >> 3;

        float textureScale = 0.125f; // 1/8th
        float halfScale = textureScale * 0.5f;
        float s = x * textureScale + halfScale;
        float t = 1f - (y * textureScale + halfScale);
        // float t = y * 0.125f;

        // log.info("createFactory:: tileKey = "+tileKey);
        InfinityBlockFactory result = InfinityBlockFactory.createCube(0, getMaterialType(tileKey));
        /*
         * for (PartFactory partFactory : result.getDirParts()) { for (GeomPart part :
         * ((DefaultPartFactory) partFactory).getTemplates()) {
         * 
         * // Convenient that we want all texture coordinates to be the same float[]
         * texes = part.getTexCoords(); for (int i = 0; i < texes.length; i += 2) {
         * texes[i] = s; texes[i + 1] = t; } } }
         */
        return result;
    }

    private BlockType getBlockType(int tileKey) {
        BlockType type = tileKeyToBlockTypeMap.get(tileKey);

        if (type == null) {
            type = new BlockType(createFactory(tileKey, 1));

            tileKeyToBlockTypeMap.put(tileKey, type);
        } else {
            // log.info("Found cached BlockType: "+type);
        }
        return type;
    }

    // First type of information:
    private BlockType getBlockType(int tileId, int mapId) {

        // Check to see if we have loaded this map before
        if (!mapIdToLevels.containsKey(mapId)) {
            // TODO: Lookup stringname based on mapId
            String mapName = "aswz/aswz-el-blazer-01.lvl";
            LevelFile level = loadMap("Maps/" + mapName);
            mapIdToLevels.put(mapId, level);
        }

        int tileKey = tileId | (mapId << 8);
        // log.info("getBlockType:: tileKey = " + tileKey + " <= (Tile,Map) = (" +
        // tileId + "," + mapId + ")");

        return getBlockType(tileKey);
    }

    // Second type of info:
    private MaterialType getMaterialType(int tileKey) {

        MaterialType matType = tileKeyToMaterialType.get(tileKey);

        if (matType == null) {

            int tileId = tileKey & TILEID_MASK;
            int mapId = (tileKey & MAPID_MASK) >> 8;
            // TODO: Lookup the levelname, using the id:

            // log.info("getMaterialType:: tileKey = " + tileKey + " => (Tile,Map) = (" +
            // tileId + "," + mapId + ")");

            String mapName = "aswz.lvl";

            matType = new MaterialType(tileKey);

            tileKeyToMaterialType.put(tileKey, matType);
        }

        return matType;
    }

    // Third type of information:
    private Material getMaterial(int tileKey) {
        // int tileKey = tileId | (mapId << 16);
        int tileId = tileKey & TILEID_MASK;
        int mapId = (tileKey & MAPID_MASK) >> 8;

        Material mat = materials.get(tileKey);

        if (mat == null) {
            mat = new Material(am, "MatDefs/BlackTransparentShader.j3md");

            // int key = tileIndex | (mapId << 16);
            Image jmeOutputImage = tileKeyToImageMap.get(tileKey);
            if (jmeOutputImage == null) {
                java.awt.Image awtInputImage = mapIdToLevels.get(mapId).getTiles()[tileId - 1];
                jmeOutputImage = imgLoader.load(toBufferedImage(awtInputImage), true);

                tileKeyToImageMap.put(tileKey, jmeOutputImage);
                // log.info("Put tile: "+tileIndex+" image into map");
            }
            Texture2D tex2D = new Texture2D(jmeOutputImage);
            mat.setTexture("ColorMap", tex2D);
            // mat = globals.createMaterial(texture, false).getMaterial();
            materials.put(tileKey, mat);
            jmeOutputImage.dispose();
        } else {
            // log.info("Found cached material: "+mat+" for tileKey = " + tileKey + " <=
            // (Tile,Map) = (" + tileId + "," + mapId + ")");
        }
        return mat;
    }

    public Node generateBlocks(Node target, CellArray cells) {

        Set<Integer> tileSet = new HashSet<>();
        int count = 0;
        int count2 = 0;
//System.out.println("===============generateBlocks(" + target + ", " + cells + ")");
        long start = System.nanoTime();
        Node result = target;
        result.detachAllChildren();

        DefaultPartBuffer buffer = new DefaultPartBuffer();

        int xSize = cells.getSizeX();
        int ySize = cells.getSizeY();
        int zSize = cells.getSizeZ();

        for (int x = 0; x < xSize; x++) {
            for (int y = 0; y < ySize; y++) {
                for (int z = 0; z < zSize; z++) {
                    int val = cells.getCell(x, y, z);
                    count++;
//System.out.println("[" + x + "][" + y + "][" + z + "] val:" + val);                    
                    int tileId = MaskUtils.getType(val) & 0x000000ff;

                    if (tileId == 0) {
                        // log.info("TileID == 0 for val = " + val + " => (Tile) = (" + tileId + ") -
                        // Coords: ["+x+", "+y+", "+z+"]");
                        continue;
                    }
                    tileSet.add(tileId);

                    int mapId = (MaskUtils.getType(val) & 0x000fff00) >> 8;

                    Vector3f targetLoc = target.getWorldTranslation();
                    Vector3f worldLoc = targetLoc.add(x, y, z);
                    // log.info("generateBlocks:: val = " + val + " => (Tile,Map) = (" + tileId +
                    // "," + mapId + ") - Coords: ["+worldLoc+"]");

                    BlockType blockType = getBlockType(tileId, mapId);

                    if (blockType == null) {
                        // log.info("BlockType was null for val = " + val + " => (Tile,Map) = (" +
                        // tileId + "," + mapId + ") - Coords: ["+x+", "+y+", "+z+"]");
                        continue;
                    }
                    // No masks for now so we'll force it
                    int lightMask = 0;
                    int sideMask = MaskUtils.getSideMask(val);
                    if (debug && sideMask != 0) {
                        log.info("[" + x + "][" + y + "][" + z + "] val:" + val + " @" + tileId + " #"
                                + Integer.toBinaryString(sideMask));
                    }
                    InfinityBlockFactory.debug = debug;

                    blockType.getFactory().addGeometryToBuffer(buffer, x, y, z, x, y, z, sideMask, lightMask,
                            blockType);
                }
            }
        }
        // log.info("Counted: " + count2 + " cells with value != 0");
        // log.info("Counted: " + tileSet.size() + " different tiles");

        for (DefaultPartBuffer.PartList list : buffer.getPartLists()) {
//            System.out.println("Part list:" + list);

            if (list.list.isEmpty()) {
                // log.info("List.list was empty for buffer: " + buffer.toString() + " and List:
                // " + list);
                continue;
            }

            // We know we have simplified geometry so our mesh generation
            // can also be simplified
            int vertCount = list.vertCount;
            FloatBuffer pos = BufferUtils.createFloatBuffer(vertCount * 3);
            // ByteBuffer texes = BufferUtils.createByteBuffer(vertCount * 2);
            FloatBuffer texes = BufferUtils.createFloatBuffer(vertCount * 2);
            ShortBuffer indexes = BufferUtils.createShortBuffer(list.triCount * 3);
            // ByteBuffer normalIndexes = BufferUtils.createByteBuffer(vertCount);

            // We'll create real normals for now to avoid a custom material
            FloatBuffer norms = BufferUtils.createFloatBuffer(vertCount * 3);

            int baseIndex = 0;
            for (DefaultPartBuffer.PartEntry entry : list.list) {

                int i = entry.i;
                int j = entry.j;
                int k = entry.k;
                GeomPart part = entry.part;

                int dir = part.getDirection();
//System.out.println("dir:" + dir); 
                int size = part.getVertexCount();
                float[] verts = part.getCoords();
                int vIndex = 0;
                int tIndex = 0;
                for (int v = 0; v < size; v++) {
                    float x = verts[vIndex++];
                    float y = verts[vIndex++];
                    float z = verts[vIndex++];
//System.out.println("pos:" + (i + x) + ", " + (j + y) + ", " + (k + z));                      
                    pos.put(i + x);
                    pos.put(j + y);
                    pos.put(k + z);

                    switch (dir) {
                    case 0:
                        norms.put(0).put(0).put(-1);
                        break;
                    case 1:
                        norms.put(0).put(0).put(1);
                        break;
                    case 2:
                        norms.put(1).put(0).put(0);
                        break;
                    case 3:
                        norms.put(-1).put(0).put(0);
                        break;
                    case 4:
                        norms.put(0).put(1).put(0);
                        break;
                    default:
                    case 5:
                        norms.put(0).put(-1).put(0);
                        break;
                    }
                }

                float[] texArray = part.getTexCoords();
                for (int t = 0; t < texArray.length; t++) {
                    if (texArray[t] < 0 || texArray[t] > 1) {
                        throw new RuntimeException(
                                "Entry has out of bounds texcoord:" + texArray[t] + " type:" + part.getMaterialType());
                    }
                    // texes.put((byte)(texArray[t] * 255));
                    texes.put(texArray[t]);
                }

                // The indexes need to be offset also
                for (short s : part.getIndexes()) {
                    indexes.put((short) (baseIndex + s));
                }
                baseIndex += size;
            }
//System.out.println("Index count:" + baseIndex);            

            Mesh mesh = new Mesh();
            mesh.setBuffer(VertexBuffer.Type.Position, 3, pos);
            mesh.setBuffer(VertexBuffer.Type.TexCoord, 2, texes);
            mesh.setBuffer(VertexBuffer.Type.Index, 3, indexes);
            mesh.setBuffer(VertexBuffer.Type.Normal, 3, norms);
            mesh.setStatic();
            mesh.updateBound();

            Quad quad = new Quad(1, 1);
            quad.setBuffer(VertexBuffer.Type.Position, 3, pos);
            quad.setBuffer(VertexBuffer.Type.Index, 3, indexes);
            quad.setBuffer(VertexBuffer.Type.Normal, 3, norms);
            quad.setBuffer(VertexBuffer.Type.TexCoord, 2, texes);
            quad.setStatic();
            quad.updateBound();

            // Geometry geom = new Geometry("mesh:" + list.materialType + ":" +
            // list.primitiveType, mesh);
            Geometry geom = new Geometry("mesh:" + list.materialType + ":" + list.primitiveType, quad);
            geom.setMaterial(getMaterial(list.materialType.getId()));
            result.attachChild(geom);
            count++;
        }

        // log.info("Counted: " + count + " meshes added to world");
        long end = System.nanoTime();
//        System.out.println("Generated in:" + ((end - start)/1000000.0) + " ms");
        return result;
    }

    // Subspace infinity version, we dont want to re-calculate below or above the
    // layer
    public static void recalculateSideMasks(CellData data, int x, int y, int z) {
        int xStart = x;
        int yStart = Math.max(0, y); // y is 0 to infinity
        int zStart = z;
        int xEnd = x;
        int yEnd = y;
        int zEnd = z;

        for (x = xStart; x <= xEnd; x++) {
            for (y = yStart; y <= yEnd; y++) {
                for (z = zStart; z <= zEnd; z++) {
                    int val = data.getCell(x, y, z);
                    int tileId = MaskUtils.getType(val);
//log.info("  [" + x + "][" + y + "][" + z + "] = " + val + " @" + type + " #" + Integer.toBinaryString(getSideMask(val)));                
                    // if( type == 0 ) {
                    // // 0 is always empty space
                    // continue;
                    // }
                    // But we can't skip it completely because the existing
                    // mask might still be wrong.
                    /*
                     * BlockType type = types[val]; if( type == null ) { continue; }
                     */
                    int sideMask = 0;

                    // 0 is always empty space so we only need to recalculate
                    // a mask if there is a thing there... but we still want to
                    // set an empty mask back in case it wasn't empty before.
                    // Note: this shouldn't happen in practice because of how we
                    // set the cell data to the raw type before applying side masks
                    // but it's better to be correct just in case... and costs us
                    // nothing.
                    if (tileId != 0) {
                        // Calculate the mask right here
                        for (Direction dir : Direction.values()) {
                            int next = MaskUtils.getType(data.getCell(x, y, z, dir, 0));
//log.info("    " + dir + " -> " + next);
                            // Just a simple check for now
                            if (next == 0) {
                                // It's empty so we emit a face in that direction
                                sideMask = sideMask | dir.getBitMask();
                            }
                        }
                    }
//log.info("    result:" + setSideMask(val, sideMask) + "   sides:" + Integer.toBinaryString(sideMask));                    
                    data.setCell(x, y, z, MaskUtils.setSideMask(val, sideMask));
                }
            }
        }

    }

    protected LevelFile loadMap(String tileSet) {
        LevelFile localMap = (LevelFile) am.loadAsset(tileSet);

        return localMap;
    }

    /**
     * Converts a given Image into a BufferedImage
     *
     * @param img The Image to be converted
     * @return The converted BufferedImage
     */
    private BufferedImage toBufferedImage(java.awt.Image img) {
        if (img instanceof BufferedImage) {
            return (BufferedImage) img;
        }

        int width = img.getWidth(null);
        int height = img.getHeight(null);

        // Create a buffered image with transparency
        BufferedImage bimage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

        // Draw the image on to the buffered image
        Graphics2D bGr = bimage.createGraphics();
        bGr.drawImage(img, 0, 0, null); // No flip
        // bGr.drawImage(img, 0 + width, 0, -width, height, null); //Horisontal flip
        // bGr.drawImage(img, 0, 0 + height, width, -height, null); //Vertical flip
        // bGr.drawImage(img, height, 0, -width, height, null);

        bGr.dispose();

        AffineTransform tx = AffineTransform.getScaleInstance(-11, -1);
        tx.translate(-bimage.getWidth(null), -bimage.getHeight(null));
        AffineTransformOp op = new AffineTransformOp(tx, AffineTransformOp.TYPE_NEAREST_NEIGHBOR);
        bimage = op.filter(bimage, null);

        // Return the buffered image
        return bimage;
    }

    /**
     * This array is used to define the quad bounds in the right order. Its
     * important relative to where the camera is and what facing the camera has
     *
     * @param halfSize
     * @return array
     */
    private float[] getVertices(float halfSize) {
        float[] res = new float[] { halfSize, 0, -halfSize, -halfSize, 0, -halfSize, -halfSize, 0, halfSize, halfSize,
                0, halfSize };
        return res;
    }

    /**
     * This will create the normals that is point in the z unit vector direction.
     * This is used in relation to the lighting on the quad (towards camera)
     *
     * @return float array containing the right normals
     */
    private float[] getNormals() {
        float[] normals;
        normals = new float[] { 0, 1, 0, 0, 1, 0, 0, 1, 0, 0, 1, 0 };
        return normals;
    }
}
