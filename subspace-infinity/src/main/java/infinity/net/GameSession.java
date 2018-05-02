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
package infinity.net;

import com.jme3.math.Vector3f;
import com.jme3.network.service.rmi.Asynchronous;

import com.simsilica.es.EntityId;

/**
 * The client's view of the 'game'. Provides necessary access to the general
 * game interaction and possibly game or player state.
 *
 * @author Paul Speed
 */
public interface GameSession {

    /**
     * @return the ID of the ship entity.
     */
    public EntityId getShip();

    /**
     * @return the ID of the player entity.
     */
    public EntityId getPlayer();

    /**
     * Sends information to the game back end about the current movement state
     * of the player from user input. Because this state is continuous, it
     * doesn't need to be reliable.
     *
     * @param thrust the Vector containing the thrust
     */
    @Asynchronous(reliable = false)
    public void move(Vector3f thrust);

    //Weapons:
    /**
     * RMI call to attack with guns
     */
    public void attackGuns();

    /**
     * RMI call to attack with bombs
     */
    public void attackBomb();

    /**
     * RMI call to attack with gravity bombs
     */
    public void attackGravityBomb();

    /**
     * RMI call to place a mine
     */
    public void placeMine();

    /**
     * RMI call to attack with burst
     */
    public void attackBurst();

    /**
     * RMI call to attack with thor
     */
    public void attackThor();

    //Actions:
    /**
     * RMI call to repel
     */
    public void repel();

    /**
     * RMI call to warp
     */
    public void warp();

    //Misc
    /**
     * RMI call to choose a ship
     *
     * @param ship the chosen ship
     */
    public void chooseShip(byte ship);

    /**
     * RMI call to place a tower
     *
     * @param x the x-coordinate to place the tower
     * @param y the y-coordinate to place the tower
     */
    public void tower(double x, double y);

    //Map
    /**
     * RMI call to edit the map
     *
     * @param tileSet the tileset chosen
     * @param x the x-coordinate to edit the map
     * @param y the y-coordinate to edit the map
     */
    public void editMap(String tileSet, double x, double y);

    /**
     * RMI call to create a tile
     *
     * @param tileSet the tileset chosen
     * @param x the x-coordinate to create the tile on
     * @param y the y-coordinate to create the tile on
     */
    public void createTile(String tileSet, double x, double y);

    /**
     * RMI call to remove a tile
     *
     * @param x the x-coordinate to remove the tile from
     * @param y the y-coordinate to remove the tile from
     */
    public void removeTile(double x, double y);
}
