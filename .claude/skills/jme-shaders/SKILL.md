---
name: jme-shaders
description: Create custom shaders for jMonkeyEngine 3, including GLSL basics, Material Definitions (.j3md), vertex/fragment shaders, and the Shader Node system for modular shader composition.
---

# jMonkeyEngine Shaders Skill

## Overview

This skill covers creating custom shaders for jMonkeyEngine 3, including GLSL basics, Material Definitions (.j3md), vertex/fragment shaders, and the Shader Node system for modular shader composition.

## Shader Basics

### How Shaders Work

1. **Vertex Shader** (.vert): Executed once per vertex - computes screen position
2. **Fragment Shader** (.frag): Executed once per pixel - computes final color

```
Mesh Data → Vertex Shader → Fragment Shader → Screen Pixels
              (position)        (color)
```

### Variable Scopes in GLSL

| Scope | Description | Prefix in jME3 |
|-------|-------------|----------------|
| `uniform` | Global values passed from Java | `g_` (world), `m_` (material) |
| `attribute` | Per-vertex data (position, normal) | `in` |
| `varying` | Passed from vertex to fragment shader | - |

### Coordinate Spaces

```
Object Space → World Space → View Space → Projection Space
     ↓              ↓            ↓              ↓
  (model)      (g_WorldMatrix)  (g_ViewMatrix)  (g_ProjectionMatrix)
```

The `WorldViewProjectionMatrix` combines all three transforms.

## Creating Custom Shaders

### Step-by-Step Process

1. Create vertex shader (.vert)
2. Create fragment shader (.frag)  
3. Create Material Definition (.j3md)
4. Load and use in Java

### File Structure

```
assets/
  MatDefs/
    MyShader/
      MyShader.j3md      # Material definition
      MyShader.vert      # Vertex shader
      MyShader.frag      # Fragment shader
```

## Simple Shader Example

### Vertex Shader (MyShader.vert)

```glsl
// World uniforms (prefixed with g_)
uniform mat4 g_WorldViewProjectionMatrix;

// Vertex attributes (prefixed with in)
attribute vec3 inPosition;

void main() {
    // Transform vertex from object space to screen space
    gl_Position = g_WorldViewProjectionMatrix * vec4(inPosition, 1.0);
}
```

### Fragment Shader (MyShader.frag)

```glsl
// Material parameters (prefixed with m_)
uniform vec4 m_Color;

void main() {
    // Output the final pixel color
    gl_FragColor = m_Color;
}
```

### Material Definition (MyShader.j3md)

```
MaterialDef My Shader {
    MaterialParameters {
        Color Color        // User-defined parameter
    }
    Technique {
        VertexShader GLSL100:   MatDefs/MyShader/MyShader.vert
        FragmentShader GLSL100: MatDefs/MyShader/MyShader.frag
        
        WorldParameters {
            WorldViewProjectionMatrix
        }
    }
}
```

### Using in Java

```java
Material mat = new Material(assetManager, "MatDefs/MyShader/MyShader.j3md");
mat.setColor("Color", ColorRGBA.Red);
geometry.setMaterial(mat);
```

## JME3 Global Uniforms (World Parameters)

Declare in `WorldParameters` block, access with `g_` prefix:

| Uniform | Type | Description |
|---------|------|-------------|
| WorldViewProjectionMatrix | mat4 | Full transform to screen |
| WorldViewMatrix | mat4 | World + View transform |
| WorldMatrix | mat4 | Object to world space |
| ViewMatrix | mat4 | World to view space |
| ProjectionMatrix | mat4 | View to projection space |
| NormalMatrix | mat3 | For transforming normals |
| ViewProjectionMatrix | mat4 | View + Projection |
| WorldMatrixInverse | mat4 | Inverse world matrix |
| CameraPosition | vec3 | Camera location in world |
| Time | float | Time since start (seconds) |
| Resolution | vec2 | Screen resolution |

Full list: [UniformBinding.java](https://github.com/jMonkeyEngine/jmonkeyengine/blob/master/jme3-core/src/main/java/com/jme3/shader/UniformBinding.java)

## JME3 Vertex Attributes

Available mesh attributes (access with `in` prefix):

| Attribute | Type | GLSL Name |
|-----------|------|-----------|
| Position | vec3/vec4 | inPosition |
| Normal | vec3 | inNormal |
| Tangent | vec4 | inTangent |
| TexCoord | vec2 | inTexCoord |
| TexCoord2 | vec2 | inTexCoord2 |
| Color | vec4 | inColor |
| BoneWeight | vec4 | inHWBoneWeight |
| BoneIndex | vec4 | inHWBoneIndex |

Full list: [VertexBuffer.java Type enum](https://github.com/jMonkeyEngine/jmonkeyengine/blob/master/jme3-core/src/main/java/com/jme3/scene/VertexBuffer.java)

## Material Parameters

### Declaring Parameters

In `.j3md` MaterialParameters block:
```
MaterialParameters {
    Color Color           // ColorRGBA
    Texture2D ColorMap    // Texture
    Float Shininess       // float
    Boolean UseTexture    // boolean
    Vector3 Scale         // Vector3f
}
```

### Using Defines (Conditional Compilation)

```
MaterialParameters {
    Texture2D ColorMap
    Color Color
}
Technique {
    Defines {
        HAS_COLORMAP : ColorMap
        HAS_COLOR : Color
    }
    // ...
}
```

In shader:
```glsl
uniform vec4 m_Color;

#ifdef HAS_COLORMAP
    uniform sampler2D m_ColorMap;
#endif

void main() {
    #ifdef HAS_COLORMAP
        gl_FragColor = texture2D(m_ColorMap, texCoord);
    #else
        gl_FragColor = m_Color;
    #endif
}
```

## Lighting Uniforms

For techniques with `LightMode MultiPass`:

| Uniform | Type | Description |
|---------|------|-------------|
| g_LightColor | vec4 | Light color |
| g_LightPosition | vec4 | Position (xyz), 1/range (w) |
| g_LightDirection | vec4 | Direction (xyz), spot angle cos (w) |
| g_AmbientLightColor | vec4 | Ambient light color |

## Textured Shader Example

### Vertex Shader (Textured.vert)

```glsl
uniform mat4 g_WorldViewProjectionMatrix;

attribute vec3 inPosition;
attribute vec2 inTexCoord;

varying vec2 texCoord;

void main() {
    texCoord = inTexCoord;
    gl_Position = g_WorldViewProjectionMatrix * vec4(inPosition, 1.0);
}
```

### Fragment Shader (Textured.frag)

```glsl
uniform sampler2D m_ColorMap;
uniform vec4 m_Color;

varying vec2 texCoord;

void main() {
    vec4 texColor = texture2D(m_ColorMap, texCoord);
    gl_FragColor = texColor * m_Color;
}
```

### Material Definition (Textured.j3md)

```
MaterialDef Textured {
    MaterialParameters {
        Texture2D ColorMap
        Color Color : 1.0 1.0 1.0 1.0   // Default white
    }
    Technique {
        VertexShader GLSL100:   MatDefs/Textured/Textured.vert
        FragmentShader GLSL100: MatDefs/Textured/Textured.frag
        
        WorldParameters {
            WorldViewProjectionMatrix
        }
        
        Defines {
            HAS_COLORMAP : ColorMap
        }
    }
}
```

## Shader Node System

Shader Nodes allow modular shader composition by connecting nodes together, similar to Blender's node system.

### Why Shader Nodes?

- Modular: Reusable shader pieces
- Maintainable: Easier than monolithic shaders
- Extensible: Users can add features without forking

### Shader Node Definition (.j3sn)

```
ShaderNodeDefinitions {
    ShaderNodeDefinition ColorMult {
        Type: Fragment
        Shader GLSL100: MatDefs/ShaderNodes/colorMult.frag
        
        Documentation {
            Multiplies two colors together
            @input color1 the first color
            @input color2 the second color
            @output outColor the resulting color
        }
        
        Input {
            vec4 color1
            vec4 color2
        }
        
        Output {
            vec4 outColor
        }
    }
}
```

### Shader Node Code (colorMult.frag)

```glsl
void main() {
    outColor = color1 * color2;
}
```

**Important**: Do NOT declare inputs/outputs as uniforms - the generator handles this.

### Using Shader Nodes in Material Definition

```
MaterialDef NodeBased {
    MaterialParameters {
        Color Color
    }
    Technique {
        WorldParameters {
            WorldViewProjectionMatrix
        }
        
        VertexShaderNodes {
            ShaderNode CommonVert {
                Definition : CommonVert : Common/MatDefs/ShaderNodes/Common/CommonVert.j3sn
                InputMappings {
                    worldViewProjectionMatrix = WorldParam.WorldViewProjectionMatrix
                    modelPosition = Global.position.xyz
                }
                OutputMappings {
                    Global.position = projPosition
                }
            }
        }
        
        FragmentShaderNodes {
            ShaderNode ColorMult {
                Definition : ColorMult : MatDefs/ShaderNodes/ColorMult.j3sn
                InputMappings {
                    color1 = MatParam.Color
                    color2 = Global.color
                }
                OutputMappings {
                    Global.color = outColor
                }
            }
        }
    }
}
```

### Input Mapping Namespaces

| Namespace | Description | Example |
|-----------|-------------|---------|
| MatParam | Material parameter | `MatParam.Color` |
| WorldParam | World parameter | `WorldParam.WorldViewProjectionMatrix` |
| Attr | Vertex attribute | `Attr.inPosition` |
| Global | Global shader variable | `Global.position` |
| NodeName | Previous node output | `CommonVert.projPosition` |

### Conditional Nodes

```
ShaderNode MyNode {
    Definition : MyDef : path/to/def.j3sn
    Condition : Color || ColorMap    // Only active if Color OR ColorMap is set
    InputMappings {
        // ...
    }
}
```

## OpenGL 3+ Compatibility

GLSL 1.3+ deprecated built-in variables. Use jME3 equivalents:

| Deprecated (GLSL 1.2) | jME3 Equivalent |
|-----------------------|-----------------|
| gl_Vertex | inPosition |
| gl_Normal | inNormal |
| gl_Color | inColor |
| gl_MultiTexCoord0 | inTexCoord |
| gl_ModelViewMatrix | g_WorldViewMatrix |
| gl_ProjectionMatrix | g_ProjectionMatrix |
| gl_ModelViewProjectionMatrix | g_WorldViewProjectionMatrix |
| gl_NormalMatrix | g_NormalMatrix |

## Best Practices

1. **Start Simple**: Begin with Unshaded.j3md modifications
2. **Use Defines**: Conditional code for optional features
3. **Check GLSL Version**: Specify minimum version in .j3md
4. **Prefix Conventions**: `g_` world, `m_` material, `in` attributes
5. **Test Incrementally**: Add features one at a time
6. **Document Nodes**: Required documentation block in Shader Nodes

## Debugging Shaders

```java
// Print generated shader code
mat.getMaterialDef().getTechniqueDefs().forEach(tech -> {
    System.out.println(tech.getShaderProgramLanguage());
});
```

Check console for GLSL compilation errors when loading materials.

## File Locations in This Project

- Custom MatDefs: `infinity/assets/MatDefs/`
- Shader files: `infinity/assets/Shaders/`
- Built-in MatDefs: See jME3 `Common/MatDefs/`

## References

- [JME3 and Shaders](https://wiki.jmonkeyengine.org/docs/3.9/core/shader/jme3_shaders.html)
- [Shader Node System](https://wiki.jmonkeyengine.org/docs/3.9/core/shader/jme3_shadernodes.html)
- [Material Specification](https://wiki.jmonkeyengine.org/docs/3.9/core/material/material_specification.html)
- [GLSL Reference](https://www.khronos.org/opengl/wiki/GLSL_Type)
