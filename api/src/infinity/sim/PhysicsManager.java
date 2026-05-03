// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.sim;

import com.simsilica.mphys.PhysicsSpace;

/**
 * Manager interface exposing the physics space to game systems.
 *
 * @author AFahrenholz
 */
public interface PhysicsManager {

    PhysicsSpace<?, ?> getPhysics();

}
