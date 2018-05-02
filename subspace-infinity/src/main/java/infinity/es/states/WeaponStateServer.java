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
import infinity.CorePhysicsConstants;
import infinity.api.es.Attack;
import infinity.api.es.AttackDirection;
import infinity.api.es.AttackVelocity;
import infinity.api.es.AudioTypes;
import infinity.api.es.WeaponType;
import infinity.api.es.WeaponTypes;
import infinity.api.es.Damage;
import infinity.api.es.GravityWell;
import infinity.api.es.ship.weapons.Guns;
import infinity.api.es.HitPoints;
import infinity.api.es.PhysicsVelocity;
import infinity.api.es.Position;
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
import org.dyn4j.geometry.Transform;
import org.dyn4j.geometry.Vector2;

/**
 * This state authorizes and attacks upon requests. Also keeps track of weapon
 * cooldowns
 *
 * @author Asser Fahrenholz
 */
public class WeaponStateServer extends AbstractGameSystem {

    private EntityData ed;
    private EntitySet attacks;
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

        attacks = ed.getEntities(Attack.class, WeaponType.class);

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
        attacks.release();
        attacks = null;

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

        if (attacks.applyChanges()) {
            for (Entity e : attacks.getAddedEntities()) {
                Attack a = e.get(Attack.class);
                WeaponType at = e.get(WeaponType.class);

                this.attack(a.getOwner(), at);

                ed.removeEntity(e.getId()); //Attack has been processed, now remove it
            }
        }

        time = tpf;
    }

    /**
     * Requests an attack from an entity
     *
     * @param requestor the requesting entity
     * @param type the weapon type to attack with
     */
    private void attack(EntityId requestor, WeaponType type) {
        if (type.getTypeName(ed).equals(WeaponTypes.BOMB)) {
            this.entityAttackBomb(requestor);
        } else if (type.getTypeName(ed).equals(WeaponTypes.BULLET)) {
            this.entityAttackGuns(requestor);
        } else if (type.getTypeName(ed).equals(WeaponTypes.BURST)) {
            this.entityBurst(requestor);
        } else if (type.getTypeName(ed).equals(WeaponTypes.GRAVITYBOMB)) {
            this.entityAttackGravityBomb(requestor);
        } else if (type.getTypeName(ed).equals(WeaponTypes.MINE)) {
            this.entityPlaceMine(requestor);
        } else if (type.getTypeName(ed).equals(WeaponTypes.THOR)) {
            this.entityAttackThor(requestor);
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
        AttackInfo info = this.attack(new Attack(requestor), WeaponTypes.thor(ed));

        this.attackThor(info, new Damage(-20));

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
        AttackInfo info = this.attack(new Attack(requestor), WeaponTypes.bullet(ed));

        this.attackGuns(info, shipGuns.getLevel(), new Damage(-20));

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
        AttackInfo info = this.attack(new Attack(requestor), WeaponTypes.bomb(ed));
        this.attackBomb(info, shipBombs.getLevel(), new Damage(-20));

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
        AttackInfo info = this.attack(new Attack(requestor), WeaponTypes.mine(ed));
        this.attackBomb(info, shipMines.getLevel(), new Damage(-20));

        //Set new cooldown
        ed.setComponent(requestor, new GunsCooldown(shipMines.getCooldown()));
    }

    /**
     * Checks that an entity can attack with Bursts
     *
     * @param requestor requesting entity
     */
    private void entityBurst(EntityId requestor) {

        throw new UnsupportedOperationException();
        /*
        //Check authorization
        if (!bursts.containsId(requestor)) {
            return;
        }
        //Deduct health
        //Perform attack
        this.attack(new Attack(requestor), WeaponTypes.burst(ed), new Damage(-20));
         */
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
        AttackInfo info = this.attack(new Attack(requestor), WeaponTypes.gravityBomb(ed));
        this.attackGravBomb(info, shipGravityBombs.getLevel(), new Damage(-20));

        //Set new cooldown
        ed.setComponent(requestor, new GunsCooldown(shipGravityBombs.getCooldown()));
    }

    /**
     * Performs an attack
     *
     * @param requestor requesting entity
     */
    private AttackInfo attack(Attack a, WeaponType type) {
        EntityId eId = a.getOwner();
        SimTime localTime = time; //Copy incase someone else is writing to it
        Entity e = ed.getEntity(eId, HitPoints.class);

        Quatd orientation = new Quatd();
        Vec3d location;
        double rotation;
        Vector2 attackVel;

        if (e.get(HitPoints.class) != null) {
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
        } else {
            // towers
            e = ed.getEntity(eId, AttackVelocity.class, Position.class, AttackDirection.class);
            location = e.get(Position.class).getLocation().add(new Vec3d(0, 0, 0));

            Vector2 ori = e.get(AttackDirection.class).getDirection();

            orientation = orientation.fromAngles(ori.getAngleBetween(new Vector2(1, 0)), ori.getAngleBetween(new Vector2(0, 1)), 0);
            rotation = 0;
            // skal angive længde og ikke bare multiply...
            attackVel = ori.multiply(e.get(AttackVelocity.class).getVelocity());
        }

        return new AttackInfo(location, orientation, rotation, attackVel);
    }

    /**
     * Creates a bomb entity
     *
     * @param info the attack information
     * @param level the bomb level
     * @param damage the damage of the bomb
     */
    private void attackBomb(AttackInfo info, BombLevel level, Damage damage) {
        EntityId projectile;
        projectile = CoreGameEntities.createBomb(info.getLocation(), info.getOrientation(), info.getRotation(), info.getAttackVelocity(), CoreGameConstants.BULLETDECAY, ed, level);

        //TODO: Calculate damage based on level:
        ed.setComponent(projectile, damage);

        CoreGameEntities.createBombSound(projectile, ed, level);
    }

    /**
     * Creates a bullet entity
     *
     * @param info the attack information
     * @param level the bullet level
     * @param damage the damage of the bullet
     */
    private void attackGuns(AttackInfo info, GunLevel level, Damage damage) {
        EntityId projectile;
        projectile = CoreGameEntities.createBullet(info.getLocation(), info.getOrientation(), info.getRotation(), info.getAttackVelocity(), CoreGameConstants.BULLETDECAY, ed, level);

        //TODO: Calculate damage based on level:
        ed.setComponent(projectile, damage);

        CoreGameEntities.createBulletSound(projectile, ed, level);
    }

    /**
     * Creates a gravity bomb entity
     *
     * @param info the attack information
     * @param level the bomb level
     * @param damage the damage of the bomb
     */
    private void attackGravBomb(AttackInfo info, BombLevel level, Damage damage) {
        EntityId projectile;
        HashSet<EntityComponent> delayedComponents = new HashSet<>();
        delayedComponents.add(new GravityWell(5, CoreGameConstants.GRAVBOMBWORMHOLEFORCE, GravityWell.PULL));             //Suck everything in
        delayedComponents.add(new PhysicsVelocity(new Vector2(0, 0))); //Freeze the bomb
        delayedComponents.add(WeaponTypes.gravityBomb(ed));
        projectile = CoreGameEntities.createDelayedBomb(info.getLocation(), info.getOrientation(), info.getRotation(), info.getAttackVelocity(), CoreGameConstants.GRAVBOMBDECAY, CoreGameConstants.GRAVBOMBDELAY, delayedComponents, ed, level);
        ed.setComponent(projectile, new Damage(damage.getDamage()));

        CoreGameEntities.createSound(projectile, AudioTypes.FIRE_GRAVBOMB, ed);
    }

    /**
     * Creates a thor entity
     *
     * @param info the attack information
     * @param damage the damage of the thor
     */
    private void attackThor(AttackInfo info, Damage damage) {
        EntityId projectile;
        projectile = CoreGameEntities.createThor(info.getLocation(), info.getOrientation(), info.getRotation(), info.getAttackVelocity(), CoreGameConstants.THORDECAY, ed);
        ed.setComponent(projectile, new Damage(damage.getDamage()));

        CoreGameEntities.createSound(projectile, AudioTypes.FIRE_THOR, ed);
    }

    /**
     * AttackInfo is where attacks originate (location, orientation, rotation
     * and velocity)
     */
    private class AttackInfo {

        private final Vec3d location;
        private final Quatd orientation;
        private final double rotation;
        private final Vector2 attackVelocity;

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

        if (type.getTypeName(ed).equals(WeaponTypes.BOMB)) {
            attackVel.multiply(CoreGameConstants.BOMBPROJECTILESPEED);
            attackVel.add(linearVelocity); //Add ships velocity to account for direction of ship and rotation

        } else if (type.getTypeName(ed).equals(WeaponTypes.BULLET)) {
            attackVel.multiply(CoreGameConstants.BULLETPROJECTILESPEED);
            attackVel.add(linearVelocity); //Add ships velocity to account for direction of ship and rotation

        } else if (type.getTypeName(ed).equals(WeaponTypes.GRAVITYBOMB)) {
            attackVel.multiply(CoreGameConstants.GRAVBOMBPROJECTILESPEED);
            attackVel.add(linearVelocity); //Add ships velocity to account for direction of ship and rotation

        } else if (type.getTypeName(ed).equals(WeaponTypes.MINE)) {
            attackVel.multiply(0); //A mine stands still
        } else if (type.getTypeName(ed).equals(WeaponTypes.THOR)) {
            attackVel.multiply(CoreGameConstants.THORPROJECTILESPEED);
            attackVel.add(linearVelocity); //Add ships velocity to account for direction of ship and rotation

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
        Vector2 attackPos = new Vector2(0, 1);
        attackPos.rotate(rotation);
        attackPos.multiply(CorePhysicsConstants.PROJECTILEOFFSET);
        attackPos.add(translation.x, translation.y);
        return attackPos;
    }

}
