# Hardcoded Values Registry

Running ledger of literal numbers and strings in Java code that look like configuration or magic constants — candidates to move into `arena.conf`, `InfinityConstants`, a Groovy script, or a named constant.

Append new entries as you spot them during normal work. This is an inbox, not a triage queue — don't block a task to fix them, just log and move on. Batch-address periodically.

## Format

One row per occurrence. If the same value appears in multiple files, log each.

| File:Line | Symbol / Context | Value | Notes |
|---|---|---|---|
| ~~PlayerDriver.java:57~~ | ~~`pickup` acceleration slope~~ | ~~`3`~~ | **Resolved** — now reads per-ship `Thrust.getThrust()` (Pattern 4, step 5e). |
| ~~PlayerDriver.java:70~~ | ~~`FORCE_MULTIPLIER`~~ | ~~`20.0`~~ | **Resolved** — removed. PlayerDriver now uses `setLinearVelocity` directly instead of `addForce`, so no force-scaling constant is needed. Our computed velocity is authoritative. |
| ~~PlayerDriver.java:70~~ | ~~`DRAG_FACTOR`~~ | ~~`0.05`~~ | **Resolved** — promoted to per-ship `ShipConfig.dragFactor()` → `DragFactor` component (Pattern 4 #4). PlayerDriver now reads from the watched entity. |
| ~~PlayerDriver.java:80~~ | ~~`TURN_RESPONSIVENESS`~~ | ~~`8.0`~~ | **Resolved** — promoted to per-ship `ShipConfig.turnResponsiveness()` → `TurnResponsiveness` component (Pattern 4 #4). |
| ~~ContactSystem.java:92~~ | ~~`contact.restitution = 1`~~ | ~~`1`~~ | **Resolved** — promoted to per-ship `ShipConfig.bounceRestitution()` → `BounceRestitution` component (Pattern 4 #4). Non-ship dynamic bodies still default to perfectly-elastic (`1.0`) when the component is absent. |
| ~~[ArenaSystem.java](../infinity/src/main/java/infinity/systems/ArenaSystem.java) `SCRIPT_POLL_INTERVAL_NANOS`~~ | ~~Throttle for the per-arena `ships.groovy` file watcher~~ | ~~`5_000_000_000L` (5 s)~~ | **Resolved** — promoted to `ZoneConfig.scriptPollIntervalSeconds()` (DSL: `scriptPollInterval N`), read at startup by `ArenaSystem`. Default still 5 s. |
| [ContactSystem.java](../infinity/src/main/java/infinity/systems/ContactSystem.java) `contact.friction = 0.0` (ship-vs-static branch) | Per-contact tangential-friction coefficient for ship-vs-wall hits | `0.0` | Tuning knob. Different ships could plausibly want different wall friction (slidey vs grippy). Promote to per-ship `ShipConfig.wallFriction()` → `WallFriction` component (parallel to `BounceRestitution`). |
| [FlatTileBlockFactory.java](../infinity/src/main/java/infinity/client/view/FlatTileBlockFactory.java) `TILE_PIXELS` | Inner tile size in atlas pixels | `16` | Tied to Subspace BMP format (16x16 tiles in 19x10 grid). Format constant, not a tuning knob — stays in Java unless we ever support non-Subspace tilesets. |
| [FlatTileBlockFactory.java](../infinity/src/main/java/infinity/client/view/FlatTileBlockFactory.java) `GUTTER_PIXELS` | Replicate-padding around each tile in the loaded atlas | `1` | Filtering-correctness constant: 1 px is enough for trilinear/anisotropic 4-texel kernels. Bump only if we move to a kernel that reads further (e.g. 4x4 lanczos). Not a gameplay knob. |
| [BlockGeometryIndex.java](../infinity/src/main/java/infinity/client/view/BlockGeometryIndex.java) `tex.setAnisotropicFilter(8)` | Anisotropic filter level for the tileset | `8` | Render-quality knob. 8x is generally indistinguishable from 16x at the camera angles we use; dropping to 4x or 1x would re-introduce mild oblique shimmer. Candidate for a zone-tier setting if we ever add a "low quality" preset. |
| [RadarState.java](../infinity/src/main/java/infinity/client/states/RadarState.java) `RADAR_PIXEL_SIZE` | Off-screen radar texture + GUI quad size in px | `218` | Display knob, not gameplay-balance — controls HUD footprint and radar resolution. Candidate for a zone-tier UI scale setting if HUD scaling lands. |
| [RadarState.java](../infinity/src/main/java/infinity/client/states/RadarState.java) `RADAR_CAM_HEIGHT` | Top-down ortho-cam Y above the avatar | `1000f` | Parallel projection — height itself doesn't change rendered size, only the depth slab. Just needs to clear all expected geometry; no tuning value. |
| [RadarState.java](../infinity/src/main/java/infinity/client/states/RadarState.java) `RADAR_CAM_NEAR` / `RADAR_CAM_FAR` | Ortho-cam depth slab | `1f` / `5000f` | Pair with `RADAR_CAM_HEIGHT`; both wide enough to swallow any expected object Y. Not gameplay-tuned. |
| [RadarState.java](../infinity/src/main/java/infinity/client/states/RadarState.java) `DEFAULT_RANGE_WORLD_UNITS` | Frustum half-extent before `RadarRange` resolves | `256.0` | Bridge value used only for the first frame(s) until the avatar's `RadarRange` arrives. Per-ship value lives in the Groovy preset (`ShipConfig.radarRange`). |
| [RadarState.java](../infinity/src/main/java/infinity/client/states/RadarState.java) `RADAR_CANONICAL_RANGE` | Reference radar range at which `RadarBlipFactory` mesh sizes render at their nominal pixel size | `256.0` | Blips scale uniformly by `currentRange / RADAR_CANONICAL_RANGE` so on-screen blip size stays constant regardless of zoom. Promote to a `RadarTheme` record alongside the colour palette if HUD theming lands. |
| [RadarState.java](../infinity/src/main/java/infinity/client/states/RadarState.java) `bp.initialize(avatarEntityId, 12)` | `BodyPosition` ring-buffer history depth | `12` | Mirrors the same magic 12 in `AvatarMovementState.getInterpolatedAvatarPosition`. SimEthereal-side convention; if it changes there it should change here too. |
| ~~[RadarState.java](../infinity/src/main/java/infinity/client/states/RadarState.java) `SELF_COLOR` / `FRIENDLY_COLOR` / `ENEMY_COLOR` / `NEUTRAL_COLOR`~~ | ~~Radar blip team palette~~ | ~~`White` / `Green` / `Red` / `Gray`~~ | **Resolved** — promoted to `RadarTheme.{selfColor, friendlyColor, enemyColor, neutralColor}()` (`infinity.client.view.RadarTheme`). Defaults unchanged. |
| [RadarBlipFactory.java](../infinity/src/main/java/infinity/client/view/RadarBlipFactory.java) `SHIP_DOT_RADIUS` / `DOT_SEGMENTS` / `STATIC_BLIP_HALF_SIZE` | Ship-blip disc radius / fan smoothness / static-blip half-extent (world units) | `5f` / `16` / `10f` | Visual sizing/quality knobs. Tied to radar zoom, not to ship physics — intentionally not promoted to per-ship `ShipConfig`. Promote to a client `RadarTheme` record if blip scaling/shape ever needs to react to a "compact / classic / large" preference. |
| ~~[RadarState.java](../infinity/src/main/java/infinity/client/states/RadarState.java) `RADAR_BACKGROUND_COLOR`~~ | ~~Ambient fill inside the radar circle~~ | ~~`(0.12, 0.20, 0.10)` (muddy dark green)~~ | **Resolved** — promoted to `RadarTheme.backgroundColor()`. Default unchanged. |
| ~~[RadarLeafSilhouetteIndex.java](../infinity/src/main/java/infinity/client/view/RadarLeafSilhouetteIndex.java) `BLOCK_COLOR`~~ | ~~Solid-tile silhouette colour~~ | ~~`(0.55, 0.55, 0.55)` (medium grey)~~ | **Resolved** — promoted to `RadarTheme.blockColor()`; passed into `RadarLeafSilhouetteIndex` via constructor. Default unchanged. |

## Guidance on what counts

**Log it:**
- Numeric literals used as thresholds, limits, timings, speeds, distances
- String literals naming sections/keys/components that are repeated across files
- Default-value arguments to `getInt`/`getString` that should match a canonical default
- Magic numbers in physics, rendering, or gameplay math

**Skip it:**
- Obvious identity values (`0`, `1`, `-1`, `null`)
- Loop indices, array sizes derived from input
- Test fixture values
- Values already named via a `final static` constant (those *are* the named constant)
