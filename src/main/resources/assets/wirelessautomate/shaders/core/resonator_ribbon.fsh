#version 150

#moj_import <wirelessautomate:wa_common.glsl>

uniform float GameTime;

in vec2 quad;
in vec4 params;
flat in ivec2 extra;

out vec4 fragColor;

const vec3 CORAL = vec3(0.949, 0.569, 0.478);
const vec3 WHITE = vec3(1.0, 0.94, 0.92);
const vec3 CREAM = vec3(1.0, 0.96, 0.84);

const float PI = 3.14159265;

// O fio k do feixe do Reparador em x (0 a 1): uma hélice em volta do eixo (o seno da fase th, de lado; o cosseno é a
// profundidade), com uma ondulação lenta por ruído, presa a zero nas duas pontas.
float weaveY(float x, float th, float seed, float k, float amp) {
    float wob = 0.18 * wa_noise(vec3(x * 3.0 - GameTime * 1200.0 * 0.8, seed + k * 4.7, 2.3));
    return amp * pow(max(sin(PI * clamp(x, 0.0, 1.0)), 0.0), 0.6) * (sin(th) + wob);
}

void main() {
    float t = GameTime * 1200.0;
    vec3 tier = params.rgb;
    float power = params.a;
    int mode = extra.x;
    float arg = float(extra.y);
    vec3 col = vec3(0.0);

    if (mode == 1) {
        // o feixe mágico do Reparador, do emissor ao rachado: três fios de plasma em espiral em volta do eixo, que
        // ondulam devagar, com contas de luz correndo neles do emissor para o cristal, mais um fio fraco no eixo. Na
        // convergência os fios abrem, engrossam, giram mais rápido e vibram sem ficar mais fortes, para não estourar em
        // branco: o miolo é creme e fraco, não o HDR do raio. O parâmetro leva a semente da linha (4 bits), a força da convergência de 0 a 7
        // (3 bits) e o comprimento sobre a meia largura vezes 0,35, em décimos (7 bits); o alfa é a força
        int bits = int(arg);
        float seed = float(bits & 15) * 2.31 + 0.7;
        float surge = float((bits >> 4) & 7) / 7.0;
        float aspect = max(float((bits >> 8) & 127) * 0.1, 0.3) / 0.35;
        float x = quad.x;
        float y = (quad.y - 0.5) * 2.0;
        float amp = mix(0.40, 0.58, surge);
        float turns = (1.4 + 0.6 * surge) * 2.0 * PI;
        float spin = t * mix(2.2, 7.5, surge);
        // a vibração da convergência: o feixe inteiro treme de lado
        float shake = surge * 0.09 * sin(t * 61.0 + x * 37.0 + seed);
        float wc = mix(0.040, 0.055, surge);
        vec3 glowCol = mix(CORAL, tier, 0.5);
        vec3 coreCol = mix(CREAM, tier, 0.3);
        float e = 0.006;
        for (int k = 0; k < 3; k++) {
            float fk = float(k);
            float ph = fk * 2.0944 + seed;
            float th = x * turns - spin + ph;
            float yk = weaveY(x, th, seed, fk, amp) + shake;
            float slope = (weaveY(x + e, th + e * turns, seed, fk, amp) - weaveY(x - e, th - e * turns, seed, fk, amp))
                    / (2.0 * e);
            float kk = slope / aspect;
            float d = abs(y - yk) / sqrt(1.0 + kk * kk);
            // o fio da frente mais forte que o de trás, pela profundidade da hélice
            float front = 0.5 + 0.5 * cos(th);
            // as contas de luz, umas cinco por fio, correndo do emissor para o cristal
            float bead = pow(0.5 + 0.5 * sin(x * 31.4 - t * 7.0 + ph * 3.0), 8.0);
            float core = exp(-d * d / (wc * wc * 0.12)) * (0.45 + 0.55 * front) * (0.7 + 1.6 * bead);
            float g = wc * 1.5 / (d + wc * 1.5);
            col += coreCol * core * 0.9 + glowCol * g * g * (0.18 + 0.22 * front);
        }
        // as faíscas soltas que viajam pelos fios, uma por vez em cada um, mais rápidas que as contas
        for (int m = 0; m < 3; m++) {
            float fm = float(m);
            float xm = fract(t * (0.55 + 0.1 * fm) + fm * 0.33 + seed * 0.1);
            float thm = xm * turns - spin + fm * 2.0944 + seed;
            vec2 dm = vec2((x - xm) * aspect, y - weaveY(xm, thm, seed, fm, amp) - shake);
            col += CREAM * exp(-dot(dm, dm) / 0.02) * 1.1 * (0.5 + 0.5 * cos(thm));
        }
        col += glowCol * exp(-y * y / 0.03) * 0.16;
        float edgeY = 1.0 - smoothstep(0.55, 1.0, abs(y));
        col *= edgeY;
        // os oito feixes nascem juntos no emissor e a fita soma: o começo some devagar, para a soma não estourar em
        // branco perto dele (o brilho ali é o do próprio emissor)
        col *= smoothstep(0.0, 0.30, x) * (1.0 - smoothstep(0.94, 1.0, x));
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
    }

    col *= power;
    float peak = max(col.r, max(col.g, col.b));
    if (peak < 0.003) discard;
    fragColor = vec4(wa_dither(wa_tonemap(col)), 1.0);
}
