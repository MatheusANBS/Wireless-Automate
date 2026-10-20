#version 150

uniform float GameTime;

in vec2 quad;
in vec4 params;
flat in ivec2 extra;

out vec4 fragColor;

const vec3 CORAL = vec3(0.949, 0.569, 0.478);
const vec3 WHITE = vec3(1.0, 0.94, 0.92);

float h21(vec2 p) { p = fract(p * vec2(123.34, 456.21)); p += dot(p, p + 45.32); return fract(p.x * p.y); }
float n2(vec2 p) {
    vec2 i = floor(p), f = fract(p); f = f * f * (3.0 - 2.0 * f);
    return mix(mix(h21(i), h21(i + vec2(1, 0)), f.x), mix(h21(i + vec2(0, 1)), h21(i + vec2(1, 1)), f.x), f.y);
}
float fbm(vec2 p, int octaves) {
    float s = 0.0, a = 0.5;
    for (int i = 0; i < 4; i++) { if (i >= octaves) break; s += a * n2(p); p = p * 2.03 + vec2(1.7, 9.2); a *= 0.5; }
    return s;
}
float segD(vec2 p, vec2 a, vec2 b) { vec2 pa = p - a, ba = b - a; float h = clamp(dot(pa, ba) / dot(ba, ba), 0.0, 1.0); return length(pa - ba * h); }
vec2 liss(float s, float t, float mis) {
    return vec2(sin(3.0 * s + t * 0.6), sin(2.0 * s + t * 0.35 + 1.5708)) * vec2(0.62, 0.42)
         + mis * vec2(sin(s * 13.0 + t * 7.0), cos(s * 11.0 - t * 5.0)) * 0.06;
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
    int detail = extra.y;
    int octaves = detail >= 2 ? 4 : (detail == 1 ? 3 : 2);
    int samples = detail >= 2 ? 48 : (detail == 1 ? 32 : 16);

    vec3 col = tier * 0.05 * (1.0 - r * 0.6);
    vec2 q = p * 3.0;
    float w = fbm(q + vec2(t * 0.4, -t * 0.3) + fbm(q * 1.5 - t * 0.2, octaves) * 1.6, octaves);
    float radius = 0.26 + 0.03 * sin(t * 2.0) + mis * 0.08 * (w - 0.5);
    float body = smoothstep(radius, radius - 0.12, r) * (0.55 + 0.9 * w);
    col += mix(CORAL * 0.8, CORAL, smoothstep(0.0, radius, radius - r + 0.1)) * vec3(1.5, 0.75, 0.55) * body;
    float ring = (r - radius) / 0.07;
    col += tier * 1.3 * exp(-ring * ring);
    col += WHITE * exp(-r * r * 160.0) * (0.35 + 0.1 * sin(t * 3.0));
    col += tier * 0.4 * exp(-r * 4.5);

    float d = 1e3;
    vec2 prev = liss(0.0, t, mis);
    for (int i = 1; i <= 48; i++) {
        if (i > samples) break;
        vec2 cur = liss(float(i) / float(samples) * 6.28318, t, mis);
        d = min(d, segD(p, prev, cur));
        prev = cur;
    }
    col += mix(CORAL, WHITE, 0.5) * (0.0022 / (d + 0.002)) * (0.35 + 0.65 * tune);

    for (int i = 0; i < 8; i++) {
        if (float(i) >= crystals) break;
        float ang = t * 0.7 + float(i) * 6.28318 / crystals;
        vec2 c = vec2(cos(ang) * 0.78, sin(ang) * 0.5);
        float dc = length(p - c);
        col += mix(CORAL, tier, 0.35) * (0.0035 / (dc * dc + 0.0009)) * 0.08;
        col += tier * (0.0007 / (segD(p, c, vec2(0.0)) + 0.0015)) * tune * (0.5 + 0.5 * sin(t * 6.0 + float(i)));
    }

    float st = h21(floor(gl_FragCoord.xy / 2.0) + floor(t * 24.0));
    col += vec3(st) * mis * mis * 0.22 * (1.0 - r);
    col = 1.0 - exp(-col * 0.9);
    float edge = smoothstep(1.0, 0.7, r);
    fragColor = vec4(col * edge, 1.0);
}
