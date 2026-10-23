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

const float PI = 3.14159265;

// Ruído 1D de valor com interpolação LINEAR: a linha quebra em retas, como um raio de verdade (a interpolação suave
// dava as curvas de senoide de antes).
float n1(float x, float seed) {
    float i = floor(x);
    return mix(wa_h21(vec2(i, seed)), wa_h21(vec2(i + 1.0, seed)), x - i) * 2.0 - 1.0;
}

// O desvio lateral do raio em x (0 a 1), fractal: cada oitava com o dobro da frequência e metade da amplitude, o que
// dá o recorte em todas as escalas; preso a zero nas duas pontas.
float boltOffset(float x, float seed) {
    float s = 0.0;
    float a = 0.55;
    float fr = 3.0;
    for (int k = 0; k < 5; k++) {
        s += a * n1(x * fr, seed + float(k) * 19.0);
        a *= 0.5;
        fr *= 2.1;
    }
    return s * pow(sin(PI * clamp(x, 0.0, 1.0)), 0.7);
}

// A distância de (x, y) ao raio, em unidades de y, corrigida pela inclinação do trecho para a espessura ficar a mesma
// nos trechos inclinados (sem isso eles engrossam e borram). {@code aspect} é o comprimento da fita sobre a meia
// largura.
float boltDist(float x, float y, float seed, float amp, float aspect) {
    float e = 0.006;
    float slope = (boltOffset(x + e, seed) - boltOffset(x - e, seed)) * amp / (2.0 * e);
    float k = slope / aspect;
    return abs(y - boltOffset(x, seed) * amp) / sqrt(1.0 + k * k);
}

// A luz de um raio a distância d do eixo, de largura wc: x é o núcleo (um pico estreito, em HDR, que o tonemap leva ao
// branco) e y o brilho (uma queda por distância, sem borda, mais um halo largo e fraco).
vec2 boltLight(float d, float wc) {
    float core = exp(-d * d / (wc * wc)) * 4.0;
    float g = wc * 3.0 / (d + wc * 3.0);
    return vec2(core, g * g * 0.55 + exp(-d / (wc * 16.0)) * 0.18);
}

void main() {
    float t = GameTime * 1200.0;
    vec3 tier = params.rgb;
    float power = params.a;
    int mode = extra.x;
    float arg = float(extra.y);
    vec3 col = vec3(0.0);

    if (mode == 0) {
        // o raio: um canal principal fractal, arcos secundários, forquilhas e uma descarga que o alarga, tudo pela faixa
        // de vazão. Sem tubo e sem contorno: núcleo branco em HDR, brilho que cai com a distância e o tier só no
        // brilho. O parâmetro leva a faixa (3 bits), a sintonia de 0 a 15 (4 bits), o buffer cheio (1 bit) e o
        // comprimento da fita em décimos de bloco (7 bits)
        int bits = int(arg);
        int band = bits & 7;
        float tuneV = float((bits >> 3) & 15) / 15.0;
        bool full = ((bits >> 7) & 1) != 0;
        float len = max(float((bits >> 8) & 127) * 0.1, 0.3);
        float aspect = len / 0.35;
        float flow = clamp(float(band) / 7.0, 0.0, 1.0);
        float x = quad.x;
        float y = (quad.y - 0.5) * 2.0;
        // o raio é sorteado de novo a 11 a 17 Hz e cada sorteio acende forte e decai um pouco
        float rate = mix(11.0, 17.0, flow);
        float slot = floor(t * rate);
        float flick = (0.75 + 0.5 * wa_h21(vec2(slot, 3.0))) * (0.85 + 0.15 * exp(-fract(t * rate) * 3.0));
        float chance = mix(0.35, 1.0, tuneV);
        float wc = 0.018 + 0.018 * flow;
        // o canal principal, com pacotes de energia correndo
        float packet = 1.0 + 1.1 * pow(0.5 + 0.5 * sin(x * len * 9.0 - t * (6.0 + 2.0 * float(band))), 6.0);
        float mainSeed = slot * 3.0;
        vec2 light = boltLight(boltDist(x, y, mainSeed, 0.42, aspect), wc) * vec2(packet, 1.0);
        // os arcos secundários: mais finos, mais soltos, em janelas que saltam
        int arcs = band >= 5 ? 3 : band == 4 ? 2 : band == 3 ? 1 : 0;
        for (int k = 0; k < 3; k++) {
            if (k >= arcs) break;
            float cycle = t * (1.1 + 0.4 * float(k)) + float(k) * 0.37;
            float on = step(fract(cycle), 0.6) * step(wa_h21(vec2(floor(cycle), float(k) * 5.0)), chance);
            float seed = floor(t * 13.0 + float(k) * 3.0) * 5.0 + 101.0 + float(k) * 50.0;
            light += boltLight(boltDist(x, y, seed, 0.75, aspect), wc * 0.6) * 0.55 * on;
        }
        // as forquilhas: saem do canal principal em ângulo, afinam e somem
        for (int j = 0; j < 2; j++) {
            if (band < 4 || (j == 1 && band < 6)) break;
            float at = 0.22 + 0.40 * wa_h21(vec2(slot, 40.0 + float(j)));
            float u = x - at;
            if (u > 0.0 && u < 0.24) {
                float side = j == 0 ? 1.0 : -1.0;
                float base = boltOffset(at, mainSeed) * 0.42;
                float fy = base + side * u * aspect * 0.8 + boltOffset(u * 4.0, mainSeed + 60.0 + float(j)) * 0.15 * u * 4.0;
                float d = abs(y - fy) / sqrt(1.0 + 0.64);
                light += boltLight(d, wc * 0.55) * (1.0 - u / 0.24) * 0.7;
            }
        }
        // a descarga: o canal principal alarga e brilha forte por um instante
        float period = band >= 7 ? 1.0 : band == 6 ? 1.6 : band == 5 ? 2.4 : 0.0;
        if (period > 0.0) {
            float phase = fract(t / period);
            if (phase < 0.12) {
                float strike = step(wa_h21(vec2(floor(t / period), 77.0)), chance) * (1.0 - phase / 0.12);
                light += boltLight(boltDist(x, y, mainSeed, 0.42, aspect), wc * 2.4) * 1.6 * strike;
            }
        }
        light *= flick * (full ? 1.25 : 1.0);
        col += WHITE * light.x + mix(tier, WHITE, 0.2) * light.y * 1.6;
        // os clarões redondos: um dentro da esfera, onde o raio nasce, e outro onde bate na Porta
        vec2 ps = vec2(x * len, y * 0.35);
        vec2 pe = vec2((1.0 - x) * len, y * 0.35);
        col += mix(tier, WHITE, 0.5) * (exp(-dot(ps, ps) / 0.018) * 1.3 + exp(-dot(pe, pe) / 0.012) * 1.0) * flow * flick;
        float edgeY = 1.0 - smoothstep(0.25, 1.0, abs(y));
        col *= edgeY * edgeY;
        col *= smoothstep(0.0, 0.14, x) * (1.0 - smoothstep(0.90, 1.0, x));
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
