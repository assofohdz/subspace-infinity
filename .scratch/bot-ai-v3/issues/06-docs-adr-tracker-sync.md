# Docs / ADR + tracker sync

Status: ready-for-agent
Category: documentation
Type: AFK

## Parent

[Bot AI v3 PRD](../PRD.md) — slice 6. Five ADRs claim `Proposed` /
`partially implemented` for fully-landed code; trackers drifted. No code
dependency — can land independently. Governed by CLAUDE.md rules #4/#5/#6 +
tracker-hygiene.

## Findings

### ADR status flips (code is fully live)

| ADR | Doc says | Actual | Action |
|-----|----------|--------|--------|
| [0009](../../docs/adr/0009-bot-ai-architecture.md) | `Proposed` | all v1+v2 slices done | → `Accepted`; delete RESOLVED items still sitting in "Open work" (input-abstraction; `MovementInput.facing`-dead note belongs in code-todos-backlog) |
| [0010](../../docs/adr/0010-bot-composition-dsl.md) | `Proposed — partially` | slice #01 done, tweak overlay live | → `Accepted` |
| [0013](../../docs/adr/0013-bot-tactical-goal-layer.md) | `Proposed — partially (gated on #07)` | #05+#06+#07 done | → `Accepted` |
| [0014](../../docs/adr/0014-capability-derived-bot-composition.md) | `Proposed — four-multiplier pending #06` | four-multiplier live since 2026-05-26 (c241ab11) | → `Accepted`; remove pending-#06 note. NB: `BotDerivationConfig` open-work is real → tracked in v3 #02, keep that as a partial-acceptance note |
| 0011 / 0012 / 0015 | `Accepted` | matches | OK |
| [0016](../../docs/adr/0016-bot-behaviour-catalog.md) | `Proposed` | catalog is future work | OK (accurate) |

### Tracker / snapshot drift

- `replacement-as-mutation.md` snapshot (`:358`) lists `BotBrainSystem` as writer of `MovementInput`+`BotDebug` but omits `BotCapability` (`BotBrainSystem:496`) and `BotRole` (`:422`) — add both (CLAUDE.md #5).
- [bot-ai-v2/PRD.md](../../bot-ai-v2/PRD.md:124) done-criterion "ADRs 0011/0012/0013/0014/0015 flip to Accepted" is unmet (0013/0014 still Proposed) — reconciled by the flips above; update the PRD note.
- [bot-ai/issues/01-retire-chicken-framework.md](../../bot-ai/issues/01-retire-chicken-framework.md) stuck at `needs-triage` though the move is done — bump to `done`/`ready-for-human` and point at v3 #04 for the deletion (also handled there).
- `engine-bot-ai.groovy` has 30 synergy entries; only 5 have `Behaviour` impls (`engage`/`disengage`/`search`/`follow-traffic`/`hold-position`). Selection correctly gates the rest, but the gap is untracked. Add a header note mapping synergy-only vs implemented, cross-referencing the [behaviour-catalog](../../bot-behaviour-catalog/) 30 issues (1:1). Note `anchor`/`attach-to-anchor` `requires { profile.attachReceive() }` is a permanent `false` placeholder — they silently never fire.
- Broken Javadoc cross-ref: `CapabilityProfile.java:11` `@see docs/bot-ai/capability-derivation.md` — file exists per the review; verify the path/link resolves, else point at ADR-0014.

### SPDX spot-check (CLAUDE.md #2)

Review found api/ + new-server bot-AI `.java` and the bot-AI `.groovy` files
all carry headers. Legacy gaps are handled by deletion in #04. No action here
beyond confirming new files added by v3 #01–#05 get headers.

## Acceptance criteria

- [ ] ADR-0009/0010/0013/0014 status lines updated; resolved "Open work" items deleted (not struck through) per CLAUDE.md #6
- [ ] `replacement-as-mutation.md` snapshot adds `BotCapability`+`BotRole` under `BotBrainSystem`
- [ ] bot-ai-v2 PRD done-criterion reconciled; retirement issue status bumped + cross-linked to v3 #04
- [ ] `engine-bot-ai.groovy` synergy-vs-implemented gap documented + cross-referenced to behaviour catalog
- [ ] `CapabilityProfile` `@see` link verified/fixed

## Comments
