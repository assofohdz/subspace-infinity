package infinity;

import infinity.sim.util.InfinityRunTimeException;

/**
 * This enum holds both byte information and string names for the ships in the game.
 * The order of the ships are warbird, javelin, spider, leviathan, terrier, weasel, lancaster, shark.
 *
 * @author asser
 */
public enum Ships {
  WARBIRD(1, "ship_warbird"),
    JAVELIN(2, "ship_javelin"),
    SPIDER(3, "ship_spider"),
    LEVIATHAN(4, "ship_leviathan"),
    TERRIER(5, "ship_terrier"),
    WEASEL(6, "ship_weasel"),
    LANCASTER(7, "ship_lancaster"),
    SHARK(8, "ship_shark");

    private final byte id;
    private final String name;

    Ships(final int id, final String name) {
        this.id = (byte) id;
        this.name = name;
    }

    public byte getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public static Ships getShip(final byte id) {
        for (final Ships ship : values()) {
            if (ship.getId() == id) {
                return ship;
            }
        }
        throw new InfinityRunTimeException("No ship with id " + id);
    }

    public static Ships getShip(final String name) {
        for (final Ships ship : values()) {
            if (ship.getName().equals(name)) {
                return ship;
            }
        }
        throw new InfinityRunTimeException("No ship with name " + name);
    }
}