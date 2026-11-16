# Ressonador de Sinal: a Armilar, etapa 2 (o render)

Esta etapa troca a câmara antiga pelo render do conceito aprovado e completa a Armilar da spec `2026-11-16-ressonador-armilar-design.md` (seção "Etapas", item 2).

O visual já está decidido. A fonte é o filme "Ressonador: três estruturas", cena Armilar, e o código dele fica fora do repositório, em `C:\Users\matheuss\Desktop\Sistemas\Wireless-Automate\ressonador-conceitos\filme\`:
- `partes.js`: o objeto `W.armilar`, com `raios`, `nos`, `engaste`, `malhas`, `tau`, `pose`, `luzes`, `desenhar`, `fase` e `ponteiro`;
- `shared.js`: `anelFacetado`, `cristal`, `caixa`, `spline`, `spr`, `dec` e `fita`;
- `fx_atlas.py` e `fx.json`: o kit de texturas de efeito.

A versão de jogo porta esse código ao pé da letra: números, tempos, camadas e cores. Ela só acrescenta o que o jogo tem e o filme não mostra (seção "Estados do jogo"). Ver a memória "fiel ao conceito".

## Referencial

As unidades do filme são pixels (1/16 de bloco). A origem fica no centro do chão da Armilar: o bloco local (2,5; 0; 2,5), com a frente para o sul.
- +x vai para o leste, onde fica a Porta de Energia.
- +z vai para o sul, onde fica o Núcleo.

No mundo, o referencial gira pela frente da Armilar com a mesma conta da regra (`ResonatorLayout.toWorld` e `rotateX/rotateZ`). O centro e a frente vêm do `ChamberView.origin` e do `facing` do Núcleo.

## O que se vê (do filme)

### Peças sólidas
São malhas facetadas com textura em pixel art na densidade do mundo.

- **Três órbitas:**
  - raios de 13, 19,5 e 26 px, com 24, 32 e 40 segmentos;
  - em volta do sol, que fica a 47,5 px do chão;
  - cada uma com dois pinos de grafite;
  - nós 2 + 3 + 3, cada um com engaste: base e 4 garras, com os números de `engaste`.
- **Cristais:**
  - raio 1,5, prisma de ±1,5 e pontas de 1,9;
  - de pé no engaste, com o centro a 5 px;
  - giram com a órbita.
- **Horizonte e marcos:**
  - o horizonte é um anel de raio 30 a 35, entre y 46 e 49, com os quatro marcos;
  - ficam parados;
  - a geometria é a de `gerar_conceitos.armilar()`, com as mudanças de `armilar_formado.py`, ali onde não viraram bloco.
- **Marcos:** coral, como no conceito (`marco_NN` de `gerar_conceitos.armilar()`).
- **Portas e Núcleo no nicho:** no jogo, as Portas e o Núcleo ficam 2 px mais para dentro que no conceito (seção "Forma montada" da spec da etapa 1). Os pontos do arco, do impacto e do ponteiro levam o mesmo deslocamento.

O ponteiro do mostrador do Núcleo (`ponteiro`) é uma fita de luz, como no filme, e entra nos efeitos. Fora de sintonia ele caça a estação; em sintonia, para nela.

### Efeitos
Cada camada segue a ficha da `wa-efeitos`.

- **Sol:**
  - tinta: duas fumaças vinho;
  - luz: o brilho, o disco de 6 quadros a 8 fps e as 6 chamas da coroa a 10 fps;
  - a estrela e o aro tracejado;
  - 12 brasas em órbita.
- **Filamentos:** vão de cada cristal vivo ao sol. Por baixo, a tinta de dois trilhos; por cima, a luz do fluxo correndo.
- **Conjunção:** segue a linha do tempo de `fase(t, u)`:
  - preparação de −0,6 a 0 s;
  - estouro: o feixe vai do sol ao ponto do horizonte do lado da Porta de Energia (32,5; 47,5; 0);
  - arco: uma bezier até a Porta (42,8; 8,6; 0), com o raio de 8 quadros a 12 fps e o fluxo;
  - impacto na Porta: clarão, sigilo, choque no chão, 16 brasas e 4 fumaças;
  - dissolução de 1,6 a 2,4 s.
- **Período:** `P = 2π / 1,2` s. A fase sai do `gameTime + partialTick` do cliente, então todos veem a mesma coisa.

### Luz no mundo
As três luzes de `luzes()` iluminam o que está em volta, pela passada de tela da `wa-efeitos` (seção 6 do `pipeline.md`):
- o sol, com raio de 64 px;
- o clarão da conjunção no horizonte, com raio de 26 px;
- a Porta, com raio de 34 px.

## Estados do jogo

O filme mostra só "fora de sintonia" e "em sintonia, com a energia saindo". O resto foi decidido com o dono em 16/11/2026, só com peças do filme.

Tudo vem do `ChamberView`, que já chega pelo update tag. Não entra nada novo na rede.

| Estado do jogo | De onde vem | Como aparece |
| --- | --- | --- |
| Sintonia | `tuned`, `band` (0 a 7) | `u = tuned ? 1 : band / 7`, suavizado a 1,6/s. As órbitas tombam pela `pose` do filme na proporção de `1 − u` e deitam em `u = 1`. O assentamento (`sett`) começa quando `tuned` vira verdadeiro (`tunedAt`). |
| Eficiência baixa | `band` | Sol e filamentos multiplicados por `0,55 + 0,45 u`. Com `band = 0`, não há filamento. |
| Conjunção | `tuned` | Só acontece em sintonia. Fora dela as órbitas não se alinham. |
| Vazão pela Porta de Energia | `flows[0]` (0 a 7) | Com vazão 0, a conjunção acontece no sol (preparação, estouro e clarão no horizonte), sem arco nem impacto. Com vazão de 1 a 7, o arco e o impacto saem com força `k = 0,45 + 0,55 × vazão / 7`, que multiplica alfa, largura e número de brasas. |
| Buffer cheio | `full` | O sol fica sob pressão: o encolher e escurecer da preparação, parado em 60%, pulsando a 0,5 Hz, com as brasas puxadas para perto. |
| Câmaras | `chambers[]` | A câmara `i` ocupa o nó `i`, de dentro para fora: órbita 13 (nós 0 e 1), órbita 19,5 (2 a 4), órbita 26 (5 a 7). O Básico tem 1 cristal; o Ultimate tem 8. Nó sem câmara ou câmara vazia: engaste vazio. |
| Cristal de Sinal / Lapidado | código 1 / 2 | O cristal do filme, com a textura de cada um, com filamento e com o brilho. Em sintonia, a estrela também. |
| Afinado | `tiers[]` | O Lapidado com a faixa tingida na cor do tier (`GuiPaint.tierColor`). |
| Opaco | código 3 / 4 | Textura opaca, sem filamento nem brilho, inclinado 8° no engaste, para fora do anel. É o maior ângulo em que o cristal encosta na base sem atravessar as garras. |
| Rachado | código 5 / 6 | Textura com a trinca e filamento a 35%. |
| Recarga | `rechargeStart`, `rechargeTicks`, `rechargers` | O Opaco que recarrega acende de baixo para cima em degraus de 1 texel, pelo progresso. Grãos de redstone vão da Porta de Cristais ao cristal, como o código de hoje: no máximo 4 vivos, cada um voando 33 ticks. No fim, um clarão de 0,7 s no cristal. A escolha de quem recarrega segue a lógica de hoje (`isRecharging`, `rank`). |
| Montagem | `formedAt` | Vale o `a` do filme, de 0 a 1 em 2,5 s (`scene-armilar.js`): as órbitas crescem uma a uma, os cristais aparecem e o sol acende. Se a Armilar já estava montada, `a = 1`. |

## Como é desenhado (skill `wa-efeitos`)

### Peças sólidas
- Órbitas, engaste, pinos, cristais, horizonte e marcos vão pelo BER, no passe normal do mundo, com o tipo de entidade do vanilla e a nossa textura.
- O tipo é `entityCutoutNoCull`, ou `entitySolid` onde não há recorte.
- Isso vale também com shaderpack e também na passada de sombra, para o pacote iluminar as peças e fazer a sombra delas.

### Efeitos
Ficam num pacote genérico novo, `client/fx/`, que o Reparador também poderá usar.

- **Core shader `fx`:** uma cópia do `rendertype_entity_translucent` com os uniforms `AlphaCut` e `AirFog`. Tem duas variantes:
  - `fx_add`: aditiva, sem névoa;
  - `fx_add_premul`: pré-multiplicada.
- **`RenderType`s:** tinta, luz e luz pré-multiplicada, com um cache por textura.
- **Desenho:**
  - sprites de frente para a câmera;
  - decalques;
  - fitas por Catmull-Rom com amostra a cada 1/16 de bloco;
  - o quadro de cada animação sai da fase, nunca do `.mcmeta`.
- **Sem shaderpack:** o BER só anota o que desenhar no quadro. O desenho acontece no `RenderLevelStageEvent` em `AFTER_TRANSLUCENT_BLOCKS`, para não brigar com água e vidro.
- **Com shaderpack do Iris:**
  - o Iris é detectado por reflexão, no `ShaderPackCheck` levado para `client/fx/`, com `isRenderingShadowPass`;
  - nada de efeito na passada de sombra;
  - os efeitos vão para um buffer tardio;
  - a profundidade é salva no último estágio do mundo;
  - depois da composição do pacote, o buffer é desenhado por cima da imagem final, com a profundidade restaurada e as matrizes do quadro.
- **Luz no mundo:** uma passada de tela, em `AFTER_LEVEL` sem pacote. Com pacote, ela entra logo depois do buffer tardio.
- **Pré-carga:** as texturas de efeito carregam ao entrar no mundo, 2 por tick.

### Custo
- Nada por tick no servidor.
- No cliente:
  - sem Armilar montada à vista, custo zero;
  - os efeitos param a 48 blocos e as peças sólidas a 64 (a distância de vista do BER);
  - nada de alocação por quadro, com um buffer por tipo de desenho;
  - pela malha do filme, são uns 924 quads sólidos por Armilar com os 8 cristais, mais os efeitos.

## Texturas

Todas saem do gerador (`scripts/textures/`), em pixel art com filtro NEAREST:
- **O kit de efeitos do filme:** portado de `fx_atlas.py` para sprites próprios em `textures/fx/`. São as folhas de quadros (sol, chama, estrela, fumaça, raio, clarão, choque, sigilo), o brilho, o aro tracejado, as brasas, os trilhos com o `_rim`, o fluxo e o risco.
- **O latão facetado das órbitas e do engaste:** seção 3 × 2 texels, que repete a cada 16 texels pelo comprimento.
- **O grafite** dos pinos e o coral dos marcos.
- **Os cristais:** Sinal, Lapidado, faixa do Afinado (cinza, para tingir), Opaco e Rachado.
- **O horizonte e o ponteiro.**

## O que sai

- **Classes da câmara antiga** em `client/resonator/render/`:
  - `ResonatorRenderer` e `ChamberScene`;
  - `FlowConduit`, `ChamberRing`, `ChamberLens`, `ChamberParticles` e `ChamberSparks`;
  - `LissajousCurve`, `LissajousTrace` e `PlasmaTendrils`;
  - o desenho de `ChamberCrystals`. A lógica de recarga e de estado, que é útil, vai para a classe nova;
  - `ArcClock`, se o render novo não usar.
- **Testes antigos:** `ChamberRingTest`, `LissajousCurveTest` e, se o `ArcClock` sair, o `ArcClockTest`.
- **Shader:** os modos 0 (raio) e 2 (tentáculo) do `resonator_ribbon`.
- **O que fica, porque o Reparador usa, e vai para `client/repairer/render/`:**
  - `PlasmaCore`, `PlasmaShader` e `RibbonShader`;
  - `RibbonQuad` e `RibbonParam`;
  - `CrystalShader`, `CrystalGem`, `CrystalMesh` e `CrystalParam`;
  - a parte do `ChamberRenderTypes` que ele usa.
- **O `ShaderPackCheck`:** vai para `client/fx/`.
- **Os textos** que descrevem a câmara antiga, nos docs e no `docs/mapa/ressonador.md`.

## Ambiente de teste com shaderpack

- **Shaders no cliente de dev:** `./gradlew runClient -Pshaders` (e o e2e com a mesma flag) põe no cliente de dev, como `localRuntime`, `maven.modrinth:sodium:mc1.21.1-0.6.13-neoforge` e `maven.modrinth:iris:1.8.12+1.21.1-neoforge`. Eles não entram no jar do mod.
- **Pacotes:** já estão em `run/shaderpacks`:
  - `ComplementaryReimagined_r5.9.3.zip` (sha1 `838139b5…`);
  - `BSL_v10.1.8.zip` (sha1 `e89818c8…`).
- **Escolha do pacote:** o e2e escolhe o pacote pelo `run/config/iris.properties`. Ele nunca mexe em `run/options.txt`.

## Verificação e aprovação

- **Capturas da suíte `ressonador`:** as da câmara antiga são trocadas por estas:
  - fora de sintonia;
  - em sintonia;
  - as fases da conjunção, com o relógio parado por um gancho de teste: preparação, estouro, arco, impacto e dissolução;
  - cheio;
  - cristais nos estados;
  - recarga;
  - montagem.

  Cada uma sai sem pacote, com Complementary e com BSL.
- **Suíte `ressonador-desempenho`:** passa a medir o tempo de quadro com 0, 1 e 10 Armilares à vista.
- **Prancha:** as capturas lado a lado com o filme na mesma fase vão para o dono. O merge do ramo espera o "aprovado" dele.
- **Commits:** um por tarefa no ramo, como o dono autorizou em 16/11/2026.
- **Vitrine do CurseForge:** as fotos `s19` e `s20` e o GIF são refeitos depois do aprovado.
