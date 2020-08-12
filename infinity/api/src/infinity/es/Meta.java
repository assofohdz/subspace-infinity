/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package infinity.es;

import com.simsilica.es.EntityComponent;

/**
 *
 * @author asser
 */
public class Meta implements EntityComponent {

    long timeCreated;

    public Meta(long timeCreated) {
        this.timeCreated = timeCreated;
    }

    public long getTimeCreated() {
        return timeCreated;
    }

}
