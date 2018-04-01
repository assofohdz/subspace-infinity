/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package example.es;

import com.simsilica.es.EntityComponent;
import com.simsilica.es.EntityId;

/**
 *
 * @author Asser
 */
public class Parent implements EntityComponent {
    
    private final EntityId parentEntity;
    
    public Parent() {
        parentEntity = new EntityId(0);
    }
    
    public EntityId getParentEntity() {
        return parentEntity;
    }
    
    public Parent(EntityId parentEntity) {
        this.parentEntity = parentEntity;
    }
}
