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

void main(){
    texCoord1 = inTexCoord;
    vertColor = inColor;

    vec4 modelSpacePos = vec4(inPosition, 1.0);
    vViewPos = TransformWorldView(modelSpacePos).xyz;
    vViewNormal = normalize(TransformNormal(inNormal));
    vWorldPos = TransformWorld(modelSpacePos).xyz;

    gl_Position = TransformWorldViewProjection(modelSpacePos);
}
