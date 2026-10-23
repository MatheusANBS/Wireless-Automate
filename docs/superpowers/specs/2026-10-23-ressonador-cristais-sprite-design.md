# Ressonador: os cristais da câmara em pixel-art 3D (Parte A, revisada)

Data: 2026-10-23. **Substitui** o desenho de `2026-10-23-ressonador-cristais-gema-design.md` (malha de quartzo de 8 lados com shader de gema por ruído), que o dono recusou ao ver a primeira versão no jogo ("não gostei da direção"): o cristal 3D deve acompanhar os sprites que o mod já tem, só que em 3D. O resto daquela spec (parâmetro `CrystalParam` com o estado rachado, aura em estrela, captura dos quatro estados) continua valendo onde não conflita. Mesmo ramo, `ressonador-plasma-qualidade`. A Parte B (energia) segue como está.

## Os sprites que o 3D acompanha

Medidos nos PNGs de `assets/wirelessautomate/textures/item/`:

- **Cristal de Sinal:** aglomerado de 4 pontas de alturas diferentes (uma alta no meio, uma média à esquerda, uma curta à direita e uma pequena na frente), coral, com a face da esquerda clara, a do meio média e a da direita escura, sobre uma base mais escura. Paleta: contorno `#0F0E12`, sombra `#8A3A2C`, escura `#C9604B`, média `#F2917A`, clara `#FFC9B8`, brilho `#FFF0EA`.
- **Lapidado:** losango com cinta (um octaedro com uma faixa no meio), na mesma paleta coral, faixa dourada `#F7E3A1` e `#DDBC5C`.
- **Afinado (um por tier, do Elite ao Ultimate):** o mesmo losango, com a faixa e duas marcas em cruz nos polos na cor do tier. A cor é a `GuiPaint.tierColor` do tier do cristal: Elite `#1FA9A0`, Esmeralda `#22A84E`, Allthemodium `#E8740A`, Vibranium `#18B57A`, Unobtainium `#B23FD0`, Ultimate `#7B4FE0`.
- **Opaco:** a mesma forma em branco-osso: `#5E5952`, `#8C857C`, `#B5ADA2`, `#D9D2C8`, `#F4F0EA`, com pintas `#AE7161`; a faixa dourada do Lapidado se mantém, a do Afinado fica acinzentada.
- **Rachado:** o Opaco com um risco marrom.

## Decisões

- **Estilo:** facetas chapadas em tons da paleta dos sprites (nada de ruído, refração, núcleo fingido ou tonemap), com contorno escuro na silhueta e o brilho que varre o cristal, como nos quadros animados dos sprites. As cores saem direto no valor sRGB do sprite.
- **Duas malhas:** o **aglomerado** (Sinal e Opaco e rachado do Sinal) e o **losango com cinta** (Lapidado e Afinado e os Opacos e rachados deles). O parâmetro do vértice leva qual malha é (o bit 14, que a Task 2 usava como "longe").
- **Dado novo do servidor:** o `ChamberView` ganha `tiers`, um byte por câmara (0 sem tier, o tier + 1 num Afinado), para a câmara saber a cor do Afinado. Só vai no update tag quando há algum Afinado. É a única mudança fora de `client/`.
- **A cor da faixa** vai pelo RGB do vértice (a cor do tier do Afinado ou o dourado), no lugar da cor do tier do Núcleo que o desenho antigo mandava; o Núcleo não tem mais cor no cristal.
- **Fora de escopo:** os feixes (B), a espiral, o anel e os estilhaços (C), o ambiente (D), o desenho antigo sem shader (o modelo do item, para o shaderpack do Iris) e a tela do Núcleo.

## Desenho

### Malhas (`CrystalMesh`, duas instâncias)

- **Aglomerado:** 4 pontas hexagonais, cada uma com 6 lados + a capa + o fundo, 24 triângulos, 96 no total. Alturas do ápice 0,58 (a alta), 0,30, 0,06 e −0,02, todas com o fundo em −0,46 (os limites `TOP` e `BOTTOM` do shader seguem). Cada ponta tem um pequeno desvio do ápice (a inclinação). A normal de cada triângulo sai para fora do eixo da ponta dele.
- **Losango:** octógono: anel de cima em y = 0,12 e anel de baixo em y = −0,02, ambos com raio 0,30 e girados 22,5°; ápices em `TOP` e `BOTTOM`; a cinta são os 16 triângulos entre os anéis; 32 no total. A faixa pintada fica entre as alturas relativas 0,42 e 0,56.
- Cada malha é montada uma vez; o desenho só lê as tabelas. Sem cantos de faceta: o cel-shading não usa arestas.

### Shader (`resonator_crystal.fsh`, reescrito e bem menor)

- A normal da pose e a direção de visão dão o tom da faceta por uma luz fixa em cima e à esquerda, em 5 degraus: sombra, escura, média, clara, brilho (os mesmos 5 tons da paleta do estado). Como o cristal gira, as facetas trocam de tom ao girar, o que dá o brilho do sprite animado.
- **Contorno:** a faceta quase de lado (a incidência `1 − |N·V|` alta) sai em `#0F0E12`.
- **Brilho que varre:** uma faixa diagonal que atravessa o cristal a cada ~3,5 s e sobe um degrau o tom das facetas em que passa.
- **Fora de sintonia:** facetas sorteadas, por altura, caem um degrau, em saltos (a mesma sensação do desenho antigo).
- **Vivo:** paleta coral. **Opaco:** a paleta osso com pintas fixas no cristal. **Rachado:** o Opaco mais um risco marrom no objeto. **Recarga:** abaixo da altura do progresso a paleta coral, acima o osso, com uma linha de brilho na frente do nível.
- **Losango:** a faixa entre as alturas da cinta na cor da vértice (dourada se a cor do vértice é o dourado do Lapidado; senão a do tier, com as duas marcas em cruz nos polos), com os 5 tons derivados dela (a base escurecida, a base e a base clareada).

### Aura

Continua a da spec anterior: halo suave e estrela de quatro pontas, modo 2 do `resonator_ribbon.fsh`, sem mudança de contrato.

## Custo (wa-performance)

- **Parado e longe:** igual a hoje; a malha é montada uma vez, nada alocado por quadro.
- **CPU por quadro:** até 8 cristais × (96 vértices do aglomerado ou 96 do losango) ≈ 770 vértices transformados, mais um `switch` de malha por cristal. É menos que o desenho de gema anterior em GPU: o fragment só faz o tom, o contorno e o brilho (sem ruído, sem derivadas).
- **Servidor:** o `ChamberView.matches` (a cada 20 ticks por Núcleo) compara um byte a mais por câmara, sem alocar; o update tag só ganha a chave `tiers` quando há Afinado.
- **Medição:** o tempo de quadro do e2e tem de ficar no da linha de base (`run/e2e-antes-cristais`).

## Verificação

- JUnit: `CrystalMeshTest` (contagens, normais unitárias e coerentes com o sentido dos vértices, alturas de 0 a 1, os anéis do losango no raio e na altura, a cinta entre as alturas 0,42 e 0,56) e `CrystalParamTest` (o bit 14 vira "losango").
- GameTest: `ChamberView` com o tier do Afinado, ida e volta pelo NBT, sem a chave quando não há Afinado, e o `matches` que vê a troca de um Afinado.
- e2e `WA_E2E_ONLY=ressonador`: as capturas `ressonador-cristais-estados`, `-camara-recarga-perto` e `-camara-rachado`, e um passo novo com um Afinado de cada tier na câmara para conferir as cores das faixas (a captura `ressonador-cristais-afinados`).
- O aceite visual é do dono.
