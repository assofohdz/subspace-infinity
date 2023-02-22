package infinity;

import infinity.sim.util.InfinityRunTimeException;

/**
 * This enum holds both byte information and string names for the ships in the game.
 * The order of the ships are warbird, javelin, spider, leviathan, terrier, weasel, lancaster, shark.
 *
 * @author asser
 */
public enum Ships {
  WARBIRD(1, "ship_warbird", 31),
    JAVELIN(2, "ship_javelin", 27),
    SPIDER(3, "ship_spider", 23),
    LEVIATHAN(4, "ship_leviathan",19),
    TERRIER(5, "ship_terrier",15),
    WEASEL(6, "ship_weasel", 11),
    LANCASTER(7, "ship_lancaster",7),
    SHARK(8, "ship_shark",3);

    private final byte id;
    private final String name;
    private final int visualOffset;

    Ships(final int id, final String name, int visualOffset) {
        this.id = (byte) id;
        this.name = name;
        this.visualOffset = visualOffset;
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

  public int getVisualOffset() {
    return visualOffset;
  }
}