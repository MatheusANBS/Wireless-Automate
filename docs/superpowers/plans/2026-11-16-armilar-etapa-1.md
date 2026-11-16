# Armilar, etapa 1: estrutura e blocos

> **Para os agentes:** use `superpowers:subagent-driven-development`, uma tarefa por subagente, com revisão entre elas (`.claude/skills/wa-feature/references/conducao-de-agentes.md`). Os passos usam caixas (`- [ ]`).

**Objetivo:** o Ressonador vira a Armilar de 35 blocos, que monta, desmonta e é projetada pela regra nova e, formada, mostra a escultura do conceito A. Os mundos antigos convertem sem perder nada.

**Arquitetura:**
- A regra é lógica pura (`ResonatorLayout`), num referencial local com a frente para o sul, girado pela frente do Núcleo.
- A formação grava em cada bloco a posição e a frente, e o blockstate escolhe e gira o modelo.
- Os modelos saem do gerador Python. Os soltos vêm do protótipo aprovado; os formados são a escultura do conceito fatiada na grade.

**Tecnologias:** NeoForge 21.1.248 (Minecraft 1.21.1, Java 21), JUnit 5, GameTests do NeoForge, Python 3 com PIL no gerador.

**Spec:** `docs/superpowers/specs/2026-11-16-ressonador-armilar-design.md`. Leia antes de qualquer tarefa.

## Restrições globais

- **Idioma:** código, comentários, commits e docs em português. Todo texto de jogo entra no `en_us.json` e no `pt_br.json`, sempre nos dois.
- **Commits:** sem linhas de atribuição (nada de `Co-Authored-By` nem `Claude-Session`).
- **Chaves:** as de NBT, componentes e config não mudam. A única exceção é a chave `layout` do `CoreShell`, que deixa de ser gravada e lida (Tarefa 4).
- **`ModPayloads.VERSION`:** passa de `"52"` para `"53"` na Tarefa 4, porque o `ResonatorStatePayload` perde o campo de layout. Fora disso, nada.
- **Ids:**
  - Novos: `resonator_column` e `resonator_cradle`.
  - Mantidos: `resonator_casing` (agora a Laje), `resonance_core`, `resonator_energy_port`, `resonator_crystal_port` e `resonator_tuner`.
  - O `resonator_glass` sai, com alias para o `resonator_casing` nos registros de blocos e de itens.
- **Nomes:**

  | Id | pt_br | en_us |
  | --- | --- | --- |
  | `resonator_casing` | Laje do Ressonador | Resonator Floor |
  | `resonator_column` | Coluna do Ressonador | Resonator Column |
  | `resonator_cradle` | Berço do Sol | Sun Cradle |

  Os outros continuam como estão.
- **Referencial local:** a frente fica para o sul. x vai de 0 (oeste) a 4 (leste), y de 0 a 4 e z de 0 (trás) a 4 (frente). O Núcleo fica em (2, 0, 4). A tabela de casas está na spec, seção "Regra da montagem".
- **Giro pela frente** (deslocamento local (dx, dz) a partir do centro (2, 2), levado ao mundo):

  | Frente | Deslocamento no mundo |
  | --- | --- |
  | sul | (dx, dz) |
  | oeste | (−dz, dx) |
  | norte | (−dx, −dz) |
  | leste | (dz, −dx) |

  É o mesmo giro do `y` do blockstate: sul 0, oeste 90, norte 180, leste 270, para modelos feitos com a frente para o sul.
- **Blocos soltos:** Núcleo e peças são feitos com a frente para o norte, e o giro segue o `ROTACOES_PECA` do `blocos.py`: norte 0, leste 90, sul 180, oeste 270.
- **Performance** (`.claude/skills/wa-performance/SKILL.md`, leis 1, 2, 3, 6 e 9):
  - nada por tick;
  - a regra só roda por evento de bloco;
  - a formação faz um `setBlock` por membro, com `UPDATE_CLIENTS | UPDATE_KNOWN_SHAPE`;
  - nada de alocação nova em caminho quente.
  Antes de cada commit, rode `python -I .claude/skills/wa-performance/scripts/varrer_perf.py` e resolva ou justifique cada candidato no relatório.
- **Comentários:** antes de cada commit, rode `python -I .claude/skills/wa-comentarios/scripts/varrer.py`.
- **Tamanho:** antes de cada commit, rode `python -I .claude/skills/wa-modular/scripts/medir.py <arquivos mudados>` e divida o que passar do limite.
- **Texturas e modelos:** só pelo gerador, nunca à mão no PNG ou no JSON. Rode `python -I scripts/textures/gerar_texturas.py` na raiz. Siga a skill `wa-texturas` (`.claude/skills/wa-texturas/SKILL.md`).
- **Verificação:**
  - As Tarefas 1 a 4 terminam com `./gradlew build` verde (compila e roda o JUnit). A estrutura muda de forma no meio do caminho, e os GameTests do Ressonador só voltam a valer na Tarefa 5.
  - As Tarefas 5 e 6 terminam com `./gradlew build runGameTestServer runGameTestServerChemicals runGameTestServerSource runGameTestServerAllthemodium` verde e o e2e pedido na tarefa (`./scripts/e2e.sh --only <suíte>`, com `run/e2e/result.txt` dizendo `OK`).
  - Compile antes de rodar jogo (`./gradlew compileJava`). Com `BUILD FAILED`, conserte em vez de esperar.
  - Nunca rode dois jogos ao mesmo tempo. Não mexa em `run/options.txt`.
  - Todo GameTest novo leva `timeoutTicks` de no máximo 300.
- **Protótipos de referência** (fora do repositório, só leitura), em `C:\Users\matheuss\Desktop\Sistemas\Wireless-Automate\ressonador-conceitos\`:
  - `armilar_blocos.py`: as texturas e os modelos soltos aprovados;
  - `gerar_conceitos.py`: a escultura do conceito, a pintura dos materiais e as conferências;
  - `armilar_formado.py`: a escultura fatiada na grade;
  - `prancha-blocos-armilar.png` e `blocos_armilar/comparacao.png`: como tem de ficar.

## Mapa de arquivos

| Arquivo | Papel |
| --- | --- |
| `resonator/ResonatorColumnBlock.java` (novo) | A Coluna: `part` (`StackPart`) e `corner` (`Corner`), formas por estado |
| `resonator/ResonatorCradleBlock.java` (novo) | O Berço do Sol: `part` (`StackPart`), formas por estado |
| `resonator/StackPart.java`, `resonator/Corner.java` (novos) | Os enums dos estados da Coluna e do Berço |
| `resonator/BasePosition.java` (novo) | As posições da Laje: `loose` e as 22 casas `pXZ` |
| `resonator/ResonatorCasingBlock.java` (mudar) | `position` (`BasePosition`) e `facing` horizontal; oclusão vazia quando formada |
| `resonator/ResonatorPartBlock.java`, `resonator/ResonanceCoreBlock.java` (mudar) | `facing` horizontal; frente ao colocar |
| `resonator/ResonatorGlassBlock.java`, `resonator/GlassFace.java`, `resonator/CasingPosition.java` (apagar) | A casca antiga |
| `resonator/ResonatorLayout.java` (reescrever) | A regra da Armilar (lógica pura) |
| `resonator/ResonatorBlueprint.java` (reescrever) | O desenho esperado numa caixa do mundo, para a projeção e a conta |
| `resonator/ResonatorBill.java`, `resonator/ProjectionDiff.java`, `resonator/PlacementFacing.java` (mudar) | A conta, a comparação da projeção e a frente ao colocar |
| `resonator/ResonatorFormation.java`, `resonator/CoreShell.java`, `resonator/ResonanceCoreBlockEntity.java` (mudar) | Estados montado e solto, a casca sem layout |
| `packet/ResonatorStatePayload.java`, `menu/ResonatorMenu.java`, `client/resonator/screen/*`, `registry/ModPayloads.java` (mudar) | Sem layout; `VERSION` 53 |
| `client/resonator/ResonatorProjection.java`, `ProjectionRenderer.java`, `ProjectionText.java` (mudar) | A projeção de um desenho só |
| `client/resonator/render/ChamberScene.java` (mudar) | O centro da câmara antiga no lugar do sol, até a etapa 2 |
| `compat/jei/StructureView.java`, `ResonatorStructureCategory.java`, `ResonatorJei.java` (mudar) | Uma montagem só, com Coluna e Berço |
| `registry/ModBlocks.java`, `ModItems.java`, `ModCreativeTabs.java` (mudar) | Os blocos novos, o Vidro fora, o alias |
| `data/wirelessautomate/recipe/*`, `loot_table/blocks/*`, `data/minecraft/tags/block/mineable/pickaxe.json`, `lang/*.json` (mudar) | Receitas, drops, ferramenta, nomes e textos |
| `scripts/textures/blocos.py` (mudar) | Os sprites e modelos soltos dos sete blocos; os blockstates novos |
| `scripts/textures/armilar_modelo.py` (novo) | A escultura do conceito fatiada: materiais, modelos por casa e conferência |
| `gametest/ResonatorTestSupport.java` e os `Resonator*GameTests`, `bench/BenchResonators.java`, `client/dev/E2e*Resonator*.java` (mudar) | Montam a Armilar |
| `scripts/guide/resonator_scene.txt`, `scripts/guide/paginas/signal_resonator.py` (mudar) | A cena e a página do guia |
| `docs/mapa/ressonador.md`, `docs/progresso/*`, spec antiga (mudar) | Documentação |

---

### Tarefa 1: Coluna e Berço (blocos, registro e dados)

Só acrescenta. A estrutura antiga continua montando, e os blocos novos ainda não entram nela.

**Arquivos:**
- Criar: `src/main/java/io/github/matheusanbs/wirelessautomate/resonator/StackPart.java`, `Corner.java`, `ResonatorColumnBlock.java`, `ResonatorCradleBlock.java`
- Mudar: `registry/ModBlocks.java`, `registry/ModItems.java`, `registry/ModCreativeTabs.java`
- Criar: `src/main/resources/data/wirelessautomate/loot_table/blocks/resonator_column.json`, `resonator_cradle.json`; `recipe/resonator_column.json`, `recipe/resonator_cradle.json`
- Mudar: `src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json`, `assets/wirelessautomate/lang/en_us.json`, `pt_br.json`
- Teste: `src/test/java/io/github/matheusanbs/wirelessautomate/resonator/StackPartTest.java` (novo)

**Interfaces:**
- **Produz:**
  - `StackPart { LOOSE("loose"), LOWER("lower"), UPPER("upper") }`, que implementa `StringRepresentable`. Tem `static StackPart byName(String)`, que devolve `LOOSE` para nome desconhecido.
  - `Corner { SE("se"), SW("sw"), NW("nw"), NE("ne") }`, que implementa `StringRepresentable`. Tem `static Corner byName(String)`, que devolve `SE` para nome desconhecido.
  - `ResonatorColumnBlock.PART` (`EnumProperty<StackPart>`, "part") e `ResonatorColumnBlock.CORNER` (`EnumProperty<Corner>`, "corner").
  - `ResonatorCradleBlock.PART` (`EnumProperty<StackPart>`, "part").
  - `ModBlocks.RESONATOR_COLUMN`, `ModBlocks.RESONATOR_CRADLE`, `ModItems.RESONATOR_COLUMN` e `ModItems.RESONATOR_CRADLE`.

**Custo:** nenhum por tick. Os dois blocos só chamam o `ResonatorManager.blockChanged` no `onPlace` e no `onRemove`, como a Carcaça.

- [ ] **Passo 1: o teste dos enums.** Escreva `StackPartTest`:

```java
package io.github.matheusanbs.wirelessautomate.resonator;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class StackPartTest {
    @Test
    void nomesDoEstado() {
        assertEquals("lower", StackPart.LOWER.getSerializedName());
        assertEquals(StackPart.UPPER, StackPart.byName("upper"));
        assertEquals(StackPart.LOOSE, StackPart.byName("nnn"));
        assertEquals("nw", Corner.NW.getSerializedName());
        assertEquals(Corner.NE, Corner.byName("ne"));
        assertEquals(Corner.SE, Corner.byName("?"));
    }
}
```

- [ ] **Passo 2:** rode `./gradlew test --tests '*StackPartTest'` e veja falhar porque as classes não existem.
- [ ] **Passo 3: os enums.**
  - Use o mesmo molde do `CasingPosition` atual: `getSerializedName` em minúsculas e `byName` por laço sobre uma constante `VALUES`, sem `values()` a cada chamada.
- [ ] **Passo 4: os blocos.** Siga o `ResonatorCasingBlock` (codec `simpleCodec`, `onPlace` e `onRemove` chamando `ResonatorManager.blockChanged`).
  - **Estado padrão:** a Coluna nasce com `PART=LOOSE` e `CORNER=SE`; o Berço, com `PART=LOOSE`.
  - **Propriedades:** `noOcclusion()`, porque os modelos não são cubos inteiros. O resto é igual à Carcaça (força, som e ferramenta), com `requiresCorrectToolForDrops` se a Carcaça tiver.
  - **Forma da Coluna** (`getShape`, em pixels de 0 a 16; a de colisão é a mesma):

    | Estado | Caixas |
    | --- | --- |
    | `LOOSE` | `box(1,0,1,15,3,15)`, `box(4,3,4,12,13,12)` e `box(2,13,2,14,16,14)` |
    | `LOWER` e `UPPER`, canto `SE` | `box(12,0,12,16,16,16)` (a perna fica no canto de fora da casa) |

    Os outros cantos são essa caixa girada pelo canto: `SW` x 0..4, `NW` x 0..4 z 0..4, `NE` z 0..4. Guarde as formas em constantes estáticas, uma por estado.
  - **Forma do Berço:**

    | Estado | Caixas |
    | --- | --- |
    | `LOOSE` | `box(1,0,1,15,3,15)`, `box(5,3,5,11,10,11)` e `box(2,10,2,14,14,14)` |
    | `LOWER` | `box(2,0,2,14,2,14)` e `box(5,2,5,11,16,11)` |
    | `UPPER` | `box(5,0,5,11,2,11)` e `box(3,2,3,13,6,13)` |
- [ ] **Passo 5: o registro.**
  - Registre `resonator_column` e `resonator_cradle` em `ModBlocks`, logo depois de `RESONATOR_CASING`, com itens simples em `ModItems`.
  - Ponha os dois na aba criativa, logo depois da Carcaça.
- [ ] **Passo 6: os dados.**
  - **Loot tables:** cada bloco derruba a si mesmo. Copie o `loot_table/blocks/resonator_casing.json` e troque o id.
  - **Tag:** acrescente os dois ids em `pickaxe.json`.
  - **Receita da Coluna** (`crafting_shaped`, categoria `building`):

```json
{
  "type": "minecraft:crafting_shaped",
  "category": "building",
  "pattern": ["C C", "CKC", " L "],
  "key": {
    "C": {"item": "minecraft:copper_ingot"},
    "K": {"item": "wirelessautomate:signal_crystal"},
    "L": {"item": "wirelessautomate:resonator_casing"}
  },
  "result": {"id": "wirelessautomate:resonator_column", "count": 2}
}
```

  - **Receita do Berço:**

```json
{
  "type": "minecraft:crafting_shaped",
  "category": "building",
  "pattern": ["GKG", " G ", " L "],
  "key": {
    "G": {"item": "minecraft:gold_ingot"},
    "K": {"item": "wirelessautomate:cut_signal_crystal"},
    "L": {"item": "wirelessautomate:resonator_casing"}
  },
  "result": {"id": "wirelessautomate:resonator_cradle", "count": 1}
}
```

    Confirme no código os ids do Cristal de Sinal e do Lapidado (`ModItems`) antes de gravar.
  - **Lang:** nos dois idiomas, `block.wirelessautomate.resonator_column` ("Coluna do Ressonador" / "Resonator Column") e `block.wirelessautomate.resonator_cradle` ("Berço do Sol" / "Sun Cradle").
- [ ] **Passo 7:** rode `./gradlew build`. Tem de passar.
- [ ] **Passo 8:** commit `Armilar: os blocos Coluna e Berço do Sol`.

---

### Tarefa 2: Texturas e modelos soltos dos sete blocos

Leva para o gerador do mod as texturas e os modelos soltos da prancha aprovada (`armilar_blocos.py`).

**Arquivos:**
- Mudar: `scripts/textures/blocos.py`, `scripts/textures/gerar_texturas.py` (só se a lista de sprites ou modelos for montada lá)
- Gerados: `assets/wirelessautomate/textures/block/*`, `models/block/*`, `models/item/*` e `blockstates/resonator_column.json`, `resonator_cradle.json`

**Interfaces:**
- **Consome:** os nomes de estado da Tarefa 1.
- **Produz:**
  - **Sprites** (`block/<nome>`):
    - `resonator_familia_lado`, `resonator_fundo` e `resonator_fundo_latao`;
    - `resonator_latao_liso`, `resonator_grafite_liso` e `resonator_coral_liso`;
    - `resonator_laje_topo`, `resonator_coluna_lado`, `resonator_coluna_topo`;
    - `resonator_berco_lado`, `resonator_berco_topo`, `resonator_berco_dentro` e `resonator_brasa_lado`;
    - `resonance_core_<tier>_solto` (frente) e `resonance_core_<tier>_topo`, para os 8 tiers;
    - `resonator_energy_port_solto` e `resonator_energy_port_topo`;
    - `resonator_crystal_port_solto` e `resonator_crystal_port_topo`;
    - `resonator_tuner_solto` e `resonator_tuner_topo`.
  - **Modelos soltos:**
    - `resonator_casing_loose`, `resonator_column_loose` e `resonator_cradle_loose`;
    - `resonance_core_<tier>_loose`, `resonator_energy_port_loose`, `resonator_crystal_port_loose` e `resonator_tuner_loose`;
    - os itens apontando para eles.

**Custo:** nenhum no jogo. São só assets.

- [ ] **Passo 1: as funções de textura.**
  - Porte as funções de `armilar_blocos.py` para o `blocos.py`, no bloco do Ressonador, trocando as `res_*` antigas dos soltos: `familia_lado`, `fundo`, `liso`, `laje_topo`, `coluna_*`, `berco_*`, `brasa_lado`, `nucleo_frente`, `nucleo_topo`, `energia_*`, `cristais_*` e `sintonizador_*`.
  - Mantenha os pincéis e a `PALETA` do mod, que o protótipo já usa.
  - Remova o que só servia ao protótipo (o atlas, a fileira, os vanilla).
  - Mantenha as texturas formadas antigas (`resonator_casing_<bordas>`, `resonator_glass_*`, `*_dentro`, `resonator_forro`) até a Tarefa 4, porque a estrutura antiga ainda as usa.
- [ ] **Passo 2: os modelos soltos.**
  - Porte o `modelo()` do protótipo para `Modelo` do `blocos.py`, com uma `caixa(...)` por elemento.
  - O Núcleo e as peças ficam com a frente para o norte, a face que a vista `gui` mostra à direita.
  - O `render_type` é `cutout`, e `cubo=True` só na Laje, que é cubo inteiro.
  - Os modelos têm de passar no `validar_geometria`.
- [ ] **Passo 3: os blockstates.**
  - Coluna e Berço: todo estado aponta para o modelo solto por enquanto. A Tarefa 4 troca para os formados.
  - Modelos de item: a Coluna e o Berço usam o solto. O Núcleo continua com as sobreposições por tier.
  - A Laje solta (`position=loose`) passa a usar `resonator_casing_loose` novo.
- [ ] **Passo 4:** rode `python -I scripts/textures/gerar_texturas.py` e depois a conferência dos modelos soltos:

```bash
python -I .claude/skills/wa-texturas/scripts/verificar_modelo.py src/main/resources/assets/wirelessautomate/models/block/resonator_column_loose.json src/main/resources/assets/wirelessautomate/models/block/resonator_cradle_loose.json src/main/resources/assets/wirelessautomate/models/block/resonance_core_basic_loose.json src/main/resources/assets/wirelessautomate/models/block/resonator_energy_port_loose.json src/main/resources/assets/wirelessautomate/models/block/resonator_crystal_port_loose.json src/main/resources/assets/wirelessautomate/models/block/resonator_tuner_loose.json
```

  Tem de sair com código 0, com 0 sobreposições e 0 faces no mesmo plano.
- [ ] **Passo 5: compare com a prancha.**
  - Abra a folha `docs/preview/folha-de-sprites.png` gerada e compare com `prancha-blocos-armilar.png`. As texturas têm de ser as mesmas, pixel a pixel, para os mesmos tons.
  - Ponha no relatório o caminho da folha.
- [ ] **Passo 6:** rode `./gradlew build`. Tem de passar.
- [ ] **Passo 7:** commit `Armilar: texturas e modelos soltos`.

---

### Tarefa 3: A escultura formada (gerador)

Cria `scripts/textures/armilar_modelo.py`, que gera os modelos formados de cada casa a partir da escultura do conceito. Os blockstates continuam apontando para os modelos antigos até a Tarefa 4.

**Arquivos:**
- Criar: `scripts/textures/armilar_modelo.py`
- Mudar: `scripts/textures/gerar_texturas.py` (chamar o módulo novo, como chama o `reparador_modelo.py`)
- Gerados: `textures/block/armilar_*.png` e `models/block/armilar/*.json`

**Interfaces:**
- **Produz** (todos feitos com a frente para o sul; o giro fica no blockstate):
  - `models/block/armilar/casing_pXZ.json` para cada uma das 22 posições da Laje;
  - `armilar/column_lower.json` e `armilar/column_upper.json`, para o canto `se`;
  - `armilar/cradle_lower.json` e `armilar/cradle_upper.json`;
  - `armilar/core_<tier>.json`, os 8 tiers;
  - `armilar/energy_port.json`, `armilar/crystal_port.json` e `armilar/tuner.json`.
  - A função `blockstates_armilar()`, que **não** é chamada nesta tarefa (a Tarefa 4 chama). Ela devolve o dicionário dos blockstates novos da Laje, da Coluna, do Berço, do Núcleo e das peças, pelas regras de giro das Restrições globais.

**Custo:** nenhum por quadro. As fatias são modelos de bloco e entram na malha do chunk.

- [ ] **Passo 1: a geometria.** Porte para o módulo novo o que a escultura precisa:
  - de `gerar_conceitos.py`: `Cubo`, `rotm`, `linhas_chanfro`, `laje`, a função `armilar()` sem o horizonte nem os marcos, `cortar_faces`, `faces_coplanares`, `dividir`, `sobreposicoes` e `sat`;
  - de `armilar_formado.py`: `subtrair`, `armilar_formada`, `fatiar` e `celula`.

  Ajuste a geometria em relação ao protótipo:
  - **Mostrador do Núcleo:** fica com 16 px de largura (x de −8 a 8, em vez de −10 a 10), para caber na casa do Núcleo, num sprite só.
  - **Coordenadas:** as do conceito, com o centro da estrutura em (0, 0, 0) e o chão em y 0.
  - **Casas:** a casa (cx, cy, cz) vai de x = 16·cx − 8 a 16·cx + 8, de y = 16·cy a 16·cy + 16 e de z = 16·cz − 8 a 16·cz + 8. A casa local (lx, lz) do referencial de frente sul é a (lx − 2, lz − 2).
- [ ] **Passo 2: os materiais.**
  - Sprites de 16 × 16 pintados como o `pintar_atlas` do `gerar_conceitos.py` pinta cada material: `armilar_porcelana`, `armilar_grafite`, `armilar_coral`, `armilar_latao`, `armilar_vidro` e `armilar_grelha`. Porte uma casa de 16 px de cada material, com o mesmo desenho.
  - **Especiais:**
    - `armilar_mostrador_<tier>`: a escala do mostrador do conceito, com o vidro na cor da `LENTE` do tier e as marcas de `MARCAS`;
    - `armilar_sintonia`, `armilar_escotilha` e `armilar_energia`, cada um como o especial do mesmo nome no `pintar_atlas`.
  - **UV de cada face:** a projeção da caixa nas coordenadas locais da casa (`uv_padrao`), que já cai alinhada à grade do mundo.
  - **Pernas giradas (45°):** UV pelo tamanho da própria face.
  - **Faces especiais:** o sprite especial inteiro, nas faces marcadas como especial no conceito (`south` do mostrador, `east` da caixa da energia, `west` da escotilha, `north` da placa do Sintonizador).
- [ ] **Passo 3: os modelos por casa.**
  - **Casas e frente:** monte a escultura com a frente para o sul e corte na grade (`fatiar`, depois a remoção de faces cobertas). Para cada casa:
    - **Laje:** a casa local (lx, 0, lz), com o nome `casing_p<lx><lz>`.
    - **Peças:** cada peça na sua casa (Núcleo (2, 4), Energia (4, 2), Cristais (0, 2), Sintonizador (2, 0)), girada para ficar de frente para o sul:
      - a Energia (frente leste) gira 90° no sentido do `y` do blockstate;
      - os Cristais (frente oeste) giram 270°;
      - o Sintonizador (frente norte) gira 180°;
      - o Núcleo já está de frente para o sul.
    - **Perna `se`:** a perna da casa diagonal (1, 1) do mundo (+x, +z), cortada em `lower` (y 16 a 32) e `upper` (y 32 a 48).
    - **Berço:** a casa central (0, 0), cortada em `lower` e `upper`.
  - **`Modelo`:** cada casa vira um `Modelo` do `blocos.py`, com as caixas em coordenadas locais de 0 a 16 e a rotação de 45° (`("y", 45.0, origem)`) nas peças giradas.
  - **Faces de dentro:** a face que encosta no bloco vizinho da estrutura **sai** do modelo, porque o `cullface` não a tiraria (regra "Cullface entre fatias" da `wa-texturas`). O `cullface` fica só nas faces do limite que dão para fora.
- [ ] **Passo 4: a conferência dentro do módulo** (o gerador falha com `ModeloInvalido` se alguma der errado):
  - 0 sobreposições de volume e 0 faces no mesmo plano no conjunto inteiro (as funções portadas);
  - toda peça dentro da própria casa;
  - no máximo 40 elementos por modelo.
- [ ] **Passo 5: a conferência dos modelos gerados como conjunto**, com a frente para o sul (os giros são os do blockstate). Gere a lista de argumentos dentro do próprio módulo e imprima-a numa função `argumentos_junto()`:
  - a Laje `pXZ` em `@lx-2,0,lz-2`;
  - a Coluna em `@1,1,1` e `@1,2,1` (canto `se`), e as outras três pernas pelo giro do canto;
  - o Núcleo em `@0,0,2`;
  - a Energia em `@2,0,0,270` e os Cristais em `@-2,0,0,90`;
  - o Sintonizador em `@0,0,-2,180`;
  - o Berço em `@0,1,0` e `@0,2,0`.

```bash
python -I .claude/skills/wa-texturas/scripts/verificar_modelo.py --junto $(python -I scripts/textures/armilar_modelo.py --argumentos)
```

  O conjunto tem de dar 0 sobreposições e 0 faces no mesmo plano.
- [ ] **Passo 6: a imagem do resultado.**
  - Gere `docs/preview/armilar-formada.png`, uma prévia isométrica das fatias juntas, com o mesmo pincel das prévias do `blocos.py`. Vale também o harness do protótipo, `filme/formado.html`, apontando para os modelos gerados.
  - Compare com `blocos_armilar/comparacao.png`: a silhueta e as cores têm de bater.
  - Ponha o caminho da imagem no relatório.
- [ ] **Passo 7:** rode `python -I scripts/textures/gerar_texturas.py` e depois `./gradlew build`. Tem de passar.
- [ ] **Passo 8:** commit `Armilar: a escultura formada fatiada na grade`.

---

### Tarefa 4: A estrutura nova

É a troca de verdade. Muda a regra, os estados, a formação, a projeção, o JEI e os blockstates, e tira o Vidro.

**Arquivos:**
- Reescrever: `resonator/ResonatorLayout.java`, `resonator/ResonatorBlueprint.java`
- Mudar:
  - `resonator/ResonatorBill.java`, `ProjectionDiff.java`, `PlacementFacing.java`, `ResonatorFormation.java`, `CoreShell.java`, `ResonanceCoreBlockEntity.java`;
  - `ResonatorCasingBlock.java`, `ResonatorPartBlock.java`, `ResonanceCoreBlock.java`, `ResonatorStatus.java`;
  - `packet/ResonatorStatePayload.java`, `registry/ModPayloads.java`, `menu/ResonatorMenu.java`, `client/resonator/screen/*` (`StructureLines` e o que mostra o layout);
  - `client/resonator/ResonatorProjection.java`, `ProjectionRenderer.java`, `ProjectionText.java`, `client/ClientSetup.java` (a tecla de troca de layout sai);
  - `client/resonator/render/ChamberScene.java`;
  - `compat/jei/StructureView.java`, `ResonatorStructureCategory.java`, `ResonatorJei.java`;
  - `registry/ModBlocks.java`, `ModItems.java`, `ModCreativeTabs.java`;
  - `bench/BenchResonators.java`, `gametest/ResonatorTestSupport.java` e as classes de e2e que não compilam mais (só o necessário para compilar; a Tarefa 5 acerta o comportamento).
- Criar: `resonator/BasePosition.java`
- Apagar: `resonator/ResonatorGlassBlock.java`, `GlassFace.java`, `CasingPosition.java`; `recipe/resonator_glass.json`; `loot_table/blocks/resonator_glass.json`
- Mudar: `recipe/harmonic_lens.json` (`resonator_glass` vira `minecraft:glass`), `lang/*.json`, `scripts/textures/blocos.py` (blockstates novos e fim da casca antiga)
- Testes: reescrever `ResonatorLayoutTest`, `ResonatorLayoutPreferredTest`, `ResonatorBlueprintTest`, `ResonatorBillTest`, `ProjectionDiffTest` e `PlacementFacingTest`; mudar `ResonatorGuideSceneTest` (a cena é regerada na Tarefa 6)

**Interfaces:**
- **Consome:** `StackPart`, `Corner`, `ResonatorColumnBlock.PART`/`CORNER` e `ResonatorCradleBlock.PART` (Tarefa 1); `armilar_modelo.blockstates_armilar()` (Tarefa 3).
- **Produz:**
  - `ResonatorLayout.Part`: `CASING`, `CORE`, `ENERGY_PORT`, `CRYSTAL_PORT`, `TUNER`, `COLUMN`, `CRADLE`, `AIR` e `OTHER`.
  - `ResonatorLayout.FRONTS = {SOUTH, WEST, NORTH, EAST}` e `ResonatorLayout.expected(int lx, int ly, int lz)`.
  - `ResonatorLayout.world(Face front, int cx, int cy, int cz, int lx, int ly, int lz) -> int[3]` e `ResonatorLayout.toWorld(Face front, Face local) -> Face`.
  - `record Member(int x, int y, int z, Part part, @Nullable String slot, @Nullable Face face, @Nullable String corner)`
  - `record Formed(int minX, int minY, int minZ, Face front, List<Member> members)`
  - `ResonatorBlueprint.expected(Face front) -> Part[5][5][5]` (índices da caixa do mundo, a partir do canto mínimo) e `ResonatorBlueprint.origin(int cx, int cy, int cz, Face front) -> int[3]`.
  - `ResonatorBill.of()`, sem argumento.
  - `PlacementFacing.wallFacing(int[] origin, int x, int y, int z) -> @Nullable Face`
  - `BasePosition`: `LOOSE("loose")` e as 22 casas `P00("p00")` … `P44("p44")`, sem `p24`, `p42` e `p02`; `static BasePosition byName(String)`, que devolve `LOOSE` para nome desconhecido.
  - `ResonatorCasingBlock.POSITION` (`EnumProperty<BasePosition>`) e `ResonatorCasingBlock.FACING` (= `BlockStateProperties.HORIZONTAL_FACING`).
  - `ResonatorPartBlock.FACING` passa a ser `BlockStateProperties.HORIZONTAL_FACING`.

**Custo:**
- A regra lê no máximo 4 caixas de 125 casas pela `Probe` com cache (as mesmas 9³ leituras de hoje), só por evento.
- A formação faz 35 `setBlock`, uma vez.
- O payload perde um byte.

- [ ] **Passo 1: os testes da regra.** Reescreva o `ResonatorLayoutTest` com um mundo falso (`Map<Long, Part>` e uma `Probe`) e um construtor que põe a Armilar inteira em volta de um Núcleo, para uma frente dada, usando `ResonatorLayout.world`. Casos:
  - monta nas 4 frentes: 35 membros, `formed.front()` igual à frente montada, Núcleo com `face` = a frente;
  - sem Sintonizador (Laje na casa dele): monta, e a Laje dessa casa tem `slot` `"p20"`;
  - os `slot` das Lajes (frente sul): a casa local (0, 0, 0) tem `"p00"` e (4, 0, 4) tem `"p44"`; a `face` de toda Laje é a frente;
  - as Colunas com frente sul: a da casa local (3, 1, 3) tem `slot` `"lower"` e `corner` `"se"`; a de (1, 2, 1), `"upper"` e `"nw"`;
  - as Colunas com frente leste: a casa local (3, 1, 3) cai no mundo a (dz, −dx) = (1, −1) do centro, então tem `corner` `"ne"`;
  - a `face` das peças com frente sul: Energia `EAST`, Cristais `WEST`, Sintonizador `NORTH`; com frente leste: Energia `NORTH`, Cristais `SOUTH`, Sintonizador `WEST`. Quem olha a frente de fora está a leste, olhando para oeste, e a direita dessa pessoa é o norte;
  - Laje na casa da Energia: `Broken(MISSING_ENERGY_PORT, …)` na posição da casa; o mesmo para os Cristais;
  - ar na casa de uma Coluna: `WRONG_BLOCK` ali;
  - qualquer bloco numa casa de ar (por exemplo, (2, 3, 2)): `INTERIOR_NOT_EMPTY`;
  - outro Núcleo numa casa de Laje: `EXTRA_CORE`;
  - a frente do `check(..., front, probe)` é tentada primeiro (a classe `ResonatorLayoutPreferredTest`): com a Armilar montada para oeste e o Núcleo dizendo oeste, a `Probe` lê só 125 casas diferentes.
- [ ] **Passo 2:** rode `./gradlew test --tests '*ResonatorLayout*'` e veja falhar.
- [ ] **Passo 3: o `ResonatorLayout`.** Reescreva com esta forma (o `Face` mantém `dx`, `dy` e `dz`; `right()` e `up()` saem, porque só o vidro usava):

```java
public static final int SIZE = 5;
private static final int LAST = SIZE - 1;
private static final int MID = SIZE / 2;
public static final Face[] FRONTS = {Face.SOUTH, Face.WEST, Face.NORTH, Face.EAST};

public enum Part { CASING, CORE, ENERGY_PORT, CRYSTAL_PORT, TUNER, COLUMN, CRADLE, AIR, OTHER }

public enum Problem {
    WRONG_BLOCK, INTERIOR_NOT_EMPTY, EXTRA_CORE, MISSING_ENERGY_PORT, MISSING_CRYSTAL_PORT, SHARED_WALL
}

/** A peça esperada na casa local (frente para o sul): a tabela da spec. */
public static Part expected(int lx, int ly, int lz) {
    if (ly == 0) {
        if (lx == MID && lz == LAST) return Part.CORE;
        if (lx == LAST && lz == MID) return Part.ENERGY_PORT;
        if (lx == 0 && lz == MID) return Part.CRYSTAL_PORT;
        if (lx == MID && lz == 0) return Part.TUNER;
        return Part.CASING;
    }
    if (ly <= 2) {
        if (lx == MID && lz == MID) return Part.CRADLE;
        if ((lx == 1 || lx == 3) && (lz == 1 || lz == 3)) return Part.COLUMN;
    }
    return Part.AIR;
}

/** O deslocamento (dx, dz) do referencial local (frente sul) levado ao mundo para a frente {@code front}. */
static int[] rotate(Face front, int dx, int dz) {
    return switch (front) {
        case SOUTH -> new int[] {dx, dz};
        case WEST -> new int[] {-dz, dx};
        case NORTH -> new int[] {-dx, -dz};
        case EAST -> new int[] {dz, -dx};
        default -> throw new IllegalArgumentException("frente vertical: " + front);
    };
}

/** A posição no mundo da casa local, com o Núcleo em (cx, cy, cz) e a frente {@code front}. */
public static int[] world(Face front, int cx, int cy, int cz, int lx, int ly, int lz) {
    int[] core = rotate(front, 0, LAST - MID);
    int[] cell = rotate(front, lx - MID, lz - MID);
    return new int[] {cx - core[0] + cell[0], cy + ly, cz - core[1] + cell[1]};
}

/** A direção local (frente sul) levada ao mundo. */
public static Face toWorld(Face front, Face local) {
    int[] d = rotate(front, local.dx(), local.dz());
    for (Face face : FRONTS) {
        if (face.dx() == d[0] && face.dz() == d[1]) return face;
    }
    throw new IllegalArgumentException("direção vertical: " + local);
}
```

  - **`check`:**
    - `check(cx, cy, cz, front, probe)` tenta `front` e depois as outras de `FRONTS`, na ordem.
    - Para cada frente, `evaluate` percorre as 125 casas locais e lê `probe.at(world(...))`. Se os erros alcançam o melhor já visto, para cedo (como hoje, devolvendo `null`).
    - Uma frente sem erro monta. Senão vale o `Broken` da frente de menos erros.
    - `check(cx, cy, cz, probe)` é `check(cx, cy, cz, Face.SOUTH, probe)`.
  - **Problema de uma casa:**

```java
private static Problem problem(Part want, Part have, boolean thisCore) {
    if (have == Part.CORE && !thisCore) return Problem.EXTRA_CORE;
    return switch (want) {
        case AIR -> have == Part.AIR ? null : Problem.INTERIOR_NOT_EMPTY;
        case TUNER -> have == Part.TUNER || have == Part.CASING ? null : Problem.WRONG_BLOCK;
        case ENERGY_PORT -> have == Part.ENERGY_PORT ? null : Problem.MISSING_ENERGY_PORT;
        case CRYSTAL_PORT -> have == Part.CRYSTAL_PORT ? null : Problem.MISSING_CRYSTAL_PORT;
        default -> have == want ? null : Problem.WRONG_BLOCK;
    };
}
```

  - **Membros**, cada casa que não é ar:
    - **`CASING`:** `slot = "p" + lx + lz` e `face = front`.
    - **`CORE`:** `face = front`. **`ENERGY_PORT`:** `toWorld(front, EAST)`. **`CRYSTAL_PORT`:** `toWorld(front, WEST)`. **`TUNER`:** `toWorld(front, NORTH)`.
    - **`COLUMN`:** `slot = ly == 1 ? "lower" : "upper"`. O `corner` vem dos sinais do deslocamento no mundo, `rotate(front, lx - 2, lz - 2)`: (+, +) `"se"`, (−, +) `"sw"`, (−, −) `"nw"`, (+, −) `"ne"`.
    - **`CRADLE`:** `slot = ly == 1 ? "lower" : "upper"`.
  - **`Formed`:** `minX`, `minY` e `minZ` são o canto mínimo das 125 casas no mundo.
- [ ] **Passo 4: o `ResonatorBlueprint`.**
  - `expected(Face front)` preenche um `Part[5][5][5]` nos índices da caixa do mundo: para cada casa local, `world(front, ...)` menos `origin(...)`.
  - `origin(cx, cy, cz, front)` é o canto mínimo.
  - Saem `Layout`, `layoutOf`, `window`, `band`, `isBand`, `isPiece`, `windowKeys`, `opposite`, `clockwise` e `counterClockwise`.
  - Teste (`ResonatorBlueprintTest`): nas 4 frentes, a contagem é 21 `CASING`, 1 `CORE`, 1 `ENERGY_PORT`, 1 `CRYSTAL_PORT`, 1 `TUNER`, 8 `COLUMN`, 2 `CRADLE` e 90 `AIR`. O Núcleo fica na casa da caixa igual a (núcleo − origem).
- [ ] **Passo 5: a conta, a comparação e a frente ao colocar.**
  - **`ResonatorBill.of()`:** frente sul, Núcleo na casa (2, 0, 4) da caixa. Teste: `count(CASING) == 21`, `count(COLUMN) == 8`, `count(CRADLE) == 2`, `count(TUNER) == 1` e `members().size() == 35`.
  - **`ProjectionDiff.stateOf(want, have)`:** é `OK` se `have == want` ou se `want == TUNER && have == CASING`; `MISSING` se `have == AIR`; senão `WRONG`. Sai `isFaceFill`. Teste: Laje na casa do Sintonizador é `OK` e não entra em `missing`; ar numa casa de Coluna conta `missing[COLUMN]`.
  - **`PlacementFacing.wallFacing(origin, x, y, z)`:** só a camada y = origem. Nas casas (2, 0, 0), (2, 0, 4), (0, 0, 2) e (4, 0, 2) da caixa devolve `NORTH`, `SOUTH`, `WEST` e `EAST`; senão `null`. Teste das quatro casas e de um canto (`null`).
- [ ] **Passo 6: os blocos.**
  - **`BasePosition`:** no molde do `CasingPosition`, com a lista das 22 casas gerada num laço estático. Apague `CasingPosition` e `GlassFace`.
  - **`ResonatorCasingBlock`:** `POSITION` (`BasePosition`, padrão `LOOSE`) e `FACING` horizontal (padrão `NORTH`). `getOcclusionShape` devolve `Shapes.empty()` com `POSITION != LOOSE`, porque o modelo formado não é cubo inteiro. O resto continua.
  - **`ResonatorPartBlock` e `ResonanceCoreBlock`:** `FACING = BlockStateProperties.HORIZONTAL_FACING`.
    - `towardPlayer` vira `context.getHorizontalDirection().getOpposite()`.
    - O `wallFacing` acha o Núcleo mais próximo no cubo de raio 4 (como hoje) e chama `PlacementFacing.wallFacing(ResonatorBlueprint.origin(núcleo, frente do Núcleo), pos)`.
    - As duas classes ganham `noOcclusion()` nas propriedades do registro, porque os modelos não são cubos inteiros.
  - Apague o `ResonatorGlassBlock`.
- [ ] **Passo 7: a formação.**
  - **`partOf`:** `ResonatorColumnBlock` vira `COLUMN` e `ResonatorCradleBlock` vira `CRADLE`; sai o vidro.
  - **`formedState`:**
    - Laje: `POSITION = BasePosition.byName(slot)` e `FACING = direção(face)`.
    - Coluna: `PART = StackPart.byName(slot)` e `CORNER = Corner.byName(corner)`.
    - Berço: `PART`.
    - Núcleo e peças: `FACING = direção(face)` e `FORMED = true`.
  - **`looseState`:** Laje `POSITION = LOOSE`, Coluna e Berço `PART = LOOSE`, Núcleo e peças `FORMED = false`.
  - **`check`:** pega a frente do `FACING` horizontal do Núcleo.
  - **`structure()`:** só conta o Sintonizador e as Portas. Sai o `layoutOf`. `core.setStructure(tuner, energyPorts, crystalPorts, gameTime)`.
  - **`CoreShell`:** sai o `layout`. Não grava nem lê `layout`; as outras chaves continuam.
- [ ] **Passo 8: o cliente e a rede.**
  - **Payload:** sai o layout do `ResonatorStatePayload` e do `ResonatorMenu`. `ModPayloads.VERSION = "53"`.
  - **Tela do Núcleo:** sai a linha de layout.
  - **Projeção:** usa `ResonatorBlueprint.expected(frente do Núcleo)`.
    - Sai a troca Shift + roda e o estado de layout.
    - O `ProjectionText` mostra os nomes das peças novas e perde as chaves `layout.*`.
  - **`ChamberScene`:** o centro passa a ser origem + (2,5; 47,5/16; 2,5), a posição do sol, até a etapa 2.
  - **JEI:** uma receita de estrutura só.
    - `StructureView` desenha Coluna e Berço (os estados formados vêm do `formedState` dos membros da conta, como hoje).
    - Os espaços de ingrediente vêm da conta: 7 tipos.
    - O texto `jei...resonator.info` descreve a Armilar.
- [ ] **Passo 9: o registro e os dados.**
  - Tire o `RESONATOR_GLASS` de `ModBlocks`, `ModItems` e da aba criativa.
  - Registre o alias nos dois `DeferredRegister`, perto do registro: `BLOCKS.addAlias(id("resonator_glass"), id("resonator_casing"))` e `ITEMS.addAlias(...)`, com a mesma assinatura (`DeferredRegister#addAlias(ResourceLocation, ResourceLocation)`, existe no NeoForge 21.1.248).
  - Apague a receita e o loot table do Vidro. Na `harmonic_lens.json`, `wirelessautomate:resonator_glass` vira `minecraft:glass`.
  - **Lang:**
    - Laje com o nome novo nos dois idiomas.
    - Saem `block.wirelessautomate.resonator_glass`, o problema `TOO_MANY_TUNERS` e as chaves `layout.*` da projeção, da tela e do JEI.
    - Entram as peças novas onde a projeção e o JEI listam peças.
    - Procure tudo com `rg -n "glass|layout|TOO_MANY_TUNERS|5x5x5|5×5×5" src/main/resources/assets/wirelessautomate/lang`.
- [ ] **Passo 10: os blockstates.**
  - No `blocos.py`, troque o `blockstates_ressonador()` pelo `armilar_modelo.blockstates_armilar()`:
    - **Laje:** `facing=<f>,position=loose` vai para `resonator_casing_loose`; `facing=<f>,position=pXZ` vai para `armilar/casing_pXZ` com o `y` da frente.
    - **Coluna:** `part=loose` (qualquer canto) vai para o solto; `part=lower|upper,corner=<c>` vai para `armilar/column_<part>` com o `y` do canto (`se` 0, `sw` 90, `nw` 180, `ne` 270).
    - **Berço:** `part=loose|lower|upper`.
    - **Peças:** `facing=<f>,formed=false` vai para o solto com o giro de solto; `formed=true` vai para `armilar/<peça>` com o giro de formado.
    - **Núcleo:** igual às peças, com `tier`.
  - Apague do gerador a casca antiga (carcaça por posição, vidro, forro, `*_dentro`). A limpeza do gerador remove os arquivos que sobrarem.
- [ ] **Passo 11: compilar o resto.**
  - Ajuste o mínimo em `ResonatorTestSupport`, `BenchResonators`, nos `E2e*Resonator*` e em todo `Resonator*GameTests` que não compila mais.
  - O construtor de estrutura do `ResonatorTestSupport` passa a montar a Armilar pelo `ResonatorLayout.world` e pelo `expected`.
  - Os testes que dependem da caixa antiga ficam para a Tarefa 5: não os desligue, só faça compilar.
- [ ] **Passo 12:** rode `python -I scripts/textures/gerar_texturas.py`, `./gradlew test --tests '*Resonator*' --tests '*Projection*' --tests '*Placement*'` e depois `./gradlew build`. Tem de passar.
- [ ] **Passo 13: olhar no jogo.**
  - Com o `runClient`, monte uma Armilar à mão, uma vez, e tire duas capturas: solta e formada.
  - Compare com a prancha e com `comparacao.png`.
  - Ponha os caminhos das capturas no relatório. Não faça uma série de rodadas de jogo.
- [ ] **Passo 14:** commit `Armilar: a regra nova, os estados e a formação`.

---

### Tarefa 5: GameTests, bench e e2e

**Arquivos:**
- Mudar:
  - `gametest/ResonatorTestSupport.java`, `ResonatorFormationGameTests.java`, `ResonatorSharedWallGameTests.java`, `ResonatorPlacementGameTests.java`, `ResonatorGuideSceneGameTests.java`;
  - os outros `Resonator*GameTests` que falharem;
  - `bench/BenchResonators.java`;
  - `client/dev/E2eResonatorSteps.java`, `E2eShowcaseResonator.java`, `E2eResonatorFlowSteps.java`, `E2eResonatorScreenSteps.java`, `E2eJeiResonatorSteps.java`, `E2eShowcaseWorld.java`, `E2eShowcaseRepairer.java`.
- Os templates `structure/empty7.nbt` e `empty11.nbt` continuam: a Armilar (5 × 5 × 5) cabe.

**Custo:** nenhum no jogo, só testes.

- [ ] **Passo 1: o suporte.** `ResonatorTestSupport.build(helper, frente)` monta a Armilar com o Núcleo virado para a frente dada, mais uma variante sem Sintonizador. A posição do Núcleo e das Portas sai do `ResonatorLayout.world`.
- [ ] **Passo 2: a formação** (`ResonatorFormationGameTests`), com estes casos:
  - monta nas 4 frentes;
  - os estados gravados: Laje `pXZ` com a frente, Coluna com `part` e `corner`, Berço com `part`, peças viradas para fora;
  - quebrar uma Coluna desmonta e tudo volta a solto;
  - um bloco numa casa de ar não monta (`INTERIOR_NOT_EMPTY`);
  - um Ressonador antigo convertido: monte uma caixa 5 × 5 × 5 de Lajes soltas em volta de um Núcleo e confira que não monta e que o Núcleo guarda o tier e os itens. É o caso "mundo antigo".
- [ ] **Passo 3: a parede compartilhada** (`ResonatorSharedWallGameTests`).
  - Duas Armilares sem Sintonizador, de costas uma para a outra, dividindo a fileira de trás do chão: a primeira com frente sul, a segunda com frente norte, ao norte dela.
  - A segunda não monta (`SHARED_WALL`), e a desmontagem da primeira libera a segunda, como hoje.
- [ ] **Passo 4: a frente ao colocar** (`ResonatorPlacementGameTests`). Uma Porta posta numa casa do meio da borda do chão nasce virada para fora; num canto, virada para o jogador.
- [ ] **Passo 5: o resto.**
  - Rode `./gradlew runGameTestServer` e acerte cada `Resonator*GameTests` que falhar pela forma nova (coordenadas fixas, `CasingPosition`, contagem 98 virando 35).
  - Não mude o que o teste prova, só onde ele monta e lê.
- [ ] **Passo 6: o bench.** O `BenchResonators` monta Armilares na grade.
  - Siga `.claude/skills/wa-performance/references/medir.md`: rode o cenário do Ressonador antes (no `armilar~4`, o commit de antes da Tarefa 4) e depois.
  - Ponha os números no `docs/benchmark.md`. A revalidação não pode ficar mais cara além do ruído.
- [ ] **Passo 7: o e2e.**
  - As classes de e2e do Ressonador montam a Armilar (o `placeVitrine` vira `placeArmilar`; `DIRT` e `MISSING_GLASS` viram casos da forma nova: um bloco numa casa de ar e uma Coluna faltando).
  - As câmeras da vitrine olham o centro novo.
  - Rode `./scripts/e2e.sh --only resonator --capture armilar`, mais as suítes que tocam o Ressonador (`./scripts/e2e.sh --lista` mostra quais).
  - `run/e2e/result.txt` tem de dizer `OK`.
  - Olhe as capturas novas (folha de contato: `python -I .claude/skills/wa-curseforge-imagens/scripts/folha.py`) e ponha o caminho no relatório.
- [ ] **Passo 8:** rode `./gradlew build runGameTestServer runGameTestServerChemicals runGameTestServerSource runGameTestServerAllthemodium`. Tem de passar.
- [ ] **Passo 9:** commit `Armilar: GameTests, bench e e2e na forma nova`.

---

### Tarefa 6: Guia, documentação e progresso

**Arquivos:**
- Mudar:
  - `scripts/guide/paginas/signal_resonator.py` (texto e anotações);
  - `scripts/guide/resonator_scene.txt` (regerado pelo `ResonatorGuideSceneTest` com `-Dwa.regen=true`);
  - as páginas do guia geradas (`python -I scripts/guide/gerar_guia.py`);
  - `scripts/guide/index.py` e `recipes.py` (os ids novos e o Vidro fora).
- Mudar os docs:
  - `docs/mapa/ressonador.md` (as classes novas e as apagadas);
  - `docs/superpowers/specs/2026-10-15-ressonador-de-sinal-design.md` (uma linha no topo da seção "Estrutura": substituída pela spec da Armilar);
  - `README.md` (o parágrafo do Ressonador fala em caixa 5×5×5);
  - `docs/progresso/` (histórico do mês, `estado.md`, `pendencias.md` e o próximo passo no `README.md` de lá).

**Custo:** nenhum.

- [ ] **Passo 1: a cena.** Regere a cena com `./gradlew test --tests '*ResonatorGuideSceneTest' -Dwa.regen=true`: são 35 linhas `<Block>`. Confira que o teste passa sem a flag.
- [ ] **Passo 2: a página.** Reescreva a página `signal_resonator.py` nos dois idiomas, só com "como operar", em passos curtos:
  - camada 1, o chão: o Núcleo na frente, a Porta de Energia à direita, a de Cristais à esquerda, o Sintonizador atrás (opcional) e o resto Lajes;
  - camadas 2 e 3: as Colunas nas quatro diagonais em volta do centro e um Berço do Sol no centro de cada;
  - em cima: vazio.

  As anotações apontam o Núcleo, as Portas, uma Coluna e o Berço, pelas posições da conta. Rode `python -I scripts/guide/gerar_guia.py`.
- [ ] **Passo 3: os docs.** Atualize o mapa, o README e a spec antiga como listado.
- [ ] **Passo 4: o progresso.**
  - Uma linha no histórico de novembro: o que foi feito e os números de testes, no formato das outras linhas.
  - As pendências da etapa 2: render do sol, das órbitas, dos cristais e do horizonte, e a saída do interior antigo.
- [ ] **Passo 5:** rode `./gradlew build runGameTestServer runGameTestServerChemicals runGameTestServerSource runGameTestServerAllthemodium` e o e2e do guia (`./scripts/e2e.sh --only guia`, ou o nome que o `--lista` mostrar). Os dois têm de passar.
- [ ] **Passo 6:** commit `Armilar: guia, mapa e progresso da etapa 1`.
