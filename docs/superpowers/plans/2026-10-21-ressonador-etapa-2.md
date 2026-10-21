# Ressonador: rebalanceamento, etapa 2 (interferência, Eco e tela) · plano

> Um subagente por tarefa (`wa-feature`). **Depende das etapas 1 e 1b.** Spec: `docs/superpowers/specs/2026-10-20-ressonador-rebalanceamento-design.md` seções 4, 5 e "Tela do Núcleo" (a de 21/10 prevalece onde divergem).

## Restrições globais

As da etapa 1 (idioma, os dois `lang`, chaves existentes não mudam, performance, JUnit e GameTest com `timeoutTicks` ≤ 300 e config em lote próprio com `try/finally`, texturas só pelo gerador, `wa-modular`: servidor até 600 linhas, `ResonanceCoreBlockEntity` já passa de 650 e não cresce, `wa-comentarios`, commits **sem** linhas de atribuição, sem push, verificação com as quatro runs). Em especial:
- Chaves novas de config em `resonator`: `interferenceRadius` (padrão 32, 0 desliga, 0..256), `interferencePenalty` (0,15; 0..1), `interferenceMax` (0,60; 0..1), `ecoBonus` (0,05; 0..1), `ecoMax` (0 = sem teto). Chave de NBT nova `eco` (long) no Núcleo; componente `resonator_eco` no item do Núcleo (loot table com `copy_components`).
- `ModPayloads.VERSION` vai de `"25"` a `"26"` na Task 3 (o `ResonatorStatePayload` muda); antes disso continua `"25"`. Se o ramo já tiver subido o protocolo (o do Tablet foi a 26), use o próximo número livre e diga no relatório.
- Performance: a interferência trabalha só ao montar, desmontar, carregar e descarregar um Núcleo (varredura linear dos Núcleos carregados da dimensão, no `ResonatorManager`); o fator fica guardado no Núcleo e o lote só o lê; nada por tick; o Eco só quando um cristal é queimado; tela fechada não manda nada.

### Task 1: Interferência
- Lógica pura em classe nova (`ResonatorInterference`, com JUnit): dado o conjunto de posições dos outros Núcleos montados e carregados da mesma dimensão, calcula a perda (`min(interferenceMax, n × interferencePenalty)`, n = Núcleos a no máximo `interferenceRadius` blocos pela distância de Chebyshev) e a distância do mais perto (para a tela). 0 no raio desliga.
- `ResonatorManager`: ao montar, desmontar, carregar e descarregar um Núcleo, refaz o fator dos Núcleos afetados (só os dentro do raio). Vizinho em chunk descarregado não conta; ao carregar, a conta é refeita. O fator multiplica a eficiência **depois** do teto do Sintonizador (`ResonatorBatch`). Guardar `interferenceLoss` (float/permille) e `nearest` no Núcleo, fora do NBT salvo.
- `/wa resonator <pos> info` mostra a perda e o vizinho.
- GameTests (template `empty11`, dois Núcleos): sem vizinho = 0; vizinho a 24 blocos tira 15%; três vizinhos batem no teto de 60%; fora do raio nada; desmontar o vizinho devolve; `interferenceRadius = 0` desliga (config em lote próprio).
- Commit: `Ressonador: interferência entre Núcleos`.

### Task 2: Eco Ressonante (só Ultimate)
- Slot **Eco** novo no Núcleo Ultimate (no `CoreInventory`/menu; só aceita Afinado Ultimate rachado): o rachado posto ali é consumido na hora e soma 1 de Eco. A Porta de Cristais também entrega ao Eco o rachado Ultimate que chega pelo funil. Fator = `1 + eco × ecoBonus` sobre a geração base (`ecoMax` > 0 limita o Eco), saturando em `Long.MAX_VALUE`; o buffer acompanha (capacidade = geração máxima com Eco × 200 ticks). `eco` (long) no NBT do Núcleo, no item ao quebrar (componente `resonator_eco`, `copy_components` na loot table) e de volta ao colocar. Núcleo de outro tier ignora o slot.
- Lógica pura em classe nova (`CoreEco`, JUnit): fator, saturação, teto. O estado vai em classe de pacote nova, não no `ResonanceCoreBlockEntity`.
- GameTests: queimar soma 1 e o fator sobe 5%; só o Afinado Ultimate rachado vale; teto `ecoMax`; saturação; quebrar e colocar preserva o Eco; a Porta entrega ao Eco; Núcleo não Ultimate recusa.
- Commit: `Ressonador: Eco Ressonante no Ultimate`.

### Task 3: Tela do Núcleo, payload e câmara
- `ResonatorStatePayload` ganha a perda da interferência (permille), a distância do vizinho mais perto, o Eco e o bônus do Eco; `ModPayloads.VERSION` sobe (ver restrições); a integridade vai pelo próprio `ItemStack` dos slots. A tela (352 × 244, sem mudar o tamanho): barra fina (16 × 2 px) de integridade sob cada câmara (vermelha no rachado) e dica "Integridade 5 de 8" / "Rachado: refaça com 4 Cristais de Sinal"; rótulo do slot do catalisador por tier ("Redstone", "Pó de Ressonância" ou o Pó Carregado do tier; redstone antiga num Núcleo alto: "gasta até acabar"); linha "Interferência: nenhuma" ou, em laranja, "−15% · Núcleo a 24 blocos"; no Ultimate, caixa do Eco com o slot, "Eco 10 · +50%" e a dica. Tema claro e escuro; todo texto por `GuiText`; textos nos dois `lang`.
- O `ChamberView` do BER já tem os códigos de rachado (etapa 1): confira que a câmara mostra o cristal rachado escuro, sem recarga.
- Capturas e2e novas (`ressonador-tela-*`: interferência, rachado, Eco no Ultimate, nos dois temas e em pt) e `ressonador-camara-rachado`; o e2e `WA_E2E_ONLY=ressonador` precisa dar OK (`./scripts/e2e.sh`, Xvfb) e as capturas existentes do Ressonador continuam batendo. `GuiText.clipCount()==0`.
- GameTests do menu/payload (`ResonatorMenuGameTests`): campos novos, `pollState` só quando muda, o slot do Eco.
- Commit: `Ressonador: tela do Núcleo com interferência, integridade e Eco`.

### Task 4: Fechamento da etapa 2 (e das etapas 1 e 1b)
- `docs/progresso.md` (estado, histórico com os números de testes das etapas 1, 1b e 2), `CLAUDE.md` (mapa: `CoreMath`, `CoreYield`, `CrystalIntegrity`, `CoreCatalyst`, `CatalystRule`, itens novos, `ResonatorInterference`, `CoreEco`, config e chaves novas, protocolo), `docs/especificacao.md` onde o Ressonador é descrito. Sprites e itens novos entram na tabela de `docs/pacote-de-design.md` (`identidade-visual.md` se citar itens). Verificação completa das quatro runs, `varrer_perf.py`, `varrer.py`, `medir.py`.
- Commit: `Ressonador: fechamento da etapa 2`.
