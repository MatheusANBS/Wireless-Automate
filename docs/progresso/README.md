# Progresso

Comece por aqui para continuar o trabalho. Este arquivo é curto de propósito: diz onde o projeto está e para onde vai, e aponta para o resto.

## Agora

- **Versão atual:** 2.15.0 (04/11/2026): presa no grupo, alcance com desvincular grupo e copiar/colar por máquina, Ctrl + clique do Vinculador (sem a área), o Afinador do Configurador, ícone de grupo no Tablet. Protocolo 49.
- **Publicação:** a 2.14.0 está no CurseForge; a 2.15.0 tem release no GitHub com o jar e espera o dono subir no CurseForge e no Modrinth.
- **Em andamento:** ramo `ccr-52410279-si07pg`: o destino seletivo no motor e as etapas 1 a 4 do roteador de alcance (a presa no grupo; desvincular o grupo, copiar e colar por máquina, Atualizar com cartões; o Afinador do Configurador, com as correções da auditoria pré-PR de 03/11; os menores das revisões) e o Ctrl + clique do Vinculador, feitos; falta o teste à mão do dono. O ramo `mc/1.20.1-forge` (porte) está parado à espera do ok do dono (`docs/portes.md`).
- **Desenhado em 10/11, sem código:** a **Lixeira** (spec e mockup da tela aprovados, plano da etapa 1 em `docs/superpowers/plans/2026-11-10-lixeira-etapa-1.md`) e a **Doca de Padrões**, a peça de crafting da proposta de integração com o AE2 e o RS2 (`docs/superpowers/specs/2026-11-10-ae2-rs2-proposta.md`: mecânica, alcance e arte aprovados). Modelos e GIFs em `docs/preview/`. A identidade ganhou a regra dos blocos que trabalham (casco de porcelana, miolo escuro com luz na cor do tier). Duas correções de defeitos esperam merge no ramo `correcoes-renomear-e-float`.

## Próximo passo

1. O dono testa à mão as etapas 1 a 4 e o Ctrl + clique do Vinculador (listas nas linhas de 02/11 do histórico) e decide o merge.
2. Reidealizar as telas do Tablet do zero (criação de redes etc.), mockup primeiro para o ok do dono.
3. Subir a 2.15.0 no CurseForge e no Modrinth; refazer as imagens do Vinculador na vitrine (sem a área) e a do livro (ver `pendencias.md`).
4. Os menores que sobraram do alcance (`pendencias.md`, seção "Roteador de alcance").
5. **Lixeira:** executar o plano da etapa 1 (skill `wa-feature`, um subagente por tarefa) quando o dono der o ok; depois a etapa 2 (o anel, o cristal e o laço engolindo). **Doca de Padrões:** conferir os pontos de API listados na proposta e escrever a spec.

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
