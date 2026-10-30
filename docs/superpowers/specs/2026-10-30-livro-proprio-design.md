# Livro próprio no lugar do GuideME

Mockup aprovado: `docs/preview/livro-proprio.html` (artifact "Livro do Wireless Automate"). Data: 30/10/2026.

## Decisões do dono

- O livro próprio **substitui o GuideME de vez**: o mod deixa de declarar o GuideME, e o item do livro abre sempre a nossa tela.
- O visual é um **livro aberto de duas folhas**, na linha do caderno do Ars Nouveau, com a identidade Porcelana e Sinal: capa de **grafite com cantoneiras de latão**, presilhas de latão na lombada, páginas de porcelana em pergaminho suave (manchas largas em cinco tons próximos, bordas um pouco mais escuras), sombra na dobra, costura coral, moldura fina com cantos de latão e a antena de sinal coral nos cantos de cima, fita marcadora coral saindo por baixo.
- **Fitas marcadoras na borda direita**, uma por grupo, na cor do grupo e com o ícone dele; a do grupo aberto fica mais comprida.
- **Tamanho fixo**, como o do Ars: a textura tem 300 × 196 px de interface; as folhas não esticam. Numa tela pequena o livro usa uma escala de interface menor que a do jogo, para caber.
- O **ícone do item** é o livro fechado do mesmo desenho: capa de grafite, cantoneiras de latão, costura coral na lombada, borda das folhas em porcelana, fita coral embaixo e o **Olho de porcelana pulsando** no centro (a animação de sempre dos itens do mod, 4 quadros).
- Quem já recebeu o livro antigo (o item do GuideME) **recebe o novo uma vez, no próximo login**.
- As 10 cenas 3D entram **paradas na etapa 1** (ângulo fixo, sem girar) e **interativas na etapa 2**.
- O livro não segue o tema claro e escuro das telas: é papel, com um visual só.

## Escala e medidas

O livro não usa a escala de interface do jogo: ele é desenhado com cada pixel da textura valendo `k` pixels de tela, com `k` o **maior número par** com que o livro inteiro (e as fitas) cabe na janela com margem; o mínimo é 2. O texto é desenhado com a fonte do jogo a `k / 2` pixels de tela por pixel da fonte, então a pixel art e o texto ficam nítidos.

A paginação trabalha em **unidades de folha** = 2 por pixel da textura (um pixel da fonte vale uma unidade). A área de conteúdo de cada folha, em pixels da textura: esquerda de x = 23 a 139 e direita de x = 161 a 277, as duas de y = 24 a 164 (116 × 140 px, ou **232 × 280 unidades**: umas 38 letras por linha e 31 linhas de 9 unidades). O rodapé (◀, número, ▶) fica em y = 166. As fitas ficam à direita da capa, começando em y = 15, a cada 21 px.

## O que o jogador vê

1. **Folha de rosto (esquerda) e índice (direita)** ao abrir pela primeira vez: o título, o selo com o roteador, a busca e a dica da tecla G; à direita, o índice em grupos com o ícone de cada página.
2. **Uma página**: título, ornamento, e o conteúdo correndo pelas duas folhas (a esquerda e depois a direita). ◀ ▶ embaixo viram as duas folhas juntas; o número de cada folha embaixo, no meio. "← Índice" no alto da folha esquerda volta ao índice do grupo.
3. **Busca**: digitar na folha de rosto troca a folha direita pelos resultados (título e trecho com a palavra realçada em coral). Sem acento e sem maiúscula (a normalização da `NetworkSearch`).

Grupos do índice (o campo `group` de cada página): Começar, Ferramentas, Armazenamentos, Energia e cristais, Painel, Integrações, Referência. Cores das fitas: coral (Começar), latão (Ferramentas), fluido.2 (Armazenamentos), source.1 (Energia e cristais), chunk.1 (Painel), quimico.2 (Integrações), porcelana.0 (Referência).

### Gestos

| Gesto | O que faz |
| --- | --- |
| Clique direito com o livro | Abre onde o jogador parou nesta sessão (ou na folha de rosto). |
| Segurar G sobre um item do mod com página | No inventário, num baú e no JEI: abre o livro na página do item (`item_ids` da página). |
| Clique numa fita | Índice daquele grupo. |
| ◀ ▶, ← →, roda do mouse | Vira as duas folhas. |
| Clique num link, ou num item (de receita, tabela ou linha) que tem página | Vai para a página. |
| Botão lateral do mouse, Backspace | Volta (histórico da sessão do livro). |
| Digitar, ou Ctrl + F | Folha de rosto, na busca. Enter abre o primeiro resultado, ↑ ↓ andam, Esc limpa e depois fecha. |

## As páginas

- Fonte: continua `scripts/guide/` (`guia_base.py` + `paginas/<pagina>.py`). Destino novo: `assets/wirelessautomate/guide/en_us/<pagina>.md` e `assets/wirelessautomate/guide/pt_br/<pagina>.md`; imagens em `assets/wirelessautomate/guide/images/`. Sai `guides/wirelessautomate/guide/` e `guideme_guides/guide.json`.
- Idioma: o do jogo se existir (`pt_br`), senão `en_us`; página que falte no idioma cai para `en_us`.
- Cabeçalho: o de hoje (`navigation.title`, `navigation.icon`, `navigation.parent`, `navigation.position`, `item_ids`) mais `group: <id>`.
- O corpo continua no subconjunto que o guia usa hoje: títulos `#`/`##`/`###`, parágrafos com `**negrito**`, `` `código` ``, `[texto](pagina.md)`, listas `-` e `1.`, tabelas, `<br clear="all" />`, `![alt](images/x.png)`, e as tags `ItemImage`, `BlockImage`, `Row`, `RecipeFor`, `Recipe`, `ItemLink`, `SubPages` e `GameScene` (com `Block`, `BlockAnnotation`, `LineAnnotation`, `IsometricCamera`). Tag desconhecida: aviso no log uma vez e o resto da página segue.

## Arquitetura

Tudo de leitura e desenho é cliente (`client/guide/`); o comum só tem o item, a receita, a entrega no login e o gancho.

| Parte | Lado | Papel |
| --- | --- | --- |
| `item/GuideBookItem` | comum | O item `wirelessautomate:guide` (pilha de 1). No uso, no cliente, chama `GuideClientHooks.open`. |
| `GuideClientHooks` | comum | Ganchos que o cliente preenche (padrão do `TvClientHooks`): `open(pagina ou null)`. |
| `item/GuideBook` | comum | `create()` devolve o nosso item; entrega no primeiro login pela marca `wirelessautomate:guide_v2_given`. |
| `client/guide/doc/` | cliente, puro | O modelo do documento (`GuideDoc`, blocos e trechos) e o `GuideParser` (markdown + tags), com JUnit. |
| `client/guide/layout/` | cliente, puro | A paginação: blocos → folhas de tamanho fixo, por uma `TextMeasure`; com JUnit. |
| `client/guide/GuidePages` | cliente | Lê as páginas do idioma pelo `ResourceManager` uma vez (recarrega no `/reload` de recursos), monta o índice por grupo e o mapa item → página. |
| `client/guide/GuideScreen` | cliente | A anfitriã: o livro, as fitas, virar, voltar, folha de rosto, índice, busca, teclado e mouse; repassa o desenho. |
| `client/guide/GuidePainter` | cliente | Desenha as operações de uma folha (texto, ícones, tabelas, links, molduras). |
| `client/guide/GuideRecipes` | cliente | A receita de bancada do `RecipeManager` do cliente (pelo id ou pelo item de saída), com os ingredientes alternando a cada segundo. Item sem receita de bancada cai para a de alto-forno ou fornalha (o ingrediente no meio, a estação embaixo), como o lingote de liga. |
| `client/guide/GuideScenes` | cliente | A cena 3D parada (ângulo isométrico fixo) dentro da moldura; etapa 2 a torna interativa. |
| `client/guide/GuideKeys` | cliente | A tecla G (`KeyMapping`, padrão G) e o item sob o mouse (tela de inventário e JEI). |
| `compat/jei` | cliente | O item do JEI sob o mouse, para a tecla G (só com o JEI). |

Texturas: `scripts/textures/livro.py`, chamado pelo `gerar_texturas.py`, gera `textures/gui/guide/book.png` e as fitas; o `GUIA` do `gerar_texturas.py` passa a ser o livro fechado novo.

## O custo

| Onde | Quanto |
| --- | --- |
| Servidor | Zero por tick. Só a receita, a entrega no login (uma marca no NBT persistente) e o item; nenhum pacote. |
| Abrir o livro | Lê ~60 KB de texto do idioma uma vez e guarda (até o próximo `/reload` de recursos). |
| Paginar | Uma vez por página e idioma, guardado (o tamanho é fixo). Virar a folha só troca o índice da folha desenhada. |
| Por quadro | Só desenhar duas folhas já montadas: nada de `font.width`, `split` ou montagem no `render`. |
| Busca | O texto normalizado de cada página sai na primeira busca; cada tecla filtra 25 páginas. |
| Cena parada | Só a cena da folha à vista; os blocos montados uma vez por cena. |
| Tecla G | Só olhada com uma tela de contêiner (ou o JEI) aberta, quando a tecla é apertada. |
| Memória | Os documentos e as folhas das 25 páginas de um idioma (centenas de KB), soltos ao trocar de idioma. |

## Etapas

- **Etapa 1**: item e ícone novos, receita sem condição, entrega no login; páginas no formato novo; parser, paginação, tela, índice, fitas, busca, receitas, cenas paradas, tecla G (inventário e JEI); sai o GuideME (toml, `build.gradle`, `guideme_guides`, e2e, vitrine, docs); e2e `guia` refeito.
- **Etapa 2**: as cenas giram (arrastar) e têm zoom (roda), com as anotações (`BlockAnnotation`, `LineAnnotation`) por cima.
- **Depois, se o dono quiser**: som e animação de folha virando; tabelas com os números do servidor.

## Fora

- Favoritos, marcar página e anotações do jogador.
- Livro com tema escuro.
- Qualquer integração com o GuideME (nem leitura do formato dele com ele instalado).
