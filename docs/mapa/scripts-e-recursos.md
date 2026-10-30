# Scripts e recursos

Geradores de texturas, guia e CurseForge, e as pastas de recursos. Parte do mapa do código (pacote base `src/main/java/io/github/matheusanbs/wirelessautomate/`). Índice em [CLAUDE.md](../../CLAUDE.md).

## `scripts/textures/gerar_texturas.py`

Gera todas as texturas (PIL) e a folha `docs/preview/folha-de-sprites.png`; o modelo 3D do Reparador sai à parte, do `docs/preview/reparador-de-cristais.bbmodel` pelo `reparador_modelo.py` (chamado pelo `gerar_texturas.py`); edite as paletas ali, não os PNGs; gera também os modelos de bloco e de item e os blockstates do roteador (com os modelos `router_<tier>_spin1..3` por giro, pela transformação raiz do NeoForge), dos armazenamentos e dos cartões por tier (o `router_basic.json` é o molde escrito à mão) e os do Tanque de Source; gera também os cristais do Ressonador (com o Opaco e os overrides do predicado `dull`) e, pelo `blocos.py`, as texturas, modelos e blockstates do minério de Quartzo de Sinal e os seis blocos do Ressonador e, da etapa 3, o sprite `block/resonator_halo` da câmara e os seis blocos (Carcaça, Vidro, Núcleo, Portas de Energia e de Cristais e Sintonizador, com as texturas conectadas)

## `scripts/guide/`

Gera as páginas do livro-guia (GuideME) em inglês e português; edite os `.py`, não os `.md` gerados.

- `gerar_guia.py`: só o `main` e a lista `PAGINAS` (a ordem de escrita; a ordem no livro vem do `position` do `front`). Coloca a própria pasta no caminho de import, porque o `python -I` não coloca.
- `guia_base.py`: o que mais de uma página usa: `front`, `fill`, `item`, `page`, `write`, os tiers (`TIERS`, `RATES`, `RANGE`, `CAPACITY`, `capacity_table`), a cena `SCENE_STORAGE_PAIR` e o texto `COMMON_PT`/`COMMON_EN` dos armazenamentos.
- `paginas/<pagina>.py`: uma página por arquivo, com `PAGINA = page('<pagina>.md', pt, en)` (os dois idiomas lado a lado) e as cenas e tabelas que só ela usa. Página nova: um arquivo aqui e o nome dele em `PAGINAS`.
- O texto segue o tom simples: "Como usar" em passos primeiro, frases curtas, gestos em tabela "Gesto | O que faz", sem jargão de config; detalhe fino de interface fica de fora.

## `scripts/curseforge/`

Capa (`gerar_capa.py`, 400x400) e banner e imagens da descrição (`gerar_imagens.py`) do CurseForge, em `docs/curseforge/`. As fotos vêm da vitrine: `WA_SHOWCASE=run/showcase ./gradlew runClient` (modo do `DevEndToEnd`). `gerar_gif.py <pasta dos quadros>` monta o `docs/curseforge/ressonador.gif` da vitrine do Ressonador (paleta única, 256 cores e cai para 192 e 128 até caber nos 2 MB do CurseForge) e, com `--prefixo tela --recorte --largura --laco 1500`, o `docs/curseforge/tela.gif` da parede de Telas; com `--prefixo bau --recorte 120,140,1160,665 --largura 1024 --laco 12160`, o `docs/curseforge/compactacao.gif` dos upgrades do Baú. `arte_vitrine.py` (a pixel art de 128 × 64 em 12 quadros e o ícone do painel, em `WA-ARTE1`) e `gif_vitrine.py` (o GIF de cor livre de 256 × 256 em 24 quadros) gravam em `run/showcase/entrada`, que a ala da Tela da vitrine lê; rode os dois antes da vitrine. `WA_SHOWCASE_ONLY=ressonador` grava só o GIF e as fotos do Ressonador, `=cascata` só a foto da cascata, `=reparador` só as do Reparador `=tela` só a parede das Telas (fotos e GIF) e `=bau` só os upgrades do Baú (`E2eShowcaseChest`: as fotos da Mesa e da Compactação e o GIF das pepitas).

## Recursos em `src/main/resources/`

- `assets/wirelessautomate/`: blockstates, modelos, texturas e `lang/` (en_us e pt_br; mantenha os dois em dia).
- `assets/wirelessautomate/guideme_guides/guide.json` e `guides/wirelessautomate/guide/`: o livro-guia do GuideME (opcional, sem código), páginas em inglês na pasta e em português em `_pt_br/`, geradas por `scripts/guide/gerar_guia.py`. O guia é para o jogador: só como operar (nada de config, ms/tick ou detalhes internos). Mantenha as especificações (tabela de tiers, gestos) em dia com o código. O e2e abre cada página nos dois idiomas, rola até o fim (pela barra do GuideME) e salva `guia-<página>[-pt][-N].png` quando o GuideME está presente.
- `data/`: loot table, receita, tags e `wirelessautomate/structure/empty.nbt` (estrutura 3×3×3 vazia dos GameTests).
- `data/occultism/recipe/miner/`: o Cristal de Sinal nos mineiros do Occultism, só dados (sem código nem dependência): `ores/signal_crystal.json` (Djinni, peso 300) e `eldritch/signal_crystal.json` (Ancient Eldritch, peso 90, o padrão dessa categoria), 2 cristais por resultado (a média do minério). Leem a tag de item `c:gems/signal_quartz` (só o Cristal de Sinal cheio) e só carregam com a tag do mineiro e a da gema não vazias; conferidos pelo JUnit `OccultismMinerRecipesTest`. A Fortuna dos mineiros do Occultism soma rolagens extras (`DimensionalMineshaftBlockEntity`), então vale para qualquer resultado. Outro mod minerador tem formato próprio: veja o código dele antes.
- `src/main/templates/META-INF/neoforge.mods.toml`: preenchido pelo Gradle a partir do `gradle.properties`.

## Recursos dos upgrades do Baú

Os sprites dos itens `crafting_upgrade` e `compacting_upgrade` são animados e interpolados, gerados pelo `gerar_texturas.py`. A limpeza dele (`limpar`) não mexe em `textures/gui/tv/`, que é do `tela_icones.py` (`DE_OUTROS`). As receitas são sem forma (`crafting_shapeless`): Mesa = mesa de trabalho + Cristal de Sinal; Compactação = pistão + Cristal de Sinal + bloco de ferro. O guia explica os upgrades na página `wireless-chest.md` (pt e en, pelo `gerar_guia.py`). Só no `runClient` de dev entram o AllTheCompressed, o Occultism (com Modonomicon e SmartBrainLib) e o Just Dire Things, para testar as cadeias grandes.
