# Câmara do Ressonador: limpar e refazer (cristais, recarga, esfera, ambiente)

Pedido do dono (23/10): a energia (raio da esfera à Porta, commit `cf470fba`) ficou perfeita e **não muda**. O resto da câmara é refeito do zero, com **menos elementos e mais qualidade**: hoje há poluição (curvas que giram, linhas ligando cristal e estrela, aura, faíscas). A skill `wa-shaders` guarda o método.

## Ponto de volta

Tudo que existe antes desta refeitura está no ramo `ressonador-plasma-qualidade`, último commit bom `cf470fba`. Para voltar atrás: `git checkout cf470fba -- src/main/resources/assets/wirelessautomate/shaders src/main/java/.../client/resonator/render`. A refeitura vai em commits separados por elemento, para reverter um de cada vez.

## Inventário e decisão proposta

| Elemento | Hoje | Proposta |
| --- | --- | --- |
| Raio da esfera à Porta (modo 0) | perfeito | **mantém, intocado** |
| Esfera de plasma | boa, halo e borda do tier | mantém a esfera; **tira** a ruído de TV fora de sintonia no halo se poluir; revisar só se o dono pedir |
| Curvas de Lissajous 3D girando | poluem | **remove** (a sintonia já aparece no raio e na esfera) |
| Linhas cristal → estrela (feixes/órbitas) | poluem | **remove**; o vínculo cristal-esfera vira só um arco fino curto em disparo (já planejado, usa `boltLight`) |
| Aura de cada cristal (halo + estrela de 4 pontas) | borrada | **remove**; o brilho do cristal fica no próprio shader (varredura) |
| Faíscas viajantes | muitas | reduz a poucas, só com buffer cheio, ou remove |
| Chladni (placa no chão) | ruído aditivo | revisar: manter só se mostrar a sintonia com clareza |
| Cristais (pixel-art 3D) | aprovados | **refazer** com a mesma paleta, mais limpo, só facetas e contorno (sem aura) |
| Recarga (espiral + anel + estilhaços) | espiral de catalisador e anel | **refazer**: um único efeito claro (o cristal Opaco se enche de baixo para cima com um fio de luz da Porta de Cristais até ele, e um pulso ao terminar) |
| Plasma sob pressão (buffer cheio) | raio menor e borda pulsando | mantém, é o sinal do buffer |

## Regra de decisão para o que fica

Cada elemento precisa mostrar **um dado do jogo**: vazão (raio), sintonia (esfera tremendo e cor), buffer cheio (pressão), carga e tier do cristal (cor da cinta, brilho), recarga (enchimento). Sem dado, sai.

## Ordem

1. Remover a poluição (curvas, linhas, aura, faíscas), com captura antes e depois. Aprovação.
2. Cristais limpos. Aprovação.
3. Recarga nova. Aprovação.
4. Esfera e ambiente (Chladni), só o que o dono pedir.
5. Arcos dos cristais para a esfera (disparos finos) e arcos ociosos, e o estalo sonoro (`ArcSounds`, `ClientConfig.effects.arcSounds`), da Parte B já desenhada.
6. Fechamento: perf, comentários, build, e2e com tempos, `CLAUDE.md`, `docs/progresso.md`.

## Passo 2: cristais limpos

O que estava errado (capturas de 23/10, ampliadas): o contorno por `1 - |N·V| > 0,88` escurece facetas inteiras quase de lado em vez de traçar a silhueta, então o contorno preto dos sprites quase não aparece; a luz fica parada no mundo, e de alguns lados o cristal fica todo no tom escuro; o losango de 8 lados vira uma bola, e o sprite é um quadrado girado; fora de sintonia as facetas piscam um tom a 14 Hz, mostrando a sintonia que a esfera já mostra (poluição).

### Pesquisa

1. **Casco invertido** (ASW Academy, "Guilty Gear Toon Line Control Techniques", da Arc System Works, que fez o jogo; o guia BNPR e o wiki do URP Toon Shader confirmam): a mesma malha desenhada de novo, inflada pelas normais e só com as faces de trás. **Adotado:** inflar no vertex shader e inverter a ordem dos vértices em vez de trocar o cull (o vanilla só corta as faces de trás). Em arestas duras o casco abre, então a direção do casco é uma **segunda normal**, só para o contorno (no jogo vai no canal da tangente; aqui, no `Normal` do passe do casco, que não leva luz). A largura vem da distância vezes `tan(FOV/2)` (`1 / ProjMat[1][1]`), para ficar constante na tela. **Descartado:** contorno por pós-processo (passe extra, fora do pipeline) e o empurrão em z do casco (sem linhas internas a esconder numa malha convexa).
2. **Luz em degraus** (manual de rampas de luz do Panda3D e o wiki do Toon RP): quantizar `N·L` em poucos níveis. **Adotado:** 5 tons da paleta do sprite por faceta, como já era, com a luz **na vista** (de cima e da esquerda, como nos sprites), para o cristal ler igual de qualquer lado. **Descartado:** textura de rampa (textura nova).
3. **Facetas chapadas sem textura** (pacote de pós-processo de relighting low-poly no Fab, os guias de flat shading): cor única por faceta e contorno para separar os planos. **Adotado:** já é assim; o losango passa a ter 6 lados (com 4, visto de cima, as faces caíam no mesmo tom e ele virava uma placa; com 8 era uma bola). **Descartado:** borda de Fresnel e brilho interno (um efeito a mais por cristal; o brilho fica só na varredura).

### Fórmula

Cristal = facetas chapadas em 5 tons da paleta do sprite por `N·L` na vista + contorno preto por casco invertido de largura constante na tela (2 px) + a varredura de um tom. **Não tem:** aura, halo, Fresnel, piscar por sintonia, ruído nas facetas vivas.

**Dado do jogo:** a carga (coral vivo, osso Opaco), a recarga (o enchimento de baixo para cima, o passo 3 refaz), o rachado (o risco) e o tier do Afinado (a cor da cinta e as cruzes). A sintonia sai das facetas (fica o balanço dos cristais fora de sintonia, que é movimento e não pisca).

**Custo:** o dobro de triângulos (aglomerado 96 + 96 do casco, losango 24 + 24), sem ruído por pixel no vivo; o Opaco mantém um hash por pixel nas pintas.

## Pedido do dono (23/10, durante o passo 2): a câmara precisa de alma

"A tela está muito sem alma e os cristais só parecem estar soltos ali, sem fazer sentido com a esfera." Requisitos:

- Os cristais precisam de um **sentido visual** para estarem ali: é uma câmara de ressonância, então tem de haver uma **aplicação física**, com um **efeito contínuo na esfera causado pelos cristais**.
- O flutuar para cima e para baixo é clichê e sem alma: **sai** (o balanço de `ChamberCrystals.crystalPos` e o giro do anel são revistos junto).
- Vale a regra de sempre: o efeito contínuo mostra um dado do jogo (carga de cada cristal, sintonia) e substitui, não soma, o que já mostra o mesmo dado.

Entra como um passo próprio, depois do visual dos cristais (passo 2) e antes da recarga (passo 3), porque a recarga depende de onde e como os cristais ficam. Começa pela pesquisa (ressonância física: osciladores de quartzo, piezoeletricidade, ondas estacionárias, interferência) e por uma proposta ao dono antes do código.

## Passo 2b: os diapasões (proposta A, aprovada em 23/10)

Os cristais param de flutuar e de girar no anel: ficam fixos, cada um no seu lugar do anel, com a ponta apontada para a esfera. Cada cristal vivo é um diapasão: oscila numa frequência e, a cada oscilação, dá um pulso curto para a esfera e lança uma onda na superfície dela, que parte do ponto da esfera virado para ele.

### Pesquisa

1. **Interferência de duas ou mais fontes** (Physics Classroom, "Mathematics of Two-Point Source Interference"; tutorial da Universidade de Maryland; material da Universidade de Sydney): fontes de mesma frequência formam linhas nodais **paradas** onde a diferença de caminho é meio comprimento de onda ímpar. **Adotado:** em sintonia, todos os cristais na mesma frequência e fase, então o padrão na esfera tem nós fixos e só as cristas correm entre eles.
2. **Batimento** (JoVE, "Beats"; Chalkdust, "That's a Moiré"): duas frequências próximas entram e saem de fase na frequência da diferença. **Adotado:** fora de sintonia, cada cristal ganha um desvio de frequência próprio, proporcional à dessintonia, e o padrão desliza e se desfaz no ritmo do batimento. É o sinal da sintonia, no lugar do chiado de TV, que sai.
3. **Distância na esfera** (Wikipedia, "Great-circle distance"; o ripple em sinc da biblioteca do Geeks3D como forma da onda plana): a distância na superfície é o ângulo central, `acos(clamp(dot(n, s), -1, 1))`. **Adotado:** a onda de cada fonte é `cos(k·θ − φ)` com queda `exp(−c·θ)`. **Descartado:** o sinc (o pico central estoura dentro da esfera aditiva) e os harmônicos esféricos (dão um modo global, sem ligar cada onda a um cristal).

### Fórmula

`W(n) = Σ vivos cos(k·θᵢ − φᵢ(t))·e^(−c·θᵢ) / Σ e^(−c·θᵢ)`, com `θᵢ` o ângulo entre a normal da esfera e a direção do cristal `i`, e `φᵢ = 2π·fᵢ·t`. Na primeira versão a crista era `pow(max(W, 0), n)` da soma normalizada: com o mesmo contraste da convecção ela sumia, e afinada virava pontos onde todas as fontes coincidiam (ruído). **Ficou:** cada cristal desenha os próprios anéis como linha, pela distância em arco até a crista mais perto (`d = |fract(u + ½) − ½|·2π/k`), com núcleo `exp(−d²/w²)` e brilho `(k/(d + k))²`, a receita do raio; os anéis de cristais diferentes somam onde se cruzam. `k` = 8, queda 1,4, na cor do tier com branco, só dentro da esfera. O cristal anda `A·pow(0.5 + 0.5·cos φᵢ, 6)` na direção da esfera: o pulso sai junto com a crista no ponto dele. **Não tem:** halo novo, linha cristal-esfera, partícula, giro.

**Dados do jogo:** cristal vivo (onda e pulso) × Opaco (parado e sem onda; o ponto dele na esfera fica um pouco mais escuro); sintonia (padrão parado em sintonia; fora dela, `fᵢ = F·(1 + 0,25·(1 − sintonia)·dᵢ)`, com `dᵢ` de −1 a 1 pela semente do cristal, e o padrão escorrega).

**Contrato:** o anel é fixo nos eixos do mundo (ângulo `2πi/n + π/4`, raio 0,95, altura −0,15), então o shader acha a direção de cada cristal com o número de câmaras e a máscara dos vivos no UV2.x (8 bits da máscara e o número em cima) e o uniform `ViewRot` (a rotação da câmera, global, igual para todos os Núcleos; volta o `PlasmaView`). O tempo é o mesmo nos dois lados (`(gameTime % 24000 + partialTick) / 20` no Java e `GameTime·1200` no shader), para o pulso bater com a crista.

**Custo:** por pixel da esfera, até 8 fontes com `acos`, `cos` e `exp`, num loop com teto constante e saída pelo número de câmaras; nada fora da esfera; no Java, nada alocado (um `Quaternionf` de rascunho).

**Reprovado (23/10):** "essa animação deles deitados indo pra frente e pra trás está horrível". O dono quer algo épico, na mesma qualidade do raio, e mais criativo. Os diapasões não entram; volta à proposta, com ideias que liguem os cristais à esfera com a mesma força visual do raio.

## Passo 2b: tentáculos de plasma (aprovado em 23/10)

Os cristais ficam de pé e parados no anel (`ChamberRing`: sem flutuar e sem girar). De cada cristal vivo sai um tentáculo de plasma até a esfera, como o filamento de um globo de plasma até o dedo no vidro: é a corrente que o cristal entrega. O raio leva a energia da esfera para a Porta: entrada × saída.

### Pesquisa

1. **Física do globo de plasma** (Princeton, "On filament structure and propagation within a commercial plasma globe", Physics of Plasmas, 2015; material da ANU e do CERN, Science in School 37): o filamento é o caminho da corrente; tocar o vidro baixa a resistência ali, a corrente se concentra, o filamento clareia e vai para o dedo; filamentos de mesma polaridade se repelem; com xenônio as pontas abrem ("bloom"); a descarga avança a cada ciclo da tensão. **Adotado:** o tentáculo agarra a esfera com um clarão na ponta, pacotes de corrente correm do cristal para a esfera, e cada cristal tem o seu caminho (sementes diferentes). **Descartado:** filamentos saindo do centro para o vidro (é o globo comum; aqui a energia entra pelos cristais).
2. **Domain warping** (Inigo Quilez, "warp"; The Book of Shaders, cap. 13): torcer o domínio do ruído com outro ruído dá curvas orgânicas, e o tempo no argumento anima. **Adotado:** o eixo do tentáculo é ruído de gradiente suave (o `wa_noise`) em 3 oitavas, com a primeira deslocando as outras; lento. **Descartado:** o ruído linear do raio (quebrado em retas, é a cara do raio; o tentáculo é curvo para não competir com ele).
3. **A receita do raio** (o modo 0, aprovado): distância corrigida pela inclinação, núcleo gaussiano em HDR, brilho `(k/(d + k))²`, fade nas bordas do quad. **Adotado** sem mudar.

### Fórmula

Tentáculo = eixo curvo por ruído suave com domínio torcido, preso nas pontas por `sin(πx)^0,8` + a luz do raio (`boltLight`) com núcleo branco e brilho coral puxado para o tier + pacotes de corrente correndo para a esfera + clarão onde agarra. **Não tem:** borda, tubo, partícula, giro, linha reta.

**Dados do jogo:**
- Cristal vivo: tentáculo inteiro. Opaco: nenhum. Opaco em recarga: o tentáculo cresce com a recarga (mais fino e fraco no começo; a força vai no alfa).
- Sintonia: em sintonia, grosso (núcleo 0,042), firme e lento. Fora dela, fino (0,020), mais ondulado e rápido, e se solta: recua até onde alcança (30% a 100% do caminho) e volta a procurar a esfera, cada cristal no seu ritmo; o clarão só acende quando agarra. O chiado de TV sai (a sintonia é dos tentáculos).

**Contrato:** modo 2 do `resonator_ribbon.fsh` (o 0 do raio fica intocado); o parâmetro leva a sintonia de 0 a 15 (4 bits), a semente (3 bits) e o comprimento em décimos (7 bits); o alfa é a força.

**Custo:** por pixel da fita, 3 avaliações do eixo (o ponto e a inclinação), cada uma com 4 `wa_noise`: 12 ruídos, sem loop. Até 8 fitas por Núcleo, só dentro de `RANGE_SQ`.

## Passo 3: recarga nova

No servidor a recarga não gasta energia: consome o catalisador do Núcleo ao longo de `rechargeTicks` e devolve a carga ao primeiro Opaco. A Porta de Cristais não participa, então a espiral saindo dela mostrava uma coisa que não acontece. Saem a espiral (modo 1) e o anel (modo 3).

### Pesquisa

1. **Sentido do fluxo pela direção dos pulsos** (patente US 2009/0309548, "Graphic State of Charge Indicator for a Battery Charging System": pulsos num sentido para carga entrando, no outro para saindo; o applet de capacitor do MOE de Singapura, a carga pela cor). **Adotado:** o Opaco em recarga ganha o tentáculo **invertido**, com os pacotes correndo da esfera para o cristal. **Descartado:** a contagem em número (é da tela do Núcleo).
2. **Enchimento por altura com a linha da superfície** (o líquido do Half-Life: Alyx refeito em GLSL no projeto da Carleton, com a linha de espuma; o "Fill Liquid Volume" do Godot Shaders, CC0): limiar pela altura no espaço do objeto e uma linha clara na superfície. **Adotado:** o que o cristal já fazia (coral abaixo do nível, osso acima, a linha clara no nível), no espaço do cristal. **Descartado:** a superfície mexendo (onda no nível): mais um movimento, sem dado novo.
3. **O fim marcado por um clarão curto** ("Juice It or Lose It", Jonasson e Purho, GDC Europe 2012; a resposta "Don't Juice It or Lose It", de Folmer Kelly, sobre não exagerar): um flash curto dá o retorno de "terminou". **Adotado:** o cristal acende quase branco e volta ao tom em 0,7 s, no próprio cristal. **Descartado:** o anel que abria em volta (um elemento a mais).

### Fórmula

Recarga = o tentáculo invertido (fino, vermelho do catalisador puxado para o coral, sempre agarrado, pacotes da esfera para o cristal) + o cristal enchendo de baixo para cima com a linha clara + o clarão de 0,7 s no fim. Quando acaba, o tentáculo vira o normal. **Não tem:** espiral, anel, grãos, ligação com a Porta de Cristais.

**Dados do jogo:** recarga em curso (o tentáculo invertido), o progresso (a altura do enchimento) e o fim (o clarão).

**Custo:** o tentáculo invertido é a mesma fita do normal; o clarão é um `mix` no shader do cristal. Saem a espiral (10 grãos por pixel) e o anel.
