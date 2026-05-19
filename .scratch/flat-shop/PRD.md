# FlatShop — buy-prizes-with-points implementation PRD

Status: ready-for-agent
Cross-ref: extends `arena-modules/PRD.md` F2.9 (the FlatShop stub that landed for the F2 capstone); depends on F2.7 (PlayerTotalScore via ScoreCoordinatorSystem) and the existing `PrizeConsumptionSystem` applier dispatch.
Labels: area:modules, area:shop, area:scoring

## Background

The arena-modules F2.9 slice landed `FlatShop` as a zero-config stub satisfying the `ShopModule` category for the F2 FFA capstone arena. It loads cleanly, logs once, and registers no chat commands. There is no way to actually purchase a prize in-game today.

This PRD fleshes out the shop into a working buy-prizes-with-points mechanic that matches Subspace canon (`[Cost]` per-prize point cost table + `PurchaseAnytime` safe-zone gate + `!buy <prize>` canon chat command) while wiring it cleanly into the existing scoring + prize-applier pipelines.

## Resolved decisions (this session)

| # | Decision | Outcome |
|---|---|---|
| D1 | Chat command prefix | Canon `!` prefix. `!shop` lists available prizes + their cost; `!buy <prize>` purchases. (`~` reserved for admin / debug commands per existing convention.) |
| D2 | `Gold` entity component | Unused tech debt — remove entirely. The Subspace shop never used a separate currency; points are the spend pool. |
| D3 | Spend currency | `PlayerTotalScore`. Players spend from total. `PlayerRoundScore` + `PlayerMatchScore` are read-only earnings tallies — they count how much was *earned* during the current round/match but are not deducted on purchase. |
| D4 | Cost source | `[Cost]` per-prize table from Subspace REFERENCE.md, declared as kwargs on the `flat-shop` module (operator-tunable per arena). |
| D5 | `PurchaseAnytime` | Canon two-state (`0` = safe-zone only, `1` = anywhere). Default `false` (canon default). |

## Scope

### In scope

- Remove `Gold` entity component and its sole writer (`ResourceSystem`'s gold-touch handler). Verify no read consumers exist.
- Add `FlatShopConfig` typed record with `costs` (per-prize cost map) + `purchaseAnytime` boolean.
- Wire `FlatShop` to register `!shop` + `!buy <prize>` chat commands per arena. Same registration shape `ArenaCommandsSystem` / `FrequencySystem` use today.
- Introduce a `PlayerScoreSpend(int amount)` transient component drained by `ScoreCoordinatorSystem` — deducts only from `PlayerTotalScore` (NOT round/match). Preserves the canonical-writer rule.
- `!buy <prize>` flow: read `PlayerTotalScore`, look up cost, validate affordable, emit `PlayerScoreSpend` + dispatch via `PrizeConsumptionSystem.applyPrizeByName`.
- `PurchaseAnytime: false` gate — when the player is NOT on a safe-zone tile, refuse with a chat-reply explaining why.
- Update `zone/arenas/ffa/arena.groovy` to use the kwargs-bearing form (`shop 'flat-shop', purchaseAnytime: 1, costs: [Bomb: 500, ...]`).

### Out of scope (deferred to follow-on slices)

- `MultiPrize` mechanic (single buy → bundle of random prizes) — separate slice; needs `[Prize] MultiPrizeCount` + `PrizeFactor` integration.
- `PrizeNegativeFactor` (probability that a bought "prize" is actually negative). Subspace edge case; defer.
- Discount / multiplier modifiers (per-team, per-event).
- Chat-reply UX polish (currently plain text via existing chat poster; styled / clickable shop UI is a later HUD slice).
- Shop UI on the client side. Chat-only for v1.

## Design

### Currency model (per D3)

```
PlayerTotalScore   ← spendable; decreases on !buy
PlayerRoundScore   ← tally only; cleared on round-end via ScoreReset(ROUND)
PlayerMatchScore   ← tally only; cleared on match-end via ScoreReset(MATCH)
```

`KillPointsScoring` (and any future earning module) emits `PlayerScoreChange(+delta)` which `ScoreCoordinatorSystem` adds to ALL three tiers. Shop introduces a new transient that affects only the total tier:

```java
// api/src/main/java/infinity/es/score/PlayerScoreSpend.java
public record PlayerScoreSpend(int amount) implements EntityComponent {
  public PlayerScoreSpend { /* validate amount > 0 */ }
}
```

`ScoreCoordinatorSystem` gains a parallel drain — drains `(PlayerScoreSpend, ChangeTarget)` transients and subtracts only from `PlayerTotalScore`. Round / Match tiers unchanged. One-shot per ADR-0001 (transient destroyed after drain).

### `FlatShopConfig`

```java
// api/src/main/java/infinity/config/FlatShopConfig.java
public record FlatShopConfig(
    Map<String, Integer> costs,         // canon prize name → point cost
    boolean purchaseAnytime              // false = safe-zone only (canon default)
) {
  public FlatShopConfig { /* validate cost values >= 0; immutable copy of map */ }
  public FlatShopConfig() { this(Map.of(), false); }
}
```

Kwargs bind via Jackson (same as `FillUpXTeamsConfig` / `RandomRadiusConfig`). Operator-facing form:

```groovy
shop 'flat-shop',
     purchaseAnytime: 1,
     costs: [
         Bomb: 500, Bullet: 300, Burst: 800, Repel: 200, Brick: 200,
         Decoy: 200, Portal: 200, Rocket: 200, Thor: 1500, AntiWarp: 1000,
         Cloak: 1500, Stealth: 1500, XRadar: 1500, MultiFire: 1500,
         Prox: 1000, Super: 5000, Shield: 5000, Shrap: 300,
         Recharge: 500, Energy: 500, Rotation: 500, Bounce: 500,
         Thrust: 500, Speed: 500
     ]
```

Unspecified prizes default to "not for sale" — `!shop` omits them.

### Chat commands

Registered via `InfinityChatHostedService.registerPatternTriConsumer` in `FlatShop.onArenaLoad`, unregistered in `onArenaUnload` (no current `unregister` method — see Open work item below; for v1 we accept the registration leaks at module-unload and address in a follow-up).

- **`!shop`** — prints the cost table for the caller's arena, with current `PlayerTotalScore` for context. Format:
  ```
  Shop (you have 2,500 points):
    Bomb       500   Burst      800   Repel    200
    Bullet     300   Brick      200   Decoy    200
    ...
  ```
- **`!buy <prize>`** — case-insensitive prize-name match against config keys; rejects unknown names with the canon prize list.

### Buy-flow state machine

```
!buy <name>
  ↓ resolve <name> → PrizeTypes enum (case-insensitive, canon strings only)
  ↓ ok? else "Unknown prize. Try !shop."
  ↓ lookup cost in FlatShopConfig.costs
  ↓ priced? else "<name> is not for sale here."
  ↓ purchaseAnytime || ed.getComponent(ship, OnSafeZone.class) != null
  ↓ ok? else "Move to a safe zone to buy."
  ↓ readScore = ed.getComponent(player, PlayerTotalScore.class).getValue()
  ↓ readScore >= cost? else "Not enough points (need <cost>, have <readScore>)."
  ↓ EMIT PlayerScoreSpend(cost) + ChangeTarget.self(player)
  ↓ DISPATCH prizeConsumption.applyPrizeByName(<canonName>, ship)
  ↓ chat-reply: "Purchased <name> (-<cost>; <readScore-cost> left)."
```

### `OnSafeZone` marker (for `purchaseAnytime: false`)

Subspace canon: a player is "in safe zone" when their ship is on a tile of type `171` (`VIE_SAFE_ZONE`, defined in `MapTypes.java`). Today there is no runtime component tracking this — a new server-side system polls each ship's position against the loaded map and stamps `OnSafeZone` when over a safe tile, removes it otherwise.

Two implementation options:
1. **Eager `SafeZoneTrackerSystem`** — per-tick: for each ship in arena, sample the map under its position; toggle `OnSafeZone`. Cheap (~one map lookup per ship per tick).
2. **Lazy at-buy-time check** — at `!buy` time, do the tile lookup directly. No new component, no per-tick work. Trade-off: other future mechanics that need "in safe zone" (regen boost, immunity) re-implement the same lookup.

Default to option 1 — it's small, and a `SafeZone` marker is more reusable than a buy-only check. If implementation cost rises beyond ~30 LOC, fall back to option 2.

### Removing `Gold`

Audit shows 4 sites:
- `ShipFactory.createPlayerShip` — `ed.setComponent(result, new Gold(0))` (line 57). DELETE.
- `GameServer.registerSerializers` — `Serializer.registerClass(Gold.class, ...)` (line 596). DELETE.
- `ResourceSystem` — reads + writes `Gold` (lines 18, 61, 63, 102). The whole class appears to be the only consumer; verify there are no `getComponent(_, Gold.class)` reads outside this file. If clean, **delete `ResourceSystem` entirely** (it's the only writer; nothing reads).

Component file deletes:
- `api/src/main/java/infinity/es/Gold.java` — gone.

This is a separate slice (S1) within the FlatShop arc — lands first, independently smoke-tested, before any shop wiring.

## Slice plan

Each slice is independently mergeable + has its own smoke acceptance.

### S1 — `Gold` rip-out (prep)

- Delete `infinity.es.Gold`, `ResourceSystem`, the serializer registration, and the `ShipFactory.createPlayerShip` seed.
- Verify the only reader is `ResourceSystem` itself (now deleted).
- Tests: build + existing test suite pass.
- Smoke: launch arena, kill yourself, swap ships — verify no NullPointerException from a stale Gold read.

### S2 — `PlayerScoreSpend` transient + ScoreCoordinator drain

- Add `infinity.es.score.PlayerScoreSpend` record (api/).
- Register serializer (cross-wire — clients may show "you spent X" toasts later).
- Extend `ScoreCoordinatorSystem` with a second EntitySet for `PlayerScoreSpend + ChangeTarget`, drains by subtracting from `PlayerTotalScore` only.
- Unit test in `ScoreCoordinatorSystemTest`: spend transient decrements total; round + match tiers unchanged.

### S3 — `OnSafeZone` marker

- Add `infinity.es.ship.OnSafeZone` marker component (api/).
- Add `SafeZoneTrackerSystem` (server) that polls each ship's `BodyPosition` against the arena map; toggles `OnSafeZone`. Per-tick, per-ship.
- Unit / integration test against a fixture arena with a known safe-zone tile.
- (If option 2 wins after impl review: skip this slice and inline the lookup in S5.)

### S4 — `FlatShopConfig` + `!shop` listing (read-only)

- Add `FlatShopConfig(Map<String, Integer> costs, boolean purchaseAnytime)` record + validator.
- Update `ModuleCatalog`'s `flat-shop` entry: `configType = FlatShopConfig.class`.
- Extend `FlatShop` to take the config, register `!shop` command. `!buy` still no-op or returns "buy not yet wired".
- Update `zone/arenas/ffa/arena.groovy` to declare the cost map.
- Smoke: load arena, type `!shop`, see the cost table + current total.

### S5 — `!buy` end-to-end

- Wire `!buy <prize>` per the state-machine above. Read PlayerTotalScore, validate, emit `PlayerScoreSpend`, dispatch to `PrizeConsumptionSystem.applyPrizeByName`.
- Unit test for the validator (unknown prize, unpriced prize, insufficient points, safe-zone gate).
- Smoke: kill enough bots to accumulate points, `!buy Bomb`, verify weapon level rises + total drops.

### S6 — Polish (out-of-scope follow-ups)

- Chat-reply formatting (column-align the listing).
- `MultiPrize`-bought bundle.
- Discount modifiers.
- Client-side HUD shop screen (deferred until UI slice).

## Test strategy

- **Unit (per slice)**: S2 score-spend drain; S3 safe-zone tracker; S5 validator branches.
- **Integration smoke**: S5's end-to-end smoke recipe — kill, `!shop`, `!buy`, verify HUD reflects new total + weapon level.
- **Cleanup contract test (post-F3)**: `FlatShop.onArenaUnload` un-registers chat commands. (Chat de-register API is a separate small slice; see Open work below.)

## Open work / known limitations

- **Chat command de-registration.** `InfinityChatHostedService.registerPatternTriConsumer` has no inverse. Until that's added, `FlatShop.onArenaUnload` cannot unregister `!shop`/`!buy`. Acceptable for v1 (arenas don't unload during a session in typical ops); track as a follow-up.
- **Safe-zone tracker performance.** Per-tick position-vs-map lookup for every ship is small but adds work. If it shows in profiling, switch to option 2 (lazy check at buy time).
- **Currency semantics question.** Spending from `PlayerTotalScore` is per-D3. Subspace canon is actually a single per-life score (no separate "total" tier). Our 3-tier (Round/Match/Total) split is a divergence from canon; this PRD treats Total as the canon-equivalent spend currency. If a future gametype wants spend-from-Round-only (e.g. round-locked currency), introduce a new transient + scope flag — don't overload `PlayerScoreSpend`.

## Dependencies / consumers

**Upstream:**
- ADR-0001 (canonical writer rule — `PlayerScoreSpend` drained only by `ScoreCoordinatorSystem`, same shape as `PlayerScoreChange`).
- ADR-0002 (config-component projection — `FlatShopConfig` is template-tier; per-purchase cost lookups are config reads, not component projections).
- ADR-0008 (arena modules — FlatShop is the first `ShopModule` impl with non-trivial behaviour).
- `arena-modules/PRD.md` F2.9 (FlatShop stub).
- REFERENCE.md `## Cost` (canon prize cost knobs).
- REFERENCE.md `## Misc / Spawn` for any safe-zone-related canon (e.g. `SafetyLimit` may or may not apply).

**Downstream:**
- Client HUD shop UI (separate slice, not blocked by this PRD's chat-only v1).
- Subspace gametype slices that want shop-buy mechanics — Trench / KOTH / CTF / etc. can compose `flat-shop` with arena-specific cost tables.
- `MultiPrize` slice (extends FlatShop with bundled purchases).

## References

- [REFERENCE.md `## Cost`](../subspace-ini-reference/REFERENCE.md) — Subspace `[Cost]` per-prize knobs.
- [ADR-0001](../../docs/adr/0001-ecs-component-model.md) — canonical writer rule for the new `PlayerScoreSpend` drain.
- [ADR-0002](../../docs/adr/0002-config-component-projection.md) — `FlatShopConfig` template/instance split.
- [ADR-0008](../../docs/adr/0008-arena-composition-and-modules.md) — arena modules; `ShopModule` category.
- [`arena-modules/PRD.md`](../arena-modules/PRD.md) — F2.9 FlatShop stub that this PRD replaces.
- [`infinity.systems.PrizeConsumptionSystem`](../../infinity-server/src/main/java/infinity/systems/PrizeConsumptionSystem.java) — `applyPrizeByName(String, EntityId)` is the dispatch FlatShop hooks.
- [`infinity.systems.ChecksShipsSystem`](../../infinity-server/src/main/java/infinity/systems/ChecksShipsSystem.java) — canonical example of registering chat commands via `registerPatternTriConsumer`.
- [`infinity.modules.ScoreCoordinatorSystem`](../../infinity-server/src/main/java/infinity/modules/ScoreCoordinatorSystem.java) — gets the second drain for `PlayerScoreSpend`.

## Comments

(none yet)
