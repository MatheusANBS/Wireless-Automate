# Roteador de alcance, etapa 2: desvincular o grupo, copiar e colar por máquina, Atualizar sem perder cartões

Aprovado pelo dono em 02/11/2026 sobre a seção "Etapa 2" do mockup `docs/preview/alcance-presa-no-grupo.html` ("pode seguir com as próximas tarefas"). Vem depois da etapa 1 (`2026-11-02-presa-no-grupo-design.md`): a presa já é membro do grupo do bloco dela.

## 1. Desvincular o grupo inteiro

- **Onde:** na linha do grupo escolhido na lista da tela do roteador, um ✕ (o mesmo ícone do Desvincular da máquina). Também no grupo "×0" (sem máquina), que só sai por aqui.
- **Dois cliques:** o primeiro arma o botão, que vira "Desvincular N?" em vermelho (N = máquinas remotas do grupo; no "×0", "Apagar?"); o segundo confirma; tirar o mouse desarma. Como o Desvincular da máquina e o Remover do Tablet.
- **O que faz** (`UnlinkGroupPayload(containerId, groupId, block)`; o servidor confere que o grupo com esse id existe e é desse bloco, senão recusa sem mudar nada, o que cobre o id reaproveitado):
  - desvincula todas as máquinas remotas do grupo pelos métodos do roteador (índice do mixin, caches e rotas em dia);
  - **grupo sem a presa:** o grupo sai;
  - **grupo com a presa:** o grupo fica, só com a presa, com o perfil zerado (`new MachineConfig()`, sem nome) e a presa sem exceção; a presa nunca é desvinculada;
  - os **Cartões de Filtro** do grupo (o grupo que sai e o grupo da presa, que fica sem cartões) vão para o inventário de quem clicou; o que não couber cai aos pés dele;
  - aviso numa linha (`PanelNoticePayload`, sem Desfazer): "Desvinculadas N máquinas · M cartões devolvidos" (singular e plural; sem máquinas ou sem cartões, só a parte que houve; no "×0" sem nada, "Grupo apagado").
- **Depois:** o alvo da tela vira a presa (se o grupo era o dela) ou o primeiro alvo válido (`validTarget`).

## 2. Copiar e colar o perfil por máquina

- **A cópia é do jogador, no servidor** (`menu/MachineClipboard`, por UUID): uma `MachineConfig` (cópia), o bloco e o nome de origem ("Fornalha #2", "presa", "Perfil: Fornalha"). Vale em qualquer roteador que o jogador possa editar, até ele sair do mundo (apagada no logout e na parada do servidor). Não vai para o item nem para o NBT.
- **Copiar** (`MachineCopyPayload(containerId)`): copia a configuração que vale para o alvo escolhido: numa máquina, a exceção ou o perfil; na presa, idem; no grupo, o perfil. Os lados são `RemoteSide` (relativos à frente da máquina) dos dois lados, então colar não converte nada. Os cartões **não** entram (são do grupo).
- **Colar** (`MachinePastePayload(containerId, wholeGroup)`), no alvo escolhido:
  - **máquina remota:** como exceção da máquina; se ficar igual ao perfil, a exceção some;
  - **presa:** pela semântica de máquina da etapa 1 (sozinha no grupo muda o perfil; com outras, exceção);
  - **grupo**, ou máquina/presa com `wholeGroup` (Shift + clique, Shift + Ctrl+V): no perfil do grupo; as exceções que ficarem iguais somem; as outras ficam e são contadas no aviso;
  - **redes:** a mesma regra do Tablet (`PresetApplier`/`PresetPaste.canLeave`): só entra rede que o jogador pode usar, e a face só sai de uma rede que ele edita; o resto fica como estava e o aviso conta;
  - outro bloco é permitido (fornalha → alto-forno): o aviso diz de onde veio;
  - **Desfazer** no aviso (como o "Tirar redes": `menu/MachinePasteUndo`, só na memória, um por jogador, apagado ao colar de novo, ao fechar o menu e ao sair): volta a exceção ou o perfil de antes, só se o alvo ainda está como o colar deixou; vale uma vez, no mesmo menu e roteador.
- **Tela:** na linha escolhida da lista (máquina, presa ou grupo), ⧉ Copiar e ⎘ Colar (o ⎘ só com cópia; dica "Colar o perfil de <origem> · Shift: no grupo inteiro"). Ctrl+C e Ctrl+V na tela do roteador (fora de um campo de texto) fazem o mesmo; Shift + Ctrl+V no grupo inteiro. No rodapé da lista, "Cópia: <origem>" com ✕ para esquecer (`MachineCopyPayload` com `forget`). O snapshot do roteador leva o nome da cópia do jogador (texto curto, só com a tela aberta, só quando muda).
- **Sem o Upgrade de Alcance** (sem lista): Ctrl+C e Ctrl+V valem na presa.

## 3. Atualizar sem perder cartões

Hoje o Atualizar (`RemoteMachines.regroup`) passa a máquina trocada para um grupo novo com a cópia do perfil e sem cartões, e o grupo que esvazia solta os cartões no chão, sem aviso.

- **Grupo que esvazia e tem exatamente um grupo de destino criado pelo próprio Atualizar:** os cartões vão junto para ele (o perfil já ia).
- **Senão** (o grupo não esvazia: os cartões ficam com ele; ou esvazia para um grupo que já existia, ou para vários): os cartões do grupo que saiu vão para o inventário de quem clicou (o que não couber, aos pés dele).
- **Aviso** ao clicar: "Atualizada N máquina(s)" e, quando houver, "· M cartões devolvidos".

## Custo

Nada por tick nem por visita. A cópia é um `MachineConfig` por jogador que copiou, na memória. O snapshot ganha um texto curto (o nome da cópia), mandado só quando muda. Payloads novos: `UnlinkGroupPayload`, `MachineCopyPayload`, `MachinePastePayload`; `ModPayloads.VERSION` 41 → 42.

## Fora desta etapa

A função nova do Configurador (etapa 3) e os menores (etapa 4: o Vinculador que desvincula num clique e solta cartões, o clique ignorado quando a lista mudou, a máquina trocada que segue recebendo até o Atualizar).
