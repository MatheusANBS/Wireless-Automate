---
navigation:
  title: Tela de Sinal
  icon: wirelessautomate:signal_screen
  parent: index.md
  position: 20
item_ids:
  - wirelessautomate:signal_screen
---


# Tela de Sinal

<ItemImage id="wirelessautomate:signal_screen" scale="2" float="left" />

Um painel de parede, de **1 × 1 até 32 × 32 blocos**, que mostra os números das suas [redes](networks.md) ao vivo: o
que entra, o que sai e quantas faces estão movendo.

<br clear="all" />

## Como usar

1. Clique com a Tela na **lateral de um bloco sólido**. Não vai no chão nem no teto, e cai se a parede sair.
2. Clique nela com a **mão vazia** para abrir a configuração.
3. Na aba **Tamanho**, escolha a largura e a altura e clique em **Aplicar**.
4. Na aba **Editar**, aplique um layout pronto ou monte o seu com peças.
5. Clique em **Salvar** (ou **Ctrl+S**). Nada vai para a parede antes disso.

Só quem colocou a Tela, os operadores e quem está na aba **Acesso** configuram; os outros só olham.

## Tamanho

| Parte | Como usar |
| --- | --- |
| **Largura** e **Altura** | ‹ e › mudam de 1 em 1 bloco; com **Shift**, de 4 em 4. |
| **Cresce a partir do bloco para** | ↗, ↖ ou ✥: onde o bloco que você colocou fica dentro da tela. |
| **Prévia** | A célula vermelha é a que impede o tamanho. |

A tela grande **não ocupa blocos**: é desenhada sobre a parede. Cada bloco da área precisa de **parede sólida atrás**
e **espaço livre na frente** (ar ou grama servem; uma tocha impede), e a área não pode cruzar outra Tela.

## O editor

Na aba **Editar** você monta o que a Tela mostra com **peças**. Se a janela não couber, diminua a escala da interface.

| Peça | Para quê |
| --- | --- |
| **Valor** | Um número ao vivo: o que entra, sai ou está guardado, quantas faces movem... |
| **Texto** | Uma frase fixa. |
| **Forma** | Um retângulo: cartão, moldura, divisória. |
| **Ícone de item** | O desenho de um item. |
| **Barra** | Enche conforme um número. |
| **Medidor** | Um mostrador em arco com ponteiro. |
| **Gráfico** | O histórico de um número nos últimos minutos. |
| **Seta** | Sobe, desce ou fica parada conforme o número. |
| **Imagem** | Uma pixel art sua ou uma imagem enviada (PNG, JPG ou GIF). |

| Gesto | O que faz |
| --- | --- |
| **Clique** | Escolhe a peça (num grupo, o grupo inteiro; dois cliques entram nele). |
| **Shift + clique** | Soma ou tira da escolha. |
| **Arrastar** | Move. A alça do canto redimensiona. |
| **Alt** ao arrastar | Ajusta de 1 em 1 px (o normal é uma grade de 4 px). |
| **Camadas** | Arraste uma linha para mudar a ordem; o olho esconde. |

| Atalho | O que faz |
| --- | --- |
| **Ctrl+Z**, **Ctrl+Y** | Desfaz e refaz. |
| **Ctrl+C**, **Ctrl+X**, **Ctrl+V** | Copia, recorta e cola, até de uma Tela para outra. |
| **Ctrl+D**, **Delete** | Duplica e remove. |
| **Ctrl+A**, **Ctrl+G**, **Ctrl+Shift+G** | Escolhe tudo, agrupa e desagrupa. |
| **[** e **]** | Sobe e desce a camada. |
| **Setas** | Movem 4 px; com **Shift**, 32 px; com **Alt**, 1 px. |

As abas da direita ajustam a peça escolhida: **Dado** (de onde vem o número), **Formato** (casas, k/M/G, prefixo,
unidade), **Estilo** (posição, cores, borda, fonte) e **Regras** (muda a cor, esconde ou pisca quando o número passa
de um valor).

### De onde vem o número

Na aba **Dado**, a **Fonte** pode ser:

- a **Rede**: o que entra, sai ou está guardado, e quantas faces movem. **Guardado** soma os Armazéns da rede;
- um **Armazém** (Baú, Bateria ou Tanque) ligado à Tela com o [Vinculador](linker.md): guardado, capacidade,
  enchimento e o que entra, sai ou sobra por segundo. Num Baú, dá para ler um item só, escolhido na grade dele (ou
  arrastado do JEI);
- o **Relógio** do jogo: hora do dia e dia.

Os números chegam uma vez por segundo enquanto há alguém perto. Uma rede privada a que quem colocou a Tela perdeu
acesso mostra **—**.

Fechar com mudanças pede dois cliques no ✕ (**Descartar?**).

### Base, temas e layouts

Com **nada escolhido**, a coluna da direita mostra a **base** da Tela:

- **Tema:** uma paleta de seis cores. Há quatro prontos (Grafite, Fósforo, Âmbar e Porcelana) e você salva os seus.
  As cores "do tema" (bolinhas redondas) acompanham a troca; as quadradas são fixas. A Tela guarda a paleta: apagar
  um tema seu depois não muda nenhuma Tela.
- **Fundo:** o do tema, uma cor, um degradê ou uma arte em mosaico.
- **Layout pronto:** **Energia**, **Armazenamento**, **Rede** ou **Relógio**. **Aplicar layout** troca o que está na
  Tela e se ajusta ao tamanho (**Ctrl+Z** desfaz antes de salvar). **Salvar layout atual** guarda o seu; **Remover
  layout** apaga, em dois cliques.

### Componentes

Na coluna da esquerda, **Componentes** são grupos de peças prontos (**Painel de rede**, **Cartão de número**,
**Título** e os seus). Clique para inserir. Para guardar o seu, escolha as peças e clique em **Salvar como
componente** (⊕). Componentes, temas e layouts valem em qualquer Tela sua.

## Pixel art

Na aba **Artes**, parte **Pixel art**:

1. Clique em **Nova** (ou escolha uma arte e clique em **Editar**).
2. Dê o nome e o **Tamanho**, de 8 a 128 pixels de lado.
3. Desenhe e clique em **Salvar** (ou **Ctrl+S**). Se a arte não couber na biblioteca, o desenho fica aberto com o
   aviso.

Cada arte tem até **16 cores** e **128 × 128** pixels. O clique esquerdo usa a ferramenta e o **direito apaga**.
**Ctrl** + clique pega a cor de um pixel.

| Tecla | Ferramenta |
| --- | --- |
| **B** | Lápis. |
| **E** | Borracha. |
| **G** | Balde. Com **Shift**, troca a cor na arte inteira. |
| **I** | Conta-gotas. |
| **L** | Linha. **Shift** trava em 0°, 45° e 90°. |
| **R** | Retângulo. **R** de novo alterna contorno e cheio; **Shift** faz quadrado. |
| **O** | Elipse. **O** de novo alterna contorno e cheia; **Shift** faz círculo. |
| **D** | Clarear (esquerdo) e escurecer (direito). |
| **T** | Pontilhado em xadrez. |
| **S** | Seleção. |

| Tecla | Ajuda |
| --- | --- |
| **[** e **]** | Pincel de 1 a 8 px. |
| **M** e **Shift+M** | Espelho horizontal e vertical. |
| **P** | Pixel perfect no lápis de 1 px. |
| **X** | Volta para a cor anterior. |
| **Ctrl+G** | Liga e desliga a grade. |
| **Ctrl+Z**, **Ctrl+Y** | Desfaz e refaz, até 100 passos. |
| **Shift+H**, **Shift+V** | Vira na horizontal e na vertical. |
| Roda do mouse | Zoom. |
| **Espaço** + arrastar | Move a vista. |
| **0** | Enquadra a arte. |

- **Paleta:** 16 cores em 4 linhas; a cor 0 é sempre transparente. Cada linha é uma **rampa** do escuro ao claro, que
  o clarear e o escurecer seguem. Trocar uma cor muda todos os pixels dela. **Criar rampa** gera a linha a partir da
  cor escolhida.
- **Seleção (S):** com ela, as ferramentas só pintam dentro. Arraste de dentro para mover; **Enter** assenta, **Esc**
  desfaz.

### Animação

Uma arte pode ter **até 64 quadros**, do mesmo tamanho e com a mesma paleta. A faixa embaixo do desenho mostra os
quadros: clique para escolher, arraste para reordenar.

- **Velocidade** de 1 a 20 quadros por segundo; **Loop** ou **Vai e volta**.
- **Papel vegetal:** o quadro anterior aparece apagado por baixo.
- Os quadros contam no teto de pixels da Tela: uma arte de 32 × 32 com 10 quadros conta 10.240.

| Tecla | O que faz |
| --- | --- |
| **,** e **.** | Quadro anterior e seguinte. |
| **Alt+N**, **Alt+D** | Quadro novo e duplicar. |
| **Alt+O** | Papel vegetal. |
| **Alt+P** | Toca e pausa a prévia. |

### Pôr a arte na Tela

1. Clique em **Imagem** na lista de **Peças**, ou arraste a arte da aba **Artes** até a miniatura.
2. Na aba **Estilo**, ajuste **Escala** (1× a 16×, ou **Esticar**), **Alinhar** e **Opacidade**.

A Tela guarda uma **cópia** da arte: mudar ou apagar a arte depois não muda a Tela. **Atualizar da biblioteca** traz
a versão atual. A linha **Pixels** da base mostra quanto as artes da Tela somam e o teto.

### Exportar e importar

| Botão | O que faz |
| --- | --- |
| **Exportar** | Copia o código do layout (na base) ou da arte escolhida (na aba **Artes**). |
| **Importar** | Lê o código: aplica o layout (**Ctrl+Z** desfaz) ou cria uma arte nova. |
| **Copiar como texto** | Uma forma legível, para editar à mão ou pedir a uma IA. **Importar** lê as duas. |

O layout leva as artes junto. Ligações a redes ou armazenamentos que você não tem saem: ligue de novo na aba **Dado**.
Há um guia para gerar artes e Telas com IA no GitHub do mod: `docs/guia-pixel-art-com-ia.md`.

## Imagens e GIFs

Na aba **Artes**, parte **Imagens**:

1. Clique em **Enviar arquivo** e escolha um PNG, JPG ou GIF (ou importe um código **WA-IMAGEM1**).
2. A imagem vai para o servidor e entra na sua biblioteca.
3. Arraste-a até a miniatura, ou escolha-a na grade da aba **Estilo** (o GIF tem a marca ▶).

| Limite | Padrão |
| --- | --- |
| Arquivo | Até **8 MB** |
| Tamanho | Até **1024 × 1024** pixels |
| GIF | Até **240** quadros |
| Por Tela | Até **8** imagens diferentes |

O servidor pode mudar esses números. A imagem fica guardada no mundo, nunca é um link da internet. O GIF só toca
quando alguém olha para a Tela.

- **Esconder as imagens dos outros:** na config do cliente, `showOthersImages = false` troca as imagens de outros
  jogadores por um quadro cinza.
- **Operadores:** `/wa tela imagens` lista as imagens do mundo; `/wa tela imagem remover <resumo>` tira uma de todas
  as Telas; `/wa tela imagem banir <jogador>` impede alguém de enviar (`desbanir` desfaz).

## Acesso

Na aba **Acesso** (só o dono e os operadores a veem):

1. Escreva o nome em **Convidar jogador…** e aperte **Enter**. Só vale quem já entrou no servidor.
2. Com o FTB Teams, adicione um time em **Adicionar time…**.
3. Para tirar alguém, clique duas vezes no **✕**.

Quem está na lista edita como o dono, mas uma ligação nova só vale a uma rede de quem a faz. As ligações que a Tela já
tem ficam quando qualquer um salva.

## Receita

<RecipeFor id="wirelessautomate:signal_screen" />
