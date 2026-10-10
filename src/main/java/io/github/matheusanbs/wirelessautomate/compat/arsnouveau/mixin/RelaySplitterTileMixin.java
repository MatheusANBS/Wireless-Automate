package io.github.matheusanbs.wirelessautomate.compat.arsnouveau.mixin;

import com.hollingsworth.arsnouveau.common.block.tile.RelaySplitterTile;
import io.github.matheusanbs.wirelessautomate.compat.arsnouveau.ArsStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Porte 1.20.1 (Ars 4.12): o Relay Splitter (e o Warp, que o estende) aceita qualquer posição na lista, mas
 * a cada ciclo descarta as que não são {@code AbstractSourceMachine}. Nos dois métodos que percorrem as
 * listas, o Tanque de Source vira a {@code RelayView} dele, como no {@link RelayTileMixin}.
 */
@Mixin(value = RelaySplitterTile.class, remap = false)
public abstract class RelaySplitterTileMixin {
    @Redirect(method = {"processFromList", "processToList"},
            at = @At(value = "INVOKE", remap = true,
                    target = "Lnet/minecraft/world/level/Level;getBlockEntity(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/entity/BlockEntity;"),
            require = 0)
    private BlockEntity wirelessautomate$relayView(Level level, BlockPos pos) {
        return ArsStorage.forRelay(level.getBlockEntity(pos));
    }
}
