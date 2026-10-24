// Funções comuns dos core shaders do Ressonador (resonator_plasma e resonator_ribbon). Sem #version: o
// #moj_import cola este texto no fragment shader.

// Hash inteiro (sem seno nem fract: sem o padrão visível do hash antigo).
uint wa_hash(uint x) {
    x ^= x >> 16;
    x *= 0x7feb352dU;
    x ^= x >> 15;
    x *= 0x846ca68bU;
    x ^= x >> 16;
    return x;
}

// Ruído branco por célula, de 0 a 1.
float wa_h21(vec2 p) {
    uvec2 q = uvec2(ivec2(floor(p)) + ivec2(32768));
    return float(wa_hash(q.x * 1597334677U ^ q.y * 3812015801U)) * (1.0 / 4294967295.0);
}

vec3 wa_grad(ivec3 c) {
    uvec3 q = uvec3(c + ivec3(4096));
    uint h = wa_hash(q.x * 1597334677U ^ q.y * 3812015801U ^ q.z * 2798796415U);
    vec3 g = vec3(float(h & 255U), float((h >> 8) & 255U), float((h >> 16) & 255U)) * (1.0 / 127.5) - 1.0;
    return g / (length(g) + 1e-4);
}

// Ruído de gradiente 3D com interpolação quíntica: suave, sem os blocos do value noise.
float wa_noise(vec3 p) {
    vec3 i = floor(p);
    vec3 f = p - i;
    vec3 u = f * f * f * (f * (f * 6.0 - 15.0) + 10.0);
    ivec3 c = ivec3(i);
    float n000 = dot(wa_grad(c), f);
    float n100 = dot(wa_grad(c + ivec3(1, 0, 0)), f - vec3(1.0, 0.0, 0.0));
    float n010 = dot(wa_grad(c + ivec3(0, 1, 0)), f - vec3(0.0, 1.0, 0.0));
    float n110 = dot(wa_grad(c + ivec3(1, 1, 0)), f - vec3(1.0, 1.0, 0.0));
    float n001 = dot(wa_grad(c + ivec3(0, 0, 1)), f - vec3(0.0, 0.0, 1.0));
    float n101 = dot(wa_grad(c + ivec3(1, 0, 1)), f - vec3(1.0, 0.0, 1.0));
    float n011 = dot(wa_grad(c + ivec3(0, 1, 1)), f - vec3(0.0, 1.0, 1.0));
    float n111 = dot(wa_grad(c + ivec3(1, 1, 1)), f - vec3(1.0, 1.0, 1.0));
    return mix(mix(mix(n000, n100, u.x), mix(n010, n110, u.x), u.y),
               mix(mix(n001, n101, u.x), mix(n011, n111, u.x), u.y), u.z);
}

// Soma de oitavas do ruído (até 4), de 0 a 1.
float wa_fbm(vec3 p, int octaves) {
    float s = 0.0;
    float a = 0.5;
    for (int i = 0; i < 4; i++) {
        if (i >= octaves) break;
        s += a * wa_noise(p);
        p = p * 2.03 + vec3(1.7, 9.2, 4.1);
        a *= 0.5;
    }
    return clamp(0.5 + s * 0.9, 0.0, 1.0);
}

// A temperatura do plasma: 0 é vermelho fundo, 1 é branco quente.
vec3 wa_heat(float h) {
    vec3 c = mix(vec3(0.30, 0.02, 0.03), vec3(0.95, 0.18, 0.10), smoothstep(0.0, 0.3, h));
    c = mix(c, vec3(1.15, 0.42, 0.22), smoothstep(0.25, 0.55, h));
    c = mix(c, vec3(1.2, 0.72, 0.30), smoothstep(0.55, 0.8, h));
    return mix(c, vec3(1.1, 0.95, 0.80), smoothstep(0.85, 1.0, h));
}

// O perfil de um traço de luz a distância d do eixo: núcleo gaussiano de largura w e uma cauda curta.
float wa_line(float d, float w) {
    return exp(-d * d / (w * w)) * 1.4 + 0.22 * w / (d + w);
}

// Tonemap filmic (ACES): o miolo chega ao branco e as bordas ficam saturadas.
vec3 wa_tonemap(vec3 c) {
    vec3 x = c * 0.85;
    return clamp((x * (2.51 * x + 0.03)) / (x * (2.43 * x + 0.59) + 0.14), 0.0, 1.0);
}

// Arredonda para 8 bits com ruído intercalado: tira o banding do brilho escuro. O zero fica zero, então o
// quad não aparece como um quadrado fraco sobre a cena (a soma é aditiva).
vec3 wa_dither(vec3 c) {
    float n = fract(52.9829189 * fract(dot(gl_FragCoord.xy, vec2(0.06711056, 0.00583715))));
    return floor(c * 255.0 + n) / 255.0;
}
