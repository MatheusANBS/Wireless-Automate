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
2. Clique nela com a **mão vazia** para abrir a tela de configuração. Só quem a colocou (e os operadores) pode
   configurar; os outros só olham.

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

## Receita

<RecipeFor id="wirelessautomate:signal_screen" />
