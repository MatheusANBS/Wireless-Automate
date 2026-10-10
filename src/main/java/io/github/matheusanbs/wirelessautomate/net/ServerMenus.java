package io.github.matheusanbs.wirelessautomate.net;

import com.mojang.logging.LogUtils;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Abrir um menu com dados de abertura. Imita o {@code ServerPlayer.openMenu(MenuProvider, Consumer)} do NeoForge
 * 1.21 sobre o {@link NetworkHooks#openScreen} do Forge: troque {@code player.openMenu(provider, buf -> ...)} por
 * {@code ServerMenus.openMenu(player, provider, buf -> ...)}.
 *
 * <p><b>Ordem:</b> o Forge escreve os dados de abertura <b>antes</b> de criar o menu, e o NeoForge <b>depois</b>
 * (o Tablet escreve o snapshot que o próprio menu montou). Aqui o menu é criado primeiro, com o id que o Forge vai
 * reservar ({@code containerCounter % 100 + 1}, a conta do {@code nextContainerCounter} vanilla), os dados são
 * escritos e medidos, e o Forge recebe esse mesmo menu.
 *
 * <p><b>Tamanho:</b> o Forge recusa dados de abertura acima de 32600 bytes com uma exceção (o NeoForge 1.21
 * divide o pacote). Toda tela cujos dados podem crescer dá uma versão reduzida ({@code reducedWriter}) que cabe
 * no teto por construção (só o cabeçalho e o que tem tamanho limitado): se os dados completos passam do teto, ela
 * vai na abertura e o resto chega logo depois pelos pacotes de {@code followUp} (o mesmo caminho das atualizações
 * da tela, que vão em partes quando passam do teto de um pacote). As outras telas abrem com dados de tamanho fixo.
 * Nenhuma tela deixa de abrir por tamanho; uma versão reduzida acima do teto é erro de programação
 * ({@link IllegalStateException}).
 */
public final class ServerMenus {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** Teto do Forge para os dados de abertura (o {@code openScreen} soma o VarInt do tamanho). */
    public static final int MAX_OPEN_DATA = 32600 - 5;

    private ServerMenus() {
    }

    /** Tela com dados de abertura de tamanho fixo (sem versão reduzida). */
    public static void openMenu(ServerPlayer player, MenuProvider provider,
            Consumer<RegistryFriendlyByteBuf> extraDataWriter) {
        openMenu(player, provider, extraDataWriter, null, id -> List.of());
    }

    /**
     * @param reducedWriter dados que cabem no teto por construção, para quando os completos passam de
     *                      {@link #MAX_OPEN_DATA}; nulo só se os completos têm tamanho fixo
     * @param followUp      pacotes que completam a tela aberta com os dados reduzidos (recebe o {@code containerId})
     */
    public static void openMenu(ServerPlayer player, MenuProvider provider,
            Consumer<RegistryFriendlyByteBuf> extraDataWriter, @Nullable Consumer<RegistryFriendlyByteBuf> reducedWriter,
            IntFunction<List<? extends CustomPacketPayload>> followUp) {
        if (player.level().isClientSide) {
            return;
        }
        int containerId = player.containerCounter % 100 + 1;
        AbstractContainerMenu menu = provider.createMenu(containerId, player.getInventory(), player);
        if (menu == null) {
            return;
        }
        RegistryFriendlyByteBuf data = write(extraDataWriter);
        boolean reduced = false;
        if (data.readableBytes() > MAX_OPEN_DATA) {
            int full = data.readableBytes();
            if (reducedWriter == null) {
                throw new IllegalStateException("Tela " + provider.getDisplayName().getString()
                        + " sem versão reduzida com " + full + " bytes de abertura (teto " + MAX_OPEN_DATA + ")");
            }
            data = write(reducedWriter);
            if (data.readableBytes() > MAX_OPEN_DATA) {
                throw new IllegalStateException("Versão reduzida da tela " + provider.getDisplayName().getString()
                        + " com " + data.readableBytes() + " bytes passa do teto de " + MAX_OPEN_DATA);
            }
            reduced = true;
        }
        RegistryFriendlyByteBuf bytes = data;
        AbstractContainerMenu[] opened = {menu};
        NetworkHooks.openScreen(player, new MenuProvider() {
            @Override
            public Component getDisplayName() {
                return provider.getDisplayName();
            }

            @Override
            public @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory, Player p) {
                return opened[0];
            }
        }, buf -> {
            if (player.containerCounter != containerId) {
                // Não acontece com o ServerPlayer vanilla; se outro código mudar a conta, o menu nasce de novo
                // com o id certo (os dados já escritos continuam valendo para o cliente).
                LOGGER.warn("Id de menu inesperado: {} em vez de {}", player.containerCounter, containerId);
                opened[0] = provider.createMenu(player.containerCounter, player.getInventory(), player);
            }
            buf.writeBytes(bytes, bytes.readerIndex(), bytes.readableBytes());
        });
        if (reduced && opened[0] != null && player.containerMenu == opened[0]) {
            for (CustomPacketPayload payload : followUp.apply(opened[0].containerId)) {
                if (player.connection != null && PacketDistributor.hasChannel(player.connection, payload)) {
                    PacketDistributor.sendToPlayer(player, payload);
                }
            }
        }
    }

    private static RegistryFriendlyByteBuf write(Consumer<RegistryFriendlyByteBuf> writer) {
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer());
        writer.accept(buf);
        return buf;
    }
}
