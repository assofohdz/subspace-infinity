/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package infinity.es.ship;

/**
 * Amount added per 'Rotation' Prize
 * 
 * @author Asser Fahrenholz
 */
public class RotationUpgrade {

    int energyUpgrade;

    public int getEnergyUpgrade() {
        return energyUpgrade;
    }

    public RotationUpgrade(int energyUpgrade) {
        this.energyUpgrade = energyUpgrade;
    }
}
