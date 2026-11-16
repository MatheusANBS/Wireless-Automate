#version 150

uniform sampler2D Sampler0;

uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;
uniform float AlphaCut;
uniform float AirFog;

in float vertexDistance;
in vec4 vertexColor;
in vec4 lightMapColor;
in vec2 texCoord0;
in float dissolve;

out vec4 fragColor;

// A trama Bayer do filme (o XFS de shared.js).
float b2(vec2 a) {
    a = floor(a);
    return fract(dot(a, vec2(0.5, a.y * 0.75)));
}

float b4(vec2 a) {
    return b2(a * 0.5) * 0.25 + b2(a);
}

// O mesmo valor do linear_fog do vanilla (fog.glsl), sem misturar a cor.
float fog_value(float dist, float fogStart, float fogEnd) {
    if (dist <= fogStart) {
        return 0.0;
    }
    return dist < fogEnd ? smoothstep(fogStart, fogEnd, dist) : 1.0;
}

void main() {
    vec4 color = texture(Sampler0, texCoord0);
    if (color.a < AlphaCut) {
        discard;
    }
    ivec2 sz = textureSize(Sampler0, 0);
    vec2 tx = floor(texCoord0 * vec2(sz));
    if (dissolve > 0.0 && b4(tx) < dissolve) {
        discard;
    }
    color *= vertexColor * ColorModulator;
    color *= lightMapColor;
    // AirFog e o teto da nevoa: 0,35 no ar, 1,0 na agua, na lava e na cegueira (o Java escolhe no desenho).
    float fog = min(fog_value(vertexDistance, FogStart, FogEnd) * FogColor.a, AirFog);
    color.rgb = mix(color.rgb, FogColor.rgb, fog);
    fragColor = color;
}
