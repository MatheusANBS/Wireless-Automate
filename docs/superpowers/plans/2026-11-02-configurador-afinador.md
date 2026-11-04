# Configurador: o Afinador (etapa 3): plano de implementação

> **Para agentes:** um subagente por tarefa, na ordem; cada tarefa compila, passa nos testes e vira um commit.

**Spec:** [`docs/superpowers/specs/2026-11-02-configurador-afinador-design.md`](../specs/2026-11-02-configurador-afinador-design.md). Restrições globais: as da etapa 1 (`2026-11-02-presa-no-grupo.md`) e da etapa 2 (`2026-11-02-alcance-etapa-2.md`), com:

- `ModPayloads.VERSION` sobe de `"43"` para `"44"` na Tarefa 1.
- A lógica de decisão dos modos (papéis por face → modos) é pura, sem classes do Minecraft, com JUnit em `src/test/java` (por exemplo `item/tuner/TunerPlan` recebendo assinaturas e papéis).
- Nada por tick; só no clique. Limites da spec (64 provas, 27 slots por face).
- Mekanism, Ars Nouveau e FTB Teams continuam opcionais (químicos e Source só pelas pontes `Chemicals`/`Sources`).

## Tarefa 1: servidor

1. A análise (leitura das capabilities por face e tipo, provas, assinaturas, papéis) e a decisão pura dos modos, com JUnit.
2. O gesto no `ConfiguratorItem.useOn` (análise, proposta pendente por jogador com prazo de 10 s, segundo clique aplica, Shift no grupo; limpar no logout e na parada), a escolha do roteador (mesma regra e ordem do modo Abrir do Vinculador: `LinkerOpen`), a aplicação pela semântica de máquina (`RouterAttached`/`RouterTargets`), os textos na action bar nos dois `lang`.
3. `ConfiguratorProposalPayload` (servidor → cliente), registrado; o cliente pode só guardar a proposta (o desenho é da Tarefa 2).
4. GameTests: fornalha vanilla (Cima Insere, Lados Insere pelo combustível, Baixo Extrai, sem duplicar), barril (armazenamento: uma face só em Armazém), uma máquina de teste só de energia, segundo clique aplica, Shift no grupo, prazo vencido não aplica, sem roteador recusa, máquina de outro jogador recusada, redes intactas.

## Tarefa 2: cliente, textos e docs

1. A pintura das faces (spec, "A pintura"), em `client/`, sem nada por quadro além de desenhar; some no prazo ou ao mirar outro bloco por 2 s.
2. Tooltip, guia do Configurador (`scripts/guide/paginas/`, dois idiomas, gerador rodado, `port_*.png` revertidas se mudarem só por bytes), as menções "sem função" fora do código (guia, README, `docs/curseforge/descricao.md`, `docs/especificacao.md`).
3. e2e: passo novo com a fornalha (clicar, captura da pintura `cfg-afinador-*` claro/pt/escuro, clicar de novo, conferir os modos) e olhar as capturas.
4. `docs/mapa/`, `docs/progresso/`.
