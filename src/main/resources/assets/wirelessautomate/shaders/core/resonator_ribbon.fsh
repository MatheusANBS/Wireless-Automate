#version 150

#moj_import <wirelessautomate:wa_common.glsl>

uniform float GameTime;

in vec2 quad;
in vec4 params;
flat in ivec2 extra;

out vec4 fragColor;

const vec3 CORAL = vec3(0.949, 0.569, 0.478);
const vec3 RED = vec3(0.839, 0.271, 0.231);
const vec3 WHITE = vec3(1.0, 0.94, 0.92);

void main() {
    float t = GameTime * 1200.0;
    vec3 tier = params.rgb;
    float power = params.a;
    int mode = extra.x;
    float arg = float(extra.y);
    vec3 col = vec3(0.0);

    if (mode == 0) {
        // conduto: seção de cilindro com núcleo quente, halo exponencial, ruído de energia correndo, cometas e clarão nas pontas
        float flow = clamp(arg / 7.0, 0.0, 1.0);
        float x = quad.x;
        float d = abs(quad.y - 0.5) * 2.0;
        float cyl = sqrt(max(0.0, 1.0 - d * d));
        float coreW = 0.18 + 0.22 * flow;
        float core = exp(-d * d / (coreW * coreW));
        float halo = exp(-d * 2.6) * (1.0 - smoothstep(0.7, 1.0, d));
        float run = wa_noise(vec3(x * 14.0 - t * (3.0 + 1.5 * arg), d * 3.0, t * 0.5)) * 0.9 + 0.5;
        float speed = 0.4 + 0.15 * arg;
        float comet = 0.0;
        for (int k = 0; k < 3; k++) {
            float dx = fract(t * speed + float(k) / 3.0) - x;
            comet += dx > 0.0 ? exp(-dx * 14.0) : exp(dx * 160.0);
        }
        vec3 hot = mix(CORAL, tier, x);
        col += hot * halo * (0.5 + 0.6 * run) * (0.6 + flow);
        col += mix(hot, WHITE, 0.6) * core * (0.8 + 1.8 * comet) * cyl;
        col += WHITE * core * core * comet * flow * 1.5;
        float flare = (exp(-x * x * 220.0) + exp(-(1.0 - x) * (1.0 - x) * 220.0)) * exp(-d * d * 1.5);
        col += mix(hot, WHITE, 0.5) * flare * flow * 1.2;
        col *= smoothstep(0.0, 0.02, x) * (1.0 - smoothstep(0.985, 1.0, x));
    } else if (mode == 1) {
        // espiral de catalisador: dez grãos sobem de u = 0 a u = 1, girando em v
        for (int k = 0; k < 10; k++) {
            float s = fract(float(k) / 10.0 + t * 0.45);
            float vc = 0.5 + 0.35 * sin(s * 18.0 + t * 3.0) * (1.0 - s);
            vec2 dp = vec2((quad.x - s) * 0.4, (quad.y - vc) * 0.5);
            col += mix(RED, CORAL, s) * (0.0016 / (dot(dp, dp) + 0.0012)) * 0.6;
        }
        col *= smoothstep(0.0, 0.03, quad.x) * (1.0 - smoothstep(0.97, 1.0, quad.x));
    } else if (mode == 2) {
        // aura do cristal: um halo suave e uma estrela de quatro pontas que gira devagar e pulsa com a carga; fora de
        // sintonia a estrela encolhe e treme. O parâmetro leva a sintonia (7 bits) e a semente do cristal (4 bits)
        float seed = floor(arg / 128.0);
        float tuneV = clamp((arg - seed * 128.0) / 100.0, 0.0, 1.0);
        float mis = 1.0 - tuneV;
        vec2 pp = quad * 2.0 - 1.0;
        float r = length(pp);
        vec3 base = mix(CORAL, tier, 0.3);
        float pulse = 0.6 + 0.4 * sin(t * 4.0 + seed * 2.4);
        float halo = exp(-r * r * 7.0) * pulse;
        float ang = t * 0.25 + seed;
        vec2 rp = vec2(cos(ang) * pp.x + sin(ang) * pp.y, -sin(ang) * pp.x + cos(ang) * pp.y);
        float reach = mix(0.45, 1.0, tuneV);
        float arm = exp(-abs(rp.y) * 42.0) * exp(-abs(rp.x) * 3.0 / reach)
            + exp(-abs(rp.x) * 42.0) * exp(-abs(rp.y) * 3.0 / reach);
        arm *= 0.6 * (0.3 + 0.7 * tuneV) * pulse;
        float jitter = 1.0 + mis * 0.6 * wa_noise(vec3(t * 9.0, seed * 3.7, 0.5)) * 1.2;
        col += base * halo * 0.55 * jitter + mix(base, WHITE, 0.5) * arm * jitter;
        col *= (1.0 - smoothstep(0.8, 1.0, r));
    } else {
        // anel: abre e some com o progresso
        float prog = clamp(arg / 100.0, 0.0, 1.0);
        float r = length(quad * 2.0 - 1.0);
        col += CORAL * (1.0 - smoothstep(0.0, 0.08, abs(r - (0.28 + prog * 0.7)))) * prog * (1.0 - prog) * 4.0;
    }

    col *= power;
    float peak = max(col.r, max(col.g, col.b));
    if (peak < 0.003) discard;
    fragColor = vec4(wa_dither(wa_tonemap(col)), 1.0);
}
