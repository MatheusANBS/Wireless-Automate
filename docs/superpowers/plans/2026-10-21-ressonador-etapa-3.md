# Ressonador: rebalanceamento, etapa 3 (JEI, guia e fechamento) · plano

> Um subagente por tarefa. **Depende das etapas 1, 1b e 2.** Spec: `docs/superpowers/specs/2026-10-20-ressonador-rebalanceamento-design.md` ("JEI e guia") e a de 21/10 (cascata).

## Restrições globais
As das etapas anteriores. Guia: edite `scripts/guide/gerar_guia.py` (nunca os `.md`), nos dois idiomas, só "como operar" (nada de config, ms/tick ou detalhes internos); o gerador reescreve PNGs de imagens por desvio do PIL: confira e restaure os que não mudaram. JEI só no pacote `compat/jei/`.

### Task 1: JEI
- Receitas novas aparecem pelo tipo de bancada; os Afinados e o Pó (tipo `tune_crystal`), o Refazer (`recast_crystal`, que é `CustomRecipe` e não aparece sozinho: registre uma categoria "Refazer o cristal" com um exemplo por tipo de cristal), a cascata (liga no alto-forno pelo nativo), os Corações e a Moldura; categoria Recarga com o catalisador por tier (redstone/Pó/Pó Carregado) e a perda de integridade; informação por tier (`ResonatorJei`) com o Afinado exigido, a integridade, a vida do cristal por tier, a interferência, o Coração para subir e (Ultimate) o Eco. Os itens dos tiers do Allthemodium só aparecem com o mod (esconder do JEI os Afinados, Pós Carregados e Corações dos tiers não carregados).
- e2e: capturas `ressonador-jei-*` novas (receitas dos Afinados, do Refazer, da cascata e dos Corações). `WA_E2E_ONLY=ressonador` OK.
- Commit: `Ressonador: JEI do rebalanceamento`.

### Task 2: Guia
- Página `signal-quartz`: Afinados, integridade, rachado, refazer, Pó e Pó Carregado, a cascata (tabela dos 10 itens e do Coração por tier). Página `signal-resonator`: catalisador por tier, vida do cristal por tier, interferência, Eco, o Coração para subir o Núcleo e a tabela nova de tiers (geração máxima). O e2e abre as páginas nos dois idiomas e salva `guia-<página>[-pt][-N].png`.
- Commit: `Ressonador: guia do rebalanceamento`.

### Task 3: Fechamento
- `docs/progresso.md` (tudo pronto, próximo passo a definir com o dono, histórico), `CLAUDE.md`, checagem final: `./gradlew build runGameTestServer runGameTestServerChemicals runGameTestServerSource runGameTestServerAllthemodium`, `./scripts/e2e.sh` completo (OK) e as folhas de contato das capturas do Ressonador.
- Commit: `Ressonador: fechamento do rebalanceamento`.
