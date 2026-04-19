---
name: jme-materials
description: Work with jMonkeyEngine 3 materials, including Material objects, .j3m material files, Material Definitions (.j3md), and common material configurations like textures, lighting, transparency, and glow.
---

# jMonkeyEngine Materials Skill

## Overview

This skill covers working with jMonkeyEngine 3 materials, including Material objects, .j3m material files, Material Definitions (.j3md), and common material configurations like textures, lighting, transparency, and glow.

## Material System Basics

### Core Concepts

- **Material**: Java object that defines surface properties (color, texture, shininess, etc.)
- **Material Definition (.j3md)**: Defines the "logic" for materials - what parameters exist and which shaders to use
- **Material File (.j3m)**: Saves material settings to a reusable asset file
- **Shader**: GLSL code that actually renders the material

### Built-in Material Definitions

| Definition | Use Case |
|-----------|----------|
| `Common/MatDefs/Misc/Unshaded.j3md` | No lighting - UI, sky, billboards, toons |
| `Common/MatDefs/Light/Lighting.j3md` | Phong-illuminated realistic objects |
| `Common/MatDefs/Terrain/Terrain.j3md` | Splat-textured terrain |
| `Common/MatDefs/Misc/Particle.j3md` | Particle effects |

## Code Patterns

### Basic Unshaded Material (Solid Color)

```java
Material mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
mat.setColor("Color", ColorRGBA.Blue);
geometry.setMaterial(mat);
```

### Unshaded Material with Texture

```java
Material mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
mat.setTexture("ColorMap", assetManager.loadTexture("Textures/myTexture.png"));
geometry.setMaterial(mat);
```

### Phong-Illuminated Material (Requires Light Source!)

```java
Material mat = new Material(assetManager, "Common/MatDefs/Light/Lighting.j3md");
mat.setBoolean("UseMaterialColors", true);
mat.setColor("Ambient", ColorRGBA.Blue);
mat.setColor("Diffuse", ColorRGBA.Blue);
geometry.setMaterial(mat);
```

### Textured Illuminated Material

```java
Material mat = new Material(assetManager, "Common/MatDefs/Light/Lighting.j3md");
mat.setTexture("DiffuseMap", assetManager.loadTexture("Textures/diffuse.png"));
mat.setTexture("NormalMap", assetManager.loadTexture("Textures/normal.png"));  // Bump map
mat.setTexture("SpecularMap", assetManager.loadTexture("Textures/specular.png"));
mat.setFloat("Shininess", 64f);  // 1 (rough) to 128 (smooth)
geometry.setMaterial(mat);

// Required for normal maps:
TangentBinormalGenerator.generate(geometry.getMesh());
```

### Shiny Material

```java
Material mat = new Material(assetManager, "Common/MatDefs/Light/Lighting.j3md");
mat.setBoolean("UseMaterialColors", true);
mat.setColor("Diffuse", ColorRGBA.White);
mat.setColor("Specular", ColorRGBA.White);  // Shiny highlight color
mat.setFloat("Shininess", 64f);  // Higher = more focused shine
geometry.setMaterial(mat);
```

### Glowing Material

```java
// 1. Add BloomFilter to viewport (once in simpleInitApp)
FilterPostProcessor fpp = new FilterPostProcessor(assetManager);
BloomFilter bloom = new BloomFilter(BloomFilter.GlowMode.Objects);
fpp.addFilter(bloom);
viewPort.addProcessor(fpp);

// 2. Set glow on material
Material mat = new Material(assetManager, "Common/MatDefs/Light/Lighting.j3md");
mat.setColor("GlowColor", ColorRGBA.Green);
// Optional: mat.setTexture("GlowMap", assetManager.loadTexture("Textures/glow.png"));
geometry.setMaterial(mat);
```

### Transparent Material

```java
Material mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
mat.setColor("Color", new ColorRGBA(1f, 0f, 0f, 0.5f));  // 50% transparent red
mat.getAdditionalRenderState().setBlendMode(BlendMode.Alpha);
geometry.setMaterial(mat);
geometry.setQueueBucket(Bucket.Transparent);
```

### Transparent Texture (Alpha Channel)

```java
Material mat = new Material(assetManager, "Common/MatDefs/Light/Lighting.j3md");
mat.setTexture("DiffuseMap", assetManager.loadTexture("Textures/foliage.png"));
mat.setBoolean("UseAlpha", true);
mat.setFloat("AlphaDiscardThreshold", 0.5f);  // Pixels below this alpha = invisible
mat.getAdditionalRenderState().setBlendMode(BlendMode.Alpha);
geometry.setMaterial(mat);
geometry.setQueueBucket(Bucket.Transparent);
```

### Wireframe Mode

```java
mat.getAdditionalRenderState().setWireframe(true);
mat.getAdditionalRenderState().setLineWidth(2f);
```

## .j3m Material Files

Save commonly used material configurations to files for easy reuse.

### File Location
`assets/Materials/MyMaterial.j3m`

### Basic Structure

```
Material MyMaterialName : Common/MatDefs/Light/Lighting.j3md {
    MaterialParameters {
        Shininess: 8.0
        DiffuseMap: Textures/diffuse.png
        NormalMap: Textures/normal.png
        UseMaterialColors : true
        Ambient  : 0.5 0.5 0.5 1.0
        Diffuse  : 1.0 1.0 1.0 1.0
        Specular : 1.0 1.0 1.0 1.0
    }
}
```

### Loading a .j3m File

```java
geometry.setMaterial(assetManager.loadMaterial("Materials/MyMaterial.j3m"));
```

### J3M Data Types

| Type | Format | Example |
|------|--------|---------|
| Float | number | `Shininess: 8.0` |
| Vector2 | two floats | `TexCoord: 1.0 1.0` |
| Vector3 | three floats | `NormalScale: 1.0 1.0 1.0` |
| Vector4/Color | four floats (RGBA) | `Diffuse: 1.0 0.5 0.0 1.0` |
| Boolean | true/false | `UseMaterialColors: true` |
| Texture2D | path | `DiffuseMap: Textures/rock.png` |

### Texture Modifiers

```
DiffuseMap: Flip Textures/flipped.png       // Flip vertically
DiffuseMap: Repeat Textures/tiled.png       // Repeat/tile texture
DiffuseMap: Flip Repeat Textures/both.png   // Both (Flip must come first)
```

### Transparency in .j3m

```
Material TransparentLeaves : Common/MatDefs/Light/Lighting.j3md {
    Transparent On
    
    MaterialParameters {
        DiffuseMap : Models/Tree/Leaves.png
        UseAlpha : true
        AlphaDiscardThreshold : 0.5
        UseMaterialColors : true
        Diffuse : 0.7 0.7 0.7 1
    }
    AdditionalRenderState {
        Blend Alpha
        FaceCull Off
    }
}
```

## Common Material Parameters

### Unshaded.j3md Parameters

| Parameter | Type | Description |
|-----------|------|-------------|
| Color | Color | Solid color |
| ColorMap | Texture2D | Texture |
| LightMap | Texture2D | Pre-baked lighting |
| GlowColor | Color | Glow effect color |
| GlowMap | Texture2D | Areas that glow |
| VertexColor | Boolean | Use vertex colors |

### Lighting.j3md Parameters

| Parameter | Type | Description |
|-----------|------|-------------|
| DiffuseMap | Texture2D | Main texture |
| NormalMap | Texture2D | Bump mapping |
| SpecularMap | Texture2D | Shiny areas |
| ParallaxMap | Texture2D | Depth effect |
| AlphaMap | Texture2D | Transparency mask |
| GlowMap | Texture2D | Glow areas |
| Diffuse | Color | Base color |
| Ambient | Color | Shadow color |
| Specular | Color | Highlight color |
| GlowColor | Color | Glow color |
| Shininess | Float | 1-128, specular focus |
| UseMaterialColors | Boolean | Enable color params |
| UseAlpha | Boolean | Use texture alpha |
| AlphaDiscardThreshold | Float | Alpha cutoff |

## RenderState Options

```java
RenderState rs = mat.getAdditionalRenderState();

// Blending
rs.setBlendMode(BlendMode.Off);       // Default, opaque
rs.setBlendMode(BlendMode.Alpha);     // Standard transparency
rs.setBlendMode(BlendMode.Additive);  // Additive (particles)

// Face culling
rs.setFaceCullMode(FaceCullMode.Back);  // Default, hide back faces
rs.setFaceCullMode(FaceCullMode.Off);   // Show both sides
rs.setFaceCullMode(FaceCullMode.Front); // Hide front faces

// Depth
rs.setDepthWrite(false);  // Don't write to depth buffer
rs.setDepthTest(false);   // Don't test depth

// Other
rs.setWireframe(true);
rs.setColorWrite(false);  // Don't write color
rs.setPolyOffset(1f, 1f); // Fix z-fighting
```

## Best Practices

1. **Reuse Materials**: Create once, apply to many geometries
2. **Add Light Sources**: Illuminated materials are invisible without lights
3. **Generate Tangents**: Required for NormalMaps: `TangentBinormalGenerator.generate(mesh)`
4. **Queue Buckets**: Use `Bucket.Transparent` or `Bucket.Translucent` for transparency
5. **Use .j3m Files**: Store common materials as assets, not code
6. **Check Material Def**: Verify parameters exist in the .j3md you're using

## File Locations in This Project

- Materials: `infinity/assets/Materials/`
- Textures: `infinity/assets/Textures/`
- Material Definitions: `infinity/assets/MatDefs/` (for custom)

## References

- [jME3 How to Use Materials](https://wiki.jmonkeyengine.org/docs/3.9/core/material/how_to_use_materials.html)
- [j3m Material Files](https://wiki.jmonkeyengine.org/docs/3.9/core/material/j3m_material_files.html)
- [Material Definitions](https://wiki.jmonkeyengine.org/docs/3.9/core/material/material_definitions.html)
- [Material Properties Overview](https://wiki.jmonkeyengine.org/docs/3.9/core/material/materials_overview.html)
