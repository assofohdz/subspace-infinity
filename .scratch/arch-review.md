# Architectural review — Subspace Infinity

Read-only review by team `s6-ship-radius` (5 reviewers across 5 concern slices). Branch: `slice/s7-mine-speed` HEAD `9c1abec4`. Date: 2026-05-09.

All findings closed. (Tier 1 closed in `arch-review-tier1-bundle`; Tier 2's 6 items closed in `arch-review-tier2-bundle` — commit `7eb8b2b8`; Tier 3 #7–#10, #12–#13 closed in `arch-review-tier3-bundle` — commit `2578dd6c`; #11 closed in `arch-review-megasplit-bundle` — commit `3cf92a86`.) Findings are sourced from five parallel reviews:
- **planner** — module boundaries, api-contracts, cross-module layering
- **config-2** — config + settings pipeline (Pattern 4, Groovy adapters, prize-appliers)
- **spawn** — ECS architecture + server-side systems + EntitySet lifecycle
- **cleanup** — tests + tooling + dev workflow
- **client** (temporary 5th teammate, shut down post-review) — client / UI / rendering / input

## Patterns the team flagged as working well

- **api/ → infinity/ direction discipline holds** — `grep -rln "import infinity.\(systems\|server\|client\|modules\|ai\)" api/src` returns zero hits after 50+ slices.
- **`modules/` cleanliness** — all 6 module classes (`basicTester`, `doorTester`, `lightTester`, `prizeTester`, `warpTester`, `wangTester`) only import `infinity.sim.*` + jME math + Simsilica primitives.
- **Pattern 4 template→projection→component is consistently applied** — `ShipConfig` → `ShipSpawnSystem.project*` → ECS components. Recent S6/s7 slices land cleanly because the pattern is well-grooved.
- **Logic-extraction discipline is real and consistent** — `WeaponsLogic`, `WeaponsDamageLogic`, `WeaponsEligibility`, `ConsumableLogic`, `MapSystemLogic`, `ArenaLogic`, `ShipWeaponsProjector`, `ShipStatusProjector`. Systems delegate to static helpers rather than absorb complexity.
- **Decay/TTL canonicalization fully respected** — zero parallel `*Lifetime` / `*Ttl` / `*ExpiresAt` / `*Decay` components. `RocketTime` is correctly modeled (per-ship duration template → projection writes `Decay` deadline).
- **Client-side ECS reads only** — `grep ed.setComponent` in `infinity.client.*` returns zero hits. `client-read-only.md` honored.
- **`BodyPosition` (not RMI polling) drives avatar render** — `AvatarMovementState.getInterpolatedAvatarPosition:218-260` prefers SimEthereal interpolation, falls back to RMI only when buffer is unfilled.
- **Lazy-resolve avatar id in `update()`** — `JitterState`, `PositionHudState`, `InfinityCameraState`, `RadarState`, `ModelViewState.tryInitializeAvatar` all wait for `GameSessionState.getAvatarEntityId()` before binding watches. The Javadoc on `PositionHudState:108-148` is the canonical write-up.
- **Backward-compat overload pattern in `GameEntities`** — primary takes new param; no-param version forwards via `EngineConfig.DEFAULTS`. Predictable for module authors (despite TD-9's growth concern).
- **`pmdPath` task** (`infinity.java-conventions.gradle:137`) — surgical per-file PMD scoping; cleanly supports the touched-files ratchet rule.
- **Static-analysis ceilings wired** — per-tool, per-module strict pin via `gradle.properties` (Checkstyle + PMD); ceiling failure on any new violation. Error Prone + NullAway still warn-only (out of scope; need stdout-parsing).
- **`LayerDependencyTest`** — covers api/server/client/modules/ai layering AND api/sim+api/config (post-discipline-ratchet bundle), with FQN-anchored exemptions. Compact + gating + the ONE test that actually fails the build today.
- **PRD-as-folder pattern** in `.scratch/<feature>/PRD.md` — multi-document slice work stays organized.
- **Replacement-as-Mutation rule landed** ([rule](../.claude/rules/replacement-as-mutation.md), [PRD](./replacement-as-mutation/PRD.md)) — codifies single-writer-per-component + intent-queue discipline. WeaponsSystem extraction (TD-1) is the pilot migration slice.
- **Workflow vacuum is clean post-prune** — neither `CLAUDE.md` nor `.claude/rules/*.md` has any orphan reference to the deleted `multi-machine-workflow.md` / `active-work.md` / `config-consumers.md`.

## Open questions surfaced by the review

- TD-6 hot-reload — is engine-tier tuning expected to continue, or has it settled? Determines whether the watcher is worth the effort.
- TD-1 extraction direction — `WeaponsFireSystem` vs `WeaponsReaperSystem` vs `WeaponsImpactSystem` — three-way carve identified during the grilling for the spatial-query slice. Producer-audit + RaM pilot will resolve canonical writers as part of the slice.
- Are `PostProcessingState` / `BloomPostState` / `SkyState` / `GridState` / `SettingsState` intended for restoration, or are they dead? They occupy ~1.5K LOC and aren't wired to `Main`. (Their commented-out instantiation in Main.java has been removed; the .java files are still on disk.)

## Recommendations — biggest wins

All tiers fully closed. Tier 1 (same-day-fix bundle: Main.java graveyard, SISpatialFactory dead code, spatial-query promotion, static-analysis ceilings), Tier 2 (#1–6: WeaponsSystem RaM split, ConfigRegistry slot-store + adapter dedup, client/server/net seed tests, spawn-projection RaM pillar, engine-tier hot-reload), and Tier 3 (#7–#10, #12–#13: requireSystem helper, WatchedEntity lifecycle, GameEntities split, GroovyShipLoader extraction, api/ test sourceset, Main.java SPDX; #11: infinity/ mega-module split into infinity-server + infinity-client) have all landed.
