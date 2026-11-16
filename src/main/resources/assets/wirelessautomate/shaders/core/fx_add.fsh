#version 150

uniform sampler2D Sampler0;

uniform vec4 ColorModulator;
uniform float AlphaCut;

in vec4 vertexColor;
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
    fragColor = color * vertexColor * ColorModulator;
}
