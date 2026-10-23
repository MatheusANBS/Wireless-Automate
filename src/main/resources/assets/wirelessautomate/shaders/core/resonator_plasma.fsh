#version 150

#moj_import <wirelessautomate:wa_common.glsl>

uniform float GameTime;
uniform mat3 ViewRot;

in vec2 quad;
in vec4 params;
flat in ivec2 extra;

out vec4 fragColor;

const vec3 CORAL = vec3(0.949, 0.569, 0.478);
const vec3 WHITE = vec3(1.0, 0.94, 0.92);
// o raio da esfera em unidades do quad (HALF = 1,45 bloco): 0,34 bloco
const float SPHERE = 0.234;

float segD(vec2 p, vec2 a, vec2 b) { vec2 pa = p - a, ba = b - a; float h = clamp(dot(pa, ba) / dot(ba, ba), 0.0, 1.0); return length(pa - ba * h); }
const float TAU = 6.28318;

// A curva de Lissajous em 3D, parada no mundo (gira devagar em Y) e levada ao quadro do billboard pelo ViewRot.
vec3 curve(float s, float t, float mis) {
    vec3 c = vec3(sin(2.0 * s + t * 0.45), sin(3.0 * s + t * 0.30 + 1.5708), sin(s + t * 0.22 + 0.7));
    c *= vec3(0.50, 0.40, 0.44);
    c += mis * 0.06 * vec3(sin(s * 13.0 + t * 7.0), cos(s * 11.0 - t * 5.0), sin(s * 9.0 + t * 6.0));
    float ca = cos(t * 0.12);
    float sa = sin(t * 0.12);
    return ViewRot * vec3(ca * c.x + sa * c.z, c.y, -sa * c.x + ca * c.z);
}

void main() {
    vec2 p = quad * 2.0 - 1.0;
    float r = length(p);
    if (r > 1.0) discard;
    float t = GameTime * 1200.0;
    vec3 tier = params.rgb;
    float tune = params.a;
    float mis = 1.0 - tune;
    float crystals = float(extra.x);
    int detail = extra.y & 3;
    float press = extra.y >= 4 ? 0.5 + 0.5 * sin(t * 7.0) : 0.0;
    int octaves = detail >= 2 ? 4 : (detail == 1 ? 3 : 2);
    int samples = detail >= 2 ? 48 : (detail == 1 ? 32 : 16);

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

    // LINHAS
    float amp = 0.35 + 0.65 * tune;
    float behind = smoothstep(radius - 0.01, radius + 0.01, r);
    float dFront = 1e3;
    float dBack = 1e3;
    float zFront = 0.0;
    if (r < 0.85) {
        vec3 prev = curve(0.0, t, mis);
        for (int i = 1; i <= 48; i++) {
            if (i > samples) break;
            vec3 cur = curve(float(i) / float(samples) * TAU, t, mis);
            vec2 pa = p - prev.xy;
            vec2 ba = cur.xy - prev.xy;
            float h = clamp(dot(pa, ba) / max(dot(ba, ba), 1e-8), 0.0, 1.0);
            float dist = length(pa - ba * h);
            float z = mix(prev.z, cur.z, h);
            if (z >= 0.0) {
                if (dist < dFront) { dFront = dist; zFront = z; }
            } else if (dist < dBack) {
                dBack = dist;
            }
            prev = cur;
        }
    }
    vec3 lineColor = mix(CORAL, WHITE, 0.5);
    // o trecho de trás é mais fraco e some sob o corpo da esfera; o da frente passa por cima dela
    col += lineColor * wa_line(dBack, 0.0045) * amp * 0.35 * behind;
    col += lineColor * wa_line(dFront, 0.005) * amp * (0.7 + 0.5 * zFront / 0.5);
    // a cabeça que percorre a curva
    vec3 head = curve(fract(t * 0.07) * TAU, t, mis);
    float dh = length(p - head.xy);
    float headVisible = head.z >= 0.0 ? 1.0 : behind;
    col += WHITE * (0.0016 / (dh * dh + 0.0007)) * amp * headVisible;

    // as órbitas dos cristais carregados: um anel inclinado no mundo, com o fio de cada um até o centro
    for (int i = 0; i < 8; i++) {
        if (float(i) >= crystals || detail == 0) break;
        float ang = t * 0.7 + float(i) * TAU / crystals;
        vec3 c3 = ViewRot * vec3(cos(ang) * 0.66, 0.2 * sin(ang * 2.0 + float(i)), sin(ang) * 0.66);
        float vis = c3.z >= 0.0 ? 1.0 : behind;
        float dc = length(p - c3.xy);
        col += mix(CORAL, tier, 0.35) * (0.0035 / (dc * dc + 0.0009)) * 0.08 * vis;
        col += tier * (0.0007 / (segD(p, c3.xy, vec2(0.0)) + 0.0015)) * tune
            * (0.5 + 0.5 * sin(t * 6.0 + float(i))) * vis;
    }
    // FIM LINHAS

    // chiado de TV quando fora de sintonia
    float st = wa_h21(floor(gl_FragCoord.xy / 2.0) + vec2(floor(t * 24.0), 0.0));
    col += vec3(st) * mis * mis * 0.22 * (1.0 - r);

    fragColor = vec4(wa_dither(wa_tonemap(col)), 1.0);
}
