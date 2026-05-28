// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.objective;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class BotRoleRegistryTest {

  private static final String DEFAULT = "default";
  private static final String ENGAGE = "engage";

  @Test
  public void getReturnsRegisteredRole() {
    final BotRoleRegistry r =
        new BotRoleRegistry(List.of(new BotRoleConfig("flag-defender", Map.of("hold-position", 2.0))));
    assertEquals(2.0, r.get("flag-defender").behaviourBias().get("hold-position"), 0.0);
    assertTrue(r.isRegistered("flag-defender"));
  }

  @Test
  public void unknownRoleFallsToDefaultIdentity() {
    final BotRoleRegistry r = new BotRoleRegistry(List.of());
    assertTrue("default always present", r.isRegistered(DEFAULT));
    assertFalse(r.isRegistered("nope"));
    assertTrue("unknown ⇒ identity bias", r.get("nope").behaviourBias().isEmpty());
    assertEquals(DEFAULT, r.get("nope").name());
  }

  @Test
  public void botRoleConfigDefensivelyCopiesBias() {
    final Map<String, Double> mutable = new java.util.HashMap<>();
    mutable.put(ENGAGE, 1.5);
    final BotRoleConfig cfg = new BotRoleConfig("r", mutable);
    mutable.put(ENGAGE, 9.9);
    assertEquals(1.5, cfg.behaviourBias().get(ENGAGE), 0.0);
    assertThrows(
        UnsupportedOperationException.class, () -> cfg.behaviourBias().put("x", 1.0));
  }

  @Test
  public void authoredDefaultOverridesBuiltIn() {
    final BotRoleRegistry r =
        new BotRoleRegistry(new ArrayList<>(List.of(new BotRoleConfig(DEFAULT, Map.of(ENGAGE, 0.5)))));
    assertEquals(0.5, r.get(DEFAULT).behaviourBias().get(ENGAGE), 0.0);
  }
}
