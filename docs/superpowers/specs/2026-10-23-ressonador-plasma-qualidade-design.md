# Ressonador: qualidade visual do plasma, das linhas e do raio

Data: 2026-10-23. Só shader e câmara; sem dependência nova, sem mudança de servidor, de rede ou de dados.

## Problema

O dono testou o mod e achou o cristal bom, mas a esfera do meio, as linhas em volta e o raio que mostra a energia parecem de baixa qualidade. A leitura dos shaders (`resonator_plasma.fsh`, `resonator_ribbon.fsh`) acha as causas:

- A esfera é um disco 2D com `fbm` plano (sem normal, sem Fresnel, sem volume).
- As linhas de Lissajous são uma curva 2D colada no quad: não têm profundidade, não passam atrás da esfera e não giram em 3D.
- O ruído é um hash de `fract` com value noise (blocos e padrão visível), e não há dither (banding no brilho escuro).
- O halo morre em `smoothstep(0.7, 1.0, r)`: o brilho tem uma borda em vez de se dissolver.
- A cor sai de `1 - exp(-col)`, que lava o miolo para um coral chapado.
- O raio (modo 0 da fita) é uma faixa com seção `smoothstep` e pacotes de `sin`, sem cauda nem clarão nas pontas.

## Decisões

- **Sem dependência.** Veil (bloom HDR) fica descartado por ora: exige teste com Embeddium e Iris no ATM10 e a API muda entre versões. Se, depois deste trabalho, faltar bloom real, avalia-se à parte.
- **Fora do escopo:** o cristal (`resonator_crystal`), a tela do Núcleo, o Chladni, as faíscas e o caminho antigo (shaderpack do Iris ou sem shader), que não mudam.
- **Sem fade pela profundidade da cena:** amostrar a profundidade do framebuffer em que se desenha é um feedback loop ilegal; o halo em camadas faz o papel.

## Desenho

### 1. Include compartilhado

`shaders/include/wa_common.glsl`, importado com `#moj_import`: hash inteiro (`uint`, permitido no `#version 150`), ruído de gradiente 3D e `fbm3`, o tonemap filmic, o gradiente de temperatura (branco, amarelo, coral, vermelho fundo, com a cor do tier no anel) e o dither (ruído de gradiente intercalado por `gl_FragCoord` e tempo, ±0,5/255 no fim). O plasma e as fitas usam as mesmas funções, para os dois combinarem.

### 2. Esfera (`resonator_plasma.fsh`)

- O quad continua um billboard (`PlasmaCore`), mas o fragment faz a interseção analítica raio-esfera (câmera ortográfica na escala do quad, que é a de um billboard) e acha a normal por pixel.
- Superfície com `fbm3` deformado por fluxo (duas camadas que correm em sentidos opostos), brilho pelo Fresnel na borda e a temperatura pelo ruído e pelo ângulo: miolo quente e borda na cor do tier. A dessintonia (`1 - tune`) continua deformando o raio e agita o ruído, como hoje.
- O buffer cheio (`press`) mantém o raio menor e o anel pulsando.
- O halo é um acúmulo de duas camadas exponenciais que chega a zero exato na borda do quad; o `HALF` do `PlasmaCore` sobe de 1,3 para que o halo caiba, e o detalhe por distância não muda.

### 3. Linhas em 3D (`resonator_plasma.fsh`)

- A curva de Lissajous passa a ter três eixos de frequência (hoje 3 e 2 em 2D), girando devagar em torno da esfera, e cada amostra tem profundidade.
- Cada segmento é medido em 2D depois de projetado; a profundidade decide a ordem: o trecho atrás da esfera é escurecido e some atrás do corpo, e o da frente cobre a esfera.
- O traço tem núcleo claro, halo exponencial e uma cabeça brilhante que percorre a curva (a de hoje no `LissajousTrace`, só do caminho antigo, serve de referência de movimento).
- A órbita de cada cristal carregado segue o mesmo tratamento: pontos 3D e a linha até o centro com pulso.
- Para a curva girar em mundo (e não presa à câmera), o `PlasmaShader` passa a mandar um uniform `ViewRot` (a rotação da câmera, `mat3`), calculado por quadro sem alocar (campos `float` reaproveitados). Sem isso o paralaxe de andar em volta se perde.

### 4. Raio (`resonator_ribbon.fsh`, modo 0)

- Seção de cilindro: o `d` de 0 a 1 vira uma normal, com núcleo branco-quente e halo exponencial (largura pela faixa de vazão, como hoje).
- Os pacotes viram cometas: cabeça forte e cauda que decai para trás, com espaçamento e velocidade pela faixa.
- Um ruído de energia corre ao longo da fita (`fbm` em `x - t`), e a borda tremula um pouco.
- Clarão nas duas pontas (perto do plasma e na Porta), que cresce com a faixa.
- Os modos 1 a 3 (espiral, aura e anel) mudam só para usar o tonemap e o dither novos; o desenho deles continua.

### 5. Cor

O tonemap filmic troca o `1 - exp(-col * k)` dos dois shaders. As cores deixam de passar de 1 sem curva: o miolo chega ao branco e as bordas ficam saturadas. O dither fecha o banding.

## Custo (wa-performance)

- **Parado e longe:** igual a hoje. Só desenha Núcleo montado e dentro do alcance (`RANGE_SQ` do conduto, `FAR_SQ` do plasma). Sem alocação por quadro, e os vértices e o buffer não mudam.
- **Por quadro:** o aumento é só de fragment, dentro do quad do plasma (pixels no máximo de um bloco e meio) e das fitas. O nível de detalhe por distância (`PlasmaCore.detail`: 2, 1 e 0) controla octaves, amostras da curva e se há linhas de cristal; ele passa a controlar também as octaves do `fbm3` (4, 3 e 2).
- **Orçamento de GPU:** o novo plasma não pode custar mais que ~1,5 vez o tempo de quadro do atual no nível 2, medido pelo e2e (`camara plasma`, parado, com conduto e com espiral). Acima disso, reduz-se octaves e amostras antes de aceitar.
- **Memória e rede:** nada novo.
- **Varredura:** `python -I .claude/skills/wa-performance/scripts/varrer_perf.py` nos arquivos Java mudados (`PlasmaShader`, `PlasmaCore`).

## Verificação

- O e2e (`WA_E2E_ONLY=ressonador`) grava as capturas `ressonador-camara*` e o tempo de quadro no `result.txt`; guardam-se as de antes e as de depois lado a lado.
- A tela de comparação é por captura, não por teste automático: o aceite visual é do dono.
- `./gradlew build` e `runGameTestServer` não devem mudar (não há lógica nova); roda-se o `build` por causa dos recursos.
- Com shaderpack do Iris, o desenho antigo continua (`forceLegacyForTests` cobre no e2e).

## Fora de escopo / depois

- Bloco de bloom real por pós-processamento (Veil).
- O cristal, as faíscas e a placa de Chladni.

## Riscos

- Ajuste de look é iterativo: contam-se algumas rodadas com capturas.
- O `#moj_import` e o `uint` nos shaders de core precisam de teste em driver variado (Intel e AMD); se algum falhar, o hash volta a ser em ponto flutuante só para esse caso.
