/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package infinity.api.es.ship;

/**
 * Amount added per 'Thruster' Prize
 * @author Asser Fahrenholz
 */
public class ThrustUpgrade {

    int energyUpgrade;

    public int getEnergyUpgrade() {
        return energyUpgrade;
    }

    public ThrustUpgrade(int energyUpgrade) {
        this.energyUpgrade = energyUpgrade;
    }
}
