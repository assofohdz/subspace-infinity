# Implement SubspaceServer module archetypes (parent)

Status: ready-for-human
Cross-ref: [GH #86](https://github.com/assofohdz/subspace-infinity/issues/86)
Labels: area:modules

## Background

Subspace Infinity's arena model now mirrors a canonical SVS (Standard VIE Settings) layout: thin `arena.conf` files that `#include` shared rule fragments from `infinity/zone/conf/{base,svs,svs-league,svs-pb,svs-tce,svs-turf}/`. The five `conf/svs*` folders hold canonical VIE-derived tunings.

What's missing: the **behavior** those configs reference. Each canonical SVS arena turns on one or more server **modules** that implement the actual gameplay — scoring, matchmaking, ship enforcement, territory control. Without those modules, arena configs are data-only: the settings load but nothing acts on them.

This parent tracks implementing each module as an Infinity game system or `ArenaModule` implementation. Sub-issues live under `issues/`, one per module.

## Arena archetype → module map

| Archetype | Arenas | Modules required |
|---|---|---|
| Dueling (1v1) | `duel` | OneVersusOneStats |
| Small-team match (2v2–4v4) | `2v2pub/league`, `3v3pub`, `4v4pub/league/prac` | TeamVersusStats, MatchFocus, MatchLvz, RecklessPlayPenalty |
| Captain-drafted comp | `4v4caps` | CaptainsMatch + above |
| Flag carry/hold | `warzone`, `jackpot`, `running` | FlagGamePoints, KillPoints |
| Rabbit chase | `rabbit` | FlagGamePoints, KillPoints |
| Territory (static flags) | `turf`, `tce` | StaticFlags, KillPoints |
| King of the Hill | `king` | Koth, KillPoints |
| Speed zone (timed FFA) | `speed` | SpeedGame, KillPoints |
| Soccer (PowerBall) | `pb` | LegalShip, BallGamePoints |

## Implementation priority

### Phase 1 — biggest archetype unlock per line of code

- [ ] [01-killpoints](issues/01-killpoints.md) — used by every scoring arena
- [ ] [02-flag-game-points](issues/02-flag-game-points.md) — warzone, jackpot, running, rabbit
- [ ] [03-koth](issues/03-koth.md) — king
- [ ] [04-one-versus-one-stats](issues/04-one-versus-one-stats.md) — duel

### Phase 2 — mode-specific

- [ ] [05-speed-game](issues/05-speed-game.md) — speed
- [ ] [06-ball-game-points](issues/06-ball-game-points.md) — pb
- [ ] [07-legal-ship](issues/07-legal-ship.md) — pb ship constraints
- [ ] [08-static-flags](issues/08-static-flags.md) — turf, tce (covers PersistentTurfOwners)

### Phase 3 — matchmaking / competitive stack

- [ ] [09-team-versus-stats](issues/09-team-versus-stats.md)
- [ ] [10-match-focus](issues/10-match-focus.md)
- [ ] [11-match-lvz](issues/11-match-lvz.md)
- [ ] [12-reckless-play-penalty](issues/12-reckless-play-penalty.md)
- [ ] [13-captains-match](issues/13-captains-match.md)

## Sub-issue template

Each sub-issue follows: behavioral description, arenas that need it, settings keys it reads, and integration surface (events / EntitySets on Infinity side).

## References

- Arena configs: `infinity/zone/arenas/*/arena.conf`
- Shared fragments: `infinity/zone/conf/{base,svs,svs-league,svs-pb,svs-tce,svs-turf}/`
- `create-module` skill: `.claude/skills/create-module/`
- `arena-settings` skill: `.claude/skills/arena-settings/`
- `sio2-system` skill: `.claude/skills/sio2-system/`

## Comments
