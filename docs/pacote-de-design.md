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
`WA_SCREENSHOT_ONLY=blocos` (os armazenamentos em dois tiers e dois ângulos), com o `runClient` sob Xvfb;
a prévia isométrica `docs/preview/blocos-preview.png` sai do próprio script.
