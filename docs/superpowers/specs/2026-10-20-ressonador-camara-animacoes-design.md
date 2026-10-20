# Ressonador de Sinal: todas as animações da câmara (parte 2)

Aprovado pelo dono em 20/10/2026 sobre o protótipo [`docs/preview/ressonador-camara-completa.html`](../../preview/ressonador-camara-completa.html). Continua a [parte 1, o Núcleo de plasma](2026-10-20-ressonador-camara-plasma-design.md), que já está no ramo `ressonador-rebalanceamento`.

## Por que

O dono pediu para trocar todas as animações da câmara, não só o centro. Duas delas tinham problema além do visual:

- As **faíscas** da transferência de energia andam conforme a **geração**, mesmo quando nada sai pela Porta de Energia. Parecem energia saindo quando não está.
- A **recarga** são grãos de redstone com partículas de pó do vanilla, pouco legíveis.

## O que muda

| Animação | Hoje | Novo |
| --- | --- | --- |
| Fluxo de energia | faíscas pela geração | **conduto** de luz do plasma até cada Porta de Energia **que está entregando energia**, com pacotes correndo para fora; mais grosso e mais rápido com mais vazão; apagado sem vazão |
| Recarga | grãos de redstone e pó | **espiral** de catalisador da Porta de Cristais até o Opaco; o cristal **enche de luz** com o progresso e solta um **anel** ao terminar |
| Cristais | modelo do item em anel, feixe até a cabeça | igual, mais uma **aura** do shader em cada cristal vivo, que treme fora de sintonia |
| Buffer cheio | nada | o plasma **pulsa contido** (pressão), e nenhum conduto acende |
| Placa de Chladni | igual | igual |

Tudo novo é desenhado por shader, como o plasma: um segundo core shader, `wirelessautomate:resonator_ribbon`, para fitas e auras (conduto, espiral, aura e anel), com os parâmetros nos vértices. Com shaderpack do Iris ativo, a câmara usa o desenho antigo, mas as faíscas antigas passam a seguir a vazão real (correção que vale nos dois modos).

## O que o servidor passa a contar

- **Vazão por Porta de Energia.** Cada visão de energia de uma Porta (a `CoreEnergy` e a `CoreEnergyHandler`) sabe o índice da sua Porta. O Núcleo soma em um `long` por Porta o que sai de verdade (só o que não é simulação). No lote de 20 ticks que já existe, cada soma vira uma **faixa de vazão de 0 a 7** e zera.
  - Faixa 0 = nada saiu; 1 a 7 pela fração da geração máxima do tier que saiu por tick, em escala de raiz (`CoreMath.flowBand(extraído, ticks, geraçãoMáxima)`, pura, com JUnit): `f = extraído / (ticks × máxima)`, faixa = `min(7, 1 + floor(7 × sqrt(f)))` com `f > 0`.
  - **Índice da Porta:** a ordem das Portas de Energia na casca, por x, depois y, depois z, a partir da origem da caixa (a mesma ordem em que o `ChamberScene.readPorts` do cliente as lê). Calculado na montagem, quando as Portas são registradas.
- **Buffer cheio:** `energia ≥ capacidade` no fim do lote.
- **Na `ChamberView`:** `byte[] flows` (uma faixa por Porta de Energia, na ordem acima) e `boolean full`; chaves do update tag `flows` e `full`. Como hoje, o update só sai quando a vista muda; a vazão é quantizada, então uma vazão estável não manda nada.

## Custo de performance

| Parte | Custo |
| --- | --- |
| Contagem | um `long +=` por extração real, no caminho que já existe; o índice da Porta fica na visão, calculado na montagem |
| Lote | uma conta por Porta de Energia a cada 20 ticks |
| Rede | o update da câmara só quando uma faixa ou o "cheio" muda (no máximo um por segundo por Núcleo, e só se mudar) |
| Cliente | uma fita por Porta com vazão, uma espiral na recarga, uma aura por cristal vivo e um anel ao terminar: até ~14 quads por Núcleo, sem alocação por quadro |
| Medida | a mesma do e2e da parte 1, com o conduto e a recarga ligados |

## Fora desta spec

- A placa de Chladni em shader.
- Bloom de verdade e integração com o Veil.
- Mudar a tela do Núcleo (a vazão por Porta não aparece na tela).
