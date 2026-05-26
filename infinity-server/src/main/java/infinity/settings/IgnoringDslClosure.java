// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.settings;

import groovy.lang.Closure;

/**
 * A DSL closure that swallows a {@code name { ... }} block without evaluating its body. Lets one
 * settings adapter tolerate another adapter's top-level block when they share a file — e.g.
 * {@code engine-bot-ai.groovy} holds both {@code synergy { }} and {@code roles { }}, and each loader
 * binds the other's name to this so its own evaluation doesn't fail on a missing method.
 */
final class IgnoringDslClosure extends Closure<Void> {

  private static final long serialVersionUID = 1L;

  IgnoringDslClosure() {
    super(null);
  }

  @SuppressWarnings("unused") // invoked via Groovy dispatch
  public Void doCall(final Object... args) {
    return null;
  }
}
