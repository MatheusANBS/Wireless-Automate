# Reparador de Cristais e recarga paralela do Núcleo (spec)

Estado: desenho aprovado pelo dono em 26/10/2026 (mecânica, modelo 3D e linha do tempo da animação); técnica do brilho decidida: nativa, sem Veil. Nada implementado.

## Motivação

O tempo ativo dos cristais incomoda: a vida cai por tier do Núcleo (20, 20, 15, 10, 5, 5, 4 e 3 minutos) e a recarga hoje é de um Opaco por vez (`ResonatorBatch.recharge`, 600 ticks cada). No Ultimate são 8 câmaras de 3 min: recarregar os 8 em sequência leva 4 min, mais que a vida deles. Além disso, o cristal rachado só volta pela bancada (`RecastCrystalRecipe`). O dono quer o ciclo de reposição sem downtime e uma máquina bonita para isso.

## Decisões

1. **Núcleo: recarga paralela.** Um cronômetro só (o `rechargeProgress` de hoje); ao completar, **todos** os Opacos recarregáveis enchem de uma vez, cada um gastando 1 catalisador (a regra do tier, `CatalystRule`) e 1 de integridade. Faltou catalisador para todos: enchem os primeiros, na ordem das câmaras. NBT, rede e GameTests do `ResonatorBatch` continuam valendo; muda só a varredura (de `firstRechargeable` para todas).
2. **Sem reparo dentro do Núcleo.** Descartado pelo dono.
3. **Reparador de Cristais: bloco independente, sem tier, sem vínculo com o Ressonador.** Repara cristais rachados. Recarregar Opacos fora do Núcleo ficou de fora (decisão do dono), então não precisa da regra de catalisador por tier.
4. **Slots:** 8 de rachados, 8 de saída e 1 pilha de Cristais de Sinal cheios (64) compartilhada. Oito é o máximo de câmaras (Ultimate).
5. **Reparo:** por linha, com cronômetro próprio, `repairTicks` na config (padrão 140 = 7 s). Começa quando a linha tem um rachado, o estoque tem `recastCrystals` (4) Cristais de Sinal cheios e o buffer tem a energia do cristal. Sai o mesmo cristal, com carga e integridade cheias (a regra do `RecastCrystalRecipe`, que continua na bancada). Entram os rachados comuns, Lapidados e Afinados; o Eco do Ultimate segue como está (o Afinado Ultimate rachado no Núcleo é queimado, o que não passa por aqui).
6. **Energia:** capability de FE comum, sem vínculo com o Ressonador: o jogador leva a energia como quiser (cabo, roteador, Bateria). Buffer para as 8 linhas de uma vez. Custo por reparo = fator (2) × o FE por cristal do tipo reparado, por tabela na config: Sinal 432 (2 × 216), Lapidado 13.824 (2 × 6.912), Afinado do tier T = 2 × `defaultFePerCrystal(T)` (Elite 20.000 até Ultimate 536.870.912). O custo sai do buffer no início do reparo. Os valores são iniciais, a afinar no bench de balanceamento.
7. **Automação:** itens por qualquer lado pela capability, cada slot só aceita o que cabe nele (rachados e cheios entram, só a saída sai). Funciona com o roteador na Presa e com funis.

## Modelo 3D

Pronto no Blockbench: `docs/preview/reparador-de-cristais.bbmodel` (105 cubos, 0 problemas no `check_model`, 1 componente conectado, sem faces coplanares, UV por face com paleta). Base com porta de energia (norte), emblema, escotilha de entrada (leste) e de saída (oeste); câmara de vidro com 4 colunas octogonais, 8 pedestais e 8 runas em anel; tampa com condutos, faixa luminosa e 4 pontas de cristal; emissor pendurado. O centro da câmara fica vazio para o BER. Estado final (26/10/2026): o modelo foi repintado no Blockbench, com luz e sombra, num atlas de 256 × 256 (`reparador_alma`, 104 elementos, embutido no `.bbmodel`). O `scripts/textures/reparador_modelo.py` o converte em `models/block/repairer/crystal_repairer.json` (sólido) e `crystal_repairer_glass.json` (translúcido), `textures/block/repairer/repairer.png`, o blockstate `multipart` por `facing` e o item `neoforge:composite`; a luz do emissor, das runas e das luzes (`*_glow`, `led_glow`) fica chapada no atlas até o BER (etapa 3). Não há paleta 16 × 16.

## Animação (BER)

Referência: o mockup aprovado (`reparador-de-cristais.html`). Linha do tempo de um reparo de 7 s:

| Tempo | O que acontece | Elemento |
| --- | --- | --- |
| 0,0–0,5 s | A energia é puxada: o emissor acende, uma onda de luz corre pelas runas e o pedestal da linha acende | Emissor, runas, pedestal |
| 0,3–1,2 s | Os 4 cristais cheios aparecem em órbita ao redor do emissor | Cristais consumidos |
| 1,0–5,5 s | Um raio fractal liga o emissor ao rachado, com pacotes de luz correndo; as rachaduras fecham em sequência, de dentro para fora, com um ponto quente de solda | Raio, cristal rachado |
| 5,5–6,3 s | O raio engrossa, o cristal vibra e o brilho sobe; os 4 em órbita se dissolvem com borda quente | Convergência, dissolução |
| 6,3–7,0 s | Clarão no cristal, onda de choque pelo chão, o reparado gira e sai | Clarão, onda |
| Parado | Runas fracas, emissor pulsando devagar | Ocioso |
| Sem energia | Tudo apagado, rachaduras sem brilho | Desligado |

Técnica por elemento (a primeira é a candidata nativa, a segunda a alternativa):

| Elemento | Nativa | Alternativa |
| --- | --- | --- |
| Rachaduras fechando | `CrystalShader` com parâmetro de progresso e a máscara das rachaduras por distância a segmentos | Texturas pré-geradas em 8 estágios |
| Raio emissor → cristal | Modo 0 do `resonator_ribbon` (o raio aprovado como "perfeito") | Tentáculo modo 2 |
| Emissor | `PlasmaCore` menor, pendurado | Só brilho no modelo |
| Cristais consumidos | `CrystalMesh` em órbita com dissolução por limiar de ruído e borda quente | Escalar até 0 |
| Onda no chão | Novo modo 3 da fita (anel horizontal) | Sprite aditivo do atlas |
| Runas e pedestais | Quads aditivos sobre as runas, intensidade por fase | Emissivo estático |

**Decisão (26/10/2026, o dono delegou a escolha): 100% nativo, sem Veil nem outra dependência.** O dono pediu para comparar com o Veil; antes do protótipo pediu que eu decidisse, e a decisão é não usar o Veil, sem a comparação. Razões, conferidas em buscas de 26/10/2026 (confirmar de novo se o assunto voltar): (1) nenhuma fonte achada lista o Veil no ATM10, então seria dependência nova para todo jogador, ou dois caminhos de render se fosse opcional; (2) com shaderpack do Iris ativo os efeitos do Veil passam por fora do pipeline do shaderpack e só voltam com mods de compatibilidade à parte (Iris Veil Compat, Veil Iris Lights Compat), uma cadeia de dependências para uma câmara; (3) o Veil é LGPL-3.0-only e as buscas trouxeram versões NeoForge 1.21.1 em duas linhas (3.6.x e 4.4.x) sem consenso; (4) os efeitos aprovados (raio, tentáculos, plasma) usam brilho fingido no próprio quad e o Reparador reaproveita os mesmos shaders; (5) um pós-processamento de tela cheia custa por quadro mesmo sem o Reparador à vista, contra a lei "parado custa zero". O que se perde é o bloom real; se o dono o quiser depois, entra como melhoria opcional sem refazer o resto.

Com shaderpack do Iris ativo (`ShaderPackCheck`): o caminho de contingência do Ressonador (sprites e faíscas no lugar dos shaders).

## Custo de performance (lei por lei)

- **Parado custa zero:** sem reparo, o servidor não agenda tick (o tick só existe enquanto há linha trabalhando); o cliente desenha só o pulso fraco do emissor e das runas, e só a menos de 64 blocos.
- **Servidor:** um gerenciador agenda o tick do bloco só com trabalho; o estado de cada linha é o `gameTime` de início. Update tag só no início e no fim de um reparo (nada por tick). `setChanged` só nessas transições.
- **Cliente:** o progresso sai do `gameTime` (sem pacote por tick). Alocação zero por quadro, como o `PlasmaTendrils`. Quads aditivos: 16 das runas e pedestais, 1 raio por linha trabalhando, 4 cristais em órbita por linha.
- **Memória:** os 8 slots e o buffer só por bloco; capacidades de FE e custos por tabela estática.
- **Medida:** `/wa profile` ganha a linha do Reparador; bench antes e depois no tempo de quadro da câmara (a mesma rodada do e2e do Ressonador).

## Etapas

1. **Núcleo, recarga paralela** (pequena): `ResonatorBatch.recharge` e os GameTests de recarga e fadiga; JEI e guia.
2. **Reparador, mecânica e bloco** (sem animação): bloco, block entity, menu e tela, capabilities de itens e energia, config, receita, JEI, guia, GameTests (nova run ou o lote do Ressonador).
3. **Animação (nativa):** e2e só do efeito (`WA_E2E_ONLY=reparador`, com o tempo parado em cada fase), verificação lado a lado, medidas.

## Fora de escopo

Recarregar Opacos fora do Núcleo; Tablet (o Ressonador é só presencial); tiers do Reparador; reparo dentro do Núcleo.
