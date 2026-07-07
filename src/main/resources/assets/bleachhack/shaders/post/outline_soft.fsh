#version 330

// Ported from Minecraft 1.19.4's shaders/program/outline_soft.fsh (Mojang) - Pencil mode.
// LumaRamp/LumaLevel were live uniforms there; baked as consts here since 1.21.11 post-effect
// uniforms are read once at load, not per-frame.

uniform sampler2D InSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

in vec2 texCoord;

out vec4 fragColor;

const float LumaRamp = 16.0;
const float LumaLevel = 4.0;

void main() {
    vec2 oneTexel = 1.0 / InSize;

    vec4 center = texture(InSampler, texCoord);
    vec4 up     = texture(InSampler, texCoord + vec2(        0.0, -oneTexel.y));
    vec4 up2    = texture(InSampler, texCoord + vec2(        0.0, -oneTexel.y) * 2.0);
    vec4 down   = texture(InSampler, texCoord + vec2( oneTexel.x,         0.0));
    vec4 down2  = texture(InSampler, texCoord + vec2( oneTexel.x,         0.0) * 2.0);
    vec4 left   = texture(InSampler, texCoord + vec2(-oneTexel.x,         0.0));
    vec4 left2  = texture(InSampler, texCoord + vec2(-oneTexel.x,         0.0) * 2.0);
    vec4 right  = texture(InSampler, texCoord + vec2(        0.0,  oneTexel.y));
    vec4 right2 = texture(InSampler, texCoord + vec2(        0.0,  oneTexel.y) * 2.0);
    vec4 uDiff = abs(center - up);
    vec4 dDiff = abs(center - down);
    vec4 lDiff = abs(center - left);
    vec4 rDiff = abs(center - right);
    vec4 u2Diff = abs(center - up2);
    vec4 d2Diff = abs(center - down2);
    vec4 l2Diff = abs(center - left2);
    vec4 r2Diff = abs(center - right2);
    vec4 sum = uDiff + dDiff + lDiff + rDiff + u2Diff + d2Diff + l2Diff + r2Diff;
    vec4 gray = vec4(0.3, 0.59, 0.11, 0.0);
    float sumLuma = 1.0 - dot(clamp(sum, 0.0, 1.0), gray);

    float centerLuma = dot(center + (center - pow(center, vec4(LumaRamp))), gray);

    centerLuma = centerLuma - fract(centerLuma * LumaLevel) / LumaLevel;
    centerLuma = centerLuma * (LumaLevel / (LumaLevel - 1.0));
    centerLuma = centerLuma * sumLuma;

    fragColor = vec4(centerLuma, centerLuma, centerLuma, 1.0);
}
