/*
 * Copyright (c) 2018-2026, Asser Fahrenholz
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
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

  /** api/ (components + events) must not leak into server/client/modules/ai. */
  @ArchTest
  static final ArchRule api_must_not_depend_on_server_client_or_modules =
      noClasses()
          .that()
          .resideInAnyPackage("infinity.es..", "infinity.events..")
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
   * <p>Exception: {@code MobDebugState} is a client-side debug overlay that intentionally reads
   * {@code MobSystem}/{@code MobStats} internals for debug visualization — legitimate in
   * co-hosted client/server.
   */
  @ArchTest
  static final ArchRule client_must_not_depend_on_server_modules_or_ai =
      noClasses()
          .that()
          .resideInAPackage("infinity.client..")
          .and()
          .doNotHaveSimpleName("MobDebugState")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "infinity.systems..", "infinity.server..", "infinity.modules..", "infinity.ai..");
}
