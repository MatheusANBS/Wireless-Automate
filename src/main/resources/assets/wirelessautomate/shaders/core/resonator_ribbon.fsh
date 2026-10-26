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
const vec3 CREAM = vec3(1.0, 0.96, 0.84);

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

// O desvio lateral do tentáculo em x (0 a 1): curvo e orgânico, por ruído de gradiente suave com o domínio torcido
// (o warping do Inigo Quilez: a primeira oitava desloca as outras), lento e preso a zero nas duas pontas. {@code tt}
// é o tempo já na velocidade do tentáculo.
float tendrilOffset(float x, float seed, float tt) {
    float warp = wa_noise(vec3(x * 1.1 + tt * 0.4, seed, 0.3));
    float s = 0.75 * wa_noise(vec3(x * 1.4 + warp * 1.2 - tt, seed + 3.1, 0.7))
            + 0.25 * wa_noise(vec3(x * 2.9 + warp * 1.6 - tt * 1.5, seed + 6.3, 1.9));
    return s * 2.4 * pow(sin(PI * clamp(x, 0.0, 1.0)), 0.8);
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
    } else if (mode == 3) {
        // o disco de luz do Reparador (runas, pedestais, clarão e onda de choque), num quad virado para a câmera ou
        // deitado: um brilho redondo (miolo creme e halo na cor) ou um anel fino com grão. O parâmetro leva o raio
        // (7 bits, fração do meio lado do quad), o anel (bit 7) e a espessura do anel (7 bits); o alfa é a força
        int bits = int(arg);
        float rad = max(float(bits & 127) / 127.0, 0.02);
        bool ring = ((bits >> 7) & 1) != 0;
        float ringW = max(float((bits >> 8) & 127) / 127.0, 0.01);
        vec2 p = quad * 2.0 - 1.0;
        float r = length(p);
        if (ring) {
            float d = abs(r - rad);
            float grain = 1.0 + 0.3 * wa_noise(vec3(p * 9.0, t * 2.0));
            float glow = exp(-d * d / (ringW * ringW)) * 2.0 + exp(-d / (ringW * 3.0)) * 0.35;
            col += mix(tier, WHITE, 0.55) * glow * grain;
        } else {
            col += CREAM * exp(-r * r / (rad * rad * 0.08)) * 1.6;
            col += mix(tier, WHITE, 0.3) * exp(-r / (rad * 0.35));
        }
        col *= 1.0 - smoothstep(0.85, 1.0, r);
    } else {
        // o tentáculo de plasma, da ponta de um cristal vivo até a esfera, como o filamento de um globo de plasma
        // até o dedo no vidro: o caminho da corrente que o cristal entrega. Curvo e lento; os pacotes da corrente
        // correm do cristal para a esfera e a ponta abre num clarão onde agarra. Em sintonia ele é grosso e firme;
        // fora dela afina, treme e se solta, recua e volta a procurar a esfera. Na recarga ele é o invertido: fino,
        // na cor do catalisador, sempre agarrado, com a corrente da esfera para o cristal. O parâmetro leva a sintonia
        // de 0 a 15 (4 bits), a semente do cristal (3 bits), o comprimento em décimos de bloco (7 bits) e o invertido
        // (bit 14); o alfa é a força
        int bits = int(arg);
        float tuneV = float(bits & 15) / 15.0;
        float seed = float((bits >> 4) & 7) * 1.37 + 0.5;
        float len = max(float((bits >> 7) & 127) * 0.1, 0.2);
        bool reverse = ((bits >> 14) & 1) != 0;
        float aspect = len / 0.30;
        float mis = 1.0 - tuneV;
        float x = quad.x;
        float y = (quad.y - 0.5) * 2.0;
        float tt = t * (0.35 + 1.6 * mis) + seed * 11.0;
        float amp = mix(0.40, 0.70, mis);
        float e = 0.006;
        float off = tendrilOffset(x, seed, tt) * amp;
        float slope = (tendrilOffset(x + e, seed, tt) - tendrilOffset(x - e, seed, tt)) * amp / (2.0 * e);
        float kk = slope / aspect;
        float d = abs(y - off) / sqrt(1.0 + kk * kk);
        // fora de sintonia o tentáculo se solta: recua até onde alcança e volta a crescer, cada cristal no seu ritmo
        float hold = clamp(tuneV * 1.6 + (wa_noise(vec3(t * 0.9, seed * 3.3, 4.1)) + 0.5) - 0.6, 0.0, 1.0);
        float reach = reverse ? 1.04 : mix(0.30, 1.04, smoothstep(0.0, 1.0, hold));
        // o corpo de plasma é largo e macio na cor do cristal; o núcleo branco é fino e fraco, para não ser o raio
        float wc = mix(0.014, 0.030, tuneV) * (0.75 + 0.5 * x) * (0.45 + 0.55 * power);
        float body = mix(0.05, 0.13, tuneV) * (0.8 + 0.4 * x) * (0.45 + 0.55 * power);
        if (reverse) {
            wc = 0.014;
            body = 0.06;
        }
        // os pacotes da corrente, do cristal para a esfera
        float packet = pow(0.5 + 0.5 * sin(x * len * 7.0 + (reverse ? t : -t) * 4.0 + seed), 10.0);
        vec2 light = boltLight(d, wc) * vec2(0.35 + 0.9 * packet, 0.6);
        float plasma = exp(-d * d / (body * body)) * (0.55 + 0.6 * packet);
        float shimmer = 0.9 + 0.1 * sin(t * 23.0 + seed * 5.0) + mis * 0.35 * wa_noise(vec3(t * 14.0, seed, 8.0));
        float cut = 1.0 - smoothstep(reach - 0.10, reach, x);
        vec3 glow = reverse ? mix(RED, CORAL, 0.35) : mix(CORAL, tier, 0.25);
        col += (WHITE * light.x + glow * light.y * 1.4 + glow * plasma * mix(0.5, 1.1, tuneV)) * shimmer * cut;
        // o clarão onde agarra a esfera, só quando alcança, e a raiz na ponta do cristal
        vec2 pe = vec2((1.0 - x) * len, (y - off) * 0.30);
        col += mix(glow, WHITE, 0.4) * exp(-dot(pe, pe) / 0.004) * 1.6 * smoothstep(0.92, 1.02, reach);
        vec2 ps = vec2(x * len, y * 0.30);
        col += glow * exp(-dot(ps, ps) / 0.0025) * 0.8;
        float edgeY = 1.0 - smoothstep(0.30, 1.0, abs(y));
        col *= edgeY * edgeY;
        col *= smoothstep(0.0, 0.05, x) * (1.0 - smoothstep(0.93, 1.0, x));
    }

    col *= power;
    float peak = max(col.r, max(col.g, col.b));
    if (peak < 0.003) discard;
    fragColor = vec4(wa_dither(wa_tonemap(col)), 1.0);
}
