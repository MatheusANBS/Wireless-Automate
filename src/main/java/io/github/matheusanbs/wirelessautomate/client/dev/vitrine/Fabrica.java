package io.github.matheusanbs.wirelessautomate.client.dev.vitrine;

import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.client.dev.E2eWorld;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;


/**
 * Peças de fábrica que mais de uma cena usa: a fornalha que fica acesa (combustível e minério de sobra), o baú e o
 * roteador preso em cima de uma máquina, já numa rede e num modo.
 */
final class Fabrica {
    private Fabrica() {
    }

    /** Uma fornalha virada para o sul (a câmera olha para o norte), com blocos de carvão de combustível e ferro cru para horas. */
    static void fornalha(ServerLevel nivel, BlockPos pos) {
        fornalha(nivel, pos, 64);
    }

    /** O mesmo, com {@code entrada} de ferro cru (0: só o combustível, a fornalha acende quando chega minério). */
    static void fornalha(ServerLevel nivel, BlockPos pos, int entrada) {
        maquina(nivel, pos, Blocks.FURNACE, entrada);
    }

    /** O mesmo com outro bloco de fornalha ({@code bloco}: a fornalha comum, o alto-forno, o defumador). */
    static void maquina(ServerLevel nivel, BlockPos pos, Block bloco, int entrada) {
        nivel.setBlockAndUpdate(pos, bloco.defaultBlockState().setValue(AbstractFurnaceBlock.FACING, Direction.SOUTH));
        Container forno = (Container) nivel.getBlockEntity(pos);
        if (entrada > 0) {
            forno.setItem(0, new ItemStack(Items.RAW_IRON, entrada));
        }
        forno.setItem(1, new ItemStack(Items.COAL_BLOCK, 16));
    }

    /** O roteador do tier no alto de {@code maquina}, na rede {@code rede} (ou nenhuma), com a face de cima no modo dado. */
    static void roteador(MinecraftServer servidor, BlockPos maquina, UUID rede, String nome, PortMode modo) {
        ServerLevel nivel = servidor.overworld();
        BlockPos pos = maquina.above();
        nivel.setBlockAndUpdate(pos, ModBlocks.ROUTER.get().defaultBlockState().setValue(RouterBlock.FACING, Direction.UP)
                .setValue(RouterBlock.TIER, RouterTier.EMERALD));
        var roteador = E2eWorld.router(servidor, pos);
        roteador.setName(nome);
        if (rede != null) {
            roteador.setNetworkId(rede);
            roteador.setMode(ResourceType.ITEM, Direction.UP, modo);
        }
    }
}
