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

import com.simsilica.es.EntityComponent;
import infinity.api.es.PhysicsShape;
import infinity.api.sim.CollisionFilters;
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
