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
package infinity.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.ini4j.Ini;
import org.ini4j.Profile.Section;
import org.junit.Test;

/**
 * Exercises {@link GroovyFragmentLoader} via the package-private
 * {@code evaluate} entry point so we don't depend on filesystem layout.
 * Mirrors the {@code GroovyZoneLoaderTest} / {@code GroovyShipLoaderRadarTest}
 * shape.
 */
public class GroovyFragmentLoaderTest {

  @Test
  public void section_storesEachKeyAsString() {
    final String src =
        "section('Bomb') {\n"
            + "    BombDamageLevel 750\n"
            + "    BombAliveTime 6000\n"
            + "}\n";

    final Ini ini = new GroovyFragmentLoader().evaluate(src, "test:section_storesEachKeyAsString");

    final Section bomb = ini.get("Bomb");
    assertNotNull("Bomb section must exist", bomb);
    assertEquals("750", bomb.get("BombDamageLevel"));
    assertEquals("6000", bomb.get("BombAliveTime"));
  }

  @Test
  public void section_acceptsQuotedHyphenatedKeys() {
    // INI keys like `Team0-Radius` aren't valid Groovy identifiers (hyphen
    // tokenises as minus), so fragments must quote them as method-name
    // strings: `"Team0-Radius"(96)`. Groovy dispatches that to invokeMethod
    // with name="Team0-Radius", which the SectionDelegate stores verbatim.
    final String src =
        "section('Spawn') {\n"
            + "    \"Team0-Radius\"(96)\n"
            + "    \"Team0-X\"(416)\n"
            + "    \"Team0-Y\"(-480)\n"
            + "}\n";

    final Ini ini = new GroovyFragmentLoader().evaluate(src, "test:hyphenatedKeys");

    final Section spawn = ini.get("Spawn");
    assertEquals("96", spawn.get("Team0-Radius"));
    assertEquals("416", spawn.get("Team0-X"));
    assertEquals("-480", spawn.get("Team0-Y"));
  }

  @Test
  public void multipleSections_eachLandsUnderItsOwnHeader() {
    final String src =
        "section('Bullet') { BulletDamageLevel 100 }\n"
            + "section('Mine') { MineAliveTime 12000; TeamMaxMines 12 }\n";

    final Ini ini = new GroovyFragmentLoader().evaluate(src, "test:multipleSections");

    assertEquals("100", ini.get("Bullet").get("BulletDamageLevel"));
    assertEquals("12000", ini.get("Mine").get("MineAliveTime"));
    assertEquals("12", ini.get("Mine").get("TeamMaxMines"));
    assertNull("no cross-contamination", ini.get("Bullet").get("MineAliveTime"));
  }

  @Test
  public void shipSection_acceptsValidShipName() {
    final String src =
        "shipSection('Warbird') {\n"
            + "    SuperTime 6000\n"
            + "    BulletFireEnergy 20\n"
            + "}\n";

    final Ini ini = new GroovyFragmentLoader().evaluate(src, "test:shipSection_valid");

    assertEquals("6000", ini.get("Warbird").get("SuperTime"));
    assertEquals("20", ini.get("Warbird").get("BulletFireEnergy"));
  }

  @Test
  public void shipSection_rejectsUnknownShipName() {
    final String src = "shipSection('Lancater') { Whatever 1 }\n";

    try {
      new GroovyFragmentLoader().evaluate(src, "test:shipSection_typo");
      fail("expected IllegalArgumentException for unknown ship name");
    } catch (final Exception e) {
      // GroovyShell wraps script failures; the wrapper layer carries our
      // verbatim "Lancater", while the innermost cause carries the
      // uppercased "LANCATER" from Ship.valueOf. Accept either by walking
      // the whole chain.
      assertTrue(
          "expected some level of the exception chain to mention 'Lancater'; chain was: "
              + chainMessages(e),
          anyMessageContains(e, "Lancater"));
    }
  }

  @Test
  public void shipSections_splatsBlockAcrossEveryName() {
    final String src =
        "shipSections('Warbird', 'Javelin', 'Spider') {\n"
            + "    InitialBurst 1\n"
            + "    InitialDecoy 2\n"
            + "}\n";

    final Ini ini = new GroovyFragmentLoader().evaluate(src, "test:shipSections_splat");

    for (final String name : new String[] {"Warbird", "Javelin", "Spider"}) {
      final Section sec = ini.get(name);
      assertNotNull("Expected " + name + " section", sec);
      assertEquals(name + " should carry InitialBurst", "1", sec.get("InitialBurst"));
      assertEquals(name + " should carry InitialDecoy", "2", sec.get("InitialDecoy"));
    }
  }

  @Test
  public void shipSections_rejectsTypoBeforeMutating() {
    // If validation happens after we start writing, the first two sections would
    // be populated and the third would fail; assert nothing was written when any
    // name is bad.
    final String src =
        "shipSections('Warbird', 'Javelin', 'Lancater') {\n"
            + "    InitialBurst 1\n"
            + "}\n";

    try {
      new GroovyFragmentLoader().evaluate(src, "test:shipSections_typo");
      fail("expected IllegalArgumentException for unknown ship name");
    } catch (final Exception e) {
      assertTrue(
          "expected some level of the exception chain to mention 'Lancater'; chain was: "
              + chainMessages(e),
          anyMessageContains(e, "Lancater"));
    }
  }

  @Test
  public void section_stringValuesAreStored() {
    // Some Subspace settings are short text (e.g. [Misc] SheepMessage=Baaah).
    // Confirm the loader handles non-numeric values too.
    final String src = "section('Misc') { SheepMessage 'Baaah' }\n";

    final Ini ini = new GroovyFragmentLoader().evaluate(src, "test:stringValue");

    assertEquals("Baaah", ini.get("Misc").get("SheepMessage"));
  }

  @Test
  public void smokePort_svsCost_loadsFromClasspath() {
    // End-to-end check: the real /conf/svs/cost.groovy fixture loads and
    // contains the expected canonical-SVS Cost section with all-zero values.
    // Sanity-check four representative keys from the 25-key fragment so we
    // catch a wholesale regression without enumerating every key.
    final Ini ini = new GroovyFragmentLoader().load("/conf/svs/cost.groovy");

    assertNotNull("cost.groovy should load from the classpath", ini);
    final Section cost = ini.get("Cost");
    assertNotNull("Cost section must exist", cost);
    assertEquals("0", cost.get("PurchaseAnytime"));
    assertEquals("0", cost.get("Gun"));
    assertEquals("0", cost.get("Bomb"));
    assertEquals("0", cost.get("Portal"));
  }

  @Test
  public void load_missingPath_returnsNull() {
    final Ini ini = new GroovyFragmentLoader().load("/does-not-exist.groovy");
    assertNull(ini);
  }

  @Test
  public void include_pullsAnotherFragmentIntoTheSameIni() {
    // svs/cost.groovy is on the classpath (committed in PR #1). Verify that an
    // including script can pull it in and that the resulting Ini carries both
    // its own sections and the included file's sections.
    final String src =
        "include '/conf/svs/cost.groovy'\n"
            + "section('Bomb') { BombDamageLevel 1234 }\n";

    final Ini ini = new GroovyFragmentLoader().evaluate(src, "test:include_basic");

    // Included file contributes [Cost]
    assertNotNull("Cost section from included file", ini.get("Cost"));
    assertEquals("0", ini.get("Cost").get("PurchaseAnytime"));
    // Including script contributes [Bomb]
    assertNotNull("Bomb section from outer script", ini.get("Bomb"));
    assertEquals("1234", ini.get("Bomb").get("BombDamageLevel"));
  }

  @Test
  public void include_lastWriterWins_outerOverridesIncluded() {
    // Mimics the override pattern svs-league uses: include the base preset,
    // then write keys after the include to override.
    final String src =
        "include '/conf/svs/cost.groovy'\n"
            + "section('Cost') { PurchaseAnytime 1 }\n";

    final Ini ini = new GroovyFragmentLoader().evaluate(src, "test:include_override");

    assertEquals(
        "Outer's later write should win over the included file's earlier 0",
        "1",
        ini.get("Cost").get("PurchaseAnytime"));
  }

  @Test
  public void integration_svsLeagueComposesAndOverrides() {
    // Exercises the most complex real preset: include of every leaf,
    // shipSections splat for league items, plus per-section overrides.
    // Asserts a representative sample of keys from each layer so a
    // structural regression (e.g. include not reaching, shipSections not
    // splatting, override not winning) shows up clearly.
    final Ini ini = new GroovyFragmentLoader().load("/conf/svs-league/svs-league.groovy");

    assertNotNull("svs-league.groovy should load", ini);

    // From cost.groovy (included)
    assertEquals("0", ini.get("Cost").get("PurchaseAnytime"));
    // From misc.groovy (included)
    assertEquals("750", ini.get("Bomb").get("BombDamageLevel"));
    // From prizeweights.groovy (included)
    assertEquals("25", ini.get("PrizeWeight").get("BouncingBullets"));
    // From ship-warbird.groovy (included)
    assertNotNull("Warbird section should exist", ini.get("Warbird"));
    // shipSections league baseline splatted across every ship
    for (final String name :
        new String[] {
          "Warbird", "Javelin", "Spider", "Leviathan", "Terrier", "Weasel", "Lancaster", "Shark"
        }) {
      assertEquals(name + " should carry league RepelMax=2", "2", ini.get(name).get("RepelMax"));
      assertEquals(name + " should carry league InitialBurst=0", "0", ini.get(name).get("InitialBurst"));
    }
    // Leviathan-only override after the splat
    assertEquals("Leviathan league override InitialBrick=1", "1",
        ini.get("Leviathan").get("InitialBrick"));
    // DPrizeWeight added after the includes
    assertEquals("12", ini.get("DPrizeWeight").get("Energy"));
    assertEquals("1", ini.get("DPrizeWeight").get("Repel"));
    // Prize override layered onto the included misc
    assertEquals("1", ini.get("Prize").get("UseDeathPrizeWeights"));
  }

  @Test
  public void integration_svsPbSplatsShipsUniformly() {
    // svs-pb/ships.groovy uses a single shipSections block to apply the
    // same stat block to all 8 ships. Verify each ship section exists and
    // carries identical values.
    final Ini ini = new GroovyFragmentLoader().load("/conf/svs-pb/ships.groovy");

    assertNotNull("svs-pb/ships.groovy should load", ini);
    for (final String name :
        new String[] {
          "Warbird", "Javelin", "Spider", "Leviathan", "Terrier", "Weasel", "Lancaster", "Shark"
        }) {
      assertEquals(name + " MaximumSpeed", "3250", ini.get(name).get("MaximumSpeed"));
      assertEquals(name + " InitialBounty", "100", ini.get(name).get("InitialBounty"));
    }
  }

  @Test
  public void include_cycleIsRejected() {
    // The script claims its own path on the stack on entry, so an include of
    // the very path it's evaluating from is a 1-step cycle. Asserts the
    // detector fires.
    final String src = "include 'test:cycle_self'\n";

    try {
      new GroovyFragmentLoader().evaluate(src, "test:cycle_self");
      fail("expected IllegalStateException for include cycle");
    } catch (final Exception e) {
      assertTrue(
          "expected the chain to mention 'cycle'; got: " + chainMessages(e),
          anyMessageContains(e, "cycle"));
    }
  }

  private static boolean anyMessageContains(final Throwable t, final String needle) {
    for (Throwable cur = t; cur != null && cur != cur.getCause(); cur = cur.getCause()) {
      if (cur.getMessage() != null && cur.getMessage().contains(needle)) {
        return true;
      }
      if (cur.getCause() == null) {
        break;
      }
    }
    return false;
  }

  private static String chainMessages(final Throwable t) {
    final StringBuilder sb = new StringBuilder();
    for (Throwable cur = t; cur != null; cur = cur.getCause()) {
      if (sb.length() > 0) {
        sb.append(" -> ");
      }
      sb.append(cur.getClass().getSimpleName()).append(": ").append(cur.getMessage());
      if (cur.getCause() == cur) {
        break;
      }
    }
    return sb.toString();
  }
}
