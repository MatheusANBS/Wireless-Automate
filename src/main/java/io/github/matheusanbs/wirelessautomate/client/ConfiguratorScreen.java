package io.github.matheusanbs.wirelessautomate.client;

import io.github.matheusanbs.wirelessautomate.menu.ConfiguratorMenu;
import io.github.matheusanbs.wirelessautomate.menu.ConfiguratorView;
import io.github.matheusanbs.wirelessautomate.menu.ConfiguratorView.Area;
import io.github.matheusanbs.wirelessautomate.menu.ConfiguratorView.Dot;
import io.github.matheusanbs.wirelessautomate.menu.ConfiguratorView.FaceLine;
import io.github.matheusanbs.wirelessautomate.menu.ConfiguratorView.LibraryEntry;
import io.github.matheusanbs.wirelessautomate.menu.ConfiguratorView.Wand;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.RelativeSide;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.packet.ConfiguratorActionPayload;
import io.github.matheusanbs.wirelessautomate.packet.ConfiguratorActionPayload.Op;
import io.github.matheusanbs.wirelessautomate.preset.AreaOps;
import io.github.matheusanbs.wirelessautomate.preset.PresetCode;
import io.github.matheusanbs.wirelessautomate.preset.PresetLibrary;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * Tela do Configurador (especificação, "Telas da interface › Configurador"), aberta com Shift +
 * clique direito no ar. Duas abas:
 * <ul>
 *   <li><b>Biblioteca:</b> a lista de presets do jogador, o preset selecionado com Aplicar (a
 *       varinha passa a colar esse) e Exportar código (com Copiar), Apagar, e recolhidos em "Salvar
 *       ou importar": salvar a cópia da varinha com um nome, renomear e importar um código {@code WA1:}.</li>
 *   <li><b>Área:</b> o que os cliques da varinha fazem (Pincel ou Área), um mapa de cima da área
 *       marcada, e os modos Copiar área (copiar, depois colar na origem escolhida no mundo) e
 *       Aplicar em área (um preset, opcionalmente só nos roteadores presos a um tipo de máquina).</li>
 * </ul>
 * Tudo vem de {@link ConfiguratorMenu#view()}; cada ação vira um {@link ConfiguratorActionPayload}
 * e o servidor responde com a visão nova e um aviso, mostrado no rodapé.
 */
public class ConfiguratorScreen extends AbstractContainerScreen<ConfiguratorMenu> {
    private static final int W = 300;
    private static final int H = 232;
    private static final int X0 = 9;
    private static final int X1 = W - 9;
    private static final int HEAD_Y = 8;
    private static final int TABS_Y = 23;
    private static final int TAB_H = 15;
    private static final int SEP_Y = 42;
    private static final int TOP = 48;
    private static final int BTN_H = 14;
    // biblioteca: lista e o preset selecionado
    private static final int LIST_W = 128;
    private static final int ROW_H = 22;
    private static final int ROWS = 5;
    private static final int LIST_H = ROWS * ROW_H + 2;
    private static final int RX = X0 + LIST_W + 6;
    private static final int RW = X1 - RX;
    private static final int PAD = 5;
    private static final int IX = RX + PAD;
    private static final int IW = RW - 2 * PAD;
    private static final int APPLY_Y = TOP + LIST_H - 2 * BTN_H - 7;
    private static final int EXPORT_Y = APPLY_Y + BTN_H + 2;
    private static final int WAND_Y = TOP + LIST_H + 5;
    private static final int CODE_Y = WAND_Y + 15;
    private static final int MORE_Y = CODE_Y + 19;
    private static final int NOTICE_Y = H - 13;
    // recolhido: salvar ou importar (no lugar do preset selecionado)
    private static final int NAME_Y = TOP + 13;
    private static final int SAVE_Y = NAME_Y + 17;
    private static final int IMPORT_LABEL_Y = SAVE_Y + 20;
    private static final int IMPORT_Y = IMPORT_LABEL_Y + 10;
    private static final int IMPORT_BTN_Y = IMPORT_Y + 17;
    // área
    private static final int CLICKS_Y = TOP;
    private static final int MODE_Y = TOP + 18;
    private static final int MAP_Y = MODE_Y + 20;
    private static final int MAP_W = 128;
    private static final int MAP_H = NOTICE_Y - 6 - MAP_Y;
    private static final int AX = X0 + MAP_W + 6;
    private static final int AW = X1 - AX;
    private static final int AREA_BTN2_Y = MAP_Y + MAP_H - BTN_H;
    private static final int AREA_BTN1_Y = AREA_BTN2_Y - BTN_H - 3;

    private static final int ACCENT = 0xFF45D6CC;
    private static final int DANGER = 0xFFE5534B;
    private static final int OK = 0xFF41C96B;
    private static final int IDLE = 0xFF5A6270;
    private static final int SEL_BOX = 0xFF151A21;
    private static final long CONFIRM_MS = 3000;
    private static final long NOTICE_MS = 10_000;

    private enum Tab { LIBRARY, AREA }

    private final boolean preview;
    private final List<FlatButton> buttons = new ArrayList<>();
    private final List<FlatButton> libraryButtons = new ArrayList<>();
    private final List<FlatButton> areaButtons = new ArrayList<>();

    private int lastVersion = -1;
    private Tab tab = Tab.LIBRARY;
    private int selected;
    private int scroll;
    private boolean moreOpen;
    private boolean applyMode;
    /** Preset de Aplicar em área: −1 = a cópia da varinha, senão o índice na biblioteca. */
    private int applySource = -1;
    /** Máquina de Aplicar em área: −1 = todas, senão o índice em {@link Area#machines()}. */
    private int machineFilter = -1;
    private long deleteArmedUntil;
    private int seenNoticeId = -1;
    private long noticeUntil;
    /** Aviso local (copiar não passa pelo servidor); some quando chega um aviso novo. */
    private @Nullable Component noticeOverride;
    private String nameDraft = "";
    private String codeDraft = "";

    private FlatButton libraryTab;
    private FlatButton areaTab;
    private FlatButton applyButton;
    private FlatButton exportButton;
    private FlatButton deleteButton;
    private FlatButton clearWandButton;
    private FlatButton copyCodeButton;
    private FlatButton moreButton;
    private FlatButton saveButton;
    private FlatButton renameButton;
    private FlatButton pasteCodeButton;
    private FlatButton importButton;
    private EditBox nameBox;
    private EditBox codeBox;
    private EditBox exportBox;
    private FlatButton brushButton;
    private FlatButton areaModeButton;
    private FlatButton copyModeButton;
    private FlatButton applyModeButton;
    private FlatButton sourceButton;
    private FlatButton machineButton;
    private FlatButton areaPrimaryButton;
    private FlatButton areaSecondaryButton;

    public ConfiguratorScreen(ConfiguratorMenu menu, Inventory inventory, Component title) {
        this(menu, inventory, title, false);
    }

    /** @param preview sem servidor (captura de desenvolvimento): as ações não vão a lugar nenhum */
    public ConfiguratorScreen(ConfiguratorMenu menu, Inventory inventory, Component title, boolean preview) {
        super(menu, inventory, title);
        this.preview = preview;
        this.imageWidth = W;
        this.imageHeight = H;
    }

    // ------------------------------------------------------------------ estado

    private ConfiguratorView view() {
        return menu.view();
    }

    private List<LibraryEntry> library() {
        return view().library();
    }

    private @Nullable LibraryEntry selectedEntry() {
        return selected >= 0 && selected < library().size() ? library().get(selected) : null;
    }

    private static Component tr(String key, Object... args) {
        return Component.translatable("gui.wirelessautomate.configurator." + key, args);
    }

    private static Component typeName(ResourceType type) {
        return Component.translatable("gui.wirelessautomate.router.type." + type.name().toLowerCase(Locale.ROOT));
    }

    private static Component sideName(RelativeSide side) {
        return tr("side." + side.key());
    }

    private static Component modeName(PortMode mode) {
        return Component.translatable("gui.wirelessautomate.router.mode." + mode.name().toLowerCase(Locale.ROOT));
    }

    private static Component machineName(ResourceLocation id) {
        return BuiltInRegistries.BLOCK.get(id).getName();
    }

    private void syncView() {
        lastVersion = menu.version();
        int size = library().size();
        selected = size == 0 ? -1 : Math.max(0, Math.min(selected, size - 1));
        scroll = Math.max(0, Math.min(scroll, Math.max(0, size - ROWS)));
        if (applySource >= size || (applySource == -1 && !view().wand().hasPreset() && size > 0)) {
            applySource = view().wand().hasPreset() || size == 0 ? -1 : 0;
        }
        if (machineFilter >= view().area().machines().size()) {
            machineFilter = -1;
        }
        if (view().noticeId() != seenNoticeId) {
            if (seenNoticeId >= 0 || view().notice().isPresent()) {
                noticeUntil = Util.getMillis() + NOTICE_MS;
            }
            seenNoticeId = view().noticeId();
        }
        if (exportBox != null) {
            exportBox.setValue(view().export().map(ConfiguratorView.Export::code).orElse(""));
            exportBox.moveCursorToStart(false);
        }
    }

    private boolean deleteArmed() {
        return deleteArmedUntil > Util.getMillis();
    }

    // ------------------------------------------------------------------ montagem

    @Override
    protected void init() {
        super.init();
        buttons.clear();
        libraryButtons.clear();
        areaButtons.clear();
        int x = leftPos;
        int y = topPos;

        Component libraryLabel = tr("tab.library");
        Component areaLabel = tr("tab.area");
        int libW = font.width(libraryLabel) + 14;
        int areaW = font.width(areaLabel) + 14;
        libraryTab = add(new FlatButton(x + X0, y + TABS_Y, libW, TAB_H, libraryLabel,
                (g, b, h) -> paintSegment(g, b, h, libraryLabel, tab == Tab.LIBRARY), () -> setTab(Tab.LIBRARY)));
        areaTab = add(new FlatButton(x + X0 + libW + 2, y + TABS_Y, areaW, TAB_H, areaLabel,
                (g, b, h) -> paintSegment(g, b, h, areaLabel, tab == Tab.AREA), () -> setTab(Tab.AREA)));

        // biblioteca: o preset selecionado
        applyButton = lib(new FlatButton(x + IX, y + APPLY_Y, IW, BTN_H, tr("apply"),
                (g, b, h) -> paintPrimary(g, b, h, tr("apply")), () -> sendSelected(Op.LOAD))
                .tooltip(() -> tr("apply.tooltip")));
        int half = (IW - 2) / 2;
        exportButton = lib(new FlatButton(x + IX, y + EXPORT_Y, half, BTN_H, tr("export"),
                (g, b, h) -> paintText(g, b, h, tr("export")), () -> sendSelected(Op.EXPORT))
                .tooltip(() -> tr("export.tooltip")));
        deleteButton = lib(new FlatButton(x + IX + IW - half, y + EXPORT_Y, half, BTN_H, tr("delete"),
                (g, b, h) -> paintDanger(g, b, h, deleteArmed() ? tr("delete.confirm") : tr("delete"), deleteArmed()),
                this::delete).tooltip(() -> deleteArmed() ? tr("delete.confirm.tooltip") : tr("delete.tooltip")));
        // varinha e código exportado
        int clearW = font.width(tr("wand.clear")) + 12;
        clearWandButton = lib(new FlatButton(x + X1 - clearW, y + WAND_Y - 2, clearW, 12, tr("wand.clear"),
                (g, b, h) -> paintText(g, b, h, tr("wand.clear")), () -> send(Op.CLEAR_WAND, 0, ""))
                .tooltip(() -> tr("wand.clear.tooltip")));
        int copyW = font.width(tr("code.copy")) + 16;
        exportBox = new EditBox(font, x + X0 + 4, y + CODE_Y + 3, X1 - X0 - copyW - 12, 9, tr("code.narration"));
        exportBox.setBordered(false);
        exportBox.setMaxLength(PresetCode.MAX_CODE_LENGTH);
        exportBox.setTextColor(GuiPaint.MUTED);
        exportBox.setEditable(false);
        addRenderableWidget(exportBox);
        copyCodeButton = lib(new FlatButton(x + X1 - copyW, y + CODE_Y, copyW, BTN_H, tr("code.copy"),
                (g, b, h) -> paintText(g, b, h, tr("code.copy")), this::copyCode).tooltip(() -> tr("code.copy.tooltip")));
        moreButton = lib(new FlatButton(x + X0, y + MORE_Y, 120, 12, tr("more"), this::paintMore, this::toggleMore)
                .tooltip(() -> tr("more.tooltip")));
        // recolhido: salvar e importar
        nameBox = new EditBox(font, x + IX + 4, y + NAME_Y + 3, IW - 8, 9, tr("name"));
        nameBox.setBordered(false);
        nameBox.setMaxLength(PresetLibrary.MAX_NAME);
        nameBox.setTextColor(GuiPaint.FG);
        nameBox.setHint(tr("name.hint").copy().withColor(GuiPaint.DISABLED));
        nameBox.setValue(nameDraft);
        nameBox.setResponder(value -> nameDraft = value);
        addRenderableWidget(nameBox);
        saveButton = lib(new FlatButton(x + IX, y + SAVE_Y, half, BTN_H, tr("save"),
                (g, b, h) -> paintPrimary(g, b, h, tr("save")), this::save)
                .tooltip(() -> view().wand().hasPreset() ? tr("save.tooltip") : tr("save.nothing")));
        renameButton = lib(new FlatButton(x + IX + IW - half, y + SAVE_Y, half, BTN_H, tr("rename"),
                (g, b, h) -> paintText(g, b, h, tr("rename")), this::rename).tooltip(() -> selectedEntry() == null
                        ? tr("rename.none") : tr("rename.tooltip", selectedEntry().name())));
        codeBox = new EditBox(font, x + IX + 4, y + IMPORT_Y + 3, IW - 8, 9, tr("code.narration"));
        codeBox.setBordered(false);
        codeBox.setMaxLength(PresetCode.MAX_CODE_LENGTH);
        codeBox.setTextColor(GuiPaint.FG);
        codeBox.setHint(Component.literal(PresetCode.PREFIX + "…").withColor(GuiPaint.DISABLED));
        codeBox.setValue(codeDraft);
        codeBox.setResponder(value -> codeDraft = value);
        addRenderableWidget(codeBox);
        pasteCodeButton = lib(new FlatButton(x + IX, y + IMPORT_BTN_Y, half, BTN_H, tr("code.paste"),
                (g, b, h) -> paintText(g, b, h, tr("code.paste")), this::pasteCode).tooltip(() -> tr("code.paste.tooltip")));
        importButton = lib(new FlatButton(x + IX + IW - half, y + IMPORT_BTN_Y, half, BTN_H, tr("import"),
                (g, b, h) -> paintPrimary(g, b, h, tr("import")), this::importCode).tooltip(() -> tr("import.tooltip")));

        // área: cliques da varinha e modo
        Component brush = tr("clicks.brush");
        Component area = tr("clicks.area");
        int clicksX = x + X0 + font.width(tr("clicks")) + 6;
        int brushW = font.width(brush) + 12;
        int areaModeW = font.width(area) + 12;
        brushButton = area(new FlatButton(clicksX, y + CLICKS_Y, brushW, BTN_H, brush,
                (g, b, h) -> paintSegment(g, b, h, brush, !view().wand().areaMode()), () -> setWandMode(false))
                .tooltip(() -> tr("clicks.brush.tooltip")));
        areaModeButton = area(new FlatButton(clicksX + brushW + 2, y + CLICKS_Y, areaModeW, BTN_H, area,
                (g, b, h) -> paintSegment(g, b, h, area, view().wand().areaMode()), () -> setWandMode(true))
                .tooltip(() -> tr("clicks.area.tooltip")));
        Component copyMode = tr("mode.copy");
        Component applyModeLabel = tr("mode.apply");
        int copyModeW = font.width(copyMode) + 14;
        int applyW = font.width(applyModeLabel) + 14;
        copyModeButton = area(new FlatButton(x + X0, y + MODE_Y, copyModeW, BTN_H + 1, copyMode,
                (g, b, h) -> paintSegment(g, b, h, copyMode, !applyMode), () -> applyMode = false));
        applyModeButton = area(new FlatButton(x + X0 + copyModeW + 2, y + MODE_Y, applyW, BTN_H + 1, applyModeLabel,
                (g, b, h) -> paintSegment(g, b, h, applyModeLabel, applyMode), () -> applyMode = true));
        sourceButton = area(new FlatButton(x + AX, y + MAP_Y + 10, AW, BTN_H, tr("source"),
                (g, b, h) -> paintCycle(g, b, h, sourceLabel()), () -> cycleSource(hasShiftDown() ? -1 : 1))
                .tooltip(() -> tr("source.tooltip")));
        machineButton = area(new FlatButton(x + AX, y + MAP_Y + 38, AW, BTN_H, tr("machine"),
                (g, b, h) -> paintCycle(g, b, h, machineLabel()), () -> cycleMachine(hasShiftDown() ? -1 : 1))
                .tooltip(() -> tr("machine.tooltip")));
        areaPrimaryButton = area(new FlatButton(x + AX, y + AREA_BTN1_Y, AW, BTN_H, tr("area.copy"),
                (g, b, h) -> paintPrimary(g, b, h, areaPrimaryLabel()), this::areaPrimary)
                .tooltip(this::areaPrimaryTooltip));
        areaSecondaryButton = area(new FlatButton(x + AX, y + AREA_BTN2_Y, AW, BTN_H, tr("area.clear"),
                (g, b, h) -> paintText(g, b, h, areaSecondaryLabel()), this::areaSecondary)
                .tooltip(this::areaSecondaryTooltip));

        syncView();
        refresh();
    }

    private FlatButton add(FlatButton button) {
        buttons.add(button);
        return addRenderableWidget(button);
    }

    private FlatButton lib(FlatButton button) {
        libraryButtons.add(button);
        return add(button);
    }

    private FlatButton area(FlatButton button) {
        areaButtons.add(button);
        return add(button);
    }

    /** Visibilidade e estado que dependem da aba, da visão e do que está aberto. */
    private void refresh() {
        boolean lib = tab == Tab.LIBRARY;
        boolean areaTabOpen = tab == Tab.AREA;
        for (FlatButton b : libraryButtons) {
            b.visible = lib;
        }
        for (FlatButton b : areaButtons) {
            b.visible = areaTabOpen;
        }
        LibraryEntry entry = selectedEntry();
        boolean showSelection = lib && !moreOpen;
        applyButton.visible = exportButton.visible = deleteButton.visible = showSelection;
        applyButton.active = exportButton.active = deleteButton.active = entry != null;
        boolean showForm = lib && moreOpen;
        saveButton.visible = renameButton.visible = pasteCodeButton.visible = importButton.visible = showForm;
        nameBox.visible = codeBox.visible = showForm;
        saveButton.active = view().wand().hasPreset() && PresetLibrary.cleanName(nameDraft) != null;
        renameButton.active = entry != null && PresetLibrary.cleanName(nameDraft) != null;
        importButton.active = !codeDraft.isBlank();
        clearWandButton.visible = lib && view().wand().hasPreset();
        boolean hasCode = lib && view().export().isPresent();
        exportBox.visible = copyCodeButton.visible = hasCode;
        Component more = moreOpen ? tr("less") : tr("more");
        moreButton.setWidth(11 + font.width(more) + 2);

        Wand wand = view().wand();
        boolean clipboard = wand.clipboardSize() > 0;
        sourceButton.visible = machineButton.visible = areaTabOpen && applyMode;
        sourceButton.active = wand.hasPreset() || !library().isEmpty();
        machineButton.active = !view().area().machines().isEmpty();
        if (applyMode) {
            areaPrimaryButton.active = view().area().usable() && applyCount() > 0
                    && (applySource >= 0 || wand.hasPreset());
        } else if (clipboard) {
            areaPrimaryButton.active = wand.anchor().isPresent() && wand.anchorHits() > 0;
        } else {
            areaPrimaryButton.active = view().area().usable() && !view().area().routers().isEmpty();
        }
        areaSecondaryButton.active = !applyMode && clipboard
                || wand.corner1().isPresent() || wand.corner2().isPresent();
        if (!lib && (getFocused() == nameBox || getFocused() == codeBox)) {
            setFocused(null);
        }
    }

    // ------------------------------------------------------------------ ações

    private void send(Op op, int index, String text) {
        if (!preview) {
            PacketDistributor.sendToServer(new ConfiguratorActionPayload(menu.containerId, op, index, text));
        }
    }

    private void sendSelected(Op op) {
        if (selectedEntry() != null) {
            send(op, selected, "");
        }
    }

    private void setTab(Tab next) {
        tab = next;
        deleteArmedUntil = 0;
        setFocused(null);
    }

    private void delete() {
        if (selectedEntry() == null) {
            return;
        }
        if (!deleteArmed()) {
            deleteArmedUntil = Util.getMillis() + CONFIRM_MS;
            return;
        }
        deleteArmedUntil = 0;
        send(Op.DELETE, selected, "");
    }

    private void copyCode() {
        view().export().ifPresent(export -> {
            minecraft.keyboardHandler.setClipboard(export.code());
            noticeOverride = tr("code.copied", export.name());
            noticeUntil = Util.getMillis() + NOTICE_MS;
        });
    }

    private void toggleMore() {
        moreOpen = !moreOpen;
        if (moreOpen) {
            if (nameDraft.isEmpty() && selectedEntry() != null) {
                nameBox.setValue(selectedEntry().name());
            }
            setFocused(nameBox);
        } else {
            setFocused(null);
        }
    }

    private void save() {
        String name = PresetLibrary.cleanName(nameDraft);
        if (name != null && view().wand().hasPreset()) {
            send(Op.SAVE, 0, name);
            // o preset novo vai para o fim da lista: seleciona-o quando chegar
            selected = library().size();
        }
    }

    private void rename() {
        String name = PresetLibrary.cleanName(nameDraft);
        if (name != null && selectedEntry() != null) {
            send(Op.RENAME, selected, name);
        }
    }

    private void pasteCode() {
        String clip = minecraft.keyboardHandler.getClipboard();
        if (clip != null) {
            codeBox.setValue(clip.strip().length() > PresetCode.MAX_CODE_LENGTH
                    ? clip.strip().substring(0, PresetCode.MAX_CODE_LENGTH) : clip.strip());
        }
    }

    private void importCode() {
        if (!codeDraft.isBlank()) {
            send(Op.IMPORT, 0, codeDraft.strip());
            selected = library().size();
            codeBox.setValue("");
        }
    }

    private void setWandMode(boolean area) {
        if (view().wand().areaMode() != area) {
            send(Op.SET_MODE, area ? 1 : 0, "");
        }
    }

    private int applyCount() {
        return view().area().count(machineFilter);
    }

    private void cycleSource(int direction) {
        int first = view().wand().hasPreset() ? -1 : 0;
        int last = library().size() - 1;
        if (last < first) {
            return;
        }
        int next = applySource + direction;
        applySource = next > last ? first : next < first ? last : next;
    }

    private void cycleMachine(int direction) {
        int last = view().area().machines().size() - 1;
        int next = machineFilter + direction;
        machineFilter = next > last ? -1 : next < -1 ? last : next;
    }

    private Component sourceLabel() {
        if (applySource >= 0 && applySource < library().size()) {
            return Component.literal(library().get(applySource).name());
        }
        return view().wand().hasPreset() ? tr("source.wand") : tr("source.none");
    }

    private Component machineLabel() {
        List<ResourceLocation> machines = view().area().machines();
        return machineFilter >= 0 && machineFilter < machines.size()
                ? tr("machine.only", machineName(machines.get(machineFilter)))
                : tr("machine.all");
    }

    private Component areaPrimaryLabel() {
        Wand wand = view().wand();
        if (applyMode) {
            return tr("area.apply", applyCount());
        }
        if (wand.clipboardSize() > 0) {
            return tr("area.paste", wand.anchor().isPresent() ? wand.anchorHits() : 0);
        }
        return tr("area.copy", view().area().routers().size());
    }

    private @Nullable Component areaPrimaryTooltip() {
        Wand wand = view().wand();
        if (!applyMode && wand.clipboardSize() > 0) {
            return wand.anchor().isPresent() ? tr("area.paste.tooltip") : tr("area.paste.no_anchor");
        }
        return applyMode ? tr("area.apply.tooltip") : tr("area.copy.tooltip");
    }

    private void areaPrimary() {
        Wand wand = view().wand();
        if (applyMode) {
            List<ResourceLocation> machines = view().area().machines();
            String machine = machineFilter >= 0 && machineFilter < machines.size()
                    ? machines.get(machineFilter).toString() : "";
            send(Op.APPLY_AREA, applySource, machine);
        } else if (wand.clipboardSize() > 0) {
            send(Op.PASTE, 0, "");
        } else {
            send(Op.COPY_AREA, 0, "");
        }
    }

    private Component areaSecondaryLabel() {
        return !applyMode && view().wand().clipboardSize() > 0 ? tr("area.discard") : tr("area.clear");
    }

    private Component areaSecondaryTooltip() {
        return !applyMode && view().wand().clipboardSize() > 0 ? tr("area.discard.tooltip") : tr("area.clear.tooltip");
    }

    private void areaSecondary() {
        send(!applyMode && view().wand().clipboardSize() > 0 ? Op.CLEAR_CLIPBOARD : Op.CLEAR_AREA, 0, "");
    }

    // ------------------------------------------------------------------ entrada

    /** Índice do preset sob o mouse, ou −1. */
    private int rowAt(double mouseX, double mouseY) {
        double rx = mouseX - (leftPos + X0 + 1);
        double ry = mouseY - (topPos + TOP + 1);
        if (tab != Tab.LIBRARY || rx < 0 || ry < 0 || rx >= LIST_W - 2 || ry >= ROWS * ROW_H) {
            return -1;
        }
        int index = scroll + (int) ry / ROW_H;
        return index < library().size() ? index : -1;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int row = rowAt(mouseX, mouseY);
        if (row >= 0) {
            if (row != selected) {
                selected = row;
                deleteArmedUntil = 0;
                if (moreOpen) {
                    nameBox.setValue(library().get(row).name());
                }
            }
            return true;
        }
        if (deleteArmed() && !deleteButton.isMouseOver(mouseX, mouseY)) {
            deleteArmedUntil = 0;
        }
        boolean wasOpen = moreOpen;
        boolean handled = super.mouseClicked(mouseX, mouseY, button);
        if (moreOpen && !wasOpen) {
            // a tela dá o foco ao botão clicado depois do onPress
            setFocused(nameBox);
        }
        return handled;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (tab == Tab.LIBRARY && mouseX >= leftPos + X0 && mouseX < leftPos + X0 + LIST_W
                && mouseY >= topPos + TOP && mouseY < topPos + TOP + LIST_H) {
            scroll = Math.max(0, Math.min(Math.max(0, library().size() - ROWS), scroll - (int) Math.signum(scrollY)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (getFocused() instanceof EditBox box && box.isFocused() && box.visible) {
            boolean enter = keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER;
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                setFocused(null);
            } else if (enter && box == nameBox) {
                save();
            } else if (enter && box == codeBox) {
                importCode();
            } else {
                box.keyPressed(keyCode, scanCode, modifiers);
            }
            // nenhuma tecla fecha a tela enquanto se digita (nem a do inventário)
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (getFocused() instanceof EditBox box && box.isFocused() && box.visible) {
            return box.charTyped(codePoint, modifiers);
        }
        return super.charTyped(codePoint, modifiers);
    }

    // ------------------------------------------------------------------ desenho

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (menu.version() != lastVersion) {
            int before = seenNoticeId;
            syncView();
            if (seenNoticeId != before) {
                noticeOverride = null;
            }
        }
        refresh();
        super.render(g, mouseX, mouseY, partialTick);
        for (FlatButton button : buttons) {
            if (button.visible && button.isHovered()) {
                Component tooltip = button.currentTooltip();
                if (tooltip != null) {
                    setTooltipForNextRenderPass(font.split(tooltip, 220));
                }
                return;
            }
        }
        int row = rowAt(mouseX, mouseY);
        if (row >= 0) {
            LibraryEntry entry = library().get(row);
            setTooltipForNextRenderPass(Component.literal(entry.name()));
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        // os textos são desenhados em renderBg, em coordenadas da tela
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        GuiPaint.panel(g, x, y, W, H, ACCENT);
        GuiPaint.text(g, font, tr("title"), x + X0, y + HEAD_Y + 3, GuiPaint.FG);
        Component status = wandStatus();
        int statusX = x + X0 + font.width(tr("title")) + 10;
        GuiPaint.text(g, font, GuiPaint.ellipsize(font, status, x + X1 - statusX), x + X1 - Math.min(font.width(status),
                x + X1 - statusX), y + HEAD_Y + 3, GuiPaint.MUTED);
        g.fill(x + X0, y + SEP_Y, x + X1, y + SEP_Y + 1, GuiPaint.LINE);
        if (tab == Tab.LIBRARY) {
            renderLibrary(g, mouseX, mouseY);
        } else {
            renderArea(g);
        }
        renderNotice(g);
    }

    /** "Pincel · 6 faces copiadas" ou "Área · 12 roteadores copiados". */
    private Component wandStatus() {
        Wand wand = view().wand();
        Component mode = wand.areaMode() ? tr("clicks.area") : tr("clicks.brush");
        Component detail = wand.areaMode() && wand.clipboardSize() > 0
                ? tr("status.clipboard", wand.clipboardSize())
                : wand.hasPreset() ? tr(wand.presetFaces() == 1 ? "status.preset.one" : "status.preset", wand.presetFaces())
                : tr("status.empty");
        return mode.copy().append(" · ").append(detail);
    }

    private void renderLibrary(GuiGraphics g, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        // lista
        GuiPaint.box(g, x + X0, y + TOP, LIST_W, LIST_H, GuiPaint.INSET, GuiPaint.LINE);
        List<LibraryEntry> entries = library();
        if (entries.isEmpty()) {
            List<FormattedCharSequence> lines = font.split(tr("library.empty"), LIST_W - 12);
            for (int i = 0; i < lines.size(); i++) {
                GuiPaint.text(g, font, lines.get(i), x + X0 + 6, y + TOP + 8 + i * 10, GuiPaint.DISABLED);
            }
        }
        int hovered = rowAt(mouseX, mouseY);
        for (int i = 0; i < ROWS && scroll + i < entries.size(); i++) {
            int index = scroll + i;
            LibraryEntry entry = entries.get(index);
            int ry = y + TOP + 1 + i * ROW_H;
            boolean sel = index == selected;
            if (sel) {
                GuiPaint.box(g, x + X0 + 1, ry, LIST_W - 2, ROW_H, GuiPaint.mix(GuiPaint.BUTTON, ACCENT, 0.18f), ACCENT);
            } else if (index == hovered) {
                GuiPaint.box(g, x + X0 + 1, ry, LIST_W - 2, ROW_H, GuiPaint.BUTTON, GuiPaint.BUTTON_HOVER_BORDER);
            }
            GuiPaint.text(g, font, GuiPaint.ellipsize(font, Component.literal(entry.name()), LIST_W - 12),
                    x + X0 + 6, ry + 3, GuiPaint.FG);
            GuiPaint.text(g, font, GuiPaint.ellipsize(font, shortFacesText(entry.faces(), entry.networks()), LIST_W - 12),
                    x + X0 + 6, ry + 12, GuiPaint.MUTED);
        }
        if (entries.size() > ROWS) {
            int barH = LIST_H - 2;
            int thumb = Math.max(8, barH * ROWS / entries.size());
            int ty = y + TOP + 1 + (barH - thumb) * scroll / Math.max(1, entries.size() - ROWS);
            g.fill(x + X0 + LIST_W - 3, ty, x + X0 + LIST_W - 1, ty + thumb, GuiPaint.BUTTON_HOVER_BORDER);
        }

        // à direita: o preset selecionado ou o formulário
        GuiPaint.box(g, x + RX, y + TOP, RW, LIST_H, SEL_BOX, GuiPaint.LINE);
        if (moreOpen) {
            renderForm(g);
        } else {
            renderSelection(g);
        }

        // varinha, código e "Salvar ou importar"
        Wand wand = view().wand();
        Component wandLine = wand.hasPreset() ? tr("wand.preset", facesText(wand.presetFaces(), wand.presetNetworks()))
                : tr("wand.empty");
        int wandRight = clearWandButton.visible ? clearWandButton.getX() - 6 : x + X1;
        GuiPaint.text(g, font, GuiPaint.ellipsize(font, wandLine, wandRight - x - X0), x + X0, y + WAND_Y,
                wand.hasPreset() ? GuiPaint.FG : GuiPaint.MUTED);
        view().export().ifPresent(export -> {
            GuiPaint.box(g, x + X0, y + CODE_Y, copyCodeButton.getX() - leftPos - X0 - 4, BTN_H, GuiPaint.INSET,
                    GuiPaint.LINE);
        });
    }

    /** "4 faces · 2 redes", para a lista. */
    private Component shortFacesText(int faces, int networks) {
        Component text = tr(faces == 1 ? "faces.short.one" : "faces.short", faces);
        if (networks > 0) {
            text = text.copy().append(" · ").append(tr(networks == 1 ? "networks.one" : "networks", networks));
        }
        return text;
    }

    private Component facesText(int faces, int networks) {
        Component text = tr(faces == 1 ? "faces.one" : "faces", faces);
        if (networks > 0) {
            text = text.copy().append(" · ").append(tr(networks == 1 ? "networks.one" : "networks", networks));
        }
        return text;
    }

    private void renderSelection(GuiGraphics g) {
        int x = leftPos;
        int y = topPos;
        LibraryEntry entry = selectedEntry();
        if (entry == null) {
            List<FormattedCharSequence> lines = font.split(tr("selection.none"), IW);
            for (int i = 0; i < lines.size(); i++) {
                GuiPaint.text(g, font, lines.get(i), x + IX, y + TOP + 6 + i * 10, GuiPaint.DISABLED);
            }
            return;
        }
        GuiPaint.text(g, font, GuiPaint.ellipsize(font, Component.literal(entry.name()), IW), x + IX, y + TOP + 6,
                GuiPaint.FG);
        GuiPaint.text(g, font, GuiPaint.ellipsize(font, shortFacesText(entry.faces(), entry.networks()), IW), x + IX,
                y + TOP + 16, GuiPaint.MUTED);
        int lineY = y + TOP + 29;
        int maxLines = (APPLY_Y - 3 - (TOP + 29)) / 9;
        List<FaceLine> lines = entry.lines();
        if (lines.isEmpty()) {
            GuiPaint.text(g, font, tr("selection.no_faces"), x + IX, lineY, GuiPaint.DISABLED);
        }
        for (int i = 0; i < lines.size() && i < maxLines; i++) {
            FaceLine line = lines.get(i);
            boolean last = i == maxLines - 1 && (lines.size() > maxLines || entry.faces() > lines.size());
            if (last) {
                GuiPaint.text(g, font, tr("selection.more", entry.faces() - i), x + IX, lineY + i * 9, GuiPaint.MUTED);
                break;
            }
            GuiPaint.dot(g, x + IX, lineY + i * 9 + 1, GuiPaint.modeColor(line.mode()));
            Component text = typeName(line.type()).copy().append(" · ").append(sideName(line.side())).append(": ")
                    .append(modeName(line.mode()));
            if (line.filterSize() > 0) {
                text = text.copy().append(tr("selection.filter", line.filterSize()));
            }
            GuiPaint.text(g, font, GuiPaint.ellipsize(font, text, IW - 8), x + IX + 8, lineY + i * 9, GuiPaint.FG);
        }
    }

    private void renderForm(GuiGraphics g) {
        int x = leftPos;
        int y = topPos;
        GuiPaint.text(g, font, tr("name"), x + IX, y + TOP + 3, GuiPaint.MUTED);
        GuiPaint.box(g, x + IX, y + NAME_Y, IW, BTN_H, GuiPaint.INSET,
                getFocused() == nameBox ? ACCENT : GuiPaint.BUTTON_BORDER);
        GuiPaint.text(g, font, tr("import.label"), x + IX, y + IMPORT_LABEL_Y, GuiPaint.MUTED);
        GuiPaint.box(g, x + IX, y + IMPORT_Y, IW, BTN_H, GuiPaint.INSET,
                getFocused() == codeBox ? ACCENT : GuiPaint.BUTTON_BORDER);
    }

    private void renderArea(GuiGraphics g) {
        int x = leftPos;
        int y = topPos;
        GuiPaint.text(g, font, tr("clicks"), x + X0, y + CLICKS_Y + 3, GuiPaint.MUTED);
        renderMap(g);

        Wand wand = view().wand();
        Area area = view().area();
        List<Component> lines = new ArrayList<>();
        if (applyMode) {
            GuiPaint.text(g, font, tr("source.label"), x + AX, y + MAP_Y, GuiPaint.MUTED);
            GuiPaint.text(g, font, tr("machine.label"), x + AX, y + MAP_Y + 28, GuiPaint.MUTED);
            lines.add(areaLine(area, wand));
            renderLines(g, lines, y + MAP_Y + 58);
            return;
        }
        if (wand.clipboardSize() > 0) {
            lines.add(tr("clipboard.size", wand.clipboardSize()));
            if (wand.anchor().isPresent()) {
                lines.add(tr("clipboard.anchor", wand.anchor().get().toShortString()));
                lines.add(tr("clipboard.hits", wand.anchorHits(), wand.clipboardSize()));
            } else {
                lines.add(wand.areaMode() ? tr("clipboard.pick") : tr("clipboard.pick_area_mode"));
            }
        } else {
            lines.add(areaLine(area, wand));
        }
        renderLines(g, lines, y + MAP_Y);
    }

    /** Situação da área: o que marcar, o problema dela ou quantos roteadores tem. */
    private Component areaLine(Area area, Wand wand) {
        if (wand.corner1().isEmpty()) {
            return wand.areaMode() ? tr("area.mark1") : tr("area.mark_mode");
        }
        if (wand.corner2().isEmpty()) {
            return tr("area.mark2");
        }
        if (area.problem().isPresent()) {
            String problem = area.problem().get();
            return switch (problem) {
                case "too_large" -> tr("area.problem.too_large", AreaOps.MAX_VOLUME);
                case "too_far" -> tr("area.problem.too_far", AreaOps.MAX_REACH);
                default -> tr("area.problem." + problem);
            };
        }
        return tr("area.summary", applyMode ? applyCount() : area.routers().size());
    }

    private void renderLines(GuiGraphics g, List<Component> lines, int top) {
        int ty = top;
        for (Component line : lines) {
            for (FormattedCharSequence part : font.split(line, AW)) {
                if (ty + 9 > topPos + AREA_BTN1_Y - 2) {
                    return;
                }
                GuiPaint.text(g, font, part, leftPos + AX, ty, GuiPaint.FG);
                ty += 10;
            }
            ty += 2;
        }
    }

    /** Mapa de cima da área: o retângulo dela e um ponto por coluna com roteador (verde = configurado). */
    private void renderMap(GuiGraphics g) {
        int x = leftPos + X0;
        int y = topPos + MAP_Y;
        GuiPaint.box(g, x, y, MAP_W, MAP_H, GuiPaint.INSET, GuiPaint.LINE);
        Area area = view().area();
        Wand wand = view().wand();
        int sx = area.size().getX();
        int sz = area.size().getZ();
        if (sx <= 0 || sz <= 0) {
            Component hint = wand.corner1().isPresent() ? tr("map.one_corner") : tr("map.empty");
            List<FormattedCharSequence> lines = font.split(hint, MAP_W - 16);
            int ty = y + (MAP_H - lines.size() * 10) / 2;
            for (FormattedCharSequence line : lines) {
                g.drawString(font, line, x + (MAP_W - font.width(line)) / 2, ty, GuiPaint.DISABLED, false);
                ty += 10;
            }
            return;
        }
        int inner = 8;
        float scale = Math.min(12f, Math.min((MAP_W - 2f * inner) / sx, (MAP_H - 2f * inner) / sz));
        int w = Math.max(2, Math.round(sx * scale));
        int h = Math.max(2, Math.round(sz * scale));
        int ox = x + (MAP_W - w) / 2;
        int oz = y + (MAP_H - h) / 2;
        int frame = area.usable() ? ACCENT : DANGER;
        g.fill(ox, oz, ox + w, oz + h, (frame & 0x00FFFFFF) | 0x1F000000);
        GuiPaint.outline(g, ox - 1, oz - 1, w + 2, h + 2, frame);
        if (scale >= 4) {
            for (int i = 1; i < sx; i++) {
                int lx = ox + Math.round(i * scale);
                g.fill(lx, oz, lx + 1, oz + h, 0x22FFFFFF);
            }
            for (int i = 1; i < sz; i++) {
                int lz = oz + Math.round(i * scale);
                g.fill(ox, lz, ox + w, lz + 1, 0x22FFFFFF);
            }
        }
        int size = Math.max(2, Math.round(scale) - 2);
        for (Dot dot : area.routers()) {
            boolean dim = applyMode && machineFilter >= 0 && dot.machine() != machineFilter;
            int color = dot.configured() ? OK : IDLE;
            if (dim) {
                color = GuiPaint.mix(color, GuiPaint.INSET, 0.7f);
            }
            int px = ox + Math.round((dot.dx() + 0.5f) * scale) - size / 2;
            int pz = oz + Math.round((dot.dz() + 0.5f) * scale) - size / 2;
            g.fill(px, pz, px + size, pz + size, color);
        }
        // canto 1 (origem da cópia) em branco
        wand.corner1().ifPresent(c1 -> {
            int px = ox + Math.round((c1.getX() - area.min().getX() + 0.5f) * scale);
            int pz = oz + Math.round((c1.getZ() - area.min().getZ() + 0.5f) * scale);
            g.fill(px - 2, pz - 2, px + 3, pz + 3, GuiPaint.BEVEL_DARK);
            g.fill(px - 1, pz - 1, px + 2, pz + 2, 0xFFFFFFFF);
        });
        Component legend = tr("map.size", sx + "×" + area.size().getY() + "×" + sz);
        GuiPaint.text(g, font, legend, x + 4, y + MAP_H - 11, GuiPaint.MUTED);
    }

    private void renderNotice(GuiGraphics g) {
        Component text = noticeOverride != null ? noticeOverride : view().notice().orElse(null);
        if (text == null || Util.getMillis() > noticeUntil) {
            return;
        }
        GuiPaint.text(g, font, GuiPaint.ellipsize(font, text, X1 - X0), leftPos + X0, topPos + NOTICE_Y, ACCENT);
    }

    // ------------------------------------------------------------------ pintura dos botões

    private void paintText(GuiGraphics g, FlatButton b, boolean hovered, Component text) {
        int border = hovered ? GuiPaint.BUTTON_HOVER_BORDER : GuiPaint.BUTTON_BORDER;
        GuiPaint.box(g, b.getX(), b.getY(), b.getWidth(), b.getHeight(), b.active ? GuiPaint.BUTTON : GuiPaint.INSET,
                b.active ? border : GuiPaint.LINE);
        Component fit = Component.empty().append(text);
        g.drawString(font, GuiPaint.ellipsize(font, fit, b.getWidth() - 6),
                b.getX() + Math.max(3, (b.getWidth() - font.width(text)) / 2 + 1), b.getY() + (b.getHeight() - 8) / 2,
                b.active ? GuiPaint.FG : GuiPaint.DISABLED, false);
    }

    private void paintPrimary(GuiGraphics g, FlatButton b, boolean hovered, Component text) {
        if (!b.active) {
            paintText(g, b, false, text);
            return;
        }
        int fill = hovered ? GuiPaint.mix(ACCENT, 0xFFFFFFFF, 0.2f) : ACCENT;
        GuiPaint.box(g, b.getX(), b.getY(), b.getWidth(), b.getHeight(), fill, fill);
        g.drawString(font, GuiPaint.ellipsize(font, text, b.getWidth() - 6),
                b.getX() + Math.max(3, (b.getWidth() - font.width(text)) / 2 + 1), b.getY() + (b.getHeight() - 8) / 2,
                GuiPaint.DARK_TEXT, false);
    }

    private void paintDanger(GuiGraphics g, FlatButton b, boolean hovered, Component text, boolean armed) {
        if (!b.active) {
            paintText(g, b, false, text);
            return;
        }
        int fill = armed ? GuiPaint.mix(GuiPaint.BUTTON, DANGER, 0.3f) : GuiPaint.BUTTON;
        int border = armed || hovered ? DANGER : GuiPaint.BUTTON_BORDER;
        GuiPaint.box(g, b.getX(), b.getY(), b.getWidth(), b.getHeight(), fill, border);
        GuiPaint.textCentered(g, font, text, b.getX() + b.getWidth() / 2 + 1, b.getY() + (b.getHeight() - 8) / 2,
                armed || hovered ? GuiPaint.mix(DANGER, 0xFFFFFFFF, 0.45f) : GuiPaint.FG);
    }

    private void paintSegment(GuiGraphics g, FlatButton b, boolean hovered, Component text, boolean on) {
        if (on) {
            GuiPaint.box(g, b.getX(), b.getY(), b.getWidth(), b.getHeight(), ACCENT, ACCENT);
        } else {
            GuiPaint.box(g, b.getX(), b.getY(), b.getWidth(), b.getHeight(), GuiPaint.BUTTON,
                    hovered ? GuiPaint.BUTTON_HOVER_BORDER : GuiPaint.BUTTON_BORDER);
        }
        GuiPaint.textCentered(g, font, text, b.getX() + b.getWidth() / 2, b.getY() + (b.getHeight() - 7) / 2,
                on ? GuiPaint.DARK_TEXT : GuiPaint.FG);
    }

    /** Botão de escolha com setas: clique avança, Shift + clique volta. */
    private void paintCycle(GuiGraphics g, FlatButton b, boolean hovered, Component text) {
        GuiPaint.box(g, b.getX(), b.getY(), b.getWidth(), b.getHeight(), b.active ? GuiPaint.BUTTON : GuiPaint.INSET,
                b.active && hovered ? GuiPaint.BUTTON_HOVER_BORDER : GuiPaint.BUTTON_BORDER);
        int color = b.active ? GuiPaint.FG : GuiPaint.DISABLED;
        GuiPaint.text(g, font, Component.literal("‹"), b.getX() + 4, b.getY() + 3, GuiPaint.MUTED);
        GuiPaint.textRight(g, font, Component.literal("›"), b.getX() + b.getWidth() - 4, b.getY() + 3, GuiPaint.MUTED);
        FormattedCharSequence label = GuiPaint.ellipsize(font, text, b.getWidth() - 20);
        g.drawString(font, label, b.getX() + (b.getWidth() - font.width(label)) / 2, b.getY() + 3, color, false);
    }

    private void paintMore(GuiGraphics g, FlatButton b, boolean hovered) {
        int color = hovered || moreOpen ? GuiPaint.FG : GuiPaint.MUTED;
        if (moreOpen) {
            GuiPaint.arrowDown(g, b.getX() + 1, b.getY() + 3, color);
        } else {
            GuiPaint.arrowRight(g, b.getX() + 2, b.getY() + 2, color);
        }
        GuiPaint.text(g, font, moreOpen ? tr("less") : tr("more"), b.getX() + 10, b.getY() + 1, color);
    }

    // ------------------------------------------------------------------ captura de desenvolvimento

    void previewTab(boolean area) {
        setTab(area ? Tab.AREA : Tab.LIBRARY);
    }

    void previewSelect(int index) {
        selected = index;
    }

    void previewMore(boolean open, String name, String code) {
        moreOpen = open;
        nameBox.setValue(name);
        codeBox.setValue(code);
        setFocused(open ? nameBox : null);
    }

    void previewApplyMode(boolean apply, int source, int machine) {
        applyMode = apply;
        applySource = source;
        machineFilter = machine;
    }

    void previewArmDelete() {
        deleteArmedUntil = Util.getMillis() + 60_000;
    }

    /** Centro de um widget pela mensagem, para simular o mouse em cima na captura. */
    int[] previewCenter(Component message) {
        for (FlatButton button : buttons) {
            if (button.visible && button.getMessage().equals(message)) {
                return new int[] {button.getX() + button.getWidth() / 2, button.getY() + button.getHeight() / 2};
            }
        }
        return new int[] {-1, -1};
    }
}
