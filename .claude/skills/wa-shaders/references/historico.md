# Histórico de decisões visuais do dono

Contexto, não regra. Cada linha vale para o pedido e o lugar em que foi dada; um pedido novo do dono vale mais. Quando um pedido novo conflitar com uma linha daqui, pergunte.

## Câmara do Ressonador (outubro de 2026)

- **Aprovado como "perfeito":** o raio da esfera à Porta de Energia (`resonator_ribbon.fsh` modo 0, `FlowConduit`, commit `cf470fba`): eixo fractal, núcleo HDR, brilho por distância, sem borda.
- **Aprovados:** os tentáculos de plasma dos cristais até a esfera; cristais de pé com contorno por casco invertido, girando no eixo pela sintonia, no anel que orbita a esfera; a recarga por tentáculo invertido, enchimento e clarão; a esfera cobrindo o que está atrás (alfa pré-multiplicado).
- **Pedido para a câmara:** menos elementos e mais qualidade (curvas de Lissajous, órbitas, feixes, aura e a placa de Chladni saíram).
- **Reprovados na câmara:** tubo com borda e fios trançados em volta ("borda estranha"); halo de cauda longa ("borrado"); gema por ruído; esfera estourada em branco ou rosada; cristais deitados com pulso e anéis de onda na esfera ("horrível", tímido; o dono quer efeitos épicos, no nível do raio).

## Reator estelar (23/10/2026)

- O dono trouxe um GIF (sol com escudo hexagonal, feixes dos cantos, erupção que enche o escudo) e pediu igual, com bloom de verdade, e depois a comparação entre o nativo e uma versão com dependências externas. A primeira tentativa (nativa, framebuffer próprio com bloom de Jimenez, Voronoi de Fibonacci para os hexágonos) foi apagada pelo dono antes de fechar; as lições estão em `armadilhas.md` e `tecnicas.md`.
