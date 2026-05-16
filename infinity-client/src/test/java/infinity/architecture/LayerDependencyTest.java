// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchUnitRunner;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.runner.RunWith;

/**
 * Layer-dependency invariants enforced as tests.
 *
 * <p>Post-megasplit (commit 3cf92a86), the project is split into the {@code :api},
 * {@code :infinity-server}, {@code :infinity-client}, and {@code :modules} Gradle modules.
 * That split means rules 1 and 2 below are belt-and-suspenders:
 *
 * <ul>
 *   <li><b>Rule 1</b> ({@code api/} has no dep on server/client/modules) — already enforced
 *       at compile time because {@code :api} declares no dependency on the higher-layer
 *       modules. ArchUnit catches violations that would be introduced by a new gradle dep
 *       edge before they ship.
 *   <li><b>Rule 2</b> (server/modules/ai have no dep on client) — same story: {@code
 *       :infinity-server} and {@code :modules} have no compile dep on {@code
 *       :infinity-client}, so this is a guard against a future cross-module dep being added.
 *   <li><b>Rule 3</b> (client has no dep on server/modules/ai except the documented
 *       exceptions) earns its keep on its own merits. {@code :infinity-client} <i>does</i>
 *       compile-depend on {@code :infinity-server} (for {@code HostState} co-hosting), so
 *       gradle module-level enforcement cannot catch cross-package leaks within that edge.
 *       This rule is what actually keeps client code from reaching into server internals
 *       outside the allowed boundary classes.
 * </ul>
 */
@RunWith(ArchUnitRunner.class)
@AnalyzeClasses(
    packages = "infinity",
    importOptions = ImportOption.DoNotIncludeTests.class)
public class LayerDependencyTest {

  private static final String PKG_SYSTEMS = "infinity.systems..";
  private static final String PKG_SERVER = "infinity.server..";
  private static final String PKG_CLIENT = "infinity.client..";
  private static final String PKG_MODULES = "infinity.modules..";
  private static final String PKG_AI = "infinity.ai..";
  private static final String PKG_SETTINGS = "infinity.settings..";
  private static final String PKG_CONFIG = "infinity.config..";

  /**
   * api/ (components + events + sim factories + config records) must not leak into
   * server/client/ai/settings.
   *
   * <p>{@code infinity.modules..} is NOT in either list: the package splits across api/
   * (value types — records, enums, marker interfaces) and infinity-server/ (concrete impls)
   * under the same package name per ADR-0008. Gradle's module boundary (api/ has no compile
   * dep on infinity-server/) is what actually prevents api/-side classes from importing
   * server-side classes in the same package; ArchUnit can't distinguish by source-set.
   *
   * <p>The same applies to {@code infinity.sim..} — but there the legacy
   * {@code infinity.sim.internal..} sub-package was introduced for server impls. The exclusion
   * clause below carries that historical split forward.
   */
  @ArchTest
  static final ArchRule api_must_not_depend_on_server_client_or_modules =
      noClasses()
          .that()
          .resideInAnyPackage(
              "infinity.es..",
              "infinity.events..",
              "infinity.sim..",
              "infinity.config..")
          .and()
          .resideOutsideOfPackage("infinity.sim.internal..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(PKG_SYSTEMS, PKG_SERVER, PKG_CLIENT, PKG_AI, PKG_SETTINGS);

  /** Server, modules, and AI must not reach into client code. */
  @ArchTest
  static final ArchRule server_modules_ai_must_not_depend_on_client =
      noClasses()
          .that()
          .resideInAnyPackage(PKG_SYSTEMS, PKG_SERVER, PKG_MODULES, PKG_AI)
          .should()
          .dependOnClassesThat()
          .resideInAPackage(PKG_CLIENT);

  /**
   * Hot-path systems must not import {@code infinity.config..} — Config-Component Projection
   * (ADR-0002) says tuning values cross into the entity world at spawn time only, and hot-path
   * code reads components. The named-allowlist regex below covers the spawn-tier exempt set:
   * ship spawn + per-aspect projectors, arena structural setup, admin / reload paths, and
   * entity-creation sites (projectile spawn, consumable spawn, prize spawn) that project
   * per-instance state at creation time. Each entry matches the outer class and any
   * {@code $Inner} class via the trailing optional group.
   */
  @ArchTest
  static final ArchRule hot_path_systems_must_not_depend_on_infinity_config =
      noClasses()
          .that()
          .resideInAPackage(PKG_SYSTEMS)
          .and()
          .haveNameNotMatching(
              "infinity\\.systems\\.("
                  + "ArenaCommandsSystem"
                  + "|ArenaLogic|ArenaLogic\\$.*"
                  + "|ArenaSpatialIndex"
                  + "|ArenaSystem|ArenaSystem\\$.*"
                  + "|AvatarSystem"
                  + "|ConfigShipRestrictor"
                  + "|LegacyMapProjector|LegacyMapProjector\\$.*"
                  + "|MapSystem"
                  + "|PrizeSpawnerSystem|PrizeSpawnerSystem\\$.*"
                  + "|DeathPrizeSystem|DeathPrizeSystem\\$.*"
                  + "|ship\\.ConsumableLogic"
                  + "|ship\\.ConsumableSystem"
                  + "|ship\\.RepelSystem"
                  + "|ship\\.ShipSpawnSystem"
                  + "|ship\\.ShipStatusProjector"
                  + "|ship\\.ShipWeaponsProjector"
                  + "|ship\\.WeaponsFireEligibilitySystem"
                  + "|ship\\.WeaponsProjectileSpawnSystem"
                  + ")")
          .should()
          .dependOnClassesThat()
          .resideInAPackage(PKG_CONFIG);

  @ArchTest
  static final ArchRule client_must_not_depend_on_server_modules_or_ai =
      noClasses()
          .that()
          .resideInAPackage(PKG_CLIENT)
          .and()
          .doNotHaveFullyQualifiedName("infinity.client.states.MobDebugState")
          .and()
          .doNotHaveFullyQualifiedName("infinity.client.states.HostState")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              PKG_SYSTEMS,
              PKG_SERVER,
              PKG_MODULES,
              PKG_AI,
              "infinity.sim.internal..");
}
