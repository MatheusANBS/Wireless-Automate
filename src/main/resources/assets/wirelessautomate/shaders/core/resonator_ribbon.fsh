#version 150

uniform float GameTime;

in vec2 quad;
in vec4 params;
flat in ivec2 extra;

out vec4 fragColor;

const vec3 CORAL = vec3(0.949, 0.569, 0.478);
const vec3 RED = vec3(0.839, 0.271, 0.231);
const vec3 WHITE = vec3(1.0, 0.94, 0.92);

float h21(vec2 p) { p = fract(p * vec2(123.34, 456.21)); p += dot(p, p + 45.32); return fract(p.x * p.y); }
float n2(vec2 p) {
    vec2 i = floor(p), f = fract(p); f = f * f * (3.0 - 2.0 * f);
    return mix(mix(h21(i), h21(i + vec2(1, 0)), f.x), mix(h21(i + vec2(0, 1)), h21(i + vec2(1, 1)), f.x), f.y);
}

void main() {
    float t = GameTime * 1200.0;
    vec3 tier = params.rgb;
    float power = params.a;
    int mode = extra.x;
    float arg = float(extra.y);
    vec3 col = vec3(0.0);

    if (mode == 0) {
        // conduto: pacotes correndo de u = 0 para u = 1, mais largos e rápidos com a faixa de vazão
        float flow = clamp(arg / 7.0, 0.0, 1.0);
        float d = abs(quad.y - 0.5) * 2.0;
        float width = 0.18 + 0.5 * flow;
        float tube = smoothstep(width, 0.0, d);
        float packets = pow(0.5 + 0.5 * sin(quad.x * 28.0 - t * (6.0 + 2.0 * arg)), 6.0);
        col += mix(CORAL, tier, quad.x) * tube * (0.6 + 2.6 * packets);
        col += WHITE * smoothstep(width * 0.5, 0.0, d) * packets * flow;
        col *= smoothstep(0.0, 0.04, quad.x) * smoothstep(1.0, 0.96, quad.x);
    } else if (mode == 1) {
        // espiral de catalisador: dez grãos sobem de u = 0 a u = 1, girando em v
        for (int k = 0; k < 10; k++) {
            float s = fract(float(k) / 10.0 + t * 0.45);
            float vc = 0.5 + 0.35 * sin(s * 18.0 + t * 3.0) * (1.0 - s);
            vec2 dp = vec2((quad.x - s) * 0.4, (quad.y - vc) * 2.0);
            col += mix(RED, CORAL, s) * (0.0016 / (dot(dp, dp) + 0.0012)) * 0.6;
        }
        col *= smoothstep(0.0, 0.03, quad.x) * smoothstep(1.0, 0.97, quad.x);
    } else if (mode == 2) {
        // aura do cristal: treme pelo ruído quanto menor a sintonia
        float mis = 1.0 - clamp(arg / 100.0, 0.0, 1.0);
        float r = length(quad * 2.0 - 1.0);
        float aura = exp(-r * r * 6.0) * (0.6 + 0.4 * sin(t * 4.0));
        col += mix(CORAL, tier, 0.3) * aura * 0.55 * (1.0 + mis * 0.6 * (n2(vec2(t * 9.0, floor(quad.x * 3.0))) - 0.5));
        col *= smoothstep(1.0, 0.8, r);
    } else {
        // anel: abre e some com o progresso
        float prog = clamp(arg / 100.0, 0.0, 1.0);
        float r = length(quad * 2.0 - 1.0);
        col += CORAL * smoothstep(0.08, 0.0, abs(r - (0.28 + prog * 0.7))) * prog * (1.0 - prog) * 4.0;
    }

    col *= power;
    float peak = max(col.r, max(col.g, col.b));
    if (peak < 0.003) discard;
    fragColor = vec4(col, 1.0 - exp(-peak * 1.5));
}
