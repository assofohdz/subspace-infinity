# Bot AI v3 — Backlog

Carried-over work that isn't a numbered v3 issue yet. Items land here from
status reconciliations and reviews; promote to an `issues/NN-*.md` slice when
picked up. Per CLAUDE.md #6, **delete** a row when it lands or is formally
descoped — don't strikethrough.

## Carried from bot-ai v1 (2026-05-26 reconciliation)

### B1 — Flocking / boids steering (v1 slice #07, never built)

Source: [bot-ai/issues/07-flocking-blended-steering.md](../bot-ai/issues/07-flocking-blended-steering.md)

v1 shipped without flocking. None of `Separation`, `Cohesion`, `Alignment`,
`BlendedSteering` exist in `api/src/main/java/infinity/ai/steer/`. Slice #08
shipped anyway, dropping the #07 dependency — the `BotBrainConfig`
flocking-weight fields (`flockingSeparationWeight`, etc.) exist but feed
nothing.

**Open decision (needs-human): build vs descope.** v2 added scalar density
fields (`TeamDensityField`, `ArenaDensity`, the `follow-traffic` behaviour)
that may already satisfy the original intent ("allied bots maintain spacing,
collectively pursue, don't dogpile into one point"). Two paths:

- **Descope** — declare flocking covered by v2 density fields; delete the dead
  `flocking*Weight` fields from `BotBrainConfig`; close #07 `wontfix`.
- **Build** — implement the boids triad + `BlendedSteering` as a proper v3
  slice (unit-tested per the v1 acceptance criteria: spacing maintained, no
  collisions, no dogpile), and wire the existing `BotBrainConfig` weights.

Recommend deciding by playtesting current v2 allied-bot behaviour: if bots
already spread acceptably, descope; if they dogpile, build. Until decided,
the `flocking*Weight` config fields are dead and should be flagged as such.

### B2 — Tuning-knob externalisation tail (v1 slice #08, partial)

Source: [bot-ai/issues/08-groovy-ccp-scaling-archtest.md](../bot-ai/issues/08-groovy-ccp-scaling-archtest.md)
criterion "All hard-coded brain/steering constants now live in `bot-tuning.groovy`".

**Already a v3 issue — not a loose backlog item.** Fully covered by
[bot-ai-v3 #02 — tuning-knob migration](issues/02-tuning-knob-migration.md)
(~12 reactive-steering / derivation / perception constants still in Java).
Listed here only so the v1 #08 trail has a forward pointer. Delete this row
once #02 lands.

## Notes deferred from the 2026-05-26 review (not promoted to issues)

### B3 — `lvl_flowfield_check.py` erosion mismatch (diagnostic tooling)

Source: review finding H1 (fields agent). `scripts/lvl_flowfield_check.py`
uses a symmetric `(2N+1)²` clearance check; the server's
`NavGrids.erodeFootprint` uses unilateral 2×2 Minkowski erosion. The script
can report false `UNREACHABLE` for 2-wide corridors the server actually
routes through. Not bot-AI runtime — it's the nav-debug tool the
[diagnose-nav-from-data] workflow relies on, so worth fixing, but it lives
outside the v3 code slices. Promote to a tooling issue if nav debugging hits
the false-negative.

### B4 — LOW-severity perf micro-opts (review, deferred per PRD out-of-scope)

Source: fields/brain agents, all LOW. Not worth their own slices; fold into
the relevant #02/#03 work only if the file is already open:
- `DijkstraDistanceField` heap-entry `long[]` allocation per push (worker thread, acceptable at current scale).
- `AsyncNavigationFields` `ConcurrentHashMap` → `HashMap` (access is single-threaded per its own invariant).
- `ThreatField`/`*DensityField` squared-distance compare to skip `Math.sqrt` on out-of-disc cells.
- `new Quatd()` allocated per bot per tick in `MovementInput` ctor (`BotBrainSystem:264`) — reuse a static identity.
