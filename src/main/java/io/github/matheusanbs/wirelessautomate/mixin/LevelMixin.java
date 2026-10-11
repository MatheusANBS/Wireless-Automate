package io.github.matheusanbs.wirelessautomate.mixin;

import io.github.matheusanbs.wirelessautomate.range.RemoteIndex;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Acorda o roteador quando uma máquina remota vinculada muda (decisão 8 da spec do alcance): uma
 * máquina remota não encosta no roteador, então o aviso de vizinho do {@code setChanged} dela não chega
 * a ele. {@code BlockEntity.setChanged} chama {@link Level#blockEntityChanged(BlockPos)}, e aqui o
 * {@link RemoteIndex} confere a posição.
 *
 * <p>Caminho quente (todo {@code setChanged} do jogo): só o {@code isClientSide} e as consultas do
 * índice, sem alocar. Por isso é um {@code @ModifyVariable} no argumento, que devolve a posição como
 * veio, e não um {@code @Inject}: o {@code @Inject} cria um {@code CallbackInfo} por chamada.
 */
@Mixin(Level.class)
public abstract class LevelMixin {
    @Shadow
    @Final
    public boolean isClientSide;

    @ModifyVariable(method = "blockEntityChanged", at = @At("HEAD"), argsOnly = true)
    private BlockPos wirelessautomate$remoteMachineChanged(BlockPos pos) {
        if (!isClientSide) {
            RemoteIndex.blockEntityChanged((Level) (Object) this, pos);
        }
        return pos;
    }
}
