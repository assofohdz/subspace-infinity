#import "Common/ShaderLib/GLSLCompat.glsllib"
#import "Common/ShaderLib/Instancing.glsllib"

attribute vec3 inPosition;
attribute vec2 inTexCoord;
attribute vec3 inNormal;
attribute vec4 inColor;

varying vec2 texCoord1;
varying vec4 vertColor;
// View-space vert position + normal. jME's SinglePass lighting expresses
// g_LightData[].position in view space, so the frag needs its fragment
// position in the same space (see SPLighting.frag / .vert).
varying vec3 vViewPos;
varying vec3 vViewNormal;
varying vec3 vWorldPos;

// Animation atlas params — picks which atlas frame to display at g_Time.
uniform int m_numTilesX;
uniform int m_numTilesY;
uniform int m_numTilesOffsetX;
uniform int m_numTilesOffsetY;
uniform float m_StartTime;
uniform float m_Speed;
uniform float g_Time;

void main(){
    vertColor = inColor;

    // Frame-step UV: incoming inTexCoord is [0,1], divide by atlas grid + add
    // current frame's cell offset. Math copied from AnimateMultilineSpriteUnshaded.vert.
    float tileDistance = float((g_Time - m_StartTime) * m_Speed);
    int selectedTileX = int(mod(float(tileDistance), float(m_numTilesX))) + m_numTilesOffsetX;
    int selectedTileY = (m_numTilesY - 1)
        - (int(mod(float(tileDistance / float(m_numTilesX)), float(m_numTilesY))) + m_numTilesOffsetY);
    texCoord1.x = inTexCoord.x / float(m_numTilesX) + float(selectedTileX) / float(m_numTilesX);
    texCoord1.y = inTexCoord.y / float(m_numTilesY) + float(selectedTileY) / float(m_numTilesY);

    vec4 modelSpacePos = vec4(inPosition, 1.0);
    vViewPos = TransformWorldView(modelSpacePos).xyz;
    vViewNormal = normalize(TransformNormal(inNormal));
    vWorldPos = TransformWorld(modelSpacePos).xyz;

    gl_Position = TransformWorldViewProjection(modelSpacePos);
}
