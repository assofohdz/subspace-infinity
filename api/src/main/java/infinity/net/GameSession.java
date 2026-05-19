/*
 * $Id$
 *
 * Copyright (c) 2018, Simsilica, LLC
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its
 *    contributors may be used to endorse or promote products derived
 *    from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
 * FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package infinity.net;

import com.jme3.network.service.rmi.Asynchronous;

import com.simsilica.es.EntityId;
import com.simsilica.mathd.Quatd;
import com.simsilica.mathd.Vec3d;

import infinity.es.input.MovementInput;
import infinity.events.MapAction;

/**
 *
 *
 * @author Paul Speed
 */
public interface GameSession {

    /**
     * Returns the durable player entity for this connection. Persists across ship death,
     * respawn, ship-change. The current ship (if any) is reachable via the
     * {@code CurrentShip} component on the player.
     */
    EntityId getPlayer();

    Vec3d getPlayerLocation();

    /**
     * For now we just direct-move the player avatar.
     */
    @Asynchronous(reliable = false)
    void setView(Quatd rotation, Vec3d location);

    /**
     *  Send movement input for this player's character.
     */
    @Asynchronous(reliable=false)
    void setMovementInput(MovementInput input);

    /**
     * For now we just direct-move the player avatar.
     */
    @Asynchronous(reliable = false)
    void move(MovementInput movementForces);

    /**
     * Perform an action, such as placing a brick, firing off burst, placing a decoy
     * etc.
     */
    @Asynchronous(reliable = true)
    void action(byte actionInput);

    /**
     * Attack using bullets, bombs, mines, gravbombs
     */
    @Asynchronous(reliable = true)
    void attack(byte attackInput);

    /**
     * Request a ship change or enter spectator mode
     */
    @Asynchronous(reliable = true)
    void avatar(byte avatarInput);

    /**
     * Toggle antiwarp, cloak, stealth etc.
     */
    @Asynchronous(reliable = true)
    void toggle(byte toggleInput);

    /**
     * Edit the map at the supplied world coordinate.
     *
     * @param mapInput action selector — see {@link MapAction}
     * @param coords   world-space coordinate the action applies to
     */
    @Asynchronous(reliable = true)
    void map(MapAction mapInput, Vec3d coords);
}
