# Pipeline dos efeitos (NeoForge 1.21.1)

O método tirado do estudo dos jars de Ronova: Ruler of Death 1.13.1 (NeoForge 1.21.1) e SPMovesets 2.0 (Forge 1.20.1), lidos como zip e com `javap`. A implementação aqui é nossa: os nomes das classes de lá aparecem só como referência de onde o método foi visto.

## 1. Os tipos de desenho

- **Core shader próprio = cópia do vanilla.** `rendertype_entity_translucent` (`.vsh`, `.fsh`, `.json`), com dois uniforms a mais:
  - `AlphaCut`: o corte de alfa do vanilla é 0,1, o que recorta degradês macios; use 0,004 ou o valor que o tipo pedir.
  - `AirFog`: a névoa do ar entra no máximo a 35%; a névoa de água, lava e cegueira, inteira.
  - O Ronova faz assim (`ronova:fx`).
- **Variantes:**
  - `fx_add`: sem névoa, porque a névoa misturaria cor no aditivo e o apagaria de preto.
  - `fx_add_premul`: a saída é `rgb × a` com alfa 1, para aditivo em textura com degradê de alfa sem halo escuro.
  - Registro em `RegisterShadersEvent`, com `DefaultVertexFormat.NEW_ENTITY`.
- **Os tipos** (`RenderType.create(nome, NEW_ENTITY, QUADS, 1536, false, ordenar, CompositeState)`), um cache `Map<ResourceLocation, RenderType>` por família:
  - **tinta:** blend alfa (`SRC_ALPHA, ONE_MINUS_SRC_ALPHA`), ordenação ligada, luz real, sem cull e sem escrita de profundidade;
  - **luz:** pré-multiplicado aditivo (`ONE, ONE`, com o shader premul) ou aditivo (`SRC_ALPHA, ONE`), sem ordenação, luz cheia (`LightTexture.FULL_BRIGHT`), sem cull e sem escrita de profundidade;
  - **sólido de efeito** (um corpo que deve esconder o que está atrás): escreve profundidade, sem blend;
  - **textura:** `TextureStateShard(tex, blur, false)`. Na pixel art, `blur = false`; na textura pintada e macia, `true`. O SPMovesets, de pixel art, não liga o blur em nenhuma das 221 texturas.
- **Vértice `NEW_ENTITY`:** posição, cor (o tingimento da textura cinza), uv, overlay `OverlayTexture.NO_OVERLAY`, luz e normal.
- **Ajuste do vértice a distância:** o Ronova embrulha o `VertexConsumer` num que pré-multiplica a cor e reforça o brilho de longe, para o efeito continuar legível.

## 2. A ordem das camadas e o que vai para o buffer tardio

1. **Peças sólidas que se movem** (anel, prato, cristal como objeto):
   - Malha lisa pelo tipo de desenho de entidade do vanilla (`entitySolid` ou `entityCutoutNoCull` com a nossa textura), no passe normal do mundo, **também com shaderpack**.
   - Assim o pacote as ilumina e faz a sombra delas, e elas tampam o que está atrás.
   - Nunca vão para o buffer tardio.
2. **Tinta:** alfa e ordenada, com a luz real, depois dos sólidos.
3. **Luz** (aditiva, com luz cheia), depois da tinta, e a **borda** `_rim` logo em seguida.
4. **Partículas.**
5. **Luz no mundo:** a passada de tela da seção 6, por último.

Com shaderpack, as camadas de 2 a 5 vão para o buffer tardio, nessa ordem. Sem pacote, entram no estágio depois dos blocos translúcidos (`AFTER_TRANSLUCENT_BLOCKS`), para não brigar com água e vidro. A passada de luz entra em `AFTER_LEVEL`.

## 3. A fita entre pontos (feixe, arco, filamento, corrente)

- **Caminho:**
  - Uma curva Catmull-Rom pelos pontos de controle.
  - As amostras são tiradas a cada 1/16 de bloco, para o pixel da textura caber num pixel do mundo.
  - Os pontos de controle mexem por ondulação lenta (até 2 Hz); nunca por sorteio.
- **Largura:**
  - Em cada amostra, `lado = normalize(cross(tangente, ponto - câmera)) × meiaLargura(f)`.
  - A tangente é a média dos dois vizinhos. As amostras vizinhas dividem o mesmo par de vértices, então a fita é contínua, sem emenda nem degrau.
- **Pontas:** a largura afina nos últimos 8% de cada lado, em degraus de pixel.
- **UV:**
  - Ao longo da fita: `u = comprimento × 16 / larguraDaTexturaEmPixels - fase × velocidade`. A textura repete pelo `fract` no shader ou pelo `wrap` da textura.
  - Na largura: `v` de 0 a 1.
- **Feixe reto:** pode usar dois quads cruzados no eixo, para não sumir quando visto de lado.
- **Camadas:** a fita da tinta é mais larga, por baixo; a da luz, por cima; a borda, quando houver, por cima de tudo.

## 4. Fase sincronizada

- O servidor grava no block entity o `gameTime` do início do efeito.
- Esse `gameTime` vai no update tag e só é mandado quando o efeito começa ou muda.
- O cliente calcula a fase e nada viaja por tick. Ver a seção "Tempo e suavidade".

## 5. Quando e onde desenhar

- **Sem shaderpack:**
  - Pelo BER, com `getRenderBoundingBox` cobrindo o efeito inteiro.
  - Para volumes grandes, por `RenderLevelStageEvent`, nos estágios `AFTER_PARTICLES`, `AFTER_TRANSLUCENT_BLOCKS` ou `AFTER_ENTITIES`, conforme a ordenação com água e vidro.
- **Com shaderpack do Iris (o método dos dois mods):**
  1. **Detectar por reflexão**, uma vez, com handles guardados:
     - Classe `net.irisshaders.iris.api.v0.IrisApi`: `getInstance()`, `isShaderPackInUse()` e `isRenderingShadowPass()`.
     - Sem Iris instalado, tudo dá `false`.
     - Se for virar dependência `compileOnly`, siga a skill `wa-mod-opcional`.
  2. **Passada de sombra:** sai cedo e não desenha nada (o Ronova: "no glow in the shaders' shadow pass").
  3. **Durante o mundo:**
     - Os desenhos vão para um `MultiBufferSource` próprio (o buffer tardio, com `ByteBufferBuilder` reaproveitado).
     - A profundidade do alvo principal é salva num `TextureTarget` nosso, por blit de profundidade, no último estágio do mundo.
     - O SPMovesets faz isso em `FxStage.saveDepth` e `blitDepth`, limpando os erros de GL antes.
  4. **Depois da composição do pacote** (em `RenderGuiEvent.Pre`, antes do HUD, ou num mixin no fim do `GameRenderer.renderLevel`):
     - Ligar o alvo principal e restaurar a profundidade salva.
     - Reaplicar a projeção e a modelview guardadas do quadro, ligar o teste de profundidade e desenhar o buffer tardio.
     - O Ronova: "with Iris shaders the effects are drawn over the frame" (`LevelRenderHooks.firePost`).
  5. **Conferir a oclusão** com Complementary e com BSL. Se a profundidade salva vier vazia num pacote, o ponto de salvar está cedo ou tarde demais: mova o estágio, não desligue a oclusão.
- **Por que não os tipos emissivos do vanilla:**
  - Com o pacote ativo, o Iris ignora os shaders próprios dos mods na passada do mundo (doc oficial `docs/development/compatibility/core-shaders.md`).
  - Já `eyes`, `beaconBeam` e `lightning` ficam com a cara de cada pacote: some num, estoura noutro.
  - O desenho tardio dá a mesma imagem em todos.
- **Partículas** com shaderpack também vão para o buffer tardio, por uma camada de partícula própria (`ParticleRenderType` nosso).

## 6. Luz no mundo (o efeito acende o que está em volta)

- **Uma passada de tela**, por último:
  - **Sem pacote:** em `AFTER_LEVEL`, sobre o alvo principal (com Fabulous, a cor já está composta nesse estágio, mas a **profundidade foi limpa** pelo último passo da cadeia de transparência: ela é guardada antes, em `AFTER_WEATHER`).
  - **Com pacote:** no mesmo gancho do desenho tardio, logo depois do buffer, sobre a imagem final. A passada de `AFTER_LEVEL` fica desligada, para a luz não sair dobrada.
  - Usa a cópia da cor e da profundidade (com pacote, a profundidade salva).
- **No shader:**
  - Refazer a posição pelo `InvViewProj`.
  - Normal pelas derivadas: `cross(dFdx(p), dFdy(p))`, virada para a câmera.
  - Prender o ponto à grade de pixel do mundo: `snap = (floor((p + camFrac) * 16 + n * 0.5) + 0.5) / 16 - camFrac`. A borda da luz sai em quadradinhos, como o resto do mundo.
- **Queda em degraus:** `f = 1 - d / r` e `f = floor(f * f * 5 + 0.35) / 5`, de modo que o último degrau já é 0 e a borda não corta seco.
- **Face de costas:** recebe 40%, porque a luz rebate.
- **Mistura:** `luz × (0,2 + 2 × cena)`, que tinge um pouco o escuro e multiplica o que está aceso, então a textura da superfície continua aparecendo.
- **Teto suave:** quando a soma passa de 1, divide por `1 + (topo - 1) × 1,2`, para muitas luzes juntas não queimarem a imagem.
- **Limites:** até 32 luzes por quadro, agrupadas por proximidade. Fora o céu (profundidade 1) e a mão.
- **Fonte do método:** o SPMovesets, em `fx_light.fsh` e `FxLights`. A implementação é nossa.

## 7. Tempo e suavidade

- **Fase do efeito:** `(gameTime - inicio + partialTick) / 20`, com o `inicio` sincronizado pelo block entity. Laços puramente visuais podem usar um relógio de nanossegundos (`Util.getNanos()`), que não engasga com o TPS.
- **Posições** de entidade e de block entity interpoladas pelo `partialTick`.
- **Quadros desenhados:** o índice sai da fase (`floor(fase × fps) % n`), nunca do `.mcmeta`.

## 8. Custo e robustez

- **Sem efeito à vista:** custo zero, sem visita por tick.
- **Desenho:** um buffer por tipo de desenho e nada de alocação por quadro.
- **Pré-carga:** as texturas de efeito carregam ao entrar no mundo, 2 por tick, pelo `TextureManager.getTexture` (o Ronova, em `Warmup`). O primeiro uso não engasga.
- **Placas Intel e Apple:**
  - Normalizar com guarda (vetor zero), dividir com `max(..., 1e-4)` e nada de `NaN` nem `inf`.
  - No fragment, `highp` onde houver profundidade.
  - O Ronova consertou efeitos que viravam "black squares or invisible on Intel and Apple graphics".
- **Compatibilidade a testar:**
  - Distant Horizons: esconder o efeito sobre LOD.
  - Improved Transparency (OIT): o Ronova tinha um crash com ele.
  - Teclas: o Ronova conflitava com o R de recarregar shaders do Iris.
