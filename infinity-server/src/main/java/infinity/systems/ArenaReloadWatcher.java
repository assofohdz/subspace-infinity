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

/** Hot-reload watch plumbing for {@link ArenaSystem}; owns the watched-files map and per-tick polling cadence. */
final class ArenaReloadWatcher {

    static final Logger log = LoggerFactory.getLogger(ArenaReloadWatcher.class);

    private final ArenaSystem arenaSystem;

    private final Map<String, List<ArenaLogic.WatchedFile>> watchedFiles = new ConcurrentHashMap<>();

    private long nextScriptPollNanos;

    ArenaReloadWatcher(final ArenaSystem arenaSystem) {
        this.arenaSystem = arenaSystem;
    }

    void pollIfDue(final long nowNanos, final long intervalNanos) {
        if (nowNanos < nextScriptPollNanos) {
            return;
        }
        nextScriptPollNanos = nowNanos + intervalNanos;
        pollScriptWatches();
    }

    /** Registers the {@code shipsScript} + every {@code .groovy} fragment include. */
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

    void unregisterScriptWatch(final String arenaName) {
        final List<ArenaLogic.WatchedFile> removed = watchedFiles.remove(arenaName);
        if (removed != null && !removed.isEmpty() && log.isDebugEnabled()) {
            log.debug("Stopped watching {} file(s) for arena {}", removed.size(), arenaName);
        }
    }

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
