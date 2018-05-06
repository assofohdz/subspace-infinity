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
import infinity.CoreGameConstants;
import infinity.api.es.AudioTypes;
import infinity.api.es.WeaponType;
import infinity.api.es.WeaponTypes;
import infinity.api.es.Damage;
import infinity.api.es.GravityWell;
import infinity.api.es.ship.weapons.Guns;
import infinity.api.es.HitPoints;
import infinity.api.es.PhysicsVelocity;
import infinity.api.es.ship.weapons.BombLevel;
import infinity.api.es.ship.weapons.Bombs;
import infinity.api.es.ship.weapons.BombsCooldown;
import infinity.api.es.ship.weapons.Bursts;
import infinity.api.es.ship.weapons.BurstsCooldown;
import infinity.api.es.ship.weapons.GravityBombs;
import infinity.api.es.ship.weapons.GravityBombsCooldown;
import infinity.api.es.ship.weapons.GunLevel;
import infinity.api.es.ship.weapons.GunsCooldown;
import infinity.api.es.ship.weapons.Mines;
import infinity.api.es.ship.weapons.MinesCooldown;
import infinity.api.es.ship.weapons.Thor;
import infinity.api.es.ship.weapons.ThorCooldown;
import infinity.sim.SimpleBody;
import infinity.sim.CoreGameEntities;
import infinity.sim.SimplePhysics;
import java.util.HashSet;
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

    //Entities that has guns
    private EntitySet guns;
    //Entities that has bombs
    private EntitySet bombs;
    //Entities that has bursts
    private EntitySet bursts;
    //Entities that has gravityBombs
    private EntitySet gravityBombs;
    //Entities that has mines
    private EntitySet mines;
    private EntitySet gunsCooldowns;
    private EntitySet bombsCooldowns;
    private EntitySet burstsCooldowns;
    private EntitySet gravityBombsCooldowns;
    private EntitySet minesCooldowns;
    private EntitySet thors;
    private EntitySet thorsCooldowns;

    @Override
    protected void initialize() {
        this.ed = getSystem(EntityData.class);

        // Get the physics system... it's not available yet when onInitialize() is called.
        physics = getSystem(SimplePhysics.class);
        if (physics == null) {
            throw new RuntimeException("GameSessionHostedService requires a SimplePhysics system.");
        }

        health = getSystem(HealthState.class);

        guns = ed.getEntities(Guns.class);
        gunsCooldowns = ed.getEntities(GunsCooldown.class);

        bombs = ed.getEntities(Bombs.class);
        bombsCooldowns = ed.getEntities(BombsCooldown.class);

        bursts = ed.getEntities(Bursts.class);
        burstsCooldowns = ed.getEntities(BurstsCooldown.class);

        gravityBombs = ed.getEntities(GravityBombs.class);
        gravityBombsCooldowns = ed.getEntities(GravityBombsCooldown.class);

        mines = ed.getEntities(Mines.class);
        minesCooldowns = ed.getEntities(MinesCooldown.class);

        thors = ed.getEntities(Thor.class);
        thorsCooldowns = ed.getEntities(ThorCooldown.class);

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

        gunsCooldowns.release();
        gunsCooldowns = null;

        bombsCooldowns.release();
        bombsCooldowns = null;

        burstsCooldowns.release();
        burstsCooldowns = null;

        gravityBombsCooldowns.release();
        gravityBombsCooldowns = null;

        minesCooldowns.release();
        minesCooldowns = null;

        thors.release();
        thors = null;

        thorsCooldowns.release();
        thorsCooldowns = null;

    }

    @Override
    public void update(SimTime tpf) {

        //Update who has what ship weapons
        guns.applyChanges();
        gunsCooldowns.applyChanges();

        bombs.applyChanges();
        bombsCooldowns.applyChanges();

        gravityBombs.applyChanges();
        gravityBombsCooldowns.applyChanges();

        mines.applyChanges();
        minesCooldowns.applyChanges();

        bursts.applyChanges();
        burstsCooldowns.applyChanges();

        thors.applyChanges();
        thorsCooldowns.applyChanges();

        for (Entity e : gunsCooldowns) {
            GunsCooldown d = e.get(GunsCooldown.class);
            if (d.getPercent() >= 1.0) {
                ed.removeComponent(e.getId(), GunsCooldown.class);
            }
        }

        for (Entity e : bombsCooldowns) {
            BombsCooldown d = e.get(BombsCooldown.class);
            if (d.getPercent() >= 1.0) {
                ed.removeComponent(e.getId(), BombsCooldown.class);
            }
        }

        for (Entity e : gravityBombsCooldowns) {
            GravityBombsCooldown d = e.get(GravityBombsCooldown.class);
            if (d.getPercent() >= 1.0) {
                ed.removeComponent(e.getId(), GravityBombsCooldown.class);
            }
        }

        for (Entity e : minesCooldowns) {
            MinesCooldown d = e.get(MinesCooldown.class);
            if (d.getPercent() >= 1.0) {
                ed.removeComponent(e.getId(), MinesCooldown.class);
            }
        }

        for (Entity e : burstsCooldowns) {
            BurstsCooldown d = e.get(BurstsCooldown.class);
            if (d.getPercent() >= 1.0) {
                ed.removeComponent(e.getId(), BurstsCooldown.class);
            }
        }

        for (Entity e : thorsCooldowns) {
            ThorCooldown d = e.get(ThorCooldown.class);
            if (d.getPercent() >= 1.0) {
                ed.removeComponent(e.getId(), ThorCooldown.class);
            }
        }

        /*
        Default pattern to let multiple sessions call methods and then process them one by one
         */
        for (Attack attack : sessionAttackCreations) {
            this.attack(attack.getOwner(), attack.getWeaponType());
        }
        sessionAttackCreations.clear();

        time = tpf;
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
        if (!thors.containsId(requestor) || thorsCooldowns.containsId(requestor)) {
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
        ed.setComponent(requestor, new ThorCooldown(shipThors.getCooldown()));

        //Reduce count of thors in inventory:
        if (shipThors.getCount() == 1) {
            ed.removeComponent(requestor, Thor.class);
        } else {
            ed.setComponent(requestor, new Thor(shipThors.getCooldown(), shipThors.getCount() - 1));
        }
    }

    //TODO: Get the damage from some setting instead of hard coded
    /**
     * Checks that an entity can attack with Bullets
     *
     * @param requestor requesting entity
     */
    private void entityAttackGuns(EntityId requestor) {
        //Check authorization and cooldown
        if (!guns.containsId(requestor) || gunsCooldowns.containsId(requestor)) {
            return;
        }
        Guns shipGuns = guns.getEntity(requestor).get(Guns.class);

        //Check health
        if (!health.hasHealth(requestor) || health.getHealth(requestor) < shipGuns.getCost()) {
            return;
        }
        //Deduct health
        health.createHealthChange(requestor, -1 * shipGuns.getCost());

        //Perform attack
        AttackInfo info = this.getAttackInfo(requestor, WeaponTypes.bullet(ed));

        this.attackGuns(info, shipGuns.getLevel(), new Damage(-20), requestor);

        //Set new cooldown
        ed.setComponent(requestor, new GunsCooldown(shipGuns.getCooldown()));
    }

    /**
     * Checks that an entity can attack with Bombs
     *
     * @param requestor requesting entity
     */
    private void entityAttackBomb(EntityId requestor) {
        //Check authorization
        if (!bombs.containsId(requestor) || bombsCooldowns.containsId(requestor)) {
            return;
        }

        Bombs shipBombs = bombs.getEntity(requestor).get(Bombs.class);

        //Check health
        if (!health.hasHealth(requestor) || health.getHealth(requestor) < shipBombs.getCost()) {
            return;
        }
        //Deduct health
        health.createHealthChange(requestor, -1 * shipBombs.getCost());

        //Perform attack
        AttackInfo info = this.getAttackInfo(requestor, WeaponTypes.bomb(ed));

        this.attackBomb(info, shipBombs.getLevel(), new Damage(-20), requestor);

        //Set new cooldown
        ed.setComponent(requestor, new GunsCooldown(shipBombs.getCooldown()));

    }

    /**
     * Checks that an entity can place Mines
     *
     * @param requestor requesting entity
     */
    private void entityPlaceMine(EntityId requestor) {
        //Check authorization
        if (!mines.containsId(requestor) || minesCooldowns.containsId(requestor)) {
            return;
        }

        Mines shipMines = mines.getEntity(requestor).get(Mines.class);

        //Check health
        if (!health.hasHealth(requestor) || health.getHealth(requestor) < shipMines.getCost()) {
            return;
        }
        //Deduct health
        health.createHealthChange(requestor, -1 * shipMines.getCost());

        //Perform attack 
        AttackInfo info = this.getAttackInfo(requestor, WeaponTypes.mine(ed));

        this.attackBomb(info, shipMines.getLevel(), new Damage(-20), requestor);

        //Set new cooldown
        ed.setComponent(requestor, new GunsCooldown(shipMines.getCooldown()));
    }

    /**
     * Checks that an entity can attack with Bursts
     *
     * @param requestor requesting entity
     */
    private void entityBurst(EntityId requestor) {
        //Check authorization
        if (!bursts.containsId(requestor) || burstsCooldowns.containsId(requestor)) {
            return;
        }

        Bursts shipBursts = bursts.getEntity(requestor).get(Bursts.class);

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
            ed.removeComponent(requestor, Bursts.class);
        } else {
            ed.setComponent(requestor, new Thor(shipBursts.getCooldown(), shipBursts.getCount() - 1));
        }
        //Set new cooldown
        ed.setComponent(requestor, new BurstsCooldown(shipBursts.getCooldown()));
    }

    /**
     * Checks that an entity can attack with Gravity Bombs
     *
     * @param requestor requesting entity
     */
    private void entityAttackGravityBomb(EntityId requestor) {
        //Check authorization
        if (!gravityBombs.containsId(requestor) || gravityBombsCooldowns.containsId(requestor)) {
            return;
        }

        GravityBombs shipGravityBombs = gravityBombs.getEntity(requestor).get(GravityBombs.class);

        //Check health
        if (!health.hasHealth(requestor) || health.getHealth(requestor) < shipGravityBombs.getCost()) {
            return;
        }
        //Deduct health
        health.createHealthChange(requestor, -1 * shipGravityBombs.getCost());

        //Perform attack
        AttackInfo info = this.getAttackInfo(requestor, WeaponTypes.gravityBomb(ed));

        this.attackGravBomb(info, shipGravityBombs.getLevel(), new Damage(-20), requestor);

        //Set new cooldown
        ed.setComponent(requestor, new GunsCooldown(shipGravityBombs.getCooldown()));
    }

    /**
     * Performs an attack
     *
     * @param requestor requesting entity
     */
    private AttackInfo getAttackInfo(EntityId attacker, WeaponType type) {
        EntityId eId = attacker;
        SimTime localTime = time; //Copy incase someone else is writing to it
        Entity e = ed.getEntity(eId, HitPoints.class);

        Quatd orientation = new Quatd();
        Vec3d location;

        //if (e.get(HitPoints.class) != null) {
        double rotation;
        Vector2 attackVel;
        // Attacking body:
        SimpleBody shipBody = physics.getBody(e.getId());

        Transform shipTransform = shipBody.getTransform();

        //Bomb velocity:
        attackVel = this.getAttackVelocity(shipTransform.getRotation(), type, shipBody.getLinearVelocity());

        //Position
        Vector2 attackPos = this.getAttackPosition(shipTransform.getRotation(), shipTransform.getTranslation());

        //Convert to Vec3d because that's what the rest of sim-eth-es uses
        location = new Vec3d(attackPos.x, attackPos.y, 0);
        orientation = shipBody.orientation;
        rotation = shipTransform.getRotation();
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
    private void attackBomb(AttackInfo info, BombLevel level, Damage damage, EntityId owner) {
        EntityId projectile;
        projectile = CoreGameEntities.createBomb(owner, info.getLocation(), info.getOrientation(), info.getRotation(), info.getAttackVelocity(), CoreGameConstants.BULLETDECAY, ed, level);

        //TODO: Calculate damage based on level:
        ed.setComponent(projectile, damage);

        CoreGameEntities.createBombSound(projectile, info.location, ed, level);
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
        projectile = CoreGameEntities.createBurst(owner, info.getLocation(), info.getOrientation(), info.getRotation(), info.getAttackVelocity(), CoreGameConstants.BULLETDECAY, ed);

        //TODO: Calculate damage based on level:
        ed.setComponent(projectile, damage);

        CoreGameEntities.createBurstSound(owner, info.location, ed);
    }

    /**
     * Creates a bullet entity
     *
     * @param info the attack information
     * @param level the bullet level
     * @param damage the damage of the bullet
     */
    private void attackGuns(AttackInfo info, GunLevel level, Damage damage, EntityId owner) {
        EntityId projectile;
        projectile = CoreGameEntities.createBullet(owner, info.getLocation(), info.getOrientation(), info.getRotation(), info.getAttackVelocity(), CoreGameConstants.BULLETDECAY, ed, level);

        //TODO: Calculate damage based on level:
        ed.setComponent(projectile, damage);

        CoreGameEntities.createBulletSound(projectile, info.location, ed, level);
    }

    /**
     * Creates a gravity bomb entity
     *
     * @param info the attack information
     * @param level the bomb level
     * @param damage the damage of the bomb
     */
    private void attackGravBomb(AttackInfo info, BombLevel level, Damage damage, EntityId owner) {
        EntityId projectile;
        HashSet<EntityComponent> delayedComponents = new HashSet<>();
        delayedComponents.add(new GravityWell(5, CoreGameConstants.GRAVBOMBWORMHOLEFORCE, GravityWell.PULL));             //Suck everything in
        delayedComponents.add(new PhysicsVelocity(new Vector2(0, 0))); //Freeze the bomb
        delayedComponents.add(WeaponTypes.gravityBomb(ed));
        projectile = CoreGameEntities.createDelayedBomb(owner, info.getLocation(), info.getOrientation(), info.getRotation(), info.getAttackVelocity(), CoreGameConstants.GRAVBOMBDECAY, CoreGameConstants.GRAVBOMBDELAY, delayedComponents, ed, level);
        ed.setComponent(projectile, new Damage(damage.getDamage()));

        CoreGameEntities.createSound(projectile, info.location, AudioTypes.FIRE_GRAVBOMB, ed);
    }

    /**
     * Creates a thor entity
     *
     * @param info the attack information
     * @param damage the damage of the thor
     */
    private void attackThor(AttackInfo info, Damage damage, EntityId owner) {
        EntityId projectile;
        projectile = CoreGameEntities.createThor(owner, info.getLocation(), info.getOrientation(), info.getRotation(), info.getAttackVelocity(), CoreGameConstants.THORDECAY, ed);
        ed.setComponent(projectile, new Damage(damage.getDamage()));

        CoreGameEntities.createSound(projectile, info.location, AudioTypes.FIRE_THOR, ed);
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
