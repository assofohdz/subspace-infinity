#import "Common/ShaderLib/GLSLCompat.glsllib"

uniform sampler2D m_ColorMap;

#ifdef DISCARD_ALPHA
    uniform float m_AlphaDiscardThreshold;
#endif

// jME single-pass lighting data. NB_LIGHTS = active light slots * 3 (each
// light is 3 vec4s: color+type, pos+invRadius, spotDir+angle). Positions
// are in VIEW space — that's why the frag uses vViewPos / vViewNormal.
uniform vec4 g_LightData[NB_LIGHTS];

varying vec2 texCoord1;
varying vec4 vertColor;
varying vec3 vViewPos;
varying vec3 vViewNormal;

void main(){
    vec4 color = texture2D(m_ColorMap, texCoord1);

    #ifdef DISCARD_ALPHA
        if(color.a < m_AlphaDiscardThreshold){
           discard;
        }
    #endif

    // Baked MOSS voxel lighting from vertex colors.
    const float SUN_SCALE = 0.5;
    const float POOL_GAIN = 3.0;
    vec3 sun = vec3(vertColor.a * SUN_SCALE);
    vec3 pool = vertColor.rgb * POOL_GAIN;
    float coreIntensity = max(max(vertColor.r, vertColor.g), vertColor.b);
    vec3 core = vec3(smoothstep(0.7, 1.0, coreIntensity));

    // Dynamic jME point lights — omnidirectional, distance-only attenuation.
    // Skipping dot(N,L) because in a top-down game we want every tile lit
    // regardless of the light's angle of incidence.
    //
    // The light's actual jME radius (lightData1.w = invRadius) is inflated
    // well beyond the visible pool size so that DefaultLightFilter keeps
    // the light in many leaves' lists at once and leaves don't pop on/off
    // as the ship crosses boundaries. We compute attenuation against a
    // SHADER-LOCAL visual radius instead, which keeps the pool at the
    // intended size regardless of the inflated filter radius.
    const float SHIP_VISUAL_RADIUS = 25.0;
    vec3 dynamic = vec3(0.0);
    for (int i = 0; i < NB_LIGHTS; i += 3) {
        vec4 lightColor = g_LightData[i];
        vec4 lightData1 = g_LightData[i + 1];
        float type = lightColor.w;
        float atten;
        if (type < 0.5) {
            atten = 1.0;
        } else {
            float dist = length(lightData1.xyz - vViewPos);
            atten = max(0.0, 1.0 - dist / SHIP_VISUAL_RADIUS);
            atten = atten * atten;
        }
        dynamic += lightColor.rgb * atten;
    }

    vec3 light = min(sun + pool + core * 0.8 + dynamic, vec3(1.0));
    gl_FragColor = vec4(color.rgb * light, color.a);
}
