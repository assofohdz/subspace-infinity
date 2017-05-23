package example.view;

import com.jme3.asset.AssetManager;
import com.jme3.effect.ParticleEmitter;
import com.jme3.effect.ParticleMesh;
import com.jme3.material.Material;
import com.jme3.material.RenderState.BlendMode;
import com.jme3.math.ColorRGBA;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Spatial;
import com.jme3.scene.VertexBuffer;
import com.jme3.scene.shape.Quad;
import com.jme3.texture.Texture;
import com.jme3.texture.plugins.AWTLoader;
import com.simsilica.es.Entity;
import com.simsilica.es.EntityData;
import com.sun.glass.ui.Application;
import example.ConnectionState;
import example.GameConstants;
import example.ViewConstants;
import example.es.ObjectType;
import example.es.ObjectTypes;

/**
 *
 * @author Asser
 */
public class SISpatialFactory implements ModelFactory {

    private AssetManager assets;
    private EntityData ed;

    private EffectFactory ef;

    private ModelViewState state;

    public SISpatialFactory() {
    }

    @Override
    public void setState(ModelViewState state) {
        this.assets = state.getApplication().getAssetManager();
        this.ed = state.getApplication().getStateManager().getState(ConnectionState.class).getEntityData();

        this.assets.registerLoader(AWTLoader.class, "bm2");

        this.state = state;

    }

    @Override
    public Spatial createModel(Entity e) {
        ObjectType type = e.get(ObjectType.class);
        if (ObjectTypes.SHIP.equals(type.getTypeName(ed))) {
            //Right now, only the shark is supported
            return createShip();
        } else if (ObjectTypes.THRUST.equals(type.getTypeName(ed))) {
            //Create a particle emitter:
            return createParticleEmitter(e);
        } else if (ObjectTypes.BULLET.equals(type.getTypeName(ed))) {
            //Create bullet
            return createBullet(e);
        } else if (ObjectTypes.BOMB.equals(type.getTypeName(ed))) {
            //Create bomb
            return createBomb(e);
        } else if (ObjectTypes.EXPLOSION.equals(type.getTypeName(ed))) {
            //Create explosion
            return createExplosion(e);
        } else if (ObjectTypes.BOUNTY.equals(type.getTypeName(ed))) {
            //Create bounty
            return createBounty(e);
        } else if (ObjectTypes.ARENA.equals(type.getTypeName(ed))) {
            return createArena(e);
        } else if (ObjectTypes.MAPTILE.equals(type.getTypeName(ed))) {
            return createMapTile(e);
        } else if (ObjectTypes.EXPLOSION2.equals(type.getTypeName(ed))) {
            return createExplosion2(e);
        } else if (ObjectTypes.OVER5.equals(type.getTypeName(ed))) {
            return createOver5(e);
        } else if (ObjectTypes.WORMHOLE.equals(type.getTypeName(ed))) {
            return createWormhole(e);
        } else {
            throw new RuntimeException("Unknown spatial type:" + type.getTypeName(ed));
        }
    }

    private Spatial createShip() {
        Quad quad = new Quad(ViewConstants.SHIPSIZE, ViewConstants.SHIPSIZE);
        //<-- Move into the material?
        float halfSize = ViewConstants.SHIPSIZE * 0.5f;
        quad.setBuffer(VertexBuffer.Type.Position, 3, new float[]{-halfSize, -halfSize, 0,
            halfSize, -halfSize, 0,
            halfSize, halfSize, 0,
            -halfSize, halfSize, 0
        });
        //-->
        quad.updateBound();
        Geometry geom = new Geometry("Ship", quad);
        Material mat = assets.loadMaterial("Materials/ShipMaterial.j3m");
        geom.setMaterial(mat);
        geom.setQueueBucket(RenderQueue.Bucket.Transparent);

        return geom;
    }

    private Spatial createParticleEmitter(Entity e) {
        Spatial result = null;
        ParticleEmitter particleEmitter = new ParticleEmitter("Emitter", ParticleMesh.Type.Triangle, 30); //will be overriden in switch
        switch (e.get((ObjectType.class)).getTypeName(ed)) {
            //Create a thrust particle emitter
            case ObjectTypes.THRUST:
                result = createThrustEmitter(particleEmitter, e);
        }
        return result;
    }

    private Spatial createThrustEmitter(ParticleEmitter thrustEffect, Entity e) {

        Material smokeMat = new Material(assets, "Common/MatDefs/Misc/Particle.j3md");
        smokeMat.setTexture("Texture", assets.loadTexture("Effects/Smoke/Smoke.png"));

        thrustEffect = new ParticleEmitter("Emitter", ParticleMesh.Type.Triangle, 250);
        thrustEffect.setGravity(0, 0, 0);
        thrustEffect.setMaterial(smokeMat);
        thrustEffect.setImagesX(15);
        thrustEffect.setImagesY(1); // 2x
        thrustEffect.setEndColor(ColorRGBA.Black);
        thrustEffect.setStartColor(ColorRGBA.Orange);
        thrustEffect.setStartSize(0.1f);
        thrustEffect.setEndSize(0f);
        thrustEffect.setHighLife(0.25f); //Fits the decay
        thrustEffect.setLowLife(0.1f);
        thrustEffect.setNumParticles(1);

        return thrustEffect;
    }

    private Spatial createBomb(Entity e) {
        Quad quad = new Quad(ViewConstants.BOMBSIZE, ViewConstants.BOMBSIZE);
        //<-- Move into the material?
        float halfSize = ViewConstants.BOMBSIZE * 0.5f;
        quad.setBuffer(VertexBuffer.Type.Position, 3, new float[]{-halfSize, -halfSize, 0,
            halfSize, -halfSize, 0,
            halfSize, halfSize, 0,
            -halfSize, halfSize, 0
        });
        //-->
        quad.updateBound();
        Geometry geom = new Geometry("Bomb", quad);
        Material mat = assets.loadMaterial("Materials/BombMaterial.j3m");
        geom.setMaterial(mat);
        geom.setQueueBucket(RenderQueue.Bucket.Transparent);
        return geom;
    }

    private Spatial createBullet(Entity e) {
        Quad quad = new Quad(ViewConstants.BULLETSIZE, ViewConstants.BULLETSIZE);
        //<-- Move into the material?
        float halfSize = ViewConstants.BULLETSIZE * 0.5f;
        quad.setBuffer(VertexBuffer.Type.Position, 3, new float[]{-halfSize, -halfSize, 0,
            halfSize, -halfSize, 0,
            halfSize, halfSize, 0,
            -halfSize, halfSize, 0
        });
        //-->
        quad.updateBound();
        Geometry geom = new Geometry("Bullet", quad);
        Material mat = assets.loadMaterial("Materials/BulletMaterial.j3m");
        geom.setMaterial(mat);
        geom.setQueueBucket(RenderQueue.Bucket.Transparent);
        return geom;
    }

    private Spatial createExplosion(Entity e) {

        return ef.createExplosion();

//        /** Explosion effect. Uses Texture from jme3-test-data library! */ 
//        Material debrisMat = new Material(assets, "Common/MatDefs/Misc/Particle.j3md");
//        debrisMat.setTexture("Texture", assets.loadTexture("Effects/Explosion/Debris.png"));
//        
//        ParticleEmitter debrisEffect = new ParticleEmitter("Debris", ParticleMesh.Type.Triangle, 10);
//        debrisEffect.setMaterial(debrisMat);
//        debrisEffect.setImagesX(3); debrisEffect.setImagesY(3); // 3x3 texture animation
//        debrisEffect.setRotateSpeed(4);
//        debrisEffect.setSelectRandomImage(true);
//        debrisEffect.getParticleInfluencer().setInitialVelocity(new Vector3f(0, 4, 0));
//        debrisEffect.setStartColor(new ColorRGBA(1f, 1f, 1f, 1f));
//        debrisEffect.setGravity(0f,6f,0f);
//        debrisEffect.getParticleInfluencer().setVelocityVariation(.60f);
//        //debrisEffect.setNumParticles(1);
//        return debrisEffect;
    }

    private Spatial createBounty(Entity e) {
        Quad quad = new Quad(ViewConstants.BOUNTYSIZE, ViewConstants.BOUNTYSIZE);
        //<-- Move into the material?
        float halfSize = ViewConstants.BOUNTYSIZE * 0.5f;
        quad.setBuffer(VertexBuffer.Type.Position, 3, new float[]{-halfSize, -halfSize, 0,
            halfSize, -halfSize, 0,
            halfSize, halfSize, 0,
            -halfSize, halfSize, 0
        });
        //-->
        quad.updateBound();
        Geometry geom = new Geometry("Bounty", quad);
        Material mat = assets.loadMaterial("Materials/BountyMaterial.j3m");
        mat.setFloat("StartTime", state.getApplication().getTimer().getTimeInSeconds());
        geom.setMaterial(mat);
        geom.setQueueBucket(RenderQueue.Bucket.Transparent);
        return geom;
    }

    private Spatial createArena(Entity e) {
        Quad quad = new Quad(ViewConstants.ARENASIZE, ViewConstants.ARENASIZE);
        //<-- Move into the material?
        float halfSize = ViewConstants.ARENASIZE * 0.5f;
        quad.setBuffer(VertexBuffer.Type.Position, 3, new float[]{-halfSize, -halfSize, 0,
            halfSize, -halfSize, 0,
            halfSize, halfSize, 0,
            -halfSize, halfSize, 0
        });
        //-->
        quad.updateBound();
        Geometry geom = new Geometry("Arena", quad);
        geom.setCullHint(Spatial.CullHint.Always);
        Material mat = new Material(assets, "Common/MatDefs/Misc/Unshaded.j3md");

        geom.setMaterial(mat);

        return geom;
    }

    private Spatial createMapTile(Entity e) {
        //SquareShape s = e.get(SquareShape.class);

        Quad quad = new Quad(ViewConstants.MAPTILESIZE, ViewConstants.MAPTILESIZE);
        //<-- Move into the material?

        float halfSize = ViewConstants.MAPTILESIZE * 0.5f;
        quad.setBuffer(VertexBuffer.Type.Position, 3, new float[]{-halfSize, -halfSize, 0,
            halfSize, -halfSize, 0,
            halfSize, halfSize, 0,
            -halfSize, halfSize, 0
        });

        //-->
        quad.updateBound();
        Geometry geom = new Geometry("Arena", quad);

        Material mat = new Material(assets, "Common/MatDefs/Misc/Unshaded.j3md");
        mat.setColor("Color", ColorRGBA.Yellow);
        geom.setMaterial(mat);

        return geom;
    }

    private Spatial createExplosion2(Entity e) {
        Quad quad = new Quad(ViewConstants.EXPLOSION2SIZE, ViewConstants.EXPLOSION2SIZE);
        //<-- Move into the material?
        float halfSize = ViewConstants.EXPLOSION2SIZE * 0.5f;
        quad.setBuffer(VertexBuffer.Type.Position, 3, new float[]{-halfSize, -halfSize, 0,
            halfSize, -halfSize, 0,
            halfSize, halfSize, 0,
            -halfSize, halfSize, 0
        });
        //-->
        quad.updateBound();
        Geometry geom = new Geometry("Bomb", quad);
        Material mat = assets.loadMaterial("Materials/Explode2Material.j3m");
        mat.setFloat("StartTime", state.getApplication().getTimer().getTimeInSeconds());
        geom.setMaterial(mat);
        geom.setQueueBucket(RenderQueue.Bucket.Transparent);
        return geom;
    }

    private Spatial createOver5(Entity e) {

        Quad quadOver5 = new Quad(ViewConstants.OVER5SIZE, ViewConstants.OVER5SIZE);
        //<-- Move into the material?
        float halfSizeOver5 = ViewConstants.OVER5SIZE * 0.5f;
        quadOver5.setBuffer(VertexBuffer.Type.Position, 3, new float[]{-halfSizeOver5, -halfSizeOver5, 0,
            halfSizeOver5, -halfSizeOver5, 0,
            halfSizeOver5, halfSizeOver5, 0,
            -halfSizeOver5, halfSizeOver5, 0
        });
        //-->
        quadOver5.updateBound();
        Geometry geomOver5 = new Geometry("Wormhole", quadOver5);
        Material matOver5 = assets.loadMaterial("Materials/Over5Material.j3m");
        matOver5.setFloat("StartTime", state.getApplication().getTimer().getTimeInSeconds());
        geomOver5.setMaterial(matOver5);
        geomOver5.setQueueBucket(RenderQueue.Bucket.Transparent);
        return geomOver5;

    }

    private Spatial createWormhole(Entity e) {
        Quad quad = new Quad(ViewConstants.WORMHOLESIZE, ViewConstants.WORMHOLESIZE);
        //<-- Move into the material?
        float halfSize = ViewConstants.WORMHOLESIZE * 0.5f;
        quad.setBuffer(VertexBuffer.Type.Position, 3, new float[]{-halfSize, -halfSize, 0,
            halfSize, -halfSize, 0,
            halfSize, halfSize, 0,
            -halfSize, halfSize, 0
        });
        //-->
        quad.updateBound();
        Geometry geom = new Geometry("Wormhole", quad);
        Material mat = assets.loadMaterial("Materials/WormholeMaterial.j3m");
        mat.setFloat("StartTime", state.getApplication().getTimer().getTimeInSeconds());
        geom.setMaterial(mat);
        geom.setQueueBucket(RenderQueue.Bucket.Transparent);
        return geom;
    }
}
