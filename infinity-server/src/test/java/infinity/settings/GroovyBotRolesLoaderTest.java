// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import infinity.ai.objective.BotRoleRegistry;
import org.junit.Test;

/** Loads the real {@code zone/engine-bot-ai.groovy} roles block (ignoring its synergy block). */
public class GroovyBotRolesLoaderTest {

  @Test
  public void realFileParsesRoles() {
    final BotRoleRegistry r = new GroovyBotRolesLoader().load();
    assertTrue(r.isRegistered("default"));
    assertTrue(r.isRegistered("flag-defender"));
    assertTrue(r.isRegistered("flag-attacker"));
    assertEquals(2.0, r.get("flag-defender").behaviourBias().get("hold-position"), 0.0);
    assertEquals(0.4, r.get("flag-attacker").behaviourBias().get("hold-position"), 0.0);
    assertEquals(1.6, r.get("flag-attacker").behaviourBias().get("engage"), 0.0);
  }

  @Test
  public void unknownRoleFallsToDefaultIdentity() {
    assertTrue(new GroovyBotRolesLoader().load().get("nonexistent").behaviourBias().isEmpty());
  }
}
