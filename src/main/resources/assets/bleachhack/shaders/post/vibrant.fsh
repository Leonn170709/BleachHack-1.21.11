#version 330

// Ported from Minecraft 1.19.4's shaders/program/color_convolve.fsh (Mojang), used there as the
// "Vibrant" mode via a Saturation=1.4 uniform override. Same reasoning as desaturate.fsh - 1.21.11's
// own color_convolve.fsh hardcodes a different Saturation const, so this bakes the original value
// instead of reusing it.

uniform sampler2D InSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

in vec2 texCoord;

const vec3 Gray = vec3(0.3, 0.59, 0.11);
const float Saturation = 1.4;

out vec4 fragColor;

void main() {
    vec4 InTexel = texture(InSampler, texCoord);

    float Luma = dot(InTexel.rgb, Gray);
    vec3 Chroma = InTexel.rgb - Luma;
    vec3 OutColor = (Chroma * Saturation) + Luma;

    fragColor = vec4(OutColor, 1.0);
}
