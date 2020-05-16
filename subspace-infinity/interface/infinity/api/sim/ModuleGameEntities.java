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

import com.jme3.math.ColorRGBA;
import infinity.api.es.Spawner;
import infinity.api.es.WeaponTypes;
import infinity.api.es.HealthChange;
import com.simsilica.es.common.Decay;
import infinity.api.es.PhysicsForce;
import infinity.api.es.ship.Energy;
import infinity.api.es.PhysicsShapes;
import infinity.api.es.Buff;
import infinity.api.es.SphereShape;
import infinity.api.es.ViewTypes;
import infinity.api.es.ship.ShipTypes;
import infinity.api.es.TowerTypes;
import infinity.api.es.Position;
import infinity.api.es.Frequency;
import infinity.api.es.PhysicsMassTypes;
import infinity.api.es.WarpTouch;
import infinity.api.es.Bounty;
import infinity.api.es.Steerable;
import infinity.api.es.Flag;
import infinity.api.es.TileType;
import infinity.api.es.Gold;
import infinity.api.es.MobTypes;
import infinity.api.es.Delay;
import infinity.api.es.Damage;
import infinity.api.es.BaseTypes;
import infinity.api.es.GravityWell;
import infinity.api.es.PhysicsVelocity;
import infinity.api.es.SteeringSeekable;
import com.simsilica.mathd.*;
import com.simsilica.es.*;
import infinity.api.es.ActionTypes;
import infinity.api.es.AudioType;
import infinity.api.es.AudioTypes;
import infinity.api.es.Meta;
import infinity.api.es.Parent;
import infinity.api.es.PointLightComponent;

import infinity.api.es.TileTypes;
import infinity.api.es.ship.EnergyMax;
import infinity.api.es.ship.Recharge;
import infinity.api.es.ship.actions.Burst;
import infinity.api.es.ship.actions.BurstMax;
import infinity.api.es.ship.actions.Repel;
import infinity.api.es.ship.actions.RepelMax;
import infinity.api.es.ship.actions.Thor;
import infinity.api.es.ship.actions.ThorMax;
import infinity.api.es.ship.weapons.BombLevelEnum;
import infinity.api.es.ship.weapons.Bomb;
import infinity.api.es.ship.weapons.BombCost;
import infinity.api.es.ship.weapons.BombFireDelay;
import infinity.api.es.ship.weapons.GravityBomb;
import infinity.api.es.ship.weapons.GravityBombCost;
import infinity.api.es.ship.weapons.GravityBombFireDelay;
import infinity.api.es.ship.weapons.GunLevelEnum;
import infinity.api.es.ship.weapons.Gun;
import infinity.api.es.ship.weapons.GunCost;
import infinity.api.es.ship.weapons.GunFireDelay;
import infinity.api.es.ship.weapons.Mine;
import infinity.api.es.ship.weapons.MineCost;
import infinity.api.es.ship.weapons.MineFireDelay;
import infinity.api.es.ship.weapons.MineMax;
import infinity.api.es.subspace.ArenaId;
import infinity.api.es.subspace.PrizeType;
import java.util.HashSet;
import java.util.concurrent.TimeUnit;
import org.dyn4j.dynamics.Force;
import org.dyn4j.geometry.Convex;
import org.dyn4j.geometry.Vector2;
import org.ini4j.Ini;

/**
 * Utility methods for creating the common game entities used by the simulation.
 * In cases where a game entity may have multiple specific componnets or
 * dependencies used to create it, it can be more convenient to have a
 * centralized factory method. Especially if those objects are widely used. For
 * entities with only a few components or that are created by one system and
 * only consumed by one other, then this is not necessarily true.
 *
 */
public class ModuleGameEntities {

    //TODO: All constants should come through the parameters - for now, they come from the constants
    //TODO: All parameters should be dumb types and should be the basis of the complex types used in the backend
    public static EntityId createShip(EntityId parent, EntityData ed, Ini settings, long createdTime) {
        EntityId result = ed.createEntity();
        Name name = ed.getComponent(parent, Name.class);
        ed.setComponent(result, name);

        ed.setComponents(result,
                ViewTypes.ship_warbird(ed),
                ShipTypes.warbird(ed),
                PhysicsMassTypes.normal(ed),
                PhysicsShapes.ship(settings));

        ed.setComponent(result, new Frequency(1));
        ed.setComponent(result, new Gold(0));
        ed.setComponent(result, new Energy(settings.get("Warbird", "StartingHealth", int.class)));
        //ed.setComponent(result, new HitPoints(GameConstants.SHIPHEALTH));

        ed.setComponent(result, new Meta(createdTime));
        return result;
    }

    public static EntityId createGravSphere(Vec3d pos, double radius, EntityData ed, Ini settings, long createdTime) {
        EntityId result = ed.createEntity();
        ed.setComponents(result, ViewTypes.gravSphereType(ed),
                new Position(pos, new Quatd().fromAngles(-Math.PI * 0.5, 0, 0), 0.0),
                new SphereShape(radius, new Vec3d()));

        ed.setComponent(result, new Meta(createdTime));
        return result;
    }

    public static EntityId createBounty(Vec3d pos, EntityData ed, Ini settings, long createdTime) {
        EntityId result = ed.createEntity();

        ed.setComponents(result, ViewTypes.prize(ed),
                new Position(pos, new Quatd(), 0f),
                //new Bounty(GameConstants.BOUNTYVALUE),
                new Bounty(settings.get("Bounties", "DefaultValue", int.class)),
                PhysicsShapes.prize(settings),
                new SphereShape(settings.get("Bounties", "ViewSize", int.class)),
                //new SphereShape(ViewConstants.BOUNTYSIZE, new Vec3d()),
                new Decay(createdTime, createdTime + TimeUnit.NANOSECONDS.convert(settings.get("Bounties", "Decay", int.class), TimeUnit.MILLISECONDS)));
        //new Decay(GameConstants.BOUNTYDECAY));

        ed.setComponent(result, new Meta(createdTime));
        return result;
    }

    public static EntityId createBountySpawner(String arenaId, Vec3d pos, double radius, EntityData ed, Ini settings, long createdTime) {
        EntityId result = ed.createEntity();
        ed.setComponents(result,
                //Possible to add model if we want the players to be able to see the spawner
                new Position(pos, new Quatd(), 0f),
                new SphereShape(radius),
                //new Spawner(GameConstants.BOUNTYMAXCOUNT, Spawner.SpawnType.Bounties));
                PhysicsShapes.prize(settings),
                new ArenaId(arenaId),
                new Spawner(settings.get("BountySpawners", "MaxCount", int.class), Spawner.SpawnType.Prizes));
        ed.setComponent(result, new Meta(createdTime));
        return result;
    }

    public static EntityId createBomb(Vec3d location, Quatd quatd, double rotation, Vector2 linearVelocity,
            long decayMillis, EntityData ed, Ini settings, BombLevelEnum level, long createdTime) {
        EntityId lastBomb = ed.createEntity();

        ed.setComponents(lastBomb, ViewTypes.bomb(ed, level),
                new Position(location, quatd, rotation),
                new PhysicsVelocity(new Vector2(linearVelocity.x, linearVelocity.y)),
                new Decay(createdTime, createdTime + TimeUnit.NANOSECONDS.convert(decayMillis, TimeUnit.MILLISECONDS)),
                WeaponTypes.bomb(ed),
                PhysicsMassTypes.normal_bullet(ed),
                PhysicsShapes.bomb(settings));

        ed.setComponent(lastBomb, new Meta(createdTime));
        return lastBomb;
    }

    public static EntityId createDelayedBomb(Vec3d location, Quatd quatd, double rotation, Vector2 linearVelocity,
            long decayMillis, long scheduledMillis, HashSet<EntityComponent> delayedComponents, EntityData ed, Ini settings,
            BombLevelEnum level, long createdTime) {

        EntityId lastDelayedBomb = ModuleGameEntities.createBomb(location, quatd, rotation, linearVelocity,
                decayMillis, ed, settings, level, createdTime);

        ed.setComponents(lastDelayedBomb, new Delay(scheduledMillis, delayedComponents, Delay.SET));
        ed.setComponents(lastDelayedBomb, WeaponTypes.gravityBomb(ed));

        return lastDelayedBomb;
    }

    public static EntityId createBullet(Vec3d location, Quatd quatd, double rotation, Vector2 linearVelocity,
            long decayMillis, EntityData ed, Ini settings, GunLevelEnum level, long createdTime) {
        EntityId lastBomb = ed.createEntity();

        ed.setComponents(lastBomb, ViewTypes.bullet(ed, level),
                new Position(location, quatd, rotation),
                new PhysicsVelocity(new Vector2(linearVelocity.x, linearVelocity.y)),
                new Decay(createdTime, createdTime + TimeUnit.NANOSECONDS.convert(decayMillis, TimeUnit.MILLISECONDS)),
                PhysicsMassTypes.normal_bullet(ed),
                WeaponTypes.bullet(ed),
                PhysicsShapes.bullet(settings));
        ed.setComponent(lastBomb, new Meta(createdTime));

        return lastBomb;
    }

    public static EntityId createArena(EntityData ed, String arenaId, Vec3d pos, long createdTime) { //TODO: Should have Position as a parameter in case we want to create more than one arena
        EntityId lastArena = ed.createEntity();

        ed.setComponents(lastArena, ViewTypes.arena(ed),
                new Position(pos, new Quatd(), 0f),
                new ArenaId(arenaId)
        );
        ed.setComponent(lastArena, new Meta(createdTime));

        return lastArena;
    }

    /*
    public static EntityId createMapTile(String tileSet, short tileIndex, Vec3d location, Convex c, double invMass,
            String tileType, EntityData ed, Ini settings, long createdTime) {
        EntityId lastTileInfo = ed.createEntity();

        ed.setComponents(lastTileInfo,
                TileType.create(tileType, tileSet, tileIndex, ed),
                ViewTypes.mapTile(ed),
                new Position(location, new Quatd(), 0f),
                PhysicsMassTypes.infinite(ed),
                PhysicsShapes.mapTile(c));
        ed.setComponent(lastTileInfo, new Meta(createdTime));

        return lastTileInfo;
    }
*/
    //Explosion is for now only visual, so only object type and position
    public static EntityId createExplosion2(Vec3d location, Quatd quat, EntityData ed, Ini settings, long createdTime) {
        EntityId lastExplosion = ed.createEntity();

        ed.setComponents(lastExplosion,
                ViewTypes.explosion2(ed),
                new Position(location, quat, 0f),
                //new Decay(ViewConstants.EXPLOSION2DECAY));
                new Decay(createdTime, createdTime
                        + TimeUnit.NANOSECONDS.convert(settings.get("Explosions", "Type2Decay", int.class), TimeUnit.MILLISECONDS)));
        ed.setComponent(lastExplosion, new Meta(createdTime));

        return lastExplosion;
    }

    public static EntityId createWormhole(Vec3d location, double radius, double targetAreaRadius, double force,
            String gravityType, Vec3d warpTargetLocation, EntityData ed, Ini settings, long createdTime) {
        EntityId lastWormhole = ed.createEntity();

        ed.setComponents(lastWormhole,
                ViewTypes.wormhole(ed),
                new Position(location, new Quatd(), 0f),
                PhysicsMassTypes.infinite(ed),
                new GravityWell(radius, force, gravityType),
                PhysicsShapes.wormhole(settings),
                new WarpTouch(warpTargetLocation));
        ed.setComponent(lastWormhole, new Meta(createdTime));

        return lastWormhole;
    }

    /*
    public static EntityId createAttack(EntityId owner, String attackType, EntityData ed, Ini settings, long createdTime) {
        EntityId lastAttack = ed.createEntity();
        ed.setComponents(lastAttack,
                new Attack(owner),
                WeaponType.create(attackType, ed),
                new Damage(-20));

        return lastAttack;
    }
     */
    public static EntityId createForce(EntityId owner, Force force, Vector2 forceWorldCoords, EntityData ed,
            Ini settings, long createdTime) {
        EntityId lastForce = ed.createEntity();
        ed.setComponents(lastForce,
                new PhysicsForce(owner, force, forceWorldCoords));
        ed.setComponent(lastForce, new Meta(createdTime));

        return lastForce;
    }

    public static EntityId createOver5(Vec3d location, double radius, double force, String gravityType, EntityData ed,
            Ini settings, long createdTime) {
        EntityId lastOver5 = ed.createEntity();

        ed.setComponents(lastOver5,
                ViewTypes.over5(ed),
                new Position(location, new Quatd(), 0f),
                PhysicsMassTypes.infinite(ed),
                new GravityWell(radius, force, gravityType),
                PhysicsShapes.over5(settings));
        ed.setComponent(lastOver5, new Meta(createdTime));

        return lastOver5;
    }

    /**
     * Small asteroid with animation
     *
     * @param location the Vec3d position of the asteroid
     * @param ed the entitydata set to create the entity in
     * @param settings the settings to load this small asteroid with
     * @return the entityid of the created entity
     */
    public static EntityId createOver1(Vec3d location, EntityData ed, Ini settings, long createdTime) {
        EntityId lastOver1 = ed.createEntity();

        ed.setComponents(lastOver1,
                ViewTypes.over1(ed),
                new Position(location, new Quatd(), 0f),
                PhysicsMassTypes.normal(ed),
                PhysicsShapes.over1(settings));
        ed.setComponent(lastOver1, new Meta(createdTime));

        return lastOver1;
    }

    /**
     * Medium asteroid with animation
     *
     * @param location the Vec3d position of the asteroid
     * @param ed the entitydata set to create the entity in
     * @param settings the settings to load this medium asteroid with
     * @return the entityid of the created entity
     */
    public static EntityId createOver2(Vec3d location, EntityData ed, Ini settings, long createdTime) {
        EntityId lastOver2 = ed.createEntity();

        ed.setComponents(lastOver2,
                ViewTypes.over2(ed),
                new Position(location, new Quatd(), 0f),
                PhysicsMassTypes.normal(ed),
                PhysicsShapes.over2(settings));
        ed.setComponent(lastOver2, new Meta(createdTime));

        return lastOver2;
    }

    public static EntityId createWarpEffect(Vec3d location, EntityData ed, Ini settings, long createdTime) {
        EntityId lastWarpTo = ed.createEntity();

        ed.setComponents(lastWarpTo,
                ViewTypes.warp(ed),
                new Position(location, new Quatd(), 0f),
                //new Decay(ViewConstants.WARPDECAY));
                new Decay(createdTime, createdTime + TimeUnit.NANOSECONDS.convert(settings.get("Warp", "Decay", int.class), TimeUnit.MILLISECONDS)));
        ed.setComponent(lastWarpTo, new Meta(createdTime));

        return lastWarpTo;
    }

    public static EntityId createCaptureTheFlag(Vec3d location, EntityData ed, Ini settings, long createdTime) {
        EntityId lastFlag = ed.createEntity();

        ed.setComponents(lastFlag,
                ViewTypes.flag_theirs(ed),
                new Position(location, new Quatd(), 0f),
                PhysicsShapes.flag(settings),
                PhysicsMassTypes.infinite(ed),
                new Flag(),
                new Frequency(0));
        ed.setComponent(lastFlag, new Meta(createdTime));

        return lastFlag;
    }

    public static EntityId createMob(Vec3d location, EntityData ed, Ini settings, long createdTime) {
        EntityId lastMob = ed.createEntity();

        ed.setComponents(lastMob,
                ViewTypes.mob(ed),
                MobTypes.mob1(ed),
                //new Drivable(), //old
                new Steerable(),
                new Position(location, new Quatd(), 0f),
                PhysicsShapes.mob(settings),
                PhysicsMassTypes.normal(ed),
                //new HitPoints(GameConstants.MOBHEALTH));
                new Energy(settings.get("Enemies", "Health", int.class)));
        ed.setComponent(lastMob, new Meta(createdTime));

        return lastMob;
    }

    public static EntityId createTower(Vec3d location, EntityData ed, Ini settings, long createdTime) { // add tower-type ?
        EntityId lastTower = ed.createEntity();
        double rand = Math.random() * Math.PI;

        ed.setComponents(lastTower,
                ViewTypes.tower(ed), // this
                TowerTypes.tower1(ed, lastTower),
                //new Position(location, new Quatd(0,0,Math.sin(rand),Math.cos(rand)), 0f),
                new Position(location, new Quatd(0, 0, -0.5, 0.5), 0f),
                PhysicsShapes.tower(settings), // and this moved to type creation, for modularity ?
                PhysicsMassTypes.infinite(ed),
                new Damage(20)); //The amount of damage the tower does
        ed.setComponent(lastTower, new Meta(createdTime));

        return lastTower;
    }

    public static EntityId createBase(Vec3d location, EntityData ed, Ini settings, long createdTime) {
        EntityId lastBase = ed.createEntity();

        ed.setComponents(lastBase,
                ViewTypes.base(ed),
                BaseTypes.base1(ed),
                new SteeringSeekable(),
                new Position(location, new Quatd(), 0f),
                PhysicsShapes.base(settings),
                PhysicsMassTypes.infinite(ed),
                //new HitPoints(GameConstants.BASEHEALTH));
                new Energy(settings.get("Base", "Health", int.class)));
        ed.setComponent(lastBase, new Meta(createdTime));

        return lastBase;
    }

    public static EntityId createHealthBuff(int healthChange, EntityId target, EntityData ed, Ini settings, long createdTime) {

        EntityId lastHealthBuff = ed.createEntity();

        ed.setComponents(lastHealthBuff,
                new HealthChange(healthChange), //apply the damage
                new Buff(target, 0)); //apply right away
        ed.setComponent(lastHealthBuff, new Meta(createdTime));

        return lastHealthBuff;
    }

    public static EntityId createLight(Vec3d vec3d, EntityData ed, Ini settings, long createdTime) {

        EntityId lastLight = ed.createEntity();

        ed.setComponents(lastLight,
                new Position(vec3d, new Quatd(), 0));
        ed.setComponent(lastLight, new Meta(createdTime));

        return lastLight;
    }

    //TODO: All constants should come through the parameters - for now, they come from the constants
    //TODO: All parameters should be dumb types and should be the basis of the complex types used in the backend
    public static EntityId createShip(EntityId parent, EntityData ed, long createdTime) {
        EntityId result = ed.createEntity();
        Name name = ed.getComponent(parent, Name.class);
        ed.setComponent(result, name);

        ed.setComponents(result,
                ViewTypes.ship_warbird(ed),
                ShipTypes.warbird(ed),
                PhysicsMassTypes.normal(ed),
                PhysicsShapes.ship());

        ed.setComponent(result, new Frequency(1));
        ed.setComponent(result, new Gold(0));

        ed.setComponent(result, new Energy(CoreGameConstants.SHIPHEALTH));
        ed.setComponent(result, new EnergyMax(CoreGameConstants.SHIPHEALTH * 2));
        ed.setComponent(result, new Recharge(100));

        //Add bombs:
        ed.setComponent(result, new Bomb(BombLevelEnum.BOMB_1));
        ed.setComponent(result, new BombCost(2));
        ed.setComponent(result, new BombFireDelay(500));

        //Add burst:
        ed.setComponent(result, new Burst(5));
        ed.setComponent(result, new BurstMax(5));

        //Add guns:
        ed.setComponent(result, new Gun(GunLevelEnum.LEVEL_1));
        ed.setComponent(result, new GunCost(10));
        ed.setComponent(result, new GunFireDelay(250));

        //Add gravity bombs
        ed.setComponent(result, new GravityBomb(BombLevelEnum.BOMB_1));
        ed.setComponent(result, new GravityBombCost(10));
        ed.setComponent(result, new GravityBombFireDelay(1000));

        //Add mines
        ed.setComponent(result, new Mine(BombLevelEnum.BOMB_1));
        ed.setComponent(result, new MineCost(50));
        ed.setComponent(result, new MineFireDelay(500));
        ed.setComponent(result, new MineMax(4));

        //Add thors
        ed.setComponent(result, new Thor(2));
        ed.setComponent(result, new ThorMax(2));

        //Add repels
        ed.setComponent(result, new Repel(10));
        ed.setComponent(result, new RepelMax(20));

        ed.setComponent(result, new PointLightComponent(ColorRGBA.White, CoreViewConstants.SHIPLIGHTRADIUS));

        ed.setComponent(result, new Meta(createdTime));
        return result;
    }

    public static EntityId createGravSphere(Vec3d pos, double radius, EntityData ed, long createdTime) {
        EntityId result = ed.createEntity();
        ed.setComponents(result, ViewTypes.gravSphereType(ed),
                new Position(pos, new Quatd().fromAngles(-Math.PI * 0.5, 0, 0), 0.0),
                new SphereShape(radius, new Vec3d()));
        ed.setComponent(result, new Meta(createdTime));
        return result;
    }

    public static EntityId createPrize(Vec3d pos, String prizeType, EntityData ed, long createdTime) {
        EntityId result = ed.createEntity();

        ed.setComponents(result, ViewTypes.prize(ed),
                new Position(pos, new Quatd(), 0f),
                new Bounty(CoreGameConstants.BOUNTYVALUE),
                PhysicsShapes.bounty(),
                PhysicsMassTypes.normal(ed),
                PrizeType.create(prizeType, ed),
                new SphereShape(CoreViewConstants.PRIZESIZE, new Vec3d()),
                new Decay(createdTime, createdTime + TimeUnit.NANOSECONDS.convert(CoreGameConstants.PRIZEDECAY, TimeUnit.MILLISECONDS)));

        ed.setComponent(result, new Meta(createdTime));
        return result;
    }

    public static void createBulletSound(EntityId parent, Vec3d pos, EntityData ed, GunLevelEnum level, long createdTime) {
        EntityId result = ed.createEntity();

        ed.setComponents(result, AudioTypes.fire_bullet(ed, level),
                new Decay(createdTime, createdTime + TimeUnit.NANOSECONDS.convert(3000, TimeUnit.MILLISECONDS)), //Three seconds to play the sound
                new Position(pos, new Quatd(), 0),
                new Parent(parent));

        ed.setComponent(result, new Meta(createdTime));

    }

    public static void createBombSound(EntityId parent, Vec3d pos, EntityData ed, BombLevelEnum level, long createdTime) {
        EntityId result = ed.createEntity();

        ed.setComponents(result, AudioTypes.fire_bomb(ed, level),
                new Decay(createdTime, createdTime + TimeUnit.NANOSECONDS.convert(3000, TimeUnit.MILLISECONDS)), //Three seconds to play the sound
                new Position(pos, new Quatd(), 0),
                new Parent(parent));
        ed.setComponent(result, new Meta(createdTime));
    }

    public static void createExplosionSound(EntityId parent, Vec3d pos, EntityData ed, long createdTime) {
        EntityId result = ed.createEntity();

        ed.setComponents(result, AudioTypes.explosion2(ed),
                new Decay(createdTime, createdTime + TimeUnit.NANOSECONDS.convert(3000, TimeUnit.MILLISECONDS)), //Three seconds to play the sound
                new Position(pos, new Quatd(), 0),
                new Parent(parent));
        ed.setComponent(result, new Meta(createdTime));
    }

    public static EntityId createSound(EntityId parent, Vec3d pos, String audioType, EntityData ed, long createdTime) {
        EntityId result = ed.createEntity();

        ed.setComponents(result, AudioType.create(audioType, ed),
                new Decay(createdTime, createdTime + TimeUnit.NANOSECONDS.convert(3000, TimeUnit.MILLISECONDS)), //Three seconds to play the sound
                new Position(pos, new Quatd(), 0),
                new Parent(parent));

        ed.setComponent(result, new Meta(createdTime));
        return result;
    }

    public static EntityId createPrizeSpawner(Vec3d pos, double radius, EntityData ed, long createdTime) {
        EntityId result = ed.createEntity();
        ed.setComponents(result,
                //Possible to add model if we want the players to be able to see the spawner
                new Position(pos, new Quatd(), 0f),
                new Spawner(CoreGameConstants.PRIZEMAXCOUNT, Spawner.SpawnType.Prizes));
        ed.setComponent(result, new Meta(createdTime));
        return result;
    }

    public static EntityId createBomb(EntityId owner, Vec3d location, Quatd quatd, double rotation, Vector2 linearVelocity,
            long decayMillis, EntityData ed, BombLevelEnum level, long createdTime) {
        EntityId lastBomb = ed.createEntity();

        ed.setComponents(lastBomb, ViewTypes.bomb(ed, level),
                new Position(location, quatd, rotation),
                new PhysicsVelocity(new Vector2(linearVelocity.x, linearVelocity.y)),
                new Decay(createdTime, createdTime + TimeUnit.NANOSECONDS.convert(decayMillis, TimeUnit.MILLISECONDS)),
                WeaponTypes.bomb(ed),
                PhysicsMassTypes.normal_bullet(ed),
                PhysicsShapes.bomb(),
                new Parent(owner),
                new PointLightComponent(level.lightColor, level.lightRadius));

        ed.setComponent(lastBomb, new Meta(createdTime));
        return lastBomb;
    }

    public static EntityId createBurst(EntityId owner, Vec3d location, Quatd quatd, double rotation, Vector2 linearVelocity,
            long decayMillis, EntityData ed, long createdTime) {
        EntityId lastBomb = ed.createEntity();

        ed.setComponents(lastBomb, ViewTypes.burst(ed),
                new Position(location, quatd, rotation),
                new PhysicsVelocity(new Vector2(linearVelocity.x, linearVelocity.y)),
                new Decay(createdTime, createdTime + TimeUnit.NANOSECONDS.convert(decayMillis, TimeUnit.MILLISECONDS)),
                WeaponTypes.burst(ed),
                PhysicsMassTypes.normal_bullet(ed),
                PhysicsShapes.burst(),
                new Parent(owner)
        //new PointLightComponent(level.lightColor, level.lightRadius));
        );
        ed.setComponent(lastBomb, new Meta(createdTime));
        return lastBomb;
    }

    public static void createBurstSound(EntityId owner, Vec3d location, EntityData ed, long createdTime) {
        EntityId result = ed.createEntity();

        ed.setComponents(result, AudioTypes.fire_burst(ed),
                new Decay(createdTime, createdTime + TimeUnit.NANOSECONDS.convert(3000, TimeUnit.MILLISECONDS)) //Three seconds to play the sound
        );
        ed.setComponent(result, new Meta(createdTime));
    }

    public static EntityId createDelayedBomb(EntityId owner, Vec3d location, Quatd quatd, double rotation,
            Vector2 linearVelocity, long decayMillis, long scheduledMillis, HashSet<EntityComponent> delayedComponents,
            EntityData ed, BombLevelEnum level, long createdTime) {

        EntityId lastDelayedBomb = ModuleGameEntities.createBomb(owner, location, quatd, rotation, linearVelocity, decayMillis, ed, level, createdTime);

        ed.setComponents(lastDelayedBomb, new Delay(scheduledMillis, delayedComponents, Delay.SET));
        ed.setComponents(lastDelayedBomb, WeaponTypes.gravityBomb(ed));

        ed.setComponent(lastDelayedBomb, new Meta(createdTime));
        return lastDelayedBomb;
    }

    public static EntityId createBullet(EntityId owner, Vec3d location, Quatd quatd, double rotation, Vector2 linearVelocity, long decayMillis, EntityData ed, GunLevelEnum level, long createdTime) {
        EntityId lastBomb = ed.createEntity();

        ed.setComponents(lastBomb, ViewTypes.bullet(ed, level),
                new Position(location, quatd, rotation),
                new PhysicsVelocity(new Vector2(linearVelocity.x, linearVelocity.y)),
                new Decay(createdTime, createdTime + TimeUnit.NANOSECONDS.convert(decayMillis, TimeUnit.MILLISECONDS)),
                PhysicsMassTypes.normal_bullet(ed),
                WeaponTypes.bullet(ed),
                new Parent(owner),
                PhysicsShapes.bullet());

        ed.setComponent(lastBomb, new Meta(createdTime));
        return lastBomb;
    }

    public static EntityId createMapTile(String tileSet, short tileIndex, Vec3d location, String tileType, EntityData ed, long createdTime) {
        EntityId lastTileInfo = ed.createEntity();

        ed.setComponents(lastTileInfo,
                TileType.create(tileType, tileSet, tileIndex, ed),
                ViewTypes.mapTile(ed),
                new Position(location, new Quatd(), 0f),
                PhysicsMassTypes.infinite(ed),
                PhysicsShapes.mapTile());

        ed.setComponent(lastTileInfo, new Meta(createdTime));
        
        return lastTileInfo;
    }

    //This is called by the server when it has calculcated the correct tileIndex number
    public static EntityId updateWangBlobEntity(EntityId entity, String tileSet, short tileIndex, Vec3d location, EntityData ed, long createdTime) {

        ed.setComponents(entity,
                TileTypes.wangblob(tileSet, tileIndex, ed),
                ViewTypes.mapTile(ed),
                new Position(location, new Quatd(), 0f),
                PhysicsMassTypes.infinite(ed),
                PhysicsShapes.mapTile());

        ed.setComponent(entity, new Meta(createdTime));
        return entity;
    }

    //Explosion is for now only visual, so only object type and position
    public static EntityId createExplosion2(Vec3d location, Quatd quat, EntityData ed, long createdTime) {
        EntityId lastExplosion = ed.createEntity();

        ed.setComponents(lastExplosion,
                ViewTypes.explosion2(ed),
                new Position(location, quat, 0f),
                new Decay(createdTime, createdTime + TimeUnit.NANOSECONDS.convert(CoreViewConstants.EXPLOSION2DECAY, TimeUnit.MILLISECONDS)));

        ed.setComponent(lastExplosion, new Meta(createdTime));
        return lastExplosion;
    }

    public static EntityId createWormhole(Vec3d location, double radius, double targetAreaRadius, double force, String gravityType, Vec3d warpTargetLocation, EntityData ed, long createdTime) {
        EntityId lastWormhole = ed.createEntity();

        ed.setComponents(lastWormhole,
                ViewTypes.wormhole(ed),
                new Position(location, new Quatd(), 0f),
                PhysicsMassTypes.infinite(ed),
                new GravityWell(radius, force, gravityType),
                PhysicsShapes.wormhole(),
                new WarpTouch(warpTargetLocation));

        ed.setComponent(lastWormhole, new Meta(createdTime));
        return lastWormhole;
    }

    public static EntityId createForce(EntityId owner, Force force, Vector2 forceWorldCoords, EntityData ed, long createdTime) {
        EntityId lastForce = ed.createEntity();
        ed.setComponents(lastForce,
                new PhysicsForce(owner, force, forceWorldCoords));

        ed.setComponent(lastForce, new Meta(createdTime));
        return lastForce;
    }

    public static EntityId createOver5(Vec3d location, double radius, double force, String gravityType, EntityData ed, long createdTime) {
        EntityId lastOver5 = ed.createEntity();

        ed.setComponents(lastOver5,
                ViewTypes.over5(ed),
                new Position(location, new Quatd(), 0f),
                PhysicsMassTypes.infinite(ed),
                new GravityWell(radius, force, gravityType),
                PhysicsShapes.over5());

        ed.setComponent(lastOver5, new Meta(createdTime));
        return lastOver5;
    }

    /**
     * Small asteroid with animation
     *
     * @param location the Vec3d position of the asteroid
     * @param ed the entitydata set to create the entity in
     * @return the entityid of the created entity
     */
    public static EntityId createOver1(Vec3d location, EntityData ed, long createdTime) {
        EntityId lastOver1 = ed.createEntity();

        ed.setComponents(lastOver1,
                ViewTypes.over1(ed),
                new Position(location, new Quatd(), 0f),
                PhysicsMassTypes.normal(ed),
                PhysicsShapes.over1());

        ed.setComponent(lastOver1, new Meta(createdTime));
        return lastOver1;
    }

    /**
     * Medium asteroid with animation
     *
     * @param location the Vec3d position of the asteroid
     * @param ed the entitydata set to create the entity in
     * @return the entityid of the created entity
     */
    public static EntityId createOver2(Vec3d location, EntityData ed, long createdTime) {
        EntityId lastOver2 = ed.createEntity();

        ed.setComponents(lastOver2,
                ViewTypes.over2(ed),
                new Position(location, new Quatd(), 0f),
                PhysicsMassTypes.normal(ed),
                PhysicsShapes.over2());

        ed.setComponent(lastOver2, new Meta(createdTime));
        return lastOver2;
    }

    public static EntityId createWarpEffect(Vec3d location, EntityData ed, long createdTime) {
        EntityId lastWarpTo = ed.createEntity();

        ed.setComponents(lastWarpTo,
                ViewTypes.warp(ed),
                new Position(location, new Quatd(), 0f),
                new Decay(createdTime, createdTime + TimeUnit.NANOSECONDS.convert(CoreViewConstants.WARPDECAY, TimeUnit.MILLISECONDS)));

        ed.setComponent(lastWarpTo, new Meta(createdTime));
        return lastWarpTo;
    }

    public static EntityId createRepel(EntityId owner, Vec3d location, EntityData ed, long createdTime) {
        EntityId lastWarpTo = ed.createEntity();

        ed.setComponents(lastWarpTo,
                ViewTypes.repel(ed),
                new Position(location, new Quatd(), 0f),
                new Decay(createdTime, createdTime + TimeUnit.NANOSECONDS.convert(CoreViewConstants.REPELDECAY, TimeUnit.MILLISECONDS)),
                new Parent(owner),
                ActionTypes.repel(ed),
                PhysicsShapes.repel(),
                PhysicsMassTypes.infinite(ed),
                AudioTypes.repel(ed));

        ed.setComponent(lastWarpTo, new Meta(createdTime));
        return lastWarpTo;
    }

    public static EntityId createCaptureTheFlag(Vec3d location, EntityData ed, long createdTime) {
        EntityId lastFlag = ed.createEntity();

        ed.setComponents(lastFlag,
                ViewTypes.flag_theirs(ed),
                new Position(location, new Quatd(), 0f),
                PhysicsShapes.flag(),
                PhysicsMassTypes.infinite(ed),
                new Flag(),
                new Frequency(0));
        ed.setComponent(lastFlag, new Meta(createdTime));

        return lastFlag;
    }

    public static EntityId createMob(Vec3d location, EntityData ed, long createdTime) {
        EntityId lastMob = ed.createEntity();

        ed.setComponents(lastMob,
                ViewTypes.mob(ed),
                MobTypes.mob1(ed),
                //new Drivable(), //old
                new Steerable(),
                new Position(location, new Quatd(), 0f),
                PhysicsShapes.mob(),
                PhysicsMassTypes.normal(ed),
                new Energy(CoreGameConstants.MOBHEALTH));
        ed.setComponent(lastMob, new Meta(createdTime));

        return lastMob;
    }

    public static EntityId createTower(Vec3d location, EntityData ed, long createdTime) { // add tower-type ?
        EntityId lastTower = ed.createEntity();
        double rand = Math.random() * Math.PI;

        ed.setComponents(lastTower,
                ViewTypes.tower(ed), // this
                TowerTypes.tower1(ed, lastTower),
                //new Position(location, new Quatd(0,0,Math.sin(rand),Math.cos(rand)), 0f),
                new Position(location, new Quatd(0, 0, -0.5, 0.5), 0f),
                PhysicsShapes.tower(), // and this moved to type creation, for modularity ?
                PhysicsMassTypes.infinite(ed),
                new Damage(20)); //The amount of damage the tower does

        ed.setComponent(lastTower, new Meta(createdTime));
        return lastTower;
    }

    public static EntityId createBase(Vec3d location, EntityData ed, long createdTime) {
        EntityId lastBase = ed.createEntity();

        ed.setComponents(lastBase,
                ViewTypes.base(ed),
                BaseTypes.base1(ed),
                new SteeringSeekable(),
                new Position(location, new Quatd(), 0f),
                PhysicsShapes.base(),
                PhysicsMassTypes.infinite(ed),
                new Energy(CoreGameConstants.BASEHEALTH));

        ed.setComponent(lastBase, new Meta(createdTime));
        return lastBase;
    }

    public static EntityId createHealthBuff(int healthChange, EntityId target, EntityData ed, long createdTime) {

        EntityId lastHealthBuff = ed.createEntity();

        ed.setComponents(lastHealthBuff,
                new HealthChange(healthChange), //apply the damage
                new Buff(target, 0)); //apply right away

        ed.setComponent(lastHealthBuff, new Meta(createdTime));
        return lastHealthBuff;
    }

    public static EntityId createThor(EntityId owner, Vec3d location, Quatd orientation, double rotation, Vector2 attackVelocity, long thorDecay, EntityData ed, long createdTime) {
        EntityId lastBomb = ed.createEntity();

        ed.setComponents(lastBomb, ViewTypes.thor(ed),
                new Position(location, orientation, rotation),
                new PhysicsVelocity(new Vector2(attackVelocity.x, attackVelocity.y)),
                new Decay(createdTime, createdTime + TimeUnit.NANOSECONDS.convert(thorDecay, TimeUnit.MILLISECONDS)),
                WeaponTypes.thor(ed),
                new Parent(owner),
                PhysicsMassTypes.normal_bullet(ed),
                PhysicsShapes.bomb());
        ed.setComponent(lastBomb, new Meta(createdTime));

        return lastBomb;
    }
}
