
# Nullspace Energy System Reference

This document cross-references the **Nullspace C client** (`plushmonkey/nullspace`) as a canonical implementation of Subspace Continuum's energy system. Use this to validate Infinity's energy implementation for correctness against the original game's physics and order-of-operations.

**Original Nullspace repo:** https://github.com/plushmonkey/nullspace  
**Key files:**
- `src/null/ShipController.cpp` — ship state, energy updates, fire delays
- `src/null/ShipController.h` — ship struct, prize enum, capability flags
- `src/null/ArenaSettings.h` — arena-wide tuning parameters
- `src/null/WeaponManager.cpp/h` — weapon simulation and collision

---

## Energy Model: The Golden Rule

### Order of Operations (Continuum Canon)

From `ShipController::Update()` in Nullspace:

```c
// Energy update order MUST be:
// 1. Afterburners drain FIRST (highest priority)
// 2. THEN recharge happens (fills toward max)
// 3. THEN status costs (Cloak, Stealth, XRadar, Antiwarp)

if (afterburners) {
    self->energy -= ab_cost;
}

if (ship.emped_time > 0.0f) {
    ship.emped_time -= dt;
} else {
    self->energy += (ship.recharge / 10.0f) * dt;
    if (self->energy > ship.energy) {
        self->energy = (float)ship.energy;
    }
}

HandleStatusEnergy(*self, Status_XRadar, ship_settings.XRadarEnergy, dt);
HandleStatusEnergy(*self, Status_Stealth, ship_settings.StealthEnergy, dt);
HandleStatusEnergy(*self, Status_Cloak, ship_settings.CloakEnergy, dt);
HandleStatusEnergy(*self, Status_Antiwarp, ship_settings.AntiWarpEnergy, dt);
```

### Why This Order Matters

- **Afterburners first:** They have the highest precedence. A ship at max energy with active afterburners will *not* recharge further until afterburners are off.
- **Recharge second:** Only happens if not EMPed. Recharge fills *toward* max, not past it. Must clamp: `min(current + recharge * tpf, max)`.
- **Status drains last:** These are the lowest priority. They only drain if the ship has energy > the drain cost this tick.

**Implication for Infinity:** The `EnergySystem` + `StatusDrainSystem` already implement this via the Change-entity pattern (ADR-0001). But the *order in which systems run must match Continuum*:
1. Emit recharge changes first (`EnergySystem.emitRechargeChanges()`)
2. Drain status changes second (`StatusDrainSystem` via `BaseEnergyDrainSystem`)
3. Apply all changes in a single drain pass (`EnergySystem.drainEnergyChanges()`)

---

## Ship Energy State

From `ShipController.h`:

```cpp
struct Ship {
  u32 energy;              // Current energy pool
  u32 recharge;            // Recharge rate (energy/second, typically in tenths)
  u32 rotation;            // Max rotation rate
  u32 guns;                // Gun level (0-3)
  u32 bombs;               // Bomb level (0-3)
  u32 thrust;              // Max thrust
  u32 speed;               // Max speed
  u32 shrapnel;            // Shrapnel level

  u32 next_bullet_tick;    // Last fire tick (for fire-delay logic)
  u32 next_bomb_tick;
  u32 next_repel_tick;

  u32 rocket_end_tick;     // Buff durations (timed effects)
  u32 shutdown_end_tick;   // EMP duration
  u32 fake_antiwarp_end_tick;

  float emped_time;        // EMP duration in float (seconds)
  float super_time;        // Super duration
  float shield_time;       // Shield duration

  float portal_time;       // Portal buff duration
  Vector2f portal_location;

  bool multifire;          // Active toggleable

  ShipCapabilityFlags capability;  // Bitmask: Stealth, Cloak, XRadar, etc.
};
```

**Mapping to Infinity:**
- `energy` → `Energy` component
- `recharge` → `EnergyStats.rechargePerSecond()`
- `emped_time` → Temporary buff tracked via `Decay` component on a status drain change
- `next_bullet_tick`, `next_bomb_tick` → Fire delay tracking (not energy, but timing)
- `capability` → Toggle active state (e.g., `CloakActive` component)

---

## Status Drain Costs

### Nullspace Implementation

```cpp
void ShipController::HandleStatusEnergy(Player& self, u32 status, u32 cost, float dt) {
  if (self.togglables & status) {
    float update_cost = (cost / 10.0f) * dt;  // Convert to per-tick from per-100ms

    if (self.energy > update_cost) {
      self.energy -= update_cost;
    } else {
      self.togglables &= ~status;  // Turn OFF if not enough energy
    }
  }
}
```

**Key insight:** A status drains at a constant rate (energy/sec). If the ship runs out of energy *mid-tick*, the toggle turns off immediately (next tick).

### Status Drain Rates (from ArenaSettings.h)

```cpp
// Per-ship settings:
uint16_t CloakEnergy;       // Thousandths per tick (e.g., 100 = 10 energy/sec)
uint16_t StealthEnergy;
uint16_t AntiWarpEnergy;
uint16_t XRadarEnergy;
```

**Infinity mapping:**
- `CloakStats.energyDrainPerSecond()` should return the rate in energy/second
- `BaseEnergyDrainSystem.perTickDrain()` converts: `(rate / sec) * tpf` → int, rounded half-up
- If result > current energy, toggle turns off

---

## EMP / Energy Shutdown

### Nullspace Model

```cpp
if (ship.emped_time > 0.0f) {
    ship.emped_time -= dt;
} else {
    // Recharge only happens if NOT emped
    self->energy += (ship.recharge / 10.0f) * dt;
    ...
}
```

**Behavior:**
- While `emped_time > 0`, energy does *not* recharge (but damage still applies).
- Status drains still apply even while EMPed.
- When `emped_time` reaches 0, recharge resumes next tick.

**Infinity mapping:**
- Track EMP as a temporary buff with `Decay` component.
- When the `Decay` fires, the EMP effect ends (recharge resumes).
- Modify `EnergySystem.emitRechargeChanges()` to check for EMP presence before emitting recharge.

---

## Prize Application Order

From `ShipController.h`, the `Prize` enum lists 30 prize types. The order matters for spawn/death drops:

```cpp
enum class Prize : u16 {
  None,
  Recharge,                // Energy regen boost
  Energy,                  // +energy pickup
  Rotation,                // +rotation upgrade
  Stealth,                 // Stealth toggle unlock
  Cloak,                   // Cloak toggle unlock
  XRadar,                  // X-Radar toggle unlock
  Warp,                    // Warp engine unlock
  Guns,                    // Guns upgrade
  Bombs,                   // Bombs upgrade
  BouncingBullets,         // Bouncing bullet toggle
  Thruster,                // +thrust upgrade
  TopSpeed,                // +speed upgrade
  FullCharge,              // Full energy restore
  EngineShutdown,          // EMP effect
  Multifire,               // Multifire toggle
  Proximity,               // Proximity mines toggle
  Super,                   // Super shield (buff)
  Shields,                 // Shield upgrade
  Shrapnel,                // Shrapnel level
  Antiwarp,                // Antiwarp toggle unlock
  Repel,                   // +repel count
  Burst,                   // +burst count
  Decoy,                   // +decoy count
  Thor,                    // +thor count
  Multiprize,              // Random mixed prizes
  Brick,                   // +brick count
  Rocket,                  // +rocket count
  Portal,                  // +portal count
  Count
};
```

**Infinity's `.claude/rules/prize-applier.md`** documents the canonical semantics. Nullspace is the reference implementation for edge cases (e.g., `FullCharge` semantics, `EngineShutdown` duration).

---

## Weapon-Related Energy Costs

From `ShipController.h`:

```cpp
struct Ship {
  u32 next_bullet_tick = 0;  // Fire delay tracking
  u32 next_bomb_tick = 0;
  u32 next_repel_tick = 0;
};
```

And from `ArenaSettings.h`:

```cpp
uint16_t BulletFireEnergy;
uint16_t MultiFireEnergy;           // Extra cost for multifire
uint16_t BombFireEnergy;
uint16_t BombFireEnergyUpgrade;     // Extra per level
uint16_t LandmineFireEnergy;
uint16_t LandmineFireEnergyUpgrade;
```

**Infinity mapping:**
- Energy cost should be deducted *before* the weapon fires (not after).
- If not enough energy, the weapon doesn't fire.
- This is handled by `WeaponsFireEligibilitySystem` checking energy before emission.

---

## Recharge Formula

From Nullspace:

```cpp
const int charge = Math.toIntExact(Math.round(tpf * stats.rechargePerSecond()));
```

**Java equivalent (already in Infinity):**

```java
final int charge = Math.toIntExact(Math.round(tpf * stats.rechargePerSecond()));
if (charge <= 0) {
  continue;
}
```

**Rules:**
- Recharge is calculated as `(rate in energy/sec) × (delta time in seconds)`.
- Round half-up using `Math.round()`.
- Skip if result is ≤ 0.
- Clamp final energy to max: `min(current + charge, max)`.

---

## Status Drain Formula

From Nullspace's `BaseEnergyDrainSystem` (implemented in Infinity):

```cpp
static int perTickDrain(final double energyDrainPerSecond, final double tpfSeconds) {
    if (energyDrainPerSecond <= 0.0 || tpfSeconds <= 0.0) {
        return 0;
    }
    return (int) Math.round(energyDrainPerSecond * tpfSeconds);
}
```

**Same formula as recharge.** Drain per tick = `(drain rate in energy/sec) × tpf`.

---

## Test Cases: Validation Against Nullspace

Use these cases to verify Infinity's energy system matches Continuum canon:

### Test 1: Afterburner Priority
- Ship at 100/100 energy, recharge 50 energy/sec, afterburner cost 20 energy/sec
- Both active for 0.5 sec
- Expected: energy drops to 90/100 (afterburner drains first, recharge cannot exceed max)
- Nullspace behavior: Confirmed in `ShipController::Update()`

### Test 2: EMP Blocks Recharge
- Ship at 50/100 energy, recharge 50 energy/sec, EMP for 1.0 sec
- After 1.5 sec: energy should still be 50 (no recharge while EMPed)
- After 2.0 sec: energy should be ~75 (recharge resumes after EMP ends)

### Test 3: Status Drain Turnoff
- Ship at 10 energy, Cloak drain 100 energy/sec, cloak active
- After 0.05 sec: energy at 0, cloak should turn off automatically
- Expected: Cloak toggle flips off, no energy deficit

### Test 4: Multi-Status Drain
- Ship at 100 energy, Cloak 50/sec, XRadar 30/sec, both active
- After 1.0 sec: energy drops by 80 to 20
- Both drains apply in the same tick (additive via Change-entity fold)

---

## References

- **Nullspace WeaponManager.cpp:** Weapon simulation + collision (reference for damage model)
- **Nullspace ShipController.cpp:** Energy + status updates + prize application
- **Nullspace ArenaSettings.h:** Ship tuning parameters
- **Infinity ADR-0001:** Change-entity mutation pattern (supercedes direct mutation)
- **Infinity ADR-0002:** Config-Component Projection (template vs instance tier)
- **Infinity `.claude/rules/prize-applier.md`:** Canonical prize semantics

---

## FAQ

**Q: Should I port Nullspace's weapon simulation directly?**  
A: No. Nullspace is a C client; Infinity is a Java server. Use Nullspace as a *reference* for the algorithms and edge cases, but port to Java + Zay-ES semantics (entities, components, systems).

**Q: What if Infinity's behavior diverges from Nullspace?**  
A: It's a bug unless explicitly documented as a design choice (e.g., Infinity chooses a different EMP duration). File an issue with the Nullspace behavior as the reference.

**Q: Where do I validate my changes?**  
A: Write unit tests that compare Infinity's output (final energy, toggle state) to Nullspace's for identical inputs. See `infinity-server/src/test/` for examples.

**Q: Is Nullspace the only reference?**  
A: No. The canonical references are:
1. **Nullspace** (well-documented, modern C)
2. **ASSS** (the original Subspace Server, in C; older but definitive)
3. **Continuum** (the original client; closed-source, less accessible)

Nullspace is the most accessible modern reference.

---

## Notes for Contributors

- Keep this document in sync with ADR-0001 (Change-entity pattern) and ADR-0002 (Config-Component Projection).
- If you find Nullspace behavior that contradicts this doc, update the doc and file an issue.
- For gameplay balance changes (e.g., new EMP duration), document the rationale as a deviation from Nullspace canon.
