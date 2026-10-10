package io.github.matheusanbs.wirelessautomate.menu;

import io.github.matheusanbs.wirelessautomate.packet.RecipeEditorStatePayload;
import io.github.matheusanbs.wirelessautomate.recipe.edit.RecipeEditor;
import io.github.matheusanbs.wirelessautomate.registry.ModMenus;
import java.util.function.Consumer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;

/**
 * Menu do editor de receitas ({@code /wa recipes}): só o inventário do jogador como slots, para a tela pegar
 * itens dele. Nada se move. As receitas e as ações passam pelos payloads, sem tick nem sincronização com a
 * tela fechada. Ao fechar com pendências, o servidor recarrega as receitas.
 */
public class RecipeEditorMenu extends AbstractContainerMenu {
    public static final int INVENTORY_X = 81;
    public static final int INVENTORY_Y = 180;
    public static final int HOTBAR_Y = 238;

    private RecipeEditorSnapshot snapshot;
    private @Nullable Consumer<RecipeEditorSnapshot> listener;

    /** Servidor, e o cliente depois de ler o buffer. */
    public RecipeEditorMenu(int containerId, Inventory inventory, RecipeEditorSnapshot snapshot) {
        super(ModMenus.RECIPE_EDITOR.get(), containerId);
        this.snapshot = snapshot;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, INVENTORY_X + col * 18, INVENTORY_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, INVENTORY_X + col * 18, HOTBAR_Y));
        }
    }

    /** Cliente: o snapshot inicial vem no buffer de abertura. */
    public RecipeEditorMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(containerId, inventory, RecipeEditorSnapshot.STREAM_CODEC.decode(buf));
    }

    /** Servidor: abre o editor para o jogador (o comando confere a permissão). */
    public static void open(ServerPlayer player) {
        RecipeEditorSnapshot snapshot = RecipeEditorSnapshot.of(player.server);
        player.openMenu(new SimpleMenuProvider(
                        (containerId, inventory, p) -> new RecipeEditorMenu(containerId, inventory, snapshot),
                        Component.translatable("gui.wirelessautomate.recipes.title")),
                buf -> RecipeEditorSnapshot.STREAM_CODEC.encode(buf, snapshot));
    }

    public RecipeEditorSnapshot snapshot() {
        return snapshot;
    }

    /** A tela se registra aqui para saber de cada snapshot novo (como no Vinculador). */
    public void setListener(@Nullable Consumer<RecipeEditorSnapshot> listener) {
        this.listener = listener;
    }

    public void applySnapshot(RecipeEditorSnapshot snapshot) {
        this.snapshot = snapshot;
        if (listener != null) {
            listener.accept(snapshot);
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.isAlive() && player.hasPermissions(2);
    }

    /** No servidor, fechar com receitas salvas recarrega os recursos e manda o estado aos outros editores abertos. */
    @Override
    public void removed(Player player) {
        super.removed(player);
        // Fechar ao sair do jogo ou com o servidor parando também passa aqui: só recarrega com ele rodando.
        if (player instanceof ServerPlayer serverPlayer && RecipeEditor.pending() > 0 && serverPlayer.server.isRunning()) {
            MinecraftServer server = serverPlayer.server;
            RecipeEditor.reload(server).thenRunAsync(() -> broadcast(server, false), server);
        }
    }

    /** Servidor: manda o snapshot atual a todo jogador com o editor aberto (e a mensagem de recarga, se pedida). */
    public static void broadcast(MinecraftServer server, boolean announce) {
        RecipeEditorSnapshot snapshot = RecipeEditorSnapshot.of(server);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.containerMenu instanceof RecipeEditorMenu) {
                RecipeEditorStatePayload.send(player, snapshot);
                if (announce) {
                    player.displayClientMessage(Component.translatable("gui.wirelessautomate.recipes.reloaded"), true);
                }
            }
        }
    }

    /** Cliente: o snapshot novo vai ao menu aberto. */
    public static void onState(RecipeEditorStatePayload payload, IPayloadContext context) {
        if (context.player() != null && context.player().containerMenu instanceof RecipeEditorMenu menu) {
            menu.applySnapshot(payload.snapshot());
        }
    }
}
