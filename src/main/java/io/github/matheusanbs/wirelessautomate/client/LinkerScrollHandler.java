package io.github.matheusanbs.wirelessautomate.client;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.packet.CycleConfiguratorTypePayload;
import io.github.matheusanbs.wirelessautomate.packet.CycleLinkerTypePayload;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Shift + roda do mouse com o Vinculador na mão principal troca as abas que ele vincula pelos
 * atalhos (Todos, Itens, Fluidos, Energia e, com o Mekanism, Químicos; uma combinação marcada na tela
 * vai para Todos); com o Configurador, o tipo que ele cola (os mesmos atalhos). Só no jogo, sem tela aberta; o evento é cancelado para a roda não trocar o slot da
 * hotbar. Como na hotbar, rolar para baixo vai para o próximo. O servidor troca o tipo e mostra o
 * novo na action bar.
 */
@EventBusSubscriber(modid = WirelessAutomate.MODID, value = Dist.CLIENT)
public final class LinkerScrollHandler {
    private LinkerScrollHandler() {
    }

    @SubscribeEvent
    static void onScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen != null || minecraft.player == null || event.getScrollDeltaY() == 0
                || !minecraft.options.keyShift.isDown()) {
            return;
        }
        int direction = event.getScrollDeltaY() > 0 ? -1 : 1;
        ItemStack held = minecraft.player.getMainHandItem();
        CustomPacketPayload payload;
        if (held.is(ModItems.LINKER.get())) {
            payload = new CycleLinkerTypePayload(direction);
        } else if (held.is(ModItems.CONFIGURATOR.get())) {
            payload = new CycleConfiguratorTypePayload(direction);
        } else {
            return;
        }
        ClientPacketListener connection = minecraft.getConnection();
        if (connection == null || !connection.hasChannel(payload.type())) {
            return;
        }
        PacketDistributor.sendToServer(payload);
        event.setCanceled(true);
    }
}
