package io.github.matheusanbs.wirelessautomate.bench;

import io.github.matheusanbs.wirelessautomate.bench.ae2.BenchAe2Grid;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * A rede AE2 de cada Doca do cenário {@code doca-ae2} ({@link BenchScenario#DOCK_AE2}), sem tipos do AE2 (a API fica
 * em {@link BenchAe2Grid}, só chamada com o AE2 carregado): o Pattern Provider no lugar do baú, com o padrão de
 * {@link #ORE_PER_ORDER} minérios de ferro e 1 carvão dando {@link #ORE_PER_ORDER} lingotes; atrás dele uma CPU de
 * 3 × 3 × 4 blocos ({@link #CO_PROCESSORS} coprocessadores e 4 armazenamentos de 256k), a célula de energia criativa
 * e um drive com uma célula de 64k. Os blocos vão por id, como um jogador poria.
 *
 * <p>A preparação ({@link #prepare}) espera a rede subir, guarda o minério e o carvão do pedido e começa o cálculo
 * dele; o pedido vai à CPU no começo da medição ({@link #submit}).
 */
final class BenchDockAe2 {
    /** Coprocessadores da CPU. */
    static final int CO_PROCESSORS = 32;
    /** Minérios (e lingotes) por execução do padrão; um carvão cozinha 8. */
    static final int ORE_PER_ORDER = 8;
    /** Execuções do padrão no pedido: 8.192 lingotes, bem mais do que as 32 máquinas dão na medição. */
    static final int ORDERS = 1024;
    /** Quantos blocos a rede vai ao norte do provedor (até o drive). */
    static final int NORTH_REACH = 5;
    private static final int PLACE_FLAGS = Block.UPDATE_ALL;
    private static final int REMOVE_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    private final ServerLevel level;
    final BlockPos provider;
    private final List<BlockPos> cpu = new ArrayList<>();
    private final BlockPos energy;
    private final BlockPos drive;
    private boolean stocked;
    private @Nullable Object plan;
    private @Nullable String problem;

    /** A rede de uma Doca com o provedor em {@code provider}, a CPU ao norte dele. */
    BenchDockAe2(ServerLevel level, BlockPos provider) {
        this.level = level;
        this.provider = provider;
        for (int x = -1; x <= 1; x++) {
            for (int y = 0; y < 4; y++) {
                for (int z = 1; z <= 3; z++) {
                    cpu.add(provider.offset(x, y, -z));
                }
            }
        }
        energy = provider.north(4);
        drive = energy.north();
    }

    void build() {
        level.setBlock(provider, ae2("pattern_provider"), PLACE_FLAGS);
        // os 4 primeiros blocos da CPU são os armazenamentos; o resto, coprocessadores
        for (int i = 0; i < cpu.size(); i++) {
            level.setBlock(cpu.get(i), ae2(i < cpu.size() - CO_PROCESSORS ? "256k_crafting_storage" : "crafting_accelerator"),
                    PLACE_FLAGS);
        }
        level.setBlock(energy, ae2("creative_energy_cell"), PLACE_FLAGS);
        level.setBlock(drive, ae2("drive"), PLACE_FLAGS);
        if (!BenchAe2Grid.insertCell(level, drive, "item_storage_cell_64k")) {
            problem = "a célula não entrou no drive";
        }
        if (!BenchAe2Grid.installPattern(level, provider, BenchAe2Grid.processingPattern(Items.RAW_IRON, ORE_PER_ORDER,
                Items.COAL, 1, Items.IRON_INGOT, ORE_PER_ORDER))) {
            problem = "o padrão não entrou no provedor";
        }
    }

    /**
     * Um passo da preparação por tick: a rede pronta (CPU formada e o padrão conhecido), depois o minério e o carvão
     * guardados, depois o cálculo começado (o cálculo lê o estoque do tick seguinte). Devolve se o cálculo terminou.
     */
    boolean prepare() {
        if (plan != null) {
            return BenchAe2Grid.planned(plan);
        }
        if (BenchAe2Grid.notReady(level, provider, Items.IRON_INGOT, CO_PROCESSORS) != null) {
            return false;
        }
        if (!stocked) {
            long ore = BenchAe2Grid.stock(level, provider, Items.RAW_IRON, (long) ORDERS * ORE_PER_ORDER);
            long coal = BenchAe2Grid.stock(level, provider, Items.COAL, ORDERS);
            if (ore < (long) ORDERS * ORE_PER_ORDER || coal < ORDERS) {
                problem = "o estoque não coube na célula";
            }
            stocked = true;
            return false;
        }
        plan = BenchAe2Grid.plan(level, provider, Items.IRON_INGOT, (long) ORDERS * ORE_PER_ORDER);
        return false;
    }

    /** Manda o pedido calculado à CPU; o motivo da falha fica para a linha do relatório. */
    void submit() {
        if (plan == null || !BenchAe2Grid.planned(plan)) {
            problem = "o cálculo não terminou antes da medição";
            return;
        }
        String error = BenchAe2Grid.submit(level, provider, plan);
        if (error != null) {
            problem = error;
        }
    }

    /** A parte da linha do relatório desta rede. */
    String describe() {
        return String.format(Locale.ROOT, "CPU de %d coprocessadores, pedido de %d lingotes (%d execuções)%s",
                BenchAe2Grid.coProcessors(level, provider), ORDERS * ORE_PER_ORDER, ORDERS,
                problem == null ? "" : " | PROBLEMA: " + problem);
    }

    void teardown() {
        level.setBlock(drive, Blocks.AIR.defaultBlockState(), REMOVE_FLAGS);
        level.setBlock(energy, Blocks.AIR.defaultBlockState(), REMOVE_FLAGS);
        for (BlockPos pos : cpu) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), REMOVE_FLAGS);
        }
        level.setBlock(provider, Blocks.AIR.defaultBlockState(), REMOVE_FLAGS);
    }

    private static BlockState ae2(String path) {
        return BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("ae2", path)).defaultBlockState();
    }
}
