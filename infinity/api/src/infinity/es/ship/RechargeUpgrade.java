/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package infinity.es.ship;

/**
 * Amount added per 'Recharge Rate' Prize
 *
 * @author Asser Fahrenholz
 */
public class RechargeUpgrade {

    int energyUpgrade;

    public int getEnergyUpgrade() {
        return energyUpgrade;
    }

    public RechargeUpgrade(int energyUpgrade) {
        this.energyUpgrade = energyUpgrade;
    }
}
