// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.base.DefaultEntityData;
import com.simsilica.sim.GameSystemManager;
import infinity.Ship;
import infinity.config.ArenaConfig;
import infinity.es.arena.ArenaId;
import infinity.systems.SettingsSystem;
import java.util.List;
import org.junit.Test;

/**
 * Orchestration smoke for slice B0 — boots a minimal {@link GameSystemManager}
 * with the systems {@link ConfigRegistrySystem#load} depends on
 * ({@link SettingsSystem}, {@link GroovyShipLoader}, {@link GroovyWeaponsLoader}),
 * builds an {@link ArenaConfig} pointing at the real trench-04-2026 preset
 * fragments, and asserts the resulting snapshot has all three phases populated:
 * ships from {@code ships.groovy}, weapons from {@code misc.groovy} via the
 * compat shim, prize from {@code misc.groovy}.
 *
 * <p>Pre-B0, this orchestration was spread across {@code ArenaSystem.bootstrap}
 * + {@code applyWeaponsConfig}. The test pins the new entry point at
 * {@link ConfigRegistrySystem#load} so a regression in the seam (missing
 * collaborator wiring, wrong phase order) shows up immediately.
 */
public class ConfigRegistrySystemLoadTest {

  @Test
  public void load_realTrenchPreset_populatesShipsWeaponsAndPrize() {
    final GameSystemManager systems = new GameSystemManager();
    final DefaultEntityData ed = new DefaultEntityData();
    systems.register(EntityData.class, ed);

    // Registration order matches GameServer: ConfigRegistrySystem first (no
    // deps), then its collaborators. ConfigRegistrySystem.initialize() pulls
    // all three via getSystem(), so they must all be registered before the
    // manager initializes.
    final ConfigRegistrySystem registry =
        systems.register(ConfigRegistrySystem.class, new ConfigRegistrySystem());
    systems.register(GroovyShipLoader.class, new GroovyShipLoader(registry));
    systems.register(SettingsSystem.class, new SettingsSystem());
    systems.initialize();
    systems.start();

    try {
      final ArenaId arenaId = new ArenaId("trench", EntityId.NULL_ID);
      final ArenaConfig arenaConfig =
          new ArenaConfig(
              "trench.lvl",
              "/conf/trench-04-2026/ships.groovy",
              512,
              512,
              List.of(
                  "/conf/trench-04-2026/misc.groovy",
                  "/conf/trench-04-2026/bullet.groovy",
                  "/conf/trench-04-2026/bomb.groovy",
                  "/conf/trench-04-2026/mine.groovy",
                  "/conf/trench-04-2026/burst.groovy",
                  "/conf/trench-04-2026/repel.groovy",
                  "/conf/trench-04-2026/rocket.groovy",
                  "/conf/trench-04-2026/brick.groovy",
                  "/conf/trench-04-2026/decoy.groovy",
                  "/conf/trench-04-2026/portal.groovy",
                  "/conf/trench-04-2026/prize.groovy",
                  "/conf/trench-04-2026/prize-weights.groovy",
                  "/conf/trench-04-2026/spawn.groovy"),
              0.0,
              List.of(),
              0);

      registry.load(arenaId, arenaConfig);

      final ConfigRegistry snapshot = registry.forArena(arenaId);
      assertNotNull("snapshot must be installed", snapshot);

      // Phase 2 — ships.groovy populated trench's per-ship templates.
      assertTrue(
          "trench preset configures every Subspace ship",
          snapshot.configuredShips().size() >= 8);
      final var warbird = snapshot.getShip(Ship.WARBIRD);
      assertNotNull("WARBIRD config from ships.groovy", warbird);
      assertEquals(
          "WARBIRD initial thrust from trench ships.groovy",
          16,
          warbird.thrust().initial());

      // Per-ship inventory (post-B2-Migration): trench/warbird is a
      // bullet-only build — no bombs/mines/repels/bursts/bricks/rockets/portals
      // (Q6 maps Subspace `*Max 0` to null = disallow). Has bullets, thors,
      // decoys per ship-warbird.groovy's authored values.
      assertNotNull("WARBIRD has bullets", warbird.bullets());
      assertEquals("WARBIRD MaxGuns = 3 (LEVEL_3)",
          infinity.BulletLevel.LEVEL_3, warbird.bullets().max());
      // Slice 10 — per-ship BulletSpeed lifted 1:1 from legacy
      // ship-warbird.groovy (`BulletSpeed 5000` Subspace velocity units).
      // Engine-tier scale 0.01 lands at jME 50 = today's hardcoded
      // addLocal(0,0,50). See WeaponsSystemSplashTest for the math.
      assertEquals(
          "WARBIRD BulletSpeed = 5000 (lifted from legacy ship-warbird.groovy)",
          5000,
          warbird.bullets().speed());
      assertNotNull("WARBIRD has thors", warbird.thors());
      assertEquals("WARBIRD ThorMax = 3", 3, warbird.thors().max());
      assertNotNull("WARBIRD has decoys (B2-activated)", warbird.decoys());
      assertEquals("WARBIRD DecoyMax = 1", 1, warbird.decoys().max());
      // Disallow checks — verifies Q6 null mapping fixed the dual-pipeline
      // drift (was: silently 10 repels via DEFAULT_REPELS; now: null per
      // operator's authored RepelMax 0).
      assertEquals("WARBIRD bombs disallowed (MaxBombs 0)",
          null, warbird.bombs());
      assertEquals("WARBIRD repels disallowed (RepelMax 0)",
          null, warbird.repels());
      assertEquals("WARBIRD bursts disallowed (BurstMax 0)",
          null, warbird.bursts());
      assertEquals("WARBIRD bricks disallowed (BrickMax 0)",
          null, warbird.bricks());
      assertEquals("WARBIRD rockets disallowed (omitted block in trench/ships.groovy)",
          null, warbird.rockets());

      // trench/javelin gets rockets (start: 1, max: 3, activeTimeCs: 400) per
      // ship-javelin.groovy's `RocketTime 400`.
      final var javelin = snapshot.getShip(Ship.JAVELIN);
      assertNotNull("JAVELIN config", javelin);
      assertNotNull("JAVELIN has rockets (RocketMax > 0)", javelin.rockets());
      assertEquals("JAVELIN rocket start = 1", 1, javelin.rockets().start());
      assertEquals("JAVELIN rocket max = 3", 3, javelin.rockets().max());
      assertEquals("JAVELIN rocket activeTimeCs = 400 (per-ship RocketTime)",
          400L, javelin.rockets().activeTimeCs());
      // Slice 10b — javelin fires bullets backward (signed-scalar contract).
      // Lifted from legacy SVS BulletSpeed 64636 (= int16 -900).
      assertNotNull("JAVELIN has bullets", javelin.bullets());
      assertEquals("JAVELIN BulletSpeed = -900 (Slice 10b backward-firing lift)",
          -900, javelin.bullets().speed());

      // trench/leviathan is a heavy build with bombs + mines + portals.
      final var leviathan = snapshot.getShip(Ship.LEVIATHAN);
      assertNotNull("LEVIATHAN config", leviathan);
      assertNotNull("LEVIATHAN has bombs (MaxBombs 3)", leviathan.bombs());
      assertEquals("LEVIATHAN bomb max = BOMB_3",
          infinity.BombLevel.BOMB_3, leviathan.bombs().max());
      // Slice S2: BombThrust = 400 (SVS canon recoil) on every
      // bomb-carrying trench ship. WeaponsSystem.applyBombRecoil scales by
      // EngineConfig.subspaceVelocityScale at fire time.
      assertEquals("LEVIATHAN BombThrust = 400 (SVS canon)",
          400, leviathan.bombs().thrust());
      assertNotNull("LEVIATHAN has mines (MaxMines > 0)", leviathan.mines());
      assertEquals("LEVIATHAN mine cost (LandmineFireEnergy 800)",
          800, leviathan.mines().cost());
      assertNotNull("LEVIATHAN has portals (PortalMax 1, B2-activated)",
          leviathan.portals());
      assertEquals("LEVIATHAN PortalMax = 1", 1, leviathan.portals().max());

      // Slice 6a: trench/leviathan has stealth status 2 (start active) per
      // ship-leviathan.groovy's StealthStatus 2 / StealthEnergy 1000.
      // Cloak is forbidden (omitted block → null).
      assertNull("LEVIATHAN has no cloak block (forbidden, omitted)",
          leviathan.cloak());
      assertNotNull("LEVIATHAN has stealth (StealthStatus 2)",
          leviathan.stealth());
      assertEquals("LEVIATHAN StealthStatus = 2 (start active)",
          2, leviathan.stealth().status());
      assertEquals("LEVIATHAN StealthEnergy = 1000",
          1000, leviathan.stealth().energyDrainPer1000Cs());

      // Slice 6a: trench/weasel has both cloak + stealth at status 2 per
      // ship-weasel.groovy (CloakStatus 2 / CloakEnergy 1250 + StealthStatus
      // 2 / StealthEnergy 400).
      final var weasel = snapshot.getShip(Ship.WEASEL);
      assertNotNull("WEASEL config", weasel);
      assertNotNull("WEASEL has cloak (CloakStatus 2)", weasel.cloak());
      assertEquals("WEASEL CloakStatus = 2", 2, weasel.cloak().status());
      assertEquals("WEASEL CloakEnergy = 1250",
          1250, weasel.cloak().energyDrainPer1000Cs());
      assertNotNull("WEASEL has stealth (StealthStatus 2)", weasel.stealth());
      assertEquals("WEASEL StealthStatus = 2", 2, weasel.stealth().status());
      assertEquals("WEASEL StealthEnergy = 400",
          400, weasel.stealth().energyDrainPer1000Cs());

      // Slice 6a: trench/warbird has neither cloak nor stealth (both
      // omitted = forbidden in trench).
      assertNull("WARBIRD has no cloak block (forbidden, omitted)",
          warbird.cloak());
      assertNull("WARBIRD has no stealth block (forbidden, omitted)",
          warbird.stealth());
      // Slice 6b: trench/warbird also has no xradar/antiwarp (both
      // status==0 in legacy ship-warbird.groovy → omitted).
      assertNull("WARBIRD has no xradar block (forbidden, omitted)",
          warbird.xradar());
      assertNull("WARBIRD has no antiwarp block (forbidden, omitted)",
          warbird.antiwarp());

      // Slice 6b: trench/spider gets both XRadar and AntiWarp (legacy
      // ship-spider.groovy: XRadarStatus 2 + XRadarEnergy 200; AntiWarpStatus 1
      // + AntiWarpEnergy 800).
      final var spider = snapshot.getShip(Ship.SPIDER);
      assertNotNull("SPIDER config", spider);
      assertNotNull("SPIDER has xradar (XRadarStatus 2)", spider.xradar());
      assertEquals("SPIDER XRadarStatus = 2 (start active)",
          2, spider.xradar().status());
      assertEquals("SPIDER XRadarEnergy = 200",
          200, spider.xradar().energyDrainPer1000Cs());
      assertNotNull("SPIDER has antiwarp (AntiWarpStatus 1)",
          spider.antiwarp());
      assertEquals("SPIDER AntiWarpStatus = 1 (acquirable)",
          1, spider.antiwarp().status());
      assertEquals("SPIDER AntiWarpEnergy = 800",
          800, spider.antiwarp().energyDrainPer1000Cs());

      // Slice 6b: trench/lancaster has xradar acquirable (status 1,
      // energy 2000); no antiwarp (status 0).
      final var lancaster = snapshot.getShip(Ship.LANCASTER);
      assertNotNull("LANCASTER config", lancaster);
      assertNotNull("LANCASTER has xradar (XRadarStatus 1)", lancaster.xradar());
      assertEquals("LANCASTER XRadarStatus = 1", 1, lancaster.xradar().status());
      assertEquals("LANCASTER XRadarEnergy = 2000",
          2000, lancaster.xradar().energyDrainPer1000Cs());
      assertNull("LANCASTER has no antiwarp block (forbidden, omitted)",
          lancaster.antiwarp());

      // Phase 3 — weapons compat shim pulled trench's misc.groovy values
      // into ConfigRegistry's flat weapon-projectile slots (post-B1a flatten).
      assertEquals(
          "trench's [Bullet] BulletDamageLevel = 520",
          520,
          snapshot.bullet().damage());
      assertEquals(
          "trench's [Bomb] BombDamageLevel = 2650",
          2650,
          snapshot.bomb().damage());

      // Slice 9a — typed [Bomb] BombExplodePixels parses to
      // BombConfig.explodeRadius (Infinity authors in tiles / world units;
      // SVS canonical 80 px = 5 tiles at 16 px/tile).
      assertEquals(
          "trench's [Bomb] explodeRadius = 5 tiles (= SVS BombExplodePixels 80)",
          5.0,
          snapshot.bomb().explodeRadius(),
          0.0);

      // Slice 9b — typed [Bomb] ProximityDistance + BombExplodeDelay parse
      // through BombAdapter (proximityDistance direct in tiles;
      // explodeDelayCs ×10 → ms). trench authors canon SVS values
      // (3 tiles base, 10 cs fuse = 100 ms).
      assertEquals(
          "trench's [Bomb] ProximityDistance = 3 tiles",
          3,
          snapshot.bomb().proximityDistance());
      assertEquals(
          "trench's [Bomb] BombExplodeDelay = 10 cs (= 100 ms)",
          100L,
          snapshot.bomb().explodeDelayMs());

      // Slice 9c-BombSafety — typed [Bomb] BombSafety parses through
      // BombAdapter as a boolean. trench opts in (= SVS canon BombSafety=1)
      // so a Warbird hugging an enemy can't lob a self-detonating bomb.
      assertTrue(
          "trench's [Bomb] BombSafety = true (opt-in, SVS canon)",
          snapshot.bomb().bombSafety());

      // [Rocket] arena-global tuning from rocket.groovy.
      assertEquals(
          "trench's [Rocket] RocketThrust = 100",
          100,
          snapshot.rocket().thrust());
      assertEquals(
          "trench's [Rocket] RocketSpeed = 3000",
          3000,
          snapshot.rocket().speed());

      // [Brick] arena-global tuning from brick.groovy. trench's
      // pre-migration misc.groovy authored only BrickTime; brick.groovy
      // fills in the canonical base BrickSpan = 7.
      assertEquals(
          "trench's [Brick] BrickSpan = 7",
          7,
          snapshot.brick().spanTiles());
      assertEquals(
          "trench's [Brick] BrickTime = 1000 cs (= 10000 ms)",
          10_000L,
          snapshot.brick().timeMs());

      // [Misc] DecoyAliveTime arena-global tuning from decoy.groovy.
      // Migrated 1:1 from trench's pre-migration misc.groovy
      // (DecoyAliveTime 10000 cs = 100000 ms).
      assertEquals(
          "trench's [Misc] DecoyAliveTime = 10000 cs (= 100000 ms)",
          100_000L,
          snapshot.decoy().aliveTimeMs());

      // [Misc] WarpPointDelay arena-global tuning from portal.groovy.
      // Migrated 1:1 from trench's pre-migration misc.groovy
      // (WarpPointDelay 24000 cs = 240000 ms).
      assertEquals(
          "trench's [Misc] WarpPointDelay = 24000 cs (= 240000 ms)",
          240_000L,
          snapshot.portal().activeTimeMs());

      // Phase 3 — prize compat shim. trench's PrizeMaxExist = 12000 (cs) → 120000 ms.
      assertEquals(
          "trench's [Prize] PrizeMaxExist = 12000 cs (= 120000 ms)",
          120_000L,
          snapshot.prize().defaultDecayMs());

      // Slice 8a: PrizeMinExist authored alongside PrizeMaxExist gives
      // each prize a random lifetime in [minExist, maxExist] cs.
      // trench: minExist 4000 cs (= 40000 ms).
      assertEquals(
          "trench's [Prize] PrizeMinExist = 4000 cs (= 40000 ms)",
          40_000L,
          snapshot.prize().defaultMinDecayMs());

      // Slice 8b: DeathPrizeTime governs the lifetime of prizes dropped
      // at a ship's death point. trench: deathPrizeTime 1500 cs (15s).
      assertEquals(
          "trench's [Prize] DeathPrizeTime = 1500 cs (= 15000 ms)",
          15_000L,
          snapshot.prize().deathPrizeTimeMs());

      // Slice 8c: PrizeNegativeFactor — 1-in-N odds for a spawning prize
      // to be replaced by Dud. trench: negativeFactor 1000.
      assertEquals(
          "trench's [Prize] PrizeNegativeFactor = 1000 (1-in-1000 odds)",
          1000,
          snapshot.prize().prizeNegativeFactor());

      // Typed prize-weights.groovy populated the prizeWeights slot (B3).
      // trench is a no-greens preset for most prize types; sample the few
      // non-zero entries to pin the typed loader's behaviour.
      assertEquals(
          "trench Repel weight = 100",
          Integer.valueOf(100),
          snapshot.prizeWeights().weights().get("Repel"));
      assertEquals(
          "trench MultiFire weight = 255",
          Integer.valueOf(255),
          snapshot.prizeWeights().weights().get("MultiFire"));
      assertEquals(
          "trench Brick weight = 3",
          Integer.valueOf(3),
          snapshot.prizeWeights().weights().get("Brick"));

      // Slice 7: typed spawn.groovy populated the spawn slot.
      // trench/spawn.groovy: warpRadiusLimit 1024, single team0 at
      // (1000, 20) radius 0 — migrated 1:1 from arena.groovy's
      // legacy `spawn 1000, 20`.
      assertEquals(
          "trench spawn warpRadiusLimit = 1024 (= no cap)",
          1024,
          snapshot.spawn().warpRadiusLimit());
      assertEquals(
          "trench spawn has 1 team authored",
          1,
          snapshot.spawn().teams().size());
      final var team0 = snapshot.spawn().forFreq(0);
      assertNotNull("trench team0 resolved for freq=0", team0);
      assertEquals("trench team0 X = 1000 (arena-local tile)", 1000, team0.x());
      assertEquals("trench team0 Y = 20 (arena-local tile)", 20, team0.y());
      assertEquals("trench team0 radius = 0 (exact-point spawn)", 0, team0.radiusTiles());
      // Wraparound: freq 1, 2, 3, 4, … all resolve to team0 because
      // teams.size() == 1 and floorMod(N, 1) == 0.
      assertEquals(
          "trench freq=4 wraps to team0 (single-team config)",
          team0,
          snapshot.spawn().forFreq(4));
    } finally {
      systems.stop();
      systems.terminate();
    }
  }
}
