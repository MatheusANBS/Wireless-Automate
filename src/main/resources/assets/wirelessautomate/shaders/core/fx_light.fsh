#version 150

// A luz no mundo: refaz a posição do pixel pela profundidade, prende-a à grade de 1/16 e soma as luzes em degraus.
// O #version 150 já dá precisão alta no fragment; as divisões têm guarda (placas Intel e Apple não perdoam NaN).

uniform sampler2D DiffuseSampler;
uniform sampler2D DepthSampler;
uniform mat4 InvViewProj;
uniform vec3 CamFrac;
uniform int LightCount;
uniform vec4 LightPos[32];
uniform vec4 LightCol[32];

// A mistura: a luz tinge um pouco o escuro (BASE) e multiplica o que já está aceso (GAIN).
const float BASE = 0.2;
const float GAIN = 2.0;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec4 scene = texture(DiffuseSampler, texCoord);
    float depth = texture(DepthSampler, texCoord).r;
    // as derivadas saem antes de qualquer saída antecipada: elas pedem fluxo uniforme no quadrado de 2x2 pixels
    vec4 clip = InvViewProj * vec4(texCoord * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    float w = abs(clip.w) > 1e-6 ? clip.w : 1e-6;
    vec3 p = clip.xyz / w;
    vec3 n = cross(dFdx(p), dFdy(p));
    n = dot(n, n) > 1e-12 ? normalize(n) : vec3(0.0, 1.0, 0.0);
    if (depth >= 1.0) {
        fragColor = scene;
        return;
    }
    if (dot(n, p) > 0.0) {
        n = -n;
    }
    vec3 sp = (floor((p + CamFrac) * 16.0 + n * 0.5) + 0.5) / 16.0 - CamFrac;
    vec3 light = vec3(0.0);
    for (int i = 0; i < 32; i++) {
        if (i >= LightCount) {
            break;
        }
        vec3 d = LightPos[i].xyz - sp;
        float ds = length(d);
        float f = clamp(1.0 - ds / max(LightPos[i].w, 1e-4), 0.0, 1.0);
        f = floor(f * f * 5.0 + 0.35) / 5.0;
        float face = clamp(dot(n, d / max(ds, 1e-4)) * 0.6 + 0.4, 0.0, 1.0);
        light += LightCol[i].rgb * LightCol[i].a * f * mix(0.4, 1.0, face);
    }
    float top = max(light.r, max(light.g, light.b));
    if (top > 1.0) {
        light /= 1.0 + (top - 1.0) * 1.2;
    }
    fragColor = vec4(scene.rgb + light * (BASE + GAIN * scene.rgb), scene.a);
}
