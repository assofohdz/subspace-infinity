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
package infinity.client.states;

import infinity.client.view.EffectFactory;
import infinity.client.view.ShipLightControl;
import infinity.es.Flag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jme3.asset.AssetManager;
import com.jme3.effect.ParticleEmitter;
import com.jme3.effect.ParticleMesh;
import com.jme3.light.PointLight;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.VertexBuffer;
import com.jme3.scene.shape.Quad;
import com.jme3.system.Timer;
import com.jme3.util.BufferUtils;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.ext.mphys.Mass;
import com.simsilica.ext.mphys.ShapeInfo;
import com.simsilica.mblock.phys.MBlockShape;

import infinity.es.ShapeNames;
import infinity.Bombs;
import infinity.sim.CoreViewConstants;

/**
 *
 * @author Asser
 */
public class SIRadarSpatialFactory {

    static Logger log = LoggerFactory.getLogger(SIRadarSpatialFactory.class);
    private final AssetManager assets;

    private EffectFactory ef;
    private Timer timer;
    // private MapState mapState;
    // private ModelViewState state;

    // Use to flip between using the lights and using unshaded textures
    private final boolean unshaded = true;
    private final EntityData ed;
    private final Node rootNode;

    SIRadarSpatialFactory(final EntityData ed, final Node rootNode, final AssetManager assets) {
        this.ed = ed;
        // this.mapState = app.getStateManager().getState(MapState.class);
        // this.state = app.getStateManager().getState(ModelViewState.class);
        this.rootNode = rootNode;
        this.assets = assets;
    }

    Spatial createModel(@SuppressWarnings("unused") final EntityId id,
            @SuppressWarnings("unused") final MBlockShape shape, final ShapeInfo shapeInfo,
            @SuppressWarnings("unused") final Mass mass) {
        final String shapeName = shapeInfo.getShapeName(ed);

        if (shapeName == null || shapeName == "") {
            throw new NullPointerException("Model shapeInfo name cannot be null or empty");
        }

        final Spatial s = this.createModel(shapeName);

        return s;
    }

    public Spatial createModel(final String shapeName) {

        switch (shapeName) {
        // case "thrust":
        // return createParticleEmitter(eId, shapeName);
        case ShapeNames.BULLETL1:
            return createBullet(1);
        case ShapeNames.BOMBL1:
            // Create bomb
            return createBomb(Bombs.BOMB_1);
        case ShapeNames.BOMBL2:
            // Create bomb
            return createBomb(Bombs.BOMB_2);
        case ShapeNames.BOMBL3:
            // Create bomb
            return createBomb(Bombs.BOMB_3);
        case ShapeNames.BOMBL4:
            // Create bomb
            return createBomb(Bombs.BOMB_4);
        case ShapeNames.THOR:
            // Create bomb
            return createBomb(Bombs.THOR);
        case ShapeNames.BURST:
            // Create bomb
            return createBurst();
        case ShapeNames.EXPLOSION:
            // Create explosion
            return createExplosion();
        case ShapeNames.PRIZE:
            // Create bounty
            return createBounty();
        case ShapeNames.ARENA:
            return createArena();
        /*
         * case ShapeNames.MAPTILE: return createMapTile(eId);
         */
        case ShapeNames.EXPLOSION2:
            return createExplosion2();
        case ShapeNames.OVER5:
            return createOver5();
        case ShapeNames.WORMHOLE:
            return createWormhole();
        case ShapeNames.OVER1:
            return createOver1();
        case ShapeNames.WARP:
            return createWarp();
        case ShapeNames.REPEL:
            return createRepel();
        case ShapeNames.OVER2:
            return createOver2();
        case ShapeNames.SHIP_WARBIRD:
            return createShip(31);
        case ShapeNames.SHIP_JAVELIN:
            return createShip(27);
        case ShapeNames.SHIP_SPIDER:
            return createShip(23);
        case ShapeNames.SHIP_LEVI:
            return createShip(19);
        case ShapeNames.SHIP_TERRIER:
            return createShip(15);
        case ShapeNames.SHIP_WEASEL:
            return createShip(11);
        case ShapeNames.SHIP_LANCASTER:
            return createShip(7);
        case ShapeNames.SHIP_SHARK:
            return createShip(3);
        case ShapeNames.FLAG:
            return createFlag(Flag.FLAG_THEIRS);
        /*
         * case "mob": return createMob(); case "tower": return createTower(); case
         * "base": return createBase();
         */
        default:
            return null;

        }
    }

    @SuppressWarnings("unused")
    private Spatial createBase() {
        final Quad quad = new Quad(CoreViewConstants.BASESIZE, CoreViewConstants.BASESIZE);
        final float halfSize = CoreViewConstants.BASESIZE * 0.5f;
        quad.setBuffer(VertexBuffer.Type.Position, 3, getVertices(halfSize));
        quad.setBuffer(VertexBuffer.Type.Normal, 3, BufferUtils.createFloatBuffer(getNormals()));
        quad.updateBound();
        final Geometry geom = new Geometry("Base", quad);

        if (unshaded) {
            geom.setMaterial(assets.loadMaterial("Materials/BaseMaterialUnshaded.j3m"));
        } else {
            geom.setMaterial(assets.loadMaterial("Materials/BaseMaterialLight.j3m"));
        }

        geom.setQueueBucket(RenderQueue.Bucket.Transparent);
        return geom;
    }

    @SuppressWarnings("unused")
    private Spatial createMob() {
        final Quad quad = new Quad(CoreViewConstants.MOBSIZE, CoreViewConstants.MOBSIZE);
        final float halfSize = CoreViewConstants.MOBSIZE * 0.5f;
        quad.setBuffer(VertexBuffer.Type.Position, 3, getVertices(halfSize));
        quad.setBuffer(VertexBuffer.Type.Normal, 3, BufferUtils.createFloatBuffer(getNormals()));
        quad.updateBound();
        final Geometry geom = new Geometry("Mob", quad);

        if (unshaded) {
            geom.setMaterial(assets.loadMaterial("Materials/MobMaterialUnshaded.j3m"));
        } else {
            geom.setMaterial(assets.loadMaterial("Materials/MobMaterialLight.j3m"));
        }

        geom.setQueueBucket(RenderQueue.Bucket.Transparent);
        return geom;
    }

    @SuppressWarnings("unused")
    private Spatial createTower() {
        final Quad quad = new Quad(CoreViewConstants.TOWERSIZE, CoreViewConstants.TOWERSIZE);
        final float halfSize = CoreViewConstants.TOWERSIZE * 0.5f;
        quad.setBuffer(VertexBuffer.Type.Position, 3, getVertices(halfSize));
        quad.setBuffer(VertexBuffer.Type.Normal, 3, BufferUtils.createFloatBuffer(getNormals()));
        quad.updateBound();
        final Geometry geom = new Geometry("Tower", quad);

        if (unshaded) {
            geom.setMaterial(assets.loadMaterial("Materials/TowerMaterialUnshaded.j3m"));
        } else {
            geom.setMaterial(assets.loadMaterial("Materials/TowerMaterialLight.j3m"));
        }

        geom.setQueueBucket(RenderQueue.Bucket.Transparent);
        return geom;
    }

    private Spatial createFlag(final int flag) {
        final Quad quad = new Quad(CoreViewConstants.FLAGSIZE, CoreViewConstants.FLAGSIZE);
        final float halfSize = CoreViewConstants.FLAGSIZE * 0.5f;
        quad.setBuffer(VertexBuffer.Type.Position, 3, getVertices(halfSize));
        quad.setBuffer(VertexBuffer.Type.Normal, 3, BufferUtils.createFloatBuffer(getNormals()));
        quad.updateBound();
        final Geometry geom = new Geometry("Flag", quad);

        if (unshaded) {
            geom.setMaterial(assets.loadMaterial("Materials/FlagMaterialUnshaded.j3m"));
        } else {
            geom.setMaterial(assets.loadMaterial("Materials/FlagMaterialLight.j3m"));
        }
        // mat.setInt("numTilesOffsetY", flag);
        setFlagMaterialVariables(geom, flag);
        geom.setQueueBucket(RenderQueue.Bucket.Transparent);

        return geom;
    }

    public void setFlagMaterialVariables(final Spatial s, final int flag) {
        Geometry geom;
        if (s instanceof Geometry) {
            geom = (Geometry) s;
        } else {
            geom = (Geometry) ((Node) s).getChild("Flag");
        }

        final Material mat = geom.getMaterial();
        mat.setInt("numTilesOffsetY", flag);
        geom.setMaterial(mat);
    }

    private Spatial createShip(final int ship) {
        final Quad quad = new Quad(CoreViewConstants.SHIPSIZE, CoreViewConstants.SHIPSIZE);
        final float halfSize = CoreViewConstants.SHIPSIZE * 0.5f;
        quad.setBuffer(VertexBuffer.Type.Position, 3, BufferUtils.createFloatBuffer(getVertices(halfSize)));
        quad.setBuffer(VertexBuffer.Type.Normal, 3, BufferUtils.createFloatBuffer(getNormals()));
        quad.updateBound();
        final Geometry geom = new Geometry("Ship", quad);

        if (unshaded) {
            geom.setMaterial(assets.loadMaterial("Materials/ShipMaterialUnshaded.j3m"));
        } else {
            geom.setMaterial(assets.loadMaterial("Materials/ShipMaterialLight.j3m"));
        }

        setShipMaterialVariables(geom, ship);

        geom.setQueueBucket(RenderQueue.Bucket.Transparent);

        final PointLight myLight = new PointLight();
        myLight.setColor(ColorRGBA.White);
        myLight.setRadius(20);
        rootNode.addLight(myLight);
        final ShipLightControl lightControl = new ShipLightControl(myLight);
        geom.addControl(lightControl);

        return geom;
    }

    public void setShipMaterialVariables(final Spatial s, final int ship) {
        Geometry geom;
        if (s instanceof Geometry) {
            geom = (Geometry) s; // From createShip
        } else {
            geom = (Geometry) ((Node) s).getChild("Ship"); // From ModelViewState
        }
        final Material mat = geom.getMaterial();
        mat.setInt("numTilesOffsetY", ship);
        geom.setMaterial(mat);
        log.info("Setting geometry material on spatial:" + s + "; ship:" + ship);
    }

    @SuppressWarnings("unused")
    private Spatial createParticleEmitter(final EntityId eId, final String shapeName) {
        final Spatial result;

        switch (shapeName) {
        // Create a thrust ParticleEmitter
        case "thrust":
            result = createThrustEmitter();
            break;
        default:
            result = null;
            break;
        }
        return result;
    }

    private Spatial createThrustEmitter() {
        final Material smokeMat = new Material(assets, "Common/MatDefs/Misc/Particle.j3md");
        smokeMat.setTexture("Texture", assets.loadTexture("Effects/Smoke/Smoke.png"));
        final ParticleEmitter result = new ParticleEmitter("Emitter", ParticleMesh.Type.Triangle, 250);
        result.setGravity(0, 0, 0);
        result.setMaterial(smokeMat);
        result.setImagesX(15);
        result.setImagesY(1); // 2x
        result.setEndColor(ColorRGBA.Black);
        result.setStartColor(ColorRGBA.Orange);
        result.setStartSize(0.1f);
        result.setEndSize(0f);
        result.setHighLife(0.25f); // Fits the decay
        result.setLowLife(0.1f);
        result.setNumParticles(1);
        return result;
    }

    private Spatial createBomb(final Bombs level) {
        final Quad quad = new Quad(CoreViewConstants.BOMBSIZE, CoreViewConstants.BOMBSIZE);
        final float halfSize = CoreViewConstants.BOMBSIZE * 0.5f;
        quad.setBuffer(VertexBuffer.Type.Position, 3, getVertices(halfSize));
        quad.setBuffer(VertexBuffer.Type.Normal, 3, BufferUtils.createFloatBuffer(getNormals()));
        quad.updateBound();
        final Geometry geom = new Geometry("Bomb", quad);

        if (unshaded) {
            geom.setMaterial(assets.loadMaterial("Materials/BombMaterialUnshaded.j3m"));
        } else {
            geom.setMaterial(assets.loadMaterial("Materials/BombMaterialLight.j3m"));
        }
        geom.getMaterial().setInt("numTilesOffsetY", level.viewOffset);

        geom.setQueueBucket(RenderQueue.Bucket.Transparent);
        return geom;
    }

    private Spatial createBullet(final int offSet) {
        final Quad quad = new Quad(CoreViewConstants.BULLETSIZE, CoreViewConstants.BULLETSIZE);
        final float halfSize = CoreViewConstants.BULLETSIZE * 0.5f;
        quad.setBuffer(VertexBuffer.Type.Position, 3, getVertices(halfSize));
        quad.setBuffer(VertexBuffer.Type.Normal, 3, BufferUtils.createFloatBuffer(getNormals()));
        quad.updateBound();
        final Geometry geom = new Geometry("Bullet", quad);

        if (unshaded) {
            geom.setMaterial(assets.loadMaterial("Materials/BulletMaterialUnshaded.j3m"));
        } else {
            geom.setMaterial(assets.loadMaterial("Materials/BulletMaterialLight.j3m"));
        }

        geom.getMaterial().setInt("numTilesOffsetY", offSet);
        geom.setQueueBucket(RenderQueue.Bucket.Transparent);
        return geom;
    }

    private Spatial createExplosion() {

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

    private Spatial createBounty() {
        final Quad quad = new Quad(CoreViewConstants.PRIZESIZE, CoreViewConstants.PRIZESIZE);
        final float halfSize = CoreViewConstants.PRIZESIZE * 0.5f;
        quad.setBuffer(VertexBuffer.Type.Position, 3, getVertices(halfSize));
        quad.setBuffer(VertexBuffer.Type.Normal, 3, BufferUtils.createFloatBuffer(getNormals()));
        quad.updateBound();
        final Geometry geom = new Geometry("Bounty", quad);

        if (unshaded) {
            geom.setMaterial(assets.loadMaterial("Materials/BountyMaterialUnshaded.j3m"));
        } else {
            geom.setMaterial(assets.loadMaterial("Materials/BountyMaterialLight.j3m"));
        }

        geom.getMaterial().setFloat("StartTime", timer.getTimeInSeconds());
        geom.setQueueBucket(RenderQueue.Bucket.Transparent);
        return geom;
    }

    private Spatial createArena() {
        final Quad quad = new Quad(CoreViewConstants.ARENASIZE, CoreViewConstants.ARENASIZE);
        final float halfSize = CoreViewConstants.ARENASIZE * 0.5f;
        quad.setBuffer(VertexBuffer.Type.Position, 3, getVertices(halfSize));
        quad.setBuffer(VertexBuffer.Type.Normal, 3, BufferUtils.createFloatBuffer(getNormals()));
        quad.updateBound();

        final Geometry geom = new Geometry("Arena", quad);
        geom.setCullHint(Spatial.CullHint.Always);
        geom.setQueueBucket(RenderQueue.Bucket.Transparent);

        final Material mat = new Material(assets, "Common/MatDefs/Misc/Unshaded.j3md");
        // mat.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
        mat.setTransparent(true);
        geom.setMaterial(mat);

        geom.setUserData("arena", Boolean.TRUE);

        return geom;
    }

    /*
     * private Spatial createMapTile(EntityId eId) { Quad quad = new
     * Quad(CoreViewConstants.MAPTILESIZE, CoreViewConstants.MAPTILESIZE); float
     * halfSize = CoreViewConstants.MAPTILESIZE * 0.5f;
     * quad.setBuffer(VertexBuffer.Type.Position, 3, this.getVertices(halfSize));
     * quad.setBuffer(VertexBuffer.Type.Normal, 3,
     * BufferUtils.createFloatBuffer(getNormals())); quad.updateBound();
     *
     * Geometry geom = new Geometry("MapTile", quad);
     *
     * if (state.getType(eId).getTypeName(ed).equals(TileTypes.LEGACY)) { Material
     * mat = new Material(assets, "MatDefs/BlackTransparentShader.j3md");
     *
     * Image image = mapState.getImage(eId);
     *
     * if (image == null) { image = mapState.forceLoadImage(eId); }
     *
     * Texture2D tex2D = new Texture2D(image);
     *
     * //image.dispose();
     *
     * mat.setTexture("ColorMap", tex2D); geom.setMaterial(mat); } else { TileType
     * tileType = state.getType(eId);
     *
     * //TODO: Do some caching and re-use materials/shaders Material mat =
     * assets.loadMaterial("Materials/WangBlobLight.j3m"); //tileType.getTileSet()
     *
     * mat.setTransparent(false);
     *
     *
     * geom.setMaterial(mat); geom.setQueueBucket(RenderQueue.Bucket.Opaque);
     * this.updateWangBlobTile(geom, tileType); }
     *
     * return geom; }
     */
    private Spatial createExplosion2() {
        final Quad quad = new Quad(CoreViewConstants.EXPLOSION2SIZE, CoreViewConstants.EXPLOSION2SIZE);
        final float halfSize = CoreViewConstants.EXPLOSION2SIZE * 0.5f;
        quad.setBuffer(VertexBuffer.Type.Position, 3, getVertices(halfSize));
        quad.setBuffer(VertexBuffer.Type.Normal, 3, BufferUtils.createFloatBuffer(getNormals()));
        quad.updateBound();
        final Geometry geom = new Geometry("Bomb", quad);

        if (unshaded) {
            geom.setMaterial(assets.loadMaterial("Materials/Explode2MaterialUnshaded.j3m"));
        } else {
            geom.setMaterial(assets.loadMaterial("Materials/Explode2MaterialLight.j3m"));
        }

        geom.getMaterial().setFloat("StartTime", timer.getTimeInSeconds());

        geom.setQueueBucket(RenderQueue.Bucket.Transparent);
        return geom;
    }

    private Spatial createOver5() {
        final Quad quadOver5 = new Quad(CoreViewConstants.OVER5SIZE, CoreViewConstants.OVER5SIZE);
        final float halfSizeOver5 = CoreViewConstants.OVER5SIZE * 0.5f;
        quadOver5.setBuffer(VertexBuffer.Type.Position, 3, getVertices(halfSizeOver5));
        quadOver5.setBuffer(VertexBuffer.Type.Normal, 3, BufferUtils.createFloatBuffer(getNormals()));
        quadOver5.updateBound();
        final Geometry geomOver5 = new Geometry("Wormhole", quadOver5);

        if (unshaded) {
            geomOver5.setMaterial(assets.loadMaterial("Materials/Over5MaterialUnshaded.j3m"));
        } else {
            geomOver5.setMaterial(assets.loadMaterial("Materials/Over5MaterialLight.j3m"));
        }

        geomOver5.getMaterial().setFloat("StartTime", timer.getTimeInSeconds());
        geomOver5.setQueueBucket(RenderQueue.Bucket.Transparent);
        return geomOver5;

    }

    private Spatial createWormhole() {
        final Quad quad = new Quad(CoreViewConstants.WORMHOLESIZE, CoreViewConstants.WORMHOLESIZE);
        // <-- Move into the material?
        final float halfSize = CoreViewConstants.WORMHOLESIZE * 0.5f;
        quad.setBuffer(VertexBuffer.Type.Position, 3, getVertices(halfSize));
        quad.setBuffer(VertexBuffer.Type.Normal, 3, BufferUtils.createFloatBuffer(getNormals()));
        // -->
        quad.updateBound();
        final Geometry geom = new Geometry("Wormhole", quad);

        if (unshaded) {
            geom.setMaterial(assets.loadMaterial("Materials/WormholeMaterialUnshaded.j3m"));
        } else {
            geom.setMaterial(assets.loadMaterial("Materials/WormholeMaterialLight.j3m"));
        }

        geom.getMaterial().setFloat("StartTime", timer.getTimeInSeconds());
        geom.setQueueBucket(RenderQueue.Bucket.Transparent);
        return geom;
    }

    private Spatial createOver1() {
        final Quad quad = new Quad(CoreViewConstants.OVER1SIZE, CoreViewConstants.OVER1SIZE);
        final float halfSize = CoreViewConstants.OVER1SIZE * 0.5f;
        quad.setBuffer(VertexBuffer.Type.Position, 3, getVertices(halfSize));
        quad.setBuffer(VertexBuffer.Type.Normal, 3, BufferUtils.createFloatBuffer(getNormals()));
        quad.updateBound();
        final Geometry geom = new Geometry("Over1", quad);

        if (unshaded) {
            geom.setMaterial(assets.loadMaterial("Materials/Over1MaterialUnshaded.j3m"));
        } else {
            geom.setMaterial(assets.loadMaterial("Materials/Over1MaterialLight.j3m"));
        }

        geom.getMaterial().setFloat("StartTime", timer.getTimeInSeconds());
        geom.setQueueBucket(RenderQueue.Bucket.Transparent);
        return geom;
    }

    private Spatial createOver2() {
        final Quad quad = new Quad(CoreViewConstants.OVER2SIZE, CoreViewConstants.OVER2SIZE);
        final float halfSize = CoreViewConstants.OVER2SIZE * 0.5f;
        quad.setBuffer(VertexBuffer.Type.Position, 3, getVertices(halfSize));
        quad.setBuffer(VertexBuffer.Type.Normal, 3, BufferUtils.createFloatBuffer(getNormals()));
        quad.updateBound();
        final Geometry geom = new Geometry("Over2", quad);

        if (unshaded) {
            geom.setMaterial(assets.loadMaterial("Materials/Over2MaterialUnshaded.j3m"));
        } else {
            geom.setMaterial(assets.loadMaterial("Materials/Over2MaterialLight.j3m"));
        }

        geom.getMaterial().setFloat("StartTime", timer.getTimeInSeconds());
        geom.setQueueBucket(RenderQueue.Bucket.Transparent);
        return geom;
    }

    private Spatial createWarp() {
        final Quad quad = new Quad(CoreViewConstants.WARPSIZE, CoreViewConstants.WARPSIZE);
        final float halfSize = CoreViewConstants.WARPSIZE * 0.5f;
        quad.setBuffer(VertexBuffer.Type.Position, 3, getVertices(halfSize));
        quad.setBuffer(VertexBuffer.Type.Normal, 3, BufferUtils.createFloatBuffer(getNormals()));
        quad.updateBound();
        final Geometry geom = new Geometry("Warp", quad);

        if (unshaded) {
            geom.setMaterial(assets.loadMaterial("Materials/WarpMaterialUnshaded.j3m"));
        } else {
            geom.setMaterial(assets.loadMaterial("Materials/WarpMaterialLight.j3m"));
        }

        geom.getMaterial().setFloat("StartTime", timer.getTimeInSeconds());
        geom.setQueueBucket(RenderQueue.Bucket.Transparent);
        return geom;
    }

    private Spatial createRepel() {
        final Quad quad = new Quad(CoreViewConstants.REPELSIZE, CoreViewConstants.REPELSIZE);
        final float halfSize = CoreViewConstants.REPELSIZE * 0.5f;
        quad.setBuffer(VertexBuffer.Type.Position, 3, getVertices(halfSize));
        quad.setBuffer(VertexBuffer.Type.Normal, 3, BufferUtils.createFloatBuffer(getNormals()));
        quad.updateBound();
        final Geometry geom = new Geometry("Repel", quad);

        if (unshaded) {
            geom.setMaterial(assets.loadMaterial("Materials/RepelMaterialUnshaded.j3m"));
        } else {
            geom.setMaterial(assets.loadMaterial("Materials/RepelMaterialLight.j3m"));
        }

        geom.getMaterial().setFloat("StartTime", timer.getTimeInSeconds());
        geom.setQueueBucket(RenderQueue.Bucket.Transparent);
        return geom;
    }

    /*
     * public void updateWangBlobTile(Spatial s, TileType tileType) { Geometry geom;
     * if (s instanceof Geometry) { geom = (Geometry) s; } else { geom = (Geometry)
     * ((Node) s).getChild("MapTile"); //From ModelViewState } Material mat =
     * geom.getMaterial(); //Offset tile mat.setInt("numTilesOffsetX",
     * mapState.getWangBlobTileNumber(tileType.getTileIndex()));
     *
     * //Rotate tile Quaternion rot = new Quaternion(); float rotations =
     * mapState.getWangBlobRotations(tileType.getTileIndex()); float
     * ninety_degrees_to_radians = FastMath.PI / 2;
     *
     * rot.fromAngleAxis(-ninety_degrees_to_radians * rotations, Vector3f.UNIT_Y);
     * //Reset rotation geom.setLocalRotation(new Quaternion()); //Set correct
     * rotation geom.rotate(rot);
     *
     * //log.info("Coords: "+s.getLocalTranslation()
     * +" rotated: "+geom.getLocalRotation()); }
     */
    private Spatial createBurst() {
        final Quad quad = new Quad(CoreViewConstants.BURSTSIZE, CoreViewConstants.BURSTSIZE);
        final float halfSize = CoreViewConstants.BURSTSIZE * 0.5f;
        quad.setBuffer(VertexBuffer.Type.Position, 3, getVertices(halfSize));
        quad.setBuffer(VertexBuffer.Type.Normal, 3, BufferUtils.createFloatBuffer(getNormals()));
        quad.updateBound();
        final Geometry geom = new Geometry("Burst", quad);

        if (unshaded) {
            geom.setMaterial(assets.loadMaterial("Materials/BurstMaterialUnshaded.j3m"));
        } else {
            geom.setMaterial(assets.loadMaterial("Materials/BurstMaterialLight.j3m"));
        }
        geom.getMaterial().setFloat("StartTime", timer.getTimeInSeconds());
        geom.setQueueBucket(RenderQueue.Bucket.Transparent);
        return geom;
    }

    /**
     * This array is used to define the quad bounds in the right order. Its
     * important relative to where the camera is and what facing the camera has
     *
     * @param halfSize
     * @return array
     */
    private float[] getVertices(final float halfSize) {
        final float[] res = new float[] { halfSize, 0, -halfSize, -halfSize, 0, -halfSize, -halfSize, 0, halfSize,
                halfSize, 0, halfSize };
        return res;
    }

    /**
     * This will create the normals that is point in the z unit vector direction.
     * This is used in relation to the lighting on the quad (towards camera)
     *
     * @return float array containing the right normals
     */
    private float[] getNormals() {
        float[] normals;
        normals = new float[] { 0, 1, 0, 0, 1, 0, 0, 1, 0, 0, 1, 0 };
        return normals;
    }
}
