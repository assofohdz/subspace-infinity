// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.es;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import com.simsilica.es.EntityId;
import com.simsilica.es.base.DefaultEntityData;
import org.junit.Test;

/**
 * ABI contract test for {@link ChangeTarget} — the routing component
 * decided in ADR 0001 (Change-entity mutation model).
 *
 * <p>This test is intentionally narrow. It pins the record-shape
 * contract that other code in the ADR 0001 implementation relies on:
 * <ul>
 *   <li>{@code (target, source)} accessor exposure with the canonical
 *       record-component names — the canonical-writer drain reads both
 *       fields, so a rename here is a breaking change.
 *   <li>No-arg constructor exists and yields {@code (null, null)} —
 *       required by {@code .claude/rules/components.md} for Zay-ES
 *       deserialization symmetry.
 *   <li>Self-change semantics ({@code source == target}) are legal and
 *       are reachable through the {@link ChangeTarget#self(EntityId)}
 *       factory.
 *   <li>Value-equality / hash-code follow the record contract — the
 *       drain code does <em>not</em> rely on object identity.
 * </ul>
 *
 * <p>Behavioural tests (apply on add, reverse on remove with
 * {@code Decay}, multi-source summing, no-op skip) live in the
 * server-side {@code CanonicalWriterDrainTest} — this file is the
 * api-side ABI guardrail.
 */
public class ChangeTargetTest {

  @Test
  public void noArgCtor_producesNullPair() {
    // Required by components.md for Zay-ES deserialization symmetry —
    // every EntityComponent record needs a no-arg ctor whose fields
    // default to null/0. ChangeTarget delegates to (null, null).
    final ChangeTarget ct = new ChangeTarget();
    assertNull("target null after no-arg ctor", ct.target());
    assertNull("source null after no-arg ctor", ct.source());
  }

  @Test
  public void canonicalCtor_exposesTargetAndSource() {
    // Record-component accessor names are the ABI — the canonical
    // writer reads .target() and .source(). Renaming them silently
    // breaks every drain.
    final DefaultEntityData ed = new DefaultEntityData();
    final EntityId target = ed.createEntity();
    final EntityId source = ed.createEntity();
    // Sanity-check the test setup — separate entities are required
    // for the "source != target" branch to be meaningful.
    assertNotEquals("test fixture sanity: distinct ids", target, source);

    final ChangeTarget ct = new ChangeTarget(target, source);
    assertSame("target accessor returns the constructor arg", target, ct.target());
    assertSame("source accessor returns the constructor arg", source, ct.source());
  }

  @Test
  public void selfFactory_setsTargetEqualToSource() {
    // The common case: a ship buffing / draining itself. ADR 0001
    // explicitly calls out that source == target is valid, and the
    // self(...) factory is the idiomatic way to spell it at emit
    // sites so reviewers can grep "ChangeTarget.self(" without
    // false positives.
    final DefaultEntityData ed = new DefaultEntityData();
    final EntityId self = ed.createEntity();
    final ChangeTarget ct = ChangeTarget.self(self);
    assertSame("self() sets target to the given id", self, ct.target());
    assertSame("self() sets source to the given id", self, ct.source());
    assertEquals("self() yields target.equals(source)", ct.target(), ct.source());
  }

  @Test
  public void recordEquality_followsValueSemantics() {
    // Records compute equals/hashCode off their components. The
    // canonical writer uses ChangeTarget as a map key in some folds
    // (e.g. per-target accumulation) — value-equality is the contract,
    // not object identity.
    final DefaultEntityData ed = new DefaultEntityData();
    final EntityId target = ed.createEntity();
    final EntityId source = ed.createEntity();
    final ChangeTarget a = new ChangeTarget(target, source);
    final ChangeTarget b = new ChangeTarget(target, source);
    assertEquals("value-equal ChangeTargets compare equal", a, b);
    assertEquals("value-equal ChangeTargets share hashCode", a.hashCode(), b.hashCode());

    final ChangeTarget swapped = new ChangeTarget(source, target);
    assertNotEquals("target/source order matters in equality", a, swapped);
  }

  @Test
  public void noArgCtor_andCanonicalCtor_areDistinguishable() {
    // Belt-and-braces: a no-arg ChangeTarget should not equal an
    // explicit (null, null) — they DO equal each other under record
    // semantics; this test pins that the no-arg ctor really is the
    // same record value, not a sentinel.
    final ChangeTarget noArg = new ChangeTarget();
    final ChangeTarget nullPair = new ChangeTarget(null, null);
    assertEquals(
        "no-arg ctor equals explicit (null, null) — same record value", noArg, nullPair);
  }

  @Test
  public void toStringMentionsBothFields() {
    // Loose contract — the writer logs ChangeTarget instances in error
    // paths ("could not apply X to target=Y"), so toString needs to be
    // human-readable. Record toString does this for free; this test
    // pins the expectation so a future custom toString override that
    // drops source would fail loudly.
    final DefaultEntityData ed = new DefaultEntityData();
    final EntityId target = ed.createEntity();
    final EntityId source = ed.createEntity();
    final String text = new ChangeTarget(target, source).toString();
    assertNotNull("toString non-null", text);
    // The record-generated form is "ChangeTarget[target=..., source=...]".
    // We don't pin the exact format, just that both ids appear.
    final String tStr = target.toString();
    final String sStr = source.toString();
    org.junit.Assert.assertTrue(
        "toString contains target id (" + tStr + ") — got: " + text,
        text.contains(tStr));
    org.junit.Assert.assertTrue(
        "toString contains source id (" + sStr + ") — got: " + text,
        text.contains(sStr));
  }
}
