# Armadilhas do pipeline, do GLSL e do e2e

## GLSL e core shaders (Minecraft 1.21.1)

- Um core shader que não compila fica `null`, e o tipo de desenho que depende dele some. Confira o log depois de cada rodada: `grep -c "Couldn't compile"`.
- **`#version 150`:**
  - `inverse()` e `round()` existem.
  - Cuidado com `uint` e `int` misturados em shifts.
  - `vec3(vec3, float)` dá "too much data in type constructor".
  - Nome repetido na mesma função ("declaration conflicts") derruba o shader.
- Palavras reservadas: `packed`, `input`, `output`, `sample`, `filter`.
- UV2 do vértice é `short` (< 32768): empacote por bits.
- `ScreenSize`, `ModelViewMat` e `ProjMat` só chegam ao shader se estiverem declarados no JSON.
- Uniform próprio é global: serve para o que é igual no quadro inteiro (a câmera), não para dado de cada objeto.
- O corte de alfa do vanilla (0,1) recorta degradê macio: no nosso `fx`, use `AlphaCut` baixo.
- A névoa no aditivo mistura cor e apaga o brilho: os tipos aditivos não têm névoa.
- **Placas Intel e Apple:** `NaN` e `inf` viram quadrado preto. Guarde normalizações (vetor zero) e divisões, e use `highp` onde entra profundidade.

## Pipeline

- O `RenderType` padrão liga o framebuffer principal no setup: para desenhar noutro alvo, um `OutputStateShard` próprio.
- No estágio `AFTER_PARTICLES`, com os gráficos Fabulous, o alvo aberto é o das partículas (`levelRenderer.getParticlesTarget()`), não o principal.
- Com Fabulous, a cadeia de transparência (`PostPass.process`) limpa o alvo principal, cor e profundidade, antes de compor: quem lê a profundidade do principal em `AFTER_LEVEL` só vê 1,0. Guarde a profundidade em `AFTER_WEATHER` (o último estágio antes da cadeia) e devolva o alvo do clima como aberto depois do blit. Limitação conhecida: essa cópia provavelmente não traz os translúcidos (água e vidro), que têm alvo próprio, então a luz pode ignorar um vidro ou uma água na frente de uma superfície (não medido).
- **Billboard:** o que deve ficar parado no mundo precisa do inverso da rotação da câmera.
- **Aditivo não esconde nada atrás:** um corpo que deve cobrir precisa de alfa pré-multiplicado e de ser desenhado depois do que cobre, ou de um tipo sólido que escreva profundidade.
- **Com shaderpack:**
  - Nada na passada de sombra.
  - O desenho tardio precisa da profundidade salva antes da composição do pacote; sem ela, o efeito aparece através das paredes.
  - Teste com Complementary e com BSL.
- Tecla do mod no R conflita com o recarregar shaders do Iris: deixe as teclas do mod caladas fora do contexto delas.

## e2e de captura (Windows)

- O cliente roda dentro de `run/`, então `WA_E2E=run/e2e-x` grava em `run/run/e2e-x`.
- Um cliente por vez (~3 GB cada); o cliente fecha sozinho no fim.
- A abertura do e2e deixa uma tela aberta: feche-a antes das capturas no mundo.
- **A janela é real:** o mouse, inclusive o do dono, gira a câmera. Solte o mouse (`mouseHandler.releaseMouse()`) e reaplique o ângulo da câmera a cada tick até a foto.
- Use um gancho de teste para parar o tempo do efeito numa fase e capture uma foto por fase, para comparar com a referência.
- **Tempo de quadro:** 120 quadros sem vsync e sem limite de FPS, sempre comparando dentro da mesma rodada (oscila de 0,8 a 5 ms entre rodadas).
- O `gerar_texturas.py` reescreve dezenas de PNGs nesta máquina (versão do PIL): commite só o que a mudança pede.
- Heredoc com aspas e `$` no bash do Windows quebra fácil: escreva o script Python num arquivo do scratchpad e rode-o com `python -I`. O `-I` ignora o `PYTHONIOENCODING`: use `sys.stdout.reconfigure(encoding='utf-8')` no script.
- Não abra o jogo em série para ajustar no olho: pense, rode uma vez e mostre (memória "pensar antes de rodar").
- **A mão e a luz no mundo:** sem pacote a luz poupa a mão (a passada vem antes dela); com pacote a profundidade é salva antes de a mão ser desenhada e o Iris não dá máscara dela, então a luz e o desenho tardio podem cair por cima da mão perto do sol. Não é bug: decisão registrada em `docs/mapa/efeitos.md`.
