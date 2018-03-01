package example.es.states;

import com.simsilica.es.ComponentFilter;
import com.simsilica.es.Entity;
import com.simsilica.es.EntityContainer;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntitySet;
import com.simsilica.es.Filters;
import com.simsilica.es.filter.AndFilter;
import com.simsilica.es.filter.FieldFilter;
import com.simsilica.sim.AbstractGameSystem;
import com.simsilica.sim.SimTime;
import example.es.Captain;
import example.es.Frequency;
import example.es.ShipType;
import example.es.ShipTypes;
import example.es.ViewTypes;
import game.ShipRestrictor;
import java.util.HashMap;

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
    
    @Override
    protected void initialize() {
        this.ed = getSystem(EntityData.class);
        
        this.freqs = ed.getEntities(Frequency.class, ShipType.class);
        this.captains = ed.getEntities(ShipType.class, Captain.class);
        
        teamRestrictions = new HashMap<>();
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
    
    public int getFrequency(EntityId entityId) {
        Frequency freq = ed.getComponent(entityId, Frequency.class);
        
        return freq.getFreq();
    }
    
    public void requestFreqChange(EntityId eId, int newFreq) {
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

    /**
     * Resets this team to completely empty, just as when it was instantiated
     * This does not change the ShipRestrictor, however
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
     * @param name the name of the player
     */
    public void removePlayer(EntityId p) {
        //Could perhaps be that we should set frequency to 0 instead of removing frequency
        if (p != null) {
            Frequency freq = new Frequency(0);
            ed.setComponent(p, freq);
        }
    }

    /**
     * Demotes a specified player from being a team captain
     *
     * @param EntityId the player to demote
     */
    public void removeCaptainFromTeam(EntityId p) {
        ed.removeComponent(p, Captain.class);
    }

    /**
     * Determines whether a player is a team captain or not
     *
     * @param name the name of the player to check
     * @return true if the player is a team captain, false otherwise
     */
    public boolean isCaptain(EntityId p) {
        return (captains.containsId(p));
    }

    /**
     * Makes a specified player a captain of this team
     *
     * @param p the EntityId of the player to be promoted
     */
    public void addCaptain(EntityId p) {
        ed.setComponent(p, new Captain());
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
