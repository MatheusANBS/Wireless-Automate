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
| Básico | 1 | 128 | 256 | 85% |
| Avançado | 2 | 1.024 | 4.096 | 88% |
| Elite | 3 | 8.192 | 49.152 | 90% |
| Esmeralda | 4 | 32.768 | 262.144 | 92% |
| Allthemodium¹ | 5 | 262.144 | 2.621.440 | 94% |
| Vibranium¹ | 6 | 2.097.152 | 25.165.824 | 95% |
| Unobtainium¹ | 7 | 134.217.728 | 1.879.048.192 | 96% |
| Ultimate | 8 | 1.073.741.824 | 17.179.869.184 | 98% |

¹ Só com o Allthemodium. Números na config do servidor; em cada tier, um roteador do mesmo tier dá conta da geração cheia (o do Unobtainium passa a 2.097.152.000 FE/t para isso; o do Ultimate não tem limite). Balanceamento de 15/10/2026 (decisão do dono): os três primeiros dobraram (passam o Wind Generator, o Powah Hardened e o Niotic), e o Unobtainium e o Ultimate subiram ×8 para o topo passar com folga o Dyson Cube Project do ATM10 (~1 G FE/t). A Porta de Energia como `IEnergyStorage` corta em 2^31 − 1 FE por chamada; a saída cheia do Ultimate sai pelo roteador do mod (`BulkEnergy`, em `long`).

- O Núcleo é o único bloco com tick e calcula em lote, a cada 20 ticks. Porta de Energia expõe FE (só tira); Porta de Cristais aceita cristais e redstone e devolve os trocados. Sem comparador e sem controle por redstone (decisão do dono, 15/10/2026): quem automatiza é o Sintonizador.

## Tela e câmara (etapa 3)

- Tela do Núcleo (mockup aprovado `docs/preview/ressonador-etapa3.html`): 352 × 244 de tamanho fixo, cabeçalho igual ao da Bateria; osciloscópio (limpo em sintonia, chiado fora), ponteiro de sintonia de −2 a +2 MHz com o platô de ±0,25, lâmpada EM SINTONIA, dial com passos de ±1 e ±0,05 MHz e roda do mouse, modo Manual × Sintonizador, slots dos cristais só das câmaras do tier (a carga é a barra de durabilidade do item), slot de redstone, eficiência com a marca do teto, geração, energia (o buffer de 10 s), o bloco Estrutura e o inventário embaixo. A estação nunca aparece como número. Tema claro e escuro. Abre pelo clique de mão vazia no Núcleo montado.
- Câmara (conceito B), desenhada por um `BlockEntityRenderer` do Núcleo, só montado, sem shaders nem pós-processamento: figura de Lissajous de luz (razões 3:2:1 travam e a figura fecha em sintonia; rabisco fora dela; some a 0%); placa de Chladni no chão (16 quadros pré-gerados misturados com ruído pela sintonia); cristais em anel baixo, subindo e descendo juntos em sintonia, cada um com um feixe fino até a cabeça de luz; lente do Núcleo acesa na parede dele; faíscas até a Porta de Energia e redstone até o Opaco como partículas do cliente. Transições: acender em sequência ao montar, clarão discreto ao entrar em sintonia, deslizar para o rabisco ao sair. ~700 quads por quadro no Ultimate em sintonia, só com a estrutura à vista.

## Identidade

- Paleta do cristal (`sinal`): `#FFF0EA #FFC9B8 #F2917A #C9604B #8A3A2C`; opaco: `#F4F0EA #D9D2C8 #B5ADA2 #8C857C #5E5952`.
- Animações no ritmo da marca (ciclos de 1,6 a 3,2 s): Cristal de Sinal 10 quadros × 4 ticks (brilho atravessando as pontas); Lapidado 12 × 3 (luz na cinta de latão e o Pulso saindo da gema); Opaco 8 × 6 (brasa fraca no pé); minério 12 × 4 (cada cristal cintila na sua vez).

## Etapas

1. **Minério e cristais**: worldgen, os dois itens com carga e o estado Opaco, texturas, receita do Lapidado. Dá para testar no jogo sem o gerador (`/give` com o componente).
2. **Estrutura e Núcleo**: os seis blocos, texturas conectadas pela posição, validação por evento, estação e deriva, geração em lote, recarga, Portas com capability, Cartões de Upgrade, config. Sem tela: o dial por comando.
3. **Tela, Sintonizador e câmara**: a tela do Núcleo, o modo automático do Sintonizador e o BER da câmara. Sem comparador e sem controle por redstone.
4. **Tablet e acabamento**: sintonia à distância pelo Tablet, guia nos dois idiomas, JEI (estrutura e receitas), vitrine do CurseForge.

## Etapa 2: decisões de implementação (15/10/2026)

- **Portas:** pelo menos uma Porta de Energia e uma Porta de Cristais; pode haver mais de cada (decisão do dono). O Sintonizador continua 0 ou 1; o Núcleo, exatamente 1.
- **Buffer de energia do Núcleo:** 10 s da geração máxima do tier (máximo com lapidados × 200 ticks): Básico 51.200 FE, Elite 9.830.400 FE, Ultimate 3.435.973.836.800 FE (decisão do dono). Com o buffer cheio, o Núcleo não gera nem gasta cristal.
- **Receitas** (bancada, editáveis pelo editor de receitas; decisão do dono): Carcaça ×4 = 4 Terracota Branca nos cantos, 4 Lingotes de Ferro nos lados, 1 Cristal de Sinal no centro; Vidro ×4 = 4 Vidros em cruz em volta de 1 Cristal de Sinal; Núcleo (Básico) = 7 Carcaças, 1 Cristal Lapidado no centro e 1 Diamante embaixo no meio; Porta de Energia = Carcaça + Bloco de Redstone; Porta de Cristais = Carcaça + Funil; Sintonizador = Carcaça + Relógio + Comparador (as três sem forma).
- **Ids:** `resonator_casing`, `resonator_glass`, `resonance_core`, `resonator_energy_port`, `resonator_crystal_port`, `resonator_tuner`. Nomes: Carcaça de Porcelana / Porcelain Casing; Vidro de Ressonância / Resonance Glass; Núcleo de Ressonância / Resonance Core; Porta de Energia / Resonator Energy Port; Porta de Cristais / Resonator Crystal Port; Sintonizador / Resonator Tuner.
- **Interior:** as 27 posições de dentro precisam estar vazias (ar).
- **Câmaras por tier:** tier + 1 (Básico 1 … Ultimate 8), fixo, sem config.
- **Config do servidor, seção `resonator`:** `crystalLifeTicks` (12.000), `cutCrystalLifeTicks` (24.000), `rechargeTicks` (600) e, por tier, `fePerCrystal` (128, 1.024, 8.192, 32.768, 262.144, 2.097.152, 134.217.728, 1.073.741.824).
- **Lote:** um gerenciador central (`ResonatorManager`, chamado no tick do servidor, como o `SourceTankLevels`) visita cada Núcleo montado e carregado a cada 20 ticks, espalhados pela posição para não caírem todos no mesmo tick. A deriva dá um passo a cada 20 lotes (400 ticks).
- **Desgaste:** só cristal que está gerando gasta (eficiência acima de 0 e espaço no buffer), na proporção do que gerou: com espaço para só parte do lote, cada cristal gasta `⌈ticks × gerado ÷ (por tick × ticks)⌉` ticks (decisão do dono, 15/10/2026); com o buffer cheio, nada.
- **Recarga:** um Opaco por vez; com redstone no Núcleo, a recarga leva `rechargeTicks` e consome 1 pó no fim.
- **Porta de Cristais (itens):** aceita Cristal de Sinal e Lapidado (um por câmara vazia) e pó de redstone (até 64 no Núcleo). Só entrega Opacos, e só quando o Núcleo está sem redstone (com redstone, o Opaco fica para recarregar). A redstone não sai.
- **Porta de Energia:** só tira; expõe `BulkEnergy` (em `long`, o que o roteador usa) e `IEnergyStorage`. Quando a energia volta a ter algo, o Núcleo avisa os vizinhos das Portas para o roteador acordar.
- **Portas sem block entity:** a capability acha o Núcleo por um índice do servidor (porta → Núcleo), preenchido ao montar e no `onLoad` do Núcleo.
- **Sem tela nesta etapa:** clique com a mão vazia no Núcleo mostra na action bar o estado (montado: tier, cristais, eficiência e FE/t; solto: o que falta e onde). Clique com cristal ou redstone põe no Núcleo; Shift + clique com a mão vazia tira o último cristal. O Cartão de Upgrade sobe o tier do Núcleo como no roteador. O dial e a consulta vêm do comando `/wa resonator <pos> info` e `/wa resonator <pos> dial <MHz>` (operador).
- **Quebrar o Núcleo** derruba os cristais e a redstone; a energia se perde. O Núcleo volta como item com o tier (como o roteador).
- **Fica para a etapa 3:** modo automático do Sintonizador, tela e câmara (BER); na etapa 2 o Sintonizador só conta na validação.
- **Projeção (Carcaça e Vidro):** no miolo das faces da caixa, Carcaça e Vidro contam como certos um pelo outro, como a montagem aceita; na aresta, só o esperado.
- **Parede compartilhada: proibida** (decisão do dono). Um índice do servidor membro → Núcleo (por dimensão), preenchido ao montar e no `onLoad` do Núcleo montado (antes da revalidação, para a ordem da carga não trocar o dono), guarda o dono de cada bloco da casca; só o dono tira. Um Ressonador com um membro de outro Núcleo montado não monta: problema `SHARED_WALL`, "Parede já usada por outro Ressonador (Núcleo em x y z)". A desmontagem só solta os blocos do próprio Núcleo e agenda a revalidação dos Núcleos que esperavam por uma parede dele (montar não avisa ninguém, então não há pingue-pongue). A projeção mostra a estrutura completa (os blocos são do tipo certo); o clique no Núcleo explica.
- **Frente ao colocar:** a peça com frente perto de um Núcleo, numa parede da caixa 5×5×5, nasce olhando para fora dela (numa aresta ou canto, a parede mais voltada para o jogador); nos outros casos, e no Núcleo, olha para o jogador. A busca é só ao colocar (raio 4, sem carregar chunk).
- **Tirar cristal:** Shift + clique no Núcleo só tira o último cristal com as duas mãos vazias.
- **Convenção das faces (texturas conectadas):** para cada face, as direções "direita" e "cima" da textura no mundo são as do modelo do vanilla sem rotação: Norte (direita = Oeste, cima = +Y), Sul (Leste, +Y), Oeste (Sul, +Y), Leste (Norte, +Y), Cima (Leste, Norte), Baixo (Leste, Sul). Os bits de conexão do vidro são 1 = cima, 2 = direita, 4 = baixo, 8 = esquerda (bit ligado = há vidro do mesmo lado da estrutura daquele lado). O Java calcula os bits e o script gera as texturas pela mesma tabela.

## Etapa 3: decisões de implementação (15/10/2026)

Mockup aprovado: `docs/preview/ressonador-etapa3.html` (a fonte do visual). Plano: `docs/superpowers/plans/2026-10-15-ressonador-etapa-3.md`.

- **Escopo:** a tela do Núcleo, o modo automático do Sintonizador e a câmara animada (BER, conceito B). **Sem comparador e sem controle por redstone** (decisão do dono). Tablet, JEI, guia e vitrine ficam na etapa 4.
- **Interação no Núcleo:** o clique de mão vazia no Núcleo **montado** abre a tela (`ResonatorMenu.open`), sem action bar; no solto continuam a projeção e a linha do problema. Shift + clique com as duas mãos vazias continua tirando o último cristal; clique com cristal ou redstone continua pondo; o Cartão de Upgrade continua subindo o tier (as câmaras novas aparecem na tela aberta no snapshot seguinte).
- **Tela:** 352 × 244, tamanho fixo (o cabeçalho é o da Bateria: título, pílula do tier na cor dele, botão de tema 11 × 11 no canto direito; a faixa de 2 px no topo na cor do tier). Posições em pixels de interface, a partir do canto da tela: osciloscópio `(10, 28) 172 × 50`; ponteiro `(10, 80) 172 × 22`; lâmpada `(10, 106) 172 × 8`; linha do dial `(10, 120) 172 × 14` (botões « ‹ de 14 × 14 em x 10 e 26, o campo de 42 a 150, os botões › » em 152 e 168); linha do modo `(10, 138)`: Manual 64 px e Sintonizador 104 px; rótulo Cristais `(190, 28)`; slots das câmaras em `(190 + 18·i, 38)`, só as `chambers()` do tier; slot da redstone `(190, 60)` com o rótulo de duas linhas ao lado; Eficiência `(190, 84)` com a barra `(190, 94) 152 × 6` e a marca do teto; Geração `(190, 106)`; Energia `(190, 120)` com a barra `(190, 130) 152 × 6` e a legenda "buffer: 10 s da saída cheia" em `(190, 140)`; inventário em `(10, 158)` (3 linhas) e barra em `(10, 216)`; bloco Estrutura em `(190, 158)`: Sintonizador sim/não `(190, 170)`, Portas de Energia `(190, 182)`, Portas de Cristais `(190, 194)` e Montagem `(190, 206)` (Vitrine, Faixa ou Própria, reconhecida pela `ResonatorBlueprint.layoutOf`). Rótulos do osciloscópio: ESTAÇÃO (|d| ≤ 0,25), QUASE LÁ (|d| < 2) e CHIADO; fora de ±2 MHz o ponteiro encosta e diz "sem sinal: varra o dial". O osciloscópio é o único fundo escuro nos dois temas (`GuiPaint.SCOPE_BG`, `SCOPE_GRID`, `SCOPE_LINE`, iguais nas duas paletas). O dial é mostrado com duas casas e o separador do idioma do jogo: `94,00 MHz` em português e `94.00 MHz` em inglês (decisão do dono, como no mockup); o comando continua com ponto. Todo o resto por tokens do `GuiPaint` e todo texto variável pelo `GuiText`.
- **Carga como barra do item** (decisão do dono): `SignalCrystalItem.isBarVisible` é verdadeiro só com `0 < carga < cheia`; `getBarWidth` é `round(13 × carga ÷ FULL)` e `getBarColor` o coral `0xF0603E`. Vale no slot, no inventário e no JEI. **O Opaco não tem barra fora do Núcleo** (o progresso da recarga é do Núcleo, não do item); na tela, o `CrystalSlots` desenha sobre o slot do Opaco a barra da recarga em latão (`GuiPaint.BRASS`), com o progresso que vem do menu.
- **Sintonizador:** lógica pura no `Tuning`: `autoStep(dial, station)` anda o dial até a estação no máximo `AUTO_STEP_MHZ = 0,5` por passo (passando pelo `clampDial`); `ceiling(tierOrdinal)` é o teto `{0,85; 0,88; 0,90; 0,92; 0,94; 0,95; 0,96; 0,98}` (constantes, sem config); `capped(efficiency, auto, tierOrdinal)` aplica o teto só no modo automático. O passo roda no `ResonatorBatch.drift`, depois do passo da deriva (a cada 20 lotes = 400 ticks), só com `auto && hasTuner`. A eficiência do Núcleo (`efficiency()`) sai capada no modo automático; a lâmpada usa o desvio cru. O modo é gravado no NBT do Núcleo na chave nova `auto` (boolean); as chaves existentes não mudam. A ligação do Sintonizador ao Núcleo é fixada ao montar: `ResonatorFormation.form` passa ao Núcleo `hasTuner`, as contagens das Portas e a montagem (`setStructure`), chaves `has_tuner`, `energy_ports`, `crystal_ports`, `layout` (0 própria, 1 Vitrine, 2 Faixa) e `formed_at` (o tempo de jogo da montagem, para a sequência de acender). Sem a peça ao montar, o modo volta a Manual e o dial fica onde parou. O índice `ResonatorMembers` não é necessário para isso.
- **`crystals()` só leitura:** devolve uma visão imutável (`Collections.unmodifiableList`); quem muda uma câmara usa `insertCrystal`, `insertCrystalAt`, `takeCrystalAt`, `takeLastCrystal` ou o novo `setCrystalAt(int, ItemStack)` (o slot do menu), e a redstone `addRedstone`, `spendRedstone` ou o novo `setRedstone(int)`. O menu entra pela visão `Container` `CoreInventory` (`inventory()`): câmaras do tier + 1 slot da redstone.
- **Sincronização com a tela fechada (a câmara):** o Núcleo ganha `getUpdateTag`, `getUpdatePacket` e `handleUpdateTag`. O tag é a `ChamberView`, só o que a câmara desenha: `chambers` (byte por câmara, `0` vazia, `1` Cristal de Sinal, `2` Lapidado, `3` Cristal Opaco, `4` Lapidado Opaco; só as `chambers()` do tier), `band` (a faixa da eficiência, byte 0 a 7), `tuned` (boolean, |d| ≤ 0,25), `recharge_start` (long: tempo de jogo em que a recarga em curso começou, `−1` sem recarga), `recharge_ticks` (int, a config), `formed_at` (long) e `origin` (int[3], o canto mínimo da caixa). `syncIfChanged()` monta a view, compara com a última mandada e só então chama `level.sendBlockUpdated`; é chamado pelos mutadores dos cristais e da redstone, pelo `setDial`, `setAuto`, `setStructure` e no fim de cada lote. **Nada por tick.** A faixa é `TuneSignal.band(e) = ⌈e × 7⌉` (0 = 0%, 7 = acima de 6/7); a câmara interpola a sintonia pela faixa.
- **Com a tela aberta:** `ResonatorMenu.broadcastChanges` manda o `ResonatorStatePayload` a cada 5 ticks (`StorageListMenu.SYNC_INTERVAL`) e só se mudou (o primeiro na hora): `energy`, `capacity`, `generation` (FE/t agora), `maxGeneration` (estes cristais a 100%), `dial`, `efficiencyPermille` (já capada), `ceilingPermille`, `auto`, `tuner`, `deviationSteps` (`round(d ÷ 0,05)` só com |d| < 2 MHz, senão `NO_SIGNAL = 999`), `recharge` (ticks andados), `rechargeTicks`, `energyPorts`, `crystalPorts` e `layout`. A estação nunca sai do servidor. Os slots (cristais e redstone) viajam pelo próprio menu. `stillValid`: a 8 blocos e o Núcleo montado.
- **Payloads cliente → servidor:** `ResonatorDialPayload(containerId, double mhz)` e `ResonatorModePayload(containerId, boolean auto)`, validados por `ModPayloads.handleResonatorDial`/`handleResonatorMode`: o menu aberto com esse `containerId`, `stillValid`, Núcleo montado; o dial é recusado no modo automático; o automático é recusado sem Sintonizador. `ModPayloads.VERSION` sobe para `"17"`.
- **Câmara (BER do Núcleo, só montado, `client/resonator/render/`):** `ResonatorRenderer` (registrado no `EntityRenderersEvent.RegisterRenderers`; `getViewDistance` 64; a caixa de render é o 5 × 5 × 5 pela `origin` da view). O estado por Núcleo fica numa `ChamberScene` guardada pelo renderer num mapa por posição (esquecida depois de 200 ticks sem desenhar), com os 260 pontos de Lissajous recalculados uma vez por tick do cliente na hora de desenhar (o tick anterior fica guardado para a interpolação por `partialTick`), a sintonia suavizada `q` (aproxima `band ÷ 7` a 1,6 por segundo), os tempos (`formed_at`, o clarão a partir do tick em que `tuned` passou a verdadeiro) e as Portas da casca (lidas do `ClientLevel` quando a `origin` muda e a cada 200 ticks). Curva pura `LissajousCurve` (JUnit): razões 3 : 2 : 1 travadas em sintonia, escorregando por `(1 − q)` fora dela; amplitudes 0,78 × 0,55 × 0,78 blocos em torno do centro da câmara (`origin + 2,5`). Traço: 2 quads cruzados aditivos por segmento, `RenderType.lightning()` (aditivo, sem textura, full bright), alfa `(0,12 + 0,7q) × min(1, 4q + 0,05)`; cabeça (halo com a textura `block/resonator_halo`, `RenderType.eyes(InventoryMenu.BLOCK_ATLAS)`, virada para a câmera) e miolo (icosaedro, 20 triângulos, `lightning`); lente do Núcleo por dentro (1 quad aditivo na cor do tier, respirando em 2,4 s); clarão (o quad do halo escalando `1 + 2,2t` e esvaindo em 0,9 s); placa de Chladni no chão (2 quads: a figura `block/resonator_chladni`, 16 quadros 32 × 32 animados por `.mcmeta` a 3 ticks, com alfa `q`, e o ruído `block/resonator_chladni_noise`, 8 quadros a 2 ticks, com alfa `1 − q`; as texturas vivem no atlas dos blocos porque estão em `textures/block/`, e a animação é a do atlas; 32 px por decisão do dono (linhas mais finas, mais perto do mockup; numa placa de 3 blocos cada texel vale 1,5 pixel de bloco), a única textura de 32 px do mod); cristais em anel de raio 0,95 (o modelo do item pelo `ItemRenderer.renderStatic`, `ItemDisplayContext.FIXED`, luz cheia; o Opaco sai cinza pelo próprio predicado `dull`, caído a −0,35 e voltando à altura com `(tempo − recharge_start) ÷ recharge_ticks`; os outros sobem e descem juntos em sintonia, cada um no seu tempo fora dela), feixes (2 quads cruzados por cristal até a cabeça, alfa `max(0, q − 0,5) × 2`, nenhum no Opaco), acender em sequência ao montar (lente, chão e luz, cristais, figura e feixes, 0,9 s cada a partir de `formed_at`). Partículas pelo `ChamberParticles`: viajantes do cliente (posição, destino, idade) avançados uma vez por tick com o jogador a menos de 32 blocos; cada viajante solta a cada tick uma partícula do vanilla parada onde está (`ParticleTypes.ELECTRIC_SPARK` nas faíscas, do centro à face de dentro da Porta de Energia, 2 a 5 por segundo pela faixa, no máximo 8 vivas; `DustParticleOptions` vermelha na redstone, da face de dentro da Porta de Cristais ao Opaco em recarga, no máximo 4 vivas). Sem tipo de partícula novo, nada no servidor. ~700 quads por quadro no Ultimate em sintonia.
- **Texturas novas** pelo `scripts/textures/blocos.py` (`sprites_ressonador`): `block/resonator_chladni` (16 quadros de 32 × 32, a figura (3, 2) `cos(3πx)cos(2πy) − cos(2πx)cos(3πy)` com a luz pulsando pelo quadro), `block/resonator_chladni_noise` (8 quadros de ruído) e `block/resonator_halo` (radial, 16 × 16). Nunca PNG à mão.
- **GameTests:** o menu e os slots (`ResonatorMenuGameTests`), os payloads e a validação, o modo automático com e sem a peça, a peça saindo, o teto (`ResonatorTunerGameTests`), a view e a faixa no update tag (`ResonatorSyncGameTests`). **e2e:** a tela do Núcleo nos dois temas e em pt_br (`ressonador-tela`, `ressonador-tela-escuro`, `ressonador-tela-pt`) com `GuiText.clipCount() == 0`, e a câmara (`ressonador-camara`, `ressonador-camara-chiado`).

## Em aberto

- Nada.

## Fica de fora

Frequência por cristal; risco de explosão ou superaquecimento; interferência entre Ressonadores; outros geradores; chunk loading próprio (use o upgrade do roteador preso na Porta); cristais de outros mods como combustível.
