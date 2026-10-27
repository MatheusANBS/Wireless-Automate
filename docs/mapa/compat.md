# Integrações opcionais

JEI, Mekanism, FTB Teams e Ars Nouveau. Parte do mapa do código (pacote base `src/main/java/io/github/matheusanbs/wirelessautomate/`). Índice em [CLAUDE.md](../../CLAUDE.md).

## `compat/jei/`

Plugin opcional do JEI; nada fora desse pacote referencia o JEI. Ressonador (etapa 4): o `WirelessAutomateJeiPlugin` registra o subtipo do Núcleo por tier (`CoreTierSubtype`); `ResonatorJei` (categorias, receitas, catalisadores e a informação por tier), `ResonatorRechargeCategory` (Recarga no Núcleo, o tempo pela config), `ResonatorRecastCategory` (Refazer o cristal: a receita especial `recast_crystal`, um exemplo por cristal), `ResonatorInfo` (a página de informação por tier: câmaras, FE por cristal, teto do Sintonizador, o cristal que rende cheio e a duração da carga, o Coração, a integridade, a interferência e, no Ultimate, o Eco, pela config), `UnloadedTiers` (esconde do JEI os itens dos tiers que não carregaram: Afinados, Pós Carregados, Corações e Cartões do Allthemodium sem o mod), `JeiText` (os números com o separador do idioma e o texto centrado das categorias) e `ResonatorStructureCategory` com a `StructureView` (a estrutura em 3D, pelo `ResonatorBill`: arrastar gira, roda dá zoom, botões de camada 1 a 4, no estado solto pelo `ResonatorFormation.looseState`, e Tudo, a caixa montada)

## `compat/mekanism/`, `network/Chemicals.java`, `network/ChemicalTransfer.java`

Químicos do Mekanism (só a API dele). O resto do mod passa pela ponte `Chemicals` (`Chemicals.LOADED`), sem tipos do Mekanism

## `compat/ftbteams/`, `network/TeamAccess.java`

Times do FTB Teams (mod opcional, id `ftbteams`; só times de grupo valem, não o time pessoal de cada jogador). O resto do mod passa pela ponte `TeamAccess` (`LOADED`, `isMember(time, jogador)`, `info(time)` com `gone` para o time desfeito, `teams()` para as sugestões); só `FtbTeams` toca na API dele (`FTBTeamsAPI.api().getManager()`, `getTeamByID`, `getTeams`, `Team.getMembers`, conferidos no ramo `1.21.1/main` do FTB Teams; o `main` dele usa nomes de outra versão) e só carrega com o mod presente. `FtbTeamsDev` cria um time para o e2e. A rede guarda o id do time, não os membros: quem entra ou sai do time ganha ou perde o acesso sozinho

## `compat/arsnouveau/`, `network/Sources.java`

Source do Ars Nouveau (só a API dele, em `ArsSources`: a capability `ars_nouveau:source` e o `ScalarAccess` da Source). O resto do mod passa pela ponte `Sources` (`Sources.LOADED`), sem tipos do Ars. `ArsStorage` dá ao Tanque de Source a capability `ISourceCap` (roteador e Relays) e o provider no `SourceManager` do Ars (Sourcelinks a 5 blocos depositam, máquinas do Ars tiram); a visão `int` é limitada a `Integer.MAX_VALUE`

