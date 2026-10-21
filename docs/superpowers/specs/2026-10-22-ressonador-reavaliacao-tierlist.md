# Ressonador de Sinal: reavaliação do tier list com a cascata completa dos rivais

Análise de 22/10/2026, a pedido do dono. O tier list anterior (`https://claude.ai/artifact/XifgBxwhHfuFQLLSvxce7Z`) dava ao custo uma nota de 1 a 10 por julgamento, que **satura**: o painel Infinity valia 9,5 e o Ressonador Unobtainium 8, quando a diferença real é de ordens de grandeza. Aqui o custo é a **cascata inteira contada**: do primeiro item de base até o gerador pronto, rivais e Ressonador no mesmo motor. **Nada no código do mod mudou**; o que aplicar está em "Opções".

## Método

Um motor de custo recursivo lê as receitas reais do Infinity Legacy II (os jars e o KubeJS da instância; as do ATM10 conferem nos itens candidatos) e abre cada item até os itens de base (lingote, gema, pó, drop, minério; 1 bloco = 9 lingotes). Para cada gerador, na construção completa:

- **N**, itens de base gastos (soma de tudo que entra na cascata);
- **crafts**, operações de craft e de máquina (cada receita executada conta 1; é o "complexidade de crafting" que o dono apontou);
- **matérias de base** distintas e **itens intermediários** distintos (quantos itens diferentes o jogador precisa saber fazer);
- **profundidade** (quantos níveis de receita até o fim);
- **G**, itens-gargalo (Nether Star, Dragon Egg, netherite, Awakened Draconium, Large Chaos Fragment, Dragon Heart, singularidades, pellets de antimatéria, metais de tier do ATM).

O Ressonador conta a construção completa de cada tier: estrutura de 98 peças (Vitrine: 85 carcaças, 9 vidros, 3 portas e o Núcleo), os Cartões e os Corações de cada degrau desde o Básico, e as câmaras com o cristal do tier.

### Limites (todos para baixo: o real é igual ou maior)

- Itens sem receita legível contam **1 unidade cada**: Ultimate Singularity, The Ultimate Ingot, Creative Essence (o painel Infinity usa 48 de cada), o minério cru da Eclipse Alloy e o Coal T4 (a cadeia Goo T4, Mystical Agriculture e compressão de carvão não é legível; **o painel Eclipse não foi medido de verdade**, a linha dele abaixo é só o que apareceu).
- A escada dos painéis do Solar Flux (níveis 1 a 7, só no código do mod) não dá para ler. As linhas do Chaotic, do Neutronium e do Infinity trazem um intervalo: o piso conta cada painel de nível 1 a 7 como 1 item; o teto assume 2 do nível anterior + 3 células por nível.
- Fluidos (mB) não entram em N; as receitas de máquina de outros mods entram só pelos itens (energia e fluido ficam de fora).
- Pesos de qualquer fórmula composta não foram usados: a comparação é direta, medida por medida.

## Os rivais, cascata completa

| Gerador | FE/t | N (itens de base) | crafts | matérias | intermediários | prof. | G |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Alternator (Create Crafts & Additions) | 360 | 30 | 44 | 12 | 20 | 10 | 0 |
| Eclipse Alloy Solar Panel (não medido, ver limites) | 23.040 | ≥ 2 | ≥ 1 | ≥ 3 | ≥ 4 | ≥ 6 | 0 |
| Powah Solar Panel Nitro | 50.000 | 25.040 | 8.244 | 15 | 32 | 24 | 1 |
| Rainbow Furnace | 50.000 | 497 | 93 | 27 | 43 | 14 | 14 |
| Powah Reactor Nitro (36 blocos) | 489.500 | 1.458 | 953 | 14 | 32 | 24 | 9 |
| Chaotic Solar Panel | 1.048.576 | 737 a 6.849 | 116 a 3.148 | 10 | 20 | 24 | 45 |
| Neutronium Solar Panel | 16.777.216 | 2.460 a 34.548 | 2.881 a 18.799 | 11 | 23 | 24 | 63 |
| Draconic Reactor | 24.400.000 | 7.248 | 912 | 8 | 18 | 9 | 653 |
| Naquadah Reactor (Mekanism Extras) | 39.200.000 | 3.509 | 223 | 11 | 21 | 15 | 0 |
| Reinforced Turbine (Extreme Reactors 2) | 45.900.000 | 2.898 | 378 | 5 | 4 | 4 | 24 |
| Turbina Industrial + Fissão (Mekanism) | 53.485.714 | 75.770 | 10.970 | 10 | 18 | 7 | 0 |
| **Infinity Solar Panel** | **2.147.483.647** | **328.502 a 360.590** | **202.402 a 218.320** | **115** | **154** | **30** | **4.855** |

O que há dentro do painel Infinity, contado: 48 Infinity Catalysts (4 no painel e 11 em cada um dos 4 Infinity Ingots; cada um sai da Catalyst Forge do Modular Machinery com 27 itens de topo, 1 bilhão de FE e 20.000 mB de cryofluid), 48 Ultimate Singularities, 48 Creative Essences, 48 The Ultimate Ingots, 3.888 pellets de antimatéria, 456 Awakened Draconium, 69 Dragon Eggs, 66 Nether Stars, 48 cabeças de Wither Skeleton e 16 de Dragon Breath. Os maiores volumes: 47 mil lingotes de ferro, 46 mil fios de cobre, 23 mil folhas de borracha, 17 mil vidros, 15,5 mil sky stone, 13 mil de redstone e de certus quartz, 11,7 mil Cell Components 256k. Uma pergunta que o motor responde: o painel Neutronium (16,8 milhões FE/t) pede ~1.228 neutron nuggets só nos itens soltos (≈ 11 mil Neutron Piles do Neutron Collector, nenhuma receita) e 21 Dragon Eggs.

## O Ressonador, no mesmo motor

| Tier | FE/t máx. | N | crafts | matérias | intermediários | prof. | G | itens/h de manutenção |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Básico | 432 | 356 | 105 | 17 | 24 | 14 | 0 | 4,5 |
| Avançado | 27.648 | 562 | 211 | 18 | 26 | 14 | 0 | 9 |
| Elite | 60.000 | 868 | 385 | 20 | 30 | 14 | 14 | 15 |
| Esmeralda | 1.280.000 | 1.278 | 594 | 25 | 34 | 14 | 14 | 30 |
| Allthemodium | 64.000.000 | 2.064 | 1.016 | 27 | 36 | 14 | 39 | 82,5 |
| Vibranium | 80.400.000 | 3.618 | 1.857 | 31 | 39 | 16 | 61 | 99 |
| Unobtainium | 2.576.000.000 | 6.708 | 3.535 | 34 | 41 | 16 | 89 | 144 |
| Ultimate | 4.294.967.296 + Eco | 11.330 | 6.050 | 36 | 43 | 16 | 121 | 220 |

Manutenção por hora com o Núcleo no máximo: recargas (catalisador) mais os Cristais de Sinal do refazer; 4,5 a 220 itens. Os rivais ativos: o Rainbow Furnace queima ~6.240 carvões por hora, a Turbina com Fissão ~277 mil de urânio por hora, o Naquadah ~36 mil de naquadah por hora. Nenhum rival passivo (os painéis) tem manutenção.

## Pareamento tier a tier

| Tier (FE/t) | Rival de design (FE/t) | N: Ressonador × rival | crafts | G |
| --- | --- | --- | --- | --- |
| Básico (432) | Alternator (360) | 356 × 30 | 105 × 44 | 0 × 0 |
| Avançado (27.648) | Eclipse (23.040), não medido | 562 × ≥ 2 | 211 × ≥ 1 | 0 × 0 |
| Elite (60.000) | Powah Solar Nitro (50.000) e Rainbow (50.000) | 868 × 25.040 e 497 | 385 × 8.244 e 93 | 14 × 1 e 14 |
| Esmeralda (1.280.000) | Chaotic Solar (1.048.576) | 1.278 × 737 a 6.849 | 594 × 116 a 3.148 | 14 × 45 |
| Allthemodium (64.000.000) | Turbina + Fissão (53.485.714); mais barato com a mesma ordem: Extreme Turbine (45.900.000), Naquadah (39.200.000) | 2.064 × 75.770; 2.898; 3.509 | 1.016 × 10.970; 378; 223 | 39 × 0; 24; 0 |
| Vibranium (80.400.000) | Naquadah (39.200.000), Extreme Turbine (45.900.000) | 3.618 × 3.509; 2.898 | 1.857 × 223; 378 | 61 × 0; 24 |
| **Unobtainium (2.576.000.000)** | **Infinity (2.147.483.647)** | **6.708 × 328.502 a 360.590 (49× a 54×)** | **3.535 × 202.402 a 218.320 (57× a 62×)** | **89 × 4.855 (55×)** |
| **Ultimate (4.294.967.296)** | **Infinity (2.147.483.647), duas vezes** | **11.330 × 328.502 a 360.590 (29× a 32×)** | **6.050 × 202.402 a 218.320 (33× a 36×)** | **121 × 4.855 (40×)** |

## Leitura

1. **Do Básico ao Vibranium está justo.** No mesmo FE/t, o Ressonador gasta tanto quanto o gerador mais barato do tier (Extreme Turbine e Naquadah no Allthemodium e no Vibranium, 2,9 a 3,6 mil de N; Rainbow e Powah no Elite) e tem a manutenção mais leve do pack. A Turbina com Fissão, que pareei com o Allthemodium, é um mau negócio (75 mil de N para 53 milhões), não a régua.
2. **Unobtainium e Ultimate não estão justos:** produzem mais que o painel Infinity (e o Ultimate duas vezes mais) gastando de 1/30 a 1/60 da cascata dele, em itens, crafts e gargalos, e com um terço das matérias de base e dos intermediários.
3. **O degrau do Vibranium (80 milhões) para o Unobtainium (2,58 bilhões) é de 32×** e o pack só tem dois geradores acima dos 54 milhões: o painel Infinity (2,15 bilhões) e a Dyson Sphere (1 bilhão, milhões de blocos de Insanium). Qualquer gerador nessa faixa é um megaprojeto: Unobtainium e Ultimate precisam custar um megaprojeto ou gerar na faixa de quem não é um.
4. **Cada tier novo do Ressonador sobe o custo de ×1,5 a ×1,9 e a geração de ×1,3 a ×64.** A cascata do Ressonador é feita de ferro, cobre, ouro, redstone e Cristais de Sinal em volume (~70% do N do Ultimate); os itens de outros mods se resumem ao netherite, à esmeralda e aos metais do ATM nos Cartões. Não há nenhuma cadeia de máquina de outro mod (AE2, Draconic, Mekanism, Modern Industrialization) no meio, que é o que dá ao painel Infinity os 154 intermediários.

## Itens de outros mods que servem de cascata (medidos pelo mesmo motor, existem nas receitas do ATM10)

| Item | Mod | N | crafts | matérias | intermediários | G |
| --- | --- | --- | --- | --- | --- | --- |
| Supreme Machine Frame | Industrial Foregoing | 30 | 9 | 9 | 7 | 4 |
| Wyvern Core | Draconic Evolution | 41 | 5 | 4 | 2 | 1 |
| Draconic Energy Core | Draconic Evolution | 113 | 14 | 6 | 4 | 5 |
| Awakened Core | Draconic Evolution | 169 | 21 | 5 | 3 | 9 |
| Chaotic Core | Draconic Evolution | 1.085 | 135 | 6 | 6 | 445 |
| MI Processing Unit | Modern Industrialization | 3.186 | 1.207 | 27 | 25 | 0 |
| MI Quantum Circuit | Modern Industrialization | 12.750 | 4.828 | 30 | 26 | 0 |
| MEGA Cell Component 16M | MEGA Cells (AE2) | 18.835 | 8.331 | 13 | 22 | 0 |
| MEGA Cell Component 64M | MEGA Cells (AE2) | 56.513 | 24.996 | 14 | 23 | 0 |
| MEGA Cell Component 256M | MEGA Cells (AE2) | 169.549 | 74.991 | 14 | 24 | 0 |
| Crystal Matrix Ingot | Avaritia | 22 | 5 | 3 | 2 | 6 |
| Endest Pearl | Avaritia | 93 | 77 | 4 | 4 | 1 |

(O processador de acumulação e o de engenharia do AE2 e o Quantum Processor do Advanced AE saem com N quase zero no motor porque a receita é de prensa de inscritor com saída múltipla que ele não conta direito; ficaram de fora desta tabela.)

## Opções

O Ressonador tem duas alavancas: a **geração** (FE por cristal) e o **custo** (a cascata de crafting). O ponto de partida da escada atual está justo até o Vibranium; só os dois degraus de cima precisam mudar.

**1. Megaprojeto.** Mantém 2,58 e 4,29 bilhões e põe nos Corações e nos Cartões do Unobtainium e do Ultimate uma cascata do tamanho do painel Infinity: ~360 mil de N, ~215 mil crafts, ~115 matérias e ~150 intermediários (Unobtainium) e o dobro disso no Ultimate. Exemplo de composição: 1 MEGA Cell Component 256M (169,5 mil de N e 75 mil crafts) + Chaotic Cores (1.085 de N e 445 gargalos cada) + Awakened Cores + MI Quantum Circuits (12,7 mil de N cada) no Unobtainium; o dobro, mais Crystal Matrix e Endest Pearl, no Ultimate. Custo de vida do jogador: construir o Unobtainium passa a ser um projeto de várias horas de automação de AE2 e Draconic.

**2. Teto fora do megaprojeto.** Baixa o Unobtainium para ~150 milhões e o Ultimate para ~300 milhões (cerca de 3× e 6× a Turbina com Fissão, que é o maior gerador que não é megaprojeto) e sobe o custo uma ordem de grandeza (~60 mil de N: um MEGA Cell Component 64M e dois Chaotic Cores no Unobtainium; o dobro no Ultimate). Mexe no FE por cristal, na energia do roteador e na migração do balanceamento 4.

**3. Misto.** Unobtainium na opção 2 e o Ultimate como megaprojeto da opção 1 (o Eco já faz dele o gerador sem teto; é o item de fim de jogo).

## Arquivos de apoio

Motor e dados na pasta de rascunhos da sessão (`load.py`, `eng2.py`, `riv2.py`, `res2.py`, `final_riv.py`, `riv_final.json`, `res2.json`, `cands.json`); não fazem parte do repositório. Rodar o `final_riv.py` refaz a tabela dos rivais; o `res2.py`, a do Ressonador.
