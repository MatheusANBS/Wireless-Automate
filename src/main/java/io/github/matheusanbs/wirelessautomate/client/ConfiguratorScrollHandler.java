package io.github.matheusanbs.wirelessautomate.client;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.packet.CycleConfiguratorTypePayload;
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
 * Shift + roda do mouse com o Configurador na mão principal troca o tipo que ele cola (Todos e um por tipo
 * carregado: Itens, Fluidos, Energia e, com os mods, Químicos e Source). Só no jogo, sem tela aberta; o
 * evento é cancelado para a roda não trocar o slot da hotbar. Como na hotbar, rolar para baixo vai para o
 * próximo. O servidor troca o tipo e mostra o novo na action bar.
 */
@EventBusSubscriber(modid = WirelessAutomate.MODID, value = Dist.CLIENT)
public final class ConfiguratorScrollHandler {
    private ConfiguratorScrollHandler() {
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
        if (!held.is(ModItems.CONFIGURATOR.get())) {
            return;
        }
        CustomPacketPayload payload = new CycleConfiguratorTypePayload(direction);
        ClientPacketListener connection = minecraft.getConnection();
        if (connection == null || !connection.hasChannel(payload.type())) {
            return;
        }
        PacketDistributor.sendToServer(payload);
        event.setCanceled(true);
    }
}
