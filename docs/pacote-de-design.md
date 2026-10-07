# Wireless Automate · pacote de design

Transporte wireless de itens, fluidos, energia e químicos para NeoForge 1.21.1 (ATM10). Este pacote reúne a especificação, os sprites em rascunho e a prévia visual. Versão de 7 de outubro de 2026.

## Conteúdo

```
wireless-automate/
├── README.md
├── docs/
│   └── especificacao.md          especificação completa do mod
├── preview/
│   ├── rascunho-visual.html      cena 3D, telas clicáveis e texturas (abre no navegador)
│   └── folha-de-sprites.png      todos os sprites ampliados 8x
└── assets/wirelessautomate/      pronto para src/main/resources/assets/
    ├── blockstates/router.json
    ├── models/block/router_<tier>.json
    ├── models/item/*.json
    └── textures/
        ├── item/                 9 itens, 16×16
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
| `item/chunk_loader_upgrade.png` | Upgrade de chunk loading |
| `item/tier_core_<tier>.png` | Núcleos Básico, Avançado, Elite e Ultimate |
| `block/router_<tier>_<face>.png` | Faces do roteador: front, back, side, top, bottom e antenna |
| `gui/port_extract.png` | Porta Extrai (azul, seta para cima) |
| `gui/port_insert.png` | Porta Insere (laranja, seta para baixo) |
| `gui/port_both.png` | Porta Ambos (verde, seta dupla) |
| `gui/port_none.png` | Porta Nenhum (contorno pontilhado) |

Tiers: `basic`, `advanced`, `elite`, `ultimate`.

## Roteador no jogo

- **Texturas de bloco:** cada face ocupa o canto superior esquerdo de um quadro 16×16 (frente 14×6, lateral 12×6, topo e base 14×12). O modelo já aponta as UVs certas.
- **Modelo:** corpo de 14×6×12 px na base do bloco, frente (LEDs) para o sul, antenas atrás.
- **Blockstate:** propriedades `facing` (face da máquina onde o roteador foi preso) e `tier`. As rotações seguem a mesma convenção do para-raios: `up` sem rotação, `down` com x=180, laterais com x=90 e y conforme a direção.
- **LEDs:** na textura ficam só os encaixes escuros. O brilho de cada LED (energia, rede, atividade e destino cheio) entra como camada emissiva separada, a desenhar na fase de código.

## Próximos passos

1. Refinar as texturas no Blockbench, mantendo nomes e tamanhos.
2. Criar o projeto NeoForge 1.21.1 e copiar `assets/` para `src/main/resources/`.
3. Seguir o roadmap v1 da especificação.

Os sprites foram desenhados pixel a pixel em código e servem como rascunho de direção visual.
