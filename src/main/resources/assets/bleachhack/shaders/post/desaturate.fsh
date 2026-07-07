#version 330

// Ported from Minecraft 1.19.4's shaders/program/color_convolve.fsh (Mojang), used there as the
// "Desaturate" mode via a Saturation=0.2 uniform override. 1.21.11's own color_convolve.fsh (kept
// for Creeper mode) hardcodes Saturation as a const 1.8, so it can't be reused for this - this is
// the same convolve+saturation math with Saturation baked to the old Desaturate value instead.

uniform sampler2D InSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

in vec2 texCoord;

const vec3 Gray = vec3(0.3, 0.59, 0.11);
const float Saturation = 0.2;

out vec4 fragColor;

void main() {
    vec4 InTexel = texture(InSampler, texCoord);

    float Luma = dot(InTexel.rgb, Gray);
    vec3 Chroma = InTexel.rgb - Luma;
    vec3 OutColor = (Chroma * Saturation) + Luma;

    fragColor = vec4(OutColor, 1.0);
}
