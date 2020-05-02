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
package infinity.api.es;

import infinity.api.sim.CorePhysicsConstants;
import infinity.api.sim.ModuleCollisionFilters;
import org.dyn4j.collision.Filter;
import org.dyn4j.dynamics.BodyFixture;
import org.dyn4j.geometry.Convex;
import org.dyn4j.geometry.Geometry;
import org.ini4j.Ini;

/**
 *
 * @author Asser
 */
public class PhysicsShapes {

    public static PhysicsShape tower(Ini settings) {

        Filter filter = ModuleCollisionFilters.FILTER_CATEGORY_STATIC_TOWERS;
        BodyFixture fixture = new BodyFixture(Geometry.createPolygonalCircle(settings.get("Physics", "VertexCountCircle", int.class), settings.get("Physics", "TowerSizeRadius", double.class)));
        fixture.setFilter(filter);
        //fixture.setSensor(true);

        return new PhysicsShape(fixture);
    }

    public static PhysicsShape ship(Ini settings) {
        Filter filter = ModuleCollisionFilters.FILTER_CATEGORY_DYNAMIC_PLAYERS;
        BodyFixture fixture = new BodyFixture(Geometry.createPolygonalCircle(settings.get("Physics", "VertexCountCircle", int.class), settings.get("Physics", "ShipSizeRadius", double.class)));
        fixture.setFilter(filter);

        return new PhysicsShape(fixture);
    }

    public static PhysicsShape prize(Ini settings) {
        Filter filter = ModuleCollisionFilters.FILTER_CATEGORY_DYNAMIC_MAPOBJECTS;
        BodyFixture fixture = new BodyFixture(Geometry.createPolygonalCircle(settings.get("Physics", "VertexCountCircle", int.class), settings.get("Physics", "BountySizeRadius", double.class)));
        fixture.setFilter(filter);

        return new PhysicsShape(fixture);
    }

    public static PhysicsShape bomb(Ini settings) {
        Filter filter = ModuleCollisionFilters.FILTER_CATEGORY_DYNAMIC_PROJECTILES;
        BodyFixture fixture = new BodyFixture(Geometry.createPolygonalCircle(settings.get("Physics", "VertexCountCircle", int.class), settings.get("Physics", "BombSizeRadius", double.class)));
        fixture.setFilter(filter);

        return new PhysicsShape(fixture);
    }

    public static PhysicsShape bullet(Ini settings) {
        Filter filter = ModuleCollisionFilters.FILTER_CATEGORY_DYNAMIC_PROJECTILES;
        BodyFixture fixture = new BodyFixture(Geometry.createPolygonalCircle(settings.get("Physics", "VertexCountCircle", int.class), settings.get("Physics", "BulletSizeRadius", double.class)));
        fixture.setFilter(filter);

        return new PhysicsShape(fixture);
    }

    public static PhysicsShape mapTile(Convex c) {

        Filter filter = ModuleCollisionFilters.FILTER_CATEGORY_STATIC_BODIES;
        BodyFixture fixture = new BodyFixture(c);
        fixture.setFilter(filter);

        return new PhysicsShape(fixture);
    }

    public static PhysicsShape wormhole(Ini settings) {
        Filter filter = ModuleCollisionFilters.FILTER_CATEGORY_STATIC_GRAVITY;
        BodyFixture fixture = new BodyFixture(Geometry.createPolygonalCircle(settings.get("Physics", "VertexCountCircle", int.class), settings.get("Physics", "WormholeSizeRadius", double.class)));
        fixture.setFilter(filter);

        return new PhysicsShape(fixture);
    }

    public static PhysicsShape over5(Ini settings) {
        Filter filter = ModuleCollisionFilters.FILTER_CATEGORY_STATIC_BODIES;
        BodyFixture fixture = new BodyFixture(Geometry.createPolygonalCircle(settings.get("Physics", "VertexCountCircle", int.class), settings.get("Physics", "Over5SizeRadius", double.class)));
        fixture.setFilter(filter);

        return new PhysicsShape(fixture);
    }

    public static PhysicsShape over1(Ini settings) {
        Filter filter = ModuleCollisionFilters.FILTER_CATEGORY_DYNAMIC_MAPOBJECTS;
        BodyFixture fixture = new BodyFixture(Geometry.createPolygonalCircle(settings.get("Physics", "VertexCountCircle", int.class), settings.get("Physics", "Over1SizeRadius", double.class)));
        fixture.setFilter(filter);

        return new PhysicsShape(fixture);
    }

    public static PhysicsShape over2(Ini settings) {
        Filter filter = ModuleCollisionFilters.FILTER_CATEGORY_DYNAMIC_MAPOBJECTS;
        BodyFixture fixture = new BodyFixture(Geometry.createPolygonalCircle(settings.get("Physics", "VertexCountCircle", int.class), settings.get("Physics", "Over2SizeRadius", double.class)));
        fixture.setFilter(filter);

        return new PhysicsShape(fixture);
    }

    public static PhysicsShape flag(Ini settings) {
        Filter filter = ModuleCollisionFilters.FILTER_CATEGORY_SENSOR_FLAGS;
        BodyFixture fixture = new BodyFixture(Geometry.createPolygonalCircle(settings.get("Physics", "VertexCountCircle", int.class), settings.get("Physics", "FlagSizeRadius", double.class)));
        fixture.setFilter(filter);
        fixture.setSensor(true);

        return new PhysicsShape(fixture);
    }

    public static PhysicsShape mob(Ini settings) {
        Filter filter = ModuleCollisionFilters.FILTER_CATEGORY_DYNAMIC_MOBS;
        BodyFixture fixture = new BodyFixture(Geometry.createPolygonalCircle(settings.get("Physics", "VertexCountCircle", int.class), settings.get("Physics", "MobSizeRadius", double.class)));
        fixture.setFilter(filter);
        //fixture.setSensor(true);

        return new PhysicsShape(fixture);
    }

    public static PhysicsShape base(Ini settings) {
        Filter filter = ModuleCollisionFilters.FILTER_CATEGORY_STATIC_BASE;
        BodyFixture fixture = new BodyFixture(Geometry.createPolygonalCircle(settings.get("Physics", "VertexCountCircle", int.class), settings.get("Physics", "BaseSizeRadius", double.class)));
        fixture.setFilter(filter);
        //fixture.setSensor(true);

        return new PhysicsShape(fixture);
    }

    public static PhysicsShape repel(Ini settings) {
        Filter filter = ModuleCollisionFilters.FILTER_CATEGORY_SENSOR_REPEL;
        BodyFixture fixture = new BodyFixture(Geometry.createPolygonalCircle(settings.get("Physics", "VertexCountCircle", int.class), settings.get("Physics", "BaseRepelSize", double.class)));
        fixture.setFilter(filter);
        return new PhysicsShape(fixture);
    }
    
    public static PhysicsShape tower() {

        Filter filter = ModuleCollisionFilters.FILTER_CATEGORY_STATIC_TOWERS;
        //BodyFixture fixture = new BodyFixture(Geometry.createPolygonalCircle(CorePhysicsConstants.VERTEXCOUNTCIRCLE, CorePhysicsConstants.TOWERSIZERADIUS));
        BodyFixture fixture = new BodyFixture(Geometry.createCircle(CorePhysicsConstants.TOWERSIZERADIUS));
        fixture.setFilter(filter);
        //fixture.setSensor(true);

        return new PhysicsShape(fixture);
    }

    public static PhysicsShape ship() {
        Filter filter = ModuleCollisionFilters.FILTER_CATEGORY_DYNAMIC_PLAYERS;
        //BodyFixture fixture = new BodyFixture(Geometry.createPolygonalCircle(CorePhysicsConstants.VERTEXCOUNTCIRCLE, CorePhysicsConstants.SHIPSIZERADIUS));
        BodyFixture fixture = new BodyFixture(Geometry.createCircle(CorePhysicsConstants.SHIPSIZERADIUS));
        fixture.setFilter(filter);

        return new PhysicsShape(fixture);
    }

    public static PhysicsShape bounty() {
        Filter filter = ModuleCollisionFilters.FILTER_CATEGORY_DYNAMIC_MAPOBJECTS;
        //BodyFixture fixture = new BodyFixture(Geometry.createPolygonalCircle(CorePhysicsConstants.VERTEXCOUNTCIRCLE, CorePhysicsConstants.BOUNTYSIZERADIUS));
        BodyFixture fixture = new BodyFixture(Geometry.createCircle(CorePhysicsConstants.BOUNTYSIZERADIUS));
        fixture.setFilter(filter);

        return new PhysicsShape(fixture);
    }

    public static PhysicsShape bomb() {
        Filter filter = ModuleCollisionFilters.FILTER_CATEGORY_DYNAMIC_PROJECTILES;
        //BodyFixture fixture = new BodyFixture(Geometry.createPolygonalCircle(CorePhysicsConstants.VERTEXCOUNTCIRCLE, CorePhysicsConstants.BOMBSIZERADIUS));
        BodyFixture fixture = new BodyFixture(Geometry.createCircle(CorePhysicsConstants.BOMBSIZERADIUS));
        fixture.setFilter(filter);
        fixture.setRestitution(1d); //Bounciness
        fixture.setFriction(0d);

        return new PhysicsShape(fixture);
    }

    public static PhysicsShape bullet() {
        Filter filter = ModuleCollisionFilters.FILTER_CATEGORY_DYNAMIC_PROJECTILES;
        //BodyFixture fixture = new BodyFixture(Geometry.createPolygonalCircle(CorePhysicsConstants.VERTEXCOUNTCIRCLE, CorePhysicsConstants.BULLETSIZERADIUS));
        BodyFixture fixture = new BodyFixture(Geometry.createCircle(CorePhysicsConstants.BULLETSIZERADIUS));

        fixture.setFilter(filter);

        return new PhysicsShape(fixture);
    }

    public static PhysicsShape mapTile() {

        Filter filter = ModuleCollisionFilters.FILTER_CATEGORY_STATIC_BODIES;
        BodyFixture fixture = new BodyFixture(Geometry.createRectangle(1,1));
        fixture.setFilter(filter);
        //fixture.setRestitution(1d); //Bounciness
        //fixture.setFriction(0d);
        
        fixture.setUserData("mapTile");

        return new PhysicsShape(fixture);
    }

    public static PhysicsShape wormhole() {
        Filter filter = ModuleCollisionFilters.FILTER_CATEGORY_STATIC_GRAVITY;
        //BodyFixture fixture = new BodyFixture(Geometry.createPolygonalCircle(CorePhysicsConstants.VERTEXCOUNTCIRCLE, CorePhysicsConstants.WORMHOLESIZERADIUS));
        BodyFixture fixture = new BodyFixture(Geometry.createCircle(CorePhysicsConstants.WORMHOLESIZERADIUS));
        fixture.setFilter(filter);

        return new PhysicsShape(fixture);
    }

    public static PhysicsShape over5() {
        Filter filter = ModuleCollisionFilters.FILTER_CATEGORY_STATIC_BODIES;
        //BodyFixture fixture = new BodyFixture(Geometry.createPolygonalCircle(CorePhysicsConstants.VERTEXCOUNTCIRCLE, CorePhysicsConstants.OVER5SIZERADIUS));
        BodyFixture fixture = new BodyFixture(Geometry.createCircle(CorePhysicsConstants.OVER5SIZERADIUS));
        fixture.setFilter(filter);

        return new PhysicsShape(fixture);
    }

    public static PhysicsShape over1() {
        Filter filter = ModuleCollisionFilters.FILTER_CATEGORY_DYNAMIC_MAPOBJECTS;
        //BodyFixture fixture = new BodyFixture(Geometry.createPolygonalCircle(CorePhysicsConstants.VERTEXCOUNTCIRCLE, CorePhysicsConstants.OVER1SIZERADIUS));
        BodyFixture fixture = new BodyFixture(Geometry.createCircle(CorePhysicsConstants.OVER1SIZERADIUS));
        fixture.setFilter(filter);

        return new PhysicsShape(fixture);
    }

    public static PhysicsShape over2() {
        Filter filter = ModuleCollisionFilters.FILTER_CATEGORY_DYNAMIC_MAPOBJECTS;
        //BodyFixture fixture = new BodyFixture(Geometry.createPolygonalCircle(CorePhysicsConstants.VERTEXCOUNTCIRCLE, CorePhysicsConstants.OVER2SIZERADIUS));
        BodyFixture fixture = new BodyFixture(Geometry.createCircle(CorePhysicsConstants.OVER2SIZERADIUS));
        fixture.setFilter(filter);

        return new PhysicsShape(fixture);
    }

    public static PhysicsShape flag() {
        Filter filter = ModuleCollisionFilters.FILTER_CATEGORY_SENSOR_FLAGS;
        //BodyFixture fixture = new BodyFixture(Geometry.createPolygonalCircle(CorePhysicsConstants.VERTEXCOUNTCIRCLE, CorePhysicsConstants.FLAGSIZERADIUS));
        BodyFixture fixture = new BodyFixture(Geometry.createCircle(CorePhysicsConstants.FLAGSIZERADIUS));
        fixture.setFilter(filter);
        fixture.setSensor(true);

        return new PhysicsShape(fixture);
    }

    public static PhysicsShape mob() {
        Filter filter = ModuleCollisionFilters.FILTER_CATEGORY_DYNAMIC_MOBS;
        //BodyFixture fixture = new BodyFixture(Geometry.createPolygonalCircle(CorePhysicsConstants.VERTEXCOUNTCIRCLE, CorePhysicsConstants.MOBSIZERADIUS));
        BodyFixture fixture = new BodyFixture(Geometry.createCircle(CorePhysicsConstants.MOBSIZERADIUS));
        fixture.setFilter(filter);
        //fixture.setSensor(true);

        return new PhysicsShape(fixture);
    }

    public static PhysicsShape base() {
        Filter filter = ModuleCollisionFilters.FILTER_CATEGORY_STATIC_BASE;
        //BodyFixture fixture = new BodyFixture(Geometry.createPolygonalCircle(CorePhysicsConstants.VERTEXCOUNTCIRCLE, CorePhysicsConstants.BASESIZERADIUS));
        BodyFixture fixture = new BodyFixture(Geometry.createCircle(CorePhysicsConstants.BASESIZERADIUS));
        fixture.setFilter(filter);
        //fixture.setSensor(true);

        return new PhysicsShape(fixture);
    }
    
    public static PhysicsShape burst() {
        Filter filter = ModuleCollisionFilters.FILTER_CATEGORY_DYNAMIC_PROJECTILES;
        BodyFixture fixture = new BodyFixture(Geometry.createCircle(CorePhysicsConstants.BURSTSIZERADIUS));
        fixture.setFilter(filter);
        
        return new PhysicsShape(fixture);
    }

    public static PhysicsShape repel() {
        Filter filter = ModuleCollisionFilters.FILTER_CATEGORY_SENSOR_REPEL;
        BodyFixture fixture = new BodyFixture(Geometry.createCircle(CorePhysicsConstants.REPELRADIUS));
        fixture.setFilter(filter);
        return new PhysicsShape(fixture);
    }
}
