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
public class Bombs implements EntityComponent {


    long cooldown;
    int cost;
    BombLevel level;

    public Bombs(long cooldown, int cost, BombLevel level) {
        this.cooldown = cooldown;
        this.cost = cost;
        this.level = level;
    }

    public long getCooldown() {
        return cooldown;
    }

    public int getCost() {
        return cost;
    }

    public BombLevel getLevel() {
        return level;
    }
}
