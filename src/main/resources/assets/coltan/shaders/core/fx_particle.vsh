#version 150

#moj_import <fog.glsl>

// One unit quad corner per vertex, one particle per instance.
in vec2 Corner;
in vec3 IPos;
in vec2 ISizeRoll;
in vec4 IUV;
in vec4 IColor;
in ivec2 ILight;

uniform sampler2D Sampler2;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform int FogShape;
uniform vec3 CamRight;
uniform vec3 CamUp;

out float vertexDistance;
out vec2 texCoord0;
out vec4 vertexColor;

void main() {
    // Same billboard as SingleQuadParticle: roll about the view axis, then the camera rotation.
    float c = cos(ISizeRoll.y);
    float s = sin(ISizeRoll.y);
    vec2 rolled = vec2(Corner.x * c - Corner.y * s, Corner.x * s + Corner.y * c);
    vec3 position = IPos + (CamRight * rolled.x + CamUp * rolled.y) * ISizeRoll.x;

    gl_Position = ProjMat * ModelViewMat * vec4(position, 1.0);
    vertexDistance = fog_distance(ModelViewMat, position, FogShape);
    texCoord0 = vec2(Corner.x < 0.0 ? IUV.z : IUV.x, Corner.y < 0.0 ? IUV.w : IUV.y);
    vertexColor = IColor * texelFetch(Sampler2, ILight / 16, 0);
}
