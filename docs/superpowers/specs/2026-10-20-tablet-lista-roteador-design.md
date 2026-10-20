# Tablet: a Lista mostra o roteador, e a máquina presa ganha nome

Aprovado pelo dono em 20/10/2026 sobre o mockup [`docs/preview/tablet-lista-roteador.html`](../../preview/tablet-lista-roteador.html), com as recomendações das 4 decisões.

## Por que

Na aba Lista do Tablet, a linha de um roteador sem nome mostra o nome do bloco em que ele está preso (Fornalha, Baú…). Duas Fornalhas iguais não se distinguem sem abrir, e o dono quer controlar e renomear **por roteador**. A máquina presa também não aceita nome (o servidor recusa o `RenameTargetPayload` com `Kind.ATTACHED`), ao contrário das máquinas vinculadas pelo alcance.

## O que muda

0. **A visão Roteadores deixa de agrupar pelo bloco preso.** Hoje os roteadores ficam embaixo de um cabeçalho do bloco em que estão presos ("Baú", "Fornalha"…). Agora cada roteador é uma linha de primeiro nível, e abaixo dele, quando aberto, ficam a presa, os grupos e as máquinas. A visão Máquinas continua agrupada por bloco. (Correção do dono durante a Tarefa 2.)
1. **A linha do roteador (visão Roteadores da Lista)** mostra o roteador:
   - ícone do item do roteador do tier (`RouterBlockItem.withTier`, um `ItemStack` por tier criado uma vez no cliente);
   - o nome dado ao roteador ou, sem nome, **"Roteador &lt;tier&gt;"** (decisão 1);
   - a pílula do tier na cor do `GuiPaint.tierColor`;
   - na segunda linha, **"presa: &lt;nome da presa&gt;"** antes das coordenadas e do estado. O nome da presa é o rótulo dado ou, sem rótulo, o nome do bloco (`RouterBlock.machineName`).
   O mesmo nome do roteador vale no Mapa (`NodeText`), na dica e na visão Máquinas ("via &lt;roteador&gt;").
2. **Renomear na própria Lista** (decisão 2): o ✎ ao passar o mouse e dois cliques no nome abrem o editor de nome (`LabelEditor`, Enter salva, Esc cancela, até 32 letras) na linha do roteador e na linha da presa da árvore. O roteador usa o `RenameRouterPayload` que já existe; a presa, o `RenameTargetPayload` com `Kind.ATTACHED`.
3. **A máquina presa ganha nome:**
   - guardado no roteador, num campo novo do `RouterBlockEntity`, chave de NBT `attached_label` (só se não vazio); continua quando a máquina presa é trocada (decisão 3);
   - o `RenameTargetPayload` com `Kind.ATTACHED` passa a ser aceito (sem exigir o Upgrade de Alcance nem posição), pelo mesmo `RouterBlockEntity.renameTarget`;
   - aparece na linha da presa da árvore (com o ícone do bloco e o ✎), na segunda linha do roteador, na visão Máquinas (o nome da linha da presa), na pílula "Editando" do painel do nó e da tela do roteador (com o ✎, como grupos e máquinas), e na lista do alcance da tela do roteador.
4. **Busca** (decisão 4): acha pelo nome do roteador, pelo nome da presa, pelo bloco e pelas coordenadas.

## Protocolo e dados

- `NodeIndex.Entry` ganha `attachedLabel` (NBT do índice `attached_label`, só se não vazio; o formato antigo lê vazio), copiado do roteador no aviso de mudança, e entra no texto da busca.
- `TabletPageCodec` escreve o rótulo da presa por nó (texto vazio quando não há); o `NodeView` ganha o campo. A visão Máquinas passa o rótulo da presa em vez de `""`.
- `RouterSnapshot`/`RangeView` levam o rótulo da presa para o painel e a tela do roteador.
- `ModPayloads.VERSION` sobe de 25 para 26 (o formato da página do Tablet muda).
- Textos novos nos dois `lang`: o "Roteador %s" e o "presa: %s".

## Custo de performance

| Parte | Custo |
| --- | --- |
| Servidor | um texto a mais por roteador, gravado no NBT e copiado no índice só quando muda; nada por tick |
| Rede | um texto curto por nó na página, vazio quando não há rótulo; a página continua saindo só quando a estrutura muda |
| Cliente | 8 `ItemStack` de ícone criados uma vez; textos montados quando a página muda, como hoje (nada por quadro além de desenhar) |

## Fora desta spec

- Ícone na linha de cada máquina vinculada (continua só no cabeçalho do bloco na visão Máquinas).
- Mudar o renomear do roteador no cabeçalho da tela do roteador (continua igual).
