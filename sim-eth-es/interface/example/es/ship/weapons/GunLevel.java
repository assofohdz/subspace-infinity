/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package example.es.ship.weapons;

/**
 *
 * @author Asser
 */
public enum GunLevel {
    LEVEL_1(1, 9),
    LEVEL_2(2, 8),
    LEVEL_3(3, 7),
    LEVEL_4(4, 6);

    /**
     * Level value
     */
    public final int level;

    /**
     * Offset in the bm2 file
     */
    public final int viewOffset;

    private GunLevel(int level, int viewOffset) {
        this.level = level;
        this.viewOffset = viewOffset;
    }
}
