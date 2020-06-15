/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package infinity.es.ship;

/** 
 * Amount added per 'Speed' Prize
 * @author Asser Fahrenholz
 */
public class SpeedUpgrade {

    int energyUpgrade;

    public int getEnergyUpgrade() {
        return energyUpgrade;
    }

    public SpeedUpgrade(int energyUpgrade) {
        this.energyUpgrade = energyUpgrade;
    }
}
