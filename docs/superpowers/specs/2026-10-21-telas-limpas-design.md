# Telas mais limpas: design

Data: 21 de outubro de 2026. Aprovado pelo dono na versão 5 do estudo.

**Referência visual:** [`docs/preview/interfaces-limpas.html`](../../preview/interfaces-limpas.html), com os desenhos em pixel em [`docs/preview/interfaces-limpas/`](../../preview/interfaces-limpas/) (`roteador.png`, `alcance.png`, `alcance-maquina.png`, `tablet-lista.png`, `tablet-grade.png`, `filtro-entrada.png`, `filtro-tags.png`, `filtro-regra.png`, `vinculador.png`). As imagens estão em escala 1 ampliada 3×, desenhadas com as primitivas do `GuiPaint`, a fonte do Minecraft e os sprites do mod. Medidas em px de interface tiradas delas valem como alvo; a tela final é conferida pelas capturas do e2e.

## Problema

Jogadores reclamaram que as telas têm muita coisa e confundem. Na tela do roteador, para mudar uma coisa, o jogador acerta quatro seletores em ordem (alvo, aba do tipo, face, modo) e lê uma frase explicando o modo. O cubo das faces repete o que o visor 3D já faz, e todo controle tem o mesmo peso.

## Direção

Cada peça imita uma tela que o jogador do ATM10 já conhece, sem perder a identidade (visor 3D, Olho, abas com ícones, cores dos modos, porcelana e coral):

- **Faces como a configuração de lados do Mekanism** (`GuiSideConfiguration`, `SideDataButton`): o botão da face é o modo.
- **Ajustes em linhas com rótulo**, todas da mesma altura, rótulo em `MUTED` e controle à direita.
- **Grupos de controles em quadros afundados** (`GuiPaint.inset`), em vez de botões soltos.
- **Visão em grade no Tablet, como o Controller do XNet.**

Nenhuma função some. Onde uma muda de lugar, a tabela de cada tela abaixo diz para onde.

## O editor de faces (roteador, alcance e painel do Tablet)

É um componente só (o `NodePanel` de hoje, refeito), igual nos três lugares.

1. **Abas dos tipos:** iguais às de hoje (a aberta com nome e sublinhado coral, as outras só com o ícone), com "N redes" à direita.
2. **Quadro das faces** (`inset`, altura 82): o cubo desdobrado (Cima em cima; Esquerda, Frente, Direita, Trás no meio; Baixo embaixo), botões de **30 × 22** com 2 px entre eles.
   - Com modo: fundo `mix(BUTTON, modeColor, 0.28)`, borda `FG`, o sprite da porta (`port_*.png`, 16 px) à esquerda e a letra do lado (C, E, F, D, T, B, as chaves `side.letter.*` que já existem) à direita.
   - Sem modo: fundo `INSET`, borda `BEVEL_DARK`, só a letra em `MUTED`, centrada.
   - Rede: a bolinha (`GuiPaint.dot`) no canto de baixo à direita. Face do roteador: o Olho na cor do tier no canto de cima à direita.
   - Face com modo e sem rede: borda tracejada em `ACCENT` (o aviso de hoje).
   - Face escolhida: borda dupla `ACCENT`.
   - Ao lado, no mesmo quadro: **Limpar**, **Copiar** e **Colar** (46 × 14, um embaixo do outro, centrados na altura).
3. **Linha da face:** "Cima ▪ Extrai · 27 slots · roteador aqui": o nome do lado em `FG`, a tarja e o modo na cor do modo, o resto em `MUTED`. Substitui os quatro botões de modo e a frase do modo.
4. **Linhas** (rótulo com 58 px, linhas de 14 px a cada 17 px):
   - **Rede:** lista suspensa com a bolinha, borda na cor da rede (como hoje).
   - **Filtro:** o resumo ("Sem filtro", "3 entradas"), os **dois slots de cartão** e **Editar**. O rótulo "Cartões de filtro da face …" sai.
   - **Redstone:** caixa com o pó de redstone e o modo, ‹ › troca (Ignorar, Com sinal, Sem sinal).
   - **Prioridade:** caixa com ‹ valor ›; a roda do mouse também muda.

### Cliques no cubo

| Gesto | Efeito |
| --- | --- |
| Clique | Escolhe a face e avança o modo: Extrai → Insere → Armazém → Nenhum |
| Clique direito | Escolhe a face e volta o modo |
| Shift + clique | Escolhe a face e põe em Nenhum |
| Clique no visor 3D | Escolhe a face sem mudar o modo (como hoje) |
| Limpar | Todas as faces do tipo aberto em Nenhum; com Shift, de todos os tipos. Dois cliques, como o Desvincular (`ArmedClick`) |
| Copiar / Colar | Copia a face escolhida (modo, prioridade, redstone, filtro e rede) e cola na face escolhida depois, no mesmo tipo |
| Dica sobre um botão | "Cima · Extrai · Fundição" e o que o modo faz (o texto da frase de hoje) |

No painel só de leitura (Tablet longe ou descarregado), o cubo não muda nada e Limpar, Copiar e Colar ficam apagados.

## Tela do roteador

- Cabeçalho igual (nome com renomear, vazão, pílula do tier, dois slots de upgrade, tema).
- À esquerda, o visor 3D (maior, porque o rótulo dos cartões sai) e o inventário embaixo.
- À direita, o editor de faces.

## Alcance (com o Upgrade de Alcance)

- À esquerda: o visor da máquina escolhida no alto (menor), "Máquinas · 10/16" e a lista embaixo, o inventário no pé.
- **Uma linha por máquina** (12 px): bolinha do estado, "#N" (com "· exceção" quando tem), distância e o Olho. Coordenadas na dica.
- Grupo: ▾/▸, o ícone do bloco, "Nome ×N" e, quando houver, "1 trocada" à direita.
- Os botões da linha escolhida (renomear, Atualizar, Voltar ao perfil, Tirar redes, Desvincular) continuam como hoje.
- À direita: a barra **Editando** (pílula com o ícone do bloco e o alvo: "Alto-forno (todas)" ou "Alto-forno #3") e, numa exceção, o botão **Igualar** (o "Voltar a igual às outras"). Embaixo, o editor de faces; na exceção, a linha da face diz "difere do grupo" quando a face difere do perfil.

## Tablet

| Hoje | Na versão nova |
| --- | --- |
| Busca com etiquetas e sugestões | Igual |
| Roteadores · Máquinas | Seletor com a terceira opção **Grade** |
| Filtro "Com problemas N" | Botão "! N" (fundo `WARN` claro) na ponta do seletor; o texto inteiro na dica |
| Linhas, árvore, Mostrar todas, renomear, Olho | Iguais; segunda linha com a máquina presa e o estado na cor (cheio em `WARN`, sem rede em `ACCENT`) |
| Selecionar, marcar, Mover, Colar | Shift + clique marca; a barra aparece com algo marcado |
| Copiar, colar e desfazer o perfil | Iguais (Ctrl+C, V, Z) |
| Painel do nó, No mundo, Abrir | À direita, com o editor de faces; "No mundo" vira botão com o Olho |
| Fluxos, Mapa, Redes | Iguais; só o cabeçalho e as abas no desenho novo |

**Grade:** uma linha por nó (14 px): Olho do tier, nome, uma coluna de 28 px por tipo carregado com **uma tarja 4 × 8 por face com modo**, na cor do modo (contorno `FG`), um traço `DISABLED` sem faces; depois o estado e a rede (bolinha e nome, "—" em `ACCENT` sem rede). Legenda das cores embaixo. Clique numa célula escolhe o nó e abre o painel na aba daquele tipo. Usa o `faceModes` que a página do Tablet já leva (`TabletPageCodec`): nada novo no pacote.

## Filtro

- Linha do alto: Lista branca · Lista negra (seletor de duas partes) e a caixa **Componentes**.
- Esquerda: busca, a lista (ícone 16 px, nome, tipo da entrada em `DISABLED` para item exato e `MUTED` nos outros, estoque à direita em `ACCENT_DEEP`), a dica "Shift + clique ou arraste do JEI" e o inventário.
- Direita: as abas **Entrada, Tags, Regra, Mais** sobre um quadro afundado com o conteúdo.
- Na Regra, o "—" escolhido fica apagado (`INSET`), e Sim e Não escolhidos ficam escuros.
- Tudo de hoje continua: estoque, Ver tags, Editar, Remover, inspetor, busca de tags, caixas com contagem, prévia, Adicionar N, as seis propriedades, encantamento com nível, durabilidade, Só em, resumo, pilhas acesas, Limpar, Cancelar, Salvar, Adicionar, Cartão de Filtro (Importar e Exportar), Limpar com confirmação, janela redimensionável.

## Vinculador

- Cabeçalho: "Vinculador" e o modo (Único · Área · Máquinas) num seletor.
- Esquerda: Rede ativa (lista com "Nenhuma (desvincula)"), + Nova rede e o quadro **Vincula**: os cinco tipos como botões de ícone 16 × 15 com **Todos**, e as faces no **mesmo cubo** (botões 22 × 16 com a letra; marcada em coral claro) com **Todas**.
- Direita: o mapa da área, os cantos, a contagem, **Vincular** na cor da rede e **Limpar**.
- Usa a máscara de faces e as abas que já existem (`LinkerFaces`, `LinkerTabs`).

## Outras telas

Baú, tanques, Bateria, Tanque de Source, Núcleo do Ressonador e editor de receitas: só o cabeçalho, as abas e os quadros afundados comuns quando já têm o equivalente. Sem mudança de função.

## Fora

- Mudar o motor, o NBT, os componentes ou a config.
- O guia continua com o mesmo conteúdo; só os gestos do cubo entram nele.

## Custo

- O cubo e a grade desenham retângulos e sprites que a tela já tem; nada por quadro além de desenhar, nada alocado no `render`.
- Nenhum dado novo do servidor para o cubo, a grade ou a lista; tudo vem do `RouterSnapshot` e da página do Tablet.
- Um payload novo, pequeno: Limpar, Copiar/Colar entre faces (`FaceActionPayload`), aplicado com `batchChanges` (uma remontagem). `ModPayloads.VERSION` 27 → 28.
