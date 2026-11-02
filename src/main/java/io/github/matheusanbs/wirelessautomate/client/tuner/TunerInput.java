package io.github.matheusanbs.wirelessautomate.client.tuner;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.range.RemoteCapabilities;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;

/**
 * O toque novo do Configurador: o uso numa máquina só vai ao servidor se a tecla de uso soltou desde o último, para o
 * botão segurado (que o jogo repete a cada 4 ticks) não analisar e confirmar sozinho. A trava só age onde o
 * Configurador agiria ({@code ConfiguratorItem.onItemUseFirst}): bloco com block entity ou com capability, e não
 * roteador. Num roteador, no ar e em qualquer outro bloco o evento segue intacto, para a mão secundária (tocha, bloco,
 * farinha de osso) repetir o uso como sempre. O servidor tem o próprio intervalo mínimo entre os dois cliques; isto só
 * poupa os pacotes.
 */
@EventBusSubscriber(modid = WirelessAutomate.MODID, value = Dist.CLIENT)
public final class TunerInput {
    /** A tecla de uso soltou desde o último uso aceito numa máquina. */
    private static boolean released = true;

    private TunerInput() {
    }

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        if (!released && !Minecraft.getInstance().options.keyUse.isDown()) {
            released = true;
        }
    }

    @SubscribeEvent
    static void onUse(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!event.isUseItem() || event.getHand() != InteractionHand.MAIN_HAND || minecraft.player == null
                || minecraft.level == null || !minecraft.player.getMainHandItem().is(ModItems.CONFIGURATOR.get())
                || !(minecraft.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK
                || !actsOn(minecraft.level, hit.getBlockPos())) {
            return;
        }
        if (released) {
            released = false;
            return;
        }
        event.setCanceled(true);
        event.setSwingHand(false);
    }

    /** O Configurador agiria no bloco: a mesma condição do {@code ConfiguratorItem.onItemUseFirst}. */
    private static boolean actsOn(ClientLevel level, BlockPos pos) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        return blockEntity == null ? RemoteCapabilities.any(level, pos) : !(blockEntity instanceof RouterBlockEntity);
    }
}
