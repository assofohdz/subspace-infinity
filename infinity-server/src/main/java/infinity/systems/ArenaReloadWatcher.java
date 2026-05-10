// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems;

import infinity.es.arena.ArenaId;
import infinity.settings.GroovySettingsHost;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Hot-reload watch subsystem extracted from {@link ArenaSystem} (round 24
 * class-CC slice). Owns the watched-files map + per-tick polling cadence.
 *
 * <p>Not a {@link com.simsilica.sim.AbstractGameSystem} — it's plumbing
 * held as a field on {@link ArenaSystem} so the lifecycle stays a single
 * unit (initialize / terminate run from ArenaSystem; tick polling fires
 * from {@code ArenaSystem.update}).
 *
 * <p>Reload callbacks delegate back to ArenaSystem via two narrow
 * methods: {@link ArenaSystem#handleShipsScriptReload} (re-applies typed
 * ship config + reprojects live ships) and {@link
 * ArenaSystem#handleFragmentReload} (rebuilds the merged settings store
 * via {@code ConfigRegistrySystem.load}). Both look up the current arena
 * record fresh so a swap-map / hot-edit cycle picks up the latest
 * {@code ArenaConfig} rather than the snapshot captured at watch time.
 *
 * <p>Failure modes:
 *
 * <ul>
 *   <li><b>File not on disk</b> (production / classpath-only deployments)
 *       — {@code registerFileWatch} no-ops with a debug log; live reload is
 *       silently disabled for that file.
 *   <li><b>{@code stat()} throws</b> mid-poll — logged + contained so a
 *       single file's failure doesn't abort the rest of the poll.
 *   <li><b>Reload callback throws</b> — caught + logged inside
 *       {@link ArenaLogic#pollSingleWatch}; the watcher stays armed.
 * </ul>
 *
 * <p>Thread model: mutated only on the sim thread (matches ArenaSystem's
 * own discipline). The {@link Map} is {@link ConcurrentHashMap} as defence
 * in depth in case a future caller reads from another thread, but no
 * concurrent-write contract is exposed.
 */
final class ArenaReloadWatcher {

    static final Logger log = LoggerFactory.getLogger(ArenaReloadWatcher.class);

    private final ArenaSystem arenaSystem;

    /**
     * Per-arena watch state, keyed by arena name. Each value is the list of
     * {@link ArenaLogic.WatchedFile}s registered for that arena's
     * {@code shipsScript} + every fragment include. Production /
     * classpath-only deployments produce no entries — the on-disk path is
     * unresolvable.
     */
    private final Map<String, List<ArenaLogic.WatchedFile>> watchedFiles = new ConcurrentHashMap<>();

    /**
     * Throttle deadline for {@link #pollScriptWatches} — stat() at most once
     * per zone {@code scriptPollIntervalNanos} interval.
     */
    private long nextScriptPollNanos;

    ArenaReloadWatcher(final ArenaSystem arenaSystem) {
        this.arenaSystem = arenaSystem;
    }

    /**
     * Per-tick entry point. If {@code nowNanos} has reached the next poll
     * deadline, walk every watched file and run its reload callback when
     * its on-disk mtime has changed since last poll.
     */
    void pollIfDue(final long nowNanos, final long intervalNanos) {
        if (nowNanos < nextScriptPollNanos) {
            return;
        }
        nextScriptPollNanos = nowNanos + intervalNanos;
        pollScriptWatches();
    }

    /**
     * Register the per-arena reload watches: the {@code shipsScript}
     * (re-projects all ships on edit via
     * {@link ArenaSystem#handleShipsScriptReload}) plus every fragment in
     * {@code fragmentIncludes} (forces a full settings reload via
     * {@link ArenaSystem#handleFragmentReload}). Filters non-Groovy
     * fragments as defence in depth — the loader only handles
     * {@code .groovy}.
     */
    void registerArenaReloadWatches(
            final ArenaId arenaId,
            final String shipsScript,
            final List<String> fragmentIncludes) {
        if (shipsScript != null && !shipsScript.isBlank()) {
            registerFileWatch(
                arenaId,
                shipsScript,
                () -> arenaSystem.handleShipsScriptReload(arenaId, shipsScript));
        }
        // Watch every Groovy includeFragment so editing a fragment file at
        // runtime triggers a full re-load through ConfigRegistrySystem.
        // Consumers re-read on next consumption — no event/callback fires.
        for (final String fragmentPath : fragmentIncludes) {
            if (fragmentPath != null && fragmentPath.endsWith(".groovy")) {
                registerFileWatch(
                    arenaId,
                    fragmentPath,
                    () -> arenaSystem.handleFragmentReload(arenaId, fragmentPath));
            }
        }
    }

    /**
     * Drop every registered watch for the named arena. Called from
     * {@link ArenaSystem}'s unload path so an unloaded arena doesn't keep
     * firing callbacks against a record that no longer holds an
     * {@link com.simsilica.es.EntityId}.
     */
    void unregisterScriptWatch(final String arenaName) {
        final List<ArenaLogic.WatchedFile> removed = watchedFiles.remove(arenaName);
        if (removed != null && !removed.isEmpty() && log.isDebugEnabled()) {
            log.debug("Stopped watching {} file(s) for arena {}", removed.size(), arenaName);
        }
    }

    /**
     * Register a per-arena watch on a Groovy file so a dev-mode edit fires
     * {@code onChanged} on the next throttled poll. No-op if the file isn't
     * reachable on disk (production / classpath-only deployments) or the
     * path is blank.
     */
    private void registerFileWatch(
            final ArenaId arenaId, final String classpathPath, final Runnable onChanged) {
        if (classpathPath == null || classpathPath.isBlank()) {
            return;
        }
        final Path onDisk = GroovySettingsHost.INSTANCE.resolveOnDisk(classpathPath);
        if (onDisk == null) {
            if (log.isDebugEnabled()) {
                log.debug(
                    "{} for arena {} not on disk; live reload disabled for this run",
                    classpathPath, arenaId.getArena());
            }
            return;
        }
        try {
            final FileTime mtime = Files.getLastModifiedTime(onDisk);
            watchedFiles
                .computeIfAbsent(arenaId.getArena(), k -> new ArrayList<>())
                .add(new ArenaLogic.WatchedFile(arenaId.getArena(), classpathPath, onDisk, mtime, onChanged));
            if (log.isInfoEnabled()) {
                log.info("Watching {} for arena {}", onDisk, arenaId.getArena());
            }
        } catch (final java.io.IOException e) {
            if (log.isWarnEnabled()) {
                log.warn(
                    "Could not stat {} to enable live reload for arena {}: {}",
                    onDisk, arenaId.getArena(), e.toString());
            }
        }
    }

    /**
     * Stat each watched file's on-disk path; if the mtime changed, run the
     * file's reload callback. Reload failures are logged and contained so
     * a single file's failure doesn't abort the rest of the poll.
     */
    private void pollScriptWatches() {
        if (watchedFiles.isEmpty()) {
            return;
        }
        for (final List<ArenaLogic.WatchedFile> arenaWatches : watchedFiles.values()) {
            for (final ArenaLogic.WatchedFile w : arenaWatches) {
                ArenaLogic.pollSingleWatch(log, w);
            }
        }
    }
}
