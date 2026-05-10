// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es;

import com.simsilica.es.EntityComponent;
import com.simsilica.es.EntityId;

/**
 * Associated with entities that apply some effect to another entity.
 *
 * @author Paul Speed
 */
public class Buff implements EntityComponent {
    private final EntityId target;
    // private EntityId source;
    private final long startTime;

    public Buff() {
        this(null, 0L);
    }

    public Buff(final EntityId target, final long startTime) {
        this.target = target;
        this.startTime = startTime;
    }

    public EntityId getTarget() {
        return target;
    }

    public long getStartTime() {
        return startTime;
    }

    @Override
    public String toString() {
        return "Buff[" + target + ", at:" + startTime + "]";
    }
}
