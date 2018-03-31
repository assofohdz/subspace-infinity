/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package example.es.ship.weapons;

import com.simsilica.es.EntityComponent;

/**
 *
 * @author Asser
 */
public class Thor implements EntityComponent {

    private final long cooldown;
    private final int count;

    public int getCount() {
        return count;
    }

    public Thor(long cooldown, int count) {
        this.cooldown = cooldown;
        this.count = count;
    }

    public long getCooldown() {
        return cooldown;
    }
}
