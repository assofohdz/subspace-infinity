#import "Common/ShaderLib/GLSLCompat.glsllib"

uniform sampler2D m_ColorMap;

#ifdef DISCARD_ALPHA
    uniform float m_AlphaDiscardThreshold;
#endif

varying vec2 texCoord1;

void main(){
    vec4 color = texture2D(m_ColorMap, texCoord1);

    #ifdef DISCARD_ALPHA
        if(color.a < m_AlphaDiscardThreshold){
           discard;
        }
    #endif

    gl_FragColor = color;
}
