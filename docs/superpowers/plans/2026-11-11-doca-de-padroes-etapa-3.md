# Doca de Padrões: plano de implementação (etapa 3: a arte animada)

> **Para agentes:** SUB-SKILL OBRIGATÓRIA: use superpowers:subagent-driven-development (recomendado) ou superpowers:executing-plans. Um implementador por vez; revisão de cada tarefa antes da seguinte. Esta etapa segue a skill `wa-shaders` (decompor a referência, comparar técnicas, conferir lado a lado, mostrar ao dono antes do commit final).

**Objetivo:** a Doca no jogo como nos GIFs aprovados (`docs/preview/doca-de-padroes-repouso.gif` e `-trabalhando.gif`): o circuito dos painéis animado na cor do tier, o giroscópio (três anéis e o cristal) girando no miolo, e no laço de trabalho o pacote que entra pela vigia do lado ativo, o cristal que o absorve e os fragmentos em órbita. Mais as etiquetas dos lados legíveis de longe.

**Arquitetura:**
- **Circuito:** textura animada (`.mcmeta`) por tier, gerada pelo `scripts/textures/doca_modelo.py`: o desenho fixo e o brilho de cada traço oscilando entre a sombra, a lente e o brilho do tier, numa onda diagonal a cada 3 s (duas voltas por laço de 6 s). Custo zero no código.
- **Giroscópio, pacote e fragmentos:** um `BlockEntityRenderer` por fórmula a partir do `gameTime` (sem arquivo de animação), como os do Reparador e do Ressonador; umas 15 caixas móveis; só a menos de 64 blocos; sem alocação por quadro; `getRenderBoundingBox` do bloco.
- **"Trabalhando" no cliente:** sem ticker e sem sincronia por tick. O servidor manda um evento de bloco (`level.blockEvent`) quando um lado entrega ou devolve, no máximo um a cada 100 ticks por Doca, com o lado; o cliente guarda o tick e o lado e desenha o laço de trabalho enquanto o último evento tiver menos de 15 s (300 ticks), a mesma regra do "trabalhando" da tela. A velocidade dos anéis acelera e desacelera em ~1 s; o pacote só entra no começo de um laço.

**Decisões do dono (11/11/2026):** a vigia de 6 × 6 ou 8 × 8 decide-se pelo GIF do jogo, lado a lado; sem vidro na vigia; trabalha enquanto algum lado entregou ou devolveu nos últimos 15 s, com o pacote pela vigia do último lado; a passagem entre repouso e trabalho é suave, sem animação nova; as etiquetas dos lados ficam maiores nesta etapa; a aprovação é por GIF gravado do jogo antes do commit final.

**Referências:** a seção "Arte e animação da Doca" de `docs/superpowers/specs/2026-11-10-ae2-rs2-proposta.md` (o modelo, os tempos dos laços, o circuito), `docs/preview/doca-de-padroes.bbmodel` (as duas animações em keyframes), os GIFs, `docs/identidade-visual.md`, a spec da Doca.

## Restrições globais

Valem as restrições globais das etapas 1 e 2 (idioma, cliente só em `client/`, performance, tamanho, comentários, GameTests, commits com o autor do dono e o committer Claude, sem atribuição), mais:

- **Classes de render só em `client/dock/render/`**, registradas no `ClientSetup`; nada referenciado por código comum.
- **Por quadro, só desenhar:** nada de alocação, nada de busca no mundo; o estado vem do block entity (o tick e o lado do último evento) e do `gameTime` com o `partialTick`.
- **Distância:** o renderer só desenha a menos de 64 blocos; além disso fica o modelo estático com o circuito animado.
- **Protocolo:** o evento de bloco não é payload; `ModPayloads.VERSION` não muda. Nenhuma chave nova de NBT (o tick do último evento não é salvo).
- **Iris/Oculus e Embeddium/Sodium:** o renderer usa os `RenderType` do vanilla (sólido e translúcido de entidade), sem core shader próprio; conferir que nada some com um shaderpack (a nota da `wa-shaders`).
- **Verificação:** `./gradlew build runGameTestServer runGameTestServerAe2 runGameTestServerRefined` (e as outras runs no último commit), `./scripts/e2e.sh --only doca --capture doca` com o tempo parado nas fases, e a medição de quadro (`references/medir.md` da `wa-performance`: 50 Docas à vista, antes e depois).

## Tarefas

### Tarefa 1: decompor a referência e escolher as técnicas

- [ ] `.claude/skills/wa-shaders/scripts/quadros.py` nos dois GIFs: a folha de quadros e a tabela da linha do tempo (cada peça, quando nasce e some, rotação, escala, cor), conferida contra os keyframes do `.bbmodel`.
- [ ] Para cada elemento (circuito, anéis, cristal, pacote, fragmentos), pelo menos duas técnicas comparadas (por exemplo: textura animada × renderer para o circuito; renderer por fórmula × GeckoLib × modelo animado do NeoForge para o giroscópio), com custo, fidelidade e compatibilidade, numa seção nova da spec ("A arte no jogo"). A escolha esperada é a da arquitetura acima; se a comparação apontar outra, parar e perguntar ao dono.
- [ ] Commit: "Doca: a decomposição da animação e a escolha das técnicas".

### Tarefa 2: o circuito animado e as etiquetas

- [ ] `scripts/textures/doca_modelo.py`: a textura por tier vira animada (quadros e `.mcmeta`, a onda diagonal de 3 s nas três cores da lente do tier, interpolada); a luz cheia só nos traços se der sem elemento a mais (pendência da etapa 1), senão como está.
- [ ] As etiquetas dos lados maiores e legíveis de longe (as cores do `DockColors`), sem passar da moldura; conferir de 8 e 16 blocos.
- [ ] Gerar as duas vigias (6 × 6 e 8 × 8) por uma chave do script, para o GIF da tarefa 5; o modelo padrão continua 6 × 6 até o dono decidir.
- [ ] e2e: capturas de perto e de longe; `python -I scripts/textures/gerar_texturas.py` só muda os arquivos da Doca.
- [ ] Commit: "Doca: o circuito animado por tier e as etiquetas maiores".

### Tarefa 3: o sinal "trabalhando"

- [ ] O servidor manda `level.blockEvent(pos, bloco, EVENT_WORK, lado)` quando um lado entrega (a `DockDelivery`) ou devolve (a `DockReturn`), no máximo um a cada 100 ticks por Doca (um `long` no block entity, não salvo); o `triggerEvent` do block entity no cliente guarda o tick e o lado.
- [ ] Um método puro (JUnit) que, do tick atual, do tick do último evento e do `partialTick`, dá a fase do laço, se está trabalhando (menos de 300 ticks) e o fator de velocidade suave (rampa de ~1 s).
- [ ] GameTest: uma entrega dispara o evento; duas entregas no mesmo intervalo, um evento só.
- [ ] Commit: "Doca: o sinal de trabalho para o cliente".

### Tarefa 4: o renderer

- [ ] `client/dock/render/PatternDockRenderer` (e as peças em classes pequenas pela `wa-modular`): os três anéis quadrados (latão, porcelana e grafite claro), um em cada eixo, nas velocidades do repouso (metade) e do trabalho (duas voltas, uma e uma por laço, sentidos alternados; o de latão balançando 10°), o cristal na cor da lente do tier respirando; no trabalho, o pacote de latão com fita de porcelana entrando pela vigia do lado do último evento (0,2 a 1,2 s), o cristal absorvendo e inchando (1,05 a 1,45 s), os quatro fragmentos em órbita (1,4 a 2,3 s), saindo e sumindo (2,3 a 2,8 s), voltando em espiral (3,3 a 4,4 s) e o pacote voltando à vigia (4,6 a 5,8 s) — os tempos da proposta, conferidos na tabela da tarefa 1.
- [ ] Os laços emendam (diferença zero entre o fim e o começo de cada peça, conferida em número, como no Blockbench); a rampa da velocidade não dá pulo de ângulo.
- [ ] Ganchos do e2e para parar o tempo numa fase (como os do Reparador) e capturas nas fases da tabela; `.claude/skills/wa-shaders/scripts/lado_a_lado.py` contra os quadros da referência; corrigir a maior diferença primeiro.
- [ ] Medição de quadro com 50 Docas à vista, antes e depois, no relatório.
- [ ] Commit (só depois do ok da tarefa 5): "Doca: o giroscópio, o pacote e os fragmentos no renderer".

### Tarefa 5: o GIF para o dono

- [ ] Gravar do jogo, com o tempo avançando quadro a quadro pelo gancho do e2e: um GIF do repouso e um do trabalhando (6 s cada, a 20 quadros por segundo), com a vigia de 6 × 6 e com a de 8 × 8, e uma folha lado a lado com os GIFs do Blockbench. Mandar ao dono e **parar**: o commit do renderer e a escolha da vigia esperam o "aprovado".
- [ ] Aplicar a escolha da vigia e o que o dono pedir; commit.

### Tarefa 6: fechamento

- [ ] A spec (seção "A arte no jogo", estado da etapa 3), os mapas (`doca.md`, `scripts-e-recursos.md`, `cliente-e-pacotes.md`), `docs/progresso/` (histórico, estado, pendências, próximo passo: etapa 4).
- [ ] Revisão final da etapa (modelo mais capaz) e correções.
- [ ] Commit: "Doca de Padrões: fechamento da etapa 3".
