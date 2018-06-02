/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package infinity.api.es.ship;

/**
 * Initial speed of ship (0 = can't move)
 *
 * @author Asser Fahrenholz
 */
public class Speed {

    int speed;

    public Speed(int speed) {
        this.speed = speed;
    }

    public int getSpeed() {
        return speed;
    }

}
