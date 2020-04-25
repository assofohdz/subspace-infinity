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
package infinity.es.states;

import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.simsilica.es.Entity;
import com.simsilica.es.EntityComponent;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.mathd.Quatd;
import com.simsilica.mathd.Vec3d;
import com.simsilica.sim.AbstractGameSystem;
import com.simsilica.sim.SimTime;
import infinity.api.es.AudioTypes;
import infinity.api.es.WeaponType;
import infinity.api.es.WeaponTypes;
import infinity.api.es.Damage;
import infinity.api.es.GravityWell;
import infinity.api.es.ship.weapons.Gun;
import infinity.api.es.ship.Energy;
import infinity.api.es.PhysicsVelocity;
import infinity.api.es.ship.weapons.BombLevelEnum;
import infinity.api.es.ship.weapons.Bomb;
import infinity.api.es.ship.weapons.BombFireDelay;
import infinity.api.es.ship.actions.Burst;
import infinity.api.es.ship.weapons.GravityBomb;
import infinity.api.es.ship.weapons.GravityBombFireDelay;
import infinity.api.es.ship.weapons.GunLevelEnum;
import infinity.api.es.ship.weapons.GunFireDelay;
import infinity.api.es.ship.weapons.Mine;
import infinity.api.es.ship.weapons.MineFireDelay;
import infinity.api.es.ship.actions.Thor;
import infinity.api.es.ship.weapons.BombCost;
import infinity.api.es.ship.weapons.GravityBombCost;
import infinity.api.es.ship.weapons.GunCost;
import infinity.api.es.ship.weapons.MineCost;
import infinity.api.sim.CoreGameConstants;
import infinity.api.sim.ModuleGameEntities;
import infinity.sim.SimpleBody;
import infinity.sim.SimplePhysics;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import org.dyn4j.geometry.Transform;
import org.dyn4j.geometry.Vector2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This state authorizes and attacks upon requests. Also keeps track of weapon
 * cooldowns
 *
 * @author Asser Fahrenholz
 */
public class WeaponStateServer extends AbstractGameSystem {

    static Logger log = LoggerFactory.getLogger(WeaponStateServer.class);
    private LinkedHashSet<Attack> sessionAttackCreations = new LinkedHashSet<>();

    private EntityData ed;
    private Object gameSystems;
    private SimplePhysics physics;
    private SimTime time;
    private HealthState health;

    //Sets for those entities that has the weapon available
    private EntitySet thors, mines, gravityBombs, bursts, bombs, guns;

    @Override
    protected void initialize() {
        this.ed = getSystem(EntityData.class);

        // Get the physics system... it's not available yet when onInitialize() is called.
        physics = getSystem(SimplePhysics.class);
        if (physics == null) {
            throw new RuntimeException("GameSessionHostedService requires a SimplePhysics system.");
        }

        health = getSystem(HealthState.class);

        guns = ed.getEntities(Gun.class, GunFireDelay.class, GunCost.class);

        bombs = ed.getEntities(Bomb.class, BombFireDelay.class, BombCost.class);

        bursts = ed.getEntities(Burst.class);

        gravityBombs = ed.getEntities(GravityBomb.class, GravityBombFireDelay.class, GravityBombCost.class);

        mines = ed.getEntities(Mine.class, MineFireDelay.class, MineCost.class);

        thors = ed.getEntities(Thor.class);

    }

    @Override
    protected void terminate() {
        guns.release();
        guns = null;

        bombs.release();
        bombs = null;

        gravityBombs.release();
        gravityBombs = null;

        mines.release();
        mines = null;

        bursts.release();
        bursts = null;

        thors.release();
        thors = null;

    }

    @Override
    public void update(SimTime tpf) {
        time = tpf;

        //Update who has what ship weapons
        guns.applyChanges();

        bombs.applyChanges();

        gravityBombs.applyChanges();

        mines.applyChanges();

        bursts.applyChanges();

        thors.applyChanges();

        /*
        Default pattern to let multiple sessions call methods and then process them one by one
         */
        Iterator<Attack> iterator = sessionAttackCreations.iterator();
        while (iterator.hasNext()) {
            Attack a = iterator.next();

            this.attack(a.getOwner(), a.getWeaponType());

            iterator.remove();
        }
        /*
        for (Attack attack : sessionAttackCreations) {
            this.attack(attack.getOwner(), attack.getWeaponType());
        }
        sessionAttackCreations.clear();
         */
    }

    /**
     * Requests an attack from an entity
     *
     * @param requestor the requesting entity
     * @param type the weapon type to attack with
     */
    private void attack(EntityId requestor, WeaponType type) {
        switch (type.getTypeName(ed)) {
            case WeaponTypes.BOMB:
                this.entityAttackBomb(requestor);
                break;
            case WeaponTypes.BULLET:
                this.entityAttackGuns(requestor);
                break;
            case WeaponTypes.BURST:
                this.entityBurst(requestor);
                break;
            case WeaponTypes.GRAVITYBOMB:
                this.entityAttackGravityBomb(requestor);
                break;
            case WeaponTypes.MINE:
                this.entityPlaceMine(requestor);
                break;
            case WeaponTypes.THOR:
                this.entityAttackThor(requestor);
                break;
            default:
                throw new UnsupportedOperationException("Unsupported weapontype " + type + " in attack");
        }
    }

    /**
     * Checks that an entity can attack with Thors
     *
     * @param requestor requesting entity
     */
    private void entityAttackThor(EntityId requestor) {
        //Check authorization and cooldown
        if (!thors.containsId(requestor)) {
            return;
        }
        Thor shipThors = thors.getEntity(requestor).get(Thor.class);

        /* Health check disabled because Thors are free to use
        //Check health
        if (!health.hasHealth(requestor) || health.getHealth(requestor) < shipGuns.getCost()) {
            return;
        }
        //Deduct health
        health.createHealthChange(requestor, -1 * shipGuns.getCost());
         */
        //Perform attack
        AttackInfo info = this.getAttackInfo(requestor, WeaponTypes.thor(ed));

        this.attackThor(info, new Damage(-20), requestor);

        //Set new cooldown
        //No cooldown on thors
        //ed.setComponent(requestor, new ThorFireDelay(shipThors.getCooldown()));
        //Reduce count of thors in inventory:
        if (shipThors.getCount() == 1) {
            ed.removeComponent(requestor, Thor.class);
        } else {
            ed.setComponent(requestor, new Thor(shipThors.getCount() - 1));
        }
    }

    //TODO: Get the damage from some setting instead of hard coded
    /**
     * Checks that an entity can attack with Bullets
     *
     * @param requestor requesting entity
     */
    private void entityAttackGuns(EntityId requestor) {
        Entity entity = guns.getEntity(requestor);

        Gun shipGuns = entity.get(Gun.class);
        GunCost shipGunCost = entity.get(GunCost.class);
        GunFireDelay shipGunCooldown = entity.get(GunFireDelay.class);

        //Check authorization and check cooldown
        if (!guns.containsId(requestor) || shipGunCooldown.getPercent() < 1.0) {
            return;
        }

        //Check health
        if (!health.hasHealth(requestor) || health.getHealth(requestor) < shipGunCost.getCost()) {
            return;
        }
        //Deduct health
        health.createHealthChange(requestor, -1 * shipGunCost.getCost());

        //Perform attack
        AttackInfo info = this.getAttackInfo(requestor, WeaponTypes.bullet(ed));

        this.attackGuns(info, shipGuns.getLevel(), new Damage(-20), requestor);

        //Set new cooldown
        ed.setComponent(requestor, shipGunCooldown.copy());
    }

    /**
     * Checks that an entity can attack with Bombs
     *
     * @param requestor requesting entity
     */
    private void entityAttackBomb(EntityId requestor) {
        Entity entity = bombs.getEntity(requestor);
        Bomb shipBombs = entity.get(Bomb.class);
        BombFireDelay shipBombCooldown = entity.get(BombFireDelay.class);
        BombCost shipBombCost = entity.get(BombCost.class);

        //Check authorization
        if (!bombs.containsId(requestor) || shipBombCooldown.getPercent() < 1.0) {
            return;
        }

        //Check health
        if (!health.hasHealth(requestor) || health.getHealth(requestor) < shipBombCost.getCost()) {
            return;
        }
        //Deduct health
        health.createHealthChange(requestor, -1 * shipBombCost.getCost());

        //Perform attack
        AttackInfo info = this.getAttackInfo(requestor, WeaponTypes.bomb(ed));

        this.attackBomb(info, shipBombs.getLevel(), new Damage(-20), requestor);

        //Set new cooldown
        ed.setComponent(requestor, shipBombCooldown.copy());

    }

    /**
     * Checks that an entity can place Mines
     *
     * @param requestor requesting entity
     */
    private void entityPlaceMine(EntityId requestor) {
        Entity entity = mines.getEntity(requestor);
        Mine shipMines = entity.get(Mine.class);
        MineCost shipMineCost = entity.get(MineCost.class);
        MineFireDelay shipMineCooldown = entity.get(MineFireDelay.class);

        //Check authorization and cooldown
        if (!mines.containsId(requestor) || shipMineCooldown.getPercent() < 1.0) {
            return;
        }

        //Check health
        if (!health.hasHealth(requestor) || health.getHealth(requestor) < shipMineCost.getCost()) {
            return;
        }
        //Deduct health
        health.createHealthChange(requestor, -1 * shipMineCost.getCost());

        //Perform attack 
        AttackInfo info = this.getAttackInfo(requestor, WeaponTypes.mine(ed));

        this.attackBomb(info, shipMines.getLevel(), new Damage(-20), requestor);

        //Set new cooldown
        ed.setComponent(requestor, shipMineCooldown.copy());
    }

    /**
     * Checks that an entity can attack with Bursts
     *
     * @param requestor requesting entity
     */
    private void entityBurst(EntityId requestor) {

        //Check authorization
        if (!bursts.containsId(requestor)) {
            return;
        }
        Burst shipBursts = bursts.getEntity(requestor).get(Burst.class);

        //No health check for these
        //Perform attack
        float rotation;

        float angle = (360 / CoreGameConstants.BURSTPROJECTILECOUNT) * FastMath.DEG_TO_RAD;

        AttackInfo infoOrig = this.getAttackInfo(requestor, WeaponTypes.burst(ed));
        for (int i = 0; i < CoreGameConstants.BURSTPROJECTILECOUNT; i++) {
            AttackInfo info = infoOrig.clone();
            rotation = angle * (float) i;

            //log.info("Rotating (from original) degrees: "+rotation * FastMath.RAD_TO_DEG);
            //Quaternion newOrientation = info.getOrientation().toQuaternion().fromAngleAxis(rotation, Vector3f.UNIT_Z);
            Vector2 newVelocity = info.getAttackVelocity();

            //log.info("Attack velocity: "+newVelocity.toString());
            newVelocity.rotate(rotation);

            //log.info("rotated velocity: "+newVelocity.toString());
            //info.setOrientation(new Quatd(newOrientation));
            info.setAttackVelocity(newVelocity);
            info.setRotation(rotation);

            this.attackBurst(info, new Damage(-30), requestor);
        }

        //Reduce count of bursts in inventory:
        if (shipBursts.getCount() == 1) {
            ed.removeComponent(requestor, Burst.class);
        } else {
            ed.setComponent(requestor, new Burst(shipBursts.getCount() - 1));
        }
    }

    /**
     * Checks that an entity can attack with Gravity Bombs
     *
     * @param requestor requesting entity
     */
    private void entityAttackGravityBomb(EntityId requestor) {
        Entity entity = gravityBombs.getEntity(requestor);

        GravityBombFireDelay shipGravBombCooldown = entity.get(GravityBombFireDelay.class);
        GravityBombCost shipGravBombCost = entity.get(GravityBombCost.class);

        //Check authorization
        if (!gravityBombs.containsId(requestor) || shipGravBombCooldown.getPercent() < 1.0) {
            return;
        }

        GravityBomb shipGravityBombs = entity.get(GravityBomb.class);

        //Check health
        if (!health.hasHealth(requestor) || health.getHealth(requestor) < shipGravBombCost.getCost()) {
            return;
        }
        //Deduct health
        health.createHealthChange(requestor, -1 * shipGravBombCost.getCost());

        //Perform attack
        AttackInfo info = this.getAttackInfo(requestor, WeaponTypes.gravityBomb(ed));

        this.attackGravBomb(info, shipGravityBombs.getLevel(), new Damage(-20), requestor);

        //Set new cooldown
        ed.setComponent(requestor, shipGravBombCooldown.copy());
    }

    /**
     * Performs an attack
     *
     * @param requestor requesting entity
     */
    private AttackInfo getAttackInfo(EntityId attacker, WeaponType type) {
        EntityId eId = attacker;
        SimTime localTime = time; //Copy incase someone else is writing to it
        Entity e = ed.getEntity(eId, Energy.class);

        Quatd orientation = new Quatd();
        Vec3d location;

        //if (e.get(HitPoints.class) != null) {
        double rotation;
        Vector2 attackVel;
        // Attacking body:
        SimpleBody shipBody = physics.getBody(e.getId());

        Transform shipTransform = shipBody.getTransform();

        //Bomb velocity:
        attackVel = this.getAttackVelocity(shipTransform.getRotationAngle(), type, shipBody.getLinearVelocity());

        //Position
        Vector2 attackPos = this.getAttackPosition(shipTransform.getRotationAngle(), shipTransform.getTranslation());

        //Convert to Vec3d because that's what the rest of sim-eth-es uses
        location = new Vec3d(attackPos.x, attackPos.y, 0);
        orientation = shipBody.orientation;
        rotation = shipTransform.getRotationAngle();
        /*} else {
            // towers
            e = ed.getEntity(eId, AttackVelocity.class, Position.class, AttackDirection.class);
            location = e.get(Position.class).getLocation().add(new Vec3d(0, 0, 0));

            Vector2 ori = e.get(AttackDirection.class).getDirection();

            orientation = orientation.fromAngles(ori.getAngleBetween(new Vector2(1, 0)), ori.getAngleBetween(new Vector2(0, 1)), 0);
            rotation = 0;
            // skal angive længde og ikke bare multiply...
            attackVel = ori.multiply(e.get(AttackVelocity.class).getVelocity());
        }*/

        return new AttackInfo(location, orientation, rotation, attackVel);
    }

    /**
     * Creates a bomb entity
     *
     * @param info the attack information
     * @param level the bomb level
     * @param damage the damage of the bomb
     */
    private void attackBomb(AttackInfo info, BombLevelEnum level, Damage damage, EntityId owner) {
        EntityId projectile;
        projectile = ModuleGameEntities.createBomb(owner, info.getLocation(), info.getOrientation(), info.getRotation(), info.getAttackVelocity(), CoreGameConstants.BULLETDECAY, ed, level, time.getTime());

        //TODO: Calculate damage based on level:
        ed.setComponent(projectile, damage);

        ModuleGameEntities.createBombSound(projectile, info.location, ed, level, time.getTime());
    }

    /**
     * Creates one or more burst entity
     *
     * @param info the attack information
     * @param burstCount how many burst projectiles are we to create
     * @param damage the damage of the bomb
     */
    private void attackBurst(AttackInfo info, Damage damage, EntityId owner) {
        EntityId projectile;
        projectile = ModuleGameEntities.createBurst(owner, info.getLocation(), info.getOrientation(), info.getRotation(), info.getAttackVelocity(), CoreGameConstants.BULLETDECAY, ed, time.getTime());

        //TODO: Calculate damage based on level:
        ed.setComponent(projectile, damage);

        ModuleGameEntities.createBurstSound(owner, info.location, ed, time.getTime());
    }

    /**
     * Creates a bullet entity
     *
     * @param info the attack information
     * @param level the bullet level
     * @param damage the damage of the bullet
     */
    private void attackGuns(AttackInfo info, GunLevelEnum level, Damage damage, EntityId owner) {
        EntityId projectile;
        projectile = ModuleGameEntities.createBullet(owner, info.getLocation(), info.getOrientation(), info.getRotation(), info.getAttackVelocity(), CoreGameConstants.BULLETDECAY, ed, level, time.getTime());

        //TODO: Calculate damage based on level:
        ed.setComponent(projectile, damage);

        ModuleGameEntities.createBulletSound(projectile, info.location, ed, level, time.getTime());
    }

    /**
     * Creates a gravity bomb entity
     *
     * @param info the attack information
     * @param level the bomb level
     * @param damage the damage of the bomb
     */
    private void attackGravBomb(AttackInfo info, BombLevelEnum level, Damage damage, EntityId owner) {
        EntityId projectile;
        HashSet<EntityComponent> delayedComponents = new HashSet<>();
        delayedComponents.add(new GravityWell(5, CoreGameConstants.GRAVBOMBWORMHOLEFORCE, GravityWell.PULL));             //Suck everything in
        delayedComponents.add(new PhysicsVelocity(new Vector2(0, 0))); //Freeze the bomb
        delayedComponents.add(WeaponTypes.gravityBomb(ed));
        projectile = ModuleGameEntities.createDelayedBomb(owner, info.getLocation(), info.getOrientation(), info.getRotation(), info.getAttackVelocity(), CoreGameConstants.GRAVBOMBDECAY, CoreGameConstants.GRAVBOMBDELAY, delayedComponents, ed, level,  time.getTime());
        ed.setComponent(projectile, new Damage(damage.getDamage()));

        ModuleGameEntities.createSound(projectile, info.location, AudioTypes.FIRE_GRAVBOMB, ed,  time.getTime());
    }

    /**
     * Creates a thor entity
     *
     * @param info the attack information
     * @param damage the damage of the thor
     */
    private void attackThor(AttackInfo info, Damage damage, EntityId owner) {
        EntityId projectile;
        projectile = ModuleGameEntities.createThor(owner, info.getLocation(), info.getOrientation(), info.getRotation(), info.getAttackVelocity(), CoreGameConstants.THORDECAY, ed, time.getTime());
        ed.setComponent(projectile, new Damage(damage.getDamage()));

        ModuleGameEntities.createSound(projectile, info.location, AudioTypes.FIRE_THOR, ed, time.getTime());
    }

    public class Attack {

        final EntityId owner;
        final WeaponType weaponType;

        public Attack(EntityId owner, WeaponType type) {
            this.owner = owner;
            this.weaponType = type;
        }

        public EntityId getOwner() {
            return owner;
        }

        public WeaponType getWeaponType() {
            return weaponType;
        }
    }

    /**
     * AttackInfo is where attacks originate (location, orientation, rotation
     * and velocity)
     */
    private class AttackInfo {

        private Vec3d location;
        private Quatd orientation;
        private double rotation;
        private Vector2 attackVelocity;

        public AttackInfo(Vec3d location, Quatd orientation, double rotation, Vector2 attackVelocity) {
            this.location = location;
            this.orientation = orientation;
            this.rotation = rotation;
            this.attackVelocity = attackVelocity;
        }

        public Vec3d getLocation() {
            return location;
        }

        public Quatd getOrientation() {
            return orientation;
        }

        public double getRotation() {
            return rotation;
        }

        public Vector2 getAttackVelocity() {
            return attackVelocity;
        }

        public void setLocation(Vec3d location) {
            this.location = location;
        }

        public void setOrientation(Quatd orientation) {
            this.orientation = orientation;
        }

        public void setRotation(double rotation) {
            this.rotation = rotation;
        }

        public void setAttackVelocity(Vector2 attackVelocity) {
            this.attackVelocity = attackVelocity;
        }

        public AttackInfo clone() {
            return new AttackInfo(this.location.clone(), this.orientation.clone(), this.rotation, this.attackVelocity.copy());
        }

    }

    /**
     * Find the velocity vector2 of a given attack
     *
     * @param rotation the direction of the weapon
     * @param type the weapon type
     * @param linearVelocity the initial linear velocity
     * @return the Vector2 containing the total projectile velocity
     */
    private Vector2 getAttackVelocity(double rotation, WeaponType type, Vector2 linearVelocity) {
        Vector2 attackVel = new Vector2(0, 1);

        attackVel.rotate(rotation);

        attackVel.multiply(CoreGameConstants.BASEPROJECTILESPEED);

        switch (type.getTypeName(ed)) {
            case WeaponTypes.BOMB:
                attackVel.multiply(CoreGameConstants.BOMBPROJECTILESPEED);
                attackVel.add(linearVelocity); //Add ships velocity to account for direction of ship and rotation
                break;
            case WeaponTypes.BULLET:
                attackVel.multiply(CoreGameConstants.BULLETPROJECTILESPEED);
                attackVel.add(linearVelocity); //Add ships velocity to account for direction of ship and rotation
                break;
            case WeaponTypes.GRAVITYBOMB:
                attackVel.multiply(CoreGameConstants.GRAVBOMBPROJECTILESPEED);
                attackVel.add(linearVelocity); //Add ships velocity to account for direction of ship and rotation
                break;
            case WeaponTypes.MINE:
                attackVel.multiply(0); //A mine stands still
                break;
            case WeaponTypes.THOR:
                attackVel.multiply(CoreGameConstants.THORPROJECTILESPEED);
                attackVel.add(linearVelocity); //Add ships velocity to account for direction of ship and rotation
                break;
            case WeaponTypes.BURST:
                attackVel.multiply(CoreGameConstants.BURSTPROJECTILESPEED);
                attackVel.add(linearVelocity);
                break;
            default:
                throw new UnsupportedOperationException("WeaponType " + type.getTypeName(ed) + " not supported for velocity calculation");
        }

        return attackVel;
    }

    /**
     * Calculcate the starting position for an attack using a base offset
     *
     * @param rotation the direction of the attack
     * @param translation the base translation of the attack
     * @return the Vector2 containing the coordinates the projectile will
     * originate
     */
    private Vector2 getAttackPosition(double rotation, Vector2 translation) {
        Vector2 attackPos = new Vector2(0, 0);
        attackPos.rotate(rotation);
        //attackPos.multiply(CorePhysicsConstants.PROJECTILEOFFSET);
        attackPos.add(translation.x, translation.y);
        return attackPos;
    }

    /**
     * Queue up an attack
     *
     * @param attacker the attacking entity
     * @param weaponType the weapon of choice
     */
    public void sessionAttack(EntityId attacker, String weaponType) {
        sessionAttackCreations.add(new Attack(attacker, WeaponType.create(weaponType, ed)));
    }

}
