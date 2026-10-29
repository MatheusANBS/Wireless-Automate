# Tela de Sinal, etapa 4: pixel art (spec)

**Data:** 27/10/2026 (rascunho); **aprovada pelo dono em 29/10/2026** com o mockup (versão 4, depois de três rodadas: ferramentas, seleção e navegação, rampas) [`docs/preview/tela-de-sinal-pixel-art.html`](../../preview/tela-de-sinal-pixel-art.html). **Base:** a spec geral [`2026-10-27-tela-de-sinal-design.md`](2026-10-27-tela-de-sinal-design.md) (decisões 13 e 14) e o mockup `docs/preview/tela-de-sinal.html` (peça Imagem e o fundo em mosaico).

## O que entra

1. O **editor de pixel art**: as ferramentas, o pincel de 1 a 8 px, a seleção, a paleta de 16 cores em 4 rampas, desfazer, grade e a navegação no quadro sem rolagem.
2. A **biblioteca de pixel arts** do jogador, junto dos componentes e temas da etapa 2.
3. A peça **Imagem** e o **fundo em mosaico** da Tela.

## Decisões

1. **Desenho:** de 8 × 8 a 64 × 64 pixels (largura e altura livres, em passos de 1), paleta de 16 cores ARGB (a cor 0 é sempre transparente), cada pixel um índice da paleta. Mudar o tamanho mantém o canto de cima à esquerda (corta ou completa com transparente).
1a. **Ferramentas** (tecla entre parênteses): lápis (B), borracha (E), balde (G; vizinhança de 4; com Shift troca a cor na arte inteira), conta-gotas (I; também Ctrl + clique com qualquer ferramenta), linha (L; Shift trava em 0°, 45° e 90°), retângulo (R; R de novo alterna contorno e cheio; Shift faz quadrado), elipse (O; O de novo alterna contorno e cheia; Shift faz círculo), clarear e escurecer (D), pontilhado (T; pinta só os pixels com `(x + y)` par, em xadrez) e seleção (S). O clique esquerdo usa a ferramenta; o **clique direito apaga** (no lápis, na borracha, na linha, no retângulo, na elipse e no pontilhado, com a cor 0) e, no clarear, escurece.
1b. **Pincel** de 1 a 8 px, quadrado ou redondo (redondo a partir de 3 px), nas teclas `[` e `]`; vale no lápis, na borracha, nos contornos, no pontilhado e no clarear. O pincel aparece desenhado sob o cursor. **Ajudas:** espelho horizontal (M) e vertical (Shift+M), juntos ou não, valendo em todas as ferramentas; **pixel perfect** (P) no lápis de 1 px, que devolve o canto de um L ao que era durante o traço; **X** volta para a cor anterior; Ctrl+G liga e desliga a grade.
1c. **Clarear e escurecer por rampa:** cada linha da paleta (4 × 4) é uma rampa, do escuro à esquerda ao claro à direita (a linha 0 começa no transparente, que nunca entra). O clarear troca o pixel pela cor da casa à direita na mesma linha, o escurecer pela da esquerda, uma vez por pixel por traço; nunca troca de linha. **Criar rampa** refaz a linha da cor escolhida como rampa dela, com desvio de matiz (para a esquerda: mais escura, mais saturada, matiz puxando para o azul, 235°; para a direita: mais clara, menos saturada, matiz puxando para o amarelo, 55°; 14° de matiz, 0,08 de saturação e 0,17 de brilho por casa, em HSV), num passo do desfazer. Paleta padrão de uma arte nova, em 4 rampas: transparente, `#1B1920`, `#7A7366`, `#ECE5D6`; `#8E2A35`, `#C2442A`, `#F0603E`, `#FFB08A`; `#1E5E4A`, `#2F9D5C`, `#5CC97A`, `#B9EBA3`; `#2C2F7A`, `#4B4FB0`, `#7A80E0`, `#B8BEF5`.
1d. **Cor:** cada uma das 16 cores pode ser qualquer ARGB, editada pelo mesmo seletor do editor da Tela (quadrado de saturação e brilho, matiz, alfa, hexadecimal e recentes), aberto por dois cliques na cor ou por "Editar cor"; trocar uma cor muda todos os pixels dela; cada edição da cor é um passo do desfazer.
1e. **Seleção:** S marca um retângulo; arrastar de dentro solta o pedaço e o move (o transparente do pedaço não cobre nada ao assentar); setas movem 1 px (Shift, 8 px); Ctrl+A seleciona tudo, Ctrl+D tira; Ctrl+C, Ctrl+X e Ctrl+V copiam, recortam e colam (colar cria um pedaço solto na seleção ou no canto de cima à esquerda da vista); Delete apaga; Enter assenta; Esc desfaz o mover e tira a seleção. Com uma seleção, as ferramentas só pintam dentro dela. A área de cópia vale enquanto o editor está aberto.
1f. **Arte inteira:** virar na horizontal (Shift+H) e na vertical (Shift+V) e girar 90° no sentido horário (troca largura e altura); com seleção, agem só no pedaço. **Limpar tudo** em dois cliques. Desfazer e refazer (Ctrl+Z, Ctrl+Y) até 100 passos; um traço, uma edição de cor, o tamanho, virar, girar, limpar e cada mover de pedaço são um passo.
1g. **Navegação no quadro, sem barra de rolagem:** a roda dá zoom no ponto do mouse (Ctrl + roda também, como no editor da Tela), em níveis de 2 a 48 px por pixel; Alt + arrastar, Espaço + arrastar e o botão do meio arrastando movem a vista; as setas movem a vista quando não há seleção; 0 enquadra a arte; `+` e `−` dão zoom no centro; clicar na prévia leva a vista até aquele ponto. O rodapé mostra a coordenada e o índice da cor sob o mouse e o tamanho da seleção. No jogo, a tecla do inventário (E) não fecha o editor; o Esc primeiro tira a seleção e, sem seleção, volta para o editor da Tela.
1h. **Prévia:** a coluna da direita mostra a arte em 1×, 2× e repetida 3 × 3 (como fica no mosaico), refeitas junto com o quadro.
2. **Onde fica:** uma aba "Pixel art" na coluna da esquerda do editor (ao lado de Peças, Componentes e Camadas) lista as artes da biblioteca com miniatura; "Nova", "Editar", "Duplicar" e "Remover" (dois cliques). Editar abre o editor de pixel art por cima do editor da Tela (uma tela própria, redimensionável como o editor), que volta para ele ao salvar ou cancelar. A paleta usa o seletor de cor da etapa 2 (Tarefa 12).
3. **Biblioteca:** as artes ficam no `TvLibrary` do jogador (`arts`), até `tv.maxArts` (padrão 32), cada uma até 64 × 64; nome até 32 caracteres; vão ao cliente com a biblioteca (só com o editor aberto).
4. **A Tela guarda uma cópia da arte, não a referência.** Inserir uma Imagem copia a arte para o layout (`images`, por id, sem repetir a mesma arte), porque a Tela é vista por todos e não pode depender da biblioteca de quem a fez: apagar ou mudar a arte na biblioteca não muda a Tela (como os temas). "Atualizar da biblioteca" na aba da Imagem troca a cópia pela versão atual, se o jogador ainda tiver a arte.
5. **Teto por Tela:** até `tv.maxImagePixels` pixels somados nas imagens distintas de um layout (padrão 16.384, quatro de 64 × 64). Os pixels vão em meio byte cada (dois por byte, a paleta tem 16 cores), então o teto padrão dá 8 KB, e as imagens contam no `tv.maxLayoutBytes` (padrão 16 KB) como o resto do layout: o teto do pacote do chunk não muda e a config existente fica como está.
6. **Peça Imagem:** qualquer tamanho e posição; a escala é inteira (1×, 2×, 3×...) por padrão, para não borrar, ou "esticar" livre; alinhamento dentro da caixa (9 posições, como o `TvAlign` já faz em duas direções); opacidade; as regras podem escondê-la ou piscar.
7. **Fundo em mosaico:** `TvBackground` ganha o tipo `tiled`: uma imagem do layout repetida pela Tela inteira, com escala inteira (1× a 8×) e opacidade, por baixo das peças (as linhas de varredura continuam por cima).
8. **Desenho na Tela:** a primeira versão pintava por faixas horizontais e mediu ~21 ms no redesenho de uma Tela 32 × 32 com mosaico 64 × 64 em escala 1 (acima dos 4 ms do teto). Por isso a imagem é uma textura pequena própria (`TvArtTextures`: uma por arte distinta no cliente, NEAREST sem mipmap, `GL_REPEAT` no mosaico, solta quando nenhuma Tela nem o editor a usa), desenhada num quadrilátero no redesenho: ~0,17 ms no mesmo caso.

## Modelo e chaves

- `TvPixelArt` (record: `id` UUID, `name`, `width`, `height`, `palette` int[16], `pixels` byte[] com o índice por pixel na memória), codec compacto: os pixels gravados dois por byte (`byte[]` de `ceil(w × h / 2)`), a paleta como `int[]`.
- `TvLayout`: `images` (lista de `TvPixelArt`, opcional, vazia por padrão).
- `TvPieceType`: `image` (não `bindable()`); os campos da Imagem no `TvPartStyle`, só gravados quando diferem do padrão: `image` (o id), `scale` (0 = esticar, padrão 1) e `valign` (em cima, meio, embaixo; padrão meio); o alinhamento horizontal é o `align` do `TvStyle` e a opacidade é a `opacity` do `TvStyle`.
- `TvBackground`: kind `tiled`, `image` (id), `scale` (1 a 8, padrão 2), `opacity` (0 a 100, padrão 100). Ao aplicar o layout, as imagens que nenhuma peça nem o fundo usam saem, e uma peça ou um fundo que aponta para imagem que não existe sai (o fundo volta ao do tema).
- `TvLibrary`: `arts` por jogador.
- Config do servidor `tv`: `maxArts` (padrão 32), `maxImagePixels` (padrão 16.384).
- Ações novas no `TvLibraryActionPayload` (`SAVE_ART`, `REMOVE_ART`, `RENAME_ART`). `ModPayloads.VERSION` sobe para `"38"`.

## Custo

- **Servidor:** só a biblioteca, nos eventos; o layout fica maior (até 16 KB de pixels), mas só vai no salvar e no update tag.
- **Cliente:** a Imagem e o mosaico só no redesenho; o editor de pixel art é GUI comum, com o quadro numa textura pequena refeita só quando um pixel muda (não por quadro); por quadro só o quadrilátero do quadro, a grade, a seleção e o pincel sob o cursor (poucos retângulos).

## Fora

Importar PNG do computador; animação e camadas; paletas maiores que 16; paletas prontas; contorno automático; seleção à mão livre ou por cor; deslocar a arte com volta; compartilhar artes entre jogadores.
