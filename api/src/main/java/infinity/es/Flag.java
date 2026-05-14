// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es;

import com.simsilica.es.EntityComponent;

/**
 * Component identifying a captureable flag entity.
 *
 * @author Asser
 */
public class Flag implements EntityComponent {

    public static final int FLAG_THEIRS = 1;
    public static final int FLAG_OURS = 0;

    public Flag() {
        // no-op: Zay-ES requires a public no-arg constructor for deserialization.
    }

}
