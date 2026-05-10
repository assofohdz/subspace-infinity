// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es;

import com.simsilica.es.EntityComponent;
import com.simsilica.es.EntityData;

public class BaseType implements EntityComponent {

    private final int type;

    protected BaseType() {
        this(0);
    }

    public BaseType(final int type) {
        this.type = type;
    }

    public static BaseType create(final String typeName, final EntityData ed) {
        return new BaseType(ed.getStrings().getStringId(typeName, true));
    }

    public int getType() {
        return type;
    }

    public String getTypeName(final EntityData ed) {
        return ed.getStrings().getString(type);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[type=" + type + "]";
    }
}
