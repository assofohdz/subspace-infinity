// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.client.states;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.simsilica.mathd.Vec3d;
import org.junit.Test;

/**
 * Unit coverage for the pure-logic helpers in {@link RadarStateLogic}.
 *
 * <p>Today this exercises {@code pointInPolygon} — the per-frame
 * "is the avatar inside this arena footprint?" test that drives the radar's
 * mute / un-mute branch. The visual rendering output (full-strength vs
 * muted theme colours) is deferred to manual smoke-test per the slice
 * spec, but the polygon math is the most likely source of a regression
 * and is trivially unit-testable.
 */
public class RadarStateLogicTest {

    /** 100x100 axis-aligned square at the origin — the canonical arena rectangle case. */
    private static final Vec3d[] SQUARE = new Vec3d[] {
        new Vec3d(0, 0, 0),
        new Vec3d(100, 0, 0),
        new Vec3d(100, 0, 100),
        new Vec3d(0, 0, 100),
    };

    @Test
    public void pointInPolygon_pointInsideSquare_returnsTrue() {
        assertTrue("centre of the square is inside",
            RadarStateLogic.pointInPolygon(50.0, 50.0, SQUARE));
    }

    @Test
    public void pointInPolygon_pointOutsideSquare_returnsFalse() {
        assertFalse("point left of square",
            RadarStateLogic.pointInPolygon(-10.0, 50.0, SQUARE));
        assertFalse("point above square (Z > maxZ)",
            RadarStateLogic.pointInPolygon(50.0, 200.0, SQUARE));
        assertFalse("point right of square",
            RadarStateLogic.pointInPolygon(150.0, 50.0, SQUARE));
        assertFalse("point below square (Z < minZ)",
            RadarStateLogic.pointInPolygon(50.0, -10.0, SQUARE));
    }

    @Test
    public void pointInPolygon_yCoordIgnored_radarIsTopDown() {
        // The radar is a top-down X-Z view; Y should not influence the test.
        // verts[*].y is 0 here, but the avatar's Y in production is non-zero.
        // The helper takes only x + z, so this is by construction — the test
        // documents the contract for future readers.
        assertTrue("X-Z coordinates inside square; Y is irrelevant",
            RadarStateLogic.pointInPolygon(50.0, 50.0, SQUARE));
    }

    @Test
    public void pointInPolygon_nullVerts_returnsFalse() {
        assertFalse("null polygon is not 'containing' anything",
            RadarStateLogic.pointInPolygon(0.0, 0.0, null));
    }

    @Test
    public void pointInPolygon_fewerThanThreeVerts_returnsFalse() {
        final Vec3d[] degenerate = new Vec3d[] {
            new Vec3d(0, 0, 0),
            new Vec3d(10, 0, 0),
        };
        assertFalse("a 2-vertex polygon is degenerate",
            RadarStateLogic.pointInPolygon(5.0, 0.0, degenerate));

        assertFalse("a zero-vertex polygon is degenerate",
            RadarStateLogic.pointInPolygon(5.0, 0.0, new Vec3d[0]));
    }

    @Test
    public void pointInPolygon_concaveLShape_correctlyExcludesNotch() {
        // L-shaped polygon — concave. Verifies the ray-casting algorithm is
        // not degenerating to an axis-aligned bounding-box test.
        //
        //   (0,30) ┌──────┐ (30,30)
        //          │      │
        //   (0,20) │  ┌───┘ (20,20)
        //          │  │
        //          └──┘
        //   (0,0)    (10,0)
        final Vec3d[] lShape = new Vec3d[] {
            new Vec3d(0, 0, 0),
            new Vec3d(10, 0, 0),
            new Vec3d(10, 0, 20),
            new Vec3d(20, 0, 20),
            new Vec3d(20, 0, 30),  // <-- adjusted: the corner at (30,30) becomes (20,30) clarifying L-shape
            new Vec3d(0, 0, 30),
        };
        assertTrue("inside the vertical leg of the L",
            RadarStateLogic.pointInPolygon(5.0, 10.0, lShape));
        assertTrue("inside the horizontal top of the L",
            RadarStateLogic.pointInPolygon(15.0, 25.0, lShape));
        assertFalse("inside the bounding box but outside the L's notch",
            RadarStateLogic.pointInPolygon(15.0, 10.0, lShape));
    }
}
