/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package example.es;

import com.simsilica.es.EntityComponent;
import example.es.PhysicsShape;
import example.sim.CollisionFilters;
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

        Filter filter = CollisionFilters.FILTER_CATEGORY_STATIC_TOWERS;
        BodyFixture fixture = new BodyFixture(Geometry.createPolygonalCircle(settings.get("Physics", "VertexCountCircle", int.class), settings.get("Physics", "TowerSizeRadius", double.class)));
        fixture.setFilter(filter);
        //fixture.setSensor(true);

        return new PhysicsShape(fixture);
    }

    public static PhysicsShape ship(Ini settings) {
        Filter filter = CollisionFilters.FILTER_CATEGORY_DYNAMIC_PLAYERS;
        BodyFixture fixture = new BodyFixture(Geometry.createPolygonalCircle(settings.get("Physics", "VertexCountCircle", int.class), settings.get("Physics", "ShipSizeRadius", double.class)));
        fixture.setFilter(filter);

        return new PhysicsShape(fixture);
    }

    public static PhysicsShape prize(Ini settings) {
        Filter filter = CollisionFilters.FILTER_CATEGORY_DYNAMIC_MAPOBJECTS;
        BodyFixture fixture = new BodyFixture(Geometry.createPolygonalCircle(settings.get("Physics", "VertexCountCircle", int.class), settings.get("Physics", "BountySizeRadius", double.class)));
        fixture.setFilter(filter);

        return new PhysicsShape(fixture);
    }

    public static PhysicsShape bomb(Ini settings) {
        Filter filter = CollisionFilters.FILTER_CATEGORY_DYNAMIC_PROJECTILES;
        BodyFixture fixture = new BodyFixture(Geometry.createPolygonalCircle(settings.get("Physics", "VertexCountCircle", int.class), settings.get("Physics", "BombSizeRadius", double.class)));
        fixture.setFilter(filter);

        return new PhysicsShape(fixture);
    }

    public static PhysicsShape bullet(Ini settings) {
        Filter filter = CollisionFilters.FILTER_CATEGORY_DYNAMIC_PROJECTILES;
        BodyFixture fixture = new BodyFixture(Geometry.createPolygonalCircle(settings.get("Physics", "VertexCountCircle", int.class), settings.get("Physics", "BulletSizeRadius", double.class)));
        fixture.setFilter(filter);

        return new PhysicsShape(fixture);
    }

    public static PhysicsShape mapTile(Convex c) {

        Filter filter = CollisionFilters.FILTER_CATEGORY_STATIC_BODIES;
        BodyFixture fixture = new BodyFixture(c);
        fixture.setFilter(filter);

        return new PhysicsShape(fixture);
    }

    public static PhysicsShape wormhole(Ini settings) {
        Filter filter = CollisionFilters.FILTER_CATEGORY_STATIC_GRAVITY;
        BodyFixture fixture = new BodyFixture(Geometry.createPolygonalCircle(settings.get("Physics", "VertexCountCircle", int.class), settings.get("Physics", "WormholeSizeRadius", double.class)));
        fixture.setFilter(filter);

        return new PhysicsShape(fixture);
    }

    public static PhysicsShape over5(Ini settings) {
        Filter filter = CollisionFilters.FILTER_CATEGORY_STATIC_BODIES;
        BodyFixture fixture = new BodyFixture(Geometry.createPolygonalCircle(settings.get("Physics", "VertexCountCircle", int.class), settings.get("Physics", "Over5SizeRadius", double.class)));
        fixture.setFilter(filter);

        return new PhysicsShape(fixture);
    }

    public static PhysicsShape over1(Ini settings) {
        Filter filter = CollisionFilters.FILTER_CATEGORY_DYNAMIC_MAPOBJECTS;
        BodyFixture fixture = new BodyFixture(Geometry.createPolygonalCircle(settings.get("Physics", "VertexCountCircle", int.class), settings.get("Physics", "Over1SizeRadius", double.class)));
        fixture.setFilter(filter);

        return new PhysicsShape(fixture);
    }

    public static PhysicsShape over2(Ini settings) {
        Filter filter = CollisionFilters.FILTER_CATEGORY_DYNAMIC_MAPOBJECTS;
        BodyFixture fixture = new BodyFixture(Geometry.createPolygonalCircle(settings.get("Physics", "VertexCountCircle", int.class), settings.get("Physics", "Over2SizeRadius", double.class)));
        fixture.setFilter(filter);

        return new PhysicsShape(fixture);
    }
    
    public static PhysicsShape flag(Ini settings) {
        Filter filter = CollisionFilters.FILTER_CATEGORY_SENSOR_FLAGS;
        BodyFixture fixture = new BodyFixture(Geometry.createPolygonalCircle(settings.get("Physics", "VertexCountCircle", int.class), settings.get("Physics", "FlagSizeRadius", double.class)));
        fixture.setFilter(filter);
        fixture.setSensor(true);

        return new PhysicsShape(fixture);
    }
    
    public static PhysicsShape mob(Ini settings) {
        Filter filter = CollisionFilters.FILTER_CATEGORY_DYNAMIC_MOBS;
        BodyFixture fixture = new BodyFixture(Geometry.createPolygonalCircle(settings.get("Physics", "VertexCountCircle", int.class), settings.get("Physics", "MobSizeRadius", double.class)));
        fixture.setFilter(filter);
        //fixture.setSensor(true);

        return new PhysicsShape(fixture);
    }
    
    public static PhysicsShape base(Ini settings) {
        Filter filter = CollisionFilters.FILTER_CATEGORY_STATIC_BASE;
        BodyFixture fixture = new BodyFixture(Geometry.createPolygonalCircle(settings.get("Physics", "VertexCountCircle", int.class), settings.get("Physics", "BaseSizeRadius", double.class)));
        fixture.setFilter(filter);
        //fixture.setSensor(true);
        

        return new PhysicsShape(fixture);
    }
}
