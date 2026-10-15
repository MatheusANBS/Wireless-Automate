# Ressonador de Sinal · design

Gerador de energia multibloco do Wireless Automate. Ideia aprovada pelo dono em 15/10/2026, depois de sete versões do mockup (`docs/preview/ressonador.html`, a v7; prancha das texturas em `docs/preview/ressonador-prancha.png`; protótipo das texturas em `docs/preview/ressonador-proto.py`).

## A ideia

Um gerador multibloco que tira energia da vibração de cristais de quartzo. Cada Ressonador tem uma **estação** (uma frequência), e o dial dele precisa estar em cima dela. A estação escorrega devagar com o tempo. Quem volta e sintoniza ganha 100%. O Sintonizador faz isso sozinho, um pouco abaixo. Fora da estação, a geração vai a zero: o jogador tem que sintonizar.

O tema "sintonia = sincronia" guia o visual: em sintonia, os cristais lá dentro oscilam juntos e a figura de luz fecha; fora dela, cada um no seu tempo e a figura vira um rabisco.

## Decisões do dono

- Mecânica própria do mod: Ressonador de cristal com sintonia (descartados: combustível, solar, reator, torre de isoladores, radiotelescópio).
- Minério do mod (Quartzo de Sinal), com recarga do cristal gasto **dentro do Núcleo, com redstone**.
- Modo automático pelo **Sintonizador**, um pouco abaixo do manual.
- **Tier pelo Núcleo**, com os mesmos Cartões de Upgrade do roteador.
- **Uma estação por Ressonador**, com deriva lenta. Cristais **sem** frequência própria (a v1 com frequência por cristal não fechava: não chegava a 100% e a estação pulava quando um cristal se gastava).
- **Sem piso**: fora da estação a eficiência vai a 0%.
- Estrutura **5×5×5**, fixa, igual em todos os tiers (3×3×3 ficou pequena para o interior; 7×7×7 ficou grande).
- Texturas conectadas, com a moldura **da estrutura** (não do bloco); estado montado diferente do solto.
- Câmara com o conceito **B · Lissajous e Chladni**.

## Sintonia

- Estação nasce aleatória entre 88,0 e 108,0 MHz quando a estrutura monta pela primeira vez; fica salva no Núcleo.
- Dial de 88,0 a 108,0 MHz, passo fino de 0,05 MHz.
- Eficiência pelo desvio `d = |estação − dial|` em MHz: `1` se `d ≤ 0,25`; `0` se `d ≥ 2,0`; entre os dois, `1 − ((d − 0,25) / 1,75)^1,5`. Exemplos: 0,5 → 95%, 1,0 → 72%, 1,5 → 40%.
- Deriva: passeio aleatório com velocidade limitada, um passo a cada 20 s de jogo (400 ticks): `v ← clamp(v + N(0; 0,03), −0,12, +0,12)` MHz/min, `estação ← clamp(estação + v/3, 88,5, 107,5)`. Calculada no lote do Núcleo, nada por tick.
- Sintonizador (peça opcional): no modo automático, move o dial até a estação (no máximo 0,5 MHz por passo) e limita a eficiência ao teto do tier.
- Simulação (300 Ressonadores, 4 h de jogo, dial acertado a cada visita): a cada 10 min 97,3% (pior momento 73%); 15 min 94,0% (48%); 20 min 90,1% (25%); 30 min 82,9% (6%); 1 h 68,7% (2%); nunca mais mexe 70,7% em 1 h, 42,8% em 4 h, 31,6% em 8 h, rumo a 0. Sem mexer, abaixo de 90% em ~14 min e de 50% em ~37 min.

## Cristais

| Item | Rende | Dura gerando (padrão) |
| --- | --- | --- |
| Cristal de Sinal | 1× | 12.000 ticks (10 min) |
| Cristal Lapidado | 2× | 24.000 ticks (20 min) |

- A **carga** fica no componente `crystal_charge` (inteiro de 0 a 1.000.000; sem componente = cheio). Independe da config de duração: a config só muda a velocidade com que o Núcleo gasta.
- Com carga 0 o cristal fica **Opaco**: mesmo item, nome "Cristal de Sinal Opaco" / "Cristal Lapidado Opaco", textura opaca pelo predicado de modelo `wirelessautomate:dull`. Opacos do mesmo tipo empilham. O Núcleo recarrega com 1 pó de redstone em 30 s (etapa 2).
- Ids: `signal_crystal`, `cut_signal_crystal`.
- Receita do Lapidado (proposta): bancada, 4 Cristais de Sinal em cruz em volta de 1 Fragmento de Ametista → 1 Cristal Lapidado. Rende 2× e dura 2×: o mesmo valor de 4 cristais, em uma câmara. Tipo próprio `wirelessautomate:cut_crystal` (um `ShapedRecipe` que só aceita cristais **cheios**): com a receita comum, 4 Opacos virariam um Lapidado cheio, energia de graça. Por ser tipo próprio, fica fora do editor de receitas (como os anéis dos Cartões de Upgrade); um pack muda por datapack ou KubeJS.

## Minério

- `signal_quartz_ore` (pedra) e `deepslate_signal_quartz_ore` (ardósia), no Overworld.
- Veio de tamanho 7, 6 por chunk, altura em trapézio de y = −64 a y = 32 (mais comum em −16).
- Drop: 1 a 3 Cristais de Sinal, com Fortuna (`ore_drops`); Toque de Seda dá o minério; 2 a 5 de experiência.
- Picareta de ferro ou melhor. Dureza 3,0 (pedra) e 4,5 (ardósia), resistência 3,0.
- Tags: `c:ores`, `c:ores/signal_quartz`, `c:ores_in_ground/stone` e `c:ores_in_ground/deepslate` (bloco e item), `minecraft:mineable/pickaxe` e `minecraft:needs_iron_tool`.
- Geração só por dados (`configured_feature`, `placed_feature`, `neoforge/biome_modifier`); um pack desliga ou muda por datapack.

## Estrutura (etapa 2)

- 5×5×5 fixa: casca de 98 blocos (125 − 27 do interior 3×3×3); 44 arestas e cantos de Carcaça; 54 posições de face (9 por face) para Carcaça, Vidro e as peças.
- Peças: exatamente 1 Núcleo de Ressonância; pelo menos 1 Porta de Energia e 1 Porta de Cristais; 0 ou 1 Sintonizador; todas em posições de face fora das arestas.
- Montagens de exemplo: Vitrine (janela 3×3 na frente) com 85 Carcaças, 9 Vidros e as 4 peças; Faixa (3 vidros na linha de cima das 4 laterais) com 82 Carcaças e 12 Vidros. Mínimo legal: 95 Carcaças + Núcleo + 1 Porta de cada.
- Validação só por evento (colocar ou quebrar uma peça procura o Núcleo no raio de 4; ele varre as 125 posições); nada por tick.
- Texturas conectadas pela **posição no blockstate**: Carcaça com `EnumProperty` de posição (solto, 6 faces, 12 arestas, 8 cantos; 5 texturas e o resto por rotação no JSON gerado); Vidro com face × 16 conexões + solto (97 estados); peças com `montado`. O Núcleo grava ao montar e volta tudo a solto ao desmontar (98 `setBlock` com flag 2, uma vez).
- Visual: moldura de grafite só na borda externa; campo de porcelana liso; uma listra coral no rodapé das laterais; vidro com aro só onde acaba o vidro; Núcleo é a única placa escura da face (Olho 8×8 na cor do tier); peças com simetria par (sem pixel central em 16 px). Solto: cada bloco com moldura própria, Olho apagado, vidro fosco.
- Feedback ao montar: Pulso saindo do Núcleo, som curto, action bar "Ressonador montado"; clicar no Núcleo diz o que falta e contorna em coral o bloco errado.

## Núcleo e geração (etapa 2)

- Tier pelo Núcleo, com os Cartões de Upgrade do roteador, sem desmontar.

| Tier | Cristais | FE/t por cristal | Máx. (lapidados, 100%) | Teto do Sintonizador |
| --- | --- | --- | --- | --- |
| Básico | 1 | 64 | 128 | 85% |
| Avançado | 2 | 512 | 2.048 | 88% |
| Elite | 3 | 4.096 | 24.576 | 90% |
| Esmeralda | 4 | 32.768 | 262.144 | 92% |
| Allthemodium¹ | 5 | 262.144 | 2.621.440 | 94% |
| Vibranium¹ | 6 | 2.097.152 | 25.165.824 | 95% |
| Unobtainium¹ | 7 | 16.777.216 | 234.881.024 | 96% |
| Ultimate | 8 | 134.217.728 | 2.147.483.648 | 98% |

¹ Só com o Allthemodium. Números na config do servidor; em cada tier, um roteador do mesmo tier dá conta da geração cheia.

- O Núcleo é o único bloco com tick e calcula em lote, a cada 20 ticks. Porta de Energia expõe FE (só tira); Porta de Cristais aceita cristais e redstone e devolve os trocados. Comparador pela eficiência; controle por redstone.

## Tela e câmara (etapa 3)

- Tela do Núcleo: osciloscópio (limpo em sintonia, chiado fora), ponteiro de sintonia de −2 a +2 MHz, lâmpada EM SINTONIA, dial com passos de ±1 e ±0,05 MHz e roda do mouse, modo Manual × Sintonizador, slots dos cristais com a carga, geração e teto. Tema claro e escuro.
- Câmara (conceito B), desenhada por um `BlockEntityRenderer` do Núcleo, só montado, sem shaders nem pós-processamento: figura de Lissajous de luz (razões 3:2:1 travam e a figura fecha em sintonia; rabisco fora dela; some a 0%); placa de Chladni no chão (16 quadros pré-gerados misturados com ruído pela sintonia); cristais em anel baixo, subindo e descendo juntos em sintonia, cada um com um feixe fino até a cabeça de luz; lente do Núcleo acesa na parede dele; faíscas até a Porta de Energia e redstone até o Opaco como partículas do cliente. Transições: acender em sequência ao montar, clarão discreto ao entrar em sintonia, deslizar para o rabisco ao sair. ~700 quads por quadro no Ultimate em sintonia, só com a estrutura à vista.

## Identidade

- Paleta do cristal (`sinal`): `#FFF0EA #FFC9B8 #F2917A #C9604B #8A3A2C`; opaco: `#F4F0EA #D9D2C8 #B5ADA2 #8C857C #5E5952`.
- Animações no ritmo da marca (ciclos de 1,6 a 3,2 s): Cristal de Sinal 10 quadros × 4 ticks (brilho atravessando as pontas); Lapidado 12 × 3 (luz na cinta de latão e o Pulso saindo da gema); Opaco 8 × 6 (brasa fraca no pé); minério 12 × 4 (cada cristal cintila na sua vez).

## Etapas

1. **Minério e cristais**: worldgen, os dois itens com carga e o estado Opaco, texturas, receita do Lapidado. Dá para testar no jogo sem o gerador (`/give` com o componente).
2. **Estrutura e Núcleo**: os seis blocos, texturas conectadas pela posição, validação por evento, estação e deriva, geração em lote, recarga, Portas com capability, Cartões de Upgrade, config. Sem tela: o dial por comando.
3. **Tela, Sintonizador e câmara**: a tela do Núcleo, o modo automático, o comparador e o BER da câmara.
4. **Tablet e acabamento**: sintonia à distância pelo Tablet, guia nos dois idiomas, JEI (estrutura e receitas), vitrine do CurseForge.

## Em aberto

- Mais de uma Porta de cada tipo na mesma estrutura (o mínimo é uma).

## Fica de fora

Frequência por cristal; risco de explosão ou superaquecimento; interferência entre Ressonadores; outros geradores; chunk loading próprio (use o upgrade do roteador preso na Porta); cristais de outros mods como combustível.
