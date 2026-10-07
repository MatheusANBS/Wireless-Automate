---
navigation:
  title: Cartões de Upgrade
  icon: wirelessautomate:tier_core_advanced
  parent: index.md
  position: 3
item_ids:
  - wirelessautomate:tier_core_advanced
  - wirelessautomate:tier_core_elite
  - wirelessautomate:tier_core_ultimate
---


# Cartões de Upgrade

<Row gap="8">
  <ItemImage id="wirelessautomate:tier_core_advanced" scale="2" />
  <ItemImage id="wirelessautomate:tier_core_elite" scale="2" />
  <ItemImage id="wirelessautomate:tier_core_ultimate" scale="2" />
</Row>

Todo roteador nasce **Básico**. Os cartões sobem um tier de cada vez: Básico → Avançado → Elite →
Ultimate. A configuração do roteador (faces, filtros, redes) não se perde.

## O que cada cartão aumenta

Valores padrão, **por face e por tipo** (o servidor pode mudar na config; o tooltip do cartão
mostra os valores do seu servidor). Químicos usam o limite de fluido.

| Cartão | Itens/s | Fluido e químico (mB/s) | Energia (FE/t) | Alcance |
| --- | --- | --- | --- | --- |
| <ItemImage id="wirelessautomate:tier_core_advanced" /> Avançado (Básico → Avançado) | 512 → **8.192** (16×) | 32.000 → **512.000** (16×) | 16.000 → **256.000** (16×) | 128 → **1.024** blocos |
| <ItemImage id="wirelessautomate:tier_core_elite" /> Elite (Avançado → Elite) | 8.192 → **131.072** (16×) | 512.000 → **8.000.000** (16×) | 256.000 → **4.000.000** (16×) | 1.024 blocos → **a dimensão inteira** |
| <ItemImage id="wirelessautomate:tier_core_ultimate" /> Ultimate (Elite → Ultimate) | 131.072 → **sem limite** | 8.000.000 → **sem limite** | 4.000.000 → **sem limite** | a dimensão → **todas as dimensões** |

"Sem limite" quer dizer que só o orçamento de tempo do mod segura a vazão (veja
[Desempenho e config](performance.md)). O alcance e as dimensões contam pelo tier da **origem**.

## Como usar

- **No mundo:** clique com o cartão do tier seguinte num roteador já colocado.
- **Na bancada:** um roteador + o cartão do tier seguinte, em qualquer posição, viram o roteador no
  tier novo (o nome dado ao roteador continua).

Não dá para pular tier: um roteador Básico não aceita o cartão Elite.

## Receitas

<RecipeFor id="wirelessautomate:tier_core_advanced" />
<RecipeFor id="wirelessautomate:tier_core_elite" />
<RecipeFor id="wirelessautomate:tier_core_ultimate" />

O que cada tier entrega está na tabela de [Roteador](router.md).
