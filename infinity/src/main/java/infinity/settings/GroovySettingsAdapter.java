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

import groovy.lang.Binding;
import java.util.List;

/**
 * DSL semantics for one kind of Groovy settings file. Implementations supply
 * the five things {@link GroovySettingsHost} needs to evaluate one file end to
 * end; the host owns the I/O, security hardening, and error handling.
 *
 * <p>One adapter per kind of file (zone, arena, ship, fragment, …). See
 * {@code CONTEXT.md} ("Settings adapter") for the role this plays in the
 * settings layer.
 *
 * @param <T> the typed result the adapter produces (e.g. {@code ZoneConfig})
 * @param <A> the mutable accumulator the DSL closures write into during
 *     evaluation (e.g. a builder). Kept on the type signature so the host can
 *     thread it from {@link #bind} to {@link #extract} without an unchecked
 *     cast.
 */
public interface GroovySettingsAdapter<T, A> {

  /**
   * Explicit imports the script may use. Empty list = no explicit imports at
   * all (Groovy's auto-imports — {@code java.lang.*}, {@code java.util.*},
   * etc. — are unaffected). Anything outside this list is rejected at
   * compile time by {@code SecureASTCustomizer}.
   */
  List<String> allowedImports();

  /**
   * Construct an empty accumulator and wire DSL names into the supplied
   * binding so script closures can populate the accumulator. Returns the
   * accumulator the host hands back to {@link #extract} after evaluation.
   *
   * <p>Called once per {@link GroovySettingsHost#load} invocation. The
   * accumulator must not be retained across calls.
   */
  A bind(Binding binding);

  /**
   * Extract the typed result from a populated accumulator. Called only on
   * successful evaluation; the host short-circuits to {@link #empty} on any
   * failure.
   */
  T extract(A accumulator);

  /**
   * Sentinel returned when the source file is missing on both filesystem and
   * classpath, or when evaluation throws. Must be non-null. Adapters should
   * return a documented {@code EMPTY} constant so callers can check for it
   * with {@code ==}.
   */
  T empty();
}
