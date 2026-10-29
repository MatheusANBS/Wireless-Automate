# Tela de Sinal, etapa 4: pixel art (spec)

**Data:** 27/10/2026 (rascunho); **aprovada pelo dono em 29/10/2026** com o mockup [`docs/preview/tela-de-sinal-pixel-art.html`](../../preview/tela-de-sinal-pixel-art.html). **Base:** a spec geral [`2026-10-27-tela-de-sinal-design.md`](2026-10-27-tela-de-sinal-design.md) (decisões 13 e 14) e o mockup `docs/preview/tela-de-sinal.html` (peça Imagem e o fundo em mosaico).

## O que entra

1. O **editor de pixel art**: lápis, balde, borracha, conta-gotas, linha e retângulo, paleta de 16 cores editável, desfazer, grade e zoom.
2. A **biblioteca de pixel arts** do jogador, junto dos componentes e temas da etapa 2.
3. A peça **Imagem** e o **fundo em mosaico** da Tela.

## Decisões

1. **Desenho:** de 8 × 8 a 64 × 64 pixels (largura e altura livres, em passos de 1), paleta de 16 cores ARGB (a cor 0 é sempre transparente), cada pixel um índice da paleta. Ferramentas: lápis (1 px), borracha, balde (preenche a área da mesma cor, vizinhança de 4), conta-gotas, linha e retângulo (contorno ou cheio); espelho horizontal opcional enquanto desenha. Desfazer e refazer (Ctrl+Z, Ctrl+Y) até 100 passos; cada traço é um passo (mudar uma cor da paleta ou o tamanho também é um passo). Grade ligável e zoom com Ctrl + roda, como a miniatura do editor. O clique esquerdo usa a ferramenta; o **clique direito apaga** (borracha, com o lápis, a linha e o retângulo). Mudar o tamanho mantém o canto de cima à esquerda (corta ou completa com transparente). Atalhos: **B** lápis, **E** borracha, **G** balde, **I** conta-gotas, **L** linha, **R** retângulo (apertar R com o retângulo já escolhido alterna contorno e cheio), **M** espelho.
1b. **Prévia:** a coluna da direita do editor mostra a arte em 1× e 2× (o tamanho real na Tela), refeita junto com o quadro.
2. **Onde fica:** uma aba "Pixel art" na coluna da esquerda do editor (ao lado de Peças, Componentes e Camadas) lista as artes da biblioteca com miniatura; "Nova", "Editar", "Duplicar" e "Remover" (dois cliques). Editar abre o editor de pixel art por cima do editor da Tela (uma tela própria, redimensionável como o editor), que volta para ele ao salvar ou cancelar. A paleta usa o seletor de cor da etapa 2 (Tarefa 12).
3. **Biblioteca:** as artes ficam no `TvLibrary` do jogador (`arts`), até `tv.maxArts` (padrão 32), cada uma até 64 × 64; nome até 32 caracteres; vão ao cliente com a biblioteca (só com o editor aberto).
4. **A Tela guarda uma cópia da arte, não a referência.** Inserir uma Imagem copia a arte para o layout (`images`, por id, sem repetir a mesma arte), porque a Tela é vista por todos e não pode depender da biblioteca de quem a fez: apagar ou mudar a arte na biblioteca não muda a Tela (como os temas). "Atualizar da biblioteca" na aba da Imagem troca a cópia pela versão atual, se o jogador ainda tiver a arte.
5. **Teto por Tela:** até `tv.maxImagePixels` pixels somados nas imagens distintas de um layout (padrão 16.384, quatro de 64 × 64). Os pixels vão em meio byte cada (dois por byte, a paleta tem 16 cores), então o teto padrão dá 8 KB, e as imagens contam no `tv.maxLayoutBytes` (padrão 16 KB) como o resto do layout: o teto do pacote do chunk não muda e a config existente fica como está.
6. **Peça Imagem:** qualquer tamanho e posição; a escala é inteira (1×, 2×, 3×...) por padrão, para não borrar, ou "esticar" livre; alinhamento dentro da caixa (9 posições, como o `TvAlign` já faz em duas direções); opacidade; as regras podem escondê-la ou piscar.
7. **Fundo em mosaico:** `TvBackground` ganha o tipo `tiled`: uma imagem do layout repetida pela Tela inteira, com escala inteira (1× a 8×) e opacidade, por baixo das peças (as linhas de varredura continuam por cima).
8. **Desenho na Tela:** a imagem é pintada na textura da Tela no redesenho, por faixas horizontais da mesma cor (um retângulo por faixa, não um por pixel); o mosaico repete as faixas. O e2e mede o redesenho de uma Tela 32 × 32 com mosaico; se passar de 4 ms, a imagem vira uma textura pequena própria (uma por arte distinta no cliente, liberada com a Tela), desenhada com repetição.

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
- **Cliente:** a Imagem e o mosaico só no redesenho; o editor de pixel art é GUI comum, com o desenho do quadro numa textura pequena refeita só quando um pixel muda (não por quadro).

## Fora

Importar PNG do computador; animação; paletas maiores que 16; pincel com mais de 1 px; compartilhar artes entre jogadores.
