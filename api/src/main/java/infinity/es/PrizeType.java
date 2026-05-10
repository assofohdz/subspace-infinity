// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es;

import com.simsilica.es.EntityComponent;
import com.simsilica.es.EntityData;

/**
 * Component identifying the prize type for a pickup entity.
 *
 * @author Asser
 */
public class PrizeType implements EntityComponent {

    private final int type;

    protected PrizeType() {
        this(0);
    }

    public PrizeType(final int type) {
        this.type = type;
    }

    public static PrizeType create(final String typeName, final EntityData ed) {
        return new PrizeType(ed.getStrings().getStringId(typeName, true));
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
