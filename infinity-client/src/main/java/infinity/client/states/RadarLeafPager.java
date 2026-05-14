// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.client.states;

import com.jme3.scene.Node;
import com.simsilica.mathd.Vec3d;
import com.simsilica.mathd.Vec3i;
import com.simsilica.mworld.LeafChangeEvent;
import com.simsilica.mworld.LeafChangeListener;
import com.simsilica.mworld.LeafData;
import com.simsilica.mworld.LeafId;
import com.simsilica.mworld.World;
import com.simsilica.mworld.WorldGrids;
import com.simsilica.thread.Job;
import com.simsilica.thread.JobState;
import infinity.client.view.RadarLeafSilhouetteIndex;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Radar's leaf-paging subsystem. Schedules {@link RadarLeafView} jobs against
 * the shared {@link JobState} pools as the avatar's leaf-cell changes; closer
 * leaves served first via {@code x² + z²} priority. Drains
 * {@link World#addLeafChangeListener} events so server-side wall edits requeue
 * the affected leaf at top priority via {@link #drainLeafUpdates}.
 *
 * <p>Subspace is single-leaf in Y so the visible set is a 2D disc (X/Z only);
 * the radius (in leaves) is derived from {@code currentRange} via
 * {@link WorldGrids#LEAF_GRID} spacing — no hand-rolled {@code * 1024}
 * arithmetic, per {@code .claude/rules/world-coordinates.md}.
 */
final class RadarLeafPager {

    private final World world;
    private final JobState workers;
    private final JobState priorityWorkers;
    private final RadarLeafSilhouetteIndex silhouetteIndex;
    private final Node radarBlockRoot;

    private final Map<LeafId, RadarLeafView> radarLeafCache = new HashMap<>();
    private final Queue<LeafId> radarUpdatedLeafIds = new ConcurrentLinkedQueue<>();
    private final RadarLeafObserver radarLeafObserver = new RadarLeafObserver();
    private RadarViewEntry[] radarViewArray;
    /** Sentinel — no real cell will match (Y=100 is above the world). */
    private final Vec3i radarCenterCell = new Vec3i(0, 100, 0);
    private int radarLeafRadius = -1;

    RadarLeafPager(
            final World world,
            final JobState workers,
            final JobState priorityWorkers,
            final RadarLeafSilhouetteIndex silhouetteIndex,
            final Node radarBlockRoot) {
        this.world = world;
        this.workers = workers;
        this.priorityWorkers = priorityWorkers;
        this.silhouetteIndex = silhouetteIndex;
        this.radarBlockRoot = radarBlockRoot;
    }

    /** Register the {@link LeafChangeListener}; invoked once by the host state after construction. */
    void attachLeafObserver() {
        if (world != null) {
            world.addLeafChangeListener(radarLeafObserver);
        }
    }

    /** Drain queued leaf-change events; requeue affected leaves at top priority (canonical poll-drain idiom). */
    @SuppressWarnings("PMD.AssignmentInOperand")
    void drainLeafUpdates() {
        LeafId leafId;
        while ((leafId = radarUpdatedLeafIds.poll()) != null) {
            final RadarLeafView view = radarLeafCache.get(leafId);
            if (view != null) {
                priorityWorkers.execute(view, -1);
            }
        }
    }

    /** Page leaf silhouettes in/out around {@code avatarPos}; radius derived from {@code currentRange}. */
    void updateLeafPaging(final Vec3d avatarPos, final double currentRange) {
        if (world == null || workers == null) {
            return;
        }
        final Vec3i leafSpacing = WorldGrids.LEAF_GRID.getSpacing();
        // Leaf radius rounds up so the visible disc never extends past the
        // outermost loaded leaf. With currentRange = N world units and a leaf
        // X-size of S, ceil(N/S) leaves on each side covers the full radius.
        final int newRadius = (int) Math.ceil(currentRange / leafSpacing.x);
        final boolean radiusChanged = newRadius != radarLeafRadius;
        if (radiusChanged) {
            radarLeafRadius = newRadius;
            rebuildRadarViewArray(newRadius);
            // Force re-paging at the new radius.
            radarCenterCell.set(0, 100, 0);
        }

        final Vec3i newCenter = WorldGrids.LEAF_GRID.worldToCell(avatarPos);
        if (!radiusChanged && newCenter.equals(radarCenterCell)) {
            return;
        }
        radarCenterCell.set(newCenter);
        final Vec3i centerWorld = WorldGrids.LEAF_GRID.cellToWorld(newCenter);

        final Set<LeafId> toRemove = new HashSet<>(radarLeafCache.keySet());
        scheduleVisibleLeaves(centerWorld, leafSpacing, toRemove);
        evictStaleLeaves(toRemove);
    }

    // Ensures each visible leaf has a queued view; removes still-present leaves from {@code toRemove}.
    private void scheduleVisibleLeaves(
            final Vec3i centerWorld,
            final Vec3i leafSpacing,
            final Set<LeafId> toRemove) {
        final Vec3d entryWorld = new Vec3d();
        for (final RadarViewEntry e : radarViewArray) {
            entryWorld.set(
                    centerWorld.x + e.offset.x * (double) leafSpacing.x,
                    centerWorld.y,
                    centerWorld.z + e.offset.z * (double) leafSpacing.z);
            final LeafId leafId = LeafId.fromWorld(entryWorld);
            toRemove.remove(leafId);

            RadarLeafView view = radarLeafCache.get(leafId);
            if (view == null) {
                view = new RadarLeafView(leafId);
                radarLeafCache.put(leafId, view);
                view.queued = true;
                workers.execute(view, e.priority);
            }
        }
    }

    /** Release + cancel any leaves that left the visible disc this tick. */
    private void evictStaleLeaves(final Set<LeafId> toRemove) {
        for (final LeafId remove : toRemove) {
            final RadarLeafView view = radarLeafCache.remove(remove);
            if (view == null) {
                continue;
            }
            view.release();
            if (view.queued && workers.cancel(view)) {
                view.queued = false;
            }
        }
    }

    private void rebuildRadarViewArray(final int radius) {
        // Only X/Z are paged — Subspace is single-leaf in Y.
        final int xzSize = 2 * radius + 1;
        radarViewArray = new RadarViewEntry[xzSize * xzSize];
        int idx = 0;
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                radarViewArray[idx++] = new RadarViewEntry(x, z);
            }
        }
    }

    /** Tear down listener + cancel jobs + release nodes; called from host state {@code cleanup()}. */
    void dispose() {
        if (world != null) {
            world.removeLeafChangeListener(radarLeafObserver);
        }
        for (final RadarLeafView view : radarLeafCache.values()) {
            view.release();
            if (workers != null) {
                workers.cancel(view);
            }
        }
        radarLeafCache.clear();
    }

    /** Pre-computed paging entry: leaf-cell offset from the avatar's leaf. */
    private static final class RadarViewEntry {
        final Vec3i offset;
        final int priority;

        RadarViewEntry(final int x, final int z) {
            // Subspace is single-leaf in Y, so y-offset is always 0.
            this.offset = new Vec3i(x, 0, z);
            // Higher priority = greater distance — workers serve closer leaves first.
            this.priority = x * x + z * z;
        }
    }

    // Cells render at absolute world coords; the radar camera (at the avatar's absolute pos) frames them without a conveyor offset.
    private final class RadarLeafView implements Job {

        private final LeafId leafId;
        private final Node leafNode;
        private Node attachedSilhouette;
        private Node generatedSilhouette;
        volatile boolean queued;

        RadarLeafView(final LeafId leafId) {
            this.leafId = leafId;
            this.leafNode = new Node("RadarLeaf:" + leafId);
            final Vec3i origin = leafId.getWorld(null);
            // Place the leaf at its absolute world origin; cells inside the silhouette
            // are emitted at leaf-local coordinates [0..LEAF_SIZE).
            leafNode.setLocalTranslation(origin.x, 0f, origin.z);
            radarBlockRoot.attachChild(leafNode);
        }

        @Override
        public void runOnWorker() {
            // Once a job actually starts running there's no point trying to cancel it.
            queued = false;

            final LeafData data = world.getLeaf(leafId);
            if (data == null || data.isEmpty()) {
                synchronized (this) {
                    generatedSilhouette = null;
                }
                return;
            }
            final Node temp = new Node("Silhouette:" + leafId);
            silhouetteIndex.generate(temp, data.getRawCells());
            synchronized (this) {
                generatedSilhouette = temp;
            }
        }

        @Override
        @SuppressWarnings("PMD.UnusedAssignment") // null is the final state when next == null
        public double runOnUpdate() {
            final Node next;
            synchronized (this) {
                next = generatedSilhouette;
            }
            if (attachedSilhouette != null) {
                attachedSilhouette.removeFromParent();
                attachedSilhouette = null;
            }
            if (next != null && leafNode.getParent() != null) {
                leafNode.attachChild(next);
                attachedSilhouette = next;
                return 1;
            }
            return 0;
        }

        void release() {
            leafNode.removeFromParent();
        }
    }

    /** {@link LeafChangeListener} that just enqueues changed leaf ids; drain in update(). */
    private final class RadarLeafObserver implements LeafChangeListener {
        @Override
        public void leafChanged(final LeafChangeEvent event) {
            radarUpdatedLeafIds.add(event.getLeafId());
        }
    }
}
