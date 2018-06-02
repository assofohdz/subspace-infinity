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
package infinity.client;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.simsilica.es.Entity;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import infinity.ConnectionState;
import infinity.GameSessionState;
import infinity.api.es.Flag;
import infinity.api.es.Frequency;
import infinity.api.es.ship.ShipType;

/**
 *
 * @author Asser
 */
public class ShipFrequencyStateClient extends BaseAppState {

    private EntityData ed;
    private EntitySet ships;
    private int localPlayerFrequency = -1; //Frequency initiated to -1. All real frequencies are positive integers

    @Override
    protected void initialize(Application app) {
        this.ed = getState(ConnectionState.class).getEntityData();
        ships = ed.getEntities(ShipType.class, Frequency.class);
    }

    @Override
    protected void cleanup(Application app) {
        ships.release();
        ships = null;
    }

    @Override
    protected void onEnable() {

    }

    @Override
    protected void onDisable() {

    }

    @Override
    public void update(float tpf) {
        if (ships.applyChanges()) {
            for (Entity e : ships.getChangedEntities()) {
                Frequency freq = e.get(Frequency.class);
                if (e.getId().getId() == getState(GameSessionState.class).getShipId().getId()) {
                    localPlayerFrequency = freq.getFreq();
                }
                //TODO: Use remaining information to update roster in HUD
            }
            for (Entity e : ships.getAddedEntities()) {
                Frequency freq = e.get(Frequency.class);
                if (e.getId().getId() == getState(GameSessionState.class).getShipId().getId()) {
                    localPlayerFrequency = freq.getFreq();
                }
                //TODO: Use information to update roster in HUD
            }
            for (Entity e : ships.getRemovedEntities()) {
                //TODO: Use information to update roster in HUD
            }
        }
    }

    public int getLocalPlayerFrequency() {
        return this.localPlayerFrequency;
    }
}
