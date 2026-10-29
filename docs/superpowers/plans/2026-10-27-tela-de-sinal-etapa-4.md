# Tela de Sinal, etapa 4 (pixel art) · plano de implementação

> **Para agentes:** SUB-SKILL OBRIGATÓRIA: use superpowers:subagent-driven-development (recomendado) ou superpowers:executing-plans para executar este plano tarefa por tarefa. Os passos usam caixas (`- [ ]`) para acompanhar.

**Estado:** spec e mockup (`docs/preview/tela-de-sinal-pixel-art.html`) aprovados pelo dono em 29/10/2026; executado no ramo `claude/tela-de-sinal-pixel-art-dff2bd`, cortado de `tela-de-sinal`.

**Objetivo:** o editor de pixel art, as artes na biblioteca do jogador, a peça Imagem e o fundo em mosaico.

**Arquitetura:** a arte é um record compacto (`TvPixelArt`, paleta de 16 e um índice por pixel); a biblioteca do jogador ganha `arts`; o layout guarda cópias das artes que usa (`images`), com teto de pixels; a pintura é por faixas no redesenho; o editor de pixel art é uma tela própria, aberta do editor da Tela, com a lógica das ferramentas pura (JUnit).

**Stack:** Java 21, NeoForge 21.1 (Minecraft 1.21.1), JUnit 5, GameTests, e2e do cliente.

**Spec:** [`docs/superpowers/specs/2026-10-27-tela-de-sinal-etapa-4-design.md`](../specs/2026-10-27-tela-de-sinal-etapa-4-design.md).

**Modo de execução (decisão do dono em 27/10, para poupar cota):** poucas tarefas grandes, um agente por tarefa, sem revisão por tarefa, uma revisão única no fim da etapa; e2e só no fim de cada tarefa, sem as variantes (`--variants pt,escuro` só na revisão final).

## Decisões deste plano (o dono pode trocar antes da tarefa em que entram)

1. **Cópia da arte no layout** (spec, decisão 4), como os temas da etapa 2.
2. **Pintura por faixas antes de textura própria** (spec, decisão 8): só vira textura se o e2e medir mais de 4 ms no redesenho com mosaico.

## Restrições globais

- Código, comentários, commits e docs em português; textos nos dois `lang` (`en_us.json`, `pt_br.json`), sempre os dois, na mesma ordem.
- **Chaves existentes não mudam.** As das etapas anteriores continuam; as novas são as da spec da etapa 4 ("Modelo e chaves"), todas opcionais com padrão, para um layout salvo antes ler e desenhar igual. **`ModPayloads.VERSION` sobe de `"37"` para `"38"`** (na primeira tarefa que mexe em payload).
- **Performance (skill `wa-performance`; leia `.claude/skills/wa-performance/SKILL.md`):** nada novo por tick no servidor; salvar e a biblioteca são eventos; nada sai para o cliente com o editor fechado (a biblioteca só com ele aberto); no mundo, nada por quadro além do que a etapa 1 faz: regras, fontes, tema, cantos e opacidade só no redesenho da textura; o piscar redesenha só a Tela com regra de piscar valendo e à vista, 2 vezes por segundo; no editor, a miniatura redesenha no máximo uma vez por quadro e só com o rascunho, os números ou o tema mudados; nada de alocação por quadro no desenho da GUI do editor além do que as telas do mod já fazem (textos medidos guardados até mudarem). Rode `python -I .claude/skills/wa-performance/scripts/varrer_perf.py` nos arquivos mudados e resolva ou justifique cada candidato no relatório.
- Lógica pura com JUnit (`src/test/java`); o que depende do jogo, com GameTest (`@GameTestHolder(WirelessAutomate.MODID)`, `@PrefixGameTestTemplate(false)`, `template = "empty"`, `timeoutTicks` ≤ 300; jogador só por `TestPlayers.inLevel`; cada teste cria a própria rede com `NetworkSavedData.get(server).create(...)`; compare redes com `Objects.equals`; teste que muda a config vai em `batch = "wirelessautomate_config_tv"` com `try/finally`; o `onLoad` só roda no tick seguinte: `startSequence().thenWaitUntil(...)`; testes do mesmo lote rodam em paralelo, então teste pertinência e não contagem global).
- Mod opcional: nada de tipos do Mekanism, Ars, FTB Teams ou Allthemodium em `tv/` e `client/tv/`; os tipos de recurso vêm do `ResourceType` e do `LoadedTypes`.
- Lado do cliente: tudo de tela e render só em `client/` (`client/tv/`, `client/tv/editor/`, `client/tv/render/`), nunca referenciado por código comum.
- Todo texto variável da GUI do editor passa por `GuiText` (o e2e confere `GuiText.clipCount() == 0`); cores da GUI pelo `GuiPaint`, nos dois temas; o botão `ThemeToggle` no cabeçalho. O texto **desenhado na Tela** (na parede e na miniatura) não passa pelo `GuiText`: encolhe pelo `TvFit` e nunca é cortado.
- Componentes de tela não mandam payload direto: tudo sai por uma classe de ações (`TvEditorActions`), que na captura só mexe no estado local.
- Tamanho (`wa-modular`): classe de tela até 500 linhas (alvo 300), servidor até 600; rode `python -I .claude/skills/wa-modular/scripts/medir.py` nos arquivos mudados. Comentários (`wa-comentarios`): `python -I .claude/skills/wa-comentarios/scripts/varrer.py` nos arquivos mudados.
- Guia: edite `scripts/guide/gerar_guia.py`, nunca os `.md` gerados. Não rode o `gerar_texturas.py` (reescreve dezenas de PNGs).
- Antes de cada commit: `./gradlew build runGameTestServer` (Git Bash, na raiz); tarefa só de lógica pura roda `./gradlew test --tests "..."`. As runs Chemicals, Source, Allthemodium e Teams ficam para a verificação final do ramo (pedido do dono em 27/10, para poupar tempo). Compile antes (`./gradlew compileJava`); com `BUILD FAILED`, pare e conserte, nunca espere um resultado que não vem. Tarefas de tela e render rodam o e2e (`./scripts/e2e.sh --only tela --capture tela`; `run/e2e/result.txt` precisa dizer `OK`), olham as capturas e copiam as novas para `.superpowers/sdd/2026-10-27-tela-de-sinal-etapa-4/capturas/` (o e2e seguinte apaga as de `run/e2e/`). Nunca dois jogos ao mesmo tempo (~3 GB cada); não mexa em `run/options.txt`. Se o e2e de outra suíte falhar por algo fora da tarefa, pare e relate: não entre em loop.
- Commits sem linhas de atribuição (nada de `Co-Authored-By` nem `Claude-Session`), com o autor do dono; `git add` só dos arquivos da tarefa (nunca `run/`, nunca `.superpowers/`). Não faça push.

## Tarefas

### Task 1: O modelo, a biblioteca e a pintura

Custo: só eventos no servidor; só redesenho no cliente.

- [ ] `TvPixelArt` (record e codec compacto; validação de tamanho e paleta), `TvLayout.images` com o teto `tv.maxImagePixels`, peça `image`, `TvBackground` `tiled`; NBT antigo lê igual.
- [ ] `TvLibrary.arts` com `tv.maxArts`, as ações `SAVE_ART`, `REMOVE_ART`, `RENAME_ART`, protocolo 38; `TvEditing.applyLayout` valida as imagens (as peças que apontam para imagem que não existe saem).
- [ ] Pintura por faixas (`TvPaintImage`, a varredura das faixas pura com JUnit) e o mosaico; e2e com a medida do redesenho de uma Tela 32 × 32 com mosaico (decisão 8 da spec).
- [ ] GameTests (ida e volta, tetos, biblioteca). Commit "Tela de Sinal: pixel art no modelo, na biblioteca e no desenho".

### Task 1b: A arte vira textura própria (feita, `9b5b7833`)

O redesenho por faixas mediu ~21 ms; a decisão 8 da spec troca por uma textura por arte (`TvArtTextures`), ~0,17 ms.

### Task 2: O editor de pixel art

Custo: só GUI; o quadro numa textura pequena refeita só quando um pixel muda; por quadro, poucos retângulos (grade, seleção, pincel sob o cursor).

- [ ] Lógica pura (JUnit): as ferramentas da spec (decisões 1 a 1f), o histórico de 100 passos e a vista (zoom no ponto, arrastar, enquadrar).
- [ ] A tela do editor de pixel art em partes (`wa-modular`): quadro, ferramentas com ícones novos no `tela_icones.py`, paleta em 4 rampas com o seletor de cor da etapa 2, prévia em 1×, 2× e 3 × 3, pincel, nome, tamanho, salvar e cancelar; todos os atalhos e a navegação da decisão 1g.
- [ ] A aba "Pixel art" na coluna da esquerda do editor da Tela (Nova, Editar, Duplicar, Remover em dois cliques; arrastar para o canvas cria a Imagem); as opções da Imagem na aba Estilo ("Atualizar da biblioteca", escala, alinhamento em 9 posições, opacidade); o Fundo em mosaico na base.
- [ ] e2e: desenhar uma arte pelo mouse, salvar, pôr como Imagem e como mosaico; capturas `tela-pixel-editor`, `tela-pixel-imagem`, `tela-pixel-mosaico`. Commit "Tela de Sinal: editor de pixel art".

### Task 1c: Orçamento maior (artes fora do pacote do chunk)

Custo: no servidor, o envio das artes é por evento (revisão nova ou jogador chegando perto), com orçamento por tick; no cliente, mais VRAM e mais redesenho por quadro, aceitos pelo dono.

- [ ] Update tag sem os pixels; pacote de artes do `TvManager` para quem está perto e não tem a revisão, com fila e orçamento por tick; envio na abertura do menu; cache por resumo no cliente; tetos novos (131.072 pixels por Tela, arte até 128 × 128, salvar até 256 KB); `vramBudgetMb` 1024 e o redesenho por quadro dobrado. GameTests (o update tag sem pixels, o pacote vai a quem está perto, uma vez por revisão, tetos, NBT antigo) e e2e (a Imagem aparece depois do pacote; uma Tela com 128 × 128). Commit "Tela de Sinal: artes fora do pacote do chunk e orçamento maior".

### Task 2c: Exportar e importar por código

Custo: só cliente, só no clique.

- [ ] Codec puro do código (`WA-TELA1:` e `WA-ARTE1:`, deflate e base64, tetos de tamanho) com JUnit; botões "Copiar código" e "Colar código" na lista de layouts e na aba Pixel art; a importação do layout como mudança desfazível; avisos de código inválido. e2e: exportar e importar um layout com imagem e uma arte. Commit "Tela de Sinal: exportar e importar layouts e pixel arts por código".

### Task 2e: A Imagem se escolhe como o Ícone

Custo: só GUI; as miniaturas da grade reaproveitam as texturas das artes.

- [ ] Imagem na paleta de Peças; a grade de artes na seção Peça da Imagem e no mosaico da base; e2e e capturas. Commit "Tela de Sinal: a Imagem escolhe a arte numa grade, como o Ícone".

### Task 2d: Acesso à Tela

Custo: só eventos (as ações da lista); a conferência de acesso no salvar é uma busca num conjunto pequeno.

- [ ] `TvBlockEntity.access` (jogadores e times, chave opcional), `canEdit` com a lista (e o `TeamAccess`), o pacote das ações (só o dono ou operador), tetos da rede; a aba Acesso no desenho da `AccessSection` da central. GameTests (convidado edita, removido não edita, time, tetos, NBT antigo) e o de times na run `gameTestServerTeams`. e2e: a aba Acesso. Commit "Tela de Sinal: acesso à Tela por jogador e por time".

### Task 3: Guia, docs e verificação

- [ ] Guia (pt e en), `CLAUDE.md`, `docs/especificacao.md`, `docs/progresso.md`.
- [ ] `./gradlew build runGameTestServer` e o e2e da Tela. Commit "Tela de Sinal: guia e docs da etapa 4".
