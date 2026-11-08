# Balanceamento das vazões e capacidades fora da energia: análise

Análise de 08/11/2026, a pedido do dono ("faça uma análise para balancear tudo além de energia; hoje só houve um rebalance da energia e não do resto; repensar se a vazão está sendo atingida"). Não muda nada: levanta o que há, mede o que o motor entrega de fato e propõe opções com as perguntas para o dono decidir. A energia (roteador e Bateria) fica de fora: ela já está presa ao Ressonador do mesmo tier (`EnergyBalance`, 22/10).

## 1. O que há hoje

A escada é ×8 por degrau em tudo, decidida em 08/10 (`2026-10-08-tiers-esmeralda-e-atm-design.md`), com os tiers vanilla modestos de propósito para os do Allthemodium serem um salto.

| Tier | Itens/s | Fluido e químico (mB/s) | Source/s | Baú (itens) | Tanque e Tanque Químico (mB) | Tanque de Source |
| --- | --- | --- | --- | --- | --- | --- |
| Básico | 32 | 2.000 | 100 | 32.768 | 256.000 | 10.000 |
| Avançado | 256 | 16.000 | 800 | 262.144 | 2.048.000 | 80.000 |
| Elite | 2.048 | 128.000 | 6.400 | 2.097.152 | 16.384.000 | 640.000 |
| Esmeralda | 16.384 | 1.024.000 | 51.200 | 16.777.216 | 131.072.000 | 5.120.000 |
| Allthemodium | 131.072 | 8.192.000 | 409.600 | 134.217.728 | 1.048.576.000 | 40.960.000 |
| Vibranium | 1.048.576 | 65.536.000 | 3.276.800 | 1.073.741.824 | 8.388.608.000 | 327.680.000 |
| Unobtainium | 8.388.608 | 524.288.000 | 26.214.400 | 8.589.934.592 | 67.108.864.000 | 2.621.440.000 |
| Ultimate | sem limite | sem limite | sem limite | sem limite | sem limite | sem limite |

A energia, para comparar o método: roteador = geração máxima do Ressonador do tier + 5%; Bateria = uma hora dessa geração (454 FE/t e 31,1 M FE no Básico; 158 M FE/t e 10,79 T FE no Unobtainium).

## 2. A vazão configurada é atingida?

Sim, em todos os tiers com limite, quando origem e destino não são o gargalo. Medido pelos GameTests `VazaoMedidaGameTests` (um lote por caso, 60 ticks de itens e 40 de fluido depois de 10 de aquecimento, linhas `MEDIDA vazao` no log da run comum; rodada de 08/11, orçamento padrão de 1 ms):

| Tier | Itens, gaveta → ralo (uma pilha por extração) | Itens, Baú do mod → Baú do mod | Fluido, tanque comum → tanque de entrada | Fluido, Tanque do mod → Tanque do mod |
| --- | --- | --- | --- | --- |
| Básico | 32/s (100%) | 32/s (100%) | 2.000 mB/s (100%) | 2.000 (100%) |
| Avançado | 256 (100%) | 256 (100%) | 16.000 (100%) | 16.000 (100%) |
| Elite | 2.048 (100%) | 2.048 (100%) | 128.000 (100%) | 128.000 (100%) |
| Esmeralda | 16.384 (100%) | 16.384 (100%) | 1.024.000 (100%) | 1.024.000 (100%) |
| Allthemodium | 131.072 (100%) | 131.072 (100%) | 8.192.000 (100%) | 8.192.000 (100%) |
| Vibranium | 1.048.576 (100%) | 1.048.576 (100%) | 65.536.000 (100%) | 65.536.000 (100%) |
| Unobtainium | 9.074.961 (108%, o balde de um segundo que começa cheio) | 8.388.608 (100%) | 524.288.000 (100%) | 524.288.000 (100%) |
| Ultimate | 32,4 milhões/s, preso no orçamento (1,3 ms/tick) | 40 bilhões/s (um tipo inteiro por chamada) | 34 bilhões mB/s (o teto de `int` por chamada) | 40 bilhões (um tipo por chamada) |

Leitura:

- O limitador de vazão e o laço entregam o número da config até o Unobtainium, mesmo com uma pilha de 64 por extração (7 mil extrações por tick no Unobtainium, em 0,37 ms). Não há perda estrutural no motor.
- O Ultimate é limitado só pelo orçamento de 1 ms (`tickBudgetMs`) e pelo custo por chamada da máquina do outro lado; entre os armazenamentos do mod, por um tipo inteiro por chamada, ele passa de bilhões por segundo.
- Logo, quando o jogador vê menos que a tabela, a causa está fora do limitador: (a) a máquina não produz nem aceita naquele ritmo (a maioria dos casos); (b) um inventário comum entrega uma pilha por extração e o destino uma pilha por slot, e inventários grandes de outros mods custam dezenas de µs por chamada (o barril do Sophisticated a 50 µs por entrega segura o Ultimate em 40 mil/s, `docs/benchmark.md`); (c) a latência do sono: um destino cheio ou uma origem vazia é reencontrada em até 5 s (itens) ou 1 s (fluidos, químicos, energia, Source) quando a máquina muda sem avisar (as do Mekanism não chamam `setChanged`); (d) o orçamento adaptativo, que cai para 0,25 ms com o servidor acima de 40 ms de MSPT; (e) os dois bugs corrigidos em 08/11 (o cursor preso numa saída da máquina e o tanque seletivo dormindo como cheio), que paravam de vez a segunda saída de uma máquina.

## 3. O que a escada atual quer dizer, em tempo

A energia foi presa a um tempo: a Bateria é uma hora do roteador. Os outros tipos nunca foram, e o resultado é que o mesmo tier conta tempos muito diferentes para encher o próprio armazenamento por uma face (a razão é a mesma em todos os tiers, porque vazão e capacidade sobem ×8 juntas):

| Tipo | Capacidade ÷ vazão do mesmo tier | Em tempo |
| --- | --- | --- |
| Energia (Bateria ÷ roteador) | 31,1 M ÷ 454 FE/t ≈ 68.500 ticks | ~57 min (uma hora por desenho) |
| Itens (Baú ÷ face) | 32.768 ÷ 32 | 1.024 s ≈ 17 min |
| Fluido e químico (Tanque ÷ face) | 256.000 ÷ 2.000 | 128 s ≈ 2 min |
| Source (Tanque ÷ face) | 10.000 ÷ 100 | 100 s ≈ 1,7 min |

Ou seja: um Tanque enche em dois minutos pela face do próprio tier e um Baú leva dezessete; a Bateria, uma hora. Isso é uma escolha a fazer, não um erro, mas hoje não foi escolhida.

### Referências do ATM10 (para situar os tiers vanilla)

Números de fontes secundárias, com a versão nem sempre a do pack; servem de ordem de grandeza, não de verdade. A energia fica de fora.

| Transporte | Itens | Observação |
| --- | --- | --- |
| Funil vanilla | 2,5/s | 1 item a cada 8 ticks |
| Pipez sem upgrade → Ultimate | 1/s → 64 por tick (1.280/s) | guia de terceiros; Básico 4 por 20 ticks, Melhorado 16 por 10, Avançado 32 por 5 |
| LaserIO, cartão de itens | 8/s → 64 por tick (1.280/s) com 4 overclockers | wiki StarT |
| Modular Routers | 1/s → 64/s com 6 Stack Upgrades; até 640/s com velocidade | guias de terceiros divergem (todo tick × tick sim tick não) |
| AE2, Import Bus | 1 item por operação, 4 operações/s; 8 e 32 itens por operação com 1 e 2 cartões (≈ 128/s) | guia oficial do AE2 1.21 |
| Wireless Automate | 32 → 2.048/s nos tiers vanilla; 16 mil a 8,4 mi nos do ATM | |

Fluidos: as fontes sobre os canos do Mekanism se contradizem (o Elite entre 1.600 mB/s e 16.000 mB/t conforme a página); a única leitura coerente achada dá 250, 1.000 e 4.000 mB/t do Básico ao Elite (5.000 a 80.000 mB/s). Sem número confiável para o Pipez de fluido nem para os tubos de químico. Source: o Sourcelink deposita a cada 5 s com buffer de 20.000 e a Source Jar guarda 10.000; não achei a vazão do Source Relay. Fontes no fim.

Leitura das referências:

- **Itens:** o Básico (32/s, treze funis) já está entre o Pipez Melhorado e o Avançado; o Avançado (256/s) passa qualquer cano comum do pack; o Elite (2.048/s) é o maior transporte de itens do ATM10 sem contar o Ultimate de ninguém. A escada vanilla de itens está coerente com "modesta no começo, salto no ATM".
- **Fluido e químico:** o Básico (2.000 mB/s = 100 mB/t) parece ficar abaixo do cano básico do Mekanism (250 mB/t, se a fonte vale) e o Elite (6.400 mB/t) abaixo do Elite dele (4.000 mB/t? ou 16.000?). Em "baldes por segundo", o Básico move 2 baldes/s contra 32 itens/s: se o jogador pensa num balde como um item, fluido é 16× mais lento que item no mesmo tier; se pensa num balde como uma pilha, é 4× mais rápido. É a equivalência que falta decidir.
- **Source:** 100/s no Básico enche uma Source Jar em 100 s e um Sourcelink cheio (20.000) leva 200 s para esvaziar por uma face. É o tipo com menos referência; a vazão do Relay decidiria.

## 4. Opções

Três caminhos, do menos ao mais invasivo. Em todos, a escada continua ×8 por degrau e o Ultimate sem limite; só o ponto de partida (o Básico) e a razão capacidade ÷ vazão mudam. A migração da config é a mesma de sempre (`migration.balanceVersion`, só os valores ainda no padrão antigo).

**Opção A, não mexer na vazão, só documentar.** As vazões são atingidas e os tiers vanilla cumprem "modesto no começo". Falta só escrever a regra de tempo por tipo na especificação (17 min, 2 min, 1,7 min) para a próxima pessoa saber que foi escolhido.

**Opção B, uma regra de tempo para os armazenamentos (como a Bateria), sem mexer na vazão.** Escolhe-se quanto tempo uma face do tier leva para encher o armazenamento do tier, e a capacidade sai da vazão. Com dez minutos (12.000 ticks), que mantém os armazenamentos na ordem de grandeza de hoje:

| Tipo | Capacidade hoje (Básico) | Com 10 min | Com 1 h (como a Bateria) |
| --- | --- | --- | --- |
| Baú | 32.768 | 19.200 | 115.200 |
| Tanque e Tanque Químico | 256.000 mB | 1.200.000 mB | 7.200.000 mB |
| Tanque de Source | 10.000 | 60.000 | 360.000 |

(Os outros tiers seguem ×8.) O Baú perde um pouco e os tanques ganham bastante; com uma hora tudo cresce. Custo zero no motor: capacidade é só um número da config e o `KeyedStorage` já é `long`.

**Opção C, rever o Básico de fluido, químico e Source e deixar os itens.** Se a equivalência escolhida for "um balde é um item" ou "o Básico deve acompanhar um cano básico do Mekanism", o fluido do Básico sobe para 8.000 ou 16.000 mB/s (400 ou 800 mB/t), com a escada ×8 em cima (Elite 512 mil ou 1 M mB/s, Unobtainium 2 ou 4 bilhões mB/s, dentro do `long`; a visão em `int` dos tanques de outros mods limita cada chamada a 2,1 bilhões e o Tanque do mod não tem esse teto). O químico acompanha o fluido (mesma chave `fluidPerSecond`). A Source sobe quando houver a vazão do Relay para ancorar (se um Relay move ~1.000/s, o Básico em 100/s é dez vezes menor que o cano mais simples do Ars, o que foge do "modesto"). Os tiers do ATM ficam no salto ×8 e continuam sem rival. Custo no motor: nenhum (o limitador já foi medido a 524 M mB/s).

Recomendação: B com dez minutos para os tanques e C só para a Source quando houver a referência; itens e fluido como estão até o dono dizer qual equivalência quer (pergunta 2). A opção A é o mínimo em qualquer caso.

## 5. Perguntas para o dono (com a resposta recomendada)

1. **Que tempo uma face do tier deve levar para encher o armazenamento do mesmo tier?** Hoje é 17 min nos itens, 2 min nos fluidos e químicos, 1,7 min na Source e 1 h na energia. Recomendado: fixar dez minutos para Baú, Tanques e Tanque de Source (tabela da opção B) e manter a hora da Bateria, que tem outro papel (guardar a noite do gerador).
2. **Um balde vale quanto em itens para a sua escada?** Se um item, a vazão de fluido do Básico deveria ser ~32.000 mB/s (hoje 2.000); se uma pilha, 500 mB/s (hoje já passa); se "fluido é a sua própria escada", fica como está. Recomendado: tratar o Básico de fluido pelo cano básico do Mekanism (cerca de 250 mB/t = 5.000 mB/s) e arredondar para 8.000 mB/s (400 mB/t), com o resto ×8; é o único ponto em que o Wireless Automate fica atrás do cano mais simples do pack.
3. **Químico segue o fluido (mesma chave e mesma vazão) ou ganha escada própria?** Recomendado: seguir o fluido, como hoje. Os tubos de pressão do Mekanism são menores que os canos de fluido, então o químico já fica confortável.
4. **Source: ancorar em quê?** Recomendado: medir no jogo a vazão de um Source Relay e de um Sourcelink com o Ars da versão do pack (um teste à mão de dois minutos) e pôr o Básico perto do Relay; até lá, não mexer.
5. **O Ultimate continua "sem limite, preso no orçamento"?** Recomendado: sim. Medido, ele move 32 milhões de itens por segundo de uma gaveta com 1 ms, e bilhões entre armazenamentos do mod; qualquer número seria menor que isso.
6. **O Básico de itens em 32/s fica?** Recomendado: sim. É treze funis e já passa o Pipez com upgrade Melhorado; subir tiraria o sentido do Avançado.

## 6. O que fazer depois da decisão

- Trocar os padrões no `ResourceType` (vazão) e no `StorageKind` (capacidade), a tabela da especificação e do guia, e escrever a migração 6 em `Config.migrateBalance` (só os valores ainda no padrão de hoje).
- Rodar os `VazaoMedidaGameTests` de novo: eles leem a config e conferem que o número novo é atingido.
- Atualizar o changelog com a tabela antes → depois, como no rebalance da energia.

## Fontes das referências

- Funil vanilla: conhecimento geral do jogo (1 item a cada 8 ticks).
- Pipez: [Craft Down Under, Pipez Guide](https://craftdownunder.co/guides/mods/pipez); [Minecraft Guides, Pipez](https://www.minecraft-guides.com/mod/pipez/).
- LaserIO: [StarT Wiki, Transportation Methods](https://start-dev-team.github.io/StarT-Wiki/Guides/Transportation-Methods/Items/).
- Modular Routers: [Craft Down Under, Modular Routers Guide](https://craftdownunder.co/guides/mods/modular-routers); [wiki do ModularRouters](https://vat.pages.dev/desht/ModularRouters/blob/MC1.21-master/wiki.md).
- AE2: [Import Bus](https://guide.appliedenergistics.org/1.21/items-blocks-machines/import_bus) e [Upgrade Cards](https://guide.appliedenergistics.org/1.21/items-blocks-machines/upgrade_cards), guia oficial 1.21.
- Mekanism: [Mechanical Pipe, wiki do Mekanism](https://wiki.aidancbrady.com/wiki/Mechanical_Pipe); [FTB wiki, Elite Mechanical Pipe](https://ftb.fandom.com/wiki/Elite_Mechanical_Pipe); [Minecraft Guides, Pipes and Tubes](https://www.minecraft-guides.com/mod/mekanism/wiki/pipes-and-tubes/).
- Ars Nouveau: [All The Guides, Source](https://allthemods.github.io/alltheguides/atma/arsnouveau/source); [ars.guide, Source Relay](https://ars.guide/book/source/block.ars_nouveau.relay/).
