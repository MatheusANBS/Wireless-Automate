#version 150

#moj_import <wirelessautomate:wa_common.glsl>

uniform float GameTime;

in vec2 quad;
in vec4 params;
flat in ivec2 extra;

out vec4 fragColor;

const vec3 CORAL = vec3(0.949, 0.569, 0.478);
const vec3 WHITE = vec3(1.0, 0.94, 0.92);
// o raio da esfera em unidades do quad (HALF = 1,45 bloco): 0,34 bloco
const float SPHERE = 0.234;

void main() {
    vec2 p = quad * 2.0 - 1.0;
    float r = length(p);
    if (r > 1.0) discard;
    float t = GameTime * 1200.0;
    vec3 tier = params.rgb;
    float tune = params.a;
    float mis = 1.0 - tune;
    int detail = extra.y & 3;
    float press = extra.y >= 4 ? 0.5 + 0.5 * sin(t * 7.0) : 0.0;
    int octaves = detail >= 2 ? 4 : (detail == 1 ? 3 : 2);

    // o raio respira, encolhe sob pressão e treme na borda quando fora de sintonia
    float radius = SPHERE * (1.0 + 0.06 * sin(t * 2.0)) * (1.0 - press * 0.2);
    if (mis > 0.02) {
        radius += mis * 0.04 * wa_noise(vec3(normalize(p + vec2(1e-5)) * 2.5, t * 1.2));
    }

    vec3 col = vec3(0.0);

    // o corpo: uma esfera de verdade, com normal, convecção e temperatura
    float inside = 1.0 - smoothstep(radius - 0.012, radius, r);
    if (inside > 0.0) {
        float rr = min(r / radius, 1.0);
        float zz = sqrt(max(0.0, 1.0 - rr * rr));
        vec3 n = vec3(p / radius, zz);
        float ca = cos(t * 0.35);
        float sa = sin(t * 0.35);
        vec3 sn = vec3(ca * n.x + sa * n.z, n.y, -sa * n.x + ca * n.z);
        vec3 q = sn * 2.4;
        float w1 = wa_fbm(q + vec3(0.0, t * 0.30, 0.0), octaves);
        float w2 = wa_fbm(q * 1.7 + vec3(w1 * 1.8, -t * 0.22, w1 * 1.2), octaves);
        float heat = clamp(0.30 + 1.1 * (w2 - 0.5) + 0.35 * zz + 0.3 * (w1 - 0.5) + (w1 - 0.5) * mis * 0.6, 0.0, 1.0);
        float fres = pow(1.0 - zz, 2.6);
        vec3 surf = wa_heat(heat) * (0.45 + 0.9 * heat * heat);
        surf = mix(surf, tier * 1.0, fres * 0.6);
        surf += tier * fres * (0.9 + press * 0.8);
        surf += WHITE * pow(zz, 6.0) * 0.18 * (0.8 + 0.2 * sin(t * 3.0));
        col += surf * inside;
    }

    // o halo: duas camadas exponenciais e um brilho amplo, todas a zero exato na borda do quad
    float rim = max(r - radius, 0.0);
    float fade = 1.0 - smoothstep(0.45, 1.0, r);
    fade *= fade;
    float outside = 1.0 - inside;
    col += (tier * 0.8 * exp(-rim * 9.0) + CORAL * 0.4 * exp(-rim * 22.0)) * fade * (1.0 + press * 0.5) * outside;
    col += WHITE * 0.12 * exp(-r * r * 38.0) * fade * outside;
    col += tier * 0.25 * exp(-r * 3.0) * fade * outside;

    fragColor = vec4(wa_dither(wa_tonemap(col)), 1.0);
}
