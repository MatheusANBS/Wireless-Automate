# Wireless Automate · pacote de design

Transporte wireless de itens, fluidos, energia e químicos para NeoForge 1.21.1 (ATM10). Este pacote reúne a especificação, os sprites e a prévia visual. Versão de 7 de outubro de 2026.

## Conteúdo

O esquema abaixo é o do pacote original; no repositório, a prévia e a folha ficam em `docs/preview/` e os assets em `src/main/resources/assets/wirelessautomate/`.

```
wireless-automate/
├── README.md
├── docs/
│   └── especificacao.md          especificação completa do mod
├── preview/
│   ├── rascunho-visual.html      cena 3D, telas clicáveis e texturas (abre no navegador)
│   └── folha-de-sprites.png      todos os sprites ampliados 8x (gerada pelo script)
└── assets/wirelessautomate/      pronto para src/main/resources/assets/
    ├── blockstates/router.json
    ├── models/block/router_<tier>.json
    ├── models/item/*.json
    └── textures/
        ├── item/                 9 itens (com o livro-guia), 16×16
        ├── block/                faces do roteador por tier, 16×16
        └── gui/                  sprites das portas, 16×16
```

## Sprites

| Arquivo | Uso |
| --- | --- |
| `item/configurator.png` | Configurador (varinha) |
| `item/network_tablet.png` | Tablet de rede |
| `item/filter_card.png` | Cartão de filtro |
| `item/linker.png` | Vinculador (controle) |
| `item/guide.png` | Livro-guia (GuideME) |
| `item/chunk_loader_upgrade.png` | Upgrade de chunk loading: cartão com faixa verde e grade 3×3 de chunks |
| `item/tier_core_<tier>.png` | Upgrades de tier Avançado, Elite e Ultimate: cartão de circuito com faixa e núcleo do chip na cor do tier e 1 a 3 marcas de nível (não existe o básico) |
| `block/router_<tier>_<face>.png` | Faces do roteador: front, back, side, top, bottom e antenna |
| `block/storage_<tipo>_<tier>_side.png` | Laterais do armazenamento (`chest`, `tank`, `battery`, `chemical_tank`): quatro slots com itens, visor de fluido pela metade, raio com brilho, visor cheio de gás |
| `block/storage_<tipo>_<tier>_top.png` | Topo do armazenamento: arcos wireless na cor do tier e núcleo na cor do recurso |
| `block/storage_<tier>_bottom.png` | Base dos quatro tipos: grade de ventilação |
| `gui/port_extract.png` | Porta Extrai (azul, seta para cima) |
| `gui/port_insert.png` | Porta Insere (laranja, seta para baixo) |
| `gui/port_both.png` | Porta Armazém, antes Ambos (verde, seta dupla) |
| `gui/port_none.png` | Porta Nenhum (contorno pontilhado) |

Tiers: `basic`, `advanced`, `elite`, `ultimate`.

## Roteador no jogo

- **Texturas de bloco:** cada face ocupa o canto superior esquerdo de um quadro 16×16 (frente 14×6, lateral 12×6, topo e base 14×12). O modelo já aponta as UVs certas.
- **Modelo:** corpo de 14×6×12 px na base do bloco, frente (LEDs) para o sul, antenas atrás.
- **Blockstate:** propriedades `facing` (face da máquina onde o roteador foi preso) e `tier`. As rotações seguem a mesma convenção do para-raios: `up` sem rotação, `down` com x=180, laterais com x=90 e y conforme a direção.
- **LEDs:** decorativos, desenhados na própria textura. Não acendem por estado nem têm camada emissiva (decisão do dono: o modelo fica como está).

## Família de cartões

Os upgrades de tier, o upgrade de chunk loading e o cartão de filtro têm a mesma silhueta: cartão 16×12 com contorno escuro, faixa de 3 px à esquerda, ícone no centro e contatos dourados embaixo.

- **Upgrades de tier:** placa ardósia, chip preto com o núcleo na cor do tier (a mesma de `GuiPaint.tierColor`: ouro, ciano, roxo) e marcas brancas na faixa (1, 2 ou 3) para quem não distingue as cores.
- **Chunk loading:** placa ardósia, faixa verde e grade de chunks com o do centro aceso.
- **Filtro:** placa verde de circuito, faixa prateada e funil claro no lugar do chip.

## Armazenamento do mod (0.2)

Quatro cubos inteiros (Baú, Tanque, Bateria e Tanque Químico) com o casco e o acento por tier do roteador. A moldura é a mesma nas seis faces e sai de `moldura_armazenamento` no script:

- borda de 1 px na cor do tier, com cantos em L de 3 px um tom acima (abaixo nos lados escuros);
- anel de casco com chanfro;
- recesso com sombra em cima/esquerda e lábio claro embaixo/direita, em volta de um painel 10×10.

As quatro laterais são iguais, porque o roteador pode ser preso em qualquer face. O painel mostra o recurso:

- **Baú:** quatro slots de inventário (pedra, lingote de ouro, diamante e redstone).
- **Tanque:** visor de vidro com fluido azul até ~60%, reflexo e marcas de nível.
- **Bateria:** raio amarelo com brilho âmbar no fundo escuro.
- **Tanque Químico:** visor cheio de gás verde-amarelado com bolhas e as mesmas marcas de nível.

O topo traz os arcos wireless do roteador na cor do tier e um núcleo na cor do recurso, para achar o bloco olhando de cima; a base é a mesma para os quatro. A folha mostra os 16 cubos montados, e `docs/preview/armazenamento-preview.png` é o recorte deles.

## Estilo e como regenerar

Todos os sprites saem de `scripts/textures/gerar_texturas.py` (Python 3 com Pillow). Cada sprite é uma grade de texto em que cada caractere aponta para uma cor de uma paleta nomeada (`PALETAS` no topo do script): para ajustar uma cor, mude a paleta; para mexer no desenho, mude a grade.

```bash
python scripts/textures/gerar_texturas.py            # grava os 73 PNGs e a folha de sprites
python scripts/textures/gerar_texturas.py --so-folha # só refaz docs/preview/folha-de-sprites.png
```

A folha mostra tudo ampliado 8×, o roteador montado (frente + topo + lateral) de cada tier e os itens em 1× e 2×. Regras seguidas pelo script (e que ele confere):

- 16×16 RGBA, alfa só 0 ou 255, sem anti-aliasing nem ruído;
- 3 a 5 tons por material, luz de cima à esquerda;
- itens com contorno escuro;
- faces do roteador só no canto superior esquerdo do quadro (o resto transparente), menos o topo, que é preenchido com o casco porque também é a textura de partícula;
- acento do roteador por tier: ferro (basic), ouro (advanced), ciano (elite), roxo (ultimate); os LEDs da frente ficam em x=2, 4, 6 e 8 como lentes decorativas.
