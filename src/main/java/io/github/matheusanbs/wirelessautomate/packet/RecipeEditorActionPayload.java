package io.github.matheusanbs.wirelessautomate.packet;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.menu.RecipeEditorMenu;
import io.github.matheusanbs.wirelessautomate.menu.RecipeEditorSnapshot;
import io.github.matheusanbs.wirelessautomate.recipe.edit.RecipeDraft;
import io.github.matheusanbs.wirelessautomate.recipe.edit.RecipeEditor;
import java.util.Optional;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.codec.NeoForgeStreamCodecs;
import org.jetbrains.annotations.Nullable;

/**
 * Cliente → servidor: uma ação do editor de receitas. O rascunho só viaja em {@code SAVE} (nos outros é
 * {@code null}); em {@code RELOAD} o id também é ignorado.
 */
public record RecipeEditorActionPayload(Action action, ResourceLocation id, @Nullable RecipeDraft draft)
        implements CustomPacketPayload {
    public enum Action { SAVE, DISABLE, ENABLE, RESTORE, RELOAD }

    /** Id que o cliente manda em {@code RELOAD}. */
    private static final ResourceLocation NO_ID = WirelessAutomate.id("none");

    public static final Type<RecipeEditorActionPayload> TYPE = new Type<>(WirelessAutomate.id("recipe_editor_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RecipeEditorActionPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                NeoForgeStreamCodecs.enumCodec(Action.class).encode(buf, payload.action());
                buf.writeResourceLocation(payload.id());
                if (payload.action() == Action.SAVE) {
                    RecipeEditorSnapshot.DRAFT_CODEC.encode(buf, payload.draft());
                }
            },
            buf -> {
                Action action = NeoForgeStreamCodecs.enumCodec(Action.class).decode(buf);
                ResourceLocation id = buf.readResourceLocation();
                RecipeDraft draft = action == Action.SAVE ? RecipeEditorSnapshot.DRAFT_CODEC.decode(buf) : null;
                return new RecipeEditorActionPayload(action, id, draft);
            });

    public static RecipeEditorActionPayload save(ResourceLocation id, RecipeDraft draft) {
        return new RecipeEditorActionPayload(Action.SAVE, id, draft);
    }

    public static RecipeEditorActionPayload of(Action action, ResourceLocation id) {
        return new RecipeEditorActionPayload(action, id, null);
    }

    public static RecipeEditorActionPayload reload() {
        return new RecipeEditorActionPayload(Action.RELOAD, NO_ID, null);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * Servidor: confere permissão 2 e o editor aberto (recusa em silêncio, só log em debug), executa a ação,
     * mostra o erro na barra de ação e devolve o snapshot novo. Público para os GameTests.
     */
    public static void handle(@Nullable ServerPlayer player, RecipeEditorActionPayload payload) {
        if (player == null) {
            return;
        }
        if (!player.hasPermissions(2) || !(player.containerMenu instanceof RecipeEditorMenu)) {
            WirelessAutomate.LOGGER.debug("Ação do editor de receitas recusada para {}", player.getGameProfile().getName());
            return;
        }
        MinecraftServer server = player.server;
        Optional<Component> error = switch (payload.action()) {
            case SAVE -> payload.draft() == null ? Optional.empty()
                    : RecipeEditor.save(server, payload.id(), payload.draft());
            case DISABLE -> RecipeEditor.setDisabled(server, payload.id(), true);
            case ENABLE -> RecipeEditor.setDisabled(server, payload.id(), false);
            case RESTORE -> RecipeEditor.restore(server, payload.id());
            case RELOAD -> Optional.empty();
        };
        error.ifPresent(message -> player.displayClientMessage(message, true));
        if (payload.action() == Action.RELOAD) {
            RecipeEditor.reload(server).thenRunAsync(() -> RecipeEditorMenu.broadcast(server, true), server);
            return;
        }
        RecipeEditorStatePayload.send(player, RecipeEditorSnapshot.of(server));
    }
}
