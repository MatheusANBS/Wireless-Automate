package io.github.matheusanbs.wirelessautomate.gametest;

import com.mojang.authlib.GameProfile;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.net.CustomPacketPayload;
import io.github.matheusanbs.wirelessautomate.net.PacketParts;
import io.github.matheusanbs.wirelessautomate.net.PayloadRegistrar;
import io.github.matheusanbs.wirelessautomate.net.RegistryFriendlyByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.AttributeKey;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.MCRegisterPacketHandler;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PlayMessages;
import org.jetbrains.annotations.Nullable;

/**
 * Porte 1.20.1: o que o {@link GameTestHelper} do 1.21 tem e o do 1.20.1 não, com o mesmo comportamento, para os
 * testes continuarem parecidos com os do {@code main}. Troque {@code helper.x(...)} por
 * {@code GameTestCompat.x(helper, ...)}.
 */
final class GameTestCompat {
    /**
     * O canal do Forge que leva o pacote de abertura de tela ({@code NetworkHooks.openScreen}): o
     * {@code NetworkConstants.FML_PLAY_RESOURCE}, que é {@code fml:play} (não {@code forge:play}) e não é público.
     */
    private static final ResourceLocation FORGE_PLAY = new ResourceLocation("fml", "play");
    /** O índice do {@code PlayMessages.OpenContainer} no canal {@code fml:play} ({@code NetworkInitialization}). */
    private static final int OPEN_CONTAINER = 1;

    private GameTestCompat() {
    }

    /** O {@code helper.assertValueEqual(actual, expected, valueName)} do 1.21. */
    static <N> void assertValueEqual(GameTestHelper helper, N actual, N expected, String valueName) {
        if (!actual.equals(expected)) {
            throw new GameTestAssertException("Expected " + valueName + " to be " + expected + ", but was " + actual);
        }
    }

    /** O {@code helper.getBlockEntity(pos)} genérico do 1.21 (o do 1.20.1 devolve {@link BlockEntity}). */
    @SuppressWarnings("unchecked")
    static <T extends BlockEntity> T getBlockEntity(GameTestHelper helper, BlockPos pos) {
        return (T) helper.getBlockEntity(pos);
    }

    /** O {@code GameTestCompat.makeMockPlayer(helper, gameType)} do 1.21 (o do 1.20.1 é sempre criativo). */
    static Player makeMockPlayer(GameTestHelper helper, GameType gameType) {
        return new Player(helper.getLevel(), BlockPos.ZERO, 0.0F, new GameProfile(UUID.randomUUID(), "test-mock-player")) {
            @Override
            public boolean isSpectator() {
                return gameType == GameType.SPECTATOR;
            }

            @Override
            public boolean isCreative() {
                return gameType.isCreative();
            }

            @Override
            public boolean isLocalPlayer() {
                return true;
            }
        };
    }

    /**
     * O {@code GameTestCompat.makeMockServerPlayerInLevel(helper)} do 1.21: jogador criativo na lista de jogadores, com uma conexão
     * presa a um {@link EmbeddedChannel}. O do 1.20.1 cria a conexão sem canal, e o Forge (o
     * {@code NetworkHooks.sendMCRegistryPackets} do {@code placeNewPlayer}) dá NPE nela. Com o canal embutido, os
     * pacotes mandados ao jogador ficam na fila de saída do canal ({@link #lastOpenData} lê o de abertura de tela);
     * o canal do mod não é negociado, então {@code PacketDistributor.hasChannel} dá falso, como no {@code main}.
     */
    static ServerPlayer makeMockServerPlayerInLevel(GameTestHelper helper) {
        ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "test-mock-player")) {
            @Override
            public boolean isSpectator() {
                return false;
            }

            @Override
            public boolean isCreative() {
                return true;
            }
        };
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player);
        return player;
    }

    /**
     * Os dados de abertura da última tela aberta para o jogador de {@link #makeMockServerPlayerInLevel}, lidos do
     * pacote do Forge na fila de saída, como o cliente os leria; {@code null} se nenhuma tela abriu. Esvazia a fila.
     * No {@code main}, o jogador falso guardava esses dados ao abrir a tela (o {@code openMenu} do NeoForge é um
     * método do jogador; o do Forge é estático, no {@code NetworkHooks}).
     */
    static @Nullable RegistryFriendlyByteBuf lastOpenData(ServerPlayer player) {
        if (!(player.connection.connection.channel() instanceof EmbeddedChannel channel)) {
            return null;
        }
        RegistryFriendlyByteBuf last = null;
        Object message;
        while ((message = channel.readOutbound()) != null) {
            if (message instanceof ClientboundCustomPayloadPacket packet && FORGE_PLAY.equals(packet.getIdentifier())) {
                FriendlyByteBuf data = new FriendlyByteBuf(packet.getData().copy());
                if (data.readUnsignedByte() == OPEN_CONTAINER) {
                    FriendlyByteBuf extra = PlayMessages.OpenContainer.decode(data).getAdditionalData();
                    last = new RegistryFriendlyByteBuf(extra);
                }
            }
        }
        return last;
    }

    /**
     * Faz o jogador de {@link #makeMockServerPlayerInLevel} parecer um cliente com o mod: o canal do mod passa a
     * constar como registrado do outro lado da conexão (a lista {@code minecraft:register} que o Forge guarda no
     * canal netty), então {@code PacketDistributor.hasChannel} dá verdadeiro e os pacotes do mod vão de verdade
     * para a fila de saída ({@link #drain} os lê). Sem isso, como no {@code main}, os menus não mandam nada ao
     * jogador falso.
     */
    static void negotiateModChannel(ServerPlayer player) {
        io.netty.channel.Channel channel = player.connection.connection.channel();
        AttributeKey<MCRegisterPacketHandler.ChannelList> key = AttributeKey.valueOf("minecraft:netregistry");
        channel.attr(key).setIfAbsent(new MCRegisterPacketHandler.ChannelList());
        byte[] name = (WirelessAutomate.id("main") + "\0").getBytes(StandardCharsets.UTF_8);
        channel.attr(key).get().updateFrom(() -> null, new FriendlyByteBuf(Unpooled.wrappedBuffer(name)),
                NetworkEvent.RegistrationChangeType.REGISTER);
    }

    /**
     * O que foi mandado ao jogador de {@link #makeMockServerPlayerInLevel} desde a última leitura, como o cliente
     * leria: os dados da última abertura de tela, os payloads do mod (as partes juntadas pelo mesmo leitor do
     * canal) e o tamanho dos dados de cada pacote do canal do mod. Esvazia a fila.
     */
    record Sent(@Nullable RegistryFriendlyByteBuf openData, int openSize, List<CustomPacketPayload> payloads,
            List<Integer> modPacketSizes) {
        /** Os payloads de uma classe, na ordem em que chegaram. */
        <T> List<T> of(Class<T> type) {
            return payloads.stream().filter(type::isInstance).map(type::cast).toList();
        }
    }

    static Sent drain(ServerPlayer player) {
        if (!(player.connection.connection.channel() instanceof EmbeddedChannel channel)) {
            throw new GameTestAssertException("jogador sem canal embutido");
        }
        ResourceLocation mod = WirelessAutomate.id("main");
        PacketParts.Assembly assembly = new PacketParts.Assembly(PayloadRegistrar.MAX_ASSEMBLED_TO_CLIENT);
        RegistryFriendlyByteBuf openData = null;
        int openSize = -1;
        List<CustomPacketPayload> payloads = new ArrayList<>();
        List<Integer> sizes = new ArrayList<>();
        Object message;
        while ((message = channel.readOutbound()) != null) {
            if (!(message instanceof ClientboundCustomPayloadPacket packet)) {
                continue;
            }
            if (FORGE_PLAY.equals(packet.getIdentifier())) {
                FriendlyByteBuf data = new FriendlyByteBuf(packet.getData().copy());
                if (data.readUnsignedByte() == OPEN_CONTAINER) {
                    FriendlyByteBuf extra = PlayMessages.OpenContainer.decode(data).getAdditionalData();
                    openSize = extra.readableBytes();
                    openData = new RegistryFriendlyByteBuf(extra);
                }
            } else if (mod.equals(packet.getIdentifier())) {
                FriendlyByteBuf data = new FriendlyByteBuf(packet.getData().copy());
                sizes.add(data.readableBytes());
                CustomPacketPayload payload = PayloadRegistrar.receive(false, assembly, data);
                if (payload != null) {
                    payloads.add(payload);
                }
            }
        }
        if (assembly.pending()) {
            throw new GameTestAssertException("partes de um pacote do mod sem a última");
        }
        return new Sent(openData, openSize, payloads, sizes);
    }

    /**
     * O {@code GameTestCompat.craftingInput(width, height, items)} do 1.21: no 1.20.1, as receitas leem um {@link CraftingContainer}.
     * A grade fica presa a um menu vazio (o {@link TransientCraftingContainer} avisa o menu a cada mudança).
     */
    static CraftingContainer craftingInput(int width, int height, List<ItemStack> items) {
        AbstractContainerMenu menu = new AbstractContainerMenu(null, -1) {
            @Override
            public ItemStack quickMoveStack(Player player, int index) {
                return ItemStack.EMPTY;
            }

            @Override
            public boolean stillValid(Player player) {
                return false;
            }
        };
        CraftingContainer grid = new TransientCraftingContainer(menu, width, height);
        for (int i = 0; i < items.size(); i++) {
            grid.setItem(i, items.get(i));
        }
        return grid;
    }
}
