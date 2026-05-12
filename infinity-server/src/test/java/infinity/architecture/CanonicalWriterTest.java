// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaConstructorCall;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.Test;

/**
 * Enforces the Replacement-as-Mutation rule: each target component drained by a {@code *Change} /
 * {@code *StatsChange} payload has exactly one canonical writer in {@code infinity.systems..}.
 *
 * <p>Scans constructor call sites for each registered target component type and asserts that only
 * the canonical writer system (plus the documented spawn-tier projector / factory exemptions)
 * constructs the target. Catches future violations where a second system silently starts writing a
 * canonical-owned component instead of emitting a Change entity through the owner.
 *
 * @see <a href="../../../../../../../.claude/rules/replacement-as-mutation.md">RaM rule</a>
 * @see <a href="../../../../../../../docs/adr/0001-ecs-component-model.md">ADR 0001</a>
 */
public class CanonicalWriterTest {

  /**
   * Registry of (target component → canonical writer). Each row is one ADR 0001 aspect. Adding a
   * new {@code *Change} type requires adding a row here so the test guards the new writer too.
   */
  private static final Map<String, String> CANONICAL_WRITER = new LinkedHashMap<>();

  static {
    // ship-state aspects
    put("infinity.es.ship.Energy", "infinity.systems.ship.EnergySystem");
    put("infinity.es.ship.EnergyStats", "infinity.systems.ship.EnergyStatsSystem");
    put("infinity.es.ship.Rotation", "infinity.systems.ship.RotationSystem");
    put("infinity.es.ship.Speed", "infinity.systems.ship.SpeedSystem");
    put("infinity.es.ship.Thrust", "infinity.systems.ship.ThrustSystem");
    // fresh-finds (ADR C4)
    put("infinity.es.Frequency", "infinity.systems.FrequencySystem");
    put("infinity.es.ship.ShipType", "infinity.systems.AvatarSystem");
    // status-family toggles
    put("infinity.es.ship.toggles.AntiwarpActive", "infinity.systems.ship.AntiwarpSystem");
    put("infinity.es.ship.toggles.CloakActive", "infinity.systems.ship.CloakSystem");
    put("infinity.es.ship.toggles.StealthActive", "infinity.systems.ship.StealthSystem");
    put("infinity.es.ship.toggles.XRadarActive", "infinity.systems.ship.XRadarSystem");
    // weapon-level (Wave 4a)
    put("infinity.es.ship.weapons.BombCurrentLevel", "infinity.systems.ship.BombSystem");
    put("infinity.es.ship.weapons.BulletCurrentLevel", "infinity.systems.ship.BulletSystem");
    put("infinity.es.ship.weapons.MineCurrentLevel", "infinity.systems.ship.MineSystem");
    // inventory (Wave 4b)
    put("infinity.es.ship.actions.Brick", "infinity.systems.ship.BrickSystem");
    put("infinity.es.ship.actions.Burst", "infinity.systems.ship.BurstSystem");
    put("infinity.es.ship.actions.Decoy", "infinity.systems.ship.DecoySystem");
    put("infinity.es.ship.actions.Portal", "infinity.systems.ship.PortalSystem");
    put("infinity.es.ship.actions.Repel", "infinity.systems.ship.RepelCountSystem");
    put("infinity.es.ship.actions.Rocket", "infinity.systems.ship.RocketSystem");
    put("infinity.es.ship.actions.ThorCurrentCount", "infinity.systems.ship.ThorSystem");
    // WarpToChange deliberately omitted: side-effect intent (physics teleport via BodyPosition),
    // not a target-component write. WarpSystem is the sole drainer by inspection; code review
    // is the safety net for this shape since the constructor-call check doesn't apply.
  }

  /**
   * Spawn-tier projector / factory classes that may additionally construct target components at
   * entity-creation time. The ADR 0001 RaM rule explicitly exempts the factory tier: "spawn-time
   * projection from a template is the single-writer at creation time" — the template IS the intent.
   */
  private static final Set<String> SPAWN_TIER_EXEMPT =
      Set.of(
          "infinity.systems.ship.ShipSpawnSystem",
          "infinity.systems.ship.ShipStatusProjector",
          "infinity.systems.ship.ShipWeaponsProjector");

  private static void put(final String target, final String writer) {
    CANONICAL_WRITER.put(target, writer);
  }

  /** Scope restriction: enforce RaM only within {@code infinity.systems..} (server runtime). */
  private static final String SYSTEMS_PACKAGE_PREFIX = "infinity.systems.";

  @Test
  public void each_target_component_has_exactly_one_canonical_writer_in_systems_package() {
    final JavaClasses classes =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("infinity");

    final StringBuilder problems = new StringBuilder();
    for (final Map.Entry<String, String> entry : CANONICAL_WRITER.entrySet()) {
      appendIfViolating(classes, entry.getKey(), entry.getValue(), problems);
    }

    if (problems.length() > 0) {
      throw new AssertionError(
          "Replacement-as-Mutation violation(s) — see .claude/rules/replacement-as-mutation.md:\n"
              + problems
              + "\nFix by routing the extra writers through the canonical writer via a "
              + "*Change entity (ChangeTarget + typed payload), or — if the extra writer is "
              + "spawn-tier — add its FQN to SPAWN_TIER_EXEMPT.");
    }
  }

  private static void appendIfViolating(
      final JavaClasses classes,
      final String target,
      final String expectedWriter,
      final StringBuilder problems) {
    final Set<String> actualWriters = findWritersOf(classes, target);
    if (actualWriters.isEmpty()) {
      problems
          .append("  - target ")
          .append(target)
          .append(" has NO writer in infinity.systems.. (expected ")
          .append(expectedWriter)
          .append(")\n");
      return;
    }
    if (actualWriters.size() == 1 && actualWriters.contains(expectedWriter)) {
      return; // canonical
    }
    problems
        .append("  - target ")
        .append(target)
        .append(" expected canonical writer { ")
        .append(expectedWriter)
        .append(" } but found writers ")
        .append(actualWriters)
        .append('\n');
  }

  private static Set<String> findWritersOf(final JavaClasses classes, final String target) {
    final Set<String> writers = new TreeSet<>();
    for (final JavaClass cls : classes) {
      final String fqn = cls.getFullName();
      if (!fqn.startsWith(SYSTEMS_PACKAGE_PREFIX)) {
        continue; // RaM scope = infinity.systems..
      }
      if (SPAWN_TIER_EXEMPT.contains(fqn) || fqn.equals(target)) {
        continue; // factory-tier exemption + a component's own ctor is not a writer call
      }
      if (constructsTarget(cls, target)) {
        writers.add(fqn);
      }
    }
    return writers;
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
