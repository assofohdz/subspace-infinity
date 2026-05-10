#import "Common/ShaderLib/GLSLCompat.glsllib"

uniform sampler2D m_ColorMap;

#ifdef DISCARD_ALPHA
    uniform float m_AlphaDiscardThreshold;
#endif

uniform float m_PoolGain;
uniform float m_SunScale;
uniform float m_Exposure;
uniform float m_TextureGamma;

// jME single-pass lighting data. NB_LIGHTS = active light slots * 3 (each
// light is 3 vec4s: color+type, pos+invRadius, spotDir+angle). Positions
// are in VIEW space — that's why the frag uses vViewPos / vViewNormal.
uniform vec4 g_LightData[NB_LIGHTS];
// Ambient lights are NOT packed into g_LightData in SinglePass mode; they
// are summed into this separate uniform by SinglePassLightingLogic.
uniform vec4 g_AmbientLightColor;

varying vec2 texCoord1;
varying vec4 vertColor;
varying vec3 vViewPos;
varying vec3 vViewNormal;
varying vec3 vWorldPos;

void main(){
    vec4 color = texture2D(m_ColorMap, texCoord1);

    #ifdef DISCARD_ALPHA
        if(color.a < m_AlphaDiscardThreshold){
           discard;
        }
    #endif

    // Brighten mid-tones of the tile texture before lighting. Subspace tile
    // palettes are dark by default; without this, even max-brightness light
    // produces dim output because color.rgb * light preserves texture
    // darkness. Gamma < 1 lifts mids, leaves full-white alone.
    // 1.0 = no boost, 0.7 = mild, 0.5 = aggressive.
    color.rgb = pow(color.rgb, vec3(m_TextureGamma));

    #ifdef DEBUG_LEAF_GRID
        // Checker-tint each 32×32 leaf with one of four distinct colors based
        // on (leafX mod 2, leafZ mod 2). Adjacent leaves always differ, so the
        // color transition IS the leaf boundary. Texture is preserved so you
        // can still see which block popped.
        const float LEAF_SIZE = 32.0;
        vec2 leafXZ = floor(vWorldPos.xz / LEAF_SIZE);
        float parityX = mod(leafXZ.x, 2.0);
        float parityZ = mod(leafXZ.y, 2.0);
        vec3 tint;
        if (parityX < 0.5 && parityZ < 0.5)       tint = vec3(1.0, 0.5, 0.5);
        else if (parityX >= 0.5 && parityZ < 0.5) tint = vec3(0.5, 1.0, 0.5);
        else if (parityX < 0.5 && parityZ >= 0.5) tint = vec3(0.5, 0.5, 1.0);
        else                                      tint = vec3(1.0, 1.0, 0.5);
        gl_FragColor = vec4(color.rgb * tint, color.a);
        return;
    #endif

    #ifdef DEBUG_SHIP_LIGHT
        // Blue   = no point light in this fragment's light list
        // Green  = inside the nearest light's radius (bright at center, dim at edge)
        // White  = exactly at d = radius (the filter/attenuation boundary)
        // Red    = outside the nearest light's radius but light is still in the list
        float dbgDist = 1e6;
        float dbgInvR = 0.0;
        for (int j = 0; j < NB_LIGHTS; j += 3) {
            vec4 lc = g_LightData[j];
            vec4 ld = g_LightData[j + 1];
            if (lc.w >= 0.5) {
                float d = length(ld.xyz - vViewPos);
                if (d < dbgDist) {
                    dbgDist = d;
                    dbgInvR = ld.w;
                }
            }
        }
        float DBG_R = 1.0 / max(dbgInvR, 0.0001);
        vec3 dbg;
        if (dbgDist > 9e5) {
            dbg = vec3(0.0, 0.0, 1.0);
        } else if (abs(dbgDist - DBG_R) < 0.3) {
            dbg = vec3(1.0);
        } else if (dbgDist < DBG_R) {
            dbg = vec3(0.0, 1.0 - dbgDist / DBG_R, 0.0);
        } else {
            float t = clamp((dbgDist - DBG_R) / 75.0, 0.0, 1.0);
            dbg = vec3(1.0 - t, 0.0, 0.0);
        }
        gl_FragColor = vec4(dbg, 1.0);
        return;
    #endif

    // Baked MOSS voxel lighting from vertex colors.
    vec3 sun = vec3(vertColor.a * m_SunScale);
    vec3 pool = vertColor.rgb * m_PoolGain;
    float coreIntensity = max(max(vertColor.r, vertColor.g), vertColor.b);
    vec3 core = vec3(smoothstep(0.7, 1.0, coreIntensity));

    // Dynamic jME point lights — omnidirectional, distance-only attenuation.
    // Skipping dot(N,L) because in a top-down game we want every tile lit
    // regardless of the light's angle of incidence.
    //
    // Quadratic falloff (linear squared) against the light's own radius
    // (lightData1.w = 1/radius). Attenuation reaches exactly 0 at the filter
    // boundary (no pop) and drops steeply near the edge so the visible pool
    // matches the configured radius instead of bleeding outward via bright
    // colors + bloom.
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
            float linear = max(0.0, 1.0 - dist * lightData1.w);
            atten = linear * linear;
        }
        dynamic += lightColor.rgb * atten;
    }

    // Exponential tone-map: 1 - exp(-x * EXPOSURE). Single exposure knob.
    // Brighter mid-range than Reinhard (x=1 -> 0.78 at EXPOSURE=1.5 vs 0.5),
    // still asymptotes to 1.0 so values above 1 roll off smoothly and the
    // bloom filter sees them before they saturate.
    vec3 light = g_AmbientLightColor.rgb + sun + pool + core * 0.8 + dynamic;
    light = vec3(1.0) - exp(-light * m_Exposure);
    gl_FragColor = vec4(color.rgb * light, color.a);
}
