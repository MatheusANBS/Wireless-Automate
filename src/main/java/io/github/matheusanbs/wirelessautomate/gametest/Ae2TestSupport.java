package io.github.matheusanbs.wirelessautomate.gametest;

import appeng.api.AECapabilities;
import appeng.api.behaviors.GenericInternalInventory;
import appeng.api.config.Actionable;
import appeng.api.config.LockCraftingMode;
import appeng.api.config.Settings;
import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.implementations.blockentities.ICraftingMachine;
import appeng.api.implementations.blockentities.PatternContainerGroup;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.util.IConfigurableObject;
import io.github.matheusanbs.wirelessautomate.compat.ae2.Ae2Chemicals;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import java.util.List;
import java.util.concurrent.Future;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * A parte dos testes do AE2 que usa a API dele. Não é {@code @GameTestHolder} nem {@code @EventBusSubscriber}: o
 * NeoForge inspeciona essas classes por reflexão, e um tipo do AE2 numa assinatura impede o mod de carregar sem ele.
 * Só é chamada por {@link Ae2DockGameTests} e {@link DockChemicalAe2GameTests} com o AE2 presente.
 *
 * <p>O que o jar da API não tem (a lógica do Pattern Provider: o inventário de padrões) é lido por
 * reflexão, só aqui, para a compilação não depender do jar inteiro do AE2.
 */
final class Ae2TestSupport {
    private Ae2TestSupport() {
    }

    private static @Nullable ICraftingMachine machine(ServerLevel level, BlockPos pos, Direction face) {
        return level.getCapability(AECapabilities.CRAFTING_MACHINE, pos, face);
    }

    static boolean hasMachine(ServerLevel level, BlockPos pos, Direction face) {
        return machine(level, pos, face) != null;
    }

    static boolean acceptsPlans(ServerLevel level, BlockPos pos, Direction face) {
        ICraftingMachine machine = machine(level, pos, face);
        return machine != null && machine.acceptsPlans();
    }

    /** O nome do grupo no Pattern Access Terminal. */
    static Component groupName(ServerLevel level, BlockPos pos, Direction face) {
        return machine(level, pos, face).getCraftingMachineInfo().name();
    }

    /** O ícone do grupo no Pattern Access Terminal, ou {@code Items.AIR}. */
    static Item groupIcon(ServerLevel level, BlockPos pos, Direction face) {
        PatternContainerGroup group = machine(level, pos, face).getCraftingMachineInfo();
        return group.icon() == null ? Items.AIR : group.icon().getItem();
    }

    /** Um padrão de processamento: {@code inputs} (item e quantidade, alternados) dá {@code out}. */
    static ItemStack processingPattern(Item out, int outCount, Object... inputs) {
        GenericStack[] stacks = new GenericStack[inputs.length / 2];
        for (int i = 0; i < stacks.length; i++) {
            stacks[i] = new GenericStack(AEItemKey.of((Item) inputs[2 * i]), (Integer) inputs[2 * i + 1]);
        }
        return PatternDetailsHelper.encodeProcessingPattern(List.of(stacks),
                List.of(new GenericStack(AEItemKey.of(out), outCount)));
    }

    /** Um padrão de processamento com uma entrada de item e uma de fluido ({@code mb}) que dá um {@code out}. */
    static ItemStack itemAndFluidPattern(Item out, Item item, int count, Fluid fluid, int mb) {
        return PatternDetailsHelper.encodeProcessingPattern(
                List.of(new GenericStack(AEItemKey.of(item), count), new GenericStack(AEFluidKey.of(fluid), mb)),
                List.of(new GenericStack(AEItemKey.of(out), 1)));
    }

    /**
     * A chave do AE2 do químico {@code chemical} (um {@code ChemicalStack} como {@code Object}, do
     * {@link ChemicalTestSupport}): a {@code MekanismKey} do Applied Mekanistics, como {@code Object}.
     */
    static Object chemicalKey(Object chemical) {
        AEKey key = Ae2Chemicals.key(chemical, null);
        if (key == null) {
            throw new IllegalStateException("sem a chave de químico do Applied Mekanistics");
        }
        return key;
    }

    /**
     * Um padrão de processamento com uma entrada de item e uma de químico ({@code in}, uma chave de
     * {@link #chemicalKey}, {@code inMb}) que dá o químico {@code out} ({@code outMb}).
     */
    static ItemStack itemAndChemicalPattern(Item item, int count, Object in, long inMb, Object out, long outMb) {
        return PatternDetailsHelper.encodeProcessingPattern(
                List.of(new GenericStack(AEItemKey.of(item), count), new GenericStack((AEKey) in, inMb)),
                List.of(new GenericStack((AEKey) out, outMb)));
    }

    /** Um padrão de bancada (um tronco de carvalho dá quatro tábuas), que só o Molecular Assembler aceita. */
    @SuppressWarnings("unchecked")
    static ItemStack craftingPattern(ServerLevel level) {
        RecipeHolder<?> holder = level.getRecipeManager()
                .byKey(ResourceLocation.withDefaultNamespace("oak_planks")).orElseThrow();
        ItemStack[] in = new ItemStack[9];
        java.util.Arrays.fill(in, ItemStack.EMPTY);
        in[0] = new ItemStack(Items.OAK_LOG);
        return PatternDetailsHelper.encodeCraftingPattern((RecipeHolder<CraftingRecipe>) holder, in,
                new ItemStack(Items.OAK_PLANKS, 4), false, false);
    }

    /**
     * O que o provedor faz ao empurrar uma execução do padrão: monta as entradas e chama o {@code pushPattern} da face.
     * Em {@code remaining[0]}, a soma do que ficou nas entradas depois (a Doca não tira nada delas).
     */
    static boolean push(ServerLevel level, BlockPos pos, Direction face, ItemStack pattern, long[] remaining) {
        IPatternDetails details = PatternDetailsHelper.decodePattern(pattern, level);
        IPatternDetails.IInput[] patternInputs = details.getInputs();
        KeyCounter[] inputs = new KeyCounter[patternInputs.length];
        for (int i = 0; i < inputs.length; i++) {
            GenericStack stack = patternInputs[i].getPossibleInputs()[0];
            inputs[i] = new KeyCounter();
            inputs[i].add(stack.what(), stack.amount() * patternInputs[i].getMultiplier());
        }
        boolean pushed = machine(level, pos, face).pushPattern(details, inputs, face);
        long total = 0;
        for (KeyCounter counter : inputs) {
            for (Object2LongMap.Entry<?> entry : counter) {
                total += entry.getLongValue();
            }
        }
        remaining[0] = total;
        return pushed;
    }

    // A rede de verdade: o provedor, a CPU, a célula e o drive.

    private static Object logic(ServerLevel level, BlockPos provider) {
        BlockEntity entity = level.getBlockEntity(provider);
        try {
            return entity.getClass().getMethod("getLogic").invoke(entity);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("sem a lógica do Pattern Provider em " + provider.toShortString(), e);
        }
    }

    private static Object call(Object target, String method) {
        try {
            return target.getClass().getMethod(method).invoke(target);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("sem " + method + " em " + target.getClass().getName(), e);
        }
    }

    /** Põe o padrão no Pattern Provider (o inventário de padrões dele, por reflexão). */
    static boolean installPattern(ServerLevel level, BlockPos provider, ItemStack pattern) {
        InternalInventory patterns = (InternalInventory) call(logic(level, provider), "getPatternInv");
        return patterns.addItems(pattern).isEmpty();
    }

    /** Liga a trava "até o resultado" do provedor. */
    static void lockUntilResult(ServerLevel level, BlockPos provider) {
        ((IConfigurableObject) level.getBlockEntity(provider)).getConfigManager()
                .putSetting(Settings.LOCK_CRAFTING_MODE, LockCraftingMode.LOCK_UNTIL_RESULT);
    }

    /** Tira a trava do provedor. */
    static void unlock(ServerLevel level, BlockPos provider) {
        ((IConfigurableObject) level.getBlockEntity(provider)).getConfigManager()
                .putSetting(Settings.LOCK_CRAFTING_MODE, LockCraftingMode.NONE);
    }

    /** O retorno do provedor (os 9 espaços em que as máquinas devolvem) está vazio, visto pela face {@code face}. */
    static boolean returnEmpty(ServerLevel level, BlockPos provider, Direction face) {
        GenericInternalInventory inventory = level.getCapability(AECapabilities.GENERIC_INTERNAL_INV, provider, face);
        if (inventory == null) {
            throw new IllegalStateException("sem o retorno do provedor em " + provider.toShortString());
        }
        for (int slot = 0; slot < inventory.size(); slot++) {
            if (inventory.getKey(slot) != null) {
                return false;
            }
        }
        return true;
    }

    /** O nó do bloco em {@code pos} está ativo (com energia e canal). */
    static boolean active(ServerLevel level, BlockPos pos) {
        IGridNode node = node(level, pos);
        return node != null && node.isActive();
    }

    /** Põe uma célula de 1k no drive, pelo inventário exposto dele. */
    static boolean insertCell(ServerLevel level, BlockPos drive) {
        return insertCell(level, drive, ResourceLocation.fromNamespaceAndPath("ae2", "item_storage_cell_1k"), 0);
    }

    /** Põe a célula {@code id} no espaço {@code slot} do drive, pelo inventário exposto dele. */
    static boolean insertCell(ServerLevel level, BlockPos drive, ResourceLocation id, int slot) {
        IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, drive, null);
        Item cell = BuiltInRegistries.ITEM.get(id);
        return handler != null && cell != Items.AIR && handler.insertItem(slot, new ItemStack(cell), false).isEmpty();
    }

    private static @Nullable IGridNode node(ServerLevel level, BlockPos pos) {
        IInWorldGridNodeHost host = level.getCapability(AECapabilities.IN_WORLD_GRID_NODE_HOST, pos, null);
        if (host == null) {
            return null;
        }
        // o nó só aparece pelas faces em que está exposto, nunca pela face nula
        for (Direction face : Direction.values()) {
            IGridNode node = host.getGridNode(face);
            if (node != null) {
                return node;
            }
        }
        return null;
    }

    private static IGrid grid(ServerLevel level, BlockPos pos) {
        IGridNode node = node(level, pos);
        if (node == null || node.getGrid() == null) {
            throw new IllegalStateException("sem rede em " + pos.toShortString());
        }
        return node.getGrid();
    }

    /**
     * O que falta para a rede em {@code pos} fabricar {@code output}: o nó ativo (com energia e canal), o padrão
     * conhecido e uma CPU; {@code null} quando está pronta.
     */
    static @Nullable String notReady(ServerLevel level, BlockPos pos, Item output) {
        return notReadyFor(level, pos, AEItemKey.of(output));
    }

    /** O {@link #notReady(ServerLevel, BlockPos, Item)} de uma chave qualquer ({@code AEKey}, como a de químico). */
    static @Nullable String notReadyFor(ServerLevel level, BlockPos pos, Object output) {
        IGridNode node = node(level, pos);
        if (node == null || node.getGrid() == null) {
            return "sem nó";
        }
        if (!node.isActive()) {
            return "nó inativo (energia " + node.isPowered() + ", canal " + node.meetsChannelRequirements()
                    + ", nós na rede " + node.getGrid().size() + ")";
        }
        ICraftingService crafting = node.getGrid().getCraftingService();
        if (!crafting.isCraftable((AEKey) output)) {
            return "o padrão não chegou à rede";
        }
        return crafting.getCpus().isEmpty() ? "sem CPU" : null;
    }

    /** Guarda {@code count} de {@code item} na rede; devolve quanto entrou. */
    static long stock(ServerLevel level, BlockPos pos, Item item, int count) {
        return stockKey(level, pos, AEItemKey.of(item), count);
    }

    /** Guarda {@code amount} da chave {@code key} ({@code AEKey}) na rede; devolve quanto entrou. */
    static long stockKey(ServerLevel level, BlockPos pos, Object key, long amount) {
        return grid(level, pos).getStorageService().getInventory()
                .insert((AEKey) key, amount, Actionable.MODULATE, IActionSource.empty());
    }

    /** Quanto de {@code item} a rede tem guardado. */
    static long stored(ServerLevel level, BlockPos pos, Item item) {
        return storedKey(level, pos, AEItemKey.of(item));
    }

    /** Quanto da chave {@code key} ({@code AEKey}) a rede tem guardado. */
    static long storedKey(ServerLevel level, BlockPos pos, Object key) {
        return grid(level, pos).getStorageService().getInventory()
                .extract((AEKey) key, Long.MAX_VALUE, Actionable.SIMULATE, IActionSource.empty());
    }

    /** Começa o cálculo de um pedido de {@code amount} de {@code item} (numa thread do AE2); devolve o futuro. */
    static Object plan(ServerLevel level, BlockPos pos, Item item, long amount) {
        return planKey(level, pos, AEItemKey.of(item), amount);
    }

    /** O {@link #plan} de uma chave qualquer ({@code AEKey}, como a de químico). */
    static Object planKey(ServerLevel level, BlockPos pos, Object key, long amount) {
        IGridNode node = node(level, pos);
        // o cálculo só olha os padrões da rede do nó de quem pede
        IActionSource source = IActionSource.ofMachine(() -> node);
        return node.getGrid().getCraftingService().beginCraftingCalculation(level, () -> source,
                (AEKey) key, amount, CalculationStrategy.REPORT_MISSING_ITEMS);
    }

    static boolean planned(Object future) {
        return ((Future<?>) future).isDone();
    }

    /** Manda o pedido calculado a uma CPU livre; devolve {@code null} ou o motivo da falha. */
    static @Nullable String submit(ServerLevel level, BlockPos pos, Object future) {
        ICraftingPlan plan;
        try {
            plan = (ICraftingPlan) ((Future<?>) future).get();
        } catch (Exception e) {
            return "o cálculo falhou: " + e;
        }
        if (plan.simulation()) {
            StringBuilder missing = new StringBuilder("faltam itens:");
            for (Object2LongMap.Entry<?> entry : plan.missingItems()) {
                missing.append(' ').append(entry.getKey()).append(" × ").append(entry.getLongValue());
            }
            return missing.toString();
        }
        IGridNode node = node(level, pos);
        var result = node.getGrid().getCraftingService().submitJob(plan, null, null, false,
                IActionSource.ofMachine(() -> node));
        return result.successful() ? null : "a CPU recusou: " + result.errorCode();
    }

    /** As classes das CPUs de crafting da rede em {@code pos}, separadas por vírgula (para saber de quem é a CPU). */
    static String cpus(ServerLevel level, BlockPos pos) {
        StringBuilder names = new StringBuilder();
        for (Object cpu : grid(level, pos).getCraftingService().getCpus()) {
            names.append(names.isEmpty() ? "" : ",").append(cpu.getClass().getSimpleName());
        }
        return names.toString();
    }

    /** A rede ainda tem um pedido de {@code item} em andamento. */
    static boolean requesting(ServerLevel level, BlockPos pos, Item item) {
        return requestingKey(level, pos, AEItemKey.of(item));
    }

    /** A rede ainda tem um pedido da chave {@code key} ({@code AEKey}) em andamento. */
    static boolean requestingKey(ServerLevel level, BlockPos pos, Object key) {
        return grid(level, pos).getCraftingService().isRequesting((AEKey) key);
    }
}
