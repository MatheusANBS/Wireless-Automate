# Lixeira: plano de implementação (etapa 1: mecânica, bloco e tela)

> **Para agentes:** SUB-SKILL OBRIGATÓRIA: use superpowers:subagent-driven-development (recomendado) ou superpowers:executing-plans para executar este plano tarefa por tarefa. Os passos usam caixas (`- [ ]`) para acompanhar.

**Objetivo:** um bloco `wirelessautomate:trash` que some com itens, fluidos, energia, químicos e Source, com uma tela de cinco teclas que liga e desliga cada tipo, o modelo estático com o circuito e o vórtice animados por textura, receita fora (fica para o balanceamento).

**Arquitetura:** a Lixeira é um block entity sem ticker que guarda só uma máscara de cinco bits (um por `ResourceType`). As capabilities são registradas por block entity e devolvem um handler sem estado, um objeto por tipo para todas as Lixeiras, ou `null` quando o tipo está desligado. A tela não tem slots: a máscara vai num `DataSlot` do menu e o clique volta pelo `clickMenuButton` do vanilla, então **nenhum pacote novo** é criado.

**Stack:** Java 21, NeoForge 21.1 (Minecraft 1.21.1), ModDevGradle, JUnit 5, GameTests, Mekanism e Ars Nouveau (opcionais, só nas runs deles), Python 3 com Pillow (modelo e textura).

**Spec:** [`docs/superpowers/specs/2026-11-10-lixeira-design.md`](../specs/2026-11-10-lixeira-design.md). Mockup da tela: `docs/preview/lixeira-tela.html` (layout B). Modelo: `docs/preview/lixeira.bbmodel`. A etapa 2 (o anel e o cristal no renderer e o laço engolindo) tem plano próprio, depois desta.

## Restrições globais

- Código, comentários, mensagens de commit e docs em português; traduções em `en_us.json` e `pt_br.json`, sempre as duas.
- Id do bloco e do item: `trash`. Nomes: "Lixeira" (pt_br) e "Trash Bin" (en_us). Título da tela: `container.wirelessautomate.trash`.
- **Sem tier, sem receita, sem filtro, sem contador, sem inventário do jogador na tela.**
- **Nasce com os cinco tipos ligados.** A máscara usa o `ordinal()` do `ResourceType` (ITEM 0, FLUID 1, ENERGY 2, CHEMICAL 3, SOURCE 4); tudo ligado é `31`.
- Chave de NBT do block entity: `types` (int), gravada só quando diferente de 31. Componente de dados do item: `wirelessautomate:trash_types` (int de 0 a 31), posto no item só quando diferente de 31, para as Lixeiras padrão empilharem.
- **Tipos do Mekanism** só em `compat/mekanism/`; **tipos do Ars** só em `compat/arsnouveau/`; nunca na assinatura de método de classe `@EventBusSubscriber` ou `@GameTestHolder`. O resto passa pelas pontes `network/Chemicals.java` e `network/Sources.java`. A run `runGameTestServer` (sem os dois) prova que o mod carrega sem eles.
- **Classes de tela só em `client/`**, nunca referenciadas por código comum.
- Chaves existentes de NBT, componentes e config **não mudam**. `ModPayloads.VERSION` **continua `"49"`**: esta etapa não cria nem muda payload.
- Cor da luz (lente do Ultimate, de `scripts/textures/identidade.py`): `#7B4FE0`, brilho `#C7B0FA`, sombra `#4A2C92`.
- Colisão: caixa cheia de y 0 a 13 e quatro pilares de 3 × 3 de y 13 a 16 nos cantos.
- Performance (skill `wa-performance`, ler antes de cada tarefa): nada de tick por bloco; capability só por `BlockCapabilityCache`; nada sincronizado com a tela fechada; handlers sem alocação; `level.blockEntityChanged(pos)` no lugar de `setChanged()`; por quadro, só desenhar.
- Limites de tamanho (skill `wa-modular`): tela até 500 linhas, classe de servidor até 600. Comentários pela skill `wa-comentarios`.
- Antes de cada commit: `./gradlew build runGameTestServer runGameTestServerChemicals runGameTestServerSource runGameTestServerAllthemodium runGameTestServerTeams` (Git Bash, na raiz), mais `python -I .claude/skills/wa-performance/scripts/varrer_perf.py`, `python -I .claude/skills/wa-comentarios/scripts/varrer.py` e `python -I .claude/skills/wa-modular/scripts/medir.py` nos arquivos mudados. Nas tarefas 4 e 5, também `./scripts/e2e.sh --only lixeira --capture lixeira` (o `run/e2e/result.txt` deve dizer `OK`). Compile antes (`./gradlew compileJava`); com `BUILD FAILED`, pare e conserte. Nunca dois jogos ao mesmo tempo.
- Todo GameTest novo com `timeoutTicks` de no máximo 300, cria a própria rede (`NetworkSavedData.get(server).create(...)`) e espera o `onLoad` com `startSequence().thenWaitUntil(...)`.
- Commits sem linhas de atribuição, com o autor do dono.

**Ajuste em relação à spec:** a spec previa "um menu e um payload novos, logo `ModPayloads.VERSION` sobe". O estado da tela são cinco bits e a ação é um clique numa de cinco teclas, o que o vanilla já transporta: o `DataSlot` do menu leva a máscara (só com a tela aberta, só quando muda) e o `clickMenuButton` traz o clique. Com isso não há payload, a versão do protocolo não sobe e um cliente da 2.15 continua entrando no servidor. A tarefa 6 corrige a frase na spec.

## Mapa de arquivos

Base: `src/main/java/io/github/matheusanbs/wirelessautomate/`.

| Arquivo | Papel |
| --- | --- |
| `trash/TrashTypes.java` (novo) | Lógica pura da máscara: ligado, alternar, limpar, contar |
| `trash/TrashHandlers.java` (novo) | Os handlers sem estado de itens, fluidos e energia (os do NeoForge e as visões `Bulk*`) e a `BulkSource` |
| `trash/TrashBlock.java`, `trash/TrashBlockEntity.java`, `trash/TrashShapes.java` (novos) | O bloco (abre a tela, colisão), o block entity (a máscara, NBT, componente) e as caixas de colisão |
| `trash/TrashCapabilities.java` (novo) | Registro das capabilities, cada uma condicionada ao bit do tipo |
| `compat/mekanism/MekanismTrash.java`, `compat/arsnouveau/ArsTrash.java` (novos) | O handler de químico e a capability de Source da Lixeira |
| `network/Chemicals.java`, `network/Sources.java` (mudar) | `registerTrash(event)`, que só chama o compat com o mod presente |
| `menu/TrashMenu.java` (novo) | Menu sem slots: `DataSlot` da máscara e `clickMenuButton` |
| `client/trash/TrashKeysLayout.java`, `client/trash/TrashScreen.java` (novos) | Onde ficam as teclas (puro, com JUnit) e o desenho da tela |
| `registry/ModBlocks.java`, `ModItems.java`, `ModBlockEntities.java`, `ModMenus.java`, `ModDataComponents.java`, `ModCreativeTabs.java`, `WirelessAutomate.java`, `client/ClientSetup.java` (mudar) | Registros |
| `gametest/TrashGameTests.java`, `TrashMenuGameTests.java`, e os casos novos em `ChemicalGameTests.java` e `SourceGameTests.java` (novos e mudar) | GameTests |
| `src/test/java/.../trash/TrashTypesTest.java`, `.../client/trash/TrashKeysLayoutTest.java` (novos) | JUnit |
| `scripts/textures/lixeira_modelo.py` (novo) | Converte `docs/preview/lixeira.bbmodel` no JSON de bloco e gera a textura animada |
| `src/main/resources/assets/wirelessautomate/{blockstates,models/block,models/item,textures/block}/trash*`, `data/wirelessautomate/loot_table/blocks/trash.json`, `data/minecraft/tags/block/mineable/pickaxe.json`, `lang/*.json` (novos e mudar) | Recursos |
| `client/dev/E2eTrashSteps.java` (novo) e o registro da suíte | e2e |
| `scripts/guide/paginas/`, `docs/mapa/`, `docs/progresso/`, a spec (mudar) | Docs |

---

### Tarefa 1: a máscara de tipos (lógica pura)

**Arquivos:**
- Criar: `trash/TrashTypes.java`
- Teste: `src/test/java/io/github/matheusanbs/wirelessautomate/trash/TrashTypesTest.java`

**Interfaces:**
- Produz: `TrashTypes.COUNT` (5), `TrashTypes.ALL` (31), `boolean isOn(int mask, int index)`, `int toggle(int mask, int index)`, `int clean(int mask)`, `int count(int mask, int available)`.

**Custo:** nenhum em jogo; são contas de bits chamadas só num clique ou ao montar a capability.

- [ ] **Passo 1: escrever o teste que falha**

```java
package io.github.matheusanbs.wirelessautomate.trash;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TrashTypesTest {
    @Test
    void nasceComTudoLigado() {
        assertEquals(31, TrashTypes.ALL);
        for (int i = 0; i < TrashTypes.COUNT; i++) {
            assertTrue(TrashTypes.isOn(TrashTypes.ALL, i));
        }
    }

    @Test
    void alternarMexeSoNoBitPedido() {
        int semEnergia = TrashTypes.toggle(TrashTypes.ALL, 2);
        assertEquals(0b11011, semEnergia);
        assertFalse(TrashTypes.isOn(semEnergia, 2));
        assertEquals(TrashTypes.ALL, TrashTypes.toggle(semEnergia, 2));
    }

    @Test
    void indiceForaDaFaixaNaoMuda() {
        assertEquals(TrashTypes.ALL, TrashTypes.toggle(TrashTypes.ALL, -1));
        assertEquals(TrashTypes.ALL, TrashTypes.toggle(TrashTypes.ALL, 5));
        assertFalse(TrashTypes.isOn(TrashTypes.ALL, 5));
    }

    @Test
    void limparTiraBitsQueNaoExistem() {
        assertEquals(31, TrashTypes.clean(0xFF));
        assertEquals(0, TrashTypes.clean(0b100000));
        assertEquals(5, TrashTypes.clean(5));
    }

    @Test
    void contaSoOsTiposDisponiveis() {
        assertEquals(5, TrashTypes.count(TrashTypes.ALL, 0b11111));
        assertEquals(3, TrashTypes.count(TrashTypes.ALL, 0b00111));
        assertEquals(2, TrashTypes.count(0b11011, 0b00111));
    }
}
```

- [ ] **Passo 2: rodar e ver falhar**

Rode: `./gradlew test --tests '*TrashTypesTest'`
Esperado: falha de compilação, `TrashTypes` não existe.

- [ ] **Passo 3: implementar**

```java
package io.github.matheusanbs.wirelessautomate.trash;

/**
 * A máscara de tipos da Lixeira: um bit por tipo de recurso, na posição do {@code ordinal()} do
 * {@code ResourceType}. Bit ligado, a Lixeira aceita e descarta aquele tipo. Lógica pura, com JUnit.
 */
public final class TrashTypes {
    /** Quantos tipos a máscara cobre. */
    public static final int COUNT = 5;
    /** Tudo ligado: como a Lixeira nasce. */
    public static final int ALL = (1 << COUNT) - 1;

    private TrashTypes() {
    }

    public static boolean isOn(int mask, int index) {
        return index >= 0 && index < COUNT && (mask & 1 << index) != 0;
    }

    /** A máscara com o bit de {@code index} trocado; índice fora da faixa não muda nada. */
    public static int toggle(int mask, int index) {
        return index >= 0 && index < COUNT ? mask ^ 1 << index : mask;
    }

    /** Só os bits que existem (o que vem do disco ou de um item de outra versão). */
    public static int clean(int mask) {
        return mask & ALL;
    }

    /** Quantos tipos ligados há entre os {@code available} (a máscara dos tipos carregados). */
    public static int count(int mask, int available) {
        return Integer.bitCount(mask & available & ALL);
    }
}
```

- [ ] **Passo 4: rodar e ver passar**

Rode: `./gradlew test --tests '*TrashTypesTest'`
Esperado: PASS (5 testes).

- [ ] **Passo 5: commit**

```bash
git add src/main/java/io/github/matheusanbs/wirelessautomate/trash/TrashTypes.java src/test/java/io/github/matheusanbs/wirelessautomate/trash/TrashTypesTest.java
git commit -m "Lixeira: a máscara de tipos (lógica pura)"
```

---

### Tarefa 2: bloco, block entity e as capabilities de itens, fluidos e energia

**Arquivos:**
- Criar: `trash/TrashHandlers.java`, `trash/TrashShapes.java`, `trash/TrashBlock.java`, `trash/TrashBlockEntity.java`, `trash/TrashCapabilities.java`, `gametest/TrashGameTests.java`
- Mudar: `registry/ModBlocks.java` (junto do `CRYSTAL_REPAIRER`, linha 110), `registry/ModItems.java` (linha 156), `registry/ModBlockEntities.java` (linha 44), `registry/ModDataComponents.java`, `registry/ModCreativeTabs.java` (linha 92), `WirelessAutomate.java` (linha 70, junto do `StorageCapabilities::register`)
- Criar: `src/main/resources/data/wirelessautomate/loot_table/blocks/trash.json`; mudar `data/minecraft/tags/block/mineable/pickaxe.json` e os dois `lang`
- Criar, provisórios até a tarefa 5: `assets/wirelessautomate/blockstates/trash.json`, `models/block/trash.json`, `models/item/trash.json`

**Interfaces:**
- Consome: `TrashTypes` (tarefa 1).
- Produz: `ModBlocks.TRASH`, `ModItems.TRASH`, `ModBlockEntities.TRASH`, `ModDataComponents.TRASH_TYPES` (`Supplier<DataComponentType<Integer>>`); `TrashBlockEntity.types()`, `accepts(ResourceType)`, `toggle(ResourceType)`; `TrashHandlers.ITEMS`, `FLUIDS`, `ENERGY`, `BULK_ITEMS`, `BULK_FLUIDS`, `BULK_ENERGY`, `BULK_SOURCE`.

**Custo:** parado, zero (sem ticker). Por entrega, uma chamada a um singleton que devolve a quantidade pedida, sem alocação. Por clique na tela, `invalidateCapabilities` uma vez. Por Lixeira, um `int`. A medição é o bench da tarefa 6.

- [ ] **Passo 1: escrever os GameTests que falham** (`gametest/TrashGameTests.java`)

Use o ajudante `router(...)` do `gametest/StorageGameTests.java` (linhas 86 a 106) como modelo: ele põe um roteador em cima da máquina com `RouterBlock.FACING = UP`, chama `setNetworkId(network)`, `setNetworkId(type, network)` e `setMode(type, Direction.UP, modo)`. Copie-o para esta classe (é privado lá). A classe leva `@GameTestHolder(WirelessAutomate.MODID)` e `@PrefixGameTestTemplate(false)`.

Os cinco testes, todos com `template = "empty"` e `timeoutTicks = 200`:

1. `lixeiraEngoleItens`: um baú vanilla em `A` com 64 pedregulhos e um roteador em `PortMode.EXTRACT`; uma Lixeira em `B` com um roteador em `PortMode.INSERT`, os dois na mesma rede criada no teste. `thenWaitUntil` o baú ficar vazio.
2. `lixeiraNuncaDevolve`: com a Lixeira colocada, pegue as três capabilities pela face de cima (`level.getCapability(Capabilities.ItemHandler.BLOCK, pos, Direction.UP)` e as de fluido e energia). Confira que `extractItem(0, 64, true)` é vazio, que `drain(1000, SIMULATE)` é vazio e que `extractEnergy(1000, true)` é 0; e que `insertItem(0, pilha de 64, true)` devolve vazio, `fill(1000 mB de água, SIMULATE)` devolve 1000 e `receiveEnergy(1000, true)` devolve 1000.
3. `tipoDesligadoSomeEVolta`: chame `trash.toggle(ResourceType.ENERGY)`; a capability de energia passa a ser `null` e a de itens continua; `toggle` de novo e ela volta.
4. `lixeiraPerdePrioridadeParaDestinoDeVerdade`: a mesma origem de 64 pedregulhos, dois destinos na rede: um baú vanilla com prioridade maior e a Lixeira com a menor. No fim o baú de destino tem os 64.
5. `teclasVaoNoItem`: `trash.toggle(ResourceType.FLUID)`; monte o item com `trash.saveToItem(...)` do jeito que o `StorageBlockEntity` faz ao quebrar (procure `collectImplicitComponents` em `storage/`), e confira que o componente `ModDataComponents.TRASH_TYPES` vale `0b11101`; numa Lixeira intocada o item sai **sem** o componente.

- [ ] **Passo 2: rodar e ver falhar**

Rode: `./gradlew compileJava`
Esperado: falha, `ModBlocks.TRASH` e `TrashBlockEntity` não existem.

- [ ] **Passo 3: os handlers** (`trash/TrashHandlers.java`)

Sete singletons em `enum` de uma constante, cada um implementando uma interface. Aceitam tudo, não guardam nada, nada sai:

```java
package io.github.matheusanbs.wirelessautomate.trash;

import io.github.matheusanbs.wirelessautomate.storage.BulkEnergy;
import io.github.matheusanbs.wirelessautomate.storage.BulkFluids;
import io.github.matheusanbs.wirelessautomate.storage.BulkItems;
import io.github.matheusanbs.wirelessautomate.storage.BulkSource;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * Os handlers da Lixeira: aceitam a quantidade inteira pedida e descartam; nada sai. Não guardam estado, então há
 * um objeto por tipo para todas as Lixeiras e nenhuma chamada aloca.
 */
public final class TrashHandlers {
    public static final IItemHandler ITEMS = Items.INSTANCE;
    public static final IFluidHandler FLUIDS = Fluids.INSTANCE;
    public static final IEnergyStorage ENERGY = Energy.INSTANCE;
    public static final BulkItems BULK_ITEMS = BulkItemsVoid.INSTANCE;
    public static final BulkFluids BULK_FLUIDS = BulkFluidsVoid.INSTANCE;
    public static final BulkEnergy BULK_ENERGY = BulkEnergyVoid.INSTANCE;
    public static final BulkSource BULK_SOURCE = BulkSourceVoid.INSTANCE;

    private TrashHandlers() {
    }

    private enum Items implements IItemHandler {
        INSTANCE;

        @Override public int getSlots() { return 1; }
        @Override public ItemStack getStackInSlot(int slot) { return ItemStack.EMPTY; }
        @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) { return ItemStack.EMPTY; }
        @Override public ItemStack extractItem(int slot, int amount, boolean simulate) { return ItemStack.EMPTY; }
        @Override public int getSlotLimit(int slot) { return Integer.MAX_VALUE; }
        @Override public boolean isItemValid(int slot, ItemStack stack) { return true; }
    }

    private enum Fluids implements IFluidHandler {
        INSTANCE;

        @Override public int getTanks() { return 1; }
        @Override public FluidStack getFluidInTank(int tank) { return FluidStack.EMPTY; }
        @Override public int getTankCapacity(int tank) { return Integer.MAX_VALUE; }
        @Override public boolean isFluidValid(int tank, FluidStack stack) { return true; }
        @Override public int fill(FluidStack resource, FluidAction action) { return resource.getAmount(); }
        @Override public FluidStack drain(FluidStack resource, FluidAction action) { return FluidStack.EMPTY; }
        @Override public FluidStack drain(int maxDrain, FluidAction action) { return FluidStack.EMPTY; }
    }

    private enum Energy implements IEnergyStorage {
        INSTANCE;

        @Override public int receiveEnergy(int toReceive, boolean simulate) { return Math.max(0, toReceive); }
        @Override public int extractEnergy(int toExtract, boolean simulate) { return 0; }
        @Override public int getEnergyStored() { return 0; }
        @Override public int getMaxEnergyStored() { return Integer.MAX_VALUE; }
        @Override public boolean canExtract() { return false; }
        @Override public boolean canReceive() { return true; }
    }

    private enum BulkItemsVoid implements BulkItems {
        INSTANCE;

        @Override public long total() { return 0; }
        @Override public long capacity() { return 0; }
        @Override public int slots() { return 0; }
        @Override public ItemStack key(int index) { return ItemStack.EMPTY; }
        @Override public long count(int index) { return 0; }
        @Override public long count(ItemStack key) { return 0; }
        @Override public long countItem(ItemStack key) { return 0; }
        @Override public long insert(ItemStack key, long amount, boolean simulate) { return Math.max(0, amount); }
        @Override public long extract(ItemStack key, long amount, boolean simulate) { return 0; }
    }

    private enum BulkFluidsVoid implements BulkFluids {
        INSTANCE;

        @Override public long total() { return 0; }
        @Override public long capacity() { return 0; }
        @Override public int slots() { return 0; }
        @Override public FluidStack key(int index) { return FluidStack.EMPTY; }
        @Override public long count(int index) { return 0; }
        @Override public long count(FluidStack key) { return 0; }
        @Override public long countFluid(FluidStack key) { return 0; }
        @Override public long insert(FluidStack key, long amount, boolean simulate) { return Math.max(0, amount); }
        @Override public long extract(FluidStack key, long amount, boolean simulate) { return 0; }
    }

    private enum BulkEnergyVoid implements BulkEnergy {
        INSTANCE;

        @Override public long stored() { return 0; }
        @Override public long capacity() { return 0; }
        @Override public long insert(long amount, boolean simulate) { return Math.max(0, amount); }
        @Override public long extract(long amount, boolean simulate) { return 0; }
    }

    private enum BulkSourceVoid implements BulkSource {
        INSTANCE;

        @Override public long stored() { return 0; }
        @Override public long capacity() { return 0; }
        @Override public long insert(long amount, boolean simulate) { return Math.max(0, amount); }
        @Override public long extract(long amount, boolean simulate) { return 0; }
    }
}
```

Se o formatador do projeto recusar os métodos numa linha só, quebre-os no estilo do `bench/BenchCapabilities.java` (os `ItemVoid`, `FluidVoid` e `EnergyVoid` de lá, linhas 117, 197 e 273, têm os mesmos corpos). Confira no `network/` como o motor trata `capacity() <= 0` numa visão `Bulk*` (o Javadoc de `BulkItems.capacity` diz "sem limite"); se algum caminho ler `slots() == 0` como "cheio", o teste 1 pega.

- [ ] **Passo 4: a colisão** (`trash/TrashShapes.java`)

```java
package io.github.matheusanbs.wirelessautomate.trash;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** As caixas da Lixeira, abraçando o desenho: o corpo até a borda e os quatro pilares dos cantos. */
public final class TrashShapes {
    public static final VoxelShape SHAPE = Shapes.or(
            Block.box(0, 0, 0, 16, 13, 16),
            Block.box(0, 13, 0, 3, 16, 3),
            Block.box(13, 13, 0, 16, 16, 3),
            Block.box(0, 13, 13, 3, 16, 16),
            Block.box(13, 13, 13, 16, 16, 16));

    private TrashShapes() {
    }
}
```

- [ ] **Passo 5: o componente de dados** (`registry/ModDataComponents.java`, no padrão do `RESONATOR_ECO`, linha 169)

```java
    /** As teclas de tipo de uma Lixeira guardada no item (só quando alguma está desligada). */
    public static final Supplier<DataComponentType<Integer>> TRASH_TYPES =
            DATA_COMPONENTS.registerComponentType("trash_types", builder -> builder
                    .persistent(Codec.intRange(0, TrashTypes.ALL)).networkSynchronized(ByteBufCodecs.VAR_INT));
```

- [ ] **Passo 6: o block entity** (`trash/TrashBlockEntity.java`)

```java
package io.github.matheusanbs.wirelessautomate.trash;

import io.github.matheusanbs.wirelessautomate.network.LoadedTypes;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.registry.ModBlockEntities;
import io.github.matheusanbs.wirelessautomate.registry.ModDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A Lixeira: guarda só quais tipos de recurso aceita. Sem ticker; as capabilities são handlers sem estado
 * ({@link TrashHandlers}), oferecidos ou não conforme o bit do tipo.
 */
public class TrashBlockEntity extends BlockEntity {
    private static final String TYPES = "types";

    private int types = TrashTypes.ALL;

    public TrashBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TRASH.get(), pos, state);
    }

    public int types() {
        return types;
    }

    public boolean accepts(ResourceType type) {
        return TrashTypes.isOn(types, type.ordinal());
    }

    /** Liga ou desliga um tipo carregado; devolve se mudou. Quem está ligado na Lixeira refaz a capability sozinho. */
    public boolean toggle(ResourceType type) {
        if (!LoadedTypes.contains(type)) {
            return false;
        }
        types = TrashTypes.toggle(types, type.ordinal());
        if (level != null && !level.isClientSide) {
            level.blockEntityChanged(worldPosition);
            level.invalidateCapabilities(worldPosition);
        }
        return true;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (types != TrashTypes.ALL) {
            tag.putInt(TYPES, types);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        types = tag.contains(TYPES) ? TrashTypes.clean(tag.getInt(TYPES)) : TrashTypes.ALL;
    }

    @Override
    protected void applyImplicitComponents(DataComponentInput input) {
        super.applyImplicitComponents(input);
        types = TrashTypes.clean(input.getOrDefault(ModDataComponents.TRASH_TYPES.get(), TrashTypes.ALL));
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder builder) {
        super.collectImplicitComponents(builder);
        if (types != TrashTypes.ALL) {
            builder.set(ModDataComponents.TRASH_TYPES.get(), types);
        }
    }

    @Override
    public void removeComponentsFromTag(CompoundTag tag) {
        tag.remove(TYPES);
    }
}
```

- [ ] **Passo 7: o bloco** (`trash/TrashBlock.java`)

No molde do `repairer/RepairerBlock.java`, sem `FACING` nem `LIT`: `extends BaseEntityBlock`, `CODEC = simpleCodec(TrashBlock::new)`, `getRenderShape` devolve `RenderShape.MODEL`, `newBlockEntity` devolve `new TrashBlockEntity(pos, state)`, `getShape` devolve `TrashShapes.SHAPE`. O `useWithoutItem` abre a tela na tarefa 4; por enquanto devolve `InteractionResult.PASS`. Sem `getTicker`.

- [ ] **Passo 8: os registros**

- `ModBlocks`: `public static final DeferredBlock<TrashBlock> TRASH = BLOCKS.registerBlock("trash", TrashBlock::new, stone().pushReaction(PushReaction.BLOCK).noOcclusion());`
- `ModItems`: `public static final DeferredItem<BlockItem> TRASH = ITEMS.registerSimpleBlockItem(ModBlocks.TRASH);`
- `ModBlockEntities`: `TRASH`, no padrão do `CRYSTAL_REPAIRER` (linhas 44 a 46), com `TrashBlockEntity::new` e `ModBlocks.TRASH.get()`.
- `ModCreativeTabs`: `output.accept(ModItems.TRASH.get());` depois do Reparador.
- `WirelessAutomate`: `modEventBus.addListener(TrashCapabilities::register);` depois do `StorageCapabilities::register`.

- [ ] **Passo 9: as capabilities** (`trash/TrashCapabilities.java`)

```java
package io.github.matheusanbs.wirelessautomate.trash;

import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.registry.ModBlockEntities;
import io.github.matheusanbs.wirelessautomate.storage.BulkEnergy;
import io.github.matheusanbs.wirelessautomate.storage.BulkFluids;
import io.github.matheusanbs.wirelessautomate.storage.BulkItems;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/** As capabilities da Lixeira: cada uma só existe com o tipo ligado na tela. Químicos e Source entram na tarefa 3. */
public final class TrashCapabilities {
    public static void register(RegisterCapabilitiesEvent event) {
        var type = ModBlockEntities.TRASH.get();
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, type,
                (trash, side) -> trash.accepts(ResourceType.ITEM) ? TrashHandlers.ITEMS : null);
        event.registerBlockEntity(BulkItems.BLOCK, type,
                (trash, side) -> trash.accepts(ResourceType.ITEM) ? TrashHandlers.BULK_ITEMS : null);
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, type,
                (trash, side) -> trash.accepts(ResourceType.FLUID) ? TrashHandlers.FLUIDS : null);
        event.registerBlockEntity(BulkFluids.BLOCK, type,
                (trash, side) -> trash.accepts(ResourceType.FLUID) ? TrashHandlers.BULK_FLUIDS : null);
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, type,
                (trash, side) -> trash.accepts(ResourceType.ENERGY) ? TrashHandlers.ENERGY : null);
        event.registerBlockEntity(BulkEnergy.BLOCK, type,
                (trash, side) -> trash.accepts(ResourceType.ENERGY) ? TrashHandlers.BULK_ENERGY : null);
    }

    private TrashCapabilities() {
    }
}
```

- [ ] **Passo 10: os recursos**

- `loot_table/blocks/trash.json`: copie `loot_table/blocks/storage_battery.json` e troque o item por `wirelessautomate:trash` e o componente copiado por `wirelessautomate:trash_types`.
- `tags/block/mineable/pickaxe.json`: acrescente `wirelessautomate:trash`.
- `lang`: `block.wirelessautomate.trash` = "Trash Bin" / "Lixeira".
- Provisórios: `blockstates/trash.json` com uma variante `""` apontando `wirelessautomate:block/trash`; `models/block/trash.json` com `parent: "minecraft:block/cube_all"` e `all: "wirelessautomate:block/grafite"`; `models/item/trash.json` com `parent: "wirelessautomate:block/trash"`.

- [ ] **Passo 11: rodar e ver passar**

Rode: `./gradlew build runGameTestServer`
Esperado: `All N required tests passed`, com os cinco testes novos entre eles.

- [ ] **Passo 12: varreduras e commit**

Rode as três varreduras das Restrições globais nos arquivos mudados e resolva ou justifique cada candidato.

```bash
git add src/main/java/io/github/matheusanbs/wirelessautomate/trash src/main/java/io/github/matheusanbs/wirelessautomate/registry src/main/java/io/github/matheusanbs/wirelessautomate/WirelessAutomate.java src/main/java/io/github/matheusanbs/wirelessautomate/gametest/TrashGameTests.java src/main/resources
git commit -m "Lixeira: bloco, block entity e as capabilities de itens, fluidos e energia"
```

---

### Tarefa 3: químicos e Source

**Arquivos:**
- Criar: `compat/mekanism/MekanismTrash.java`, `compat/arsnouveau/ArsTrash.java`
- Mudar: `network/Chemicals.java` (junto do `registerStorage`, linha 90), `network/Sources.java` (linha 43), `trash/TrashCapabilities.java`
- Teste: um caso em `gametest/ChemicalGameTests.java` e um em `gametest/SourceGameTests.java`

**Interfaces:**
- Consome: `TrashBlockEntity.accepts(ResourceType)`, `TrashHandlers.BULK_SOURCE`, `ModBlockEntities.TRASH`.
- Produz: `Chemicals.registerTrash(RegisterCapabilitiesEvent)`, `Sources.registerTrash(RegisterCapabilitiesEvent)`.

**Custo:** igual ao da tarefa 2. Os handlers são singletons; a capability do Ars é em `int`, e a Lixeira aceita o valor inteiro pedido.

- [ ] **Passo 1: escrever os GameTests que falham**

Em `ChemicalGameTests` (run `runGameTestServerChemicals`): uma origem de químico do `ChemicalTestSupport`/`ChemicalTestTanks` com um roteador extraindo e uma Lixeira com um roteador inserindo, na mesma rede; espere a origem esvaziar. Depois `toggle(ResourceType.CHEMICAL)` e confira, pelo `ChemicalTestSupport`, que a Lixeira não oferece mais o handler. Os tipos do Mekanism ficam dentro do `ChemicalTestSupport`: acrescente lá um método `boolean hasChemicalHandler(Level, BlockPos)` e use-o no teste.

Em `SourceGameTests` (run `runGameTestServerSource`): o mesmo, com uma origem de Source do `SourceTestSupport` e `toggle(ResourceType.SOURCE)`.

- [ ] **Passo 2: rodar e ver falhar**

Rode: `./gradlew runGameTestServerChemicals`
Esperado: o teste novo falha (a origem não esvazia: a Lixeira não tem a capability).

- [ ] **Passo 3: o handler de químico** (`compat/mekanism/MekanismTrash.java`)

```java
package io.github.matheusanbs.wirelessautomate.compat.mekanism;

import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.registry.ModBlockEntities;
import mekanism.api.Action;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.IChemicalHandler;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/** A Lixeira para o Mekanism: aceita qualquer químico e descarta. Só é carregada com o Mekanism presente. */
public final class MekanismTrash {
    public static void register(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(MekanismChemicals.BLOCK, ModBlockEntities.TRASH.get(),
                (trash, side) -> trash.accepts(ResourceType.CHEMICAL) ? Void.INSTANCE : null);
    }

    private MekanismTrash() {
    }

    private enum Void implements IChemicalHandler {
        INSTANCE;

        @Override
        public int getChemicalTanks() {
            return 1;
        }

        @Override
        public ChemicalStack getChemicalInTank(int tank) {
            return ChemicalStack.EMPTY;
        }

        @Override
        public void setChemicalInTank(int tank, ChemicalStack stack) {
        }

        @Override
        public long getChemicalTankCapacity(int tank) {
            return Long.MAX_VALUE;
        }

        @Override
        public boolean isValid(int tank, ChemicalStack stack) {
            return true;
        }

        @Override
        public ChemicalStack insertChemical(int tank, ChemicalStack stack, Action action) {
            return ChemicalStack.EMPTY;
        }

        @Override
        public ChemicalStack extractChemical(int tank, long amount, Action action) {
            return ChemicalStack.EMPTY;
        }
    }
}
```

São os mesmos sete métodos que o `MekanismStorage.Handler` implementa (linha 93 de `compat/mekanism/MekanismStorage.java`); se a interface pedir mais algum, siga o que aquele handler faz.

- [ ] **Passo 4: a capability de Source** (`compat/arsnouveau/ArsTrash.java`)

Um `enum Void implements ISourceCap` de uma constante, com os onze métodos que o `ArsStorage.TankCap` implementa: `canAcceptSource` devolve `true`; `canProvideSource` devolve `false`; `getMaxExtract` devolve 0; `getMaxReceive`, `getSourceCapacity` e `getMaxSource` devolvem `Integer.MAX_VALUE`; `getSource` devolve 0; `setSource` e `setMaxSource` não fazem nada; `receiveSource(source, simulate)` devolve `Math.max(0, source)`; `extractSource` devolve 0. O `register` registra `ArsSources.BLOCK` para `ModBlockEntities.TRASH.get()` com `trash.accepts(ResourceType.SOURCE) ? Void.INSTANCE : null`. A Lixeira **não** entra no `SourceManager` do Ars (o provider é para quem guarda Source).

- [ ] **Passo 5: as pontes**

Em `network/Chemicals.java`, depois do `registerStorage`:

```java
    /** Registra a capability de químico da Lixeira; sem o Mekanism, nada. */
    public static void registerTrash(RegisterCapabilitiesEvent event) {
        if (LOADED) {
            MekanismTrash.register(event);
        }
    }
```

Em `network/Sources.java`, o mesmo com `ArsTrash.register(event)`. Em `trash/TrashCapabilities.register`, no fim:

```java
        event.registerBlockEntity(BulkSource.BLOCK, type,
                (trash, side) -> trash.accepts(ResourceType.SOURCE) ? TrashHandlers.BULK_SOURCE : null);
        Chemicals.registerTrash(event);
        Sources.registerTrash(event);
```

- [ ] **Passo 6: rodar e ver passar**

Rode: `./gradlew build runGameTestServer runGameTestServerChemicals runGameTestServerSource`
Esperado: as três passam. A primeira prova que o mod carrega sem o Mekanism e sem o Ars.

- [ ] **Passo 7: varreduras e commit**

```bash
git add src/main/java/io/github/matheusanbs/wirelessautomate/compat src/main/java/io/github/matheusanbs/wirelessautomate/network/Chemicals.java src/main/java/io/github/matheusanbs/wirelessautomate/network/Sources.java src/main/java/io/github/matheusanbs/wirelessautomate/trash/TrashCapabilities.java src/main/java/io/github/matheusanbs/wirelessautomate/gametest
git commit -m "Lixeira: químicos do Mekanism e Source do Ars Nouveau"
```

---

### Tarefa 4: menu e tela

**Arquivos:**
- Criar: `menu/TrashMenu.java`, `client/trash/TrashKeysLayout.java`, `client/trash/TrashScreen.java`, `gametest/TrashMenuGameTests.java`, `client/dev/E2eTrashSteps.java`
- Teste: `src/test/java/io/github/matheusanbs/wirelessautomate/client/trash/TrashKeysLayoutTest.java`
- Mudar: `registry/ModMenus.java` (junto do `CRYSTAL_REPAIRER`, linha 47), `client/ClientSetup.java` (`registerScreens`, linha 113), `trash/TrashBlock.java` (`useWithoutItem`), os dois `lang`, o registro das suítes do e2e

**Interfaces:**
- Consome: `TrashBlockEntity.types()` e `toggle(ResourceType)`, `TrashTypes`, `LoadedTypes.LIST`, `ResourceStyle.drawIcon`/`color`/`name`.
- Produz: `ModMenus.TRASH`, `TrashMenu.open(ServerPlayer, TrashBlockEntity)`, `TrashMenu.types()`, `TrashKeysLayout.keyX(int index, int count, int panelWidth)`.

**Custo:** com a tela fechada, zero. Com ela aberta, o vanilla manda o `DataSlot` só quando a máscara muda (um `short`). Por quadro, cinco teclas desenhadas, sem medir texto nem montar lista: os nomes curtos e as posições são calculados no `init`. A medição é o e2e (`GuiText.clipCount()==0`).

- [ ] **Passo 1: o teste do layout que falha** (`TrashKeysLayoutTest.java`)

```java
package io.github.matheusanbs.wirelessautomate.client.trash;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TrashKeysLayoutTest {
    @Test
    void cincoTeclasCentradasNoPainel() {
        int total = 5 * TrashKeysLayout.KEY + 4 * TrashKeysLayout.GAP;
        int primeira = (TrashKeysLayout.PANEL_W - total) / 2;
        assertEquals(primeira, TrashKeysLayout.keyX(0, 5, TrashKeysLayout.PANEL_W));
        assertEquals(primeira + 4 * (TrashKeysLayout.KEY + TrashKeysLayout.GAP),
                TrashKeysLayout.keyX(4, 5, TrashKeysLayout.PANEL_W));
    }

    @Test
    void tresTeclasTambemFicamCentradas() {
        int total = 3 * TrashKeysLayout.KEY + 2 * TrashKeysLayout.GAP;
        assertEquals((TrashKeysLayout.PANEL_W - total) / 2, TrashKeysLayout.keyX(0, 3, TrashKeysLayout.PANEL_W));
    }

    @Test
    void achaATeclaPeloMouse() {
        int x0 = TrashKeysLayout.keyX(1, 5, TrashKeysLayout.PANEL_W);
        assertEquals(1, TrashKeysLayout.keyAt(x0 + 2, TrashKeysLayout.KEYS_Y + 2, 5, TrashKeysLayout.PANEL_W));
        assertEquals(-1, TrashKeysLayout.keyAt(x0 - 1, TrashKeysLayout.KEYS_Y + 2, 5, TrashKeysLayout.PANEL_W));
        assertEquals(-1, TrashKeysLayout.keyAt(x0 + 2, TrashKeysLayout.KEYS_Y - 1, 5, TrashKeysLayout.PANEL_W));
    }
}
```

- [ ] **Passo 2: rodar e ver falhar**

Rode: `./gradlew test --tests '*TrashKeysLayoutTest'`
Esperado: falha de compilação.

- [ ] **Passo 3: o layout** (`client/trash/TrashKeysLayout.java`)

```java
package io.github.matheusanbs.wirelessautomate.client.trash;

/** Onde ficam as teclas da tela da Lixeira, em pixels do painel. Lógica pura, com JUnit. */
public final class TrashKeysLayout {
    /** Largura e altura do painel. */
    public static final int PANEL_W = 150;
    public static final int PANEL_H = 78;
    /** Largura e altura de uma tecla, e o vão entre duas. */
    public static final int KEY = 24;
    public static final int KEY_H = 34;
    public static final int GAP = 4;
    /** O topo da fileira de teclas, abaixo do cabeçalho. */
    public static final int KEYS_Y = 22;

    private TrashKeysLayout() {
    }

    /** O x da tecla {@code index} de {@code count}, com a fileira centrada no painel. */
    public static int keyX(int index, int count, int panelWidth) {
        int total = count * KEY + (count - 1) * GAP;
        return (panelWidth - total) / 2 + index * (KEY + GAP);
    }

    /** A tecla sob o ponto (em pixels do painel), ou -1. */
    public static int keyAt(int x, int y, int count, int panelWidth) {
        if (y < KEYS_Y || y >= KEYS_Y + KEY_H) {
            return -1;
        }
        for (int i = 0; i < count; i++) {
            int kx = keyX(i, count, panelWidth);
            if (x >= kx && x < kx + KEY) {
                return i;
            }
        }
        return -1;
    }
}
```

Rode de novo: `./gradlew test --tests '*TrashKeysLayoutTest'`. Esperado: PASS.

- [ ] **Passo 4: o GameTest do menu que falha** (`gametest/TrashMenuGameTests.java`)

Abra o menu do mesmo jeito que o `gametest/RepairerMenuGameTests.java` abre o do Reparador (mesmo jogador de teste, só pelo `TestPlayers`). Dois testes, `timeoutTicks = 100`:

1. `teclaDesligaEVolta`: com o menu aberto, `menu.clickMenuButton(player, ResourceType.ENERGY.ordinal())` devolve `true`, `trash.accepts(ResourceType.ENERGY)` vira `false` e, depois de `menu.broadcastChanges()`, `menu.types()` vale `0b11011`. Um segundo clique volta a 31.
2. `botaoInvalidoNaoMuda`: `clickMenuButton(player, 9)` e `clickMenuButton(player, -1)` devolvem `false` e a máscara não muda; um tipo de mod ausente (`ResourceType.CHEMICAL` na run sem o Mekanism) também devolve `false`.

- [ ] **Passo 5: o menu** (`menu/TrashMenu.java`)

```java
package io.github.matheusanbs.wirelessautomate.menu;

import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.registry.ModMenus;
import io.github.matheusanbs.wirelessautomate.trash.TrashBlockEntity;
import io.github.matheusanbs.wirelessautomate.trash.TrashTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * O menu da Lixeira: sem slots. A máscara de tipos vai num {@link DataSlot} (o vanilla só a manda com a tela aberta
 * e quando muda) e o clique numa tecla volta pelo {@link #clickMenuButton}, com o {@code ordinal} do tipo.
 */
public class TrashMenu extends AbstractContainerMenu {
    private static final ResourceType[] TYPES = ResourceType.values();

    private final BlockPos pos;
    private final @Nullable TrashBlockEntity trash;
    private final DataSlot types = DataSlot.standalone();

    public TrashMenu(int containerId, Inventory inventory, TrashBlockEntity trash) {
        this(containerId, trash.getBlockPos(), trash, trash.types());
    }

    public TrashMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(containerId, BlockPos.STREAM_CODEC.decode(buf), null, TrashTypes.clean(buf.readVarInt()));
    }

    private TrashMenu(int containerId, BlockPos pos, @Nullable TrashBlockEntity trash, int mask) {
        super(ModMenus.TRASH.get(), containerId);
        this.pos = pos;
        this.trash = trash;
        types.set(mask);
        addDataSlot(types);
    }

    public static void open(ServerPlayer player, TrashBlockEntity trash) {
        player.openMenu(new SimpleMenuProvider(
                        (containerId, inventory, p) -> new TrashMenu(containerId, inventory, trash),
                        Component.translatable("container.wirelessautomate.trash")),
                buf -> {
                    BlockPos.STREAM_CODEC.encode(buf, trash.getBlockPos());
                    buf.writeVarInt(trash.types());
                });
    }

    public BlockPos pos() {
        return pos;
    }

    /** A máscara de tipos: no servidor a do bloco, no cliente a última recebida. */
    public int types() {
        return types.get();
    }

    @Override
    public void broadcastChanges() {
        if (trash != null) {
            types.set(trash.types());
        }
        super.broadcastChanges();
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        return trash != null && id >= 0 && id < TYPES.length && trash.toggle(TYPES[id]);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return trash == null || Container.stillValidBlockEntity(trash, player);
    }
}
```

`ModMenus`: `TRASH`, no padrão do `CRYSTAL_REPAIRER` (linha 47), com `IMenuTypeExtension.create(TrashMenu::new)`. `TrashBlock.useWithoutItem`: como o do `RepairerBlock`, chamando `TrashMenu.open(serverPlayer, trash)`.

- [ ] **Passo 6: a tela** (`client/trash/TrashScreen.java`)

`extends AbstractContainerScreen<TrashMenu>`, no molde do `client/storage/StorageScalarScreen.java` (188 linhas): `imageWidth = TrashKeysLayout.PANEL_W`, `imageHeight = TrashKeysLayout.PANEL_H`; `render` começa com `GuiText.beginFrame()`; `renderLabels` vazio; tudo desenhado em `renderBg`.

- **No `init`:** guarde `ResourceType[] shown` com os tipos de `LoadedTypes.LIST`, os nomes curtos (`Component.translatable("gui.wirelessautomate.trash.short." + type.key())`) e a máscara dos tipos disponíveis. Nada disso é refeito por quadro.
- **Painel e cabeçalho:** `GuiPaint.panel(g, x, y, W, H, GuiPaint.tierColor(RouterTier.ULTIMATE))`, o título com `GuiText.draw` e o botão de tema no canto, do mesmo jeito que a `StorageScalarScreen` (linhas 113 a 128).
- **Cada tecla** (`TrashKeysLayout.keyX(i, shown.length, W)`, `KEYS_Y`, `KEY` × `KEY_H`):
  - ligada: `GuiPaint.box` com o fundo de tecla e a borda `GuiPaint.LINE`; o crachá com `ResourceStyle.drawIcon(g, type, ...)`; o nome curto centrado com `GuiText.draw`; uma barrinha de 14 × 2 na `ResourceStyle.color(type)`.
  - desligada: fundo `GuiPaint.INSET`, a borda **tracejada** (segmentos de 2 px com 2 px de vão, em `GuiPaint.MUTED`), o crachá e o texto em `GuiPaint.MUTED`, a barrinha em `GuiPaint.MUTED`. Se o `GuiPaint` já tem a tecla "Nenhum" das portas do roteador (a tecla apagada com borda tracejada da identidade), reuse o método dela em vez de desenhar à mão.
  - com o mouse em cima: a borda em coral (a mesma cor de foco dos botões do mod) e a dica com o nome inteiro, `ResourceStyle.name(type)`, mais "aceita" ou "recusa".
- **Rodapé:** uma linha `GuiPaint.LINE`, "Aceitando" à esquerda e, à direita, "tudo", "nada" ou "N de M" (`TrashTypes.count(menu.types(), disponíveis)` de `shown.length`).
- **Clique** (`mouseClicked`): `int i = TrashKeysLayout.keyAt(mouseX - leftPos, mouseY - topPos, shown.length, W)`; se `i >= 0`, toque o som de clique de botão e chame `minecraft.gameMode.handleInventoryButtonClick(menu.containerId, shown[i].ordinal())`. A tela **não** muda a máscara localmente: redesenha quando o `DataSlot` chegar.
- Chaves de tradução novas, nos dois idiomas: `container.wirelessautomate.trash` ("Trash Bin" / "Lixeira"), `gui.wirelessautomate.trash.accepting` ("Accepting" / "Aceitando"), `.all` ("all" / "tudo"), `.none` ("nothing" / "nada"), `.some` ("%s of %s" / "%s de %s"), `.on` ("accepts" / "aceita"), `.off` ("refuses" / "recusa"), e `gui.wirelessautomate.trash.short.item|fluid|energy|chemical|source` ("Items", "Fluid", "Energy", "Chem", "Source" / "Itens", "Fluid", "Energ", "Quím", "Source").

`ClientSetup.registerScreens`: `event.register(ModMenus.TRASH.get(), TrashScreen::new);`.

- [ ] **Passo 7: o e2e** (`client/dev/E2eTrashSteps.java`)

Uma suíte `lixeira`, no molde dos passos de armazenamento (`client/dev/E2eStorageSteps.java`) e registrada onde as outras suítes são: colocar a Lixeira, abrir a tela, capturar `lixeira-tela`; clicar na tecla de energia, esperar a máscara voltar do servidor, conferir que a tecla ficou apagada e capturar `lixeira-tela-sem-energia`; clicar de novo e conferir que voltou; `GuiText.clipCount() == 0` em todas. Repita a captura no tema escuro. Veja `docs/e2e.md` para registrar a suíte.

- [ ] **Passo 8: rodar e ver passar**

Rode: `./gradlew build runGameTestServer runGameTestServerChemicals runGameTestServerSource runGameTestServerAllthemodium runGameTestServerTeams`
Depois: `./scripts/e2e.sh --only lixeira --capture lixeira --variants escuro`
Esperado: tudo passa e o `run/e2e/result.txt` diz `OK`. **Abra as capturas** e compare com o layout B de `docs/preview/lixeira-tela.html`: tecla apagada legível sem depender da cor, nenhum texto cortado, a fileira centrada com três e com cinco tipos.

- [ ] **Passo 9: varreduras e commit**

```bash
git add src/main/java/io/github/matheusanbs/wirelessautomate/menu/TrashMenu.java src/main/java/io/github/matheusanbs/wirelessautomate/client/trash src/main/java/io/github/matheusanbs/wirelessautomate/client/dev src/main/java/io/github/matheusanbs/wirelessautomate/client/ClientSetup.java src/main/java/io/github/matheusanbs/wirelessautomate/registry/ModMenus.java src/main/java/io/github/matheusanbs/wirelessautomate/trash/TrashBlock.java src/main/java/io/github/matheusanbs/wirelessautomate/gametest/TrashMenuGameTests.java src/test src/main/resources/assets/wirelessautomate/lang
git commit -m "Lixeira: a tela de teclas por tipo"
```

---

### Tarefa 5: o modelo estático e a textura animada

**Arquivos:**
- Criar: `scripts/textures/lixeira_modelo.py`
- Gerar (pelo script): `assets/wirelessautomate/models/block/trash.json`, `textures/block/trash.png`, `textures/block/trash.png.mcmeta`
- Mudar: `assets/wirelessautomate/blockstates/trash.json` e `models/item/trash.json` (deixam de ser provisórios), `docs/mapa/scripts-e-recursos.md`
- Teste: um caso novo em `gametest/TrashGameTests.java`

**Interfaces:**
- Consome: `docs/preview/lixeira.bbmodel` (o grupo `base`), `scripts/textures/bbmodel_json.py`, `TrashShapes.SHAPE`.

**Custo:** nenhum no servidor. No cliente, o bloco é modelo estático na malha do chunk; a animação é do atlas de texturas do jogo (`.mcmeta`), sem block entity renderer nesta etapa.

- [ ] **Passo 1: o GameTest da colisão que falha** (em `TrashGameTests`)

`colisaoSegueODesenho`: a forma de colisão do estado padrão não é vazia, fica dentro do bloco, encosta no chão (`minY == 0`), e um ponto no centro do topo, `(0.5, 15/16.0, 0.5)`, **não** está dentro dela (a abertura), enquanto o canto `(1/16.0, 15/16.0, 1/16.0)` está (o pilar). Use `shape.toAabbs()` para testar o ponto. Este teste já passa com a tarefa 2; ele entra aqui para travar a forma junto do modelo.

- [ ] **Passo 2: o conversor** (`scripts/textures/lixeira_modelo.py`)

No molde do `scripts/textures/reparador_modelo.py`, que usa o `bbmodel_json.py`:

1. Leia `docs/preview/lixeira.bbmodel`. Ele está no formato genérico, com o bloco centrado em x e z: some 8 a x e z de cada caixa. Converta **só as caixas do grupo `base`** (38 caixas); as dos grupos `anel` e `nucleo` ficam para o renderer da etapa 2.
2. As caixas das escotilhas e dos parafusos passam até 0,4 de 0 a 16. O JSON de bloco aceita de -16 a 32, então elas entram como estão; confira no jogo que não há face piscando contra um bloco vizinho.
3. A chave da textura nas faces é `#trash`; a partícula também.
4. As faces do circuito (o painel escuro das quatro paredes) e a do vórtice (a face de cima da caixa `vazio`) levam o `neoforge_data` de luz cheia, como as luzes do Reparador.
5. **A textura animada:** o atlas do `.bbmodel` tem 64 × 64. Gere `trash.png` com **12 quadros empilhados na vertical** (64 × 768) e o `trash.png.mcmeta` com `{"animation": {"frametime": 5, "interpolate": true}}` (12 quadros × 5 ticks = 3 s, o ciclo da spec). Em cada quadro `f`, com a fase `ph = f / 12`, repinte só duas regiões:
   - **o circuito**, nos pixels de traço da região da lateral (origem 0,0). As linhas são as de y 5 a 11, e as colunas de traço em cada uma: y5: 4, 10; y6: 2, 3, 4, 5, 7, 8, 9, 10, 12, 13; y7 e y8: 2, 13; y9: 2, 3, 12, 13; y10: 2, 4, 5, 6, 8, 9, 10, 11, 13; y11: 4, 11. A cor de cada pixel é `w = 0.5 + 0.5 * sin(2π * (ph - (x + y) / 22))`: de `w` 0 a 0,5 mistura a sombra `#4A2C92` com a lente `#7B4FE0`; de 0,5 a 1, a lente com o brilho `#C7B0FA`.
   - **o vórtice**, nos 12 × 12 pixels a partir de (32, 0). Para cada pixel, `dx = x - 5.5`, `dy = y - 5.5`, `r = hypot(dx, dy)`, `a = atan2(dy, dx)`, `v = sin(3a + 0.9r - 2π * ph)`. Se `r < 1.3`, grafite `#0F0E12`; senão, se `v > 0.45`, o brilho quando `r < 3.6` e a lente caso contrário; senão, se `v > -0.1`, a sombra quando `r < 5` e `#2A1A55` caso contrário; senão, grafite.
6. Como a textura tem 12 quadros, o `uv` das faces continua em relação a **um** quadro de 64 × 64 (o jogo trata a tira como animação).
7. Grave também o `blockstates/trash.json` (uma variante só) e o `models/item/trash.json` com `parent: "wirelessautomate:block/trash"` e a vista `gui` do Baú.

Rode: `python -I scripts/textures/lixeira_modelo.py`
Esperado: os três arquivos gerados, sem aviso do `bbmodel_json`.

- [ ] **Passo 3: conferir no jogo**

Rode: `./gradlew build runGameTestServer`, depois `./scripts/e2e.sh --only lixeira --capture lixeira`.
Acrescente à suíte `lixeira` duas capturas do bloco no mundo (de lado e de cima). **Abra as capturas** e compare com `docs/preview/lixeira-idle.gif` e `lixeira-engolir.gif`: casco de porcelana, linha coral, circuito violeta aceso no escuro, vórtice no fundo. Sem o anel e o cristal, que são da etapa 2. Preto numa face é `uv` fora da textura.

- [ ] **Passo 4: varreduras e commit**

```bash
git add scripts/textures/lixeira_modelo.py src/main/resources/assets/wirelessautomate src/main/java/io/github/matheusanbs/wirelessautomate/gametest/TrashGameTests.java src/main/java/io/github/matheusanbs/wirelessautomate/client/dev docs/mapa/scripts-e-recursos.md
git commit -m "Lixeira: modelo estático e a textura animada do circuito e do vórtice"
```

---

### Tarefa 6: guia, JEI, bench, docs e fechamento

**Arquivos:**
- Criar: a página da Lixeira em `scripts/guide/paginas/` (e a entrada na lista `PAGINAS` do `scripts/guide/gerar_guia.py`)
- Mudar: `compat/jei/` (a informação do item), `bench/` (um cenário de transbordo), `docs/benchmark.md`, `docs/mapa/` (um arquivo novo `lixeira.md` e a linha no índice do `CLAUDE.md`), `docs/mapa/testes.md`, `docs/mapa/logica-pura.md`, `docs/progresso/` (`README.md`, `estado.md`, `pendencias.md`, o histórico do mês), `docs/superpowers/specs/2026-11-10-lixeira-design.md`

**Custo:** a tarefa mede o que as outras afirmaram.

- [ ] **Passo 1: o bench do transbordo**

Siga `.claude/skills/wa-performance/references/medir.md`. Acrescente ao `/wa bench` um cenário `transbordo`: N origens de itens sempre cheias, um destino de verdade com prioridade maior e uma Lixeira com a menor, na mesma rede. Meça antes (sem a Lixeira, com um sumidouro `ItemVoid` do bench no lugar) e depois. O que tem de aparecer: a Lixeira não custa mais que o sumidouro do bench por entrega, e a rede sem nada a mover custa zero. Ponha a tabela em `docs/benchmark.md`. Regressão acima do ruído: pare e investigue antes de seguir.

- [ ] **Passo 2: a página do guia**

Nos dois idiomas, só "como operar", com os passos primeiro: colocar, ligar a face do roteador na rede em modo de inserção, **pôr a prioridade mais baixa** (senão ela disputa com os destinos de verdade), abrir a tela e desligar o que não deve ser jogado fora. Uma frase dizendo que nada volta. Rode o gerador do guia.

- [ ] **Passo 3: o JEI**

Uma página de informação para o item `trash`, no padrão das informações que o plugin já registra em `compat/jei/`, com duas frases: aceita os cinco recursos e some com eles; a tela liga e desliga cada tipo. Sem receita.

- [ ] **Passo 4: as docs**

- `docs/mapa/lixeira.md`: uma linha por classe nova (as do mapa de arquivos deste plano), e a linha da área na tabela do `CLAUDE.md`.
- `docs/mapa/logica-pura.md`: `TrashTypes` e `TrashKeysLayout`. `docs/mapa/testes.md`: `TrashGameTests`, `TrashMenuGameTests` e os casos de químico e Source.
- A spec: na seção "Custo de performance", troque a linha "Protocolo" por: "a tela não cria payload: a máscara vai num `DataSlot` do menu e o clique volta pelo `clickMenuButton`; `ModPayloads.VERSION` não muda." Na seção "A tela", o mesmo ajuste onde ela fala em pedido ao servidor.
- `docs/progresso/`: a linha no histórico (com os números dos GameTests e "e2e OK"), a Lixeira em `estado.md` como "etapa 1 pronta, sem receita e sem a animação do anel e do cristal", e em `pendencias.md`: a receita (balanceamento) e a etapa 2.

- [ ] **Passo 5: a verificação inteira**

```bash
./gradlew build runGameTestServer runGameTestServerChemicals runGameTestServerSource runGameTestServerAllthemodium runGameTestServerTeams
./scripts/e2e.sh --only lixeira --capture lixeira --variants pt,escuro
python -I .claude/skills/wa-performance/scripts/varrer_perf.py
python -I .claude/skills/wa-comentarios/scripts/varrer.py
python -I .claude/skills/wa-modular/scripts/medir.py
```

Esperado: tudo passa, `run/e2e/result.txt` diz `OK`, e cada candidato das varreduras nos arquivos mudados está resolvido ou justificado no relatório.

- [ ] **Passo 6: commit**

```bash
git add scripts/guide src/main/java/io/github/matheusanbs/wirelessautomate/compat/jei src/main/java/io/github/matheusanbs/wirelessautomate/bench src/main/resources docs CLAUDE.md
git commit -m "Lixeira: guia, JEI, bench do transbordo e docs da etapa 1"
```

---

## O que fica fora desta etapa

- **A receita:** a Lixeira só existe na aba criativa até o balanceamento das receitas.
- **O anel, o cristal e o laço engolindo** (etapa 2, com plano próprio): o renderer, o aviso de "está descartando" ao cliente e o e2e das capturas da animação. Nesta etapa o bloco já tem o circuito e o vórtice animados, mas não o miolo flutuante.
- **A Lixeira no Tablet**, o filtro por tipo e o contador: fora de escopo na spec.
