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
    LEVEL_1(1, 12, ColorRGBA.White, 500),
    LEVEL_2(2, 11, ColorRGBA.Red, 600),
    LEVEL_3(3, 10, ColorRGBA.Yellow, 700),
    LEVEL_4(4, 9, ColorRGBA.Blue, 800),
    EMP_1(1, 8, ColorRGBA.White, 300),
    EMP_2(2, 7, ColorRGBA.Red,400),
    EMP_3(3, 6, ColorRGBA.Yellow, 500),
    EMP_4(4, 5, ColorRGBA.Blue, 600),
    SUPER_1(1, 4, ColorRGBA.White, 700),
    SUPER_2(2, 3, ColorRGBA.Red,800),
    SUPER_3(3, 2, ColorRGBA.Yellow,900),
    SUPER_4(4, 1, ColorRGBA.Blue,1000),
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
