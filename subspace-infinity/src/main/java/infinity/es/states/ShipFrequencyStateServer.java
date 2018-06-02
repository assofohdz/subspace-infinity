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
package infinity.es.states;

import com.jme3.network.service.HostedServiceManager;
import com.simsilica.es.ComponentFilter;
import com.simsilica.es.Entity;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntitySet;
import com.simsilica.es.filter.FieldFilter;
import com.simsilica.sim.AbstractGameSystem;
import com.simsilica.sim.SimTime;
import infinity.api.es.Captain;
import infinity.api.es.Frequency;
import infinity.api.es.ship.ShipType;
import infinity.api.es.ship.ShipTypes;
import infinity.api.es.ViewTypes;
import infinity.net.chat.server.ChatHostedService;
import infinity.api.sim.AccessLevel;
import infinity.api.sim.CommandConsumer;
import infinity.ShipRestrictor;
import java.util.HashMap;
import java.util.regex.Pattern;

/**
 *
 * @author Asser
 */
public class ShipFrequencyStateServer extends AbstractGameSystem {

    private EntityData ed;
    private EntitySet freqs;

    /**
     * The number of allowed players in each ship on this team
     */
    private HashMap<Integer, ShipRestrictor> teamRestrictions;
    private EntitySet captains;

    //Matches =214 to capture frequency 214
    private final Pattern joinTeam = Pattern.compile("\\=(\\d+)");
    private final HostedServiceManager serviceManager;

    public ShipFrequencyStateServer(HostedServiceManager serviceManager) {
        this.serviceManager = serviceManager;
    }

    @Override
    protected void initialize() {
        this.ed = getSystem(EntityData.class);

        this.freqs = ed.getEntities(Frequency.class, ShipType.class);
        this.captains = ed.getEntities(ShipType.class, Captain.class);

        teamRestrictions = new HashMap<>();

        //Register consuming methods for patterns
        this.serviceManager.getService(ChatHostedService.class).registerPatternBiConsumer(joinTeam, "The command to join a team is =<frequyency> where <frequency> is the freq you wish to join", new CommandConsumer(AccessLevel.PLAYER_LEVEL, (id, frequency) -> this.joinTeam(id, frequency)));

    }

    private void joinTeam(EntityId from, String frequency) {
        ed.setComponent(from, new Frequency(Integer.valueOf(frequency)));
    }

    @Override
    protected void terminate() {
        freqs.release();
        freqs = null;
    }

    @Override
    public void update(SimTime tpf) {
        if (freqs.applyChanges()) {
            for (Entity e : freqs.getAddedEntities()) {

            }
            for (Entity e : freqs.getChangedEntities()) {

            }
            for (Entity e : freqs.getRemovedEntities()) {

            }
        }

        if (captains.applyChanges()) {
            for (Entity e : captains.getAddedEntities()) {

            }
            for (Entity e : captains.getChangedEntities()) {

            }
            for (Entity e : captains.getRemovedEntities()) {

            }
        }
    }

    @Override
    public void start() {
    }

    @Override
    public void stop() {
    }

    public void requestShipChange(EntityId shipEntity, int shipType) {
        //TODO: Check for energy (full energy to switch ships)

        int freq = freqs.getEntity(shipEntity).get(Frequency.class).getFreq();

        ShipRestrictor restrictor = this.getRestrictor(freq);

        //Allow ship change if no restrictions on frequency, or if restrictions allow it
        if (restrictor == null || restrictor.canSwitch(shipEntity, (byte) shipType, freq)) {

            switch (shipType) {
                case 1:
                    ed.setComponent(shipEntity, ShipTypes.warbird(ed));
                    ed.setComponent(shipEntity, ViewTypes.ship_warbird(ed));
                    break;
                case 2:
                    ed.setComponent(shipEntity, ShipTypes.javelin(ed));
                    ed.setComponent(shipEntity, ViewTypes.ship_javelin(ed));
                    break;
                case 3:
                    ed.setComponent(shipEntity, ShipTypes.spider(ed));
                    ed.setComponent(shipEntity, ViewTypes.ship_spider(ed));
                    break;
                case 4:
                    ed.setComponent(shipEntity, ShipTypes.leviathan(ed));
                    ed.setComponent(shipEntity, ViewTypes.ship_levi(ed));
                    break;
                case 5:
                    ed.setComponent(shipEntity, ShipTypes.terrier(ed));
                    ed.setComponent(shipEntity, ViewTypes.ship_terrier(ed));
                    break;
                case 6:
                    ed.setComponent(shipEntity, ShipTypes.weasel(ed));
                    ed.setComponent(shipEntity, ViewTypes.ship_weasel(ed));
                    break;
                case 7:
                    ed.setComponent(shipEntity, ShipTypes.lancaster(ed));
                    ed.setComponent(shipEntity, ViewTypes.ship_lanc(ed));
                    break;
                case 8:
                    ed.setComponent(shipEntity, ShipTypes.shark(ed));
                    ed.setComponent(shipEntity, ViewTypes.ship_shark(ed));
                    break;
            }
        }
    }

    /**
     * Find the frequency of an entity
     * @param entityId the entity to check
     * @return the frequency of the entity
     */
    public int getFrequency(EntityId entityId) {
        Frequency freq = ed.getComponent(entityId, Frequency.class);

        return freq.getFreq();
    }

    /**
     * Requests a frequency change for an entity
     * @param eId the entity to change frequency for
     * @param newFreq the new freuency
     */
    public void requestFreqChange(EntityId eId, int newFreq) {
        //TODO: Check the ship restrictor in place to make sure the new frequency is allowed
        ed.setComponent(eId, new Frequency(newFreq));
    }

    /**
     * Sets the ShipRestrictor this team uses to restrict ship access. If
     * restrictor is null, the team will be set to use a Restrictor that allows
     * full access to all ships.
     *
     * @param team Frequency
     * @param restrict The new ShipRestrictor to use
     */
    public void setRestrictor(int team, ShipRestrictor restrict) {
        if (!teamRestrictions.containsKey(team)) {
            teamRestrictions.put(team, new ShipRestrictor() {
                @Override
                public boolean canSwitch(EntityId p, byte ship, int t) {
                    return true;
                }

                @Override
                public boolean canSwap(EntityId p1, EntityId p2, int t) {
                    return true;
                }

                public byte fallbackShip() {
                    return 0;
                }
            });
        } else {
            this.teamRestrictions.put(team, restrict);
        }
    }

    public ShipRestrictor getRestrictor(int team) {
        return this.teamRestrictions.get(team);
    }

    /**
     * Resets this team to completely empty, just as when it was instantiated
     * This does not change the ShipRestrictor, however
     * @param team the team to clear and reset
     */
    public void reset(int team) {
        /*
        players.clear();
        changed = true;
        plist = null;
        ships = new Player[8][0];
         */
    }

    /**
     * Removes a player from this team. The removed player will be put in team 0
     *
     * @param eId the player entity to remove
     */
    public void removePlayer(EntityId eId) {
        //Could perhaps be that we should set frequency to 0 instead of removing frequency
        if (eId != null) {
            Frequency freq = new Frequency(0);
            ed.setComponent(eId, freq);
        }
    }

    /**
     * Demotes a specified player from being a team captain
     *
     * @param eId the player to demote
     */
    public void removeCaptainFromTeam(EntityId eId) {
        ed.removeComponent(eId, Captain.class);
    }

    /**
     * Determines whether a player is a team captain or not
     *
     * @param eId the entity to check
     * @return true if the player is a team captain, false otherwise
     */
    public boolean isCaptain(EntityId eId) {
        return (captains.containsId(eId));
    }

    /**
     * Makes a specified player a captain of this team
     *
     * @param eId the EntityId of the player to be promoted
     */
    public void addCaptain(EntityId eId) {
        ed.setComponent(eId, new Captain());
    }

    /**
     * Gets the number of players on this team in a particular ship
     *
     * @param team the frequency to check
     * @param type the type of ship
     * @return the count of the ship type
     */
    public int getShipCount(int team, ShipType type) {

        ComponentFilter freqFilter = FieldFilter.create(Frequency.class, "freq", team);
        EntitySet freq = ed.getEntities(freqFilter, Frequency.class, ShipType.class);

        int count = 0;

        //Sum up the entities with the right type
        count = freq.stream().filter((e) -> (e.get(ShipType.class).getType() == type.getType())).map((_item) -> 1).reduce(count, Integer::sum);

        return count;
    }

}
