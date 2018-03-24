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
public class BombsCooldown implements EntityComponent {

    private long start;
    private long delta;

    public BombsCooldown() {
        this.start = System.nanoTime();
        this.delta = 1000000 * 10;
    }

    public BombsCooldown(long deltaMillis) {
        this.start = System.nanoTime();
        this.delta = deltaMillis * 1000000;
    }

    public double getPercent() {
        long time = System.nanoTime();
        return (double) (time - start) / delta;
    }

    @Override
    public String toString() {
        return "BombsCooldown[" + (delta / 1000000.0) + " ms]";
    }
}
