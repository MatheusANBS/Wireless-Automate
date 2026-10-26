#version 150

#moj_import <wirelessautomate:wa_common.glsl>

// O cristal em pixel-art 3D: facetas chapadas em 5 tons da paleta dos sprites do mod pela luz da vista, o contorno
// escuro do casco invertido (o vertex shader o infla) e o brilho que varre o cristal, como nos quadros animados. A malha é o aglomerado do Cristal de Sinal ou o
// losango com cinta do Lapidado e dos Afinados (o bit 14 do parâmetro); a cor do vértice é a da faixa do losango (o
// dourado do Lapidado ou a cor do tier do Afinado, com as duas marcas em cruz nos polos). O Opaco é a paleta osso
// com pintas, o rachado ganha um risco marrom e, na recarga, abaixo da altura do progresso vale a paleta coral. As
// pintas e o risco usam só as coordenadas do cristal (crystal.x, a altura e crystal.z), então não escorregam quando
// a câmera anda. O alfa da cor é o clarão do fim da recarga. O parâmetro leva a semente
// (4 bits), o estado (10 bits: 0 vivo, 1 Opaco, 2 a 1002 recarga de 0 a 1, 1003 rachado) e o bit 14 (losango).
// O canal de efeito (fx, o UV0.y; 0 no Núcleo, que fica como sempre) é do Reparador: 1 a 2 o rachado em reparo com
// energia, 3 a 4 o rachado sem energia (a fração é o progresso) e 5 a 6 a dissolução, como no CrystalParam. No reparo,
// o risco comum dá lugar às 6 rachaduras do mockup nos planos x-y e z-y do cristal, que fecham de dentro para fora com o ponto
// de solda creme na frente, enquanto a paleta vai do osso ao coral.

uniform float GameTime;

in vec3 normal;
in vec3 crystal;
flat in vec3 light;
flat in int hull;
flat in vec4 tint;
flat in int param;
flat in float fx;

out vec4 fragColor;

const float BOTTOM = -0.46;
const float TOP = 0.58;
const vec3 OUTLINE = vec3(0.059, 0.055, 0.071);
// os 5 tons de cada paleta, do mais escuro ao brilho (os dos sprites)
const vec3 CORAL[5] = vec3[5](vec3(0.541, 0.227, 0.173), vec3(0.788, 0.376, 0.294), vec3(0.949, 0.569, 0.478),
        vec3(1.0, 0.788, 0.722), vec3(1.0, 0.941, 0.918));
const vec3 BONE[5] = vec3[5](vec3(0.369, 0.349, 0.322), vec3(0.549, 0.522, 0.486), vec3(0.710, 0.678, 0.635),
        vec3(0.851, 0.824, 0.784), vec3(0.957, 0.941, 0.918));
const vec3 SPECK = vec3(0.682, 0.443, 0.380);
const vec3 CRACK = vec3(0.478, 0.290, 0.243);
// o dourado do Lapidado, para saber se a faixa é a de um Afinado
const vec3 GOLD = vec3(0.867, 0.737, 0.361);
// as rachaduras do mockup em unidades do cristal: o tronco (4 segmentos) e os dois galhos
const vec2 CRACK_A[6] = vec2[6](vec2(-0.028, 0.536), vec2(0.083, 0.339), vec2(-0.069, 0.175), vec2(0.055, -0.087),
        vec2(0.083, 0.339), vec2(-0.069, 0.175));
const vec2 CRACK_B[6] = vec2[6](vec2(0.083, 0.339), vec2(-0.069, 0.175), vec2(0.055, -0.087), vec2(-0.014, -0.306),
        vec2(0.290, 0.219), vec2(-0.276, 0.077));
// de onde as rachaduras fecham, e a distância desse centro em que todas já fecharam
const vec2 HEAL_CENTER = vec2(0.0, 0.06);
const float HEAL_REACH = 0.50;
// a meia largura da rachadura e do miolo aceso: o cristal tem poucos pixels na tela, e a rachadura do mockup é grossa
const float CRACK_WIDTH = 0.045;
const float CRACK_CORE = 0.02;
const vec3 CRACK_GLOW = vec3(1.0, 0.471, 0.314);
const vec3 WELD = vec3(1.0, 0.957, 0.824);

// a faixa: o tom da cor do vértice, do escuro ao brilho
vec3 bandTone(vec3 base, int tone) {
    if (tone >= 4) return mix(base, vec3(1.0), 0.8);
    if (tone == 3) return mix(base, vec3(1.0), 0.45);
    if (tone == 2) return base;
    if (tone == 1) return base * 0.75;
    return base * 0.45;
}

// a rachadura mais perto de p: a distância (x) e quão longe do centro do reparo fica o ponto dela mais perto (y)
// (distância a segmento de Inigo Quilez, "distfunctions2d")
vec2 nearestCrack(vec2 p) {
    float best = 1e9;
    float reach = 0.0;
    for (int k = 0; k < 6; k++) {
        vec2 pa = p - CRACK_A[k];
        vec2 ba = CRACK_B[k] - CRACK_A[k];
        float u = clamp(dot(pa, ba) / dot(ba, ba), 0.0, 1.0);
        float dist = length(pa - ba * u);
        if (dist < best) {
            best = dist;
            reach = length(CRACK_A[k] + ba * u - HEAL_CENTER);
        }
    }
    return vec2(best, reach);
}

void main() {
    int fxKind = fx < 0.5 ? 0 : fx < 2.5 ? 1 : fx < 4.5 ? 2 : 3;
    float fxValue = fxKind == 1 ? fx - 1.0 : fxKind == 2 ? fx - 3.0 : fxKind == 3 ? fx - 5.0 : 0.0;
    bool repair = fxKind == 1 || fxKind == 2;
    bool powered = fxKind == 1;
    // no reparo com energia, quanto a paleta já foi do osso ao coral
    float heal = powered ? clamp((fxValue - 0.15) / 0.75, 0.0, 1.0) : 0.0;
    float seed = float(param & 15) * 0.73 + 0.1;
    int state = (param >> 4) & 1023;
    bool diamond = ((param >> 14) & 1) != 0;
    bool cracked = state > 1002;
    bool dull = state == 1 || cracked;
    float rec = state >= 2 && state <= 1002 ? float(state - 2) / 1000.0 : -1.0;
    float t = GameTime * 1200.0;
    if (hull == 1) {
        fragColor = vec4(OUTLINE, 1.0);
        return;
    }

    vec3 N = normalize(normal);

    float h = clamp(crystal.y, 0.0, 1.0);
    vec3 obj = vec3(crystal.x, mix(BOTTOM, TOP, h), crystal.z);

    // o tom da faceta pela luz da vista, em 5 degraus
    float d = dot(N, light);
    int tone = d > 0.82 ? 4 : d > 0.45 ? 3 : d > 0.02 ? 2 : d > -0.45 ? 1 : 0;
    // o brilho que varre o cristal sobe um tom
    if (fract(h * 0.9 + obj.x * 0.6 - t * 0.28 + seed * 0.13) < 0.07) {
        tone = min(tone + 1, 4);
    }

    float lit = dull ? 0.0 : 1.0;
    if (rec >= 0.0) {
        lit = step(h, rec);
    }
    vec3 col = lit > 0.5 ? CORAL[tone] : BONE[tone];
    if (repair) {
        col = mix(BONE[tone], CORAL[tone], heal);
    }
    if (lit < 0.5 && heal < 0.5) {
        // as pintas do Opaco, fixas no cristal
        vec3 g = floor(obj * 16.0);
        if (wa_h21(vec2(g.x + g.z * 57.0, g.y + seed * 19.0)) > 0.975) {
            col = SPECK;
        }
    }
    if (repair) {
        // o desenho vale nos planos x-y e z-y do cristal, para as rachaduras aparecerem de qualquer lado que se olhe
        vec2 crack = nearestCrack(obj.xy);
        vec2 side = nearestCrack(vec2(-obj.z, obj.y));
        if (side.x < crack.x) {
            crack = side;
        }
        float front = fxValue * HEAL_REACH;
        if (crack.y > front) {
            if (crack.x < CRACK_WIDTH) {
                col = CRACK;
            }
            // com energia, o miolo aceso das rachaduras ainda abertas
            if (powered && crack.x < CRACK_CORE) {
                col = CRACK_GLOW;
            }
        }
        if (powered && fxValue > 0.0 && fxValue < 1.0) {
            float ahead = (crack.y - front) / 0.03;
            float weld = exp(-ahead * ahead) * exp(-crack.x * crack.x / 0.004);
            col = mix(col, WELD, clamp(weld * 1.4, 0.0, 1.0));
        }
    } else if (cracked) {
        // o risco do rachado
        float line = abs(obj.x * 0.9 - obj.y * 0.45 + 0.04 * sin(obj.y * 16.0) - 0.02);
        if (line < 0.022) {
            col = CRACK;
        }
    }
    if (diamond) {
        bool tuned = distance(tint.rgb, GOLD) > 0.06;
        // o Afinado Opaco ou rachado perde a cor da faixa para um cinza esverdeado
        vec3 base = dull && tuned ? mix(tint.rgb, vec3(0.62, 0.64, 0.60), 0.6 * (1.0 - heal)) : tint.rgb;
        if (h > 0.42 && h < 0.56) {
            col = bandTone(base, tone);
        }
        // as duas marcas em cruz dos polos do Afinado
        if (tuned && (h > 0.80 || h < 0.20) && length(obj.xz) < 0.16 && (abs(obj.x) < 0.04 || abs(obj.z) < 0.04)) {
            col = bandTone(base, tone);
        }
    }
    // a linha clara na frente do nível da recarga
    if (rec > 0.001 && rec < 0.999 && abs(h - rec) < 0.025) {
        col = CORAL[4];
    }
    // o clarão quando a recarga termina: o cristal acende quase branco e volta ao tom em 0,7 s
    col = mix(col, CORAL[4], tint.a * 0.85);
    fragColor = vec4(col, 1.0);
}
