# E2E: guia de uso

O e2e (`client/dev/DevEndToEnd`, o roteiro em `E2eScript`, as suítes em `E2eSuites` e os passos em `E2e*Steps`) abre o jogo com um mundo de verdade, clica pelas telas, confere cada passo no servidor e na tela e escreve `result.txt`. É **rápido por padrão**: só asserções, sem capturas, sem repetir em português e no tema escuro, sem a comparação do desenho antigo com o plasma. Capturas, variantes e medidas de desempenho entram quando pedidas.

## Rodar

```bash
./scripts/e2e.sh                                  # conjunto padrão, rápido, em run/e2e
./scripts/e2e.sh --only reparador,ressonador      # só essas suítes
./scripts/e2e.sh --only tablet --capture tablet-  # com as capturas que começam por "tablet-"
./scripts/e2e.sh --completo                       # tudo: capturas, variantes e as suítes opcionais
./scripts/e2e.sh --lista                          # as suítes
./scripts/e2e.sh --skip guia,escuro --out run/e2e-x
```

No Windows (Git Bash, sem `xvfb-run`) o jogo abre numa janela de verdade: não mexa no mouse nem no teclado até acabar. No Linux roda sob o Xvfb. Um jogo por vez: cada um usa ~3 GB e o mesmo `run/`. O `E2E_TIMEOUT` (segundos) muda o tempo-limite (padrão 600; 1500 no `--completo`). Sem o script, as variáveis valem direto: `WA_E2E=<dir> WA_E2E_ONLY=... ./gradlew runClient`.

## Variáveis

| Variável | Valor | Efeito |
| --- | --- | --- |
| `WA_E2E` | pasta de saída | liga o e2e |
| `WA_E2E_ONLY` | `a,b,c`, `todas` ou vazio | quais suítes rodam; vazio ou `todas` = as padrão (todas menos as opcionais); um nome opcional vale quando pedido; `lista` só imprime as suítes no log |
| `WA_E2E_SKIP` | `a,b` | tira suítes do conjunto escolhido |
| `WA_E2E_CAPTURE` | `off` (padrão), `todas` ou prefixos (`reparador-tela,ressonador-camara`) | quais capturas saem; as outras viram passos instantâneos (a de falha sempre sai) |
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
| `vinculador` | Configurador e Vinculador | redes | padrão |
| `portugues` | telas em português (a variante pt de todas) | redes | opcional |
| `armazenamento` | Baú de armazenamento | redes | padrão |
| `source` | Tanque de Source (só com o Ars Nouveau) | redes | padrão |
| `receitas` | editor de receitas | redes | padrão |
| `escuro` | tema escuro (usa os baús e a Bateria de `armazenamento`, que entra junto) | armazenamento | opcional |
| `ressonador` | Ressonador de Sinal: montagem, tela, câmara e conduto (~130 s com todas as capturas; o passo "buffer cheio" leva ~64 s) | nenhum | padrão |
| `ressonador-desempenho` | tempo de quadro da câmara, desenho antigo × plasma (mede, não afirma) | nenhum | opcional |
| `ressonador-jei` | JEI do Ressonador | nenhum | padrão |
| `reparador` | Reparador de Cristais (~21 s com as capturas) | nenhum | padrão |
| `reparador-animacao` | a animação do Reparador no mundo, com o tempo parado em cada fase (capturas `reparador-anim-*`; confere quantos cristais e quads o BER pôs) | nenhum | padrão |
| `reparador-desempenho` | tempo de quadro do Reparador (mede, não afirma) | nenhum | opcional |
| `guia` | páginas do guia (só com o GuideME) | nenhum | padrão |

Tempos de referência (Windows, janela real): conjunto padrão sem capturas ~343 s; perfil `completo` ~712 s. A tabela por suíte vem no `result.txt` de cada rodada.

## Receitas

- **Verificar uma tela nova:** `./scripts/e2e.sh --only <suíte> --capture <prefixo>` e olhe os PNGs em `run/e2e/`.
- **Antes de fechar uma versão:** `./scripts/e2e.sh --completo`.
- **Suíte que falha:** rode só ela (`--only <suíte>`, com `--capture todas` para ver a tela) e leia `run/e2e/result.txt` e o `client.log`. Cada suíte roda sozinha; se só falha junto das outras, falta declarar o pré-requisito.
- **Português e tema escuro de uma tela:** `--only reparador --variants pt,escuro --capture todas`.

## Armadilhas

- Um Núcleo removido solta cristais, redstone e catalisador como itens: o `E2eResonatorSteps.clear` os descarta, senão aparecem no chão da câmara da montagem seguinte.

## Suíte nova

Registre em `E2eSuites` (nome, descrição, pré-requisito, passos, padrão ou opcional) e declare o pré-requisito; nunca copie passos de outra suíte. Repetições em `pt_br` e no escuro entram por `E2eVariants`; o que só mede desempenho é opcional. Atualize a tabela acima.
