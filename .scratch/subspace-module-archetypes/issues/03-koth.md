# Koth (King of the Hill) module

Status: ready-for-human
Cross-ref: [GH #89](https://github.com/assofohdz/subspace-infinity/issues/89)
Parent: [../PRD.md](../PRD.md)
Labels: area:modules, mode:kill

Part of the parent. Phase 1 — KOTH mode.

## What it does

Crown-based scoring: the crown transfers on kill, awards points over time to the wearer, expires after inactivity.

## Arenas that need it

`king`.

## Settings surface

`[King]` section: `DeathCount` (lives before crown lost), `ExpireTime` (crown decay), `RewardFactor`, `NonCrownAdjustTime`, `NonCrownMinimumBounty`, `CrownRecoverKills` (kills needed to recover crown).

## Integration on Infinity

- Hook: auto-start/stop (`[King] AutoStart=1`), min-players gate
- State: per-player crown component; crown expiry timer
- Depends on: KillPoints (crown-kill rewards), death-event plumbing

## Comments
