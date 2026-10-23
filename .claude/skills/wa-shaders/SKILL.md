---
name: wa-shaders
description: Como fazer animações com core shaders no Wireless Automate (NeoForge 1.21.1, sem GeckoLib nem Veil) com qualidade alta e sem poluição - pesquisa obrigatória com critérios de fonte, as exigências antes de escrever e antes de mostrar ao dono, o raio fractal como único exemplo aprovado, o que foi reprovado, técnicas de referência por tipo de efeito, armadilhas de GLSL e do pipeline do Minecraft, orçamento de performance e o e2e de captura. Use ao criar ou mexer em qualquer shader, fita, billboard ou animação da câmara do Ressonador, e quando o dono falar em "shader", "animação", "efeito", "raio", "brilho", "borrado", "baixa qualidade", "poluído" ou "refazer a animação".
---

# wa-shaders

Animação do mod = **core shader próprio** (`assets/wirelessautomate/shaders/core/*.fsh|vsh|json`) + um **BER** que monta quadrados sem alocar (`RibbonQuad`, `PlasmaCore`, `CrystalGem`). Sem GeckoLib, sem Veil, sem dependência nova. Leia antes `wa-performance` (parado custa zero, sem alocação por quadro) e o mapa de `client/resonator/render/` no `CLAUDE.md`.

## Única referência aprovada: o raio

O raio da esfera à Porta de Energia (modo 0 de `resonator_ribbon.fsh`, `FlowConduit`, commit `cf470fba`) é o **único** efeito que o dono aprovou como "perfeito". Todo efeito novo é comparado com ele: se não está no mesmo nível de nitidez, limpeza e leitura, não está pronto. Esfera, cristais, aura, espiral e o resto **não são exemplo**: foram aceitos como "bons o bastante" ou estão marcados para refazer (`docs/superpowers/specs/2026-10-23-camara-limpeza-design.md`). Não copie o estilo deles.

### Por que o raio deu certo (a receita)

- **Pesquisado antes**, depois de uma reprovação por ter começado do óbvio (tubo com borda).
- **Fórmula dita em uma frase**: eixo fractal por ruído 1D linear + brilho `k/(d+ε)` + núcleo HDR. E o que ele **não** tem: borda, contorno, névoa larga.
- **Eixo**: ruído 1D com interpolação **linear** (reta quebrada, não senoide), 4 a 5 oitavas (frequência ×2,1, amplitude ×0,5), preso a zero nas pontas por `pow(sin(pi x), 0.7)`.
- **Distância corrigida pela inclinação** (`/sqrt(1+k²)`, `aspect = comprimento/meia largura`): a espessura não varia nos trechos inclinados.
- **Luz em três camadas**: núcleo gaussiano estreito em HDR (`exp(-d²/wc²)*4`, o tonemap leva ao branco), brilho lorentziano `g = k/(d+k)` ao quadrado, halo largo e fraco. O tier só colore o brilho, nunca o núcleo.
- **Vida**: sorteio de novo a 11 a 17 Hz (`floor(t*rate)`), cada sorteio acende e decai; secundários mais finos e soltos, forquilhas que afinam, descarga que alarga o principal por 0,12 do período. Cada camada responde a **um dado do jogo** (faixa de vazão, sintonia, buffer cheio).
- **Fade de borda do quad largo** (`smoothstep(0,0.14,x)`, `(1-smoothstep(0.25,1,|y|))²`): sem isso aparece um retângulo de luz cortado.
- **Fechamento**: tonemap ACES + dither no fim (`wa_tonemap`, `wa_dither`).

## Pesquisa: obrigatória e com critério

Antes de escrever a primeira linha de um efeito novo, faça **pelo menos 3 buscas** de ângulos diferentes (a técnica, a matemática e uma implementação de referência) e registre no spec do efeito: o que achou, o que adotou e o que descartou e por quê.

**Fontes, em ordem de confiança:** (1) Shadertoy, Inigo Quilez (iquilezles.org), The Book of Shaders, GPU Gems/GPU Pro, artigos de GDC e SIGGRAPH; (2) código de outros mods e jogos com o resultado visível, só como referência de resultado, **nunca copiar** código com licença incompatível; (3) tutoriais e fóruns, só para confirmar o que (1) diz. Resposta de IA, post sem código ou sem imagem do resultado não vale como fonte.

**Critérios para aceitar uma técnica:**
- Roda em **GLSL 150** dentro do pipeline do Minecraft 1.21.1 (um quad com uniforms e UVs), sem compute, sem textura nova, sem passe extra (`DynamicTexture` e framebuffers estão fora).
- Tem **custo conhecido** (loops com teto, oitavas fixas) e cabe no orçamento abaixo.
- Mostra **um dado do jogo**. Se não, é decoração, e decoração polui.
- Tem **resultado visível** na referência (imagem ou demo), não só a fórmula.
- Funciona **de perto e de longe** (a câmara é vista a 1 bloco e a 20).

**Quando pesquisar de novo:** o dono reprovou o visual, o efeito saiu "borrado", "baixa qualidade" ou "estranho", ou a técnica escolhida precisou de mais de 3 rodadas de ajuste. A resposta nunca é só mexer em constantes: volte à pesquisa e troque a técnica.

## Exigências (portões)

**Antes de escrever:**
1. Os 3 achados da pesquisa registrados no spec.
2. A fórmula em uma frase e a lista do que o efeito **não** terá.
3. O dado do jogo que ele mostra e o que acontece em cada valor dele (ex.: faixas 0 a 7).
4. O custo estimado (iterações do loop, chamadas de ruído por pixel).

**Antes de mostrar ao dono:**
1. `grep -c "Couldn't compile\|não compilou" run/e2e/client.log` = 0 e o `result.txt` começa com `OK`.
2. Capturas **de perto e de longe**, em pelo menos **dois instantes** (o tempo muda o sorteio) e em **mais de um valor do dado** (ex.: faixas 7, 5 e 4).
3. Eu **li as imagens** (`Read`) e descrevi o que vi, procurando: borda, retângulo cortado, halo estourado em branco, banding, serrilhado, ruído que pisca demais, elementos sobrepostos.
4. Comparado com o raio: nitidez e limpeza no mesmo nível.
5. O tempo de quadro medido na mesma rodada que o desenho antigo (`camara antiga` × `camara plasma`).
6. No máximo **3 rodadas** de ajuste por efeito antes de mostrar; se não chegou, voltar à pesquisa (acima), não insistir.

**Nada de commit antes do "aprovado" do dono.** Cada elemento em commit próprio para reverter um de cada vez.

## Anti-poluição

O dono pediu menos elementos e mais qualidade. Regras:
- Um elemento novo só entra se mostrar um dado que nenhum outro já mostra. Dois elementos para o mesmo dado: fica o melhor.
- O dono pediu (23/10) que os cristais girem no próprio eixo (pela sintonia) e que o anel orbite a esfera devagar, para o de trás não ficar sempre atrás: esses dois ficam. Fora isso, nada que gira, orbita ou liga coisas só para "parecer vivo" (curvas girando, linhas cristal-estrela, auras com estrela, faíscas soltas já foram reprovadas como poluição).
- No máximo **um** efeito forte por vez na tela; os outros ficam discretos até o dado mudar.
- Estado parado (sem vazão, sem recarga) tem de ser calmo.

## Reprovado (não repetir)

- Tubo cilíndrico com borda e fios trançados: "borda estranha". Raio **não tem contorno**.
- Aura/halo com cauda longa: "borrado". Cauda curta, brilho que cai com a distância.
- Gema por ruído procedural: parecia outra coisa. Cristal segue o sprite do mod, em 3D.
- Esfera estourada em branco (halo somado dentro dela) e rosada (stops dessaturados): halo só **fora** da esfera, rampa de temperatura com stops saturados.
- Linhas 3D poligonais: frequência baixa e amplitude menor.
- Muitos elementos ao mesmo tempo (poluição).
- Cristais deitados apontando para a esfera, com um pulso de vai e vem, e anéis de onda na superfície da esfera (os "diapasões", 23/10): "horrível", tímido. O dono quer algo **épico, no nível do raio**: um efeito forte e criativo que ligue os cristais à esfera, não um detalhe discreto.

## Técnicas de referência por tipo de efeito

Ponto de partida a pesquisar, não receita pronta:
- **Raio/arco**: a do raio acima.
- **Esfera/plasma**: interseção analítica raio-esfera no fragment, convecção por ruído 3D deslocado no tempo, Fresnel na borda, rampa de temperatura.
- **Brilho/halo**: queda exponencial ou lorentziana por distância, nunca gaussiana larga; só fora do corpo que brilha.
- **Cristal/sólido**: normais por face, cel-shading de poucos tons da paleta do sprite, contorno por `1-|N·V|`.
- **Preenchimento (recarga, carga)**: limiar por altura no espaço do objeto com uma linha clara na frente, não gradiente borrado.
- **Pulso/anel**: `smoothstep` em torno de um raio que cresce no tempo, borda fina, some em 0,7 s.
- **Ruído**: gradiente 3D e fbm do `wa_common.glsl`; hash inteiro com `uint`.

## Armadilhas

- **Um shader que não compila derruba a câmara inteira para o desenho antigo** (log "não compilou"): sempre conferir o log depois do e2e.
- Palavras reservadas do GLSL: `packed`, `input`, `output`, `sample`, `filter` (use `bits`, `arg`).
- `#version 150`: cuidado com `uint` e `int` misturados em shifts.
- `wa_h21(vec2)` faz `floor` do argumento: para valor contínuo use `wa_noise`.
- UV2 do vértice é `short` (< 32768): empacotar por bits (faixa 3, sintonia 4, cheio 1, comprimento em décimos 7).
- Tempo: `t = GameTime*1200` no shader (0 a 1200 s, repete); o Java usa `(gameTime % 24000)/20`. Alinhar quando o som tem de bater com o visual (`ArcClock`).
- Blend aditivo (`SRC_ALPHA, ONE`) soma com o que está atrás: o que nasce dentro da esfera precisa de fade de entrada ou estoura.
- Shaderpack do Iris em uso: tudo no desenho antigo (`ShaderPackCheck`); não tentar contornar.
- Edição de arquivo com CRLF por script: preservar o `\r\n` (os scripts do scratchpad já fazem).

## Orçamento de performance

- Parado custa zero; nada alocado por quadro; só desenha perto (`RANGE_SQ`, `getViewDistance`).
- Teto do plasma completo: ~1,5× o desenho antigo, medido na mesma rodada.
- Loops com limite constante e saída antecipada (`discard` quando o pico < 0,003; `if (k >= n) break`).
- Oitavas de ruído fixas e poucas; nada de raymarch com passos variáveis.
- Rodar `python -I .claude/skills/wa-performance/scripts/varrer_perf.py` no fim.

## e2e de captura

```bash
WA_E2E_ONLY=ressonador WA_E2E=run/e2e ./gradlew runClient   # capturas em run/e2e/*.png e result.txt
```

No Windows, use um script no scratchpad que inicia o cliente, espera o `result.txt` e mata o java (`bash` + `powershell Stop-Process`). Ganchos só de teste: `ResonatorRenderer.forceLegacyForTests` e `forceBandForTests` (-1 = vazão real). Câmera de perto: `E2eResonatorSteps.CLOSE_VIEW`; volta à `WINDOW_VIEW`. Os tempos oscilam 0,8 a 5 ms entre rodadas: compare sempre dentro da mesma rodada. Um cliente por vez (cada um usa ~3 GB).

## Fim de cada efeito

`varrer_perf.py`, `wa-comentarios`, `./gradlew build`, e2e final com tempos, e atualizar `CLAUDE.md` e `docs/progresso.md`.
