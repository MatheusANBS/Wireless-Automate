# Tier Esmeralda e tiers do Allthemodium: design

Aprovado pelo dono em 8/10/2026 (página de proposta no artifact "Tiers Esmeralda e ATM", versão 2).

## Escada

- Sem o Allthemodium: Básico → Avançado → Elite → **Esmeralda** → Ultimate.
- Com o mod `allthemodium`: Básico → Avançado → Elite → **Esmeralda** → **Allthemodium** → **Vibranium** → **Unobtainium** → Ultimate.
- O enum `RouterTier` tem sempre os oito, nessa ordem; cada um diz o mod que exige (`null` ou `"allthemodium"`). `next()` e `previous()` pulam os tiers cujo mod não está carregado. Um bloco num tier do ATM sem o mod continua funcionando e o próximo upgrade é o Ultimate.
- O "entre dimensões" começa na Esmeralda (Elite continua só na dimensão).
- Roteador, Baú, Tanque, Bateria, Tanque Químico e Tanque de Source usam a mesma escada e os mesmos Cartões de Upgrade.

## Balanceamento (×8 por degrau, nerf nos tiers vanilla)

| Tier | Itens/s | Fluido e químico mB/s | Energia FE/t | Source/s | Alcance |
| --- | --- | --- | --- | --- | --- |
| Básico | 32 | 2.000 | 1.000 | 100 | 64 |
| Avançado | 256 | 16.000 | 8.000 | 800 | 512 |
| Elite | 2.048 | 128.000 | 64.000 | 6.400 | dimensão |
| Esmeralda | 16.384 | 1.024.000 | 512.000 | 51.200 | entre dimensões |
| Allthemodium | 131.072 | 8.192.000 | 4.096.000 | 409.600 | entre dimensões |
| Vibranium | 1.048.576 | 65.536.000 | 32.768.000 | 3.276.800 | entre dimensões |
| Unobtainium | 8.388.608 | 524.288.000 | 262.144.000 | 26.214.400 | entre dimensões |
| Ultimate | sem limite | sem limite | sem limite | sem limite | entre dimensões |

Capacidades padrão dos armazenamentos:

| Tier | Baú (itens) | Tanque e Tanque Químico (mB) | Bateria (FE) | Tanque de Source |
| --- | --- | --- | --- | --- |
| Básico | 32.768 | 256.000 | 1.000.000 | 10.000 |
| Avançado | 262.144 | 2.048.000 | 8.000.000 | 80.000 |
| Elite | 2.097.152 | 16.384.000 | 64.000.000 | 640.000 |
| Esmeralda | 16.777.216 | 131.072.000 | 512.000.000 | 5.120.000 |
| Allthemodium | 134.217.728 | 1.048.576.000 | 4.096.000.000 | 40.960.000 |
| Vibranium | 1.073.741.824 | 8.388.608.000 | 32.768.000.000 | 327.680.000 |
| Unobtainium | 8.589.934.592 | 67.108.864.000 | 262.144.000.000 | 2.621.440.000 |
| Ultimate | sem limite | sem limite | sem limite | sem limite |

Configs que já existem guardariam os valores antigos (o NeoForge não sobrescreve) e o Elite ficaria acima da Esmeralda. Por isso a chave `migration.balanceVersion` (0 num arquivo antigo): na primeira carga, `Config.migrateBalance()` troca pelo padrão novo só os valores de vazão, alcance e capacidade que ainda estão no padrão antigo, e marca a versão 1. O que o dono do servidor mudou fica. As seções novas (`tiers.emerald`... e as chaves novas em `storage.*`) entram com os padrões.

## Receitas

- `tier_core_emerald`: `BYB / RCR / BYB`, B bloco de esmeralda, Y olho de ender, R obsidiana chorona, C Cartão Elite.
- `tier_core_allthemodium` (só com `allthemodium`): `IBI / ICI / IBI`, I `c:ingots/allthemodium`, B `c:storage_blocks/allthemodium`, C Cartão Esmeralda.
- `tier_core_vibranium` (só com `allthemodium`): `IAI / BCB / IAI`, I `c:ingots/vibranium`, A `c:ingots/vibranium_allthemodium_alloy`, B `c:storage_blocks/vibranium`, C Cartão Allthemodium.
- `tier_core_unobtainium` (só com `allthemodium`): `IAI / BCB / IAI`, I `c:ingots/unobtainium`, A `c:ingots/unobtainium_vibranium_alloy`, B `c:storage_blocks/unobtainium`, C Cartão Vibranium.
- Ultimate, três arquivos e só um carrega:
  - `tier_core_ultimate` (sem `allthemodium`): a receita de hoje com o Cartão Esmeralda no centro.
  - `tier_core_ultimate_atm` (com `allthemodium` e sem `allthetweaks`): a mesma, com o Cartão Unobtainium.
  - `tier_core_ultimate_atm_star` (com `allthemodium` e `allthetweaks`): `SDS / UCU / SUS`, S `allthetweaks:atm_star_shard`, D ovo do dragão, U `c:storage_blocks/unobtainium_allthemodium_alloy`, C Cartão Unobtainium.

Ids e tags conferidos nos jars do Allthemodium 3.0.1/3.2.0 e do All The Tweaks 2.12.1 (1.21.1). O fragmento sai triturando a ATM Star no Mekanism (receita do próprio All The Tweaks).

## Visual

- Cores (acento do roteador 4..0): Esmeralda `#c4ffd6 #4ee87a #1fbf4e #128a37 #0a5c24`; Allthemodium `#fff07a #ffc70c #ff8b04 #cf5a13 #a92405`; Vibranium `#73ffb9 #26de88 #1bb38a #0f5c7a #0e3c78`; Unobtainium `#f6c2fb #ea84f5 #d152e3 #a82ce3 #432a94` (tirados dos lingotes do mod).
- Cor de destaque na tela (`GuiPaint.tierColor`, ARGB): Esmeralda `0xFF2FDC62`, Allthemodium `0xFFFF8B04`, Vibranium `0xFF26DE88`, Unobtainium `0xFFD152E3`.
- Cartões de Upgrade com quatro marcas na faixa: Avançado 1, Elite 2, Esmeralda 3, Ultimate 4; Allthemodium 1, Vibranium 2, Unobtainium 3, com os contatos no metal do tier em vez de ouro.
- Modelos, blockstates e modelos de item dos tiers passam a ser gerados pelo `gerar_texturas.py`.

## Integração

- Sem tipos do Allthemodium nem do All The Tweaks: só `ModList`, receitas com `neoforge:conditions` e tags `c:`. Dependências opcionais no `neoforge.mods.toml`.
- Itens dos tiers do ATM (cartões, roteador e armazenamentos nesses tiers) ficam fora da aba criativa (e do JEI) sem o mod; o tooltip do cartão diz que precisa do Allthemodium.
- `ModPayloads.VERSION` sobe para `"9"` (o tier viaja pela posição no enum).
- Run nova `gameTestServerAllthemodium` (namespace `wirelessautomate_atm`, pasta `run/gametest-atm`) com o Allthemodium e o All The Tweaks do CurseForge (cursemaven).
