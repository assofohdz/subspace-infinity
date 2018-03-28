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
public enum BombLevel {
    LEVEL_1(1, 12),
    LEVEL_2(2, 11),
    LEVEL_3(3, 10),
    LEVEL_4(4, 9),
    EMP_1(1, 8),
    EMP_2(2, 7),
    EMP_3(3, 6),
    EMP_4(4, 5),
    SUPER_1(1, 4),
    SUPER_2(2, 3),
    SUPER_3(3, 2),
    SUPER_4(4, 1),
    THOR(1, 0);

    /**
     * Level value
     */
    public final int level;

    /**
     * Offset in the bm2 file
     */
    public final int viewOffset;

    private BombLevel(int level, int viewOffset) {
        this.level = level;
        this.viewOffset = viewOffset;
    }
}
