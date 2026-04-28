# BallGamePoints module

Status: ready-for-human
Cross-ref: [GH #92](https://github.com/assofohdz/subspace-infinity/issues/92)
Parent: [../PRD.md](../PRD.md)
Labels: area:modules, mode:ball

Part of the parent. Phase 2 — ball/soccer scoring.

## What it does

Scores on ball-goal events, handles ball capture/pass/reward points, enforces capture-based win conditions.

## Arenas that need it

`pb` (PowerBall).

## Settings surface

`[Soccer]` — `Mode`, `BallCount`, `BallLocation`, `SendTime`, `Reward`, `CapturePoints`, `UseFlagger`, `PassDelay`, `AllowBombs`, `AllowGuns`, `BallBounce`.

## Integration on Infinity

- Hook: ball-goal, ball-pickup, ball-pass events
- State: ball entity position + owner
- Depends on: LegalShip (ship-per-frequency in `pb`), basic ball entity model
- Win condition: `CapturePoints` + `WinBy` for goal-difference victory

## Comments
