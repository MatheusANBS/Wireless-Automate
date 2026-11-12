# Progresso

Comece por aqui para continuar o trabalho. Este arquivo é curto de propósito: diz onde o projeto está e para onde vai, e aponta para o resto.

## Agora

- **Versão atual:** 2.17.0 (12/11/2026, fechada no ramo `ccr-71b175d3-weymk6`, sem tag nem release ainda): a **Doca de Padrões** (AE2 e Refined Storage 2, com os addons do AE2 do ATM10 e os químicos pelo Applied Mekanistics ou pela integração do RS2 com o Mekanism), a remota quebrada que sai sozinha do roteador de alcance e os mínimos folgados das dependências opcionais. Protocolo 51 (a 2.16.0 tinha 49), então servidor e clientes precisam da mesma versão.
- **Publicação:** a 2.14.0 está no CurseForge; no GitHub a última release é a v2.15.0. A 2.16.0 tem changelog no unificado mas não tem tag nem release no GitHub. A 2.17.0 tem o jar em `build/libs/` e o texto em `docs/curseforge/changelog-2.17.0.md`, à espera do ok do dono para o merge, a tag e a release.
- **Em andamento:** nada no código. A máquina faladeira (A13 do backlog da `wa-performance`) espera um cenário de bench antes da decisão. O ramo `mc/1.20.1-forge` (porte) está parado à espera do ok do dono (`docs/portes.md`).
- **Doca de Padrões:** etapas 1 a 4 prontas e na 2.17.0 (planos em `docs/superpowers/plans/2026-11-1*-doca-de-padroes-etapa-*.md`, spec `docs/superpowers/specs/2026-11-10-doca-de-padroes-design.md` com todos os ajustes e medições). Falta o teste à mão do dono no jogo com o AE2 e o RS2 (lista em `pendencias.md`).
- **Desenhado em 10/11, sem código:** a **Lixeira** (spec e mockup da tela aprovados, plano da etapa 1 em `docs/superpowers/plans/2026-11-10-lixeira-etapa-1.md`) e a **Doca de Padrões**, a peça de crafting da proposta de integração com o AE2 e o RS2 (`docs/superpowers/specs/2026-11-10-ae2-rs2-proposta.md`: mecânica, alcance e arte aprovados; APIs conferidas no código; spec em `docs/superpowers/specs/2026-11-10-doca-de-padroes-design.md`, com o mockup da tela em `docs/preview/doca-de-padroes-tela.html` à espera do ok). Modelos e GIFs em `docs/preview/`. A identidade ganhou a regra dos blocos que trabalham (casco de porcelana, miolo escuro com luz na cor do tier). Duas correções de defeitos esperam merge no ramo `correcoes-renomear-e-float`; foram feitas sobre a 2.15.0 e conferidas contra a 2.16.0 (os arquivos delas não mudaram e o ramo junta sem conflito; os testes não foram rodados de novo). O plano da Lixeira também foi escrito sobre a 2.15.0 e segue valendo: o protocolo continua 49 e os números de linha citados não mudaram.

## Próximo passo

1. Publicar a 2.17.0 com o ok do dono: merge do ramo `ccr-71b175d3-weymk6` no main, tag `v2.17.0` e release no GitHub com `build/libs/wirelessautomate-2.17.0.jar`; depois, o dono sobe no CurseForge e no Modrinth com o texto de `docs/curseforge/changelog-2.17.0.md` (a 2.16.0 nunca ganhou tag nem release; decidir se ganha).
2. O dono testa à mão a Doca no jogo com o AE2 e o RS2 (a lista em `pendencias.md`, com um padrão do Mekanism de item, fluido e químico, a receita e a Doca + cartão na bancada e no JEI) e as etapas 1 a 4 do alcance e o Ctrl + clique do Vinculador (linhas de 02/11 do histórico).
3. O e2e rápido inteiro passou de 600 s (o padrão do `scripts/e2e.sh`) com as suítes da Doca e da Tela: subir o tempo-limite padrão ou rodar em partes. A descrição do `neoforge.mods.toml` sai com os acentos em mojibake no jar (provavelmente o `gradle.properties` lido em ISO-8859-1; já era assim na 2.16.0).
4. Reidealizar as telas do Tablet do zero (criação de redes etc.), mockup primeiro para o ok do dono. Refazer as imagens do Vinculador na vitrine (sem a área) e a do livro, e uma imagem da Doca para o CurseForge (`wa-curseforge-imagens`).
5. **Lixeira:** executar o plano da etapa 1 quando o dono der o ok. Higiene: a varredura de 12/11 lista os planos já mesclados (o livro, o alcance, o Afinador, a presa e a etapa 4 da Doca) e o protótipo do livro, para apagar com o ok do dono.

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
