// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaConstructor;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.Set;
import java.util.TreeSet;
import org.junit.Assert;
import org.junit.Test;

/**
 * Enforces ECS component immutability + no-arg constructor invariants under {@code infinity.es..}.
 *
 * @see <a href="../../../../../../../.claude/rules/components.md">components rule</a>
 * @see <a href="../../../../../../../docs/adr/0001-ecs-component-model.md">ADR 0001</a>
 */
public class ComponentImmutabilityTest {

  private static final String ES_PACKAGE_PREFIX = "infinity.es.";
  private static final String ENTITY_COMPONENT_FQN = "com.simsilica.es.EntityComponent";

  /**
   * Allowlist for classes that implement {@code EntityComponent} but legitimately cannot satisfy
   * the strict rule. Add a one-line WHY comment per entry; tighten by removing entries when the
   * underlying class is fixed.
   */
  private static final Set<String> ALLOWLIST =
      Set.of(
          // Legacy pre-rule classes — package-private mutable fields. Fixing them is out of scope
          // for the test introduction (architectural-review item P1-b). Track + clean up later.
          "infinity.es.ship.toggles.Multishot",
          "infinity.es.PointLightComponent");

  @Test
  public void all_entity_components_are_immutable_with_no_arg_constructor() {
    final Iterable<JavaClass> classes =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("infinity");

    final StringBuilder problems = new StringBuilder();
    for (final JavaClass cls : classes) {
      if (!isInScope(cls)) {
        continue;
      }
      checkComponent(cls, problems);
    }

    if (problems.length() > 0) {
      Assert.fail(
          "Component immutability violation(s) — see .claude/rules/components.md:\n"
              + problems
              + "\nFix by making the field final, removing the setter, or adding a no-arg "
              + "constructor. If the class genuinely cannot comply (generated code / framework-"
              + "required mutability) add its FQN to ALLOWLIST with a one-line WHY comment.");
    }
  }

  private static boolean isInScope(final JavaClass cls) {
    final String fqn = cls.getFullName();
    if (!fqn.startsWith(ES_PACKAGE_PREFIX)) {
      return false;
    }
    if (cls.isInterface() || cls.getModifiers().contains(JavaModifier.ABSTRACT)) {
      return false;
    }
    if (ALLOWLIST.contains(fqn)) {
      return false;
    }
    return implementsEntityComponent(cls);
  }

  private static boolean implementsEntityComponent(final JavaClass cls) {
    for (final JavaClass iface : cls.getAllRawInterfaces()) {
      if (ENTITY_COMPONENT_FQN.equals(iface.getFullName())) {
        return true;
      }
    }
    return false;
  }

  private static void checkComponent(final JavaClass cls, final StringBuilder problems) {
    final Set<String> issues = new TreeSet<>();
    checkFields(cls, issues);
    checkSetters(cls, issues);
    checkNoArgCtor(cls, issues);

    if (!issues.isEmpty()) {
      problems.append("  - ").append(cls.getFullName()).append('\n');
      for (final String issue : issues) {
        problems.append("      * ").append(issue).append('\n');
      }
    }
  }

  private static void checkFields(final JavaClass cls, final Set<String> issues) {
    for (final JavaField field : cls.getFields()) {
      final Set<JavaModifier> mods = field.getModifiers();
      if (mods.contains(JavaModifier.STATIC)) {
        continue; // constants / markers are fine
      }
      if (!mods.contains(JavaModifier.FINAL)) {
        issues.add("non-final instance field: " + field.getName());
      }
    }
  }

  private static void checkSetters(final JavaClass cls, final Set<String> issues) {
    for (final JavaMethod method : cls.getMethods()) {
      if (!method.getName().startsWith("set") || method.getName().length() < 4) {
        continue;
      }
      if (!Character.isUpperCase(method.getName().charAt(3))) {
        continue;
      }
      if (method.getRawParameterTypes().size() != 1) {
        continue;
      }
      if (!"void".equals(method.getRawReturnType().getName())) {
        continue;
      }
      issues.add("setter method: " + method.getName() + "(...)");
    }
  }

  private static void checkNoArgCtor(final JavaClass cls, final Set<String> issues) {
    for (final JavaConstructor ctor : cls.getConstructors()) {
      if (ctor.getRawParameterTypes().isEmpty()) {
        return;
      }
    }
    issues.add("missing no-arg constructor (required for Zay-ES deserialization)");
  }
}
