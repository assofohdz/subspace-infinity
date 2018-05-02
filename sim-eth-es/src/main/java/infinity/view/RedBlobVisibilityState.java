/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package example.view;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.jme3.math.LineSegment;
import com.jme3.math.Rectangle;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.scene.shape.Quad;
import com.simsilica.es.Entity;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntitySet;
import example.ConnectionState;
import example.es.Occluder;
import example.es.Position;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.ListIterator;

/**
 *
 * @author Asser
 */
public class RedBlobVisibilityState extends BaseAppState {

    private EntityData ed;
    private EntitySet occluders;

    @Override
    protected void initialize(Application app) {
        segments = new LinkedList<>();
        endpoints = new LinkedList<>();
        open = new LinkedList<>();
        center = new Vector2f();
        output = new LinkedList<>();
        demo_intersectionsDetected = new ArrayList<>();
        this.ed = getState(ConnectionState.class).getEntityData();
        this.occluders = ed.getEntities(Occluder.class, Position.class);
    }

    @Override
    protected void cleanup(Application app) {
        this.occluders.release();
        this.occluders = null;
    }

    @Override
    protected void onEnable() {

    }

    @Override
    protected void onDisable() {

    }

    @Override
    public void update(float tpf) {
        this.occluders.applyChanges();

        if (this.occluders.hasChanges()) {
            for (Entity e : this.occluders.getAddedEntities()) {

            }
            for (Entity e : this.occluders.getChangedEntities()) {

            }
            for (Entity e : this.occluders.getRemovedEntities()) {

            }

            //Map changed, reload
            //this.loadMap(tpf, tpf, blocks, walls);
        }

    }

    private class EndPoint {

        final float x, y;
        boolean begin = false;
        RedBlobSegment segment;
        float angle;
        boolean visualize = false;

        public EndPoint(float x, float y) {
            this.x = x;
            this.y = y;
        }

        public boolean isBegin() {
            return begin;
        }

        public void setBegin(boolean begin) {
            this.begin = begin;
        }

        public RedBlobSegment getSegment() {
            return segment;
        }

        public void setSegment(RedBlobSegment segment) {
            this.segment = segment;
        }

        public float getAngle() {
            return angle;
        }

        public void setAngle(float angle) {
            this.angle = angle;
        }

        public boolean isVisualize() {
            return visualize;
        }

        public void setVisualize(boolean visualize) {
            this.visualize = visualize;
        }
    }

    /* 2d visibility algorithm, for demo
   Usage:
      Instance the appstate
   Whenever map data changes:
      appstate.loadMap
   Whenever light source changes:
      setLightLocation
   To calculate the area:
      sweep
     */
    // These represent the map and the light location:    
    LinkedList<RedBlobSegment> segments;
    LinkedList<EndPoint> endpoints;
    Vector2f center;

    // These are currently 'open' line segments, sorted so that the nearest
    // segment is first. It's used only during the sweep algorithm, and exposed
    // as a public field here so that the demo can display it.
    LinkedList<RedBlobSegment> open;

    // The output is a series of points that forms a visible area polygon
    LinkedList<Vector2f> output;

    // For the demo, keep track of wall intersections
    ArrayList<ArrayList<Vector2f>> demo_intersectionsDetected;

    // Construct an empty visibility set
    public RedBlobVisibilityState() {
    }

    // Add a segment, where the first point shows up in the
    // visualization but the second one does not. (Every endpoint is
    // part of two segments, but we want to only show them once.)
    private void addSegment(float x1, float y1, float x2, float y2) {
        EndPoint p1 = new EndPoint(x1, y1);
        EndPoint p2 = new EndPoint(x2, y2);

        //RedBlobSegment segment = new RedBlobSegment(new Vector3f(p1.x, p1.y, 0), new Vector3f(p2.x, p2.y, 0));
        RedBlobSegment segment = new RedBlobSegment(p1, p2);

        p1.segment = segment;
        p1.visualize = true;
        p2.segment = segment;
        p2.visualize = false;

        p1.segment = segment;
        p2.segment = segment;

        segments.add(segment);
        endpoints.add(p1);
        endpoints.add(p2);
    }

// Helper function to construct segments along the outside perimeter
    private void loadEdgeOfMap(float size, float margin) {
        addSegment(margin, margin, margin, size - margin);
        addSegment(margin, size - margin, size - margin, size - margin);
        addSegment(size - margin, size - margin, size - margin, margin);
        addSegment(size - margin, margin, margin, margin);
        // NOTE: if using the simpler distance function (a.d < b.d)
        // then we need segments to be similarly sized, so the edge of
        // the map needs to be broken up into smaller segments.
    }

// Load a set of square blocks, plus any other line segments
    public void loadMap(float size, float margin, ArrayList<Rectangle> blocks, ArrayList<RedBlobSegment> walls) {

        segments.clear();
        endpoints.clear();
        loadEdgeOfMap(size, margin);

        for (Rectangle r : blocks) {
            //A to B
            addSegment(r.getA().x, r.getA().y, r.getB().x, r.getB().y);
            //B to C
            addSegment(r.getB().x, r.getB().y, r.getC().x, r.getC().y);

            Vector3f d = (r.getB().add(r.getC())).subtract(r.getA());

            //C to D
            addSegment(r.getC().x, r.getC().y, d.x, d.y);
            //D to A
            addSegment(d.x, d.y, r.getA().x, r.getA().y);
        }

        for (RedBlobSegment segment : walls) {
            addSegment(segment.getOrigin().x, segment.getOrigin().y, segment.getEnd().x, segment.getEnd().y);
        }
    }

    private class RedBlobSegment {

        private EndPoint p2;
        private EndPoint p1;
        private double d;

        public RedBlobSegment(EndPoint start, EndPoint end) {
            this.p2 = end;
            this.p1 = start;
        }

        public EndPoint getEnd() {
            return p2;
        }

        public double getD() {
            return d;
        }

        public void setD(float d) {
            this.d = d;
        }

        public EndPoint getOrigin() {
            return p1;
        }
    }

    // Set the light location. Segment and EndPoint data can't be
    // processed until the light location is known.
    public void setLightLocation(float x, float y) {
        this.center.x = x;
        this.center.y = y;

        for (RedBlobSegment segment : segments) {
            double dx = 0.5 * (segment.getOrigin().x + segment.getEnd().x) - x;
            double dy = 0.5 * (segment.getOrigin().y + segment.getEnd().y) - y;
            // NOTE: we only use this for comparison so we can use
            // distance squared instead of distance. However in
            // practice the sqrt is plenty fast and this doesn't
            // really help in this situation.
            segment.d = dx * dx + dy * dy;

            // NOTE: future optimization: we could record the quadrant
            // and the y/x or x/y ratio, and sort by (quadrant,
            // ratio), instead of calling atan2. See
            // <https://github.com/mikolalysenko/compare-slope> for a
            // library that does this. Alternatively, calculate the
            // angles and use bucket sort to get an O(N) sort.
            segment.p1.angle = (float) Math.atan2(segment.p1.y - y, segment.p1.x - x);
            segment.p2.angle = (float) Math.atan2(segment.p2.y - y, segment.p2.x - x);

            float dAngle = segment.p2.angle - segment.p1.angle;
            if (dAngle <= -Math.PI) {
                dAngle += 2 * Math.PI;
            }
            if (dAngle > Math.PI) {
                dAngle -= 2 * Math.PI;
            }
            segment.p1.begin = (dAngle > 0.0);
            segment.p2.begin = !segment.p1.begin;
        }
    }

    class EndPointComparator implements Comparator<EndPoint> {

        @Override
        public int compare(EndPoint a, EndPoint b) {
            // Traverse in angle order
            if (a.angle > b.angle) {
                return 1;
            }
            if (a.angle < b.angle) {
                return -1;
            }
            // But for ties (common), we want Begin nodes before End nodes
            if (!a.begin && b.begin) {
                return 1;
            }
            if (a.begin && !b.begin) {
                return -1;
            }
            return 0;
        }
    }

    // Helper: leftOf(segment, point) returns true if point is "left"
    // of segment treated as a vector. Note that this assumes a 2D
    // coordinate system in which the Y axis grows downwards, which
    // matches common 2D graphics libraries, but is the opposite of
    // the usual convention from mathematics and in 3D graphics
    // libraries.
    private boolean leftOf(RedBlobSegment s, Vector2f p) {
        // This is based on a 3d cross product, but we don't need to
        // use z coordinate inputs (they're 0), and we only need the
        // sign. If you're annoyed that cross product is only defined
        // in 3d, see "outer product" in Geometric Algebra.
        // <http://en.wikipedia.org/wiki/Geometric_algebra>
        float cross = (s.p2.x - s.p1.x) * (p.y - s.p1.y)
                - (s.p2.y - s.p1.y) * (p.x - s.p1.x);
        return cross < 0;
        // Also note that this is the naive version of the test and
        // isn't numerically robust. See
        // <https://github.com/mikolalysenko/robust-arithmetic> for a
        // demo of how this fails when a point is very close to the
        // line.
    }

    // Helper: do we know that segment a is in front of b?
    // Implementation not anti-symmetric (that is to say,
    // _segment_in_front_of(a, b) != (!_segment_in_front_of(b, a)).
    // Also note that it only has to work in a restricted set of cases
    // in the visibility algorithm; I don't think it handles all
    // cases. See http://www.redblobgames.com/articles/visibility/segment-sorting.html
    private boolean segment_in_front_of(RedBlobSegment a, RedBlobSegment b, Vector2f relativeTo) {
        // NOTE: we slightly shorten the segments so that
        // intersections of the endpoints (common) don't count as
        // intersections in this algorithm

        Vector2f vecB1 = new Vector2f(b.p1.x, b.p1.y);
        Vector2f vecB2 = new Vector2f(b.p2.x, b.p2.y);

        Vector2f vecA1 = new Vector2f(a.p1.x, a.p1.y);
        Vector2f vecA2 = new Vector2f(a.p2.x, a.p2.y);

        boolean A1 = leftOf(a, vecB1.interpolateLocal(vecB2, 0.01f));
        boolean A2 = leftOf(a, vecB2.interpolateLocal(vecB1, 0.01f));
        boolean A3 = leftOf(a, relativeTo);
        boolean B1 = leftOf(b, vecA1.interpolateLocal(vecA2, 0.01f));
        boolean B2 = leftOf(b, vecA2.interpolateLocal(vecA1, 0.01f));
        boolean B3 = leftOf(b, relativeTo);

        // NOTE: this algorithm is probably worthy of a short article
        // but for now, draw it on paper to see how it works. Consider
        // the line A1-A2. If both B1 and B2 are on one side and
        // relativeTo is on the other side, then A is in between the
        // viewer and B. We can do the same with B1-B2: if A1 and A2
        // are on one side, and relativeTo is on the other side, then
        // B is in between the viewer and A.
        if (B1 == B2 && B2 != B3) {
            return true;
        }
        if (A1 == A2 && A2 == A3) {
            return true;
        }
        if (A1 == A2 && A2 != A3) {
            return false;
        }
        if (B1 == B2 && B2 == B3) {
            return false;
        }
        // If A1 != A2 and B1 != B2 then we have an intersection.
        // Expose it for the GUI to show a message. A more robust
        // implementation would split segments at intersections so
        // that part of the segment is in front and part is behind.
        ArrayList<Vector2f> arrayList = new ArrayList<>();
        arrayList.add(new Vector2f(a.p1.x, a.p1.y));
        arrayList.add(new Vector2f(a.p2.x, a.p2.y));
        arrayList.add(new Vector2f(b.p1.x, b.p1.y));
        arrayList.add(new Vector2f(b.p2.x, b.p2.y));
        demo_intersectionsDetected.add(arrayList);
        return false;

        // NOTE: previous implementation was a.d < b.d. That's simpler
        // but trouble when the segments are of dissimilar sizes. If
        // you're on a grid and the segments are similarly sized, then
        // using distance will be a simpler and faster implementation.
    }

    // Run the algorithm, sweeping over all or part of the circle to find
    // the visible area, represented as a set of triangles
    public void sweep(float maxAngle) {
        output = new LinkedList<>();  // output set of triangles
        demo_intersectionsDetected = new ArrayList<>();

        endpoints.sort(new EndPointComparator());
        open.clear();

        float beginAngle = 0f;

        // At the beginning of the sweep we want to know which
        // segments are active. The simplest way to do this is to make
        // a pass collecting the segments, and make another pass to
        // both collect and process them. However it would be more
        // efficient to go through all the segments, figure out which
        // ones intersect the initial sweep line, and then sort them.
        for (int pass = 0; pass < 2; pass++) {
            for (EndPoint p : endpoints) {
                if (pass == 1 && p.angle > maxAngle) {
                    // Early exit for the visualization to show the sweep process
                    break;
                }

                RedBlobSegment current_old = open.isEmpty() ? null : open.getFirst();

                if (p.begin) {
                    // Insert into the right place in the list
                    ListIterator<RedBlobSegment> openIterator = open.listIterator();
                    RedBlobSegment node = openIterator.next();
                    while (node != null && segment_in_front_of(p.segment, node, center)) {
                        node = openIterator.next();
                    }

                    if (node == null) {
                        open.add(p.segment);
                    } else {
                        open.add(open.indexOf(p), p.segment);
                    }
                } else {
                    open.remove(p.segment);
                }

                RedBlobSegment current_new = open.isEmpty() ? null : open.getFirst();
                if (current_old != current_new) {
                    if (pass == 1) {
                        addTriangle(beginAngle, p.angle, current_old);
                    }
                    beginAngle = p.angle;
                }
            }
        }
    }

    public Vector2f lineIntersection(Vector2f p1, Vector2f p2, Vector2f p3, Vector2f p4) {
        // From http://paulbourke.net/geometry/lineline2d/
        float s = ((p4.x - p3.x) * (p1.y - p3.y) - (p4.y - p3.y) * (p1.x - p3.x))
                / ((p4.y - p3.y) * (p2.x - p1.x) - (p4.x - p3.x) * (p2.y - p1.y));
        return new Vector2f(p1.x + s * (p2.x - p1.x), p1.y + s * (p2.y - p1.y));
    }

    private void addTriangle(float angle1, float angle2, RedBlobSegment segment) {
        Vector2f p1 = center;
        Vector2f p2 = new Vector2f(center.x + (float) Math.cos(angle1), center.y + (float) Math.sin(angle1));
        Vector2f p3 = new Vector2f();
        Vector2f p4 = new Vector2f();

        if (segment != null) {
            // Stop the triangle at the intersecting segment
            p3.x = segment.p1.x;
            p3.y = segment.p1.y;
            p4.x = segment.p2.x;
            p4.y = segment.p2.y;
        } else {
            // Stop the triangle at a fixed distance; this probably is
            // not what we want, but it never gets used in the demo
            p3.x = (float) (center.x + Math.cos(angle1) * 500);
            p3.y = (float) (center.y + Math.sin(angle1) * 500);
            p4.x = (float) (center.x + Math.cos(angle2) * 500);
            p4.y = (float) (center.y + Math.sin(angle2) * 500);
        }

        Vector2f pBegin = lineIntersection(p3, p4, p1, p2);

        p2.x = (float) (center.x + Math.cos(angle2));
        p2.y = (float) (center.y + Math.sin(angle2));
        Vector2f pEnd = lineIntersection(p3, p4, p1, p2);

        output.push(pBegin);
        output.push(pEnd);
    }
}
