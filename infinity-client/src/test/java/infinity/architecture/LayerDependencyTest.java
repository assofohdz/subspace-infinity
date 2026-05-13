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

  /**
   * api/ (components + events + sim factories + config records) must not leak into
   * server/client/modules/ai/settings.
   *
   * <p>Note: the {@code infinity.sim..} package prefix matches BOTH api-side
   * (factories, manager interfaces — the module-facing ABI) and server-side
   * {@code infinity.sim.internal..} (concrete impls relocated per ADR-0005). The
   * latter is server-tier and legitimately depends on server-tier classes; the
   * {@code .and().resideOutsideOfPackage} clause excludes it from this rule.
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
          .resideInAnyPackage(
              "infinity.systems..",
              "infinity.server..",
              "infinity.client..",
              "infinity.modules..",
              "infinity.ai..",
              "infinity.settings..");

  /** Server, modules, and AI must not reach into client code. */
  @ArchTest
  static final ArchRule server_modules_ai_must_not_depend_on_client =
      noClasses()
          .that()
          .resideInAnyPackage(
              "infinity.systems..", "infinity.server..", "infinity.modules..", "infinity.ai..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("infinity.client..");

  /**
   * Client must not reach into server/modules/ai/internal. Commands go via RMI, not direct calls.
   * See ADR-0005 for the api-side {@code infinity.sim..} vs server-internal
   * {@code infinity.sim.internal..} relocation that closed the CubeFactory class of leak.
   *
   * <p>Exceptions:
   *
   * <ul>
   *   <li>{@code MobDebugState} — client-side debug overlay that intentionally reads
   *       {@code MobSystem}/{@code MobStats} internals for debug visualization, legitimate in
   *       co-hosted client/server.
   *   <li>{@code HostState} — "Host a Game" state that spawns and manages a local
   *       {@code GameServer} inside the client process. It IS the co-hosting orchestration
   *       boundary, so a direct dependency on {@code infinity.server.GameServer} is
   *       structural, not a layering leak.
   * </ul>
   */
  @ArchTest
  static final ArchRule client_must_not_depend_on_server_modules_or_ai =
      noClasses()
          .that()
          .resideInAPackage("infinity.client..")
          .and()
          .doNotHaveFullyQualifiedName("infinity.client.states.MobDebugState")
          .and()
          .doNotHaveFullyQualifiedName("infinity.client.states.HostState")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "infinity.systems..",
              "infinity.server..",
              "infinity.modules..",
              "infinity.ai..",
              "infinity.sim.internal..");
}
