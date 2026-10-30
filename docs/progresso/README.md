# Progresso

Comece por aqui para continuar o trabalho. Este arquivo é curto de propósito: diz onde o projeto está e para onde vai, e aponta para o resto.

## Agora

- **Versão atual:** 2.13.0 (30/10/2026), publicada no GitHub; os upgrades do Baú (Mesa de Trabalho e Compactação). Protocolo 40.
- **Publicação:** a 2.13.0 está no CurseForge e no Modrinth (`modrinth.com/mod/wireless-automate`, em análise desde 30/10).
- **Em andamento:** nada em ramo aberto. O ramo `mc/1.20.1-forge` (porte) está parado à espera do ok do dono (`docs/portes.md`).

## Próximo passo

1. Correções pequenas e já confirmadas no código: os comandos de imagem da Tela em inglês, o singular de "1 máquinas" no Vinculador, o código morto do Reparador e a limpeza do `gerar_texturas.py` que apaga `gui/tv` (detalhes em [pendencias.md](pendencias.md)).
2. Dividir o `NodePanel` (822 linhas), antes da próxima mudança no painel.
3. A próxima funcionalidade, a escolher com o dono: a lixeira do mod, o fim do roteador de alcance (pausar grupo, perfis no Configurador, página no guia) ou os atalhos de AE2/RS2.

## Os arquivos

| Arquivo | O que tem |
| --- | --- |
| [pendencias.md](pendencias.md) | O que falta, por área, e o que depende do dono |
| [estado.md](estado.md) | Cada funcionalidade: pronta ou não, e em que versão entrou |
| [decisoes.md](decisoes.md) | As decisões em vigor, do dono e do código |
| [historico/](historico/) | Uma linha por sessão, um arquivo por mês ([2026-10](historico/2026-10.md)) |

O que mudou em cada versão está no changelog unificado (`docs/curseforge/changelog-curseforge-<versão>.md`); onde cada coisa fica no código, no [mapa](../mapa/).

## Como atualizar (ao fim de cada sessão)

1. **Histórico:** uma linha no arquivo do mês (`historico/AAAA-MM.md`, a mais nova em cima; mês novo, arquivo novo). O que foi feito, o que foi verificado (testes, e2e) e o que ficou. Curta: o detalhe está no commit.
2. **Pendências:** o que ficou para depois entra em `pendencias.md`, na área certa; o que foi feito **sai** de lá.
3. **Estado:** se uma funcionalidade entrou, mudou de estado ou saiu numa versão, a linha dela em `estado.md`.
4. **Decisões:** decisão nova do dono ou regra nova do código vai para `decisoes.md`, no presente.
5. **Este arquivo:** reescreva "Agora" e "Próximo passo" (até 5 itens). Nada de acumular: o que passou vai para o histórico.
