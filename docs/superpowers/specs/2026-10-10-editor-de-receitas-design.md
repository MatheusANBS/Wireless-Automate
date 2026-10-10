# Editor de receitas (`/wa recipes`): design

Aprovado pelo dono em 10/10/2026, a partir do mockup `docs/preview/editor-de-receitas.html`
([artifact](https://claude.ai/artifact/TGpmYD6GZM4eqjZfcRJ6AH)).

## Objetivo

Um operador troca, desativa e restaura as receitas de bancada do Wireless Automate dentro do jogo, sem
escrever datapack nem script. A mudança vale para todos os mundos da instância (num servidor, para o
servidor) e sobrevive a atualizações do mod.

## Escopo (decisões do dono)

- Só as receitas **do mod**, de bancada **com forma** (`minecraft:crafting_shaped`) e **sem forma**
  (`minecraft:crafting_shapeless`). As especiais (`filter_card_copy`, `router_upgrade`) ficam fora.
- Alcance: **todos os mundos da instância** (datapack global na pasta de config), não por mundo.
- Extras: slot vira **tag**, **quantidade do resultado** (1 a 64) e **desativar** a receita.
- Fora: receitas de outros mods, criar receita nova, trocar com forma ↔ sem forma, ingredientes com
  componentes, receitas de máquina, ramo 1.20.1 (desce depois do porte).

## Comando

`/wa recipes`, permissão 2 (como `/wa face`). Abre a tela para o jogador que rodou. Num mundo sem cheats o
dono precisa ligá-los ("Abrir para LAN"), como em qualquer comando de operador.

## Onde grava: datapack global

- Pasta: `config/wirelessautomate/recipes/` (`FMLPaths.CONFIGDIR`). O mod cria a pasta e o
  `pack.mcmeta` (`pack_format` 48, descrição "Wireless Automate: receitas editadas") quando faltam.
- Registro: `AddPackFindersEvent` (barramento do mod), só para `PackType.SERVER_DATA`, com um `Pack`
  no molde de `AddPackFindersEvent.addPackFinders`, mas sobre a pasta de config:
  id `wirelessautomate:recipe_overrides`, `PackSource.BUILT_IN`, sem `KnownPack`,
  `PackSelectionConfig(required = true, Pack.Position.TOP, fixedPosition = false)`. Obrigatório = sempre
  selecionado, também em mundos que já existiam; no topo = acima do jar do mod.
- Arquivo de override: `data/wirelessautomate/recipe/<caminho do id>.json`, mesmo id da receita do mod.

## O que é editável

A lista é a união de:
1. as receitas carregadas no `RecipeManager` com namespace `wirelessautomate` e serializador
   `RecipeSerializer.SHAPED_RECIPE` ou `SHAPELESS_RECIPE`;
2. os overrides desativados que estão na pasta (a receita não carrega, mas precisa aparecer para ser
   reativada), desde que o mod tenha um arquivo padrão com esse id.

Cada receita tem um **padrão**: o JSON do jar do mod, lido pelo `ResourceManager` do servidor
(`getResourceStack`, o recurso cujo `sourcePackId()` não é o nosso pack). O Ultimate aparece uma vez só,
na variante que carregou (`tier_core_ultimate`, `_atm` ou `_atm_star`, cada uma com o próprio id).

## Modelo: rascunho de receita

Puro, sem classes do Minecraft (JUnit):

- `kind`: `SHAPED` ou `SHAPELESS`, o mesmo do padrão; não muda.
- `slots`: 9 posições, cada uma vazia, `item(id)` ou `tag(id)` (ids como texto `namespace:caminho`).
- `count`: 1 a 64.
- `disabled`: booleano.

Regras:
- Com forma: a grade 3×3 é a receita. Ao gravar, linhas e colunas vazias nas bordas saem (como o vanilla
  faz ao ler), as chaves são letras `A`, `B`, `C`... na ordem da primeira aparição e ingredientes iguais
  dividem a letra.
- Sem forma: os slots não vazios viram a lista de ingredientes, na ordem da grade.
- Grade toda vazia não grava (erro na tela: "A receita precisa de pelo menos um ingrediente").

## O JSON gravado

Parte do JSON padrão (preserva `category` e as condições `neoforge:conditions`) e troca `pattern`/`key`
ou `ingredients` e `result` (`{"id", "count"}`). Assim o override do Ultimate do ATM continua só valendo
com o Allthemodium e não quebra se o pack perder o mod.

- **Desativar:** acrescenta `{"type": "neoforge:false"}` às condições (o resto do conteúdo é o rascunho
  atual). Reativar tira essa condição.
- **Restaurar padrão:** apaga o arquivo.

## Recarga

- Salvar, desativar, reativar e restaurar só mexem no arquivo e contam uma mudança pendente (contador do
  servidor, zerado a cada recarga e ao parar o servidor).
- **Recarregar agora** (ou fechar a tela com pendências) roda a mesma recarga do `/reload`:
  `server.reloadResources(server.getPackRepository().getSelectedIds())`. Quando termina, quem está com a
  tela aberta recebe o estado novo. Não se recarrega a cada Salvar porque, num pack grande, a recarga trava
  o servidor por alguns segundos.

## Estado de cada receita

- **Padrão** (bolinha verde): sem override.
- **Editada** (coral): override ativo.
- **Desativada** (cinza): override com `neoforge:false`.
- **Diferente do carregado** (aviso âmbar na tela): o override existe mas a receita carregada não bate com
  ele (outro datapack ou o KubeJS mexeu depois, ou falta recarregar). Comparação: desativada e carregada,
  ou ativa e ausente, ou resultado (item e quantidade) diferente, ou número de ingredientes diferente, ou
  algum ingrediente do rascunho sem um ingrediente carregado que aceite o item dele (para tag, o primeiro
  item da tag).

## Tela (cliente)

Como no mockup, 324 × 250, tamanho fixo, nas cores do `GuiPaint`, com todo texto variável pelo `GuiText`:
- à esquerda, busca e a lista (ícone do resultado, nome, bolinha de estado); embaixo, "N salvas, falta
  recarregar" e **Recarregar agora**, ou "Tudo aplicado";
- à direita, nome, "Com forma"/"Sem forma" e o id, a pílula de estado ("Não salva" se o rascunho mudou),
  a grade 3×3 de slots fantasmas, a seta, o resultado com `−`/`+`, e o inspetor do slot escolhido
  (nome, **Item**/**Tag**, setas entre as tags do item, **Limpar**);
- botões **Salvar**, **Restaurar padrão** e **Desativar**/**Reativar**;
- o inventário do jogador embaixo. Clicar num item do inventário com um slot da grade escolhido põe uma
  cópia fantasma (o item não sai do inventário); clique com item no cursor sobre a grade também; clique
  direito num slot da grade limpa. Com o JEI, arrastar um item para a grade (handler de fantasma).
- As tags oferecidas são as do item no cliente (`ItemStack.getTags()`), em ordem alfabética.

## Rede (protocolo `12`)

- Abertura: o snapshot vai no buffer de abertura do menu.
- `RecipeEditorStatePayload` (servidor → cliente): o snapshot inteiro (lista e pendências), depois de
  cada ação e de cada recarga, só para quem está com a tela aberta.
- `RecipeEditorActionPayload` (cliente → servidor): ação (`SAVE`, `DISABLE`, `ENABLE`, `RESTORE`,
  `RELOAD`), id e rascunho. O servidor confere tudo: jogador com permissão 2 e com o menu do editor
  aberto, id editável, itens existentes no registro, tags com id válido, 1 ≤ count ≤ 64, grade não vazia.
- Nada é sincronizado com a tela fechada.

## Testes

- JUnit: rascunho (corte de bordas, letras, ingredientes iguais, sem forma, grade vazia, limites de count)
  e o JSON (preserva categoria e condições, desativar e reativar).
- GameTests: o pack está registrado e selecionado; salvar grava o arquivo e o JSON decodifica como receita
  válida pelo codec do jogo; desativar, reativar e restaurar; a ação de quem não é operador é recusada; a
  lista contém as receitas do mod e não as especiais. Os testes limpam os arquivos que criam
  (`try/finally`).
- e2e: abre a tela por `/wa recipes`, edita o Cartão de Filtro para dar 4, salva, recarrega, confere que a
  receita carregada dá 4, restaura, recarrega e salva as capturas.

## Documentação

Página nova no guia ("Editar receitas", nos dois idiomas, só como operar), `CLAUDE.md`, `progresso.md` e o
changelog da versão.
