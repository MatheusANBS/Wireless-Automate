package io.github.matheusanbs.wirelessautomate.bench.ae2;

import appeng.api.AECapabilities;
import appeng.api.config.Actionable;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import java.util.List;
import java.util.concurrent.Future;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * A rede AE2 do cenário {@code doca-ae2} ({@code bench/BenchDock}): o que usa a API do AE2. Só é chamada com o AE2
 * carregado (o {@code BenchRunner} pula o cenário sem ele), e nenhuma assinatura daqui tem tipo do AE2, para quem chama
 * não precisar dele: o plano do cálculo vai e volta como {@code Object}. Os blocos são postos por id por quem chama.
 *
 * <p>O que o jar da API não tem (o inventário de padrões do Pattern Provider) é lido por reflexão, como no
 * {@code gametest/Ae2TestSupport}.
 */
public final class BenchAe2Grid {
    private BenchAe2Grid() {
    }

    /** Um padrão de processamento: {@code inCount} de {@code in} e {@code fuelCount} de {@code fuel} dão {@code outCount} de {@code out}. */
    public static ItemStack processingPattern(Item in, int inCount, Item fuel, int fuelCount, Item out, int outCount) {
        return PatternDetailsHelper.encodeProcessingPattern(
                List.of(new GenericStack(AEItemKey.of(in), inCount), new GenericStack(AEItemKey.of(fuel), fuelCount)),
                List.of(new GenericStack(AEItemKey.of(out), outCount)));
    }

    /** Põe o padrão no inventário de padrões do Pattern Provider em {@code provider} (por reflexão). */
    public static boolean installPattern(ServerLevel level, BlockPos provider, ItemStack pattern) {
        BlockEntity entity = level.getBlockEntity(provider);
        try {
            Object logic = entity.getClass().getMethod("getLogic").invoke(entity);
            InternalInventory patterns = (InternalInventory) logic.getClass().getMethod("getPatternInv").invoke(logic);
            return patterns.addItems(pattern).isEmpty();
        } catch (ReflectiveOperationException | NullPointerException e) {
            return false;
        }
    }

    /** Põe uma célula de {@code cell} (id do AE2) no primeiro slot livre do drive, pelo inventário exposto dele. */
    public static boolean insertCell(ServerLevel level, BlockPos drive, String cell) {
        IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, drive, null);
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("ae2", cell));
        return handler != null && handler.insertItem(0, new ItemStack(item), false).isEmpty();
    }

    private static @Nullable IGridNode node(ServerLevel level, BlockPos pos) {
        IInWorldGridNodeHost host = level.getCapability(AECapabilities.IN_WORLD_GRID_NODE_HOST, pos, null);
        if (host == null) {
            return null;
        }
        for (Direction face : Direction.values()) {
            IGridNode node = host.getGridNode(face);
            if (node != null) {
                return node;
            }
        }
        return null;
    }

    /**
     * O que falta para a rede em {@code pos} fabricar {@code output} com uma CPU de {@code coProcessors} coprocessadores
     * ou mais: o nó ativo, o padrão conhecido e a CPU formada; {@code null} quando está pronta.
     */
    public static @Nullable String notReady(ServerLevel level, BlockPos pos, Item output, int coProcessors) {
        IGridNode node = node(level, pos);
        if (node == null || node.getGrid() == null) {
            return "sem nó";
        }
        if (!node.isActive()) {
            return "nó inativo";
        }
        ICraftingService crafting = node.getGrid().getCraftingService();
        if (!crafting.isCraftable(AEItemKey.of(output))) {
            return "o padrão não chegou à rede";
        }
        for (ICraftingCPU cpu : crafting.getCpus()) {
            if (cpu.getCoProcessors() >= coProcessors) {
                return null;
            }
        }
        return "sem CPU com " + coProcessors + " coprocessadores";
    }

    /** Coprocessadores da maior CPU da rede em {@code pos}. */
    public static int coProcessors(ServerLevel level, BlockPos pos) {
        IGridNode node = node(level, pos);
        int most = 0;
        if (node != null && node.getGrid() != null) {
            for (ICraftingCPU cpu : node.getGrid().getCraftingService().getCpus()) {
                most = Math.max(most, cpu.getCoProcessors());
            }
        }
        return most;
    }

    /** Guarda {@code count} de {@code item} na rede em {@code pos}; devolve quanto entrou. */
    public static long stock(ServerLevel level, BlockPos pos, Item item, long count) {
        IGridNode node = node(level, pos);
        if (node == null || node.getGrid() == null) {
            return 0;
        }
        return node.getGrid().getStorageService().getInventory()
                .insert(AEItemKey.of(item), count, Actionable.MODULATE, IActionSource.empty());
    }

    /** Começa o cálculo de um pedido de {@code amount} de {@code item} (numa thread do AE2); devolve o futuro. */
    public static Object plan(ServerLevel level, BlockPos pos, Item item, long amount) {
        IGridNode node = node(level, pos);
        IActionSource source = IActionSource.ofMachine(() -> node);
        return node.getGrid().getCraftingService().beginCraftingCalculation(level, () -> source,
                AEItemKey.of(item), amount, CalculationStrategy.REPORT_MISSING_ITEMS);
    }

    public static boolean planned(Object future) {
        return ((Future<?>) future).isDone();
    }

    /** Manda o pedido calculado a uma CPU livre; devolve {@code null} ou o motivo da falha. */
    public static @Nullable String submit(ServerLevel level, BlockPos pos, Object future) {
        ICraftingPlan plan;
        try {
            plan = (ICraftingPlan) ((Future<?>) future).get();
        } catch (Exception e) {
            return "o cálculo falhou: " + e;
        }
        if (plan.simulation()) {
            return "faltam itens para o pedido";
        }
        IGridNode node = node(level, pos);
        var result = node.getGrid().getCraftingService().submitJob(plan, null, null, false,
                IActionSource.ofMachine(() -> node));
        return result.successful() ? null : "a CPU recusou: " + result.errorCode();
    }
}
