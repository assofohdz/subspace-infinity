// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.sim;

import infinity.es.input.MovementInput;

/**
 * Receives movement input on behalf of a driven entity.
 *
 * @author AFahrenholz
 */
public interface Driver {

    void applyMovementState(MovementInput movement);
}
