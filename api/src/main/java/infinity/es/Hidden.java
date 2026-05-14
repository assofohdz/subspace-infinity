// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.es;

import com.simsilica.es.EntityComponent;

/** Marker — client suppresses rendering; server-side state (collision, pickup, decay) unaffected. */
public class Hidden implements EntityComponent {

  public Hidden() {
    // no-op: Zay-ES requires a public no-arg constructor for deserialization.
  }
}
