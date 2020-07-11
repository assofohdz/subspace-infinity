/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package infinity.es;

import com.simsilica.es.EntityComponent;
import infinity.sim.CategoryFilter;

/**
 *
 * @author AFahrenholz
 */
public class CollisionCategory implements EntityComponent{
    
    CategoryFilter filter;

    public CollisionCategory(CategoryFilter filter) {
        this.filter = filter;
    }

    public CategoryFilter getFilter() {
        return filter;
    }
    
}
