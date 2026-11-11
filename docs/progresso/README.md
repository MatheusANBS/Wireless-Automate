# Progresso

Comece por aqui para continuar o trabalho. Este arquivo é curto de propósito: diz onde o projeto está e para onde vai, e aponta para o resto.

## Agora

- **Versão atual:** 2.16.0 (08/11/2026): o balanceamento 6 (fluido e químico ×4, Source ×10, capacidades de uma hora, migração 6 da config) e as três correções do motor da auditoria de 08/11 (o cursor numa saída da máquina, o tanque seletivo, o `isLoaded` das remotas). Protocolo 49 (o mesmo da 2.15.0).
- **Publicação:** a 2.14.0 está no CurseForge; no GitHub a última release é a v2.15.0 (a 2.14.0 e a 2.15.1 não têm release lá, apesar do que o histórico dizia). A 2.16.0 tem release com o jar e espera o dono subir no CurseForge e no Modrinth.
- **Em andamento:** nada no código. A máquina faladeira (A13 do backlog da `wa-performance`) espera um cenário de bench antes da decisão. O ramo `mc/1.20.1-forge` (porte) está parado à espera do ok do dono (`docs/portes.md`).
- **Doca de Padrões, etapas 1 (AE2) e 2 (RS2), prontas em 11/11** no ramo `ccr-71b175d3-weymk6`, à espera do teste à mão e do merge (planos `docs/superpowers/plans/2026-11-10-doca-de-padroes-etapa-1.md` e `2026-11-11-doca-de-padroes-etapa-2.md`, spec atualizada com os ajustes da execução das duas). O resultado volta direto à rede do provedor (ME ou RS2), e a tela avisa a trava "até o resultado" do AE2. Protocolo 50 no ramo.
- **Desenhado em 10/11, sem código:** a **Lixeira** (spec e mockup da tela aprovados, plano da etapa 1 em `docs/superpowers/plans/2026-11-10-lixeira-etapa-1.md`) e a **Doca de Padrões**, a peça de crafting da proposta de integração com o AE2 e o RS2 (`docs/superpowers/specs/2026-11-10-ae2-rs2-proposta.md`: mecânica, alcance e arte aprovados; APIs conferidas no código; spec em `docs/superpowers/specs/2026-11-10-doca-de-padroes-design.md`, com o mockup da tela em `docs/preview/doca-de-padroes-tela.html` à espera do ok). Modelos e GIFs em `docs/preview/`. A identidade ganhou a regra dos blocos que trabalham (casco de porcelana, miolo escuro com luz na cor do tier). Duas correções de defeitos esperam merge no ramo `correcoes-renomear-e-float`; foram feitas sobre a 2.15.0 e conferidas contra a 2.16.0 (os arquivos delas não mudaram e o ramo junta sem conflito; os testes não foram rodados de novo). O plano da Lixeira também foi escrito sobre a 2.15.0 e segue valendo: o protocolo continua 49 e os números de linha citados não mudaram.

## Próximo passo

1. Subir a 2.16.0 no CurseForge e no Modrinth (jar em `build/libs/`, texto em `docs/curseforge/changelog-2.16.0.md`); conferir no jogo que um mundo antigo migra a config na primeira carga.
2. O dono testa à mão as etapas 1 a 4 do alcance e o Ctrl + clique do Vinculador (listas nas linhas de 02/11 do histórico), já no main.
3. Reidealizar as telas do Tablet do zero (criação de redes etc.), mockup primeiro para o ok do dono.
4. Refazer as imagens do Vinculador na vitrine (sem a área) e a do livro (ver `pendencias.md`). Os menores que sobraram do alcance (`pendencias.md`, seção "Roteador de alcance").
5. **Lixeira:** executar o plano da etapa 1 quando o dono der o ok. **Doca de Padrões:** as etapas 1 (AE2) e 2 (RS2) estão prontas no ramo `ccr-71b175d3-weymk6` (todas as runs e o e2e OK): o dono testa à mão no jogo e decide o merge; depois vem o plano da etapa 3 (a arte animada: o circuito por tier, o giroscópio, o pacote e os fragmentos no renderer, pela `wa-shaders`).

## Os arquivos

| Arquivo | O que tem |
| --- | --- |
| [pendencias.md](pendencias.md) | O que falta, por área, e o que depende do dono |
| [estado.md](estado.md) | Cada funcionalidade: pronta ou não, e em que versão entrou |
| [decisoes.md](decisoes.md) | As decisões em vigor, do dono e do código |
| [historico/](historico/) | Uma linha por sessão, um arquivo por mês ([2026-11](historico/2026-11.md), [2026-10](historico/2026-10.md)) |

O que mudou em cada versão está no changelog unificado (`docs/curseforge/changelog-curseforge-<versão>.md`); onde cada coisa fica no código, no [mapa](../mapa/).

## Como atualizar (ao fim de cada sessão)

1. **Histórico:** uma linha no arquivo do mês (`historico/AAAA-MM.md`, a mais nova em cima; mês novo, arquivo novo). O que foi feito, o que foi verificado (testes, e2e) e o que ficou. Curta: o detalhe está no commit.
2. **Pendências:** o que ficou para depois entra em `pendencias.md`, na área certa; o que foi feito **sai** de lá.
3. **Estado:** se uma funcionalidade entrou, mudou de estado ou saiu numa versão, a linha dela em `estado.md`.
4. **Decisões:** decisão nova do dono ou regra nova do código vai para `decisoes.md`, no presente.
5. **Este arquivo:** reescreva "Agora" e "Próximo passo" (até 5 itens). Nada de acumular: o que passou vai para o histórico.
