// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.client.view;


import com.simsilica.mblock.geom.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import com.simsilica.mathd.Vec3d;
import com.simsilica.mblock.BlockType;
import com.simsilica.mblock.Direction;

/** Infinity-flavoured {@link DefaultBlockFactory} — skips face emission at world Y=1 (collision plane). */
public class InfinityBlockFactory extends DefaultBlockFactory {
    static final long serialVersionUID = 42L;

    static Logger log = LoggerFactory.getLogger(InfinityBlockFactory.class);

    public static final boolean DEBUG = false;

    private final PartFactory[] dirParts;
    private final PartFactory internalParts;

    /** {@code solid == null} → all faces solid; {@code transparency == null} → fully opaque. */
    public InfinityBlockFactory(final PartFactory[] dirParts, final PartFactory internalParts, final boolean[] solid,
            final double[] transparency, final double volume, final Vec3d min, final Vec3d max) {
        super(dirParts, internalParts, solid, transparency, volume, min, max);

        this.dirParts = dirParts;
        this.internalParts = internalParts;
    }

    /** Builds a cube; part factories must be full-size with bounds [0,0,0]..[1,1,1]. */
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

    /** Cube using {@code materialType} for every face. */
    public static InfinityBlockFactory createCube(final double transparency, final MaterialType materialType) {
        return createCube(transparency, DefaultPartFactory.createCubeFace(materialType, Direction.North),
                DefaultPartFactory.createCubeFace(materialType, Direction.South),
                DefaultPartFactory.createCubeFace(materialType, Direction.East),
                DefaultPartFactory.createCubeFace(materialType, Direction.West),
                DefaultPartFactory.createCubeFace(materialType, Direction.Up),
                DefaultPartFactory.createCubeFace(materialType, Direction.Down));
    }

    /** Cube using a different material type per face (indexed by {@link Direction#ordinal()}). */
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

    public static InfinityBlockFactory create(final PartFactory[] dirParts, final PartFactory internalParts,
            final int tileId, final int mapId) {
        return create(dirParts, internalParts, null, tileId, mapId);
    }

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
                accumulateFaceContribution(dirParts[dir.ordinal()], dir, solid, min, max, newTrans);
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

    private static void accumulateFaceContribution(
            final PartFactory face,
            final Direction dir,
            final boolean[] solid,
            final Vec3d min,
            final Vec3d max,
            final double[] newTrans) {
        if (face == null) {
            return;
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

    public PartFactory[] getDirParts() {
        return dirParts;
    }

    // Mirrors the Moss BlockFactory.addGeometryToBuffer shape (lightMask in place of CellData) — kept for API symmetry.
    @SuppressWarnings("PMD.ExcessiveParameterList")
    public int addGeometryToBuffer(final GeomPartBuffer buffer, final int i, final int j, final int k, final int xWorld,
            final int yWorld, final int zWorld, final int sideMask, final int lightMask, final BlockType type) {

        if (yWorld == 1) {
            return 0;
        }

        int count = 0;

        if (dirParts != null) {
            for (final Direction dir : Direction.values()) {
                final PartFactory part = dirParts[dir.ordinal()];
                if (part == null || (sideMask & dir.getBitMask()) == 0) {
                    continue;
                }
                if (DEBUG) {
                    log.info("add part for:{}", dir);
                }
                count += part.addParts(buffer, i, j, k, xWorld, yWorld, zWorld, type, dir);
            }
        }

        if (internalParts != null) {
            count += internalParts.addParts(buffer, i, j, k, xWorld, yWorld, zWorld, type, null);
        }

        return count;
    }
}
