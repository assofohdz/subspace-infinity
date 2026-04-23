#import "Common/ShaderLib/GLSLCompat.glsllib"
#import "Common/ShaderLib/Instancing.glsllib"

attribute vec3 inPosition;
attribute vec2 inTexCoord;
attribute vec4 inColor;

varying vec2 texCoord1;
varying vec4 vertColor;

void main(){
    texCoord1 = inTexCoord;
    vertColor = inColor;
    gl_Position = TransformWorldViewProjection(vec4(inPosition, 1.0));
}
