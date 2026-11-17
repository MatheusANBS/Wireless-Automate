# E2E: guia de uso

O e2e (`client/dev/DevEndToEnd`, o roteiro em `E2eScript`, as suítes em `E2eSuites` e os passos em `E2e*Steps`) abre o jogo com um mundo de verdade, clica pelas telas, confere cada passo no servidor e na tela e escreve `result.txt`. É **rápido por padrão**: só asserções, sem capturas, sem repetir em português e no tema escuro. Capturas, variantes e medidas de desempenho entram quando pedidas.

## Rodar

```bash
./scripts/e2e.sh                                  # conjunto padrão, rápido, em run/e2e
./scripts/e2e.sh --only reparador,ressonador      # só essas suítes
./scripts/e2e.sh --only tablet --capture tablet-  # com as capturas que começam por "tablet-"
./scripts/e2e.sh --completo                       # tudo: capturas, variantes e as suítes opcionais
./scripts/e2e.sh --lista                          # as suítes
./scripts/e2e.sh --only ressonador --capture armilar --shaders bsl --out run/e2e-armilar-bsl   # com shaderpack
./scripts/e2e.sh --skip guia,escuro --out run/e2e-x
```

No Windows (Git Bash, sem `xvfb-run`) o jogo abre numa janela de verdade: não mexa no mouse nem no teclado até acabar. No Linux roda sob o Xvfb. Um jogo por vez: cada um usa ~3 GB e o mesmo `run/`. O `E2E_TIMEOUT` (segundos) muda o tempo-limite (padrão 600; 1500 no `--completo`). Sem o script, as variáveis valem direto: `WA_E2E=<dir> WA_E2E_ONLY=... ./gradlew runClient`.

## Variáveis

| Variável | Valor | Efeito |
| --- | --- | --- |
| `WA_E2E` | pasta de saída | liga o e2e |
| `WA_E2E_ONLY` | `a,b,c`, `todas` ou vazio | quais suítes rodam; vazio ou `todas` = as padrão (todas menos as opcionais); um nome opcional vale quando pedido; `lista` só imprime as suítes no log |
| `WA_E2E_SKIP` | `a,b` | tira suítes do conjunto escolhido |
| `WA_E2E_CAPTURE` | `off` (padrão), `todas` ou prefixos (`reparador-tela,armilar-sintonia`) | quais capturas saem; as outras viram passos instantâneos (a de falha sempre sai) |
| `WA_E2E_VARIANTS` | `off` (padrão), `todas` ou `pt`, `escuro` | se as repetições em português e no tema escuro entram nas suítes que as têm (os PNGs saem como `<nome>-pt` e `<nome>-escuro`) |
| `WA_E2E_PROFILE` | `rapido` (padrão) ou `completo` | `completo` = `CAPTURE=todas`, `VARIANTS=todas` e as suítes opcionais; as outras variáveis, quando presentes, ganham do perfil |

O `result.txt` começa por `OK` ou `FALHA: <passo> <motivo>`, traz o log dos passos e termina com `# Tempo por suíte` (nome, passos, segundos, capturas) e os `# 10 passos mais lentos`. O que vem antes da primeira suíte (abertura e, se preciso, as redes de B) é a `abertura`.

## Suítes

Ordem do registro. "Pré-requisito": o que a suíte pressupõe além da abertura; o roteiro monta sozinho (`redes` = as redes de B, `armazenamento` = também os baús e a Bateria dessa suíte, que entra junto).

| Suíte | Prova | Pré-requisito | Tipo |
| --- | --- | --- | --- |
| `filtro` | tela de filtro | nenhum | padrão |
| `roteador` | nome, redes por face, tamanhos, painel, cartões e chunk loading | nenhum (faz as redes de B) | padrão |
| `alcance` | roteador de alcance | redes | padrão |
| `tablet` | transporte por aba e o Tablet | redes | padrão |
| `vinculador` | Vinculador | redes | padrão |
| `afinador` | Afinador do Configurador: uma fornalha vinculada no ar, o clique pinta a proposta (capturas `cfg-afinador-*`, com `pt` e `escuro`), o segundo clique aplica, a tecla de uso segurada não confirma (o segundo `InteractionKeyMappingTriggered` vem cancelado e a fornalha não muda), o clique no ar apaga a proposta nos dois lados, mirar outro bloco por 2 s também (no cliente e no servidor), e as letras com dois tipos por face (com a action bar limpa) | nenhum | padrão |
| `portugues` | telas em português (a variante pt de todas) | redes | opcional |
| `armazenamento` | Baú de armazenamento | redes | padrão |
| `source` | Tanque de Source (só com o Ars Nouveau) | redes | padrão |
| `receitas` | editor de receitas | redes | padrão |
| `escuro` | tema escuro (usa os baús e a Bateria de `armazenamento`, que entra junto) | armazenamento | opcional |
| `ressonador` | Ressonador de Sinal: a Armilar pela metade com a projeção, solta (uma terra no ar e uma Coluna faltando) e formada, de lado, de frente e de cima, com o topo de cada peça de face, a tela e a câmara; a Armilar em cada fase da conjunção (sintonia, preparação, estouro, arco, impacto, dissolução, vazão 0), os cristais, o buffer cheio, a recarga, a montagem e o rachado, a luz no mundo e a mão (capturas `armilar-*`, câmera do filme; confere por fase quantos sprites e fitas a Armilar pôs no buffer, e na luz no mundo e na mão quantas luzes o `FxLights` anotou: ao menos 1 no sol e 2 no clarão e na Porta com a vazão 7 (a Armilar tem 3 luzes, e o clarão já apagou quando a Porta enche); só vale a cena desenhada no tick atual ou no anterior; o `armilar-fluxo-real` tira energia da Porta a cada tick, sem vazão forçada, e exige o arco acima da sintonia) (~140 s com todas as capturas) | nenhum | padrão |
| `ressonador-desempenho` | tempo de quadro da Armilar com 0, 1 e 10 à vista e, de pior caso, as 10 na conjunção (vazão 7, arco, meia-noite, luz no mundo ligada) (mede, não afirma; sem pacote, 120 quadros por medida) | nenhum | opcional |
| `fx-base` | o caminho de desenho dos efeitos (`client/fx`): um brilho e uma fita de teste atrás de uma parede de pedra (`fx-base-oclusao`: cortado pela borda), a 2 blocos do jogador (`fx-base-perto`: inteiro) e a 60 blocos (`fx-base-longe`: nada, o corte de 48); com `--shaders complementary` ou `bsl`, por cima do pacote | nenhum | opcional |
| `armilar-fabulous` | a Armilar com os gráficos Fabulous: a conjunção, a luz no mundo e a mão; troca os gráficos e recria os alvos, e devolve o de antes no fim, mesmo que já fosse Fabulous (roda sem pacote: com o Iris ligado a suíte falha de propósito, com a mensagem pedindo `--shaders nenhum`) | nenhum | opcional |
| `ressonador-jei` | JEI do Ressonador | nenhum | padrão |
| `reparador` | Reparador de Cristais (~21 s com as capturas) | nenhum | padrão |
| `reparador-animacao` | a animação do Reparador no mundo, com o tempo parado em cada fase (capturas `reparador-anim-*`; confere quantos cristais e quads o BER pôs) | nenhum | padrão |
| `reparador-desempenho` | tempo de quadro do Reparador (mede, não afirma) | nenhum | opcional |
| `doca` | a tela da Doca de Padrões nos três cenários do mockup (base em uso, recém-colocada e config baixada, esta com o provedor do Leste em "travar até o resultado" e a linha "Tire a trava do provedor"), montados ao lado do jogador com máquinas de verdade e o Pattern Provider do AE2 encostado, nos dois temas, com nenhum texto cortado (capturas `doca-*`, com `pt`; `doca-uso-dica`, a dica de uma linha); depois, o cubo do alcance no mundo com o Vinculador na mão, a Doca Básica escolhida e oito fornalhas em dois lados, cada uma na cor do lado (`doca-cubo`); guardado o Vinculador o cubo some, pego de novo volta, e esquecido no cliente volta pelo pedido ao servidor; por último, o modelo estático: as Docas dos tiers carregados lado a lado (`doca-blocos`), a Elite de perto (`doca-perto`) e à noite, com o circuito aceso (`doca-noite`), e de dia de 8 e de 16 blocos pelo Sul, para as etiquetas (`doca-8` e `doca-16`); e a animação: uma Doca Elite no ar, o evento de trabalho do Leste que chega ao cliente e faz o laço seguinte desenhar o pacote e os fragmentos, e o tempo parado (`DockClock`) em cada fase da tabela da spec, na câmera dos GIFs do Blockbench, conferindo quantas caixas o renderer pôs (`doca-anim-repouso-*`, `doca-anim-trabalho-*`, o pacote pelo Norte, `doca-anim-norte-0.7`, e o trabalho à meia-noite, com o cristal e os fragmentos acesos, `doca-anim-noite-2.0`) | nenhum | padrão |
| `doca-gif` | os laços de repouso e de trabalho da Doca quadro a quadro (120 quadros de 0,05 s cada, `doca-gif-<laço>-NNN`, na câmera e no campo de visão dos GIFs do Blockbench) e 12 s de trabalho contínuo pelo caminho do jogo, com um evento a cada 100 ticks (240 quadros, `doca-gif-continuo-NNN`: dois laços inteiros, o segundo pelo lado do evento do meio do primeiro), para montar os GIFs do jogo fora dele | nenhum | opcional |
| `doca-desempenho` | tempo de quadro com 50 Docas Elite à vista: sem o renderer, no repouso, paradas no trabalho e fora de vista (mede, não afirma) | nenhum | opcional |
| `tela` | Tela de Sinal: a aba Tamanho (4 × 2 com ↗ aplicado e conferido no servidor, o problema da tocha na prévia), a aba Acesso (um convidado e, com o FTB Teams, um time), a aba Conteúdo (o Painel da rede dos Fluxos em Itens), um número chegando, o desenho no mundo e as quatro fontes TTF em três tamanhos (capturas `tela-*`, com `pt` e `escuro`) | nenhum | padrão |
| `tela-desempenho` | tempo de quadro com 8 Telas 32 × 32 com o Painel contra nenhuma, e o tempo de um redesenho da imagem (mede, não afirma) | nenhum | opcional |
| `guia` | o livro-guia (tela própria, sem outro mod): abre pelo gancho do item, a folha de rosto e o índice, clica num link e volta, a busca (digitar, Enter, descer até o 9º resultado, Esc), a tecla G sobre um item com a tecla e o caractere, como o jogo manda (abre na página com a busca vazia e o Esc volta ao inventário; num baú, volta ao baú com o menu sincronizado; com um campo de texto focado ou a busca do criativo com foco, não abre), a dica da folha de rosto com teclas de nome longo, a cena do roteador (arrastar gira sem virar a folha, a roda dá zoom, o clique duplo volta; capturas `guia-cena-antes` e `guia-cena-girada`), a anotação da cena das redes (o mouse no centro projetado do bloco da fornalha dá a dica "Furnace · Extract", ou "Fornalha · Extrai" em português; capturas `guia-anotacao` e `guia-anotacao-pt`; na passada em português e só com a captura pedida, a cena dá uma volta em 24 quadros de 15°, `guia-giro-000` a `-023`, para o GIF do `scripts/curseforge/gerar_gif.py --prefixo guia-giro`), e cada página com todas as folhas viradas nos dois idiomas (capturas `guia-<pagina>-<n>`, um par por captura; confere por `GuideScreen.problems()` que nenhum texto saiu da folha) | nenhum | padrão |

Tempos de referência (Windows, janela real): conjunto padrão sem capturas ~343 s e perfil `completo` ~712 s (medidos antes da Armilar; hoje são só uma ordem de grandeza). Da rodada de 16/11/2026: a suíte `ressonador` com `--capture armilar` leva ~178 s (132 passos, 37 capturas), e a `ressonador-desempenho` mede, sem pacote, 1,03 ms/quadro com nenhuma Armilar, 1,39 com uma, 1,89 com dez e 2,12 com dez na conjunção à meia-noite. A tabela por suíte vem no `result.txt` de cada rodada.

## Shaderpacks (`--shaders` e `-Pshaders`)

- `--shaders nenhum|complementary|bsl` (padrão `nenhum`) escreve o `run/config/iris.properties` (liga o Iris e escolhe o pacote de `run/shaderpacks`) e abre o cliente com `-Pshaders`. As capturas de efeito (`armilar-*`, `fx-base-*`) saem então por cima do pacote. Só o `iris.properties` escolhe o pacote, nunca o `run/options.txt`.
- A flag `-Pshaders` do Gradle liga com qualquer valor e põe o Sodium e o Iris no `localRuntime`, isto é, em **todo** `runtimeClasspath`, inclusive nas runs de GameTest e de data (`runGameTestServer*`, `runData`). **Não use `-Pshaders` nelas**: o servidor de teste não precisa dos dois e a flag só muda o que carrega. Ela serve ao `runClient` e ao `scripts/e2e.sh --shaders`.
- Rodar com `-Pshaders` faz o próprio jogo acrescentar as teclas do Iris (R, K e O, `key_iris.keybind.*`) ao `run/options.txt`, e uma rodada sem a flag as tira de novo. É o jogo que reescreve o arquivo (nenhum passo do e2e toca nessas linhas); o hash do `options.txt` muda entre rodadas com e sem Iris sem que algo tenha sido estragado. Não restaure o arquivo à mão por isso.
- O `--shaders nenhum` grava `enableShaders=false` no `run/config/iris.properties`, para um `runClient -Pshaders` manual depois subir sem shader (sem isso o pacote da rodada anterior fica ligado).
- Um ambiente por rodada e um jogo por vez: sem pacote, Complementary, BSL e, à parte e sem `--shaders`, a suíte `armilar-fabulous`.

## Receitas

- **Verificar uma tela nova:** `./scripts/e2e.sh --only <suíte> --capture <prefixo>` e olhe os PNGs em `run/e2e/`.
- **Antes de fechar uma versão:** `./scripts/e2e.sh --completo`.
- **Suíte que falha:** rode só ela (`--only <suíte>`, com `--capture todas` para ver a tela) e leia `run/e2e/result.txt` e o `client.log`. Cada suíte roda sozinha; se só falha junto das outras, falta declarar o pré-requisito.
- **Português e tema escuro de uma tela:** `--only reparador --variants pt,escuro --capture todas`.

## Armadilhas

- Um Núcleo removido solta cristais, redstone e catalisador como itens: o `E2eResonatorSteps.clear` os descarta, senão aparecem no chão da câmara da montagem seguinte.

## Suíte nova

Registre em `E2eSuites` (nome, descrição, pré-requisito, passos, padrão ou opcional) e declare o pré-requisito; nunca copie passos de outra suíte. Repetições em `pt_br` e no escuro entram por `E2eVariants`; o que só mede desempenho é opcional. Atualize a tabela acima.
