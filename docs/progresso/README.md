# Progresso

Comece por aqui para continuar o trabalho. Este arquivo é curto de propósito: diz onde o projeto está e para onde vai, e aponta para o resto.

## Agora

- **Versão atual:** 2.15.1 (05/11/2026): correção urgente da 2.15.0, quebrar uma máquina vinculada derrubava o servidor. Protocolo 49 (o mesmo da 2.15.0).
- **Publicação:** a 2.14.0 está no CurseForge; a 2.15.1 tem release no GitHub com o jar e espera o dono subir no CurseForge e no Modrinth (no lugar da 2.15.0).
- **Em andamento:** ramo `ccr-6099bf57-xlgxhy` (08/11): a auditoria de performance do motor, com três correções (o cursor que travava numa saída da máquina, o tanque seletivo que dormia como cheio, o `isLoaded` das remotas por tick), verificada e com bench; espera o teste à mão e o merge do dono, e a decisão sobre a máquina faladeira (A13 do backlog). O ramo `mc/1.20.1-forge` (porte) está parado à espera do ok do dono (`docs/portes.md`).

## Próximo passo

1. O dono testa à mão as correções do motor de 08/11 (um Separador Eletrolítico ou uma máquina com saída secundária num roteador Básico; dois tanques de tipos diferentes diante de um tanque só de lava) e decide o merge; e responde às seis perguntas da análise de balanceamento (`docs/superpowers/specs/2026-11-08-balanceamento-vazao-analise.md`).
2. O dono testa à mão as etapas 1 a 4 e o Ctrl + clique do Vinculador (listas nas linhas de 02/11 do histórico), já no main.
3. Reidealizar as telas do Tablet do zero (criação de redes etc.), mockup primeiro para o ok do dono.
4. Subir a 2.15.1 no CurseForge e no Modrinth; refazer as imagens do Vinculador na vitrine (sem a área) e a do livro (ver `pendencias.md`).
5. Os menores que sobraram do alcance (`pendencias.md`, seção "Roteador de alcance").

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
