// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.client.states;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.renderer.queue.RenderQueue.ShadowMode;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
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
import infinity.client.view.BombVisuals;
import infinity.client.view.BulletVisuals;
import infinity.client.view.ShipVisuals;
import infinity.client.view.SpecialBombVisuals;
import infinity.client.view.BlockGeometryIndex;
import infinity.es.Flag;
import infinity.es.ShapeNames;
import infinity.sim.CoreViewConstants;
import java.nio.FloatBuffer;
import java.util.Map;
import java.util.function.DoubleFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Builds gameplay-entity spatials (ships, flags, doors, bombs, bullets, prizes, arena ghost-cube); delegates effects to {@link EffectSpatialFactory}. */
public class SISpatialFactory {

  // Use to flip between using the lights and using unshaded textures
  private static final boolean UNSHADED = false;
  private static final String NUMTILESOFFSETY = "numTilesOffsetY";
  private static final String STARTTIME = "StartTime";
  static Logger log = LoggerFactory.getLogger(SISpatialFactory.class);
  private final AssetManager assets;
  private final Timer timer;
  private final BlockGeometryIndex geomIndex;
  private final EffectSpatialFactory effects;
  private String objectString = "Object:";

  SISpatialFactory(
      final AssetManager assets,
      final Timer timer,
      final BlockGeometryIndex geomIndex,
      final EffectSpatialFactory effects) {
    this.assets = assets;
    this.timer = timer;
    this.geomIndex = geomIndex;
    this.effects = effects;
  }

  /** Most shapes ignore {@code scale}; the arena ghost-cube reads it via {@link #createArena(double)}. */
  public Spatial createModel(EntityId id, String shapeName, Mass mass, double scale) {
    final DoubleFunction<Spatial> factory = shapeFactories.get(shapeName);
    if (factory != null) {
      return factory.apply(scale);
    }
    // Unknown shape — defer to the effect factory, which throws if it also
    // doesn't recognise the name. Single source of the "Unknown shape name"
    // exception keeps the failure mode unchanged from the pre-split factory.
    return effects.createEffect(shapeName, scale);
  }

  private final Map<String, DoubleFunction<Spatial>> shapeFactories = Map.ofEntries(
      Map.entry(ShapeNames.BULLETL4, scale -> createBullet(BulletVisuals.LEVEL_4.viewOffset)),
      Map.entry(ShapeNames.BULLETL3, scale -> createBullet(BulletVisuals.LEVEL_3.viewOffset)),
      Map.entry(ShapeNames.BULLETL2, scale -> createBullet(BulletVisuals.LEVEL_2.viewOffset)),
      Map.entry(ShapeNames.BULLETL1, scale -> createBullet(BulletVisuals.LEVEL_1.viewOffset)),
      Map.entry(ShapeNames.MINEL1, scale -> createBomb(BombVisuals.BOMB_1.viewOffset)),
      Map.entry(ShapeNames.BOMBL1, scale -> createBomb(BombVisuals.BOMB_1.viewOffset)),
      Map.entry(ShapeNames.MINEL2, scale -> createBomb(BombVisuals.BOMB_2.viewOffset)),
      Map.entry(ShapeNames.BOMBL2, scale -> createBomb(BombVisuals.BOMB_2.viewOffset)),
      Map.entry(ShapeNames.MINEL3, scale -> createBomb(BombVisuals.BOMB_3.viewOffset)),
      Map.entry(ShapeNames.BOMBL3, scale -> createBomb(BombVisuals.BOMB_3.viewOffset)),
      Map.entry(ShapeNames.MINEL4, scale -> createBomb(BombVisuals.BOMB_4.viewOffset)),
      Map.entry(ShapeNames.BOMBL4, scale -> createBomb(BombVisuals.BOMB_4.viewOffset)),
      Map.entry(ShapeNames.THOR, scale -> createBomb(SpecialBombVisuals.THOR.viewOffset)),
      Map.entry(ShapeNames.PRIZE, scale -> createBounty()),
      Map.entry(ShapeNames.ARENA, this::createArena),
      Map.entry(ShapeNames.SHIP_WARBIRD, scale -> createShip(ShipVisuals.WARBIRD.visualOffset)),
      Map.entry(ShapeNames.SHIP_JAVELIN, scale -> createShip(ShipVisuals.JAVELIN.visualOffset)),
      Map.entry(ShapeNames.SHIP_SPIDER, scale -> createShip(ShipVisuals.SPIDER.visualOffset)),
      Map.entry(ShapeNames.SHIP_LEVI, scale -> createShip(ShipVisuals.LEVIATHAN.visualOffset)),
      Map.entry(ShapeNames.SHIP_TERRIER, scale -> createShip(ShipVisuals.TERRIER.visualOffset)),
      Map.entry(ShapeNames.SHIP_WEASEL, scale -> createShip(ShipVisuals.WEASEL.visualOffset)),
      Map.entry(ShapeNames.SHIP_LANCASTER, scale -> createShip(ShipVisuals.LANCASTER.visualOffset)),
      Map.entry(ShapeNames.SHIP_SHARK, scale -> createShip(ShipVisuals.SHARK.visualOffset)),
      Map.entry(ShapeNames.FLAG, scale -> createFlag(Flag.FLAG_THEIRS)),
      Map.entry(ShapeNames.DOOR, scale -> createDoor()));

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

    parts.setLocalScale((float) part.getScale());
    parts.setShadowMode(ShadowMode.CastAndReceive);

    node.setUserData("oid", id.getId());

    return node;
  }

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
    translateMesh(box, new Vector3f(halfSize, halfSize, halfSize));
    scaleMesh(box, 0.5f);
    translateMesh(box, new Vector3f(quarterSize, quarterSize, quarterSize));
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

  /** {@code flag}: 0 = enemy flag, 1 = team flag. */
  private Spatial createFlag(final int flag) {
    final Quad quad = new Quad(CoreViewConstants.FLAGSIZE, CoreViewConstants.FLAGSIZE);
    final float halfSize = CoreViewConstants.FLAGSIZE * 0.5f;
    quad.setBuffer(VertexBuffer.Type.Position, 3, QuadMeshes.verticesQuad(halfSize));
    quad.setBuffer(VertexBuffer.Type.Normal, 3, BufferUtils.createFloatBuffer(QuadMeshes.normalsQuad()));
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
        VertexBuffer.Type.Position, 3, BufferUtils.createFloatBuffer(QuadMeshes.verticesQuad(halfSize)));
    quad.setBuffer(VertexBuffer.Type.Normal, 3, BufferUtils.createFloatBuffer(QuadMeshes.normalsQuad()));
    quad.updateBound();
    final Geometry geom = new Geometry("Ship", quad);

    if (UNSHADED) {
      geom.setMaterial(assets.loadMaterial("Materials/ShipMaterialUnshaded.j3m"));
    } else {
      geom.setMaterial(assets.loadMaterial("Materials/ShipMaterialLight.j3m"));
    }

    setShipMaterialVariables(geom, ship);

    geom.setQueueBucket(RenderQueue.Bucket.Transparent);

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
    mat.setInt(NUMTILESOFFSETY, ship);
    geom.setMaterial(mat);
    log.info("Setting geometry material on spatial:{}; ship:{}", s, ship);
  }

  private Spatial createBomb(int viewOffset) {
    final Quad quad = new Quad(CoreViewConstants.BOMBSIZE, CoreViewConstants.BOMBSIZE);
    final float halfSize = CoreViewConstants.BOMBSIZE * 0.5f;
    quad.setBuffer(VertexBuffer.Type.Position, 3, QuadMeshes.verticesQuad(halfSize));
    quad.setBuffer(VertexBuffer.Type.Normal, 3, BufferUtils.createFloatBuffer(QuadMeshes.normalsQuad()));
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
    quad.setBuffer(VertexBuffer.Type.Position, 3, QuadMeshes.verticesQuad(halfSize));
    quad.setBuffer(VertexBuffer.Type.Normal, 3, BufferUtils.createFloatBuffer(QuadMeshes.normalsQuad()));
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

  private Spatial createBounty() {
    final Quad quad = new Quad(CoreViewConstants.PRIZESIZE, CoreViewConstants.PRIZESIZE);
    final float halfSize = CoreViewConstants.PRIZESIZE * 0.5f;
    quad.setBuffer(VertexBuffer.Type.Position, 3, QuadMeshes.verticesQuad(halfSize));
    quad.setBuffer(VertexBuffer.Type.Normal, 3, BufferUtils.createFloatBuffer(QuadMeshes.normalsQuad()));
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

  private Spatial createArena(final double scale) {
    // CubeFactory produces edge-length = scale/2. Wireframe so ships inside stay visible.
    // Anchored at min-corner to mirror the server's SpawnPosition convention.
    final float edge = (float) (scale * 0.5);
    final float halfEdge = edge * 0.5f;
    final Box box = new Box(halfEdge, halfEdge, halfEdge);
    translateMesh(box, new Vector3f(halfEdge, halfEdge, halfEdge));
    box.updateBound();

    final Geometry geom = new Geometry("Arena", box);
    final Material mat =
        GuiGlobals.getInstance().createMaterial(ColorRGBA.Yellow, false).getMaterial();
    mat.getAdditionalRenderState().setWireframe(true);
    geom.setMaterial(mat);
    // The "arena" marker tells Model.resetVisibility() to keep CullHint.Never
    // when the spatial is shown — the wireframe extends y=0..1024 but the
    // camera sits ~75 above the avatar, putting most of the bounding box
    // behind the camera and tripping JME's frustum test when alongside.
    geom.setUserData("arena", Boolean.TRUE);

    return geom;
  }

  private static void translateMesh(final Mesh mesh, final Vector3f offset) {
    final VertexBuffer posBuffer = mesh.getBuffer(VertexBuffer.Type.Position);
    final FloatBuffer pos = (FloatBuffer) posBuffer.getData();
    for (int i = 0; i + 2 < pos.limit(); i += 3) {
      pos.put(i,     pos.get(i)     + offset.x);
      pos.put(i + 1, pos.get(i + 1) + offset.y);
      pos.put(i + 2, pos.get(i + 2) + offset.z);
    }
    posBuffer.setUpdateNeeded();
    mesh.updateBound();
  }

  private static void scaleMesh(final Mesh mesh, final float factor) {
    final VertexBuffer posBuffer = mesh.getBuffer(VertexBuffer.Type.Position);
    final FloatBuffer pos = (FloatBuffer) posBuffer.getData();
    for (int i = 0; i < pos.limit(); i++) {
      pos.put(i, pos.get(i) * factor);
    }
    posBuffer.setUpdateNeeded();
    mesh.updateBound();
  }
}
