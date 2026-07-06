#version 330

// Ported from Meteor Client's outline.frag (MeteorDevelopment/meteor-client,
// utils/render/postprocess/PostProcessShaders) - same distance-field glow algorithm.
//
// Width/fill/glow are baked as consts instead of live post-effect uniforms. An earlier
// version tried to hot-swap a fresh PostEffectProcessor (with per-frame uniform values)
// into ShaderLoader's cache whenever ESP's sliders changed, since post-effect uniforms
// are otherwise only read once at resource-load time - but that swap never actually took
// effect at runtime, so the *static* JSON-declared pass below kept rendering instead,
// which is why "Shader" mode always looked like a flat single-color outline no matter
// what the sliders were set to. AnarchyClient's working esp_outline.fsh (same hijack of
// vanilla's entity_outline post effect) uses this exact baked-const approach - proven to
// actually render Meteor's fill+glow, at the cost of the sliders no longer being live.

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

uniform sampler2D InSampler;

in vec2 texCoord;

out vec4 fragColor;

// Meteor's own literal defaults (width 2, fill 0.3, glow 3.5) read as barely more than a thin
// outline at typical resolutions - AnarchyClient's proven esp_outline.fsh tunes these up for an
// actually-visible glow+fill, so match those numbers instead of Meteor's raw slider defaults.
const int WIDTH = 4;
const float FILL_OPACITY = 0.25;
const float GLOW_MULTIPLIER = 3.0;

void main() {
    vec4 center = texture(InSampler, texCoord);

    // Inside the entity silhouette - translucent fill (Meteor's shapeMode "Both"/"Sides").
    if (center.a > 0.0) {
        fragColor = vec4(center.rgb, center.a * FILL_OPACITY);
        return;
    }

    // Outside the silhouette - soft glow falloff towards the nearest silhouette pixel.
    float minDist = float(WIDTH * WIDTH) + 1.0;
    vec3 glowColor = vec3(0.0);

    for (int x = -WIDTH; x <= WIDTH; x++) {
        for (int y = -WIDTH; y <= WIDTH; y++) {
            vec4 sampled = texture(InSampler, texCoord + vec2(x, y) / InSize);
            if (sampled.a > 0.0) {
                float d = float(x * x + y * y) - 1.0;
                if (d < minDist) {
                    minDist = d;
                    glowColor = sampled.rgb;
                }
            }
        }
    }

    float threshold = float(WIDTH * WIDTH);
    if (minDist > threshold) {
        discard;
    }

    fragColor = vec4(glowColor, min((1.0 - minDist / threshold) * GLOW_MULTIPLIER, 1.0));
}
