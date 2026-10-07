package io.github.matheusanbs.wirelessautomate.linker;

import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Roteadores dentro de uma caixa, só nos chunks já carregados: percorre os block entities de cada
 * chunk que a caixa toca (nunca bloco a bloco) e conta os chunks descarregados, que ficam de fora
 * sem ser carregados.
 *
 * @param routers         roteadores na caixa, na ordem dos chunks
 * @param unloadedChunks  chunks da caixa que não estavam carregados
 */
public record LinkerScan(List<RouterBlockEntity> routers, int unloadedChunks) {
    public static LinkerScan of(ServerLevel level, LinkerBox box) {
        List<RouterBlockEntity> routers = new ArrayList<>();
        int unloaded = 0;
        for (int cx = box.minChunkX(); cx <= box.maxChunkX(); cx++) {
            for (int cz = box.minChunkZ(); cz <= box.maxChunkZ(); cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) {
                    unloaded++;
                    continue;
                }
                for (BlockEntity entity : chunk.getBlockEntities().values()) {
                    BlockPos pos = entity.getBlockPos();
                    if (entity instanceof RouterBlockEntity router && !router.isRemoved()
                            && box.contains(pos.getX(), pos.getY(), pos.getZ())) {
                        routers.add(router);
                    }
                }
            }
        }
        return new LinkerScan(routers, unloaded);
    }
}
