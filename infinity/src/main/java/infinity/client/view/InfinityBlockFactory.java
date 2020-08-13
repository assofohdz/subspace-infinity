/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package infinity.client.view;

import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.MoreObjects;

import com.simsilica.mathd.Vec3d;
import com.simsilica.mblock.Axis;
import com.simsilica.mblock.BlockType;
import com.simsilica.mblock.Direction;
import com.simsilica.mblock.geom.BlockFactory;
import com.simsilica.mblock.geom.BoundaryShape;
import com.simsilica.mblock.geom.BoundaryShapes;
import com.simsilica.mblock.geom.DefaultPartFactory;
import com.simsilica.mblock.geom.GeomPartBuffer;
import com.simsilica.mblock.geom.MaterialType;
import com.simsilica.mblock.geom.PartFactory;

/**
 *
 * @author AFahrenholz
 */
public class InfinityBlockFactory implements BlockFactory {
    static final long serialVersionUID = 42L;

    static Logger log = LoggerFactory.getLogger(InfinityBlockFactory.class);

    public static boolean debug = false;

    private final PartFactory[] dirParts;
    private final PartFactory internalParts;
    private final boolean allSolid;
    private final boolean[] solid;
    private final double[] transparency;
    private final boolean isTransparent;
    private final double volume;
    private final Vec3d min;
    private final Vec3d max;
    // private Collider collider;

    /**
     * Creates a fully specified DefaultBlockFactory with the supplied parameters.
     * If the solid[] array is null then all directions are considered solid. If the
     * transparency array is null then all directions are considered transparency =
     * 0, ie: fully opaque.
     */
    public InfinityBlockFactory(final PartFactory[] dirParts, final PartFactory internalParts, final boolean[] solid,
            final double[] transparency, final double volume, final Vec3d min, final Vec3d max) {
        this.dirParts = dirParts;
        this.internalParts = internalParts;
        this.solid = solid;
        this.transparency = transparency;
        this.volume = volume;
        this.min = min;
        this.max = max;

        boolean all = true;
        if (solid != null) {
            for (final boolean b : solid) {
                if (!b) {
                    all = false;
                }
            }
        }
        allSolid = all;
        if (transparency == null) {
            isTransparent = false;
        } else {
            isTransparent = (transparency[0] + transparency[1] + transparency[2]) != 0;
        }
    }

    /**
     * Constructs a block factory that creates a regular cube with the overall
     * specified transparency. It is assumed that all of the part factories are full
     * size and that the min is 0,0,0 and the max is 1,1,1, etc.
     */
    public static InfinityBlockFactory createCube(final double transparency, final PartFactory... dirParts) {
        if (dirParts.length != Direction.values().length) {
            throw new IllegalArgumentException("Incorrect number of part factories:" + dirParts.length + ", requires:"
                    + Direction.values().length);
        }
        double[] trans = null;
        if (transparency != 0) {
            trans = new double[] { transparency, transparency, transparency };
        }
        return new InfinityBlockFactory(dirParts, null, null, trans, 1, new Vec3d(0, 0, 0), new Vec3d(1, 1, 1));
    }

    /**
     * Constructs a block factory that creates a regular cube with the overall
     * specified transparency and PartFactories created by calling
     * DefaultPartFactory.createFace() all using the specified material type.
     */
    public static InfinityBlockFactory createCube(final double transparency, final MaterialType materialType) {
        return createCube(transparency, DefaultPartFactory.createCubeFace(materialType, Direction.North),
                DefaultPartFactory.createCubeFace(materialType, Direction.South),
                DefaultPartFactory.createCubeFace(materialType, Direction.East),
                DefaultPartFactory.createCubeFace(materialType, Direction.West),
                DefaultPartFactory.createCubeFace(materialType, Direction.Up),
                DefaultPartFactory.createCubeFace(materialType, Direction.Down));
    }

    /**
     * Constructs a block factory that creates a regular cube with the overall
     * specified transparency and PartFactories created by calling
     * DefaultPartFactory.createFace() with each of the specified material types.
     */
    public static InfinityBlockFactory createCube(final double transparency,
            @SuppressWarnings("unused") final int tileId, @SuppressWarnings("unused") final int mapId,
            final MaterialType... materialTypes) {
        return createCube(transparency,
                DefaultPartFactory.createCubeFace(materialTypes[Direction.North.ordinal()], Direction.North),
                DefaultPartFactory.createCubeFace(materialTypes[Direction.South.ordinal()], Direction.South),
                DefaultPartFactory.createCubeFace(materialTypes[Direction.East.ordinal()], Direction.East),
                DefaultPartFactory.createCubeFace(materialTypes[Direction.West.ordinal()], Direction.West),
                DefaultPartFactory.createCubeFace(materialTypes[Direction.Up.ordinal()], Direction.Up),
                DefaultPartFactory.createCubeFace(materialTypes[Direction.Down.ordinal()], Direction.Down));
    }

    /**
     * Constructs a new block factory, calculating the min/max, volume,
     * transparency, and solid values based on the supplied part factories.
     */
    public static InfinityBlockFactory create(final PartFactory[] dirParts, final PartFactory internalParts,
            final int tileId, final int mapId) {
        return create(dirParts, internalParts, null, tileId, mapId);
    }

    /**
     * Constructs a new block factory, calculating the min/max, volume, and solid
     * values based on the supplied part factories.
     */
    public static InfinityBlockFactory create(final PartFactory[] dirParts, final PartFactory internalParts,
            final double[] transparency, @SuppressWarnings("unused") final int tileId,
            @SuppressWarnings("unused") final int mapId) {

        if (dirParts == null && internalParts == null) {
            throw new IllegalArgumentException("dirParts and internalParts cannot both be null");
        }

        final boolean[] solid = new boolean[Direction.values().length];
        final double[] newTrans = transparency == null ? new double[] { 1, 1, 1 } : null;

        final Vec3d min = new Vec3d(100, 100, 100); // just needs to be relatively big
        final Vec3d max = new Vec3d(-100, -100, -100); // just needs to be relatively small

        if (dirParts != null) {
            for (final Direction dir : Direction.values()) {
                final PartFactory face = dirParts[dir.ordinal()];
                if (face == null) {
                    continue;
                }
                min.minLocal(face.getMin());
                max.maxLocal(face.getMax());
                final BoundaryShape shape = face.getBoundaryShape();
                final double area = shape.getArea();
                if (area >= 1) {
                    solid[dir.ordinal()] = true;
                }

                if (newTrans != null) {
                    final double t = Math.max(0, 1 - area);
                    final int a = dir.getAxis().ordinal();
                    newTrans[a] = Math.min(newTrans[a], t);
                }
            }
        }

        if (internalParts != null) {
            min.minLocal(internalParts.getMin());
            max.maxLocal(internalParts.getMax());
        }

        final double x = max.x - min.x;
        final double y = max.y - min.y;
        final double z = max.z - min.z;

        final double volume = x * y * z;

        return new InfinityBlockFactory(dirParts, internalParts, solid, newTrans != null ? newTrans : transparency,
                volume, min, max);
    }

    public PartFactory[] getDirParts() {
        return dirParts;
    }

    @Override
    public int addGeometryToBuffer(final GeomPartBuffer buffer, final int i, final int j, final int k, final int xWorld,
            final int yWorld, final int zWorld, final int sideMask, final int lightMask, final BlockType type) {

        if (yWorld == 1) {
            return 0;
        }

        int count = 0;

        if (dirParts != null) {
            for (final Direction dir : Direction.values()) {

                /*
                 * if (yWorld == 0 && dir.compareTo(Direction.Up) != 0) { continue; }
                 */
                final PartFactory part = dirParts[dir.ordinal()];
                if (part == null || (sideMask & dir.getBitMask()) == 0) {
                    continue;
                }
                if (debug) {
                    log.info("add part for:" + dir);
                }
                count += part.addParts(buffer, i, j, k, xWorld, yWorld, zWorld, type, dir);
            }
        }

        if (internalParts != null) {
            count += internalParts.addParts(buffer, i, j, k, xWorld, yWorld, zWorld, type, null);
        }

        return count;
    }

    @Override
    public final BoundaryShape getShape(final Direction dir) {
        final PartFactory factory = dirParts[dir.ordinal()];
        if (factory == null) {
            return BoundaryShapes.NULL_SHAPE;
        }
        return factory.getBoundaryShape();
    }

    @Override
    public final boolean isSolid(final Direction dir) {
        return solid == null ? true : solid[dir.ordinal()];
    }

    @Override
    public final boolean isSolid() {
        return allSolid;
    }

    @Override
    public final double getTransparency(final Axis axis) {
        return transparency == null ? 0 : transparency[axis.ordinal()];
    }

    @Override
    public final boolean isTransparent() {
        return isTransparent;
    }

    @Override
    public final double getVolume() {
        return volume;
    }

    @Override
    public final Vec3d getMin() {
        return min;
    }

    @Override
    public final Vec3d getMax() {
        return max;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(getClass().getSimpleName()).add("dirParts", Arrays.asList(dirParts))
                .add("internalParts", internalParts).add("min", min).add("max", max).toString();
    }
}
