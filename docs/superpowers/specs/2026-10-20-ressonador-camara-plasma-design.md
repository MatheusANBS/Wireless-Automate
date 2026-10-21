# Ressonador de Sinal: câmara com Núcleo de plasma (shader próprio)

Escolhido pelo dono em 20/10/2026 entre três estilos do protótipo [`docs/preview/ressonador-camara-shader.html`](../../preview/ressonador-camara-shader.html) (aba **Núcleo de plasma**). Muda só o visual da câmara animada do Núcleo montado (`client/resonator/render/`); nada no servidor, no protocolo ou no NBT.

## Por que

Hoje a câmara é feita de linhas finas (o traço de Lissajous), uma textura de 32 px (a placa de Chladni), faíscas e um halo aditivo. De longe fica fina e sem brilho. Os mods recentes com efeitos bonitos (Veil, Lodestone, Photon, Effekseer) desenham por shader. Um shader GLSL próprio dá o mesmo tipo de resultado sem dependência nova: o efeito é calculado por pixel, nítido a qualquer distância.

## O que muda

- **Núcleo de plasma** no centro da câmara: um quad virado para a câmera do jogador (billboard), desenhado por um **core shader** do mod (`wirelessautomate:resonator_plasma`), com o mesmo GLSL do protótipo portado para o GLSL 150 do Minecraft:
  - esfera de plasma de ruído em movimento (fbm), da cor do cristal (coral) com a borda da cor do tier;
  - a curva de Lissajous como traço de luz com brilho suave, no plano do billboard;
  - um ponto de luz por cristal carregado, orbitando, com um feixe fraco até o centro quando em sintonia;
  - fora de sintonia, o plasma treme, a curva se desfaz em chiado e a imagem cintila.
- **Substitui** o `LissajousTrace` e a `ChamberLens` (lente, clarão e halo). **Continuam:** os cristais nas câmaras (`ChamberCrystals`), as faíscas e os grãos de redstone (`ChamberSparks`, `ChamberParticles`) e a placa de Chladni no chão (`ChladniPlate`), que combinam com o plasma.
- **Entradas do shader** (uniforms, todas já no cliente): tempo (`gameTime + partialTick`), sintonia (0 a 1, a faixa da `ChamberView` sobre 7, suavizada pela `ChamberScene`), cor do tier (`GuiPaint.tierColor`), cristais carregados (0 a 8, pelos bytes da `ChamberView`) e um nível de detalhe pela distância.
- **Com um shaderpack do Iris ativo** (`net.irisshaders.iris.api.v0.IrisApi`, consultado por reflexão, sem dependência de compilação), a câmara volta ao desenho de hoje (traço e lente). Se o teste com shaderpack mostrar que o plasma renderiza bem, essa volta pode sair.

## Custo de performance

- **Servidor e rede:** nada.
- **CPU por quadro:** um quad por Núcleo visível (até 64 blocos, como hoje) e o envio de 6 uniforms. Nada alocado por quadro (os uniforms e o quad reaproveitam campos).
- **GPU:** o custo é por pixel coberto pelo quad. O fragment shader descarta fora do círculo do efeito, usa 48 amostras da curva (64 no protótipo) e um ruído de 4 oitavas; com a distância, o nível de detalhe corta amostras e oitavas. Medir com o e2e: o tempo de quadro com 1 e com 4 Núcleos à vista, antes e depois, na mesma máquina.

## Fora desta spec

- Bloom de verdade (pós-processamento) e integração opcional com o Veil.
- Mudar a placa de Chladni, os cristais ou as partículas.
- A tela do Núcleo e o JEI (a vista 3D do JEI não desenha a câmara).
