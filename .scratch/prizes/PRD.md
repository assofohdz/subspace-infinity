# Server/Game: Implement prizes

Status: ready-for-human
Cross-ref: [GH #67](https://github.com/assofohdz/subspace-infinity/issues/67)
Labels: enhancement, area:server

Prizes to implement (status reflects [`PrizeSystem`](../../infinity/src/main/java/infinity/systems/PrizeSystem.java) and Pattern 4 follow-ups):

**Stat-upgrade prizes (all wired via the Pattern 4 `current = min(current + Upgrade, Max)` shape):**

- [x] Recharge — `handleAcquireRecharge` (bumps `Recharge`, clamped at `RechargeMax`)
- [x] Energy — `handleAcquireEnergy` (bumps `Energy` cap, clamped at `EnergyMax`; live `Health` pool is separate)
- [x] Rotation — `handleAcquireRotation`
- [x] Thruster — `handleAcquireThruster`
- [x] Top Speed — `handleAcquireTopSpeed`
- [x] Full Charge — QUICKCHARGE → `EnergySystem.refillHealth` (Health = Energy cap)

**Inventory / level prizes:**

- [x] Guns — `handleAcquireGun` (Cost/FireDelay/CurrentLevel/MaxLevel; Damage-per-level still pending — see [`guns`](../guns/PRD.md))
- [x] Bombs — `handleAcquireBomb` (also acts as Mine prize via `handleAcquireMine`)
- [x] Burst — `handleAcquireBurst`
- [x] Thor — `handleAcquireThor`

**Not yet wired (empty TODO branches in `handlePrizeAcquisition`):**

- [ ] Stealth
- [ ] Cloak
- [ ] XRadar
- [ ] Warp
- [ ] Bouncing
- [ ] Engine Shutdown
- [ ] MultiFire
- [ ] Proximity
- [ ] Super
- [ ] Shields
- [ ] Shrapnel
- [ ] AntiWarp
- [ ] Repel
- [ ] Decoy
- [ ] Multiprize
- [ ] Brick
- [ ] Rocket
- [ ] Portal

## Comments
