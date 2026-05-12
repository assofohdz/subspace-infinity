// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.settings;

import groovy.lang.Binding;
import java.util.List;

/** DSL contract for one kind of Groovy settings file; {@link GroovySettingsHost} owns I/O + eval. */
public interface GroovySettingsAdapter<T, A> {

  /** Fully-qualified class names — both default-imported (ergonomics) and whitelisted (security). */
  List<String> allowedImports();

  /** Constructs the accumulator + wires DSL names into the binding; one accumulator per evaluation. */
  A bind(Binding binding);

  /** Extracts the typed result from a populated accumulator. */
  T extract(A accumulator);

  /** Non-null sentinel returned on missing-file / eval-failure. */
  T empty();
}
