# DebugState + Subspace key-bindings

Status: needs-triage

Today every key binding is mapped at boot from `Main.simpleInitApp` regardless of intent (`MainGameFunctions` + `DebugFunctions` + `ToolFunctions` all share the `"In Game"` Lemur group string), and `F12` is double-purposed — already bound to `F_THOR` on the gameplay side, so the user's "F12 → toggle DebugState" intent collides immediately. Several action-group bindings are silently broken (`F_DECOY` / `F_ROCKET` / `F_BRICK` / `F_ATTACH` all have `if (!hasMappings(F_X)) inputMapper.map(F_REPEL, …)` — guard checks the wrong key, four keys redundantly map `F_REPEL`, the four guarded functions have zero mappings). The PRD inventories the gaps, flips the bindings into proper Lemur input groups so `activateGroup` / `deactivateGroup` is the toggle mechanism, introduces a `DebugState extends BaseAppState` whose enable/disable switches groups, and backfills the missing canonical Continuum/Subspace bindings.

## Why
- Manual playtesting is the only verification path for keyboard interactions today; the buggy `F_REPEL`-mapped-four-times pattern slipped past every prior slice's review because the only feedback loop is "press the key in a running client and see what fires." A clean group-based architecture surfaces the issue at the catalog level.
- F-key reuse (F3/F4 each have a debug toggle AND an attempted action map) means players in a debug-attached build see different game behaviour than players in a clean build — silent drift between dev workflow and release behaviour.
- The user reports "a lot of key bindings are missing" — this is the right time to systematically catalog Subspace canon vs current state and close the gaps in one sweep rather than ad-hoc per-slice.
- A dedicated `DebugState` enables future per-developer toggles (camera fly-mode, lighting-tuner, bin-status overlays, body/contact/probe debug) without polluting the gameplay key surface.

## Goal
Single-line: introduce `DebugState extends BaseAppState` toggled by F12 (default detached), partition every existing `FunctionId` into mutually-exclusive input groups (`Game` / `Debug` / `System` / `Tool` / `Camera`), wire group activate/deactivate to the toggle, and backfill missing Continuum/Subspace player-facing bindings.

## Out of scope
- **Multi-keymap loadout** (per-player saved profiles, "Continuum classic" vs "modernized" key sets).
- **Rebinding via in-game UI** — the F1 HelpState already iterates `inputMapper.getFunctionIds()`; a rebinder is a downstream feature.
- **Gamepad / joystick remap** — existing joystick mappings stay untouched; group partitioning only affects keyboard-side toggling.
- **Persisted user preferences** (DebugState attached/detached saved across launches).
- **Keyboard-layout localization** (AZERTY, etc.).
- **Server-side chat-typed commands** (`?find`, `?go`, etc.) — those flow through `ChatHostedService`, not `InputMapper`. The PRD notes them but doesn't redesign them.

## Background — current state inventory

Seven `Functions` classes register `FunctionId` mappings; `Main.simpleInitApp` lines 176-183 invokes 6 of them (CameraMovementFunctions stays commented out). Key + group + intended consumer side:

| Class | Group string | FunctionId | Default key | Side | Notes |
|---|---|---|---|---|---|
| `MainGameFunctions` | `"In Game"` | `F_IN_GAME_MENU` | ESC + JoystickSelect/B8 | Game | menu open |
| `MainGameFunctions` | `"In Game"` | `F_PLAYER_LIST` | F2 | Game | player roster |
| `MainGameFunctions` | `"In Game"` | `F_CHAT_CONSOLE` | RETURN, NUMPADENTER | Game | chat |
| `AvatarMovementFunctions` | `"Movement"` | `F_RUN` (Afterburner) | **unmapped** | Game | declared, never bound |
| `AvatarMovementFunctions` | `"Movement"` | `F_THRUST` | UP / DOWN(neg) | Game | thrust axis |
| `AvatarMovementFunctions` | `"Movement"` | `F_TURN` | LEFT / RIGHT(neg) | Game | rotate axis |
| `AvatarMovementFunctions` | `"Movement"` | `F_STOP` | SPACE | Game | full stop |
| `AvatarMovementFunctions` | `"Map"` | `F_MOUSE1/2/3` | MOUSE1/2/3 | Game | tile edit |
| `AvatarMovementFunctions` | `"Weapons"` | `F_BOMB` | TAB | Game | Subspace canon (S2) |
| `AvatarMovementFunctions` | `"Weapons"` | `F_GRAVBOMB` | BACKSLASH | Game | gravity bomb |
| `AvatarMovementFunctions` | `"Weapons"` | `F_MINE` | **unmapped** | Game | declared, never bound |
| `AvatarMovementFunctions` | `"Weapons"` | `F_SHOOT` (BULLET) | LCONTROL | Game | Subspace canon |
| `AvatarMovementFunctions` | `"Weapons"` | `F_THOR` | **F12** | Game | clashes with DebugState toggle target |
| `AvatarMovementFunctions` | `"Weapons"` | `F_BURST` | DELETE | Game | |
| `AvatarMovementFunctions` | `"Actions"` | `F_REPEL` | F3, F4, F5, F7 | Game | **bug**: 4 redundant maps from broken guards |
| `AvatarMovementFunctions` | `"Actions"` | `F_WARP` | INSERT | Game | |
| `AvatarMovementFunctions` | `"Actions"` | `F_PORTAL` | **unmapped** | Game | declared, never bound |
| `AvatarMovementFunctions` | `"Actions"` | `F_DECOY` | **unmapped** | Game | guard intent: F5 (broken) |
| `AvatarMovementFunctions` | `"Actions"` | `F_ROCKET` | **unmapped** | Game | guard intent: F3 (broken) |
| `AvatarMovementFunctions` | `"Actions"` | `F_BRICK` | **unmapped** | Game | guard intent: F4 (broken) |
| `AvatarMovementFunctions` | `"Actions"` | `F_ATTACH` | **unmapped** | Game | guard intent: F7 (broken) |
| `AvatarMovementFunctions` | `"Toggles"` | `F_MULTI/F_ANTI/F_STEALTH/F_CLOAK/F_XRADAR` | **unmapped** | Game | declared, none bound |
| `AvatarMovementFunctions` | `"Tower"` | `F_TOWER` | T | Game | tower-defense gameplay |
| `AvatarMovementFunctions` | `"Ship"` | `F_WARBIRD..F_SHARK` | KEY_1..KEY_8 | Game | ship select |
| `AvatarMovementFunctions` | `"Alternative"` | `F_SHIFT` | LSHIFT | Game | modifier for chords |
| `DebugFunctions` | `"In Game"` | `F_BIN_DEBUG` | F3 | Debug | **clashes with `F_REPEL` F3 binding** |
| `DebugFunctions` | `"In Game"` | `F_BODY_DEBUG` | F4 | Debug | **clashes with `F_REPEL` F4 binding** |
| `DebugFunctions` | `"In Game"` | `F_CONTACT_DEBUG` | F4+Shift | Debug | chord clash with F4 |
| `DebugFunctions` | `"In Game"` | `F_PROBE_DEBUG` | F4+Ctrl | Debug | chord clash with F4 |
| `DebugFunctions` | `"In Game"` | `BinStatusState.F_PHYSICS_DUMP` | F3+Shift | Debug | chord clash with F3 |
| `ToolFunctions` | `"In Game"` | `F_MAIN_TOOL` | MOUSE1 | Tool | **clashes with `F_MOUSE1`** |
| `ToolFunctions` | `"In Game"` | `F_ALT_TOOL` | MOUSE2 | Tool | **clashes with `F_MOUSE2`** |
| `LightingTunerState` | (no group) | `F_LIGHTING_TUNER` | F8 | Debug | dev-only |
| `HelpState` | (no group) | `F_HELP` | F1 | System | help overlay |
| `CameraMovementFunctions` | `"Movement"` | `F_Y_LOOK / F_X_LOOK / F_MOVE / F_STRAFE / F_ELEVATE / F_ZOOM / F_RUN` | WASD + arrows + Q/Z + LSHIFT + mouse axes | Camera | **commented out** in Main.java; group string also clashes with `AvatarMovementFunctions` "Movement" |

Critical findings: `MainGameFunctions`, `DebugFunctions`, and `ToolFunctions` share group string `"In Game"` — group-level toggling is impossible without renaming. `AvatarMovementFunctions.mapActions` lines 139-153 has a copy-paste bug producing 4 redundant `F_REPEL` mappings and 4 unmapped intended functions. `F12` is already taken by `F_THOR`, so the user's F12-toggle target conflicts with gameplay. Six FunctionId constants are declared but never mapped (`F_RUN` afterburner, `F_MINE`, `F_PORTAL`, `F_MULTI`, `F_ANTI`, `F_STEALTH`, `F_CLOAK`, `F_XRADAR`).

## Background — Subspace / Continuum canonical bindings

Compiled from Continuum 0.40-class clients + community wikis; canonical key column is the most-common default. **Status** column reflects the Infinity wiring vs canon: ✅ wired, ⚠ wired with different key, ❌ missing.

| Subspace function | Canonical key | Infinity status | Notes |
|---|---|---|---|
| Thrust forward | UP | ✅ | matches |
| Reverse | DOWN | ✅ | wired as `F_THRUST` Negative axis |
| Rotate left | LEFT | ✅ | wired as `F_TURN` |
| Rotate right | RIGHT | ✅ | wired as `F_TURN` Negative axis |
| Afterburner (held) | LSHIFT | ❌ | `F_RUN` declared, never mapped; LSHIFT is `F_SHIFT` modifier |
| Fire bullet | LCTRL | ✅ | matches |
| Fire bomb | TAB | ✅ | S2 landed |
| Fire mine | LSHIFT+TAB | ❌ | `F_MINE` declared, never mapped; needs chord with `F_SHIFT` |
| Multi-fire toggle | LSHIFT+LCTRL | ❌ | `F_MULTI` declared, never mapped |
| Burst | DELETE | ✅ | matches Continuum |
| Thor | END (or shift+chord) | ⚠ | wired to **F12** (dev-keyboard convenience); Continuum default is END or chord |
| Repel | LSHIFT (tap) | ⚠ | wired to F3/F4/F5/F7 (broken guards); LSHIFT is the canonical key |
| Warp (random) | INSERT | ✅ | matches |
| Portal drop | INSERT (chord with shift) | ❌ | `F_PORTAL` declared, never mapped |
| Decoy | F5 (Continuum) / `'` | ❌ | `F_DECOY` guard-broken |
| Brick drop | F4 (Continuum) / `;` | ❌ | `F_BRICK` guard-broken |
| Rocket | F3 (Continuum) | ❌ | `F_ROCKET` guard-broken |
| Attach to teammate | F7 | ❌ | `F_ATTACH` guard-broken |
| Antiwarp toggle | LSHIFT+A | ❌ | `F_ANTI` declared, never mapped |
| Stealth toggle | LSHIFT+S | ❌ | `F_STEALTH` declared, never mapped |
| Cloak toggle | LSHIFT+C | ❌ | `F_CLOAK` declared, never mapped |
| XRadar toggle | LSHIFT+X | ❌ | `F_XRADAR` declared, never mapped |
| Help screen | F1 | ✅ | wired via HelpState |
| Player list | F2 | ✅ | matches |
| Energy display toggle | F3 | ⚠ | F3 currently bound to `F_REPEL` + `F_BIN_DEBUG`; Continuum default is energy-bar toggle |
| Spectate cycle | F8 / F9 | ❌ | not wired |
| Ship-change menu | KEY_0 (Continuum) | ❌ | individual KEY_1..KEY_8 work, no menu open |
| Radar zoom | F5 (Continuum) | ❌ | not wired |
| Statbox toggle | F6 | ❌ | not wired |
| Send team chat | `=` | ❌ | not wired (Enter opens generic chat) |
| Send private msg | `;` | ❌ | not wired |
| In-game menu | ESC | ✅ | matches |
| Chat console | RETURN | ✅ | matches |
| Find player | `?find <name>` | n/a | chat-typed; out of binding scope |
| Goto arena | `?go <arena>` | n/a | chat-typed |
| Spec / unspec | `?spec` / `?obs` | n/a | chat-typed |
| Self-kill | `?kill` | n/a | chat-typed |

Gap summary: of ~25 player-facing keyboard-bound canon functions, **8 are wired correctly** (movement axes, bullet, bomb, burst, warp, menu, chat, player list, ship-select 1-8, help), **2 are wired with off-canon keys** (Thor on F12, Repel on F-keys instead of LSHIFT), and **15 are missing or broken** (Afterburner, Mine, Multi, Portal, Decoy, Brick, Rocket, Attach, all 4 status toggles, energy display, spectate, ship-change menu, team/private chat). The "broken guards" in `mapActions` is the largest single point of bug compression.

## Clash analysis

| Key (or chord) | Game-side function | Debug-side function | Resolution under DebugState toggle |
|---|---|---|---|
| F12 | `F_THOR` (fire) | (proposed) DebugState toggle | DebugState toggle binds to a NEW system-group function, not `F_THOR`. Move `F_THOR` to a different key (e.g., END per canon). |
| F3 | `F_REPEL` (broken) | `F_BIN_DEBUG` (toggle bin overlay) | When DebugState detached: `F_REPEL` reachable on its canonical LSHIFT (after the broken-guard fix). When DebugState attached: deactivate the `Game` group containing `F_REPEL`'s F3 mapping (after fix, F3 is no longer bound to F_REPEL anyway). |
| F4 | `F_REPEL` (broken) | `F_BODY_DEBUG` | Same shape — F4 routes to `F_BRICK` after canon fix; deactivate Game group when DebugState attached. |
| F5 | `F_REPEL` (broken) | (no debug user) | F5 routes to `F_DECOY` after canon fix; no clash. |
| F7 | `F_REPEL` (broken) | (no debug user) | F7 routes to `F_ATTACH` after canon fix; no clash. |
| MOUSE1 | `F_MOUSE1` (tile-edit) | `F_MAIN_TOOL` (Tool group) | Both are redundant mappings of the same physical button under the same `"In Game"` group string. Consolidate: keep one Game `F_MOUSE1`, drop `F_MAIN_TOOL` OR move tools to a `Tool` group activated by a separate AppState. |
| MOUSE2 | `F_MOUSE2` | `F_ALT_TOOL` | Same shape as MOUSE1. |
| WASD | `F_MOVE` / `F_STRAFE` (CameraMovementFunctions, group `"Movement"`) | (commented out at boot) | Camera is currently dormant, but if re-enabled the group string overlaps with `AvatarMovementFunctions.G_MOVEMENT` ("Movement") — both ships' axes and camera's would be active simultaneously. Rename one before re-enabling. |
| LSHIFT | `F_SHIFT` (chord modifier) | (no clash) | Acts as modifier; canonical Repel is "tap LSHIFT", which Lemur differentiates from "hold LSHIFT then chord." Wiring requires care — see open question. |

## Architecture

### Group partitioning (slice 1)

Every `FunctionId` carries a group string in its constructor. Lemur's `InputMapper.activateGroup(group)` / `deactivateGroup(group)` flips on/off all FunctionIds in that group. The new partition:

| New group | Members | Default state at boot |
|---|---|---|
| `Game` | every gameplay binding (Movement axes, weapons, actions, toggles, ship-select, in-game menu, player list, chat, tile-edit mouse) | active |
| `Debug` | `F_BIN_DEBUG`, `F_BODY_DEBUG`, `F_CONTACT_DEBUG`, `F_PROBE_DEBUG`, `F_PHYSICS_DUMP`, `F_LIGHTING_TUNER` | inactive |
| `System` | `F_HELP`, F12 (new) `F_DEBUG_TOGGLE` | active (F1/F12 always live) |
| `Camera` | `CameraMovementFunctions` axes (when re-enabled) | inactive |
| `Tool` | dev-only tool overlays | inactive |

The current ad-hoc group strings (`"In Game"`, `"Movement"`, `"Map"`, `"Ship"`, `"Tower"`, `"Toggles"`, `"Actions"`, `"Weapons"`, `"Alternative"`) become **subgroup names** appended to the group, e.g. `Game.Movement`, `Game.Weapons`, `Debug.Physics`. Lemur's group string supports arbitrary content; the dotted convention is purely organizational and `activateGroup("Game")` toggles every Game-prefixed group at once iff we maintain a registry of subgroups (or call `activateGroup` for each `Game.*` explicitly).

### `DebugState` AppState shape (slice 2)

```java
package infinity.client.states;

public class DebugState extends BaseAppState {
    private InputMapper inputMapper;

    @Override
    protected void initialize(final Application app) {
        inputMapper = GuiGlobals.getInstance().getInputMapper();
    }

    @Override
    protected void onEnable() {
        // Toggle off Game-side bindings that clash with debug equivalents.
        // Keep System group (Help/F12-toggle) always active.
        inputMapper.deactivateGroup(GameFunctions.GROUP);  // every Game.* subgroup
        inputMapper.activateGroup(DebugFunctions.GROUP);
    }

    @Override
    protected void onDisable() {
        inputMapper.deactivateGroup(DebugFunctions.GROUP);
        inputMapper.activateGroup(GameFunctions.GROUP);
    }
}
```

A `MainGameFunctions.F_DEBUG_TOGGLE` listener (always active in `System` group) attaches/detaches `DebugState` from the state manager on F12 press. F12 must be moved off `F_THOR`.

### Boot sequence change (slice 2)

`Main.simpleInitApp`:
- Calls `initializeDefaultMappings` for every Functions class, with the new group partition (slice 1's diff).
- Activates `Game` and `System` groups; leaves `Debug` / `Camera` / `Tool` inactive.
- Does NOT attach `DebugState` (per user requirement).
- F12 listener is in the `System` group, ready to attach `DebugState` on press.

## Slices

Each slice is independently mergeable; manual smoke-test confirms behaviour because there's no automated keyboard-test harness today.

| # | Slice | Files | Acceptance |
|---|---|---|---|
| **0** | **Catalog tracker** — write `.scratch/debug-state-bindings/CATALOG.md` capturing today's full FunctionId inventory + Subspace canon target list. No code change. | `.scratch/debug-state-bindings/CATALOG.md` (new) | The current-state and target-state tables are rendered + checked in; future slice diffs flip rows in this file. |
| **1** | **Fix the broken `mapActions` guards.** Lift the four `if (!hasMappings(F_X)) inputMapper.map(F_REPEL, …)` blocks to map the function the guard names. Document the old behaviour in the commit. | `AvatarMovementFunctions.java` | After the fix, `F_DECOY` maps F5, `F_ROCKET` maps F3, `F_BRICK` maps F4, `F_ATTACH` maps F7. `F_REPEL` ends up unmapped (deferred to slice 3 along with LSHIFT canon). Existing build passes; manual smoke confirms F5/F4/F3/F7 trigger their declared actions. |
| **2** | **Group partition + `DebugState` + F12 toggle.** Rename group strings on every FunctionId (`MainGameFunctions` "In Game" → "System", `DebugFunctions` "In Game" → "Debug.Physics", `ToolFunctions` "In Game" → "Tool", `AvatarMovementFunctions.G_*` → "Game.Movement" / "Game.Weapons" / etc.). Add `MainGameFunctions.F_DEBUG_TOGGLE` (group System, F12). Add `DebugState extends BaseAppState`. Move `F_THOR` off F12 (e.g., to END per canon). `Main.simpleInitApp` activates Game + System, leaves Debug inactive. | `MainGameFunctions.java`, `DebugFunctions.java`, `ToolFunctions.java`, `LightingTunerState.java`, `HelpState.java`, `AvatarMovementFunctions.java`, `Main.java`, `infinity/client/states/DebugState.java` (new) | F12 attaches/detaches `DebugState`; in detached state Game bindings work + Debug ones don't; in attached state Debug bindings work + Game movement/weapons don't. Manual smoke. |
| **3** | **Repel → LSHIFT (canon) + F_RUN → afterburner.** Rebind `F_REPEL` to LSHIFT-tap (Lemur's `InputState.Positive` on press, with a configurable hold-vs-tap discrimination). Rebind `F_RUN` to LSHIFT-hold for afterburner. Resolve the LSHIFT-as-modifier-vs-action ambiguity (see open Q1). | `AvatarMovementFunctions.java`, possibly new helper for tap-vs-hold dispatch | LSHIFT tap fires Repel; LSHIFT hold engages afterburner; LSHIFT+letter chords still trigger toggles (slice 4). |
| **4** | **Status toggles cluster — Antiwarp / Stealth / Cloak / XRadar.** Wire `F_ANTI` / `F_STEALTH` / `F_CLOAK` / `F_XRADAR` to LSHIFT+A/S/C/X chords. | `AvatarMovementFunctions.java` | Each toggle dispatches a `ToggleAction` RMI to the server; component flip visible in HUD. |
| **5** | **Mine / Multi / Portal cluster.** Wire `F_MINE` to LSHIFT+TAB. Wire `F_MULTI` to LSHIFT+LCTRL. Wire `F_PORTAL` to LSHIFT+INSERT. | `AvatarMovementFunctions.java` | Mine drops with LSHIFT+TAB; Multi-fire toggles with LSHIFT+LCTRL; Portal drops with LSHIFT+INSERT. F6/S7 mine slice presumed landed by this point. |
| **6** | **System cluster — energy display, spectate cycle, statbox.** Wire `F_ENERGY_DISPLAY` to F3 (Game, since it's a player-facing HUD toggle), move `F_BIN_DEBUG` off F3 (e.g., F3 only when DebugState attached → Debug subgroup uses F3 freely after group partition). Wire `F_SPECTATE_CYCLE` to F8 or F9. Wire `F_STATBOX` to F6. | `MainGameFunctions.java`, `AvatarMovementFunctions.java` (depending on consumer) | Per-key smoke confirmation. |
| **7** | **HelpState content refresh.** Update HelpState's enumerated bindings to surface the new group structure + every newly-wired binding. | `HelpState.java` | F1 in detached-debug state shows Game bindings only; F1 in attached-debug state shows Debug bindings. (Open Q3 — could also list both.) |
| **8** | **Camera group re-enable** (optional, only if dev wants WASD camera back). Uncomment `CameraMovementFunctions.initializeDefaultMappings` in Main; rename its `GROUP_MOVEMENT` to `Camera.Movement`; let `DebugState.onEnable` activate `Camera`. | `Main.java`, `CameraMovementFunctions.java` | When DebugState attached, WASD moves camera; when detached, WASD does nothing (avatar uses arrows). |

## Open questions

1. **LSHIFT as canonical Repel-tap vs the existing `F_SHIFT` modifier.** Continuum's Repel fires on LSHIFT press (no chord). But Infinity uses LSHIFT as a chord modifier (`F_SHIFT` group `"Alternative"`). Two reconciliations: (a) Lemur tap-vs-hold semantics — short tap = Repel, hold = chord modifier (afterburner). Risk: tap detection latency hurts twitch play. (b) Move Repel to a different key (e.g., END), accept divergence from canon. Lean (a) — it's what Continuum players expect, and Lemur's `InputMapper` does support `Pressed` vs `Released` events the consumer can disambiguate.

2. **F-key layout: which Continuum default to honor for F3-F8.** Continuum has F3=energy-toggle, F4=help, F5=radar-zoom, F6=statbox, F7=attach, F8=spectate-cycle. Infinity has F3/F4 hijacked by debug (slice 2 partition fixes), F8 hijacked by `F_LIGHTING_TUNER`. Resolution: when DebugState detached the F-keys honour Continuum canon (F3=energy, F5=radar, F6=statbox, F7=attach, F8=spec); when DebugState attached the F-keys are free for debug overlays (`F_BIN_DEBUG` / `F_BODY_DEBUG` / `F_LIGHTING_TUNER` / etc.). This is exactly the toggle design's job to handle. Confirm direction in slice 2 review.

3. **HelpState content under DebugState attached vs detached.** Should F1 show one binding list or both? Lean: HelpState reads `inputMapper.getActiveGroups()` (or equivalent) and renders only currently-active groups. That way the help content reflects the actual key surface when the user presses F1.

4. **DebugState toggle persistence.** Saved across launches? Lean **no** — session-only matches the user's stated default ("DebugState NOT attached by default") and avoids a settings.json schema. Reopening dev's last state is a downstream feature.

5. **Tool group's existence.** `ToolFunctions` registers `F_MAIN_TOOL` / `F_ALT_TOOL` on MOUSE1/2 — exactly the same buttons as `F_MOUSE1`/`F_MOUSE2` in `AvatarMovementFunctions`. Either Tool is dead code from a Simsilica-template stage and should be deleted, or the two Functions classes describe two distinct features and the codebase needs to disambiguate them. Lean **delete `ToolFunctions`** unless someone names a current consumer; the Functions class hasn't grown since the Simsilica-1.0 template imported it.

6. **`F_THOR` new key.** Continuum has Thor on END. F11 also free. Lean END for canon fidelity. Alternatives: END, F11, BACKSLASH (currently `F_GRAVBOMB`). Tabled until slice 2 — the move-off-F12 is forced; the destination is bikeshedding.

## Acceptance

End-to-end manual + targeted unit-test acceptance:

- **Group partition (slice 2):** `git grep -n 'new FunctionId(' infinity/src/main` shows every FunctionId in one of the 5 canonical groups (Game.* / Debug.* / System / Camera.* / Tool). No FunctionId carries a group string outside that taxonomy.
- **`DebugState` boot state:** Cold launch into trench-04 — `:infinity:run`. State manager queried via `app.getStateManager().getState(DebugState.class)` returns null. F1 (Help) shows Game-side bindings. F3-F4 trigger Game functions (Rocket / Brick), not debug overlays.
- **F12 toggle on:** Press F12 — `getStateManager().getState(DebugState.class)` returns non-null + `isEnabled()` true. F1 now shows Debug bindings. F3 now triggers `F_BIN_DEBUG` (overlay appears), not `F_ROCKET`. Avatar movement (UP/DOWN/LEFT/RIGHT) unchanged — those are System or remained Game-active per design choice (likely System, since debugging often wants to drive around).
- **F12 toggle off:** Press F12 again — DebugState detached. F3 triggers Rocket again. Movement unchanged.
- **Bug-fix assertion (slice 1):** `git grep -n 'F_REPEL' infinity/src/main/java/infinity/client/AvatarMovementFunctions.java` returns exactly **one** mapping line (the LSHIFT mapping, slice 3) — not four.
- **Mapping diagnostics:** `HelpState.dumpInputMappings(inputMapper)` printed at boot has zero "function declared but not mapped" entries for active groups; the previous 6+ unmapped declarations are all wired. Adding `assert inputMapper.hasMappings(F_X)` in a unit test for a sentinel set of expected functions provides a regression hook.
- **Continuum-fidelity test:** A cold-launch player using stock Continuum muscle memory can complete the canonical action-cycle (thrust → fire bullet → fire bomb → drop mine → repel) without consulting docs.
- **PMD baseline:** No new violations on touched files; ratchet one lowest-effort fix per slice.

## Tracker hygiene

- `.scratch/debug-state-bindings/CATALOG.md` (new in slice 0) tracks current vs target state per binding row; flipped by each subsequent slice as the work lands.
- Slice 0's CATALOG.md row format mirrors the inventory tables above so that slice diffs are 1-line flips.
