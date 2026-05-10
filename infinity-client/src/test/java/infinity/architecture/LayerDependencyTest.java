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

@RunWith(ArchUnitRunner.class)
@AnalyzeClasses(
    packages = "infinity",
    importOptions = ImportOption.DoNotIncludeTests.class)
public class LayerDependencyTest {

  /** api/ (components + events + sim factories + config records) must not leak into server/client/modules/ai. */
  @ArchTest
  static final ArchRule api_must_not_depend_on_server_client_or_modules =
      noClasses()
          .that()
          .resideInAnyPackage(
              "infinity.es..",
              "infinity.events..",
              "infinity.sim..",
              "infinity.config..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "infinity.systems..",
              "infinity.server..",
              "infinity.client..",
              "infinity.modules..",
              "infinity.ai..");

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
   * Client must not reach into server/modules/ai. Commands go via RMI, not direct calls.
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
              "infinity.systems..", "infinity.server..", "infinity.modules..", "infinity.ai..");
}
