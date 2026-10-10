package io.github.matheusanbs.wirelessautomate.packet;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.menu.RecipeEditorSnapshot;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/** Servidor → cliente: o estado novo do editor de receitas aberto (receitas e pendências). */
public record RecipeEditorStatePayload(RecipeEditorSnapshot snapshot) implements CustomPacketPayload {
    public static final Type<RecipeEditorStatePayload> TYPE = new Type<>(WirelessAutomate.id("recipe_editor_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RecipeEditorStatePayload> STREAM_CODEC =
            StreamCodec.composite(RecipeEditorSnapshot.STREAM_CODEC, RecipeEditorStatePayload::snapshot,
                    RecipeEditorStatePayload::new);

    /** Manda ao jogador, se a conexão negociou o canal (jogadores falsos dos GameTests não). */
    public static void send(ServerPlayer player, RecipeEditorSnapshot snapshot) {
        RecipeEditorStatePayload payload = new RecipeEditorStatePayload(snapshot);
        if (player.connection != null && player.connection.hasChannel(payload)) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
