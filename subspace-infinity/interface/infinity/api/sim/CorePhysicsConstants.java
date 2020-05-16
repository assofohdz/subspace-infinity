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
package infinity.api.sim;

/**
 *
 * @author Asser
 */
public class CorePhysicsConstants {

    public static double PHYSICS_SCALE = 1;

    //Radius
    public final static double BULLETSIZERADIUS = 0.125f;
    public final static double BOMBSIZERADIUS = 0.5f;
    public final static double BOUNTYSIZERADIUS = 0.5f;
    public final static double SHIPSIZERADIUS = 1f;
    public final static double MOBSIZERADIUS = 1f;
    public final static double TOWERSIZERADIUS = 1f;
    public final static double BASESIZERADIUS = 2.5f;
    public final static double WORMHOLESIZERADIUS = 0.1;
    public final static double OVER5SIZERADIUS = 0.1;
    public final static double OVER1SIZERADIUS = 0.5;
    public final static double OVER2SIZERADIUS = 1;
    public final static double FLAGSIZERADIUS = 0.5;
    public final static double BURSTSIZERADIUS = 0.125f;

    public final static double REPELRADIUS = 0.125f;

    //Weights
    public final static double SHIPMASS = 50;
    public final static double BOMBMASS = 25;
    public final static double BULLETMASS = 5;
    public final static double MAPTILEMASS = 0; //Infinite mass
    public final static double WORMHOLEMASS = 0;
    public final static double OVER1MASS = 10;
    public final static double OVER2MASS = 40;
    public final static double OVER5MASS = 0;

    //View
    public final static double PROJECTILEOFFSET = 3;

    //Forces
    public final static float SHIPTHRUST = 10;

    //Pathfinding and polygons
    public final static int VERTEXCOUNTCIRCLE = 20;

    //Map tiles
    public final static int MAPTILEWIDTH = 1, MAPTILEHEIGHT = 1;

}
