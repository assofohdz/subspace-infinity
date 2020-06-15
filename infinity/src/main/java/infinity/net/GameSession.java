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

import com.jme3.math.Vector3f;
import com.jme3.network.service.rmi.Asynchronous;

import com.simsilica.es.EntityId;
import com.simsilica.mathd.Quatd;
import com.simsilica.mathd.Vec3d;

/**
 *
 *
 * @author Paul Speed
 */
public interface GameSession {

    /**
     * Returns the ID of the player's current 'avatar' in the game.
     */
    public EntityId getAvatar();

    /**
     * For now we just direct-move the player avatar.
     */
    @Asynchronous(reliable = false)
    public void setView(Quatd rotation, Vec3d location);

    /**
     * Starts a grab+hold operation on some object. We send these async because
     * we don't have to wait for the result but we do need it to be reliably
     * sent because we won't be sending it again.
     */
    @Asynchronous(reliable = true)
    public void startHolding(EntityId object, Vec3d location);

    /**
     * Releases a previous grab+hold operation.
     */
    @Asynchronous(reliable = true)
    public void stopHolding(EntityId object);

    /**
     * For now we just direct-move the player avatar.
     */
    @Asynchronous(reliable = false)
    public void move(Vec3d movementForces);

    /**
     * RMI call to create a tile
     *
     * @param tileSet the tileset chosen
     * @param x the x-coordinate to create the tile on
     * @param y the y-coordinate to create the tile on
     */
    @Asynchronous(reliable = true)
    public void createTile(String tileSet, double x, double y);

    /**
     * RMI call to remove a tile
     *
     * @param x the x-coordinate to remove the tile from
     * @param y the y-coordinate to remove the tile from
     */
    @Asynchronous(reliable = true)
    public void removeTile(double x, double y);

    //Weapons:
    /**
     * RMI call to attack with guns
     */
    @Asynchronous(reliable = true)
    public void attackGuns();

    /**
     * RMI call to attack with bombs
     */
    @Asynchronous(reliable = true)
    public void attackBomb();

    /**
     * RMI call to attack with gravity bombs
     */
    @Asynchronous(reliable = true)
    public void attackGravityBomb();

    /**
     * RMI call to place a mine
     */
    @Asynchronous(reliable = true)
    public void placeMine();

    /**
     * RMI call to attack with burst
     */
    @Asynchronous(reliable = true)
    public void attackBurst();

    /**
     * RMI call to attack with thor
     */
    @Asynchronous(reliable = true)
    public void attackThor();

    //Actions:
    /**
     * RMI call to repel
     */
    @Asynchronous(reliable = true)
    public void repel();

    /**
     * RMI call to warp
     */
    @Asynchronous(reliable = true)
    public void warp();

    //Misc
    /**
     * RMI call to choose a ship
     *
     * @param ship the chosen ship
     */
    @Asynchronous(reliable = true)
    public void chooseShip(byte ship);
}
