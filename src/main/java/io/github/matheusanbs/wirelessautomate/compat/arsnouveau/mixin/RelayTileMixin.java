package io.github.matheusanbs.wirelessautomate.compat.arsnouveau.mixin;

import com.hollingsworth.arsnouveau.common.block.tile.RelayTile;
import io.github.matheusanbs.wirelessautomate.compat.arsnouveau.ArsStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Porte 1.20.1 (Ars 4.12): o Relay só liga e só transfere com um {@code AbstractSourceMachine} na posição
 * ({@code level.getBlockEntity(pos) instanceof AbstractSourceMachine}, conferido no bytecode). Nestes métodos,
 * o block entity do Tanque de Source vira a {@code RelayView} dele, que é um. Só é aplicado com o Ars
 * carregado (mixin/WaMixinPlugin); com outra versão do Ars sem estes métodos, não faz nada
 * ({@code require = 0}).
 */
@Mixin(value = RelayTile.class, remap = false)
public abstract class RelayTileMixin {
    @Redirect(method = {"setSendTo", "onFinishedConnectionFirst", "onFinishedConnectionLast", "tick"},
            at = @At(value = "INVOKE", remap = true,
                    target = "Lnet/minecraft/world/level/Level;getBlockEntity(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/entity/BlockEntity;"),
            require = 0)
    private BlockEntity wirelessautomate$relayView(Level level, BlockPos pos) {
        return ArsStorage.forRelay(level.getBlockEntity(pos));
    }
}
