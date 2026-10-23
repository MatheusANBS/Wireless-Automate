# Ressonador: o cristal da câmara como gema de verdade (Parte A)

> **Substituída** por `2026-10-23-ressonador-cristais-sprite-design.md` (23/10): o dono recusou a direção da gema por ruído; o cristal 3D passa a acompanhar os sprites do mod. Este arquivo fica como registro.

Data: 2026-10-23. Só malha, shader e câmara; sem dependência nova, sem mudança de servidor, de rede ou de dados. Continua no ramo `ressonador-plasma-qualidade`, em cima do `wa_common.glsl` e do `ViewRot` da spec `2026-10-23-ressonador-plasma-qualidade-design.md`.

## Contexto e decomposição

O dono quer melhorar todas as animações da câmara do Ressonador com shader próprio: cristais, energia, recarga e o resto. São assuntos independentes, então foram separados em sub-projetos, cada um com a sua spec e o seu plano:

- **A. Cristais (esta spec):** a gema (malha e shader, os quatro estados) e a aura em volta.
- B. Energia: feixes dos cristais até a esfera e conduto até as Portas como arcos, faíscas e partículas em shader.
- C. Recarga: espiral do catalisador, anel final e estilhaçar do rachado.
- D. Ambiente: placa de Chladni, poeira e brilho no vidro, animação de montagem.

A Parte A refaz o visual dos **quatro estados dentro do shader do cristal** (vivo, Opaco, enchimento da recarga, rachado), porque os quatro saem do mesmo shader. Os efeitos de fora do cristal (a espiral, o anel, os estilhaços) são da C.

## Problema

O cristal já usa shader próprio, mas a malha é uma bipirâmide de 6 lados com 12 triângulos (`CrystalMesh`, a "Ponta dupla"), e o visual é ruído de valor de 3 oitavas com veios coral, borda por Fresnel e um reflexo especular de uma luz fixa. Falta profundidade (parece uma pedra pintada), a luz é uma direção só, o Opaco é um cinza chapado e o rachado não existe no shader: o código trata rachado como Opaco (`ChamberCrystals.isDull` cobre os dois e o `CrystalGem` só tem `LIVE`, `DULL` e a recarga).

## Decisões

- **Sem dependência.** Mesma linha da spec anterior.
- **A aparência de profundidade é fingida no fragment** (camadas de ruído deslocadas para dentro do cristal), sem raymarching nem billboard: a gema continua uma malha de verdade, com profundidade correta contra o Vidro e a Carcaça.
- **Mantém o contrato dos vértices** do `CrystalGem` (posição e normal na pose, cor do tier e sintonia em `Color`, cantos da faceta em UV0, x e z do cristal em UV1, altura e parâmetro em UV2, `short`) e os limites `BOTTOM` −0,46 e `TOP` 0,58.
- **Fora de escopo:** os efeitos externos da recarga e do rachado (C), os feixes (B), o desenho antigo sem shader (o modelo do item, para o shaderpack do Iris) e a tela do Núcleo.

## Desenho

### 1. Malha nova (`CrystalMesh`)

Um prisma de quartzo de 8 lados com as duas pontas chanfradas:

- Corpo: dois anéis de 8 vértices, um em y ≈ −0,22 e outro em y ≈ 0,22, com raio perto de 0,28. O anel de cima é torcido ~12° em relação ao de baixo, para as facetas laterais não serem retângulos perfeitos. Cada vértice leva o desnível de raio e de altura fixo de hoje (`jitter`), para o cristal ser irregular e cada câmara parecer única pela semente.
- Pontas: uma pirâmide de 8 triângulos em cima (ápice em `TOP`) e outra embaixo (ápice em `BOTTOM`).
- Total: 16 triângulos de corpo (8 faces, 2 triângulos cada) + 16 das pontas = **32 triângulos, 96 vértices** (hoje 12 e 36). Montada uma vez, como hoje; as normais planas saem para fora.
- As diagonais dos retângulos do corpo não são arestas do cristal: o UV0 passa a levar um sinal por triângulo que desliga a aresta da diagonal no shader. Só as arestas reais acendem.

### 2. Shader da gema (`resonator_crystal.fsh`)

- **Normal e luz.** A normal do objeto de cada faceta sai das derivadas de `crystal` (`cross(dFdx, dFdy)`, virada para fora a partir do centro do cristal): não precisa de atributo novo e a faceta é plana. O sombreamento deixa de ser uma luz fixa: duas luzes de estúdio (uma fria e uma quente na cor do tier) mais um reflexo de ambiente analítico (gradiente pelo vetor refletido, com duas faixas de softbox).
- **Interior fingido.** Em 3 amostras (1 de longe), o ruído de gradiente 3D do `wa_common.glsl` é lido em pontos deslocados para dentro, ao longo da normal do objeto, com o passo esticado pelo ângulo de visão. Cada camada tem a sua cor (a de dentro mais quente), e o resultado soma veios que correm para cima por dentro, com profundidade.
- **Núcleo de energia.** Um núcleo claro no eixo, visto através dessas camadas (por isso distorce com o ângulo), com pulsação lenta; a luminosidade segue a carga (a sintonia em `Color.a`): bem sintonizado, firme e quente; fora de sintonia, cintilando em saltos, como hoje.
- **Superfície.** Dispersão cromática na borda (a potência do Fresnel diferente por canal: o vermelho e o azul se separam), arestas acesas só nas bordas reais e brilhos que acendem em arestas aleatórias, com o sorteio pelo tempo e por um código da faceta tirado da normal do objeto.
- **Os quatro estados**, todos do mesmo shader:
  - **Vivo:** o acima.
  - **Opaco:** vidro fosco cinza, translúcido, com poeira e um núcleo quase apagado, uma brasa fraca que pulsa muito devagar.
  - **Recarga:** nível de luz líquida que sobe até a altura do progresso. A superfície do nível é um menisco com ondulação (uma faixa clara com o deslocamento de seno em x e z do objeto); abaixo dele o interior acende em cheio e bolhas pequenas sobem; acima dele continua o Opaco. Substitui a máscara `step(h, rec)` e a linha clara de hoje.
  - **Rachado:** o Opaco com fraturas: um padrão de veios finos (ruído em cadeia, só o fio no limiar) atravessando as facetas, com um brilho vermelho fraco nas frestas. Estado novo no parâmetro (abaixo); no servidor e no `CrystalIntegrity` nada muda.
- **Tonemap.** O `1 - exp` por canal máximo de hoje (que preservava a matiz do coral) é mantido, e o dither do `wa_dither` fecha o banding. O include `wa_common.glsl` é importado no `.fsh` e o hash e o ruído de valor locais saem.

### 3. Contrato do parâmetro

O parâmetro de vértice (UV2.y, `short`) hoje é `seed (4 bits) | state << 4`, com `state` 0 vivo, 1 Opaco e 2 + 1000 × progresso na recarga. Passa a ser:

- `state` 1003: **rachado** (1002 é a recarga em 100%, então não conflita).
- Bit 14 (16384): **longe**, o cristal desenhado a mais de 12 blocos (o mesmo `NEAR_SQ` do `PlasmaCore`), que usa 1 amostra do interior em vez de 3.
- O total máximo é 16384 + (1003 << 4) + 15 = 32447, abaixo de 32768.
- Um helper puro `CrystalParam` (`pack(seed, state, far)` e os leitores), com JUnit, no lugar da aritmética solta no `CrystalGem`, para os limites ficarem testados.

No `ChamberCrystals`, o estado é escolhido por cristal: vivo, Opaco, recarga (só o primeiro Opaco não rachado, como hoje) e rachado (o código de câmara `CRACKED_SIGNAL` e acima). O desenho antigo (item) segue igual.

### 4. Aura (`resonator_ribbon.fsh`, modo 2)

Mesmo quad e mesmo contrato do parâmetro (a sintonia e a semente). O visual passa a ser um halo suave com queda exponencial e uma estrela de quatro pontas, um reflexo de lente, que gira devagar e pulsa com a carga; o tamanho cresce com a sintonia. Fora de sintonia, a estrela treme e perde as pontas. O Opaco continua sem aura.

## Custo (wa-performance)

- **Parado e longe:** igual a hoje. A câmara só desenha com o Núcleo montado e perto; a malha é montada uma vez; nada alocado por quadro (os rascunhos de `CrystalGem` e `ChamberCrystals` são os mesmos).
- **Por quadro, CPU:** até 8 cristais × 96 vértices (era 36) e 32 normais (era 12) transformados pela pose: ~770 vértices por quadro, trabalho de uma conta pequena por vértice.
- **Por quadro, GPU:** cada cristal ocupa poucos pixels. O fragment tem 3 amostras de ruído de gradiente (1 longe), mais duas derivadas. O teto é ~1,5× o tempo de quadro da base, medido no e2e, e antes de aceitar acima disso cortam-se amostras.
- **Memória e rede:** nada novo.
- **Varredura:** `varrer_perf.py` nos arquivos Java mudados (`CrystalMesh`, `CrystalGem`, `CrystalParam`, `ChamberCrystals`).

## Verificação

- JUnit: `CrystalMeshTest` atualizado (32 triângulos, normais para fora, alturas de 0 a 1, os anéis do corpo no raio e na altura novos, o sinal da diagonal) e `CrystalParamTest` (ida e volta e o limite abaixo de 32768).
- e2e `WA_E2E_ONLY=ressonador`: as capturas `ressonador-camara-cristais`, `-recarga*` e `-rachado` já existem e servem de comparação; o plano acrescenta uma captura de perto dos cristais se a existente não mostrar bem os quatro estados. O `result.txt` traz o tempo de quadro.
- O aceite visual é do dono, por capturas de antes e depois, em portões como na spec anterior.
- Com shaderpack do Iris ou sem o shader do cristal, o desenho continua o modelo do item (`forceLegacyForTests` cobre).

## Riscos

- Ajuste de look de gema é iterativo: contam-se algumas rodadas.
- As derivadas em `cross(dFdx(crystal), dFdy(crystal))` dão a normal por faceta plana, mas podem ser instáveis em triângulos de poucos pixels; para os de longe, o shader cai na normal do vértice (`normal`, em coordenadas do mundo) e usa só 1 amostra.
- O sinal de diagonal no UV0 precisa sobreviver à interpolação (soma constante nos três vértices do triângulo); o JUnit da malha confere.
