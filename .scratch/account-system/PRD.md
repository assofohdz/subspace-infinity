# Cross-zone account server

Status: ready-for-human
Cross-ref: [GH #62](https://github.com/assofohdz/subspace-infinity/issues/62) (account system),
[GH #39](https://github.com/assofohdz/subspace-infinity/issues/39) (master server / biller),
[GH #37](https://github.com/assofohdz/subspace-infinity/issues/37) (squadrons),
[GH #19](https://github.com/assofohdz/subspace-infinity/issues/19) (access roles),
[GH #36](https://github.com/assofohdz/subspace-infinity/issues/36) (persisted entities),
[GH #73](https://github.com/assofohdz/subspace-infinity/issues/73) (Steam API)
Labels: area:server, backlog, scope:cross-zone

## 1. Context + motivation

Today an Infinity zone authenticates "players" by a trust-the-client string passed in
[`AccountSession.login(playerName)`](../../api/src/main/java/infinity/net/AccountSession.java).
[`AccountHostedService`](../../infinity-server/src/main/java/infinity/server/AccountHostedService.java)
creates a fresh `EntityId` per connection, stamps a `Name` component, and grants
`AccessLevel.PLAYER_LEVEL`. The session disappears on disconnect; nothing is
persisted. There is no password, no identity, no cross-arena memory of who you
are. Squadrons, persistent K/D, mod role lists, machine bans, and Subspace
"biller" semantics are all open issues
(GH #37 / #19 / #36 / #39) blocked on the same missing piece: a real account.

The Subspace canon model is a *biller* (master server) external to each *zone*
(game server). The biller owns the account; zones authenticate against it; one
account roams across many zones. We want to recover that shape, redrawn as a
modern protocol against Infinity's existing layering
([ADR-0005](../../docs/adr/0005-layered-architecture.md)) and intent-based
mutation discipline ([ADR-0001](../../docs/adr/0001-ecs-component-model.md)).

**In-arena identity** (what zone-local systems use: `EntityId`, `Frequency`,
`ArenaId`, `AccessLevel` for chat-command gating) stays as it is. **Cross-zone
identity** (who *owns* this `EntityId` today, what stats does that account
carry, what squadron are they in) is what this PRD adds.

## 2. Existing state snapshot

| Concern | Today | File |
|---|---|---|
| Login RMI | `AccountSession.login(playerName)` — no password | [`api/net/AccountSession.java`](../../api/src/main/java/infinity/net/AccountSession.java) |
| Login callback | `notifyLoginStatus(boolean)` | [`api/net/AccountSessionListener.java`](../../api/src/main/java/infinity/net/AccountSessionListener.java) |
| Server impl | `AccountHostedService` — creates `EntityId` + `Name`, holds in-memory operator map | [`infinity-server/server/AccountHostedService.java`](../../infinity-server/src/main/java/infinity/server/AccountHostedService.java) |
| Client proxy | `AccountClientService` | [`infinity-client/client/AccountClientService.java`](../../infinity-client/src/main/java/infinity/client/AccountClientService.java) |
| Client UI | `LoginState` — single name field | [`infinity-client/client/states/LoginState.java`](../../infinity-client/src/main/java/infinity/client/states/LoginState.java) |
| Login lifecycle event | `AccountEvent.playerLoggedOn` / `playerLoggedOff` on `EventBus` per [ADR-0003](../../docs/adr/0003-communication-channels.md) | [`api/net/AccountEvent.java`](../../api/src/main/java/infinity/net/AccountEvent.java) |
| Access tiers | `AccessLevel` enum (Player → Owner), no persistence | [`api/sim/AccessLevel.java`](../../api/src/main/java/infinity/sim/AccessLevel.java) |
| Manager contract | `AccountManager` — empty marker | [`api/sim/AccountManager.java`](../../api/src/main/java/infinity/sim/AccountManager.java) |
| Chat-command gating | Prepared but commented out at `InfinityChatHostedService.java:193` | — |
| Reference impl | Simsilica `maccount` (`Account`, `AccountManager`, `Authenticator`, `HashAuthenticator`, `JsonAccountStorage`) | `moss/maccount/` |

The hook surface is small and intentional: anyone wiring a real account
system replaces `AccountHostedService`'s body, swaps the `login()` signature
in lockstep, and the rest of the codebase keeps consuming the existing
`playerLoggedOn` / `playerLoggedOff` `EventBus` events.

## 3. Proposed architecture

### 3.1 Cross-zone account server as a separate process

A new standalone JVM process — working name **`infinity-account-server`** —
hosted as a sibling Gradle module. Per [ADR-0005](../../docs/adr/0005-layered-architecture.md)
it lives **outside** the four-layer DAG: it neither knows about nor depends on
`api/`, `infinity-server/`, or `infinity-client/`. Zones talk to it; clients
*may* talk to it directly for sign-up flows. Crashes do not take any zone down;
a zone running in detached mode (no account server reachable) falls back to a
guest-identity path that mirrors today's behavior.

**Protocol: HTTPS / REST + JWT.** Justified:

- Plain HTTPS works through corporate proxies and is operable with any
  standard load balancer / CDN.
- JSON over HTTPS lets a future web-based zone browser, mod admin panel,
  or Steam-OpenID bridge reuse the same endpoints.
- SpiderMonkey RMI is *not* a fit here. SpiderMonkey is built for low-latency
  in-arena RPC over a long-lived authenticated socket. Cross-zone account
  calls are infrequent (login, stats flush at logoff, squadron lookup) and
  benefit from stateless request-response, CDN edge caching for read-heavy
  endpoints (zone list, public profile), and standard auth tooling.
- gRPC was considered. Rejected because it adds a code-gen toolchain and a
  binary transport that complicates debugging without buying us bandwidth
  we need.

Sample endpoint shape (illustrative — names not pinned):

```
POST /v1/auth/login           { userId, credential }   → { jwt, refreshToken, accountId }
POST /v1/auth/refresh         { refreshToken }         → { jwt }
POST /v1/auth/verify          { jwt, zoneId }          → { accountId, displayName, squadronId?, roles[] }
GET  /v1/account/{id}/stats                            → { kills, deaths, mvp, playtimeSeconds, … }
POST /v1/account/{id}/stats   { delta payload }        → { ok }
GET  /v1/squadron/{id}                                 → { name, tag, members[], roles{} }
GET  /v1/zones                                         → [ { zoneId, host, port, players, ping } ]
POST /v1/zones/{id}/heartbeat { players, status }      → { ok }
```

### 3.2 Identity binding (token shape)

1. Client sends `{ userId, credential }` to the account server.
2. Account server returns a signed JWT (RS256 — public key shipped to zones)
   carrying `accountId`, `displayName`, `roles[]`, `squadronId?`, `iat`, `exp`
   (short — 10 min default), plus an opaque refresh token.
3. Client sends the JWT to a zone via an extended login RMI:
   `AccountSession.login(token)` replaces today's `login(playerName)`.
4. Zone verifies the JWT signature locally (offline — no round-trip per
   login) and optionally calls `POST /v1/auth/verify` only when the JWT
   carries a `verifyRequired` claim (e.g. for elevated roles).
5. On verify, the zone provisions an in-arena `EntityId` and stamps `Name`
   from the JWT's `displayName`. The zone holds the account-id-to-EntityId
   binding in a per-connection attribute (mirrors today's
   `ATTRIBUTE_PLAYER_ENTITYID`).

The current single-string login path becomes a deprecated guest mode (or is
removed for non-dev builds).

### 3.3 Persistent player stats

**Storage tier: cross-zone server is the source of truth.** Zones accumulate
deltas in-memory and flush at clean disconnect / arena change / periodic
heartbeat (e.g. every 60 s). Stat schema deliberately minimal v1:

- `kills`, `deaths`, `playtimeSeconds`, `gamesPlayed`, `mvpCount`
- Per-ship rolls (`kills.warbird`, `kills.spider`, …) — optional, off by default
- `lastSeen`, `firstSeen`, `lastZoneId`

Zones do **not** persist stats locally. A zone restart loses any unflushed
delta — the trade is acceptable for v1; mitigation is shorter flush intervals
under load. Stat writes are idempotent at the delta level (each flush carries
a `requestId` keyed off `accountId + flushOrdinal`).

This matches Subspace canonical biller semantics. The Subspace scoreboard is
zone-local; the cross-zone *score* concept (`scoreid` grouping) lives at the
biller. We're tightening it: every zone reports to the same store, no per-zone
scoreboard fragmentation.

### 3.4 Squadron / clan membership

Squadrons are **cross-zone entities** owned by the account server. A squadron
is `{ squadronId, name, tag, ownerAccountId, members{accountId → role}, createdAt }`.

- Zones resolve squadron affiliation **at login** by reading `squadronId` off
  the JWT. They cache the squadron-name and tag in a per-arena ECS component
  (e.g. `SquadronTag` on the avatar entity, projected at ship-spawn time per
  [ADR-0002](../../docs/adr/0002-config-component-projection.md)).
- Squadron management commands (`?squadcreate`, `?squadinvite`, `?squadkick`,
  `?squadleave`) are zone-side chat commands that proxy to
  `POST /v1/squadron/*` on the account server. Zone caches stale up to ~5 min;
  the JWT refresh path picks up the change at the next refresh.
- Squadron chat is **in-zone for v1** — keyed off the cached `SquadronTag`
  matching. Cross-zone squadron chat is out of scope until a separate chat
  router exists (likely riding the same HTTPS / WebSocket transport).

### 3.5 Cross-arena identity (within a single zone)

Already implicit today via `ArenaSystem`. Nothing changes: one account →
one zone connection → many arena memberships within that zone. Per-arena
state (`ArenaId` on the avatar entity) is unchanged.

### 3.6 Cross-zone account

One account, many zones. The account server is the single source of
truth — the zone never owns "the account," it owns a per-session lease that
expires when the JWT expires. Trust model:

- **Zone → account server: one-way, server-to-server.** Zones authenticate
  to the account server with their own `zoneId` + shared signing key (or
  mTLS in production). Account server's `/zones/*` endpoints reject
  unsigned requests.
- **Client → account server: HTTPS + JWT.** Client never talks to a zone
  without first having a JWT (or a guest claim, for the fallback dev path).
- **Zone → zone: none.** Zones never gossip; everything flows through the
  account server.

Zones are listed in `/v1/zones`; the client's zone-browser UI calls that
endpoint, then dials into the zone of choice with the JWT it already holds.

### 3.7 Auth flows

- **Signup:** Direct to account server (`POST /v1/auth/signup`). Out of scope
  for the zone code path.
- **Login:** Client → account server (HTTPS) → JWT → Client → zone (RMI).
- **Refresh:** Client polls account server with refresh token before JWT
  expiry. If refresh fails (revoked / expired), client is forced to re-auth.
- **Revoke:** Account server maintains a revocation list keyed by `jti`.
  Zones pull the revocation list on a slow interval (1–5 min) — acceptable
  staleness for a v1.
- **Federated auth (optional, v2):** Steam OpenID per
  [GH #73](https://github.com/assofohdz/subspace-infinity/issues/73). The
  account server accepts a Steam ticket on `/v1/auth/login`, validates it
  against Steam's web API, and issues the same JWT. Zones don't know the
  difference. Discord / GitHub / etc. would slot in identically.

## 4. Migration / phasing

The cross-zone server does not exist yet. Build order so each phase delivers a
working slice:

- **Phase 1 — Define API.** Pin the REST + JWT contract as an OpenAPI doc
  under `.scratch/account-system/openapi.yaml`. No code yet. Land alongside
  this PRD's first revision so the contract is reviewable in isolation.
- **Phase 2 — Scaffold the account server.** New Gradle module
  `infinity-account-server` with a minimal HTTP server (Javalin / lightweight
  framework), the JWT signing key plumbing, and `Account` / `AccountManager`
  ports adapted from `moss/maccount`'s `Account`, `AccountManager`,
  `Authenticator`, `HashAuthenticator`, `JsonAccountStorage`. Storage stays
  JSON-on-disk for v1; the storage port is swappable.
- **Phase 3 — Zone-side JWT verification.** Extend `AccountSession.login` to
  accept a JWT (keep a guest-string path behind a feature flag for dev).
  Replace `AccountHostedService`'s body to verify the JWT, populate
  `EntityId + Name + AccessLevel` from claims, and publish
  `playerLoggedOn` as before (the `EventBus` shape doesn't change —
  existing listeners keep working per
  [ADR-0003](../../docs/adr/0003-communication-channels.md)).
- **Phase 4 — Stat flush.** Add a per-zone `StatsFlushSystem` that drains
  delta components (`KillDelta`, `DeathDelta`, `PlaytimeDelta`) via the
  RaM canonical-writer recipe ([ADR-0001](../../docs/adr/0001-ecs-component-model.md))
  and posts batched flushes to `/v1/account/{id}/stats`.
- **Phase 5 — Squadron lookup + chat commands.** Cache squadron data per
  connection, wire `?squadcreate` / etc. to the account server.
- **Phase 6 — Zone heartbeat + client zone browser.** Zones POST to
  `/v1/zones/{id}/heartbeat`; client `MainMenuState` queries `/v1/zones`
  to populate a server list.
- **Phase 7 — Access roles via JWT claims.** Drop the in-memory operator map
  in `AccountHostedService`; `AccessLevel` comes from `roles[]` in the
  verified JWT, closing the `InfinityChatHostedService.java:193` TODO.
- **Phase 8 (optional) — Federated auth.** Steam OpenID wired into
  `/v1/auth/login`.

Each phase is a separate slice; Phase 1 + 2 + 3 is the minimum viable
release — gives logged-in accounts but no stats, no squadrons.

## 5. Open questions

1. **Account server framework.** Javalin? Spark? Ktor? Pin in Phase 2; not
   a PRD-level decision.
2. **JSON storage vs SQLite for v1.** `JsonAccountStorage` is simple and
   inherited from moss; SQLite is cheap to add and gives concurrent-read
   safety. Likely SQLite for stats (writes are frequent) + JSON for accounts
   (writes are rare). Pin in Phase 2.
3. **Identity collision across zones.** Today the zone-local `EntityId` is
   the canonical handle for chat, ships, frequencies. Should the
   `accountId` *replace* `EntityId` for cross-zone events (e.g. cross-zone
   chat in a future iteration), or do we keep a mapping table? Lean toward
   mapping table: zone-internal code keeps using `EntityId` (avoids touching
   every consumer of `playerLoggedOn`), the account-server-bound side uses
   `accountId`.
4. **Display-name uniqueness scope.** Globally unique (Subspace canon) or
   per-zone unique? Lean globally unique, mirrored by `Account.getUserId()`
   being globally unique in moss `maccount`.
5. **Privacy of stat aggregation.** If zone A is owned by hostile party B,
   can B read all-account stats? `/v1/zones` server-to-server endpoints
   must scope reads to "stats this zone has produced." Per-account
   cross-zone aggregate stats are public-read (zone browser, profile page).
6. **Operator role provisioning.** Today `AccountHostedService.addOperator`
   is in-memory. Where do operator role grants live? Lean toward
   `Account.roles[]` mutated by a separate `POST /v1/account/{id}/roles`
   endpoint, guarded by sysop-tier JWT claims.
7. **Guest play.** Required (anonymous testing) or removed (privacy / abuse
   surface)? Lean optional, off by default in production zone config.
8. **Token leak surface.** A leaked JWT lets attacker impersonate the
   account for `exp` duration. Is 10 min short enough? Mitigation:
   bind-to-IP claim on the JWT, zone rejects if IP mismatches.
9. **Persistence of in-game ECS state across logout.** GH #36
   (persisted-entities) overlaps with this PRD on "what survives a session."
   Account-server stats are one slice; ECS entity rehydration is a
   separate decision and stays out of this PRD's scope.

## 6. Out of scope

- **Specific auth provider implementation** (Steam ticket validation flow,
  Discord OAuth dance, etc.). The PRD pins JWT + the endpoint shape that
  accepts any provider's token; the provider-specific code is downstream.
- **Persistence DB choice** beyond the v1 "JSON for accounts, SQLite for
  stats" lean. Phase 2 pins the storage adapter; the port is swappable.
- **UI / UX.** No login-screen design, no zone-browser layout, no profile
  page. This is the data + protocol layer.
- **In-game persisted entity state** (GH #36 — ECS components surviving
  logout). Different mechanism, different ADR-eligible decision.
- **Cross-zone chat / spectator routing.** Squadron chat is in-zone for v1.
- **Anti-cheat client attestation.** JWT identity is who you are; whether
  your client is modified is a separate problem.
- **Region-specific account servers.** Single global account server for v1.

## 7. Related work / references

- [`CLAUDE.md`](../../CLAUDE.md) — project conventions, build, release.
- [ADR-0001](../../docs/adr/0001-ecs-component-model.md) — single-writer
  canonical mutation; stat-delta drain follows the Change-entity recipe.
- [ADR-0002](../../docs/adr/0002-config-component-projection.md) —
  template-vs-instance; cached squadron data lives on the component side.
- [ADR-0003](../../docs/adr/0003-communication-channels.md) — `playerLoggedOn`
  / `playerLoggedOff` stay on `EventBus`; cross-zone calls are out-of-tick.
- [ADR-0004](../../docs/adr/0004-settings-pipeline.md) — zone config
  pipeline; account-server URL + signing key go in `zone.groovy`.
- [ADR-0005](../../docs/adr/0005-layered-architecture.md) — `api/` purity;
  any new `Account*` types stay in `infinity.net.*` / `infinity.sim.*`
  per existing convention.
- Sibling PRDs:
  [`master-server`](../master-server/PRD.md) (subsumed by this PRD —
  merge or delete after sign-off),
  [`squadrons`](../squadrons/PRD.md),
  [`access-roles`](../access-roles/PRD.md),
  [`persisted-entities`](../persisted-entities/PRD.md),
  [`steam-api`](../steam-api/PRD.md).
- External: Simsilica moss [`maccount`](https://github.com/Simsilica/moss/tree/master/maccount)
  — `Account`, `AccountManager`, `Authenticator`, `HashAuthenticator`,
  `JsonAccountStorage` ports.
- External: Subspace canonical biller setup —
  http://wiki.minegoboom.com/index.php/Server_Setup.

## Code-extracted TODOs

- [ ] Implement the `infinity.sim.AccountManager` interface (currently an
  empty marker) — needs per-account access-level lookup so chat command
  dispatch can gate execution. Source:
  `infinity-server/src/main/java/infinity/server/chat/InfinityChatHostedService.java:193`.
- [ ] `registerPatternTriConsumer` / `registerCommandConsumer` broadcast
  help text to every connected player on registration — should filter by
  access level. Source:
  `infinity-server/src/main/java/infinity/server/chat/InfinityChatHostedService.java:252` and `:303`.
- [ ] Replace `AccountSession.login(playerName)` with `login(token)` once
  Phase 3 lands; preserve a guest path behind a feature flag for dev.
