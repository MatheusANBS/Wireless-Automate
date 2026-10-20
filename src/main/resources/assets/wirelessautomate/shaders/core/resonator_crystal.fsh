#version 150

// O cristal facetado: veios coral correndo para cima por dentro, o núcleo quente, a borda pela incidência na cor
// coral e tier, as arestas e um reflexo; fora de sintonia, os veios aceleram e o cristal cintila em saltos. O
// Opaco é pedra cinza; na recarga ele acende de baixo para cima até a altura do progresso, com a frente clara.
// O ruído usa só as coordenadas do cristal (crystal.x, a altura e crystal.z), então não escorrega quando a câmera
// anda.

uniform float GameTime;

in vec3 rel;
in vec3 normal;
in vec2 facet;
in vec3 crystal;
flat in vec4 tint;
flat in int param;

out vec4 fragColor;

const float BOTTOM = -0.46;
const float TOP = 0.58;
const vec3 DEEP = vec3(0.40, 0.067, 0.071);
const vec3 CORAL = vec3(0.949, 0.569, 0.478);
const vec3 HOT = vec3(1.0, 0.878, 0.8);
const vec3 STONE_DARK = vec3(0.149, 0.145, 0.141);
const vec3 STONE_LIGHT = vec3(0.361, 0.341, 0.322);
const vec3 STONE_EDGE = vec3(0.275, 0.265, 0.25);

// hashes sem seno: o tempo cresce até 1200 s e o seno do GPU perde a precisão com argumentos grandes
float h1(float p) { p = fract(p * 0.1031); p *= p + 33.33; p *= p + p; return fract(p); }
float h3(vec3 p) { p = fract(p * 0.1031); p += dot(p, p.zyx + 31.32); return fract((p.x + p.y) * p.z); }
float n3(vec3 p) {
    vec3 i = floor(p), f = fract(p); f = f * f * (3.0 - 2.0 * f);
    return mix(mix(mix(h3(i), h3(i + vec3(1, 0, 0)), f.x), mix(h3(i + vec3(0, 1, 0)), h3(i + vec3(1, 1, 0)), f.x), f.y),
               mix(mix(h3(i + vec3(0, 0, 1)), h3(i + vec3(1, 0, 1)), f.x), mix(h3(i + vec3(0, 1, 1)), h3(i + vec3(1, 1, 1)), f.x), f.y), f.z);
}
float fbm(vec3 p) {
    float s = 0.0, a = 0.5;
    for (int i = 0; i < 3; i++) { s += a * n3(p); p = p * 2.03 + vec3(1.7, 9.2, 3.1); a *= 0.5; }
    return s;
}

void main() {
    // o parâmetro: a semente (4 bits) e o estado: 0 vivo, 1 Opaco, 2 a 1002 recarga de 0 a 1
    float seed = float(param & 15) * 0.73 + 0.1;
    int state = param >> 4;
    float rec = state >= 2 ? float(state - 2) / 1000.0 : -1.0;
    float mis = 1.0 - tint.a;
    float t = GameTime * 1200.0;
    vec3 tier = tint.rgb;

    vec3 N = normalize(normal);
    vec3 V = normalize(-rel);
    vec3 L = normalize(vec3(0.35, 0.9, 0.4));
    float h = clamp(crystal.y, 0.0, 1.0);
    vec3 obj = vec3(crystal.x, mix(BOTTOM, TOP, h), crystal.z);

    float fres = pow(1.0 - abs(dot(N, V)), 2.2);
    // a correnteza dos veios: a calma sempre e a rápida misturada pela falta de sintonia, sem a fase pular quando a
    // sintonia muda
    vec3 q = obj * 4.2 + seed * 3.1;
    float vein = fbm(q - vec3(0.0, t * 0.35, 0.0));
    if (mis > 0.01) {
        vein = mix(vein, fbm(q - vec3(0.0, t * 1.75, 0.0)), mis);
    }

    float lit = state == 1 ? 0.0 : 1.0;
    if (rec >= 0.0) {
        lit = step(h, rec);
    }
    float diff = 0.42 + 0.58 * max(dot(N, L), 0.0);
    float core = exp(-dot(obj.xz, obj.xz) * 16.0) * smoothstep(0.6, 0.0, abs(obj.y - 0.02));
    vec3 live = mix(DEEP, CORAL, smoothstep(0.28, 0.72, vein)) * (0.5 + 0.5 * diff);
    live += HOT * core * (0.45 + 0.2 * sin(t * 2.0 + seed * 7.0));
    // a borda leva só um pouco do tier: um tier frio (verde-água) somado ao coral puxaria a gema para o cinza
    live += mix(CORAL, tier, 0.35) * fres * 0.9;
    live *= 1.0 - mis * 0.6 * step(0.55, h1(floor(t * 16.0) + seed * 13.0));
    vec3 grey = mix(STONE_DARK, STONE_LIGHT, smoothstep(0.3, 0.7, vein)) * diff + vec3(0.5) * fres * 0.18;
    vec3 col = mix(grey, live, lit);
    if (rec >= 0.0) {
        float front = smoothstep(0.04, 0.0, abs(h - rec)) * step(0.001, rec) * step(rec, 0.999);
        col += vec3(1.0, 0.72, 0.6) * front * 1.8;
    }

    vec3 bary = vec3(1.0 - facet.x - facet.y, facet.x, facet.y);
    vec3 fw = fwidth(bary);
    vec3 e = smoothstep(vec3(0.0), fw * 1.3, bary);
    float edge = 1.0 - min(min(e.x, e.y), e.z);
    // a aresta tem largura em pixels: na gema pequena na tela ela cobriria a faceta inteira, então enfraquece
    float small = clamp(1.0 - 12.0 * max(max(fw.x, fw.y), fw.z), 0.35, 1.0);
    vec3 warmEdge = mix(CORAL, HOT, 0.5) * mix(vec3(1.0), tier * 1.6, 0.15);
    col += mix(STONE_EDGE, warmEdge, lit) * edge * 0.45 * small;
    col += HOT * pow(max(dot(reflect(-L, N), V), 0.0), 56.0) * (0.25 + 0.45 * lit);
    // a curva no maior canal e a cor escalada junto: a matiz do coral não desbota quando a luz soma
    float m = max(max(col.r, col.g), col.b);
    col *= (1.0 - exp(-1.3 * m)) / max(m, 1e-4);
    fragColor = vec4(col, 1.0);
}
