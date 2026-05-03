// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es;

import com.simsilica.es.EntityData;

public class BaseTypes {

    public static final String BASE1 = "base1";

    public static BaseType base1(final EntityData ed) {
        return BaseType.create(BASE1, ed);
    }
}
