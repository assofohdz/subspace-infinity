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
public class ThorCooldown implements EntityComponent {

    private long start;
    private long delta;

    public ThorCooldown() {
        this.start = System.nanoTime();
        this.delta = 1000000 * 10;
    }

    public ThorCooldown(long deltaMillis) {
        this.start = System.nanoTime();
        this.delta = deltaMillis * 1000000;
    }

    public double getPercent() {
        long time = System.nanoTime();
        return (double) (time - start) / delta;
    }

    @Override
    public String toString() {
        return "ThorCooldown[" + (delta / 1000000.0) + " ms]";
    }
}
