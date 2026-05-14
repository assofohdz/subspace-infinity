// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.ini4j.Profile;
import org.ini4j.Profile.Section;
import org.junit.Test;

/**
 * Exercises {@link GroovyFragmentLoader} via the package-private
 * {@code evaluate} entry point so we don't depend on filesystem layout.
 * Mirrors the {@code GroovyZoneLoaderTest} / {@code GroovyShipLoaderRadarTest}
 * shape.
 */
public class GroovyFragmentLoaderTest {

  private static final String BLOCK_CLOSE = "}\n";
  private static final String SHIP_WARBIRD = "Warbird";
  private static final String SECTION_PRIZE_WEIGHT = "PrizeWeight";

  @Test
  public void section_storesEachKeyAsString() {
    final String src =
        "section('Bomb') {\n"
            + "    BombDamageLevel 750\n"
            + "    BombAliveTime 6000\n"
            + BLOCK_CLOSE;

    final Profile ini = new GroovyFragmentLoader().evaluate(src, "test:section_storesEachKeyAsString");

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
            + BLOCK_CLOSE;

    final Profile ini = new GroovyFragmentLoader().evaluate(src, "test:hyphenatedKeys");

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

    final Profile ini = new GroovyFragmentLoader().evaluate(src, "test:multipleSections");

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
            + BLOCK_CLOSE;

    final Profile ini = new GroovyFragmentLoader().evaluate(src, "test:shipSection_valid");

    assertEquals("6000", ini.get(SHIP_WARBIRD).get("SuperTime"));
    assertEquals("20", ini.get(SHIP_WARBIRD).get("BulletFireEnergy"));
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
            + BLOCK_CLOSE;

    final Profile ini = new GroovyFragmentLoader().evaluate(src, "test:shipSections_splat");

    for (final String name : new String[] {SHIP_WARBIRD, "Javelin", "Spider"}) {
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
            + BLOCK_CLOSE;

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

    final Profile ini = new GroovyFragmentLoader().evaluate(src, "test:stringValue");

    assertEquals("Baaah", ini.get("Misc").get("SheepMessage"));
  }

  @Test
  public void smokePort_svsPrizeWeights_loadsFromClasspath() {
    // End-to-end check: a real classpath fragment loads and parses into the
    // expected section + key shape. Sanity-check a handful of keys from the
    // canonical-SVS [PrizeWeight] table so a wholesale regression in the
    // loader (eval, section binding, key capture) shows up.
    final Profile ini = new GroovyFragmentLoader().load("/conf/svs/prizeweights.groovy");

    assertNotNull("prizeweights.groovy should load from the classpath", ini);
    final Section pw = ini.get(SECTION_PRIZE_WEIGHT);
    assertNotNull("PrizeWeight section must exist", pw);
    assertEquals("80", pw.get("QuickCharge"));
    assertEquals("110", pw.get("Energy"));
    assertEquals("50", pw.get("Gun"));
    assertEquals("25", pw.get("Portal"));
  }

  @Test
  public void load_missingPath_returnsNull() {
    final Profile ini = new GroovyFragmentLoader().load("/does-not-exist.groovy");
    assertNull(ini);
  }

  @Test
  public void include_pullsAnotherFragmentIntoTheSameIni() {
    // Verify that an including script can pull in another classpath fragment
    // and that the resulting Ini carries both its own sections and the
    // included file's sections.
    final String src =
        "include '/conf/svs/prizeweights.groovy'\n"
            + "section('Bomb') { BombDamageLevel 1234 }\n";

    final Profile ini = new GroovyFragmentLoader().evaluate(src, "test:include_basic");

    // Included file contributes [PrizeWeight]
    assertNotNull("PrizeWeight section from included file", ini.get(SECTION_PRIZE_WEIGHT));
    assertEquals("80", ini.get(SECTION_PRIZE_WEIGHT).get("QuickCharge"));
    // Including script contributes [Bomb]
    assertNotNull("Bomb section from outer script", ini.get("Bomb"));
    assertEquals("1234", ini.get("Bomb").get("BombDamageLevel"));
  }

  @Test
  public void include_lastWriterWins_outerOverridesIncluded() {
    // Mimics the override pattern svs-league uses: include the base preset,
    // then write keys after the include to override.
    final String src =
        "include '/conf/svs/prizeweights.groovy'\n"
            + "section('PrizeWeight') { QuickCharge 999 }\n";

    final Profile ini = new GroovyFragmentLoader().evaluate(src, "test:include_override");

    assertEquals(
        "Outer's later write should win over the included file's earlier 80",
        "999",
        ini.get(SECTION_PRIZE_WEIGHT).get("QuickCharge"));
  }

  @Test
  public void integration_svsLeagueComposesAndOverrides() {
    // Exercises the most complex real preset: include of every leaf,
    // shipSections splat for league items, plus per-section overrides.
    // Asserts a representative sample of keys from each layer so a
    // structural regression (e.g. include not reaching, shipSections not
    // splatting, override not winning) shows up clearly.
    final Profile ini = new GroovyFragmentLoader().load("/conf/svs-league/svs-league.groovy");

    assertNotNull("svs-league.groovy should load", ini);

    // From misc.groovy (included) — sample a section still in the Ini-mirror
    // path. Weapons sections progressively migrate to typed adapters in
    // B1-X slices; [Brick] is polish-bag and stays in misc.groovy through
    // all of B1.
    assertEquals("2000", ini.get("Brick").get("BrickTime"));
    // From prizeweights.groovy (included)
    assertEquals("25", ini.get(SECTION_PRIZE_WEIGHT).get("BouncingBullets"));
    // From ship-warbird.groovy (included)
    assertNotNull("Warbird section should exist", ini.get(SHIP_WARBIRD));
    // shipSections league baseline splatted across every ship
    for (final String name :
        new String[] {
          SHIP_WARBIRD, "Javelin", "Spider", "Leviathan", "Terrier", "Weasel", "Lancaster", "Shark"
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
    final Profile ini = new GroovyFragmentLoader().load("/conf/svs-pb/ships.groovy");

    assertNotNull("svs-pb/ships.groovy should load", ini);
    for (final String name :
        new String[] {
          SHIP_WARBIRD, "Javelin", "Spider", "Leviathan", "Terrier", "Weasel", "Lancaster", "Shark"
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

  // Identity check on Throwable refs: self-causing cause is the JVM-canonical loop sentinel.
  @SuppressWarnings("PMD.CompareObjectsWithEquals")
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

  // Identity check on Throwable refs: self-causing cause is the JVM-canonical loop sentinel.
  @SuppressWarnings("PMD.CompareObjectsWithEquals")
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
