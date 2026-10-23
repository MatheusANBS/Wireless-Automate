# Câmara do Ressonador: limpar e refazer (cristais, recarga, esfera, ambiente)

Pedido do dono (23/10): a energia (raio da esfera à Porta, commit `cf470fba`) ficou perfeita e **não muda**. O resto da câmara é refeito do zero, com **menos elementos e mais qualidade**: hoje há poluição (curvas que giram, linhas ligando cristal e estrela, aura, faíscas). A skill `wa-shaders` guarda o método.

## Ponto de volta

Tudo que existe antes desta refeitura está no ramo `ressonador-plasma-qualidade`, último commit bom `cf470fba`. Para voltar atrás: `git checkout cf470fba -- src/main/resources/assets/wirelessautomate/shaders src/main/java/.../client/resonator/render`. A refeitura vai em commits separados por elemento, para reverter um de cada vez.

## Inventário e decisão proposta

| Elemento | Hoje | Proposta |
| --- | --- | --- |
| Raio da esfera à Porta (modo 0) | perfeito | **mantém, intocado** |
| Esfera de plasma | boa, halo e borda do tier | mantém a esfera; **tira** a ruído de TV fora de sintonia no halo se poluir; revisar só se o dono pedir |
| Curvas de Lissajous 3D girando | poluem | **remove** (a sintonia já aparece no raio e na esfera) |
| Linhas cristal → estrela (feixes/órbitas) | poluem | **remove**; o vínculo cristal-esfera vira só um arco fino curto em disparo (já planejado, usa `boltLight`) |
| Aura de cada cristal (halo + estrela de 4 pontas) | borrada | **remove**; o brilho do cristal fica no próprio shader (varredura) |
| Faíscas viajantes | muitas | reduz a poucas, só com buffer cheio, ou remove |
| Chladni (placa no chão) | ruído aditivo | revisar: manter só se mostrar a sintonia com clareza |
| Cristais (pixel-art 3D) | aprovados | **refazer** com a mesma paleta, mais limpo, só facetas e contorno (sem aura) |
| Recarga (espiral + anel + estilhaços) | espiral de catalisador e anel | **refazer**: um único efeito claro (o cristal Opaco se enche de baixo para cima com um fio de luz da Porta de Cristais até ele, e um pulso ao terminar) |
| Plasma sob pressão (buffer cheio) | raio menor e borda pulsando | mantém, é o sinal do buffer |

## Regra de decisão para o que fica

Cada elemento precisa mostrar **um dado do jogo**: vazão (raio), sintonia (esfera tremendo e cor), buffer cheio (pressão), carga e tier do cristal (cor da cinta, brilho), recarga (enchimento). Sem dado, sai.

## Ordem

1. Remover a poluição (curvas, linhas, aura, faíscas), com captura antes e depois. Aprovação.
2. Cristais limpos. Aprovação.
3. Recarga nova. Aprovação.
4. Esfera e ambiente (Chladni), só o que o dono pedir.
5. Arcos dos cristais para a esfera (disparos finos) e arcos ociosos, e o estalo sonoro (`ArcSounds`, `ClientConfig.effects.arcSounds`), da Parte B já desenhada.
6. Fechamento: perf, comentários, build, e2e com tempos, `CLAUDE.md`, `docs/progresso.md`.
