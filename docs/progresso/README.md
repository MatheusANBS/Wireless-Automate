# Progresso

Comece por aqui para continuar o trabalho. Este arquivo é curto de propósito: diz onde o projeto está e para onde vai, e aponta para o resto.

## Agora

- **Versão atual:** 2.18.0 (13/11/2026, no `main`): o **Vinculador de um dono** (a máquina de outro roteador, vinculada ou presa, é recusada; o Ctrl + clique pega só as do mesmo bloco que se tocam com a clicada, também na Doca, e desvincula só esse grupo) e o **cubo do alcance do roteador** no mundo com o Vinculador na mão. Protocolo 52 (a 2.17.0 tinha 51), então servidor e clientes precisam da mesma versão. Testada à mão pelo dono em 13/11. O jar fica em `build/libs/` e o texto em `docs/curseforge/changelog-2.18.0.md`; faltam a tag e a release no GitHub e a publicação no CurseForge e no Modrinth.
- **Publicação:** a 2.17.0 está no `main`, com a tag `v2.17.0`, e no CurseForge.
- **Parado:** a máquina faladeira (A13 do backlog da `wa-performance`) espera um cenário de bench; o ramo `mc/1.20.1-forge` (porte) espera o ok do dono (`docs/portes.md`); a **Lixeira** (plano da etapa 1 em `docs/superpowers/plans/2026-11-10-lixeira-etapa-1.md`) espera o ok para executar; duas correções de defeitos esperam merge no ramo `correcoes-renomear-e-float`.

## Próximo passo

1. Publicar a 2.18.0: a tag `v2.18.0` e a release no GitHub com `build/libs/wirelessautomate-2.18.0.jar`; o dono sobe no CurseForge e no Modrinth com o texto de `docs/curseforge/changelog-2.18.0.md`. O dono ainda testa a Doca no jogo com o AE2 e o RS2 (`pendencias.md`).
2. O e2e rápido inteiro passou de 600 s (o padrão do `scripts/e2e.sh`) com as suítes da Doca e da Tela: subir o tempo-limite padrão ou rodar em partes. A descrição do `neoforge.mods.toml` sai com os acentos em mojibake no jar.
3. Reidealizar as telas do Tablet do zero (criação de redes etc.), mockup primeiro para o ok do dono. Refazer as imagens do Vinculador na vitrine e a do livro, e uma imagem da Doca para o CurseForge (`wa-curseforge-imagens`).
4. **Lixeira:** executar o plano da etapa 1 quando o dono der o ok. Higiene: a varredura de 12/11 lista os planos já mesclados (o livro, o alcance, o Afinador, a presa e a etapa 4 da Doca) e o protótipo do livro, para apagar com o ok do dono.

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
