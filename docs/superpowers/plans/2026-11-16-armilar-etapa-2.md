# Armilar, etapa 2: o render

> **Para os agentes:** use `superpowers:subagent-driven-development`, uma tarefa por subagente, com revisão entre elas (`.claude/skills/wa-feature/references/conducao-de-agentes.md`). Os passos usam caixas (`- [ ]`). Subagentes de implementação e de revisão em Sonnet.

**Objetivo:** trocar a câmara antiga do Ressonador (plasma, tentáculos, Lissajous) pelo render do conceito Armilar aprovado: três órbitas de latão com cristais, o sol, os filamentos, a conjunção que sai pela Porta de Energia e a luz que o conjunto joga no mundo, com e sem shaderpack do Iris.

**Arquitetura:**
- Um pacote genérico novo, `client/fx/`, leva o que a skill `wa-efeitos` pede: core shaders `fx`, `fx_add` e `fx_add_premul`, `RenderType`s por folha de textura, desenho tardio por cima do quadro quando há shaderpack, passada de luz no mundo e pré-carga das texturas.
- A Armilar (`client/resonator/render/`) é lógica pura (geometria facetada, pose, fase da conjunção, nós, estado dos cristais) mais um BER fino. As peças sólidas vão pelo BER com o tipo de entidade do vanilla. Os efeitos são um `FxJob` que o BER anota e o `client/fx/` desenha no estágio certo.
- As texturas saem do gerador Python (`scripts/textures/`). Os números e os tempos vêm do filme do conceito, portados ao pé da letra.

**Tecnologias:** NeoForge 21.1.248 (Minecraft 1.21.1, Java 21), JUnit 5, GLSL `#version 150`, Python 3 com PIL no gerador, Iris 1.8.12 e Sodium 0.6.13 só no cliente de dev (`-Pshaders`).

**Spec:** `docs/superpowers/specs/2026-11-16-armilar-etapa-2-render-design.md`. É o requisito: leia inteira antes de qualquer tarefa. Leia também a skill `.claude/skills/wa-efeitos/SKILL.md` e as três referências dela (`pipeline.md`, `arte.md`, `armadilhas.md`).

## Restrições globais

- **Idioma:** código, comentários, commits e docs em português. Todo texto de jogo novo entra no `en_us.json` e no `pt_br.json`, nos dois (esta etapa não deve criar texto de jogo).
- **Commits:** um por tarefa, no ramo `armilar`, sem linhas de atribuição (nada de `Co-Authored-By` nem `Claude-Session`). O dono autorizou em 16/11/2026. Nunca `--no-verify`.
- **Fidelidade ao conceito** (memória "fiel ao conceito"): números, tempos, camadas e cores vêm do filme. Cada tarefa cita a função de origem; porte sem adivinhar e, onde a spec ou o jogo obriga a mudar um número, escreva o motivo no comentário do código e no relatório.
- **Fontes do conceito** (fora do repositório, só leitura), em `C:\Users\matheuss\Desktop\Sistemas\Wireless-Automate\ressonador-conceitos\`:
  - `filme/partes.js`: o objeto `W.armilar` (`raios`, `nos`, `engaste`, `malhas`, `tau`, `pose`, `luzes`, `desenhar`, `fase`, `ponteiro`);
  - `filme/shared.js`: `anelFacetado`, `cristal`, `caixa`, `quad`, `quadUV`, `spline`, e os métodos `spr`, `dec`, `fita` e o `XFS` (o fragment do filme, com a trama Bayer) do renderizador;
  - `filme/fx_atlas.py` (o kit de texturas) e `filme/fx.json` (os retângulos);
  - `gerar_conceitos.py`, função `armilar()` (horizonte e marcos), e `armilar_formado.py` (o que muda na grade);
  - `filme/formado.html` (a Armilar formada, usada na prancha): `formado.html?t=<s>&u=1&yaw=38&pitch=24&dist=215&ty=30`.
- **Unidades:** o filme usa pixels (1 px = 1/16 de bloco). A lógica pura fica em px e converte para blocos só na hora de desenhar. Nos sprites e nas fitas do filme, `s` e `w` são **meio lado** (o quad vai de −s a +s).
- **Referencial:** a origem é o centro do chão da Armilar (bloco local (2,5; 0; 2,5)), +x para o leste (Porta de Energia), +z para o sul (Núcleo), y para cima. O `ArmilarFrame` (Tarefa 4) leva o local ao mundo pela mesma regra do `ResonatorLayout` (`toWorld`, a frente é o `FACING` do Núcleo).
- **Rede e dados:** nada novo. `ModPayloads.VERSION` não muda, nenhuma chave de NBT muda, o `ChamberView` não ganha campo. Esta etapa só mexe no cliente.
- **Performance** (`.claude/skills/wa-performance/SKILL.md`):
  - nada por tick no servidor;
  - no cliente, sem Armilar montada à vista o custo é zero (BER fora do frustum não roda, e a fila de efeitos vazia sai na primeira linha dos ganchos);
  - nada de alocação por quadro: malhas montadas uma vez, vetores e arrays de rascunho reaproveitados, nenhum `lambda` que capture variável no caminho do quadro;
  - efeitos param a 48 blocos, peças sólidas a 64 (a distância de vista do BER).
  Antes de cada commit, rode `python -I .claude/skills/wa-performance/scripts/varrer_perf.py` e resolva ou justifique cada candidato no relatório.
- **Comentários:** antes de cada commit, rode `python -I .claude/skills/wa-comentarios/scripts/varrer.py`.
- **Tamanho:** antes de cada commit, rode `python -I .claude/skills/wa-modular/scripts/medir.py <arquivos mudados>` e divida o que passar do limite.
- **Texturas:** só pelo gerador, nunca à mão no PNG. Rode `python -I scripts/textures/gerar_texturas.py` na raiz e siga a skill `wa-texturas`. O gerador reescreve dezenas de PNGs nesta máquina (versão do PIL): commite só o que a mudança pede (`git status` antes do `git add`, e `git checkout -- <arquivo>` no que não é seu).
- **Verificação pesada:** esta etapa não toca em lógica de servidor. Todas as tarefas terminam com `./gradlew build` (compila e roda o JUnit). A bateria completa (`./gradlew build runGameTestServer runGameTestServerChemicals runGameTestServerSource runGameTestServerAllthemodium`) só roda na última tarefa.
- **O jogo** (memória "pensar antes de rodar"):
  - só abre nas tarefas que dizem "Rodada de jogo", e cada uma pede **uma rodada avisada ao dono**, com o que vai ser visto escrito antes;
  - compile antes (`./gradlew compileJava`); com `BUILD FAILED`, conserte em vez de esperar;
  - depois da rodada, leia o log (`grep -c "Couldn't compile"` no `client.log`, e `grep -ci "exception"`), mostre as imagens ao dono e **pare**: não ajuste a arte no olho em série;
  - nunca dois jogos ao mesmo tempo (cada um usa ~3 GB e o mesmo `run/`); nunca mexa em `run/options.txt`;
  - a janela é real: não use o mouse durante o e2e.
- **Testes:** JUnit para toda lógica pura (pose, fase, nós, malha, mapa de estados, amostragem de fitas, fila de pré-carga). O e2e cobre só o que se vê no jogo. Todo teste de contagem ou de geometria compara com **números tirados do filme** (os de `Ouro` abaixo); onde o filme não dá o número, o teste afirma a regra (encosta sem atravessar) e o limite.
- **Ambiente com shaderpack:** `./gradlew runClient -Pshaders` põe Sodium e Iris no cliente de dev (Tarefa 1). Pacotes em `run/shaderpacks`: `ComplementaryReimagined_r5.9.3.zip` e `BSL_v10.1.8.zip`. A escolha é pelo `run/config/iris.properties`, nunca por `run/options.txt`.
- **Conferir antes de usar:** onde o plano diz **(conferir)**, a assinatura depende da API do NeoForge 1.21.1, do Minecraft 1.21.1 ou do Iris e não foi lida aqui. Confira no jar (`javap -p -cp <jar> <classe>`, jars no cache do Gradle e em `build/moddev/`) ou no código do projeto, e nunca invente assinatura.

### Ouro: números do filme para os testes

Tirados de `partes.js` e `shared.js` rodados em Node (sem alterar nada). São a referência dos testes das Tarefas 4, 5, 8, 9 e 10.

- `tau(t)` com `tc = 11`, `S = 1,2`: `tau(0) = −0,528024488`, `tau(5) = −0,764012244`, `tau(11) = 0`, `tau(12,5) = 1,5`, `tau(13,6) = 2,6`, `tau(100) = −0,0117918517`, `tau(5000,123) = −0,773331452`.
- `pose(i, t, u)`:

  | t | u | i | tilt | prec | spin |
  | --- | --- | --- | --- | --- | --- |
  | 0 | 0 | 0 | 0,62 | 0 | 0 |
  | 0 | 0 | 1 | −0,718163232 | 2,1 | 0 |
  | 0 | 0 | 2 | 0,881887775 | 4,2 | 0 |
  | 3,3 | 0 | 0 | 0,707329715 | 0,99 | 2,64 |
  | 3,3 | 0 | 1 | −0,856128782 | 1,44 | −1,815 |
  | 3,3 | 0 | 2 | 0,909385914 | 4,662 | 1,32 |
  | 5000,123 | 0 | 0 | 0,536989938 | 1500,0369 | 4000,0984 |
  | 5000,123 | 0 | 1 | −0,797076823 | −997,9246 | −2750,06765 |
  | 5000,123 | 0 | 2 | 1,03057712 | 704,21722 | 2000,0492 |
  | 5000,123 | 1 | 0 | 0 | 1500,0369 | 5993,23079 |
  | 11 | 1 | 0, 1, 2 | 0,0277092284 / −0,00200063039 / −0,0298711189 | 3,3 / −0,1 / 5,74 | 6,28318531 / −6,28318531 / 6,28318531 |

  Com `u = 1` o `spin` fica igual a `1,2 × (t − 11)` módulo 2π em todas as órbitas (as três alinhadas na conjunção).
- `fase(11 + d, u = 1)` (campos `dis, prep, cresce, raio, clarao, onda, cabeca, arco, porta, surge, onda2, clarao2`):

  | d | dis | prep | cresce | raio | clarao | onda | cabeca | arco | porta | surge | onda2 | clarao2 |
  | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
  | −0,7 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
  | −0,3 | 0 | 0,5 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
  | 0 | 0 | 1 | 0,104 | 0,5 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
  | 0,1 | 0 | 0,5 | 0,648 | 1 | 0 | 0 | 0 | 0,198250729 | 0 | 0 | 0 | 0 |
  | 0,15 | 0 | 0,15625 | 0,896 | 1 | 0,68359375 | 0 | 0,028 | 1 | 0 | 0 | 0 | 0 |
  | 0,2 | 0 | 0 | 1 | 1 | 1 | 0,0232907588 | 0,104 | 1 | 0 | 0 | 0 | 0 |
  | 0,6 | 0 | 0 | 1 | 1 | 0 | 0,912847483 | 1 | 1 | 0,5 | 0,0740740741 | 0 | 0,801749271 |
  | 0,9 | 0 | 0 | 1 | 1 | 0 | 1 | 1 | 1 | 1 | 1 | 0,5 | 0,352 |
  | 2 | 0,5 | 0 | 1 | 0,5 | 0 | 1 | 1 | 0,5 | 0,5 | 1 | 1 | 0 |
  | 2,5 | 1 | 0 | 1 | 0 | 0 | 1 | 1 | 0 | 0 | 1 | 1 | 0 |

  Com `u = 0,5` e `d = 0,3`: `raio = 0,5`, `clarao = 0,448`, `arco = 0,5`, `porta = 0`.
- `luzes(t, {a: 1, u: 1})`, o `k` das três luzes (sol, horizonte, Porta), todas com raio 64, 26 e 34: `t = 11`: 0,726742041 / 0 / 0; `t = 10,5`: 0,62531445 / 0 / 0; `t = 11,1`: 1,08754258 / 0 / 0; `t = 11,2`: 1,2013577 / 1,4 / 0; `t = 11,62`: 1,2371649 / 0 / 1,2544; `t = 12`: 1,24998757 / 0 / 1,6; `t = 13`: 0,933481804 / 0 / 0,8. Com `a = 0,5` e `u = 0`, `t = 3`: 0,31722556 / 0 / 0. Posições: sol (0; 47,5; 0), horizonte (32,5; 47,5; 0), Porta (44; 8; 0).
- `ponteiro`: `f = lerp(0,5 + 0,36 sin(0,7 t) cos(0,29 t), 0,62, u)`: `(t, u) = (0, 0)`: 0,5; `(3, 0)`: 0,700383314; `(7,7; 0)`: 0,672446372; `(3, 1)`: 0,62.
- Brasas em órbita `(k, t, prep)` → `(x, y, z, alfa/ig)`: `(0, 0, 0)` → 8,5; 0; 0; 0,55. `(5, 3,3, 0)` → −6,03735103; −2,12675212; 8,28328996; 0,597029601. `(11, 7,1, 1)` → 2,26941044; −0,058632461; 3,08833814; 0,893678177. `(7, 100,5, 0,5)` → −6,039774; −1,54541097; 5,03584095; 0,358169151.
- Brasas do impacto, `(k) → (r1, r2)`: `0 → 0,211321159; 0,791346058`, `3 → 0,330932785; 0,0717497752`, `15 → 0,809379287; 0,193364645`.
- Malhas (contagem de quads): `anelFacetado(13, 24)` = 96, `(19,5; 32)` = 128, `(26; 40)` = 160 (4 por segmento). `cristal(1,5; 1,5; 1,9)` = 18. `caixa` = 6.
  - `anelFacetado(13, 24)`, `p0` de cada quad (posição, uv no atlas do filme, normal): quad 0 (topo): (11,5; 1; 0), v 0, n (0, 1, 0). Quad 1 (fora): (14,5; 1; 0), v 3, n (0,99144; 0; −0,13053). Quad 2 (dentro): (11,10815; 1; −2,97642), v 5, n (−0,99144; 0; 0,13053). Quad 3 (base): (14,5; −1; 0), v 7, n (0; −1; 0). O segundo vértice do quad 0 é (11,10815; 1; −2,97642) e o terceiro (14,00592; 1; −3,75288).
  - `cristal`: quad 0 (lado): vértices (0,75; 1,5; 1,29904), (1,5; 1,5; 0), (1,5; −1,5; 0), (0,75; −1,5; 1,29904), n (0,86603; 0; 0,5). Quad 1 (ponta de cima): (0,75; 1,5; 1,29904), (0; 3,4; 0), (0; 3,4; 0), (1,5; 1,5; 0), n (0,71491; 0,5644; 0,41275). Quad 2 (ponta de baixo): (1,5; −1,5; 0), (0; −3,4; 0), (0; −3,4; 0), (0,75; −1,5; 1,29904), n (0,71491; −0,5644; 0,41275).

---

## Mapa de arquivos

Pacote base: `src/main/java/io/github/matheusanbs/wirelessautomate/` (abreviado `B/`). Testes em `src/test/java/io/github/matheusanbs/wirelessautomate/` (`T/`).

| Arquivo | Papel |
| --- | --- |
| `build.gradle` (mudar) | A flag `-Pshaders` (Iris e Sodium como `localRuntime`) |
| `B/client/fx/ShaderPackCheck.java` (vem do `client/resonator/render/`) | Iris em uso e passada de sombra, por reflexão |
| `B/client/fx/FxShaders.java`, `FxTypes.java` (novos) | Os três core shaders e os `RenderType`s (tinta, aditivo, luz pré-multiplicada) por folha |
| `B/client/fx/FxSheet.java`, `FxSheets.java` (novos) | A folha de quadros: tamanho, quadros, camada, uv do quadro; a lista de todas as folhas |
| `B/client/fx/FxWarmup.java`, `WarmupQueue.java` (novos) | Pré-carga das texturas, 2 por tick |
| `B/client/fx/FxContext.java`, `FxJob.java`, `FxQueue.java`, `FxBatch.java`, `FxDepth.java`, `FxStages.java` (novos) | O quadro dos efeitos: contexto, fila, buffer, profundidade salva e os ganchos de estágio |
| `B/client/fx/FxDraw.java`, `FxRibbon.java` (novos) | Sprite, decalque e fita; amostragem Catmull-Rom e bezier (pura) |
| `B/client/fx/FxLights.java`, `FxLightPass.java` (novos) | As luzes do quadro e a passada de luz no mundo |
| `assets/wirelessautomate/shaders/core/fx*.{json,vsh,fsh}`, `fx_light.*` (novos) | Os core shaders |
| `B/client/resonator/render/ArmilarSpec.java` (novo) | As constantes do conceito (raios, nós, engaste, sol, período) |
| `B/client/resonator/render/QuadMesh.java`, `ArmilarMeshes.java` (novos) | A malha facetada em lógica pura: anéis, engastes, pinos, cristal, horizonte e marcos |
| `B/client/resonator/render/ArmilarFrame.java` (novo) | Local em px para mundo, pela frente do Núcleo |
| `B/client/resonator/render/Ease.java`, `ArmilarPose.java`, `ArmilarPhase.java`, `ArmilarMotion.java`, `ArmilarNodes.java`, `ArmilarAssembly.java`, `ArmilarFlow.java` (novos) | Estado e tempo em lógica pura |
| `B/client/resonator/render/CrystalLook.java`, `CrystalPlan.java`, `ArmilarRecharge.java`, `ArmilarGrains.java` (novos) | Os estados dos cristais, a recarga e os grãos, puros |
| `B/client/resonator/render/ArmilarScene.java`, `ArmilarRenderer.java`, `ArmilarSolids.java` (novos) | O estado por Núcleo no cliente, o BER e o desenho das peças sólidas |
| `B/client/resonator/render/ArmilarPass.java`, `ArmilarSun.java`, `ArmilarFilaments.java`, `ArmilarPointer.java`, `ArmilarBeam.java`, `ArmilarArc.java`, `ArmilarImpact.java`, `ArmilarEmbers.java`, `ArmilarLights.java` (novos) | Os efeitos e as luzes |
| `B/client/repairer/render/*` (receber) | `PlasmaCore`, `PlasmaShader`, `RibbonShader`, `RibbonQuad`, `RibbonParam`, `CrystalShader`, `CrystalGem`, `CrystalMesh`, `CrystalParam`, `RepairerRenderTypes` (o resto do `ChamberRenderTypes`) |
| `B/client/resonator/render/ResonatorRenderer`, `ChamberScene`, `FlowConduit`, `ChamberRing`, `ChamberLens`, `ChamberParticles`, `ChamberSparks`, `ChamberCrystals`, `LissajousCurve`, `LissajousTrace`, `PlasmaTendrils`, `ArcClock` (apagar) | A câmara antiga |
| `T/client/resonator/render/ChamberRingTest`, `LissajousCurveTest`, `ArcClockTest` (apagar); `CrystalMeshTest`, `CrystalParamTest`, `CrystalParamFxTest`, `RibbonParamTest` (mover) | Testes antigos |
| `assets/wirelessautomate/shaders/core/resonator_ribbon.fsh` (mudar) | Sai o modo 0 (raio) e o 2 (tentáculo) |
| `B/client/ClientSetup.java` (mudar) | O BER novo e o registro dos shaders (linhas 150 a 163) |
| `scripts/textures/fx_texturas.py`, `armilar_render.py` (novos), `gerar_texturas.py` (mudar) | As folhas de efeito e as texturas das peças sólidas |
| `assets/wirelessautomate/textures/fx/*.png`, `textures/armilar/*.png` (novos) | Saída do gerador |
| `B/client/dev/E2eArmilarRenderSteps.java`, `E2eFxProbeSteps.java` (novos); `E2eResonatorSteps`, `E2eResonatorFlowSteps`, `E2eResonatorScreenSteps`, `E2eSuites`, `E2eShowcaseResonator` (mudar) | O e2e da Armilar |
| `scripts/e2e.sh` (mudar) | `--shaders <pacote\|nenhum>` |
| `docs/mapa/ressonador.md`, `docs/mapa/efeitos.md` (novo), `docs/e2e.md`, `CLAUDE.md`, `docs/progresso/*` (mudar) | Documentação |

---

### Tarefa 1: a flag `-Pshaders` e a base `client/fx/`

Entrega: dá para rodar o cliente com Iris, e o pacote `client/fx/` tem os shaders, os tipos de desenho, a detecção do Iris com passada de sombra e a pré-carga. Nada é desenhado ainda.

**Arquivos:**
- Mudar: `build.gradle` (depois do bloco `repositories`, perto da linha 213)
- Criar: `src/main/resources/assets/wirelessautomate/shaders/core/fx.json`, `fx.vsh`, `fx.fsh`, `fx_add.json`, `fx_add.vsh`, `fx_add.fsh`, `fx_add_premul.json`, `fx_add_premul.vsh`, `fx_add_premul.fsh`
- Mover (`git mv`): `B/client/resonator/render/ShaderPackCheck.java` para `B/client/fx/ShaderPackCheck.java`; mudar os imports em `B/client/repairer/render/RepairerRenderer.java` e `B/client/resonator/render/ResonatorRenderer.java`
- Criar: `B/client/fx/FxSheet.java`, `FxSheets.java`, `FxShaders.java`, `FxTypes.java`, `WarmupQueue.java`, `FxWarmup.java`
- Mudar: `B/client/ClientSetup.java` (`registerShaders`)
- Testes (novos): `T/client/fx/FxSheetTest.java`, `FxSheetsTest.java`, `WarmupQueueTest.java`

**Interfaces:**
- **Produz:**
  - `FxSheet(String name, int width, int frameHeight, int frames, Layer layer, boolean repeatU)`, com `enum Layer { INK, LIGHT }`, `ResourceLocation texture()` (`wirelessautomate:textures/fx/<name>.png`), `int height()`, `float v0(int frame)`, `float v1(int frame)`, `int clampFrame(int)`, `int frameAt(double seconds, double fps)` (o `qd` do filme), `int frameByFraction(double f)` (o `qf` do filme).
  - `FxSheets`: uma constante por folha (tabela abaixo) e `static List<FxSheet> all()` com as de tinta primeiro.
  - `FxShaders.register(RegisterShadersEvent)`, `ink()`, `add()`, `light()` (`ShaderInstance`, `null` se não compilou) e `ready()`.
  - `FxTypes.of(FxSheet)` (tinta ou luz pré-multiplicada, pela camada da folha), `FxTypes.add(FxSheet)` (aditivo sem pré-multiplicar, para o Reparador e o que vier) e `FxTypes.all()` (todos os tipos de `FxSheets.all()`, tinta antes).
  - `ShaderPackCheck.active()` (já existia) e `ShaderPackCheck.shadowPass()`.
  - `WarmupQueue(List<ResourceLocation>)` com `List<ResourceLocation> next(int n)`; `FxWarmup.reset()`.
- **Consome:** nada de tarefa anterior.

- [ ] **Passo 1: a flag.** Em `build.gradle`, depois do bloco `repositories { exclusiveContent ... }` (o Maven do Modrinth já está lá), acrescente:

```groovy
// -Pshaders: Iris e Sodium só no cliente de dev (runClient e o e2e), para testar com shaderpack. Entram como
// localRuntime e não vão para o jar do mod (docs/e2e.md).
if (project.hasProperty('shaders')) {
    dependencies {
        localRuntime "maven.modrinth:sodium:mc1.21.1-0.6.13-neoforge"
        localRuntime "maven.modrinth:iris:1.8.12+1.21.1-neoforge"
    }
}
```

  Confirme que `localRuntime` existe (a linha 127 já faz `runtimeClasspath.extendsFrom localRuntime` e a 136 usa `localRuntime` para o JEI).

- [ ] **Passo 2: conferir a flag sem abrir o jogo.** Rode:

  `./gradlew dependencies --configuration runtimeClasspath -Pshaders | grep -iE "iris|sodium"` e veja as duas linhas; sem `-Pshaders`, o mesmo comando não mostra nada. Rode `./gradlew jar -Pshaders` e abra o jar com `unzip -l`: não pode ter classe do Iris nem do Sodium. Se o Gradle não achar a versão, confira o nome exato do arquivo no Modrinth (`https://api.modrinth.com/maven/maven/modrinth/iris/maven-metadata.xml`) e ajuste.

- [ ] **Passo 3: o teste da folha (falha primeiro).** `T/client/fx/FxSheetTest.java`:

```java
package io.github.matheusanbs.wirelessautomate.client.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FxSheetTest {
    private static final FxSheet SOL = new FxSheet("sol", 32, 32, 6, FxSheet.Layer.LIGHT, false);

    @Test
    void quadroPorRelogio() {
        assertEquals(0, SOL.frameAt(0.0, 8));
        assertEquals(1, SOL.frameAt(1.0 / 8 + 1e-9, 8));
        assertEquals(5, SOL.frameAt(5.5 / 8, 8));
        assertEquals(0, SOL.frameAt(6.0 / 8 + 1e-9, 8));
        // o relógio negativo também dá um quadro válido (o qd do filme)
        assertEquals(5, SOL.frameAt(-0.01, 8));
    }

    @Test
    void quadroPorFracao() {
        assertEquals(0, SOL.frameByFraction(-1));
        assertEquals(0, SOL.frameByFraction(0));
        assertEquals(3, SOL.frameByFraction(0.5));
        assertEquals(5, SOL.frameByFraction(1.0));
        assertEquals(5, SOL.frameByFraction(7));
    }

    @Test
    void uvFicaDentroDoQuadro() {
        for (int f = 0; f < 6; f++) {
            float v0 = SOL.v0(f);
            float v1 = SOL.v1(f);
            assertTrue(v0 >= f / 6f && v1 <= (f + 1) / 6f && v0 < v1, "quadro " + f);
        }
        assertEquals(192, SOL.height());
        // o quadro de fora do intervalo é limitado
        assertEquals(SOL.v0(5), SOL.v0(99));
    }
}
```

  Rode `./gradlew test --tests '*FxSheetTest'` e veja falhar (classe inexistente).

- [ ] **Passo 4: `FxSheet`.**

```java
package io.github.matheusanbs.wirelessautomate.client.fx;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import net.minecraft.resources.ResourceLocation;

/**
 * Uma folha de efeito: um PNG em {@code textures/fx/<name>.png} com {@code frames} quadros empilhados, cada um com
 * {@code width} × {@code frameHeight} texels. O quadro de cada animação sai do relógio do efeito
 * ({@link #frameAt}), nunca do {@code .mcmeta}. {@code repeatU} marca as tiras que repetem pelo comprimento (o
 * {@code u} passa de 1), que pedem a textura com a repetição ligada.
 */
public record FxSheet(String name, int width, int frameHeight, int frames, Layer layer, boolean repeatU) {
    /** Tinta: alfa, com a luz real, por baixo. Luz: aditiva pré-multiplicada, luz cheia, por cima. */
    public enum Layer { INK, LIGHT }

    /** A folga de meio décimo de texel para dentro do quadro, para o NEAREST não pegar a linha do vizinho. */
    private static final float EDGE = 0.05f;

    public FxSheet {
        if (width <= 0 || frameHeight <= 0 || frames <= 0) {
            throw new IllegalArgumentException(name);
        }
    }

    public ResourceLocation texture() {
        return WirelessAutomate.id("textures/fx/" + name + ".png");
    }

    public int height() {
        return frameHeight * frames;
    }

    public int clampFrame(int frame) {
        return Math.max(0, Math.min(frames - 1, frame));
    }

    /** O v do alto do quadro. */
    public float v0(int frame) {
        return (clampFrame(frame) * frameHeight + EDGE) / height();
    }

    /** O v de baixo do quadro. */
    public float v1(int frame) {
        return ((clampFrame(frame) + 1) * frameHeight - EDGE) / height();
    }

    /** O {@code qd} do filme: {@code floor(t × fps) mod frames}, sempre de 0 a frames − 1. */
    public int frameAt(double seconds, double fps) {
        long k = (long) Math.floor(seconds * fps);
        return (int) (((k % frames) + frames) % frames);
    }

    /** O {@code qf} do filme: {@code floor(f × frames)} limitado ao intervalo. */
    public int frameByFraction(double f) {
        return clampFrame((int) Math.floor(f * frames));
    }
}
```

  Confirme em `WirelessAutomate` que existe `id(String)` (o `CrystalShader` já usa `WirelessAutomate.id("resonator_crystal")`).

- [ ] **Passo 5: `FxSheets`** e o teste dela. As folhas saem do `fx_atlas.py` (mesmos nomes e tamanhos; os quadros animados empilhados):

  | Constante | Arquivo | Largura × altura do quadro | Quadros | Camada | Repete u | Origem em `fx_atlas.py` |
  | --- | --- | --- | --- | --- | --- | --- |
  | `FUMACA` | `fumaca` | 16 × 16 | 4 | INK | não | `fumaca()` |
  | `TRILHOS` | `trilhos` | 64 × 8 | 1 | INK | sim | `trilhos()` |
  | `BRILHO` | `brilho` | 16 × 16 | 1 | LIGHT | não | `brilho(…, 16, 'brilho')` |
  | `BRILHO_G` | `brilho_g` | 32 × 32 | 1 | LIGHT | não | `brilho(…, 32, 'brilho_g')` |
  | `SOL` | `sol` | 32 × 32 | 6 | LIGHT | não | `sol()` |
  | `CHAMA` | `chama` | 16 × 24 | 6 | LIGHT | não | `chama()` |
  | `ESTRELA` | `estrela` | 16 × 16 | 4 | LIGHT | não | `estrela()` |
  | `ARO_TRACEJADO` | `aro_tracejado` | 32 × 32 | 1 | LIGHT | não | `aro(…, 'aro_tracejado', 15, 2, True)` |
  | `BRASA0`, `BRASA1`, `BRASA2` | `brasa0`, `brasa1`, `brasa2` | 3 × 3, 4 × 4, 6 × 6 | 1 | LIGHT | não | `brasa(k)` |
  | `RISCO_L` | `risco_l` | 12 × 2 | 1 | LIGHT | não | `risco(…, 12, 'risco_l')` |
  | `CHOQUE` | `choque` | 32 × 32 | 6 | LIGHT | não | `choque()` |
  | `CLARAO` | `clarao` | 32 × 32 | 4 | LIGHT | não | `clarao()` |
  | `TRILHOS_RIM` | `trilhos_rim` | 64 × 8 | 1 | LIGHT | sim | `trilhos_rim()` |
  | `MIOLO` | `miolo` | 64 × 8 | 1 | LIGHT | sim | `miolo()` |
  | `FLUXO` | `fluxo` | 64 × 8 | 1 | LIGHT | sim | `fluxo(0)` (o `fluxo0` do filme; o `fluxo1` e o `aro_fino` e o `risco` de 8 × 2 não são usados pela Armilar e não são gerados) |
  | `RAIO` | `raio` | 64 × 8 | 8 | LIGHT | sim | `raio(k * 7 + 1)`, k de 0 a 7 |
  | `SIGILO` | `sigilo` | 64 × 64 | 4 | LIGHT | não | `sigilo()` |

  `FxSheets.all()` devolve uma `List<FxSheet>` imutável com `FUMACA` e `TRILHOS` primeiro (a ordem em que o `FxBatch` desenha: tinta, depois luz). Teste (`FxSheetsTest`): nomes únicos; exatamente duas folhas de tinta (`fumaca` e `trilhos`) e vêm antes de todas as de luz; toda folha tem `width > 0`, `frames >= 1`; as tiras (`repeatU`) têm altura de quadro 8.

- [ ] **Passo 6: `WarmupQueue` e o teste.**

```java
package io.github.matheusanbs.wirelessautomate.client.fx;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.ResourceLocation;

/** A fila da pré-carga: entrega as texturas aos poucos, {@code n} por vez, na ordem em que foram dadas. */
final class WarmupQueue {
    private final List<ResourceLocation> pending;
    private int next;

    WarmupQueue(List<ResourceLocation> all) {
        this.pending = new ArrayList<>(all);
    }

    /** Até {@code n} texturas que ainda não saíram; vazio quando acabou. */
    List<ResourceLocation> next(int n) {
        List<ResourceLocation> out = new ArrayList<>(n);
        while (out.size() < n && next < pending.size()) {
            out.add(pending.get(next++));
        }
        return out;
    }

    boolean done() {
        return next >= pending.size();
    }
}
```

  Teste: com 5 texturas e `n = 2`, três chamadas devolvem 2, 2 e 1; a quarta devolve vazio e `done()` é `true`; a ordem é a dada.

- [ ] **Passo 7: `FxWarmup`.** Um `@EventBusSubscriber(modid = WirelessAutomate.MODID, value = Dist.CLIENT)`, como o `FrameClock`. Ao entrar no mundo (**(conferir)** o evento: `ClientPlayerNetworkEvent.LoggingIn`), `reset()` cria uma `WarmupQueue(textura de cada FxSheets.all())` mais as texturas das peças sólidas (a Tarefa 7 acrescenta; deixe `static void add(ResourceLocation)` para ela). A cada `ClientTickEvent.Post` (**(conferir)** o nome), tira `next(2)` e chama `Minecraft.getInstance().getTextureManager().getTexture(rl)` em cada uma (isso registra e carrega a textura). Sem fila, retorna na primeira linha.

- [ ] **Passo 8: `ShaderPackCheck` no lugar novo.** `git mv` e acrescente `shadowPass()`, do mesmo jeito que o `active()` (a mesma busca por reflexão, outro método):

```java
    private static final MethodHandle IN_SHADOW = find("isRenderingShadowPass");

    /** Se o Iris está desenhando a passada de sombra agora; sem Iris, sempre falso. Nada de efeito nessa passada. */
    public static boolean shadowPass() {
        if (IN_SHADOW == null) {
            return false;
        }
        try {
            return (boolean) IN_SHADOW.invoke();
        } catch (Throwable e) {
            return false;
        }
    }
```

  Generalize o `find()` para receber o nome do método (`find("isShaderPackInUse")` e `find("isRenderingShadowPass")`). A resposta de `shadowPass()` **não** é guardada por quadro: a passada de sombra e a do mundo se alternam no mesmo quadro. Atualize o Javadoc da classe (hoje diz "a câmara volta ao desenho de hoje"; agora diz que a detecção serve ao desenho tardio e que nada se desenha na passada de sombra). **(Conferir)** com `javap -cp <iris.jar> net.irisshaders.iris.api.v0.IrisApi` que `isRenderingShadowPass()` existe, sem argumentos, devolvendo `boolean`.

- [ ] **Passo 9: os três core shaders.** Copie do jar do cliente os três arquivos `assets/minecraft/shaders/core/rendertype_entity_translucent.{json,vsh,fsh}` (**conferir** o conteúdo exato da 1.21.1; procure o jar em `~/.gradle/caches/` ou `build/moddev/`) e faça as mudanças abaixo. O formato de vértice é `DefaultVertexFormat.NEW_ENTITY` nos três.
  - **`fx`** (tinta; luz real; névoa do ar limitada):
    - No `.json`: acrescente os uniforms `AlphaCut` (`float`, valor padrão `0.004`) e `AirFog` (`float`, valor padrão `0.35`); tire o `Sampler1` (o overlay deixa de ser lido, o UV1 passa a levar a dissolução).
    - No `.vsh`: troque a leitura do overlay por `dissolve = float(UV1.x) / 1000.0;` (saída `out float dissolve;`), e mantenha o resto (luz do mapa pelo UV2, `minecraft_mix_light` pela normal, distância da névoa).
    - No `.fsh`: `if (color.a < AlphaCut) discard;` no lugar do corte de 0,1; **a trama Bayer do filme** (copie `b2` e `b4` do `XFS` de `shared.js` e use o texel da folha: `ivec2 sz = textureSize(Sampler0, 0); vec2 tx = floor(texCoord0 * vec2(sz));` e `if (dissolve > 0.0 && b4(tx) < dissolve) discard;`); sem a mistura do overlay; a névoa limitada: `float fog = min(linear_fog_value(vertexDistance, FogStart, FogEnd), AirFog * inAir)` não serve, porque o ar e a água não se distinguem pelo shader. Faça assim: o uniform `AirFog` é o **teto da névoa** (0,35 no ar; 1,0 na água, na lava e na cegueira, escolhido pelo Java no desenho, passo 10), e `color.rgb = mix(color.rgb, FogColor.rgb, min(linear_fog_value(vertexDistance, FogStart, FogEnd), AirFog))`. **(Conferir)** o nome `linear_fog_value` em `fog.glsl` da 1.21.1.
  - **`fx_add`** (aditivo, sem névoa e sem luz): `.json` sem `Sampler2`, sem `FogStart`, `FogEnd` e `FogColor`, com `AlphaCut`. `.vsh`: `vertexColor = Color;` (nada de `minecraft_mix_light` nem lightmap), `texCoord0 = UV0;`, `dissolve` como acima. `.fsh`: `color = texture(Sampler0, texCoord0); if (color.a < AlphaCut) discard;` mais a trama Bayer; `fragColor = color * vertexColor * ColorModulator;`.
  - **`fx_add_premul`**: igual ao `fx_add`, e a saída é `fragColor = vec4(color.rgb * color.a, 1.0)` depois de multiplicar `color` pela cor do vértice (o `rgb × a` com alfa 1 do filme, para o `ONE, ONE` somar sem halo escuro).
  - `#version 150`; nada de `vec3(vec3, float)`, nada de palavra reservada (`sample`, `input`...); conferir `armadilhas.md`.

- [ ] **Passo 10: `FxShaders` e `FxTypes`.** `FxShaders` segue o `CrystalShader` (um `load` com `event.registerShader(new ShaderInstance(event.getResourceProvider(), WirelessAutomate.id(nome), DefaultVertexFormat.NEW_ENTITY), sink)`, `try/catch (IOException | RuntimeException)` com `LOGGER.warn`), três vezes. `ready()` é `ink != null && add != null && light != null`.

  `FxTypes` estende `RenderType` (para acessar os `...Shard` protegidos, como o `ChamberRenderTypes` faz; copie o construtor privado dele) e guarda três `Map<ResourceLocation, RenderType>` (`INK`, `ADD`, `LIGHT`), preenchidos no carregamento da classe a partir de `FxSheets.all()` (nada criado por quadro):
  - **tinta:** `RenderType.create("wirelessautomate_fx_ink", NEW_ENTITY, QUADS, 1536, false, true, state)` com `setShaderState(new ShaderStateShard(FxShaders::ink))`, `setTextureState(new TextureStateShard(tex, false, false))`, `TRANSLUCENT_TRANSPARENCY`, `NO_CULL`, `LIGHTMAP`, `COLOR_WRITE` (sem escrever profundidade), com o teste de profundidade `LEQUAL_DEPTH_TEST`. O teto da névoa: um `TransparencyStateShard` próprio não serve; use um `RenderStateShard` de nome `"wirelessautomate_air_fog"` cujo `setupState` faz `FxShaders.ink().getUniform("AirFog").set(FxTypes.airFogCap())`, com `airFogCap()` devolvendo 1,0 se `Minecraft.getInstance().gameRenderer.getMainCamera().getFluidInCamera() != FogType.NONE` ou se o jogador tem cegueira ou escuridão (**conferir** `Camera.getFluidInCamera()`, `FogType`, `MobEffects.BLINDNESS`/`DARKNESS`) e 0,35 no resto.
  - **aditivo (`add`):** blend `SRC_ALPHA, ONE`, sem ordenação (`sortOnUpload = false`), `NO_CULL`, `COLOR_WRITE`, shader `fx_add`, luz cheia.
  - **luz pré-multiplicada (`of` para folhas LIGHT):** blend `ONE, ONE` (copie o `TransparencyStateShard` `PREMULTIPLIED` do `ChamberRenderTypes`, trocando o fator de destino para `ONE`), sem ordenação, `NO_CULL`, `COLOR_WRITE`, shader `fx_add_premul`.
  - Para a textura repetir nas tiras, a textura precisa estar com o modo de repetição padrão do OpenGL (`GL_REPEAT`); o vanilla não mexe nisso se não houver `.mcmeta` com `clamp`. **(Conferir)** com a primeira imagem da Tarefa 3.
  - `FxTypes.of(sheet)` devolve `INK` ou `LIGHT` pela camada; `all()` devolve a lista na ordem de `FxSheets.all()`.

- [ ] **Passo 11: registrar.** Em `ClientSetup.registerShaders` acrescente `FxShaders.register(event);` (as linhas 150 a 163 hoje só registram plasma, fitas e cristal). Atualize o Javadoc do método.

- [ ] **Passo 12: verificar.**
  - `./gradlew build` verde (compila, roda o JUnit).
  - `python -I .claude/skills/wa-comentarios/scripts/varrer.py`, `python -I .claude/skills/wa-modular/scripts/medir.py <arquivos mudados>` e `python -I .claude/skills/wa-performance/scripts/varrer_perf.py`.
  - Os shaders só se provam no jogo; a primeira rodada é a da Tarefa 3.

- [ ] **Passo 13: commit.** `git add build.gradle src/main src/test` (só o que é desta tarefa) e `git commit -m "Efeitos: a flag -Pshaders, os core shaders fx e o pacote client/fx"`.

---

### Tarefa 2: as texturas, pelo gerador

Entrega: as folhas de efeito do filme em `textures/fx/` e as texturas das peças sólidas em `textures/armilar/`, geradas por código, com uma prancha para o dono.

**Arquivos:**
- Criar: `scripts/textures/fx_texturas.py`, `scripts/textures/armilar_render.py`
- Mudar: `scripts/textures/gerar_texturas.py` (`main`, e o docstring)
- Criar (saída do gerador, não à mão): `src/main/resources/assets/wirelessautomate/textures/fx/*.png` (19 folhas), `textures/armilar/*.png` (7 texturas), `docs/preview/armilar-fx.png`, `docs/preview/armilar-pecas.png`
- Teste (novo): `T/client/fx/FxTexturesFilesTest.java`

**Interfaces:**
- **Consome:** `FxSheets.all()` (Tarefa 1, os nomes e os tamanhos).
- **Produz:** os arquivos `textures/fx/<nome>.png` com o tamanho de `FxSheet` (largura × altura do quadro × quadros), e `textures/armilar/{anel,grafite,cristal_sinal,cristal_lapidado,cristal_faixa,cristal_opaco,cristal_rachado}.png`.

- [ ] **Passo 1: o teste do contrato (falha primeiro).** `FxTexturesFilesTest` lê cada PNG pelo classpath (`getResourceAsStream("/assets/wirelessautomate/textures/fx/" + nome + ".png")`, `ImageIO.read`) e confere, para cada `FxSheet` de `FxSheets.all()`, `width()` e `height()` iguais aos de `sheet.width()` e `sheet.height()`; que há pelo menos um pixel com alfa 255 em cada quadro; e que as texturas sólidas existem com os tamanhos: `anel` 32 × 10, `grafite` 8 × 8, e as cinco `cristal_*` 8 × 16. Rode e veja falhar.

- [ ] **Passo 2: `fx_texturas.py`.** Porte `fx_atlas.py` para o gerador. Regras:
  - Importe `identidade.PALETA` e `cor`. As rampas do filme (`CORAL`, `LATAO`, `ENERGIA`, `GRAFITE`, `PORC`) já são as da `PALETA`, com **dois tons que a `PALETA` não tem**: o latão mais escuro (`#4F3B10`, `LATAO[4]`) e o coral mais escuro (`#4A1A12`, `CORAL[5]`). Defina-os como constantes locais do módulo (`LATAO_ESCURO`, `CORAL_ESCURO`) com um comentário citando o `fx_atlas.py`. Não mexa na `PALETA`.
  - Cada folha é uma imagem própria (`Image.new('RGBA', (largura, altura_do_quadro × quadros))`) e cada função do filme desenha num quadro dela. As funções do filme desenham numa tela de 512 × 512 em `(ox, oy)`; troque `put(A, ox + x, oy + y, c)` por `put(img, x, y + quadro × altura, c)` e **mantenha cada conta pixel a pixel** (o `Bayer`, `rnd`, as rampas de alfa). Cada função de origem (`brilho`, `estrela`, `brasa`, `risco`, `aro`, `choque`, `clarao`, `fumaca`, `chama`, `sol`, `tira` com `miolo`, `trilhos`, `trilhos_rim`, `fluxo`, `raio`, e `sigilo`) vem inteira de `fx_atlas.py` (linhas 54 a 356). A tabela do passo 5 da Tarefa 1 diz qual função gera cada folha.
  - **Alfa parcial:** o `validar` da `identidade.py` só aceita alfa 0 ou 255 (salvo vidro), e as folhas de efeito têm degradê de alfa de propósito (o `cinza(a)` do filme). Por isso o módulo grava com a sua própria função (`Image.save`), sem passar pelo `salvar` da `identidade.py`, e confere o que for do módulo (tamanho e pelo menos um pixel opaco por quadro).
  - **Onde grava:** `textures/fx/<nome>.png`, direto (sem `.mcmeta`: o quadro sai do relógio do efeito). A função `limpar` do `gerar_texturas.py` só varre `item`, `gui` e `block`, então a pasta `fx` fica de fora; o módulo devolve o conjunto de nomes que gravou e apaga da pasta `fx` o que sobrou de antes.
  - **Prévia:** `docs/preview/armilar-fx.png`: as 19 folhas lado a lado em 4× com o nome, no fundo escuro e no fundo claro.

- [ ] **Passo 3: `armilar_render.py`.** As texturas das peças sólidas, em `textures/armilar/`:
  - **`anel.png` (32 × 10) e `grafite.png` (8 × 8):** iguais às do filme (`fx_atlas.py`, linhas 402 a 423 para o `anel`, 456 a 459 para o `grafite`). O `anel` é o latão facetado: linhas 0 a 2 o topo, 3 e 4 a face de fora, 5 e 6 a de dentro, 7 a 9 a base, período de 16 texels, com os traços da escala, os rebites e a marca coral.
  - **`cristal_sinal.png` (8 × 16):** o `pec('cristal')` do filme (linhas 424 a 436). Layout: linhas 0 e 1 a ponta de cima, 2 a 7 o corpo, 12 a 14 a ponta de baixo; as colunas pares de 2 em 2 são as faces (`c0 = (k % 3) × 2`).
  - **`cristal_lapidado.png`, `cristal_faixa.png`, `cristal_opaco.png`, `cristal_rachado.png` (8 × 16):** **não existem no filme**; são arte nova, no mesmo layout do `cristal_sinal`:
    - `cristal_lapidado`: o Sinal com as facetas mais definidas e a **faixa dourada** (rampa `latao`) nas linhas 4 e 5 do corpo (a cinta do losango antigo);
    - `cristal_faixa`: só as linhas 4 e 5, em cinza claro (branco com alfa), o resto transparente; é a faixa que o Afinado tinge na cor do tier pelo vértice;
    - `cristal_opaco`: o mesmo desenho do Sinal em tons de osso e grafite (rampas `porcelana` e `grafite`), sem brilho;
    - `cristal_rachado`: o Opaco com a trinca (uma diagonal de grafite escuro cruzando duas faces, como o `CrystalParam.CRACKED` antigo sugeria).
  - O horizonte usa `block/armilar_latao` e os marcos `block/armilar_coral`, que o gerador da etapa 1 já grava (nenhuma textura nova para eles; ver a nota da Tarefa 4).
  - **Prévia:** `docs/preview/armilar-pecas.png`: as 7 texturas em 8× com o nome.

- [ ] **Passo 4: ligar no `gerar_texturas.py`.** Em `main`, depois de `armilar_modelo.gerar()`, chame `fx_texturas.gerar()` e `armilar_render.gerar()` (cada um grava e devolve o que gravou; nenhum entra em `gerados` do `limpar`, que não varre essas pastas). Atualize o docstring do módulo (a lista de quem gera o quê) e a entrada de `docs/mapa/` na Tarefa 11.

- [ ] **Passo 5: gerar e conferir.**
  - `python -I scripts/textures/gerar_texturas.py`. `git status`: só os arquivos novos de `textures/fx/`, `textures/armilar/` e as duas prévias; reverta o resto (`git checkout -- <arquivo>`).
  - `./gradlew test --tests '*FxTexturesFilesTest'` passa.
  - Abra `docs/preview/armilar-fx.png` e `docs/preview/armilar-pecas.png` com a ferramenta de leitura de imagem e confira: as folhas do filme batem com o `fx_x4.png` do filme (`ressonador-conceitos/filme/fx_x4.png`); o brilho, a estrela e o sol têm o degradê de alfa; a faixa do Lapidado é dourada.

- [ ] **Passo 6: parar e mostrar (memória "pensar antes de rodar").** As cinco texturas de cristal (menos o `cristal_sinal`) são arte nova que o filme não mostra. Mostre `docs/preview/armilar-pecas.png` ao dono e espere o ok antes da Tarefa 7 usá-las. As Tarefas 3 a 6 não dependem delas e podem andar.

- [ ] **Passo 7: commit.** `git add scripts/textures/fx_texturas.py scripts/textures/armilar_render.py scripts/textures/gerar_texturas.py src/main/resources/assets/wirelessautomate/textures/fx src/main/resources/assets/wirelessautomate/textures/armilar docs/preview/armilar-fx.png docs/preview/armilar-pecas.png src/test` e `git commit -m "Texturas: as folhas de efeito do filme e as texturas das peças da Armilar, pelo gerador"`.

---

### Tarefa 3: o desenho tardio e o estágio sem pacote

Entrega: um efeito de teste (um sprite e uma fita na frente do jogador) aparece com a oclusão certa sem pacote e com Complementary (o BSL entra na Tarefa 11), e o `e2e.sh` roda com `--shaders`. Nada de efeito na passada de sombra. É o que prova o caminho de desenho antes de a Armilar depender dele.

**Arquivos:**
- Criar: `B/client/fx/FxContext.java`, `FxJob.java`, `FxQueue.java`, `FxBatch.java`, `FxDepth.java`, `FxStages.java`, `FxDraw.java`, `FxRibbon.java`
- Criar: `B/client/dev/E2eFxProbeSteps.java`; mudar `B/client/dev/E2eSuites.java` (a suíte opcional `fx-base`)
- Mudar: `scripts/e2e.sh` (a opção `--shaders`, passo 8)
- Testes (novos): `T/client/fx/FxRibbonTest.java`

**Interfaces:**
- **Consome:** `FxSheet`, `FxSheets`, `FxTypes`, `ShaderPackCheck`, `FxShaders` (Tarefa 1).
- **Produz:**
  - `FxContext` (um só, reaproveitado a cada quadro): `double camX, camY, camZ`; `float rx, ry, rz` (a direita da câmera no mundo) e `ux, uy, uz` (o alto); `Matrix4f modelView, projection` (cópias); `float partialTick`; `long gameTime`. `void begin(Camera camera, Matrix4f modelView, Matrix4f projection, float partialTick, long gameTime)`.
  - `FxJob`: `void draw(FxContext ctx, MultiBufferSource buffers)`; `double distanceSq(FxContext ctx)` (a distância ao quadrado até o centro do efeito, para o corte de 48 blocos).
  - `FxQueue.submit(FxJob job)`; `FxQueue.isEmpty()`; `FxQueue.clear()`.
  - `FxBatch`: `MultiBufferSource source()`; `void flush()`; `boolean hasData()`.
  - `FxDraw.sprite(VertexConsumer out, FxContext c, FxSheet sheet, int frame, float x, float y, float z, float half, float rot, int rgb, float alpha, int dissolve, int light)`, `FxDraw.decal(VertexConsumer out, FxSheet sheet, int frame, float cx, float cy, float cz, float a1x, float a1y, float a1z, float a2x, float a2y, float a2z, float half, float rot, int rgb, float alpha, int dissolve, int light)`, `FxDraw.ribbon(VertexConsumer out, FxContext c, FxSheet sheet, int frame, FxRibbon.Buffer b, int n, int rgb, float repUnits, float offset, int dissolve, int light)`. Todas as coordenadas já relativas à câmera, em blocos.
  - `FxRibbon.Buffer` (`float[] pts, w, a`, capacidade `MAX = 256`) e `FxRibbon.catmullRom(float[] ctl, int count, float stepBlocks, Buffer out)` devolvendo o número de amostras; `FxRibbon.bezier(float[] p, int steps, Buffer out)` (`p` com os 4 pontos, 12 floats).
  - `FxStages` (os ganchos de evento).

- [ ] **Passo 1: descobrir o gancho do desenho tardio (sem abrir o jogo).** O pipeline pede que o buffer tardio seja desenhado depois da composição do pacote, "em `RenderGuiEvent.Pre`, antes do HUD, ou num mixin no fim do `GameRenderer.renderLevel`". O e2e liga `options.hideGui = true` nas capturas da câmara. **Confira no jar do NeoForge** (`neoforge-21.1.248`, `javap -p -c -cp <jar> net.minecraft.client.gui.Gui`, procurando a chamada ao `RenderGuiEvent$Pre`) se o evento é postado também com `hideGui` ligado.
  - Se **sim**: o gancho é `RenderGuiEvent.Pre`.
  - Se **não**: o gancho é um mixin de cliente em `GameRenderer.renderLevel`, no `TAIL`, que chama `FxStages.afterLevelComposed()`. Crie `B/mixin/client/GameRendererMixin.java` e `src/main/resources/wirelessautomate.client.mixins.json` (`"client": ["client.GameRendererMixin"]`, o mesmo `package`, `minVersion` e `compatibilityLevel` do `wirelessautomate.mixins.json`), e registre-o no `neoforge.mods.toml` com um segundo `[[mixins]]`. **(Conferir)** onde o `wirelessautomate.mixins.json` é registrado hoje (`grep -rn "mixins.json" src/ build.gradle`) e copie.
  Escreva a decisão e a evidência no comentário de `FxStages`.

- [ ] **Passo 2: o teste da fita (falha primeiro).** `T/client/fx/FxRibbonTest.java`:

```java
package io.github.matheusanbs.wirelessautomate.client.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FxRibbonTest {
    private static double dist(FxRibbon.Buffer b, int i, int j) {
        double dx = b.pts[i * 3] - b.pts[j * 3];
        double dy = b.pts[i * 3 + 1] - b.pts[j * 3 + 1];
        double dz = b.pts[i * 3 + 2] - b.pts[j * 3 + 2];
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    @Test
    void amostraACadaUmDezesseisAvos() {
        // controles em px: uma curva de uns 40 px; o passo é 1 px = 1/16 de bloco
        float[] ctl = {0, 0, 0, 10, 8, 3, 22, 12, -2, 34, 4, 5, 40, 0, 0};
        FxRibbon.Buffer b = new FxRibbon.Buffer();
        int n = FxRibbon.catmullRom(ctl, 5, 1f, b);
        assertTrue(n > 40, "amostras: " + n);
        for (int i = 1; i < n; i++) {
            assertTrue(dist(b, i, i - 1) <= 1.0 + 1e-4, "passo " + i + ": " + dist(b, i, i - 1));
        }
    }

    @Test
    void passaPelosControlesENasPontas() {
        float[] ctl = {0, 0, 0, 10, 8, 3, 22, 12, -2};
        FxRibbon.Buffer b = new FxRibbon.Buffer();
        int n = FxRibbon.catmullRom(ctl, 3, 1f, b);
        assertEquals(0f, b.pts[0], 1e-6);
        assertEquals(22f, b.pts[(n - 1) * 3], 1e-6);
        // o controle do meio é amostra da curva
        boolean achou = false;
        for (int i = 0; i < n; i++) {
            achou |= Math.abs(b.pts[i * 3] - 10) < 1e-4 && Math.abs(b.pts[i * 3 + 1] - 8) < 1e-4;
        }
        assertTrue(achou);
    }

    @Test
    void doisPontosViramReta() {
        float[] ctl = {0, 0, 0, 5, 0, 0};
        FxRibbon.Buffer b = new FxRibbon.Buffer();
        int n = FxRibbon.catmullRom(ctl, 2, 1f, b);
        assertEquals(2, n);
    }

    @Test
    void bezierDoArcoDoFilme() {
        // os 4 pontos do arco da conjunção (partes.js, desenhar), em px
        float[] p = {33.6f, 46.6f, 0, 46, 44, 0, 49, 17, 0, 42.8f, 8.6f, 0};
        FxRibbon.Buffer b = new FxRibbon.Buffer();
        int n = FxRibbon.bezier(p, 60, b);
        assertEquals(61, n);
        assertEquals(33.6f, b.pts[0], 1e-5);
        assertEquals(42.8f, b.pts[60 * 3], 1e-5);
        for (int i = 1; i < n; i++) {
            assertTrue(dist(b, i, i - 1) <= 1.0 + 1e-4, "passo " + i);
        }
    }
}
```

- [ ] **Passo 3: `FxRibbon`.** Porte o `spline` de `shared.js` (Catmull-Rom com as pontas estendidas `Q = [2·P0 − P1, ...P, 2·Pn − Pn−1]`) e o `bez` de `partes.js`, com uma diferença da spec: o número de amostras de cada trecho sai do comprimento (`n = max(1, ceil(comprimento / passo))`), para o pixel da textura caber num pixel do mundo. Sem `lambda`, sem alocação: tudo em `float[]` do `Buffer`.

```java
package io.github.matheusanbs.wirelessautomate.client.fx;

/** A amostragem das fitas (Catmull-Rom e bezier), pura. Os pontos e os passos na mesma unidade (px ou blocos). */
public final class FxRibbon {
    public static final int MAX = 256;

    /** Os pontos, a meia largura e o alfa de cada amostra; o chamador preenche {@code w} e {@code a} depois da amostragem. */
    public static final class Buffer {
        public final float[] pts = new float[MAX * 3];
        public final float[] w = new float[MAX];
        public final float[] a = new float[MAX];
    }

    private FxRibbon() {
    }

    /**
     * O {@code spline} do filme: curva de Catmull-Rom pelos {@code count} controles (x, y, z em {@code ctl}), com uma
     * amostra a cada {@code step} no máximo. Com menos de 3 controles devolve os próprios controles. Devolve o número de
     * amostras (as primeiras de {@code out.pts}), no máximo {@link #MAX}.
     */
    public static int catmullRom(float[] ctl, int count, float step, Buffer out) {
        if (count < 3) {
            for (int i = 0; i < count * 3; i++) {
                out.pts[i] = ctl[i];
            }
            return count;
        }
        int n = 0;
        for (int i = 0; i < count - 1; i++) {
            // a curva é mais longa que a corda: a margem de 35% mantém o passo entre amostras abaixo de {@code step}
            float len = dist(ctl, i, i + 1);
            int steps = Math.max(1, (int) Math.ceil(len * 1.35f / step));
            for (int k = 0; k < steps && n < MAX - 1; k++) {
                float t = k / (float) steps;
                for (int j = 0; j < 3; j++) {
                    float p0 = i == 0 ? 2 * ctl[j] - ctl[3 + j] : ctl[(i - 1) * 3 + j];
                    float p1 = ctl[i * 3 + j];
                    float p2 = ctl[(i + 1) * 3 + j];
                    float p3 = i + 2 >= count ? 2 * ctl[(count - 1) * 3 + j] - ctl[(count - 2) * 3 + j]
                            : ctl[(i + 2) * 3 + j];
                    float t2 = t * t;
                    float t3 = t2 * t;
                    out.pts[n * 3 + j] = 0.5f * (2 * p1 + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2
                            + (-p0 + 3 * p1 - 3 * p2 + p3) * t3);
                }
                n++;
            }
        }
        for (int j = 0; j < 3; j++) {
            out.pts[n * 3 + j] = ctl[(count - 1) * 3 + j];
        }
        return n + 1;
    }

    /** O {@code bez} do filme: bezier cúbica por 4 pontos ({@code p}, 12 floats), com {@code steps + 1} amostras. */
    public static int bezier(float[] p, int steps, Buffer out) {
        for (int i = 0; i <= steps; i++) {
            float f = i / (float) steps;
            float g = 1 - f;
            for (int j = 0; j < 3; j++) {
                out.pts[i * 3 + j] = g * g * g * p[j] + 3 * g * g * f * p[3 + j] + 3 * g * f * f * p[6 + j]
                        + f * f * f * p[9 + j];
            }
        }
        return steps + 1;
    }

    private static float dist(float[] c, int i, int j) {
        float dx = c[i * 3] - c[j * 3];
        float dy = c[i * 3 + 1] - c[j * 3 + 1];
        float dz = c[i * 3 + 2] - c[j * 3 + 2];
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
```

  Rode `./gradlew test --tests '*FxRibbonTest'`: passa. Se o teste do passo de 1 px falhar numa curva mais fechada, aumente a margem de 1,35 (a regra é o passo entre amostras abaixo de 1 px), nunca a tolerância do teste.

- [ ] **Passo 4: `FxDraw`.** Porte `spr`, `dec` e `fita` de `shared.js`. Cada vértice do `NEW_ENTITY`: `out.addVertex(x, y, z).setColor(r, g, b, a).setUv(u, v).setUv1(dissolve, 0).setUv2(light & 0xFFFF, light >> 16).setNormal(0, 1, 0)` (**conferir** os nomes em `VertexConsumer` da 1.21.1; o `setLight(int)` faz o mesmo que `setUv2`). A luz é `LightTexture.FULL_BRIGHT` nas folhas de luz e o `LevelRenderer.getLightColor` do ponto nas de tinta (o chamador passa).
  - **Sprite (`spr`):** o quad vai de `−e1 − e2` a `+e1 + e2`, com `e1 = right·cos(rot)·half + up·sin(rot)·half` e `e2 = −right·sin(rot)·half + up·cos(rot)·half`; os cantos na ordem `(−e1−e2, +e1−e2, +e1+e2, −e1+e2)` com uv `(u0,v1), (u1,v1), (u1,v0), (u0,v0)`, onde `u0 = 0`, `u1 = 1`, `v0 = sheet.v0(frame)` e `v1 = sheet.v1(frame)`. `right` e `up` vêm do `FxContext`. Não desenha com `alpha ≤ 0,002` nem `half ≤ 0`.
  - **Decalque (`dec`):** o mesmo, com `e1 = ax1·cos·half + ax2·sin·half`, `e2 = −ax1·sin·half + ax2·cos·half` e os eixos dados no lugar de `right`/`up`.
  - **Fita (`fita`):** para cada amostra `i`, `lado = normalize(cross(tangente, ponto − câmera)) × w[i]`, com a tangente `normalize(p[i+1] − p[i−1])` (nas pontas, a diferença para o único vizinho); `L = p + lado`, `R = p − lado`. O `u` acumula o comprimento: `U[i] = s_i / repUnits − offset`; o `v` vai de `v0` (lado `L`) a `v1` (lado `R`). Cada trecho é um quad `(L[i], L[i+1], R[i+1], R[i])` com alfa por vértice `a[i]`, `a[i+1]`, `a[i+1]`, `a[i]`; pula o trecho com os dois alfas abaixo de 0,002. `repUnits` e `offset` do filme são `rep` (em px por repetição) e `off`; passe-os já convertidos para blocos (`rep / 16`) e o `offset` como veio (é fração de repetição, não de distância). Proteja `normalize` de vetor zero (placas Intel: `armadilhas.md`).
  - Escreva as três sem criar objeto: vetores em `static final float[]` de rascunho (o desenho roda só na thread de render).

- [ ] **Passo 5: `FxContext`, `FxJob`, `FxQueue`, `FxBatch`, `FxDepth`.**
  - `FxQueue`: um `ArrayList<FxJob>` estático reaproveitado.
  - `FxBatch`: monta uma vez um `MultiBufferSource.BufferSource` com um `ByteBufferBuilder` por tipo de `FxTypes.all()`, em ordem de inserção (`LinkedHashMap`), e um construtor de reserva (**conferir** `MultiBufferSource.immediateWithBuffers(Map<RenderType, ByteBufferBuilder>, ByteBufferBuilder)` e que o `endBatch()` desenha na ordem do mapa; a tinta tem de sair antes da luz). Cada `ByteBufferBuilder` começa pequeno (`new ByteBufferBuilder(4096)`); eles crescem sozinhos. `flush()` chama `endBatch()`; `hasData()` diz se algum construtor recebeu vértice (guarde um contador que o `source()` incrementa a cada `getBuffer`).
  - `FxDepth`: um `TextureTarget` próprio (`new TextureTarget(w, h, true, Minecraft.ON_OSX)`), recriado se o alvo principal mudou de tamanho; `save()` faz o blit só da profundidade do alvo principal para ele (`GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.frameBufferId)`, `GL_DRAW_FRAMEBUFFER` no nosso, `GL30.glBlitFramebuffer(0, 0, w, h, 0, 0, w, h, GL11.GL_DEPTH_BUFFER_BIT, GL11.GL_NEAREST)`), e `restore()` faz o inverso. Antes de cada blit, `while (GL11.glGetError() != 0) {}` para limpar erro de GL antigo (o SPMovesets faz assim). **(Conferir)** os nomes `frameBufferId`, `width`, `height` e `TextureTarget` na 1.21.1.

- [ ] **Passo 6: `FxStages`.** Um `@EventBusSubscriber(modid = WirelessAutomate.MODID, value = Dist.CLIENT)`:
  - `onLevelStage(RenderLevelStageEvent)`:
    - No estágio `AFTER_TRANSLUCENT_BLOCKS` (**conferir** como o NeoForge 21.1 expõe os estágios: `event.getStage() == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS`): se `FxQueue.isEmpty()` ou `!FxShaders.ready()`, sai. Senão, `ctx.begin(event.getCamera(), event.getModelViewMatrix(), event.getProjectionMatrix(), partial, gameTime)` (**conferir** os getters; o parcial vem como `DeltaTracker` em 21.1), e para cada job com `distanceSq <= 48 × 48` chama `job.draw(ctx, batch.source())`. Sem pacote (`!ShaderPackCheck.active()`), `batch.flush()` aqui mesmo. Com pacote, o buffer fica guardado. Limpa a fila.
    - No estágio `AFTER_LEVEL`, **só com pacote** e `batch.hasData()`: `FxDepth.save()` e copia as matrizes do evento para o contexto.
  - O gancho tardio (o do passo 1): se `batch.hasData()` e há pacote em uso, então `FxDepth.restore()`, liga o alvo principal (`Minecraft.getInstance().getMainRenderTarget().bindWrite(false)`), `RenderSystem.setProjectionMatrix(ctx.projection, VertexSorting.DISTANCE_TO_ORIGIN)`, põe o `modelView` guardado na pilha do `RenderSystem` (**conferir** `RenderSystem.getModelViewStack()` e `applyModelViewMatrix()`), liga o teste de profundidade e chama `batch.flush()`; depois devolve as matrizes e o estado de GL. A Tarefa 10 acrescenta a passada de luz logo depois do `flush()`.
  - **Passada de sombra:** nenhum `FxJob` entra na fila se `ShaderPackCheck.shadowPass()`. Quem anota (o BER) é quem confere. O `FxProbe` também.
  - Nada disso roda sem trabalho: a primeira linha de cada gancho é a checagem de fila vazia.

- [ ] **Passo 7: a sonda do e2e.** `E2eFxProbeSteps` (suíte opcional `fx-base`, `Prereq.NENHUM`, em `E2eSuites`): coloca, no mundo plano, três coisas e tira uma captura de cada:
  1. um bloco de pedra de 3 × 3 × 1 e, atrás dele, a 4 blocos, um `FxJob` de teste (uma classe interna que implementa `FxJob`) que desenha um `BRILHO_G` de meio lado 1,2 bloco e uma fita `TRILHOS` + `FLUXO` de 6 blocos que passa **atrás** do bloco: a captura mostra o brilho cortado pelo bloco (oclusão);
  2. o mesmo job a 2 blocos do jogador, sem nada na frente: a captura mostra o brilho inteiro (cor e forma);
  3. o jogador a 60 blocos do job: nada se desenha (o corte de 48).
  O job entra na fila a cada tick de render por um `RenderFrameEvent.Pre` da própria sonda (ligado só enquanto a suíte roda); ele não entra na fila se `ShaderPackCheck.shadowPass()`. Use o `E2eResonatorSteps.camera(...)` como molde para a câmera e `capture("fx-base-oclusao")`, `capture("fx-base-perto")`, `capture("fx-base-longe")`.

- [ ] **Passo 8: `--shaders` no `e2e.sh`.** Acrescente a opção `--shaders nenhum|complementary|bsl` (cabeçalho de comentário incluído): `nenhum` roda como hoje; `complementary` e `bsl` escrevem `run/config/iris.properties` antes (`enableShaders=true` e `shaderPack=ComplementaryReimagined_r5.9.3.zip` ou `BSL_v10.1.8.zip`) e trocam o `./gradlew runClient` da linha do `timeout` por `./gradlew runClient -Pshaders`. **Confira os nomes das chaves** abrindo o jogo uma vez com `-Pshaders` e lendo o `iris.properties` que o Iris grava; nunca mexa em `run/options.txt`. A pasta de saída por ambiente é a do `--out`.

- [ ] **Passo 9: compilar e passar o JUnit.** `./gradlew build` verde, mais as três varreduras.

- [ ] **Passo 10: Rodada de jogo (a primeira; avise o dono).** A Tarefa 2 já gerou `textures/fx/brilho_g.png`, `trilhos.png` e `fluxo.png`. O que vai ser visto: `fx-base-oclusao` (o brilho cortado pelo bloco), `fx-base-perto` (o brilho e a fita inteiros, com o degradê de alfa sem halo escuro) e `fx-base-longe` (nada).
  1. **Sem pacote:** `./scripts/e2e.sh --only fx-base --capture fx-base --out run/e2e-fx`. Leia `run/e2e-fx/client.log`: `grep -c "Couldn't compile"` tem de dar 0 (se der mais, o shader `fx` não compila; corrija pelo `armadilhas.md`), e nada de exceção do `FxStages`.
  2. **Com Complementary:** `./scripts/e2e.sh --only fx-base --capture fx-base --shaders complementary --out run/e2e-fx-comp`. Se a oclusão sair errada (o brilho aparece através do bloco), o ponto de salvar a profundidade está cedo ou tarde demais: mova o estágio, nunca desligue a oclusão (`pipeline.md`, seção 5).
  3. Mostre as imagens ao dono, escreva no relatório o que cada ambiente mostrou e **pare**.

- [ ] **Passo 11: commit.** `git commit -m "Efeitos: desenho de sprites e fitas, buffer tardio com profundidade salva, a sonda e o e2e com shaderpack"`.

---

---

### Tarefa 4: a geometria sólida, em lógica pura

Entrega: as malhas facetadas do conceito (anéis, engastes, pinos, cristal, horizonte e marcos) como listas de quads em px, mais a regra que leva o local ao mundo, tudo conferido com números do filme e com testes de "encosta e não atravessa". Nada é desenhado ainda.

**Arquivos:**
- Criar: `B/client/resonator/render/ArmilarSpec.java`, `QuadMesh.java`, `ArmilarMeshes.java`, `ArmilarFrame.java`
- Testes (novos), em `T/client/resonator/render/`: `ArmilarSpecTest.java`, `ArmilarMeshesTest.java`, `ArmilarOverlapTest.java`, `ArmilarFrameTest.java` e o auxiliar `Convex.java` (só de teste)

**Interfaces:**
- **Produz:**
  - `ArmilarSpec`: as constantes do conceito (tabela abaixo) e `static double tau(double t)`.
  - `QuadMesh(int texW, int texH)`: `int quads()`, `int texW()`, `int texH()`, `float x(int q, int v)`, `y`, `z` (posição do vértice `v` de 0 a 3 do quad `q`, em px), `float u(int q, int v)`, `v(int q, int v)` (0 a 1 da textura), `float nx(int q)`, `ny`, `nz` (normal para fora), `float tone(int q)` (0 a 1), `void add(double[][] p, double u0, double v0, double u1, double v1, double tone)` (o `quad` do filme), `void addToward(double[][] p, double u0, double v0, double u1, double v1, double[] want, double tone)` (o `quadUV`). Os `u` e `v` de entrada são **texels** da textura.
  - `ArmilarMeshes`: `static QuadMesh ring(int orbit)` (o anel facetado e os engastes dos nós da órbita, textura `anel` 32 × 10), `pins(int orbit)` (textura `grafite` 8 × 8), `crystal()` (textura 8 × 16), `horizon()` (textura 16 × 16, o latão) e `markers()` (16 × 16, o coral).
  - `ArmilarFrame`: `void set(ResonatorLayout.Face front, int[] origin)`, `float yaw()` (radianos, para `Axis.YP.rotation`), `double centerX()`, `centerY()`, `centerZ()` (o centro do chão no mundo), `void toWorld(double lx, double ly, double lz, double[] out)` e `void dirToWorld(double lx, double ly, double lz, double[] out)` (px locais para blocos no mundo; a segunda sem o deslocamento do centro).
- **Consome:** `ResonatorLayout.Face` e `ResonatorLayout.toWorld(Face front, Face local)` (já existem, públicos).

**As constantes (`ArmilarSpec`), do `partes.js` e do `gerar_conceitos.armilar()`:**

| Nome | Valor | Origem |
| --- | --- | --- |
| `RAIOS` | `{13, 19.5, 26}` | `raios` |
| `NOS` | `{2, 3, 3}` | `nos` |
| `SEGMENTOS` | `{24, 32, 40}` | `malhas` |
| `SOL_Y` | 47,5 | `sol` |
| `S` | 1,2 (rad/s, a volta dos anéis travados) | `S` |
| `TC` | 11,0 (s, a conjunção de referência) | `tc` |
| `PERIODO` | `2π / S` | `tau` |
| `RING_HALF_Y` | 1,0; `RING_HALF_W` 1,5 | `anelFacetado` (`ri = R − 1,5`, `ro = R + 1,5`, y ±1) |
| `BASE`, `TOPO_BASE`, `GARRA_DE`, `GARRA_ATE`, `TOPO_GARRA`, `CENTRO` | 1,4; 1,6; 0,9; 1,4; 3,0; 5,0 | `engaste` |
| `CRISTAL_R`, `CRISTAL_H`, `CRISTAL_PONTA` | 1,5; 1,5; 1,9 | `cristal(1.5, 1.5, 1.9)` |
| `PINO_DE`, `PINO_ATE`, `PINO_MEIO` | 1,5; 3,5; 0,75 | `pinos` (caixa `[r + 1,5, ±0,75, ±0,75]` a `[r + 3,5, ...]` e o espelho em −x) |
| `HORIZ_R_IN`, `HORIZ_R_OUT`, `HORIZ_Y0`, `HORIZ_Y1`, `HORIZ_SEGS` | 30; 35; 46; 49; 16 | `anel16((0,0,0), "y", 30, 35, 46, 49, ...)` |
| `MARCO_X0`, `MARCO_X1`, `MARCO_Y0`, `MARCO_Y1`, `MARCO_Z` | 31,5; 33,5; 49; 50; 1 | `marco_NN` (a cada 90°: k = 0, 4, 8, 12) |
| `OPACO_TILT_DEG`, `OPACO_LIFT_PX` | 8; 0 | decisão do controlador: o maior ângulo medido em que o cristal encosta na base sem atravessar as garras |

**Opaco inclinado (decisão do controlador):** a 20°, com o pivô na ponta de baixo, as garras entram no cristal; subir o cristal o soltaria da base. Fica o maior ângulo que encosta sem atravessar, medido em uns 8°, sem subida (memória "sem sobreposição"). O teste `opacoInclinadoNaoAtravessaAsGarras` prova o ângulo da constante, e o `a20GrausAtravessa` prova por que não é 20°.

- [ ] **Passo 1: o teste das constantes (falha primeiro).** `ArmilarSpecTest`:

```java
package io.github.matheusanbs.wirelessautomate.client.resonator.render;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ArmilarSpecTest {
    @Test
    void tauComOsNumerosDoFilme() {
        assertEquals(-0.528024488, ArmilarSpec.tau(0), 1e-8);
        assertEquals(-0.764012244, ArmilarSpec.tau(5), 1e-8);
        assertEquals(0.0, ArmilarSpec.tau(11), 1e-9);
        assertEquals(1.5, ArmilarSpec.tau(12.5), 1e-9);
        assertEquals(2.6, ArmilarSpec.tau(13.6), 1e-9);
        assertEquals(-0.0117918517, ArmilarSpec.tau(100), 1e-8);
        assertEquals(-0.773331452, ArmilarSpec.tau(5000.123), 1e-7);
    }

    @Test
    void periodoEhAVoltaDosAneisTravados() {
        assertEquals(2 * Math.PI / 1.2, ArmilarSpec.PERIODO, 1e-12);
    }
}
```

- [ ] **Passo 2: `ArmilarSpec`.** Constantes `public static final` pela tabela, e:

```java
    /** Tempo desde a última conjunção, de −P/2 a P/2 (o {@code tau} do filme). */
    public static double tau(double t) {
        double d = (t - TC) % PERIODO;
        return ((d + 1.5 * PERIODO) % PERIODO) - 0.5 * PERIODO;
    }
```

  Rode o `ArmilarSpecTest`: passa.

- [ ] **Passo 3: o teste da malha (falha primeiro).** `ArmilarMeshesTest`:

```java
package io.github.matheusanbs.wirelessautomate.client.resonator.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ArmilarMeshesTest {
    private static final float E = 1e-4f;

    @Test
    void contagensDoFilme() {
        // 4 quads por segmento do anel (anelFacetado) mais 30 por nó (a base e as 4 garras, 6 quads cada)
        assertEquals(96 + 2 * 30, ArmilarMeshes.ring(0).quads());
        assertEquals(128 + 3 * 30, ArmilarMeshes.ring(1).quads());
        assertEquals(160 + 3 * 30, ArmilarMeshes.ring(2).quads());
        for (int i = 0; i < 3; i++) {
            assertEquals(12, ArmilarMeshes.pins(i).quads());
        }
        assertEquals(18, ArmilarMeshes.crystal().quads());
        assertEquals(16 * 6, ArmilarMeshes.horizon().quads());
        assertEquals(4 * 6, ArmilarMeshes.markers().quads());
    }

    @Test
    void anelBateComOFilme() {
        QuadMesh m = ArmilarMeshes.ring(0);
        // quad 0 (topo), 1 (fora), 2 (dentro), 3 (base) do segmento 0: p0 e normal de shared.js
        assertVertex(m, 0, 11.5f, 1f, 0f, 0f, 1f, 0f);
        assertVertex(m, 1, 14.5f, 1f, 0f, 0.99144f, 0f, -0.13053f);
        assertVertex(m, 2, 11.10815f, 1f, -2.97642f, -0.99144f, 0f, 0.13053f);
        assertVertex(m, 3, 14.5f, -1f, 0f, 0f, -1f, 0f);
        // o segundo e o terceiro vértices do topo
        assertEquals(11.10815f, m.x(0, 1), E);
        assertEquals(-2.97642f, m.z(0, 1), E);
        assertEquals(14.00592f, m.x(0, 2), E);
        assertEquals(-3.75288f, m.z(0, 2), E);
        // o uv do topo: u de 0 a seg, v de 0 a 3 texels de 10; o da face de fora, v de 3 a 5
        assertEquals(0f, m.u(0, 0), E);
        assertEquals(0f, m.v(0, 0), E);
        assertEquals(0.3f, m.v(1, 0), E);
        assertEquals(0.5f, m.v(2, 0), E);
        assertEquals(0.7f, m.v(3, 0), E);
    }

    @Test
    void cristalBateComOFilme() {
        QuadMesh m = ArmilarMeshes.crystal();
        // lado
        assertEquals(0.75f, m.x(0, 0), E);
        assertEquals(1.5f, m.y(0, 0), E);
        assertEquals(1.29904f, m.z(0, 0), E);
        assertEquals(1.5f, m.x(0, 1), E);
        assertEquals(-1.5f, m.y(0, 2), E);
        assertEquals(0.86603f, m.nx(0), E);
        assertEquals(0.5f, m.nz(0), E);
        // ponta de cima e ponta de baixo
        assertEquals(3.4f, m.y(1, 1), E);
        assertEquals(0.71491f, m.nx(1), E);
        assertEquals(0.5644f, m.ny(1), E);
        assertEquals(0.41275f, m.nz(1), E);
        assertEquals(-3.4f, m.y(2, 1), E);
        assertEquals(-0.5644f, m.ny(2), E);
    }

    @Test
    void anelEhContinuoSemFresta() {
        for (int orbit = 0; orbit < 3; orbit++) {
            QuadMesh m = ArmilarMeshes.ring(orbit);
            int n = ArmilarSpec.SEGMENTOS[orbit];
            for (int k = 0; k < n; k++) {
                int next = (k + 1) % n;
                // o fim do topo do segmento k (vértice 1) é o começo do topo do segmento k + 1 (vértice 0)
                assertEquals(m.x(4 * k, 1), m.x(4 * next, 0), E, "x " + orbit + "/" + k);
                assertEquals(m.z(4 * k, 1), m.z(4 * next, 0), E, "z " + orbit + "/" + k);
            }
        }
    }

    @Test
    void normaisUnitariasEParaFora() {
        for (int orbit = 0; orbit < 3; orbit++) {
            QuadMesh m = ArmilarMeshes.ring(orbit);
            for (int q = 0; q < m.quads(); q++) {
                double len = Math.sqrt(m.nx(q) * m.nx(q) + m.ny(q) * m.ny(q) + m.nz(q) * m.nz(q));
                assertEquals(1.0, len, 1e-4, "quad " + q);
                assertTrue(m.tone(q) > 0 && m.tone(q) <= 1);
            }
            // a face de fora de cada segmento aponta para longe do eixo
            int n = ArmilarSpec.SEGMENTOS[orbit];
            for (int k = 0; k < n; k++) {
                int q = 4 * k + 1;
                double cx = (m.x(q, 0) + m.x(q, 2)) / 2;
                double cz = (m.z(q, 0) + m.z(q, 2)) / 2;
                assertTrue(m.nx(q) * cx + m.nz(q) * cz > 0, "fora " + orbit + "/" + k);
            }
        }
    }

    private static void assertVertex(QuadMesh m, int q, float x, float y, float z, float nx, float ny, float nz) {
        assertEquals(x, m.x(q, 0), E, "x");
        assertEquals(y, m.y(q, 0), E, "y");
        assertEquals(z, m.z(q, 0), E, "z");
        assertEquals(nx, m.nx(q), E, "nx");
        assertEquals(ny, m.ny(q), E, "ny");
        assertEquals(nz, m.nz(q), E, "nz");
    }
}
```

  Rode `./gradlew test --tests '*ArmilarMeshesTest' --tests '*ArmilarSpecTest'` e veja falhar (classes inexistentes).

- [ ] **Passo 4: `QuadMesh`.** Quatro arrays de `float` que crescem por dobra (`pos` com 12 por quad, `uv` com 8, `nrm` com 3, `tone` com 1), um contador e os acessores. Os métodos de entrada:

```java
    /** O {@code quad} do filme: a normal é −normalize(cross(p1 − p0, p3 − p0)); o uv é (u0,v0) (u1,v0) (u1,v1) (u0,v1) em texels. */
    void add(double[][] p, double u0, double v0, double u1, double v1, double tone) {
        double[] n = normal(p);
        put(p, u0, v0, u1, v1, new double[] {-n[0], -n[1], -n[2]}, tone);
    }

    /** O {@code quadUV} do filme: a normal é a do plano, virada para o lado de {@code want}. O uv é o mesmo mapeamento de {@link #add}. */
    void addToward(double[][] p, double u0, double v0, double u1, double v1, double[] want, double tone) {
        double[] n = normal(p);
        double side = n[0] * want[0] + n[1] * want[1] + n[2] * want[2];
        put(p, u0, v0, u1, v1, side > 0 ? n : new double[] {-n[0], -n[1], -n[2]}, tone);
    }
```

  Confira no filme (`quadUV`) que, quando o quad é invertido, o uv acompanha os vértices: o mapeamento de cada canto original ao uv **não muda** (verificado: `p0 → (u0,v0)`, `p1 → (u1,v0)`, `p2 → (u1,v1)`, `p3 → (u0,v1)` nos dois casos), então `addToward` só escolhe o sinal da normal. O `normal(p)` é `normalize(cross(p1 − p0, p3 − p0))`; com vetor zero devolve (0, 1, 0). `put` guarda o uv dividido por `texW` e `texH`. As alocações dos `double[]` são só da montagem, que roda uma vez por classe.

- [ ] **Passo 5: `ArmilarMeshes`.** Portes (cada um cita a função do filme no Javadoc):
  - **`box(QuadMesh m, x0, y0, z0, x1, y1, z1, yaw)`** = `W.caixa` de `shared.js`: as seis faces na ordem `+z, −z, +x, −x, +y, −y`, com os cantos exatamente como no filme (`[x0,y1,z1],[x1,y1,z1],[x1,y0,z1],[x0,y0,z1]` para `+z`; `[x1,y1,z0],[x0,y1,z0],[x0,y0,z0],[x1,y0,z0]` para `−z`; `[x1,y1,z1],[x1,y1,z0],[x1,y0,z0],[x1,y0,z1]` para `+x`; `[x0,y1,z0],[x0,y1,z1],[x0,y0,z1],[x0,y0,z0]` para `−x`; `[x0,y1,z0],[x1,y1,z0],[x1,y1,z1],[x0,y1,z1]` para `+y`; `[x0,y0,z1],[x1,y0,z1],[x1,y0,z0],[x0,y0,z0]` para `−y`), cada vértice girado em y por `yaw` (`x' = x cos + z sin`, `z' = −x sin + z cos`, o `M.ry` do filme), o uv de `0` a `min(dimensão, tamanho da textura)` em texels (`uv(w, h)` do filme: `+z`/`−z` usam `(dx, dy)`, `+x`/`−x` `(dz, dy)`, `+y`/`−y` `(dx, dz)`), tom 1, pelo `m.add`.
  - **`ring(i)`** = `anelFacetado(R, n)` mais os engastes do `malhas`: para cada segmento `k` de `n`: `a0 = k·2π/n`, `a1 = (k+1)·2π/n`, `am = (a0 + a1)/2`, `seg = 2πR/n`, `u0 = (k·seg) mod 16`, `u1 = u0 + seg`, `fora = (cos am, 0, −sin am)`, `P(rr, a, y) = (rr cos a, y, −rr sin a)`, `ri = R − 1,5`, `ro = R + 1,5`; quatro `addToward` com o uv em texels `(u0, 0)–(u1, 3)` para o topo `[P(ri,a0,1), P(ri,a1,1), P(ro,a1,1), P(ro,a0,1)]` (want `(0,1,0)`), `(u0,3)–(u1,5)` para a face de fora `[P(ro,a0,1), P(ro,a1,1), P(ro,a1,−1), P(ro,a0,−1)]` (want `fora`), `(u0,5)–(u1,7)` para a de dentro `[P(ri,a1,1), P(ri,a0,1), P(ri,a0,−1), P(ri,a1,−1)]` (want `−fora`) e `(u0,7)–(u1,10)` para a base `[P(ro,a0,−1), P(ro,a1,−1), P(ri,a1,−1), P(ri,a0,−1)]` (want `(0,−1,0)`); tom 1. Depois, para cada nó `k` de `NOS[i]` com `yaw = k·2π/NOS[i]`: `box(m, r − BASE, 1, −BASE, r + BASE, TOPO_BASE, BASE, yaw)` e, para `sx` em `{−1, 1}` e `sz` em `{−1, 1}`, a garra `box(m, min(x0,x1), TOPO_BASE, min(z0,z1), max(x0,x1), TOPO_GARRA, max(z0,z1), yaw)` com `x0 = r + sx·GARRA_DE`, `x1 = r + sx·GARRA_ATE`, `z0 = sz·GARRA_DE`, `z1 = sz·GARRA_ATE`.
  - **`pins(i)`** = o `pinos` do filme: `box(m, r + 1,5; −0,75; −0,75; r + 3,5; 0,75; 0,75; 0)` e `box(m, −r − 3,5; ...; −r − 1,5; ...; 0)`, textura 8 × 8.
  - **`crystal()`** = `W.cristal(1,5; 1,5; 1,9)`: para `k` de 0 a 5: `V(k, y) = (r cos(kπ/3), y, r sin(kπ/3))`, `c0 = (k mod 3)·2`, `fora = (cos((k+0,5)π/3), 0, sin((k+0,5)π/3))`, `top = (0, h + ponta, 0)`, `bot = (0, −h − ponta, 0)`; o lado `addToward([V(k+1,h), V(k,h), V(k,−h), V(k+1,−h)], c0, 2, c0 + 2, 2 + min(12, 4h), fora, tom[k mod 4])`, a ponta de cima `addToward([V(k+1,h), top, top, V(k,h)], c0, 0, c0 + 2, 2, fora + (0,1,0), tom[(k+1) mod 4])` e a de baixo `addToward([V(k,−h), bot, bot, V(k+1,−h)], c0, 12, c0 + 2, 15, fora + (0,−1,0), tom[(k+2) mod 4])`, com os tons do filme `{1,08; 1; 0,9; 0,8}` **divididos por 1,08** (o filme passa de 1 no shader; no vértice o máximo é 1). Textura 8 × 16.
  - **`horizon()`** = `anel16` do `gerar_conceitos.py`: `L = HORIZ_R_IN · tan(π/16)`; para `k` de 0 a 15, `box(m, 30, 46, −L, 35, 49, L, k · 22,5° em rad)`. O sentido do giro não importa (o anel é simétrico). Textura 16 × 16 (o latão).
  - **`markers()`**: para `k` de 0 a 3, `box(m, 31,5; 49; −1; 33,5; 50; 1, k · 90° em rad)`. Textura 16 × 16 (o coral). **Nota:** a spec (seção "Texturas") diz "o grafite dos pinos e dos marcos", mas o conceito (`gerar_conceitos.armilar()`, material `"coral"` nos `marco_NN`) pinta os marcos de coral, e o `armilar_formado.py` diz que eles ficam "iguais ao conceito". O plano segue o conceito (coral) e deixa a escolha numa constante só (`ArmilarSolids.MARKER_TEXTURE`, Tarefa 7). A decisão é do dono.
  - Cada malha é montada uma vez (campos `static final` iniciados no carregamento da classe).

- [ ] **Passo 6: rodar os testes da malha.** `./gradlew test --tests '*ArmilarMeshesTest'` passa. Se um número do ouro não bater por uma ordem de vértices trocada, **leia de novo o `shared.js`** (`anelFacetado`, `cristal`, `caixa`, `quadUV`): a ordem e os cantos são os do filme, não os de uma caixa qualquer.

- [ ] **Passo 7: o auxiliar `Convex` (teste).** `T/client/resonator/render/Convex.java`: um sólido convexo por planos.

```java
package io.github.matheusanbs.wirelessautomate.client.resonator.render;

import java.util.ArrayList;
import java.util.List;

/** Só de teste: um sólido convexo como um conjunto de planos com a normal para fora. */
final class Convex {
    private final List<double[]> planes = new ArrayList<>();

    /** O plano que passa por (px, py, pz) com a normal (nx, ny, nz); a normal é normalizada aqui. */
    void add(double nx, double ny, double nz, double px, double py, double pz) {
        double len = Math.sqrt(nx * nx + ny * ny + nz * nz);
        nx /= len;
        ny /= len;
        nz /= len;
        planes.add(new double[] {nx, ny, nz, nx * px + ny * py + nz * pz});
    }

    /** O ponto está estritamente dentro (a distância ao plano mais próximo é menor que −eps em todos). */
    boolean strictlyInside(double x, double y, double z, double eps) {
        for (double[] p : planes) {
            if (p[0] * x + p[1] * y + p[2] * z - p[3] >= -eps) {
                return false;
            }
        }
        return true;
    }

    /**
     * Os quads {@code first} a {@code first + count − 1} da malha como planos, depois de {@code p' = r·p + t}
     * (o {@code r} é uma rotação 3 × 3). Serve para um sólido convexo (o cristal; uma caixa).
     */
    static Convex of(QuadMesh m, int first, int count, double[][] r, double[] t) {
        Convex c = new Convex();
        for (int q = first; q < first + count; q++) {
            double[] p = apply(r, t, m.x(q, 0), m.y(q, 0), m.z(q, 0), 1);
            double[] n = apply(r, new double[3], m.nx(q), m.ny(q), m.nz(q), 0);
            c.add(n[0], n[1], n[2], p[0], p[1], p[2]);
        }
        return c;
    }

    static final double[][] IDENTITY = {{1, 0, 0}, {0, 1, 0}, {0, 0, 1}};

    private static double[] apply(double[][] r, double[] t, double x, double y, double z, double w) {
        return new double[] {r[0][0] * x + r[0][1] * y + r[0][2] * z + t[0] * w,
                r[1][0] * x + r[1][1] * y + r[1][2] * z + t[1] * w,
                r[2][0] * x + r[2][1] * y + r[2][2] * z + t[2] * w};
    }

    /** Quantos pontos de uma malha regular (passo 0,05) da caixa estão estritamente dentro deste sólido. */
    int countInside(double[] box, double eps) {
        int n = 0;
        for (double x = box[0]; x <= box[3] + 1e-9; x += 0.05) {
            for (double y = box[1]; y <= box[4] + 1e-9; y += 0.05) {
                for (double z = box[2]; z <= box[5] + 1e-9; z += 0.05) {
                    if (strictlyInside(x, y, z, eps)) {
                        n++;
                    }
                }
            }
        }
        return n;
    }
}
```

- [ ] **Passo 8: o teste de sobreposição.** `ArmilarOverlapTest` (a regra "elementos encostam e nunca se atravessam", memória "sem sobreposição", com número):

```java
package io.github.matheusanbs.wirelessautomate.client.resonator.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ArmilarOverlapTest {
    private static final double EPS = 1e-6;

    /** As garras do nó em x = r (yaw 0), como caixas {x0, y0, z0, x1, y1, z1}. */
    private static List<double[]> claws(double r) {
        List<double[]> out = new ArrayList<>();
        for (int sx : new int[] {-1, 1}) {
            for (int sz : new int[] {-1, 1}) {
                double x0 = r + sx * ArmilarSpec.GARRA_DE;
                double x1 = r + sx * ArmilarSpec.GARRA_ATE;
                double z0 = sz * ArmilarSpec.GARRA_DE;
                double z1 = sz * ArmilarSpec.GARRA_ATE;
                out.add(new double[] {Math.min(x0, x1), ArmilarSpec.TOPO_BASE, Math.min(z0, z1),
                        Math.max(x0, x1), ArmilarSpec.TOPO_GARRA, Math.max(z0, z1)});
            }
        }
        return out;
    }

    /** O cristal de pé no engaste (centro a 5 px), com a inclinação e a subida dadas (o pivô é a ponta de baixo). */
    private static Convex crystal(double r, double tiltDeg, double lift) {
        double a = Math.toRadians(tiltDeg);
        double[][] rot = {{Math.cos(a), -Math.sin(a), 0}, {Math.sin(a), Math.cos(a), 0}, {0, 0, 1}};
        double[] pivot = {r, ArmilarSpec.TOPO_BASE + lift, 0};
        double[] centre = {r, ArmilarSpec.CENTRO + lift, 0};
        double[] t = new double[3];
        for (int k = 0; k < 3; k++) {
            // p' = R (p + centre − pivot) + pivot = R p + t, com t = R (centre − pivot) + pivot
            t[k] = rot[k][0] * (centre[0] - pivot[0]) + rot[k][1] * (centre[1] - pivot[1])
                    + rot[k][2] * (centre[2] - pivot[2]) + pivot[k];
        }
        return Convex.of(ArmilarMeshes.crystal(), 0, 18, rot, t);
    }

    @Test
    void cristalDePeNaoAtravessaGarrasNemBase() {
        double r = ArmilarSpec.RAIOS[0];
        Convex c = crystal(r, 0, 0);
        for (double[] claw : claws(r)) {
            assertEquals(0, c.countInside(claw, EPS), "garra");
        }
        double[] base = {r - ArmilarSpec.BASE, ArmilarSpec.RING_HALF_Y, -ArmilarSpec.BASE, r + ArmilarSpec.BASE,
                ArmilarSpec.TOPO_BASE, ArmilarSpec.BASE};
        assertEquals(0, c.countInside(base, EPS), "base");
        // a ponta de baixo encosta no topo da base
        double lowest = ArmilarSpec.CENTRO - ArmilarSpec.CRISTAL_H - ArmilarSpec.CRISTAL_PONTA;
        assertEquals(ArmilarSpec.TOPO_BASE, lowest, 1e-9);
    }

    @Test
    void engasteEncostaNoAnelSemEntrar() {
        // a base sai do topo do anel (y = 1) para cima; as garras saem do topo da base
        assertEquals(ArmilarSpec.RING_HALF_Y, 1.0, 0);
        assertTrue(ArmilarSpec.TOPO_BASE > ArmilarSpec.RING_HALF_Y);
        assertEquals(ArmilarSpec.TOPO_BASE, 1.6, 0);
    }

    @Test
    void a20GrausAtravessa() {
        double r = ArmilarSpec.RAIOS[0];
        Convex c = crystal(r, 20, 0);
        int dentro = 0;
        for (double[] claw : claws(r)) {
            dentro += c.countInside(claw, EPS);
        }
        assertTrue(dentro > 0, "o problema medido: a 20° sem subir, as garras entram no cristal");
    }

    @Test
    void opacoInclinadoNaoAtravessaAsGarras() {
        double r = ArmilarSpec.RAIOS[0];
        for (double sign : new double[] {1, -1}) {
            Convex c = crystal(r, sign * ArmilarSpec.OPACO_TILT_DEG, ArmilarSpec.OPACO_LIFT_PX);
            for (double[] claw : claws(r)) {
                assertEquals(0, c.countInside(claw, EPS), "garra a " + sign * ArmilarSpec.OPACO_TILT_DEG);
            }
        }
    }

    /** O sólido de cada segmento do anel (convexo): topo, base, dentro, fora e as duas tampas pelo eixo. */
    private static List<Convex> ringSolids(int orbit) {
        double r = ArmilarSpec.RAIOS[orbit];
        int n = ArmilarSpec.SEGMENTOS[orbit];
        List<Convex> out = new ArrayList<>();
        for (int k = 0; k < n; k++) {
            double a0 = k * 2 * Math.PI / n;
            double a1 = (k + 1) * 2 * Math.PI / n;
            double am = (a0 + a1) / 2;
            double fx = Math.cos(am);
            double fz = -Math.sin(am);
            Convex c = new Convex();
            c.add(0, 1, 0, 0, ArmilarSpec.RING_HALF_Y, 0);
            c.add(0, -1, 0, 0, -ArmilarSpec.RING_HALF_Y, 0);
            double ro = r + ArmilarSpec.RING_HALF_W;
            double ri = r - ArmilarSpec.RING_HALF_W;
            c.add(fx, 0, fz, ro * fx, 0, ro * fz);
            c.add(-fx, 0, -fz, ri * fx, 0, ri * fz);
            // as tampas passam pelo eixo; fora do segmento é o sentido do ângulo decrescente em a0 e crescente em a1
            c.add(Math.sin(a0), 0, Math.cos(a0), 0, 0, 0);
            c.add(-Math.sin(a1), 0, -Math.cos(a1), 0, 0, 0);
            out.add(c);
        }
        return out;
    }

    @Test
    void pinosNaoEntramNoAnel() {
        for (int orbit = 0; orbit < 3; orbit++) {
            double r = ArmilarSpec.RAIOS[orbit];
            double m = ArmilarSpec.PINO_MEIO;
            double[][] pins = {{r + ArmilarSpec.PINO_DE, -m, -m, r + ArmilarSpec.PINO_ATE, m, m},
                    {-r - ArmilarSpec.PINO_ATE, -m, -m, -r - ArmilarSpec.PINO_DE, m, m}};
            for (Convex segment : ringSolids(orbit)) {
                for (double[] pin : pins) {
                    assertEquals(0, segment.countInside(pin, EPS), "pino da órbita " + orbit);
                }
            }
        }
    }

    @Test
    void segmentosDoHorizonteSoSeTocamNaArestaDeDentro() {
        QuadMesh m = ArmilarMeshes.horizon();
        double l = ArmilarSpec.HORIZ_R_IN * Math.tan(Math.PI / 16);
        int n = ArmilarSpec.HORIZ_SEGS;
        List<Convex> boxes = new ArrayList<>();
        for (int k = 0; k < n; k++) {
            boxes.add(Convex.of(m, 6 * k, 6, Convex.IDENTITY, new double[3]));
        }
        for (int k = 0; k < n; k++) {
            double a = k * 2 * Math.PI / n;
            double c = Math.cos(a);
            double s = Math.sin(a);
            Convex next = boxes.get((k + 1) % n);
            for (double x = ArmilarSpec.HORIZ_R_IN; x <= ArmilarSpec.HORIZ_R_OUT; x += 0.1) {
                for (double z = -l; z <= l; z += 0.1) {
                    // o ponto do segmento k, girado para o mundo (o mesmo giro em y da malha)
                    double wx = x * c + z * s;
                    double wz = -x * s + z * c;
                    assertTrue(!next.strictlyInside(wx, 47.5, wz, EPS), "segmento " + k);
                }
            }
        }
    }

    @Test
    void marcosAssentamNoTopoDoHorizonte() {
        assertEquals(ArmilarSpec.HORIZ_Y1, ArmilarSpec.MARCO_Y0, 0);
        assertTrue(ArmilarSpec.MARCO_X0 >= ArmilarSpec.HORIZ_R_IN);
        assertTrue(ArmilarSpec.MARCO_X1 <= ArmilarSpec.HORIZ_R_OUT);
    }
}
```

  Rode `./gradlew test --tests '*ArmilarOverlapTest'`. O teste `a20GrausAtravessa` tem de **passar** (prova o problema); se não passar, o cristal do plano não é o do filme: confira o passo 5. Se `opacoInclinadoNaoAtravessaAsGarras` falhar com 8°, baixe `OPACO_TILT_DEG` um grau por vez até passar (mantenha `OPACO_LIFT_PX = 0`) e registre o número no relatório.

- [ ] **Passo 9: `ArmilarFrame` e o teste.** A implementação:

```java
public final class ArmilarFrame {
    private double cx;
    private double cy;
    private double cz;
    private int exx;
    private int exz;
    private int ezx;
    private int ezz;
    private float yaw;

    /** A frente é a do Núcleo; {@code origin} é o canto mínimo da caixa (o {@code ChamberView.origin}). */
    public void set(ResonatorLayout.Face front, int[] origin) {
        cx = origin[0] + 2.5;
        cy = origin[1];
        cz = origin[2] + 2.5;
        ResonatorLayout.Face east = ResonatorLayout.toWorld(front, ResonatorLayout.Face.EAST);
        ResonatorLayout.Face south = ResonatorLayout.toWorld(front, ResonatorLayout.Face.SOUTH);
        exx = east.dx();
        exz = east.dz();
        ezx = south.dx();
        ezz = south.dz();
        yaw = (float) Math.atan2(-exz, exx);
    }

    public float yaw() {
        return yaw;
    }

    public double centerX() {
        return cx;
    }

    public double centerY() {
        return cy;
    }

    public double centerZ() {
        return cz;
    }

    /** O ponto local (px; x leste, z sul) no mundo, em blocos. */
    public void toWorld(double lx, double ly, double lz, double[] out) {
        dirToWorld(lx, ly, lz, out);
        out[0] += cx;
        out[1] += cy;
        out[2] += cz;
    }

    /** O vetor local (px) como vetor do mundo, em blocos. */
    public void dirToWorld(double lx, double ly, double lz, double[] out) {
        double bx = lx / 16;
        double bz = lz / 16;
        out[0] = bx * exx + bz * ezx;
        out[1] = ly / 16;
        out[2] = bx * exz + bz * ezz;
    }
}
```

  Teste `ArmilarFrameTest`: (a) para cada frente (`Face.SOUTH`, `WEST`, `NORTH`, `EAST`), o `yaw` cumpre `cos(yaw) = exx` e `−sin(yaw) = exz`, com `SOUTH` dando 0, `WEST` −π/2, `EAST` +π/2; (b) para cada frente e cada casa local `(lx, lz)` de 0 a 4, com o Núcleo em `(100, 64, 100)`, o centro da casa, `((lx − 2)·16, 0, (lz − 2)·16)` em px, vira o centro do bloco que `ResonatorLayout.world(front, 100, 64, 100, lx, 0, lz)` diz (`x + 0,5`, `z + 0,5`), com o `origin` tirado do mínimo de `x` e de `z` sobre as 25 casas; (c) o Núcleo local (casa `(2, 0, 4)`) cai no `(100,5; 64; 100,5)` para toda frente (a frente do Núcleo é a direção `front` a partir do centro: o ponto local `(0, 0, 40)` fica a 2,5 blocos do centro na direção `front`).

- [ ] **Passo 10: verificar.** `./gradlew build` verde, as três varreduras (`varrer.py`, `medir.py`, `varrer_perf.py`).

- [ ] **Passo 11: commit.** `git commit -m "Armilar: a geometria facetada do conceito, o referencial e os testes de sobreposição"`.

---

### Tarefa 5: o tempo, a pose e a fase, em lógica pura

Entrega: o relógio, a pose das órbitas, a linha do tempo da conjunção, o mapa dos nós, a montagem e a vazão, conferidos contra os números do filme (`Ouro`).

**Arquivos:**
- Criar: `B/client/resonator/render/Ease.java`, `ArmilarPose.java`, `ArmilarPhase.java`, `ArmilarMotion.java`, `ArmilarNodes.java`, `ArmilarAssembly.java`, `ArmilarFlow.java`
- Testes (novos), em `T/client/resonator/render/`: `EaseTest`, `ArmilarPoseTest`, `ArmilarPhaseTest`, `ArmilarMotionTest`, `ArmilarNodesTest`, `ArmilarAssemblyFlowTest`

**Interfaces:**
- **Consome:** `ArmilarSpec` (Tarefa 4).
- **Produz:**
  - `Ease`: `cl(x)`, `seg(t, a, b)`, `lerp(a, b, k)`, `eo(x)`, `eio(x)`, `eob(x, k)`, `ss(a, b, x)` (todos `double`, do `partes.js`), e `float wrap(double radians)` (o ângulo reduzido a (−π, π], para mandar ao `float` sem perder precisão).
  - `ArmilarPose`: `static double tilt(int orbit, double t, double u, double sinceTuned)`, `static double prec(int orbit, double t)`, `static double omega(int orbit, double u)` (a velocidade angular do giro, rad/s), `static double aligned(double t)` (o ângulo de giro travado na conjunção, `S·(t − TC)`).
  - `ArmilarPhase` (mutável, um por cena): campos públicos `double d, dis, prep, cresce, raio, clarao, onda, cabeca, arco, porta, surge, onda2, clarao2`; `void set(double t, double alive)`; `static double fullPrep(double t)`.
  - `ArmilarMotion` (mutável, um por cena): `void advance(double targetU, double clockSeconds)` (um tick), `double u(float partialTick)`, `double spin(int orbit, float partialTick)`, `boolean started()`.
  - `ArmilarNodes`: `orbit(int node)`, `slot(int node)`, `angle(int node)`, `popIndex(int node)`, `static void center(int node, double ringScale, double prec, double tilt, double spin, double[] out)` (o centro do cristal, em px locais, o `M.ap(m, [0,0,0])` do filme).
  - `ArmilarAssembly`: `a(double sinceFormed, boolean alreadyFormed)`, `ring(double a, int orbit)`, `crystal(double a, int popIndex)`, `ignition(double a)`; a constante `FORM_SECONDS = 2.5`.
  - `ArmilarFlow`: `strength(double band)`, `sunMul(double u)`.

- [ ] **Passo 1: `Ease` e o teste.** Porte as funções do `partes.js` (linhas 7 a 11):

```java
final class Ease {
    private Ease() {
    }

    static double cl(double x) {
        return Math.max(0, Math.min(1, x));
    }

    static double seg(double t, double a, double b) {
        return cl((t - a) / (b - a));
    }

    static double lerp(double a, double b, double k) {
        return a + (b - a) * k;
    }

    static double eo(double x) {
        return 1 - Math.pow(1 - cl(x), 3);
    }

    static double eio(double x) {
        x = cl(x);
        return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
    }

    /** O {@code eob} do filme (sobressalto): 0 em 0, 1 em 1, passa de 1 no caminho. */
    static double eob(double x, double k) {
        x = cl(x);
        return 1 + (k + 1) * Math.pow(x - 1, 3) + k * Math.pow(x - 1, 2);
    }

    static double ss(double a, double b, double x) {
        double k = cl((x - a) / (b - a));
        return k * k * (3 - 2 * k);
    }

    /** O ângulo reduzido a (−π, π]. */
    static float wrap(double radians) {
        double r = Math.IEEEremainder(radians, 2 * Math.PI);
        return (float) r;
    }
}
```

  `EaseTest`: `eob(0, k) = 0`, `eob(1, k) = 1`, `eob(0.5, 1.4) > 1`? (confira no filme: `eob(0,5; 1,4) = 1 + 2,4·(−0,125) + 1,4·0,25 = 1,05`; afirme 1,05), `eio(0,5) = 0,5`, `ss(0, 1, 0,5) = 0,5`, `seg(5, 0, 10) = 0,5`, `wrap(7) ≈ 7 − 2π`.

- [ ] **Passo 2: o teste da pose (falha primeiro).** `ArmilarPoseTest`, com o ouro:

```java
class ArmilarPoseTest {
    @Test
    void tiltEPrecComOFilme() {
        // u = 0: sem assentamento, só a inclinação solta
        assertEquals(0.62, ArmilarPose.tilt(0, 0, 0, 1e9), 1e-9);
        assertEquals(-0.718163232, ArmilarPose.tilt(1, 0, 0, 1e9), 1e-8);
        assertEquals(0.881887775, ArmilarPose.tilt(2, 0, 0, 1e9), 1e-8);
        assertEquals(0.707329715, ArmilarPose.tilt(0, 3.3, 0, 1e9), 1e-8);
        assertEquals(0.536989938, ArmilarPose.tilt(0, 5000.123, 0, 1e9), 1e-8);
        // u = 1, longe do assentamento: deitada
        assertEquals(0.0, ArmilarPose.tilt(0, 5000.123, 1, 1e9), 1e-12);
        // u = 1 no t = 11 do filme, 0,4 s depois do assentamento (t − 10,6)
        assertEquals(0.0277092284, ArmilarPose.tilt(0, 11, 1, 0.4), 1e-8);
        assertEquals(-0.00200063039, ArmilarPose.tilt(1, 11, 1, 0.4), 1e-8);
        assertEquals(-0.0298711189, ArmilarPose.tilt(2, 11, 1, 0.4), 1e-8);
        assertEquals(1500.0369, ArmilarPose.prec(0, 5000.123), 1e-3);
        assertEquals(-997.9246, ArmilarPose.prec(1, 5000.123), 1e-3);
        assertEquals(704.21722, ArmilarPose.prec(2, 5000.123), 1e-3);
        assertEquals(2.1, ArmilarPose.prec(1, 0), 1e-9);
    }

    @Test
    void giroSoltoEhAVelocidadeDeCadaOrbita() {
        // sem sintonia o giro é off × t: 0,8, −0,55 e 0,4 rad/s (pose(·, t, 0).spin do filme)
        assertEquals(0.8, ArmilarPose.omega(0, 0), 1e-12);
        assertEquals(-0.55, ArmilarPose.omega(1, 0), 1e-12);
        assertEquals(0.4, ArmilarPose.omega(2, 0), 1e-12);
        // travado, todas na volta dos anéis
        for (int i = 0; i < 3; i++) {
            assertEquals(ArmilarSpec.S, ArmilarPose.omega(i, 1), 1e-12);
        }
    }

    @Test
    void anguloTravadoNaConjuncaoEhZero() {
        assertEquals(0.0, ArmilarPose.aligned(ArmilarSpec.TC), 1e-12);
        assertEquals(1.2 * 1.5, ArmilarPose.aligned(ArmilarSpec.TC + 1.5), 1e-12);
    }
}
```

- [ ] **Passo 3: `ArmilarPose`.** Do `pose` do filme (`partes.js`, `pose(i, t, u)`):

```java
final class ArmilarPose {
    /** {inclinação, precessão, giro} de cada órbita, os {@code off} do filme. */
    private static final double[][] OFF = {{0.62, 0.3, 0.8}, {-0.8, -0.2, -0.55}, {0.95, 0.14, 0.4}};

    private ArmilarPose() {
    }

    /**
     * A inclinação da órbita: solta ({@code 1 − u}) com a oscilação lenta, mais o assentamento ao travar. {@code
     * sinceTuned} é o tempo desde que a lâmpada acendeu (no filme, {@code t − 10,6}); sem sintonia vista, um número
     * enorme.
     */
    static double tilt(int orbit, double t, double u, double sinceTuned) {
        double x = Math.max(0, sinceTuned);
        double sett = 0.06 * Math.exp(-x * 1.4) * Math.sin(x * 5.5 + orbit) * u;
        return (OFF[orbit][0] + 0.09 * Math.sin(t * 0.55 + orbit * 2)) * (1 - u) + sett;
    }

    static double prec(int orbit, double t) {
        return orbit * 2.1 + OFF[orbit][1] * t;
    }

    /** O quanto o giro anda por segundo: o próprio solto, e a volta dos anéis conforme a sintonia sobe. */
    static double omega(int orbit, double u) {
        return OFF[orbit][2] + u * (ArmilarSpec.S - OFF[orbit][2]);
    }

    /** O ângulo de giro em que as três órbitas estão alinhadas na conjunção; zero em {@code t = TC}. */
    static double aligned(double t) {
        return ArmilarSpec.S * (t - ArmilarSpec.TC);
    }
}
```

  **Por que o giro não é a fórmula fechada do filme.** No filme, `spin = off·t + u·((S − off)·(t − tL) + w)` com `tL` fixo (9,9 s): como `t − tL` fica pequeno, o `u` pode subir sem o anel disparar. No jogo `t` é o relógio do mundo e `u` pode ficar parado em 3/7 por minutos, então `u·(S − off)·(t − tL)` cresce sem limite, e a subida de `u` ao travar giraria o anel a centenas de rad/s. Por isso o `ArmilarMotion` (próximo passo) **integra** o giro por tick e, perto da sintonia, o trava com um laço de fase na conjunção. Com `u = 1` o resultado é o mesmo do filme (`S·(t − 11)` módulo 2π); só o caminho até lá muda. O teste `semDispararAoTravarDepoisDeMuitoTempoSolto` abaixo prova a diferença.

- [ ] **Passo 4: o teste do movimento (falha primeiro).** `ArmilarMotionTest`:

```java
class ArmilarMotionTest {
    private static final double DT = 0.05;

    private static double step(ArmilarMotion m, double target, double[] clock, double[] prev) {
        for (int i = 0; i < 3; i++) {
            prev[i] = m.spin(i, 1f);
        }
        clock[0] += DT;
        m.advance(target, clock[0]);
        double worst = 0;
        for (int i = 0; i < 3; i++) {
            worst = Math.max(worst, Math.abs(m.spin(i, 1f) - prev[i]));
        }
        return worst;
    }

    @Test
    void naoComecaDeslizando() {
        ArmilarMotion m = new ArmilarMotion();
        m.advance(1.0, 5000.0);
        assertEquals(1.0, m.u(1f), 1e-12);
        for (int i = 0; i < 3; i++) {
            // nasce alinhado: S·(t − TC) módulo 2π
            assertEquals(0, Math.IEEEremainder(m.spin(i, 1f) - ArmilarPose.aligned(5000.0), 2 * Math.PI), 1e-6);
        }
    }

    @Test
    void travaNaConjuncaoDepoisDeSubir() {
        ArmilarMotion m = new ArmilarMotion();
        double[] clock = {5000.0};
        double[] prev = new double[3];
        m.advance(0.0, clock[0]);
        for (int k = 0; k < 20 * 20; k++) {
            step(m, 1.0, clock, prev);
        }
        for (int i = 0; i < 3; i++) {
            assertEquals(0, Math.IEEEremainder(m.spin(i, 1f) - ArmilarPose.aligned(clock[0]), 2 * Math.PI), 1e-3,
                    "órbita " + i);
        }
    }

    @Test
    void semDispararAoTravarDepoisDeMuitoTempoSolto() {
        ArmilarMotion m = new ArmilarMotion();
        double[] clock = {5000.0};
        double[] prev = new double[3];
        m.advance(3.0 / 7, clock[0]);
        for (int k = 0; k < 20 * 600; k++) {
            step(m, 3.0 / 7, clock, prev);
        }
        double worst = 0;
        for (int k = 0; k < 20 * 20; k++) {
            worst = Math.max(worst, step(m, 1.0, clock, prev));
        }
        // a velocidade máxima é 1,2 rad/s (0,06 por tick) mais a puxada do laço de fase (no máximo 0,03)
        assertTrue(worst < 0.25, "maior salto por tick: " + worst);
    }

    @Test
    void soltoGiraNaVelocidadeDeCadaOrbita() {
        ArmilarMotion m = new ArmilarMotion();
        double[] clock = {100.0};
        double[] prev = new double[3];
        m.advance(0.0, clock[0]);
        double s0 = m.spin(0, 1f);
        for (int k = 0; k < 20; k++) {
            step(m, 0.0, clock, prev);
        }
        assertEquals(0.8, m.spin(0, 1f) - s0, 1e-9);
    }

    @Test
    void interpolaPeloParcial() {
        ArmilarMotion m = new ArmilarMotion();
        m.advance(0.0, 100.0);
        m.advance(0.0, 100.05);
        double a = m.spin(0, 0f);
        double b = m.spin(0, 1f);
        assertEquals((a + b) / 2, m.spin(0, 0.5f), 1e-12);
    }
}
```

- [ ] **Passo 5: `ArmilarMotion`.** O suavizado `u` e o giro de cada órbita por tick (o relógio do Minecraft é de 20 ticks por segundo):

```java
final class ArmilarMotion {
    /** O quanto o {@code u} anda por segundo em direção ao alvo (a spec: 1,6/s). */
    static final double RATE = 1.6;
    /** A força do laço que trava o giro na conjunção (por segundo), quando {@code u} passa de 0,5. */
    private static final double LOCK = 4.0;
    /** A puxada máxima do laço por tick, em radianos (0,6 rad/s a mais que o giro). */
    private static final double MAX_PULL = 0.03;
    private static final double TICK = 0.05;

    private final double[] spin = new double[3];
    private final double[] prevSpin = new double[3];
    private double u;
    private double prevU;
    private boolean started;

    /** Um tick: o alvo é {@code tuned ? 1 : band / 7}; {@code clockSeconds} é o relógio do mundo neste tick. */
    void advance(double targetU, double clockSeconds) {
        System.arraycopy(spin, 0, prevSpin, 0, 3);
        prevU = u;
        if (!started) {
            // nasce onde estaria: alinhado se já está sintonizado, no ritmo solto se não
            u = targetU;
            prevU = u;
            for (int i = 0; i < 3; i++) {
                spin[i] = u >= 1 ? ArmilarPose.aligned(clockSeconds) : ArmilarPose.omega(i, u) * clockSeconds;
            }
            System.arraycopy(spin, 0, prevSpin, 0, 3);
            started = true;
            return;
        }
        u += (targetU - u) * Math.min(1, RATE * TICK);
        double lock = Ease.cl((u - 0.5) * 2);
        double gain = Math.min(1, LOCK * lock * lock * TICK);
        for (int i = 0; i < 3; i++) {
            spin[i] += ArmilarPose.omega(i, u) * TICK;
            if (gain > 0) {
                double error = Math.IEEEremainder(ArmilarPose.aligned(clockSeconds) - spin[i], 2 * Math.PI);
                // a puxada por tick é limitada: o anel que ficou minutos solto não dá um chicote ao travar
                spin[i] += Math.clamp(error * gain, -MAX_PULL, MAX_PULL);
            }
        }
    }

    double u(float partialTick) {
        return prevU + (u - prevU) * partialTick;
    }

    double spin(int orbit, float partialTick) {
        return prevSpin[orbit] + (spin[orbit] - prevSpin[orbit]) * partialTick;
    }

    boolean started() {
        return started;
    }
}
```

  Os ângulos são `double` grandes (o relógio chega a milhões de segundos); só o consumidor reduz com `Ease.wrap` ao mandar para o `float`.

  Rode `./gradlew test --tests '*ArmilarMotionTest' --tests '*ArmilarPoseTest'`. Ajuste `LOCK` só se o teste de convergência (20 s para 1e-3) ou o do salto falhar; registre o valor final.

- [ ] **Passo 6: a fase da conjunção.** Teste `ArmilarPhaseTest` com a tabela do ouro (cada linha da tabela `fase(11 + d, u = 1)`, `d` de −0,7 a 2,5, com tolerância 1e-8; os campos `dis, prep, cresce, raio, clarao, onda, cabeca, arco, porta, surge, onda2, clarao2`), mais:
  - com `alive = 0,5` e `t = 11,3`: `raio = 0,5`, `clarao = 0,448`, `arco = 0,5`, `porta = 0`;
  - com `alive = 0`: todos os campos de "vivo" são 0 (`prep, raio, clarao, arco, porta, clarao2`), e `dis`, `cresce`, `onda`, `cabeca`, `surge`, `onda2` seguem a linha do tempo (são do tempo, não de `vivo`);
  - `fullPrep(t)` fica entre 0,5 e 0,7 e tem período de 2 s (0,5 Hz): `fullPrep(0) = 0,6` e `fullPrep(0,5) = 0,7`.

  A implementação (`partes.js`, `fase(t, u)`):

```java
final class ArmilarPhase {
    double d, dis, prep, cresce, raio, clarao, onda, cabeca, arco, porta, surge, onda2, clarao2;

    /**
     * A linha do tempo da conjunção em {@code t} (segundos do mundo). {@code alive} é 0 sem conjunção (fora de
     * sintonia, a spec manda não haver) e a sintonia suavizada com ela (o {@code u} do filme).
     */
    void set(double t, double alive) {
        d = ArmilarSpec.tau(t);
        dis = Ease.ss(1.6, 2.4, d);
        double vivo = alive * (d > -0.6 && d < 2.4 ? 1 : 0);
        prep = vivo * Ease.ss(-0.6, 0, d) * (1 - Ease.ss(0, 0.2, d));
        cresce = Ease.ss(-0.05, 0.2, d);
        raio = vivo * Ease.ss(-0.05, 0.05, d) * (1 - dis);
        clarao = vivo * Ease.ss(0.1, 0.18, d) * (1 - Ease.ss(0.25, 0.5, d));
        onda = Ease.ss(0.15, 0.7, d);
        cabeca = Ease.ss(0.1, 0.6, d);
        arco = vivo * Ease.ss(0.08, 0.15, d) * (1 - dis);
        porta = vivo * Ease.ss(0.55, 0.65, d) * (1 - dis);
        surge = Ease.ss(0.55, 0.85, d);
        onda2 = Ease.ss(0.6, 1.2, d);
        clarao2 = vivo * Ease.ss(0.55, 0.62, d) * (1 - Ease.ss(0.75, 1.0, d));
    }

    /**
     * O buffer cheio: o sol sob pressão, o encolher e escurecer da preparação parado em 60% e pulsando a 0,5 Hz. A spec
     * não diz a amplitude: 0,1 (de 50% a 70%) é a proposta, num lugar só.
     */
    static double fullPrep(double t) {
        return 0.6 + 0.1 * Math.sin(2 * Math.PI * 0.5 * t);
    }
}
```

  Confira o `fullPrep(0,5)`: `sin(π/2) = 1` dá 0,7.

- [ ] **Passo 7: nós, montagem e vazão.**
  - **`ArmilarNodes`:**

```java
final class ArmilarNodes {
    private static final int[] FIRST = {0, 2, 5};

    private ArmilarNodes() {
    }

    /** A órbita do nó: a câmara i ocupa o nó i, de dentro para fora: 0 e 1; 2 a 4; 5 a 7. */
    static int orbit(int node) {
        return node < 2 ? 0 : node < 5 ? 1 : 2;
    }

    static int slot(int node) {
        return node - FIRST[orbit(node)];
    }

    /** O ângulo do nó na órbita: {@code k · 2π / nos}. */
    static double angle(int node) {
        return slot(node) * 2 * Math.PI / ArmilarSpec.NOS[orbit(node)];
    }

    /** O {@code j = i · 3 + k} do filme, que atrasa a entrada de cada cristal na montagem. */
    static int popIndex(int node) {
        return orbit(node) * 3 + slot(node);
    }

    /**
     * O centro do cristal do nó, em px locais, o {@code M.ap(m, [0,0,0])} do filme: {@code (r, 5, 0)} girado por
     * {@code spin + ângulo} em y, escalado pela órbita, inclinado por {@code tilt} em x, girado por {@code prec} em y e
     * somado ao sol.
     */
    static void center(int node, double ringScale, double prec, double tilt, double spin, double[] out) {
        double r = ArmilarSpec.RAIOS[orbit(node)];
        double a = spin + angle(node);
        double x = ringScale * (r * Math.cos(a));
        double y = ringScale * ArmilarSpec.CENTRO;
        double z = ringScale * (-r * Math.sin(a));
        double y2 = y * Math.cos(tilt) - z * Math.sin(tilt);
        double z2 = y * Math.sin(tilt) + z * Math.cos(tilt);
        out[0] = x * Math.cos(prec) + z2 * Math.sin(prec);
        out[1] = y2 + ArmilarSpec.SOL_Y;
        out[2] = -x * Math.sin(prec) + z2 * Math.cos(prec);
    }
}
```

    `ArmilarNodesTest`: a tabela dos oito nós (`orbit`: 0,0,1,1,1,2,2,2; `slot`: 0,1,0,1,2,0,1,2; `popIndex`: 0,1,3,4,5,6,7,8; `angle(4) = 4π/3`; `angle(1) = π`); e os quatro centros do ouro:

    | nó (órbita, k) | ringScale | prec | tilt | spin | centro esperado (px) |
    | --- | --- | --- | --- | --- | --- |
    | (1, 2) = nó 4 | 1 | 1,44 | −0,856128782 | −1,815 | −14,374379; 40,547018; 12,259008 |
    | (2, 0) = nó 5 | 0,5 | 4,662 | 0,909385914 | 1,32 | 5,5925553; 58,973282; 3,512423 |
    | (0, 1) = nó 1 | 1 | 3,3 | 0,0277092284 | 6,28318531 | 12,815385; 52,498081; −2,187488 |
    | (2, 2) = nó 7 | 1 | 5,908 | −0,00626574024 | 7,72318531 | 13,406235; 52,599061; 22,254717 |

    com tolerância 1e-4. (O sinal de `y2` e `z2` do `rx` segue o `M.rx` do filme: `y' = y cos − z sin`, `z' = y sin + z cos`. Se algum centro não bater, confira o `M.rx` e o `M.ry` de `shared.js`.)
  - **`ArmilarAssembly`:** `FORM_SECONDS = 2.5` (a cena do filme, `scene-armilar.js`: `a = seg(t, 3,3; 5,8)`; decisão do controlador, fiel ao filme). `a(sinceFormed, alreadyFormed)` devolve 1 se `alreadyFormed` e `clamp(sinceFormed / FORM_SECONDS, 0, 1)` senão; `ring(a, i) = Ease.eob(Ease.seg(a, 0.15 + i·0.18, 0.6 + i·0.18), 1.4)`; `crystal(a, j) = Ease.eob(Ease.seg(a, 0.55 + j·0.05, 0.85 + j·0.05), 1.6)`; `ignition(a) = Ease.eob(Ease.seg(a, 0, 0.3), 1.5)`. Teste: `a = 1` dá tudo 1; `a = 0` dá tudo 0; `ring(0,3; 0)` é maior que 0 e menor que 1,1; `a(0, false) = 0`, `a(1,25; false) = 0,5`, `a(9, false) = 1`.
  - **`ArmilarFlow`:** `strength(band)` (`band` é a faixa de vazão suavizada, 0 a 7): `Ease.ss(0, 1, band) · (0,45 + 0,55 · band / 7)` (vazão 0: nada; de 1 a 7, a força `k` da spec; o `ss(0, 1, ·)` é a rampa que evita o pulo de 0 para 0,45 quando a faixa chega perto de 0). `sunMul(u) = 0,55 + 0,45 · u`. Testes: `strength(0) = 0`; `strength(1) = 0,45 + 0,55/7`; `strength(7) = 1`; crescente em 0 a 7; `sunMul(0) = 0,55`, `sunMul(1) = 1`.

- [ ] **Passo 8: verificar.** `./gradlew build` verde e as três varreduras.

- [ ] **Passo 9: commit.** `git commit -m "Armilar: a pose, a fase da conjunção, o laço de giro e os nós, com os números do filme"`.

---

### Tarefa 6: os estados dos cristais, em lógica pura

Entrega: o mapa dos códigos da câmara para textura, filamento e brilho; as fatias do cristal para a recarga em degraus de 1 texel; a lógica de recarga e de clarão que hoje vive no render antigo; os grãos de redstone.

**Arquivos:**
- Criar: `B/client/resonator/render/CrystalLook.java`, `CrystalPlan.java`, `ArmilarRecharge.java`, `FlashTracker.java`, `ArmilarGrains.java`
- Mudar: `B/client/resonator/render/QuadMesh.java` (o corte por plano) e `ArmilarMeshes.java` (`crystalRows()`)
- Testes (novos), em `T/client/resonator/render/`: `CrystalLookTest`, `CrystalPlanTest`, `QuadMeshClipTest`, `ArmilarRechargeTest`, `FlashTrackerTest`, `ArmilarGrainsTest`

**Interfaces:**
- **Consome:** `QuadMesh`, `ArmilarMeshes.crystal()` (Tarefa 4); `ChamberView`, `RechargeRank` (já existem, puros).
- **Produz:**
  - `CrystalLook`: `enum Tex { SINAL, LAPIDADO, FAIXA, OPACO, RACHADO }` com `ResourceLocation texture()` (`textures/armilar/cristal_*.png`); `static boolean empty(byte code)`, `dull(code)`, `cracked(code)`, `cut(code)`; `static Tex liveTex(byte code)` (a textura do cristal aceso: Lapidado para os pares, Sinal para os ímpares); `static Tex bodyTex(byte code)` (a do estado: aceso, Opaco ou Rachado); `static double filament(byte code)` (1 vivo, 0 Opaco, 0,35 rachado); `static boolean star(byte code)` (a estrela só no vivo).
  - `CrystalPlan`: `static final double[] CUTS` (as 12 alturas de corte em px locais do cristal), `static int rows()` (11), `static int litRows(double progress)`, `static boolean band(int row)`, `static CrystalLook.Tex rowTex(int row, byte code, int tier, int litRows)`, `static boolean rowTinted(int row, byte code, int tier, int litRows)`.
  - `QuadMesh.clipY(double y0, double y1)` devolve a parte da malha entre os dois planos horizontais, e `ArmilarMeshes.crystalRows()` o `QuadMesh[11]` do cristal por linha de texel.
  - `ArmilarRecharge`: `rank`, `rechargeable`, `isRecharging(int i, byte[] chambers, ChamberView view)`, `firstRecharging(byte[] chambers, ChamberView view)`, `progress(int i, byte[] chambers, double ticks, ChamberView view)`, `fall(double progress)` (o quanto o Opaco ainda está caído, de 1 a 0).
  - `FlashTracker`: `static final int FLASH_TICKS = 14`; `void onView(ChamberView view, byte[] chambers, long gameTime)`, `void tick(ChamberView view, byte[] chambers, long gameTime)`, `float flash(int chamber, long gameTime, float partialTick)` (de 0 a 1, ou −1 sem clarão).
  - `ArmilarGrains`: `MAX = 4`, `TICKS = 33`; `void tick(int targetChamber, int chamberCount)`; `boolean alive(int g)`, `int chamber(int g)`, `double progress(int g)`.

- [ ] **Passo 1: `CrystalLook` e o teste.** Os códigos são os do `ChamberView.chambers`: 0 vazia, 1 Sinal, 2 Lapidado, 3 Opaco do Sinal, 4 Opaco do Lapidado, 5 Rachado do Sinal, 6 Rachado do Lapidado. Teste (e implementação) por tabela:

  | código | vazio | opaco | rachado | lapidado | `liveTex` | `bodyTex` | `filament` | `star` |
  | --- | --- | --- | --- | --- | --- | --- | --- | --- |
  | 0 | sim | não | não | não | (nenhum) | (nenhum) | 0 | não |
  | 1 | não | não | não | não | SINAL | SINAL | 1 | sim |
  | 2 | não | não | não | sim | LAPIDADO | LAPIDADO | 1 | sim |
  | 3 | não | sim | não | não | SINAL | OPACO | 0 | não |
  | 4 | não | sim | não | sim | LAPIDADO | OPACO | 0 | não |
  | 5 | não | sim | sim | não | SINAL | RACHADO | 0,35 | não |
  | 6 | não | sim | sim | sim | LAPIDADO | RACHADO | 0,35 | não |

  (O Lapidado Opaco, código 4, usa a textura do Opaco: a spec só lista cinco texturas de cristal; ela não pede um Lapidado Opaco à parte.) `Tex.texture()` devolve `WirelessAutomate.id("textures/armilar/cristal_" + nome + ".png")` com `nome` em `sinal`, `lapidado`, `faixa`, `opaco`, `rachado`.

- [ ] **Passo 2: o corte por plano (`QuadMesh.clipY`) e o teste.** Teste `QuadMeshClipTest`: para o `ArmilarMeshes.crystal()`: (a) a área de `clipY(−∞, +∞)` é igual à da malha; (b) para um corte em `y = 0`, `clipY(−100, 0)` e `clipY(0, 100)` somam a área da malha inteira (conservação); (c) todo vértice de `clipY(a, b)` está em `[a − 1e-5, b + 1e-5]`; (d) a normal e o tom de cada quad cortado são os do quad de origem; (e) o uv de um vértice novo é a interpolação linear dos uv do quad de origem (o do ponto médio de uma aresta é a média). A área de um quad é a soma das áreas dos dois triângulos `(p0, p1, p2)` e `(p0, p2, p3)` (o triângulo degenerado, com dois vértices iguais, vale 0).

  Implementação: Sutherland-Hodgman em cada quad, uma vez para `y ≥ y0` e outra para `y ≤ y1`; o polígono resultante (até 6 vértices) vira 0, 1 ou 2 quads (um polígono de 3 vértices vira um quad com o último vértice repetido; de 5 ou 6, um quad mais o resto, repetindo vértice onde faltar). O uv e a posição interpolam linearmente. Roda só na montagem (uma vez por classe).

- [ ] **Passo 3: as fatias do cristal.** `CrystalPlan.CUTS` são as 12 alturas, em px locais do cristal (o centro do cristal é a origem), de baixo para cima:

  `−3,4; −2,76667; −2,13333; −1,5; −1,0; −0,5; 0,0; 0,5; 1,0; 1,5; 2,45; 3,4`

  Elas são as linhas de texel do `cristal_*.png`: a ponta de baixo (3 linhas, de v 12 a 15) vai de −3,4 a −1,5; o corpo (6 linhas, de v 8 a 2, de baixo para cima) de −1,5 a 1,5; a ponta de cima (2 linhas, de v 2 a 0) de 1,5 a 3,4. `rows()` é 11. A faixa (a cinta do Lapidado) são as linhas de índice 5 e 6, de −0,5 a 0,5.

  `litRows(progress) = min(11, floor(progress · 11 + 1e-9))` (de 0 a 11: quantas linhas já acenderam). `rowTex(row, code, tier, lit)`:
  - se `row < lit` (acesa) e o cristal é Lapidado com `tier > 0` e `band(row)`: `FAIXA` (tingida); 
  - se `row < lit`: `liveTex(code)`;
  - senão: `bodyTex(code)`.
  Para o cristal que não recarrega: `lit = 11` se é vivo (códigos 1 e 2) e `lit = 0` se é Opaco ou rachado. `rowTinted` é `true` só no primeiro caso. O `Afinado` é o Lapidado com `tier > 0`, vivo ou aceso pela recarga.

  Testes `CrystalPlanTest`: `litRows(0) = 0`, `litRows(1) = 11`, `litRows(0,5) = 5`, crescente; um Lapidado vivo com `tier = 0` usa `LAPIDADO` nas 11 linhas; com `tier = 2` usa `FAIXA` (tingida) nas linhas 5 e 6 e `LAPIDADO` nas outras 9; o Opaco (3) com `lit = 4` usa `SINAL` nas linhas 0 a 3 e `OPACO` nas 4 a 10; o rachado (5) com `lit = 0` usa `RACHADO` em todas; `CUTS` é crescente, tem 12 valores, e `CUTS[0] = −(CRISTAL_H + CRISTAL_PONTA)`, `CUTS[11] = +(CRISTAL_H + CRISTAL_PONTA)`.

  `ArmilarMeshes.crystalRows()`: `QuadMesh[11]`, a linha `r` é `crystal().clipY(CUTS[r], CUTS[r + 1])`, montada uma vez. Teste: a soma das áreas das 11 linhas é a da malha inteira, e nenhuma linha tem vértice fora do seu intervalo.

- [ ] **Passo 4: `ArmilarRecharge` e o teste.** Porte, **sem mudar a regra**, de `ChamberCrystals` (que sai na Tarefa 7): `rank`, `rechargeable`, `isRecharging`, `firstRecharging`, `recharge`:

```java
final class ArmilarRecharge {
    private static final byte DULL = 3;
    private static final byte CRACKED = 5;

    private ArmilarRecharge() {
    }

    /** Opaco que o Núcleo recarrega: sem carga e não rachado. */
    static boolean rechargeable(byte code) {
        return code >= DULL && code < CRACKED;
    }

    /** O posto da câmara {@code i} entre os Opacos recarregáveis (0 é o primeiro), ou −1 se ela não é um. */
    static int rank(int i, byte[] chambers) {
        if (!rechargeable(chambers[i])) {
            return -1;
        }
        int rank = 0;
        for (int j = 0; j < i; j++) {
            if (rechargeable(chambers[j])) {
                rank++;
            }
        }
        return rank;
    }

    /** Recarga em curso e o posto dentro do que o catalisador paga. */
    static boolean isRecharging(int i, byte[] chambers, ChamberView view) {
        return view.rechargeStart() >= 0 && RechargeRank.includes(rank(i, chambers), view.rechargers());
    }

    /** A primeira câmara que recarrega agora, ou −1. */
    static int firstRecharging(byte[] chambers, ChamberView view) {
        for (int i = 0; i < chambers.length; i++) {
            if (isRecharging(i, chambers, view)) {
                return i;
            }
        }
        return -1;
    }

    /** De 0 a 1, o quanto a recarga da câmara {@code i} já andou; 0 se ela não recarrega. */
    static double progress(int i, byte[] chambers, double ticks, ChamberView view) {
        if (!isRecharging(i, chambers, view)) {
            return 0;
        }
        return Math.clamp((ticks - view.rechargeStart()) / view.rechargeTicks(), 0.0, 1.0);
    }

    /** O quanto o Opaco ainda está caído (inclinado e solto do engaste): 1 sem recarga, 0 ao terminar. */
    static double fall(double progress) {
        double c = Math.clamp(progress, 0.0, 1.0);
        return 1 - c * c * (3 - 2 * c);
    }
}
```

  Teste `ArmilarRechargeTest`: monte `ChamberView` com o construtor canônico (`new ChamberView(chambers, new byte[0], 0, false, rechargeStart, rechargeTicks, rechargers, 0, new int[3], new byte[0], false)`) e confira: com `chambers = {1, 3, 4, 5, 3}` os `rank` são `{−1, 0, 1, −1, 2}`; com `rechargers = 2` e `rechargeStart = 100`, `isRecharging` é falso nas câmaras 0 e 3 (vivo e rachado) e no 4 (posto 2, fora do que o catalisador paga) e verdadeiro nas 1 e 2; sem `rechargeStart` (−1), nenhuma; `firstRecharging = 1`; `progress(1, ..., ticks = 130, rechargeTicks = 60) = 0,5`, e 0 fora da recarga; `fall(0) = 1`, `fall(1) = 0`, `fall(0,5) = 0,5`.

- [ ] **Passo 5: `FlashTracker` e o teste.** Porte de `ChamberScene.trackRecharge` e do laço do clarão no `update`. O clarão do fim da recarga dura 14 ticks (0,7 s):

```java
final class FlashTracker {
    static final int FLASH_TICKS = 14;
    private static final long NEVER = Long.MIN_VALUE / 2;

    private long[] flashAt = new long[0];
    private int rechargingPrev;
    private int flashFired;

    /** Chamado quando a view muda. O clarão sai quando a câmara deixa de ser Opaco que recarrega sem ter terminado pelo progresso. */
    void onView(ChamberView view, byte[] chambers, long gameTime) {
        if (flashAt.length != chambers.length) {
            flashAt = new long[chambers.length];
            java.util.Arrays.fill(flashAt, NEVER);
            rechargingPrev = 0;
            flashFired = 0;
        }
        int now = 0;
        for (int i = 0; i < chambers.length; i++) {
            if (ArmilarRecharge.isRecharging(i, chambers, view)) {
                now |= 1 << i;
            }
        }
        if (now == rechargingPrev) {
            return;
        }
        for (int i = 0; i < chambers.length; i++) {
            int bit = 1 << i;
            if ((rechargingPrev & bit) != 0 && (now & bit) == 0 && (flashFired & bit) == 0
                    && (chambers[i] == 1 || chambers[i] == 2)) {
                flashAt[i] = gameTime;
            }
        }
        flashFired &= now;
        rechargingPrev = now;
    }

    /** Uma vez por tick novo: dispara o clarão da câmara cuja recarga acabou pelo progresso e apaga os que passaram dos 14 ticks. */
    void tick(ChamberView view, byte[] chambers, long gameTime) {
        for (int i = 0; i < chambers.length; i++) {
            int bit = 1 << i;
            if ((rechargingPrev & bit) != 0 && (flashFired & bit) == 0
                    && ArmilarRecharge.progress(i, chambers, gameTime, view) >= 1) {
                flashAt[i] = gameTime;
                flashFired |= bit;
            }
        }
        for (int i = 0; i < flashAt.length; i++) {
            if (flashAt[i] != NEVER && gameTime - flashAt[i] >= FLASH_TICKS) {
                flashAt[i] = NEVER;
            }
        }
    }

    /** De 0 a 1 em 0,7 s; −1 sem clarão. */
    float flash(int chamber, long gameTime, float partialTick) {
        return chamber < flashAt.length && flashAt[chamber] != NEVER
                ? (gameTime - flashAt[chamber] + partialTick) / FLASH_TICKS : -1f;
    }
}
```

  Teste `FlashTrackerTest` com três cenas: (1) uma câmara Opaco (código 3) recarregando (`rechargeStart = 100`, `rechargeTicks = 60`, `rechargers = 1`), `onView` no tick 100, `tick` nos ticks 101 a 160: a partir do 160 `flash(i, 160, 0) = 0` e `flash(i, 167, 0) = 0,5`, e a partir do tick 174 volta a ser −1; (2) o servidor termina antes do cliente: no tick 140 chega uma view nova com a câmara já viva (código 1, `rechargeStart = −1`): `onView` dispara o clarão em 140 e o `tick` de 160 **não** dispara outro (`flashFired`); (3) câmara esvaziada no meio da recarga (código 0): nenhum clarão.

- [ ] **Passo 6: `ArmilarGrains` e o teste.** Porte do trecho dos grãos de redstone de `ChamberParticles` (linhas 85 a 233; o relógio e a vida), sem o desenho (o desenho é do `ArmilarScene`, Tarefa 10): até 4 grãos, 33 ticks de voo cada, um novo quando o relógio (`+= 2/20` por tick, só com alvo) passa de 1 e há vaga:

```java
final class ArmilarGrains {
    static final int MAX = 4;
    static final int TICKS = 33;
    private static final double SPAWN_PER_TICK = 2.0 / 20;

    private final boolean[] alive = new boolean[MAX];
    private final int[] chamber = new int[MAX];
    private final double[] t = new double[MAX];
    private double clock;

    /** Um tick: avança os grãos vivos, apaga os de câmara que sumiu e, com alvo, nasce um novo quando o relógio passa de 1. */
    void tick(int targetChamber, int chamberCount) {
        clock += targetChamber < 0 ? 0 : SPAWN_PER_TICK;
        if (clock > 1) {
            clock -= 1;
            int g = free();
            if (g >= 0 && targetChamber >= 0) {
                alive[g] = true;
                chamber[g] = targetChamber;
                t[g] = 0;
            }
        }
        for (int g = 0; g < MAX; g++) {
            if (alive[g] && chamber[g] >= chamberCount) {
                alive[g] = false;
            }
            if (alive[g]) {
                t[g] += 1.0 / TICKS;
                if (t[g] >= 1) {
                    alive[g] = false;
                }
            }
        }
    }

    private int free() {
        for (int g = 0; g < MAX; g++) {
            if (!alive[g]) {
                return g;
            }
        }
        return -1;
    }

    boolean alive(int g) {
        return alive[g];
    }

    int chamber(int g) {
        return chamber[g];
    }

    double progress(int g) {
        return t[g];
    }
}
```

  **A spec diz "um a cada 33 ticks"; o código de hoje faz outra coisa:** o relógio sobe 0,1 por tick (um grão novo a cada ~10 ticks), há no máximo 4 vivos e cada um voa por 33 ticks. A spec também manda a escolha de quem recarrega seguir a lógica de hoje, então o plano porta o código, não a frase (anotar no relatório). O alvo do grão é `ArmilarRecharge.firstRecharging`, a origem é a Porta de Cristais e o destino é o cristal do alvo; O voo anda por `Ease.ss(0, 1, progress)` (o `smoothstep` do código de hoje).

  Teste `ArmilarGrainsTest`: sem alvo (`−1`), nenhum nasce em 100 ticks; com alvo 2 e 3 câmaras, nunca mais de 4 vivos ao longo de 200 ticks; cada grão vive exatamente 33 ticks (conte os ticks entre o nascimento e a morte); um grão de câmara 2 morre no tick em que `chamberCount` cai para 2.

- [ ] **Passo 7: verificar.** `./gradlew build` verde e as três varreduras.

- [ ] **Passo 8: commit.** `git commit -m "Armilar: o mapa dos cristais, a recarga em degraus de texel, o clarão do fim e os grãos, em lógica pura"`.

---
### Tarefa 7: o BER novo com as peças sólidas, a saída do render antigo e a mudança dos arquivos do Reparador

Entrega: o Núcleo montado mostra as órbitas, os pinos, os engastes, os cristais nos seus estados, o horizonte e os marcos, pelo BER novo e pelo tipo de entidade do vanilla (com ou sem shaderpack). A câmara antiga não existe mais. Ainda sem sol e sem efeitos (Tarefas 8 e 9).

**Arquivos:**
- Criar: `B/client/resonator/render/ArmilarScene.java`, `ArmilarRenderer.java`, `ArmilarSolids.java`
- Mudar: `B/client/ClientSetup.java` (linha 150 em diante: `registerRenderers`), `B/client/fx/FxWarmup.java` (as texturas das peças), `B/client/dev/E2eResonatorSteps.java`, `E2eResonatorFlowSteps.java` (os ganchos que somem)
- Mover (`git mv`) de `B/client/resonator/render/` para `B/client/repairer/render/`: `PlasmaCore`, `PlasmaShader`, `RibbonShader`, `RibbonQuad`, `RibbonParam`, `CrystalShader`, `CrystalGem`, `CrystalMesh`, `CrystalParam`; e `ChamberRenderTypes` vira `RepairerRenderTypes`
- Mover os testes `CrystalMeshTest`, `CrystalParamTest`, `CrystalParamFxTest`, `RibbonParamTest` de `T/client/resonator/render/` para `T/client/repairer/render/`
- Mudar: `B/client/repairer/render/RepairerBeams`, `RepairerCrystals`, `RepairerLights`, `RepairerRenderer` (imports e pacote); `src/main/resources/assets/wirelessautomate/shaders/core/resonator_ribbon.fsh` (sai o modo 0 e o 2)
- Apagar (`git rm`): `ResonatorRenderer`, `ChamberScene`, `FlowConduit`, `ChamberRing`, `ChamberLens`, `ChamberParticles`, `ChamberSparks`, `ChamberCrystals`, `LissajousCurve`, `LissajousTrace`, `PlasmaTendrils`, `ArcClock` (`B/client/resonator/render/`) e os testes `ChamberRingTest`, `LissajousCurveTest`, `ArcClockTest`

**Interfaces:**
- **Consome:** `ArmilarSpec`, `ArmilarMeshes` (`ring`, `pins`, `crystal`, `crystalRows`, `horizon`, `markers`), `ArmilarFrame` (Tarefa 4); `Ease`, `ArmilarPose`, `ArmilarMotion`, `ArmilarNodes`, `ArmilarAssembly`, `ArmilarFlow` (Tarefa 5); `CrystalLook`, `CrystalPlan`, `ArmilarRecharge`, `FlashTracker`, `ArmilarGrains` (Tarefa 6); `FxJob`, `FxContext`, `FxQueue` (Tarefa 3).
- **Produz:**
  - `ArmilarScene` (um por Núcleo, no cliente; implementa `FxJob`): `void update(ChamberView view, ResonatorLayout.Face front, long tunedAt, long gameTime, ClientLevel level)` (uma vez por tick novo), `double clock(float partialTick)` (segundos do mundo, ou o relógio parado do e2e), `double assembly(float partialTick)` (o `a`), `double sinceTuned(float partialTick)`, `ArmilarMotion motion()`, `ArmilarFrame frame()`, `byte[] chambers()`, `int tier(int chamber)`, `ChamberView view()`, `FlashTracker flash()`, `float flow(float partialTick)` (a faixa de vazão suavizada da Porta de Energia 0, de 0 a 7), `boolean full()`, `int sunLight()`, `BlockPos core()`, `void setFrame(float partialTick, long gameTime)` (o quadro em que o job vai desenhar), `long lastUsed`.
  - `ArmilarRenderer implements BlockEntityRenderer<ResonanceCoreBlockEntity>`, com os ganchos de e2e `public static int forceBandForTests = -1` e `public static double freezeSecondsForTests = Double.NaN`.
  - `ArmilarSolids.draw(ArmilarScene s, PoseStack ps, MultiBufferSource buf, float partialTick)`; as constantes de textura (`RING_TEX`, `PIN_TEX`, `HORIZON_TEX`, `MARKER_TEXTURE`).
  - Em `ArmilarSpec`: `NUCLEO_DESLOC_Z` e `PORTA_DESLOC_X` (os 2 px que o `armilar_formado.py` tira das caixas das portas; usados nas Tarefas 8 e 9, ver as notas lá), ambos −2.

- [ ] **Passo 1: o relógio do mundo e o estado por Núcleo (`ArmilarScene`).** Porte, do `ChamberScene` que sai, só o que a Armilar usa: o `update` por view nova e por tick, a suavização da vazão (`FLOW_RATE = 0,12` por tick, o que "acende e apaga em cerca de 1 s"), o `tunedAt`, o `full`. O que fica de fora: o Lissajous, as partículas antigas, o plasma, as Portas da casca (as Portas ficam em posição fixa do conceito).
  - **Por view nova** (`if (view != this.view)`): `chambers = view.chambers()` (o `clone` do record acontece só aqui), `flowTarget = view.flows().length > 0 ? view.flows()[0] : 0` (a Armilar tem uma Porta de Energia só; a spec usa `flows[0]`), `full = view.full()`, `origin = view.origin()`, `frame.set(front, origin)`, `flash.onView(view, chambers, gameTime)`.
  - **Por tick novo:** `tunedAt = tunedAt`; `motion.advance(view.tuned() ? 1 : view.band() / 7.0, clockAtTick(gameTime))` (a sintonia, `u = tuned ? 1 : band/7`, suavizada a 1,6/s dentro do `ArmilarMotion`); `flowPrev = flowNow; flowNow += (goal − flowNow) × 0,12` com `goal = ArmilarRenderer.forceBandForTests >= 0 ? forceBandForTests : flowTarget`; `flash.tick(view, chambers, gameTime)`; `lastUsed = gameTime`.
  - **O relógio:** `clock(pt) = Double.isNaN(ArmilarRenderer.freezeSecondsForTests) ? (gameTime + pt) / 20.0 : freezeSecondsForTests`; `clockAtTick(gameTime)` é o mesmo com `pt = 0`. Todos os clientes leem o `gameTime` do mundo, então veem a mesma conjunção.
  - **A luz das peças:** `sunLight = LevelRenderer.getLightColor(level, BlockPos.containing(sol no mundo))` recalculada por tick novo (nunca por quadro), com o sol no mundo = `frame.toWorld(0, SOL_Y, 0, ...)`. **(Conferir)** `LevelRenderer.getLightColor(BlockAndTintGetter, BlockPos)` na 1.21.1.
  - **A montagem:** `assembly(pt)` = `ArmilarAssembly.a(view.formedAt() > 0 ? seconds(gameTime − formedAt + pt) : 0, view.formedAt() <= 0)`; `formedAt <= 0` (montada de antes, ou nunca) vale `a = 1`.
  - `ArmilarFrame` recebe a frente de `ResonatorPartBlock.FACING` do estado do Núcleo, convertida: `NORTH → Face.NORTH`, `SOUTH → Face.SOUTH`, `WEST → Face.WEST`, `EAST → Face.EAST` (um `switch` num método estático `faceOf(Direction)` da própria cena).
  - Nada de objeto novo por quadro e por tick além do que a view nova já cria.
  - `draw(FxContext, MultiBufferSource)` e `distanceSq(FxContext)` do `FxJob` ficam por ora vazios e devolvendo o quadrado da distância ao centro; as Tarefas 8 e 9 os preenchem.

- [ ] **Passo 2: o desenho das peças (`ArmilarSolids`).** O `desenhar` do filme (`partes.js`, `desenhar`, a parte antes de `if (lz <= 0.001)`), em transformações do `PoseStack`. `PX = 1/16`:

```java
    static void draw(ArmilarScene s, PoseStack ps, MultiBufferSource buf, float pt) {
        double t = s.clock(pt);
        double a = s.assembly(pt);
        double u = s.motion().u(pt);
        double since = s.sinceTuned(pt);
        int light = s.sunLight();
        ps.pushPose();
        ps.translate(s.frame().centerX() - s.core().getX(), s.frame().centerY() - s.core().getY(),
                s.frame().centerZ() - s.core().getZ());
        ps.mulPose(Axis.YP.rotation(s.frame().yaw()));
        ps.scale(PX, PX, PX);
        mesh(buf.getBuffer(RenderType.entityCutoutNoCull(HORIZON_TEX)), ArmilarMeshes.horizon(), ps.last(), light, 0xFFFFFF);
        mesh(buf.getBuffer(RenderType.entityCutoutNoCull(MARKER_TEXTURE)), ArmilarMeshes.markers(), ps.last(), light, 0xFFFFFF);
        ps.translate(0, ArmilarSpec.SOL_Y, 0);
        for (int i = 0; i < 3; i++) {
            float ai = (float) ArmilarAssembly.ring(a, i);
            if (ai <= 0.001f) {
                continue;
            }
            ps.pushPose();
            ps.mulPose(Axis.YP.rotation(Ease.wrap(ArmilarPose.prec(i, t))));
            ps.mulPose(Axis.XP.rotation(Ease.wrap(ArmilarPose.tilt(i, t, u, since))));
            ps.scale(ai, ai, ai);
            mesh(buf.getBuffer(RenderType.entityCutoutNoCull(PIN_TEX)), ArmilarMeshes.pins(i), ps.last(), light, 0xFFFFFF);
            ps.mulPose(Axis.YP.rotation(Ease.wrap(s.motion().spin(i, pt))));
            mesh(buf.getBuffer(RenderType.entityCutoutNoCull(RING_TEX)), ArmilarMeshes.ring(i), ps.last(), light, 0xFFFFFF);
            for (int k = 0; k < ArmilarSpec.NOS[i]; k++) {
                crystal(s, ps, buf, light, a, pt, FIRST[i] + k);
            }
            ps.popPose();
        }
        ps.popPose();
    }
```

  - `FIRST = {0, 2, 5}` (o primeiro nó de cada órbita; o `ArmilarNodes` tem a mesma tabela privada: torne-a `static final int[] FIRST` do `ArmilarNodes` e use daqui).
  - **`mesh(...)`:** para cada quad, `tom = round(255 × m.tone(q))`, a cor `(r × tom / 255, ...)` e, nos 4 vértices, `vc.addVertex(pose.pose(), x, y, z).setColor(cr, cg, cb, 255).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz)`. **(Conferir)** os métodos em `VertexConsumer` da 1.21.1. Sem objeto novo: tudo em `float` e `int` locais.
  - **`crystal(...)`:** só se a câmara do nó não é vazia (`node < chambers.length && !CrystalLook.empty(chambers[node])`) e `ck = ArmilarAssembly.crystal(a, ArmilarNodes.popIndex(node)) > 0,001`. Transformações, no referencial do anel (o `ring` do filme): `ps.mulPose(Axis.YP.rotation((float) ArmilarNodes.angle(node)))`, depois o engaste: o cristal vivo fica de pé, com o centro a `(r, CENTRO, 0)`; o Opaco inclina `OPACO_TILT_DEG × fall` e sobe `OPACO_LIFT_PX × fall` (com `fall = 1` parado e `ArmilarRecharge.fall(progresso)` na recarga, o que é a lógica de hoje, "caído e volta com a recarga"), em torno da ponta de baixo: `translate(r, TOPO_BASE + lift, 0)`, `mulPose(Axis.ZP.rotation(−tilt))` (a inclinação é para fora do anel; o sentido não está na spec nem no filme, e as duas pontas passam no teste), `translate(0, CENTRO − TOPO_BASE, 0)`, `scale(ck)`.
  - **Textura e linhas:** se não há Afinado nem recarga no meio (`tier == 0` ou o cristal não é Lapidado, e `lit` é 0 ou 11), desenha a malha inteira `crystal()` com `CrystalLook.bodyTex(code)`. Senão, para cada linha `r` de 0 a 10, `QuadMesh = crystalRows()[r]` com `CrystalPlan.rowTex(r, code, tier, lit)` (a cor do tier, `GuiPaint.tierColor(RouterTier.values()[tier − 1])`, quando `rowTinted`); `lit = ArmilarRecharge.isRecharging(...) ? CrystalPlan.litRows(progress) : (CrystalLook.dull(code) ? 0 : 11)`. O cristal recarregando usa a textura viva só nas linhas acesas.
  - `MARKER_TEXTURE = textures/block/armilar_coral.png` (o conceito; grafite seria `armilar_grafite.png`). `HORIZON_TEX = textures/block/armilar_latao.png`. `RING_TEX`, `PIN_TEX` em `textures/armilar/anel.png` e `grafite.png`.
  - Os `RenderType.entityCutoutNoCull(tex)` são memoizados pelo vanilla; não crie um por quadro.

- [ ] **Passo 3: o BER (`ArmilarRenderer`).** Porte o esqueleto do `ResonatorRenderer` que sai (`VIEW_DISTANCE = 64`, o mapa `SCENES` por `SceneKey(dimension, pos)`, o `BY_CORE` fraco, o `sweep` a cada 200 ticks, as saídas antecipadas `!FORMED || view == EMPTY || !(level instanceof ClientLevel)`), trocando `ChamberScene` por `ArmilarScene`. O corpo do `render`:
  1. acha ou cria a cena; se `scene.lastUsed != gameTime`, `scene.update(view, ArmilarScene.faceOf(state.getValue(ResonatorPartBlock.FACING)), core.tunedAt(), gameTime, level)`;
  2. se `scene.chambers().length == 0`, sai;
  3. `ArmilarSolids.draw(scene, poseStack, buffer, partialTick)`.
  `getRenderBoundingBox`: a caixa 5 × 5 × 5 da `ChamberView.origin` **mais 1 bloco para cada lado na horizontal** (o arco de saída passa de 3 blocos do centro e os decalques do chão vão a ~3 blocos), `new AABB(ox − 1, oy, oz − 1, ox + 6, oy + 5, oz + 6)`; sem view, o bloco do Núcleo. `shouldRenderOffScreen` falso. A cena por posição sobrevive à recarga do chunk (como hoje).

- [ ] **Passo 4: trocar o registro.** Em `ClientSetup.registerRenderers`, `ResonatorRenderer::new` vira `ArmilarRenderer::new` e o Javadoc passa a falar da Armilar. Acrescente em `FxWarmup` as sete texturas de `textures/armilar/` e as duas de bloco (`FxWarmup.add(...)`, do passo 7 da Tarefa 1).

- [ ] **Passo 5: apagar a câmara antiga.** `git rm` das 12 classes e dos 3 testes da lista. **Antes**, confirme com `grep -rn "ArcClock" src` que só o próprio arquivo e o teste o usam (o `ArcClock` não é usado pelo render; é código morto, sai). Se algo fora da lista ainda importar uma classe apagada, o compilador diz no passo 7.

- [ ] **Passo 6: mover o que o Reparador usa.**
  - `git mv` das nove classes e dos quatro testes; troque o `package` e os imports (`B.client.repairer.render`, `T.client.repairer.render`).
  - **`PlasmaCore`:** apague o método `render(PoseStack, MultiBufferSource, ChamberScene, ...)`, as constantes `FULL_BIT`, `HALF` que só ele usa e o import do `ChamberScene`; ficam `detail(double)` e `billboard(...)` (que o `RepairerLights` usa) e `NEAR_SQ`, `FAR_SQ`, `CORNERS`.
  - **`ChamberRenderTypes` → `RepairerRenderTypes`:** só `plasma()`, `ribbon()` e `crystal()`; apague `glow()` e a constante `GLOW` (usa `ChamberLens.ATLAS`, que saiu) e o `LIGHTNING_TRANSPARENCY` se só ela o usava. Os nomes dos tipos (`"wirelessautomate_chamber_plasma"` etc.) viram `"wirelessautomate_repairer_plasma"`, `..._ribbon`, `..._crystal`. `RibbonQuad` e `CrystalGem` chamam `RepairerRenderTypes`.
  - **Visibilidade:** classes e métodos de `PlasmaCore`, `RibbonQuad`, `RibbonParam`, `CrystalGem`, `CrystalMesh`, `CrystalParam` que o `RepairerBeams`, `RepairerCrystals` e `RepairerLights` chamam já são `public`; confira no compilador.
  - **Mensagens de log:** os `LOGGER.warn` dos shaders dizem "a câmara do Ressonador volta ao desenho antigo"; troque para "o Reparador volta ao desenho antigo" (nos três: `PlasmaShader`, `RibbonShader`, `CrystalShader`).
  - **`RibbonParam`:** apague `MODE_BOLT` (0) e `MODE_TENDRIL` (2) e o que o `RibbonParamTest` afirmar sobre eles; ficam `MODE_WEAVE` (1) e `MODE_DISC` (3).
  - **`resonator_ribbon.fsh`:** apague o `if (mode == 0) {...}` (o raio) e o `else {...}` final (o tentáculo), as funções que só eles usavam (`n1`, `boltOffset`, `boltDist`, `boltLight`, `tendrilOffset`) e as constantes que sobrarem sem uso (`RED`), e deixe `if (mode == 1) { ... } else if (mode == 3) { ... }`. Um modo desconhecido não pinta nada (o `peak < 0,003` já descarta). Confira o que o `wa_common.glsl` exporta e que o `weaveY` e o disco ainda compilam.
  - **`ShaderPackCheck`:** os imports do `RepairerRenderer` já apontam para `client.fx` desde a Tarefa 1.
  - `RepairerRenderer.forceLegacyForTests` e o desenho antigo do Reparador **ficam** (o Reparador é de outra etapa).

- [ ] **Passo 7: os ganchos do e2e que somem.** Em `E2eResonatorSteps`: `ResonatorRenderer.forceLegacyForTests` e `forceBandForTests` somem do `restoreMeasure`, trocando `ResonatorRenderer.forceBandForTests` por `ArmilarRenderer.forceBandForTests`; `timed(...)` passa a medir só uma situação (`frameTime(situation, perTick)` sem o `legacy`: apague o `legacy` e o `forceLegacyForTests = legacy`); `E2eResonatorFlowSteps` troca `ResonatorRenderer.forceBandForTests` por `ArmilarRenderer.forceBandForTests` (linhas 120 e 127). As capturas continuam com os nomes de hoje; a Tarefa 11 as refaz.

- [ ] **Passo 8: compilar.** `./gradlew build` verde (compila e roda o JUnit; o `ArcClockTest`, o `ChamberRingTest` e o `LissajousCurveTest` saíram). `grep -rn "ChamberScene\|ChamberCrystals\|ResonatorRenderer\|ChamberRenderTypes" src` não acha nada. As três varreduras.

- [ ] **Passo 9: Rodada de jogo (avise o dono).** O que vai ser visto, escrito antes: as órbitas inclinadas e soltas fora de sintonia, deitadas e travadas em sintonia, cristais nos nós (Sinal, Lapidado, Afinado com a faixa na cor do tier, Opaco inclinado 8° no engaste, Rachado, e o Opaco recarregando aceso de baixo para cima em degraus), o horizonte e os quatro marcos parados; **sem sol, sem filamentos e sem luz** (vêm nas Tarefas 8 e 9). `./scripts/e2e.sh --only ressonador --capture ressonador-camara,ressonador-cristais --out run/e2e-armilar` e, depois, a mesma coisa com Complementary (`--shaders complementary`, da Tarefa 3). Confira: `grep -c "Couldn't compile" run/e2e-armilar/client.log` é 0 (o `resonator_ribbon.fsh` mexido compila; as suítes do Reparador não rodaram: rode `./scripts/e2e.sh --only reparador-animacao --out run/e2e-rep` na mesma rodada para provar o Reparador, que depende do shader mexido); `run/e2e-armilar/result.txt` na primeira linha `OK`. Mostre as imagens ao dono e **pare**.

- [ ] **Passo 10: commit.** `git commit -m "Armilar: o BER novo com as peças sólidas; sai a câmara antiga e o que o Reparador usa vai para repairer/render"`.

---

### Tarefa 8: os efeitos do sol: sol, brasas, filamentos e ponteiro

Entrega: o sol com a tinta, a luz, a estrela e o aro; as 12 brasas; os filamentos de cada cristal vivo; o ponteiro do mostrador. Fora de sintonia e em sintonia, com a montagem, o buffer cheio e a eficiência baixa.

**Arquivos:**
- Criar: `B/client/resonator/render/ArmilarPass.java`, `ArmilarSun.java`, `ArmilarFilaments.java`, `ArmilarPointer.java`, `ArmilarEmbers.java`
- Mudar: `ArmilarScene.java` (o `draw` e o `distanceSq`), `ArmilarRenderer.java` (anota o job), `FxSheet.java` (o `frameAt` com `n`)
- Testes (novos): `T/client/resonator/render/ArmilarEmbersTest.java`, `ArmilarPointerTest.java`, `FilamentPathTest.java`; mudar `T/client/fx/FxSheetTest.java`

**Interfaces:**
- **Consome:** `FxJob`, `FxContext`, `FxQueue`, `FxDraw`, `FxRibbon`, `FxSheets`, `FxTypes` (Tarefas 1 e 3); `ArmilarScene`, `ArmilarFrame`, `ArmilarPhase`, `ArmilarNodes`, `ArmilarFlow`, `ArmilarAssembly`, `CrystalLook` (Tarefas 4 a 7).
- **Produz:**
  - `FxSheet.frameAt(double seconds, double fps, int n)`: como `frameAt`, mas com `n` quadros em vez de `frames` (o `qd` do filme usa 3 dos 4 quadros da fumaça).
  - `ArmilarPass` (um por cena, reaproveitado): `void begin(ArmilarScene scene, FxContext ctx, MultiBufferSource buffers)`; os campos do quadro `double t, a, u, ig, enc, prepEff, eff`, `ArmilarPhase phase`; os ajudantes `void spr(FxSheet sheet, int frame, double lx, double ly, double lz, double halfPx, int rgb, double alpha, double rot, int dis)` (ponto local em px), `void sprCam(FxSheet sheet, int frame, float x, float y, float z, double halfPx, int rgb, double alpha, double rot, int dis)` (ponto já relativo à câmera, em blocos), `void dec(FxSheet sheet, int frame, double lx, double ly, double lz, double[] axis1, double[] axis2, double halfPx, int rgb, double alpha, double rot, int dis)`, `void ribbon(FxSheet sheet, int frame, FxRibbon.Buffer b, int n, int rgb, double repPx, double offset, int dis)` (pontos e larguras em px locais; converte para a câmera dentro), e as cores (`VINHO`, `OURO`, `CLARO`, `WHITE`, `PLASMA`, `CORAL_HI`, `CORAL`, `PONTEIRO`).
  - `ArmilarEmbers.orbit(int k, double t, double prep, double[] out)`, `orbitAlpha(int k, double t)`.
  - `ArmilarPointer.fraction(double t, double u)`.
  - `ArmilarFilaments.path(double[] from, double[] sun, double t, double u, int orbit, int slot, float[] ctl)` (os 6 controles de um filamento).

- [ ] **Passo 1: o `frameAt` com `n`.** Em `FxSheet`, acrescente a sobrecarga (e use-a na fumaça, que o filme toca com `n = 3`):

```java
    /** O {@code qd} do filme com {@code n} quadros (o filme toca 3 dos 4 quadros da fumaça): {@code floor(t × fps) mod n}. */
    public int frameAt(double seconds, double fps, int n) {
        long k = (long) Math.floor(seconds * fps);
        return (int) (((k % n) + n) % n);
    }
```

  e refaça `frameAt(seconds, fps)` como `frameAt(seconds, fps, frames)`. Teste: `frameAt(0, 4, 3) = 0`, `frameAt(0,26; 4; 3) = 1`, `frameAt(0,76; 4; 3) = 0` (quadro 3 de 3 volta a 0).

- [ ] **Passo 2: os testes das contas (falham primeiro).**
  - `ArmilarEmbersTest` com o ouro: `orbit(0, 0, 0) = (8,5; 0; 0)` e `orbitAlpha(0, 0) = 0,55`; `orbit(5; 3,3; 0) = (−6,03735103; −2,12675212; 8,28328996)` e `orbitAlpha(5; 3,3) = 0,597029601`; `orbit(11; 7,1; 1) = (2,26941044; −0,058632461; 3,08833814)` e `orbitAlpha = 0,893678177`; `orbit(7; 100,5; 0,5) = (−6,039774; −1,54541097; 5,03584095)` e `orbitAlpha = 0,358169151` (tolerância 1e-6).
  - `ArmilarPointerTest`: `fraction(0, 0) = 0,5`, `fraction(3, 0) = 0,700383314`, `fraction(7,7; 0) = 0,672446372`, `fraction(3, 1) = 0,62`, `fraction(t, 1) = 0,62` para qualquer `t`.
  - `FilamentPathTest`: o filamento de um cristal em `(−14,374379; 40,547018; 12,259008)` (o nó 4 do ouro da Tarefa 5) ao sol `(0; 47,5; 0)`, com `u = 0`, `t = 3,3`, órbita 1, slot 2: os 6 controles começam no cristal e terminam no sol (`f = 0` e `f = 1` valem `wv = 0`), e o do meio (`f = 0,4` e `0,6`) se afasta da reta por no máximo `amp = 0,4 + 1,5 × (1 − u) = 1,9` px na lateral e 0,5 × 1,9 no alto.
  - Rode e veja falhar.

- [ ] **Passo 3: `ArmilarEmbers`.** Do `desenhar` do filme (as 12 brasas em órbita), em px relativos ao sol:

```java
final class ArmilarEmbers {
    private ArmilarEmbers() {
    }

    /** A brasa {@code k} em órbita, relativa ao sol (px): sugada para perto pela preparação ({@code prep}, de 0 a 1). */
    static void orbit(int k, double t, double prep, double[] out) {
        double a0 = k * 2.399;
        double rr = (8.5 + 3.5 * ((k * 37) % 10) / 10.0) * (1 - 0.65 * prep);
        double an = a0 + t * (0.5 + (k % 3) * 0.17);
        out[0] = rr * Math.cos(an);
        out[1] = 2.2 * Math.sin(an * 1.7 + k);
        out[2] = rr * Math.sin(an);
    }

    /** O alfa (antes de multiplicar pelo {@code ig} do sol): {@code 0,55 + 0,35 sin(1,3 t + k)}. */
    static double orbitAlpha(int k, double t) {
        return 0.55 + 0.35 * Math.sin(t * 1.3 + k);
    }
}
```

- [ ] **Passo 4: `ArmilarPointer`.** Do `ponteiro` do filme:

```java
final class ArmilarPointer {
    private ArmilarPointer() {
    }

    /** A posição do ponteiro no mostrador, de 0 a 1: caça a estação fora da sintonia e para em 0,62 em sintonia. */
    static double fraction(double t, double u) {
        return Ease.lerp(0.5 + 0.36 * Math.sin(t * 0.7) * Math.cos(t * 0.29), 0.62, u);
    }
}
```

  O desenho (no `ArmilarPointer.draw(ArmilarPass p)`, a ser escrito no passo 6): `f = fraction(t, u)`; 5 pontos locais `x = 0 + (f − 0,5) × 18`, `y = 7,5 − 2,1 + 4,2 × j/4`, `z = Z + 0,12`, com `j` de 0 a 4; uma fita `FxSheets.MIOLO` com meia largura 0,22, alfa 1, cor `PONTEIRO = 0xFF9E80` (o `[1; 0,62; 0,5]` do filme), `rep = 4` e sem deslocamento (`off = 0`), sem dissolução. **O `Z` do filme é 41, o do mostrador do conceito; no jogo as caixas do Núcleo entram 2 px num nicho (`armilar_formado.py`, `desloc["nucleo"] = (0, 0, −2)`),** então `Z = 41 + ArmilarSpec.NUCLEO_DESLOC_Z = 39`. **Confira** no modelo do Núcleo (`assets/wirelessautomate/models/block/resonance_core*.json`, gerado por `scripts/textures/ressonador_blocos.py`) o `z` da face de fora do vidro do mostrador, em px do referencial da Armilar (a casa do Núcleo é a `(2, 0, 4)`: `z` de 24 a 40), e use essa face mais 0,12; se não for 39, corrija a constante e diga no relatório. O ponteiro **não é peça sólida** (a spec o lista entre as sólidas, mas o filme o desenha como fita de luz, `R.fita('luz', 'miolo', ...)`): segue o filme.

- [ ] **Passo 5: `ArmilarPass`.** O contexto do desenho dos efeitos de uma cena num quadro:

```java
final class ArmilarPass {
    static final int VINHO = 0x87301F;
    static final int OURO = 0xFFD142;
    static final int CLARO = 0xFFF2D9;
    static final int WHITE = 0xFFFFFF;
    static final int PLASMA = 0xFF8A6B;
    static final int CORAL_HI = 0xFFB39E;
    static final int CORAL = 0xF0603E;
    static final int PONTEIRO = 0xFF9E80;

    final ArmilarPhase phase = new ArmilarPhase();
    ArmilarScene scene;
    FxContext fx;
    MultiBufferSource buffers;
    double t;
    double a;
    double u;
    /** O que o sol multiplica: o {@code ig} do filme (entrada do sol) vezes a eficiência. */
    double ig;
    double enc;
    double prepEff;
    double eff;
    float sunX;
    float sunY;
    float sunZ;
    private final double[] world = new double[3];

    /** A pose de cada órbita neste quadro: precessão, inclinação, giro e a escala da entrada. */
    final double[] prec = new double[3];
    final double[] tilt = new double[3];
    final double[] spin = new double[3];
    final double[] ring = new double[3];

    void begin(ArmilarScene scene, FxContext fx, MultiBufferSource buffers) {
        this.scene = scene;
        this.fx = fx;
        this.buffers = buffers;
        float pt = fx.partialTick;
        t = scene.clock(pt);
        a = scene.assembly(pt);
        u = scene.motion().u(pt);
        phase.set(t, scene.view().tuned() ? u : 0);
        eff = ArmilarFlow.sunMul(u);
        prepEff = Math.max(phase.prep, scene.full() ? ArmilarPhase.fullPrep(t) : 0);
        ig = ArmilarAssembly.ignition(a) * eff;
        enc = 1 - 0.28 * prepEff + 0.18 * phase.raio;
        double since = scene.sinceTuned(pt);
        for (int i = 0; i < 3; i++) {
            prec[i] = ArmilarPose.prec(i, t);
            tilt[i] = ArmilarPose.tilt(i, t, u, since);
            spin[i] = scene.motion().spin(i, pt);
            ring[i] = ArmilarAssembly.ring(a, i);
        }
        scene.frame().toWorld(0, ArmilarSpec.SOL_Y, 0, world);
        sunX = (float) (world[0] - fx.camX);
        sunY = (float) (world[1] - fx.camY);
        sunZ = (float) (world[2] - fx.camZ);
    }
```

  - O `begin` acima aplica a spec: a conjunção só em sintonia (`phase.set(t, tuned ? u : 0)`); o `ig` do filme (a entrada do sol) vezes `eff = 0,55 + 0,45 u` (a spec: "Sol e filamentos multiplicados por `0,55 + 0,45 u`"; o plano multiplica a escala **e** o alfa do sol, que é como o filme usa o `ig`; se o dono preferir só o alfa, é a troca de uma linha); o buffer cheio sobe o `prepEff` (Tarefa 9).
  - **`spr`:** converte o ponto local para a câmera (`frame.toWorld` menos a câmera) e chama `sprCam`. **`sprCam`:** `FxDraw.sprite(buffers.getBuffer(FxTypes.of(sheet)), fx, sheet, frame, x, y, z, (float) (halfPx / 16), (float) rot, rgb, (float) alpha, dis, light(sheet))`, com `light(sheet) = sheet.layer() == LIGHT ? LightTexture.FULL_BRIGHT : scene.sunLight()`.
  - **`dec`:** os eixos locais viram vetores do mundo por `frame.dirToWorld` e vão a `FxDraw.decal`.
  - **`ribbon`:** copia os pontos locais de `b` para um segundo `FxRibbon.Buffer` já relativo à câmera (blocos), as larguras `/ 16`, e chama `FxDraw.ribbon(..., rep = repPx / 16)`. O `offset` e a dissolução (`dis` em milésimos: `Math.round(dis × 1000)` na hora de chamar) seguem o filme.
  - Os dois buffers de fita são do `ArmilarPass` (campos `final`), nada alocado por quadro.

- [ ] **Passo 6: o sol, as brasas, os filamentos e o ponteiro.** Cada arquivo é o trecho do `desenhar` do filme (`partes.js`) citado, com os números do filme. Ordem de chamada: sol, brasas, filamentos, ponteiro (a tinta sempre sai antes da luz, porque o `FxBatch` desenha por tipo, tinta primeiro).
  - **`ArmilarSun.draw(ArmilarPass p)`** (do `// o sol: névoa escura por baixo...` até as brasas), com `s` = o sol local `(0, 47,5, 0)`:
    1. fumaça: `spr(FUMACA, frameAt(t, 4, 3), s, 7,5 × ig × (1 + 0,3 × prepEff), VINHO, 0,45 + 0,3 × prepEff, t × 0,15)` e `spr(FUMACA, frameAt(t + 0,5; 4; 3), s, 6 × ig, VINHO, 0,3, −t × 0,11 + 1,3)`;
    2. luz: `spr(BRILHO_G, 0, s, 6,5 × ig × enc, PLASMA, 0,5, 0)` e `spr(SOL, frameAt(t, 8), s, 4,6 × ig × enc, WHITE, 1, 0)`;
    3. as 6 chamas da coroa, `k` de 0 a 5: `th = k·π/3 + t·0,35`, `rr = 4,2 × ig × enc`, a posição **na tela** `sol_camera + (right × cos th + up × sin th) × rr / 16` (os eixos `fx.rx…`, `fx.ux…`; é um deslocamento de tela, não do referencial local), `sprCam(CHAMA, frameAt(t + k × 0,37; 10), ..., 1,6 × ig × enc, WHITE, 0,85, th − π/2)`;
    4. `spr(ESTRELA, frameAt(t, 6), s, 6,5 × ig × enc, OURO, 0,35 + 0,3 × phase.raio, t × 0,2)` e `spr(ARO_TRACEJADO, 0, s, 7 × ig × enc, OURO, 0,45, −t × 0,3)`;
    5. as 12 brasas: para `k` de 0 a 11, `ArmilarEmbers.orbit(k, t, prepEff, off)`, a folha é `'brasa' + (k % 3)` (`BRASA0`, `BRASA1` ou `BRASA2`), a meia largura `0,55 + 0,2 × (k % 2)`, a cor `OURO` se `k % 3 != 0` e `CORAL_HI` se não (o filme escreve `k % 3 ? OURO : C.coralHi`), o alfa `ig × orbitAlpha(k, t)`, no ponto `s + off`, sem rotação.
  - **`ArmilarFilaments.draw(ArmilarPass p)`** (o `// filamentos` do filme): para cada nó `n` com cristal (`!CrystalLook.empty(code)` e `ck > 0,001`) e `f = CrystalLook.filament(code)` maior que 0, `view.band() > 0 || view.tuned()` (com `band = 0` não há filamento), calcule o centro `q` do cristal com `ArmilarNodes.center(n, p.ring[orbit], p.prec[orbit], p.tilt[orbit], p.spin[orbit], q)` (os mesmos valores do `ArmilarSolids`, guardados pelo `ArmilarPass` por quadro), e então:
    - `d = sol − q`, `side = normalize(cross(d, (0,2; 1; 0,3)))`, `up2 = normalize(cross(side, d))`, `amp = 0,4 + 1,5 × (1 − u)`; os 6 controles `ctl[j]`, `f = j/5`, `wv = sin(fπ) × amp`: `q + d f + side × wv × sin(t × (1,1 + orbit × 0,2) + f × 3 + slot × 2,1) + up2 × 0,5 × wv × cos(t × 0,9 + f × 2,5 + slot)` (o `ArmilarFilaments.path`, pura e testada); normalize protegido contra vetor zero;
    - `FxRibbon.catmullRom(ctl, 6, 1f, buf)` (amostra a cada 1 px, em px locais); `al = ck × (0,3 + 0,6 × u) × eff × f` (o `ck` da entrada do cristal, `eff = 0,55 + 0,45 u`, `f` o fator do estado: 1 vivo, 0,35 rachado);
    - a tinta: `w[i] = 0,75`, `a[i] = 0,3 × al × sin(fπ)`, `ribbon(TRILHOS, 0, buf, n, WHITE, 10, 0, 0)`;
    - a luz: `w[i] = 0,55 + 0,25 × u`, `a[i] = al × (0,4 + 0,6 f)`, `ribbon(FLUXO, 0, buf, n, WHITE, 9, t × 1,6, 0)`;
    - `spr(BRILHO, 0, q, 1,6, CORAL_HI, 0,45 × ck × f, 0)`; se `u > 0,05` e o cristal é vivo (`CrystalLook.star`): `spr(ESTRELA, frameAt(t + slot × 0,3; 6), q, 2,6, OURO, 0,45 × u × ck, t × 0,4 + slot)`.
  - **`ArmilarPointer.draw(ArmilarPass p)`** como no passo 4.
  - Cada um recebe só o `ArmilarPass`; nada de `lambda` que capture variável (as larguras e os alfas por amostra saem de laços `for` sobre `buf.w` e `buf.a`).

- [ ] **Passo 7: ligar o job.** `ArmilarScene.draw(FxContext ctx, MultiBufferSource buffers)`: `pass.begin(this, ctx, buffers)`; `if (pass.a <= 0) return;` (o filme: `if (a <= 0) return;`); `ArmilarSun.draw(pass); ArmilarFilaments.draw(pass); ArmilarPointer.draw(pass);` e as Tarefas 9 acrescentam o resto. `distanceSq(ctx)`: o quadrado da distância da câmera ao centro do sol. No `ArmilarRenderer.render`, depois do `ArmilarSolids.draw`: se `FxShaders.ready()` e `!ShaderPackCheck.shadowPass()`, `FxQueue.submit(scene)` (a cena é o job; o `partialTick` e o `gameTime` do quadro entram por `scene.setFrame(partialTick, gameTime)` antes). O corte de 48 blocos está no `FxStages` (Tarefa 3).

- [ ] **Passo 8: verificar.** `./gradlew build` verde (os três testes novos e o `FxSheetTest` passam) e as três varreduras. `varrer_perf.py` vai apontar o que for alocação no caminho do quadro: o que sobrar tem de ter justificativa por escrito.

- [ ] **Passo 9: Rodada de jogo (avise o dono).** O que vai ser visto: o sol com a fumaça vinho por baixo, o disco que ferve a 8 fps, as 6 chamas girando, a estrela dourada e o aro tracejado, 12 brasas em órbita; os filamentos (tinta de dois trilhos por baixo e o fluxo correndo por cima) de cada cristal vivo ao sol, ondulando fora de sintonia e firmes em sintonia; o ponteiro caçando a estação e parando em 62%. `./scripts/e2e.sh --only ressonador --capture ressonador-camara --out run/e2e-armilar`. Compare com `formado.html?t=0&u=0` e `t=0&u=1` do filme (sem conjunção: a conjunção só aparece na Tarefa 9). `grep -c "Couldn't compile"` é 0. Mostre e **pare**.

- [ ] **Passo 10: commit.** `git commit -m "Armilar: o sol, as brasas, os filamentos e o ponteiro, desenhados pelo client/fx"`.

---

### Tarefa 9: a conjunção, a recarga e o buffer cheio

Entrega: a preparação, o estouro, o arco até a Porta de Energia, o impacto e a dissolução na linha do tempo do filme, com a vazão, a recarga (grãos de redstone e o clarão no cristal) e o buffer cheio.

**Arquivos:**
- Criar: `B/client/resonator/render/ArmilarBeam.java`, `ArmilarArc.java`, `ArmilarImpact.java`
- Mudar: `ArmilarEmbers.java` (as brasas do impacto), `ArmilarScene.java` (`draw`, os grãos e o clarão), `ArmilarSpec.java` (as posições da Porta)
- Testes (novos): `T/client/resonator/render/ArmilarArcTest.java`, `ArmilarImpactMathTest.java`

**Interfaces:**
- **Consome:** `ArmilarPass`, `ArmilarPhase`, `ArmilarFlow`, `FxRibbon.bezier`, `FxSheets` (Tarefas 3 a 8); `ArmilarGrains`, `FlashTracker`, `ArmilarRecharge` (Tarefa 6).
- **Produz:**
  - Em `ArmilarSpec`, as posições (px locais) com o deslocamento da Porta de Energia: `HORIZ_BURST = {32,5; 47,5; 0}`, `PORTA_IMPACTO = {42,4 + PORTA_DESLOC_X; 8; 0}`, `PORTA_ARCO_FIM = {42,8 + PORTA_DESLOC_X; 8,6; 0}`, `PORTA_LUZ = {44 + PORTA_DESLOC_X; 8; 0}`, `PORTA_CRISTAIS = {−42,4 − PORTA_DESLOC_X; 8; 0}` (a Porta de Cristais entra 2 px do outro lado, `+2`; a conta é a mesma). `PORTA_DESLOC_X = −2` e `NUCLEO_DESLOC_Z = −2` (o `armilar_formado.py` entra as caixas das portas 2 px num nicho). **Aviso:** a spec e o filme da formada (`scene-formado.js`) usam os números do conceito (42,8 e 42,4), que ficam 2 px para fora da face da Porta que o jogo desenha. O plano aplica o deslocamento, com `PORTA_DESLOC_X` numa constante só para zerar depois da prancha, se o dono preferir o número do filme.
  - `ArmilarArc.fill(double t, double deslocFim, FxRibbon.Buffer b)` (os 61 pontos do arco em px locais, com a ondulação), `ArmilarArc.tail(double head, double f)` (o `ate(f)` do filme).
  - `ArmilarEmbers.impact(int k, double d, double[] out)` devolvendo se a brasa está viva e escrevendo `{x, y, z, fv, tamanho}`; e `impactR1(int k)`, `impactR2(int k)`.

- [ ] **Passo 1: os testes (falham primeiro).**
  - `ArmilarImpactMathTest` com o ouro: `impactR1(0) = 0,211321159`, `impactR2(0) = 0,791346058`; `impactR1(3) = 0,330932785`, `impactR2(3) = 0,0717497752`; `impactR1(15) = 0,809379287`, `impactR2(15) = 0,193364645`; e a brasa 0 no instante `d = 0,6 + ...` (calcule a mão a partir da fórmula do filme e escreva o número no teste): `t0 = 0,58 + r1 × 0,25`, `vida = 0,6 + r2 × 0,7`, só viva entre `t0` e `t0 + vida`; fora da janela `impact` devolve `false`.
  - `ArmilarArcTest`: `fill(t, 0, b)` devolve 61 amostras; a primeira é `(33,6; 46,6; 0)` e a última `(42,8; 8,6; 0)` (a ondulação é `sin(fπ)` e some nas pontas) para qualquer `t`; com `deslocFim = −2` a última é `(40,8; 8,6; 0)`; o passo entre amostras é no máximo 1 px (o arco do filme tem ~0,83 px); `tail(h, f)` vale 1 para `f` bem antes de `h`, 0 para `f ≥ h` e é decrescente entre `h − 0,05` e `h`.

- [ ] **Passo 2: o estouro (`ArmilarBeam`).** Do `// estouro:` do filme, só se `phase.raio > 0,002`, com `s` o sol e `pa = HORIZ_BURST`:
  - os pontos: `FxRibbon.Buffer` com 25 pontos `mix(s, pa, phase.cresce × j / 24)`, `j` de 0 a 24;
  - tinta: `w = 1,6`, `a = phase.raio`, `ribbon(TRILHOS, 0, buf, 25, WHITE, 12, t × 2, dis)`;
  - luz: `ribbon(TRILHOS_RIM, 0, buf, 25, WHITE, 12, t × 2, dis)` com `w = 1,6`, `a = 0,8 × raio`; `ribbon(FLUXO, 0, buf, 25, WHITE, 10, t × 3,2, dis)` com `w = 1,1`, `a = raio`; `dis = phase.dis` (em milésimos);
  - `spr(CLARAO, frameByFraction(ss(0,1; 0,5; phase.d)), pa, 6, CLARO, phase.clarao, 0,2)`;
  - `dec(CHOQUE, frameByFraction(phase.onda), pa, (0,0,1), (0,1,0), 8, OURO, phase.raio, 0, 0)`.
  - **Com a vazão 0 o estouro e o clarão no horizonte acontecem** (a spec): nada aqui depende da vazão.

- [ ] **Passo 3: o arco (`ArmilarArc`).** Do `// o arco:` do filme, só se `phase.arco > 0,002` e `k = ArmilarFlow.strength(scene.flow(pt)) > 0` (**a vazão multiplica o alfa, a largura e as brasas; com vazão 0 não há arco**):
  - `B = {{33,6; 46,6; 0}, {46; 44; 0}, {49; 17; 0}, {42,8 + PORTA_DESLOC_X; 8,6; 0}}`; os 61 pontos `bezier(B, 60)` mais a ondulação `(0,3 w sin(f·6 + t·1,7), 0,2 w sin(f·5 − t·1,3), 0,7 w sin(f·5 + t·1,9))` com `w = sin(fπ)`;
  - `h = phase.cabeca`, `ate(f) = 1 − ss(h − 0,05; h; f)`; por amostra `A = phase.arco × ate(f) × k`;
  - tinta: `ribbon(TRILHOS, 0, buf, 61, WHITE, 12, t × 1,5, dis)`, `w = 1,7 × k`, `a = A`;
  - luz: `TRILHOS_RIM` (`w = 1,7 k`, `a = 0,8 A`, `rep 12`, `off t × 1,5`), `RAIO` (`frameAt(t, 12)`, `w = 1,5 k`, `a = A`, `rep 14`, `off t × 0,6`) e `FLUXO` (`w = 0,9 k`, `a = A`, `rep 10`, `off t × 4`), todos com `dis`;
  - a cabeça, se `h < 0,995`: `hp = pts[round(h × 60)]`, `tr = pts[max(0, round(h × 60) − 4)]`; `spr(ESTRELA, frameAt(t, 10), hp, 3,2, CLARO, phase.arco, t)`; para `j` de 1 a 3, `spr(BRASA(j % 3), 0, mix(hp, tr, j/3), 0,6, OURO, phase.arco × (1 − j/4), 0)`; `spr(RISCO_L, 0, mix(hp, tr, 0,5), 2, CLARO, 0,8 × phase.arco, rot)`, com `rot = atan2(−ΔY × H, −ΔX × W)`, onde `ΔX` e `ΔY` são a diferença `hp − tr` em coordenadas de tela normalizadas e `W`, `H` o tamanho da janela (o `atan2(screen(hp).y − screen(tr).y, screen(tr).x − screen(hp).x)` do filme, que mede em pixels com o y para baixo). Acrescente em `FxContext` `void ndc(float x, float y, float z, float[] out)` com a matriz `projeção × modelview` do quadro, e use `Minecraft.getInstance().getWindow().getWidth()` e `getHeight()`.

- [ ] **Passo 4: o impacto (`ArmilarImpact`).** Do `// impacto na Porta de Energia:` do filme, só se `phase.porta > 0,002` e `k > 0`; `porta = PORTA_IMPACTO`, `PO = phase.porta × k`:
  - `spr(CLARAO, frameByFraction(ss(0,55; 0,95; d)), porta, 6,5, CLARO, phase.clarao2 × k, 0,4)`;
  - o sigilo na face da Porta: `dec(SIGILO, frameByFraction(ss(0,55; 1,3; d)), porta + (0,2; 0; 0), (0,0,1), (0,1,0), 5,5 × eob(phase.surge, 1,6), WHITE, PO, t × 0,35, dis)`;
  - o choque no chão: `dec(CHOQUE, frameByFraction(phase.onda2), (47 + PORTA_DESLOC_X; 0,12; 0), (1,0,0), (0,0,1), 11, OURO, PO, 0, 0)`; o decalque de fumaça da tinta `dec(FUMACA, 1, (46 + PORTA_DESLOC_X; 0,1; 0), (1,0,0), (0,0,1), 6, VINHO, 0,45 × PO, 0,7, dis)`;
  - **as 16 brasas**, só as `k_b` primeiras com `n = round(16 × k)` (a spec: a força multiplica o número de brasas): `ArmilarEmbers.impact` por `d`, a folha `BRASA(2 − min(2, floor(fv × 3)))`, a meia largura `{0,9; 0,7; 0,5}[min(2, floor(fv × 3))]`, a cor `fv < 0,33 ? CLARO : fv < 0,66 ? OURO : CORAL`, o alfa `PO × (1 − fv × 0,5)`;
  - **as 4 fumaças** `k` de 0 a 3: `age = d − 0,7 − k × 0,18`, só se `0 <= age <= 1,6`: `spr(FUMACA, frameByFraction(age / 1,6), porta + (1,5 + k; 2 + age × 6; (k − 1,5) × 1,5), 1,5 + age × 2, VINHO, 0,45, k + age × 0,5)`.
  - `ArmilarEmbers.impact(int k, double d, double[] out)`, do filme: `r1 = ((k × 9301 + 49297) % 233280) / 233280`, `r2 = ((k × 4271 + 7919) % 10007) / 10007` (aritmética inteira), `t0 = 0,58 + r1 × 0,25`, `vida = 0,6 + r2 × 0,7`, `age = d − t0`; fora de `[0, vida]` devolve `false`; senão `an = (r1 − 0,5) × 2,4`, `sp = 7 + r2 × 9`, `dist = sp × (1 − exp(−age × 3)) / 3`, `out = {cos(an) × dist + 0,5; dist × (0,9 + r1) − 9 × age²; sin(an) × dist}` (relativos a `porta`) e `fv = age / vida`.

- [ ] **Passo 5: a recarga e o buffer cheio.**
  - **Recarga (a lógica de hoje):** o Opaco que recarrega já acende de baixo para cima (Tarefa 7). Falta o clarão e os grãos:
    - **O clarão no cristal ao fim da recarga:** `f = scene.flash().flash(i, gameTime, pt)`; se `0 <= f < 1`: `spr(CLARAO, frameByFraction(f), centro do cristal i, 4, CLARO, (1 − f)², 0)`, no centro dado por `ArmilarNodes.center`. **O tamanho (meio lado 4 px) e a curva do alfa não estão na spec nem no filme: são propostas, e ficam em constantes só (`FLASH_HALF`, ver a prancha).** Duração 0,7 s (`FlashTracker.FLASH_TICKS = 14`).
    - **Os grãos de redstone:** em `ArmilarScene.update`, por tick novo, `grains.tick(ArmilarRecharge.firstRecharging(chambers, view), chambers.length)` e, para cada grão vivo, o ponto `from + (to − from) × Ease.ss(0, 1, progress)` com `from = PORTA_CRISTAIS` (no mundo, por `frame.toWorld`) e `to` = o centro do cristal do alvo (por `ArmilarNodes.center` com a pose do tick), e `level.addParticle(REDSTONE, x, y, z, 0, 0, 0)`, com `REDSTONE = new DustParticleOptions(new Vector3f(0,84f; 0,27f; 0,23f), 0,6f)` (a de hoje, de `ChamberParticles`, que sai). Um array `double[3]` de rascunho na cena; nada por quadro.
  - **Buffer cheio:** já entra pelo `prepEff` (Tarefa 8): o sol sob pressão, parado em 60% e pulsando a 0,5 Hz, com as brasas puxadas para perto (o `1 − 0,65 × prepEff` do raio das brasas). A amplitude de 0,1 está em `ArmilarPhase.fullPrep`.

- [ ] **Passo 6: ligar na cena.** `ArmilarScene.draw`: depois do ponteiro, `ArmilarBeam.draw(pass); ArmilarArc.draw(pass); ArmilarImpact.draw(pass);` e o clarão dos cristais. A ordem do filme (estouro, arco, impacto) vale; a tinta e a luz já se separam pelo tipo.

- [ ] **Passo 7: verificar.** `./gradlew build` verde (`ArmilarArcTest`, `ArmilarImpactMathTest`) e as três varreduras.

- [ ] **Passo 8: Rodada de jogo (avise o dono).** O que vai ser visto: as cinco fases da conjunção (preparação, estouro, arco, impacto na Porta, dissolução), com o relógio parado em cada fase pelo gancho `ArmilarRenderer.freezeSecondsForTests = TC + d` (`d` = −0,3; 0,1; 0,35; 0,9; 2,0), com a vazão 7 e depois com a vazão 0 (só o estouro e o clarão no horizonte); o buffer cheio (o sol pressionado); o grão de redstone voando da Porta de Cristais e o clarão do fim da recarga. Acrescente os passos à suíte opcional `armilar-render` (`E2eArmilarRenderSteps`, `Prereq.NENHUM`, `E2eSuites`), que a Tarefa 11 funde na `ressonador`. `./scripts/e2e.sh --only armilar-render --capture armilar-render --out run/e2e-armilar`. Compare com `formado.html?t=<11 + d>&u=1&yaw=38&pitch=24&dist=215&ty=30` do filme nas mesmas fases (a Tarefa 11 faz a prancha); aqui basta ver as formas e o tempo. Mostre e **pare**.

- [ ] **Passo 9: commit.** `git commit -m "Armilar: a conjunção, o arco até a Porta de Energia, o impacto, a recarga e o buffer cheio"`.

---
### Tarefa 10: a luz no mundo

Entrega: o sol, o clarão do horizonte e a Porta iluminam o que está em volta (o chão, a Armilar, os blocos vizinhos), em degraus presos à grade de 1/16, pela passada de tela da `wa-efeitos`: em `AFTER_LEVEL` sem pacote, e logo depois do buffer tardio com pacote.

**Arquivos:**
- Criar: `B/client/fx/FxLights.java`, `FxLightPass.java`; `B/client/resonator/render/ArmilarLights.java`
- Criar: `src/main/resources/assets/wirelessautomate/shaders/core/fx_light.json`, `fx_light.vsh`, `fx_light.fsh`
- Mudar: `B/client/fx/FxShaders.java` (o quarto shader), `FxStages.java` (os ganchos), `B/client/resonator/render/ArmilarScene.java` (anota as luzes)
- Testes (novos): `T/client/fx/FxLightsTest.java`, `T/client/resonator/render/ArmilarLightsTest.java`

**Interfaces:**
- **Consome:** `FxContext`, `FxDepth`, `FxStages` (Tarefa 3); `ArmilarPhase`, `ArmilarFlow` (Tarefas 5 e 8); as posições da Porta (`ArmilarSpec`, Tarefa 9).
- **Produz:**
  - `FxLights.add(double wx, double wy, double wz, float r, float g, float b, float k, float radiusBlocks)` (a posição no mundo), `int count()`, `void clear()`; `static double step(double d, double radius)` (a queda em 5 degraus, pura); `static double ceilDivisor(double top)` (o divisor do teto suave, puro); `FxLights.fill(double camX, double camY, double camZ, float[] pos, float[] col)` copia as luzes mais próximas da câmera (até 32) para os arrays do shader e devolve quantas.
  - `ArmilarLights.compute(double t, double a, double flowK, ArmilarPhase e, double[][] out)`: as três luzes (`{x, y, z (px locais), r, g, b, k, raio em px}`), o `luzes()` do filme.
  - `FxLightPass.apply(FxContext ctx)`: roda a passada com o que `FxLights` juntou.

- [ ] **Passo 1: os testes (falham primeiro).**
  - `ArmilarLightsTest`, com o ouro do `luzes(t, {a: 1, u: 1})` (a `k` do sol, do clarão do horizonte e da Porta; as posições; os raios 64, 26 e 34 px). Para cada `t` da tabela, monte a fase com `ArmilarPhase.set(t, 1)` e `flowK = 1` e compare com tolerância 1e-6:

    | t | k do sol | k do horizonte | k da Porta |
    | --- | --- | --- | --- |
    | 11 | 0,726742041 | 0 | 0 |
    | 10,5 | 0,62531445 | 0 | 0 |
    | 11,1 | 1,08754258 | 0 | 0 |
    | 11,2 | 1,2013577 | 1,4 | 0 |
    | 11,62 | 1,2371649 | 0 | 1,2544 |
    | 12 | 1,24998757 | 0 | 1,6 |
    | 13 | 0,933481804 | 0 | 0,8 |

    Com `a = 0,5`, `u = 0` (`alive = 0`), `t = 3`: sol 0,31722556, os outros dois 0. Com `flowK = 0,5` a luz da Porta cai à metade (a vazão mexe só na Porta), e com `flowK = 0` ela some (a conjunção acontece no sol, sem arco nem impacto).
  - `FxLightsTest`: `step(d, r)`: `step(0, 4) = 1`; `step(4, 4) = 0` e `step(5, 4) = 0` (fora do raio); o valor cai em degraus de 0,2 (`floor(f² × 5 + 0,35) / 5`, com `f = 1 − d/r`): `step(0,5 r; r)` = `floor(0,25 × 5 + 0,35)/5 = 0,2`; é monótono decrescente em `d`; `ceilDivisor(0,5) = 1`, `ceilDivisor(1) = 1`, `ceilDivisor(2) = 2,2` e é crescente; `fill` com 40 luzes devolve 32 e são as 32 mais próximas da câmera.

- [ ] **Passo 2: `FxLights` e `ArmilarLights`.**
  - `FxLights`: arrays estáticos de capacidade 64 (nada alocado por quadro); `fill` escolhe as 32 mais próximas por seleção simples (sem `sort` de objetos).
  - A queda e o teto, do `pipeline.md`, seção 6 ("quando a soma passa de 1, divide por `1 + (topo − 1) × 1,2`", com `topo` o maior canal da soma):

```java
    /** A queda em degraus: {@code f = 1 − d/r}; {@code floor(f² × 5 + 0,35) / 5}; o último degrau já é 0. */
    static double step(double d, double radius) {
        if (d >= radius) {
            return 0;
        }
        double f = 1 - d / radius;
        return Math.floor(f * f * 5 + 0.35) / 5;
    }

    /** O divisor do teto suave: 1 até a soma chegar a 1 e {@code 1 + (topo − 1) × 1,2} depois. */
    static double ceilDivisor(double top) {
        return top > 1 ? 1 + (top - 1) * 1.2 : 1;
    }
```

  - `ArmilarLights.compute` (do `luzes` do filme):

```java
    /** {x, y, z (px), r, g, b, k, raio px} do sol, do clarão do horizonte e da Porta. */
    static void compute(double t, double a, double flowK, ArmilarPhase e, double[][] out) {
        double sun = 0.75 * a * (0.92 + 0.08 * Math.sin(t * 1.7)) * (1 - 0.3 * e.prep) + 0.5 * e.raio;
        set(out[0], 0, ArmilarSpec.SOL_Y, 0, 1, 0.55, 0.36, sun, 64);
        set(out[1], 32.5, 47.5, 0, 1, 0.8, 0.35, 1.4 * e.clarao, 26);
        set(out[2], ArmilarSpec.PORTA_LUZ[0], ArmilarSpec.PORTA_LUZ[1], ArmilarSpec.PORTA_LUZ[2], 1, 0.75, 0.3,
                1.6 * e.porta * flowK, 34);
    }
```

    com `flowK = ArmilarFlow.strength(vazão)`, e `e.prep` o da fase (a pressão do buffer cheio não escurece a luz: o filme não faz).
  - `ArmilarScene.draw`: depois dos efeitos, chama `ArmilarLights.compute` e `FxLights.add` das três luzes (px para o mundo por `frame.toWorld`, raio `/ 16`). A luz some quando `k <= 0,001`.

- [ ] **Passo 3: o shader da passada.** `fx_light.{json,vsh,fsh}` (`DefaultVertexFormat.POSITION`; um triângulo que cobre a tela). **(Conferir)** como o vanilla declara samplers e uniforms em array no `.json` (`"uniforms": [{"name": "LightPos", "type": "float", "count": 128, "values": [0.0, ...]}]`).
  - **Amostradores:** `DiffuseSampler` (a cópia da cor) e `DepthSampler` (a profundidade; **sempre a copiada pelo `FxDepth`**, também sem pacote, para não ler a textura em que o próprio alvo está desenhando).
  - **Uniforms:** `InvViewProj` (a inversa de `projeção × modelview` do quadro), `CamFrac` (a parte fracionária da posição da câmera), `LightCount` (int) e os arrays `LightPos[32]` (xyz relativo à câmera, em blocos, w = raio em blocos) e `LightCol[32]` (rgb, k).
  - **Fragment** (do `pipeline.md` e do SPMovesets `fx_light.fsh`, que se estudou e não se copia; a implementação é nossa):

```glsl
#version 150

uniform sampler2D DiffuseSampler;
uniform sampler2D DepthSampler;
uniform mat4 InvViewProj;
uniform vec3 CamFrac;
uniform int LightCount;
uniform vec4 LightPos[32];
uniform vec4 LightCol[32];

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec4 scene = texture(DiffuseSampler, texCoord);
    float depth = texture(DepthSampler, texCoord).r;
    if (depth >= 1.0) {
        fragColor = scene;
        return;
    }
    vec4 clip = InvViewProj * vec4(texCoord * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    vec3 p = clip.xyz / max(abs(clip.w), 1e-6) * sign(clip.w);
    vec3 n = cross(dFdx(p), dFdy(p));
    n = dot(n, n) > 1e-12 ? normalize(n) : vec3(0.0, 1.0, 0.0);
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
    fragColor = vec4(scene.rgb + light * (0.2 + 2.0 * scene.rgb), scene.a);
}
```

    A face de costas recebe 40% (`mix(0,4; 1; face)`), o céu (profundidade 1) e a mão não mudam. **A mistura do filme** (`ad += luz × (0,12 + 1,3 × cena)`, em `shared.js`, `FS`) **é diferente da da skill** (`luz × (0,2 + 2 × cena)`); o plano usa a da skill e deixa os dois números (`0.2` e `2.0`) como constantes do shader para ajustar na prancha. Placas Intel e Apple: todas as divisões estão protegidas e o `highp` do fragment vem do `#version 150`.
  - `FxShaders` registra o quarto shader (`fx_light`, formato `POSITION`) e expõe `light()`... **cuidado com o nome**: o getter de `fx_add_premul` já é `light()`. Chame o novo `lightPass()`.

- [ ] **Passo 4: a passada (`FxLightPass`).** `apply(ctx)`:
  1. se `FxLights.count() == 0` ou `FxShaders.lightPass() == null`, sai;
  2. `FxDepth.save()` (a profundidade do alvo principal, ou, no desenho tardio com pacote, a já salva; sem pacote o quadro todo já está no alvo);
  3. copia a cor do alvo principal para um `TextureTarget` próprio (`FxScreenCopy`, do tamanho do alvo, recriado se ele mudar), por blit de cor;
  4. `ShaderInstance s = FxShaders.lightPass()`; `s.setSampler("DiffuseSampler", copia.getColorTextureId())`, `s.setSampler("DepthSampler", FxDepth.depthTextureId())`; `s.safeGetUniform("InvViewProj").set(inversa)` (com `projeção × modelview` do `FxContext`), `CamFrac` (`camX − floor(camX)`...), `LightCount` e os arrays preenchidos por `FxLights.fill`; **(conferir)** `Uniform.set(float[])` para arrays;
  5. liga o alvo principal para escrita, desliga profundidade e blend (a passada **substitui** a cor: `scene + luz`), desenha o triângulo e restaura o estado.
  **(Conferir)** os nomes: `RenderTarget.getColorTextureId()` e o da profundidade (`getDepthTextureId()`; se não existir na 1.21.1, o `FxDepth` guarda o `frameBufferId` e a textura de profundidade pelo `TextureTarget` que ele cria).

- [ ] **Passo 5: os ganchos (`FxStages`).**
  - **Sem pacote:** no estágio `AFTER_LEVEL`, `FxLightPass.apply(ctx)` e `FxLights.clear()`.
  - **Com pacote:** no gancho tardio (o do passo 1 da Tarefa 3), logo depois do `batch.flush()`: `FxLightPass.apply(ctx)` e `FxLights.clear()`. **A passada de `AFTER_LEVEL` fica desligada com pacote**, para a luz não sair dobrada (`pipeline.md`, seção 6).
  - Os dois só rodam com a fila de efeitos tendo existido no quadro (o `FxLights.count() > 0` faz isso; as luzes entram quando o job desenha).

- [ ] **Passo 6: verificar.** `./gradlew build` verde; as três varreduras.

- [ ] **Passo 7: Rodada de jogo (avise o dono).** O que vai ser visto: no `E2eArmilarRenderSteps`, uma captura da Armilar à noite (`/time set night` no servidor do e2e) ou perto de uma parede de pedra em volta, com a luz do sol da Armilar acendendo o chão e a pedra em degraus de 1/16, e a mesma na fase do estouro (a luz do clarão do horizonte) e do impacto (a da Porta); sem pacote e com Complementary. Confira que **a luz não sai dobrada** com pacote (um quadro claro demais) e que **o céu e a mão não mudam**. Mostre e **pare**.

- [ ] **Passo 8: commit.** `git commit -m "Efeitos: a luz no mundo pela passada de tela, com as três luzes da Armilar"`.

---

### Tarefa 11: o e2e nos três ambientes, o desempenho, os docs e a prancha

Entrega: as capturas da suíte `ressonador` trocadas pelas da Armilar (sem pacote, Complementary e BSL), a suíte `ressonador-desempenho` com 0, 1 e 10 Armilares, os docs atualizados, a bateria completa verde e a prancha para o dono. **O merge espera o "aprovado" dele.**

**Arquivos:**
- Criar/mudar: `B/client/dev/E2eArmilarRenderSteps.java` (as capturas da Armilar), `E2eResonatorSteps.java` (o `chamberSteps` sai e chama o novo; o `perf` com 0, 1 e 10), `E2eResonatorFlowSteps.java`, `E2eResonatorScreenSteps.java` (linha 149), `E2eSuites.java` (a suíte `armilar-render` some; a `ressonador-desempenho` com a descrição nova), `E2eShowcaseResonator.java` (só se referenciar o que mudou)
- Mudar: `scripts/e2e.sh` (se a Tarefa 3 não fechou), `docs/e2e.md`, `docs/mapa/ressonador.md` (linhas 17 a 19, a seção `client/resonator/render/`), `docs/mapa/efeitos.md` (novo), `CLAUDE.md` (o índice do mapa), `docs/progresso/historico/2026-11.md` e o que a skill `wa-docs` pedir
- Criar: `docs/preview/armilar-etapa2-prancha.png` (a prancha; saída de `lado_a_lado.py`)

**Interfaces:**
- **Consome:** tudo das Tarefas 1 a 10; `ArmilarRenderer.freezeSecondsForTests` e `forceBandForTests` (Tarefa 7); `E2eResonatorSteps.placeArmilar(level, origin, tier)`, `camera(...)`, `capture(...)` (já existem).
- **Produz:** a suíte `ressonador` com as capturas `armilar-*` abaixo; `ressonador-desempenho` com a medida em ms/quadro.

- [ ] **Passo 1: as capturas da suíte `ressonador`.** Em `E2eArmilarRenderSteps.chamberSteps(list, playerId, origin)`, no lugar do `chamberSteps` antigo, com a câmera do filme da formada (a mesma da prancha): `FILM_VIEW`: alvo `(0; 30; 0)` px sobre o centro do chão da Armilar, `yaw = 38°`, `pitch = 24°`, distância 215 px, FOV 30° (o `fov` das opções vai a 30 só durante as capturas e volta no fim; **sem tocar em `options.txt`**), o jogador no ponto `centro + (215 cos 24° sin 38°; 30 + 215 sin 24°; 215 cos 24° cos 38°) / 16` blocos e olhando para o alvo (`yaw = atan2(−dx, dz)` em graus; `pitch = −atan2(dy, hypot(dx, dz))` em graus, com `d` = alvo − olho; **conferir** o sinal contra uma captura). Capturas, cada uma com um nome `armilar-<cena>`:

  | Captura | O que arma | Fase (`ArmilarRenderer.freezeSecondsForTests`) |
  | --- | --- | --- |
  | `armilar-fora` | dial a 96,7 (faixa baixa), sem conjunção | relógio real; espere 60 ticks para a pose assentar |
  | `armilar-sintonia` | dial a 98,0 (faixa 7), vazão alta, sem conjunção | `TC + 2,6` (fora da janela da conjunção, `d = 2,6 s`: só o sol e os filamentos) |
  | `armilar-conj-preparacao`, `-estouro`, `-arco`, `-impacto`, `-dissolucao` | em sintonia, vazão 7 (`forceBandForTests = 7`) | `TC + d` com `d` = −0,3; 0,1; 0,35; 0,9; 2,0 |
  | `armilar-conj-vazao0` | em sintonia, `forceBandForTests = 0` | `TC + 0,35` (o arco não sai) |
  | `armilar-cheio` | o `full` (o passo "buffer cheio" que já existe no `E2eResonatorFlowSteps`) | relógio real |
  | `armilar-cristais` | o Ultimate com Sinal, Lapidado, Afinado, Opaco e Rachado nos 8 nós (os mesmos do passo `ressonador-cristais-estados` de hoje) | `TC + 2,6` (sem conjunção) |
  | `armilar-recarga`, `-recarga-fim` | o Opaco recarregando, no meio e no fim (o clarão) | relógio real |
  | `armilar-montagem` | o instante `a ≈ 0,5` da montagem (`formedAt` = agora − 0,75 s) | relógio real |

  Apague as capturas e os passos que eram da câmara antiga (`ressonador-camara*`, `ressonador-tentaculos*`, `ressonador-raio-perto*`, `ressonador-camara-cheio`, `-fluxo*`, `-recarga*`, `-cristais*`, de `E2eResonatorFlowSteps` e `E2eResonatorSteps.chamberSteps` e o `closeUp`), **o que a nova cobre, não mais**; a captura `ressonador-camara-rachado` (`E2eResonatorScreenSteps:149`) vira `armilar-rachado`. Cada passo prende o relógio (`freezeSecondsForTests`), espera 40 ticks (o `motion` trava na conjunção) e captura; no fim, solta o relógio (`NaN`). O relógio parado fica fora do `restoreMeasure`? **Não:** `restoreMeasure` o solta também (idempotente, como o `forceBandForTests`).

- [ ] **Passo 2: o ambiente por rodada (`--shaders`).** Se a Tarefa 3 não fechou o `scripts/e2e.sh --shaders`, feche aqui: `--shaders nenhum|complementary|bsl`; `nenhum` roda como hoje; os outros acrescentam `-Pshaders` ao `./gradlew runClient` (a linha do `timeout ... ./gradlew runClient`) e escrevem `run/config/iris.properties` antes (`enableShaders=true` e `shaderPack=ComplementaryReimagined_r5.9.3.zip` ou `BSL_v10.1.8.zip`; **conferir** os nomes das chaves abrindo o jogo uma vez com `-Pshaders` e lendo o arquivo que o Iris grava; nunca `run/options.txt`). O `--out` por ambiente: `run/e2e-armilar-nenhum`, `run/e2e-armilar-complementary`, `run/e2e-armilar-bsl`. Atualize o cabeçalho de comentário do `e2e.sh` com a opção.

- [ ] **Passo 3: o desempenho (`ressonador-desempenho`).** O `E2eResonatorSteps.perf` e o `frameTime` ficam com **três** situações, medidas com o mesmo `frameTimeStep` (120 quadros, sem vsync, dentro da mesma rodada): `armilar 0` (nenhuma Armilar à vista: o custo tem de ser o de um quadro sem a Armilar), `armilar 1` (a de hoje, de frente) e `armilar 10` (10 Armilares montadas num anel, todas no frustum, com `placeArmilar` em 10 origens a 14 blocos uma da outra). A linha de saída `label: N ms/quadro` vai ao `result.txt`. A suíte **mede, não afirma**: a descrição em `E2eSuites` passa a "tempo de quadro da Armilar com 0, 1 e 10 à vista (mede, não afirma)". Rode no cliente sem pacote e registre os três números no relatório.

- [ ] **Passo 4: os testes e a bateria.**
  - `./gradlew build` verde.
  - A bateria completa, **uma vez, aqui** (nada do servidor mudou nesta etapa, mas é a última tarefa e o dono pediu): `./gradlew build runGameTestServer runGameTestServerChemicals runGameTestServerSource runGameTestServerAllthemodium` verde. Os GameTests do Ressonador montam a Armilar (etapa 1) e não tocam no render.

- [ ] **Passo 5: Rodada de jogo, os três ambientes (avise o dono, uma rodada de cada, em série, nunca juntas).**
  1. `./scripts/e2e.sh --only ressonador --capture armilar --shaders nenhum --out run/e2e-armilar-nenhum`
  2. `./scripts/e2e.sh --only ressonador --capture armilar --shaders complementary --out run/e2e-armilar-complementary`
  3. `./scripts/e2e.sh --only ressonador --capture armilar --shaders bsl --out run/e2e-armilar-bsl`
  4. `./scripts/e2e.sh --only ressonador-desempenho --out run/e2e-armilar-perf`
  Em cada uma: `result.txt` com `OK`; `grep -c "Couldn't compile" <out>/client.log` é 0; nada de exceção do `FxStages`, `ArmilarRenderer` ou `FxLightPass`. Para a prova da skill: o mesmo quadro nos três ambientes tem a mesma cor e a mesma forma nos efeitos, a oclusão certa atrás de blocos e **nada na passada de sombra** (a sombra da Armilar no chão do BSL e do Complementary não carrega o sol nem os filamentos). Anote os que falharem e **pare** em vez de ajustar no olho.

- [ ] **Passo 6: a prancha para o dono.**
  - Os quadros do filme, na mesma fase: com o navegador do Claude (ou o que houver), abra `file:///C:/Users/matheuss/Desktop/Sistemas/Wireless-Automate/ressonador-conceitos/filme/formado.html?t=<TC + d>&u=<0 ou 1>&yaw=38&pitch=24&dist=215&ty=30` para cada captura da tabela do passo 1 (`t = 11 + d`; `u = 1` nas de sintonia, `u = 0` em `armilar-fora`), 1920 × 1080, e salve em `run/prancha/ref/<nome>.png`; **se não houver como abrir o navegador, peça ao dono os quadros** (não invente o filme).
  - `python -I .claude/skills/wa-efeitos/scripts/lado_a_lado.py run/prancha/ref run/e2e-armilar-nenhum docs/preview/armilar-etapa2-prancha.png armilar-fora:armilar-fora armilar-sintonia:armilar-sintonia armilar-conj-preparacao:armilar-conj-preparacao armilar-conj-estouro:armilar-conj-estouro armilar-conj-arco:armilar-conj-arco armilar-conj-impacto:armilar-conj-impacto armilar-conj-dissolucao:armilar-conj-dissolucao` (confira o uso exato do script: ele aceita `REFERENCIAS_DIR` e pares `nome:captura`). Gere uma segunda prancha dos três ambientes na fase do arco (`armilar-etapa2-ambientes.png`: sem pacote, Complementary e BSL lado a lado).
  - Escreva, para cada fase, **a lista de diferenças por camada** (tinta, luz, borda, partículas, chão, luz no mundo, tempo): a skill pede que "não sobre diferença grande".
  - Mostre as duas pranchas ao dono e **pare**.

- [ ] **Passo 7: os docs.**
  - `docs/mapa/ressonador.md`: troque as linhas 17 a 19 (a seção `client/resonator/render/` inteira, hoje sobre plasma, Lissajous e conduto) por uma descrição da Armilar: o `ArmilarRenderer` (BER, 64 blocos, caixa 5 × 5 × 5 mais 1), a `ArmilarScene` (um por Núcleo, varrida a cada 200 ticks sem desenho), as peças puras (`ArmilarSpec`, `QuadMesh`, `ArmilarMeshes`, `ArmilarFrame`, `Ease`, `ArmilarPose`, `ArmilarPhase`, `ArmilarMotion` com o laço de fase, `ArmilarNodes`, `ArmilarAssembly`, `ArmilarFlow`, `CrystalLook`, `CrystalPlan`, `ArmilarRecharge`, `FlashTracker`, `ArmilarGrains`), o `ArmilarSolids` e os efeitos (`ArmilarSun`, `ArmilarFilaments`, `ArmilarPointer`, `ArmilarBeam`, `ArmilarArc`, `ArmilarImpact`, `ArmilarLights`). Diga que os `PlasmaCore`, `RibbonQuad` e o resto agora são do Reparador (`client/repairer/render/`).
  - `docs/mapa/efeitos.md` (novo): o pacote `client/fx/` (shaders `fx`, `fx_add`, `fx_add_premul` e `fx_light`; `FxSheet` e as folhas; `FxTypes`; o desenho sem pacote e o tardio com Iris, o `FxDepth`, os ganchos e a decisão do gancho tardio da Tarefa 3; a passada de luz; a pré-carga; `ShaderPackCheck`), e a flag `-Pshaders`.
  - `docs/e2e.md`: a linha da `ressonador` (as capturas `armilar-*`), a `ressonador-desempenho` (0, 1 e 10), a opção `--shaders` e a flag `-Pshaders`, os três ambientes.
  - `CLAUDE.md`: no índice do mapa, a linha "Ressonador de Sinal" ganha "e a Armilar renderizada" e uma linha nova "Efeitos de render (Iris, desenho tardio, luz no mundo) | [efeitos](docs/mapa/efeitos.md) | `client/fx/`".
  - `docs/progresso/historico/2026-11.md`: uma linha por tarefa da etapa, como as outras do dia. Siga a skill `wa-docs` para o resto (`README` e a spec, se pedir).
  - Rode `python -I .claude/skills/wa-comentarios/scripts/varrer.py` sobre o que mudou.

- [ ] **Passo 8: o fechamento.**
  - `git status`: só o que é desta tarefa (as capturas do `run/` ficam fora do commit; a prancha em `docs/preview/` entra).
  - `git commit -m "Armilar etapa 2: e2e nos três ambientes, desempenho, docs e a prancha"`.
  - **Pare aqui.** O relatório final traz: os números de tempo de quadro (0, 1 e 10 Armilares), o resultado das três rodadas, a bateria, as duas pranchas e a lista de diferenças por camada. O merge do ramo `armilar` **espera o "aprovado" do dono**. Depois do aprovado, e só então (fora desta etapa de código): o merge e refazer as fotos `s19` e `s20` e o GIF da vitrine do CurseForge (`E2eShowcaseResonator` e `scripts/curseforge/gerar_gif.py`).

---

## Autorrevisão

**Cobertura da spec (cada requisito, a tarefa que o cumpre):**

| Requisito da spec | Tarefa |
| --- | --- |
| Referencial (+x leste, +z sul, giro pela frente com `ResonatorLayout.toWorld`) | 4 (`ArmilarFrame`), 7 (a frente do `FACING`) |
| Três órbitas, 24/32/40 segmentos, dois pinos, nós 2 + 3 + 3 com engaste (base e 4 garras) | 4 |
| Cristais (raio 1,5; prisma ±1,5; pontas 1,9; centro a 5 px; giram com a órbita) | 4 (malha), 7 (desenho no nó) |
| Horizonte (30 a 35, y 46 a 49) e os quatro marcos, parados | 4 (malha), 7 (desenho) |
| Ponteiro (caça a estação, para em sintonia) | 8 (`ArmilarPointer`) |
| Sol (tinta de duas fumaças, brilho, disco de 6 quadros a 8 fps, 6 chamas a 10 fps, estrela, aro, 12 brasas) | 8 |
| Filamentos (tinta de dois trilhos por baixo, fluxo por cima) | 8 |
| Conjunção (preparação, estouro até (32,5; 47,5; 0), arco bezier até a Porta com raio de 8 quadros a 12 fps e fluxo, impacto, dissolução 1,6 a 2,4 s) | 5 (fase), 9 |
| Período `P = 2π / 1,2` e fase do `gameTime + partialTick` | 4 (`tau`), 5, 7 (relógio) |
| Luz no mundo (sol 64 px, horizonte 26 px, Porta 34 px) pela passada de tela | 10 |
| Estados: sintonia (`u`, suavizado a 1,6/s, `pose`, assentamento por `tunedAt`) | 5, 7 |
| Estados: eficiência baixa (0,55 + 0,45 u; sem filamento com `band = 0`) | 5 (`sunMul`), 8 |
| Estados: conjunção só em sintonia | 8 (`phase.set(t, tuned ? u : 0)`) |
| Estados: vazão pela Porta (`k = 0,45 + 0,55 v/7`, vazão 0 sem arco nem impacto) | 5 (`ArmilarFlow`), 9 |
| Estados: buffer cheio (parado em 60%, 0,5 Hz, brasas perto) | 5 (`fullPrep`), 8, 9 |
| Estados: câmaras nos nós de dentro para fora; Básico 1, Ultimate 8 | 5 (`ArmilarNodes`), 7 |
| Estados: Sinal e Lapidado, Afinado (faixa na cor do tier), Opaco (8°), Rachado (35%) | 2 (texturas), 4 (garras), 6, 7, 8 |
| Estados: recarga (acende de baixo para cima em degraus de 1 texel, grãos, clarão de 0,7 s) | 6, 7, 9 |
| Estados: montagem (`a` de 0 a 1 em 2,5 s) | 5, 7 |
| Peças sólidas pelo BER, tipo de entidade do vanilla, também com pacote e na sombra | 7 |
| Efeitos: core shaders `fx`, `fx_add`, `fx_add_premul`, `RenderType`s por textura | 1 |
| Efeitos: sprites, decalques, fitas Catmull-Rom a cada 1/16, quadro pela fase | 3, 8, 9 |
| Sem pacote: o BER anota, o desenho em `AFTER_TRANSLUCENT_BLOCKS` | 3, 8 |
| Com Iris: detecção por reflexão, nada na sombra, buffer tardio, profundidade salva, desenho depois da composição | 1, 3 |
| Luz no mundo: `AFTER_LEVEL` sem pacote, logo depois do tardio com pacote | 10 |
| Pré-carga das texturas, 2 por tick | 1, 7 |
| Custo (nada por tick no servidor, custo zero sem Armilar à vista, 48 e 64 blocos, sem alocação por quadro) | 3, 7, 8, 11 (medida) |
| Texturas pelo gerador (kit do filme, latão, grafite, cristais, horizonte, ponteiro) | 2 |
| O que sai (classes antigas, testes antigos, modos 0 e 2 do shader) | 7 |
| O que fica e vai para `client/repairer/render/`; `ShaderPackCheck` para `client/fx/` | 1 (`ShaderPackCheck`), 7 |
| Textos e docs (`mapa/ressonador.md`, `e2e.md`, `CLAUDE.md`) | 11 |
| Ambiente com shaderpack (`-Pshaders`, pacotes, escolha pelo `iris.properties`) | 1, 3 (`--shaders`) |
| Capturas da suíte `ressonador` nos três ambientes; `ressonador-desempenho` 0, 1 e 10 | 11 |
| Prancha lado a lado e a espera do aprovado | 11 |
| Um commit por tarefa | todas |
| Vitrine do CurseForge refeita depois do aprovado | 11 (depois do aprovado, fora da etapa de código) |

**Placeholders:** nenhum "TBD". Onde há "(conferir)", o plano diz o que conferir e onde (jar do NeoForge, jar do Iris, classe do projeto). As propostas sem número na spec (a amplitude do pulso do buffer cheio, o tamanho do clarão do fim da recarga, o sentido da inclinação do Opaco, a elevação de 0,6 px do Opaco, a mistura da luz) estão em constantes únicas e listadas nas decisões abaixo.

**Nomes entre tarefas:** `ArmilarSpec` (4), `QuadMesh`, `ArmilarMeshes.{ring,pins,crystal,crystalRows,horizon,markers}`, `ArmilarFrame.{set,yaw,toWorld,dirToWorld}` (4); `ArmilarMotion.{advance,u,spin}`, `ArmilarPhase.{set,fullPrep}` e os campos `d, dis, prep, cresce, raio, clarao, onda, cabeca, arco, porta, surge, onda2, clarao2`, `ArmilarNodes.{orbit,slot,angle,popIndex,center}`, `ArmilarAssembly.{a,ring,crystal,ignition}`, `ArmilarFlow.{strength,sunMul}` (5); `CrystalLook`, `CrystalPlan.{litRows,rowTex,rowTinted}`, `ArmilarRecharge.{isRecharging,firstRecharging,progress,fall}`, `FlashTracker.flash`, `ArmilarGrains` (6); `FxSheet.{frameAt,frameByFraction}`, `FxSheets.*`, `FxTypes.of`, `FxDraw.{sprite,decal,ribbon}`, `FxRibbon.{catmullRom,bezier}` (1 e 3) usados em 8 e 9 com as mesmas assinaturas.

---

## Decisões (arbitradas pelo controlador em 16/11/2026; a direção visual é delegada)

Todas as propostas abaixo ficam como o plano diz, com duas mudanças: a montagem leva 2,5 s, como no filme, e o Opaco inclina 8° sem subir. Só a 11 (as texturas novas dos cristais) é mostrada ao dono antes de seguir.

1. **Marcos: coral ou grafite.** A spec (seção "Texturas") diz "o grafite dos pinos e dos marcos"; o conceito (`gerar_conceitos.armilar()`, os `marco_NN`) os pinta de coral, e o `armilar_formado.py` diz que o horizonte e os marcos ficam "iguais ao conceito". O plano segue o conceito (coral). A troca é a constante `ArmilarSolids.MARKER_TEXTURE`.
2. **Ponteiro: sólido ou luz.** A spec o lista entre as peças sólidas; o filme o desenha como fita de luz (`R.fita('luz', 'miolo', ...)`). O plano segue o filme.
3. **Montagem: 2,5 s.** A cena do filme (`scene-armilar.js`) usa `a = seg(t, 3,3; 5,8)`. Fica o filme (`ArmilarAssembly.FORM_SECONDS = 2.5`); a spec foi corrigida.
4. **Opaco a 8°.** A 20°, as garras entram no cristal; subir o cristal o soltaria da base. Fica o maior ângulo que encosta sem atravessar (uns 8°, `OPACO_TILT_DEG = 8`, `OPACO_LIFT_PX = 0`); a spec foi corrigida.
5. **As portas no jogo ficam 2 px mais para dentro que no conceito.** O `armilar_formado.py` entra as caixas da Porta de Energia (−2 em x), da Porta de Cristais (+2) e do Núcleo (−2 em z) num nicho. A spec e o filme da formada usam os números do conceito (arco em 42,8; impacto em 42,4; ponteiro em z = 41). O plano aplica o deslocamento (`PORTA_DESLOC_X`, `NUCLEO_DESLOC_Z`, ambos −2) para o arco cair na Porta que existe; zere as constantes se o dono preferir o número do filme. Os decalques do chão do impacto (x = 47) caem fora da Laje (que acaba em 40 px): vão para o terreno em volta.
6. **Os grãos de redstone.** A spec diz "um a cada 33 ticks"; o código de hoje solta um a cada ~10 ticks, no máximo 4 vivos, cada um voando 33 ticks. O plano porta o código (a spec também manda seguir a lógica de hoje).
7. **Quantidade de quads.** A spec estima "uns 700 quads por quadro por Armilar". Contado a partir do filme: anéis e engastes 624, pinos 36, horizonte e marcos 120, cristais 18 cada (até 144) mais os efeitos (uns 400 a 1200): cerca de 924 quads sólidos com os 8 cristais. O custo é medido na Tarefa 11.
8. **`fx_add` sem uso na Armilar.** A spec manda criar o shader aditivo sem pré-multiplicar; o filme usa só a luz pré-multiplicada (`fx_add_premul`). O `fx_add` sai pronto para o Reparador e fica sem chamada nesta etapa. Também não se geram o `aro_fino`, o `risco` de 8 × 2 e o `fluxo1` do filme (a Armilar não os usa).
9. **A dissolução por trama Bayer exige um atributo por vértice** que a cópia do `rendertype_entity_translucent` da spec não tem. O plano o põe no UV1 do `NEW_ENTITY` (o overlay, que o `fx` não lê), em milésimos. É uma extensão da cópia; vale o aval.
10. **O gancho do desenho tardio.** O plano decide na Tarefa 3 entre `RenderGuiEvent.Pre` e um mixin de cliente no fim de `GameRenderer.renderLevel`, conforme o NeoForge poste ou não o evento com `hideGui` ligado (o e2e liga). Se for mixin, o repositório ganha um segundo arquivo de mixins, só de cliente.
11. **Texturas novas sem referência no filme:** Lapidado (com a faixa dourada), a faixa do Afinado, Opaco e Rachado. Ficam para o dono ver na Tarefa 2 antes de a Tarefa 7 usá-las.
12. **Tons fora da `PALETA`.** O kit do filme usa dois tons que a `PALETA` não tem (latão `#4F3B10` e coral `#4A1A12`) e alfa parcial (degradê), que a skill `wa-texturas` proíbe para blocos ("sem cor fora da paleta"; `validar` só aceita alfa 0 ou 255). O plano os mantém só nas folhas de efeito, num módulo à parte (`fx_texturas.py`, que não passa pelo `validar`); a skill `wa-efeitos` pede esse degradê.
13. **A mistura da luz no mundo.** O filme usa `luz × (0,12 + 1,3 × cena)`; a skill `wa-efeitos` manda `luz × (0,2 + 2 × cena)`. O plano usa a da skill, com os dois números em constantes do shader, para ajustar na prancha.
14. **O que ainda é proposta minha, sem número na spec nem no filme:** a amplitude do pulso do buffer cheio (±0,1 em torno de 60%), o tamanho e a curva do clarão do fim da recarga (meio lado 4 px, alfa `(1 − f)²`), o sentido da inclinação do Opaco (para fora do anel), a suavização da vazão (a de hoje, 0,12 por tick) e o laço de fase do giro (`LOCK` e `MAX_PULL` do `ArmilarMotion`; a fórmula fechada do filme dispara o anel depois de muito tempo em sintonia parcial).
15. **A skill cita `scripts/quadros.py` e `scripts/lado_a_lado.py`;** eles estão em `.claude/skills/wa-efeitos/scripts/`, não em `scripts/`.
