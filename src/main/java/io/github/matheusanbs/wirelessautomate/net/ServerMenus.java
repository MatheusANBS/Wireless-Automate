package io.github.matheusanbs.wirelessautomate.net;

import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

/**
 * Abrir um menu com dados de abertura. Imita o {@code ServerPlayer.openMenu(MenuProvider, Consumer)} do NeoForge
 * 1.21 sobre o {@link NetworkHooks#openScreen} do Forge: troque {@code player.openMenu(provider, buf -> ...)} por
 * {@code ServerMenus.openMenu(player, provider, buf -> ...)}.
 *
 * <p>Diferença que importa: o Forge escreve os dados de abertura <b>antes</b> de criar o menu, e o NeoForge
 * <b>depois</b> (o Tablet escreve o snapshot que o próprio menu montou). Aqui o menu é criado primeiro, dentro
 * do escritor (com o id que o Forge acabou de reservar, {@code player.containerCounter}), e o Forge recebe esse
 * mesmo menu. O buffer chega como {@link RegistryFriendlyByteBuf}. O limite do Forge para os dados de abertura é
 * de 32600 bytes, o mesmo do NeoForge.
 */
public final class ServerMenus {
    private ServerMenus() {
    }

    public static void openMenu(ServerPlayer player, MenuProvider provider,
            Consumer<RegistryFriendlyByteBuf> extraDataWriter) {
        AbstractContainerMenu[] created = new AbstractContainerMenu[1];
        MenuProvider once = new MenuProvider() {
            @Override
            public Component getDisplayName() {
                return provider.getDisplayName();
            }

            @Override
            public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player p) {
                return created[0] != null ? created[0] : provider.createMenu(containerId, inventory, p);
            }
        };
        NetworkHooks.openScreen(player, once, buf -> {
            created[0] = provider.createMenu(player.containerCounter, player.getInventory(), player);
            if (created[0] != null) {
                extraDataWriter.accept(RegistryFriendlyByteBuf.wrap(buf));
            }
        });
    }
}
