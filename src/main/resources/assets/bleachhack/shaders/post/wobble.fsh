#version 330

// Ported from Minecraft 1.19.4's shaders/program/wobble.fsh (Mojang). The old "Time" uniform was
// driven manually from Java every frame - 1.21.11 post-effect uniforms are only read once at
// load, so there's nothing to keep pushing a value into. Using the engine's own GameTime (from
// globals.glsl, already updated every frame for things like animated water/lava) instead - it's
// not the same clock as the original, so the wobble phase/speed won't match 1:1, but it's a
// real per-frame value instead of a frozen one.
#moj_import <minecraft:globals.glsl>

uniform sampler2D InSampler;

in vec2 texCoord;

const vec2 Frequency = vec2(512.0, 288.0);
const vec2 WobbleAmount = vec2(0.002, 0.002);

out vec4 fragColor;

vec3 hue(float h) {
    float r = abs(h * 6.0 - 3.0) - 1.0;
    float g = 2.0 - abs(h * 6.0 - 2.0);
    float b = 2.0 - abs(h * 6.0 - 4.0);
    return clamp(vec3(r, g, b), 0.0, 1.0);
}

vec3 HSVtoRGB(vec3 hsv) {
    return ((hue(hsv.x) - 1.0) * hsv.y + 1.0) * hsv.z;
}

vec3 RGBtoHSV(vec3 rgb) {
    vec3 hsv = vec3(0.0);
    hsv.z = max(rgb.r, max(rgb.g, rgb.b));
    float minC = min(rgb.r, min(rgb.g, rgb.b));
    float c = hsv.z - minC;

    if (c != 0.0) {
        hsv.y = c / hsv.z;
        vec3 delta = (hsv.z - rgb) / c;
        delta.rgb -= delta.brg;
        delta.rg += vec2(2.0, 4.0);
        if (rgb.r >= hsv.z) {
            hsv.x = delta.b;
        } else if (rgb.g >= hsv.z) {
            hsv.x = delta.r;
        } else {
            hsv.x = delta.g;
        }
        hsv.x = fract(hsv.x / 6.0);
    }
    return hsv;
}

void main() {
    float time = GameTime;
    float xOffset = sin(texCoord.y * Frequency.x + time * 3.1415926535 * 2.0) * WobbleAmount.x;
    float yOffset = cos(texCoord.x * Frequency.y + time * 3.1415926535 * 2.0) * WobbleAmount.y;
    vec2 offset = vec2(xOffset, yOffset);
    vec4 rgb = texture(InSampler, texCoord + offset);
    vec3 hsv = RGBtoHSV(rgb.rgb);
    hsv.x = fract(hsv.x + time);
    fragColor = vec4(HSVtoRGB(hsv), 1.0);
}
