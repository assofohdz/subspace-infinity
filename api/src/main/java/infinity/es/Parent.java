// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es;

import com.simsilica.es.EntityComponent;
import com.simsilica.es.EntityId;

/**
 * Component linking this entity to a parent entity.
 *
 * @author Asser
 */
public class Parent implements EntityComponent {

    private final EntityId parentEntity;

    public Parent() {
        parentEntity = new EntityId(0);
    }

    public EntityId getParentEntityId() {
        return parentEntity;
    }

    public Parent(final EntityId parentEntity) {
        this.parentEntity = parentEntity;
    }
}
