# Tier Esmeralda e tiers do Allthemodium: plano

**Spec:** [`docs/superpowers/specs/2026-10-08-tiers-esmeralda-e-atm-design.md`](../specs/2026-10-08-tiers-esmeralda-e-atm-design.md).

## Restrições globais

- Código, comentários, commits e docs em português; traduções em `en_us.json` e `pt_br.json`, sempre as duas.
- Nenhum tipo do Allthemodium nem do All The Tweaks no código (não há API para usar): só `ModList`, receitas condicionais e tags `c:`. Nada de `compileOnly`.
- O tier continua gravado pelo nome (blockstate, `NodeIndex`, `StorageSavedData`); os nomes `basic`, `advanced`, `elite` e `ultimate` não mudam, nem as chaves de config existentes.
- `ModPayloads.VERSION` passa a `"9"`.
- Texturas, modelos e blockstates por `scripts/textures/gerar_texturas.py`; guia por `scripts/guide/gerar_guia.py`. Nunca editar os gerados à mão.
- Performance: nada muda no motor (a vazão por tier já é lida por índice em arrays).
- Verificação: `./gradlew build runGameTestServer runGameTestServerChemicals runGameTestServerSource runGameTestServerAllthemodium` e o e2e (a tela do roteador e dos armazenamentos mostram o tier). Um jogo por vez.
- Commits terminam com a linha de co-autoria.

## Tarefas

1. **Escada de tiers.** `block/TierLadder` (lógica pura, JUnit): próximo e anterior pulando os tiers cujo mod não carregou. `RouterTier` com os oito tiers, `requiredMod`, `loaded()`, `next()` e `previous()`. Alcance padrão 64/512/0..., `crossDimension` a partir da Esmeralda.
2. **Valores.** `ResourceType` e `StorageKind` com os oito valores da spec; `ResourceTypeTest` e o teste do tooltip em `RecipeGameTests` acompanham.
3. **Itens e telas.** Cartões dos tiers novos (`ModItems` já itera o enum), `TierCoreItem` com `previous()` e a linha "precisa do Allthemodium", aba criativa sem os tiers não carregados, JEI pelo `next()`, `GuiPaint.tierColor`, nomes nos dois `lang`, `ModPayloads.VERSION`.
4. **Visual.** Paletas e cartão de quatro marcas no script; modelos, blockstates e modelos de item gerados para os oito tiers; folha de sprites.
5. **Receitas.** As da spec, com as condições `neoforge:mod_loaded`/`neoforge:not`/`neoforge:and`.
6. **Testes.** GameTests na run comum (Elite → Esmeralda → Ultimate, upgrade na bancada, Esmeralda entre dimensões pela config, receitas do ATM ausentes). Run `gameTestServerAllthemodium` com `AtmGameTests` (escada de oito degraus, receitas do ATM e a do Ultimate com fragmento carregadas, a do Ultimate vanilla não). CI com o passo novo.
7. **Fechamento.** Guia (tabela de tiers e cartões), `CLAUDE.md`, `docs/especificacao.md`, `docs/progresso.md`, e2e.
