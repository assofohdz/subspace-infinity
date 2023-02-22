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

import com.jme3.asset.AssetManager;
import com.jme3.effect.ParticleEmitter;
import com.jme3.effect.ParticleMesh;
import com.jme3.light.PointLight;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.renderer.queue.RenderQueue.ShadowMode;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.VertexBuffer;
import com.jme3.scene.shape.Box;
import com.jme3.scene.shape.Quad;
import com.jme3.scene.shape.Sphere;
import com.jme3.system.Timer;
import com.jme3.texture.Texture;
import com.jme3.util.BufferUtils;
import com.simsilica.es.EntityId;
import com.simsilica.ext.mphys.Mass;
import com.simsilica.lemur.GuiGlobals;
import com.simsilica.mblock.phys.CellArrayPart;
import com.simsilica.mblock.phys.Group;
import com.simsilica.mblock.phys.Part;
import infinity.BombRegistry;
import infinity.Ships;
import infinity.client.view.BlockGeometryIndex;
import infinity.client.view.EffectFactory;
import infinity.client.view.ShipLightControl;
import infinity.es.Flag;
import infinity.es.ShapeNames;
import infinity.Bombs;
import infinity.Guns;
import infinity.sim.CoreViewConstants;
import infinity.sim.util.InfinityRunTimeException;
import jme3utilities.MyMesh;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class is responsible for creating the spatial representation of the game.
 *
 * @author Asser
 */
public class SISpatialFactory {

  // Use to flip between using the lights and using unshaded textures
  private static final boolean UNSHADED = false;
  private static final boolean DEBUG_COG = false;
  private static final String NUMTILESOFFSETY = "numTilesOffsetY";
  private static final String STARTTIME = "StartTime";
  static Logger log = LoggerFactory.getLogger(SISpatialFactory.class);
  private final AssetManager assets;
  private final Node rootNode;
  private final Timer timer;
  private final BlockGeometryIndex geomIndex;
  private EffectFactory ef;
  private String objectString = "Object:";

  SISpatialFactory(
      final Node rootNode,
      final AssetManager assets,
      final Timer timer,
      BlockGeometryIndex geomIndex) {
    this.rootNode = rootNode;
    this.assets = assets;
    this.timer = timer;
    this.geomIndex = geomIndex;
  }

  /**
   * Create a spatial for the given shape name.
   *
   * @param shapeName The name of the shape to create
   * @return The spatial
   */
  public Spatial createModel(EntityId id, String shapeName, Mass mass) {

    switch (shapeName) {
      case ShapeNames.BULLETL4:
        return createBullet(Guns.LEVEL_4.viewOffset);
      case ShapeNames.BULLETL3:
        return createBullet(Guns.LEVEL_3.viewOffset);
      case ShapeNames.BULLETL2:
        return createBullet(Guns.LEVEL_2.viewOffset);
      case ShapeNames.BULLETL1:
        return createBullet(Guns.LEVEL_1.viewOffset);
      case ShapeNames.BOMBL1:
        return createBomb(Bombs.BOMB_1.viewOffset);
      case ShapeNames.BOMBL2:
        return createBomb(Bombs.BOMB_2.viewOffset);
      case ShapeNames.BOMBL3:
        return createBomb(Bombs.BOMB_3.viewOffset);
      case ShapeNames.BOMBL4:
        return createBomb(Bombs.BOMB_4.viewOffset);
      case ShapeNames.THOR:
        return createBomb(BombRegistry.THOR.viewOffset);
      case ShapeNames.BURST:
        return createBurst();
      case ShapeNames.EXPLOSION:
        return createExplosion();
      case ShapeNames.PRIZE:
        return createBounty();
      case ShapeNames.ARENA:
        return createArena();
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
        return createShip(Ships.WARBIRD.getVisualOffset());
      case ShapeNames.SHIP_JAVELIN:
        return createShip(Ships.JAVELIN.getVisualOffset());
      case ShapeNames.SHIP_SPIDER:
        return createShip(Ships.SPIDER.getVisualOffset());
      case ShapeNames.SHIP_LEVI:
        return createShip(Ships.LEVIATHAN.getVisualOffset());
      case ShapeNames.SHIP_TERRIER:
        return createShip(Ships.TERRIER.getVisualOffset());
      case ShapeNames.SHIP_WEASEL:
        return createShip(Ships.WEASEL.getVisualOffset());
      case ShapeNames.SHIP_LANCASTER:
        return createShip(Ships.LANCASTER.getVisualOffset());
      case ShapeNames.SHIP_SHARK:
        return createShip(Ships.SHARK.getVisualOffset());
      case ShapeNames.FLAG:
        return createFlag(Flag.FLAG_THEIRS);
      case ShapeNames.DOOR:
        return createDoor();
      default:
        throw new InfinityRunTimeException("Unknown shape name: " + shapeName);
    }
  }

  protected Geometry createBox(float size, ColorRGBA color) {
    Box box = new Box(size, size, size);
    Geometry geom = new Geometry("box", box);
    geom.setMaterial(GuiGlobals.getInstance().createMaterial(color, false).getMaterial());
    geom.getMaterial().getAdditionalRenderState().setWireframe(true);

    return geom;
  }
  /** Creates a root-level spatial for the specified root group. */
  protected Spatial createPartSpatial(EntityId id, Group group, Mass mass) {
    Node node = new Node(objectString + id);
    if (DEBUG_COG) {
      node.attachChild(createBox(0.1f, ColorRGBA.Orange));
    }

    // The root level will need to be positioned relative to the rigid body
    // which is positioned at CoG relative to model space.  So we need to offset
    // negative CoG.
    Node cogOffset = new Node("CoG:" + id);
    node.attachChild(cogOffset);
    createPartSpatial(cogOffset, id, group, mass);

    // Maybe someday when we have all the time in the world and are sitting
    // on a beach somewhere worrying about ways to micro-optimize, we can
    // see about flattening this extra hierarchy.  Though, do note that
    // the group hierarchy is already flat... because that's how I roll.

    return node;
  }

  /** Creates a child spatial for the specified child group. */
  protected Spatial createPartSpatial(Node parent, EntityId id, Group group, Mass mass) {
    for (Part child : group.getChildren()) {
      if (child instanceof CellArrayPart) {
        Spatial ps = createPartSpatial(id, (CellArrayPart) child, false, mass);
        ps.setLocalTranslation(child.getShapeRelativePosition().toVector3f());
        ps.setLocalRotation(child.getShapeRelativeOrientation().toQuaternion());
        parent.attachChild(ps);
      } else if (child instanceof Group) {
        createPartSpatial(parent, id, (Group) child, mass);
      } else {
        throw new IllegalArgumentException("Unhandled part type:" + child);
      }
    }
    return parent;
  }

  /**
   * Creates a sphere for the given entity with the given radius and mass.
   *
   * @param id The entity id
   * @param radius The radius of the sphere
   * @param mass The mass of the sphere
   * @return The sphere spatial
   */
  public Spatial createSphere(EntityId id, float radius, Mass mass) {
    Sphere mesh = new Sphere(24, 24, radius);
    mesh.setTextureMode(Sphere.TextureMode.Projected);
    mesh.scaleTextureCoordinates(new Vector2f(4, 2));
    Geometry geom = new Geometry(objectString + id, mesh);

    if (mass != null && mass.getMass() != 0) {
      geom.setMaterial(
          GuiGlobals.getInstance()
              .createMaterial(new ColorRGBA(0, 0.6f, 0.6f, 1), true)
              .getMaterial());

      Texture texture =
          GuiGlobals.getInstance().loadTexture("Interface/grid-shaded-labeled.png", true, true);
      geom.getMaterial().setTexture("DiffuseMap", texture);
    } else {
      // Just a flat green
      geom.setMaterial(
          GuiGlobals.getInstance()
              .createMaterial(new ColorRGBA(0.2f, 0.6f, 0.2f, 1), true)
              .getMaterial());
    }

    geom.setShadowMode(ShadowMode.CastAndReceive);

    geom.setUserData("oid", id.getId());
    return geom;
  }

  /** Creates a root-level spatial for the specified root part. */
  protected Spatial createPartSpatial(EntityId id, CellArrayPart part, boolean isRoot, Mass mass) {
    if (part.getCells() == null) {
      return createSphere(id, (float) part.getMass().getRadius(), mass);
    }

    Node node = new Node(objectString + id);
    Node parts = new Node("Parts:" + id);
    node.attachChild(parts);

    geomIndex.generateBlocks(parts, part.getCells());

    // If we are the root level then we'll need a cog shift
    // to match with the rigid body
    if (isRoot && DEBUG_COG) {
      // The position of the object is its CoG... which means
      // we need to offset our model's origin by it.  It should
      // already be scaled and everything... just need to negate it.

      // We need to sort out what the center should be.  Directly out of generateBlocks()
      // the geometry is all relative to the corner.   See cog-offset.txt

      node.attachChild(createBox(0.1f, ColorRGBA.Red));
    }

    parts.setLocalScale((float) part.getScale());
    parts.setShadowMode(ShadowMode.CastAndReceive);

    node.setUserData("oid", id.getId());

    return node;
  }

  /**
   * Creates a cube spatial for the door entity.
   *
   * @return The spatial
   */
  private Spatial createDoor() {
    Box box =
        new Box(
            CoreViewConstants.DOORSIZE,
            CoreViewConstants.DOORSIZE,
            CoreViewConstants.DOORSIZE); // create cube shape
    // 10-02-2023: Dont ask..I dont know enough about spatial translation and scaling to know why
    // this works.
    final float halfSize = CoreViewConstants.DOORSIZE * 0.5f;
    final float quarterSize = CoreViewConstants.DOORSIZE * 0.25f;
    MyMesh.translate(box, new Vector3f(halfSize, halfSize, halfSize));
    MyMesh.scale(box, 0.5f);
    MyMesh.translate(box, new Vector3f(quarterSize, quarterSize, quarterSize));
    box.updateBound();

    Geometry geom = new Geometry("Door", box); // create cube geometry from the shape
    Material mat;
    if (UNSHADED) {
      mat = GuiGlobals.getInstance().createMaterial(ColorRGBA.Blue, false).getMaterial();
    } else {
      mat = GuiGlobals.getInstance().createMaterial(ColorRGBA.Blue, true).getMaterial();
    }
    // set the cube's material
    geom.setMaterial(mat);
    return geom;
  }

  @SuppressWarnings("unused")
  private Spatial createBase() {
    final Quad quad = new Quad(CoreViewConstants.BASESIZE, CoreViewConstants.BASESIZE);
    final float halfSize = CoreViewConstants.BASESIZE * 0.5f;
    quad.setBuffer(VertexBuffer.Type.Position, 3, getVerticesQuad(halfSize));
    quad.setBuffer(VertexBuffer.Type.Normal, 3, BufferUtils.createFloatBuffer(getNormalsQuad()));
    quad.updateBound();
    final Geometry geom = new Geometry("Base", quad);

    if (UNSHADED) {
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
    quad.setBuffer(VertexBuffer.Type.Position, 3, getVerticesQuad(halfSize));
    quad.setBuffer(VertexBuffer.Type.Normal, 3, BufferUtils.createFloatBuffer(getNormalsQuad()));
    quad.updateBound();
    final Geometry geom = new Geometry("Mob", quad);

    if (UNSHADED) {
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
    quad.setBuffer(VertexBuffer.Type.Position, 3, getVerticesQuad(halfSize));
    quad.setBuffer(VertexBuffer.Type.Normal, 3, BufferUtils.createFloatBuffer(getNormalsQuad()));
    quad.updateBound();
    final Geometry geom = new Geometry("Tower", quad);

    if (UNSHADED) {
      geom.setMaterial(assets.loadMaterial("Materials/TowerMaterialUnshaded.j3m"));
    } else {
      geom.setMaterial(assets.loadMaterial("Materials/TowerMaterialLight.j3m"));
    }

    geom.setQueueBucket(RenderQueue.Bucket.Transparent);
    return geom;
  }

  /**
   * Creates a flag.
   *
   * @param flag 0 for enemy flag, 1 for team flag
   * @return the spatial that is created to visualize the flag
   */
  private Spatial createFlag(final int flag) {
    final Quad quad = new Quad(CoreViewConstants.FLAGSIZE, CoreViewConstants.FLAGSIZE);
    final float halfSize = CoreViewConstants.FLAGSIZE * 0.5f;
    quad.setBuffer(VertexBuffer.Type.Position, 3, getVerticesQuad(halfSize));
    quad.setBuffer(VertexBuffer.Type.Normal, 3, BufferUtils.createFloatBuffer(getNormalsQuad()));
    quad.updateBound();
    final Geometry geom = new Geometry("Flag", quad);

    if (UNSHADED) {
      geom.setMaterial(assets.loadMaterial("Materials/FlagMaterialUnshaded.j3m"));
    } else {
      geom.setMaterial(assets.loadMaterial("Materials/FlagMaterialLight.j3m"));
    }
    setFlagMaterialVariables(geom, flag);
    geom.setQueueBucket(RenderQueue.Bucket.Transparent);

    return geom;
  }

  /**
   * Sets the material variables for the flag.
   *
   * @param s the spatial
   * @param flag the flag, 0 for enemy flag, 1 for team flag
   */
  public void setFlagMaterialVariables(final Spatial s, final int flag) {
    Geometry geom;
    if (s instanceof Geometry) {
      geom = (Geometry) s;
    } else {
      geom = (Geometry) ((Node) s).getChild("Flag");
    }

    final Material mat = geom.getMaterial();
    mat.setInt(NUMTILESOFFSETY, flag);
    geom.setMaterial(mat);
  }

  private Spatial createShip(final int ship) {
    final Quad quad = new Quad(CoreViewConstants.SHIPSIZE, CoreViewConstants.SHIPSIZE);
    final float halfSize = CoreViewConstants.SHIPSIZE * 0.5f;
    quad.setBuffer(
        VertexBuffer.Type.Position, 3, BufferUtils.createFloatBuffer(getVerticesQuad(halfSize)));
    quad.setBuffer(VertexBuffer.Type.Normal, 3, BufferUtils.createFloatBuffer(getNormalsQuad()));
    quad.updateBound();
    final Geometry geom = new Geometry("Ship", quad);

    if (UNSHADED) {
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

  /**
   * Set the material variables for the ship.
   *
   * @param s the spatial to set the material variables on
   * @param ship the ship number
   */
  public void setShipMaterialVariables(final Spatial s, final int ship) {
    Geometry geom;
    if (s instanceof Geometry) {
      geom = (Geometry) s; // From createShip
    } else {
      geom = (Geometry) ((Node) s).getChild("Ship"); // From ModelViewState
    }
    final Material mat = geom.getMaterial();
    mat.setInt(NUMTILESOFFSETY, ship);
    geom.setMaterial(mat);
    log.info(String.format("Setting geometry material on spatial:%s; ship:%d", s, ship));
  }

  @SuppressWarnings("unused")
  private Spatial createParticleEmitter(final EntityId entityId, final String shapeName) {
    final Spatial result;

    // Create a thrust ParticleEmitter
    if (shapeName.equals("thrust")) {
      result = createThrustEmitter();
    } else {
      result = null;
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

  private Spatial createBomb(int viewOffset) {
    final Quad quad = new Quad(CoreViewConstants.BOMBSIZE, CoreViewConstants.BOMBSIZE);
    final float halfSize = CoreViewConstants.BOMBSIZE * 0.5f;
    quad.setBuffer(VertexBuffer.Type.Position, 3, getVerticesQuad(halfSize));
    quad.setBuffer(VertexBuffer.Type.Normal, 3, BufferUtils.createFloatBuffer(getNormalsQuad()));
    quad.updateBound();
    final Geometry geom = new Geometry("Bomb", quad);

    if (UNSHADED) {
      geom.setMaterial(assets.loadMaterial("Materials/BombMaterialUnshaded.j3m"));
    } else {
      geom.setMaterial(assets.loadMaterial("Materials/BombMaterialLight.j3m"));
    }
    geom.getMaterial().setInt(NUMTILESOFFSETY, viewOffset);

    geom.setQueueBucket(RenderQueue.Bucket.Transparent);
    return geom;
  }

  private Spatial createBullet(final int offSet) {
    final Quad quad = new Quad(CoreViewConstants.BULLETSIZE, CoreViewConstants.BULLETSIZE);
    final float halfSize = CoreViewConstants.BULLETSIZE * 0.5f;
    quad.setBuffer(VertexBuffer.Type.Position, 3, getVerticesQuad(halfSize));
    quad.setBuffer(VertexBuffer.Type.Normal, 3, BufferUtils.createFloatBuffer(getNormalsQuad()));
    quad.updateBound();
    final Geometry geom = new Geometry("Bullet", quad);

    if (UNSHADED) {
      geom.setMaterial(assets.loadMaterial("Materials/BulletMaterialUnshaded.j3m"));
    } else {
      geom.setMaterial(assets.loadMaterial("Materials/BulletMaterialLight.j3m"));
    }

    geom.getMaterial().setInt(NUMTILESOFFSETY, offSet);
    geom.setQueueBucket(RenderQueue.Bucket.Transparent);
    return geom;
  }

  private Spatial createExplosion() {
    return ef.createExplosion();
  }

  private Spatial createBounty() {
    final Quad quad = new Quad(CoreViewConstants.PRIZESIZE, CoreViewConstants.PRIZESIZE);
    final float halfSize = CoreViewConstants.PRIZESIZE * 0.5f;
    quad.setBuffer(VertexBuffer.Type.Position, 3, getVerticesQuad(halfSize));
    quad.setBuffer(VertexBuffer.Type.Normal, 3, BufferUtils.createFloatBuffer(getNormalsQuad()));
    quad.updateBound();
    final Geometry geom = new Geometry("Bounty", quad);

    if (UNSHADED) {
      geom.setMaterial(assets.loadMaterial("Materials/BountyMaterialUnshaded.j3m"));
    } else {
      geom.setMaterial(assets.loadMaterial("Materials/BountyMaterialLight.j3m"));
    }

    geom.getMaterial().setFloat(STARTTIME, timer.getTimeInSeconds());
    geom.setQueueBucket(RenderQueue.Bucket.Transparent);
    return geom;
  }

  private Spatial createArena() {
    final Quad quad = new Quad(1, 1);
    final float halfSize = 1 * 0.5f;
    quad.setBuffer(VertexBuffer.Type.Position, 3, getVerticesQuad(halfSize));
    quad.setBuffer(VertexBuffer.Type.Normal, 3, BufferUtils.createFloatBuffer(getNormalsQuad()));
    quad.updateBound();

    final Geometry geom = new Geometry("Arena", quad);
    // TODO: use a material with a texture, maybe something that creates a force field kind
    //  of look
    if (UNSHADED) {
      geom.setMaterial(assets.loadMaterial("Materials/BaseMaterialUnshaded.j3m"));
    } else {
      geom.setMaterial(assets.loadMaterial("Materials/BaseMaterialLight.j3m"));
    }

    geom.setUserData("arena", Boolean.TRUE);

    return geom;
  }

  private Spatial createExplosion2() {
    final Quad quad = new Quad(CoreViewConstants.EXPLOSION2SIZE, CoreViewConstants.EXPLOSION2SIZE);
    final float halfSize = CoreViewConstants.EXPLOSION2SIZE * 0.5f;
    quad.setBuffer(VertexBuffer.Type.Position, 3, getVerticesQuad(halfSize));
    quad.setBuffer(VertexBuffer.Type.Normal, 3, BufferUtils.createFloatBuffer(getNormalsQuad()));
    quad.updateBound();
    final Geometry geom = new Geometry("Bomb", quad);

    if (UNSHADED) {
      geom.setMaterial(assets.loadMaterial("Materials/Explode2MaterialUnshaded.j3m"));
    } else {
      geom.setMaterial(assets.loadMaterial("Materials/Explode2MaterialLight.j3m"));
    }

    geom.getMaterial().setFloat(STARTTIME, timer.getTimeInSeconds());

    geom.setQueueBucket(RenderQueue.Bucket.Transparent);
    return geom;
  }

  private Spatial createOver5() {
    final Quad quadOver5 = new Quad(CoreViewConstants.OVER5SIZE, CoreViewConstants.OVER5SIZE);
    final float halfSizeOver5 = CoreViewConstants.OVER5SIZE * 0.5f;
    quadOver5.setBuffer(VertexBuffer.Type.Position, 3, getVerticesQuad(halfSizeOver5));
    quadOver5.setBuffer(
        VertexBuffer.Type.Normal, 3, BufferUtils.createFloatBuffer(getNormalsQuad()));
    quadOver5.updateBound();
    final Geometry geomOver5 = new Geometry("Wormhole", quadOver5);

    if (UNSHADED) {
      geomOver5.setMaterial(assets.loadMaterial("Materials/Over5MaterialUnshaded.j3m"));
    } else {
      geomOver5.setMaterial(assets.loadMaterial("Materials/Over5MaterialLight.j3m"));
    }

    geomOver5.getMaterial().setFloat(STARTTIME, timer.getTimeInSeconds());
    geomOver5.setQueueBucket(RenderQueue.Bucket.Transparent);
    return geomOver5;
  }

  private Spatial createWormhole() {
    final Quad quad = new Quad(CoreViewConstants.WORMHOLESIZE, CoreViewConstants.WORMHOLESIZE);
    // <-- Move into the material?
    final float halfSize = CoreViewConstants.WORMHOLESIZE * 0.5f;
    quad.setBuffer(VertexBuffer.Type.Position, 3, getVerticesQuad(halfSize));
    quad.setBuffer(VertexBuffer.Type.Normal, 3, BufferUtils.createFloatBuffer(getNormalsQuad()));
    // -->
    quad.updateBound();
    final Geometry geom = new Geometry("Wormhole", quad);

    if (UNSHADED) {
      geom.setMaterial(assets.loadMaterial("Materials/WormholeMaterialUnshaded.j3m"));
    } else {
      geom.setMaterial(assets.loadMaterial("Materials/WormholeMaterialLight.j3m"));
    }

    geom.getMaterial().setFloat(STARTTIME, timer.getTimeInSeconds());
    geom.setQueueBucket(RenderQueue.Bucket.Transparent);
    return geom;
  }

  private Spatial createOver1() {
    final Quad quad = new Quad(CoreViewConstants.OVER1SIZE, CoreViewConstants.OVER1SIZE);
    final float halfSize = CoreViewConstants.OVER1SIZE * 0.5f;
    quad.setBuffer(VertexBuffer.Type.Position, 3, getVerticesQuad(halfSize));
    quad.setBuffer(VertexBuffer.Type.Normal, 3, BufferUtils.createFloatBuffer(getNormalsQuad()));
    quad.updateBound();
    final Geometry geom = new Geometry("Over1", quad);

    if (UNSHADED) {
      geom.setMaterial(assets.loadMaterial("Materials/Over1MaterialUnshaded.j3m"));
    } else {
      geom.setMaterial(assets.loadMaterial("Materials/Over1MaterialLight.j3m"));
    }

    geom.getMaterial().setFloat(STARTTIME, timer.getTimeInSeconds());
    geom.setQueueBucket(RenderQueue.Bucket.Transparent);
    return geom;
  }

  private Spatial createOver2() {
    final Quad quad = new Quad(CoreViewConstants.OVER2SIZE, CoreViewConstants.OVER2SIZE);
    final float halfSize = CoreViewConstants.OVER2SIZE * 0.5f;
    quad.setBuffer(VertexBuffer.Type.Position, 3, getVerticesQuad(halfSize));
    quad.setBuffer(VertexBuffer.Type.Normal, 3, BufferUtils.createFloatBuffer(getNormalsQuad()));
    quad.updateBound();
    final Geometry geom = new Geometry("Over2", quad);

    if (UNSHADED) {
      geom.setMaterial(assets.loadMaterial("Materials/Over2MaterialUnshaded.j3m"));
    } else {
      geom.setMaterial(assets.loadMaterial("Materials/Over2MaterialLight.j3m"));
    }

    geom.getMaterial().setFloat(STARTTIME, timer.getTimeInSeconds());
    geom.setQueueBucket(RenderQueue.Bucket.Transparent);
    return geom;
  }

  private Spatial createWarp() {
    final Quad quad = new Quad(CoreViewConstants.WARPSIZE, CoreViewConstants.WARPSIZE);
    final float halfSize = CoreViewConstants.WARPSIZE * 0.5f;
    quad.setBuffer(VertexBuffer.Type.Position, 3, getVerticesQuad(halfSize));
    quad.setBuffer(VertexBuffer.Type.Normal, 3, BufferUtils.createFloatBuffer(getNormalsQuad()));
    quad.updateBound();
    final Geometry geom = new Geometry("Warp", quad);

    if (UNSHADED) {
      geom.setMaterial(assets.loadMaterial("Materials/WarpMaterialUnshaded.j3m"));
    } else {
      geom.setMaterial(assets.loadMaterial("Materials/WarpMaterialLight.j3m"));
    }

    geom.getMaterial().setFloat(STARTTIME, timer.getTimeInSeconds());
    geom.setQueueBucket(RenderQueue.Bucket.Transparent);
    return geom;
  }

  private Spatial createRepel() {
    final Quad quad = new Quad(CoreViewConstants.REPELSIZE, CoreViewConstants.REPELSIZE);
    final float halfSize = CoreViewConstants.REPELSIZE * 0.5f;
    quad.setBuffer(VertexBuffer.Type.Position, 3, getVerticesQuad(halfSize));
    quad.setBuffer(VertexBuffer.Type.Normal, 3, BufferUtils.createFloatBuffer(getNormalsQuad()));
    quad.updateBound();
    final Geometry geom = new Geometry("Repel", quad);

    if (UNSHADED) {
      geom.setMaterial(assets.loadMaterial("Materials/RepelMaterialUnshaded.j3m"));
    } else {
      geom.setMaterial(assets.loadMaterial("Materials/RepelMaterialLight.j3m"));
    }

    geom.getMaterial().setFloat(STARTTIME, timer.getTimeInSeconds());
    geom.setQueueBucket(RenderQueue.Bucket.Transparent);
    return geom;
  }

  private Spatial createBurst() {
    final Quad quad = new Quad(CoreViewConstants.BURSTSIZE, CoreViewConstants.BURSTSIZE);
    final float halfSize = CoreViewConstants.BURSTSIZE * 0.5f;
    quad.setBuffer(VertexBuffer.Type.Position, 3, getVerticesQuad(halfSize));
    quad.setBuffer(VertexBuffer.Type.Normal, 3, BufferUtils.createFloatBuffer(getNormalsQuad()));
    quad.updateBound();
    final Geometry geom = new Geometry("Burst", quad);

    if (UNSHADED) {
      geom.setMaterial(assets.loadMaterial("Materials/BurstMaterialUnshaded.j3m"));
    } else {
      geom.setMaterial(assets.loadMaterial("Materials/BurstMaterialLight.j3m"));
    }
    geom.getMaterial().setFloat(STARTTIME, timer.getTimeInSeconds());
    geom.setQueueBucket(RenderQueue.Bucket.Transparent);
    return geom;
  }

  /**
   * This array is used to define the quad bounds in the right order. Its important relative to
   * where the camera is and what facing the camera has
   *
   * @param halfSize the half size of the quad
   * @return array
   */
  private float[] getVerticesQuad(final float halfSize) {
    return new float[] {
      halfSize, 0, -halfSize, -halfSize, 0, -halfSize, -halfSize, 0, halfSize, halfSize, 0, halfSize
    };
  }

  /**
   * This will create the normals that is point in the z unit vector direction. This is used in
   * relation to the lighting on the quad (towards camera)
   *
   * @return float array containing the right normals
   */
  private float[] getNormalsQuad() {
    float[] normals;
    normals = new float[] {0, 1, 0, 0, 1, 0, 0, 1, 0, 0, 1, 0};
    return normals;
  }
}
