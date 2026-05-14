# SonarCloud Issues Fix Guide — subspace-infinity

> **Total issues:** 152 | **Estimated effort:** 2d 5h  
> **Severity breakdown:** 1 Blocker · 56 High · 40 Medium · 39 Low · 16 Info

---

## 🔴 BLOCKER (1 issue)

### `infinity-server/.../infinity/settings/ConfigRegistry.java`
| Line | Issue | Fix |
|------|-------|-----|
| L63 | **Rename field `slots`** — it clashes with the constant `SLOTS` | Rename the field to something distinct (e.g. `slotList` or `slotInstances`) to avoid confusion with the constant `SLOTS`. |

---

## 🔴 CRITICAL — High Severity (56 issues)

### `api/src/main/java/infinity/es/Hidden.java`
| Line | Issue | Fix |
|------|-------|-----|
| L11 | Empty method body | Add `// intentionally empty` comment, throw `UnsupportedOperationException`, or provide an implementation. |

### `api/.../infinity/es/ship/actions/RocketActive.java`
| Line | Issue | Fix |
|------|-------|-----|
| L22 | Empty method body | Same as above. |

### `api/.../infinity/es/ship/actions/RocketBuff.java`
| Line | Issue | Fix |
|------|-------|-----|
| L11 | Empty method body | Same as above. |

### `infinity-client/.../infinity/client/states/RadarState.java`
| Line | Issue | Fix |
|------|-------|-----|
| L409 | Duplicate string literal `"Color"` used 3 times | Extract to a constant: `private static final String COLOR_KEY = "Color";` |

### `infinity-server/src/main/java/infinity/ai/BrainConfigurations.java`
| Line | Issue | Fix |
|------|-------|-----|
| L88 | Duplicate literal `"selectGoal() failed goals:{}"` (×3) | Extract to a constant. |
| L192 | Duplicate literal `"---------- Create loop:{}"` (×3) | Extract to a constant. |
| L204 | Duplicate literal `"{} succeeded for:{}"` (×9) | Extract to a constant. |
| L208 | Duplicate literal `"{} failed for:{}"` (×6) | Extract to a constant. |
| L245 | Duplicate literal `"{} failed for:{} failed action:{}"` (×3) | Extract to a constant. |
| L316 | Duplicate literal `"blocked by:{}"` (×3) | Extract to a constant. |

### `infinity-server/.../infinity/settings/ConfigRegistry.java`
| Line | Issue | Fix |
|------|-------|-----|
| L88 | Duplicate literal `"slotType"` (×3) | Extract to a constant: `private static final String SLOT_TYPE = "slotType";` |

### `infinity-server/.../infinity/settings/ShipConfigBuilder.java`
| Line | Issue | Fix |
|------|-------|-----|
| L109 | Duplicate literal `"speed"` (×5) | Extract to constant `SPEED`. |
| L117 | Duplicate literal `"energy"` (×5) | Extract to constant `ENERGY`. |
| L159 | Duplicate literal `"bombs"` (×6) | Extract to constant `BOMBS`. |
| L159 | Duplicate literal `"start"` (×10) | Extract to constant `START`. |
| L162 | Duplicate literal `"fireDelay"` (×4) | Extract to constant `FIRE_DELAY`. |
| L178 | Duplicate literal `"bullets"` (×5) | Extract to constant `BULLETS`. |
| L201 | Duplicate literal `"mines"` (×5) | Extract to constant `MINES`. |
| L219 | Duplicate literal `"bursts"` (×3) | Extract to constant `BURSTS`. |
| L228 | Duplicate literal `"thors"` (×3) | Extract to constant `THORS`. |
| L262 | Duplicate literal `"rockets"` (×3) | Extract to constant `ROCKETS`. |
| L287 | Duplicate literal `"status"` (×4) | Extract to constant `STATUS`. |

### `infinity-server/src/main/java/infinity/systems/ArenaCommandsSystem.java`
| Line | Issue | Fix |
|------|-------|-----|
| L88 | Empty method body | Add comment or implementation. |
| L92 | Empty method body | Add comment or implementation. |
| L181 | Duplicate literal `"Arena "` (×7) | Extract to constant `ARENA_PREFIX`. |

### `infinity-server/src/main/java/infinity/systems/ArenaLogic.java`
| Line | Issue | Fix |
|------|-------|-----|
| L239 | Duplicate literal `"Arena "` (×10) | Extract to constant `ARENA_PREFIX`. |

### `infinity-server/src/main/java/infinity/systems/AvatarSystem.java`
| Line | Issue | Fix |
|------|-------|-----|
| L51 | Empty method body | Add comment or implementation. |
| L113 | Empty method body | Add comment or implementation. |
| L117 | Empty method body | Add comment or implementation. |

### `infinity-server/src/main/java/infinity/systems/ChecksShipsSystem.java`
| Line | Issue | Fix |
|------|-------|-----|
| L126 | Empty method body | Add comment or implementation. |
| L130 | Empty method body | Add comment or implementation. |
| L344 | Duplicate literal `"(cost="` (×3) | Extract to constant. |

### `infinity-server/src/main/java/infinity/systems/ChecksWorldSystem.java`
| Line | Issue | Fix |
|------|-------|-----|
| L114 | Empty method body | Add comment or implementation. |
| L118 | Empty method body | Add comment or implementation. |

### `infinity-server/src/main/java/infinity/systems/DoorSystem.java`
| Line | Issue | Fix |
|------|-------|-----|
| L23 | Empty method body | Add comment or implementation. |
| L41 | Empty method body | Add comment or implementation. |
| L58 | Empty method body | Add comment or implementation. |

### `infinity-server/src/main/java/infinity/systems/GameServer.java`
| Line | Issue | Fix |
|------|-------|-----|
| L624 | Empty method body | Add comment or implementation. |
| L628 | Empty method body | Add comment or implementation. |

### `infinity-server/src/main/java/infinity/systems/MapSystem.java`
| Line | Issue | Fix |
|------|-------|-----|
| L116 | Duplicate literal `"Currentmap location is:{}, current direction is:{}"` (×3) | Extract to constant. |

### `infinity-server/src/main/java/infinity/systems/MovementInputSystem.java`
| Line | Issue | Fix |
|------|-------|-----|
| L69 | Empty method body | Add comment or implementation. |
| L89 | Empty method body | Add comment or implementation. |

### `infinity-server/src/main/java/infinity/systems/SettingsSystem.java`
| Line | Issue | Fix |
|------|-------|-----|
| L27 | Empty method body | Add comment or implementation. |
| L31 | Empty method body | Add comment or implementation. |
| L39 | Empty method body | Add comment or implementation. |
| L43 | Empty method body | Add comment or implementation. |

### `infinity-server/src/main/java/infinity/systems/WorldSystem.java`
| Line | Issue | Fix |
|------|-------|-----|
| L24 | Empty method body | Add comment or implementation. |
| L32 | Empty method body | Add comment or implementation. |
| L36 | Empty method body | Add comment or implementation. |
| L40 | Empty method body | Add comment or implementation. |
| L82 | Empty method body | Add comment or implementation. |

### `infinity-server/.../infinity/systems/ship/EnergySystem.java`
| Line | Issue | Fix |
|------|-------|-----|
| L48 | Empty method body | Add comment or implementation. |

### `infinity-server/.../infinity/systems/ship/RepelSystem.java`
| Line | Issue | Fix |
|------|-------|-----|
| L173 | Empty method body | Add comment or implementation. |
| L176 | Empty method body | Add comment or implementation. |

### `infinity-server/.../infinity/systems/ship/WarpSystem.java`
| Line | Issue | Fix |
|------|-------|-----|
| L113 | Empty method body | Add comment or implementation. |
| L116 | Empty method body | Add comment or implementation. |

---

## 🟠 MAJOR — Medium/High Severity (40 issues)

### `api/src/main/java/infinity/sim/WeaponFactory.java`
| Line | Issue | Fix |
|------|-------|-----|
| L136 | Commented-out code block | Remove the commented-out code. Use version control history if needed. |

### `infinity-client/.../infinity/client/states/LocalViewState.java`
| Line | Issue | Fix |
|------|-------|-----|
| L338 | Commented-out code block | Remove the commented-out code. |
| L407 | Rename local variable `world` — hides field at line 135 | Rename the local variable to avoid shadowing (e.g. `localWorld`). |

### `infinity-client/.../infinity/client/states/RadarLeafPager.java`
| Line | Issue | Fix |
|------|-------|-----|
| L73 | Commented-out code block | Remove the commented-out code. |

### `infinity-client/.../infinity/client/states/SpaceGridState.java`
| Line | Issue | Fix |
|------|-------|-----|
| L151 | Method has 10 parameters (>7) | Introduce a parameter object / builder pattern to reduce parameter count. |
| L167 | Method has 9 parameters (>7) | Same as above. |

### `infinity-client/.../infinity/client/view/BlockMeshBuilder.java`
| Line | Issue | Fix |
|------|-------|-----|
| L184 | Method has 10 parameters (>7) | Introduce a parameter object. |

### `infinity-client/.../infinity/client/view/InfinityGeometryFactory.java`
| Line | Issue | Fix |
|------|-------|-----|
| L189 | TODO comment action required | Resolve the TODO and remove the comment. |
| L251 | Method has 9 parameters (>7) | Introduce a parameter object. |
| L295 | Method has 11 parameters (>7) | Introduce a parameter object. |

### `infinity-client/.../infinity/architecture/LayerDependencyTest.java`
| Line | Issue | Fix |
|------|-------|-----|
| L116 | Dangling Javadoc comment | Remove or attach to the correct declaration. |

### `infinity-server/src/main/java/infinity/ai/BrainScheduler.java`
| Line | Issue | Fix |
|------|-------|-----|
| L167 | Nested `if` should be merged with enclosing `if` | Combine the conditions using `&&`. |

### `infinity-server/src/main/java/infinity/map/BitmapData.java`
| Line | Issue | Fix |
|------|-------|-----|
| L6 | **Bug:** `equals`, `hashCode`, `toString` don't consider array content | Override these methods and use `Arrays.equals()` / `Arrays.hashCode()` / `Arrays.toString()`. |

### `infinity-server/src/main/java/infinity/map/RegionRleCodec.java`
| Line | Issue | Fix |
|------|-------|-----|
| L161 | Useless parentheses | Remove the unnecessary parentheses. |
| L218 | Useless parentheses | Remove the unnecessary parentheses. |
| L386 | **Bug:** `int` promotion — missing `& 0xff` | Change expression to `value & 0xff` to prevent sign extension. |

### `infinity-server/src/main/java/infinity/server/GameSessionHostedService.java`
| Line | Issue | Fix |
|------|-------|-----|
| L464 | Commented-out code block | Remove the commented-out code. |
| L467 | Commented-out code block | Remove the commented-out code. |

### `infinity-server/src/main/java/infinity/systems/ArenaLogic.java`
| Line | Issue | Fix |
|------|-------|-----|
| L132 | Method has 8 parameters (>7) | Introduce a parameter object. |
| L168 | Method has 8 parameters (>7) | Introduce a parameter object. |
| L357 | Unused method parameter `arenaIndex` | Remove the parameter or use it. |

### `infinity-server/src/main/java/infinity/systems/ArenaSystem.java`
| Line | Issue | Fix |
|------|-------|-----|
| L396 | Commented-out code block | Remove the commented-out code. |

### `infinity-server/src/main/java/infinity/systems/AvatarSystem.java`
| Line | Issue | Fix |
|------|-------|-----|
| L162 | Commented-out code block | Remove the commented-out code. |

### `infinity-server/src/main/java/infinity/systems/GravitySystem.java`
| Line | Issue | Fix |
|------|-------|-----|
| L31 | Raw generic type — missing type parameter | Add the proper type parameter (e.g. `List<SomeType>`). |

### `infinity-server/src/main/java/infinity/systems/WallLightDecorator.java`
| Line | Issue | Fix |
|------|-------|-----|
| L90 | Method has 10 parameters (>7) | Introduce a parameter object. |
| L108 | Method has 12 parameters (>7) | Introduce a parameter object. |
| L128 | Method has 9 parameters (>7) | Introduce a parameter object. |

### `infinity-server/.../infinity/systems/ship/ConsumableSystem.java`
| Line | Issue | Fix |
|------|-------|-----|
| L542 | Rename field `action` (naming conflict) | Rename to avoid ambiguity. |

### `infinity-server/.../infinity/systems/ship/RotationSystem.java`
| Line | Issue | Fix |
|------|-------|-----|
| L97 | Local variable `stats` hides field at line 26 | Rename local variable. |

### `infinity-server/.../infinity/systems/ship/SpeedSystem.java`
| Line | Issue | Fix |
|------|-------|-----|
| L97 | Local variable `stats` hides field at line 26 | Rename local variable. |

### `infinity-server/.../infinity/systems/ship/ThrustSystem.java`
| Line | Issue | Fix |
|------|-------|-----|
| L96 | Local variable `stats` hides field at line 26 | Rename local variable. |

### `infinity-server/.../infinity/systems/ship/WeaponsDamageLogic.java`
| Line | Issue | Fix |
|------|-------|-----|
| L39 | Method has 8 parameters (>7) | Introduce a parameter object. |
| L57 | Method has 11 parameters (>7) | Introduce a parameter object. |

### `infinity-server/.../infinity/systems/ship/WeaponsEligibility.java`
| Line | Issue | Fix |
|------|-------|-----|
| L35 | Method has 11 parameters (>7) | Introduce a parameter object. |
| L205 | Method has 8 parameters (>7) | Introduce a parameter object. |
| L289 | Method has 9 parameters (>7) | Introduce a parameter object. |

### `infinity-server/.../test/java/infinity/settings/ConfigRegistrySystemLoadTest.java`
| Line | Issue | Fix |
|------|-------|-----|
| L241 | Commented-out code block | Remove the commented-out code. |
| L250 | Commented-out code block | Remove the commented-out code. |

### `infinity-server/.../java/infinity/systems/ship/RocketBuffActivationTest.java`
| Line | Issue | Fix |
|------|-------|-----|
| L145 | Commented-out code block | Remove the commented-out code. |

---

## 🟡 MINOR — Low Severity (39 issues)

### `api/src/main/java/infinity/es/AudioTypes.java`
| Line | Issue | Fix |
|------|-------|-----|
| L85 | Method name doesn't match pattern `^[a-z][a-zA-Z0-9]*$` | Rename the method to follow camelCase convention. |

### `infinity-client/.../infinity/client/states/LocalViewState.java`
| Line | Issue | Fix |
|------|-------|-----|
| L453 | Too many `break`/`continue` statements in loop (>1) | Refactor loop logic to use at most one jump statement. |

### `infinity-client/.../infinity/client/states/RadarState.java`
| Line | Issue | Fix |
|------|-------|-----|
| L631 | Move method into `ArenaFootprintContainer` | Relocate the method to the appropriate class. |
| L640 | Move method into `ArenaFootprintContainer` | Relocate the method to the appropriate class. |

### `infinity-client/.../infinity/client/view/BlockMeshBuilder.java`
| Line | Issue | Fix |
|------|-------|-----|
| L84 | Too many `break`/`continue` in loop | Refactor loop. |
| L110 | Too many `break`/`continue` in loop | Refactor loop. |

### `infinity-server/src/main/java/infinity/map/LevelFile.java`
| Line | Issue | Fix |
|------|-------|-----|
| L34 | Field `eLvlAttrs` should be `private` with accessors | Make private and add getter/setter. |

### `infinity-server/src/main/java/infinity/map/Region.java`
| Line | Issue | Fix |
|------|-------|-----|
| L16 | Field `isBase` should be private | Make private and add accessor. |
| L17 | Field `isNoFlags` should be private | Make private and add accessor. |
| L18 | Field `isNoWeps` should be private | Make private and add accessor. |
| L19 | Field `isNoAnti` should be private | Make private and add accessor. |
| L20 | Field `isAutoWarp` should be private | Make private and add accessor. |
| L23 | Field `x` should be private | Make private and add accessor. |
| L24 | Field `y` should be private | Make private and add accessor. |
| L27 | Field `rects` should be private | Make private and add accessor. |
| L28 | Field `unknownBytes` should be private | Make private and add accessor. |

### `infinity-server/src/main/java/infinity/map/RegionRleCodec.java`
| Line | Issue | Fix |
|------|-------|-----|
| L401 | Use `Random.nextInt()` instead of current approach | Replace with `random.nextInt(bound)`. |
| L402 | Use `Random.nextInt()` instead | Replace with `random.nextInt(bound)`. |
| L403 | Use `Random.nextInt()` instead | Replace with `random.nextInt(bound)`. |

### `infinity-server/src/main/java/infinity/systems/ArenaLogic.java`
| Line | Issue | Fix |
|------|-------|-----|
| L420 | Field `lastModified` should be private | Make private and add accessor. |

### `infinity-server/src/main/java/infinity/systems/JitterReaperSystem.java`
| Line | Issue | Fix |
|------|-------|-----|
| L44 | Redundant `return`/`continue` jump | Remove the redundant jump statement. |
| L49 | Redundant `return`/`continue` jump | Remove the redundant jump statement. |

### `infinity-server/src/main/java/infinity/systems/MapSystem.java`
| Line | Issue | Fix |
|------|-------|-----|
| L174 | Hard-coded path delimiter | Use `File.separator` or `java.nio.file.FileSystems.getDefault().getSeparator()`. |

### `infinity-server/src/main/java/infinity/systems/MapSystemLogic.java`
| Line | Issue | Fix |
|------|-------|-----|
| L138 | Field `totalNonZero` should be private | Make private. |
| L139 | Field `turfFlags` should be private | Make private. |
| L140 | Field `asteroidsSmall` should be private | Make private. |
| L141 | Field `asteroidsMedium` should be private | Make private. |
| L142 | Field `over5` should be private | Make private. |
| L143 | Field `doors` should be private | Make private. |
| L144 | Field `wormholes` should be private | Make private. |
| L145 | Field `cellsVisible` should be private | Make private. |
| L146 | Field `cellsInvisible` should be private | Make private. |
| L147 | Field `cellsFailedLeaf` should be private | Make private. |
| L149 | Field `firstWritten` should be private | Make private. |
| L150 | Field `lastWritten` should be private | Make private. |

### `infinity-server/.../infinity/systems/ship/EnergySystem.java`
| Line | Issue | Fix |
|------|-------|-----|
| L85 | Too many `break`/`continue` in loop | Refactor loop. |

### `infinity-server/.../infinity/systems/ship/WeaponsDamageLogic.java`
| Line | Issue | Fix |
|------|-------|-----|
| L81 | Too many `break`/`continue` in loop | Refactor loop. |

### `infinity-server/.../infinity/systems/ship/WeaponsLogic.java`
| Line | Issue | Fix |
|------|-------|-----|
| L22 | **Bug:** Cast one operand of addition to `double` | Change to `(double) operand1 + operand2` to avoid integer overflow/truncation. |

### `infinity-server/src/main/java/infinity/tools/MapTileSurvey.java`
| Line | Issue | Fix |
|------|-------|-----|
| L39 | Declared `throws IOException` but never thrown | Remove the declared exception from the method signature. |

---

## 🔵 INFO (16 issues)

### `api/.../infinity/es/ship/weapons/BombSafetyRadius.java`
| Line | Issue | Fix |
|------|-------|-----|
| L19 | TODO comment remaining | Complete the task and remove the TODO. |

### `infinity-client/.../infinity/architecture/LayerDependencyTest.java`
| Line | Issue | Fix |
|------|-------|-----|
| L138 | TODO comment remaining | Complete the task and remove the TODO. |

### `infinity-server/.../infinity/settings/BombAdapter.java` — L9
### `infinity-server/.../infinity/settings/BrickAdapter.java` — L9
### `infinity-server/.../infinity/settings/BulletAdapter.java` — L9
### `infinity-server/.../infinity/settings/BurstAdapter.java` — L9
### `infinity-server/.../infinity/settings/DecoyAdapter.java` — L9
### `infinity-server/.../infinity/settings/MineAdapter.java` — L9
### `infinity-server/.../infinity/settings/PortalAdapter.java` — L9
### `infinity-server/.../infinity/settings/PrizeAdapter.java` — L9
### `infinity-server/.../infinity/settings/PrizeWeightsAdapter.java` — L12
### `infinity-server/.../infinity/settings/RepelAdapter.java` — L9
### `infinity-server/.../infinity/settings/RocketAdapter.java` — L9
### `infinity-server/.../infinity/settings/SpawnAdapter.java` — L13

> **Singleton pattern detected** in all the above adapter classes. Review whether the Singleton pattern is intentional and that the implementation is appropriate for the context (e.g. thread safety, testability).

### `infinity-server/.../infinity/systems/ship/ConsumableSystem.java`
| Line | Issue | Fix |
|------|-------|-----|
| L470 | TODO comment remaining | Complete the task and remove the TODO. |

### `infinity-server/.../infinity/systems/ship/WeaponsEligibility.java`
| Line | Issue | Fix |
|------|-------|-----|
| L315 | TODO comment remaining | Complete the task and remove the TODO. |

---

## 📋 Quick-win Summary

These categories group repeated patterns for batch-fixing:

**1. Empty method bodies** (≈30 issues across multiple system classes)
> Add `// intentionally empty` comment or throw `new UnsupportedOperationException("Not implemented")`.

**2. Commented-out code** (≈12 issues)
> Delete all commented-out code blocks. Rely on git history for recovery.

**3. Duplicate string literals** (≈20 issues in `BrainConfigurations`, `ShipConfigBuilder`, `ArenaLogic`, `ArenaCommandsSystem`, etc.)
> Extract each repeated string to a `private static final String` constant at the top of the class.

**4. Methods with too many parameters** (≈15 issues)
> Apply the *Parameter Object* or *Builder* pattern to reduce parameter counts to ≤ 7.

**5. Public mutable fields** (≈15 issues in `Region`, `MapSystemLogic`, etc.)
> Make fields `private` and add getters (and setters where mutation is needed).

**6. TODO comments** (4 issues)
> Resolve the underlying tasks or create tracked tickets and remove the TODO comments.

**7. Singleton adapters review** (13 info issues)
> Audit `BombAdapter`, `BrickAdapter`, `BulletAdapter`, `BurstAdapter`, `DecoyAdapter`, `MineAdapter`, `PortalAdapter`, `PrizeAdapter`, `PrizeWeightsAdapter`, `RepelAdapter`, `RocketAdapter`, `SpawnAdapter`, `BurstAdapter` — ensure thread safety and consider dependency injection as an alternative.