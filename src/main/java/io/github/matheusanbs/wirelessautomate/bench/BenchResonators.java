package io.github.matheusanbs.wirelessautomate.bench;

import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import io.github.matheusanbs.wirelessautomate.resonator.ResonanceCoreBlockEntity;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorPartBlock;
import io.github.matheusanbs.wirelessautomate.storage.StorageKind;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Os Ressonadores de Sinal dos cenários {@code resonator-*} ({@link BenchScene}): caixas 5×5×5 em grade, com a
 * Porta de Energia no centro da face de cima (o roteador vai em cima dela, como nos outros cenários) e, no cenário
 * que gera, uma Bateria do mod ao lado de cada uma. O desenho é o {@code ENERGY_ON_TOP} dos GameTests: Núcleo
 * no centro da face leste, Porta de Cristais na norte e a janela de vidro na sul.
 *
 * <p>A casca nasce sem o Núcleo (linha de base com os blocos soltos, sem nenhum Núcleo no gerenciador);
 * {@link #form} põe os Núcleos Ultimate com 8 Lapidados e a redstone cheia, e eles montam pela revalidação de
 * sempre, no tick seguinte à carga. {@link #tune} põe o dial na estação depois de montar.
 */
final class BenchResonators {
    static final RouterTier TIER = RouterTier.ULTIMATE;
    /** Passo da grade: a caixa de 5, a Bateria e um bloco de folga em x; a caixa e um de folga em z. */
    static final int CELL_X = 7;
    static final int CELL_Z = 6;
    private static final int PLACE_FLAGS = Block.UPDATE_CLIENTS;
    private static final int REMOVE_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    /** Desvio do dial que pede ressintonia (o platô de 100% vai até 0,25 MHz). */
    private static final double RETUNE_MHZ = 0.1;

    /** Um Ressonador: o canto mínimo da caixa, o Núcleo, a Porta de Energia e a Bateria (ou {@code null}). */
    record Resonator(BlockPos box, BlockPos core, BlockPos energyPort, @Nullable BlockPos battery) {
        /** O bloco que o evento quebra: o canto de baixo da casca, que só este Ressonador usa. */
        BlockPos corner() {
            return box;
        }
    }

    private final ServerLevel level;
    final List<Resonator> list = new ArrayList<>();
    private final BlockPos max;

    BenchResonators(ServerLevel level, BlockPos origin, int count, boolean batteries) {
        this.level = level;
        int side = (int) Math.ceil(Math.sqrt(count));
        for (int i = 0; i < count; i++) {
            BlockPos box = origin.offset((i % side) * CELL_X, 0, (i / side) * CELL_Z);
            list.add(new Resonator(box, box.offset(4, 2, 2), box.offset(2, 4, 2),
                    batteries ? box.offset(5, 0, 2) : null));
        }
        // Uma linha acima da Porta para o roteador.
        max = origin.offset(side * CELL_X, 5, ((count + side - 1) / side) * CELL_Z);
    }

    BlockPos max() {
        return max;
    }

    /** A casca inteira menos o Núcleo, e as Baterias Ultimate. */
    void build() {
        BlockState casing = ModBlocks.RESONATOR_CASING.get().defaultBlockState();
        BlockState glass = ModBlocks.RESONATOR_GLASS.get().defaultBlockState();
        BlockState battery = ModBlocks.STORAGE.get(StorageKind.BATTERY).get().defaultBlockState()
                .setValue(RouterBlock.TIER, TIER);
        for (Resonator resonator : list) {
            BlockPos origin = resonator.box();
            for (int x = 0; x < 5; x++) {
                for (int y = 0; y < 5; y++) {
                    for (int z = 0; z < 5; z++) {
                        boolean inside = x > 0 && x < 4 && y > 0 && y < 4 && z > 0 && z < 4;
                        boolean window = z == 4 && x > 0 && x < 4 && y > 0 && y < 4;
                        if (!inside) {
                            level.setBlock(origin.offset(x, y, z), window ? glass : casing, PLACE_FLAGS);
                        }
                    }
                }
            }
            level.setBlock(resonator.core(), Blocks.AIR.defaultBlockState(), PLACE_FLAGS);
            part(origin.offset(2, 2, 0), ModBlocks.RESONATOR_CRYSTAL_PORT.get(), Direction.NORTH);
            part(resonator.energyPort(), ModBlocks.RESONATOR_ENERGY_PORT.get(), Direction.UP);
            if (resonator.battery() != null) {
                level.setBlock(resonator.battery(), battery, PLACE_FLAGS);
            }
        }
    }

    private void part(BlockPos pos, Block block, Direction facing) {
        level.setBlock(pos, block.defaultBlockState().setValue(ResonatorPartBlock.FACING, facing), PLACE_FLAGS);
    }

    /** Põe os Núcleos, cada um com um Lapidado cheio por câmara e a redstone no máximo; montam no tick seguinte. */
    void form() {
        BlockState state = ModBlocks.RESONANCE_CORE.get().defaultBlockState()
                .setValue(RouterBlock.TIER, TIER).setValue(ResonatorPartBlock.FACING, Direction.EAST);
        for (Resonator resonator : list) {
            level.setBlock(resonator.core(), state, PLACE_FLAGS);
            if (level.getBlockEntity(resonator.core()) instanceof ResonanceCoreBlockEntity core) {
                for (int i = 0; i < core.chambers(); i++) {
                    core.insertCrystal(new ItemStack(ModItems.CUT_SIGNAL_CRYSTAL.get()));
                }
                core.addRedstone(ResonanceCoreBlockEntity.MAX_REDSTONE);
            }
        }
    }

    /** Dial na estação dos Núcleos montados que saíram do platô (a estação nasce na montagem e deriva). */
    void tune() {
        for (Resonator resonator : list) {
            ResonanceCoreBlockEntity core = core(resonator);
            if (core != null && core.formed() && Math.abs(core.station() - core.dial()) > RETUNE_MHZ) {
                core.setDial(core.station());
            }
        }
    }

    /** O evento: tira o canto de cada casca no mesmo tick, avisando como uma quebra (todos revalidam). */
    void breakCorners() {
        for (Resonator resonator : list) {
            level.setBlock(resonator.corner(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    /** Devolve os cantos: todos montam de novo no tick seguinte. */
    void restoreCorners() {
        BlockState casing = ModBlocks.RESONATOR_CASING.get().defaultBlockState();
        for (Resonator resonator : list) {
            level.setBlock(resonator.corner(), casing, Block.UPDATE_ALL);
        }
    }

    int formed() {
        int formed = 0;
        for (Resonator resonator : list) {
            ResonanceCoreBlockEntity core = core(resonator);
            if (core != null && core.formed()) {
                formed++;
            }
        }
        return formed;
    }

    /** Ocupação média do buffer dos Núcleos, de 0 a 1 (o que o roteador deixou de tirar). */
    double bufferFill() {
        double sum = 0;
        int count = 0;
        for (Resonator resonator : list) {
            ResonanceCoreBlockEntity core = core(resonator);
            if (core != null && core.capacity() > 0) {
                sum += (double) core.energy() / core.capacity();
                count++;
            }
        }
        return count == 0 ? 0 : sum / count;
    }

    private @Nullable ResonanceCoreBlockEntity core(Resonator resonator) {
        return level.getBlockEntity(resonator.core()) instanceof ResonanceCoreBlockEntity core ? core : null;
    }

    /**
     * Tira tudo: os Núcleos primeiro (a desmontagem deles e as quebras seguintes percorrem menos Núcleos), depois a
     * casca e as Baterias. Os cristais e a redstone que os Núcleos derrubam ficam para a limpeza dos itens da cena.
     */
    void teardown() {
        BlockState air = Blocks.AIR.defaultBlockState();
        for (Resonator resonator : list) {
            level.setBlock(resonator.core(), air, REMOVE_FLAGS);
        }
        for (Resonator resonator : list) {
            BlockPos origin = resonator.box();
            for (int x = 0; x < 5; x++) {
                for (int y = 0; y < 5; y++) {
                    for (int z = 0; z < 5; z++) {
                        level.setBlock(origin.offset(x, y, z), air, REMOVE_FLAGS);
                    }
                }
            }
            if (resonator.battery() != null) {
                level.setBlock(resonator.battery(), air, REMOVE_FLAGS);
            }
        }
    }
}
