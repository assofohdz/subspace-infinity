// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.modules;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.simsilica.es.EntityId;
import com.simsilica.es.base.DefaultEntityData;
import infinity.es.arena.ArenaId;
import infinity.sim.ArenaModule;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Cleanup-contract test (per [ADR-0008](docs/adr/0008-arena-composition-and-modules.md)):
 * every module in {@link ModuleCatalog} must produce net-zero entities after a full
 * load → unload lifecycle. Each parameterised case builds the module via
 * {@link ModuleLoader#build}, runs {@code onArenaLoad → onMatchStart → onRoundStart(1)
 * → onRoundEnd → onMatchEnd → onArenaUnload}, and asserts the underlying
 * {@link DefaultEntityData} has no net entity delta beyond the arena entity itself.
 *
 * <p>Per-tick dispatchers (e.g. {@code tickMechanic}, {@code tickTeamSetup}) are
 * intentionally NOT exercised — those create transient game-element entities that
 * live and die on their own lifecycle. The contract this test enforces is the
 * narrower one: <em>lifecycle hooks alone don't leak</em>.
 */
@RunWith(Parameterized.class)
public final class ArenaModuleContractTest {

  /** Canonical default kwargs per module that requires non-empty config to validate. */
  private static final Map<String, Map<String, Object>> DEFAULT_KWARGS = defaults();

  private static Map<String, Map<String, Object>> defaults() {
    final Map<String, Map<String, Object>> map = new HashMap<>();
    map.put("kill-points", Map.of("perKill", 1));
    map.put("timed-round", Map.of("minutes", 1));
    map.put("crown-reset", Map.of("minutes", 1));
    map.put("random-radius", Map.of("center", List.of(512, 512), "radius", 0));
    // Configs with a no-arg / zero-validating default constructor handle Map.of() fine
    // (fill-up-x-teams, first-to-x, cooldown-respawn). Zero-config modules ignore kwargs.
    return Map.copyOf(map);
  }

  private final String moduleId;
  private final ModuleDescriptor descriptor;

  public ArenaModuleContractTest(final String moduleId, final ModuleDescriptor descriptor) {
    this.moduleId = moduleId;
    this.descriptor = descriptor;
  }

  @Parameterized.Parameters(name = "{0}")
  public static Collection<Object[]> parameters() {
    final List<Object[]> rows = new ArrayList<>();
    for (final Map.Entry<String, ModuleDescriptor> entry : ModuleCatalog.allDescriptors()) {
      rows.add(new Object[] {entry.getKey(), entry.getValue()});
    }
    return rows;
  }

  @Test
  public void lifecycleLeavesNoLeftoverEntities() {
    final CountingEntityData ed = new CountingEntityData();
    final EntityId arenaEntity = ed.createEntity();
    final ArenaId arenaId = new ArenaId("contract-test-arena", arenaEntity);

    final ArenaModuleDeclarations decls = singleModuleDecls(moduleId, descriptor);
    final ValidationResult validation = ModuleLoader.validate(decls);
    assertEquals("module '" + moduleId + "' must validate; errors=" + validation.errors(),
        List.of(), validation.errors());

    final ModuleContext ctx = new ModuleContext(arenaId, arenaEntity, ed, null, null);
    final ArenaModuleSet set = ModuleLoader.build(decls, ctx);
    final List<ArenaModule> modules = set.allModules();
    assertEquals("expected exactly one module loaded", 1, modules.size());

    final int baselineCreated = ed.created();
    final int baselineRemoved = ed.removed();

    final ArenaModule module = modules.get(0);
    runLifecycle(module, arenaId);

    final int createdDelta = ed.created() - baselineCreated;
    final int removedDelta = ed.removed() - baselineRemoved;
    final int netCreatedDuringLifecycle = createdDelta - removedDelta;
    assertEquals(
        "module '" + moduleId + "' leaked " + netCreatedDuringLifecycle
            + " entities across load→unload",
        0, netCreatedDuringLifecycle);
  }

  private static void runLifecycle(final ArenaModule module, final ArenaId arenaId) {
    module.onArenaLoad(arenaId);
    module.onMatchStart(arenaId);
    module.onRoundStart(arenaId, 1);
    module.onRoundEnd(arenaId, 1, null);
    module.onMatchEnd(arenaId, null);
    module.onArenaUnload(arenaId);
  }

  @SuppressWarnings("PMD.CyclomaticComplexity") // 10 cases mirror the 10 ModuleCategory values.
  private static ArenaModuleDeclarations singleModuleDecls(
      final String id, final ModuleDescriptor desc) {
    final Map<String, Object> kwargs = DEFAULT_KWARGS.getOrDefault(id, Map.of());
    final ModuleSpec spec = new ModuleSpec(id, kwargs);
    assertNotNull("descriptor must declare a category", desc.category());
    return switch (desc.category()) {
      case TEAM_SETUP -> decls(Optional.of(spec), Optional.empty(), Optional.empty(),
          Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
          List.of(), List.of(), Map.of());
      case ROSTER -> decls(Optional.empty(), Optional.of(spec), Optional.empty(),
          Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
          List.of(), List.of(), Map.of());
      case RESPAWN_POLICY -> decls(Optional.empty(), Optional.empty(), Optional.of(spec),
          Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
          List.of(), List.of(), Map.of());
      case ROUND_STRUCTURE -> decls(Optional.empty(), Optional.empty(), Optional.empty(),
          Optional.of(spec), Optional.empty(), Optional.empty(), Optional.empty(),
          List.of(), List.of(), Map.of());
      case MATCH_STRUCTURE -> decls(Optional.empty(), Optional.empty(), Optional.empty(),
          Optional.empty(), Optional.of(spec), Optional.empty(), Optional.empty(),
          List.of(), List.of(), Map.of());
      case SPAWN_PLACEMENT -> decls(Optional.empty(), Optional.empty(), Optional.empty(),
          Optional.empty(), Optional.empty(), Optional.of(spec), Optional.empty(),
          List.of(), List.of(), Map.of());
      case SHOP -> decls(Optional.empty(), Optional.empty(), Optional.empty(),
          Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(spec),
          List.of(), List.of(), Map.of());
      case SCORING -> decls(Optional.empty(), Optional.empty(), Optional.empty(),
          Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
          List.of(spec), List.of(), Map.of());
      case WIN_CONDITION -> decls(Optional.empty(), Optional.empty(), Optional.empty(),
          Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
          List.of(), List.of(spec), Map.of());
      case MECHANIC -> decls(Optional.empty(), Optional.empty(), Optional.empty(),
          Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
          List.of(), List.of(), Map.of(id, spec));
    };
  }

  @SuppressWarnings("PMD.ExcessiveParameterList") // mirrors ArenaModuleDeclarations' 10-field record ctor.
  private static ArenaModuleDeclarations decls(
      final Optional<ModuleSpec> teamSetup,
      final Optional<ModuleSpec> roster,
      final Optional<ModuleSpec> respawnPolicy,
      final Optional<ModuleSpec> roundStructure,
      final Optional<ModuleSpec> matchStructure,
      final Optional<ModuleSpec> spawnPlacement,
      final Optional<ModuleSpec> shop,
      final List<ModuleSpec> scoring,
      final List<ModuleSpec> winConditions,
      final Map<String, ModuleSpec> mechanics) {
    return new ArenaModuleDeclarations(
        teamSetup, roster, respawnPolicy, roundStructure, matchStructure,
        spawnPlacement, shop, scoring, winConditions, mechanics);
  }

  /** {@link DefaultEntityData} subclass that counts create/remove for net-delta assertions. */
  private static final class CountingEntityData extends DefaultEntityData {
    private final AtomicInteger created = new AtomicInteger();
    private final AtomicInteger removed = new AtomicInteger();

    @Override
    public EntityId createEntity() {
      created.incrementAndGet();
      return super.createEntity();
    }

    @Override
    public void removeEntity(final EntityId entityId) {
      removed.incrementAndGet();
      super.removeEntity(entityId);
    }

    int created() {
      return created.get();
    }

    int removed() {
      return removed.get();
    }
  }
}
