# Wireless Automate · pacote de design

Transporte wireless de itens, fluidos, energia, químicos e Source para NeoForge 1.21.1 (ATM10). Este
documento descreve os sprites, os ícones e os modelos do mod e como regenerá-los. Versão de 10 de
outubro de 2026, na identidade visual **"Porcelana e Sinal"**: a ideia, a paleta, as regras e as
tabelas de cor estão em [`identidade-visual.md`](identidade-visual.md); leia aquilo antes de desenhar
qualquer coisa.

## Onde está cada coisa

```
scripts/textures/
├── identidade.py       paleta (PALETA, LENTE, RECURSO, MODO), o Olho (olho), Sprite, animacao, salvar
├── gerar_texturas.py   itens e ícones da interface, modelos item/tier_core_<tier>.json e a folha
└── blocos.py           texturas, modelos 3D e blockstates dos blocos (roteador e armazenamentos)
docs/preview/folha-de-sprites.png   a folha com tudo (gerada)
src/main/resources/assets/wirelessautomate/
├── textures/item/      13 itens, todos animados (PNG + .mcmeta)
├── textures/gui/       portas da tela e, em type/, os ícones dos tipos de recurso
├── textures/block/     texturas dos blocos (de blocos.py)
├── models/item/        item/generated para os itens; os dos blocos vêm de blocos.py
└── models/block/, blockstates/   de blocos.py
```

Todas as texturas de `textures/item`, `textures/gui` e `textures/block` são geradas: o script apaga,
ao fim de cada rodada, o que não saiu dela.

## Como regenerar

```bash
python scripts/textures/gerar_texturas.py            # grava PNGs, .mcmeta, modelos e a folha
python scripts/textures/gerar_texturas.py --so-folha # só refaz docs/preview/folha-de-sprites.png
```

Só Python 3 com Pillow. O `gerar_texturas.py` importa `identidade.py` e, se existir, `blocos.py`
(`sprites()`, `modelos()`, `previas()`); sem ele, avisa e gera só os itens e os ícones (e não mexe
em `textures/block`). No fim imprime quantos sprites gravou e quantos arquivos velhos apagou.

Cada item é uma grade de texto (a forma, pela legenda comum `LEG`: porcelana `W p q r s`, grafite
`G g h i j`, coral `C c d e f`, latão `L l m n`, vidro `V v u t`, e `x` onde o Olho entra) mais um
pouco de código para o que muda por quadro. Para ajustar uma cor, mude a paleta em `identidade.py`;
para mexer no desenho, mude a grade.

Regras que o script segue (e `validar` confere):

- 16×16 RGBA, alfa só 0 ou 255 (vidro translúcido só nos blocos que pedem);
- 3 a 5 tons por material, luz de cima à esquerda;
- itens com contorno de 1 px em `grafite.0` (`contorno`), fechado, sem pixel solto;
- todo item animado: quadros empilhados na vertical e `frametime` no `.mcmeta`; o último quadro emenda
  com o primeiro;
- mesma quantidade de detalhe em todos os itens.

## Os itens (`textures/item/`)

Cada um é um aparelho reconhecível, com o Olho (a lente da marca) no centro ou na ponta. Os aparelhos
sem tier (Configurador, Vinculador, Tablet, Filtro) levam uma lente de vidro; os Cartões de Upgrade,
a cor da lente do tier; o Guia, uma lente de porcelana; o chunk loading, a chama verde da Lanterna.

| Arquivo | Aparelho | Animação | Quadros × frametime |
| --- | --- | --- | --- |
| `configurator` | caneta-sonda na diagonal: tampa de grafite com clipe coral, anel de latão, corpo de porcelana, pegada de grafite, ponta com o Olho | a ponta pisca (a lente clareia e a onda sai) | 4 × 8 |
| `linker` | transceptor de mão: antena de grafite com bolinha, painel de grafite com a lente e um contato de latão, listra coral, janela de vidro com o medidor de 3 barras | as barras sobem e descem | 6 × 5 |
| `network_tablet` | prancheta de porcelana com clipe, moldura de grafite, tela de vidro arredondada com dois blips, Olho embaixo | varredura de radar (um raio gira, com rastro de dois tons) | 8 × 4 |
| `filter_card` | cartão perfurado de porcelana com o canto cortado, visor (Olho), listra coral, três linhas de furos de grafite | uma faixa de luz corre pelos furos, coluna a coluna | 6 × 5 |
| `tier_core_<tier>` (7) | válvula: bulbo de vidro, filamento em zigue-zague na cor da lente do tier, colar de porcelana com as `MARCAS` (1 a 4 traços), base de grafite e pinos de latão (nos tiers do Allthemodium, pinos na cor do tier) | o filamento pulsa (e esquenta o vidro em volta) e um brilho sobe pela parede do bulbo | 8 × 4 |
| `chunk_loader_upgrade` | Lanterna de Vigia: alça de grafite (com o furo aberto depois do contorno), tampa de porcelana com o anel coral, gaiola de grafite (dois montantes e uma barra fina no meio) em volta do vidro, base de grafite; dentro, a chama verde (`chunk`: borda `.1`, corpo `.2`, miolo `.3`) com um halo esverdeado no vidro em volta, "acesa enquanto você está longe" | a chama tremula: muda de altura e inclina para os lados (`CHAMA`, uma grade por quadro) | 6 × 5 |
| `guide` | caderno de campo: capa coral com o Olho em porcelana e uma etiqueta, espiral de grafite na lombada, páginas aparecendo à direita e embaixo | o Olho da capa pulsa | 4 × 8 |

Todos os modelos de item são `minecraft:item/generated` com `layer0` na textura; os
`tier_core_<tier>.json` são gravados pelo script (um por tier de `TIERS_CARTAO`).

## A interface (`textures/gui/`)

Pensados para o fundo claro das telas (`porcelana.3`, `#F2EBDD`); são sprites parados.

| Arquivo | Desenho |
| --- | --- |
| `port_extract` | tecla quadrada de porcelana (de (1,1) a (14,14), cantos de 1 px cortados): fundo `porcelana.4`, borda de 1 px `grafite.2`, luz branca em cima e à esquerda e sombra de tecla `porcelana.1` embaixo e à direita, dentro da borda; no centro, uma seta grossa para cima (haste de 3 px, cabeça de 7 px) na cor do modo (`MODO["extract"]`, azul), com 1 px de sombra ×0,75 embaixo e à direita e contorno de 1 px `grafite.2` |
| `port_insert` | a mesma tecla com a seta para baixo (laranja) |
| `port_both` | a tecla com a seta dupla (verde): cabeça em cima e embaixo, haste curta: Armazém |
| `port_none` | a tecla apagada: fundo `porcelana.2`, borda tracejada `grafite.4` (traço de 2 px, vão de 1) e um traço horizontal de 5×2 px em `porcelana.0` no centro, no lugar da seta |
| `type/item`, `type/fluid`, `type/energy`, `type/chemical`, `type/source` | crachás 9×9 no canto de cima à esquerda da textura 16×16 (a tela recorta 9×9): um disco cheio de 9 px na cor do recurso (`RECURSO`; tom cheio, faixa de 1 px ×0,75 embaixo e à direita, um pixel de brilho em cima à esquerda) com contorno de 1 px `grafite.1` e, dentro, um símbolo branco de traço grosso, 5×5 no máximo, com 1 px de sombra do tom escuro logo abaixo: saco amarrado (Itens; as orelhas do laço, o nó escuro e o corpo redondo), gota (Fluidos), raio (Energia), frasco erlenmeyer (Químicos) e estrela de 4 pontas com um ponto (Source) |

São "crachás de linha de metrô": formas cheias, uniformes e de alto contraste, pensadas para ler em
1× sobre os dois fundos das telas (`porcelana.3` e `porcelana.4`), sem objetos miúdos. Os ícones
anteriores (setas soltas, depois o Olho emitindo sinal e objetos de 9 px) foram rejeitados por virarem
manchas no tamanho real. As portas também são desenhadas apagadas (alpha 0,5) nos botões das faces e
continuam legíveis; a prancha `docs/preview/icones-preview.png` mostra os nove ícones em 1×, 2×, 4× e
8× sobre os dois fundos, as portas também apagadas e uma fila como nos botões.

## A folha (`docs/preview/folha-de-sprites.png`)

Fundo porcelana claro e rótulos em grafite. Seções: os itens (cada quadro da animação a 6×, mais o
item a 1×, 2× e 3× para avaliar no tamanho real), os ícones da interface (a 6×, 1× e 2×; a prancha própria fica em
`icones-preview.png`) e, quando
`blocos.py` existe, as texturas dos blocos (primeiro quadro, 4×) e as prévias montadas que
`blocos.previas()` devolve.

## Os blocos

Texturas, modelos 3D (com elementos; nada de cubo liso) e blockstates do roteador e dos
armazenamentos saem de `scripts/textures/blocos.py`, pela mesma `identidade.py`. A forma de cada
bloco está na tabela "Os blocos" de `identidade-visual.md`; o `gerar_texturas.py` só chama
`blocos.sprites()`, `blocos.modelos()` e `blocos.previas()` e monta a folha.

Cada modelo passa por `validar_modelo` (texturas, UVs, ângulos) e por `validar_geometria`, que falha a
geração se duas faces desenhadas dividirem o mesmo plano com área em comum (pisca no jogo), se duas
caixas se atravessarem (salvo as marcadas `dentro=True`, o conteúdo das colunas de vidro) ou se uma face
omitida não estiver inteiramente coberta por um vizinho encostado (buraco). Para conferir no jogo:
`WA_SCREENSHOT=... WA_SCREENSHOT_ONLY=giro` (roteador na parede e no chão, nos quatro giros) e
`WA_SCREENSHOT_ONLY=blocos` (os armazenamentos em dois tiers e dois ângulos, com o contorno da colisão,
e a fileira dos itens como aparecem no inventário), com o `runClient` sob Xvfb; a prévia isométrica
`docs/preview/blocos-preview.png` sai do próprio script.

### A vista de GUI do roteador (`display`)

O roteador é o único bloco com frente (a fenda do Olho e o interior da parabólica ficam no sul do modelo
`up`), e a vista `gui` padrão do `block/block` vanilla (`rotation [30, 225, 0]`) mostrava o lado de trás
no inventário, na aba criativa e no JEI. Os modelos `router_<tier>.json` levam um bloco `display`
próprio (`DISPLAY_ROTEADOR` em `blocos.py`): as entradas do vanilla copiadas (mão em primeira e terceira
pessoa, chão e moldura, que já é de frente com `rotation [0, 0, 0]`) e a `gui` girada 180° em y,
`rotation [30, 45, 0]`, com a mesma inclinação de 30° e a escala 0,625. Os `router_<tier>_spinN` herdam.
Os armazenamentos têm as quatro laterais iguais e ficam com o `display` do vanilla.

### As caixas de colisão e seleção (hitboxes)

As caixas abraçam o desenho, sem ar sobrando nem nada do modelo de fora, como o Source Jar do Ars.
Estão em dois lugares que precisam andar juntos: `HITBOXES` em `blocos.py` (a fonte para conferir: a
geração falha se algum elemento, a parabólica pela caixa envolvente do prato rotacionado, ficar fora da
união das caixas, se uma caixa não tocar nenhum elemento ou sair de 0..16) e o Java que o jogo usa:
`block/RouterShapes.UP_BOXES` (roteador com `facing=up` e `spin=0`; a classe gira por spin e facing) e
`storage/StorageShapes.java` (por id do bloco, lógica pura com `StorageShapesTest`; o `StorageBlock`
monta o `VoxelShape`, o mesmo em todos os tiers e, no Tanque de Source, nos 11 níveis). A cada geração o
script grava `docs/preview/hitboxes.txt` com a envolvente de cada elemento e do bloco inteiro ao lado das
caixas. Em pixels (`x0 y0 z0  x1 y1 z1`):

| Bloco | Caixas |
| --- | --- |
| Roteador (6) | prato da base com os para-choques `1 0 2  15 3 14`; corpo `3 1 4  13 5 12`; hastes de trás `2 3 2,5  3 12 3,5` e `13 3 2,5  14 12 3,5`; mastro `7 5 7  9 9 9`; parabólica, braço e receptor `5 9 2,75  11 14,5 8,25` (envolvente do prato a −45°: y até 14,3, z 2,76..8,06) |
| Baú (10) | quatro pés `1 0 1  4 2 4` e espelhos; corpo `1 2 1  15 14 15`; tampa `0 14 0  16 16 16`; uma caixa de gavetas por lado, `2 2 15  14 13 16` e as outras três |
| Tanque (3) | pés e base `2 0 2  14 4 14`; anéis, coluna de vidro e réguas `3 4 3  13 14 13`; tampa `2 14 2  14 16 14` |
| Bateria (10) | pés e plinto `1 0 1  15 2 15`; células `2 2 2  14 13 14`; quatro visores `6 2 14  10 13 15` e os outros três; cornija `1 13 1  15 14 15`; tampa `2 14 2  14 15 14`; terminais `3 15 3  5 16 5` e `11 15 11  13 16 13` |
| Tanque Químico (6) | pés e anel `3 0 3  13 2 13`; pescoço `4 2 4  12 3 12`; ombro de baixo `3 3 3  13 5 13`; equador `2 5 2  14 11 14`; ombro de cima `3 11 3  13 13 13`; calota e volante `4 13 4  12 15 12` |
| Tanque de Source (4) | para-choques `2 0 2  14 1 14`; base, trilhos, coluna e tampa `3 0 3  13 13 13`; colar `6 13 6  10 14 10`; gema `6,5 14 6,5  9,5 16 9,5` |

O ar que sobra é o que fica entre pés (até 2 px de altura, debaixo do corpo), entre os para-choques do
roteador e nos cantos das colunas (as réguas e os trilhos de 1 px). Os GameTests `shapeFollowsSpin` e
`shapeMatchesRelativeSides` (`RouterConfigGameTests`) conferem que a forma do roteador acompanha o
`spin` e os lados relativos (hastes no BOTTOM, parabólica inclinada para o BOTTOM, nada do lado TOP) e
`storageShapesHugTheModel` (`StorageGameTests`) que os armazenamentos ficam dentro do bloco, encostam no
chão, têm topo para pisar (≥ 15 px) e não são um cubo cheio. Os armazenamentos continuam com
`noOcclusion()`.
