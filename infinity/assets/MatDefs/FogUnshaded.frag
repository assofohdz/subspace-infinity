#import "Common/ShaderLib/GLSLCompat.glsllib"

#if defined(HAS_GLOWMAP) || defined(HAS_COLORMAP) || (defined(HAS_LIGHTMAP) && !defined(SEPARATE_TEXCOORD))
    #define NEED_TEXCOORD1
#endif

#if defined(DISCARD_ALPHA)
    uniform float m_AlphaDiscardThreshold;
#endif

uniform vec4 m_Color;
uniform sampler2D m_ColorMap;
uniform sampler2D m_LightMap;

uniform vec4 m_FogColor;
uniform float m_FogDepth;

varying vec2 texCoord1;
varying vec2 texCoord2;

varying vec4 vertColor;

varying float cameraDistance;

void main(){
    vec4 color = vec4(1.0);

    #ifdef HAS_COLORMAP
        color *= texture2D(m_ColorMap, texCoord1);     
    #endif

    #ifdef HAS_VERTEXCOLOR
        color *= vertColor;
    #endif

    #ifdef HAS_COLOR
        color *= m_Color;
    #endif

    #ifdef HAS_LIGHTMAP
        #ifdef SEPARATE_TEXCOORD
            color.rgb *= texture2D(m_LightMap, texCoord2).rgb;
        #else
            color.rgb *= texture2D(m_LightMap, texCoord1).rgb;
        #endif
    #endif

    #if defined(DISCARD_ALPHA)
        if(color.a < m_AlphaDiscardThreshold){
           discard;
        }
    #endif
 
    #ifdef HAS_FOG
        // The problem with using the drag depth is that fog will change
        // as you turn the camera.   
        //float originalZ = gl_FragCoord.z / gl_FragCoord.w;
        //float fogFactor = min(1.0, originalZ / m_FogDepth);
 
        // Might really want exp or exp2 fog here... linear seems ok for
        // space lighting versus real fog.   
        float fogFactor = min(1.0, cameraDistance / m_FogDepth);
        color = mix(color, m_FogColor, fogFactor);
    #endif 

    gl_FragColor = color;
}
