"""Página troubleshooting.md do guia."""
from guia_base import *  # noqa: F401,F403


PAGINA = page('troubleshooting.md', front('Problemas comuns', 'minecraft:barrier', 21) + '''
# Problemas comuns

Algo não se move? Confira na ordem.

| Sintoma | Causa e solução |
| --- | --- |
| Nada sai da origem | A face não está em **Extrai**, ou os roteadores não estão na mesma **Rede** (o ponto sobre o botão tem a cor da rede). |
| Nada chega no destino | Longe demais: suba a origem com um [Cartão de Upgrade](upgrade-cards.md). Ou cheio: volta sozinho quando tiver espaço. |
| Só alguns itens passam | Um [filtro](filters.md) ou estoque barra. Filtro vazio passa tudo. |
| Funciona e para | Redstone em **Com sinal** ou **Sem sinal**. Use **Ignorar**. |
| Para quando você se afasta | O chunk descarregou: [Upgrade de Chunk Loading](chunk-loading.md). |
| Modo indisponível | A máquina não abre esse tipo nessa face. Tente outra. |
| Químicos não se movem | Ligue a face na configuração de lados do Mekanism. |
| Não consigo pôr na rede | A rede é de outro jogador. |
| Vazão menor que a do tier | Muitas máquinas dividem a vazão. É normal. |

Ainda com dúvida? A aba **Fluxos** do [Tablet de Rede](network-tablet.md) mostra o que cada rede
move, quais faces esperam destino e quais destinos estão cheios.
''', front('Troubleshooting', 'minecraft:barrier', 21) + '''
# Troubleshooting

Something not moving? Check in order.

| Symptom | Cause and fix |
| --- | --- |
| Nothing leaves the source | The face isn't on **Extract**, or the routers aren't on the same **Network** (the dot above the button has the network's color). |
| Nothing reaches the destination | Too far: raise the source with an [Upgrade Card](upgrade-cards.md). Or full: it resumes on its own when there's room. |
| Only some items pass | A [filter](filters.md) or stock limit blocks. An empty filter lets everything through. |
| Works, then stops | Redstone on **With signal** or **No signal**. Use **Ignore**. |
| Stops when you walk away | The chunk unloaded: [Chunk Loading Upgrade](chunk-loading.md). |
| Mode unavailable | The machine doesn't open that type on that face. Try another. |
| Chemicals don't move | Enable the face in Mekanism's side config. |
| Can't put it in a network | The network belongs to another player. |
| Less throughput than the tier | Many machines share the throughput. It's normal. |

Still stuck? The [Network Tablet](network-tablet.md)'s **Flows** tab shows what each network is
moving, which faces are waiting for a destination and which destinations are full.
''')
