# Roteador de alcance, etapa 2: plano de implementação

> **Para agentes:** um subagente por tarefa, na ordem. Cada tarefa termina compilando, com os testes passando, e num commit próprio.

**Spec:** [`docs/superpowers/specs/2026-11-02-alcance-etapa-2-design.md`](../specs/2026-11-02-alcance-etapa-2-design.md). Leia inteira. Base: a etapa 1 ([spec](../specs/2026-11-02-presa-no-grupo-design.md)), já no ramo.

## Restrições globais

As da etapa 1 (`docs/superpowers/plans/2026-11-02-presa-no-grupo.md`, seção "Restrições globais") valem aqui, com estas diferenças:

- `ModPayloads.VERSION` sobe de `"41"` para `"42"` uma vez, na Tarefa 1.
- Vincule e desvincule só pelos métodos do `RouterBlockEntity`; o grupo e os cartões mudam só pelo roteador, que avisa o motor.
- Entregar itens ao jogador: `player.getInventory().add(stack)` e, com sobra, `player.drop(stack, false)`; nunca soltar na posição do roteador.
- Toda validação no servidor (`RouterEditHost`, mesmo `containerId`, alvo existente, grupo pelo id **e** pelo bloco).
- Textos novos nos dois `lang`, com singular e plural quando há número.
- Telas: classes só em `client/`; `RangeList` está em 496 linhas e `PanelLayout` em 634 (aviso): extraia (por exemplo os botões da linha para o `RangeRowButtons`, ou uma classe nova para o rodapé da cópia) em vez de crescer.

## Tarefa 1: servidor

1. `RouterRange`/`RouterBlockEntity.unlinkGroup(groupId)` (devolve as máquinas desvinculadas e os cartões tirados), com a regra da presa da spec; `UnlinkGroupPayload` + handler (cartões ao jogador, aviso, `validTarget`).
2. Atualizar: `RemoteMachines.regroup` passa os cartões ao grupo novo no caso da spec e devolve os demais; o handler do `UpdateMachinePayload` entrega ao jogador e manda o aviso.
3. `menu/MachineClipboard` (por UUID, limpo no logout e na parada; registre nos eventos que já limpam o `IdleNetworkUndo`), `MachineCopyPayload(containerId, forget)`, `MachinePastePayload(containerId, wholeGroup)`, `menu/MachinePasteUndo` e o Desfazer pelo `PanelNoticePayload` (nova opção de `Undo`), com a regra das redes do `PresetApplier`.
4. O nome da cópia no snapshot do roteador (por jogador, só quando muda).
5. `VERSION` = `"42"`.
6. GameTests: desvincular grupo (sem a presa, com a presa, "×0", id reaproveitado/bloco errado recusado, cartões no inventário e a sobra no chão aos pés do jogador); Atualizar (cartões vão junto; cartões devolvidos; aviso); copiar/colar (máquina → máquina como exceção, igual ao perfil some, presa sozinha muda perfil, `wholeGroup` no perfil com exceções contadas, rede proibida não entra, outro bloco, Desfazer e Desfazer recusado quando o alvo mudou, cópia some no logout).

## Tarefa 2: tela

1. O ✕ do grupo com dois cliques ("Desvincular N?" / "Apagar?") na lista; ⧉ e ⎘ nas linhas escolhidas (máquina, presa, grupo); Ctrl+C, Ctrl+V e Shift + Ctrl+V (fora de campo de texto; também sem o Upgrade de Alcance, na presa); o rodapé "Cópia: <origem>" com ✕; os avisos com Desfazer.
2. Lang nos dois idiomas.
3. e2e: passos novos para desvincular grupo (com e sem a presa), copiar/colar com Desfazer e o Atualizar com o aviso; capturas `range-q*` (claro, pt, escuro); olhe as capturas.

## Tarefa 3: docs

Especificação (tela do roteador e alcance), mapas, guia do Roteador (desvincular grupo, copiar e colar por máquina), `docs/progresso/`.
