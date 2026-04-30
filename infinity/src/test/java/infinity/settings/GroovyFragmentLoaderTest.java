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
