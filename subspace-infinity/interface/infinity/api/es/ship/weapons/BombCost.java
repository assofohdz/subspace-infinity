/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package infinity.api.es.ship.weapons;

import com.simsilica.es.EntityComponent;

/**
 *
 * @author Asser Fahrenholz
 */
public class BombCost implements EntityComponent{

    int cost;

    public int getCost() {
        return cost;
    }

    public BombCost(int cost) {
        this.cost = cost;
    }
}
