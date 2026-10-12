# Subir tier: receita direta e cartões com o mesmo custo

**Estado:** aprovado pelo dono em 2026-10-12, com as recomendações do mockup `docs/preview/subir-tier.html`. Entra no mesmo update grande, no ramo `roteador-de-alcance`, depois da rede por face. Substitui a regra de custo da spec `2026-10-09-upgrade-direto-design.md` (o cartão continua pulando degraus, mas agora com origem).

## 1. Problema

Hoje o Cartão de Upgrade de tier N é o anel do tier em volta do cartão de N−1 e sobe de **qualquer** tier abaixo. Ele embute todos os anéis anteriores, então uma receita direta de bancada (o anel em volta do roteador) sairia mais barata e induziria a craftar direto. O dono quer o mesmo custo pelos dois caminhos, como o Sophisticated Storage (baú + 8 ferro; "Ferro → Ouro" = 8 ouro + alavanca; "Básico → Ouro" = 8 ouro em volta do "Básico → Ferro").

## 2. Decisões

1. **Um anel por tier**, o mesmo nas três receitas:

   | Tier | Anel (cima, meio, baixo; `·` = centro) | Mudou? |
   | --- | --- | --- |
   | Avançado | G D G / D · D / G D G — G lingote de ouro, D diamante | sai o bloco de ouro do centro |
   | Elite | N O N / O · O / N O N — N lingote de netherite, O obsidiana | era estrela do Nether + 3 netherites |
   | Esmeralda | B S B / R · R / B Y B — B bloco de esmeralda, S estrela do Nether, R obsidiana chorona, Y olho do Ender | a estrela entra no lugar de um olho |
   | Allthemodium | I B I / I · I / I B I (como hoje) | não |
   | Vibranium | I A I / B · B / I A I (como hoje) | não |
   | Unobtainium | I A I / B · B / I A I (como hoje) | não |
   | Ultimate | X D X / B · B / X B X (como hoje), do Esmeralda sem o Allthemodium e do Unobtainium com ele | não |
   | Ultimate com o All The Tweaks | S D S / U · U / S U S (como hoje) | não |

   Os ingredientes são os mesmos itens e tags das receitas de hoje (`c:` nos do Allthemodium), com as mesmas condições `neoforge:conditions`.
2. **Receita direta** (bancada): o item do bloco do tier anterior no centro do anel = o item do tier novo, com o que o item carrega (no roteador quebrado, o nome; no armazenamento, o conteúdo). A configuração do roteador só se mantém subindo no lugar, pelo cartão. Vale para o **roteador e para os cinco armazenamentos** (Baú, Tanque, Bateria, Tanque Químico, Tanque de Source), que também sobem pelo cartão. Sobe um degrau só, pela escada (`TierLadder`: com o Allthemodium, Esmeralda → Allthemodium; sem ele, Esmeralda → Ultimate).
3. **Cartão de um degrau:** o anel em volta de uma **Redstone** = o cartão "N−1 → N".
4. **Cartão que pula:** um cartão "k → N−1" no centro do anel de N = o cartão "k → N" (a origem se mantém). Custo de k → N pelo cartão = soma dos anéis de k+1 a N + uma Redstone, igual à bancada degrau a degrau.
5. **Um item por tier de destino** (os `tier_core_<tier>` de hoje); a origem é um componente novo, `upgrade_from` (o nome do tier). Nome do item: "Cartão de Upgrade: Básico → Elite". Tooltip: "Sobe um roteador Básico para Elite", "Vale só num roteador Básico", e as linhas de vazão de hoje (de origem → destino, já que a origem é conhecida).
6. **Usar:** Shift + clique direito no bloco, ou na bancada (bloco + cartão, `RouterUpgradeRecipe`), só se o tier do bloco é **exatamente** a origem do cartão. Senão recusa, com a action bar "Este cartão sobe um roteador Básico" (ou "um armazenamento Básico").
7. **Cartões antigos** (sem `upgrade_from`, feitos antes desta versão): continuam valendo como hoje, de qualquer tier abaixo do destino. Tooltip: "Cartão antigo: sobe qualquer tier abaixo de Elite". Não se fabrica mais cartão sem origem.
8. **JEI:** as receitas diretas, os cartões de um degrau e, por tier de destino, um exemplo de cartão que pula (o de origem Básico a partir do de um degrau anterior). A categoria de "bloco + cartão" de hoje mostra os pares origem → destino com o cartão daquela origem.
9. A estrela do Nether sai do Elite e vai para o Esmeralda (pedido do dono).

## 3. Fora do escopo

- Subir mais de um tier numa receita direta.
- Descer tier.
- Converter cartões antigos.

## 4. Testes

- JUnit: a regra pura (origem exata; antigo de qualquer tier abaixo; estender o destino por um degrau da escada, com e sem o Allthemodium).
- GameTests: receita direta mantém os dados do roteador e de um armazenamento; cartão de um degrau e cartão que pula, pela bancada e pelo clique; recusa com origem diferente; cartão antigo; cartão "k → N−1" + anel = "k → N"; custo igual pelos dois caminhos (os ingredientes das receitas). Na run do Allthemodium, a escada com os tiers do ATM e o Ultimate com o All The Tweaks.
- e2e: o JEI e o tooltip, nas capturas de sempre.
