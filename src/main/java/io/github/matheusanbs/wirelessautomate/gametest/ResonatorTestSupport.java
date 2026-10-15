package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import io.github.matheusanbs.wirelessautomate.resonator.ResonanceCoreBlockEntity;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorPartBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Monta um Ressonador de Sinal inteiro (os 98 blocos) num GameTest do template {@code empty7}. */
final class ResonatorTestSupport {
    /** Onde ficam as peças; a origem é o canto de menor coordenada da caixa 5×5×5, relativa ao teste. */
    enum Layout {
        /** Janela 3×3 ao sul, Núcleo no centro da face leste, Energia na oeste, Cristais na norte e Sintonizador em cima. */
        VITRINE,
        /** Como a Vitrine, mas a Porta de Energia no centro da face de cima e sem Sintonizador. */
        ENERGY_ON_TOP
    }

    private ResonatorTestSupport() {
    }

    /** Põe os 98 blocos com a caixa a partir de {@code origin} e devolve a posição relativa do Núcleo. */
    static BlockPos build(GameTestHelper helper, BlockPos origin, Layout layout, RouterTier tier) {
        for (int x = 0; x < 5; x++) {
            for (int y = 0; y < 5; y++) {
                for (int z = 0; z < 5; z++) {
                    boolean inside = x > 0 && x < 4 && y > 0 && y < 4 && z > 0 && z < 4;
                    if (!inside) {
                        helper.setBlock(origin.offset(x, y, z), ModBlocks.RESONATOR_CASING.get());
                    }
                }
            }
        }
        for (int x = 1; x < 4; x++) {
            for (int y = 1; y < 4; y++) {
                helper.setBlock(origin.offset(x, y, 4), ModBlocks.RESONATOR_GLASS.get());
            }
        }
        BlockPos core = origin.offset(4, 2, 2);
        helper.setBlock(core, ModBlocks.RESONANCE_CORE.get().defaultBlockState()
                .setValue(RouterBlock.TIER, tier).setValue(ResonatorPartBlock.FACING, Direction.EAST));
        part(helper, origin.offset(2, 2, 0), ModBlocks.RESONATOR_CRYSTAL_PORT.get(), Direction.NORTH);
        if (layout == Layout.VITRINE) {
            part(helper, origin.offset(0, 2, 2), ModBlocks.RESONATOR_ENERGY_PORT.get(), Direction.WEST);
            part(helper, origin.offset(2, 4, 2), ModBlocks.RESONATOR_TUNER.get(), Direction.UP);
        } else {
            part(helper, origin.offset(2, 4, 2), ModBlocks.RESONATOR_ENERGY_PORT.get(), Direction.UP);
        }
        return core;
    }

    private static void part(GameTestHelper helper, BlockPos pos, Block block, Direction facing) {
        BlockState state = block.defaultBlockState().setValue(ResonatorPartBlock.FACING, facing);
        helper.setBlock(pos, state);
    }

    static ResonanceCoreBlockEntity core(GameTestHelper helper, BlockPos corePos) {
        return helper.getBlockEntity(corePos) instanceof ResonanceCoreBlockEntity core ? core : null;
    }
}
