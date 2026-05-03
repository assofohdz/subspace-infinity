// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es;

import com.simsilica.es.EntityComponent;
import com.simsilica.es.EntityData;

/**
 * For attacking game objects, this is the type of attack
 *
 * @author Paul Speed
 */
public class ActionType implements EntityComponent {

    private final int type;

    protected ActionType() {
        this(0);
    }

    public ActionType(final int type) {
        this.type = type;
    }

    public static ActionType create(final String typeName, final EntityData ed) {
        return new ActionType(ed.getStrings().getStringId(typeName, true));
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
