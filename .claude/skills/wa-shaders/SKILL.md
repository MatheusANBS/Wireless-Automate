---
name: wa-shaders
description: Como fazer animações com core shaders no Wireless Automate (NeoForge 1.21.1, sem GeckoLib nem Veil) com qualidade alta - o método (referência, fórmula, bloco de teste, e2e, iterar), as técnicas que funcionaram (raio fractal, esfera 3D, cel-shading, halo) e as que deram errado (tubo com borda, aura borrada, gema por ruído), as armadilhas de GLSL e do pipeline do Minecraft e o e2e de captura. Use ao criar ou mexer em qualquer shader, fita, billboard ou animação da câmara do Ressonador, quando o dono falar em "shader", "animação", "efeito", "raio", "brilho", "borrado", "baixa qualidade" ou "poluído".
---

# wa-shaders

Animação do mod = **core shader próprio** (`assets/wirelessautomate/shaders/core/*.fsh|vsh|json`) + um **BER** que monta quadrados sem alocar (`RibbonQuad`, `PlasmaCore`, `CrystalGem`). Sem GeckoLib, sem Veil. Antes de qualquer coisa leia `wa-performance` (parado custa zero, sem alocação por quadro) e o mapa `client/resonator/render/` no `CLAUDE.md`.

## Método (nesta ordem)

1. **Pesquisar a técnica certa** antes de escrever (uma busca por tipo de efeito: "lightning bolt shader", "fractal noise", "SDF", "raymarch sphere"). O erro que mais custou foi começar pelo que parecia óbvio (tubo com borda) e só pesquisar depois da reprovação.
2. **Dizer a fórmula em uma frase** (ex.: "eixo fractal por ruído 1D linear + brilho `k/(d+ε)` + núcleo HDR") e o que **não** terá (borda, contorno, névoa larga).
3. **Escrever o efeito como função pura** (`boltLight(d, wc)`), com os parâmetros do mundo vindo do vértice (`param` em UV2) e o tempo de `GameTime`.
4. **Capturar no e2e** (ver abaixo), olhar a imagem, ajustar **no máximo 3 rodadas** por efeito e só então mostrar ao dono (`SendUserFile`). Nada de commit antes do "aprovado".
5. **Medir** (`result.txt`: `camara antiga` × `camara plasma` na mesma rodada) e manter ≤ ~1,5× a base.

## O que dá qualidade (comprovado)

- **Raio**: eixo deslocado por ruído 1D com interpolação **linear** (reta quebrada, não senoide) em 4 a 5 oitavas (frequência ×2,1, amplitude ×0,5), preso a zero nas pontas por `pow(sin(pi x), 0.7)`; distância **corrigida pela inclinação** (`/sqrt(1+k²)`, `aspect = comprimento/meia largura`) para a espessura não variar; sorteio de novo a 11 a 17 Hz (`floor(t*rate)`), cada sorteio acende e decai. Luz = **núcleo gaussiano estreito em HDR** (`exp(-d²/wc²)*4`, o tonemap leva ao branco) + brilho lorentziano `g = k/(d+k)` ao quadrado + halo largo e fraco. Secundários mais finos e soltos, forquilhas que afinam, descarga que alarga o principal por 0,12 período.
- **Esfera/plasma**: interseção analítica no fragment, convecção por ruído 3D, halo exponencial só **fora** da esfera (somar dentro estoura em branco), cor por rampa de temperatura com stops saturados.
- **Cristal**: cel-shading de 5 tons da paleta do sprite, contorno por `1-|N·V|`, cor por tier; nada de ruído para "parecer gema".
- **Tudo**: tonemap ACES + **dither** no fim (`wa_tonemap`, `wa_dither`), ruído e hash do `wa_common.glsl`; fade de borda do quad **largo** (`smoothstep(0,0.14,x)` e `(1-smoothstep(0.25,1,|y|))²`), senão aparece um retângulo de luz cortado.

## O que foi reprovado (não repetir)

- Tubo cilíndrico com borda e fios trançados: "borda estranha". Raio **não tem contorno**.
- Aura/halo com cauda longa: "borrado". Cauda curta, brilho que cai com a distância.
- Gema por ruído procedural: parecia outra coisa. Seguir o sprite do mod em 3D.
- Poluição: elementos demais ao mesmo tempo (curvas que giram, linhas ligando o cristal à estrela, faíscas, órbitas). **Menos elementos, mais qualidade.** Cada elemento novo precisa de um motivo de gameplay (mostra vazão, carga, sintonia, recarga).

## Armadilhas

- **Um shader que não compila derruba a câmara inteira para o desenho antigo** (log: "não compilou"). Sempre `grep -c "Couldn't compile\|não compilou" run/e2e/client.log` = 0 depois do e2e.
- Palavras reservadas do GLSL: `packed`, `input`, `output`, `sample`, `filter`... (use `bits`, `arg`).
- `#version 150`: sem `uint` em shifts misturados com `int`; hash inteiro com `uint` e converter no fim.
- `wa_h21(vec2)` faz `floor` do argumento: para valor contínuo use `wa_noise`.
- UV2 do vértice é `short` (< 32768): empacotar com bits (faixa 3, sintonia 4, cheio 1, comprimento em décimos 7).
- Tempo: `t = GameTime*1200` no shader (0 a 1200 s, repete); o Java usa `(gameTime % 24000)/20`. Alinhar se o som tiver de bater com o visual (`ArcClock`).
- Blend aditivo (`SRC_ALPHA, ONE`) soma com a esfera: o que nasce dentro dela precisa de fade de entrada ou estoura.
- Shaderpack do Iris em uso → tudo no desenho antigo (`ShaderPackCheck`); não tentar contornar.

## e2e de captura

```bash
WA_E2E_ONLY=ressonador WA_E2E=run/e2e ./gradlew runClient   # capturas em run/e2e/*.png e result.txt
```

No Windows use o script do scratchpad (inicia o cliente, espera o `result.txt` e mata o java). Ganchos só de teste: `ResonatorRenderer.forceLegacyForTests`, `forceBandForTests` (-1 = vazão real). Câmera de perto: `E2eResonatorSteps.CLOSE_VIEW`, volta à `WINDOW_VIEW`. Tempos oscilam 0,8 a 5 ms entre rodadas: compare sempre dentro da mesma rodada. Leia as imagens com `Read` e descreva o que vê antes de ajustar.

## Fim de cada efeito

`varrer_perf.py`, `wa-comentarios`, `./gradlew build`, e2e final com tempos, e atualizar `CLAUDE.md` e `docs/progresso.md`.
