---
name: wa-efeitos
description: Use quando o dono pedir qualquer efeito visual, animação renderizada, feixe, arco, raio, plasma, aura, onda, partícula, rastro, brilho ou luz de efeito no Wireless Automate, quando trouxer uma referência (GIF, vídeo, print, outro mod) para igualar, quando o efeito precisar funcionar com shaderpack (Iris), ou quando um efeito for reprovado como genérico, neon, borrado, de bloquinhos ou descontínuo.
---

# wa-efeitos: efeito no nível dos melhores mods de efeitos

## Princípio

Efeito bonito é **arte desenhada em camadas**, animada por **envelope de tempo** e desenhada por **uma passada própria que sobrevive ao shaderpack**. Linha procedural com bloom, gradiente de shader e geometria sorteada a cada tick ficam genéricos: foi o que o dono reprovou. A barra é a de mods como Ronova: Ruler of Death e SPMovesets. Estude-os pelo jar (`scripts/estudar_jar.py`); nunca copie textura, shader ou código, porque são All Rights Reserved. Desenhe os nossos.

## A ficha do efeito (preencha antes de qualquer código)

Cada efeito é uma tabela com estas linhas, todas obrigatórias. Uma linha vazia é um defeito, a não ser que a ficha diga por que aquela camada não existe.

| Camada | O que é | Desenho (`RenderType`, ver `references/pipeline.md`) |
| --- | --- | --- |
| Tinta | silhueta escura que dá contraste (vinho, grafite, sombra colorida) | alfa, com a luz real |
| Luz | núcleo e halo, a forma principal | aditivo pré-multiplicado, luz cheia |
| Borda | contorno em textura própria `_rim` | aditivo |
| Partículas | dois tipos ou mais (brasa, faísca, estilhaço, névoa, pó), cada um com nascimento, arrasto, vida e rampa de cor | camada de partícula própria |
| Chão | decalque, aro ou rachadura que prende o efeito ao mundo | alfa, colado na superfície |
| Luz no mundo | as superfícies em volta acendem em degraus, presas à grade de 1/16 | passada de luz pela profundidade |
| Tempo | preparação, estouro, permanência e dissolução, em segundos | relógio do efeito |

Para cada camada, anote também a textura (do kit em `references/arte.md` ou uma nova), a cor (da rampa da `PALETA`) e o tempo de nascer, de pico e de sumir.

## As regras que fazem ficar bonito

1. **Textura desenhada, na linha do mod.** A linha do Wireless Automate é pixel art: sprites de 8 a 128 px, filtro NEAREST, rampas de 3 a 5 tons da paleta de `identidade.py` e borda em `_rim`. Volumes grandes e macios (halo, névoa) podem ser pintados lisos, em alfa baixo. Cada parte que vive ganha folha de quadros desenhada, por exemplo: raio 8, choque 6, núcleo e chama 6, clarão, fumaça, estrela e sigilo 4. Detalhes em `references/arte.md`.
2. **Contraste de valor.** A tinta escura fica sob a luz clara, e o branco só aparece no miolo pequeno. Tudo aditivo, sem tinta, vira neon.
3. **Movimento contínuo.** UV correndo, rotação e oscilação lenta (até 2 Hz). Quadros desenhados trocam a 8 a 12 fps. Nunca sorteie a geometria de novo a cada tick. Clarão dura pelo menos 4 ticks, com ease. Nada pisca mais de 3 vezes por segundo.
4. **Dissolver, nunca cortar.** Na pixel art, o sumiço é por trama Bayer 4×4 contada nos pixels da textura. O limiar de dissolução fica só para as texturas pintadas e macias.
5. **Fase vinda do servidor.** A fase do efeito sai do tick de início sincronizado mais o `partialTick`. A animação é calculada em código, não pelo `.mcmeta`, que roda num relógio global fora de fase.

## O caminho de desenho (resumo de `references/pipeline.md`)

- **Peças sólidas que se movem** (anel, prato, cristal como objeto): malha **facetada e contínua**, com textura em pixel art na densidade do mundo (1 texel = 1/16 de bloco) e sombreada por face como os modelos do jogo. Vão pelo tipo de entidade do vanilla, no passe normal do mundo, também com shaderpack, e assim o pacote dá a luz e a sombra. Malha lisa sem textura destoa de um mod de pixel art.
- **`RenderType`s próprios** para tinta, luz e borda, sobre um core shader nosso: uma cópia do `rendertype_entity_translucent` do vanilla com `AlphaCut` baixo, mais as variantes aditiva e pré-multiplicada (a aditiva sem névoa). Um cache por textura. A ordem e a receita da fita entre pontos estão nas seções 2 e 3 de `references/pipeline.md`.
- **Sem shaderpack:** desenha no estágio de sempre (BER ou `RenderLevelStageEvent`).
- **Com shaderpack do Iris** (detectado por reflexão, sem dependência):
  - Nada é desenhado na passada de sombra.
  - Durante o mundo, os desenhos são juntados num buffer tardio e a profundidade é salva.
  - Depois da composição do pacote, a profundidade é restaurada, as matrizes do quadro são reaplicadas e o buffer é desenhado por cima da imagem final. É assim que os dois mods de referência fazem.
  - Os tipos emissivos do vanilla não servem: cada pacote os desenha de um jeito.
- **Luz no mundo:** uma passada de tela que lê a profundidade, prende cada ponto à grade de 1/16, cai em 5 degraus e mistura tingir e multiplicar. Nunca bloco de luz no mundo.
- **Texturas pré-carregadas** ao entrar no mundo (2 por tick), para o primeiro uso não travar.

## Pronto quando

1. Cada linha da ficha aparece no quadro, em fotos por fase: preparação, estouro, permanência e dissolução.
2. Lado a lado com a referência, na mesma fase (`scripts/quadros.py` e `scripts/lado_a_lado.py`), não sobra diferença grande na lista por camada.
3. O mesmo quadro sem pacote, com Complementary e com BSL tem a mesma cor e a mesma forma, a oclusão certa atrás de blocos e nada na passada de sombra.
4. Não há corte seco, pisca nem salto entre quadros: o vídeo de uma rodada mostra isso.
5. O custo é zero sem o efeito à vista e o tempo de quadro está medido (skill `wa-performance`). Placa integrada (Intel) não tem quadrado preto.
6. O dono aprovou. Até lá: mostrar e parar, sem commit.

## Erros comuns

| Instinto | Correção |
| --- | --- |
| Fita lisa aditiva com bloom | Textura desenhada, mais tinta, borda e partículas |
| Com shaderpack, usar `eyes`, `beaconBeam` ou outro tipo emissivo do vanilla | Buffer tardio por cima do quadro, com a profundidade restaurada |
| Arco refeito por deslocamento aleatório a 15 a 20 Hz | Tira de raio texturizada correndo, mais ondulação lenta |
| Bloco de luz no mundo para iluminar | Passada de luz pela profundidade, em degraus |
| Animação pelo `.mcmeta` | Índice do quadro calculado pela fase do efeito |
| Gradiente suave num mod de pixel art | Rampa de tons e trama Bayer |
| Anel ou peça que se move lisa e sem textura | Facetada, contínua, com textura em pixel na densidade do mundo |
| Uma imagem só para um efeito que vive | Folha de quadros desenhada, trocando pelo relógio do efeito |
| Copiar a textura de um mod de referência | Estudar o método no jar e desenhar a nossa |

Armadilhas de GLSL, de pipeline e do e2e de captura: `references/armadilhas.md`.

## O que o dono já disse

- **Reprovado:**
  - Bloquinhos descontínuos.
  - Fitas de neon com bloom ("genérico").
  - O pulso que socava a esfera.
  - Anéis lisos e sem textura.
- **Referência de imagem com shaderpack:** o Complementary Reimagined.
- **Barra de qualidade:** Ronova e SPMovesets. Como avisado no início, iguala-se o nível, não o conteúdo.
