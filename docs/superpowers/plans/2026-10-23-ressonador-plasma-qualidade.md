# Ressonador: qualidade visual do plasma, das linhas e do raio — Plano de implementação

> **Para agentes:** SUB-SKILL OBRIGATÓRIA: use superpowers:subagent-driven-development (recomendado) ou superpowers:executing-plans para executar este plano tarefa a tarefa. Os passos usam caixas de seleção (`- [ ]`).

**Meta:** a esfera do meio, as linhas em volta e o raio de energia da câmara do Ressonador deixam de parecer um adesivo 2D e passam a ter volume, profundidade, brilho que se dissolve e cor com núcleo quente.

**Arquitetura:** só shader e câmara. Um include GLSL compartilhado (`wa_common.glsl`) dá ruído de gradiente 3D, o tonemap filmic, o gradiente de temperatura e o dither; o `resonator_plasma.fsh` ganha a esfera por interseção analítica e as linhas 3D (a rotação da câmera chega por um uniform `ViewRot`); o `resonator_ribbon.fsh` ganha o conduto cilíndrico com cometas. O cristal, as faíscas, o Chladni e o caminho antigo (shaderpack do Iris) não mudam.

**Tecnologia:** GLSL `#version 150` (core shaders do Minecraft 1.21.1, `#moj_import`), Java 21, JUnit 5, e2e pelo `WA_E2E_ONLY=ressonador`.

Spec: `docs/superpowers/specs/2026-10-23-ressonador-plasma-qualidade-design.md`.

## Restrições globais

Valem para toda tarefa (copiadas da spec e das leis da `wa-performance`):

- Sem dependência nova (nada de Veil, Lodestone, Photon nem GeckoLib).
- Fora de escopo: `resonator_crystal`, a tela do Núcleo, o Chladni, as faíscas e o caminho antigo (shaderpack do Iris ou shader que não compilou).
- Sem fade pela profundidade da cena (feedback loop ilegal) e sem passe de pós-processamento.
- Parado e longe custa o mesmo de hoje: só desenha Núcleo montado e dentro do alcance (`PlasmaCore.FAR_SQ` e `FlowConduit.RANGE_SQ`); sem alocação por quadro (campos `float` reaproveitados, nada de objeto novo) e sem mudar o buffer de vértices.
- O nível de detalhe por distância (`PlasmaCore.detail`: 2, 1, 0) continua mandando: octaves do `fbm` 4, 3 e 2; amostras da curva 48, 32 e 16; sem linhas de cristal no 0.
- O plasma novo no nível 2 não passa de ~1,5 vez o tempo de quadro do atual (medido pelo e2e, `camara plasma`).
- Comentários em português, só o porquê (skill `wa-comentarios`); texto de jogo não muda (nada de lang).
- Commits em português, sem linhas de atribuição (`Co-Authored-By`), no ramo `ressonador-plasma-qualidade`.
- Um cliente ou Gradle por vez (~3 GB cada). No Windows o e2e roda sem Xvfb: `WA_E2E_ONLY=ressonador WA_E2E="$PWD/run/e2e" ./gradlew runClient --console=plain` (JDK 21; o padrão da máquina é o 17, ver `docs/progresso.md`, nota "No Windows"). O cliente às vezes fica pendurado depois do `result.txt`: mate o processo java à mão.
- Ao ler uma captura: `Read` no `.png` de `run/e2e/`. A primeira linha do `run/e2e/result.txt` é `OK` ou `FALHA: ...`; `grep -i "não compilou\|Failed to\|moj_import\|error" run/e2e/client.log` acha erro de shader (o jogo não cai, só volta ao desenho antigo e avisa no log).
- Ajuste de look: as constantes de cada shader são o botão de ajuste; mude, rode o e2e filtrado, olhe a captura. Até 3 rodadas por tarefa antes de reportar; o aceite visual final é do dono.

## Estrutura de arquivos

| Arquivo | Papel |
| --- | --- |
| `src/main/resources/assets/wirelessautomate/shaders/include/wa_common.glsl` (novo) | hash inteiro, ruído de gradiente 3D e `fbm`, tonemap, gradiente de temperatura, perfil de linha, dither |
| `.../shaders/core/resonator_plasma.fsh` (reescrito) | a esfera, o halo, as linhas 3D e as órbitas dos cristais |
| `.../shaders/core/resonator_plasma.json` | o uniform `ViewRot` |
| `.../shaders/core/resonator_ribbon.fsh` | importa o include; modo 0 (conduto) reescrito; modos 1 a 3 com o tonemap e o dither novos |
| `client/resonator/render/PlasmaView.java` (novo) | pura: a matriz do inverso da rotação da câmera, em coluna-maior, a partir do quaternion |
| `client/resonator/render/PlasmaShader.java` | `setViewRotation(float[])` |
| `client/resonator/render/PlasmaCore.java` | `HALF` 1,45 e manda o `ViewRot` |
| `client/resonator/render/FlowConduit.java` | larguras da fita maiores, para o halo caber |
| `src/test/java/.../client/resonator/render/PlasmaViewTest.java` (novo) | JUnit da matriz |
| `CLAUDE.md`, `docs/progresso.md` | docs |

---

### Task 1: Ramo e linha de base

**Custo:** nenhum no jogo; só medição.

**Files:**
- Create: nada no código.
- Guarda as capturas e o tempo de quadro de antes em `run/e2e-antes/` (o `run/` não vai para o git).

**Interfaces:**
- Produces: `run/e2e-antes/ressonador-camara*.png` e `run/e2e-antes/result.txt` com as linhas `camara antiga` e `camara plasma`, a referência de todas as tarefas.

- [ ] **Step 1: Criar o ramo e commitar a spec e este plano**

```bash
git switch -c ressonador-plasma-qualidade
git add docs/superpowers/specs/2026-10-23-ressonador-plasma-qualidade-design.md docs/superpowers/plans/2026-10-23-ressonador-plasma-qualidade.md
git commit -m "Ressonador: spec e plano da qualidade visual do plasma"
```

- [ ] **Step 2: Rodar o e2e filtrado no código atual**

```bash
mkdir -p run/e2e && rm -f run/e2e/result.txt run/e2e/*.png
WA_E2E_ONLY=ressonador WA_E2E="$PWD/run/e2e" ./gradlew runClient --console=plain > run/e2e/client.log 2>&1
```

Expected: `run/e2e/result.txt` com `OK` na primeira linha, as capturas `ressonador-camara.png`, `ressonador-camara-chiado.png`, `ressonador-camara-fluxo.png`, `ressonador-camara-fluxo-alto.png`, `ressonador-camara-cheio.png`, `ressonador-camara-recarga*.png`, `ressonador-camara-cristais.png`, e linhas do tipo `camara antiga: 1.xxx ms/quadro` e `camara plasma: 1.xxx ms/quadro`.

- [ ] **Step 3: Guardar a linha de base**

```bash
rm -rf run/e2e-antes && cp -r run/e2e run/e2e-antes
grep "camara" run/e2e-antes/result.txt
```

Anote no relatório os tempos de `camara plasma` (parado, com conduto, com espiral): o teto das tarefas seguintes é 1,5 vez o parado, nível 2.

---

### Task 2: O include comum e as fitas com o tonemap e o dither novos

**Custo:** nenhum a mais por tick ou por quadro no Java. No fragment das fitas, um tonemap (poucas multiplicações) e um dither (uma `fract` e um `floor`) no fim: desprezível. Prova: o tempo de quadro da `camara plasma` do e2e não passa do ruído da linha de base.

**Files:**
- Create: `src/main/resources/assets/wirelessautomate/shaders/include/wa_common.glsl`
- Modify: `src/main/resources/assets/wirelessautomate/shaders/core/resonator_ribbon.fsh`

**Interfaces:**
- Produces (GLSL, usadas pelas Tasks 3 a 5): `uint wa_hash(uint)`, `float wa_h21(vec2)`, `float wa_noise(vec3)` (≈ −0,7 a 0,7), `float wa_fbm(vec3 p, int octaves)` (0 a 1), `vec3 wa_heat(float h)` (0 a 1: vermelho fundo, coral, amarelo, branco), `float wa_line(float d, float w)`, `vec3 wa_tonemap(vec3)`, `vec3 wa_dither(vec3)`.

- [ ] **Step 1: Criar o include**

```glsl
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
    vec3 c = mix(vec3(0.35, 0.04, 0.05), vec3(0.90, 0.28, 0.20), smoothstep(0.0, 0.3, h));
    c = mix(c, vec3(1.14, 0.68, 0.57), smoothstep(0.25, 0.55, h));
    c = mix(c, vec3(1.0, 0.86, 0.62), smoothstep(0.55, 0.8, h));
    return mix(c, vec3(1.0, 0.96, 0.92), smoothstep(0.8, 1.0, h));
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
```

- [ ] **Step 2: Importar o include no `resonator_ribbon.fsh` e usar as funções novas**

No `resonator_ribbon.fsh`: logo depois de `#version 150`, uma linha em branco e `#moj_import <wirelessautomate:wa_common.glsl>`; apagar as funções locais `h21` e `n2` (linhas 15 a 19); no modo 2 trocar `(n2(vec2(t * 9.0, seed * 3.7)) - 0.5)` por `(wa_noise(vec3(t * 9.0, seed * 3.7, 0.5)) * 1.2)`; no fim trocar

```glsl
    fragColor = vec4(1.0 - exp(-col * 1.2), 1.0);
```

por

```glsl
    fragColor = vec4(wa_dither(wa_tonemap(col)), 1.0);
```

O modo 0 não muda nesta tarefa (a Task 5 o reescreve).

- [ ] **Step 3: Rodar o e2e filtrado e conferir**

```bash
mkdir -p run/e2e && rm -f run/e2e/result.txt run/e2e/*.png
WA_E2E_ONLY=ressonador WA_E2E="$PWD/run/e2e" ./gradlew runClient --console=plain > run/e2e/client.log 2>&1
grep -i "não compilou\|Failed to\|moj_import" run/e2e/client.log
```

Expected: `result.txt` com `OK`; o `grep` sem saída (se acusar `uint`, `#moj_import` ou literal `U`, o include não compilou: ver o risco da spec, troque o hash por ponto flutuante só para destravar e relate). Abra `run/e2e/ressonador-camara-fluxo.png` e `ressonador-camara-recarga-perto.png` e compare com `run/e2e-antes/`: o brilho do conduto e da espiral deve estar mais rico e sem faixas; se estiver bem mais apagado ou estourado, mude o `0.85` do `wa_tonemap` e rode de novo.

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/assets/wirelessautomate/shaders
git commit -m "Ressonador: include comum dos shaders e as fitas com tonemap e dither"
```

---

### Task 3: A esfera 3D, o halo e a cor

**Custo:** o fragment do plasma passa a fazer a interseção analítica (uma raiz) e o ruído 3D só dentro do disco da esfera (`r < radius`, ~5% do quad); o resto do quad é só exponenciais. Octaves 4, 3 e 2 pelo nível de detalhe. Sem alocação nem mudança de vértices (só o `HALF`). Prova: `camara plasma` do e2e, parado, até 1,5 vez a linha de base.

**Files:**
- Modify: `src/main/resources/assets/wirelessautomate/shaders/core/resonator_plasma.fsh` (reescrita)
- Modify: `src/main/java/io/github/matheusanbs/wirelessautomate/client/resonator/render/PlasmaCore.java:17` (`HALF`)

**Interfaces:**
- Consumes: as funções do `wa_common.glsl` da Task 2.
- Produces: no `.fsh`, a variável `radius` (a esfera, em unidades do quad) e os marcadores `// LINHAS` e `// FIM LINHAS`, que a Task 4 substitui.

- [ ] **Step 1: Subir o `HALF` do quad**

Em `PlasmaCore.java`, `private static final float HALF = 1.3f;` vira `1.45f`. A esfera mantém o tamanho em blocos: no `.fsh`, `SPHERE = 0.234` (0,34 bloco ÷ 1,45).

- [ ] **Step 2: Reescrever o `resonator_plasma.fsh`**

As linhas 2D de Lissajous e as órbitas dos cristais continuam como estão hoje, só passando pelo tonemap novo, entre os marcadores.

```glsl
#version 150

#moj_import <wirelessautomate:wa_common.glsl>

uniform float GameTime;

in vec2 quad;
in vec4 params;
flat in ivec2 extra;

out vec4 fragColor;

const vec3 CORAL = vec3(0.949, 0.569, 0.478);
const vec3 WHITE = vec3(1.0, 0.94, 0.92);
// o raio da esfera em unidades do quad (HALF = 1,45 bloco): 0,34 bloco
const float SPHERE = 0.234;

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
    int detail = extra.y & 3;
    float press = extra.y >= 4 ? 0.5 + 0.5 * sin(t * 7.0) : 0.0;
    int octaves = detail >= 2 ? 4 : (detail == 1 ? 3 : 2);
    int samples = detail >= 2 ? 48 : (detail == 1 ? 32 : 16);

    // o raio respira, encolhe sob pressão e treme na borda quando fora de sintonia
    float radius = SPHERE * (1.0 + 0.06 * sin(t * 2.0)) * (1.0 - press * 0.2);
    if (mis > 0.02) {
        radius += mis * 0.04 * wa_noise(vec3(normalize(p + vec2(1e-5)) * 2.5, t * 1.2));
    }

    vec3 col = vec3(0.0);

    // o corpo: uma esfera de verdade, com normal, convecção e temperatura
    float inside = 1.0 - smoothstep(radius - 0.012, radius, r);
    if (inside > 0.0) {
        float rr = min(r / radius, 1.0);
        float zz = sqrt(max(0.0, 1.0 - rr * rr));
        vec3 n = vec3(p / radius, zz);
        float ca = cos(t * 0.35);
        float sa = sin(t * 0.35);
        vec3 sn = vec3(ca * n.x + sa * n.z, n.y, -sa * n.x + ca * n.z);
        vec3 q = sn * 2.4;
        float w1 = wa_fbm(q + vec3(0.0, t * 0.30, 0.0), octaves);
        float w2 = wa_fbm(q * 1.7 + vec3(w1 * 1.8, -t * 0.22, w1 * 1.2), octaves);
        float heat = clamp(0.30 + 0.75 * w2 + 0.45 * zz + 0.2 * w1 + (w1 - 0.5) * mis * 0.6, 0.0, 1.0);
        float fres = pow(1.0 - zz, 2.6);
        vec3 surf = wa_heat(heat) * (1.1 + 2.2 * heat * heat);
        surf = mix(surf, tier * 1.6, fres * 0.75);
        surf += tier * fres * (1.2 + press * 1.0);
        surf += WHITE * pow(zz, 6.0) * 0.5 * (0.8 + 0.2 * sin(t * 3.0));
        col += surf * inside;
    }

    // o halo: duas camadas exponenciais e um brilho amplo, todas a zero exato na borda do quad
    float rim = max(r - radius, 0.0);
    float fade = 1.0 - smoothstep(0.45, 1.0, r);
    fade *= fade;
    col += (tier * 1.1 * exp(-rim * 9.0) + CORAL * 0.55 * exp(-rim * 22.0)) * fade * (1.0 + press * 0.5);
    col += WHITE * 0.5 * exp(-r * r * 38.0) * fade;
    col += tier * 0.35 * exp(-r * 3.0) * fade;

    // LINHAS
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
    // FIM LINHAS

    // chiado de TV quando fora de sintonia
    float st = wa_h21(floor(gl_FragCoord.xy / 2.0) + vec2(floor(t * 24.0), 0.0));
    col += vec3(st) * mis * mis * 0.22 * (1.0 - r);

    fragColor = vec4(wa_dither(wa_tonemap(col)), 1.0);
}
```

- [ ] **Step 3: Rodar o e2e e olhar**

Mesmo comando da Task 2 (Step 3). Abra `ressonador-camara.png`, `ressonador-camara-cheio.png` (plasma sob pressão) e `ressonador-camara-chiado.png` (fora de sintonia) e compare com `run/e2e-antes/`. Confira: a esfera tem volume (limbo mais escuro e colorido, centro quente), o halo se dissolve sem borda de quad visível, o brilho escuro não tem faixas. Ajuste só constantes (`SPHERE`, os `exp(-rim * k)`, os pesos do halo, a mistura do `fres`, o `0.85` do tonemap) em até 3 rodadas. Confira o `result.txt`: a linha `camara plasma` (parado) até 1,5 vez a de `run/e2e-antes/result.txt`.

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/assets/wirelessautomate/shaders src/main/java/io/github/matheusanbs/wirelessautomate/client/resonator/render/PlasmaCore.java
git commit -m "Ressonador: esfera de plasma 3D com halo suave e cor filmic"
```

**Portão visual:** relate o resultado ao dono com as capturas (antes e depois) antes da Task 4; se ele pedir ajuste de look, é nesta tarefa.

---

### Task 4: As linhas em 3D e o uniform `ViewRot`

**Custo:** o laço de 16/32/48 segmentos continua; cada amostra ganha uma coordenada z e os senos de um terceiro eixo (mais 1 `sin` por amostra, ~48 por pixel de quad, só dentro de `r < 0,85`). No Java: um uniform `mat3` escrito por núcleo desenhado, com um `float[9]` estático reaproveitado: sem alocação. Prova: `camara plasma` do e2e, com conduto e com espiral, até 1,5 vez a linha de base.

**Files:**
- Create: `src/main/java/io/github/matheusanbs/wirelessautomate/client/resonator/render/PlasmaView.java`
- Create: `src/test/java/io/github/matheusanbs/wirelessautomate/client/resonator/render/PlasmaViewTest.java`
- Modify: `.../client/resonator/render/PlasmaShader.java`
- Modify: `.../client/resonator/render/PlasmaCore.java`
- Modify: `src/main/resources/assets/wirelessautomate/shaders/core/resonator_plasma.json`
- Modify: `src/main/resources/assets/wirelessautomate/shaders/core/resonator_plasma.fsh` (entre `// LINHAS` e `// FIM LINHAS`)

**Interfaces:**
- Produces: `PlasmaView.inverseRotation(float x, float y, float z, float w, float[] out)` (o quaternion da rotação da câmera; `out` recebe os 9 floats do inverso, em coluna-maior) e `PlasmaShader.setViewRotation(float[] matrix)`.

- [ ] **Step 1: O teste que falha**

```java
package io.github.matheusanbs.wirelessautomate.client.resonator.render;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PlasmaViewTest {
    private static final float EPS = 1e-5f;

    @Test
    void identityRotationGivesIdentityMatrix() {
        float[] m = new float[9];
        PlasmaView.inverseRotation(0, 0, 0, 1, m);
        float[] expected = {1, 0, 0, 0, 1, 0, 0, 0, 1};
        for (int i = 0; i < 9; i++) {
            assertEquals(expected[i], m[i], EPS, "elemento " + i);
        }
    }

    @Test
    void quarterTurnAroundYSendsWorldXToLocalZ() {
        // R gira +X para -Z (90° em Y); o inverso leva o +X do mundo para o +Z da câmera
        float s = (float) Math.sqrt(0.5);
        float[] m = new float[9];
        PlasmaView.inverseRotation(0, s, 0, s, m);
        // a primeira coluna (coluna-maior) é a imagem de (1, 0, 0)
        assertEquals(0f, m[0], EPS);
        assertEquals(0f, m[1], EPS);
        assertEquals(1f, m[2], EPS);
        // a terceira coluna é a imagem de (0, 0, 1): vira -X
        assertEquals(-1f, m[6], EPS);
        assertEquals(0f, m[7], EPS);
        assertEquals(0f, m[8], EPS);
    }
}
```

Run: `./gradlew test --tests "*PlasmaViewTest"`
Expected: FALHA de compilação (`PlasmaView` não existe).

- [ ] **Step 2: A classe**

```java
package io.github.matheusanbs.wirelessautomate.client.resonator.render;

/**
 * A matriz que leva um vetor do mundo para o quadro do billboard do plasma. O quad é girado pela rotação da câmera
 * (a mesma das partículas do vanilla), então o que está fixo no mundo precisa do inverso dela para o shader
 * desenhar a curva parada no mundo e não presa à tela. Pura: sem JOML e sem alocar.
 */
final class PlasmaView {
    private PlasmaView() {
    }

    /**
     * Escreve em {@code out} (9 floats, coluna-maior, o layout do uniform {@code mat3}) o inverso da rotação do
     * quaternion unitário {@code (x, y, z, w)}: a transposta, que é o inverso de uma rotação.
     */
    static void inverseRotation(float x, float y, float z, float w, float[] out) {
        float m00 = 1 - 2 * (y * y + z * z);
        float m01 = 2 * (x * y + z * w);
        float m02 = 2 * (x * z - y * w);
        float m10 = 2 * (x * y - z * w);
        float m11 = 1 - 2 * (x * x + z * z);
        float m12 = 2 * (y * z + x * w);
        float m20 = 2 * (x * z + y * w);
        float m21 = 2 * (y * z - x * w);
        float m22 = 1 - 2 * (x * x + y * y);
        out[0] = m00;
        out[1] = m10;
        out[2] = m20;
        out[3] = m01;
        out[4] = m11;
        out[5] = m21;
        out[6] = m02;
        out[7] = m12;
        out[8] = m22;
    }
}
```

Run: `./gradlew test --tests "*PlasmaViewTest"`
Expected: PASS (2 testes). Se o segundo falhar por sinal, confira a convenção do JOML (a primeira coluna de R para 90° em Y é (0, 0, −1)) antes de mexer no teste.

- [ ] **Step 3: O setter no `PlasmaShader`**

Acrescente em `PlasmaShader.java` (imports de `com.mojang.blaze3d.shaders.Uniform`):

```java
    /** Escreve o uniform {@code ViewRot} (9 floats, coluna-maior); sem o shader ou sem o uniform, não faz nada. */
    public static void setViewRotation(float[] matrix) {
        if (shader == null) {
            return;
        }
        Uniform uniform = shader.getUniform("ViewRot");
        if (uniform != null) {
            uniform.set(matrix);
        }
    }
```

Se `Uniform.set(float[])` não existir na classe do 1.21.1 (confira com `javap` no jar do Minecraft que o ModDevGradle gerou em `~/.gradle/caches` ou `build/moddev`: `javap -cp <jar> com.mojang.blaze3d.shaders.Uniform | grep -i "set\|Mat"`), use o `setMat3x3` com os nove floats na ordem dos parâmetros dele e diga no relatório qual usou.

- [ ] **Step 4: Mandar a rotação a cada núcleo desenhado**

Em `PlasmaCore.java`, um campo `private static final float[] VIEW = new float[9];` e, em `render`, depois do `mulPose` da câmera:

```java
        Quaternionf camera = Minecraft.getInstance().gameRenderer.getMainCamera().rotation();
        PlasmaView.inverseRotation(camera.x(), camera.y(), camera.z(), camera.w(), VIEW);
        PlasmaShader.setViewRotation(VIEW);
```

(import `org.joml.Quaternionf`). A mesma rotação serve a todos os núcleos do quadro, então o valor escrito por último vale para o lote inteiro.

- [ ] **Step 5: O uniform no `.json`**

Em `resonator_plasma.json`, depois do `GameTime`:

```json
    { "name": "ViewRot", "type": "matrix3x3", "count": 9, "values": [1,0,0, 0,1,0, 0,0,1] }
```

(com a vírgula na linha do `GameTime`).

- [ ] **Step 6: As linhas e as órbitas em 3D no `.fsh`**

Depois de `uniform float GameTime;` acrescente `uniform mat3 ViewRot;`, apague `liss` (a de 2D) e acrescente antes do `main`:

```glsl
const float TAU = 6.28318;

// A curva de Lissajous em 3D, parada no mundo (gira devagar em Y) e levada ao quadro do billboard pelo ViewRot.
vec3 curve(float s, float t, float mis) {
    vec3 c = vec3(sin(3.0 * s + t * 0.45), sin(2.0 * s + t * 0.30 + 1.5708), sin(5.0 * s + t * 0.22 + 0.7));
    c *= vec3(0.62, 0.46, 0.50);
    c += mis * 0.06 * vec3(sin(s * 13.0 + t * 7.0), cos(s * 11.0 - t * 5.0), sin(s * 9.0 + t * 6.0));
    float ca = cos(t * 0.12);
    float sa = sin(t * 0.12);
    return ViewRot * vec3(ca * c.x + sa * c.z, c.y, -sa * c.x + ca * c.z);
}
```

E troque tudo entre `// LINHAS` e `// FIM LINHAS` por:

```glsl
    // LINHAS
    float amp = 0.35 + 0.65 * tune;
    float behind = smoothstep(radius - 0.01, radius + 0.01, r);
    float dFront = 1e3;
    float dBack = 1e3;
    float zFront = 0.0;
    if (r < 0.85) {
        vec3 prev = curve(0.0, t, mis);
        for (int i = 1; i <= 48; i++) {
            if (i > samples) break;
            vec3 cur = curve(float(i) / float(samples) * TAU, t, mis);
            vec2 pa = p - prev.xy;
            vec2 ba = cur.xy - prev.xy;
            float h = clamp(dot(pa, ba) / max(dot(ba, ba), 1e-8), 0.0, 1.0);
            float dist = length(pa - ba * h);
            float z = mix(prev.z, cur.z, h);
            if (z >= 0.0) {
                if (dist < dFront) { dFront = dist; zFront = z; }
            } else if (dist < dBack) {
                dBack = dist;
            }
            prev = cur;
        }
    }
    vec3 lineColor = mix(CORAL, WHITE, 0.5);
    // o trecho de trás é mais fraco e some sob o corpo da esfera; o da frente passa por cima dela
    col += lineColor * wa_line(dBack, 0.006) * amp * 0.35 * behind;
    col += lineColor * wa_line(dFront, 0.007) * amp * (0.7 + 0.5 * zFront / 0.6);
    // a cabeça que percorre a curva
    vec3 head = curve(fract(t * 0.07) * TAU, t, mis);
    float dh = length(p - head.xy);
    float headVisible = head.z >= 0.0 ? 1.0 : behind;
    col += WHITE * (0.0016 / (dh * dh + 0.0007)) * amp * headVisible;

    // as órbitas dos cristais carregados: um anel inclinado no mundo, com o fio de cada um até o centro
    for (int i = 0; i < 8; i++) {
        if (float(i) >= crystals || detail == 0) break;
        float ang = t * 0.7 + float(i) * TAU / crystals;
        vec3 c3 = ViewRot * vec3(cos(ang) * 0.78, 0.2 * sin(ang * 2.0 + float(i)), sin(ang) * 0.78);
        float vis = c3.z >= 0.0 ? 1.0 : behind;
        float dc = length(p - c3.xy);
        col += mix(CORAL, tier, 0.35) * (0.0035 / (dc * dc + 0.0009)) * 0.08 * vis;
        col += tier * (0.0007 / (segD(p, c3.xy, vec2(0.0)) + 0.0015)) * tune
            * (0.5 + 0.5 * sin(t * 6.0 + float(i))) * vis;
    }
    // FIM LINHAS
```

- [ ] **Step 7: Rodar o e2e e conferir**

Mesmo comando da Task 2 (Step 3), e `grep` do log: sem `Uniform ... not found` nem erro de shader. Abra `ressonador-camara.png` e `ressonador-camara-cristais.png`. Confira: a curva tem a parte de trás mais fraca e some atrás da esfera; as órbitas passam por trás e pela frente; **mude a câmera** (o e2e tem outras posições nas capturas `-fluxo`, `-recarga-perto`) e veja que a curva fica parada no mundo, sem girar com a tela. Ajuste constantes (larguras 0,006/0,007, pesos, amplitude) em até 3 rodadas; tempo de quadro até 1,5 vez a linha de base nas três situações do `result.txt`.

- [ ] **Step 8: Commit**

```bash
git add src/main src/test
git commit -m "Ressonador: linhas de Lissajous em 3D paradas no mundo (uniform ViewRot)"
```

---

### Task 5: O raio (conduto) cilíndrico com cometas

**Custo:** o fragment do conduto faz um `wa_noise` e 3 exponenciais por pixel da fita (fita estreita, só quando alguma Porta entrega e dentro de `RANGE_SQ`). No Java, só as constantes de largura. Prova: `camara plasma` com conduto (`E2eResonatorFlowSteps`) até 1,5 vez a linha de base.

**Files:**
- Modify: `src/main/resources/assets/wirelessautomate/shaders/core/resonator_ribbon.fsh` (modo 0)
- Modify: `src/main/java/io/github/matheusanbs/wirelessautomate/client/resonator/render/FlowConduit.java:21-22`

**Interfaces:**
- Consumes: só `wa_noise` do include; no `main` do `resonator_ribbon.fsh`, `t`, `tier`, `power`, `mode`, `arg` e `col` já existem.

- [ ] **Step 1: Alargar a fita para o halo caber**

Em `FlowConduit.java`: `MIN_WIDTH = 0.08f` vira `0.14f` e `MAX_WIDTH = 0.3f` vira `0.5f` (o núcleo do shader passa a ser uma fração da meia-largura, então o fio de luz continua do tamanho de antes).

- [ ] **Step 2: Reescrever o modo 0**

Troque o bloco `if (mode == 0) { ... }` (do comentário "conduto" até a linha do `col *= smoothstep(...)`) por:

```glsl
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
```

(o `} else if (mode == 1) {` fecha o bloco e já existe: ao editar, não o duplique). O `t` é em segundos, então `fract(t * speed)` dá de 0,55 a 1,45 voltas por segundo conforme a faixa.

- [ ] **Step 3: Rodar o e2e e conferir**

Mesmo comando da Task 2 (Step 3). Abra `ressonador-camara-fluxo.png` e `ressonador-camara-fluxo-alto.png` (faixas 5 e 6) e compare com `run/e2e-antes/`. Confira: núcleo claro com halo, cometas visíveis, clarão no plasma e na Porta, nada que corte o halo nas bordas da fita, e a faixa alta mais larga e rápida que a baixa. Ajuste `coreW`, as constantes de `exp`, `speed` e os pesos em até 3 rodadas. `camara plasma` com conduto até 1,5 vez a linha de base.

- [ ] **Step 4: Commit**

```bash
git add src/main
git commit -m "Ressonador: raio de energia cilíndrico com cometas e clarão nas pontas"
```

**Portão visual:** relate ao dono as capturas de antes e depois do plasma inteiro (esfera, linhas e raio).

---

### Task 6: Fechamento

**Custo:** nenhum no jogo; confere o orçamento e a documentação.

**Files:**
- Modify: `CLAUDE.md` (a linha `client/resonator/render/` do mapa)
- Modify: `docs/progresso.md` (tabela de estado, próximo passo e uma linha no histórico)

- [ ] **Step 1: Varreduras**

```bash
python -I .claude/skills/wa-performance/scripts/varrer_perf.py
python -I .claude/skills/wa-comentarios/scripts/varrer.py
```

Resolva ou justifique cada candidato nos arquivos mudados (`PlasmaCore`, `PlasmaShader`, `PlasmaView`, `FlowConduit`).

- [ ] **Step 2: Medida final e build**

Rode o e2e filtrado (comando da Task 2, Step 3) e compare o `result.txt` com `run/e2e-antes/result.txt`: as linhas `camara plasma` (parado, com conduto, com espiral) até 1,5 vez a base; a linha `camara antiga` igual, dentro do ruído (o caminho antigo não mudou). Depois:

```bash
./gradlew build
```

Expected: BUILD SUCCESSFUL, com o `PlasmaViewTest` entre os JUnit. Não há GameTest novo (sem lógica de servidor).

- [ ] **Step 3: Docs**

No `CLAUDE.md`, na linha `client/resonator/render/`: onde fala do core shader `resonator_plasma`, diga que a esfera é 3D por interseção analítica, as linhas de Lissajous são curvas 3D paradas no mundo (uniform `ViewRot`, `PlasmaView`), o `shaders/include/wa_common.glsl` (ruído de gradiente, tonemap, dither) é comum ao plasma e às fitas e o modo 0 das fitas é o conduto cilíndrico com cometas. No `docs/progresso.md`: a linha do Ressonador na tabela de estado, o próximo passo e uma linha de 2026-10-23 no histórico, com os tempos de antes e depois e a nota de que o bloom real (Veil) ficou como opção futura.

- [ ] **Step 4: Commit**

```bash
git add CLAUDE.md docs/progresso.md
git commit -m "Ressonador: docs da qualidade visual do plasma"
```

---

## Autorrevisão

- **Cobertura da spec:** include comum (Task 2); esfera 3D com Fresnel, convecção e temperatura (3); halo exponencial e `HALF` (3); linhas 3D com profundidade, cabeça e órbitas, e o `ViewRot` sem alocar (4); conduto cilíndrico, cometas, ruído, clarão e modos 1 a 3 com o tonemap novo (2 e 5); tonemap e dither (2 e 3); detalhe por distância (3 e 4); custo medido pelo e2e e a varredura (6); caminho antigo intacto, conferido pela linha `camara antiga` (6).
- **Sem marcadores pendentes:** todo passo de código traz o código; as constantes de look são ajuste declarado, com limite de rodadas e portão do dono.
- **Consistência:** `PlasmaView.inverseRotation(x, y, z, w, out)`, `PlasmaShader.setViewRotation(float[])`, o uniform `ViewRot`, o marcador `// LINHAS` e as funções `wa_*` têm os mesmos nomes em todas as tarefas.
