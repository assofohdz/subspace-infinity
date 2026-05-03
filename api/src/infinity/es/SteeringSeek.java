// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es;

import com.simsilica.es.EntityComponent;
import com.simsilica.es.EntityId;

/**
 * Component pointing this entity at a target to seek toward.
 *
 * @author Asser
 */
public class SteeringSeek implements EntityComponent {

    private final EntityId target;

    public SteeringSeek() {
        this(null);
    }

    public SteeringSeek(final EntityId target) {
        this.target = target;
    }

    public EntityId getTarget() {
        return target;
    }
}
