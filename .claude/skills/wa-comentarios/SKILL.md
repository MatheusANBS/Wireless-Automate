---
name: wa-comentarios
description: Regras dos comentários do Wireless Automate e a varredura que acha os ruins - código comentado, comentário desatualizado (nome, número ou comportamento que não existe mais), histórico ("antes era", "agora"), redundante e TODO esquecido. Use ao escrever ou revisar código do mod (toda tarefa da wa-feature passa por aqui antes do commit), quando o dono pedir para "limpar comentários", "tirar código comentado", "comentário desatualizado", "revisar os comentários", e na revisão final de um ramo. Não use para mudar a convenção de comentar em português nem para encurtar comentários que explicam um porquê.
---

# Comentários do Wireless Automate

O mod é comentado em português e com bastante explicação: o porquê de uma regra, a armadilha de uma API, o custo de um laço, a ligação com outra parte do código. Isso é a convenção e **fica**, mesmo quando é longo. O que esta skill tira é o comentário que mente, que repete o código ou que conta o passado.

## O que não entra (e sai quando aparecer)

| Tipo | Exemplo | O que fazer |
| --- | --- | --- |
| **Código comentado** | `// router.setNetwork(id);` | Apagar. O git guarda a versão antiga. |
| **Desatualizado** | javadoc que cita um método renomeado, "protocolo 8" quando é 15, "165 GameTests", "quando o AE2 for usado" | Corrigir para o que o código faz hoje, ou apagar se não diz mais nada. |
| **Histórico** | "antes era por aba, agora é por face", "mudou na 1.9.0" | Reescrever no presente ("a rede é de cada face"). A história vai no commit e no `docs/progresso.md`. |
| **Redundante** | `// incrementa o contador` acima de `count++`; "Retorna o nome." num `name()` | Apagar, ou trocar pelo porquê que faltava. |
| **TODO esquecido** | `// TODO arrumar depois` | Fazer agora, ou virar uma linha em `docs/progresso.md` (o que falta), e sair do código. |

## O que fica (não confundir com ruído)

- O porquê de uma escolha que não é óbvia ("o `onLoad` só roda no tick seguinte, por isso o `thenWaitUntil`").
- Regras do jogo e do mod que o código aplica ("uma face Armazém não entrega para outra face Armazém").
- Armadilhas de API e de outro mod ("o nome do barril do Sophisticated vem com lacunas pelo bloco").
- Custo e performance ("O(slots) por visita").
- A referência à spec, ao plano ou a outra classe (`{@link}`), desde que o alvo exista.

## Ao escrever código

1. Comentário no presente, dizendo o que é e por quê; nunca o que mudou.
2. Ao renomear, mover ou mudar o comportamento de algo, procure o nome velho nos comentários do repositório inteiro (`grep -rn "nomeVelho" src scripts docs`) e atualize junto, no mesmo commit.
3. Números que mudam (versão do protocolo, contagem de testes, tamanhos de tela) só onde são a fonte da verdade (`CLAUDE.md`, `docs/progresso.md`, a constante no código). No javadoc, prefira `{@link ModPayloads#VERSION}` a escrever o número.
4. Nada de código comentado no commit. Se é para guardar, é para o git.
5. Um `{@link}` aponta para algo que existe; o build não avisa quando o alvo some.

## A varredura

`scripts/varrer.py` acha os candidatos por padrão de texto (código comentado, palavras de histórico, TODO, javadoc trivial e `{@link}` para classe que não existe). Ele não acha o desatualizado de verdade, que só aparece lendo o comentário contra o código.

```bash
python -I .claude/skills/wa-comentarios/scripts/varrer.py                 # só os arquivos mudados no ramo (contra o main)
python -I .claude/skills/wa-comentarios/scripts/varrer.py --tudo          # o repositório inteiro
python -I .claude/skills/wa-comentarios/scripts/varrer.py caminho/A.java  # arquivos escolhidos
```

Cada linha da saída é um **candidato**: abra, leia e decida pela tabela acima. Falso positivo comum: uma frase com parênteses e ponto e vírgula, ou um verbo no passado que descreve o estado de hoje ("o nível pode estar velho").

## Limpeza do repositório inteiro

Quando o dono pedir, ou a cada versão grande:

1. Rode a varredura com `--tudo`.
2. Divida o código em três partes (`network/`, `filter/` e `storage/`; `client/`; o resto, com `build.gradle`, scripts e testes) e mande um subagente de leitura (Explore) para cada uma, com as categorias da tabela, pedindo `arquivo:linha | categoria | trecho | evidência | proposta` num arquivo e "na dúvida, não aponte". Diga que o comentário explicativo longo fica.
3. Leia as listas, descarte o que for opinião e mostre ao dono o total por categoria e alguns exemplos antes de editar.
4. Aplique num commit só ("Comentários: ..."), sem tocar em código, e rode `./gradlew build` e as quatro runs de GameTest (javadoc mexido não muda comportamento, mas um `*/` apagado errado quebra a compilação).
5. Não rode a limpeza enquanto outro agente edita os mesmos arquivos.

## Na wa-feature

Antes do commit de cada tarefa, o implementador roda a varredura nos arquivos que mudou. O revisor de tarefa e o revisor final conferem as categorias da tabela no diff.
