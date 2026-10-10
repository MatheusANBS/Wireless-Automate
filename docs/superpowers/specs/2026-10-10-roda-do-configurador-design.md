# Roda do Configurador: design

Aprovado pelo dono em 10/10/2026 (mockup: https://claude.ai/artifact/TyaivPBHDuZogaZaiKpPQT).

## Problema

O modo Área do Configurador só cola em roteadores presos ao mesmo tipo de máquina da cópia. O dono copiou o roteador de um Barril e tentou colar no de um Baú Wireless: a área recusou ("1 em outra máquina ficou como estava") e pareceu bug. A regra continua, mas vira escolha do jogador.

## Decisões

### Três modos

| Modo | `configurator_mode` | `configurator_any_machine` | Comportamento |
| --- | --- | --- | --- |
| Pincel | ausente (`SINGLE`) | qualquer | clique num roteador cola nele (como hoje) |
| Área · mesma máquina | `area` | ausente | como hoje: só roteadores presos à máquina da cópia |
| Área · qualquer máquina | `area` | `true` | todos os roteadores carregados da área |

- Componente novo `wirelessautomate:configurator_any_machine` (`Unit`/booleano persistente e sincronizado). Ausente = mesma máquina, então varinhas antigas continuam iguais. As chaves `configurator_mode`, `configurator_type`, `configurator_area`, `configurator_machine` e `preset` não mudam.
- Lógica pura em `preset/PasteMode` (enum `BRUSH`, `AREA_SAME`, `AREA_ANY`): `of(LinkerMode, boolean)`, `linkerMode()`, `anyMachine()`, `next()` (o ciclo Pincel → Área mesma → Área qualquer → Pincel). JUnit.
- Shift + clique no ar passa a avançar pelo ciclo dos três.
- No Pincel o `any_machine` é removido do item (ao trocar para o Pincel o componente sai), para o item não guardar estado invisível.

### A roda

- Tecla `key.wirelessautomate.configurator_wheel`, padrão **Alt esquerdo**, categoria `key.categories.wirelessautomate`, `KeyConflictContext.IN_GAME`.
- Abre ao **apertar** (borda: `consumeClick`) com o Configurador na mão principal e sem tela aberta. Segurar e soltar: soltar a tecla (teclado ou botão do mouse, se a tecla for do mouse) escolhe o item sob o cursor e fecha; clique esquerdo escolhe sem fechar; soltar no centro, Esc ou soltar fora dos anéis fecha sem mudar nada. Não pausa o jogo.
- Anel de dentro: os três modos. Anel de fora: Todos e os tipos de `LoadedTypes.LIST` (Químicos e Source só com o mod). A opção atual de cada anel com borda na cor de destaque (`GuiPaint.ACCENT`); a fatia sob o mouse cresce e clareia; o centro mostra nome e descrição do item sob o mouse (por `GuiText`), ou "Soltar aqui fecha" sem nada.
- Abertura animada: escala de 0,6 a 1 e giro de −20° a 0 em ~150 ms, com leve passo além (ease-out-back). Sem animação nas outras coisas além do realce da fatia.
- Cores de `GuiPaint` (painel, tinta, destaque) e de `ResourceStyle` (cor e ícone de cada tipo). Ícones dos modos: o item do Configurador (Pincel) e grades de quadradinhos (mesma máquina: todos da cor do item; qualquer: cores misturadas), como no mockup.
- Geometria pura em `client/WheelLayout` (raios relativos, ponto → anel e índice; fatia 0 no topo, sentido horário). JUnit.
- Escolher manda `ConfiguratorWheelPayload(PasteMode mode, Optional<ResourceType> type)` com o estado completo. O servidor confere o Configurador na mão principal e o tipo em `LoadedTypes`, grava e mostra na action bar `Configurador: modo %s · cola %s`. A tela atualiza o estado dela na hora (otimista).
- `ModPayloads.VERSION` passa de `10` para `11`.

### Textos

- Nomes dos modos: Pincel, Área (mesma máquina), Área (qualquer máquina) (en: Brush, Area (same machine), Area (any machine)).
- Canto 2 em "qualquer máquina": `Área de %s: %s roteadores · clique direito no ar para colar` (sem a contagem da mesma máquina).
- Colar em "mesma máquina" com roteadores de fora: `%s em outra máquina ficaram como estavam (Área: qualquer máquina, na roda)`.
- Tooltip: `Modo: <nome>`; no modo Área qualquer, `Clique no ar: colar em todos os roteadores da área`; linha nova `Segure <tecla>: roda de modos e tipos` (com `Component.keybind`, sem referência a classe de cliente); `Shift + clique no ar: modo <próximo>` no lugar de `to_area`/`to_brush` (estas chaves saem dos dois `lang`).

## Fica de fora

- Roda do Vinculador.
- Opção de máquina no Pincel (continua colando em qualquer uma).
