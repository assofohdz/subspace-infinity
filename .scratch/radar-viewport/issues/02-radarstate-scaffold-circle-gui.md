# RadarState scaffold + circle HUD

Status: needs-triage
Parent: [../PRD.md](../PRD.md)
Labels: area:client

## What to build

New `RadarState` AppState alongside `ModelViewState` (`infinity/src/main/java/infinity/client/states/`). It owns:

- `radarRoot` Node (NOT attached to the main `rootNode`) with two empty children: `radarEntityRoot` (populated in #03) and `radarBlockRoot` (populated in #04)
- An offscreen orthographic `Camera` + `ViewPort` + `FrameBuffer` rendering `radarRoot`, modeled on the viewport setup in `MiniMapState`
- A GUI quad attached to `guiNode`, reusing `MatDefs/MiniMap/MiniMap.j3md` (circle mask + overlay)
- A local-avatar watch that lazy-resolves the avatar EntityId in `update()` (RMI is async; per existing client-ECS pattern) and follows the avatar in XZ
- Camera frustum sized from the avatar's `RadarRange` component; recomputes when `RadarRange` changes (e.g. ship swap)

`RadarState` is wired into `GameSessionState` init order after `ArenaRegistryState` and `ModelViewState`. `MiniMapState` is retired in this slice — its `mapRoot` constructor parameter encodes the broken assumption that blocks were entities. Lift the ~30 lines of viewport / camera / GUI-quad setup, then delete the file and remove its registration.

EntitySets / watches created in `initialize()`, released in `terminate()` (per `entity-sets.md`).

## Acceptance criteria

- [ ] `RadarState extends BaseAppState` exists; `radarRoot`, `radarEntityRoot`, `radarBlockRoot` created in `initialize()`
- [ ] Offscreen viewport renders `radarRoot` to a `FrameBuffer`-backed texture
- [ ] GUI quad with the circle mask material visible on the HUD; positioned similarly to current `MiniMapState`
- [ ] Camera follows local avatar position
- [ ] Camera frustum recomputes from local avatar's `RadarRange` (`ed.watchEntity` per `feedback_client_ecs_reads`)
- [ ] `RadarState` registered in `GameSessionState` init order
- [ ] `MiniMapState` deleted and unregistered (no dangling references)
- [ ] All EntitySets / watches released in `terminate()`
- [ ] Manual verification: empty circle appears on HUD, follows the player, no warnings/errors at startup

## Blocked by

- [01-radar-component-foundations](01-radar-component-foundations.md)

## Comments
