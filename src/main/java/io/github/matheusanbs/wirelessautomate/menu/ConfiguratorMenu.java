package io.github.matheusanbs.wirelessautomate.menu;

import io.github.matheusanbs.wirelessautomate.item.ConfiguratorItem;
import io.github.matheusanbs.wirelessautomate.network.RouterPreset;
import io.github.matheusanbs.wirelessautomate.packet.ConfiguratorActionPayload;
import io.github.matheusanbs.wirelessautomate.packet.ConfiguratorViewPayload;
import io.github.matheusanbs.wirelessautomate.preset.AreaActions;
import io.github.matheusanbs.wirelessautomate.preset.AreaClipboard;
import io.github.matheusanbs.wirelessautomate.preset.AreaOps;
import io.github.matheusanbs.wirelessautomate.preset.AreaSelection;
import io.github.matheusanbs.wirelessautomate.preset.PresetApplier;
import io.github.matheusanbs.wirelessautomate.preset.PresetCode;
import io.github.matheusanbs.wirelessautomate.preset.PresetCodes;
import io.github.matheusanbs.wirelessautomate.preset.PresetLibrary;
import io.github.matheusanbs.wirelessautomate.registry.ModDataComponents;
import io.github.matheusanbs.wirelessautomate.registry.ModMenus;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * Menu da tela do Configurador (Shift + clique direito no ar), sem slots. No servidor conhece a mão
 * com a varinha; no cliente guarda a última {@link ConfiguratorView}.
 *
 * <p>Sincronização: como nos outros menus, o vanilla só chama {@link #broadcastChanges()} no menu
 * aberto, então nada roda com a tela fechada. A visão é refeita quando a biblioteca, os componentes
 * da varinha ou o aviso mudam, e a cada {@value #REFRESH_TICKS} ticks (roteadores colocados ou
 * quebrados na área); só é enviada se ficou diferente da última.
 */
public class ConfiguratorMenu extends AbstractContainerMenu {
    private static final String KEY = "gui.wirelessautomate.configurator.";
    static final int REFRESH_TICKS = 20;

    private ConfiguratorView view;
    private int version;

    // Só no servidor.
    private final @Nullable ServerPlayer viewer;
    private final InteractionHand hand;
    private int ticks;
    private int seenLibrary = -1;
    private @Nullable Object seenPreset;
    private @Nullable Object seenSelection;
    private @Nullable Object seenClipboard;
    private boolean dirty;
    private Optional<ConfiguratorView.Export> export = Optional.empty();
    private Optional<Component> notice = Optional.empty();
    private int noticeId;

    /** Servidor. {@code view} é a visão mandada no buffer de abertura. */
    public ConfiguratorMenu(int containerId, ServerPlayer player, InteractionHand hand, ConfiguratorView view) {
        super(ModMenus.CONFIGURATOR.get(), containerId);
        this.viewer = player;
        this.hand = hand;
        this.view = view;
        markSeen();
    }

    /** Cliente: a visão inicial vem no buffer de abertura. */
    public ConfiguratorMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(containerId, ConfiguratorView.STREAM_CODEC.decode(buf));
    }

    /** Cliente (e a captura de tela de desenvolvimento). */
    public ConfiguratorMenu(int containerId, ConfiguratorView view) {
        super(ModMenus.CONFIGURATOR.get(), containerId);
        this.viewer = null;
        this.hand = InteractionHand.MAIN_HAND;
        this.view = view;
    }

    /** Abre a tela para a varinha na mão {@code hand}. */
    public static void open(ServerPlayer player, InteractionHand hand) {
        if (!(player.getItemInHand(hand).getItem() instanceof ConfiguratorItem)) {
            return;
        }
        ConfiguratorView view = initialView(player, hand);
        player.openMenu(new SimpleMenuProvider(
                        (containerId, inventory, p) -> new ConfiguratorMenu(containerId, player, hand, view),
                        Component.translatable(KEY + "title")),
                buf -> ConfiguratorView.STREAM_CODEC.encode(buf, view));
    }

    /** A visão de abertura, a mesma que vai no buffer. Pública para os GameTests. */
    public static ConfiguratorView initialView(ServerPlayer player, InteractionHand hand) {
        return build(player, hand, Optional.empty(), Optional.empty(), 0);
    }

    public ConfiguratorView view() {
        return view;
    }

    /** Muda a cada visão recebida: a tela compara para saber quando se atualizar. */
    public int version() {
        return version;
    }

    public void applyView(ConfiguratorView view) {
        this.view = view;
        version++;
    }

    public InteractionHand hand() {
        return hand;
    }

    private ItemStack wand() {
        return viewer == null ? ItemStack.EMPTY : viewer.getItemInHand(hand);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return viewer == null || player == viewer && player.getItemInHand(hand).getItem() instanceof ConfiguratorItem;
    }

    // ------------------------------------------------------------------ sincronização

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        ConfiguratorView changed = pollView();
        if (changed != null && viewer != null) {
            ConfiguratorViewPayload payload = new ConfiguratorViewPayload(containerId, changed);
            // Jogadores falsos (GameTests) não negociam os canais do mod.
            if (viewer.connection != null && viewer.connection.hasChannel(payload)) {
                PacketDistributor.sendToPlayer(viewer, payload);
            }
        }
    }

    /** Visão nova, se algo mudou e ela difere da última enviada, ou {@code null}. Público para os GameTests. */
    public @Nullable ConfiguratorView pollView() {
        if (viewer == null) {
            return null;
        }
        ticks++;
        if (!dirty && ticks % REFRESH_TICKS != 0 && !seenChanged()) {
            return null;
        }
        dirty = false;
        markSeen();
        ConfiguratorView next = build(viewer, hand, export, notice, noticeId);
        if (next.equals(view)) {
            return null;
        }
        view = next;
        return next;
    }

    private boolean seenChanged() {
        ItemStack stack = wand();
        return seenLibrary != PresetLibrary.get(viewer.server).version()
                || seenPreset != stack.get(ModDataComponents.PRESET.get())
                || seenSelection != stack.get(ModDataComponents.CONFIGURATOR_AREA.get())
                || seenClipboard != stack.get(ModDataComponents.AREA_CLIPBOARD.get());
    }

    private void markSeen() {
        ItemStack stack = wand();
        seenLibrary = PresetLibrary.get(viewer.server).version();
        seenPreset = stack.get(ModDataComponents.PRESET.get());
        seenSelection = stack.get(ModDataComponents.CONFIGURATOR_AREA.get());
        seenClipboard = stack.get(ModDataComponents.AREA_CLIPBOARD.get());
    }

    private static ConfiguratorView build(ServerPlayer player, InteractionHand hand,
            Optional<ConfiguratorView.Export> export, Optional<Component> notice, int noticeId) {
        ItemStack stack = player.getItemInHand(hand);
        RouterPreset preset = stack.get(ModDataComponents.PRESET.get());
        AreaSelection selection = AreaActions.selection(stack);
        AreaClipboard clipboard = AreaActions.clipboard(stack);
        Optional<BlockPos> anchor = local(player, selection.anchor());
        int hits = clipboard != null && anchor.isPresent() ? AreaOps.hits(player.serverLevel(), clipboard, anchor.get()) : 0;
        ConfiguratorView.Wand wand = new ConfiguratorView.Wand(selection.mode() == AreaSelection.Mode.AREA,
                preset == null ? -1 : preset.configuredFaces(), preset == null ? 0 : preset.distinctNetworks().size(),
                local(player, selection.corner1()), local(player, selection.corner2()), anchor,
                clipboard == null ? 0 : clipboard.size(), hits);

        List<ConfiguratorView.LibraryEntry> library = new ArrayList<>();
        for (PresetLibrary.Entry entry : PresetLibrary.get(player.server).presets(player.getUUID())) {
            library.add(ConfiguratorView.LibraryEntry.of(entry.name(), entry.preset()));
        }
        return new ConfiguratorView(wand, List.copyOf(library), area(player, selection), export, notice, noticeId);
    }

    private static Optional<BlockPos> local(ServerPlayer player, Optional<GlobalPos> pos) {
        return pos.filter(p -> p.dimension().equals(player.level().dimension())).map(GlobalPos::pos);
    }

    private static ConfiguratorView.Area area(ServerPlayer player, AreaSelection selection) {
        BoundingBox box = selection.box();
        AreaOps.Problem problem = AreaOps.check(player, selection);
        if (box == null || problem == AreaOps.Problem.OTHER_DIMENSION) {
            return problem == null ? ConfiguratorView.Area.NONE
                    : new ConfiguratorView.Area(Optional.of(problem.key), BlockPos.ZERO, BlockPos.ZERO, List.of(), List.of());
        }
        BlockPos min = new BlockPos(box.minX(), box.minY(), box.minZ());
        BlockPos size = new BlockPos(box.getXSpan(), box.getYSpan(), box.getZSpan());
        if (problem == AreaOps.Problem.TOO_LARGE) {
            // não varre uma área grande demais
            return new ConfiguratorView.Area(Optional.of(problem.key), min, size, List.of(), List.of());
        }
        List<ConfiguratorView.Dot> dots = new ArrayList<>();
        List<ResourceLocation> machines = new ArrayList<>();
        for (AreaOps.Found found : AreaOps.scan(player.serverLevel(), box)) {
            if (dots.size() >= ConfiguratorView.MAX_DOTS) {
                break;
            }
            int machine = machines.indexOf(found.machine());
            if (machine < 0 && machines.size() < ConfiguratorView.MAX_MACHINES) {
                machine = machines.size();
                machines.add(found.machine());
            }
            BlockPos pos = found.router().getBlockPos();
            dots.add(new ConfiguratorView.Dot(pos.getX() - min.getX(), pos.getY() - min.getY(), pos.getZ() - min.getZ(),
                    configured(found), machine));
        }
        return new ConfiguratorView.Area(Optional.ofNullable(problem).map(p -> p.key), min, size, List.copyOf(dots),
                List.copyOf(machines));
    }

    private static boolean configured(AreaOps.Found found) {
        return RouterPreset.copyOf(found.router()).configuredFaces() > 0;
    }

    private void notice(Component message) {
        notice = Optional.of(message);
        noticeId++;
        dirty = true;
    }

    // ------------------------------------------------------------------ ações

    /** Uma ação da tela aberta. Devolve se foi aceita (mesmo que tenha só gerado um aviso). */
    public static boolean handle(@Nullable ServerPlayer player, ConfiguratorActionPayload payload) {
        ConfiguratorMenu menu = player != null && player.containerMenu instanceof ConfiguratorMenu m
                && m.containerId == payload.containerId() ? m : null;
        if (menu == null || !menu.stillValid(player)) {
            return false;
        }
        return menu.act(player, payload);
    }

    private boolean act(ServerPlayer player, ConfiguratorActionPayload payload) {
        ItemStack stack = wand();
        PresetLibrary library = PresetLibrary.get(player.server);
        UUID id = player.getUUID();
        int index = payload.index();
        switch (payload.op()) {
            case SAVE -> {
                RouterPreset preset = stack.get(ModDataComponents.PRESET.get());
                if (preset == null) {
                    notice(Component.translatable(KEY + "save.nothing"));
                    return false;
                }
                PresetLibrary.Result result = library.add(id, payload.text(), preset, player.registryAccess());
                notice(libraryMessage(result, payload.text()));
                return result == PresetLibrary.Result.OK;
            }
            case RENAME -> {
                PresetLibrary.Result result = library.rename(id, index, payload.text());
                notice(libraryMessage(result, payload.text()));
                if (result == PresetLibrary.Result.OK) {
                    export = Optional.empty();
                }
                return result == PresetLibrary.Result.OK;
            }
            case DELETE -> {
                PresetLibrary.Entry entry = library.preset(id, index);
                if (entry == null || !library.remove(id, index)) {
                    return false;
                }
                export = Optional.empty();
                notice(Component.translatable(KEY + "deleted", entry.name()));
                return true;
            }
            case LOAD -> {
                PresetLibrary.Entry entry = library.preset(id, index);
                if (entry == null) {
                    return false;
                }
                stack.set(ModDataComponents.PRESET.get(), entry.preset());
                if (AreaActions.selection(stack).mode() != AreaSelection.Mode.BRUSH) {
                    AreaActions.setMode(stack, AreaSelection.Mode.BRUSH);
                }
                notice(Component.translatable(KEY + "loaded", entry.name()));
                return true;
            }
            case EXPORT -> {
                PresetLibrary.Entry entry = library.preset(id, index);
                if (entry == null) {
                    return false;
                }
                String code = PresetCodes.export(entry.name(), entry.preset(), player.registryAccess());
                if (!PresetCode.fits(code)) {
                    export = Optional.empty();
                    notice(Component.translatable(KEY + "code.export_too_large", PresetCode.MAX_CODE_LENGTH));
                    return false;
                }
                export = Optional.of(new ConfiguratorView.Export(index, entry.name(), code));
                notice(Component.translatable(KEY + "exported", entry.name(), code.length()));
                return true;
            }
            case IMPORT -> {
                return importCode(player, library, payload.text());
            }
            case CLEAR_WAND -> {
                boolean had = stack.remove(ModDataComponents.PRESET.get()) != null;
                notice(Component.translatable("item.wirelessautomate.configurator." + (had ? "cleared" : "empty")));
                return had;
            }
            case SET_MODE -> {
                if (index != 0 && index != 1) {
                    return false;
                }
                notice(AreaActions.setMode(stack, index == 1 ? AreaSelection.Mode.AREA : AreaSelection.Mode.BRUSH));
                return true;
            }
            case CLEAR_AREA -> {
                notice(AreaActions.clearCorners(stack));
                return true;
            }
            case COPY_AREA -> {
                notice(AreaActions.copy(player, stack));
                return true;
            }
            case CLEAR_CLIPBOARD -> {
                notice(AreaActions.clearClipboard(stack));
                return true;
            }
            case PASTE -> {
                notice(AreaActions.paste(player, stack));
                return true;
            }
            case APPLY_AREA -> {
                RouterPreset preset;
                if (index == -1) {
                    preset = stack.get(ModDataComponents.PRESET.get());
                } else {
                    PresetLibrary.Entry entry = library.preset(id, index);
                    preset = entry == null ? null : entry.preset();
                }
                if (preset == null) {
                    notice(Component.translatable(KEY + "apply.nothing"));
                    return false;
                }
                ResourceLocation machine = null;
                if (!payload.text().isEmpty()) {
                    machine = ResourceLocation.tryParse(payload.text());
                    if (machine == null) {
                        return false;
                    }
                }
                notice(AreaActions.apply(player, stack, preset, machine));
                return true;
            }
        }
        return false;
    }

    private boolean importCode(ServerPlayer player, PresetLibrary library, String code) {
        PresetCodes.Imported imported;
        try {
            imported = PresetCodes.importCode(code, player.registryAccess());
        } catch (PresetCode.InvalidCodeException e) {
            notice(Component.translatable(KEY + "code." + e.problem().key));
            return false;
        }
        PresetApplier.Checked checked = PresetApplier.check(player, imported.preset());
        String name = PresetLibrary.cleanName(imported.name());
        if (name == null) {
            name = Component.translatable(KEY + "imported_name").getString();
        }
        PresetLibrary.Result result = library.add(player.getUUID(), name, checked.preset(), player.registryAccess());
        if (result != PresetLibrary.Result.OK) {
            notice(libraryMessage(result, name));
            return false;
        }
        Component message = Component.translatable(KEY + "imported", name);
        if (imported.ignoredEntries() > 0) {
            message = message.copy().append(" · ")
                    .append(Component.translatable(KEY + "imported_ignored", imported.ignoredEntries()));
        }
        if (checked.droppedAny()) {
            message = message.copy().append(" · ").append(Component.translatable(KEY + "imported_networks"));
        }
        notice(message);
        return true;
    }

    private static Component libraryMessage(PresetLibrary.Result result, String name) {
        return switch (result) {
            case OK -> Component.translatable(KEY + "saved", PresetLibrary.cleanName(name));
            case FULL -> Component.translatable(KEY + "library.full", PresetLibrary.MAX_PRESETS);
            case TOO_LARGE -> Component.translatable(KEY + "library.too_large", PresetLibrary.MAX_PRESET_BYTES / 1024);
            case LIBRARY_FULL -> Component.translatable(KEY + "library.no_space", PresetLibrary.MAX_PLAYER_BYTES / 1024);
            case BAD_NAME -> Component.translatable(KEY + "library.bad_name", PresetLibrary.MAX_NAME);
            case MISSING -> Component.translatable(KEY + "library.missing");
        };
    }
}
