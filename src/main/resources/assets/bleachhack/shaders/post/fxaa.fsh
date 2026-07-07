#version 330

// Ported from Minecraft 1.19.4's shaders/program/fxaa.fsh (Mojang). The original vertex shader
// precomputed a "posPos" varying (center + a quarter-pixel NW offset) for the fragment shader to
// reuse - 1.21.11's shared minecraft:core/screenquad vertex shader only provides texCoord, so the
// NW offset is just computed inline here instead.

uniform sampler2D InSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

in vec2 texCoord;

out vec4 fragColor;

const float FXAA_REDUCE_MIN = 1.0 / 128.0;
const float SpanMax = 8.0;
const float ReduceMul = 0.125;

vec3 FxaaPixelShader(vec2 posM, vec2 posNW, sampler2D tex, vec2 rcpFrame) {
    vec3 rgbNW = texture(tex, posNW).xyz;
    vec3 rgbNE = textureOffset(tex, posNW, ivec2(1, 0)).xyz;
    vec3 rgbSW = textureOffset(tex, posNW, ivec2(0, 1)).xyz;
    vec3 rgbSE = textureOffset(tex, posNW, ivec2(1, 1)).xyz;

    vec3 rgbM = texture(tex, posM).xyz;

    vec3 luma = vec3(0.299, 0.587, 0.114);
    float lumaNW = dot(rgbNW, luma);
    float lumaNE = dot(rgbNE, luma);
    float lumaSW = dot(rgbSW, luma);
    float lumaSE = dot(rgbSE, luma);
    float lumaM  = dot(rgbM,  luma);

    float lumaMin = min(lumaM, min(min(lumaNW, lumaNE), min(lumaSW, lumaSE)));
    float lumaMax = max(lumaM, max(max(lumaNW, lumaNE), max(lumaSW, lumaSE)));

    vec2 dir;
    dir.x = -((lumaNW + lumaNE) - (lumaSW + lumaSE));
    dir.y =  ((lumaNW + lumaSW) - (lumaNE + lumaSE));

    float dirReduce = max((lumaNW + lumaNE + lumaSW + lumaSE) * (0.25 * ReduceMul), FXAA_REDUCE_MIN);
    float rcpDirMin = 1.0 / (min(abs(dir.x), abs(dir.y)) + dirReduce);
    dir = min(vec2(SpanMax, SpanMax), max(vec2(-SpanMax, -SpanMax), dir * rcpDirMin)) * rcpFrame.xy;

    vec3 rgbA = 0.5 * (
        texture(tex, posM + dir * (1.0 / 3.0 - 0.5)).xyz +
        texture(tex, posM + dir * (2.0 / 3.0 - 0.5)).xyz);
    vec3 rgbB = rgbA * 0.5 + 0.25 * (
        texture(tex, posM + dir * (0.0 / 3.0 - 0.5)).xyz +
        texture(tex, posM + dir * (3.0 / 3.0 - 0.5)).xyz);

    float lumaB = dot(rgbB, luma);

    if ((lumaB < lumaMin) || (lumaB > lumaMax)) {
        return rgbA;
    } else {
        return rgbB;
    }
}

void main() {
    vec2 rcpFrame = 1.0 / OutSize;
    vec2 posNW = texCoord - rcpFrame * 0.5;
    fragColor = vec4(FxaaPixelShader(texCoord, posNW, InSampler, rcpFrame), 1.0);
}
