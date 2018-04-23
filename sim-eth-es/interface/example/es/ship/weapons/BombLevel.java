/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package example.es.ship.weapons;

import com.jme3.math.ColorRGBA;

/**
 *
 * @author Asser
 */
public enum BombLevel {
    BOMB_1(1, 12, ColorRGBA.Red, 500),
    BOMB_2(2, 11, ColorRGBA.Yellow, 600),
    BOMB_3(3, 10, ColorRGBA.Blue, 700),
    BOMB_4(4, 9, ColorRGBA.White, 800),
    EMP_1(1, 8, ColorRGBA.Red, 300),
    EMP_2(2, 7, ColorRGBA.Yellow,400),
    EMP_3(3, 6, ColorRGBA.Blue, 500),
    EMP_4(4, 5, ColorRGBA.White, 600),
    SUPER_1(1, 4, ColorRGBA.Red, 700),
    SUPER_2(2, 3, ColorRGBA.Yellow,800),
    SUPER_3(3, 2, ColorRGBA.Blue,900),
    SUPER_4(4, 1, ColorRGBA.White,1000),
    THOR(1, 0, ColorRGBA.Black, 1100);

    /**
     * Level value
     */
    public final int level;

    /**
     * Offset in the bm2 file
     */
    public final int viewOffset;

    /**
     * Light color
     */
    public final ColorRGBA lightColor;
    public final float lightRadius;
    
    private BombLevel(int level, int viewOffset, ColorRGBA lightColor, float lightRadius) {
        this.level = level;
        this.viewOffset = viewOffset;
        this.lightColor = lightColor;
        this.lightRadius = lightRadius;
    }
}
