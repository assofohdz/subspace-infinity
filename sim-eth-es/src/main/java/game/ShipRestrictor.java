package game;

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
