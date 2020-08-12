/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package infinity.sim;

import infinity.es.input.MovementInput;

/**
 *
 * @author AFahrenholz
 */
public interface Driver {

    void applyMovementState(MovementInput movement);
}
