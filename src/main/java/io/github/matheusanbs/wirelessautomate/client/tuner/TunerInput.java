package io.github.matheusanbs.wirelessautomate.client.tuner;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.item.ConfiguratorItem;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;

/**
 * O toque novo do Configurador: depois de um uso numa máquina (onde o Configurador age,
 * {@link ConfiguratorItem#actsOn}), nenhum uso dele vai ao servidor até a tecla de uso soltar, para o botão segurado
 * (que o jogo repete a cada 4 ticks) não analisar e confirmar sozinho, nem apagar a proposta quando a mira escorrega
 * para o chão ou o ar: só um clique novo no ar a apaga. Com a tecla solta, o evento segue intacto fora das máquinas
 * (num roteador, no ar, em qualquer outro bloco), para a mão secundária (tocha, bloco, farinha de osso) repetir o uso
 * como sempre; segurado desde uma máquina, o cancelamento também a segura. O servidor tem a própria trava
 * ({@code TunerPending.attempt}); esta poupa os pacotes.
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
                || minecraft.level == null || !minecraft.player.getMainHandItem().is(ModItems.CONFIGURATOR.get())) {
            return;
        }
        if (released) {
            if (minecraft.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK
                    && ConfiguratorItem.actsOn(InteractionHand.MAIN_HAND, minecraft.level, hit.getBlockPos())) {
                released = false;
            }
            return;
        }
        event.setCanceled(true);
        event.setSwingHand(false);
    }
}
