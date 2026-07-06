#version 330

// Ported from Meteor Client's outline.frag (MeteorDevelopment/meteor-client,
// utils/render/postprocess/PostProcessShaders) - same distance-field glow algorithm,
// adapted to vanilla's std140 post-effect uniform format.

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform OutlineConfig {
    float Width;
    float FillOpacity;
    float GlowMultiplier;
    float ShapeMode;
};

uniform sampler2D InSampler;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec4 center = texture(InSampler, texCoord);
    int shapeMode = int(ShapeMode);

    // ShapeMode: 0 = Lines+Sides (default), 1 = Lines-only (no fill), 2 = Sides-only (no glow).
    // Inside the entity silhouette.
    if (center.a > 0.0) {
        if (shapeMode == 1) {
            // Lines-only: no fill.
            discard;
        }

        fragColor = vec4(center.rgb, center.a * FillOpacity);
        return;
    }

    // Outside the silhouette.
    if (shapeMode == 2) {
        // Sides-only: no glow.
        discard;
    }

    int width = int(Width);
    float minDist = float(width) + 1.0;
    float dist = minDist;
    vec3 glowColor = vec3(0.0);

    for (int x = -width; x <= width; x++) {
        for (int y = -width; y <= width; y++) {
            vec4 sampled = texture(InSampler, texCoord + vec2(x, y) / InSize);
            if (sampled.a > 0.0) {
                float d = length(vec2(x, y));
                if (d < dist) {
                    dist = d;
                    glowColor = sampled.rgb;
                }
            }
        }
    }

    if (dist > float(width)) {
        discard;
    }

    fragColor = vec4(glowColor, min((1.0 - dist / minDist) * GlowMultiplier, 1.0));
}
