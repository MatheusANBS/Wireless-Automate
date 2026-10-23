# Armadilhas do pipeline, do GLSL e do e2e

## GLSL e core shaders (Minecraft 1.21.1)

- Um core shader que não compila fica `null`, e quem depende dele cai no caminho alternativo; sempre conferir o log depois do e2e: `grep -c "Couldn't compile\|não compilou"`.
- `#version 150`: `inverse()` e `round()` existem; cuidado com `uint` e `int` misturados em shifts; um construtor `vec3(vec3, float)` dá "too much data in type constructor"; nome repetido na mesma função ("declaration conflicts") derruba o shader.
- Palavras reservadas: `packed`, `input`, `output`, `sample`, `filter`.
- UV2 do vértice é `short` (< 32768): empacotar por bits.
- Tempo: `GameTime × 1200` no shader (0 a 1200 s); no Java, `(gameTime % 24000 + partialTick) / 20`, para os dois baterem.
- `ScreenSize`, `ModelViewMat` e `ProjMat` chegam ao shader se declarados no JSON; uniform próprio (`ViewRot`, etc.) é global: serve para o que é igual para todos os objetos no quadro (a câmera), não para dado por objeto.
- O `RenderType` padrão liga o framebuffer principal no setup; para desenhar noutro alvo, um `OutputStateShard` próprio.
- No estágio `AFTER_PARTICLES` do `RenderLevelStageEvent`, com os gráficos Fabulous, o alvo aberto é o das partículas (`levelRenderer.getParticlesTarget()`), não o principal.
- Billboard virado para a câmera: o que deve ficar parado no mundo precisa do inverso da rotação da câmera no shader.
- Blend aditivo não esconde nada atrás: um corpo que deve cobrir precisa de alfa pré-multiplicado e de ser desenhado depois do que cobre.

## e2e de captura (Windows)

- O cliente roda dentro de `run/`, então `WA_E2E=run/e2e-x` grava em `run/run/e2e-x`.
- Um script no scratchpad roda `./gradlew runClient` com `WA_E2E_ONLY`, e o cliente fecha sozinho no fim; um cliente por vez (~3 GB cada).
- A abertura do e2e deixa uma tela aberta: feche antes das capturas no mundo.
- A janela é real: o mouse (inclusive o do dono) gira a câmera. Solte o mouse (`mouseHandler.releaseMouse()`) e reaplique o ângulo da câmera a cada tick até a foto; o `capture` padrão leva o mouse ao canto.
- Gancho de teste para parar o tempo do efeito numa fase, e captura por fase, para comparar com a referência.
- Tempo de quadro: 120 quadros sem vsync e sem limite de FPS, sempre comparando dentro da mesma rodada (oscila 0,8 a 5 ms entre rodadas).
- O `gerar_texturas.py` reescreve dezenas de PNGs nesta máquina (versão do PIL): commitar só o que a mudança pede.
- Heredoc com aspas e `$` no bash do Windows quebra fácil: escreva o script Python num arquivo do scratchpad e rode-o.
