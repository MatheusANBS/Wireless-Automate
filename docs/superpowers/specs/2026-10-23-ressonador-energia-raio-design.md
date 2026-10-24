# Ressonador: o raio de energia da câmara (Parte B)

Data: 2026-10-23. Só shader, câmara e um som no cliente; sem mudança de servidor, de rede ou de dados. Vem **depois** da Parte A (`2026-10-23-ressonador-cristais-gema-design.md`), no mesmo ramo `ressonador-plasma-qualidade`, e usa o `wa_common.glsl`, o `RibbonQuad` e a gema nova.

## Contexto

Do pedido do dono de melhorar todas as animações da câmara do Ressonador. A Parte A refaz os cristais, a B (esta) a energia, a C a recarga e a D o ambiente. Na conversa, o dono propôs um raio no estilo tesla saindo da esfera até a saída de energia, e escolheu o caráter **híbrido**: um feixe estável de base, com arcos que enrolam e saltam em volta, e extras: os cristais alimentando a esfera, arcos ociosos e um estalo sonoro. Sem clarão e fagulhas extras na Porta (o conduto já tem o clarão das pontas).

## Problema

- O conduto da esfera até cada Porta (modo 0 de `resonator_ribbon.fsh`) é um cilindro com cometas: limpo, mas sem a eletricidade de um raio.
- Os feixes dos cristais até a esfera (`ChamberCrystals.beams`) são segmentos retos do `RenderType.lightning()`, finos (`BEAM_HALF` 0,009), que só pulsam em alfa: ficaram fora do shader novo.
- Com pouca vazão a câmara fica parada: nada acontece até alguma Porta entregar energia.

## Decisões

- **Sem dependência e sem dado novo.** A faixa de vazão por Porta (0 a 7) já chega ao cliente (`ChamberScene.flow`), junto com a sintonia (`tune`) e o buffer cheio (`full`).
- **Os arcos são desenhados no fragment** sobre fitas do `RibbonQuad.strip`, sem geometria nova: a forma vem do ruído, e cada arco é sorteado de novo ~14 vezes por segundo (a semente é o tempo quantizado mais o índice).
- **Som só do vanilla.** O mod não tem `sounds.json` nem arquivos de áudio; um som próprio fica para depois, se o dono gravar um `.ogg`.
- **Fora de escopo:** o clarão e as fagulhas na Porta, a recarga (C), o ambiente (D), o desenho antigo (shaderpack do Iris), que mantém as faíscas e os feixes de hoje.

## Desenho

### 1. O raio híbrido da esfera até cada Porta (modo 0)

- **Base:** o feixe cilíndrico com núcleo quente, halo e cometas da spec anterior continua.
- **Arcos em volta:** a fita do conduto fica mais larga, e o fragment desenha até **3 arcos** dentro dela. Cada arco é uma linha central deslocada por ruído em duas escalas (o desvio lateral cresce no meio e some nas pontas) com núcleo branco e halo na cor do tier. Cada arco existe em janelas curtas (um ciclo de ~0,3 a 0,7 s com duty de ~40%) que saltam ao longo do feixe, cada arco com a sua fase.
- **Pela faixa de vazão** (a faixa inteira suavizada, como hoje):
  - 1 e 2: só o feixe, sem arcos.
  - 3 e 4: 1 ou 2 arcos enrolando.
  - 5 e 6: 3 arcos e, de tempos em tempos (a cada ~1,5 a 3 s), uma **descarga**: um arco grosso, de um instante só, que sai da esfera e bate direto na Porta, com 1 ou 2 ramificações finas.
  - 7: a descarga quase contínua, em sequência.
- **Fora de sintonia** os arcos falham, piscam e perdem a continuidade (a probabilidade de cada janela existir cai com a sintonia). Com o **buffer cheio**, a janela fica mais longa e os arcos mais claros.
- **Longe** (acima de 20 blocos) só o feixe base: os arcos ficam de fora.

### 2. Os cristais alimentam a esfera (modo 4 novo)

- Um arco fino de cada cristal vivo até a borda da esfera, no lugar dos feixes retos de `ChamberCrystals.beams` (que passam a valer só para o desenho antigo).
- O arco **dispara** em rajadas: sobe rápido e decai em ~0,25 s. Em sintonia os disparos são regulares e quase juntos; fora dela, cada cristal no seu ritmo (a mesma lógica da fase por semente que o código usa para o balanço). A taxa fica entre 0,6 e 1,6 disparos por segundo, subindo com a sintonia.
- O modo 4 leva a sintonia e a semente do cristal no parâmetro (o mesmo empacotamento do modo 2, que cabe em `short`), e o tempo do último disparo sai do próprio relógio do shader pelo tempo e pela semente.
- Só o Opaco e o rachado não disparam, como hoje.

### 3. Arcos ociosos

- Pelo relógio: a cada 2 a 6 s (o intervalo sorteado por um hash do índice da janela de tempo), a esfera solta um arco curto de 0,15 a 0,3 s para um alvo: um cristal vivo, ou um ponto de uma parede interior da câmara (a caixa 3 × 3 × 3 em torno do centro), também sorteado pelo hash. No máximo **um** arco ocioso ativo por câmara.
- Mais frequente com o buffer cheio; só aparece dentro de ~24 blocos.
- Usa o mesmo modo 4 da fita (o alvo de parede não precisa de cristal). Sem alocação: o alvo sai de aritmética com hash, não de uma lista nova.

### 4. O som (cliente)

- Um som do vanilla por evento, tocado pelo cliente na posição do Núcleo (a atenuação por distância é a do próprio jogo):
  - **Descarga** (o instante em que ela começa): um estalo grave e curto, o impacto do raio do vanilla com volume baixo e tom alto, escolhido por teste de ouvido; volume pela faixa de vazão.
  - **Disparo de cristal:** um tilintar de ametista leve, com o tom seguindo a sintonia.
- Limites: no máximo **2 sons por segundo por Núcleo**, e nenhum a mais de 24 blocos. O instante de cada evento vem do mesmo relógio do shader, calculado em Java a cada tick novo (não por quadro).
- **Opção desligar:** `ClientConfig` ganha uma seção `effects` com `arcSounds` (padrão ligado), como o `theme` da `gui`; sem nada do pacote `client`, para o servidor dedicado carregar a classe.

## Custo (wa-performance)

- **Parado e longe:** igual a hoje. Nada de arcos além de 20 blocos e nenhum som além de 24 blocos; a câmara só desenha com o Núcleo montado e perto.
- **CPU por quadro:** as fitas do conduto são as mesmas (uma por Porta que entrega); os cristais viram até 8 fitas finas no lugar de 8 segmentos; 1 arco ocioso. Sem alocação, com os rascunhos de `float` que o `RibbonQuad` já usa.
- **GPU por quadro:** o fragment do conduto faz até 3 arcos com 2 ruídos cada; as fitas finas dos cristais, 1 ruído. O teto é ~1,5× o tempo de quadro da base e do fim da Parte A; acima disso, cortam-se arcos.
- **Som:** uma checagem por tick novo por Núcleo; no máximo 2 sons por segundo.
- **Rede e memória:** nada novo.
- **Varredura:** `varrer_perf.py` nos arquivos Java mudados (`FlowConduit`, `ChamberCrystals`, `ChamberScene`, o novo `EnergyArcs`, `ClientConfig`).

## Verificação

- JUnit para a lógica pura (a janela do arco, a taxa de disparo, o limite de sons por segundo e o sorteio do alvo ocioso pelo tempo), sem classes do Minecraft.
- e2e `WA_E2E_ONLY=ressonador`: as capturas `ressonador-camara-fluxo` e `-fluxo-alto` (hoje ambas na faixa 3: o plano garante uma captura em cada degrau relevante, faixas 3, 5 e 7) e uma nova dos cristais disparando. O `result.txt` traz o tempo de quadro.
- O som não se confere por captura: o plano inclui um passo que conta no cliente os eventos de som disparados em um intervalo dado, para provar o limite de 2 por segundo.
- O aceite visual e do som é do dono, por portões, como nas partes anteriores.
- Com shaderpack do Iris, o desenho continua o de hoje (`forceLegacyForTests` cobre).

## Riscos

- Os arcos em shader dependem de ajuste fino de largura, de amplitude e de contraste para não virarem ruído visual: algumas rodadas de captura.
- Como o e2e roda com o jogo parado no tempo, os arcos dependem do relógio (`GameTime`): a captura pega um instante qualquer. O plano prevê fixar o tempo de captura (ou capturar mais de um instante) para o portão visual.
- O som é subjetivo: os sons do vanilla podem não ficar bons; o dono escolhe e, se não gostar, o `arcSounds` desliga.
