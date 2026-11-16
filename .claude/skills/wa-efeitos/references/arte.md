# Arte dos efeitos

O que faz um efeito ficar bonito, tirado de Ronova: Ruler of Death (texturas pintadas: 175 texturas de efeito, de 256 a 2048 px, 109 com blur) e de SPMovesets (pixel art: 221 texturas de efeito, de 8 a 128 px, nenhuma com blur e 10 pares `_rim`). Iguala-se o nível; o desenho é nosso.

## A linha do Wireless Automate: pixel art

- **Tamanho:** sprites de 8 a 128 px, no filtro NEAREST. Um pixel do sprite tem o tamanho de um pixel de textura do mundo (1/16 de bloco) ou um múltiplo inteiro dele.
- **Cor:** rampas de 3 a 5 tons tiradas da `PALETA` de `scripts/textures/identidade.py`:
  - coral, latão, vidro, energia e grafite;
  - na luz, os tons claros e o miolo quase branco;
  - na tinta, os tons 3 e 4 (os escuros).
- **Borda:** em textura à parte, `nome_rim.png`, desenhada como luz por cima da forma (o SPMovesets faz assim no `scythe_sweep_rim` e no `shadow_hand_rim`).
- **Família tingível:** textura cinza (branco com alfa) e a cor no vértice; vale para brilho, aro, brasa, faísca e névoa. A forma desenhada (estilhaço, sigilo, garra, cristal) leva a cor pintada.
- **Exceção macia:** halo e névoa grandes podem ser pintados lisos (128 a 256 px, com blur) e entram em alfa baixo, por baixo da pixel art.

## Peças sólidas renderizadas (anel, prato, cristal, pino)

- **Malha:** facetada e contínua: segmentos retos encostados, sem fresta, entre 24 e 40 por volta, e seção retangular de poucos texels (ex.: 3 × 2).
- **Textura:** em pixel art, na densidade do mundo (1 texel = 1/16 de bloco). Cada face pega a sua faixa de linhas: topo com brilho, faces laterais com rebites e escala gravada, e a de baixo escura.
- **Repetição:** pelo comprimento, com período fixo (16 texels). Cada segmento começa em `(k × compr) mod 16`, para o padrão continuar sem emenda.
- **Sombreado:** por face, como os modelos do jogo, e a luz do efeito em degraus também cai nelas. Nada de sombreado liso nem de malha sem textura.
- **Encaixe:** o que vai preso numa peça que se move (cristal no anel, prato no garfo) tem um engaste próprio na peça (base e garras), modelado junto com ela e girando com ela. Confira com números que a joia encosta na base e não entra nas garras nem na peça (memória "sem sobreposição"). Joia solta atravessando a peça é defeito.

## Kit base (desenhar uma vez, reusar em todos os efeitos)

| Peça | Forma | Uso |
| --- | --- | --- |
| `brilho` | disco com queda em 3 a 4 degraus | halo, núcleo, ponto de luz |
| `estrela` | 4 pontas, raio fino | clarão de impacto, cintilação |
| `aro_fino` e `aro_choque` | anel de 1 a 2 px; anel grosso com miolo apagado | onda de choque, pulso, marca |
| `feixe_miolo` | tira repetível: miolo claro e queda para a cor | corpo do feixe e do arco |
| `feixe_trilhos` | tira com dois trilhos claros e o meio escuro | feixe "de dois trilhos" (contraste) |
| `fluxo` | tira repetível com pacotes de energia | corrente correndo (UV andando) |
| `raio` | 8 variantes de tira dentada com ramos curtos | arco elétrico (troca a 8 a 12 fps) |
| `chama` | 6 quadros de língua de fogo | fogo, fagulha alta, línguas da coroa do núcleo |
| `fumaça` | 2 a 3 nuvens de ruído | névoa, poeira de impacto |
| `rachadura` | sequência de 8 a 16 quadros | chão quebrando, marca que cresce |
| `choque` | 6 quadros: nasce grosso, abre, quebra em trama | onda de impacto |
| `clarao` | 4 quadros: miolo, 8 raios, raios e anel, estilhaços | estouro |
| `nucleo` | 6 quadros de disco que ferve, com línguas na coroa | sol, plasma, núcleo de energia |
| `sigilo` | o mostrador do mod em 4 quadros (as marcas acendem conforme carrega) | decalque no chão e no ponto de saída |
| `brasa` | quadrado de 2×2 e de 3×3 em 2 tons | partícula padrão |
| `risco` | traço alongado, para estirar pela velocidade | faísca rápida, risco de velocidade |

## Camadas (cada efeito)

1. **Tinta:** a silhueta escura, por baixo da luz.
2. **Luz:** o miolo e o halo.
3. **Borda:** o `_rim`.
4. **Partículas.**
5. **Chão:** o decalque.
6. **Luz no mundo.**
7. **Tela** (opcional): um véu ou vinheta curta, só no auge.

Ordem de leitura: primeiro a forma grande (aro, feixe, cúpula), depois o detalhe médio (trilhos, fluxo), por fim o pequeno (faíscas). A forma tem de se ler numa miniatura de 64 px.

## Tempo

| Fase | Duração | O que acontece |
| --- | --- | --- |
| Preparação | 0,3 a 0,6 s | as partículas convergem, o núcleo encolhe e escurece, a guia aparece |
| Estouro | 0,1 a 0,3 s | escala com sobressalto, pico de luz, clarão de pelo menos 4 ticks |
| Permanência | o que o efeito pedir | UV correndo, rotação, partículas soltando, ondulação até 2 Hz |
| Dissolução | 0,4 a 1,2 s | trama Bayer 4×4 ou limiar de dissolução; partículas morrendo; luz caindo em degraus |

- **Partículas:**
  - Arrasto por tick de 0,84 a 0,94 e gravidade pequena.
  - Vida de 10 a 40 ticks.
  - O tamanho cai em degraus inteiros de pixel.
  - A cor percorre a rampa ao longo da vida (claro, latão, coral, escuro).
- **Nada pisca** mais de 3 vezes por segundo. Quadros desenhados trocam a 8 a 12 fps. Geometria nunca é sorteada de novo por tick.

## Estudar uma referência sem copiar

- **Com o ok do dono, baixe o jar e leia:**
  - `python -I scripts/estudar_jar.py <jar>` mostra os shaders, as texturas (tamanho, blur, cor ou cinza, pares `_rim`) e as palavras de render nas classes.
  - `javap -p -c -constants <classe>` mostra os métodos e os textos (tipos de desenho, estágios, chamadas ao Iris).
- **O que fica são os números do método:** tamanhos, filtros, blends, estágios, tempos e camadas.
- **Textura, shader e código** de mod All Rights Reserved não entram no nosso: desenhamos tudo de novo, na nossa paleta.
