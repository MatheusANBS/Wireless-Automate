# Matemática e técnicas por tipo de efeito

Catálogo de partida, com a fonte de cada uma. Fontes em ordem de confiança: artigos (SIGGRAPH, GDC, GPU Gems/Pro, ACM TOG), Inigo Quilez (iquilezles.org), The Book of Shaders, Shadertoy (o código lá é CC BY-NC-SA por padrão: ler e reescrever, não copiar), código de outros mods com resultado visível, e por fim tutoriais e fóruns para confirmar.

## Brilho e pós-processamento

- **Bloom**, opções a comparar: (a) cadeia de mips com o filtro de 13 amostras e a média de Karis descendo e a tenda 3 × 3 subindo (Jimenez, "Next Generation Post Processing in Call of Duty: Advanced Warfare", SIGGRAPH 2014; o "Phys. Based Bloom" do LearnOpenGL); (b) dual Kawase, mais barato (Marius Bjørge, "Bandwidth-Efficient Rendering", SIGGRAPH 2015); (c) gaussiano separável em poucos níveis (o clássico, mais caro por pixel); (d) o bloom pronto de uma dependência (Veil, Photon). Em todos: limiar suave controla o que espalha; em framebuffer de 8 bits, dither na composição ou alvo em ponto flutuante.
- **Brilho sem pós-processamento**: núcleo gaussiano estreito em HDR + `k/(d+k)` ao quadrado + halo largo fraco, tonemap ACES e dither (o raio do Ressonador, `resonator_ribbon.fsh` modo 0).
- **God rays**: ruído só da direção em coordenada polar (estrias radiais, sem passe extra) ou blur radial em tela a partir do ponto da fonte (GPU Gems 3, cap. 13, Mitchell).
- **Alfa pré-multiplicado**: `ONE, ONE_MINUS_SRC_ALPHA` cobre com α = 1 e soma com α = 0 numa passada (Wikipedia, "Alpha compositing"; Tom Forsyth, "Premultiplied alpha").
- **Impact frames**: um a três quadros de tela inteira em alto contraste ou cor invertida (técnica de anime; exige pós-processamento).

## Esferas, escudos e plasma

- **Esfera num quad**: interseção analítica raio-esfera no fragment; casca da frente e de trás separadas; inchar = mudar o raio.
- **Fresnel**: `pow(1 − |N·V|, p)`.
- **Grade hexagonal numa esfera**: só hexágonos é impossível (Euler: V − E + F = 2 exige 12 pentágonos). Opções: (a) poliedro de Goldberg, o dual de uma icosfera subdividida: acha a face do icosaedro pelo maior `dot(n, centro)`, projeta no plano dela e aplica a grade hexagonal 2D em coordenadas axiais com arredondamento cúbico (Red Blob Games, "Hexagonal Grids"); fica regular, com 12 pentágonos; (b) Voronoi do conjunto de Fibonacci (Keinert e outros, "Spherical Fibonacci Mapping", ACM TOG 2015: o ponto mais perto em tempo constante); quase todo hexágono, mas com células tortas nas costuras da espiral; (c) grade 2D por projeção estereográfica da face virada para a câmera: regular no meio, mas presa à câmera.
- **Onda pelo escudo**: `smoothstep` em torno de um ângulo geodésico `acos(dot(n, impacto))` que cresce no tempo; acender a célula pelo centro dela, não pelo pixel.
- **Superfície de estrela**: fbm 3D de várias oitavas para a granulação, warping de domínio (Quilez, "warp") para a convecção, escurecimento da borda `I(μ) = 1 − u(1 − μ)`, manchas por distância angular com umbra e penumbra, rampa de temperatura com stops saturados.
- **Proeminências e arcos**: elipse ou Bézier ancorada na borda, distância à curva com espessura e chama por fbm correndo ao longo dela.

## Linhas, feixes e raios

- **Raio**: eixo por ruído 1D com interpolação linear em 4 a 5 oitavas, preso nas pontas por `pow(sin(πx), 0,7)`, distância corrigida pela inclinação `/sqrt(1 + k²)`.
- **Feixe trançado**: dois ou três fios em hélice (`±A·sin(fase)`) em volta de um núcleo; o fio da frente mais forte por `cos(fase)`.
- **Tentáculo orgânico**: eixo por ruído de gradiente suave com domínio torcido.
- **Fita virada para a câmera**: lado = direção × (meio − câmera).

## Formas e preenchimento

- **Distâncias 2D e 3D** (círculos, hexágonos, segmentos): Quilez, "distfunctions2d" e "distfunctions".
- **Preenchimento por nível**: limiar pela altura no espaço do objeto com a linha clara na superfície.
- **Contorno de malha**: casco invertido (a malha de novo, inflada pela normal suavizada, só as faces de trás), com largura constante pela distância × `1/ProjMat[1][1]` (ASW Academy, "Guilty Gear Toon Line Control Techniques").
- **Interferência e batimento**: fontes de mesma frequência dão nós parados; frequências próximas, padrão que desliza na diferença.
