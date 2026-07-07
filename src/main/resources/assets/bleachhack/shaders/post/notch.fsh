#version 330

// Ported from Minecraft 1.19.4's shaders/program/notch.fsh (Mojang). The original sampled a 4x4
// procedurally-generated DitherSampler texture (registered as an "auxtarget" in the old post
// chain format, which 1.21.11's post_effect JSON has no equivalent for) - replaced with an
// equivalent 4x4 Bayer ordered-dither matrix computed inline instead of sampled from a texture.
// Same dithering effect, no aux texture needed.

uniform sampler2D InSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

in vec2 texCoord;

out vec4 fragColor;

const mat4 BayerMatrix = mat4(
     0.0,  8.0,  2.0, 10.0,
    12.0,  4.0, 14.0,  6.0,
     3.0, 11.0,  1.0,  9.0,
    15.0,  7.0, 13.0,  5.0
) / 16.0;

void main() {
    vec2 halfSize = InSize * 0.5;

    vec2 steppedCoord = texCoord;
    steppedCoord.x = float(int(steppedCoord.x * halfSize.x)) / halfSize.x;
    steppedCoord.y = float(int(steppedCoord.y * halfSize.y)) / halfSize.y;

    ivec2 ditherCoord = ivec2(mod(steppedCoord * halfSize, 4.0));
    float noise = BayerMatrix[ditherCoord.x][ditherCoord.y] - 0.5;

    vec4 col = texture(InSampler, steppedCoord) + noise * vec4(1.0 / 12.0, 1.0 / 12.0, 1.0 / 6.0, 1.0);
    float r = float(int(col.r * 8.0)) / 8.0;
    float g = float(int(col.g * 8.0)) / 8.0;
    float b = float(int(col.b * 4.0)) / 4.0;
    fragColor = vec4(r, g, b, 1.0);
}
