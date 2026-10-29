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

Um painel que você prende numa parede e estica pelo tamanho que quiser, de **1 × 1 até 32 × 32 blocos**. Ele mostra os
números das suas [redes](networks.md) ao vivo: o que entra, o que sai e quantas faces estão movendo.

<br clear="all" />

## Como colocar

1. Clique com a Tela na **lateral de um bloco sólido**: ela fica rente à parede, virada para você. Não dá para pôr no
   chão nem no teto, e ela cai se a parede atrás dela sair.
2. Clique nela com a **mão vazia** para abrir a tela de configuração. Só quem a colocou, os operadores e quem está na
   aba **Acesso** podem configurar; os outros só olham.

## Escolher o tamanho

Na aba **Tamanho**:

| Parte | Como usar |
| --- | --- |
| **Largura** e **Altura** | ‹ e › mudam de 1 em 1 bloco; com **Shift**, de 4 em 4. |
| **Cresce a partir do bloco para** | ↗ cima e direita, ↖ cima e esquerda ou ✥ centro: onde o bloco que você colocou fica dentro da tela. |
| **Prévia** | A parede em volta, com a área da tela. A célula vermelha é a que impede o tamanho. |
| **Aplicar** | Estica a tela. Fica desligado enquanto algo impede. |

A tela grande **não ocupa blocos**: ela é desenhada sobre a parede. Para caber, cada bloco da área precisa de
**parede sólida atrás** e de **espaço livre na frente** (ar ou grama, por exemplo; uma tocha impede), e a área não pode
cruzar outra Tela.

## O que mostrar: o editor

Na aba **Editar** você monta o que a Tela mostra, com **peças**. A janela cresce para ocupar a tela do jogo; se não
couber, diminua a escala da interface.

| Peça | Para quê |
| --- | --- |
| **Valor** | Um número ao vivo de uma [rede](networks.md): o que entra, o que sai, o que está guardado, quantas faces movem... |
| **Texto** | Uma frase fixa. |
| **Forma** | Um retângulo com fundo, borda e cantos: cartão, moldura, divisória. |
| **Ícone de item** | O desenho de um item, escolhido pelo id ou na lista de um baú ligado. |
| **Barra** | Uma barra que enche conforme um número, deitada ou em pé. |
| **Medidor** | Um mostrador em arco com um ponteiro. |
| **Gráfico** | O histórico de um número, em linha ou colunas, dos últimos minutos. |
| **Seta** | Sobe, desce ou fica parada conforme o número cresce, cai ou se mantém. |
| **Imagem** | Uma pixel art sua, desenhada na aba **Pixel art** (mais abaixo). |

- **Escolher e mexer:** clique numa peça para escolhê-la (numa peça de grupo, o grupo inteiro; dois cliques entram no
  grupo). **Shift** + clique soma ou tira da escolha; clique no vazio solta. Arraste para mover e puxe a alça do canto
  de baixo para redimensionar. Tudo encaixa numa grade de 4 px; segure **Alt** para ajustar de 1 em 1 px.
- **Grupos e camadas:** **Agrupar** junta as peças escolhidas para mover e redimensionar juntas; **Desagrupar** solta.
  Embaixo, as **Camadas** listam as peças de cima para baixo: arraste uma linha para trocar a ordem, use o olho para
  esconder e **[** e **]** para subir e descer.
- **Abas da direita:** **Dado** liga o Valor a uma rede (e escolhe o tipo e o que ler); **Formato** define casas,
  abreviar (k, M, G), sinal, prefixo, sufixo e unidade; **Estilo** muda posição, tamanho, cores, borda, cantos,
  opacidade e fonte; **Regras** muda a cor, esconde ou pisca quando o número passa de um valor.
- **De onde vem o número:** na aba **Dado**, a **Fonte** é a **Rede** (o que entra, sai ou está guardado, e quantas faces movem), um **Armazém** ligado pelo [Vinculador](linker.md) (guardado, capacidade, enchimento e o que entra, sai ou sobra por segundo; num Baú, dá para ler um item só) ou o **Relógio** do jogo (hora do dia e dia). Na Rede, **Guardado** soma o que os Armazéns da rede guardam.
- **Item de um Baú:** com um baú ligado, a aba **Dado** mostra a grade dos itens dele com um campo **Buscar itens...** e um clique escolhe o item. Com o JEI, também dá para **arrastar um item** do JEI para o campo do Ícone ou do filtro do Baú.
- **Salvar:** nada vai para a parede até clicar em **Salvar** (ou **Ctrl+S**). Fechar com mudanças pede dois cliques
  no ✕ (**Descartar?**). A miniatura mostra os números de uma ligação nova antes de salvar.

| Atalho | O que faz |
| --- | --- |
| **Ctrl+Z**, **Ctrl+Y** (ou **Ctrl+Shift+Z**) | Desfaz e refaz. |
| **Ctrl+C**, **Ctrl+X**, **Ctrl+V** | Copia, recorta e cola, inclusive de uma Tela para outra. |
| **Ctrl+D**, **Delete** | Duplica e remove. |
| **Ctrl+A**, **Ctrl+G**, **Ctrl+Shift+G** | Escolhe tudo, agrupa e desagrupa. |
| **Setas** | Movem 4 px; com **Shift**, 32 px; com **Alt**, 1 px. |

### Temas e fundo

Com **nada escolhido**, a coluna da direita mostra a **base** da Tela. O **Tema** é uma paleta de seis cores (fundo,
cartão, texto, apagado, alta e baixa): há quatro prontos (Grafite, Fósforo, Âmbar e Porcelana) e os seus. As cores das
peças marcadas como "do tema" (as bolinhas redondas) acompanham a troca; as quadradas são fixas. Edite as seis cores
pelo código `#RRGGBB` e use **Salvar tema** para guardar a paleta com um nome. O **Fundo** pode ser o do tema, uma cor
fixa ou um degradê, e dá para ligar as **linhas** de varredura e a **grade** da miniatura. A Tela guarda a paleta
inteira: apagar um tema seu depois não muda nenhuma Tela.

### Layouts prontos e o seu

Com nada escolhido, **Layout pronto** na base oferece **Energia**, **Armazenamento**, **Rede** e **Relógio**: escolha um e clique em **Aplicar layout**; ele se ajusta ao tamanho da Tela e troca o que está nela (dá para desfazer com **Ctrl+Z** antes de salvar). Para guardar o seu, escreva um nome no campo e clique em **Salvar layout atual**; ele passa a aparecer na mesma lista, em qualquer Tela sua. **Remover layout** apaga o seu escolhido, em dois cliques.

### Componentes

Na coluna da esquerda, **Componentes** são grupos de peças prontos para inserir: **Painel de rede**, **Cartão de
número** e **Título** já vêm, e você guarda os seus. Clique num componente para inseri-lo, num grupo novo, no primeiro
lugar livre. Para guardar o seu, escolha as peças, clique em **Salvar como componente** (⊕) e dê um nome; o ✎ renomeia
e o ✕ remove, em dois cliques. Os componentes e os temas ficam na sua biblioteca e valem em qualquer Tela sua. Um
componente ligado a uma rede que você não pode mais usar entra com **—**.

Para mostrar o conteúdo de um Baú, Bateria ou Tanque, ligue-o à Tela com o [Vinculador](linker.md) antes de escolhê-lo na aba **Dado**.

Os números chegam uma vez por segundo enquanto há alguém perto. Uma rede privada da qual quem colocou a Tela perdeu o
acesso mostra **—**.

## Pixel art

Desenhe suas artes na aba **Pixel art**, na coluna da esquerda do editor. Elas ficam na sua biblioteca e valem em
qualquer Tela sua.

1. Clique em **Nova** (ou escolha uma arte e clique em **Editar**). O editor de pixel art abre por cima do editor.
2. Escreva o nome e o **Tamanho**: de 8 a 128 pixels de largura e de altura. Mudar o tamanho mantém o canto de cima à
   esquerda.
3. Desenhe e clique em **Salvar** (ou **Ctrl+S**). **Cancelar** volta sem salvar; com mudanças, pede dois cliques.

Cada arte tem até **16 cores** e no máximo **128 × 128** pixels. **Duplicar** faz uma cópia e **Remover** apaga, em
dois cliques.

### Ferramentas

O clique esquerdo usa a ferramenta e o **direito apaga** (no clarear, escurece). **Ctrl** + clique pega a cor de um
pixel com qualquer ferramenta.

| Tecla | Ferramenta |
| --- | --- |
| **B** | Lápis. |
| **E** | Borracha. |
| **G** | Balde: pinta a área da mesma cor. Com **Shift**, troca a cor na arte inteira. |
| **I** | Conta-gotas. |
| **L** | Linha. **Shift** trava em 0°, 45° e 90°. |
| **R** | Retângulo. **R** de novo alterna contorno e cheio; **Shift** faz quadrado. |
| **O** | Elipse. **O** de novo alterna contorno e cheia; **Shift** faz círculo. |
| **D** | Clarear (esquerdo) e escurecer (direito), pela rampa da cor. |
| **T** | Pontilhado: pinta um pixel sim e um não, em xadrez. |
| **S** | Seleção. |

| Tecla | Ajuda |
| --- | --- |
| **[** e **]** | Pincel menor e maior, de 1 a 8 px. O botão do rodapé troca quadrado e redondo (redondo a partir de 3 px). |
| **M** e **Shift+M** | Espelho esquerda e direita, e em cima e embaixo. Valem juntos. |
| **P** | Pixel perfect: tira os cantos em L do lápis de 1 px. |
| **X** | Volta para a cor anterior. |
| **Ctrl+G** | Liga e desliga a grade. |
| **Ctrl+Z**, **Ctrl+Y** | Desfaz e refaz, até 100 passos. |

### Paleta e rampas

A paleta tem 16 cores em 4 linhas, e a cor 0 é sempre transparente. Cada linha é uma **rampa**, do tom escuro à
esquerda ao claro à direita: o clarear e o escurecer andam por ela.

- Dois cliques numa cor (ou **Editar cor**) abrem o seletor. Trocar uma cor muda todos os pixels dela.
- **Criar rampa** refaz a linha da cor escolhida como uma rampa dela: mais escura e azulada à esquerda, mais clara e
  amarelada à direita.

### Seleção, virar e girar

- Com **S**, arraste para marcar um retângulo. Arraste de dentro dele para mover o pedaço.
- As **setas** movem 1 px (com **Shift**, 8 px). **Enter** assenta e **Esc** desfaz o mover e tira a seleção.
- **Ctrl+A** seleciona tudo e **Ctrl+D** tira. **Ctrl+C**, **Ctrl+X** e **Ctrl+V** copiam, recortam e colam;
  **Delete** apaga.
- Com uma seleção, as ferramentas só pintam dentro dela.
- **Shift+H** e **Shift+V** viram na horizontal e na vertical, e o botão de girar gira 90° no sentido horário. Com
  uma seleção, só o pedaço.
- **Limpar** apaga a arte inteira, em dois cliques.

### Navegar no quadro

| Gesto | O que faz |
| --- | --- |
| Roda do mouse | Zoom no ponto do mouse. |
| **Alt**, **Espaço** ou botão do meio + arrastar | Move a vista. |
| **Setas**, sem seleção | Movem a vista. |
| **0** | Enquadra a arte. |
| **+** e **−** | Zoom no centro. |
| Clique na prévia | Leva a vista até aquele ponto. |

O rodapé mostra a posição e a cor sob o mouse. A coluna da direita mostra a arte em 1×, 2× e repetida, como no
mosaico. **Esc** sem seleção faz o mesmo que **Cancelar**.

### Pôr a arte na Tela

- **Imagem:** clique em **Imagem** na lista de **Peças** (ou arraste-a até a miniatura). Ela nasce com a arte escolhida
  na aba **Pixel art**; sem arte escolhida, nasce vazia, com **Escolha a arte**.
- **Escolher a arte:** na aba **Estilo**, seção **Peça**, clique numa miniatura da grade. As marcadas **Da Tela**
  vieram com a Tela, de outro jogador ou de um código. Arrastar uma arte da aba **Pixel art** até a miniatura também
  serve.
- Na mesma seção: **Escala** (1× a 16×, ou **Esticar**), **Alinhar** (9 posições) e **Opacidade**.
- A Tela guarda uma **cópia** da arte: mudar ou apagar a arte depois não muda a Tela. **Atualizar da biblioteca**
  traz a versão atual.
- **Fundo em mosaico:** com nada escolhido, mude o **Fundo** para **Pixel art em mosaico** e escolha a arte na grade.
  Ela se repete pela Tela inteira, por baixo das peças, com **Escala** (1× a 8×) e **Opacidade**.
- A linha **Pixels** da base mostra quanto as artes da Tela somam e o teto.

### Exportar e importar por código

Leve um layout ou uma arte para outro mundo, ou para um amigo, como texto.

| Botão | O que faz |
| --- | --- |
| **Exportar** | Copia o código: na base (embaixo de **Aplicar layout**), do layout; na aba **Pixel art**, da arte escolhida. |
| **Importar** | Lê o código copiado. Na base, aplica o layout no rascunho (**Ctrl+Z** desfaz); na aba **Pixel art**, cria uma arte nova. |
| **Copiar como texto** | Copia numa forma legível, para editar à mão ou pedir a uma IA. **Importar** lê as duas formas. |

- O layout leva as artes junto. As ligações a redes ou armazenamentos que você não tem saem: ligue de novo na aba
  **Dado**.
- Um código inválido mostra o aviso na barra de baixo, e nada muda.
- Para gerar artes e Telas com uma IA, há um guia no GitHub do mod: `docs/guia-pixel-art-com-ia.md`.

## Acesso

Na aba **Acesso** (só o dono e os operadores a veem), dê a outros jogadores o direito de editar a Tela.

1. Escreva o nome em **Convidar jogador…** e escolha uma sugestão, ou aperte **Enter**. Só vale quem já entrou no
   servidor.
2. Com o FTB Teams instalado, adicione um time em **Adicionar time…**.
3. Para tirar alguém, clique duas vezes no **✕** da linha.

Quem está na lista edita como o dono: tamanho, layout e ligações, dentro das redes que cada um pode usar. Ver a Tela
continua liberado para todos.

## Receita

<RecipeFor id="wirelessautomate:signal_screen" />
