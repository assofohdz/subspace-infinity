/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package infinity.es.ship.toggles;

/**
 * Whether ships are allowed to receive 'Cloak' 0=no 1=yes 2=yes/start-with
 *
 * @author Asser Fahrenholz
 */
public class CloakStatus {

    int status;

    public int getStatus() {
        return status;
    }

    public CloakStatus(final int status) {
        this.status = status;
    }
}
