# Vinculador e Configurador novos (design)

Aprovado pelo dono em 22/10/2026 sobre o artifact `docs/preview/vinculador-configurador-novos.html`. Motivo: a rede passou a ser da face (escolhida na tela do roteador e no Tablet), então o Vinculador de redes e o Configurador de área viraram duplicata do Tablet. A refatoração tira o que se sobrepõe e deixa o código limpo, sem o antigo ao lado do novo.

## Decisões aprovadas

1. **Vinculador, dois modos** (`LinkerMode`: `LINK` e `OPEN`). Shift + clique no ar alterna, como hoje.
   - **Vincular (`LINK`)**: é o modo Máquinas de hoje, agora o único. Clique num roteador o escolhe; clique em outro bloco vincula ou desvincula essa máquina a ele; Shift + clique em dois blocos marca os cantos de uma área; o botão da tela vincula as máquinas da área. O **Desvincular** é o botão de dois estados dentro da tela (componente `linker_unlink`, já existente), sem modo à parte. O artifact mostrou um seletor "Única/Área": não existe, único e área convivem como hoje.
   - **Abrir (`OPEN`)**: clique (sem Shift) numa máquina abre a tela do roteador já editando essa máquina. Os candidatos são o roteador preso nela e os que a têm vinculada pelo alcance. Com um só, abre direto; com mais de um, abre uma **lista curta** (nome, tipo de ligação e distância) e a escolha abre a tela. Respeita o alcance do tier (`TabletMenu.inReach`), `mayInteract` e as proteções de outros mods (claims), conferidas no roteador que abre, tanto na lista de candidatos quanto na escolha (que chega por pacote, sem clique). Não exige que o jogador veja a rede do roteador: o clique normal no bloco do roteador já abre a tela dele para qualquer um.
2. **Sai do Vinculador** tudo que punha faces em redes: os modos Único e Área, as abas de tipo (`LinkerTabs`), as faces marcadas (`LinkerFaces`), a coluna de redes e o `canLeave` do Vinculador. O vínculo de máquinas passa a valer para todos os tipos carregados (`LoadedTypes.LIST`).
3. **Configurador só com o Pincel**: copiar (Shift + clique num roteador) e colar (clique num roteador), limpar (Shift + clique num bloco que não é roteador). Saem a Área, "qualquer máquina" (`PasteMode`, `ConfiguratorArea`) e o anel de modos da roda. A roda (Alt esquerdo) fica com **um anel só, o do tipo colado** (Tudo, um por tipo carregado). Shift + roda continua trocando o tipo (`CycleConfiguratorTypePayload`). O `canLeave` do colar (`PresetPaste.canLeave`) fica.
4. Sem funcionalidade nova no Configurador (o dono escolheu só simplificar).

## Dados antigos

- Os componentes que deixam de ter leitor ficam **registrados como legado** (codec `Codec.PASSTHROUGH`, sem stream codec, nenhum código os lê): `linker_tabs`, `linker_faces`, `linker_type`, `configurator_mode`, `configurator_any_machine`, `configurator_area`. Tirar o registro faria o `ItemStack` com o componente falhar ao ler e o jogador perderia o item. Uma linha de comentário no registro diz isso.
- `linker_mode` continua e muda de enum: o codec aceita os nomes antigos (`single`, `area`, `machines`, todos viram `LINK`) e `link`/`open`. Grava `link` ou `open`; sem o componente vale `LINK`.
- Cantos (`linker_area`) e roteador escolhido (`linker_router`) continuam como estão.
- Sem migração de mundo. Redes, grupos, roteadores e o `RemoteIndex` não mudam.

## Protocolo

`ModPayloads.VERSION` de `29` para `30`.

- Sai `LinkerSnapshotPayload`/`LinkerSnapshot` se a tela nova não precisar mais do estado de rede (a tela mostra o roteador escolhido, o resultado da área e a prévia; o estado do roteador escolhido vem do que o `LinkerMenu` já manda; a tarefa 3 decide o mínimo, sem manter campo de rede).
- `LinkerActionPayload.Op`: ficam `SET_UNLINK`, `CLEAR_AREA` e `LINK`; saem `SET_ACTIVE`, `CREATE_NETWORK`, `TOGGLE_TAB`, `SET_MODE` (o modo troca no ar, não na tela), `SET_TABS`, `SET_FACES`; os campos `network` e `text` saem do record.
- Novos: `LinkerOpenListPayload` (servidor → cliente: a lista de roteadores que chegam na máquina) e `LinkerOpenPickPayload` (cliente → servidor: a máquina e o roteador escolhidos).
- `ConfiguratorWheelPayload`: só o tipo (`Optional<ResourceType>`); o modo sai.

## Custo (wa-performance)

- Nada novo por tick, por visita ou por entrega: tudo no clique ou na tela aberta.
- **Abrir**: uma consulta a `RemoteIndex.routersAt` e aos seis vizinhos (`TabletClick.attachedRouters`) por clique; o servidor não guarda estado entre a lista e a escolha (revalida tudo no pedido). A lista vai num pacote pequeno (no máximo uma linha por roteador que chega na máquina, limite de 16) só quando há mais de um.
- **Vincular em área**: igual ao de hoje.
- **Memória**: nenhuma estrutura nova por porta, máquina ou nó. Saem estados do cliente (rascunho de rede, abas, faces).
- **Bytes**: a tela do Vinculador deixa de mandar a lista de redes.
- **Medição**: não toca motor, menus de roteador nem render do mundo. Sem bench; o `varrer_perf.py` nos arquivos mudados basta.

## Fora do escopo

Nada de funcionalidade nova no Configurador; o Tablet, as redes, os grupos e o Upgrade de Alcance não mudam; sem tela de migração.
