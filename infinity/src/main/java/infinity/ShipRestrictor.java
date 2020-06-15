/* 
 * Copyright (c) 2018, Asser Fahrenholz
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package infinity;

import com.simsilica.es.EntityId;

/**
    The ShipRestrictor interface defines a set of methods to be used by the
    integrated ship access control in the Team class.

    @author D1st0rt
    @version 06.12.27
*/
public interface ShipRestrictor
{

    /** Supplying this value for maximum allowed means there is no limit */
    public static final short UNRESTRICTED = -1;

    /**
        Determines whether a player can switch to a particular ship or not
        @param p the player in question
        @param ship the ship the player wishes to switch to
        @param team the player's team
        @return true if the player is allowed to switch, false otherwise
    */
    public boolean canSwitch(EntityId p, byte ship, int team);

    public boolean canSwap(EntityId p1, EntityId p2, int team);

    /**
        Gets the ship to set the player in when they are not allowed to switch
        @return a ship value from 0-7, or 8 for spectator
    */
    public byte fallbackShip();
}
