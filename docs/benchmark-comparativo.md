# Benchmark comparativo (planejado)

Ainda não feito. Este documento guarda a intenção e o plano, para rodar numa sessão local.

## Por quê

Jogadores pediram números contra o **Logistics Network** ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/logistics-network)), um mod wireless de itens, fluidos, energia e químicos que o pessoal já usa.

Hoje só temos os números do próprio mod (`docs/benchmark.md`: 500 roteadores ativos a ~0,55 ms/tick, medidos pelo profiler interno). Isso não responde à pergunta, porque é medido por dentro e sem outro mod do lado. A especificação já pedia isso ("Comparação"), e o `docs/benchmark.md` lista como pendência comparar com outros mods de transporte do ATM10.

## Objetivo

Comparar os dois mods de forma justa e publicar os números com o método, **seja qual for o resultado**. Se o Logistics Network ganhar em algum cenário, isso também vai para o documento (e vira tarefa de performance aqui).

## Princípios

- **Mesma máquina, mesmo mundo, mesmo cenário.** Mesmo número de máquinas, os mesmos baús (vanilla e Sophisticated Storage), a mesma vazão alvo e o mesmo tempo de aquecimento.
- **Medição por fora**, igual para os dois: MSPT do servidor (com e sem a rede montada) e perfil do **spark**. O profiler interno do Wireless Automate (`/wa profile`) entra só como dado extra, nunca como a comparação.
- **Itens movidos por segundo** contados nos destinos, para não comparar um mod lento e leve com um rápido e pesado só pelo MSPT.
- Várias rodadas por cenário, com média e desvio, como no `docs/benchmark.md`.
- Servidor dedicado parado (nada de cliente ou outra build rodando ao mesmo tempo).

## Cenários (rascunho)

Espelhar os do `scripts/bench.sh` que fazem sentido para os dois:

| Cenário | O que mede |
| --- | --- |
| `many` (100, 500, 1.000 máquinas; metade origens cheias, metade destinos vazios) | Escala com tudo ativo |
| `sparse` (500 máquinas, só uma origem com itens) | Custo das origens ociosas |
| `raw` (um par de inventários grandes, tier máximo) | Vazão bruta |
| Fluido e energia | Se der para montar igual nos dois |

Cada cenário roda com dois tipos de ponta: baús vanilla (uso comum) e as origens e destinos rápidos da seção abaixo (o mod no limite).

## Origens e destinos rápidos

Os dois mods têm vazões altas no topo (o roteador Ultimate é ilimitado; o Logistics Network, pela página, passa até 10 mil itens por transferência e por canal). Com baús vanilla, o gargalo passa a ser o baú (27 slots de 64), não o mod, e os dois empatam por limite do inventário. Para medir o mod no topo, os cenários de vazão (`raw` e `many` no tier máximo) precisam de origens e destinos que aguentem esse volume:

- **Baús e tanques do próprio mod (Baú, Tanque, Bateria).** Cuidado: entre dois armazenamentos do mod o roteador usa o atalho (`BulkItems` e afins, um tipo inteiro numa operação), que o Logistics Network não tem; para ele são um inventário comum. É um cenário legítimo ("o caso de uso real com os baús do mod"), mas vai **rotulado à parte**, nunca como a comparação principal.
- **Inventários neutros de outro mod**, que nenhum dos dois conhece de forma especial: Sophisticated Storage com upgrades de pilha (já usado no `bench.sh`), e, com o Mekanism no ambiente, os criativos dele (Bin, Fluid Tank e Energy Cube criativos) como origem infinita. Essa é a comparação principal.
- **Máquinas rápidas de verdade** (por exemplo, máquinas do Mekanism com upgrades de velocidade), como um cenário extra mais próximo de uma base real: mede o mod alimentando e esvaziando máquinas, não só baú para baú.
- As máquinas de teste do GameTest (`TestMachines`: pilha enorme, 20 tanques) só existem com `-Dwirelessautomate.gameTests=true`. Servem para conferir o lado do Wireless Automate, mas não entram na comparação, porque são do próprio mod.

Para cada cenário, anotar no resultado qual foi o gargalo (o mod ou o inventário), senão o número engana.

## Pontos em aberto (resolver na sessão local)

- **Como montar o cenário do Logistics Network.** Os nós dele não são blocos (pela página, ficam "invisíveis" e têm 9 canais), então o `/wa bench` não serve. Ver se dá para criar por comando ou script; senão, montar à mão um cenário menor e salvar o mundo para reusar nas rodadas.
- **Equivalência de configuração:** o Logistics Network não tem tiers (pela página, até 10 mil itens por transferência e por canal). Escolher o tier do roteador que dá uma vazão parecida, ou medir os dois no máximo e deixar isso explícito.
- **Versão** do Logistics Network a usar (a mais nova para 1.21.1 / NeoForge) e anotar no resultado.
- **Licença:** o Logistics Network é All Rights Reserved. Usar o jar só no ambiente local de benchmark, sem redistribuir e sem pôr no repositório nem como dependência do build.

## Entrega

1. Uma seção nova no `docs/benchmark.md` com a tabela, o método, as versões e os perfis do spark.
