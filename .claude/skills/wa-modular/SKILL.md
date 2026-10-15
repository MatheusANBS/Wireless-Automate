---
name: wa-modular
description: Regras de tamanho e de divisão das classes do Wireless Automate, com foco nas telas (client/), e o roteiro para dividir uma classe monolítica sem mudar comportamento - medir, planejar as partes, extrair uma por vez, provar que a tela ficou igual pelas capturas. Use ao criar tela, componente ou menu novo (a wa-feature passa por aqui no plano e antes do commit), quando um arquivo passar do limite, quando o dono pedir para "dividir", "modularizar", "quebrar o arquivo", "ficar mais fácil de manter", "arquivo gigante", "monolítico", e na revisão final de um ramo. Não use para mudar comportamento ou visual (isso é wa-feature) nem para limpar comentários (wa-comentarios).
---

# Classes modulares no Wireless Automate

Uma tela do mod cresce fácil: estado, layout, desenho, mouse, teclado, dicas, listas abertas, ganchos de teste e o envio de payloads acabam na mesma classe, e a de 1.900 linhas ninguém consegue ler inteira nem revisar com segurança. Esta skill diz o tamanho aceito, como construir já em partes e como dividir o que já está grande, **sem mudar o que o jogador vê**.

A conversa, o código e os comentários são em português.

## Limites

Meça com o script (só conta, não julga):

```bash
python -I .claude/skills/wa-modular/scripts/medir.py           # arquivos acima dos limites
python -I .claude/skills/wa-modular/scripts/medir.py --tudo    # todos, com a categoria
```

| Categoria | Alvo | Aviso | Dividir |
| --- | --- | --- | --- |
| Tela, componente e widget (`client/`) | até 500 linhas | acima de 600 | acima de 800 |
| Classe de servidor (`menu/`, `block/`, `network/`...) | até 600 | acima de 800 | acima de 1000 |
| GameTests (`gametest/`) e ferramentas de dev (`DevEndToEnd`, `DevScreenshot`) | por área | acima de 1200 | acima de 1500, por área (um arquivo por tela ou por assunto) |
| Método | até 60 linhas | acima de 80 | acima de 120 |

O número é o gatilho para olhar, não a regra. O que manda é **uma responsabilidade por classe**: uma classe de 450 linhas que desenha, guarda estado de rede e faz parse de texto também precisa ser dividida; uma tabela de dados de 900 linhas pode ficar.

## Construir já em partes (tela ou componente novo)

O padrão do mod, que já funciona no `NodePanel`, `TabletList` e `SearchSuggestions`:

- **A tela (`Screen`) é só a anfitriã:** tamanho, redimensionamento (`ResizeHandle`), cabeçalho, abas, os slots do menu, e repassar `render`, `mouseClicked`, `mouseScrolled`, `keyPressed`, `charTyped` e `tooltip` para o componente certo. Os ganchos do e2e e do `DevScreenshot` ficam na anfitriã, delegando.
- **Componente = um retângulo com dono:** guarda o próprio estado de interface, tem `layout(x, y, w, h)`, `render(...)`, `mouseClicked`, `mouseScrolled`, `keyPressed`, `tooltip(mx, my)` e devolve `true` quando consumiu o evento. Não conhece a tela que o contém.
- **Ações para fora por uma interface estreita** (como `NodePanel.Actions`): o componente diz "o jogador escolheu o modo X na face Y"; quem manda o payload é a anfitriã. Nada de componente chamando `PacketDistributor` direto se outra tela também o usa.
- **Listas abertas, popups e menus suspensos** (rede, redstone, alvo, sugestões) são componentes próprios, reaproveitáveis entre telas.
- **Lógica pura sai para uma classe pura com JUnit** (sem classes do Minecraft): layout, geometria, filtro, ordenação, abreviação, navegação de teclado. Precedentes: `TabLayout`, `CardGrid`, `FaceNet`, `TabletSearch`, `ListGroups`, `ResizeHandle`.
- **Desenho repetido** vira método estático num ajudante de desenho já existente (`GuiPaint`, `GuiText`, `ResourceStyle`) ou num novo, nunca copiado entre telas.
- **Composição, não herança**: nada de `BaseTabletScreen extends BaseScreen extends ...` para compartilhar código. Herança só onde o Minecraft exige (`Screen`, `AbstractContainerScreen`, `AbstractWidget`).
- **Pacote por tela** quando ela tem mais de uma classe: `client/tablet/`, `client/router/`, `client/filter/`, `client/linker/`, `client/recipe/`, `client/storage/`. O que é comum a várias telas (`GuiPaint`, `GuiText`, `GuiThemes`, `ThemeToggle`, `ResizeHandle`, `ResizeGrip`, widgets) fica em `client/`. As ferramentas de dev (`DevEndToEnd`, `DevScreenshot` e as partes delas, `E2e*` e `Screenshot*`) ficam em `client/dev/`; o `ClientSetup` fica em `client/`.
- **Visibilidade mínima**: package-private dentro do pacote da tela; `public` só para o que a anfitriã de outro pacote, o `ClientSetup` ou as ferramentas de dev usam.
- Nada de classe `Utils`/`Helper` genérica: o nome diz o que faz (`PanelLayout`, `NetworkPicker`, `ProfileBand`).

Regras do mod que continuam valendo em toda parte: cores só pelo `GuiPaint` na hora de desenhar, texto variável pelo `GuiText`, tipos pelo `LoadedTypes.LIST`, cliente só em `client/` e nunca referenciado por código comum.

## Dividir o que já está grande

Uma divisão **não muda comportamento nem visual**. Se aparecer um bug no caminho, anote e conserte num commit separado, depois.

1. **Meça e leia.** `medir.py`, depois leia a classe inteira e faça o mapa: campos de estado (quem lê, quem escreve), grupos de métodos por responsabilidade, ganchos de teste (quem chama de fora: `grep -rn "NomeDaClasse\." src`).
2. **Planeje as partes** antes de mexer: nome, responsabilidade em uma frase, estado que ela passa a ser dona, interface com a anfitriã. Cada parte dentro do alvo da tabela. Escreva o plano no relatório.
3. **Capture o antes.** Com o código ainda sem mudança: `WA_SCREENSHOT=$PWD/run/shots-antes ./gradlew runClient` (no Windows, sem `xvfb-run`; use `WA_SCREENSHOT_ONLY=<prefixo>` para só a tela em questão, se o `DevScreenshot` tiver o prefixo).
4. **Extraia uma parte por vez**, compilando a cada uma (`./gradlew compileJava`): mova o código, não reescreva. Mantenha nomes de métodos e a ordem das chamadas. Ao mudar de pacote, ajuste `ClientSetup`, `DevEndToEnd`, `DevScreenshot` e os `{@link}` (a `wa-comentarios` acha os quebrados).
5. **Prove que ficou igual:**
   - `./gradlew build runGameTestServer` (e as outras três runs se tocou em código comum);
   - o e2e `WA_E2E="$PWD/run/e2e" ./gradlew runClient` (`run/e2e/result.txt` = `OK`);
   - capture o depois em `run/shots-depois` e compare: `python -I .claude/skills/wa-modular/scripts/comparar.py run/shots-antes run/shots-depois`. Diferença só fora do painel (o mundo atrás anima) é normal; dentro do painel, investigue.
6. **Atualize o `CLAUDE.md`** (mapa do código: pacote novo, classes novas e o papel de cada uma) e rode a `wa-comentarios`.
7. **Commit por tela**: `Modular: <tela> em partes (<parte>, <parte>, ...)`, sem linhas de atribuição e sem push.

Se a anfitriã continuar acima do limite depois de tirar tudo o que é componente, é sinal de que ainda falta uma parte; não force uma divisão artificial só pelo número, registre no relatório o porquê.

## Na wa-feature

- No plano: cada tela ou componente novo diz em quais classes vai, com a responsabilidade de cada uma, dentro do alvo da tabela.
- Antes do commit de cada tarefa, o implementador roda `medir.py` nos arquivos que mudou; um arquivo acima de "Dividir" volta para divisão antes de seguir.
- O revisor de tarefa e o revisor final conferem: arquivo novo acima do alvo, arquivo que cruzou o limite, componente que manda payload direto, lógica pura sem JUnit, cópia de desenho entre telas.

## Vários arquivos de uma vez

Um implementador por tela (ou par de telas que dividem classes, como roteador + `NodePanel`), **um de cada vez**: todos rodam o mesmo jogo para o e2e e as capturas, e dois jogos juntos estouram a memória (~3 GB cada). Cada um com o próprio commit, revisado antes do próximo.
