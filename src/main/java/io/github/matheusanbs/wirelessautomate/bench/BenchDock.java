package io.github.matheusanbs.wirelessautomate.bench;

import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.dock.DockDelivery;
import io.github.matheusanbs.wirelessautomate.dock.DockSide;
import io.github.matheusanbs.wirelessautomate.dock.PatternDockBlockEntity;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.range.RemoteSide;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * A cena do cenário {@code doca} ({@link BenchScenario#DOCK}): {@code n} Docas de Padrões Elite, cada uma com
 * {@link #FURNACES} fornalhas vinculadas ao lado Norte (o perfil da fornalha: Cima e Trás Insere, Baixo Extrai) e um
 * baú encostado no lugar do provedor. A cada tick, dentro do tempo medido ({@link #offerOrders}, chamado no começo do
 * tick como faria o tick do AE2), cada Doca recebe pedidos de "1 minério de ferro + 1 carvão" por
 * {@link DockDelivery#offer}, até {@link #ORDERS_PER_TICK} ou a primeira recusa (a CPU de 32 coprocessadores); a volta
 * leva o lingote ao baú, que o reenchimento esvazia ({@link #recycle}, que também faz o papel de fornalhas rápidas).
 *
 * <p>Conta as ofertas aceitas e recusadas, o tempo delas e os itens que voltaram ao baú (pelo contador do lado e pela
 * contagem neutra do baú).
 *
 * <p>No cenário {@code doca-ae2} ({@link BenchScenario#DOCK_AE2}), o AE2 de verdade ({@link BenchDockAe2}): o Pattern
 * Provider no lugar do baú, a CPU de 32 coprocessadores atrás dele, Altos-fornos no lugar das fornalhas, sem pedidos
 * falsos nem reenchimento. O pedido grande vai à CPU no começo da medição, e o provedor oferece as execuções à Doca no
 * tick do AE2. Os pedidos aceitos saem da contagem neutra das máquinas ({@link #ae2Orders}).
 */
final class BenchDock {
    /** Fornalhas por Doca: o máximo da Doca Elite (o da Esmeralda, um degrau acima). */
    static final int FURNACES = 32;
    /** Pedidos por tick e por Doca, no máximo: o que uma CPU de 32 coprocessadores do AE2 empurra. */
    static final int ORDERS_PER_TICK = 32;
    /** Lingotes que o reenchimento põe na saída de cada fornalha ({@link #recycle}). */
    static final int PRODUCED = 8;
    /** Fornalhas por fileira, ao sul da Doca. */
    private static final int ROW = 8;
    /** Distância em x entre duas Docas. */
    private static final int CELL = 12;
    private static final int NORTH = Direction.NORTH.ordinal();
    private static final int PLACE_FLAGS = Block.UPDATE_CLIENTS;
    private static final int REMOVE_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    /** O pedido, o mesmo em toda oferta (a entrega não muda as pilhas do chamador). */
    private static final List<ItemStack> ORDER = List.of(new ItemStack(Items.IRON_ORE), new ItemStack(Items.COAL));

    /** Uma Doca da cena: ela, o baú do provedor e as fornalhas. */
    record Cell(BlockPos dock, BlockPos chest, List<BlockPos> furnaces, @Nullable BenchDockAe2 ae2) {
    }

    private final ServerLevel level;
    final List<Cell> cells = new ArrayList<>();
    private final BlockPos max;
    private long accepted;
    private long refused;
    private long offerNanos;
    private long offerMaxNanos;
    /** Itens tirados do baú pelo reenchimento. */
    private long cleared;
    private long acceptedStart;
    private long refusedStart;
    private long offerNanosStart;
    private long returnedStart;
    private long deliveredStart;
    /** Com o AE2 de verdade ({@link BenchDockAe2}). */
    private final boolean ae2;
    private long ae2OrdersStart;

    BenchDock(ServerLevel level, BlockPos origin, int count, boolean ae2) {
        this.level = level;
        this.ae2 = ae2;
        // com o AE2, a rede vai ao norte do provedor: a cena desce em z para ela caber a partir da origem
        int shift = ae2 ? BenchDockAe2.NORTH_REACH + 1 : 0;
        for (int i = 0; i < count; i++) {
            BlockPos base = origin.offset(i * CELL, 0, shift);
            BlockPos dock = base.offset(ROW / 2, 0, 1);
            List<BlockPos> furnaces = new ArrayList<>(FURNACES);
            for (int f = 0; f < FURNACES; f++) {
                furnaces.add(base.offset(f % ROW, 0, 3 + f / ROW));
            }
            cells.add(new Cell(dock, dock.north(), furnaces, ae2 ? new BenchDockAe2(level, dock.north()) : null));
        }
        max = origin.offset(count * CELL, ae2 ? 4 : 1, shift + 3 + FURNACES / ROW);
    }

    BlockPos max() {
        return max;
    }

    /** Põe as Docas, os baús e as fornalhas, e vincula as fornalhas ao lado Norte com o perfil da fornalha. */
    void build() {
        Block machine = ae2 ? Blocks.BLAST_FURNACE : Blocks.FURNACE;
        ResourceLocation furnaceId = BuiltInRegistries.BLOCK.getKey(machine);
        for (Cell cell : cells) {
            level.setBlock(cell.dock(), ModBlocks.PATTERN_DOCK.get().defaultBlockState()
                    .setValue(RouterBlock.TIER, RouterTier.ELITE), PLACE_FLAGS);
            if (cell.ae2() != null) {
                cell.ae2().build();
            } else {
                level.setBlock(cell.chest(), Blocks.CHEST.defaultBlockState(), PLACE_FLAGS);
            }
            for (BlockPos pos : cell.furnaces()) {
                level.setBlock(pos, machine.defaultBlockState()
                        .setValue(AbstractFurnaceBlock.FACING, Direction.NORTH), PLACE_FLAGS);
            }
            PatternDockBlockEntity dock = dock(cell);
            if (dock == null) {
                continue;
            }
            dock.batchChanges(() -> {
                for (BlockPos pos : cell.furnaces()) {
                    dock.link(NORTH, pos, furnaceId, NORTH);
                }
            });
            DockSide side = dock.side(NORTH);
            if (side != null) {
                side.profile().face(ResourceType.ITEM, RemoteSide.TOP).setMode(PortMode.INSERT);
                side.profile().face(ResourceType.ITEM, RemoteSide.BACK).setMode(PortMode.INSERT);
                side.profile().face(ResourceType.ITEM, RemoteSide.BOTTOM).setMode(PortMode.EXTRACT);
            }
        }
    }

    /**
     * Toda Doca está no mundo com as {@link #FURNACES} fornalhas ativas e, com o AE2, a rede pronta e o pedido
     * calculado ({@link BenchDockAe2#prepare}, um passo por chamada).
     */
    boolean registered() {
        boolean ready = true;
        for (Cell cell : cells) {
            PatternDockBlockEntity dock = dock(cell);
            if (dock == null || dock.activeCount() < FURNACES) {
                ready = false;
            } else if (cell.ae2() != null && !cell.ae2().prepare()) {
                ready = false;
            }
        }
        return ready;
    }

    /** Os pedidos do tick: em cada Doca, até {@link #ORDERS_PER_TICK} ou a primeira recusa (com o AE2, é ele quem pede). */
    void offerOrders() {
        if (ae2) {
            return;
        }
        long started = System.nanoTime();
        for (Cell cell : cells) {
            PatternDockBlockEntity dock = dock(cell);
            if (dock == null) {
                continue;
            }
            for (int i = 0; i < ORDERS_PER_TICK; i++) {
                if (DockDelivery.offer(dock, NORTH, ORDER, List.<FluidStack>of(), false) == DockDelivery.Result.ACCEPTED) {
                    accepted++;
                } else {
                    refused++;
                    break;
                }
            }
        }
        long spent = System.nanoTime() - started;
        offerNanos += spent;
        offerMaxNanos = Math.max(offerMaxNanos, spent);
    }

    /**
     * O reenchimento, fora do tempo medido: esvazia os baús e faz o papel de fornalhas rápidas, tirando a entrada e o
     * combustível e pondo {@link #PRODUCED} lingotes na saída de cada uma, com o aviso de mudança que a fornalha dá ao
     * cozinhar. Sem isso a fornalha (um lingote a cada 10 s, e o carvão que sobra no combustível) enche em 3 s e a
     * medição só veria recusas; assim os dois caminhos, a entrega e a volta, andam na medição.
     */
    void recycle() {
        if (ae2) {
            return;
        }
        for (Cell cell : cells) {
            IItemHandler chest = chest(cell);
            if (chest != null) {
                for (int slot = 0, n = chest.getSlots(); slot < n; slot++) {
                    cleared += chest.extractItem(slot, Integer.MAX_VALUE, false).getCount();
                }
            }
            for (BlockPos pos : cell.furnaces()) {
                if (level.getBlockEntity(pos) instanceof AbstractFurnaceBlockEntity furnace) {
                    furnace.setItem(0, ItemStack.EMPTY);
                    furnace.setItem(1, ItemStack.EMPTY);
                    ItemStack out = furnace.getItem(2);
                    furnace.setItem(2, new ItemStack(Items.IRON_INGOT, Math.min(64, out.getCount() + PRODUCED)));
                    furnace.setChanged();
                }
            }
        }
    }

    /** Contagem neutra: itens que chegaram aos baús até agora (os que estão lá e os já tirados). */
    long delivered() {
        if (ae2) {
            return returned();
        }
        long total = cleared;
        for (Cell cell : cells) {
            IItemHandler chest = chest(cell);
            if (chest != null) {
                for (int slot = 0, n = chest.getSlots(); slot < n; slot++) {
                    total += chest.getStackInSlot(slot).getCount();
                }
            }
        }
        return total;
    }

    /** Itens que a volta contou como devolvidos, somando as Docas. */
    private long returned() {
        long total = 0;
        for (Cell cell : cells) {
            PatternDockBlockEntity dock = dock(cell);
            DockSide side = dock == null ? null : dock.side(NORTH);
            if (side != null) {
                total += side.returned();
            }
        }
        return total;
    }

    void beginMeasure() {
        acceptedStart = accepted;
        refusedStart = refused;
        offerNanosStart = offerNanos;
        offerMaxNanos = 0;
        returnedStart = returned();
        deliveredStart = delivered();
        if (ae2) {
            for (Cell cell : cells) {
                cell.ae2().submit();
            }
            ae2OrdersStart = ae2Orders();
        }
    }

    /**
     * Contagem neutra dos pedidos aceitos com o AE2, em minérios: o que está na entrada e na saída das máquinas mais o
     * que a volta devolveu. Cada execução aceita soma {@link BenchDockAe2#ORE_PER_ORDER}; cozinhar e devolver não mudam
     * a soma.
     */
    private long ae2Orders() {
        long total = returned();
        for (Cell cell : cells) {
            for (BlockPos pos : cell.furnaces()) {
                if (level.getBlockEntity(pos) instanceof AbstractFurnaceBlockEntity furnace) {
                    total += furnace.getItem(0).getCount() + furnace.getItem(2).getCount();
                }
            }
        }
        return total;
    }

    /** A linha da repetição: pedidos aceitos e recusados por segundo, o custo das ofertas e a volta. */
    String repLine(int rep, int ticks) {
        double seconds = Math.max(1, ticks) / 20.0;
        if (ae2) {
            StringBuilder line = new StringBuilder(String.format(Locale.ROOT,
                    "    Doca + AE2, rep %d: %.2f pedidos aceitos/s (%d execuções de %d minérios) | volta: %.2f itens/s pelo lado",
                    rep, (ae2Orders() - ae2OrdersStart) / (double) BenchDockAe2.ORE_PER_ORDER / seconds,
                    (ae2Orders() - ae2OrdersStart) / BenchDockAe2.ORE_PER_ORDER, BenchDockAe2.ORE_PER_ORDER,
                    (returned() - returnedStart) / seconds));
            for (Cell cell : cells) {
                line.append(" | ").append(cell.ae2().describe());
            }
            return line.toString();
        }
        long offers = accepted - acceptedStart + refused - refusedStart;
        long nanos = offerNanos - offerNanosStart;
        return String.format(Locale.ROOT,
                "    Doca, rep %d: %.1f pedidos aceitos/s, %.1f recusados/s | ofertas %.4f ms/tick (máx %.3f), %s µs/oferta"
                        + " | volta: %.2f itens/s pelo lado, %.2f itens/s no baú",
                rep, (accepted - acceptedStart) / seconds, (refused - refusedStart) / seconds, nanos / 1e6 / Math.max(1, ticks),
                offerMaxNanos / 1e6, offers == 0 ? "—" : String.format(Locale.ROOT, "%.2f", nanos / 1e3 / offers),
                (returned() - returnedStart) / seconds, (delivered() - deliveredStart) / seconds);
    }

    /** Tira tudo (as fornalhas esvaziadas antes, para nada cair no chão). */
    void teardown() {
        for (Cell cell : cells) {
            for (BlockPos pos : cell.furnaces()) {
                if (level.getBlockEntity(pos) instanceof AbstractFurnaceBlockEntity furnace) {
                    furnace.clearContent();
                }
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), REMOVE_FLAGS);
            }
            if (cell.ae2() != null) {
                cell.ae2().teardown();
            } else {
                IItemHandler chest = chest(cell);
                if (chest != null) {
                    for (int slot = 0, n = chest.getSlots(); slot < n; slot++) {
                        chest.extractItem(slot, Integer.MAX_VALUE, false);
                    }
                }
                level.setBlock(cell.chest(), Blocks.AIR.defaultBlockState(), REMOVE_FLAGS);
            }
            level.setBlock(cell.dock(), Blocks.AIR.defaultBlockState(), REMOVE_FLAGS);
        }
    }

    private @Nullable PatternDockBlockEntity dock(Cell cell) {
        return level.getBlockEntity(cell.dock()) instanceof PatternDockBlockEntity dock ? dock : null;
    }

    private @Nullable IItemHandler chest(Cell cell) {
        return level.getCapability(Capabilities.ItemHandler.BLOCK, cell.chest(), Direction.SOUTH);
    }
}
