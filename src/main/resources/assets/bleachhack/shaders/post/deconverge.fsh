#version 330

// Ported from Minecraft 1.19.4's shaders/program/deconverge.fsh (Mojang). The Converge*/
// RadialConverge* uniforms were live there; baked as consts here since 1.21.11 post-effect
// uniforms are read once at load, not per-frame.

uniform sampler2D InSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

in vec2 texCoord;

out vec4 fragColor;

const vec3 ConvergeX = vec3(-4.0,  0.0,  2.0);
const vec3 ConvergeY = vec3( 0.0, -4.0,  2.0);
const vec3 RadialConvergeX = vec3(1.0, 1.0, 1.0);
const vec3 RadialConvergeY = vec3(1.0, 1.0, 1.0);

void main() {
    vec2 oneTexel = 1.0 / InSize;

    vec3 CoordX = texCoord.x * RadialConvergeX;
    vec3 CoordY = texCoord.y * RadialConvergeY;

    CoordX += ConvergeX * oneTexel.x - (RadialConvergeX - 1.0) * 0.5;
    CoordY += ConvergeY * oneTexel.y - (RadialConvergeY - 1.0) * 0.5;

    float RedValue   = texture(InSampler, vec2(CoordX.x, CoordY.x)).r;
    float GreenValue = texture(InSampler, vec2(CoordX.y, CoordY.y)).g;
    float BlueValue  = texture(InSampler, vec2(CoordX.z, CoordY.z)).b;

    fragColor = vec4(RedValue, GreenValue, BlueValue, 1.0);
}
