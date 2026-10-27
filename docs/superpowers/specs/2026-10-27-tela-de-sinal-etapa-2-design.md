# Tela de Sinal, etapa 2: o editor (spec)

**Data:** 27/10/2026. **Base:** a spec geral [`2026-10-27-tela-de-sinal-design.md`](2026-10-27-tela-de-sinal-design.md) (decisões 4, 5, 9 a 13 e 15) e o mockup aprovado `docs/preview/tela-de-sinal.html` (seção "2 · Editor" e "Atalhos"). Esta spec detalha só o que a etapa 2 entrega e as escolhas de implementação que a spec geral deixou em aberto. Onde as duas divergem, vale esta.

## O que entra

O editor do mockup no jogo, no lugar da aba Conteúdo: o jogador monta a Tela do zero com peças soltas, arrasta, redimensiona, agrupa, ordena as camadas, liga as peças às redes, formata, pinta pelo tema, escreve regras, desfaz e refaz, copia e cola entre Telas, e salva grupos como componentes e paletas como temas na biblioteca dele. As peças da etapa 2 são as três que já existem: **Valor**, **Texto** e **Forma**. Ícone de item, Barra, Medidor, Gráfico e Seta são a etapa 3; Imagem e o fundo em mosaico, a etapa 4. A paleta do editor mostra só as peças que existem.

## Decisões

1. **Onde fica o editor.** A tela do bloco passa a ter as abas **Tamanho** e **Editar**; a Conteúdo some (e o texto `editor_soon` com ela). A aba Editar ocupa a janela inteira menos 8 px de margem (mínimo 400 × 240, como as outras telas grandes, sem alça: acompanha a janela); a aba Tamanho continua 320 × 220. O mesmo `TvMenu` serve às duas.
2. **Disposição (cabe na escala 3 de 1366 × 768, 447 × 248 úteis):** coluna da esquerda de 92 px (Peças em cima, Componentes embaixo, com rolagem), coluna da direita de 132 px (as abas Dado, Formato, Estilo e Regras), e o centro com a barra da base em cima (nome, tamanho "6×3 · 192×96 px", Tema, Fundo, linhas, grade), a miniatura, a barra de botões (↶ ↷, Agrupar, Desagrupar, Salvar como componente, Duplicar, Remover, Salvar) e as Camadas embaixo (48 px, com rolagem). Larguras maiores vão para a miniatura.
3. **Miniatura fiel:** o rascunho é desenhado pelo mesmo `TvPainter` numa textura própria do editor (um `TvCanvas` só do editor, liberado ao fechar), redesenhada só quando o rascunho, os números ou o tema mudam, e mostrada na escala inteira ou fracionária que couber. O que se vê no editor é o que vai para a parede. A grade (4 px, 8 por bloco), as caixas de escolha, as alças e as linhas de bloco são desenhadas por cima, na GUI.
4. **Rascunho só no cliente.** O editor edita uma cópia do layout. Nada vai ao servidor até **Salvar** (botão ou Ctrl+S), que manda o layout inteiro num `TvLayoutPayload`. Fechar com mudanças não salvas pede confirmação em dois cliques no ✕ ("Descartar?"), como o Remover. Se o layout da Tela mudar no servidor enquanto o editor está aberto (outro operador salvou), o editor mostra um aviso com "Recarregar" e não sobrescreve sozinho.
5. **Validação no servidor (`TvEditing.applyLayout`):** só quem edita (decisão 15); até `tv.maxElements` peças e `tv.maxBindings` ligações; cada caixa dentro da área (cortada se passar); textos, nomes e afixos pelos tetos do modelo; cada ligação a uma rede que **quem salva** pode usar (`canUse(jogador, false)`), senão a peça é salva sem ligação e o jogador recebe o aviso na action bar; tipo de recurso carregado. Payload acima de 64 KB é recusado. Salvar sobe a `revision`, grava e manda o update tag (o caminho da etapa 1).
6. **Escolher e mexer (mockup):** clique escolhe a peça (numa peça de grupo, o grupo inteiro); dois cliques entram no grupo e escolhem a peça; Shift + clique soma ou tira da escolha; clique no vazio solta. Arrastar move a escolha; a alça do canto inferior direito redimensiona (uma peça só, ou o grupo em proporção). Tudo encaixa na grade de 4 px; Alt segurado solta o encaixe (1 px). Nada sai da área.
7. **Atalhos:** os da decisão 11 da spec geral, iguais. O desfazer guarda até 100 estados do rascunho (o layout é imutável: cada estado é uma referência, sem cópia profunda) e é zerado ao fechar. Os atalhos não agem com um campo de texto focado.
8. **Área de transferência do cliente:** Ctrl+C e Ctrl+X guardam as peças escolhidas (com as posições relativas ao canto da escolha e os grupos) numa área do cliente que vale para qualquer Tela até sair do jogo; Ctrl+V cola no mesmo lugar, ou no primeiro lugar livre se não couber, com grupos novos. Não usa a área de transferência do sistema.
9. **Camadas:** a ordem da lista é a ordem de desenho (a primeira desenhada fica embaixo); a lista de Camadas mostra de cima para baixo. Cada linha: ícone do tipo, nome (ou o texto ou o dado), olho para esconder, e o grupo recolhível. Arrastar uma linha muda a camada; `[` e `]` também.
10. **Aba Dado (Valor):** a lista de redes com busca (a mesma do roteador e da etapa 1, `TvNetworkList`), o tipo (só os carregados) e a métrica (`TvMetric`). Sem ligação, o Valor mostra "—". Com o editor aberto, o servidor manda também os números das ligações do rascunho ainda não salvas (prévia, só para quem edita e com o acesso dele), para a miniatura mudar na hora (pedido do dono em 27/10, Tarefa 14).
11. **Aba Formato (Valor):** casas (0 a 3), abreviar (k, M, G), sinal, prefixo, sufixo e unidade (`TvUnit`), como o modelo da etapa 1.
12. **Aba Estilo:** X, Y, largura e altura em pixels; nome; cor do texto, fundo, borda (cor e espessura 0 a 4), cantos (raio 0 a 8 px), opacidade (0 a 100%); fonte, tamanho (ou tamanho máximo com "Diminuir a fonte para caber") e alinhamento no Valor e no Texto; o texto no Texto. Uma cor é **do tema** (um dos seis papéis: Fundo, Cartão, Texto, Apagado, Alta, Baixa; acompanha a troca de tema) ou **fixa** (amostras e o campo `#RRGGBB` com alfa opcional). "Cor de tudo" na escolha de várias peças pinta o texto de todas.
13. **Aba Regras:** até 8 regras por peça, lidas de cima para baixo, vale a primeira que bate: "menor que", "maior que" ou "igual a" um número (no Valor, o número do dado; na Forma e no Texto, o dado de uma ligação própria da regra, escolhida como na aba Dado), e o efeito: trocar a cor do texto, trocar o fundo, piscar ou esconder. As regras são avaliadas no cliente, no redesenho; uma Tela com alguma regra que pisca e que bate é redesenhada duas vezes por segundo enquanto estiver à vista, e só ela.
14. **Fontes:** cinco, escolhidas por peça: **Minecraft** (a do jogo, padrão, a da etapa 1), **Pixel** (Silkscreen), **Terminal** (VT323), **Mono** (IBM Plex Mono) e **Sans** (IBM Plex Sans SemiBold), as quatro do mockup. São TTF com licença OFL, incluídas no jar com o arquivo da licença, como fontes do jogo (`assets/wirelessautomate/font/*.json` com o provedor `ttf`, `oversample` alto para ficarem nítidas na resolução alta da 7b). O texto que cabe (`TvFit`) mede pela fonte da peça.
15. **Temas:** um tema é um nome e as seis cores. Quatro de partida, fixos no código (Grafite, Fósforo, Âmbar, Porcelana, com as cores do mockup), e os do jogador na biblioteca ("Salvar tema" grava a paleta atual com um nome; o jogador edita as seis cores na barra da base). A Tela guarda **a paleta inteira** no layout, não uma referência: apagar um tema da biblioteca não muda nenhuma Tela.
16. **Fundo da Tela:** do tema (Fundo), uma cor fixa ou um degradê vertical de duas cores; linhas de varredura ligadas ou não. O mosaico de pixel art é a etapa 4.
17. **Componentes:** um componente é um nome e uma lista de peças (com os grupos), até `tv.maxElements` peças. Prontos, fixos no código e só com Valor, Texto e Forma: "Painel de rede" (o da etapa 1), "Cartão de número" (cartão, rótulo e valor) e "Título". Inserido, vira peças soltas num grupo novo, no primeiro lugar livre. "Salvar como componente" grava a escolha na biblioteca com um nome; a lista mostra os do jogador com renomear e remover (dois cliques). As ligações vão junto no componente; ao inserir numa Tela, uma ligação a uma rede que o jogador não pode mais usar vira "—" e o aviso explica.
18. **Biblioteca do jogador** (`TvLibrary`, `SavedData` no overworld, por UUID): componentes e temas, com tetos na config (`tv.maxComponents` 64, `tv.maxThemes` 32). Vai ao cliente só quando o editor abre e depois de cada mudança (`TvLibraryPayload`), e as ações (salvar, renomear, remover) vão pelo `TvLibraryActionPayload`, validadas no servidor. A Tela não depende da biblioteca para ser desenhada.

## Modelo (o que muda nas chaves)

Tudo **opcional e com padrão**: um layout da etapa 1 lê igual e desenha igual.

- `TvPiece`: `hidden` (bool, padrão falso), `rules` (lista de `TvRule`, padrão vazia).
- `TvStyle`: `radius` (0 a 8, padrão 0), `opacity` (0 a 100, padrão 100), `font` (`TvFont`: `minecraft`, `pixel`, `terminal`, `mono`, `sans`; padrão `minecraft`). As cores (`color`, `background`, `border`) continuam `int`; um papel do tema é guardado como `0x0000000N` (N de 1 a 6, alfa zero), o que nenhum ARGB visível usa (alfa zero é transparente); a conversão e o teste ficam no `TvColor`, puro.
- `TvRule`: `op` (`lt`, `gt`, `eq`), `value` (double), `bind` (opcional, a ligação própria da regra), `color` e `background` (opcionais), `blink`, `hide`.
- `TvLayout`: `theme` (`TvTheme`: nome e as seis cores; padrão Grafite, o fundo da etapa 1), `background` (`TvBackground`: `theme`, `solid` ou `gradient`, com as cores), `scanlines` (bool).
- As ligações das regras entram no `bindings()` do layout (contam no `tv.maxBindings` e o servidor manda os números delas).
- Config do servidor, seção `tv`: `maxComponents` (padrão 64), `maxThemes` (padrão 32).
- Payloads novos: `TvLayoutPayload` (cliente → servidor), `TvLibraryPayload` (servidor → cliente), `TvLibraryActionPayload` (cliente → servidor). O `TvTemplatePayload` sai. `ModPayloads.VERSION` sobe de `"35"` para `"36"`.
- NBT da biblioteca: `wirelessautomate_tv_library` no overworld, por jogador `components` e `themes`.

## Custo

- **Servidor:** nada novo por tick. Salvar é um evento (validação proporcional ao layout, uma gravação, um update tag). A biblioteca só é lida ao abrir o editor e nas ações.
- **Rede:** o layout só no salvar e no update tag (como na etapa 1); a biblioteca só com o editor aberto. Os números das regras entram no mesmo `TvValuesPayload`.
- **Cliente, no mundo:** nada por quadro além do que a etapa 1 já faz. Regras, temas, fontes, cantos e opacidade são pintados no redesenho da textura, que continua só quando algo muda; a exceção é o piscar (2 redesenhos por segundo, só da Tela com regra de piscar valendo e à vista).
- **Cliente, no editor:** um redesenho da miniatura por mudança do rascunho (arrastar redesenha no máximo uma vez por quadro, com a miniatura no tamanho da janela, não da Tela inteira); o resto é GUI comum. O desfazer guarda referências a layouts imutáveis.
- **Fontes:** quatro TTF, poucas centenas de KB no jar; o atlas das letras é do vanilla e cresce só com os caracteres usados.

## Fora desta etapa

As peças da etapa 3 e 4, o mosaico de pixel art, a área de transferência do sistema, seleção por retângulo arrastado, alinhar e distribuir várias peças, guias inteligentes, girar peças, editar mais de uma Tela ao mesmo tempo, histórico do desfazer depois de fechar, e compartilhar componentes entre jogadores.

## Riscos

- **Fontes TTF pequenas:** o provedor `ttf` do vanilla rasteriza no tamanho dado com `oversample`; a 8 px na resolução baixa algumas fontes podem borrar. A Tarefa das fontes mede com capturas e ajusta o `oversample` e o tamanho mínimo por fonte.
- **Editor numa janela pequena:** na escala 4 de 1366 × 768 a tela útil é 333 × 184, abaixo do mínimo; o editor mostra só a mensagem pedindo uma escala menor da GUI, sem desenhar cortado.
