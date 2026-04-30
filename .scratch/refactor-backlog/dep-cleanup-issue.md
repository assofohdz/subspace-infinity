# Dependency cleanup — 2026-04-30

Snapshot of the dependency graph at commit `014ad62`. Catalogues what was removed (and why we might want it back) plus migration candidates that are still in use but worth flagging.

## Removed (zero imports across `*.java`)

These were declared in [`infinity/build.gradle`](../../infinity/build.gradle) but had no usages anywhere in the codebase. If gameplay needs any of them later, re-add the line with the version pinned at the date of the requirement.

### `com.badlogicgames.gdx:gdx-ai:1.8.2`

LibGDX's AI library — steering behaviours, behaviour trees, state machines, pathfinding (`gdx-ai`). Originally pulled in to back the `infinity/ai/` Brain/Action/Strategy stack. The current AI stack in [`infinity/src/main/java/infinity/ai/`](../../infinity/src/main/java/infinity/ai/) is hand-rolled; gdx-ai was never wired up.

If we ever want a "real" steering system (seek, flee, pursue, wander, formation) or a battle-tested behavior-tree DSL, gdx-ai is mature and free of jME-incompatible deps. Reconsider before writing one from scratch.

### `de.lighti:Clipper:6.4.2`

Java port of [Angus Johnson's Clipper](http://www.angusj.com/delphi/clipper.php) — 2D polygon boolean operations (union, intersection, difference, offset). Useful for arena boundary computation, line-of-sight polygon clipping, blocking-region merges.

If we add geometric arena masks or LOS-based mechanics, Clipper or its successor `Clipper2` is the obvious dep.

### `com.github.czyzby:noise4j:0.1.0`

Procedural dungeon / map generation — cellular automata caves, Drunkard's Walk, room-and-corridor generators. Was likely intended for procedural arena generation that never landed. Subspace's gameplay leans on hand-authored `.lvl` maps, so this stayed unused.

If we add a "generate a random arena" mode, noise4j is small and self-contained — but the one-off 0.1.0 release suggests low maintenance. SquidLib or libnoise-java are alternatives.

### `com.google.code.gson:gson:2.11.0`

JSON parsing/serialization. The build comment on line 88 said "Trying this for saving/loading configs" — that experiment was superseded by the Groovy `*.groovy` config layer (`GroovyZoneLoader`, `GroovyArenaLoader`, etc.). No `import com.google.gson.*` anywhere.

If we add a save-game format, savefile-to-server protocol, or external integration that demands JSON, Gson is the obvious choice (lighter than Jackson, well-known shape). Pin to whatever 2.x is current at that point.

### `org.apache.commons:commons-collections4:4.4`

Apache Commons Collections — bidirectional maps (`BidiMap`), multi-key maps, `ListUtils`, `CollectionUtils`. Apparently never actually used; the JDK collections + Guava (also pulled in) cover the real usages.

If we want `BidiMap` or `MultiValuedMap` later, commons-collections4 is the canonical choice.

## Stale commented-out dependency lines

Removed from build files in the same change. None had been active in months/years.

- `org.dyn4j:dyn4j:3.4.0` — old physics engine, replaced long ago by Moss/`mblock-physb`. Three commented variants in [`infinity/build.gradle`](../../infinity/build.gradle).
- `com.github.implicit-invocation:jwalkable:master-SNAPSHOT` — 2D polygonal pathfinding. Listed but never imported in either api or infinity.
- `com.simsilica:mphys` — Moss raw physics. The current code uses `mblock-physb` (mblock-flavoured physics) and `sio2-mphys`; raw `mphys` is not needed.
- `com.badlogicgames.gdx:gdx-ai:1.8.1:sources` / `:javadoc` — IDE-only sources/javadoc classifiers for gdx-ai (which we just removed).

## Still in use, but candidates for follow-up

These are not part of the current cleanup, but flagged here so they are findable when the time comes.

### `com.github.stephengold:Heart:9.3.0` — exactly one usage

Stephen Gold's jME utility library. Used at exactly one site:

- [`infinity/client/states/SISpatialFactory.java:64`](../../infinity/src/main/java/infinity/client/states/SISpatialFactory.java) — `import jme3utilities.MyMesh;`

If `MyMesh` is doing anything we can implement against `com.jme3.scene.Mesh` directly (vertex/index buffer manipulation), the Heart dep can go. Worth a 30-min look.

### `org.apache.commons:commons-math:2.2` — end-of-life version

commons-math `2.x` shipped in 2010 and is end-of-life. The successor is `commons-math3:3.6.1` (different package: `org.apache.commons.math3.*`). Used at one site:

- [`infinity/util/MathUtil.java`](../../infinity/src/main/java/infinity/util/MathUtil.java) — imports `MathException`, `distribution.TDistributionImpl`, `stat.StatUtils`.

Migration is mostly mechanical: change package to `math3`, `MathException` → `MathRuntimeException`, `TDistributionImpl` → `TDistribution`. Worth doing before the next major version bump.

### Nullable annotation consolidation — two libraries for the same job

The project pulls in **both** `com.google.code.findbugs:jsr305:3.0.2` (`javax.annotation.Nullable`) and `org.jetbrains:annotations` (`org.jetbrains.annotations.Nullable`). Distribution today:

- `javax.annotation.Nullable` — 8 files (most of `settings/`, `ArenaSystem`, `Preconditions`)
- `org.jetbrains.annotations.Nullable` — 1 file ([`api/src/infinity/sim/GameSounds.java`](../../api/src/infinity/sim/GameSounds.java))

Pick one and drop the other. Easier path: migrate the single `GameSounds.java` import to `javax.annotation.Nullable` and remove the jetbrains:annotations dep. (Or migrate the other direction if we prefer the JetBrains style — that's an 8-file change.)

## Versioning observation

Of the deps that *are* declared, almost all use `'+'` (latest) — see [`build.gradle:8-23`](../../build.gradle). The pinned exceptions are JME (`3.9.0-stable`), gson (now removed), log4j (`2.25.4`), slf4j (`2.0.17`), pager/sim-fx (`1.0.1-SNAPSHOT`), Heart (`9.3.0`), ini4j (`0.5.4`), commons-* (`4.4`/`2.2`), noise4j (now removed), gdx-ai (now removed), Clipper (now removed).

The `dependency-scout` agent tracks Simsilica drift. Worth a one-pass review of the rest at some point — `+` resolution is convenient but means a Maven Central cache flush can shift the build under us.
