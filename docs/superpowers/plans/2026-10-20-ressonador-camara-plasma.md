# Ressonador: câmara com Núcleo de plasma · plano de implementação

> **Para agentes:** SUB-SKILL OBRIGATÓRIA: use superpowers:subagent-driven-development (recomendado) ou superpowers:executing-plans para executar este plano tarefa por tarefa. Os passos usam caixas (`- [ ]`) para acompanhar.

**Objetivo:** trocar o traço de Lissajous e a lente da câmara animada pelo Núcleo de plasma do protótipo aprovado, desenhado por um core shader do mod, com volta ao desenho de hoje quando um shaderpack do Iris estiver ativo.

**Arquitetura:** um core shader `wirelessautomate:resonator_plasma` (JSON, `.vsh` e `.fsh` em GLSL 150), registrado no `RegisterShadersEvent` e usado por um `RenderType` aditivo novo no `ChamberRenderTypes`. Como o `MultiBufferSource` junta os quads de todos os Núcleos num só desenho, os parâmetros de cada Núcleo **vão nos vértices**, não em uniforms: cor do tier no RGB, sintonia no alfa, cristais carregados e nível de detalhe no `UV2`; o tempo vem do uniform `GameTime`, que o vanilla já preenche. A parte nova `PlasmaCore` monta o billboard; o `ResonatorRenderer` escolhe entre ela e o par `LissajousTrace` + `ChamberLens` pelo `ShaderPackCheck`.

**Stack:** Java 21, NeoForge 21.1 (Minecraft 1.21.1), GLSL 150 (core shaders do vanilla).

**Spec:** [`docs/superpowers/specs/2026-10-20-ressonador-camara-plasma-design.md`](../specs/2026-10-20-ressonador-camara-plasma-design.md). Protótipo aprovado (o GLSL de referência): `docs/preview/ressonador-camara-shader.html`, aba Núcleo de plasma.

## Restrições globais

- Código, comentários, commits e docs em português.
- **Só cliente:** tudo em `client/resonator/render/` e nos recursos `assets/wirelessautomate/shaders/core/`; nada referenciado por código comum (o `runGameTestServer` é servidor dedicado). Nada muda em servidor, NBT, componentes, config ou protocolo (`ModPayloads.VERSION` continua o mesmo).
- **Iris opcional:** nenhuma dependência de compilação; a consulta ao `net.irisshaders.iris.api.v0.IrisApi` é por reflexão, guardada uma vez e reavaliada no máximo uma vez por quadro (`client/FrameClock`).
- **Performance (`wa-performance`, leis 3 e 8):** nada alocado por quadro no renderer (nada de `new` de vetor, matriz, lista ou lambda que captura em `render`); um quad por Núcleo; o fragment shader descarta fora do círculo do efeito, usa no máximo 48 amostras da curva e 4 oitavas de ruído, e corta as duas pela distância (`UV2.y`). Leia `.claude/skills/wa-performance/SKILL.md` e rode `python -I .claude/skills/wa-performance/scripts/varrer_perf.py` nos arquivos mudados; resolva ou justifique cada candidato no relatório. Medir antes e depois (Task 4).
- **Visual de referência:** o protótipo. Cores fixas: coral do cristal `#F2917A`, branco quente `#FFF0EA`; a borda e o feixe na cor do tier (`GuiPaint.tierColor`).
- Tamanho (`wa-modular`): renderer e partes até 500 linhas; `python -I .claude/skills/wa-modular/scripts/medir.py` nos arquivos mudados. Comentários (`wa-comentarios`): `python -I .claude/skills/wa-comentarios/scripts/varrer.py`.
- Antes de cada commit: `./gradlew build runGameTestServer runGameTestServerChemicals runGameTestServerSource runGameTestServerAllthemodium`. Como mexe em render, também o e2e: `WA_E2E="$PWD/run/e2e" ./gradlew runClient` (no Windows, sem `xvfb-run`; `run/e2e/result.txt` deve dizer `OK`) e **abra as capturas** `ressonador-camara*.png` (a ferramenta Read mostra imagens) e descreva o que viu. Compile antes (`./gradlew compileJava`); com `BUILD FAILED`, pare e conserte. Procure no `run/logs/latest.log` do e2e por `resonator_plasma` e `Could not compile` (erro de shader não derruba o jogo, só some o efeito). Nunca dois jogos ao mesmo tempo; não mexa em `run/options.txt`.
- Commits sem linhas de atribuição, com o autor do dono; `git add` só dos arquivos da tarefa (nunca `run/`). Não faça push.

## Mapa de arquivos

Base `B/` = `src/main/java/io/github/matheusanbs/wirelessautomate/`, `R/` = `src/main/resources/`.

| Arquivo | Papel |
| --- | --- |
| `R/assets/wirelessautomate/shaders/core/resonator_plasma.json`, `.vsh`, `.fsh` (novos) | O core shader |
| `B/client/resonator/render/PlasmaShader.java` (novo) | Guarda o `ShaderInstance` registrado (`@EventBusSubscriber` do cliente no `RegisterShadersEvent`) |
| `B/client/resonator/render/ChamberRenderTypes.java` (mudar) | `plasma()`: `POSITION_COLOR_TEX_LIGHTMAP`, aditivo, sem gravar profundidade, sem cull |
| `B/client/resonator/render/PlasmaCore.java` (novo) | O billboard do Núcleo de plasma, com os parâmetros nos vértices |
| `B/client/resonator/render/ChamberScene.java` (mudar) | Sintonia suavizada (0 a 1) e a contagem de cristais carregados, por tick |
| `B/client/resonator/render/ShaderPackCheck.java` (novo) | Se há shaderpack do Iris ativo, por reflexão e uma vez por quadro |
| `B/client/resonator/render/ResonatorRenderer.java` (mudar) | Plasma ou o desenho de hoje |
| `B/client/dev/E2eResonatorSteps.java` (mudar) | Capturas e a medida do tempo de quadro |

---

### Task 1: O core shader, o registro e o tipo de desenho

**Files:**
- Create: `R/assets/wirelessautomate/shaders/core/resonator_plasma.json`, `resonator_plasma.vsh`, `resonator_plasma.fsh`
- Create: `B/client/resonator/render/PlasmaShader.java`
- Modify: `B/client/resonator/render/ChamberRenderTypes.java`

**Interfaces:**
- Produces: `PlasmaShader.get()` (`ShaderInstance` ou `null` antes do registro ou se não compilou); `ChamberRenderTypes.plasma()` (`RenderType`, formato `DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP`, `VertexFormat.Mode.QUADS`).
- Contrato dos vértices: `Color` = (R, G, B do tier, A = sintonia 0..1); `UV0` = canto do quad em 0..1; `UV2` = (cristais carregados 0..8, detalhe 0..2: 2 perto, 1 médio, 0 longe).
- Custo: nenhum por quadro além do desenho.

- [ ] **Step 1: O JSON**

```json
{
  "vertex": "wirelessautomate:resonator_plasma",
  "fragment": "wirelessautomate:resonator_plasma",
  "attributes": ["Position", "Color", "UV0", "UV2"],
  "samplers": [],
  "uniforms": [
    { "name": "ModelViewMat", "type": "matrix4x4", "count": 16, "values": [1,0,0,0, 0,1,0,0, 0,0,1,0, 0,0,0,1] },
    { "name": "ProjMat", "type": "matrix4x4", "count": 16, "values": [1,0,0,0, 0,1,0,0, 0,0,1,0, 0,0,0,1] },
    { "name": "GameTime", "type": "float", "count": 1, "values": [0.0] }
  ]
}
```

- [ ] **Step 2: O vertex shader**

```glsl
#version 150

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV2;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec2 quad;
out vec4 params;
flat out ivec2 extra;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    quad = UV0;
    params = Color;
    extra = UV2;
}
```

- [ ] **Step 3: O fragment shader** (o `plasma()` do protótipo, com os parâmetros dos vértices, o descarte fora do círculo e o detalhe pela distância)

```glsl
#version 150

uniform float GameTime;

in vec2 quad;
in vec4 params;
flat in ivec2 extra;

out vec4 fragColor;

const vec3 CORAL = vec3(0.949, 0.569, 0.478);
const vec3 WHITE = vec3(1.0, 0.94, 0.92);

float h21(vec2 p) { p = fract(p * vec2(123.34, 456.21)); p += dot(p, p + 45.32); return fract(p.x * p.y); }
float n2(vec2 p) {
    vec2 i = floor(p), f = fract(p); f = f * f * (3.0 - 2.0 * f);
    return mix(mix(h21(i), h21(i + vec2(1, 0)), f.x), mix(h21(i + vec2(0, 1)), h21(i + vec2(1, 1)), f.x), f.y);
}
float fbm(vec2 p, int octaves) {
    float s = 0.0, a = 0.5;
    for (int i = 0; i < 4; i++) { if (i >= octaves) break; s += a * n2(p); p = p * 2.03 + vec2(1.7, 9.2); a *= 0.5; }
    return s;
}
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
    int detail = extra.y;
    int octaves = detail >= 2 ? 4 : (detail == 1 ? 3 : 2);
    int samples = detail >= 2 ? 48 : (detail == 1 ? 32 : 16);

    vec3 col = tier * 0.05 * (1.0 - r * 0.6);
    vec2 q = p * 3.0;
    float w = fbm(q + vec2(t * 0.4, -t * 0.3) + fbm(q * 1.5 - t * 0.2, octaves) * 1.6, octaves);
    float radius = 0.26 + 0.03 * sin(t * 2.0) + mis * 0.08 * (w - 0.5);
    float body = smoothstep(radius, radius - 0.12, r) * (0.55 + 0.9 * w);
    col += mix(tier, CORAL, smoothstep(0.0, radius, radius - r + 0.1)) * body * 1.3;
    col += WHITE * exp(-r * r * 55.0) * (1.2 + 0.4 * sin(t * 3.0));
    col += tier * 0.35 * exp(-r * 4.5);

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

    float st = h21(floor(gl_FragCoord.xy / 2.0) + floor(t * 24.0));
    col += vec3(st) * mis * mis * 0.22 * (1.0 - r);
    col = 1.0 - exp(-col * 1.25);
    float edge = smoothstep(1.0, 0.7, r);
    fragColor = vec4(col * edge, 1.0);
}
```

A soma é aditiva (o tipo de desenho), então o preto não acende nada; a borda some suave antes do descarte.

- [ ] **Step 4: O registro**

```java
/** O core shader do Núcleo de plasma, registrado no {@code RegisterShadersEvent}; {@code null} se não compilou. */
@EventBusSubscriber(modid = WirelessAutomate.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class PlasmaShader {
    private static ShaderInstance shader;

    private PlasmaShader() {
    }

    @SubscribeEvent
    static void register(RegisterShadersEvent event) throws IOException {
        event.registerShader(new ShaderInstance(event.getResourceProvider(), WirelessAutomate.id("resonator_plasma"),
                DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP), loaded -> shader = loaded);
    }

    public static ShaderInstance get() {
        return shader;
    }
}
```

(Confira a assinatura do construtor do `ShaderInstance` e do `registerShader` no NeoForge 21.1 do projeto; se o `ClientSetup` já for o lugar dos eventos de cliente do mod, registre por lá em vez de criar o `@EventBusSubscriber`.)

- [ ] **Step 5: O tipo de desenho** no `ChamberRenderTypes`:

```java
private static final RenderType PLASMA = create(WirelessAutomate.MODID + "_chamber_plasma",
        DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP, VertexFormat.Mode.QUADS, 256, false, true,
        CompositeState.builder()
                .setShaderState(new ShaderStateShard(PlasmaShader::get))
                .setTransparencyState(LIGHTNING_TRANSPARENCY)
                .setCullState(NO_CULL)
                .setWriteMaskState(COLOR_WRITE)
                .createCompositeState(false));

/** O billboard do Núcleo de plasma: aditivo, sem gravar profundidade, nas duas faces. */
static RenderType plasma() {
    return PLASMA;
}
```

e o javadoc da classe passa a citar os dois tipos.

- [ ] **Step 6: Verificar:** `./gradlew compileJava build runGameTestServer`; rodar o e2e uma vez e procurar no log `resonator_plasma` e `Could not compile` (o shader ainda não é desenhado; a tarefa só garante que compila e carrega).
- [ ] **Step 7: Commit:** `Câmara do Ressonador: core shader do Núcleo de plasma`.

---

### Task 2: O billboard e a troca no renderer

**Files:**
- Create: `B/client/resonator/render/PlasmaCore.java`
- Modify: `B/client/resonator/render/ChamberScene.java`, `B/client/resonator/render/ResonatorRenderer.java`

**Interfaces:**
- Consumes: `ChamberRenderTypes.plasma()`, o contrato dos vértices (Task 1).
- Produces: `ChamberScene.tune(float partialTick)` (0..1, interpolada entre o tick anterior e o atual), `ChamberScene.charged()` (cristais não Opacos nas câmaras, 0..8); `PlasmaCore.render(PoseStack, MultiBufferSource, ChamberScene, float partialTick, int tierColor, double distance)`.
- Custo: por quadro, 4 vértices por Núcleo; a sintonia e a contagem são atualizadas no `update` por tick que já existe.

- [ ] **Step 1: `ChamberScene`:** dois campos `float tunePrev, tuneNow`; no `update`, `tunePrev = tuneNow` e `tuneNow` anda 25% em direção a `view.tuned() ? 1f : view.band() / (float) (TuneSignal.BANDS - 1)` (sintonia suavizada, como no protótipo); `charged` conta os bytes 1 e 2 da `view.chambers()` (Sinal e Lapidado cheios; 3 e 4 são Opacos). `tune(partialTick) = Mth.lerp(partialTick, tunePrev, tuneNow)`.
- [ ] **Step 2: `PlasmaCore.render`:** o billboard de 2,6 × 2,6 blocos (cabe na câmara de 3) centrado no centro da câmara (o `poseStack` já está transladado para lá), virado para a câmera: use a rotação da câmera do jogo (`Minecraft.getInstance().gameRenderer.getMainCamera().rotation()`) no `poseStack` (`mulPose`) e quatro vértices em ±1,3. Cada vértice: `addVertex(pose, x, y, 0)`, `setColor(r, g, b, tune)` com o RGB de `tierColor`, `setUv(u, v)` nos cantos, `setLight` com o empacotamento de (cristais, detalhe): `LightTexture.pack(charged, detail)` não serve (limita a 0..15 e o UV2 chega ao shader como inteiros crus): use `setUv2(charged, detail)`. Detalhe: 2 a menos de 12 blocos, 1 até 32, 0 além. Nada alocado: o RGB vem do `int`, a matriz é a do `poseStack.last()`.
- [ ] **Step 3: `ResonatorRenderer`:** `double distance` = distância da câmera ao centro da câmara (já calculada para o `getViewDistance`? se não, `Vec3.distanceTo` sem criar vetor: compare com `distanceToSqr` dos componentes). Se `PlasmaShader.get() != null && !ShaderPackCheck.active()` (a Task 3 cria o `ShaderPackCheck`; nesta tarefa, use `PlasmaShader.get() != null`), desenhe `PlasmaCore.render(...)` no lugar de `LissajousTrace.render` + `ChamberLens.render`; senão, o desenho de hoje. A ordem dos outros (placa, cristais, faíscas) não muda; o plasma vai por último (aditivo, sem gravar profundidade).
- [ ] **Step 4: Verificar:** build e GameTests; o e2e com as capturas `ressonador-camara.png` e `ressonador-camara-chiado.png`: abra as duas e descreva (o plasma no centro, coral com a borda do tier; no chiado, o tremor e a cintilação; os cristais e a placa continuam). Compare com o protótipo.
- [ ] **Step 5: Commit:** `Câmara do Ressonador: Núcleo de plasma no lugar da lente e do traço`.

---

### Task 3: Volta ao desenho de hoje com shaderpack do Iris

**Files:**
- Create: `B/client/resonator/render/ShaderPackCheck.java`
- Modify: `B/client/resonator/render/ResonatorRenderer.java`

**Interfaces:**
- Produces: `ShaderPackCheck.active()` (`boolean`): `true` se o Iris está carregado e com shaderpack em uso.
- Custo: uma consulta por reflexão no máximo uma vez por quadro (guardada com o número do quadro do `client/FrameClock`); sem o Iris, só a checagem de um `boolean` guardado na primeira chamada.

- [ ] **Step 1: Implementar**

```java
/**
 * Se um shaderpack do Iris está em uso: com ele, o core shader do plasma pode sair errado, e a câmara volta ao desenho
 * de hoje. Por reflexão, para o mod não depender do Iris; a resposta vale por um quadro.
 */
final class ShaderPackCheck {
    private static final MethodHandle IN_USE = find();
    private static long frame = -1;
    private static boolean active;

    private ShaderPackCheck() {
    }

    static boolean active() {
        if (IN_USE == null) {
            return false;
        }
        long now = FrameClock.frame();
        if (now != frame) {
            frame = now;
            try {
                active = (boolean) IN_USE.invoke();
            } catch (Throwable e) {
                active = false;
            }
        }
        return active;
    }

    private static MethodHandle find() {
        try {
            Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            Object instance = api.getMethod("getInstance").invoke(null);
            return MethodHandles.publicLookup()
                    .findVirtual(api, "isShaderPackInUse", MethodType.methodType(boolean.class))
                    .bindTo(instance);
        } catch (ReflectiveOperationException | LinkageError e) {
            return null;
        }
    }
}
```

(Confira o nome do método que dá o número do quadro no `FrameClock`.)

- [ ] **Step 2:** no `ResonatorRenderer`, a condição da Task 2 passa a `PlasmaShader.get() != null && !ShaderPackCheck.active()`.
- [ ] **Step 3: Verificar:** build e GameTests; sem o Iris no `runClient` de dev, o e2e continua mostrando o plasma. Registrar no relatório que o teste com shaderpack fica para o dono (o dev não traz o Iris).
- [ ] **Step 4: Commit:** `Câmara do Ressonador: desenho de hoje com shaderpack do Iris`.

---

### Task 4: Medida, capturas e fechamento

**Files:**
- Modify: `B/client/dev/E2eResonatorSteps.java`, `CLAUDE.md`, `docs/progresso.md`

- [ ] **Step 1: Medir.** No passo do e2e que olha a câmara, guarde o tempo médio de quadro (`Minecraft.getInstance().getFrameTimeNs()` ou o contador de FPS) em 120 quadros com a câmara ocupando a tela, e grave no `run/e2e/result.txt` uma linha `camara: X ms/quadro`. Rode uma vez **antes** desta etapa (no commit anterior à Task 2, com `git stash` ou num checkout temporário, sem misturar arquivos) e uma depois, na mesma máquina e na mesma escala; ponha os dois números no relatório e no `docs/progresso.md`. Regressão maior que o ruído entre duas rodadas iguais pede reduzir o detalhe (amostras, oitavas ou o tamanho do quad).
- [ ] **Step 2: Olhar.** Abrir `ressonador-camara.png` e `ressonador-camara-chiado.png` e descrever. Se o plasma ficar escuro ou estourado em relação ao protótipo, ajustar só as constantes do `.fsh`.
- [ ] **Step 3: Docs.** `CLAUDE.md`: na linha do `client/resonator/render/`, o `PlasmaCore`, o `PlasmaShader`, o `ShaderPackCheck`, o core shader `resonator_plasma` e que o `LissajousTrace` e a `ChamberLens` ficam só para o shaderpack do Iris. `docs/progresso.md`: estado, próximo passo e uma linha no histórico com as medidas.
- [ ] **Step 4: Commit:** `Câmara do Ressonador: medida, capturas e docs`.

## Revisão final

O revisor confere: nada alocado no `render` (rodar o `varrer_perf.py`), o contrato dos vértices igual nos dois lados (Java e GLSL), o descarte e o detalhe pela distância funcionando, a volta ao desenho de hoje sem o shader (forçar `PlasmaShader.get()` nulo num teste manual) e as capturas.
