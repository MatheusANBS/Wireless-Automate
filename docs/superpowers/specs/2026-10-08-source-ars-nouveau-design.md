# Source do Ars Nouveau: aba no roteador e Tanque de Source Wireless (etapas 1 e 2)

Data: 8/10/2026. Proposta visual aprovada: [Source e telas escalonáveis](https://claude.ai/artifact/XxvxW9Gejgxi9qj4LZ5ocF) (aba Source, Tanque de Source). Ícones: [Ícones dos tipos](https://claude.ai/artifact/X5fHncvZXXnSpS3Fo4sDZM), direção Sólido (o `gui/type/source.png` já existe). Base: a etapa 0 ([spec](2026-10-08-registro-de-tipos-e-telas-design.md)), que pôs os tipos num registro (`ResourceType`, `LoadedTypes`, `ResourceStyle`) e deixou as telas escalonáveis.

## Objetivo

Com o Ars Nouveau instalado, o mod transporta **Source** como transporta energia, e ganha um quinto armazenamento, o **Tanque de Source Wireless**, que as máquinas do Ars usam como se fosse uma Source Jar. Sem o Ars, nada disso aparece e o mod carrega igual.

As duas etapas saem juntas na **1.1.0**, com a etapa 0. Cada etapa tem plano, testes e revisão próprios.

## Decisões do dono

| Tema | Decisão |
| --- | --- |
| Vazão de Source por face | Básico 1.000/s, Avançado 16.000/s, Elite 256.000/s, Ultimate sem limite (config `tiers.<tier>.sourcePerSecond`) |
| Capacidade do Tanque de Source | Básico 160.000, Avançado 2.560.000, Elite 40.960.000, Ultimate sem limite (config `storage.sourceTankCapacity`) |
| Filtro e cartões | Não: Source não tem tipos (como a energia) |
| Receita do Tanque | Tanque Wireless no centro, 4 Source Gems nas laterais, 4 lingotes de ferro nos cantos; só com o Ars |
| Escopo | Aba Source (roteador, Vinculador, Configurador, Tablet, já pelo registro), o Tanque, guia e JEI. As Spell Turrets do Ars puxam do Tanque sem código a mais (pelo `SourceManager`); o guia diz isso |
| Lançamento | Junto com a etapa 0, numa 1.1.0 |

Cores (decidido no mockup do tanque): Source `#B36DE0` (roxo do Ars) e Químicos `#97C853`.

## Fatos do Ars Nouveau (investigados no código, 5.2.0 a 5.13.3)

- Mod id `ars_nouveau`; exige GeckoLib e Curios. A API de Source é **idêntica de 5.2.0.750 (a primeira para 1.21.1) a 5.13.3.1423**: a dependência opcional é `[5.2,)`, e o mod funciona com a versão que o ATM10 trouxer.
- Capability de bloco `CapabilityRegistry.SOURCE_CAPABILITY` (`ars_nouveau:source`, `BlockCapability<ISourceCap, Direction>`). `ISourceCap`: `int getSource()`, `getSourceCapacity()`, `receiveSource(int, boolean simulate)`, `extractSource(int, boolean simulate)` (devolvem quanto passou), `canAcceptSource(int)`, `canProvideSource(int)`, `getMaxExtract()`, `getMaxReceive()`, `setSource`, `setMaxSource`. Registrada pelo Ars para a Source Jar, a jarra criativa, os cinco Sourcelinks, os Relays e a Imbuement Chamber.
- As máquinas do Ars **não** acham jarras pela capability. `SourceUtil.canGiveSource`/`canTakeSource`/`takeSourceMultiple` varrem um raio procurando `SourceJarTile` **e** consultam `SourceManager.INSTANCE` (`addInterface(Level, ISpecialSourceProvider)`, limpo a cada 60 ticks dos que ficaram inválidos). Usam esse caminho: Enchanting Apparatus, Imbuement Chamber, Potion Melder, Ritual Brazier, Wixie Cauldron, Drygmy, Whirlisprig, Spell Turrets, Relay de depósito e os Sourcelinks (que depositam num raio de 5).
- `ISpecialSourceProvider`: `ISourceTile getSource()`, `boolean isValid()`, `BlockPos getCurrentPos()`.
- `ISourceTile` tem uma armadilha: `addSource(int)` e `removeSource(int)` **sem** simulação devolvem o **total novo** (como `setSource`); as versões `(int, boolean simulate)` devolvem **quanto passou**. `takeSourceMultiple` calcula o que tirou como `antes − removeSource(n)`.

## Etapa 1: aba Source

### Integração opcional (molde do Mekanism)

- Build: o Ars entra como `compileOnly` (o jar do Maven da BlameJared, `com.hollingsworth.ars_nouveau:ars_nouveau-1.21.1`, sem transitivas), e o Ars + GeckoLib + Curios na pasta `run/mods` do `runClient` e numa run nova de GameTests, `gameTestServerSource` (como a `gameTestServerChemicals`). A run comum `runGameTestServer` continua sem o Ars e garante que o mod carrega sem ele.
- `neoforge.mods.toml`: dependência `ars_nouveau`, `type = "optional"`, `versionRange = "[5.2,)"`, `ordering = "NONE"`, `side = "BOTH"`.
- Tipos do Ars só em `compat/arsnouveau/` e em `network/SourceTransfer.java` (e no suporte dos GameTests de Source), **nunca** na assinatura de método de classe `@EventBusSubscriber` ou `@GameTestHolder`. O resto do mod passa por uma ponte `network/Sources.java` (como `Chemicals`): `LOADED`, `capability()`, `move(Port, long)`.

### Registro e motor

- `ResourceType.SOURCE` no fim do enum: chave `source`, sem filtro, sem cartões, vazão `sourcePerSecond` por segundo, mod `ars_nouveau`, padrões 1.000 / 16.000 / 256.000 / 0. Comentário da config: "Source por segundo, por face (Ars Nouveau; 0 = sem limite)."
- `NetworkManager.visit`: caso `SOURCE -> Sources.move(source, now) ? MOVED : 0`.
- `SourceTransfer`: uma visita de uma origem de Source, no molde do `EnergyTransfer` (um valor só, sem tipos; prioridade, round-robin, destinos dormindo, vazão do balde). As chamadas da `ISourceCap` são `int`: o lado de fora fica limitado a `Integer.MAX_VALUE` por chamada.
- `RouterBlockEntity`: cache de capability de Source por face (como o de químico, `BlockCapabilityCache<Object, …>` pela ponte); `RouterSnapshot.slots` com o caso de Source (capacidade e quantidade da máquina).
- Tudo o que a etapa 0 deixou genérico já acompanha: aba no roteador, chip no Vinculador, posição na Shift + roda do Vinculador e do Configurador, cartão nas Estatísticas do Tablet, papel e Mover no Tablet, `/wa face … source`.

### Cliente e textos

- `ResourceStyle`: cor `0xFFFF5CC8`, ícone `gui/type/source.png` (já gerado), nome `gui.wirelessautomate.router.type.source` ("Source"), vazão `gui.wirelessautomate.router.rate.source` ("%s Source/s"), acesso `gui.wirelessautomate.router.access.source` ("guarda Source" / "holds Source"). `tablet.type.source`. `TierCoreItem`: linha de Source no tooltip dos Cartões de Upgrade (só com o Ars).
- Guia: página nova `source` (en e pt): o que é, como ligar uma face (Sourcelink → jarra → roteador → roteador → jarra perto do Apparatus), sem filtro, vazão por tier; aparece só com o Ars (como a página de químicos com o Mekanism).

### Testes

- `SourceGameTests` no namespace `wirelessautomate_source`, na run `gameTestServerSource`: Source passa de uma Source Jar para outra pela rede; prioridade e round-robin entre duas jarras de destino; destino cheio dorme e acorda; vazão do tier respeitada. Se o jogador falso dos GameTests não funcionar com o Ars (como aconteceu com o Mekanism), máquinas de teste com a capability, no molde do `ChemicalTestTanks`.
- e2e: com o Ars no `runClient`, as telas mostram 5 tipos; o modo das abas do roteador no tamanho mínimo e as capturas em português continuam sem texto vazando.

## Etapa 2: Tanque de Source Wireless

### Bloco e conteúdo

- `StorageKind.SOURCE_TANK`: id `storage_source_tank`, config `sourceTankCapacity`, tipo `SOURCE`, unidade `source`, capacidades 160.000 / 2.560.000 / 40.960.000 / 0 (sem limite). Sem tipos (como a Bateria): sem lista, sem filtro de entrada. Só registrado e visível com o Ars (bloco, item, receita, aba criativa, JEI), no molde do Tanque Químico.
- Conteúdo em `long`, no molde da Bateria (`EnergyStore`): quebrar guarda o conteúdo e o tier no item, comparador, Cartões de Upgrade, `/wa storage list`/`recover`.
- Tela: a da Bateria, generalizada para "um valor só" (barra, quantidade/capacidade, variação por segundo), na cor da Source, com a linha "Sourcelinks e máquinas do Ars por perto usam este tanque como uma Source Jar".
- Textura e modelo pelo `gerar_texturas.py`, na família dos armazenamentos (painel e núcleo na cor da Source).

### Para o roteador e os Relays

- Capability `ars_nouveau:source` (`ISourceCap`) registrada no bloco, pela `compat/arsnouveau` (no molde do `MekanismStorage`), em `int` (mostra no máximo `Integer.MAX_VALUE`, capacidade idem).
- Capability "bulk" do mod, em `long` (como `BulkEnergy`), para o `SourceTransfer` passar tudo numa operação entre dois Tanques de Source.

### Para as máquinas do Ars (`SourceManager`)

- Ao carregar no servidor, o tanque se registra com `SourceManager.INSTANCE.addInterface(level, provider)`; `provider.isValid()` é falso depois de removido ou descarregado (o Ars limpa sozinho) e o tanque se registra de novo ao recarregar.
- `provider.getSource()` devolve um `ISourceTile` sobre o conteúdo do tanque, em `int` (até `Integer.MAX_VALUE`), seguindo a semântica do Ars: `addSource(n)`/`removeSource(n)` devolvem o total novo; as versões com `simulate` devolvem quanto passou; `canAcceptSource()` = há espaço; `getTransferRate()` = `Integer.MAX_VALUE` (o tanque não limita). Escritas marcam o block entity como sujo e acordam o roteador como qualquer mudança de conteúdo.
- Com isso, sem código a mais: Sourcelinks num raio de 5 depositam no tanque, e Enchanting Apparatus, Imbuement Chamber, Potion Melder, Ritual Brazier, Wixie Cauldron, Drygmy, Whirlisprig, Spell Turrets e Relays de depósito tiram dele.

### Receita

```json
{
  "neoforge:conditions": [{"type": "neoforge:mod_loaded", "modid": "ars_nouveau"}],
  "type": "minecraft:crafting_shaped",
  "category": "redstone",
  "pattern": ["IGI", "GTG", "IGI"],
  "key": {
    "I": {"item": "minecraft:iron_ingot"},
    "G": {"item": "ars_nouveau:source_gem"},
    "T": {"item": "wirelessautomate:storage_tank"}
  },
  "result": {"id": "wirelessautomate:storage_source_tank", "count": 1}
}
```

### Testes

- GameTests na run `gameTestServerSource`: quebrar e recolocar guarda conteúdo e tier; upgrade de tier; roteador enche o tanque a partir de uma Source Jar; dois tanques passam bilhões numa operação; `SourceUtil.takeSourceMultiple` perto do tanque tira dele (e devolve a quantidade certa); `SourceUtil.canGiveSource` perto do tanque o encontra; o provider fica inválido depois de quebrar.
- e2e: o tanque na vitrine/roteiro, a tela com a barra, captura nos dois idiomas.

## Documentação e lançamento

- `CLAUDE.md` (mapa: `compat/arsnouveau`, `Sources`, `SourceTransfer`, o tanque; a regra "tipos do Ars só em…" junto da do Mekanism; a run `gameTestServerSource`), `docs/especificacao.md`, `docs/progresso.md`, README (Ars Nouveau como integração opcional), descrição do CurseForge e o guia (página `source` e página do Tanque).
- 1.1.0: `mod_version=1.1.0`, changelog unificado (1.1.0 + anteriores), jar, release no GitHub com tag `v1.1.0`, Ars Nouveau como dependência opcional no CurseForge. O protocolo continua `8` se nenhum payload mudar de formato na etapa 1 ou 2; se mudar, sobe uma vez.

## Fora do escopo

Filtro de Source, cartões de Source, mana do jogador, itens do Ars no roteador, AE2/RS2.
