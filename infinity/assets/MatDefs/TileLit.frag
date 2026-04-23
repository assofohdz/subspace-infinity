#import "Common/ShaderLib/GLSLCompat.glsllib"

uniform sampler2D m_ColorMap;

#ifdef DISCARD_ALPHA
    uniform float m_AlphaDiscardThreshold;
#endif

varying vec2 texCoord1;
varying vec4 vertColor;

void main(){
    vec4 color = texture2D(m_ColorMap, texCoord1);

    #ifdef DISCARD_ALPHA
        if(color.a < m_AlphaDiscardThreshold){
           discard;
        }
    #endif

    // MOSS voxel lighting convention:
    //   vertColor.rgb = colored light (from emissive cells, 0..1 per channel)
    //   vertColor.a   = sunlight (0..1)
    //
    // Layered formula to get a "torch-glow" look:
    //   1) sun gives a dim ambient floor (world stays visibly dark)
    //   2) pool is linear distance-from-emitter (MOSS flood fill) scaled up so
    //      near cells saturate and far cells fall off gracefully — NOT a power
    //      curve, we want the natural soft gradient the reference screenshot
    //      shows (bright core → warm amber glow → ambient)
    //   3) core: an extra boost for cells very close to the emitter
    //      (rgb > 0.7) to punch the center bright-white so it reads as a torch
    //      flame rather than a warm blob
    const float SUN_SCALE = 0.5;
    const float POOL_GAIN = 3.0;
    vec3 sun = vec3(vertColor.a * SUN_SCALE);
    vec3 pool = vertColor.rgb * POOL_GAIN;
    // Torch-core highlight: pushes bright tiles toward white.
    float coreIntensity = max(max(vertColor.r, vertColor.g), vertColor.b);
    vec3 core = vec3(smoothstep(0.7, 1.0, coreIntensity));
    vec3 light = min(sun + pool + core * 0.8, vec3(1.0));
    gl_FragColor = vec4(color.rgb * light, color.a);
}
