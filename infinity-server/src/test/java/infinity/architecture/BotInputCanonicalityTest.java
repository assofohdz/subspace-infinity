// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaConstructorCall;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.Set;
import java.util.TreeSet;
import org.junit.Assert;
import org.junit.Test;

/**
 * Regression guards for the bot-AI input contract (ADR-0009 §3 input parity):
 * <ol>
 *   <li>{@link infinity.es.input.MovementInput} on {@link infinity.es.ship.BotShip}-marked
 *       entities is written by exactly one runtime canonical writer:
 *       {@code infinity.ai.BotBrainSystem}. Spawn-tier seeds (factories) are exempt; any
 *       other AI-tier system constructing {@code MovementInput} would split the writer
 *       responsibility.</li>
 *   <li>{@link infinity.es.input.CharacterInput} (the NPC input shape Paul Speed's Moss
 *       defines) is never constructed inside {@code infinity.ai.*} or {@code infinity.modules.*}.
 *       Bots route through {@code MovementInput} + {@code PlayerDriver}, not the NPC
 *       upright-character path — preventing accidental regression to the chicken-framework
 *       lineage.</li>
 * </ol>
 *
 * @see <a href="../../../../../../../docs/adr/0009-bot-ai-architecture.md">ADR 0009</a>
 */
public class BotInputCanonicalityTest {

  private static final String MOVEMENT_INPUT = "infinity.es.input.MovementInput";
  private static final String CHARACTER_INPUT = "infinity.es.input.CharacterInput";
  private static final String AI_PACKAGE_PREFIX = "infinity.ai.";
  private static final String MODULES_PACKAGE_PREFIX = "infinity.modules.";
  private static final String BOT_BRAIN_SYSTEM = "infinity.ai.BotBrainSystem";

  // Spawn-tier exemption: api-side factories that seed MovementInput at entity creation
  // are the "template-is-the-intent" shape; not a competing canonical writer.
  private static final Set<String> MOVEMENT_INPUT_SPAWN_EXEMPT =
      Set.of("infinity.sim.AIEntities", "infinity.sim.ShipFactory");

  @Test
  public void movementInput_in_ai_package_is_constructed_only_by_botBrainSystem() {
    final Iterable<JavaClass> classes = importNonTest();
    final Set<String> writers = new TreeSet<>();
    for (final JavaClass cls : classes) {
      final String fqn = cls.getFullName();
      if (!fqn.startsWith(AI_PACKAGE_PREFIX)) {
        continue;
      }
      if (MOVEMENT_INPUT_SPAWN_EXEMPT.contains(fqn)) {
        continue;
      }
      if (constructsTarget(cls, MOVEMENT_INPUT)) {
        writers.add(fqn);
      }
    }
    if (writers.size() == 1 && writers.contains(BOT_BRAIN_SYSTEM)) {
      return;
    }
    Assert.fail(
        "MovementInput in infinity.ai.* must be constructed only by "
            + BOT_BRAIN_SYSTEM
            + " — see ADR-0009 §3 input parity. Found writers: "
            + writers);
  }

  @Test
  public void characterInput_is_never_constructed_in_ai_or_modules_packages() {
    final Iterable<JavaClass> classes = importNonTest();
    final Set<String> offenders = new TreeSet<>();
    for (final JavaClass cls : classes) {
      final String fqn = cls.getFullName();
      if (!fqn.startsWith(AI_PACKAGE_PREFIX) && !fqn.startsWith(MODULES_PACKAGE_PREFIX)) {
        continue;
      }
      if (constructsTarget(cls, CHARACTER_INPUT)) {
        offenders.add(fqn);
      }
    }
    if (offenders.isEmpty()) {
      return;
    }
    Assert.fail(
        "CharacterInput must not be constructed inside infinity.ai.* or infinity.modules.* "
            + "— bots route through MovementInput + PlayerDriver per ADR-0009. Found: "
            + offenders);
  }

  private static Iterable<JavaClass> importNonTest() {
    return new ClassFileImporter()
        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
        .importPackages("infinity");
  }

  private static boolean constructsTarget(final JavaClass cls, final String target) {
    for (final JavaConstructorCall call : cls.getConstructorCallsFromSelf()) {
      if (call.getTargetOwner().getFullName().equals(target)) {
        return true;
      }
    }
    return false;
  }
}
