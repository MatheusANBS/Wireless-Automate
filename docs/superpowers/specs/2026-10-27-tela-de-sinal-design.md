# Tela de Sinal (spec)

**Data:** 27/10/2026. **Mockup aprovado:** `docs/preview/tela-de-sinal.html` (v5, publicado como artifact e aprovado pelo dono em 27/10). As decisões abaixo saíram das perguntas de escopo e das quatro rodadas de ajustes no mockup.

## O que é

Um bloco que o jogador prende numa parede e expande no mundo até o tamanho que quiser (1×1 a 32×32 blocos). A tela mostra o que ele montar: números das redes do mod, itens dos Baús do mod, carga das Baterias e Tanques, texto, formas, gráficos e pixel art. Nada é widget fechado: tudo é feito de **peças soltas** ligadas a dados, agrupáveis e salváveis como **componentes** na biblioteca do jogador.

## Decisões

1. **Bloco:** `wirelessautomate:signal_screen` ("Tela de Sinal" / "Signal Screen"), sem tier. Só pode ser colocado contra a face lateral de um bloco sólido (`isFaceSturdy`); o blockstate `facing` (horizontal) é o lado para onde a tela olha. Some se a parede atrás do bloco sair (como uma tocha de parede).
2. **Tamanho:** escolhido na tela do bloco, de 1×1 a `tv.maxWidth` × `tv.maxHeight` (padrão 32 × 32). O bloco colocado é um canto ou o centro da área (o "cresce para": ↗ cima e direita, ↖ cima e esquerda, ✥ centro), guardado como a coluna e a linha do bloco dentro da área.
3. **A área é desenhada, sem blocos extras e sem colisão.** Para aplicar um tamanho, cada célula da área precisa de parede sólida atrás e de espaço livre na frente (ar ou bloco substituível, como grama), e a área não pode cruzar a de outra Tela carregada. Depois de aplicada, a área não é revalidada: só o bloco colocado depende da parede.
4. **Montagem por peças soltas:** Valor, Ícone de item, Barra, Medidor, Gráfico, Seta, Forma, Texto e Imagem. Cada peça tem posição e tamanho em pixels da tela (32 px por bloco, a resolução de desenho), dado ligado, formato, estilo e regras. Os "widgets" prontos são componentes feitos das mesmas peças.
5. **Grupos, camadas e componentes:** peças se agrupam (o grupo move junto), têm ordem de camada e podem ser escondidas; um grupo ou uma escolha vira componente na biblioteca do jogador e entra em qualquer Tela dele.
6. **Dados:** as redes que **quem edita** pode usar (`WaNetwork.canUse`): por rede e tipo, entra, sai, recebe e dá do Armazém, saldo, e os estados (movendo, vazia, esperando, cheio, redstone), os mesmos números dos Fluxos do Tablet. Os Baús do mod: estoque, entra e sai por item, e ocupação. Baterias e Tanques do mod: carga e capacidade. O relógio do jogo. Depois de posta, quem olha a Tela vê os números.
7. **Acesso ao dado na hora de mostrar:** a cada amostra, uma peça ligada a uma rede só mostra o número se o dono da Tela ainda pode usar a rede (`canUse(dono, false)`: dono, convidado, time ou rede pública; ser operador não vale para a Tela). Sem acesso, ou rede que não existe mais, a peça mostra "—".
8. **Item do Baú:** só os Baús do Wireless Automate, com entrada e saída exatas por item (o Baú conta só os itens que alguma Tela vigia). O ícone é o item renderizado, como no inventário; o nome só aparece se o jogador puser um Texto com ele.
9. **Texto que cabe:** Valor e Texto têm um tamanho máximo; a fonte diminui sozinha para o texto caber na largura e na altura da peça, e volta a crescer quando cabe. A opção "Diminuir a fonte para caber" vem ligada e pode ser desligada.
10. **Editor:** numa tela (clique direito na Tela), com a miniatura fiel no centro, peças e componentes à esquerda, camadas embaixo e as abas **Dado**, **Formato**, **Estilo** e **Regras** à direita. Grade de encaixe de 4 px (8 por bloco); arrastar, redimensionar pelo canto, Shift + clique para escolher várias, dois cliques para entrar no grupo.
11. **Atalhos do editor:** Ctrl+Z desfaz e Ctrl+Y ou Ctrl+Shift+Z refazem (100 mudanças, enquanto a tela está aberta); Ctrl+C, Ctrl+X e Ctrl+V (a cópia vale entre Telas; grupos colados continuam grupos); Ctrl+D duplica; Ctrl+A escolhe tudo; Ctrl+G agrupa e Ctrl+Shift+G desagrupa; Delete ou Backspace remove; setas movem 4 px, com Shift 1 bloco, com Alt 1 px; `[` e `]` descem e sobem uma camada; Esc solta a escolha (com nada escolhido, fecha); Ctrl+S salva. Os atalhos não agem dentro de um campo de texto.
12. **Regras:** por peça, lidas de cima para baixo, vale a primeira que bate: "menor que", "maior que" ou "igual a" um número, com a cor, piscar ou esconder.
13. **Temas:** um tema é uma paleta de 6 cores com nome (Fundo, Cartão, Texto, Apagado, Alta, Baixa). Uma peça usa uma cor do tema (acompanha a troca) ou uma cor fixa. Quatro temas de partida (Grafite, Fósforo, Âmbar, Porcelana) e os temas do jogador na biblioteca. O fundo da Tela é a cor do tema, um degradê ou uma pixel art em mosaico; linhas de varredura opcionais.
14. **Pixel art:** editor com lápis, balde, borracha e conta-gotas, paleta de 16 cores, desenhos de até 64 × 64 na biblioteca do jogador, guardada no servidor (limite por jogador na config).
15. **Quem edita:** quem colocou a Tela e os operadores. Os outros só olham; o clique deles mostra na action bar quem pode editar. Uma Tela sem dono (posta por comando) só é editada por operador.
16. **Receita (proposta, o dono pode trocar):** `GGG / GLG / IRI`, G = `c:glass_panes`, L = `c:dusts/glowstone`, R = `c:dusts/redstone`, I = `c:ingots/iron`; resultado 1.

## Arquitetura e custo

**Servidor.** A Tela não faz tick. O `TvManager` (um por servidor, chamado no tick, como o `RepairerManager`) conhece as Telas carregadas e espalha o trabalho em 20 baldes de fase pela posição. Na sua vez (uma vez por segundo), uma Tela sem peça ligada a dado ou sem jogador a até `tv.viewDistance` (padrão 64) blocos não faz nada. Com alguém perto, ela relê os números quando o amostrador dos Fluxos fechou uma volta nova (um mapa rede × tipo montado uma vez por volta para todas as Telas) e manda o `TvValuesPayload` só aos jogadores perto que ainda não têm os números atuais. Sem ninguém perto, nenhum pacote. Item do Baú: o Baú só conta entra e sai dos itens vigiados (uma consulta num conjunto pequeno por inserção); sem Tela vigiando, nada.

**O layout** (as peças) vai no update tag do block entity, só quando muda (aplicar o tamanho, salvar o editor). É compacto (poucos KB no máximo) e tem teto: `tv.maxElements` (128) peças e `tv.maxBindings` (64) dados distintos por Tela.

**Cliente.** Cada Tela à vista é desenhada numa textura própria (um framebuffer, `TextureTarget`) com `tv.pixelsPerBlock` (config do cliente, 16, 32 ou 64; padrão 32) pixels por bloco, e só é redesenhada quando um número que ela mostra muda ou o layout muda, no `RenderFrameEvent.Pre`, no máximo 4 Telas por quadro. O BER desenha só o quadro da moldura e um retângulo com a textura: uma 32 × 32 custa por quadro o mesmo que uma 1 × 1. A medida do texto (para caber) acontece só no redesenho. Uma Tela que não é desenhada por 200 ticks (longe ou fora da vista) libera a textura; volta a desenhar quando reaparece. Memória de vídeo: 32 × 32 blocos a 32 px é uma textura de 1024 × 1024 (4 MB); a 64 px, 2048 × 2048 (16 MB).

**Gráficos.** O histórico fica no cliente: um número por segundo por dado ligado a um gráfico, até 10 minutos (600 números); nada de histórico no servidor.

**Biblioteca do jogador** (componentes, temas, pixel arts): `SavedData` por jogador no overworld, com limites na config; vai ao cliente só com o editor aberto.

## Chaves novas

- Blockstate: `facing`.
- NBT do block entity: `owner` (UUID), `width`, `height`, `anchor_col`, `anchor_row` (int), `layout` (compound, pelo codec do `TvLayout`), `revision` (int, sobe a cada mudança de tamanho ou layout).
- Config do servidor, seção `tv`: `maxWidth` e `maxHeight` (1 a 64, padrão 32), `maxElements` (padrão 128), `maxBindings` (padrão 64), `viewDistance` (8 a 256, padrão 64).
- Config do cliente, seção `tv`: `pixelsPerBlock` (16, 32 ou 64, padrão 32).
- Payloads novos a partir da etapa 1: `TvValuesPayload` (servidor → cliente), `TvSizePayload` e `TvTemplatePayload` (cliente → servidor). `ModPayloads.VERSION` sobe de `"34"` para `"35"`.

## Etapas

1. **Bloco e motor:** bloco, item, receita e guia; aba Tamanho com a prévia e a validação; o modelo do layout (peças Valor, Texto e Forma) e o codec; o canal de dados das redes; o render por textura com o texto que cabe; a aba Conteúdo com o modelo pronto "Painel de rede" (escolher rede e tipo).
2. **Editor:** arrastar e redimensionar, camadas, grupos, as abas Dado, Formato, Estilo e Regras, os atalhos e o desfazer, temas, componentes e a biblioteca do jogador. Substitui a aba Conteúdo.
3. **Mais peças e dados:** Ícone de item, Barra, Medidor, Gráfico e Seta; o contador por item no Baú, ocupação, Bateria e Tanques; relógio do jogo.
4. **Pixel art:** editor, biblioteca, peça Imagem e o fundo em mosaico.

## Fora (por agora)

Importar PNG do computador; item em storages de outros mods (Sophisticated, AE2); dados do Ressonador (continua só presencial); editar olhando direto na parede; fórmulas entre dados; botões clicáveis na Tela; Tela no chão ou no teto; o contorno da área no mundo enquanto a aba Tamanho está aberta (a prévia fica na tela).

## Riscos

- **Framebuffer próprio com shaderpack do Iris:** o redesenho acontece fora do passe do mundo (`RenderFrameEvent.Pre`), o que deve ficar fora do pipeline do Iris, mas não há Iris no ambiente de dev. Se aparecer problema, o desenho cai para o modo direto (peças desenhadas no BER), mais caro por quadro.
- **Peça de texto com o tamanho mínimo:** num espaço minúsculo, a fonte que cabe pode ficar ilegível; não há corte, só uma fonte muito pequena. O editor mostra o tamanho em uso.
