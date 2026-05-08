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
 * Owns the radar's leaf-paging subsystem — extracted from {@link RadarState}
 * to keep the host state's class-level cyclomatic-complexity sum below PMD's
 * class threshold without fragmenting the leaf-paging flow.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Maintain the {@code LeafId → RadarLeafView} cache that backs the
 *       radar's wall silhouettes.</li>
 *   <li>Schedule async {@link RadarLeafView} jobs against the shared
 *       {@link JobState} pools as the avatar's leaf-cell changes.</li>
 *   <li>Listen on {@link World#addLeafChangeListener} (registered via
 *       {@link #attachLeafObserver()}) so server-side wall edits requeue the
 *       affected leaf at top priority via {@link #drainLeafUpdates()}.</li>
 *   <li>Tear down the listener + cancel queued jobs on {@link #dispose()}.</li>
 * </ul>
 *
 * <p>Behaviour preserved exactly from the original inline {@code RadarState}
 * code: same disc walk, same {@code x²+z²} priority assignment (closer leaves
 * served first), same eviction-on-radius-change reset of the center sentinel.
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

    /**
     * Wire the {@link LeafChangeListener} into {@code world}. Caller invokes
     * this after construction so leaf-change events flow into
     * {@link #drainLeafUpdates()}. Mirrors the original inline registration in
     * {@code RadarState.initialize}.
     */
    void attachLeafObserver() {
        if (world != null) {
            world.addLeafChangeListener(radarLeafObserver);
        }
    }

    /**
     * Apply any leaf-change events the observer queued since last frame. If
     * we are currently paging the changed leaf, requeue its job at top
     * priority so the silhouette refreshes promptly (e.g. a wall got built
     * or destroyed server-side).
     */
    @SuppressWarnings("PMD.AssignmentInOperand") // canonical `while ((leafId = poll()) != null)` drain
    void drainLeafUpdates() {
        LeafId leafId;
        while ((leafId = radarUpdatedLeafIds.poll()) != null) {
            final RadarLeafView view = radarLeafCache.get(leafId);
            if (view != null) {
                priorityWorkers.execute(view, -1);
            }
        }
    }

    /**
     * Page leaf silhouettes in / out around {@code avatarPos}. Mirrors
     * {@code LocalViewState.updateView}, simplified for radar:
     *
     * <ul>
     *   <li>Paging radius (in leaves) is derived from {@code currentRange}
     *       via {@link WorldGrids#LEAF_GRID} spacing — no hand-rolled
     *       {@code * 1024} arithmetic, per
     *       {@code .claude/rules/world-coordinates.md}.</li>
     *   <li>Subspace is effectively 2D (one Y layer), so the view spans only
     *       one leaf in Y — the one containing the avatar.</li>
     *   <li>Blocks are not entities — leaves come from {@link World#getLeaf}
     *       and are paged on world-grid boundaries, independent of the entity
     *       blip containers.</li>
     * </ul>
     */
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

    /**
     * Walk the {@link #radarViewArray} disc relative to {@code centerWorld}
     * and ensure each visible leaf has a queued {@link RadarLeafView}. Leaves
     * still present after this call are removed from {@code toRemove}.
     */
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

    /**
     * Tear down the leaf observer + cancel queued jobs + release attached
     * silhouette nodes. Called from the host state's {@code cleanup()}.
     */
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

    /**
     * Async {@link Job} that loads one leaf's silhouette: fetches cell data
     * from {@link World#getLeaf} on a worker thread, builds the silhouette
     * mesh, and (back on the JME update thread) attaches the resulting node
     * under {@code radarBlockRoot}. Mirrors {@code LocalViewState.LeafView}.
     *
     * <p>The leaf node is positioned at the leaf's absolute world origin in
     * {@code initialize}-time setup, so cells render at absolute world coords —
     * the radar camera (which sits at the avatar's absolute world position)
     * will frame them correctly without any conveyor offset.
     */
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
