# Ressonador de Sinal: a Armilar (design)

Aprovado pelo dono em 16/11/2026 (página da ideia: https://claude.ai/artifact/AH6DSgUxZ4bL3EzTENTGy8). Substitui a estrutura 5×5×5 oca da spec `2026-10-15-ressonador-de-sinal-design.md` (seção "Estrutura" e as decisões da etapa 2 sobre peças, vidro, montagens de exemplo e projeção). O resto daquela spec continua valendo: Núcleo, câmaras, cristais, sintonia, geração, catalisador, eco e interferência.

## O que muda

O Ressonador deixa a caixa oca e vira a esfera armilar do conceito A, a que o dono escolheu entre três. São 35 blocos montados à mão que, formados, viram a escultura do conceito, e o render desenha o sol e as três órbitas girando no meio.

- Os protótipos estão em `C:\Users\matheuss\Desktop\Sistemas\Wireless-Automate\ressonador-conceitos\`:
  - `gerar_conceitos.py` (a escultura do conceito, função `armilar()`);
  - `armilar_formado.py` (a escultura fatiada na grade);
  - `armilar_blocos.py` (os blocos soltos);
  - `filme/formado.html` (a renderização).
- A forma montada tem de ficar igual ao conceito. Muda só o que a grade obriga (seção "Forma montada").

## Os blocos

Sete tipos. Os ids que já existiam continuam.

| Id | pt_br | en_us | Quantos | Onde |
| --- | --- | --- | --- | --- |
| `resonator_casing` | Laje do Ressonador | Resonator Floor | 21 (22 sem Sintonizador) | chão |
| `resonance_core` | Núcleo de Ressonância | Resonance Core | 1 | chão, meio da frente |
| `resonator_energy_port` | Porta de Energia | Resonator Energy Port | 1 | chão, meio do lado direito |
| `resonator_crystal_port` | Porta de Cristais | Resonator Crystal Port | 1 | chão, meio do lado esquerdo |
| `resonator_tuner` | Sintonizador | Resonator Tuner | 0 ou 1 | chão, meio do lado de trás |
| `resonator_column` (novo) | Coluna do Ressonador | Resonator Column | 8 | as 4 casas diagonais em volta do centro, nas camadas 2 e 3 |
| `resonator_cradle` (novo) | Berço do Sol | Sun Cradle | 2 | centro, nas camadas 2 e 3 |

"Direita" e "esquerda" são de quem olha a frente do Núcleo de fora.

- Uma Porta de Energia e uma de Cristais, nos lugares fixos do conceito. Isso substitui a decisão de 15/10, que aceitava mais de uma de cada.
- O `resonator_glass` sai. Um alias leva ao `resonator_casing`, no registro de blocos e no de itens, e os blocos e itens de Vidro dos mundos viram Laje.
- O Aro do Horizonte, proposto numa rodada, não existe: o horizonte se apoia nas pernas.

## Regra da montagem

As coordenadas são locais, no referencial com a frente para o sul: x de 0 (oeste) a 4 (leste), y de 0 (chão) a 4 e z de 0 (trás, norte) a 4 (frente, sul). O Núcleo fica em (2, 0, 4).

| Camada | Casas | Bloco |
| --- | --- | --- |
| y = 0 | (2, 4) | o Núcleo |
| y = 0 | (4, 2) | Porta de Energia |
| y = 0 | (0, 2) | Porta de Cristais |
| y = 0 | (2, 0) | Sintonizador ou Laje |
| y = 0 | as outras 21 | Laje |
| y = 1 e 2 | (1, 1), (3, 1), (1, 3), (3, 3) | Coluna |
| y = 1 e 2 | (2, 2) | Berço do Sol |
| y = 1 e 2 | as outras 20 | ar |
| y = 3 e 4 | todas as 25 | ar |

- **Orientação:** a frente vem do lado onde o Núcleo está. A busca tenta primeiro a frente que o Núcleo já tem (`FACING`) e depois as outras três horizontais.
  - Uma caixa sem erro monta.
  - Se nenhuma monta, vale a de menos erros, como hoje.
  - A frente é sempre horizontal: o Núcleo não monta virado para cima nem para baixo.
- **Problemas:**
  - `WRONG_BLOCK`: bloco errado numa casa.
  - `INTERIOR_NOT_EMPTY`: algo onde deve haver ar.
  - `EXTRA_CORE`: outro Núcleo dentro da caixa.
  - `MISSING_ENERGY_PORT` e `MISSING_CRYSTAL_PORT`: a casa da Porta tem outra coisa. A posição do problema é a da casa.
  - `SHARED_WALL`: como hoje.
  - `TOO_MANY_TUNERS` sai, porque a casa do Sintonizador é uma só.
- **Membros:** os 35 blocos que não são ar.
- **Custo:** a regra lê as mesmas 125 casas, só por evento de bloco, como hoje. O `ResonatorManager.REACH` (4) continua cobrindo: a casa mais longe do Núcleo fica a distância de Chebyshev 4.

## Estados de bloco

O `facing` do Núcleo e das peças passa a ser horizontal (`HORIZONTAL_FACING`, o nome continua `facing`). Num mundo antigo, um valor `up` ou `down` cai no padrão.

### Laje

- **`position`:**
  - `loose`, mais uma posição por casa de Laje do chão no referencial local: `p<x><z>`, com as 22 casas que não são o Núcleo nem as Portas (`p20` é a casa do Sintonizador quando ele falta).
  - Os valores antigos (`nnn`...) não existem mais e caem em `loose` na carga.
- **`facing`:** a frente da estrutura.
- **Modelo:** um por posição, feito com a frente para o sul. O blockstate gira pelo `y`: sul 0, oeste 90, norte 180 e leste 270.

### Coluna

- **`part`:** `loose`, `lower` ou `upper`.
- **`corner`:** `se`, `sw`, `nw` ou `ne`, o canto de fora da casa diagonal onde fica a perna. É absoluto no mundo, porque as pernas são simétricas.
- **Modelos:** dois, feitos para `se`. O blockstate gira pelo `y`: `se` 0, `sw` 90, `nw` 180 e `ne` 270.

### Berço

- **`part`:** `loose`, `lower` ou `upper`, sem giro.

### Núcleo e Portas

- **Estado:** `facing` e `formed`, como hoje; o Núcleo ainda tem `tier`.
- **Modelo formado:** o pedaço da escultura na sua casa, feito com a frente para o sul e girado pelo `y` como a Laje.

A formação grava tudo isso e a desmontagem volta tudo a solto. Os `setBlock` usam `UPDATE_CLIENTS | UPDATE_KNOWN_SHAPE`, uma vez por montagem.

## Forma montada

É a escultura de `gerar_conceitos.armilar()`, fatiada na grade com as mudanças de `armilar_formado.armilar_formada()`:

- **Base:**
  - Rodapé, corpo, listra coral e tampo, com os cantos chanfrados em degraus (chanfro de 10 px, passo 2).
  - A mesa de porcelana fica rente ao tampo (y 14 a 16 no meio 3×3 chanfrado), porque 1 px acima cairia na camada de cima.
- **Portas e mostrador do Núcleo:** entram 2 px, num nicho do corpo, porque no conceito saíam do 5×5:
  - Núcleo: moldura em z 36 a 38 e mostrador em 38 a 39.
  - Energia: caixa em x 36 a 38 e bornes em 38 a 40.
  - Cristais: caixa em −38 a −36, escotilha em −39 a −38 e puxador em −40 a −39.
  - Sintonizador: caixa em z −38 a −36 e placa em −39 a −38.
- **Pernas:**
  - Ficam em (±22, ±22) px do centro, raio 31,1.
  - Base e colar são quadrados alinhados de 4 px (20 a 24).
  - O fuste (meia-largura 1,4) e o topo (meia-largura 1) são losangos girados 45°, e o fuste é cortado em y 32.
  - O topo fica inteiro embaixo do horizonte.
- **Pedestal:** flange em y 16 a 18 sobre o tampo, coluna até 32, colar e taça como no conceito. O copo fica na mesma altura do conceito.
- **Horizonte e marcos:** não viram bloco, porque um anel redondo não cabe em fatias de 16 px sem peça atravessada. Ficam parados, no lugar do conceito, e são desenhados pelo render (etapa 2).

Conferido no protótipo: 427 peças em 35 blocos, nenhuma sobreposição de volume, nenhuma face no mesmo plano, cada peça dentro da própria casa e no máximo 31 elementos por bloco.

**Texturas da forma montada:** os materiais do conceito (porcelana, grafite, coral, latão, vidro, grelha), em sprites de 16 × 16. A UV segue a posição no mundo, módulo 16, como manda a skill `wa-texturas`. Os desenhos especiais ficam cada um no seu sprite: o mostrador do Núcleo (com a cor e as marcas do tier), a placa de sintonia, a escotilha e a face da energia.

## Blocos soltos

São os da prancha aprovada (`armilar_blocos.py`), com silhueta e topo próprios para se distinguirem no inventário:

- **Laje:** cubo inteiro, topo de porcelana com o aro de latão e os quatro rumos, laterais com a faixa da família.
- **Coluna:** base 14 × 3, fuste 8 × 10 e capitel 12 × 3, com a listra coral.
- **Berço:** base, pescoço e taça com a brasa.
- **Núcleo:** o Olho saltado na cor do tier.
- **Portas e Sintonizador:** os bornes, a escotilha e o mostrador saltados.

Todos seguem as regras da `wa-texturas` e passam no `verificar_modelo.py`.

## Projeção, JEI e guia

- **Projeção:** sugere este desenho único em volta de um Núcleo solto, com a frente dele.
  - Sai a troca entre Vitrine e Faixa (Shift + roda) e saem as chaves `layout.*`.
  - Na casa do Sintonizador, Laje e Sintonizador contam como certos um pelo outro.
- **Conta do JEI e do guia (`ResonatorBill`):** 21 Lajes, 1 Núcleo, 1 Porta de cada, 1 Sintonizador, 8 Colunas e 2 Berços. O JEI tem uma montagem só, e o texto que dizia "5x5x5" passa a descrever a Armilar.
- **Cena do guia:** 35 blocos; a página diz como montar camada por camada.

## Receitas

| Bloco | Receita | Sai |
| --- | --- | --- |
| Laje | a da Carcaça (mesmo id) | ×4 |
| Coluna | com forma: `C C` / `CKC` / ` L `, com C = lingote de cobre, K = Cristal de Sinal e L = Laje | ×2 |
| Berço do Sol | com forma: `GKG` / ` G ` / ` L `, com G = lingote de ouro, K = Cristal de Sinal Lapidado e L = Laje | ×1 |
| Núcleo, Portas, Sintonizador | as de hoje (já usam a Carcaça, que agora é a Laje) | ×1 |

A receita do Vidro sai. Na Lente Harmônica, o `resonator_glass` vira `minecraft:glass`.

## Mundos antigos

A caixa antiga não fecha pela regra nova, então desmonta na revalidação do `onLoad`. O Núcleo guarda o tier, os cristais e os itens. A Carcaça antiga vira Laje solta e o Vidro vira Laje pelo alias. Nada some.

Chaves de NBT:
- A chave `layout` do `CoreShell` deixa de ser lida e gravada.
- As outras chaves de NBT, de componentes e de config não mudam.

## O que sai

- O `ResonatorGlassBlock`, o `GlassFace`, o `CasingPosition` e o Vidro nos assets, no lang, nas receitas e nos loot tables.
- O `ResonatorBlueprint.Layout` (Vitrine e Faixa), o `layoutOf` e o byte de layout no NBT e no `ResonatorStatePayload` (o `ModPayloads.VERSION` sobe).
- As texturas e os modelos da casca antiga: carcaça por posição, vidro e forro.
- Na etapa 2: o interior antigo da câmara (a curva de Lissajous, o anel da câmara e o plasma).

## Etapas

A página da ideia falava em três etapas. Ficam duas, porque blocos novos sem a regra nova não dão para testar:

1. **Estrutura e blocos:**
   - os blocos e os estados;
   - a regra nova, a formação, a projeção, o JEI e o guia;
   - os modelos e as texturas soltos e formados;
   - as receitas, o alias e o lang;
   - os testes e o e2e.

   O render da câmara antiga continua até a etapa 2, centrado no sol.
2. **Render:**
   - o sol, as três órbitas (raios 13, 19,5 e 26 px, centro a 47,5 px do chão) e os cristais, um por câmara ocupada, nos nós 2 + 3 + 3 dos anéis, com engaste;
   - o horizonte (raio 30 a 35, y 46 a 49) e os quatro marcos, parados;
   - a energia saindo na conjunção para a Porta de Energia;
   - tudo pelo padrão da skill `wa-efeitos`, com e sem Iris.

   Sai o interior antigo.

## Custo

- **Servidor:** a mesma conferência de 125 casas, só por evento de bloco. A montagem faz 35 `setBlock` de estado visual, uma vez. Nada por tick.
- **Cliente, parado:** as fatias formadas são modelos de bloco, entram na malha do chunk e não custam nada por quadro.
- **Cliente, render (etapa 2):** só a Armilar formada e à vista, com corte por distância. Pela malha do protótipo, são cerca de 700 quads por quadro por Armilar, agrupados por `RenderType`, sem alocação por quadro.
- **Rede:** o `ResonatorStatePayload` perde um byte; nada novo vai ao cliente.
