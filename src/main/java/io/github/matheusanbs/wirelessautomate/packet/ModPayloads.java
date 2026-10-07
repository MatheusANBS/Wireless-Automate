package io.github.matheusanbs.wirelessautomate.packet;

import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.filter.Filter;
import io.github.matheusanbs.wirelessautomate.filter.FilterEntry;
import io.github.matheusanbs.wirelessautomate.item.FilterCardItem;
import io.github.matheusanbs.wirelessautomate.menu.FilterMenu;
import io.github.matheusanbs.wirelessautomate.menu.FilterTarget;
import io.github.matheusanbs.wirelessautomate.menu.RouterFaceFilterTarget;
import io.github.matheusanbs.wirelessautomate.menu.RouterMenu;
import io.github.matheusanbs.wirelessautomate.network.NetworkSavedData;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.network.WaNetwork;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.jetbrains.annotations.Nullable;

/**
 * Registro dos pacotes do mod e os handlers. Código comum: os handlers do cliente só mexem no
 * {@link RouterMenu} e no {@link FilterMenu}, sem classes de tela.
 *
 * <p>Os handlers rodam na thread principal (o padrão do {@link PayloadRegistrar} é
 * {@code HandlerThread.MAIN}). Os do servidor só agem se o jogador está com a tela daquele roteador
 * aberta ({@code containerId} igual e {@link RouterMenu#stillValid}) e validam os valores; depois de
 * aplicar, o {@link RouterMenu#broadcastChanges()} do próprio menu manda o snapshot novo. A tela
 * de filtro segue a mesma regra com o {@link FilterMenu} e a {@link FilterViewPayload}.
 */
public final class ModPayloads {
    /** Versão do protocolo; mude quando um payload mudar de formato. */
    public static final String VERSION = "1";

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(VERSION);
        registrar.playToClient(RouterSnapshotPayload.TYPE, RouterSnapshotPayload.STREAM_CODEC,
                ModPayloads::onSnapshot);
        registrar.playToClient(RouterThroughputPayload.TYPE, RouterThroughputPayload.STREAM_CODEC,
                ModPayloads::onThroughput);
        registrar.playToServer(SetFacePayload.TYPE, SetFacePayload.STREAM_CODEC,
                (payload, context) -> handleSetFace(serverPlayer(context), payload));
        registrar.playToServer(SetNetworkPayload.TYPE, SetNetworkPayload.STREAM_CODEC,
                (payload, context) -> handleSetNetwork(serverPlayer(context), payload));
        registrar.playToServer(RenameRouterPayload.TYPE, RenameRouterPayload.STREAM_CODEC,
                (payload, context) -> handleRename(serverPlayer(context), payload));
        registrar.playToClient(FilterViewPayload.TYPE, FilterViewPayload.STREAM_CODEC, ModPayloads::onFilterView);
        registrar.playToServer(OpenFilterPayload.TYPE, OpenFilterPayload.STREAM_CODEC,
                (payload, context) -> handleOpenFilter(serverPlayer(context), payload));
        registrar.playToServer(EditFilterPayload.TYPE, EditFilterPayload.STREAM_CODEC,
                (payload, context) -> handleEditFilter(serverPlayer(context), payload));
        // Ingrediente fantasma do JEI (compat/jei); registrado sempre, com ou sem o JEI.
        registrar.playToServer(AddFilterEntryPayload.TYPE, AddFilterEntryPayload.STREAM_CODEC,
                (payload, context) -> handleAddFilterEntry(serverPlayer(context), payload));
    }

    private static @Nullable ServerPlayer serverPlayer(IPayloadContext context) {
        return context.player() instanceof ServerPlayer player ? player : null;
    }

    // Cliente.

    private static void onSnapshot(RouterSnapshotPayload payload, IPayloadContext context) {
        RouterMenu menu = openMenu(context.player(), payload.containerId());
        if (menu != null) {
            menu.applySnapshot(payload.snapshot());
        }
    }

    private static void onThroughput(RouterThroughputPayload payload, IPayloadContext context) {
        RouterMenu menu = openMenu(context.player(), payload.containerId());
        if (menu != null && payload.perType().length == ResourceType.values().length) {
            menu.applyThroughput(payload.perType());
        }
    }

    private static void onFilterView(FilterViewPayload payload, IPayloadContext context) {
        if (context.player() != null && context.player().containerMenu instanceof FilterMenu menu
                && menu.containerId == payload.containerId()) {
            menu.applyView(payload.view());
        }
    }

    private static @Nullable RouterMenu openMenu(@Nullable Player player, int containerId) {
        return player != null && player.containerMenu instanceof RouterMenu menu && menu.containerId == containerId
                ? menu
                : null;
    }

    // Servidor. Públicos para os GameTests; devolvem se aplicaram.

    /** Configura uma face. Recusa químicos (sem Mekanism) e limita a prioridade. */
    public static boolean handleSetFace(@Nullable ServerPlayer player, SetFacePayload payload) {
        RouterBlockEntity router = router(player, payload.containerId());
        if (router == null || payload.resource() == ResourceType.CHEMICAL) {
            return false;
        }
        int priority = Mth.clamp(payload.priority(), RouterMenu.MIN_PRIORITY, RouterMenu.MAX_PRIORITY);
        router.configureFace(payload.resource(), payload.face(), payload.mode(), priority, payload.redstone());
        return true;
    }

    /**
     * Muda a rede do roteador. Só aceita rede que existe e que o jogador pode usar (dono ou
     * operador nível 2, a mesma regra do Configurador), ou vazio para tirar da rede.
     */
    public static boolean handleSetNetwork(@Nullable ServerPlayer player, SetNetworkPayload payload) {
        RouterBlockEntity router = router(player, payload.containerId());
        if (router == null) {
            return false;
        }
        Optional<UUID> id = payload.network();
        if (id.isEmpty()) {
            router.setNetworkId(null);
            return true;
        }
        WaNetwork network = NetworkSavedData.get(player.server).network(id.get());
        if (network == null || !canUse(player, network)) {
            return false;
        }
        router.setNetworkId(network.id());
        return true;
    }

    /** Dá nome ao nó. Recusa nomes maiores que {@link RenameRouterPayload#MAX_LENGTH}. */
    public static boolean handleRename(@Nullable ServerPlayer player, RenameRouterPayload payload) {
        RouterBlockEntity router = router(player, payload.containerId());
        if (router == null || payload.name().strip().length() > RenameRouterPayload.MAX_LENGTH) {
            return false;
        }
        router.setName(payload.name());
        return true;
    }

    // Tela de filtro.

    /**
     * Botão Editar da tela do roteador: abre o filtro da face no lugar dela. Só itens e fluidos
     * (energia não usa filtro; químicos ainda não).
     */
    public static boolean handleOpenFilter(@Nullable ServerPlayer player, OpenFilterPayload payload) {
        RouterBlockEntity router = router(player, payload.containerId());
        if (router == null || (payload.resource() != ResourceType.ITEM && payload.resource() != ResourceType.FLUID)) {
            return false;
        }
        new RouterFaceFilterTarget(router, payload.resource(), payload.face()).open(player);
        return true;
    }

    /** Uma edição no filtro da tela aberta. Devolve se a edição foi aceita (mesmo sem mudar nada). */
    public static boolean handleEditFilter(@Nullable ServerPlayer player, EditFilterPayload payload) {
        FilterMenu menu = player != null && player.containerMenu instanceof FilterMenu m
                && m.containerId == payload.containerId() ? m : null;
        if (menu == null || menu.target() == null || !menu.stillValid(player)) {
            return false;
        }
        FilterTarget target = menu.target();
        Filter filter = target.filter();
        switch (payload.op()) {
            case ADD_TAG -> {
                ResourceLocation tag = parseTag(payload.text());
                if (tag == null) {
                    return false;
                }
                target.setFilter(filter.withEntry(new FilterEntry.TagEntry(tag, 0)));
            }
            case ADD_MOD -> {
                String mod = parseMod(payload.text());
                if (mod == null) {
                    return false;
                }
                target.setFilter(filter.withEntry(new FilterEntry.ModEntry(mod, 0)));
            }
            case REMOVE -> {
                if (!validIndex(filter, payload.index())) {
                    return false;
                }
                target.setFilter(filter.withoutEntry(payload.index()));
            }
            case SET_STOCK -> {
                if (!validIndex(filter, payload.index()) || payload.value() < 0
                        || payload.value() > Integer.MAX_VALUE) {
                    return false;
                }
                target.setFilter(filter.withStock(payload.index(), payload.value()));
            }
            case SET_LIST_MODE -> {
                if (payload.value() != 0 && payload.value() != 1) {
                    return false;
                }
                target.setFilter(filter.withListMode(payload.value() == 0
                        ? Filter.ListMode.WHITELIST : Filter.ListMode.BLACKLIST));
            }
            case SET_COMPONENTS -> {
                // Componentes só fazem sentido para itens.
                if (target.type() != ResourceType.ITEM || (payload.value() != 0 && payload.value() != 1)) {
                    return false;
                }
                target.setFilter(filter.withMatchComponents(payload.value() == 1));
            }
            case CLEAR -> target.setFilter(filter.cleared());
            case IMPORT_CARD -> {
                ItemStack card = player.getMainHandItem();
                if (!(target instanceof RouterFaceFilterTarget) || !FilterCardItem.isCard(card)) {
                    return false;
                }
                FilterCardItem.Contents contents = FilterCardItem.contents(card);
                if (contents.type() != target.type()) {
                    return false;
                }
                target.setFilter(contents.filter());
            }
            case EXPORT_CARD -> {
                ItemStack card = player.getMainHandItem();
                if (!(target instanceof RouterFaceFilterTarget) || !FilterCardItem.isCard(card)) {
                    return false;
                }
                FilterCardItem.setContents(card, new FilterCardItem.Contents(target.type(), filter));
            }
            case BACK -> {
                if (target instanceof RouterFaceFilterTarget face) {
                    RouterMenu.open(player, face.router());
                } else {
                    player.closeContainer();
                }
            }
        }
        return true;
    }

    /**
     * Ingrediente fantasma (JEI): acrescenta um item exato a um filtro de itens ou um fluido exato a
     * um de fluidos, sem o jogador ter o recurso. Normaliza para quantidade 1 e sem estoque; o
     * {@link Filter#withEntry} ignora duplicados e o teto. Devolve se a entrada foi aceita.
     */
    public static boolean handleAddFilterEntry(@Nullable ServerPlayer player, AddFilterEntryPayload payload) {
        FilterMenu menu = player != null && player.containerMenu instanceof FilterMenu m
                && m.containerId == payload.containerId() ? m : null;
        if (menu == null || menu.target() == null || !menu.stillValid(player)) {
            return false;
        }
        FilterTarget target = menu.target();
        FilterEntry entry = switch (payload.entry()) {
            case FilterEntry.ItemEntry e when target.type() == ResourceType.ITEM && !e.stack().isEmpty() ->
                    new FilterEntry.ItemEntry(e.stack(), 0);
            case FilterEntry.FluidEntry e when target.type() == ResourceType.FLUID && !e.stack().isEmpty() ->
                    new FilterEntry.FluidEntry(e.stack(), 0);
            default -> null;
        };
        if (entry == null) {
            return false;
        }
        target.setFilter(target.filter().withEntry(entry));
        return true;
    }

    /** {@code c:ingots} ou {@code #c:ingots}, em minúsculas; {@code null} se não for um id válido. */
    public static @Nullable ResourceLocation parseTag(String text) {
        String value = text.strip();
        if (value.startsWith("#")) {
            value = value.substring(1).strip();
        }
        if (value.isEmpty()) {
            return null;
        }
        ResourceLocation tag = ResourceLocation.tryParse(value.toLowerCase(Locale.ROOT));
        return tag == null || tag.getPath().isEmpty() ? null : tag;
    }

    /** {@code mekanism} ou {@code @mekanism}, em minúsculas; {@code null} se não for um namespace válido. */
    public static @Nullable String parseMod(String text) {
        String value = text.strip();
        if (value.startsWith("@")) {
            value = value.substring(1).strip();
        }
        value = value.toLowerCase(Locale.ROOT);
        return value.isEmpty() || value.length() > 64 || !ResourceLocation.isValidNamespace(value) ? null : value;
    }

    private static boolean validIndex(Filter filter, int index) {
        return index >= 0 && index < filter.entries().size();
    }

    public static boolean canUse(ServerPlayer player, WaNetwork network) {
        return network.owner().equals(player.getUUID()) || player.hasPermissions(2);
    }

    /** O roteador da tela aberta, se é ela que o pacote cita e ela ainda vale. */
    private static @Nullable RouterBlockEntity router(@Nullable ServerPlayer player, int containerId) {
        RouterMenu menu = openMenu(player, containerId);
        if (menu == null || menu.router() == null || !menu.stillValid(player)) {
            return null;
        }
        return menu.router();
    }

    private ModPayloads() {
    }
}
