#import "Common/ShaderLib/GLSLCompat.glsllib"
#import "Common/ShaderLib/Skinning.glsllib"
#import "Common/ShaderLib/Instancing.glsllib"

attribute vec3 inPosition;

#if defined(HAS_COLORMAP) || (defined(HAS_LIGHTMAP) && !defined(SEPARATE_TEXCOORD))
    #define NEED_TEXCOORD1
#endif

attribute vec2 inTexCoord;
attribute vec2 inTexCoord2;
attribute vec4 inColor;

varying vec2 texCoord1;
varying vec2 texCoord2;

//---->>
uniform int m_numTilesX;
uniform int m_numTilesY;
uniform int m_numTilesOffsetX;
uniform int m_numTilesOffsetY;
uniform float m_StartTime;
uniform float m_Speed; 
uniform float g_Tpf;
uniform float g_Time;
//<----

varying vec4 vertColor;
#ifdef HAS_POINTSIZE
    uniform float m_PointSize;
#endif

void main(){
    #ifdef NEED_TEXCOORD1
        texCoord1 = inTexCoord;
    #endif

    #ifdef SEPARATE_TEXCOORD
        texCoord2 = inTexCoord2;
    #endif

    #ifdef HAS_VERTEXCOLOR
        vertColor = inColor;
    #endif

    #ifdef HAS_POINTSIZE
        gl_PointSize = m_PointSize;
    #endif

    vec4 modelSpacePos = vec4(inPosition, 1.0);
    #ifdef NUM_BONES
        Skinning_Compute(modelSpacePos);
    #endif

    gl_Position = TransformWorldViewProjection(modelSpacePos);


    //---->>
    float tileDistance = float((g_Time-m_StartTime)*m_Speed);
    int selectedTileX = int(mod(float(tileDistance), m_numTilesX))+m_numTilesOffsetX;
    int selectedTileY = (m_numTilesY-1) - (int(mod(float(tileDistance / m_numTilesX), m_numTilesY))+m_numTilesOffsetY);

    texCoord1.x = (float(float(inTexCoord.x/m_numTilesX) + float(selectedTileX)/float(m_numTilesX)));
    texCoord1.y = (float(float(inTexCoord.y/m_numTilesY) + float(selectedTileY)/float(m_numTilesY)));
    //<----
    //texCoord = inTexCoord;
}