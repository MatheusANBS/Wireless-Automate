# Estado das funcionalidades

Uma linha por funcionalidade: o que é, se está pronta e em que versão entrou. Onde fica no código está no [mapa](../mapa/); o desenho, na [especificação](../especificacao.md); o que falta, em [pendencias.md](pendencias.md).

Legenda: ✅ pronto e testado · 🟡 parcial · ⬜ não começado

## Motor e essencial

| Funcionalidade | Estado | Versão | Resumo |
| --- | --- | --- | --- |
| Projeto NeoForge 1.21.1 | ✅ | — | Build, setup e CI |
| Gerenciador com orçamento de tempo | ✅ | — | Rotas por rede e tipo, laço com cursor entre ticks, origens e destinos dormindo |
| Roteador direcional | ✅ | — | Acessa a máquina pelas seis faces; gira (spin) sem perder a configuração |
| Configuração por face | ✅ | — | Modo, prioridade, redstone, filtro e rede por tipo e lado |
| Redes | ✅ | 1.9.0 (por face) | Rede por face, rede ativa, round-robin, grupos de redes, roteador nasce sem rede |
| Acesso às redes privadas | ✅ | 2.7.0 | Convidados e times do FTB Teams (opcional) |
| Filtros e Cartão de Filtro | ✅ | — | Sem limite de entradas, estoque em dois limites, tags, mod, propriedades, JEI |
| Tiers e Cartões de Upgrade | ✅ | 1.2.0 (escada ×8), 2.8.0 (energia pelo Ressonador), 2.16.0 (fluido, químico, Source e capacidades de uma hora), 2.17.0 (a Doca de Padrões) | Oito tiers (três com o Allthemodium), ×8 por degrau, subir tier pelo anel ou pela bancada |
| Profiler e benchmark | ✅ | — | `/wa profile`, `/wa bench`, `scripts/bench.sh` e o comparativo com o Logistics Network |
| Upgrade de Chunk Loading | ✅ | — | Mantém o roteador trabalhando com o jogador longe |

## Ferramentas e telas

| Funcionalidade | Estado | Versão | Resumo |
| --- | --- | --- | --- |
| Telas (GUI) | ✅ | 2.0.0 (tema escuro) | Tema claro e escuro, telas redimensionáveis, roteador com visor 3D e painel do nó |
| Vinculador | ✅ | 2.7.0 (modos atuais); um dono, as que se tocam e o cubo do roteador na 2.18.0 | Modos Vincular e Abrir; Ctrl + clique age nas máquinas do mesmo bloco que se tocam com a clicada (a área e a tela saíram); uma máquina é de um roteador só; com o Vinculador na mão, o cubo do alcance do roteador escolhido e as remotas aparecem no mundo, como o da Doca (2.18.0) |
| Configurador | ✅ | 1.6.0; o Afinador no ramo do roteador de alcance (etapa 3) | O Afinador: clique numa máquina vinculada mostra a proposta dos modos pintada nas faces, o segundo clique aplica (Shift: no grupo); muda só os modos; copiar e colar perfis é pelo Tablet |
| Tablet de Rede | ✅ | 1.0.0 (Fluxos e a Lista com máquinas da 2.2.0 à 2.4.0) | Lista (roteadores e máquinas), Fluxos, Mapa e Redes |
| Roteador de alcance | 🟡 | 1.9.0; a remota quebrada sai sozinha na 2.17.0 | Várias máquinas por roteador, grupos com perfil, lista e destaque no mundo; a presa é máquina do grupo do bloco dela e a vinculada segue o perfil (etapa 1) e dá para desvincular o grupo inteiro, copiar e colar o perfil por máquina e o Atualizar devolve os cartões (etapa 2), e a máquina trocada para de receber na hora (etapa 4), e o Vinculador vincula as do mesmo bloco que se tocam com Ctrl + clique e desvincula num clique, tudo no ramo; a remota quebrada ou trocada sai sozinha (2.17.0); faltam pausar grupo e a página própria no guia |
| Editor de receitas | ✅ | 1.7.0 | `/wa recipes` e tela para operadores |
| Livro-guia | ✅ | 2.14.0 (livro próprio) | Livro próprio de duas folhas, sem dependência de outro mod: índice por grupos com fitas, busca, receitas, cenas 3D que giram (arrasto, zoom na roda, clique duplo volta) e mostram anotações, e a tecla G; 25 páginas pt e en geradas por `scripts/guide/` |

## Armazenamentos

| Funcionalidade | Estado | Versão | Resumo |
| --- | --- | --- | --- |
| Baú, Tanque, Bateria, Tanque Químico e Tanque de Source | ✅ | 1.0 | Capacidade por tier, tela em lista, filtro de entrada, quebrar e levar com o conteúdo |
| Upgrades do Baú (Mesa e Compactação) | ✅ | 2.13.0 | Grade de mesa que se repõe e compactação por cadeias de receitas |

## Energia e cristais

| Funcionalidade | Estado | Versão | Resumo |
| --- | --- | --- | --- |
| Ressonador de Sinal | ✅ | 2.1.0 (rebalanceado na 2.4.0, câmara na 2.8.3) | Gerador multibloco movido a cristais e sintonia |
| Energia ligada ao Ressonador | ✅ | 2.8.0 | Energia do roteador e capacidade da Bateria pela geração do Ressonador do mesmo tier |
| Reparador de Cristais | ✅ | 2.9.0 | Conserta cristais rachados, oito de uma vez |

## Painel

| Funcionalidade | Estado | Versão | Resumo |
| --- | --- | --- | --- |
| Tela de Sinal | ✅ | 2.11.0 (etapas 1 a 3), 2.12.0 (etapa 4) | Painel na parede com peças, armazenamentos, pixel art, animação e imagens |

## Integrações

| Funcionalidade | Estado | Versão | Resumo |
| --- | --- | --- | --- |
| JEI | ✅ | — | Filtros, receitas, estrutura do Ressonador, "+" da Mesa (opcional) |
| Químicos do Mekanism | ✅ | — | Aba Químicos e Tanque Químico (opcional) |
| Source do Ars Nouveau | ✅ | — | Aba Source e Tanque de Source (opcional) |
| Doca de Padrões (AE2 e RS2) | 🟡 | 2.17.0 (etapas 1 a 4) | Bloco com oito tiers e alcance próprio; cada lado cuida de um tipo de máquina, vinculado pelo Vinculador com o perfil pelo Afinador; recebe pedidos de um Pattern Provider do AE2 (também os do ExtendedAE e do Advanced AE, e a CPU quântica) ou de um Autocrafter do RS2 (todos opcionais), entrega cada pedido inteiro a uma máquina livre (a sobra que não coube espera só na máquina dela, uma linha por item), com itens, fluidos e químicos juntos (o químico com o Mekanism e o Applied Mekanistics no AE2 ou a integração do Mekanism no RS2), e devolve o resultado direto à rede do provedor (ME ou RS2); a tela avisa a trava "até o resultado" do AE2; tela de seis linhas, cubo do alcance no mundo; a arte animada (o circuito por tier em textura animada, o giroscópio, o pacote e os fragmentos num renderer por fórmula, o laço de trabalho quando entrega, o miolo escuro e o cristal aceso); a receita só com o AE2 ou o RS2 (a do RS2 só sem o AE2); falta o teste à mão do dono |
| FTB Teams | ✅ | 2.7.0 | Times nas redes privadas (opcional) |
| Allthemodium | ✅ | — | Três tiers a mais, só com o mod |
| Occultism | ✅ | 2.8.1 | Os mineiros dão Cristais de Sinal |
| AE2 e RS2 | ⬜ | — | Não começado |
| Balanceamento das receitas | ⬜ | — | Não começado |
