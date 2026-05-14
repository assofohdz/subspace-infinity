// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es;

import com.simsilica.es.EntityData;

public class MobTypes {

    private MobTypes() { /* utility class */ }

    public static final String MOB1 = "mob1";

    public static MobType mob1(final EntityData ed) {
        return MobType.create(MOB1, ed);
    }
}
