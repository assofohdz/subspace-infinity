/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package example.es.ship.utilities;

import com.simsilica.es.EntityComponent;

/**
 *
 * @author Asser
 */
public class Recharge implements EntityComponent {

    double rechargePerSecond;

    public Recharge(double rechargePerSecond) {
        this.rechargePerSecond = rechargePerSecond;
    }

    public double getRechargePerSecond() {
        return rechargePerSecond;
    }
}
