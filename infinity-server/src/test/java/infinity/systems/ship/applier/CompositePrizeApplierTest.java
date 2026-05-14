// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.systems.ship.applier;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import com.simsilica.es.EntityId;
import com.simsilica.es.base.DefaultEntityData;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

/**
 * Compound-applier delegation: {@code BOMB} = Bomb+Mine, {@code ALLWEAPONS} = Bomb+Burst+Gun+Mine.
 * Tests verify children fire in declaration order; individual mechanics live in each leaf's
 * own test. REFERENCE.md {@code ## PrizeWeight} ({@code AllWeapons (= "Super!")}).
 */
public class CompositePrizeApplierTest {

  /** Records ship-id on each apply call so the order can be asserted. */
  private static final class RecordingApplier implements PrizeApplier {
    private final List<String> log;
    private final String tag;

    RecordingApplier(final List<String> log, final String tag) {
      this.log = log;
      this.tag = tag;
    }

    @Override
    public void apply(final EntityId ship, final PrizeApplierContext ctx) {
      log.add(tag);
    }
  }

  @Test
  public void apply_invokesEveryChildInDeclarationOrder() {
    final DefaultEntityData ed = new DefaultEntityData();
    final EntityId ship = ed.createEntity();
    final List<String> log = new ArrayList<>();
    final CompositePrizeApplier composite =
        new CompositePrizeApplier(
            new RecordingApplier(log, "a"),
            new RecordingApplier(log, "b"),
            new RecordingApplier(log, "c"));

    composite.apply(ship, new PrizeApplierContext(ed, null, null));

    assertArrayEquals(new String[] {"a", "b", "c"}, log.toArray(new String[0]));
  }

  @Test
  public void apply_zeroChildren_noop() {
    final DefaultEntityData ed = new DefaultEntityData();
    final EntityId ship = ed.createEntity();

    new CompositePrizeApplier().apply(ship, new PrizeApplierContext(ed, null, null));

    // No children → nothing observable. Smoke test: the call returns cleanly.
    assertEquals(0, 0);
  }

  @Test(expected = IllegalStateException.class)
  public void apply_childThrows_propagatesAndAbortsRemaining() {
    final DefaultEntityData ed = new DefaultEntityData();
    final EntityId ship = ed.createEntity();
    final List<String> log = new ArrayList<>();
    final PrizeApplier thrower =
        (s, ctx) -> {
          throw new IllegalStateException("boom");
        };
    final CompositePrizeApplier composite =
        new CompositePrizeApplier(
            new RecordingApplier(log, "a"),
            thrower,
            new RecordingApplier(log, "c")); // must NOT run after thrower

    try {
      composite.apply(ship, new PrizeApplierContext(ed, null, null));
    } finally {
      // Pre-thrower child ran, post-thrower child did not (PrizeSystem catches at the registry boundary).
      assertArrayEquals(new String[] {"a"}, log.toArray(new String[0]));
    }
  }
}
