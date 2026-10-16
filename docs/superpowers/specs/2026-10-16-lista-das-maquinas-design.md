# Lista das máquinas sem a linha Editando, e o Atualizar · design

Pedido do dono (16/10/2026): a tela do roteador com o Upgrade de Alcance está poluída, e a linha Editando repete o que a lista das máquinas já marca. Pediu também um jeito de atualizar uma máquina trocada no lugar (o upgrade de tier de uma fábrica do Mekanism troca o bloco) sem desvincular e vincular de novo. Mockup aprovado: [`docs/preview/roteador-sem-editando.html`](../../preview/roteador-sem-editando.html).

## Decisões

1. **Sem a linha Editando com a lista à vista.** Com a lista das máquinas à vista (`MachineColumn.listShown()`: o botão Máquinas ligado, ou a tela larga lado a lado), a linha Editando some inteira: rótulo, pílula do alvo e os botões. O painel sobe uma linha, ou duas quando há uma máquina escolhida. Sem a lista (o visor 3D no lugar dela) e no Tablet, a linha continua, com a pílula que abre a lista do alvo.
2. **Botões na linha da máquina escolhida.** Só a linha escolhida (o alvo) ganha os botões pequenos, de 10 × 10 px, à direita e na ordem ⟳ Atualizar (só se trocada), ↺ Voltar ao perfil (só com exceção) e ✕ Desvincular. Na linha escolhida, o ↺ em `ACCENT` fica no lugar do "exc." e mostra a exceção. As outras linhas ficam como hoje. Clicar num botão não escolhe nem abre a linha.
3. **Desvincular em dois cliques.** O primeiro clique arma o ✕, que vira o botão de texto "Desvincular?" em `DANGER`, com o texto claro. O segundo clique, dentro de 3 s, confirma. Tirar o mouse do botão desarma, e trocar de alvo também. A mesma regra vale para o Desvincular da linha Editando sem a lista e no Tablet, para os dois lugares se comportarem igual.
4. **Estado novo: trocada.** `RangeView.MachineState.REPLACED`: a máquina está ativa (no raio e no limite), com o chunk carregado, e o bloco na posição é outro que não o do grupo, não é ar e expõe a capability de algum tipo carregado em algum lado (o lado nulo ou um dos seis). Um bloco diferente sem capability continua `MISSING` (sumiu). O `MachineView` ganha o id do bloco novo (`replacedBy`, nulo fora de `REPLACED`). Cor: `BRASS` na bolinha e no texto, porque `WARN` já é o chunk descarregado. O texto da linha da máquina é "#n · trocada".
5. **Atualizar (⟳).** Na máquina trocada escolhida, atualiza só ela. No grupo escolhido com alguma máquina trocada, atualiza todas as trocadas do grupo. A linha do grupo mostra "N trocada(s)" em `BRASS` na segunda linha, no lugar das exceções. É um clique só, sem confirmação, porque nada se perde. Sem a lista, o Atualizar aparece na linha Editando, antes do Desvincular, quando o alvo é uma máquina trocada.
6. **Para onde a máquina vai** (o servidor decide e confere de novo que cada máquina está trocada):
   - **Grupo inteiro para o mesmo bloco novo, e nenhum grupo desse bloco:** o próprio grupo passa a ser do bloco novo, com o mesmo id, nome, perfil, cartões e exceções.
   - **Já existe um grupo do bloco novo:** a máquina entra no primeiro grupo dele e segue o perfil dele.
   - **Não existe grupo do bloco novo, e o grupo antigo fica com outras máquinas:** nasce um grupo novo do bloco novo com uma cópia do perfil do grupo antigo, sem nome e sem cartões. As outras trocadas do mesmo lote que vão para o mesmo bloco entram nele.
   - A exceção da máquina continua. A frente é relida do bloco novo.
   - O grupo que esvazia sai, e os cartões dele caem no roteador, como no Desvincular.
7. **O alvo depois de atualizar.** Uma máquina escolhida continua escolhida (o índice não muda). Um grupo escolhido que saiu passa para o grupo de destino da primeira máquina atualizada. Um grupo que só trocou de bloco continua escolhido.
8. **O motor não muda.** Ele já move pela posição e pela capability, sem olhar o bloco do grupo. O que faltava era a tela, que mostrava a trocada como sumida e não deixava editar as faces dela.

## Protocolo

- `UpdateMachinePayload` (cliente → servidor): `containerId`, o alvo (máquina pelo índice e a posição, ou grupo pelo id e o bloco do grupo), validado como o `RevertMachinePayload` e o `SelectTargetPayload`. Vale por qualquer `RouterEditHost` (roteador, roteador à distância e Tablet).
- `RangeView.MachineView` ganha `replacedBy` (`Optional<ResourceLocation>` no buffer).
- `ModPayloads.VERSION` passa de `"17"` para `"18"`.
- Nenhuma chave de NBT nova: o grupo continua gravando `block`, agora mutável.

## Textos (chaves em `gui.wirelessautomate.router.range.*` e `gui.wirelessautomate.panel.*`)

| Uso | pt_br | en_us |
| --- | --- | --- |
| Estado | Trocada: no lugar há outra máquina | Replaced: another machine is in its place |
| Linha da máquina | trocada | replaced |
| Grupo | 1 trocada / %s trocadas | 1 replaced / %s replaced |
| Botão | Atualizar | Update |
| Dica, máquina | No lugar há: %s. Atualizar passa a máquina para o grupo desse bloco; a exceção dela continua | %s is in its place. Update moves the machine to that block's group; its exception stays |
| Dica, grupo | Atualiza as máquinas trocadas deste grupo (%s) | Updates the replaced machines in this group (%s) |
| Desvincular armado | Desvincular? | Unlink? |
| Dica do Desvincular | Clique de novo para tirar esta máquina do roteador | Click again to take this machine off the router |
| Estado sumida | A máquina sumiu: o bloco foi quebrado ou não tem mais o que mover | The machine is gone: the block was broken or has nothing left to move |
| Visor e acesso da face (`range.access.replaced`) | máquina trocada | machine replaced |
| Voltar curto, na linha Editando com três botões (`range.revert.short`) | Voltar | Revert |

## Fora

Atualizar sozinho, sem o botão. Atualizar uma máquina sumida (sem capability). Mudar o motor.
