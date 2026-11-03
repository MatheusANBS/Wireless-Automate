# Tablet do zero, etapa 1: moldura e aba Redes, plano de implementação

> **Para agentes:** SUB-SKILL OBRIGATÓRIA: use superpowers:subagent-driven-development (recomendado) ou superpowers:executing-plans para executar este plano tarefa por tarefa. Os passos usam caixas (`- [ ]`) para acompanhar.

**Objetivo:** o Tablet abre numa moldura nova com três abas (Redes, Máquina, Fluxos); a aba Redes é a do mockup aprovado (grupos com ícone, rede escolhida com cor livre num seletor HSV, Grupo, Acesso, "Nesta rede", grupo escolhido com o ícone escolhido numa grade com busca e arraste do JEI); o seletor de cor vira um componente comum e a Tela de Sinal passa a usá-lo sem mudar o visual.

**Arquitetura:** no servidor, o `WaGroup` ganha `icon` (NBT opcional) e a ação `GROUP_ICON`; `GROUP_ADD_NETWORK` passa a mover a rede (uma rede, um grupo); o cabeçalho do Tablet leva o ícone do grupo, a máquina de reserva e, por rede, as máquinas e "dão/recebem" por tipo, contados por uma classe pura (`NetworkTally`) e guardados em cache pelas versões do índice e das redes. No cliente, as cores novas do mockup viram tokens do `GuiPalette`, o desenho comum vai para `client/PanelPaint`, a matemática da cor para `client/color/` (pura, com JUnit) e o seletor para `client/color/ColorPopup` (com dois visuais, `ColorLook.SIGNAL` e `ColorLook.TABLET`); o Tablet ganha `client/tablet/kit/` (componentes comuns) e `client/tablet/networks/` (a aba Redes nova, que substitui a antiga). Máquina e Fluxos mostram, dentro da moldura nova, a visão Máquinas da Lista e o Fluxos de hoje; o Mapa sai só da barra.

**Stack:** Java 21, NeoForge 21.1.248 (mínimo) / Minecraft 1.21.1, ModDevGradle, JUnit 5, GameTests, e2e (`scripts/e2e.sh`), JEI (API de ingrediente fantasma, já no projeto).

**Spec:** [`docs/superpowers/specs/2026-11-03-tablet-do-zero-design.md`](../specs/2026-11-03-tablet-do-zero-design.md), etapa 1. **Mockup:** `docs/preview/tablet-do-zero/*.png` (`redes`, `redes-cor`, `grupo`, `grupo-icone`, cada um também `-escuro`), gerado por `docs/preview/tablet-do-zero/gerar.py`, **a fonte das medidas**: todas as coordenadas deste plano são pixels de GUI relativos ao canto do painel (`layout.left`, `layout.top`) e vêm das funções do `gerar.py` citadas entre parênteses.

## Restrições globais

- Código, comentários, mensagens de commit e docs em português; todo texto de tela em `en_us.json` **e** `pt_br.json`, prefixo `gui.wirelessautomate.tablet.` (os do seletor de cor: `gui.wirelessautomate.color.`).
- **Leia antes de cada tarefa** `.claude/skills/wa-performance/SKILL.md`, `.claude/skills/wa-modular/SKILL.md` e `.claude/skills/wa-comentarios/SKILL.md`.
- **Performance (leis da `wa-performance` que esta etapa toca):** parado custa zero (tela fechada: nada roda nem é enviado); o cliente só recebe o que a aba aberta mostra, e só quando muda (o cabeçalho continua saindo por mudança, `TabletHeaderPayload`); por quadro, só desenhar: linhas, textos, larguras e `ItemStack`s montados quando chega um snapshot ou muda o tema, nunca no `render`; nada de `level.getCapability` ou registro percorrido por quadro; caminho quente sem alocação.
- **Tamanho (`wa-modular`):** tela, componente e widget em `client/` até 500 linhas (aviso 600, dividir 800); servidor até 600; método até 60. Componente não manda payload: as ações saem pela anfitriã (`TabletActions`). Lógica pura vai em classe pura com JUnit em `src/test/java`.
- **Cores só pelo `GuiPaint`** na hora de desenhar (os tokens novos da Tarefa 1); texto variável pelo `GuiText`; tipos pelo `LoadedTypes.LIST`; cliente só em `client/`, nunca referenciado por código comum.
- **Paleta (decisão 1 da spec):** o `acc` (`GuiPaint.ACCENT`) só na aba ativa e no botão primário (`GuiPaint.PRIMARY`); a linha escolhida tem a faixa de 2 px em `FG`; o campo com foco, a borda de 1 px em `FG`; o erro é `0xFFB8304F` (claro) / `0xFFE5577A` (escuro), no `danger` do `GuiPalette`; segmentos: o escolhido em `BEVEL_LIGHT` (o `hi` do mockup) com moldura de 1 px em `FG`, os outros planos em `FACE` com moldura `FRAME` e texto `MUTED`.
- **NBT:** a chave nova é só `icon` (string, id de item) no compound do grupo, gravada só quando existe; `color`, `networks`, `paused`, `name`, `owner`, `id` não mudam. Mundo antigo carrega igual (grupo sem ícone; rede em dois grupos fica como está até alguém mexer).
- **Protocolo:** `ModPayloads.VERSION` sobe de `"46"` para `"47"` na Tarefa 5, e só nela (ver "Decisão sobre o protocolo").
- **GameTests:** classe com `@GameTestHolder(WirelessAutomate.MODID)` e `@PrefixGameTestTemplate(false)`, template `"empty"`, `timeoutTicks` no máximo 300; cada teste cria as próprias redes e grupos e confere pertinência, nunca contagens totais; compare valores que podem ser nulos com `Objects.equals` (o helper `eq` do `TabletGameTests`). O `TabletGameTests` já tem 1280 linhas: testes novos vão em arquivo novo.
- **Verificação antes de cada commit** (Git Bash, na raiz; um Gradle ou jogo por vez, ~3 GB cada):
  ```bash
  ./gradlew build runGameTestServer runGameTestServerChemicals runGameTestServerSource runGameTestServerAllthemodium runGameTestServerTeams
  python -I .claude/skills/wa-performance/scripts/varrer_perf.py   # candidatos resolvidos ou justificados no relatório
  python -I .claude/skills/wa-modular/scripts/medir.py             # nada acima de "Dividir"
  python -I .claude/skills/wa-comentarios/scripts/varrer.py        # sem código comentado, histórico, TODO, {@link} quebrado
  ```
  Nas tarefas de tela, também o e2e: `./scripts/e2e.sh --only <suíte> --capture <prefixo>` (ver `docs/e2e.md`); `run/e2e/result.txt` precisa começar por `OK`, e as capturas novas são olhadas (texto cortado, cor errada). Compile antes (`./gradlew compileJava`): com `BUILD FAILED`, pare e conserte, nunca espere o `result.txt`.
- **Commits:** sem linhas de atribuição (`Co-Authored-By`, `Claude-Session`), autor do dono, sem push.
- Não mexa em `run/options.txt`.

## Decisão sobre o protocolo

`ModPayloads.VERSION` vai para **`"47"`** (Tarefa 5). Muda o formato de três coisas: o cabeçalho do `TabletSnapshot` (o `GroupView` ganha o ícone e a máquina de reserva; o `NetworkView` ganha as máquinas e as contagens por tipo), o enum `TabletMenu.Action` (constante nova `GROUP_ICON`, codificada pelo `enumCodec`, por posição) e o teto de texto do `TabletActionPayload` (`MAX_TEXT` de 64 para 256, porque um id de item pode passar de 64 caracteres e o codificador recusaria o pacote no cliente). Um cliente 46 num servidor 47 leria o cabeçalho torto; a versão nova faz o NeoForge recusar a conexão com a mensagem certa. A Tarefa 4 já acrescenta `GROUP_ICON` ao enum, mas o número sobe uma vez só, na Tarefa 5, que fecha o formato (as duas entram no mesmo ramo, sem release no meio). A linha do javadoc da `VERSION` ganha: `47: o ícone do grupo (GROUP_ICON e o GroupView com ícone e máquina), as máquinas e o "dão/recebem" por tipo no NetworkView e o texto de 256 do TabletActionPayload`.

## Ajustes em relação à spec (o código e o mockup contrariam ou não cobrem)

1. **O `TvHsv` já tem JUnit** (`src/test/.../client/tv/editor/TvPickerMathTest.java:10-26`, `hsvPrimaries` e `hsvRoundTrip`; as recentes também, `:28-45`). A spec diz que ele "ganha teste JUnit"; o plano **move** esses testes para `client/color/` e acrescenta os da classe pura nova `ColorState`.
2. **O mockup usa cores que não estão no `GuiPalette`** (`gerar.py:23-30`): `frame`, `bev_hi`, `bev_lo`, `face`, `slot`, `slot_hi`, `slot_lo`, `prim` (e `prim_ink`, branco). A decisão 1 diz "as cores são as do `GuiPalette`": a Tarefa 1 as acrescenta ao `GuiPalette` com os valores do mockup, nos dois temas. O `warn` do mockup (`0xC97E12`) difere do `GuiPalette` (`0xD88A1A`): fica o do `GuiPalette`, que a spec não manda trocar.
3. **"N máquinas" e "N dão · N recebem" não existem no snapshot:** o `NetworkView` (`menu/TabletSnapshot.java:268-275`) só traz `nodes` (roteadores) e `unloaded`. A Tarefa 5 acrescenta as máquinas por rede e as contagens por tipo, por máquina (a presa e as remotas, pelos papéis e máscaras que o `NodeIndex` já guarda: `Entry.attachedRoles/attachedNets`, `IndexRemotes.machineRoles/machineNets`). **Limite:** a máscara de redes de uma máquina é a união dos tipos, então uma máquina que extrai itens na rede A e recebe fluido na B conta "dá itens" só onde a rede A tem itens; o erro só aparece com uma máquina em duas redes no mesmo tipo e papéis cruzados.
4. **420 × 280 não cabe em 1080p com a escala automática** (escala 4 dá 480 × 270 de GUI). A tela continua redimensionável: `MIN_W` sobe de 300 para **420** (a coluna esquerda de 148 px mais o painel direito precisam disso) e `MIN_H` fica **240**; o rodapé se prende ao fundo (`h - 21`) e a coluna esquerda rola.
5. **O mockup não desenha o botão do tema nem o aviso do servidor.** O botão do tema fica logo depois de "Tablet" (x = 8 + largura do título em negrito + 6, y = 7); o aviso, entre o botão do tema e a primeira aba, em y = 4, altura 16.
6. **"Usar" (a rede ativa do Vinculador, `NETWORK_USE`)** existe na aba Redes de hoje (`client/tablet/NetworksTab.java:137-139`) e não aparece na spec nem no mockup, e não está em "O que sai do Tablet". O plano o mantém como um botão secundário "Usar" ao lado de "Pausar" no rodapé da rede (marcado "Em uso" quando ativa). **Ponto para o dono confirmar.**
7. **"+ Rede", "+ Grupo" e "+ convidar" não têm o fluxo no mockup.** "+ Rede" e "+ Grupo" criam na hora com um nome livre ("Rede", "Rede 2"...; "Grupo", "Grupo 2"...), escolhem o novo quando ele chega e abrem o nome para edição com o texto todo selecionado. "+ convidar" abre uma janela com véu com o `AccessPanel` de hoje (convidados, times e os dois campos com sugestões), que o e2e de acesso já cobre.
8. **"Pausar" na rede sem grupo:** a spec diz que pausa o grupo da rede; sem grupo, o botão fica desativado com a dica "Ponha a rede num grupo para pausá-la".
9. **Os seletores de cor têm medidas diferentes:** o da Tela de Sinal (`TvColorPopup.java:23-36`: 124 de largura, quadrado 84 × 56, matiz de 8) e o do mockup (`gerar.py:popup_cor`: 116 × 96, quadrado 88 × 56, matiz de 10, campo 104 × 14, recentes de 8 px a cada 12, moldura em `frame`, sombra). O componente comum leva um `ColorLook` com os dois conjuntos de medidas; a Tela de Sinal usa `ColorLook.SIGNAL` e fica pixel a pixel igual.
10. **O e2e de hoje depende de uma rede em dois grupos** (`client/dev/E2eNetworkSectionSteps.java:40-46`); com "uma rede, um grupo" ele é reescrito (Tarefa 8).

## Mapa de arquivos

| Arquivo | Papel |
| --- | --- |
| `client/GuiPalette.java`, `client/GuiPaint.java` (mudar) | Tokens `frame`, `reliefLight`, `reliefDark`, `face`, `slot`, `slotLight`, `slotDark`, `primary`; `danger` carmim |
| `client/PanelPaint.java` (novo) | O desenho do mockup: relevo, botão, primário, campo, slot, quadradinho, janela, véu, link, seta, chevron, lápis, ponto, linha escolhida |
| `client/color/ColorHsv.java`, `RecentColors.java` (novos, movidos de `client/tv/editor/TvHsv`, `TvRecentColors`) | HSV ↔ RGB; as 8 recentes da sessão |
| `client/color/ColorState.java` (novo, puro) | O valor do seletor: matiz, saturação, brilho, alfa, papel do tema; o que o arrasto e o hexadecimal fazem |
| `client/color/ColorLook.java` (novo) | As medidas de cada visual (`SIGNAL`, `TABLET`) |
| `client/color/HexField.java` (novo) | O campo hexadecimal (um `EditBox` sem borda, teclas repassadas), nos dois visuais |
| `client/color/ColorPopup.java` (novo) | A janela do seletor: desenho e mouse, com alfa, conta-gotas e cores do tema opcionais |
| `client/tv/editor/TvColorPopup.java` (mudar: vira adaptador) | `TvPopup` que embrulha o `ColorPopup` com tudo ligado; ganchos do e2e iguais |
| `network/WaGroup.java`, `network/NetworkSavedData.java` (mudar) | `icon`; `moveToGroup`; `nextColor` |
| `menu/TabletMenu.java`, `menu/TabletMenuActions.java` (mudar) | `GROUP_ICON`; `GROUP_ADD_NETWORK` move |
| `menu/NetworkTally.java` (novo, puro), `menu/NetworkTallies.java` (novo) | Contagem por rede (máquinas, dão, recebem por tipo) e o cache dela pelas versões |
| `menu/TabletSnapshot.java`, `menu/TabletSnapshotBuilder.java`, `packet/TabletActionPayload.java`, `packet/ModPayloads.java` (mudar) | Formato do cabeçalho, `MAX_TEXT`, `VERSION` 47 |
| `client/tablet/kit/` (novo pacote) | `SegmentLayout` e `ChipLayout` (puros), `Segments`, `TabletModal`, `ModalLayer`, `DropdownModal`, `PanelHeader`, `TitleEditor` |
| `client/tablet/TabletFrame.java` (novo, no lugar de `TabletHeader`) | Moldura: título, as 3 abas, botão do tema, linha, aviso |
| `client/tablet/TabletScreen.java`, `TabletLayout.java`, `TabletTabs.java`, `TabletBody.java`, `TabletKeys.java`, `TabletTips.java`, `TabletActions.java`, `TabletList.java` (mudar) | Moldura nova, aba Máquina = visão Máquinas da Lista, aba Redes nova |
| `client/tablet/networks/` (novo pacote) | `NetworksPanel` (anfitriã da aba), `NetworkTree` e `DefaultNames` (puros), `NetworkColumn`, `NetworkDetail`, `GroupPanel`, `GuestChips`, `AccessWindow`, `GroupPicker`, `ColorModal`, `IconModal`, `IconSearch` (puro), `NetworksText` |
| `client/tablet/NetworksTab.java`, `NetworkList.java`, `GroupDetail.java`, `NameFields.java`, `AccessSection.java`, `NetworkSections.java`, `SideList.java`, `TabletHeader.java` (apagar) | Substituídos; `NetworkSectionsTest` sai junto |
| `compat/jei/TabletGhostHandler.java` (novo), `compat/jei/WirelessAutomateJeiPlugin.java` (mudar) | Soltar um item do JEI na grade de ícones |
| `gametest/NetworkGroupGameTests.java` (novo) | Ícone (ida e volta, mundo antigo, ação), uma rede um grupo, próxima cor, contagens do cabeçalho |
| `client/dev/E2eNetworksSteps.java` (novo, no lugar de `E2eNetworkSectionSteps` e do `tabletGroupSteps`), `E2eTabletSteps.java`, `E2eAccessSteps.java`, `E2eShowcase.java`, `ScreenshotTablet.java`, `E2eFlowsSteps.java`, `E2ePortugueseSteps.java`, `E2eTabletMachinesSteps.java` (mudar) | e2e da aba Redes nova e das abas renomeadas |
| `lang/en_us.json`, `lang/pt_br.json` (mudar) | Textos novos |
| `scripts/guide/paginas/network_tablet.py`, `networks.py` (mudar) | O guia da aba Redes e das abas |
| `docs/progresso/*`, `docs/mapa/tablet.md`, `docs/mapa/tela-de-sinal.md`, `docs/mapa/cliente-e-pacotes.md`, `docs/mapa/motor.md` (mudar) | Documentação |

Pacote base de todos os caminhos Java: `src/main/java/io/github/matheusanbs/wirelessautomate/` (testes: `src/test/java/io/github/matheusanbs/wirelessautomate/`).

## Medidas do mockup (referência para as Tarefas 7 a 10)

Constantes de `gerar.py`: `W, H = 420, 280`; `X0 = 158` (início do painel direito); `X1 = 411` (borda direita, inclusive). Com a tela redimensionada: `X1 = w - 9`, as linhas presas ao fundo usam `h`. Texto: `drawString(x, y)` do jogo põe o topo da letra em `y`, igual ao `texto(x, y)` do gerador. "Direita em X1" (`direita(X1, ...)`) é `GuiPaint.textRight(..., X1 + 1, ...)`. Negrito: `Component` com `ChatFormatting.BOLD` (o jogo soma 1 px por letra, como o gerador).

| Peça (função do `gerar.py`) | Medidas |
| --- | --- |
| Moldura (`moldura`) | relevo do painel inteiro (`relevo(0,0,W,H, panel, bev_hi, bev_lo)`); "Tablet" negrito em (8, 10) `fg`; abas da direita para a esquerda a partir de `x = W - 12`: `x -= largura`, texto em (x, 10), ativa `fg` com sublinhado `acc` em (x − 1, 21, largura + 1, 2), inativa `muted`, `x -= 16`; linha `inset` em (2, 23, W − 4, 1) |
| Coluna (`coluna_esquerda`) | divisória `inset` em (150, 24, 1, H − 26) |
| Rodapé (`rodape`) | linha `inset` em (x0, H − 27, W − x0 − 2, 1), com x0 = 2 na coluna e 151 no painel; botões em y = H − 21, altura 16 |
| Linha escolhida (`realce_lista`) | `hi` em (x, y, w, h) e faixa `fg` de 2 px em (x, y, 2, h) |
| Lista de redes (`lista_redes`) | começa em y = 28; grupo: realce (2, y, 148, 20), ícone 16 em (8, y + 2), nome negrito `fg` em (28, y + 6) (ou "Sem grupo" `muted` sem negrito), "pausado" `muted` à direita em 141, y += 22; rede: realce (2, y − 2, 148, 14), quadradinho 7 em (28, y + 1), nome em (39, y + 1) (`muted` se o grupo está pausado), contagem `muted` à direita em 141, y += 14; depois das redes de um grupo, y += 4; "+ Rede" primário em (8, H − 21), largura texto + 16; "+ Grupo" 4 px depois |
| Botão (`botao`) | relevo (`face`, `bev_hi`, `bev_lo`), ou no primário (`prim`, `misturar(prim, branco, 0.3)`, `sombra(prim, 0.75)`); texto centrado em x + (w − texto)/2, y + (h − 8)/2 + 1; tinta `prim_ink` (branco) no primário, `fg` no outro |
| Relevo (`relevo`) | `frame` em (x, y, w, h); miolo `face`; luz 1 px em (x+1, y+1, w−2, 1) e (x+1, y+1, 1, h−2); sombra em (x+1, y+h−2, w−2, 1) e (x+w−2, y+1, 1, h−2) |
| Campo (`campo`) | borda 1 px `fg` com foco, `frame` sem; miolo `inset`; linha `slot_lo` em cima e à esquerda por dentro |
| Slot (`slot`, n = 18) | `slot` cheio; `slot_lo` em (x, y, n−1, 1) e (x, y, 1, n−1); `slot_hi` em (x+1, y+n−1, n−1, 1) e (x+n−1, y+1, 1, n−1); escolhido: borda `fg` em (x+1, y+1, n−2, n−2) |
| Quadradinho (`quadradinho`, n = 7) | cor cheia e borda `sombra(cor, 0.7)` |
| Segmentos (`segmentos`, h = 18) | cada parte: largura texto + 12 (+17 com ícone), a seguinte começa em x + w − 1; desenha os não escolhidos primeiro; escolhido: borda `fg`, miolo `hi`, texto `fg`; outros: borda `frame`, miolo `face`, texto `muted`; texto em (x + 6, y + (h − 8)/2 + 1), ícone em (x + 4, y + (h − 16)/2) e texto 17 px depois |
| Link (`link`) | texto `fg` e sublinhado `fg` em (x, y + 9, largura − 1, 1) |
| Cabeçalho (`cabecalho`) | ícone ou slot em (X0, 30); título negrito em (X0 + 21, 30); lápis 2 px depois de +4 (`lapis(fim + 4, 31)`); subtítulo `muted` em (X0 + 21, 40); estado: ponto 5 × 5 na cor em (X1 − texto − 7, 32) e o texto `muted` à direita em X1, y = 30; linha `inset` em (X0, 53, X1 − X0 + 1, 1) |
| Rede escolhida (`rede_detalhe`) | `vx = X0 + 58`; rótulos `muted` em (X0, y + 5); "Grupo" em y = 60: relevo (vx, 60, 112, 18), ícone (vx + 2, 61), nome (vx + 21, 65), seta `muted` em (vx + 102, 68); "Acesso" em y = 84: segmentos "Todos" e "Convidados" em (vx, 84); fichas em y = 107: borda `lo` (w = texto + 16, h = 12), texto em (xs + 4, 109), "x" em (xs + w − 9, 109), xs += w + 4; link "+ convidar" em (xs + 4, 109); linha `inset` em y = 130 (Convidados) ou 110 (Todos); "Nesta rede" `muted` em (X0, y + 7); tipos a partir de y + 20, passo 20: ícone em (X0, y), nome `fg` em (X0 + 20, y + 4), "N dão · N recebem" `muted` à direita em X1, y + 4; rodapé: "Pausar" em (X0, H − 21), "Apagar rede" `muted` à direita em X1, y = H − 16 |
| Slot da cor (`slot_cor`) | slot 18 em (x − 1, y − 1) e a cor em (x + 1, y + 1, 14, 14) |
| Seletor de cor (`popup_cor`) | véu `scrim` (= `panel`) alfa 150 em (2, 24, W − 4, H − 26); slot da cor redesenhado em (X0, 30); janela (X0 − 1, 50, 116, 96): sombra preta alfa 70 em (+2, +2) e relevo `hi`; quadrado 88 × 56 em (x + 6, y + 6) com borda `frame` 1 px por fora; marca: borda branca 5 × 5 centrada em (x+6 + s·87, y+6 + (1−v)·55); matiz 10 × 56 em (x + 100, y + 6), borda `frame` por fora, marca `fg` 14 × 1 em (x + 98, y + 6 + h·55); campo (x + 6, y + 66, 104, 14) com foco, texto em (x + 10, y + 69); recentes: quadradinho 8 em (x + 6 + i·12, y + 84), a atual com borda `fg` 12 × 12 em (−2, −2) |
| Grupo escolhido (`grupo_detalhe`) | cabeçalho com slot 18 em (X0 − 1, 29) e o ícone em (X0, 30), "N redes · N máquinas"; "Redes do grupo" `muted` em (X0, 61); linhas a partir de y = 75, passo 14: quadradinho (X0, y + 1), nome (X0 + 11, y + 1), "N máquinas" `muted` à direita em X1; link "+ pôr uma rede neste grupo" em (X0, y + 5); rodapé "Pausar grupo" e "Apagar grupo" |
| Ícone (`popup_icone`) | véu; slot e ícone redesenhados; janela (X0 − 1, 50, 8·18 + 14 = 158, 22 + 3·18 + 20 = 96); campo com foco (x + 7, y + 6, 144, 14), texto em (x + 14, y + 9), "Buscar item" `muted` vazio; células slot 18 em (x + 7 + col·18, y + 24 + lin·18), item em +1, +1, a escolhida com borda `fg`; "ou arraste um item do JEI" `muted` em (x + 7, y + 96 − 14) |
| Seta (`seta`) | 5 × 3: para i em 0..2, (x + i, y + i, 5 − 2i, 1) |
| Lápis (`lapis`) | para i em 0..5, (x + 5 − i, y + i, 2, 1); e (x, y + 6, 2, 1) |
| Ponto (`ponto`) | quadrado 5 × 5 |

---

### Task 1: Paleta do mockup e o desenho comum

**Files:**
- Modify: `client/GuiPalette.java`
- Modify: `client/GuiPaint.java:33-157` (campos e `apply`)
- Create: `client/PanelPaint.java`
- Test: `src/test/java/io/github/matheusanbs/wirelessautomate/client/GuiPaletteTest.java`, `src/test/java/io/github/matheusanbs/wirelessautomate/client/PanelPaintTest.java`

**Interfaces:**
- Produces: `GuiPalette` com os componentes novos no fim, nesta ordem: `int frame, int reliefLight, int reliefDark, int face, int slot, int slotLight, int slotDark, int primary`; `GuiPaint.FRAME`, `RELIEF_LIGHT`, `RELIEF_DARK`, `FACE`, `SLOT`, `SLOT_LIGHT`, `SLOT_DARK`, `PRIMARY` (`public static int`, refeitos no `apply`); `GuiPaint.DANGER` carmim; `PanelPaint` com `shade(int, float)`, `relief`, `reliefFace`, `button`, `field`, `slot`, `swatch`, `window`, `scrim`, `link`, `caret`, `chevron`, `pencil`, `square`, `chosenRow`, `rule` (assinaturas no passo 4).

**Custo:** nenhum no servidor; no cliente, oito campos estáticos a mais e métodos de desenho sem alocação (só `fill`).

- [ ] **Step 1: Teste que falha (valores do mockup e o carmim)**

Em `GuiPaletteTest`, acrescente:

```java
    @Test
    void mockupTokensInLight() {
        GuiPalette p = GuiPalette.LIGHT;
        assertEquals(0xFFB8304F, p.danger());
        assertEquals(0xFF8C826F, p.frame());
        assertEquals(0xFFFFFFFF, p.reliefLight());
        assertEquals(0xFFA89C84, p.reliefDark());
        assertEquals(0xFFFAF5EA, p.face());
        assertEquals(0xFFD6CCB6, p.slot());
        assertEquals(0xFFFFFBF3, p.slotLight());
        assertEquals(0xFF9C917A, p.slotDark());
        assertEquals(0xFFF0603E, p.primary());
    }

    @Test
    void mockupTokensInDark() {
        GuiPalette p = GuiPalette.DARK;
        assertEquals(0xFFE5577A, p.danger());
        assertEquals(0xFF0F0E12, p.frame());
        assertEquals(0xFF4A4550, p.reliefLight());
        assertEquals(0xFF16151A, p.reliefDark());
        assertEquals(0xFF332F39, p.face());
        assertEquals(0xFF3A3A44, p.slot());
        assertEquals(0xFF4E4C58, p.slotLight());
        assertEquals(0xFF151419, p.slotDark());
        assertEquals(0xFFD9573A, p.primary());
    }
```

e, no `everyColorIsOpaque`, acrescente ao array `all` os oito: `p.frame(), p.reliefLight(), p.reliefDark(), p.face(), p.slot(), p.slotLight(), p.slotDark(), p.primary()`. No `textReadsOnEveryBackground`, acrescente:

```java
        assertTrue(GuiPalette.contrast(p.fg(), p.face()) >= 7, "texto no botão do Tablet");
        assertTrue(GuiPalette.contrast(0xFFFFFFFF, p.primary()) >= 2.5, "texto no botão primário");
```

Crie `PanelPaintTest`:

```java
package io.github.matheusanbs.wirelessautomate.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PanelPaintTest {
    @Test
    void shadeKeepsAlphaAndScalesChannels() {
        assertEquals(0xFF7F7F7F, PanelPaint.shade(0xFFFFFFFF, 0.5f));
        assertEquals(0xFFA8432B, PanelPaint.shade(0xFFF0603E, 0.7f));
        assertEquals(0x80000000, PanelPaint.shade(0x80000000, 0.3f));
    }
}
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `./gradlew test --tests '*GuiPaletteTest' --tests '*PanelPaintTest'`
Expected: FAIL (compilação: `frame()` e `PanelPaint` não existem).

- [ ] **Step 3: Tokens no `GuiPalette`**

Acrescente os componentes no fim do record (depois de `int ring`), com o javadoc "Os do Tablet novo (mockup `docs/preview/tablet-do-zero`): moldura, luz e sombra do relevo, face de botão, slot e o primário.":

```java
        int tooltipMuted, int tooltipAccent, int darkText, int ring,
        int frame, int reliefLight, int reliefDark, int face, int slot, int slotLight, int slotDark, int primary) {
```

No `LIGHT`, troque `0xFFD5453A` (danger) por `0xFFB8304F` e acrescente no fim `0xFF8C826F, 0xFFFFFFFF, 0xFFA89C84, 0xFFFAF5EA, 0xFFD6CCB6, 0xFFFFFBF3, 0xFF9C917A, 0xFFF0603E`. No `DARK`, troque `0xFFE55A4E` por `0xFFE5577A` e acrescente `0xFF0F0E12, 0xFF4A4550, 0xFF16151A, 0xFF332F39, 0xFF3A3A44, 0xFF4E4C58, 0xFF151419, 0xFFD9573A`.

- [ ] **Step 4: Campos no `GuiPaint` e o `PanelPaint`**

No `GuiPaint`, depois de `RULE_TEXT`:

```java
    /** Tablet novo: moldura de 1 px do relevo e dos campos. */
    public static int FRAME;
    /** Luz e sombra do relevo (botões, seletores, janelas). */
    public static int RELIEF_LIGHT;
    public static int RELIEF_DARK;
    /** Face plana de botão e de segmento não escolhido. */
    public static int FACE;
    /** Slot de item e as linhas de luz e sombra dele. */
    public static int SLOT;
    public static int SLOT_LIGHT;
    public static int SLOT_DARK;
    /** O botão primário (o único lugar, com a aba ativa, em que o coral aparece). */
    public static int PRIMARY;
```

e no fim do `apply`:

```java
        FRAME = p.frame();
        RELIEF_LIGHT = p.reliefLight();
        RELIEF_DARK = p.reliefDark();
        FACE = p.face();
        SLOT = p.slot();
        SLOT_LIGHT = p.slotLight();
        SLOT_DARK = p.slotDark();
        PRIMARY = p.primary();
```

Crie `client/PanelPaint.java`:

```java
package io.github.matheusanbs.wirelessautomate.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * O desenho do Tablet novo e do seletor de cor no visual dele (mockup {@code docs/preview/tablet-do-zero}, as funções
 * de mesmo papel no {@code gerar.py}): relevo, botões, campo, slot, quadradinho de cor, janela com sombra, véu, link,
 * seta, chevron, lápis, ponto e a linha escolhida. Só {@code fill} com os tokens do {@link GuiPaint}, sem alocar.
 */
public final class PanelPaint {
    /** A tinta do texto no botão primário, igual nos dois temas. */
    public static final int PRIMARY_INK = 0xFFFFFFFF;
    /** A opacidade do véu atrás de uma janela (de 255). */
    private static final int SCRIM_ALPHA = 150;
    private static final int SHADOW = 0x46000000;

    private PanelPaint() {
    }

    /** A cor com os canais multiplicados por {@code f} (a sombra de um quadradinho, o relevo do primário). */
    public static int shade(int argb, float f) {
        int r = (int) ((argb >> 16 & 0xFF) * f);
        int g = (int) ((argb >> 8 & 0xFF) * f);
        int b = (int) ((argb & 0xFF) * f);
        return argb & 0xFF000000 | r << 16 | g << 8 | b;
    }

    /** Relevo: moldura {@link GuiPaint#FRAME}, miolo {@code face}, luz em cima e à esquerda, sombra embaixo e à direita. */
    public static void relief(GuiGraphics g, int x, int y, int w, int h, int face, int light, int dark) {
        g.fill(x, y, x + w, y + h, GuiPaint.FRAME);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, face);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, light);
        g.fill(x + 1, y + 1, x + 2, y + h - 1, light);
        g.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, dark);
        g.fill(x + w - 2, y + 1, x + w - 1, y + h - 1, dark);
    }

    /** O relevo de um botão ou seletor comum. */
    public static void reliefFace(GuiGraphics g, int x, int y, int w, int h, boolean hovered) {
        relief(g, x, y, w, h, hovered ? GuiPaint.ROW_HOVER : GuiPaint.FACE, GuiPaint.RELIEF_LIGHT, GuiPaint.RELIEF_DARK);
    }

    /** Botão com o texto centrado; o primário em {@link GuiPaint#PRIMARY} com tinta branca. Desativado: texto apagado. */
    public static void button(GuiGraphics g, Font font, int x, int y, int w, int h, Component text, boolean primary,
            boolean hovered, boolean enabled) {
        if (primary) {
            int face = hovered && enabled ? GuiPaint.mix(GuiPaint.PRIMARY, 0xFFFFFFFF, 0.12f) : GuiPaint.PRIMARY;
            relief(g, x, y, w, h, face, GuiPaint.mix(face, 0xFFFFFFFF, 0.3f), shade(face, 0.75f));
        } else {
            reliefFace(g, x, y, w, h, hovered && enabled);
        }
        int ink = !enabled ? GuiPaint.DISABLED : primary ? PRIMARY_INK : GuiPaint.FG;
        GuiPaint.text(g, font, text, x + (w - font.width(text)) / 2, y + (h - 8) / 2 + 1, ink);
    }

    /** Campo de texto: borda {@code FG} com foco ({@code FRAME} sem), miolo afundado. */
    public static void field(GuiGraphics g, int x, int y, int w, int h, boolean focused) {
        g.fill(x, y, x + w, y + h, focused ? GuiPaint.FG : GuiPaint.FRAME);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, GuiPaint.INSET);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, GuiPaint.SLOT_DARK);
        g.fill(x + 1, y + 1, x + 2, y + h - 1, GuiPaint.SLOT_DARK);
    }

    /** Slot de item {@code n} × {@code n}; o escolhido com a borda {@code FG} por dentro. */
    public static void slot(GuiGraphics g, int x, int y, int n, boolean chosen) {
        g.fill(x, y, x + n, y + n, GuiPaint.SLOT);
        g.fill(x, y, x + n - 1, y + 1, GuiPaint.SLOT_DARK);
        g.fill(x, y, x + 1, y + n - 1, GuiPaint.SLOT_DARK);
        g.fill(x + 1, y + n - 1, x + n, y + n, GuiPaint.SLOT_LIGHT);
        g.fill(x + n - 1, y + 1, x + n, y + n, GuiPaint.SLOT_LIGHT);
        if (chosen) {
            GuiPaint.outline(g, x + 1, y + 1, n - 2, n - 2, GuiPaint.FG);
        }
    }

    /** Quadradinho de cor {@code n} × {@code n} (ARGB opaco), com a borda na própria cor escurecida. */
    public static void swatch(GuiGraphics g, int x, int y, int n, int argb) {
        g.fill(x, y, x + n, y + n, argb);
        GuiPaint.outline(g, x, y, n, n, shade(argb, 0.7f));
    }

    /** Janela solta: sombra de 2 px e relevo claro. */
    public static void window(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x + 2, y + 2, x + w + 2, y + h + 2, SHADOW);
        relief(g, x, y, w, h, GuiPaint.BEVEL_LIGHT, GuiPaint.RELIEF_LIGHT, GuiPaint.RELIEF_DARK);
    }

    /** O véu que apaga o que está atrás de uma janela. */
    public static void scrim(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, GuiPaint.alpha(GuiPaint.PANEL, SCRIM_ALPHA));
    }

    /** Link: texto {@code FG} sublinhado. Devolve onde o texto termina. */
    public static int link(GuiGraphics g, Font font, Component text, int x, int y) {
        int w = font.width(text);
        GuiPaint.text(g, font, text, x, y, GuiPaint.FG);
        g.fill(x, y + 9, x + w - 1, y + 10, GuiPaint.FG);
        return x + w;
    }

    /** A seta de um seletor que abre para baixo, 5 × 3. */
    public static void caret(GuiGraphics g, int x, int y, int color) {
        for (int i = 0; i < 3; i++) {
            g.fill(x + i, y + i, x + 5 - i, y + i + 1, color);
        }
    }

    /** Chevron 4 × 7 (fechado, para a direita) ou 7 × 4 (aberto, para baixo). */
    public static void chevron(GuiGraphics g, int x, int y, boolean open, int color) {
        for (int i = 0; i < 4; i++) {
            g.fill(x + i, y + i, x + i + 1, y + i + 1, color);
            if (open) {
                g.fill(x + 6 - i, y + i, x + 7 - i, y + i + 1, color);
            } else {
                g.fill(x + i, y + 6 - i, x + i + 1, y + 7 - i, color);
            }
        }
    }

    /** O lápis de "dá para renomear", 7 × 7. */
    public static void pencil(GuiGraphics g, int x, int y, int color) {
        for (int i = 0; i < 6; i++) {
            g.fill(x + 5 - i, y + i, x + 7 - i, y + i + 1, color);
        }
        g.fill(x, y + 6, x + 2, y + 7, color);
    }

    /** Ponto de estado, quadrado 5 × 5. */
    public static void square(GuiGraphics g, int x, int y, int color) {
        g.fill(x, y, x + 5, y + 5, color);
    }

    /** A linha escolhida de uma lista: fundo claro e a faixa de 2 px em {@code FG}. */
    public static void chosenRow(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, GuiPaint.BEVEL_LIGHT);
        g.fill(x, y, x + 2, y + h, GuiPaint.FG);
    }

    /** Linha divisória de 1 px. */
    public static void rule(GuiGraphics g, int x, int y, int w) {
        g.fill(x, y, x + w, y + 1, GuiPaint.INSET);
    }
}
```

- [ ] **Step 5: Rodar e ver passar**

Run: `./gradlew test --tests '*GuiPaletteTest' --tests '*PanelPaintTest'`
Expected: PASS.

- [ ] **Step 6: Verificação completa e commit**

Rode a verificação das Restrições globais (o `DANGER` mudou em todo o mod: a suíte `tela` do e2e, `./scripts/e2e.sh --only tela`, tem de passar). Depois:

```bash
git add src/main/java/io/github/matheusanbs/wirelessautomate/client/GuiPalette.java src/main/java/io/github/matheusanbs/wirelessautomate/client/GuiPaint.java src/main/java/io/github/matheusanbs/wirelessautomate/client/PanelPaint.java src/test/java/io/github/matheusanbs/wirelessautomate/client/GuiPaletteTest.java src/test/java/io/github/matheusanbs/wirelessautomate/client/PanelPaintTest.java
git commit -m "Tablet do zero 1/11: as cores do mockup no GuiPalette (erro carmim) e o PanelPaint"
```

---

### Task 2: A matemática da cor em `client/color/`

**Files:**
- Create: `client/color/ColorHsv.java` (conteúdo do `client/tv/editor/TvHsv.java`, classe renomeada)
- Create: `client/color/RecentColors.java` (conteúdo do `client/tv/editor/TvRecentColors.java`, `public`)
- Create: `client/color/ColorState.java`
- Delete: `client/tv/editor/TvHsv.java`, `client/tv/editor/TvRecentColors.java`
- Modify: `client/tv/editor/TvColorPopup.java` (imports e nomes), `client/tv/pixel/TvPixelRamp.java:3,46,58`
- Test: Create `src/test/.../client/color/ColorHsvTest.java`, `RecentColorsTest.java`, `ColorStateTest.java`; Modify `src/test/.../client/tv/editor/TvPickerMathTest.java` (fica só o `numberDrag`), `src/test/.../client/tv/pixel/TvPixelPartsTest.java:9`

**Interfaces:**
- Produces: `public final class ColorHsv { static int toRgb(float h, float s, float v); static float[] fromRgb(int rgb) }` (públicos); `public final class RecentColors { public static final int MAX = 8; public static void push(int); public static int size(); public static int get(int); public static void clear() }`; `public final class ColorState` (passo 3).

**Custo:** nenhum: só roda em evento do seletor.

- [ ] **Step 1: Mover os testes e escrever os do `ColorState` (falham)**

`ColorHsvTest` recebe `hsvPrimaries` e `hsvRoundTrip` do `TvPickerMathTest` (troque `TvHsv` por `ColorHsv`); `RecentColorsTest` recebe `recentColorsKeepEightNewestFirst` (troque `TvRecentColors` por `RecentColors`); apague os três do `TvPickerMathTest`. Crie `ColorStateTest`:

```java
package io.github.matheusanbs.wirelessautomate.client.color;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ColorStateTest {
    private static final int[] NO_THEME = new int[6];

    @Test
    void readsTheStoredColor() {
        ColorState state = new ColorState(0xFF6B6FD0, false, NO_THEME);
        assertEquals(0xFF6B6FD0, state.value());
        assertEquals(0x6B6FD0, ColorHsv.toRgb(state.h(), state.s(), state.v()));
        assertEquals(255, state.a());
    }

    @Test
    void squareSetsSaturationAndBrightness() {
        ColorState state = new ColorState(0xFFFF0000, false, NO_THEME);
        state.square(0, 0);
        assertEquals(0xFFFFFFFF, state.value());
        state.square(1, 1);
        assertEquals(0xFF000000, state.value());
        state.square(2, -1);
        assertEquals(0xFFFF0000, state.value());
    }

    @Test
    void hueStopsBeforeWrapping() {
        ColorState state = new ColorState(0xFFFF0000, false, NO_THEME);
        state.hue(1.5);
        assertTrue(state.h() < 1f);
        state.hue(-1);
        assertEquals(0f, state.h());
    }

    @Test
    void alphaOnlyWhenAllowed() {
        ColorState solid = new ColorState(0x80FF0000, false, NO_THEME);
        assertEquals(255, solid.a());
        ColorState clear = new ColorState(0x80FF0000, true, NO_THEME);
        assertEquals(0x80, clear.a());
        clear.alpha(1);
        assertEquals(1, clear.a());
        clear.alpha(0);
        assertEquals(255, clear.a());
    }

    @Test
    void hexAcceptsSixOrEightDigits() {
        ColorState state = new ColorState(0xFF000000, false, NO_THEME);
        assertFalse(state.typed("#12345", false));
        assertTrue(state.typed("123456", false));
        assertEquals(0xFF123456, state.value());
        assertFalse(state.typed("#12345600", false));
        assertTrue(state.typed("#12345600", true));
        assertEquals(0, state.value());
    }

    @Test
    void themeRoleReadsTheThemeColor() {
        int[] theme = {0xFF101010, 0xFFFF0000, 0, 0, 0, 0};
        ColorState state = new ColorState(2, false, theme);
        assertEquals(2, state.value());
        assertEquals(0xFF0000, ColorHsv.toRgb(state.h(), state.s(), state.v()));
    }

    @Test
    void pickedColorNeedsAlpha() {
        ColorState state = new ColorState(0xFF000000, false, NO_THEME);
        assertFalse(state.picked(0x00FFFFFF));
        assertTrue(state.picked(0x40FF0000));
        assertEquals(0xFFFF0000, state.value());
    }
}
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `./gradlew test --tests '*client.color*'`
Expected: FAIL (classes não existem).

- [ ] **Step 3: As classes**

`ColorHsv` e `RecentColors`: copie o corpo das antigas, pacote `io.github.matheusanbs.wirelessautomate.client.color`, `RecentColors` e seus métodos `public`. Javadoc do `RecentColors`: "As últimas cores fixas escolhidas em qualquer seletor (Tela de Sinal e Tablet), até {@link #MAX}, a mais nova primeiro, lembradas só na sessão."

`ColorState`:

```java
package io.github.matheusanbs.wirelessautomate.client.color;

import io.github.matheusanbs.wirelessautomate.tv.TvColor;

/**
 * O valor de um seletor de cor aberto: o guardado (ARGB, 0 = nenhuma ou o papel 1 a 6 do tema da Tela de Sinal,
 * {@link TvColor}) e o matiz, a saturação, o brilho e o alfa que o quadrado, as faixas e o campo mexem. Lógica pura,
 * com JUnit; a janela ({@link ColorPopup}) só converte o mouse em frações de 0 a 1 e chama daqui.
 */
public final class ColorState {
    private final boolean alpha;
    /** As cores ARGB dos papéis 1 a 6 do tema; zeros sem tema. */
    private final int[] themeColors;
    private int value;
    private float h;
    private float s;
    private float v = 1;
    private int a = 255;

    public ColorState(int value, boolean alpha, int[] themeColors) {
        this.alpha = alpha;
        this.themeColors = themeColors;
        take(value);
    }

    public int value() {
        return value;
    }

    public float h() {
        return h;
    }

    public float s() {
        return s;
    }

    public float v() {
        return v;
    }

    public int a() {
        return a;
    }

    /** O RGB opaco do matiz, saturação e brilho atuais (a cor do quadrado e da faixa de alfa). */
    public int rgb() {
        return 0xFF000000 | ColorHsv.toRgb(h, s, v);
    }

    /** Lê matiz, saturação, brilho e alfa de um valor guardado (papel do tema, 0 ou ARGB). */
    public void take(int stored) {
        value = stored;
        int argb = TvColor.isTheme(stored) ? themeColors[stored - 1] : stored;
        if (argb == 0) {
            return;
        }
        float[] hsv = ColorHsv.fromRgb(argb & 0xFFFFFF);
        h = hsv[0];
        s = hsv[1];
        v = hsv[2];
        a = alpha ? Math.max(1, argb >>> 24) : 255;
    }

    /** O ponto do quadrado, em frações da largura ({@code fx}) e da altura ({@code fy}). */
    public void square(double fx, double fy) {
        s = (float) clamp(fx, 0, 1);
        v = 1 - (float) clamp(fy, 0, 1);
        apply();
    }

    /** O ponto da faixa de matiz, em fração da altura; para antes de dar a volta ao vermelho. */
    public void hue(double fy) {
        h = (float) clamp(fy, 0, 0.9999);
        apply();
    }

    /** O ponto da faixa de alfa, em fração da altura (em cima, opaco); nunca zero, que é "nenhuma". */
    public void alpha(double fy) {
        a = Math.max(1, Math.min(255, Math.round((1 - (float) clamp(fy, 0, 1)) * 255)));
        apply();
    }

    /** O texto do campo hexadecimal; devolve se virou uma cor. O zero só vale com {@code allowNone}. */
    public boolean typed(String text, boolean allowNone) {
        Integer parsed = TvColor.fromHex(text);
        if (parsed == null || parsed == 0 && !allowNone) {
            return false;
        }
        take(parsed);
        return true;
    }

    /** Uma cor pega (conta-gotas, recente); transparente não vale. */
    public boolean picked(int argb) {
        if (argb >>> 24 == 0) {
            return false;
        }
        take(alpha ? argb : argb | 0xFF000000);
        return true;
    }

    /** Escolhe o papel {@code role} (1 a 6) do tema, ou 0 para nenhuma. */
    public void stored(int role) {
        value = role;
    }

    private void apply() {
        value = a << 24 | ColorHsv.toRgb(h, s, v);
    }

    private static double clamp(double x, double min, double max) {
        return x < min ? min : x > max ? max : x;
    }
}
```

Atenção ao `alpha(0)` do teste: `1 - 0 = 1` → 255. O `alpha(1)` dá `round(0)` = 0 → `max(1, …)` = 1.

No `TvColorPopup`, troque `TvHsv` por `ColorHsv` e `TvRecentColors` por `RecentColors` (imports de `client.color`); no `TvPixelRamp` e no `TvPixelPartsTest`, o import.

- [ ] **Step 4: Rodar e ver passar**

Run: `./gradlew test`
Expected: PASS (inclusive `TvPickerMathTest.numberDrag` e `TvPixelPartsTest`).

- [ ] **Step 5: Verificação e commit**

Rode a verificação das Restrições globais (sem e2e: nada visível mudou). Commit: `Tablet do zero 2/11: ColorHsv, RecentColors e ColorState em client/color, com JUnit`.

---

### Task 3: O seletor de cor comum, e a Tela de Sinal sobre ele

**Files:**
- Create: `client/color/ColorLook.java`, `client/color/HexField.java`, `client/color/ColorPopup.java`
- Modify: `client/tv/editor/TvColorPopup.java` (vira adaptador, ~110 linhas), `client/tv/editor/TvEditorProbe.java:236-244` (`colorHexFocus`)
- Modify: `lang/en_us.json`, `lang/pt_br.json` (as chaves `gui.wirelessautomate.tv.editor.color.from_theme` e `.recent` viram `gui.wirelessautomate.color.from_theme` e `.recent`, mesmos textos; `.eyedropper` e `.none` ficam onde estão)

**Interfaces:**
- Consumes: `ColorState`, `ColorHsv`, `RecentColors` (Tarefa 2); `PanelPaint` (Tarefa 1).
- Produces:
  ```java
  public record ColorLook(int width, int pad, int svW, int svH, int svY, int strip, int stripGap, int hexY, int hexW,
          int hexH, int eyeDx, int eyeY, int noneDx, int recentY, int recent, int recentStep, boolean recentLabel,
          boolean framed, int themeY, int themeRow) {
      public static final ColorLook SIGNAL; public static final ColorLook TABLET;
  }
  public final class ColorPopup {
      public interface Eyedropper { void draw(GuiGraphics g, int x, int y); int size(); Component tip(); }
      public record Options(ColorLook look, boolean allowNone, boolean alpha, int @Nullable [] themeColors,
              @Nullable Eyedropper eyedropper) {}
      public static int height(Options options);
      public ColorPopup(Font font, int x, int y, int value, Options options, IntConsumer set);
      public boolean contains(double mx, double my); public void render(GuiGraphics g, int mx, int my);
      public void mouseClicked(double mx, double my, int button); public boolean mouseDragged(double mx, double my);
      public boolean mouseReleased(); public boolean keyPressed(int key, int scan, int mods);
      public boolean charTyped(char c, int mods); public boolean wantsKeys(); public @Nullable Component tooltip(double mx, double my);
      public void closed(); public boolean picking(); public void picked(int argb);
      public int value(); public int x(); public int y(); public int width(); public int height();
      public int[] themeCenter(int role); public int[] hexCenter(); public void focusHex(); public int[] svCenter();
      public int[] eyeCenter();
  }
  ```

**Custo:** o mesmo do seletor de hoje: as colunas do quadrado refeitas só quando o matiz muda; o resto é `fill`.

- [ ] **Step 1: Capturar o antes**

Com o código da Tarefa 2: `./scripts/e2e.sh --only tela --capture tela-editor --out run/e2e-antes`. Guarde `run/e2e-antes/` (a captura `tela-editor-cor` mostra o seletor).

- [ ] **Step 2: `ColorLook`**

```java
package io.github.matheusanbs.wirelessautomate.client.color;

/**
 * As medidas de um visual do {@link ColorPopup}, em px a partir do canto da janela e sem a linha das cores do tema
 * (que, quando existe, fica em {@code themeY} e empurra o resto {@code themeRow} px para baixo). {@code framed}: o visual
 * do Tablet novo (janela com sombra e relevo, bordas {@code FRAME} no quadrado e nas faixas, campo e marcas como no
 * mockup); sem ele, o da Tela de Sinal.
 */
public record ColorLook(int width, int pad, int svW, int svH, int svY, int strip, int stripGap, int hexY, int hexW,
        int hexH, int eyeDx, int eyeY, int noneDx, int recentY, int recent, int recentStep, boolean recentLabel,
        boolean framed, int themeY, int themeRow) {
    /** A Tela de Sinal ({@code TvColorPopup} de sempre: 124 de largura, quadrado 84 × 56, faixas de 8). */
    public static final ColorLook SIGNAL = new ColorLook(124, 6, 84, 56, 14, 8, 4, 76, 70, 11, 74, 74, 92, 101, 11, 13,
            true, false, 14, 18);
    /** O Tablet (mockup {@code popup_cor}: 116 × 96, quadrado 88 × 56, matiz de 10, campo 104 × 14, recentes de 8). */
    public static final ColorLook TABLET = new ColorLook(116, 6, 88, 56, 6, 10, 6, 66, 104, 14, 0, 0, 0, 84, 8, 12,
            false, true, 0, 0);

    /** A altura sem a linha do tema. */
    public int baseHeight() {
        return recentY + recent + 4;
    }
}
```

Confira contra o `TvColorPopup` antigo: `SV_Y 32 − 18 = 14`, `ROW2_Y 94 − 18 = 76`, `EYE_Y 92 − 18 = 74`, `RECENT_Y 119 − 18 = 101`, `H 134 − 18 = 116 = 101 + 11 + 4`. Tablet: `84 + 8 + 4 = 96`.

- [ ] **Step 3: `HexField`**

Um campo com o comportamento do `TvTextField` (que fica na Tela de Sinal para os outros controles): `EditBox` sem borda, `setTextShadow(false)`, `setMaxLength(9)`, filtro `#?[0-9a-fA-F]*`, `setResponder` que chama `change` a cada letra fora do `syncing`; `refresh()` relê o texto só sem foco; `mouseClicked` dá o foco; Enter, Esc ou clique fora (`blur()`) soltam o foco e relêem; `focus()` para o e2e. Desenho:

```java
    void render(GuiGraphics g, int mouseX, int mouseY) {
        if (framed) {
            PanelPaint.field(g, x, y, w, h, box.isFocused());
            box.setX(x + 4);
            box.setY(y + 3);
        } else {
            GuiPaint.box(g, x, y, w, h, GuiPaint.INSET, box.isFocused() ? GuiPaint.ACCENT : GuiPaint.BUTTON_BORDER);
            box.setX(x + 3);
            box.setY(y + 2);
        }
        if (inkVersion != GuiThemes.version()) {
            inkVersion = GuiThemes.version();
            GuiPaint.ink(box, hint);
        }
        box.render(g, mouseX, mouseY, 0f);
    }
```

Construtor: `HexField(Font font, int x, int y, int w, int h, boolean framed, Supplier<String> get, Consumer<String> change)`, o `EditBox` com largura `w - (framed ? 8 : 6)`, dica `Component.literal("#RRGGBB")`. Métodos: `contains`, `mouseClicked`, `keyPressed`, `charTyped`, `wantsKeys`, `blur`, `refresh`, `focus`, `centerX`, `centerY`. Classe package-private (só o `ColorPopup` a usa).

- [ ] **Step 4: `ColorPopup`**

Mova o corpo do `TvColorPopup` (render, mouse, teclas, ganchos) para `ColorPopup`, com estas regras:

- O estado vai para um `ColorState state = new ColorState(value, options.alpha(), themeColors)`, onde `themeColors` é `options.themeColors()` ou `new int[6]`. `shift` some: `int top = hasTheme ? look.themeRow() : 0`, e toda linha usa `y + top + look.<campo>()`; a linha do tema fica em `y + look.themeY()`.
- Geometria: `svX = x + pad`, `svY = y + top + look.svY()`, `hueX = svX + svW + stripGap`, `alphaX = hueX + strip + stripGap`, `eyeX = x + pad + eyeDx`, `noneX = x + pad + noneDx`, recentes em `x + pad + i * recentStep`, `y + top + recentY`, altura `look.baseHeight() + top`. `height(Options)` estático devolve isso para quem posiciona.
- Desenho com `framed == false`: **exatamente** o do `TvColorPopup` de hoje (`GuiPaint.box` BUTTON/BUTTON_BORDER, marca branca 5 × 5 e preta 7 × 7, faixas com a marca branca e preta de 1 px, amostras com xadrez e borda ACCENT na atual, conta-gotas, "nenhuma", rótulo "Recentes" acima das recentes).
- Desenho com `framed == true` (mockup): `PanelPaint.window(g, x, y, width, height)`; borda `FRAME` 1 px por fora do quadrado (`GuiPaint.outline(g, svX − 1, svY − 1, svW + 2, svH + 2, GuiPaint.FRAME)`) e da faixa de matiz (`hueX − 1, svY − 1, strip + 2, svH + 2`); marca do quadrado só a borda branca 5 × 5; marca do matiz `fill(hueX − 2, at, hueX + strip + 2, at + 1, FG)`; recentes com `PanelPaint.swatch(g, rx, ry, recent, color)` e, na atual, `GuiPaint.outline(g, rx − 2, ry − 2, recent + 4, recent + 4, FG)`; sem rótulo "Recentes" (`look.recentLabel()` falso).
- O quadrado: `columns = new int[svW / 2]` refeitas só quando `state.h()` muda (como hoje); com `svW = 88`, 44 colunas de 2 px.
- Mouse: `dragTo` vira `state.square((mx − svX) / (svW − 1), (my − svY) / (svH − 1))`, `state.hue((my − svY) / (svH − 1))`, `state.alpha(...)`, e depois `set.accept(state.value()); hex.refresh();`. Clique numa cor do tema: `state.stored(role)`; em "nenhuma": `state.stored(0)`; numa recente: `if (state.picked(RecentColors.get(i))) { set...; hex.refresh(); }`. O campo: `typed(text)` chama `state.typed(text, allowNone)` e, se virou, `set.accept(state.value())`.
- `closed()`: `hex.blur()` e, se `state.value() != initial`, `RecentColors.push(state.value())`.
- Rótulos: `Component.translatable("gui.wirelessautomate.color.from_theme")` e `"gui.wirelessautomate.color.recent"`, medidos uma vez no construtor (`GuiText.fit`).
- Profundidade: `g.pose().pushPose(); g.pose().translate(0, 0, 400); … popPose()` como hoje.
- Conta-gotas: só com `options.eyedropper() != null`; desenha a caixa como hoje (`GuiPaint.box` SELECTED/ACCENT quando `picking`) e chama `eyedropper.draw(g, eyeX, eyeY)`; a dica é `eyedropper.tip()`.

A classe deve ficar abaixo de 500 linhas (a de hoje tem 420 com um visual só; se passar, tire o desenho das amostras e das faixas para `ColorStrips`, package-private, com `hue`, `alpha`, `checker` e `swatch`).

- [ ] **Step 5: `TvColorPopup` como adaptador**

```java
/**
 * O seletor de cor do editor da Tela de Sinal: o {@link ColorPopup} comum com tudo ligado (cores do tema, alfa,
 * conta-gotas e "nenhuma" quando a cor pode faltar), no visual {@link ColorLook#SIGNAL}, posto ao lado da amostra que
 * o abriu. Só existe aberta; os ganchos do e2e passam direto.
 */
final class TvColorPopup implements TvPopup {
    private static final ColorPopup.Eyedropper EYE = new ColorPopup.Eyedropper() {
        private final Component tip = TvEditorText.key("color.eyedropper");

        @Override
        public void draw(GuiGraphics g, int x, int y) {
            TvIcons.draw(g, TvIcons.Icon.EYEDROPPER, x, y);
        }

        @Override
        public int size() {
            return TvIcons.SIZE;
        }

        @Override
        public Component tip() {
            return tip;
        }
    };

    private final ColorPopup popup;

    TvColorPopup(Font font, TvRect anchor, TvRect bounds, int value, boolean allowNone, boolean alpha,
            @Nullable TvTheme theme, IntConsumer set) {
        int[] themeColors = null;
        if (theme != null) {
            themeColors = new int[6];
            for (int i = 0; i < 6; i++) {
                themeColors[i] = theme.color(i + 1) | 0xFF000000;
            }
        }
        ColorPopup.Options options = new ColorPopup.Options(ColorLook.SIGNAL, allowNone, alpha, themeColors, EYE);
        int w = ColorLook.SIGNAL.width();
        int h = ColorPopup.height(options);
        int left = anchor.x() - w - 4;
        if (left < bounds.x()) {
            left = anchor.right() + 4;
        }
        int x = Mth.clamp(left, bounds.x(), Math.max(bounds.x(), bounds.right() - w));
        int y = Mth.clamp(anchor.y(), bounds.y(), Math.max(bounds.y(), bounds.bottom() - h));
        this.popup = new ColorPopup(font, x, y, value, options, set);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY) {
        popup.render(g, mouseX, mouseY);
    }

    @Override
    public boolean contains(double mouseX, double mouseY) {
        return popup.contains(mouseX, mouseY);
    }

    @Override
    public void mouseClicked(double mouseX, double mouseY, int button) {
        popup.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY) {
        return popup.mouseDragged(mouseX, mouseY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY) {
        return popup.mouseReleased();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return popup.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        return popup.charTyped(c, modifiers);
    }

    @Override
    public boolean wantsKeys() {
        return popup.wantsKeys();
    }

    @Override
    public @Nullable Component tooltip(double mouseX, double mouseY) {
        return popup.tooltip(mouseX, mouseY);
    }

    @Override
    public void closed() {
        popup.closed();
    }

    boolean picking() {
        return popup.picking();
    }

    void picked(int argb) {
        popup.picked(argb);
    }

    int[] themeCenter(int role) {
        return popup.themeCenter(role);
    }

    int[] hexCenter() {
        return popup.hexCenter();
    }

    void focusHex() {
        popup.focusHex();
    }

    int[] svCenter() {
        return popup.svCenter();
    }

    int[] eyeCenter() {
        return popup.eyeCenter();
    }

    int value() {
        return popup.value();
    }
}
```

No `TvEditorProbe.colorHexFocus`, troque `c.hex().focus()` por `c.focusHex()`. `TvEditorTab:194` e `TvPixelEditor:273,319,474` continuam iguais (`picking`, `picked`, o construtor e o `instanceof`).

- [ ] **Step 6: Provar que a Tela de Sinal ficou igual**

```bash
./gradlew build runGameTestServer
./scripts/e2e.sh --only tela --capture tela-editor --out run/e2e-depois
python -I .claude/skills/wa-modular/scripts/comparar.py run/e2e-antes run/e2e-depois
```
Expected: `result.txt` com `OK`; `comparar.py` sem diferença dentro do painel em `tela-editor-cor` (diferença só no mundo atrás é normal). Se houver diferença no seletor, a regra "`framed == false` igual ao de hoje" foi quebrada: conserte antes de seguir.

- [ ] **Step 7: Verificação e commit**

Verificação das Restrições globais. Commit: `Tablet do zero 3/11: o seletor de cor vira o ColorPopup comum (client/color), a Tela de Sinal sobre ele sem mudar o visual`.

---

### Task 4: Servidor: ícone do grupo, uma rede num grupo, a próxima cor

**Files:**
- Modify: `network/WaGroup.java`
- Modify: `network/NetworkSavedData.java:40-43,78-91` e acrescentar `moveToGroup` perto de `updateGroup` (`:240`)
- Modify: `menu/TabletMenu.java:108-138` (enum `Action`)
- Modify: `menu/TabletMenuActions.java:145-150,276-325`
- Modify: `lang/en_us.json`, `lang/pt_br.json` (`gui.wirelessautomate.tablet.notice.refused.icon`)
- Create: `gametest/NetworkGroupGameTests.java`

**Interfaces:**
- Produces: `WaGroup(UUID id, String name, UUID owner, List<UUID> networks, boolean paused, @Nullable ResourceLocation icon)` com o construtor antigo de 5 argumentos (ícone nulo) e `withIcon(@Nullable ResourceLocation)`; `NetworkSavedData.moveToGroup(UUID network, @Nullable UUID group): boolean`; `NetworkSavedData.nextColor(UUID owner): int`; `TabletMenu.Action.GROUP_ICON` (alvo: grupo; texto: id do item, vazio tira o ícone); `GROUP_ADD_NETWORK` move a rede (tira dos outros grupos).

**Custo:** zero por tick. `moveToGroup` percorre os grupos (dezenas) só na ação; o ícone é uma string a mais por grupo no NBT.

- [ ] **Step 1: GameTests que falham**

Crie `gametest/NetworkGroupGameTests.java`:

```java
package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.item.ModItems;
import io.github.matheusanbs.wirelessautomate.menu.TabletMenu;
import io.github.matheusanbs.wirelessautomate.menu.TabletMenu.Action;
import io.github.matheusanbs.wirelessautomate.network.NetworkSavedData;
import io.github.matheusanbs.wirelessautomate.network.WaGroup;
import io.github.matheusanbs.wirelessautomate.network.WaNetwork;
import io.github.matheusanbs.wirelessautomate.packet.TabletActionPayload;
import io.github.matheusanbs.wirelessautomate.packet.TabletPayloads;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.jetbrains.annotations.Nullable;

/**
 * Os grupos de redes do Tablet novo: o ícone (NBT de ida e volta, grupo antigo sem a chave, a ação {@code GROUP_ICON}),
 * "uma rede, um grupo" ({@code GROUP_ADD_NETWORK} move) e a cor de uma rede nova (a próxima da paleta). Cada teste
 * cria as próprias redes e grupos e confere pertinência.
 */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class NetworkGroupGameTests {
    private static final ResourceLocation DIAMOND = ResourceLocation.withDefaultNamespace("diamond");

    private static void eq(GameTestHelper helper, @Nullable Object actual, @Nullable Object expected, String what) {
        helper.assertTrue(Objects.equals(actual, expected), what + ": esperado " + expected + ", veio " + actual);
    }

    private static NetworkSavedData data(GameTestHelper helper) {
        return NetworkSavedData.get(helper.getLevel().getServer());
    }

    private static ServerPlayer tabletPlayer(GameTestHelper helper, int containerId) {
        ServerPlayer player = TestPlayers.inLevel(helper);
        player.getInventory().setItem(0, new ItemStack(ModItems.NETWORK_TABLET.get()));
        player.containerMenu = new TabletMenu(containerId, player.getInventory(), player);
        return player;
    }

    private static boolean act(ServerPlayer player, int containerId, Action action, @Nullable UUID target,
            @Nullable UUID other, String text) {
        return TabletPayloads.handleAction(player, new TabletActionPayload(containerId, action,
                Optional.ofNullable(target), Optional.ofNullable(other), text, 0));
    }

    @GameTest(template = "empty")
    public static void groupIconRoundTripsAndOldGroupsLoadWithoutIt(GameTestHelper helper) {
        WaGroup group = new WaGroup(UUID.randomUUID(), "teste-icone", UUID.randomUUID(), List.of(UUID.randomUUID()),
                true, DIAMOND);
        CompoundTag tag = group.save();
        eq(helper, tag.getString("icon"), "minecraft:diamond", "chave icon");
        eq(helper, WaGroup.load(tag), group, "ida e volta");

        CompoundTag old = group.withIcon(null).save();
        helper.assertFalse(old.contains("icon"), "grupo sem ícone gravou a chave");
        WaGroup loaded = WaGroup.load(old);
        helper.assertTrue(loaded.icon() == null && loaded.paused() && loaded.networks().size() == 1,
                "grupo antigo: " + loaded);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void groupIconActionChecksTheItem(GameTestHelper helper) {
        ServerPlayer player = tabletPlayer(helper, 81);
        try {
            NetworkSavedData data = data(helper);
            WaGroup group = data.createGroup(player.getUUID(), "teste-acao-icone");
            helper.assertTrue(act(player, 81, Action.GROUP_ICON, group.id(), null, "minecraft:diamond"), "pôr o ícone");
            eq(helper, data.group(group.id()).icon(), DIAMOND, "ícone");
            helper.assertFalse(act(player, 81, Action.GROUP_ICON, group.id(), null, "minecraft:nao_existe"),
                    "item que não existe");
            helper.assertFalse(act(player, 81, Action.GROUP_ICON, group.id(), null, "minecraft:air"), "ar");
            helper.assertFalse(act(player, 81, Action.GROUP_ICON, group.id(), null, "Não é id"), "texto torto");
            eq(helper, data.group(group.id()).icon(), DIAMOND, "ícone depois das recusas");
            helper.assertTrue(act(player, 81, Action.GROUP_ICON, group.id(), null, ""), "tirar o ícone");
            helper.assertTrue(data.group(group.id()).icon() == null, "ícone ficou");

            WaGroup foreign = data.createGroup(UUID.randomUUID(), "teste-acao-icone-alheio");
            helper.assertFalse(act(player, 81, Action.GROUP_ICON, foreign.id(), null, "minecraft:diamond"),
                    "mudou o ícone de grupo alheio");
            data.removeGroup(foreign.id());
            data.removeGroup(group.id());
            helper.succeed();
        } finally {
            player.containerMenu = player.inventoryMenu;
        }
    }

    @GameTest(template = "empty")
    public static void addingANetworkToAGroupMovesIt(GameTestHelper helper) {
        ServerPlayer player = tabletPlayer(helper, 82);
        try {
            NetworkSavedData data = data(helper);
            UUID me = player.getUUID();
            WaNetwork network = data.create(me, "teste-mover-rede");
            WaGroup first = data.createGroup(me, "teste-mover-1");
            WaGroup second = data.createGroup(me, "teste-mover-2");
            data.updateGroup(data.group(second.id()).withPaused(true));

            helper.assertTrue(act(player, 82, Action.GROUP_ADD_NETWORK, first.id(), network.id(), ""), "pôr no 1");
            helper.assertTrue(data.group(first.id()).networks().contains(network.id()), "não entrou no 1");
            helper.assertFalse(data.isPaused(network.id()), "pausada no grupo ativo");

            helper.assertTrue(act(player, 82, Action.GROUP_ADD_NETWORK, second.id(), network.id(), ""), "mover p/ 2");
            helper.assertFalse(data.group(first.id()).networks().contains(network.id()), "ficou no 1");
            helper.assertTrue(data.group(second.id()).networks().contains(network.id()), "não entrou no 2");
            helper.assertTrue(data.isPaused(network.id()), "o grupo 2 está pausado");

            helper.assertTrue(act(player, 82, Action.GROUP_REMOVE_NETWORK, second.id(), network.id(), ""), "sem grupo");
            helper.assertFalse(data.isPaused(network.id()), "sem grupo e pausada");

            // mundo antigo: rede em dois grupos fica como está até alguém mexer
            data.updateGroup(data.group(first.id()).withNetwork(network.id()));
            data.updateGroup(data.group(second.id()).withNetwork(network.id()));
            NetworkSavedData reloaded = NetworkSavedData.load(data.save(new CompoundTag(),
                    helper.getLevel().registryAccess()), helper.getLevel().registryAccess());
            helper.assertTrue(reloaded.group(first.id()).networks().contains(network.id())
                    && reloaded.group(second.id()).networks().contains(network.id()), "o load normalizou");

            data.remove(network.id());
            data.removeGroup(first.id());
            data.removeGroup(second.id());
            helper.succeed();
        } finally {
            player.containerMenu = player.inventoryMenu;
        }
    }

    @GameTest(template = "empty")
    public static void newNetworksTakeThePaletteInOrder(GameTestHelper helper) {
        NetworkSavedData data = data(helper);
        UUID owner = UUID.randomUUID();
        int expected = data.nextColor(owner);
        WaNetwork a = data.create(owner, "teste-cor-1");
        eq(helper, a.color(), expected, "primeira cor");
        int second = data.nextColor(owner);
        helper.assertTrue(second != expected, "a próxima repetiu a cor");
        WaNetwork b = data.create(owner, "teste-cor-2");
        eq(helper, b.color(), second, "segunda cor");
        data.remove(a.id());
        data.remove(b.id());
        helper.succeed();
    }
}
```

Confira os nomes reais antes de usar: `ModItems.NETWORK_TABLET`, `TestPlayers.inLevel`, `data.remove(UUID)` (é o que o `NETWORK_REMOVE` chama), `NetworkSavedData.save(CompoundTag, HolderLookup.Provider)` e `load(...)`.

- [ ] **Step 2: Rodar e ver falhar**

Run: `./gradlew runGameTestServer`
Expected: falha de compilação (`WaGroup` de 6 argumentos, `withIcon`, `nextColor`, `GROUP_ICON`).

- [ ] **Step 3: `WaGroup` com ícone**

```java
/**
 * ...(javadoc de hoje)...
 * @param icon     id do item que representa o grupo no Tablet, ou {@code null} (o Tablet usa a primeira máquina)
 */
public record WaGroup(UUID id, String name, UUID owner, List<UUID> networks, boolean paused,
        @Nullable ResourceLocation icon) {
    public WaGroup {
        networks = List.copyOf(networks);
    }

    /** Um grupo sem ícone. */
    public WaGroup(UUID id, String name, UUID owner, List<UUID> networks, boolean paused) {
        this(id, name, owner, networks, paused, null);
    }

    public WaGroup withIcon(@Nullable ResourceLocation icon) {
        return new WaGroup(id, name, owner, networks, paused, icon);
    }
```

Os `with*` existentes passam `icon` adiante. No `save`, depois do `paused`: `if (icon != null) { tag.putString("icon", icon.toString()); }`. No `load`: `ResourceLocation icon = tag.contains("icon", Tag.TAG_STRING) ? ResourceLocation.tryParse(tag.getString("icon")) : null;` e o construtor de 6 argumentos.

- [ ] **Step 4: `NetworkSavedData`**

Troque o javadoc da `PALETTE` para "Cores das redes novas, em ordem: a n-ésima rede de um dono recebe a n-ésima (o jogador muda depois para qualquer RGB)." e apague `colorFor` (só o `create` a usava). Acrescente:

```java
    /** A cor da próxima rede do dono: a paleta em ordem, pelo número de redes que ele já tem. */
    public int nextColor(UUID owner) {
        return PALETTE[ownedNetworks(owner) % PALETTE.length];
    }
```

e no `create`: `WaNetwork network = new WaNetwork(id, name, nextColor(owner), owner);`. Depois do `updateGroup`:

```java
    /**
     * Põe a rede no grupo {@code group} e a tira de todos os outros (no Tablet, o grupo é o lugar da rede);
     * {@code null} a deixa sem grupo. Devolve se algum grupo mudou. O mundo antigo, com a rede em vários grupos, só se
     * arruma aqui, quando alguém mexe.
     */
    public boolean moveToGroup(UUID network, @Nullable UUID group) {
        if (!networks.containsKey(network) || group != null && !groups.containsKey(group)) {
            return false;
        }
        boolean changed = false;
        for (Map.Entry<UUID, WaGroup> entry : groups.entrySet()) {
            WaGroup old = entry.getValue();
            WaGroup next = entry.getKey().equals(group) ? old.withNetwork(network) : old.withoutNetwork(network);
            if (next != old) {
                entry.setValue(next);
                changed = true;
            }
        }
        if (changed) {
            refreshPaused();
            setDirty();
        }
        return changed;
    }
```

(`withNetwork`/`withoutNetwork` devolvem `this` quando nada muda, por isso a comparação por identidade.) O `palette()` fica até a Tarefa 8 (a aba Redes antiga o usa).

- [ ] **Step 5: A ação**

No enum `Action` do `TabletMenu`, troque o javadoc do `GROUP_ADD_NETWORK` para "Alvo: grupo; outro: rede. Move a rede para o grupo, tirando-a dos outros." e acrescente **no fim**:

```java
        /** Alvo: grupo; texto: id do item do ícone (vazio tira). Só quem gerencia o grupo. */
        GROUP_ICON
```

No `TabletMenuActions.apply`, ponha `GROUP_ICON` na lista do `applyGroup`. No `applyGroup`:

```java
            case GROUP_ADD_NETWORK, GROUP_REMOVE_NETWORK -> {
                WaGroup group = managedGroup(data, target);
                // Só redes que o jogador gerencia: pausar o grupo não pode parar a rede de outro.
                WaNetwork network = managedNetwork(data, other);
                if (group == null || network == null) {
                    yield refuse("permission");
                }
                yield action == TabletMenu.Action.GROUP_ADD_NETWORK ? data.moveToGroup(network.id(), group.id())
                        : data.updateGroup(group.withoutNetwork(network.id()));
            }
            case GROUP_ICON -> {
                WaGroup group = managedGroup(data, target);
                if (group == null) {
                    yield refuse("permission");
                }
                ResourceLocation icon = text.isEmpty() ? null : ResourceLocation.tryParse(text);
                if (!text.isEmpty() && (icon == null || BuiltInRegistries.ITEM.getOptional(icon)
                        .filter(item -> item != Items.AIR).isEmpty())) {
                    yield refuse("icon");
                }
                yield data.updateGroup(group.withIcon(icon));
            }
```

Lang: `"gui.wirelessautomate.tablet.notice.refused.icon"`: pt `"Esse item não serve de ícone"`, en `"That item can't be an icon"`.

- [ ] **Step 6: Rodar e ver passar**

Run: `./gradlew runGameTestServer`
Expected: PASS, inclusive os quatro novos e o `TabletGameTests` (o "agrupar" de `:1120` continua verdadeiro: a rede entra num grupo só).

- [ ] **Step 7: Verificação e commit**

Verificação das Restrições globais. Commit: `Tablet do zero 4/11: ícone do grupo (NBT icon e GROUP_ICON), GROUP_ADD_NETWORK move a rede, a próxima cor da paleta`.

---

### Task 5: Servidor: o cabeçalho com o ícone, as máquinas e "Nesta rede"; protocolo 47

**Files:**
- Create: `menu/NetworkTally.java` (puro), `menu/NetworkTallies.java`
- Modify: `menu/TabletSnapshot.java:262-345` (`NetworkView`, `GroupView`, `TypeCount` novo) e `:490-600` (codec do cabeçalho)
- Modify: `menu/TabletSnapshotBuilder.java:54-56,121-157,342-345`
- Modify: `packet/TabletActionPayload.java:20` (`MAX_TEXT = 256`)
- Modify: `packet/ModPayloads.java:85-96` (`VERSION = "47"` e a linha do javadoc)
- Test: Create `src/test/.../menu/NetworkTallyTest.java`; Modify `gametest/NetworkGroupGameTests.java`

**Interfaces:**
- Consumes: `WaGroup.icon()` (Tarefa 4).
- Produces:
  ```java
  public record TypeCount(ResourceType type, int givers, int receivers) {}            // em TabletSnapshot
  public record NetworkView(..., AccessView access, int machines, List<TypeCount> types) // dois campos no fim
  public record GroupView(UUID id, String name, String owner, boolean manageable, boolean paused, List<UUID> networks,
          Optional<ResourceLocation> icon, Optional<ResourceLocation> machine)
  ```
  `NetworkView` mantém os construtores de hoje (`machines = 0`, `types = List.of()`); `GroupView` ganha um de 6 argumentos (ícone e máquina vazios) para quem já o usa.

**Custo:** a contagem percorre as máquinas guardadas do índice (a presa e até 512 remotas por nó) **só quando** `NodeIndex.dataVersion()` ou `NetworkSavedData.version()` mudam (cache no `NetworkTallies`, por montador); parada, custa duas comparações de `int` por montagem. No pacote: por rede, um varint de máquinas e um par de varints por tipo presente (≤ 5); por grupo, até dois ids de item. O cabeçalho continua saindo só quando muda.

- [ ] **Step 1: JUnit do `NetworkTally` (falha)**

```java
package io.github.matheusanbs.wirelessautomate.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NetworkTallyTest {
    private static final UUID A = new UUID(0, 1);
    private static final UUID B = new UUID(0, 2);

    @Test
    void countsMachinesAndRolesPerTypeAndNetwork() {
        Map<ResourceType, List<UUID>> byType = new EnumMap<>(ResourceType.class);
        byType.put(ResourceType.ITEM, List.of(A));
        byType.put(ResourceType.FLUID, List.of(B));
        List<UUID> order = List.of(A, B);
        Map<UUID, NetworkTally.Counts> totals = new HashMap<>();
        totals.put(A, new NetworkTally.Counts());
        totals.put(B, new NetworkTally.Counts());

        int extractItem = NetworkTally.bit(ResourceType.ITEM, NetworkTally.EXTRACT);
        int insertFluid = NetworkTally.bit(ResourceType.FLUID, NetworkTally.INSERT);
        int storageItem = NetworkTally.bit(ResourceType.ITEM, NetworkTally.STORAGE);
        NetworkTally.machine(extractItem | insertFluid, 0b11, order, byType::get, totals);
        NetworkTally.machine(storageItem, 0b01, order, byType::get, totals);
        NetworkTally.machine(0, 0b10, order, byType::get, totals);

        NetworkTally.Counts a = totals.get(A);
        assertEquals(2, a.machines());
        assertEquals(2, a.givers(ResourceType.ITEM));
        assertEquals(1, a.receivers(ResourceType.ITEM));
        assertEquals(0, a.receivers(ResourceType.FLUID));
        NetworkTally.Counts b = totals.get(B);
        assertEquals(2, b.machines());
        assertEquals(1, b.receivers(ResourceType.FLUID));
        assertEquals(0, b.givers(ResourceType.ITEM));
    }

    @Test
    void networksOutsideTheMapAreIgnored() {
        Map<UUID, NetworkTally.Counts> totals = new HashMap<>();
        NetworkTally.machine(NetworkTally.bit(ResourceType.ITEM, NetworkTally.EXTRACT), 1L, List.of(A),
                type -> List.of(A), totals);
        assertEquals(0, totals.size());
    }
}
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `./gradlew test --tests '*NetworkTallyTest'`
Expected: FAIL (classe não existe).

- [ ] **Step 3: `NetworkTally` (puro)**

```java
package io.github.matheusanbs.wirelessautomate.menu;

import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * A conta de "Nesta rede" do Tablet: por rede, quantas máquinas há e, por tipo, quantas dão (Extrair ou Armazém) e
 * quantas recebem (Inserir ou Armazém). Uma máquina conta numa rede quando a máscara dela ({@code NetworkOrder}) tem o
 * bit da rede; dá ou recebe um tipo quando tem o papel dele e a rede está entre as do tipo no nó. A máscara é a união
 * dos tipos, então uma máquina com papéis cruzados em duas redes do mesmo tipo pode contar a mais. Lógica pura, com
 * JUnit; os bits dos papéis são os do {@code NodeIndex.role} (um GameTest confere).
 */
public final class NetworkTally {
    public static final int EXTRACT = 0;
    public static final int INSERT = 1;
    public static final int STORAGE = 2;
    private static final int ROLES_PER_TYPE = 3;
    private static final ResourceType[] TYPES = ResourceType.values();

    private NetworkTally() {
    }

    /** O bit do papel {@code role} do tipo, igual ao {@code NodeIndex.role} (o GameTest do cabeçalho confere). */
    public static int bit(ResourceType type, int role) {
        return 1 << (type.ordinal() * ROLES_PER_TYPE + role);
    }

    /** As somas de uma rede. */
    static final class Counts {
        private int machines;
        private final int[] givers = new int[TYPES.length];
        private final int[] receivers = new int[TYPES.length];

        int machines() {
            return machines;
        }

        int givers(ResourceType type) {
            return givers[type.ordinal()];
        }

        int receivers(ResourceType type) {
            return receivers[type.ordinal()];
        }
    }

    /**
     * Soma uma máquina de papéis {@code roles} e máscara {@code nets} sobre {@code order} (as redes distintas do nó)
     * em {@code totals}; redes fora do mapa ficam de fora. Sem alocar.
     */
    static void machine(int roles, long nets, List<UUID> order, Function<ResourceType, List<UUID>> networksOf,
            Map<UUID, Counts> totals) {
        for (int k = 0, n = Math.min(order.size(), 63); k < n; k++) {
            if ((nets & 1L << k) == 0) {
                continue;
            }
            UUID network = order.get(k);
            Counts counts = totals.get(network);
            if (counts == null) {
                continue;
            }
            counts.machines++;
            for (ResourceType type : TYPES) {
                if (roles == 0) {
                    break;
                }
                List<UUID> ofType = networksOf.apply(type);
                if (ofType == null || !ofType.contains(network)) {
                    continue;
                }
                boolean storage = (roles & bit(type, STORAGE)) != 0;
                if (storage || (roles & bit(type, EXTRACT)) != 0) {
                    counts.givers[type.ordinal()]++;
                }
                if (storage || (roles & bit(type, INSERT)) != 0) {
                    counts.receivers[type.ordinal()]++;
                }
            }
        }
    }
}
```

- [ ] **Step 4: Rodar e ver passar**

Run: `./gradlew test --tests '*NetworkTallyTest'`
Expected: PASS.

- [ ] **Step 5: `NetworkTallies` (o cache) e o montador**

```java
package io.github.matheusanbs.wirelessautomate.menu;

/**
 * As contas de "Nesta rede" e a primeira máquina de cada rede (o ícone de reserva de um grupo sem ícone), refeitas só
 * quando o índice ({@link NodeIndex#dataVersion()}) ou as redes ({@link NetworkSavedData#version()}) mudam. Uma por
 * montador do Tablet; parada, custa duas comparações.
 */
final class NetworkTallies {
    private final Map<UUID, NetworkTally.Counts> counts = new HashMap<>();
    private final Map<UUID, ResourceLocation> firstMachine = new HashMap<>();
    private int indexVersion = -1;
    private int dataVersion = -1;

    void refresh(NodeIndex index, NetworkSavedData data) {
        if (index.dataVersion() == indexVersion && data.version() == dataVersion) {
            return;
        }
        indexVersion = index.dataVersion();
        dataVersion = data.version();
        counts.clear();
        firstMachine.clear();
        for (WaNetwork network : data.networks()) {
            counts.put(network.id(), new NetworkTally.Counts());
        }
        for (NodeIndex.Entry entry : index.entries()) {
            List<UUID> order = entry.networkOrder();
            if (order.isEmpty()) {
                continue;
            }
            for (UUID id : order) {
                firstMachine.putIfAbsent(id, entry.machine());
            }
            if (!entry.machineRolesRead()) {
                // índice antigo, até o próximo aviso do roteador: o nó inteiro como uma máquina com os papéis dele
                NetworkTally.machine(entry.roles(), -1L >>> 1, order, entry::networks, counts);
                continue;
            }
            NetworkTally.machine(entry.attachedRoles(), entry.attachedNets(), order, entry::networks, counts);
            IndexRemotes remotes = entry.remotes();
            for (int i = 0, n = remotes.size(); i < n; i++) {
                NetworkTally.machine(remotes.machineRoles(i), remotes.machineNets(i), order, entry::networks, counts);
            }
        }
    }

    @Nullable NetworkTally.Counts counts(UUID network) {
        return counts.get(network);
    }

    Optional<ResourceLocation> firstMachine(UUID network) {
        return Optional.ofNullable(firstMachine.get(network));
    }
}
```

Confira: `entry.machine()` devolve o bloco da presa; se o nó não tem presa (só remotas), use `entry.attachedBlock()` ou o bloco da primeira remota, o que o `Entry` expõe (`NodeIndex.java:224-245`). A presa só conta se `entry.showsAttached()` ou se `attachedNets != 0` (máscara zero não soma nada no `machine`).

No `TabletSnapshotBuilder`: campo `private final NetworkTallies tallies = new NetworkTallies();`; no `buildSnapshot`, depois de ler `index` e `data`: `tallies.refresh(index, data);`. Na montagem das `NetworkView`:

```java
        totals.forEach((id, t) -> {
            WaNetwork network = data.network(id);
            NetworkTally.Counts c = tallies.counts(id);
            networks.add(new NetworkView(id, network.name(), network.color(), ownerName(server, network.owner(), names),
                    network.owner().equals(me), network.canManage(viewer), network.isPublic(), data.isPaused(id),
                    t.nodes, t.unloaded, network.canManage(viewer) ? TabletAccess.view(server, network)
                            : TabletSnapshot.AccessView.EMPTY, c == null ? 0 : c.machines(), typeCounts(c)));
        });
```

com

```java
    /** Os tipos carregados em que a rede tem quem dá ou quem recebe, na ordem do {@link LoadedTypes#LIST}. */
    private static List<TabletSnapshot.TypeCount> typeCounts(@Nullable NetworkTally.Counts c) {
        if (c == null) {
            return List.of();
        }
        List<TabletSnapshot.TypeCount> out = new ArrayList<>(2);
        for (ResourceType type : LoadedTypes.LIST) {
            if (c.givers(type) > 0 || c.receivers(type) > 0) {
                out.add(new TabletSnapshot.TypeCount(type, c.givers(type), c.receivers(type)));
            }
        }
        return List.copyOf(out);
    }
```

e os grupos:

```java
                groups.add(new GroupView(group.id(), group.name(), ownerName(server, group.owner(), names),
                        group.canManage(viewer), group.paused(), group.networks(), Optional.ofNullable(group.icon()),
                        group.networks().stream().map(tallies::firstMachine).flatMap(Optional::stream).findFirst()));
```

- [ ] **Step 6: O formato no `TabletSnapshot`**

Acrescente o record `TypeCount` (javadoc: "Quantas máquinas de uma rede dão e recebem um tipo (Armazém conta nos dois)."), os campos novos nos dois records (com os construtores antigos delegando) e, no `encodeHeader`, depois de `n.access.write(buf)`:

```java
            buf.writeVarInt(n.machines);
            buf.writeVarInt(n.types.size());
            for (TypeCount count : n.types) {
                buf.writeEnum(count.type());
                buf.writeVarInt(count.givers());
                buf.writeVarInt(count.receivers());
            }
```

e, depois da lista de redes do grupo:

```java
            buf.writeOptional(g.icon, (b, id) -> b.writeResourceLocation(id));
            buf.writeOptional(g.machine, (b, id) -> b.writeResourceLocation(id));
```

O `decodeHeader` lê na mesma ordem (`buf.readEnum(ResourceType.class)`, teto de 8 tipos na leitura: `Math.min(count, ResourceType.values().length)` lido e o resto descartado não é preciso, porque o servidor nunca manda mais que os tipos). `TabletActionPayload.MAX_TEXT = 256` (javadoc: "Teto do texto: nomes têm 32 (`WaCommand.MAX_NAME_LENGTH`, conferido no servidor); o id de um item de ícone pode passar de 64."). `ModPayloads.VERSION = "47"` com a linha da seção "Decisão sobre o protocolo" no começo do javadoc.

- [ ] **Step 7: GameTest do cabeçalho (falha antes do passo 5/6, passa depois)**

No `NetworkGroupGameTests`, acrescente:

```java
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void headerCarriesIconMachinesAndTypes(GameTestHelper helper) {
        ServerPlayer player = tabletPlayer(helper, 83);
        NetworkSavedData data = data(helper);
        UUID me = player.getUUID();
        WaNetwork network = data.create(me, "teste-cabecalho");
        WaGroup group = data.createGroup(me, "teste-cabecalho-grupo");
        data.moveToGroup(network.id(), group.id());
        BlockPos chest = new BlockPos(0, 1, 0);
        helper.setBlock(chest, Blocks.CHEST);
        helper.setBlock(chest.above(), ModBlocks.ROUTER.get().defaultBlockState().setValue(RouterBlock.FACING,
                Direction.UP));
        RouterBlockEntity router = helper.getBlockEntity(chest.above());
        router.setNetworkId(network.id());
        router.setMode(ResourceType.ITEM, Direction.UP, PortMode.EXTRACT);
        NodeIndex.placedBy(router, me);
        TabletMenu menu = (TabletMenu) player.containerMenu;
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(NetworkManager.get().contains(router), "sem onLoad"))
                .thenExecute(() -> {
                    helper.assertTrue(TabletPayloads.handleAction(player, new TabletActionPayload(83, Action.GROUP_ICON,
                            Optional.of(group.id()), Optional.empty(), "minecraft:diamond", 0)), "ícone");
                    menu.setQuery(menu.query());
                    menu.pollSnapshot();
                    TabletSnapshot s = menu.snapshot();
                    TabletSnapshot.NetworkView view = s.network(network.id()).orElseThrow();
                    eq(helper, view.machines(), 1, "máquinas");
                    helper.assertTrue(view.types().stream().anyMatch(t -> t.type() == ResourceType.ITEM
                            && t.givers() == 1 && t.receivers() == 0), "tipos " + view.types());
                    TabletSnapshot.GroupView g = s.groups().stream().filter(x -> x.id().equals(group.id()))
                            .findFirst().orElseThrow();
                    eq(helper, g.icon(), Optional.of(DIAMOND), "ícone no cabeçalho");
                    eq(helper, g.machine(), Optional.of(BuiltInRegistries.BLOCK.getKey(Blocks.CHEST)), "máquina");
                    for (ResourceType type : ResourceType.values()) {
                        for (int role = 0; role < 3; role++) {
                            eq(helper, NetworkTally.bit(type, role), NodeIndex.role(type, role), "bit do papel");
                        }
                    }
                    data.remove(network.id());
                    data.removeGroup(group.id());
                    player.containerMenu = player.inventoryMenu;
                })
                .thenSucceed();
    }
```

O `NetworkTally` é público só pelo `bit` e pelas constantes (o `Counts` e o `machine` ficam package-private); a classe continua pura. Imports a acrescentar no teste: `BlockPos`, `Direction`, `Blocks`, `BuiltInRegistries`, `ModBlocks`, `RouterBlock`, `RouterBlockEntity`, `PortMode`, `ResourceType`, `NetworkManager`, `NodeIndex`, `NetworkTally`, `TabletSnapshot`. Confira os nomes reais: `RouterBlockEntity.setMode`, `setNetworkId`, `NodeIndex.placedBy`, `TabletMenu.query()`/`setQuery`/`pollSnapshot` (usados no `TabletGameTests.fresh`).

- [ ] **Step 8: Verificação e commit**

Verificação das Restrições globais (todas as runs: o formato do cabeçalho é lido por todas). Commit: `Tablet do zero 5/11: o cabeçalho leva ícone e máquina do grupo, máquinas e dão/recebem por rede; protocolo 47`.

---

### Task 6: O kit de componentes do Tablet

**Files:**
- Create: `client/tablet/kit/SegmentLayout.java` (puro), `ChipLayout.java` (puro), `Segments.java`, `TabletModal.java`, `ModalLayer.java`, `DropdownModal.java`, `PanelHeader.java`, `TitleEditor.java`
- Test: Create `src/test/.../client/tablet/kit/SegmentLayoutTest.java`, `ChipLayoutTest.java`

**Interfaces:**
- Consumes: `PanelPaint` (Tarefa 1).
- Produces (todas `public`, porque a aba fica em outro pacote):
  ```java
  public final class SegmentLayout { public static int[] widths(int[] textWidths, boolean icons);
      public static int[] lefts(int x, int[] widths); public static int at(int x, int[] widths, double mx); }
  public final class ChipLayout { public record Placed(int[] xs, int shown, int moreX, int linkX) {}
      public static Placed place(int[] textWidths, int x, int right, int linkW, java.util.function.IntUnaryOperator moreTextW); }
  public final class Segments { public Segments(Component... labels); public void render(GuiGraphics g, Font font, int x, int y, int chosen, int mx, int my);
      public int clicked(int x, int y, double mx, double my); public int width(); public int[] center(int x, int y, int index); }
  public interface TabletModal { void render(GuiGraphics g, Font font, int mx, int my); boolean contains(double mx, double my);
      void mouseClicked(double mx, double my, int button); default boolean mouseDragged(double mx, double my) {return false;}
      default boolean mouseReleased(double mx, double my) {return false;} default boolean mouseScrolled(double mx, double my, double delta) {return true;}
      default boolean keyPressed(int key, int scan, int mods) {return false;} default boolean charTyped(char c, int mods) {return false;}
      default @Nullable Component tooltip(double mx, double my) {return null;} default void closed() {} }
  public final class ModalLayer { public void bounds(int x, int y, int w, int h); public void open(TabletModal m); public void close();
      public boolean isOpen(); public @Nullable TabletModal current(); public void render(GuiGraphics g, Font font, int mx, int my);
      public boolean mouseClicked(double mx, double my, int button); public boolean mouseDragged(double mx, double my);
      public boolean mouseReleased(double mx, double my); public boolean mouseScrolled(double mx, double my, double d);
      public boolean keyPressed(int key, int scan, int mods); public boolean charTyped(char c, int mods); }
  public final class DropdownModal implements TabletModal { public record Row(@Nullable UUID id, Component label,
          @Nullable ItemStack icon, int color, boolean muted) {}
      public DropdownModal(int x, int y, int w, List<Row> rows, @Nullable UUID chosen, Consumer<@Nullable UUID> pick, Anchor anchor);
      public int[] rowCenter(@Nullable UUID id); }
  public final class PanelHeader { public void set(Font font, Component title, Component subtitle, @Nullable Component state, int stateColor, boolean editable, int maxTitleW);
      public void render(GuiGraphics g, Font font, int left, int top, int x0, int x1); public boolean overTitle(double mx, double my, int left, int top, int x0); }
  public final class TitleEditor { public EditBox createBox(Font font); public void start(String text, int x, int y, int w);
      public void finish(boolean commit); public boolean editing(); public @Nullable EditBox box(); }
  ```
  `DropdownModal.Anchor` é `public interface Anchor { void draw(GuiGraphics g); }` (o que a janela redesenha por cima do véu: o seletor que a abriu).

**Custo:** zero com a aba fechada; aberta, só desenho. Larguras de texto medidas por quem monta (no snapshot), nunca no `render`.

- [ ] **Step 1: Testes puros (falham)**

```java
package io.github.matheusanbs.wirelessautomate.client.tablet.kit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SegmentLayoutTest {
    @Test
    void partsOverlapByOnePixel() {
        int[] widths = SegmentLayout.widths(new int[] {27, 59}, false);
        assertArrayEquals(new int[] {39, 71}, widths);
        assertArrayEquals(new int[] {216, 254}, SegmentLayout.lefts(216, widths));
    }

    @Test
    void iconsAddSeventeen() {
        assertArrayEquals(new int[] {39 + 17}, SegmentLayout.widths(new int[] {27}, true));
    }

    @Test
    void clickPicksThePart() {
        int[] widths = {39, 71};
        assertEquals(0, SegmentLayout.at(216, widths, 216));
        assertEquals(1, SegmentLayout.at(216, widths, 254));
        assertEquals(1, SegmentLayout.at(216, widths, 324));
        assertEquals(-1, SegmentLayout.at(216, widths, 325));
        assertEquals(-1, SegmentLayout.at(216, widths, 215));
    }
}
```

```java
package io.github.matheusanbs.wirelessautomate.client.tablet.kit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ChipLayoutTest {
    @Test
    void chipsInARowThenTheLink() {
        // "Lia" 15 e "Rafa" 19 de texto: fichas de 31 e 35, 4 px entre elas; o link 4 px depois do passo (xs + 4)
        ChipLayout.Placed p = ChipLayout.place(new int[] {15, 19}, 216, 411, 52, n -> 12);
        assertArrayEquals(new int[] {216, 251}, p.xs());
        assertEquals(2, p.shown());
        assertEquals(-1, p.moreX());
        assertEquals(294, p.linkX());
    }

    @Test
    void whatDoesNotFitBecomesMore() {
        ChipLayout.Placed p = ChipLayout.place(new int[] {60, 60, 60}, 216, 411, 52, n -> 12);
        assertEquals(1, p.shown());
        assertEquals(216 + 76 + 4, p.moreX());
        assertEquals(216 + 76 + 4 + 28 + 4 + 4, p.linkX());
    }
}
```

(A ficha do "+N" tem largura `moreTextW(n) + 16`, como as outras; o link fica sempre, 4 px depois da última ficha mais 4.)

- [ ] **Step 2: Rodar e ver falhar**

Run: `./gradlew test --tests '*client.tablet.kit*'`
Expected: FAIL.

- [ ] **Step 3: As classes puras**

```java
package io.github.matheusanbs.wirelessautomate.client.tablet.kit;

/**
 * A geometria dos segmentos do Tablet (mockup {@code segmentos}): cada parte tem o texto mais 12 px (mais 17 com
 * ícone), e a seguinte começa 1 px antes do fim da anterior, dividindo a borda. Pura, com JUnit.
 */
public final class SegmentLayout {
    private SegmentLayout() {
    }

    public static int[] widths(int[] textWidths, boolean icons) {
        int[] out = new int[textWidths.length];
        for (int i = 0; i < out.length; i++) {
            out[i] = textWidths[i] + 12 + (icons ? 17 : 0);
        }
        return out;
    }

    public static int[] lefts(int x, int[] widths) {
        int[] out = new int[widths.length];
        for (int i = 0; i < widths.length; i++) {
            out[i] = x;
            x += widths[i] - 1;
        }
        return out;
    }

    /** A parte sob {@code mx}, ou −1; na borda dividida vale a da direita. */
    public static int at(int x, int[] widths, double mx) {
        int hit = -1;
        for (int i = 0; i < widths.length; i++) {
            if (mx >= x && mx < x + widths[i]) {
                hit = i;
            }
            x += widths[i] - 1;
        }
        return hit;
    }
}
```

```java
package io.github.matheusanbs.wirelessautomate.client.tablet.kit;

import java.util.function.IntUnaryOperator;

/**
 * As fichas dos convidados numa linha só (mockup {@code rede_detalhe}): cada uma com o texto mais 16 px, 4 px entre
 * elas, e o link "+ convidar" 4 px depois da última. O que não cabe antes de {@code right} vira uma ficha "+N", e o link
 * cabe sempre. Pura, com JUnit.
 */
public final class ChipLayout {
    private static final int PAD = 16;
    private static final int GAP = 4;

    private ChipLayout() {
    }

    /** {@code moreX} é −1 sem a ficha "+N". */
    public record Placed(int[] xs, int shown, int moreX, int linkX) {
    }

    public static Placed place(int[] textWidths, int x, int right, int linkW, IntUnaryOperator moreTextW) {
        int n = textWidths.length;
        int[] xs = new int[n];
        int at = x;
        int shown = 0;
        for (int i = 0; i < n; i++) {
            int w = textWidths[i] + PAD;
            int hidden = n - i - 1;
            int tail = (hidden > 0 ? GAP + moreTextW.applyAsInt(hidden) + PAD : 0) + GAP + GAP + linkW;
            if (at + w + tail > right + 1) {
                break;
            }
            xs[shown++] = at;
            at += w + GAP;
        }
        int moreX = -1;
        if (shown < n) {
            moreX = at;
            at += moreTextW.applyAsInt(n - shown) + PAD + GAP;
        }
        int[] placed = new int[shown];
        System.arraycopy(xs, 0, placed, 0, shown);
        return new Placed(placed, shown, moreX, at + GAP);
    }
}
```

As contas dos dois testes (o mockup: "Lia x" em 216, "Rafa x" em 251, "+ convidar" em `xs + 4` = 294) foram feitas à mão com esse código; se um falhar, o erro está na implementação, não no teste.

- [ ] **Step 4: Os componentes de desenho**

- `Segments`: guarda os rótulos e as larguras (`SegmentLayout.widths`, medidas com a `Font` na primeira chamada de `render` ou num `measure(Font)` chamado pela anfitriã no snapshot); `render` desenha os não escolhidos primeiro e o escolhido por último, com `GuiPaint.outline` + `fill` (escolhido: borda `FG`, miolo `BEVEL_LIGHT`, texto `FG`; outros: borda `FRAME`, miolo `FACE`, texto `MUTED`), h = 18, texto em (x + 6, y + 6); `clicked` devolve `SegmentLayout.at`.
- `TabletModal` e `ModalLayer`: a camada tem o retângulo do corpo (2, 24, w − 4, h − 26, em coordenadas da tela); `render` faz `pushPose`, `translate(0, 0, 300)`, `PanelPaint.scrim(...)`, `translate(0, 0, 100)`, `modal.render(...)`, `popPose`; clique fora da janela fecha (e consome o clique); Esc fecha; `close()` chama `closed()` do que estava aberto.
- `DropdownModal`: janela `PanelPaint.window` em (x, y + 18 + 1), largura `w`, linhas de 18 px: ícone 16 em (x + 2, y + 1) ou quadradinho 7 em (x + 6, y + 6), texto em (x + 21, y + 5) (`MUTED` em `muted`); a escolhida com `PanelPaint.chosenRow`; até 8 linhas e a roda rola; clique numa linha chama `pick` e fecha. Antes da janela, `anchor.draw(g)` redesenha o seletor por cima do véu.
- `PanelHeader`: monta no `set` (no snapshot): título em negrito (`Component.copy().withStyle(ChatFormatting.BOLD)`) cortado por `GuiText.fit` em `maxTitleW`, subtítulo cortado, estado com largura medida; `render(g, font, left, top, X0, X1)` desenha nas medidas do mockup (`cabecalho`): título em (X0 + 21, 30), lápis `PanelPaint.pencil(fim + 4, 31, MUTED)` se editável, subtítulo `MUTED` em (X0 + 21, 40), ponto `PanelPaint.square(X1 − w − 7, 32, cor)` e o estado à direita em X1, linha `PanelPaint.rule(X0, 53, X1 − X0 + 1)`. O ícone ou slot quem desenha é a aba (é diferente para rede e grupo).
- `TitleEditor`: um `EditBox` sem borda, sem sombra, máximo 32 (`WaCommand.MAX_NAME_LENGTH`), criado como widget da tela (a tela o põe entre os widgets dela, como o campo de renomear de hoje) e invisível fora da edição; `start(text, x, y, w)` posiciona sobre o título, dá o texto, `moveCursorToEnd(false)` e `setHighlightPos(0)` (tudo selecionado: digitar troca); `finish(commit)` esconde e chama o `Consumer<String>` dado no construtor só com `commit`. Moldura do campo em edição: `PanelPaint.field(x − 3, y − 3, w + 6, 14, true)`.

Cada classe abaixo de 250 linhas; nenhuma manda payload (quem age recebe um `Consumer`/`Runnable`).

- [ ] **Step 5: Rodar e ver passar**

Run: `./gradlew test --tests '*client.tablet.kit*' && ./gradlew compileJava`
Expected: PASS e compila (o kit ainda não aparece na tela; ele entra nas Tarefas 7 e 8).

- [ ] **Step 6: Verificação e commit**

Verificação das Restrições globais. Commit: `Tablet do zero 6/11: o kit do Tablet (segmentos, fichas, janela com véu, lista aberta, cabeçalho, nome editável)`.

---

### Task 7: A moldura nova com três abas

**Files:**
- Create: `client/tablet/TabletFrame.java` (substitui `TabletHeader`)
- Delete: `client/tablet/TabletHeader.java`
- Modify: `client/tablet/TabletLayout.java:8-24`, `TabletScreen.java:57,96-99,133-148,200-215,318-341`, `TabletTabs.java:55-67`, `TabletList.java` (método `showMachines()`), `TabletButtons.java` (o desenho da aba vai para o `TabletFrame`)
- Modify: `lang/en_us.json`, `lang/pt_br.json`
- Modify: `client/dev/E2eTabletSteps.java`, `E2eFlowsSteps.java`, `E2ePortugueseSteps.java`, `E2eShowcase.java`, `E2eTabletMachinesSteps.java` (a aba "Lista" agora se chama "Máquina")

**Interfaces:**
- Consumes: `PanelPaint` (Tarefa 1).
- Produces: `TabletFrame(TabletLayout layout, int noticeId)` com `onSnapshot(TabletSnapshot)`, `render(GuiGraphics, Font, int mx, int my)`, `renderNotice(GuiGraphics, Font)`, `themeX()`, `themeY()`, `addTabs(TabletScreen.Buttons, Font, Supplier<Tab>, Consumer<Tab>)`; a ordem da barra `TabletFrame.BAR = {Tab.NETWORKS, Tab.LIST, Tab.FLOWS}`; `TabletLayout.BODY_Y = 30`, `MIN_W = 420`, `MIN_H = 240`; `TabletList.showMachines()`.

**Custo:** nenhum novo: a moldura desenha com `fill` e textos medidos no `init`; a aba Máquina pede a visão por máquinas uma vez ao entrar (o `TabletMachinesPayload` que o seletor já manda).

- [ ] **Step 1: Layout**

No `TabletLayout`: `MIN_W = 420` e `MIN_H = 240` (comentário: "420 × 280 é o tamanho do desenho; 240 de altura cabe em 1080p com a escala automática"); `HEAD_Y = 10` (a linha do título e das abas); `SEP_Y = 23` (a linha embaixo da barra); `BODY_Y = 30`; apague `TAB_Y` e `TAB_H` (só a tela os usava). As constantes da aba Redes antiga (`SIDE_W`, `RX`, `SIDE_BOTTOM`, `NEW_Y`, `NEW_BOX_Y`) ficam até a Tarefa 8. Acrescente a geometria do mockup, usada a partir da Tarefa 8:

```java
    /** Tablet novo: a divisória da coluna esquerda e o começo do painel direito (mockup: 150 e X0 = 158). */
    static final int COLUMN_RULE = 150;
    static final int PANEL_X0 = 158;

    /** A borda direita do painel direito, inclusive (mockup: X1 = 411 com 420 de largura). */
    int panelX1() {
        return w - 9;
    }

    /** A linha do rodapé (mockup: H − 27) e o topo dos botões dele (H − 21). */
    int footerRuleY() {
        return h - 27;
    }

    int footerY() {
        return h - 21;
    }
```

- [ ] **Step 2: `TabletFrame`**

```java
/**
 * A moldura do Tablet (mockup {@code moldura}): o relevo do painel, "Tablet" em negrito, as três abas à direita (a
 * ativa em {@code FG} com o sublinhado coral, as outras em {@code MUTED}), o botão do tema logo depois do título, a
 * linha embaixo da barra e o aviso do servidor (4 s) entre o botão do tema e as abas. As abas são {@link FlatButton}s
 * (o e2e as acha pelo texto); as larguras são medidas no {@code init}.
 */
final class TabletFrame {
    /** A ordem da barra, da esquerda para a direita. O Mapa fica fora (sai do Tablet na etapa 4). */
    static final Tab[] BAR = {Tab.NETWORKS, Tab.LIST, Tab.FLOWS};
    ...
}
```

Desenho (`render`): `PanelPaint.relief(g, x, y, w, h, GuiPaint.PANEL, GuiPaint.RELIEF_LIGHT, GuiPaint.RELIEF_DARK)`; `GuiPaint.text(g, font, title, x + 8, y + 10, FG)` com `title = tr("frame.title").copy().withStyle(ChatFormatting.BOLD)`; `ThemeToggle.render(g, themeX(), themeY(), mx, my)` com `themeX() = left + 8 + font.width(title) + 6`, `themeY() = top + 7`; `g.fill(x + 2, y + 23, x + w - 2, y + 24, GuiPaint.INSET)`. As abas: em `addTabs`, `int ax = w - 12` e, para `t` em `BAR` de trás para a frente, `int tw = font.width(label(t)); ax -= tw;` cria `new FlatButton(left + ax - 1, top + 6, tw + 2, 17, label(t), (g, b, hovered) -> paintTab(g, font, b, t == current.get(), hovered), () -> switchTo.accept(t))` e `ax -= 16`. `paintTab`: texto em (b.getX() + 1, top + 10) `FG` na ativa ou com o mouse, `MUTED` nas outras; na ativa, `g.fill(b.getX(), top + 21, b.getX() + tw + 1, top + 23, GuiPaint.ACCENT)`. Rótulos: `tr("tab." + key(t))`, com `key(LIST) = "machine"`, `key(FLOWS) = "flows"`, `key(NETWORKS) = "networks"`.

Aviso (`renderNotice`): como o `TabletHeader.renderNotice` de hoje, mas entre `themeX() + ThemeToggle.SIZE + 6` e o `x` da primeira aba − 6, em y = `top + 4`, altura 16, com `GuiPaint.box(INSET, FG)` (o `ACCENT` sai: decisão 1). Os contadores do topo (nós, máquinas, redes) não entram (spec, "O que sai do Tablet").

- [ ] **Step 3: A tela**

No `TabletScreen`:
- troque `TabletHeader header` por `TabletFrame frame` (e `header.` por `frame.`);
- o laço das abas do `init` (`:139-145`) vira `frame.addTabs(this::add, font, () -> tab, this::switchTab);`
- `renderBg`: troque `GuiPaint.panel(g, x, y, w, h, GuiPaint.ACCENT)` + `header.render` por `frame.render(g, font, mouseX, mouseY)`; a alça de redimensionar passa a `ResizeGrip.renderDotted(..., GuiPaint.FG)`;
- no construtor, a aba lembrada: `if (restored == Tab.MAP.ordinal()) tab = Tab.NETWORKS;` (o Mapa não está na barra);
- `switchTab`: ao entrar em `Tab.LIST`, `list.showMachines()` (depois do `actions.tab`);
- javadoc da classe: "três abas na barra, Redes, Máquina (por ora, a Lista na visão Máquinas) e Fluxos; o Mapa continua na tela só para as capturas e o e2e até a etapa 4".

No `TabletList`:

```java
    /** A aba Máquina da moldura nova: a Lista na visão por máquinas (a da etapa 2 ainda não existe). */
    void showMachines() {
        if (!layout.viewBar().shown()) {
            layout.viewBar().choose(1);
        }
    }
```

Lang (pt / en): `tablet.frame.title` "Tablet" / "Tablet"; `tablet.tab.machine` "Máquina" / "Machine". `tablet.tab.list` e `tablet.tab.map` ficam até a etapa 4 (o e2e do Mapa usa `previewTab`).

- [ ] **Step 4: e2e**

Troque nos passos do e2e as buscas por `Component.translatable("gui.wirelessautomate.tablet.tab.list")` por `"gui.wirelessautomate.tablet.tab.machine"` (`grep -rn "tablet.tab.list" src/main/java/io/github/matheusanbs/wirelessautomate/client/dev`). Os passos que voltam à Lista depois do Mapa continuam por `previewTab(Tab.LIST)`. Onde um passo espera a Lista na visão Roteadores logo depois de clicar na aba (o `tabletSearchSteps`), escolha os Roteadores pelo seletor (`ViewBar.choose(0)` pelo gancho que o passo já usa, ou clique em "Roteadores") antes de conferir.

Run: `./scripts/e2e.sh --only tablet --capture tablet- --variants escuro`
Expected: `OK`; nas capturas, a barra nova e a Lista e os Fluxos sem nada cortado; a aba Redes antiga ainda aparece dentro da moldura nova (sai na Tarefa 8).

- [ ] **Step 5: Verificação e commit**

Verificação das Restrições globais. Commit: `Tablet do zero 7/11: a moldura nova com Redes, Máquina e Fluxos; o Mapa sai da barra`.

---

### Task 8: A aba Redes nova

**Files:**
- Create em `client/tablet/networks/`: `NetworksPanel.java` (público), `NetworkTree.java` (puro), `DefaultNames.java` (puro), `NetworkColumn.java`, `NetworkDetail.java`, `GroupPanel.java`, `GuestChips.java`, `AccessWindow.java`, `GroupPicker.java`, `NetworksText.java`
- Test: `src/test/.../client/tablet/networks/NetworkTreeTest.java`, `DefaultNamesTest.java`
- Delete: `client/tablet/NetworksTab.java`, `NetworkList.java`, `GroupDetail.java`, `NameFields.java`, `AccessSection.java`, `NetworkSections.java`, `SideList.java`; `src/test/.../client/tablet/NetworkSectionsTest.java`
- Modify: `client/tablet/TabletScreen.java`, `TabletBody.java`, `TabletTabs.java`, `TabletKeys.java`, `TabletTips.java`, `TabletActions.java`, `TabletLayout.java` (apaga as constantes da aba antiga), `TabletButtons.java` (apaga `paintNew` e o que ficar sem uso)
- Modify: `network/NetworkSavedData.java` (apaga `palette()`, sem uso)
- Create: `client/dev/E2eNetworksSteps.java`; Delete: `client/dev/E2eNetworkSectionSteps.java`; Modify: `client/dev/E2eTabletSteps.java:78-90,272-321` (o `tabletGroupSteps` sai), `E2eAccessSteps.java`, `E2eShowcase.java:355-395`, `ScreenshotTablet.java:110-121`
- Modify: `lang/en_us.json`, `lang/pt_br.json`

**Interfaces:**
- Consumes: kit (Tarefa 6), `TabletFrame`/`TabletLayout` (Tarefa 7), `TypeCount`, `GroupView.icon()/machine()`, `NetworkView.machines()/types()` (Tarefa 5), ações `GROUP_ADD_NETWORK` (move), `GROUP_REMOVE_NETWORK`, `GROUP_PAUSE`, `NETWORK_*` (Tarefa 4 e as de hoje).
- Produces: `public final class NetworksPanel` com `NetworksPanel(TabletLayout, TabletMenu, Supplier<TabletSnapshot>, TabletActions)`; `addButtons(TabletScreen.Buttons, Font)`; `List<EditBox> createBoxes(Font)`; `onSnapshot(TabletSnapshot, Font)`; `refresh(boolean shown)`; `render`, `renderOverlay`, `mouseClicked`, `mouseScrolled`, `mouseDragged`, `mouseReleased`, `keyPressed(int,int,int, @Nullable EditBox focused)`, `charTyped`, `tooltip`; `ModalLayer modals()`; `AccessPanel access()`; ganchos do e2e (passo 6). `TabletActions` ganha `groupMove(UUID network, @Nullable UUID group, List<UUID> currentGroups)` e `groupIcon(UUID group, @Nullable ResourceLocation icon)`.

**Custo:** com a aba fechada, nada (nenhum payload a mais: o cabeçalho é o de sempre). Aberta, a árvore, os textos, as larguras e os `ItemStack`s dos ícones são refeitos só no snapshot novo (`onSnapshot`) ou na troca de tema; o `render` só desenha as linhas à vista. Nenhuma ação manda payload por quadro.

- [ ] **Step 1: Testes puros (falham)**

```java
package io.github.matheusanbs.wirelessautomate.client.tablet.networks;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NetworkTreeTest {
    private static final UUID BASE = new UUID(1, 1);
    private static final UUID MEK = new UUID(1, 2);
    private static final UUID FUND = new UUID(2, 1);
    private static final UUID EST = new UUID(2, 2);
    private static final UUID FORJA = new UUID(2, 3);
    private static final UUID QUIM = new UUID(2, 4);
    private static final UUID ARC = new UUID(2, 5);

    @Test
    void mockupPositions() {
        List<NetworkTree.Row> rows = NetworkTree.build(
                List.of(new NetworkTree.GroupIn(BASE, List.of(FUND, EST, FORJA)), new NetworkTree.GroupIn(MEK, List.of(QUIM))),
                List.of(FUND, EST, FORJA, QUIM, ARC));
        int[] tops = rows.stream().mapToInt(NetworkTree.Row::top).toArray();
        // gerar.py, lista_redes: Base 28; Fundição, Estoque, Forja com o realce em 48, 62, 76; +4; Mekanism 96;
        // Química 116; +4; Sem grupo 136; Arcano 156
        assertEquals(List.of(28, 48, 62, 76, 96, 116, 136, 156), java.util.Arrays.stream(tops).boxed().toList());
        assertEquals(NetworkTree.Kind.LOOSE, rows.get(6).kind());
        assertEquals(ARC, rows.get(7).id());
        assertEquals(170, NetworkTree.height(rows));
    }

    @Test
    void aNetworkInTwoGroupsShowsInTheFirst() {
        List<NetworkTree.Row> rows = NetworkTree.build(
                List.of(new NetworkTree.GroupIn(BASE, List.of(FUND)), new NetworkTree.GroupIn(MEK, List.of(FUND))),
                List.of(FUND));
        assertEquals(1, rows.stream().filter(r -> FUND.equals(r.id())).count());
        assertEquals(BASE, rows.stream().filter(r -> FUND.equals(r.id())).findFirst().orElseThrow().group());
    }

    @Test
    void noLooseLabelWithoutLooseNetworks() {
        List<NetworkTree.Row> rows = NetworkTree.build(List.of(new NetworkTree.GroupIn(BASE, List.of(FUND))), List.of(FUND));
        assertEquals(2, rows.size());
    }

    @Test
    void unknownNetworksInAGroupAreSkipped() {
        List<NetworkTree.Row> rows = NetworkTree.build(List.of(new NetworkTree.GroupIn(BASE, List.of(FUND, EST))),
                List.of(EST));
        assertEquals(EST, rows.get(1).id());
        assertEquals(2, rows.size());
    }
}
```

```java
package io.github.matheusanbs.wirelessautomate.client.tablet.networks;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class DefaultNamesTest {
    @Test
    void firstFreeNameIgnoringCase() {
        assertEquals("Rede", DefaultNames.next("Rede", List.of()));
        assertEquals("Rede 2", DefaultNames.next("Rede", List.of("rede")));
        assertEquals("Rede 4", DefaultNames.next("Rede", List.of("Rede", "Rede 2", "REDE 3")));
    }
}
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `./gradlew test --tests '*client.tablet.networks*'`
Expected: FAIL.

- [ ] **Step 3: As puras**

```java
package io.github.matheusanbs.wirelessautomate.client.tablet.networks;

/**
 * As linhas da coluna esquerda da aba Redes (mockup {@code lista_redes}): cada grupo, na ordem do servidor, com as redes
 * dele; uma rede em vários grupos (mundo antigo) aparece só no primeiro; por último "Sem grupo", só o rótulo, com as
 * redes avulsas, quando há alguma. {@code top} é o topo do realce da linha, em px a partir do topo do painel. Pura, com
 * JUnit.
 */
final class NetworkTree {
    static final int TOP = 28;
    static final int GROUP_H = 20;
    static final int GROUP_STEP = 22;
    static final int NETWORK_H = 14;
    static final int GROUP_GAP = 4;

    enum Kind { GROUP, NETWORK, LOOSE }

    /** {@code group}: o grupo em que a rede aparece, ou {@code null} em "Sem grupo". */
    record Row(Kind kind, @Nullable UUID id, @Nullable UUID group, int top, int height) {
    }

    record GroupIn(UUID id, List<UUID> networks) {
    }

    private NetworkTree() {
    }

    static List<Row> build(List<GroupIn> groups, List<UUID> networks) {
        Set<UUID> known = new HashSet<>(networks);
        Set<UUID> placed = new HashSet<>();
        List<Row> rows = new ArrayList<>();
        int y = TOP;
        for (GroupIn group : groups) {
            rows.add(new Row(Kind.GROUP, group.id(), group.id(), y, GROUP_H));
            y += GROUP_STEP;
            for (UUID id : group.networks()) {
                if (known.contains(id) && placed.add(id)) {
                    rows.add(new Row(Kind.NETWORK, id, group.id(), y - 2, NETWORK_H));
                    y += NETWORK_H;
                }
            }
            y += GROUP_GAP;
        }
        boolean loose = false;
        for (UUID id : networks) {
            if (placed.contains(id)) {
                continue;
            }
            if (!loose) {
                loose = true;
                rows.add(new Row(Kind.LOOSE, null, null, y, GROUP_H));
                y += GROUP_STEP;
            }
            rows.add(new Row(Kind.NETWORK, id, null, y - 2, NETWORK_H));
            y += NETWORK_H;
        }
        return List.copyOf(rows);
    }

    /** Onde a última linha termina (a rolagem usa isso). */
    static int height(List<Row> rows) {
        if (rows.isEmpty()) {
            return TOP;
        }
        Row last = rows.get(rows.size() - 1);
        return last.top() + last.height();
    }
}
```

```java
package io.github.matheusanbs.wirelessautomate.client.tablet.networks;

/** O nome de uma rede ou grupo criado pelo "+ Rede" ou "+ Grupo": o primeiro livre de "Rede", "Rede 2", ... Pura. */
final class DefaultNames {
    private DefaultNames() {
    }

    static String next(String base, Collection<String> taken) {
        Set<String> lower = new HashSet<>();
        for (String name : taken) {
            lower.add(name.toLowerCase(Locale.ROOT));
        }
        if (!lower.contains(base.toLowerCase(Locale.ROOT))) {
            return base;
        }
        for (int n = 2; ; n++) {
            String name = base + " " + n;
            if (!lower.contains(name.toLowerCase(Locale.ROOT))) {
                return name;
            }
        }
    }
}
```

Run: `./gradlew test --tests '*client.tablet.networks*'` → PASS.

- [ ] **Step 4: As peças da aba (responsabilidades e medidas)**

Todas package-private, menos `NetworksPanel`. Cada uma abaixo de 400 linhas; nenhuma chama `TabletActions` direto, menos a anfitriã.

**`NetworksPanel`** (anfitriã, ~350 linhas): guarda o escolhido (`@Nullable UUID network` ou `@Nullable UUID group`, um dos dois), a `ModalLayer`, o `TitleEditor`, o `AccessPanel` (`new AccessPanel(windowFrame, host)`, o mesmo de hoje, com o `Host` do `AccessSection` antigo copiado para cá), a `NetworkColumn`, o `NetworkDetail` e o `GroupPanel`. No `onSnapshot`: refaz a árvore (`NetworkTree.build` com `GroupIn(g.id(), g.networks())` na ordem de `snapshot.groups()` e os ids de `snapshot.networks()`), os textos e os `ItemStack`s dos ícones (`groupIcon(GroupView)`: o ícone escolhido, senão a máquina de reserva, senão `new ItemStack(ModItems.NETWORK_TABLET.get())`; item por `BuiltInRegistries.ITEM.get(id)`; o bloco da máquina pelo `asItem()` do bloco); o escolhido que sumiu sai; se há uma criação pendente (`awaiting` com os ids conhecidos antes, como o `NetworksTab.created` de hoje), o novo fica escolhido e o nome abre no `TitleEditor`. Sem nada escolhido e com redes, escolhe a primeira. Repassa mouse e teclas: com uma janela aberta, tudo vai para a `ModalLayer`; senão, para a coluna, o detalhe ou o grupo. As ações saem por métodos que chamam `TabletActions`.

**`NetworkColumn`** (~250 linhas): desenha e clica a coluna esquerda pelas medidas de `lista_redes` (ver "Medidas"), com recorte (`g.enableScissor(left + 2, top + 24, left + 150, top + h − 27)`) e rolagem pela roda (passo 14, entre 0 e `NetworkTree.height(rows) − (h − 27 − 28)`); a linha escolhida com `PanelPaint.chosenRow(2, top, 148, height)`; nome do grupo em negrito cortado em 141 − 28 − largura de "pausado"; nome da rede cortado antes da contagem; contagem = `NetworkView.machines()`. Divisória: `g.fill(150, 24, 151, h − 2, INSET)`. Os botões "+ Rede" (primário) e "+ Grupo" são `FlatButton`s da aba (`TabletScreen.Buttons`, dono `Tab.NETWORKS`) em (8, h − 21) e (8 + w1 + 4, h − 21), larguras texto + 16, altura 16, pintados por `PanelPaint.button`. O clique em "+ Rede": `actions.create(Action.NETWORK_CREATE, DefaultNames.next(tr("nets.default_network"), nomes das minhas redes))`; "+ Grupo": igual com `GROUP_CREATE` e os nomes dos meus grupos. O rodapé da coluna: `PanelPaint.rule(2, h − 27, 148)`.

**`NetworkDetail`** (~400 linhas; se passar, a seção "Nesta rede" vai para `TypeRows`): a rede escolhida, pelas medidas de `rede_detalhe`:
- cabeçalho `PanelHeader` (título = nome, subtítulo = `plural("nets.machines", machines)`, estado `tr("nets.state.active")` com `GuiPaint.OK` ou `tr("nets.state.paused")` com `GuiPaint.DISABLED` quando `paused`, editável se `manageable`); o slot da cor: `PanelPaint.slot(X0 − 1, 29, 18, false)` e `g.fill(X0 + 1, 31, X0 + 15, 45, 0xFF000000 | color)`; clicar no slot abre o seletor (Tarefa 9: nesta tarefa o clique não faz nada); clicar no título abre o `TitleEditor` em (X0 + 21, 30, largura até o estado);
- "Grupo" (rótulo `MUTED` em (X0, 65)): o seletor `PanelPaint.reliefFace(vx, 60, 112, 18, hover)` com o ícone do grupo em (vx + 2, 61) (`g.renderItem`), o nome em (vx + 21, 65) cortado em 78 px, `PanelPaint.caret(vx + 102, 68, MUTED)`; sem grupo, "Sem grupo" `MUTED` em (vx + 6, 65). O clique abre o `GroupPicker`;
- "Acesso" (rótulo em (X0, 89)): `Segments` "Todos" / "Convidados" em (vx, 84), escolhido = `isPublic ? 0 : 1`; o clique manda `NETWORK_PUBLIC` (1 em Todos, 0 em Convidados) só se mudou;
- em Convidados: `GuestChips` em y = 107 e a linha em y = 130; em Todos, a linha em y = 110;
- "Nesta rede" `MUTED` em (X0, linha + 7); uma linha por `TypeCount`, passo 20, a partir de linha + 20: o ícone do tipo (`ResourceStyle.drawIcon(g, type, X0 + 4, y + 4)`, o desenho de 9 × 9 centrado no quadro de 16 como no mockup), `ResourceStyle.name(type)` em (X0 + 20, y + 4), `tr("nets.here.line", givers, receivers)` `MUTED` à direita em X1; sem tipos, `tr("nets.here.empty")` `MUTED` em (X0, linha + 20); as linhas que passam do rodapé não são desenhadas;
- rodapé (`PanelPaint.rule(151, h − 27, w − 153)`): `FlatButton` "Pausar"/"Retomar" em (X0, h − 21) (pausa o grupo da rede: `GROUP_PAUSE` com o grupo em que a rede aparece; desativado sem grupo, dica `nets.pause.no_group`; dica `nets.pause.tooltip` com o nome do grupo); `FlatButton` "Usar"/"Em uso" 4 px depois (`NETWORK_USE`; ver ajuste 6); "Apagar rede" `MUTED` à direita em X1, y = h − 16, de dois cliques (`ArmedClick`, como o Remover de hoje): armado, o texto vira `tr("nets.remove.armed")` em `GuiPaint.DANGER`, e o segundo clique manda `NETWORK_REMOVE`;
- rede que o jogador não gerencia: sem lápis, sem os cliques de cor, Grupo e Acesso (desenhados apagados) e sem "Apagar"; em lugar do Acesso, `tr("network.foreign")` `DISABLED`.

**`GuestChips`** (~150 linhas): as fichas da `AccessView` (convidados, depois times), montadas no snapshot (nomes cortados em 60 px, larguras medidas, `ChipLayout.place(…, vx, X1, largura de "+ convidar", n -> font.width("+" + n))`); desenho: `GuiPaint.outline(xs, 107, w, 12, BEVEL_DARK)`, nome em (xs + 4, 109) `FG`, "x" em (xs + w − 9, 109) `FG` (com o mouse em cima, `DANGER`); "+N" `MUTED`; o link `PanelPaint.link(linkX, 109, "+ convidar")`. Clique no "x": `member`/`team` com `add = false` (um clique só: a ficha é pequena e a pessoa pode ser convidada de novo); no "+N" ou no link: abre a `AccessWindow`.

**`AccessWindow`** (~150 linhas, `TabletModal`): janela `PanelPaint.window` em (X0 − 1, 50, X1 − X0 + 2, h − 50 − 30) com o título `tr("nets.access.window", nome)` em negrito em (x + 7, y + 6) e o `AccessPanel` de hoje dentro (um `AccessPanel.Frame` com `x = x + 7`, `labelY = y + 20`, `width = w − 14`, as linhas que a altura der, `bottom = y + h − 6`); ao abrir, `access.refresh(true)` (os campos aparecem); ao fechar, `access.refresh(false)`. O `AccessPanel` continua o mesmo (público e comum com a Tela de Sinal).

**`GroupPicker`** (~100 linhas): monta um `DropdownModal` em (vx, 60, 112) com "Sem grupo" e os grupos que o jogador gerencia (ícone e nome), o atual escolhido; escolher um grupo chama `actions.groupMove(rede, grupo, gruposAtuais)`: com grupo, `GROUP_ADD_NETWORK` (o servidor move); com "Sem grupo", um `GROUP_REMOVE_NETWORK` para cada grupo em que a rede está (`gruposAtuais`, quase sempre um).

**`GroupPanel`** (~250 linhas): o grupo escolhido (`grupo_detalhe`): `PanelHeader` (título = nome, subtítulo `tr("nets.group.sub", plural("nets.networks", n), plural("nets.machines", soma))`, sem estado, editável se `manageable`); o slot do ícone `PanelPaint.slot(X0 − 1, 29, 18, false)` com o item em (X0, 30) (o clique abre a grade na Tarefa 10); "Redes do grupo" `MUTED` em (X0, 61); linhas a partir de 75, passo 14 (quadradinho `PanelPaint.swatch(X0, y + 1, 7, cor)`, nome em (X0 + 11, y + 1), `plural("nets.machines", m)` à direita em X1; clicar escolhe a rede); o link `tr("nets.add_to_group")` em (X0, y + 5) abre um `DropdownModal` com as redes que o jogador gerencia e que não estão no grupo (escolher manda `GROUP_ADD_NETWORK`); rodapé "Pausar grupo"/"Retomar grupo" (`FlatButton`) e "Apagar grupo" de dois cliques (`GROUP_REMOVE`).

**`NetworksText`** (~60 linhas): `tr(key, args)` sobre o prefixo `gui.wirelessautomate.tablet.` e `plural`, como o `TabletText` (que é package-private do outro pacote).

- [ ] **Step 5: A tela e as outras partes**

- `TabletScreen`: troque `NetworksTab networks` e `NameFields names` por `NetworksPanel networks`; `init`: `networks.addButtons(this::add, font)` e `networks.createBoxes(font).forEach(this::addRenderableWidget)` (o campo do `TitleEditor` e os dois do `AccessPanel`); `render`: `networks.renderOverlay(g, font, mouseX, mouseY)` na aba Redes (as janelas e as sugestões do acesso por cima de tudo); `mouseClicked` na aba Redes: primeiro `networks.mouseClicked(...)`; `mouseDragged`/`mouseReleased`/`mouseScrolled` repassados; `rebuild()` sem o campo da nova rede; os ganchos `previewNetwork(int)` e `previewGroup(int)` passam a `networks.preview(...)`/`previewGroup(...)`; apague `previewNewOpen`, `groupRowCenter`, `groupNetworkCenter` e `newBox()` e ponha `public NetworksPanel networks()`.
- `TabletBody`, `TabletTabs`, `TabletTips`: `NetworksTab` vira `NetworksPanel`; o `overRemoveButton` sai (o "Apagar" de dois cliques desarma dentro do painel, no clique fora dele).
- `TabletKeys`: recebe o `NetworksPanel` no lugar de `NameFields` e `AccessPanel`; na aba Redes, `networks.keyPressed(keyCode, scanCode, modifiers, focused)` vem antes de tudo (janela aberta: a tecla é dela; o campo do título: Enter grava, Esc desiste; os do acesso: `access.keyPressed`); `ink()` pinta o campo do título e chama `networks.access().ink()`.
- `TabletActions`:
  ```java
    /** Põe a rede no grupo (o servidor a tira dos outros) ou, com {@code group} nulo, a tira de todos em que está. */
    void groupMove(UUID network, @Nullable UUID group, List<UUID> currentGroups) {
        if (group != null) {
            send(new TabletActionPayload(menu.containerId, Action.GROUP_ADD_NETWORK, Optional.of(group),
                    Optional.of(network), "", 0));
            return;
        }
        for (UUID current : currentGroups) {
            send(new TabletActionPayload(menu.containerId, Action.GROUP_REMOVE_NETWORK, Optional.of(current),
                    Optional.of(network), "", 0));
        }
    }

    /** O ícone do grupo; {@code null} tira (volta à primeira máquina). */
    void groupIcon(UUID group, @Nullable ResourceLocation icon) {
        send(new TabletActionPayload(menu.containerId, Action.GROUP_ICON, Optional.of(group), Optional.empty(),
                icon == null ? "" : icon.toString(), 0));
    }
  ```
  e o `groupNetwork` de hoje sai (sem uso).
- `NetworkSavedData.palette()` sai (só a aba antiga o usava).

Lang (pt / en), prefixo `gui.wirelessautomate.tablet.`:

| Chave | pt_br | en_us |
| --- | --- | --- |
| `nets.new_network` | `+ Rede` | `+ Network` |
| `nets.new_group` | `+ Grupo` | `+ Group` |
| `nets.default_network` | `Rede` | `Network` |
| `nets.default_group` | `Grupo` | `Group` |
| `nets.machines` / `.one` | `%s máquinas` / `%s máquina` | `%s machines` / `%s machine` |
| `nets.networks` / `.one` | `%s redes` / `%s rede` | `%s networks` / `%s network` |
| `nets.group.sub` | `%s · %s` | `%s · %s` |
| `nets.state.active` | `ativa` | `active` |
| `nets.state.paused` | `pausada` | `paused` |
| `nets.group` | `Grupo` | `Group` |
| `nets.access` | `Acesso` | `Access` |
| `nets.access.all` | `Todos` | `Everyone` |
| `nets.access.guests` | `Convidados` | `Guests` |
| `nets.invite` | `+ convidar` | `+ invite` |
| `nets.access.window` | `Acesso a %s` | `Access to %s` |
| `nets.here` | `Nesta rede` | `In this network` |
| `nets.here.line` | `%s dão · %s recebem` | `%s give · %s receive` |
| `nets.here.empty` | `Nenhuma máquina ainda` | `No machines yet` |
| `nets.pause` / `nets.resume` | `Pausar` / `Retomar` | `Pause` / `Resume` |
| `nets.pause.tooltip` | `Pausa o grupo %s inteiro` | `Pauses the whole %s group` |
| `nets.pause.no_group` | `Ponha a rede num grupo para pausá-la` | `Put the network in a group to pause it` |
| `nets.use` / `nets.use.active` | `Usar` / `Em uso` | `Use` / `In use` |
| `nets.remove_network` | `Apagar rede` | `Delete network` |
| `nets.remove_group` | `Apagar grupo` | `Delete group` |
| `nets.remove.armed` | `Clique de novo para apagar` | `Click again to delete` |
| `nets.pause_group` / `nets.resume_group` | `Pausar grupo` / `Retomar grupo` | `Pause group` / `Resume group` |
| `nets.add_to_group` | `+ pôr uma rede neste grupo` | `+ put a network in this group` |
| `nets.empty` | `Nenhuma rede ainda. Crie uma com + Rede.` | `No networks yet. Create one with + Network.` |

As chaves que só a aba antiga usava (`private`, `public`, `use.short*`, `network.remove.short`, `remove.confirm*`, `network.new`, `group.new`, `group.network.tooltip`, `networks.also_in`, `networks.group.tooltip`, `group.elsewhere`, `group.hint`, `meta.*` do cabeçalho) saem dos dois arquivos depois de um `grep -rn` confirmar que nada mais as usa.

- [ ] **Step 6: Ganchos e e2e**

Ganchos públicos no `NetworksPanel` (coordenadas da tela, `null` fora da vista): `networkRowCenter(UUID)`, `groupRowCenter(UUID)`, `newNetworkCenter()`, `newGroupCenter()`, `titleCenter()`, `groupPickerCenter()`, `pickerRowCenter(@Nullable UUID group)`, `accessCenter(boolean guests)`, `inviteCenter()`, `chipRemoveCenter(int index)`, `pauseCenter()`, `removeCenter()`, `addToGroupCenter()`, `colorSlotCenter()`, `iconSlotCenter()`, `modalOpen()`, `selectedNetwork()`, `selectedGroup()`, `titleBox()` (o `EditBox` do nome), `preview(int)`, `previewGroup(int)`.

Crie `client/dev/E2eNetworksSteps.java` com `static void networksSteps(List<Step> list)`, chamado no `tabletSteps` no lugar de `tabletGroupSteps(list)` e de `E2eNetworkSectionSteps.sectionSteps(list)`:

1. "redes: aba" — clica em `tab.networks`; espera `networks().selectedNetwork() != null`.
2. "redes: + Grupo cria e abre o nome" — clica em `newGroupCenter()`; espera um grupo meu com nome `tr("nets.default_group")…` no snapshot e `titleBox().isFocused()`; então `type(GROUP_NAME)` (o texto selecionado é trocado) e Enter; espera o grupo `GROUP_NAME` no servidor.
3. "redes: escolher a rede dos itens" — clica em `networkRowCenter(otherNetwork)`.
4. "redes: o seletor Grupo põe a rede no grupo" — clica em `groupPickerCenter()`, depois em `pickerRowCenter(id de GROUP_NAME)`; espera no servidor o grupo com a rede.
5. "redes: Pausar pausa o grupo da rede" — clica em `pauseCenter()`; espera `isPaused(otherNetwork)`; captura `tablet-redes-pausada`; os passos das pedras de hoje ("pedras param", "retomar: pedras chegam"), o retomar pelo mesmo botão.
6. "redes: um segundo grupo" — "+ Grupo", nome `SECOND_GROUP`, Enter; escolhe a rede; seletor Grupo → `SECOND_GROUP`; espera, **no servidor**, a rede só no segundo grupo (`data.groups().stream().filter(g -> g.networks().contains(otherNetwork)).count() == 1`).
7. Capturas `tablet-redes` e `clipCheck("Tablet: Redes")`; com a variante escuro, `tablet-redes-escuro`.
8. "redes: o grupo escolhido" — clica em `groupRowCenter(id de GROUP_NAME)`; espera `selectedGroup()` e o botão `nets.pause_group`; captura `tablet-grupo`, `clipCheck`.
9. "redes: + pôr uma rede neste grupo" — clica em `addToGroupCenter()` e na linha da rede dos itens; espera a rede de volta só no `GROUP_NAME`.
10. "redes: o segundo grupo sai" — "Apagar grupo" duas vezes no `SECOND_GROUP` (escolhido pela coluna); espera ele fora do snapshot.

No `E2eAccessSteps`, o passo "a seção aparece na rede privada" vira: escolhe a rede, clica em `accessCenter(true)` (Convidados) e em `inviteCenter()`; espera `modalOpen() && access().shown()`. O resto (campos, sugestões, ✕ de dois cliques) não muda; no fim, Esc fecha a janela. `TabletScreen.access()` passa a `networks.access()`.

No `E2eShowcase` (`:355-395`) e no `ScreenshotTablet` (`:110-121`), troque `previewNewOpen` por nada e confira os `previewNetwork`/`previewGroup`; no showcase, abra a janela de acesso pelo gancho antes de contar os convidados.

Run: `./scripts/e2e.sh --only tablet --capture tablet- --variants escuro,pt`
Expected: `OK`. Olhe `tablet-redes*` e `tablet-grupo*` ao lado de `docs/preview/tablet-do-zero/redes.png` e `grupo.png` (mesmo desenho; os dados diferem): o mesmo lugar para cada peça, nada cortado, as cores dos dois temas.

- [ ] **Step 7: Verificação e commit**

Verificação das Restrições globais, com `medir.py` (nenhum arquivo do pacote novo acima de 500; `TabletScreen` não pode crescer: ele perde os ganchos da aba antiga). Commit: `Tablet do zero 8/11: a aba Redes nova (grupos com ícone, rede com Grupo, Acesso e Nesta rede, grupo com as redes), a antiga sai`.

---

### Task 9: O seletor de cor na rede

**Files:**
- Create: `client/tablet/networks/ColorModal.java`
- Modify: `client/tablet/networks/NetworkDetail.java` (o clique no slot), `NetworksPanel.java` (ganchos), `client/dev/E2eNetworksSteps.java`

**Interfaces:**
- Consumes: `ColorPopup`, `ColorLook.TABLET` (Tarefa 3); `TabletModal`/`ModalLayer` (Tarefa 6).
- Produces: `ColorModal(Font font, int left, int top, NetworkView network, IntConsumer commit)`; ganchos `colorOpen()`, `colorSquareCenter()`, `colorValue()`, `colorHexFocus()` no `NetworksPanel`.

**Custo:** enquanto a janela está aberta, só desenho (as colunas do quadrado refeitas quando o matiz muda). **Um pacote só, ao fechar**, e só se a cor mudou (`NETWORK_COLOR`); o arrasto não manda nada. A cor mostrada no slot e na coluna durante a edição é a local.

- [ ] **Step 1: `ColorModal`**

```java
/**
 * O seletor de cor da rede escolhida (mockup {@code popup_cor}): o {@link ColorPopup} comum no visual
 * {@link ColorLook#TABLET}, sem alfa, sem conta-gotas e sem as cores do tema, logo abaixo do slot da cor. Por cima do
 * véu, o slot é redesenhado com a cor que está sendo escolhida. A cor vai ao servidor uma vez, ao fechar, e só se mudou.
 */
final class ColorModal implements TabletModal {
    private static final ColorPopup.Options OPTIONS = new ColorPopup.Options(ColorLook.TABLET, false, false, null, null);

    private final int slotX;
    private final int slotY;
    private final int initial;
    private final IntConsumer commit;
    private final ColorPopup popup;
    private int current;

    ColorModal(Font font, int left, int top, int rgb, IntConsumer commit) {
        this.slotX = left + TabletLayout.PANEL_X0;
        this.slotY = top + 30;
        this.initial = 0xFF000000 | rgb;
        this.current = initial;
        this.commit = commit;
        this.popup = new ColorPopup(font, slotX - 1, top + 50, initial, OPTIONS, value -> current = value);
    }

    /** A cor que o slot e a coluna mostram enquanto a janela está aberta. */
    int current() {
        return current;
    }

    @Override
    public void render(GuiGraphics g, Font font, int mouseX, int mouseY) {
        PanelPaint.slot(g, slotX - 1, slotY - 1, 18, false);
        g.fill(slotX + 1, slotY + 1, slotX + 15, slotY + 15, current);
        popup.render(g, mouseX, mouseY);
    }

    @Override
    public boolean contains(double mouseX, double mouseY) {
        return popup.contains(mouseX, mouseY);
    }

    @Override
    public void mouseClicked(double mouseX, double mouseY, int button) {
        popup.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY) {
        return popup.mouseDragged(mouseX, mouseY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY) {
        return popup.mouseReleased();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return popup.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        return popup.charTyped(c, modifiers);
    }

    @Override
    public void closed() {
        popup.closed();
        if (current != initial) {
            commit.accept(current & 0xFFFFFF);
        }
    }

    ColorPopup popup() {
        return popup;
    }
}
```

Confira com `TabletLayout.PANEL_X0` (Tarefa 7) e com o `ModalLayer`, que já traduz z (o `ColorPopup` traduz mais 400 por conta própria: tire o `translate` dele quando `look.framed()`, porque a camada já o fez, ou deixe os dois; o que importa é ficar por cima dos itens da coluna). No `NetworkDetail`, o clique no slot (manageable) faz `modals.open(new ColorModal(font, left, top, view.color(), rgb -> actions.networkColor(view.id(), rgb)))`; enquanto `modals.current() instanceof ColorModal c`, o slot e o quadradinho da rede na coluna usam `c.current()`.

- [ ] **Step 2: e2e**

No `E2eNetworksSteps`, depois do passo 4: "redes: a cor pelo seletor" — clica em `colorSlotCenter()`; espera `colorOpen()`; captura `tablet-redes-cor` (e `-escuro` na variante); clica em `colorSquareCenter()` (3/4 da largura, 1/4 da altura, como o gancho do `ColorPopup`); espera `colorValue()` diferente da cor antiga; "redes: Esc fecha e grava" — `tabletScreen().keyPressed(GLFW_KEY_ESCAPE, 0, 0)`; espera `!colorOpen()` e, no servidor, `data.network(otherNetwork).color() == colorValue() & 0xFFFFFF` (guarde o valor antes de fechar). Depois, "redes: o hexadecimal" — abre, `colorHexFocus()`, `type("#123456")`, Enter, Esc; espera a cor `0x123456` no servidor. No fim, devolve a cor de antes pelo servidor.

Run: `./scripts/e2e.sh --only tablet --capture tablet-redes --variants escuro`
Expected: `OK`; `tablet-redes-cor` com o seletor no lugar do mockup (`redes-cor.png`).

- [ ] **Step 3: Verificação e commit**

Verificação das Restrições globais (e `./scripts/e2e.sh --only tela`: o `ColorPopup` é o mesmo). Commit: `Tablet do zero 9/11: a cor da rede pelo seletor comum, gravada ao fechar`.

---

### Task 10: O ícone do grupo (grade com busca e o JEI)

**Files:**
- Create: `client/tablet/networks/IconModal.java`, `client/tablet/networks/IconSearch.java` (puro)
- Test: `src/test/.../client/tablet/networks/IconSearchTest.java`
- Create: `compat/jei/TabletGhostHandler.java`
- Modify: `compat/jei/WirelessAutomateJeiPlugin.java:279-283`, `client/tablet/TabletScreen.java` (`ghostBounds()`, `acceptGhost(Object)`), `client/tablet/networks/GroupPanel.java`, `NetworksPanel.java`, `client/dev/E2eNetworksSteps.java`
- Modify: `lang/en_us.json`, `lang/pt_br.json` (`nets.icon.search` "Buscar item"/"Search item"; `nets.icon.jei` "ou arraste um item do JEI"/"or drag an item from JEI"; `nets.icon.tooltip` "Escolher o ícone do grupo"/"Choose the group icon")

**Interfaces:**
- Consumes: `TabletActions.groupIcon` (Tarefa 8), `GroupView.icon()` (Tarefa 5), `TabletModal` (Tarefa 6).
- Produces: `IconSearch.matches(String query, String id, String name): boolean`; `IconModal(Font, int left, int top, @Nullable ResourceLocation chosen, ItemStack shown, Inventory inventory, Consumer<@Nullable ResourceLocation> choose)` com `cellCenter(int)`, `searchFocus()`, `bounds(): int[4]`, `accept(ItemStack)`; `TabletScreen.ghostBounds(): int @Nullable []` e `acceptGhost(Object)`.

**Custo:** a grade vazia mostra os itens do inventário do jogador (36 slots, sem registro). Com texto (2 letras ou mais), a busca percorre o registro de itens **uma vez por consulta**, 150 ms depois da última tecla, e para em 256 resultados; os `ItemStack`s da grade são criados aí, nunca por quadro. Num pack grande (dezenas de milhares de itens) isso é alguns ms por consulta: meça com o e2e (`/wa profile` não cobre o cliente; anote o tempo da consulta no relatório com `System.nanoTime` num teste manual) e, se passar de 16 ms, troque por uma lista de ids montada na primeira consulta e guardada enquanto a janela está aberta.

- [ ] **Step 1: `IconSearchTest` (falha)**

```java
package io.github.matheusanbs.wirelessautomate.client.tablet.networks;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class IconSearchTest {
    @Test
    void matchesIdOrNameIgnoringCaseAndAccents() {
        assertTrue(IconSearch.matches("pick", "minecraft:iron_pickaxe", "Picareta de Ferro"));
        assertTrue(IconSearch.matches("PICARETA", "minecraft:iron_pickaxe", "Picareta de Ferro"));
        assertTrue(IconSearch.matches("fundicao", "x:y", "Fundição"));
        assertTrue(IconSearch.matches("mekanism:", "mekanism:steel_ingot", "Steel Ingot"));
        assertFalse(IconSearch.matches("diamante", "minecraft:iron_pickaxe", "Picareta de Ferro"));
    }

    @Test
    void shortQueriesDoNotSearch() {
        assertFalse(IconSearch.searches("a"));
        assertTrue(IconSearch.searches("ab"));
    }
}
```

- [ ] **Step 2: Rodar e ver falhar**, depois a classe:

```java
/** O filtro da grade de ícones: o texto (sem acento e sem caixa) dentro do id ou do nome do item. Pura, com JUnit. */
final class IconSearch {
    static final int MIN_QUERY = 2;

    private IconSearch() {
    }

    static boolean searches(String query) {
        return query.strip().length() >= MIN_QUERY;
    }

    static boolean matches(String query, String id, String name) {
        String q = fold(query.strip());
        return fold(id).contains(q) || fold(name).contains(q);
    }

    static String fold(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }
}
```

(No laço do registro, dobre a consulta uma vez e compare com `fold` do nome; se o `fold` por item pesar, guarde o nome dobrado junto do id na lista da janela.)

Run: `./gradlew test --tests '*IconSearchTest'` → PASS.

- [ ] **Step 3: `IconModal`**

Medidas de `popup_icone` (ver "Medidas"): janela `PanelPaint.window(X0 − 1, 50, 158, 96)`; campo `PanelPaint.field(x + 7, y + 6, 144, 14, foco)` com um `EditBox` sem borda em (x + 11, y + 9), largura 136, dica `tr("nets.icon.search")` em `MUTED` (o foco vem ao abrir); 8 × 3 células `PanelPaint.slot(x + 7 + c·18, y + 24 + l·18, 18, escolhida)` e o item em +1, +1 (`g.renderItem`); a roda rola uma linha; `tr("nets.icon.jei")` `MUTED` em (x + 7, y + 82). Antes da janela, redesenha o slot do grupo (`PanelPaint.slot(X0 − 1, 29, 18, false)` e o item em (X0, 30)). A lista: vazia → os itens distintos do inventário do jogador, com o ícone atual primeiro; com consulta → `BuiltInRegistries.ITEM` filtrado por `IconSearch` (sem `Items.AIR`), até 256, na ordem do registro, com espera de 150 ms depois da última tecla (como o `SuggestField`). Clique numa célula: `choose.accept(id)` e fecha; clique na escolhida: `choose.accept(null)` (volta à primeira máquina) e fecha. Dica da célula: o nome do item. `accept(ItemStack)` (do JEI): escolhe o item e fecha. `bounds()` = a janela inteira (o JEI solta em qualquer lugar dela).

No `GroupPanel`, o clique no slot (manageable) abre `new IconModal(..., view.icon().orElse(null), stackDoGrupo, minecraft.player.getInventory(), id -> actions.groupIcon(view.id(), id))`.

- [ ] **Step 4: JEI**

```java
package io.github.matheusanbs.wirelessautomate.compat.jei;

/**
 * Ingrediente fantasma no Tablet: com a grade de ícones de um grupo aberta, soltar um item do JEI nela escolhe o ícone
 * ({@link TabletScreen#ghostBounds()}). Só itens; fora da grade, nada.
 */
final class TabletGhostHandler implements IGhostIngredientHandler<TabletScreen> {
    @Override
    public <I> List<Target<I>> getTargetsTyped(TabletScreen screen, ITypedIngredient<I> ingredient, boolean doStart) {
        int[] at = screen.ghostBounds();
        if (at == null || !(ingredient.getIngredient() instanceof ItemStack)) {
            return List.of();
        }
        Rect2i area = new Rect2i(at[0], at[1], at[2], at[3]);
        return List.of(new Target<>() {
            @Override
            public Rect2i getArea() {
                return area;
            }

            @Override
            public void accept(I dropped) {
                screen.acceptGhost(dropped);
            }
        });
    }

    @Override
    public void onComplete() {
    }
}
```

No plugin, `registration.addGhostIngredientHandler(TabletScreen.class, new TabletGhostHandler());` ao lado do da Tela de Sinal. No `TabletScreen`: `public int @Nullable [] ghostBounds() { return tab == Tab.NETWORKS ? networks.ghostBounds() : null; }` e `public void acceptGhost(Object ingredient) { if (ingredient instanceof ItemStack stack) networks.acceptGhost(stack); }` (o `NetworksPanel` repassa ao `IconModal` aberto).

- [ ] **Step 5: e2e**

No `E2eNetworksSteps`, com o `GROUP_NAME` escolhido: "grupo: abrir a grade de ícones" — clica em `iconSlotCenter()`; espera `iconOpen()` (gancho novo); `type("diamond")`; espera 150 ms + a lista (`iconCellCenter(0) != null` e o item da célula 0 é `minecraft:diamond`; gancho `iconCellItem(int)`); captura `tablet-grupo-icone` (e `-escuro`); "grupo: escolher o diamante" — clica na célula 0; espera no servidor `group.icon() == minecraft:diamond` e, no snapshot, `GroupView.icon()`; "grupo: o JEI" — chama `tabletScreen().acceptGhost(new ItemStack(Items.EMERALD))` com a grade aberta (o arraste de verdade fica para o teste do dono) e espera o esmeralda no servidor; "grupo: tirar o ícone" — abre e clica na escolhida; espera ícone nulo.

Run: `./scripts/e2e.sh --only tablet --capture tablet-grupo --variants escuro`
Expected: `OK`; `tablet-grupo-icone` no lugar do mockup (`grupo-icone.png`).

- [ ] **Step 6: Verificação e commit**

Verificação das Restrições globais. Commit: `Tablet do zero 10/11: o ícone do grupo pela grade com busca e pelo JEI`.

---

### Task 11: Docs, guia e progresso

**Files:**
- Modify: `docs/mapa/tablet.md` (seção `client/tablet/`: a moldura `TabletFrame`, o kit `client/tablet/kit/`, o pacote `client/tablet/networks/` com o papel de cada classe; o que saiu: `TabletHeader`, `NetworksTab`, `NetworkList`, `GroupDetail`, `NameFields`, `AccessSection`, `NetworkSections`, `SideList`; seção `menu/Tablet*`: `NetworkTally`, `NetworkTallies`, o `GroupView` e o `NetworkView` novos, `GROUP_ICON`)
- Modify: `docs/mapa/tela-de-sinal.md` (o `TvColorPopup` é o adaptador do `client/color/ColorPopup`; `TvHsv` e `TvRecentColors` saíram para `client/color`)
- Modify: `docs/mapa/cliente-e-pacotes.md` (`client/color/`, `client/PanelPaint`, os tokens novos do `GuiPalette`, protocolo 47)
- Modify: `docs/mapa/motor.md` (o `WaGroup.icon`, `moveToGroup`, `nextColor` no `NetworkSavedData`)
- Modify: `docs/mapa/testes.md` (`NetworkGroupGameTests`, os JUnit novos, `E2eNetworksSteps`), `docs/mapa/logica-pura.md` (`ColorHsv`, `RecentColors`, `ColorState`, `NetworkTally`, `SegmentLayout`, `ChipLayout`, `NetworkTree`, `DefaultNames`, `IconSearch`), `docs/mapa/compat.md` (`TabletGhostHandler`)
- Modify: `scripts/guide/paginas/network_tablet.py` (a tabela das abas: Redes, Máquina, Fluxos, sem o Mapa; a seção "Redes e grupos"), `scripts/guide/paginas/networks.py` (a cor livre e o grupo de cada rede), nos dois idiomas; depois `python -I scripts/guide/gerar_guia.py` (nunca edite os `.md` gerados)
- Modify: `docs/progresso/historico/2026-11.md`, `docs/progresso/pendencias.md`, `docs/progresso/estado.md`, `docs/progresso/README.md`, `docs/progresso/decisoes.md`

- [ ] **Step 1: Guia**

Na `network_tablet.py`, a linha da tabela das abas fica, em pt: `| **Redes** | Criar e organizar redes e grupos: cor, grupo, acesso e o que cada rede tem. |`, `| **Máquina** | As máquinas da base (por ora, a Lista na visão Máquinas). |`, `| **Fluxos** | ... (como hoje) |`, e a linha do **Mapa** sai. A seção "Redes e grupos" vira, em passos curtos:

```
## Redes e grupos

À esquerda ficam os grupos, cada um com o ícone e as redes dele, e por último **Sem grupo**.

- **+ Rede** e **+ Grupo** criam na hora; digite o nome e aperte Enter.
- Clique numa rede para ver e mudar:
  - **a cor:** clique no quadrado ao lado do nome e escolha no seletor (qualquer cor);
  - **o nome:** clique nele;
  - **o grupo:** uma rede fica num grupo só; escolher outro a muda de lugar;
  - **o acesso:** **Todos** ou **Convidados** (com **+ convidar** para pôr jogadores e times);
  - **Nesta rede:** quantas máquinas dão e recebem de cada tipo.
- **Pausar** para todas as redes do grupo da rede; **Apagar rede** pede dois cliques.
- Clique num grupo para mudar o nome e o ícone (clique no ícone e busque um item, ou arraste um do JEI), ver as
  redes dele, pôr outra rede nele, pausar e apagar.
```

e o equivalente em inglês. Na `networks.py`, onde fala da cor e do grupo, diga que a cor é livre e que cada rede fica num grupo só. Rode o gerador e confira o diff dos `.md` gerados.

- [ ] **Step 2: Progresso**

- `historico/2026-11.md`, no topo: uma linha "03/11 (ou a data): Tablet do zero, etapa 1: moldura com Redes, Máquina e Fluxos; aba Redes nova (grupos com ícone, cor livre no seletor comum, uma rede num grupo, Acesso com fichas, Nesta rede); o seletor de cor da Tela de Sinal no `client/color`; protocolo 47. N JUnit + N + N + N + N + N GameTests, e2e `tablet`, `tela` OK." com os números reais.
- `pendencias.md`, área Tablet: o teste à mão do dono (o arraste do JEI de verdade, a escala 4 em 1080p, o "Usar" que ficou no rodapé, ajuste 6), as etapas 2 a 4, e o limite da contagem "dão/recebem" (ajuste 3).
- `estado.md`: a linha do Tablet (aba Redes nova, ícone do grupo, cor livre) "no ramo, sem versão".
- `decisoes.md`: "Uma rede fica num grupo só (o seletor Grupo a move); um mundo antigo com a rede em dois grupos fica igual até alguém mexer, e a lista a mostra no primeiro." e "A cor de uma rede é livre; a nova recebe a próxima da paleta."
- `README.md`: "Agora" e "Próximo passo" (a etapa 2 do Tablet, a Máquina).

- [ ] **Step 3: Varreduras e commit**

```bash
python -I .claude/skills/wa-comentarios/scripts/varrer.py
python -I .claude/skills/wa-higiene/scripts/varrer.py
```

Resolva o que a varredura de comentários acusar nos arquivos do ramo (javadocs que citam `NetworksTab`, `TabletHeader`, `TvHsv`, `colorFor`, "quatro abas"; `grep -rn "NetworksTab\|TabletHeader\|TvHsv\|TvRecentColors\|colorFor\|quatro abas" src docs/mapa`). Commit: `Tablet do zero 11/11: guia, mapa e progresso da etapa 1`.

---

## Revisão do plano contra a spec (etapa 1)

| Item da spec | Tarefa |
| --- | --- |
| Moldura com 3 abas, a ativa sublinhada pelo `acc`, divisória e cabeçalho comum | 7 (moldura), 6 (`PanelHeader`) |
| Componentes comuns: segmentos, slot, seletor, campo, link, janela com véu | 1 (`PanelPaint`), 6 (kit) |
| Redes: coluna com grupos, ícone, "pausado", redes com cor e contagem, "Sem grupo", "+ Rede" primário e "+ Grupo" | 8 |
| Rede escolhida: slot de cor, nome com lápis, "N máquinas", estado; Grupo; Acesso Todos/Convidados com fichas e "+ convidar"; "Nesta rede"; Pausar e Apagar | 5 (dados), 8 (tela), 9 (cor) |
| Seletor de cor: quadrado 88 × 56, matiz, hexadecimal, recentes (8), atual emoldurada, `hi` sobre véu | 3, 9 |
| Grupo escolhido: slot do ícone, nome, "N redes · N máquinas", redes do grupo, "+ pôr uma rede", Pausar grupo, Apagar grupo | 8 |
| Ícone: grade 8 × 3 com busca, arraste do JEI, escolhido em `fg`, reserva = primeira máquina, senão o Tablet | 4, 5, 10 |
| Decisão 1 (paleta, carmim) | 1 |
| Decisão 2 (`icon` no NBT, `GROUP_ICON` com o id em `text`) | 4, 5 (`MAX_TEXT`) |
| Decisão 3 (uma rede, um grupo; mundo antigo igual; lista no primeiro grupo) | 4, 8 (`NetworkTree`) |
| Decisão 4 (cor livre; rede nova com a próxima da paleta; chave `color` igual) | 4, 9 |
| Decisão 5 (seletor comum com alfa, conta-gotas e tema opcionais; Tela de Sinal sem mudar, provado no e2e; `TvHsv` no mesmo lugar com JUnit; 8 recentes) | 2, 3 |
| Máquina e Fluxos com as abas antigas dentro da moldura; Mapa fora da barra | 7 |
| Custo: nada com a tela fechada; cabeçalho por mudança; ícone = um id a mais por grupo; nada recalculado por quadro | 5, 8, 9, 10 |
