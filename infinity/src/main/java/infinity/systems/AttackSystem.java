/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package infinity.systems;

import com.simsilica.ext.mphys.MPhysSystem;
import com.simsilica.mblock.phys.MBlockShape;
import com.simsilica.mphys.PhysicsSpace;
import infinity.es.ship.Energy;
import infinity.es.ship.weapons.Gun;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.simsilica.es.Entity;
import com.simsilica.es.EntityComponent;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.ext.mphys.BinEntityManager;
import com.simsilica.mathd.Quatd;
import com.simsilica.mathd.Vec3d;
import com.simsilica.mphys.BinIndex;
import com.simsilica.mphys.RigidBody;
import com.simsilica.sim.AbstractGameSystem;
import com.simsilica.sim.SimTime;
import infinity.es.AudioTypes;
import infinity.es.Damage;
import infinity.es.GravityWell;
import infinity.es.ship.actions.Burst;
import infinity.es.ship.actions.Thor;
import infinity.es.ship.weapons.Bomb;
import infinity.es.ship.weapons.BombCost;
import infinity.es.ship.weapons.BombFireDelay;
import infinity.es.ship.weapons.BombLevelEnum;
import infinity.es.ship.weapons.GravityBomb;
import infinity.es.ship.weapons.GravityBombCost;
import infinity.es.ship.weapons.GravityBombFireDelay;
import infinity.es.ship.weapons.GunCost;
import infinity.es.ship.weapons.GunFireDelay;
import infinity.es.ship.weapons.GunLevelEnum;
import infinity.es.ship.weapons.Mine;
import infinity.es.ship.weapons.MineCost;
import infinity.es.ship.weapons.MineFireDelay;
import infinity.sim.CoreGameConstants;
import infinity.sim.CorePhysicsConstants;
import infinity.sim.GameEntities;
import infinity.sim.GameSounds;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author AFahrenholz
 */
public class AttackSystem extends AbstractGameSystem {

    public static final byte GUN = 0x0;
    public static final byte BOMB = 0x1;
    public static final byte GRAVBOMB = 0x2;
    public static final byte MINE = 0x3;
    public static final byte BURST = 0x4;
    public static final byte THOR = 0x5;

    private EntityData ed;
    private MPhysSystem<MBlockShape> physics;
    private PhysicsSpace<EntityId, MBlockShape> space;
    private BinIndex binIndex;
    private BinEntityManager binEntityManager;

    static Logger log = LoggerFactory.getLogger(AttackSystem.class);
    private LinkedHashSet<Attack> sessionAttackCreations = new LinkedHashSet<>();
    private EntitySet thors, mines, gravityBombs, bursts, bombs, guns;

    private SimTime time;
    private EnergySystem health;
    private SettingsSystem settings;

    @Override
    protected void initialize() {
        this.ed = getSystem(EntityData.class);
        if (ed == null) {
            throw new RuntimeException(getClass().getName() + " system requires an EntityData object.");
        }
        this.physics = (MPhysSystem<MBlockShape>) getSystem(MPhysSystem.class);
        if (physics == null) {
            throw new RuntimeException(getClass().getName() + " system requires the MPhysSystem system.");
        }

        this.space = physics.getPhysicsSpace();
        this.binIndex = space.getBinIndex();
        this.binEntityManager = physics.getBinEntityManager();

        health = getSystem(EnergySystem.class);

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
    private void attack(EntityId requestor, byte flag) {
        switch (flag) {
            case AttackSystem.BOMB:
                this.entityAttackBomb(requestor);
                break;
            case AttackSystem.GUN:
                this.entityAttackGuns(requestor);
                break;
            case AttackSystem.BURST:
                this.entityBurst(requestor);
                break;
            case AttackSystem.GRAVBOMB:
                this.entityAttackGravityBomb(requestor);
                break;
            case AttackSystem.MINE:
                this.entityPlaceMine(requestor);
                break;
            case AttackSystem.THOR:
                this.entityAttackThor(requestor);
                break;
            default:
                throw new UnsupportedOperationException("Unsupported weapontype " + Byte.toString(flag) + " in attack");
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
        AttackInfo info = this.getAttackInfo(requestor, AttackSystem.THOR);

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
        //Entity doesnt have guns
        if (entity == null) {
            return;
        }
        Gun shipGuns = entity.get(Gun.class);
        GunCost shipGunCost = entity.get(GunCost.class);
        GunFireDelay shipGunCooldown = entity.get(GunFireDelay.class);

        //Check authorization and check cooldown
        if (!guns.containsId(requestor) || shipGunCooldown.getPercent() < 1.0) {
            return;
        }

        //Check health
        if (!health.hasEnergy(requestor) || health.getHealth(requestor) < shipGunCost.getCost()) {
            return;
        }
        //Deduct health
        health.createHealthChange(requestor, -1 * shipGunCost.getCost());

        //Perform attack
        AttackInfo info = this.getAttackInfo(requestor, AttackSystem.GUN);

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
        if (!health.hasEnergy(requestor) || health.getHealth(requestor) < shipBombCost.getCost()) {
            return;
        }
        //Deduct health
        health.createHealthChange(requestor, -1 * shipBombCost.getCost());

        //Perform attack
        AttackInfo info = this.getAttackInfo(requestor, AttackSystem.BOMB);

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
        if (!health.hasEnergy(requestor) || health.getHealth(requestor) < shipMineCost.getCost()) {
            return;
        }
        //Deduct health
        health.createHealthChange(requestor, -1 * shipMineCost.getCost());

        //Perform attack 
        AttackInfo info = this.getAttackInfo(requestor, AttackSystem.MINE);

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
        Quatd orientation = new Quatd();

        float angle = (360 / CoreGameConstants.BURSTPROJECTILECOUNT) * FastMath.DEG_TO_RAD;

        AttackInfo infoOrig = this.getAttackInfo(requestor, AttackSystem.BURST);
        for (int i = 0; i < CoreGameConstants.BURSTPROJECTILECOUNT; i++) {
            AttackInfo info = infoOrig.clone();
            orientation = orientation.fromAngles(0, angle * (float) i, 0);

            //log.info("Rotating (from original) degrees: "+rotation * FastMath.RAD_TO_DEG);
            //Quaternion newOrientation = info.getOrientation().toQuaternion().fromAngleAxis(rotation, Vector3f.UNIT_Z);
            Vec3d newVelocity = info.getAttackVelocity();

            //Rotate:
            newVelocity = orientation.mult(newVelocity);
            //log.info("Attack velocity: "+newVelocity.toString());

            //log.info("rotated velocity: "+newVelocity.toString());
            //info.setOrientation(new Quatd(newOrientation));
            info.setAttackVelocity(newVelocity);

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
        if (!health.hasEnergy(requestor) || health.getHealth(requestor) < shipGravBombCost.getCost()) {
            return;
        }
        //Deduct health
        health.createHealthChange(requestor, -1 * shipGravBombCost.getCost());

        //Perform attack
        AttackInfo info = this.getAttackInfo(requestor, AttackSystem.GRAVBOMB);

        this.attackGravBomb(info, shipGravityBombs.getLevel(), new Damage(-20), requestor);

        //Set new cooldown
        ed.setComponent(requestor, shipGravBombCooldown.copy());
    }

    /**
     * Creates a bomb entity
     *
     * @param info the attack information
     * @param level the bomb level
     * @param damage the damage of the bomb
     */
    private void attackBomb(AttackInfo info, BombLevelEnum level, Damage damage, EntityId owner) {
        EntityId projectile = GameEntities.createBomb(ed, owner, space, time.getTime(),
                info.getLocation(), info.getAttackVelocity(), CoreGameConstants.BULLETDECAY, level);
        ed.setComponent(projectile, damage);
        GameSounds.createBombSound(ed, owner, space, time.getTime(),
                info.getLocation(), level);
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
        projectile = GameEntities.createBurst(ed, owner, space, time.getTime(), info.getLocation(), info.getAttackVelocity(), CoreGameConstants.BULLETDECAY);
        ed.setComponent(projectile, damage);
        GameSounds.createBurstSound(ed, owner, space, time.getTime(), info.getLocation());
    }

    /**
     * Creates a bullet entity
     *
     * @param info the attack information
     * @param level the bullet level
     * @param damage the damage of the bullet
     */
    private void attackGuns(AttackInfo info, GunLevelEnum level, Damage damage, EntityId owner) {
        
        String shapeName = "bullet_l"+level.level;
        
        EntityId projectile;
        projectile = GameEntities.createBullet(ed, owner, space, time.getTime(), info.getLocation(), info.getAttackVelocity(), CoreGameConstants.BULLETDECAY, level, shapeName);
        ed.setComponent(projectile, damage);
        GameSounds.createBulletSound(ed, owner, space, time.getTime(), info.getLocation(), level);
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
        //delayedComponents.add(new PhysicsVelocity(new Vector2(0, 0))); //Freeze the bomb

        projectile = GameEntities.createDelayedBomb(ed, owner, space, time.getTime(), info.getLocation(), info.getAttackVelocity(),
                CoreGameConstants.GRAVBOMBDECAY, CoreGameConstants.GRAVBOMBDELAY, delayedComponents, level);
        ed.setComponent(projectile, new Damage(damage.getDamage()));

        GameSounds.createSound(ed, owner, space, time.getTime(), info.getLocation(), AudioTypes.FIRE_GRAVBOMB);
    }

    /**
     * Creates a thor entity
     *
     * @param info the attack information
     * @param damage the damage of the thor
     */
    private void attackThor(AttackInfo info, Damage damage, EntityId owner) {
        EntityId projectile;

        projectile = GameEntities.createThor(ed, owner, space, time.getTime(), info.getLocation(), info.getAttackVelocity(), CoreGameConstants.THORDECAY);

        ed.setComponent(projectile, new Damage(damage.getDamage()));

        GameSounds.createSound(ed, owner, space, time.getTime(), info.getLocation(), AudioTypes.FIRE_THOR);
    }

    public class Attack {

        final EntityId owner;
        final byte flag;

        public Attack(EntityId owner, byte flag) {
            this.owner = owner;
            this.flag = flag;
        }

        public EntityId getOwner() {
            return owner;
        }

        public byte getWeaponType() {
            return flag;
        }
    }

    /**
     * AttackInfo is where attacks originate (location, orientation, rotation
     * and velocity)
     */
    private class AttackInfo {

        private Vec3d location;
        private Vec3d attackVelocity;

        public AttackInfo(Vec3d location, Vec3d attackVelocity) {
            this.location = location;
            this.attackVelocity = attackVelocity;
        }

        public Vec3d getLocation() {
            return location;
        }

        public Vec3d getAttackVelocity() {
            return attackVelocity;
        }

        public void setLocation(Vec3d location) {
            this.location = location;
        }

        public void setAttackVelocity(Vec3d attackVelocity) {
            this.attackVelocity = attackVelocity;
        }

        public AttackInfo clone() {
            return new AttackInfo(this.location.clone(), this.attackVelocity.clone());
        }
    }

    /**
     * Find the velocity and the position of the projectile
     *
     * @param requestor requesting entity
     */
    private AttackInfo getAttackInfo(EntityId attacker, byte flag) {
        //Default velocity for projectiles:
        Vec3d projectileVelocity = new Vec3d(0, 0, 1);
        RigidBody shipBody = physics.getPhysicsSpace().getBinIndex().getRigidBody(attacker);

        //Step 1: Scale the velocity based on weapon type, weapon level and ship type
        //TODO: Look these settings up in SettingsSystem
        switch (flag) {
            case AttackSystem.GUN:
                projectileVelocity.addLocal(0,0,50);
                break;
            case AttackSystem.BOMB:
                projectileVelocity.addLocal(0,0,25);
                break;
            case AttackSystem.GRAVBOMB:
                break;
            case AttackSystem.MINE:
                projectileVelocity.multLocal(0);
                break;
            default:
                throw new AssertionError();
        }
        
        //Step 2: Rotate the scaled velocity
        Quatd shipRotation = new Quatd(shipBody.orientation);
        Vec3d shipVelocity = shipBody.getLinearVelocity();
        projectileVelocity = shipRotation.mult(projectileVelocity);
        
        //Step 3: Add ship velocity:
        projectileVelocity.addLocal(shipVelocity);

        //Step 4: Find the translation
        Vec3d shipPosition = new Vec3d(shipBody.position);
        //Default position is at the tip of the ship;
        Vec3d projectilePosition = new Vec3d(0,0, 0);//CorePhysicsConstants.SHIPSIZERADIUS);
        //Offset with the radius of the projectile
        switch (flag) {
            case AttackSystem.GUN:
                projectilePosition.addLocal(0, 0, CorePhysicsConstants.BULLETSIZERADIUS);
                break;
            case AttackSystem.BOMB:
            case AttackSystem.GRAVBOMB:
            case AttackSystem.MINE:
                projectilePosition.addLocal(0, 0, CorePhysicsConstants.BOMBSIZERADIUS);
                break;
            default:
                throw new AssertionError();
        }
        //projectilePosition.addLocal(0, 0, CorePhysicsConstants.SAFETYOFFSET);
        
        //Rotate the projectile position just as the ship is rotated
        projectilePosition = shipRotation.mult(projectilePosition);
        //Translate by ship position
        projectilePosition = projectilePosition.add(shipPosition);

        return new AttackInfo(projectilePosition, projectileVelocity);
    }

    /**
     * Queue up an attack
     *
     * @param attacker the attacking entity
     * @param weaponType the weapon of choice
     */
    public void sessionAttack(EntityId attacker, byte flag) {
        sessionAttackCreations.add(new Attack(attacker, flag));
    }
}
