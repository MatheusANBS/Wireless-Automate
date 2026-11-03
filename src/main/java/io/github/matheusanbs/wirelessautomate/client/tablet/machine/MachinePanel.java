package io.github.matheusanbs.wirelessautomate.client.tablet.machine;

import static io.github.matheusanbs.wirelessautomate.client.tablet.machine.MachineText.tr;

import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import io.github.matheusanbs.wirelessautomate.client.GuiThemes;
import io.github.matheusanbs.wirelessautomate.client.tablet.TabletActions;
import io.github.matheusanbs.wirelessautomate.client.tablet.TabletLayout;
import io.github.matheusanbs.wirelessautomate.client.tablet.machine.MachineListRows.Entry;
import io.github.matheusanbs.wirelessautomate.menu.RouterSnapshot;
import io.github.matheusanbs.wirelessautomate.menu.TabletMachineRows;
import io.github.matheusanbs.wirelessautomate.menu.TabletMenu;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot;
import io.github.matheusanbs.wirelessautomate.menu.RangeView;
import io.github.matheusanbs.wirelessautomate.packet.TabletPanelPayload;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * A aba Máquina do Tablet (mockup {@code maquina} de {@code docs/preview/tablet-do-zero}): à esquerda a lista de máquinas
 * ({@link MachineList}); à direita o cabeçalho da máquina escolhida ({@link MachineHeader}), com o espaço do cartão do
 * veredito e das faces embaixo. É a anfitriã: monta as linhas quando chega a página ({@link MachineListRows}), acha a
 * escolhida do servidor ({@link MachineSelect}), repassa o mouse e o teclado e manda as ações pelas
 * {@link MachineActions}; os ganchos do e2e ficam no {@link MachineProbe}. A aba pede ao servidor a página por máquinas
 * (a visão em que ela funciona) sempre que ela não é a que chegou.
 */
public final class MachinePanel {
    private final TabletLayout layout;
    private final TabletMenu menu;
    private final Supplier<TabletSnapshot> snapshot;
    private final MachineActions send;
    private final MachineSelect select;
    private final MachineList list;
    private final MachineHeader header;
    private final MachineProbe probe;
    private final Component pick = tr("pick");
    /** Os blocos que a aba já abriu: sem cabeçalho na lista, uma máquina num bloco recolhido não aparece. */
    private final Set<ResourceLocation> opened = new HashSet<>();
    private @Nullable TabletMachineRows builtPage;
    private int builtTheme = -1;
    private int builtPanel = -1;
    private MachineSelect.@Nullable Choice builtChoice;
    private MachineSelect.@Nullable Choice revealedFor;
    private boolean viewAsked;
    private boolean firstSync = true;
    private List<Entry> entries = List.of();

    public MachinePanel(TabletLayout layout, TabletMenu menu, Supplier<TabletSnapshot> snapshot, TabletActions actions) {
        this.layout = layout;
        this.menu = menu;
        this.snapshot = snapshot;
        this.send = new MachineActions(actions, menu);
        this.select = new MachineSelect(menu, send);
        this.list = new MachineList(layout, snapshot, send::query);
        this.header = new MachineHeader(layout);
        this.probe = new MachineProbe(this, list, header, menu);
    }

    private static Font font() {
        return Minecraft.getInstance().font;
    }

    // ------------------------------------------------------------------ montagem

    /** O campo de busca, refeito a cada {@code init}; a tela o põe entre os widgets. */
    public EditBox createBox(Font font) {
        return list.createBox(font);
    }

    public @Nullable EditBox box() {
        return list.box();
    }

    /** O campo só aparece com a aba aberta; a tinta dele segue o tema. */
    public void refresh(boolean shown) {
        EditBox box = list.box();
        if (box != null) {
            box.visible = shown;
        }
        list.ink(GuiThemes.version());
    }

    /** A cada tique da tela. */
    public void tick() {
        select.tick();
        list.tick();
    }

    /**
     * Põe a lista e o cabeçalho em dia com a página e com a escolha do servidor. As linhas são refeitas quando chega uma
     * página (outro objeto: linhas novas ou uma amostra) ou o tema muda; o cabeçalho, também quando a escolha ou o painel
     * mudam. Por quadro, só as comparações.
     */
    private void sync(Font font) {
        TabletMachineRows page = snapshot.get().machineRows();
        if (page.active()) {
            viewAsked = false;
        } else if (!viewAsked) {
            viewAsked = true;
            send.machinesView();
        }
        boolean pageChanged = page != builtPage || builtTheme != GuiThemes.version();
        if (pageChanged) {
            builtPage = page;
            builtTheme = GuiThemes.version();
            entries = MachineListRows.of(page);
            list.rebuild(font, entries);
            openCollapsed(page);
        }
        MachineSelect.Choice choice = select.current();
        if (!pageChanged && Objects.equals(choice, builtChoice) && menu.panelVersion() == builtPanel) {
            return;
        }
        builtChoice = choice;
        builtPanel = menu.panelVersion();
        int index = indexOf(choice);
        list.choose(index);
        if (index >= 0 && !choice.equals(revealedFor)) {
            // só uma vez por escolha: uma amostra nova não briga com a rolagem do jogador
            revealedFor = choice;
            list.reveal(index);
        }
        buildHeader(font, index, choice);
        if (firstSync) {
            firstSync = false;
            TabletSnapshot.Query query = snapshot.get().query();
            if (!query.machines().isEmpty() || !query.networks().isEmpty() || !query.quick().isEmpty()
                    || query.type().isPresent()) {
                list.sendQuery();
            }
        }
    }

    private void openCollapsed(TabletMachineRows page) {
        for (TabletMachineRows.Row row : page.rows()) {
            if (row instanceof TabletMachineRows.Block block && block.collapsed() && opened.add(block.block())) {
                send.openBlock(block.block());
            }
        }
    }

    private int indexOf(MachineSelect.@Nullable Choice choice) {
        if (choice == null) {
            return -1;
        }
        for (int i = 0; i < entries.size(); i++) {
            if (choice.matches(entries.get(i))) {
                return i;
            }
        }
        return -1;
    }

    /**
     * O cabeçalho da escolhida: da linha, se a página a traz (o mesmo nome e ícone da lista); senão do painel do
     * servidor, que sempre tem a máquina (uma busca ou um "mais N" a esconde da lista).
     */
    private void buildHeader(Font font, int index, MachineSelect.@Nullable Choice choice) {
        MachineList.Line line = list.line(index);
        if (line != null) {
            Entry entry = line.entry();
            header.set(font, line.icon(), line.name(), entry.tier(),
                    MachineHeader.distance(entry.pos(), entry.router().dimension()));
            return;
        }
        TabletPanelPayload payload = menu.panel();
        RouterSnapshot snap = choice == null || payload == null ? null : payload.snapshot().orElse(null);
        if (snap == null) {
            header.clear();
            return;
        }
        RangeView.MachineView remote = choice.attached() ? null : snap.range().machine(choice.index());
        String label = choice.attached() ? snap.attachedLabel() : remote == null ? "" : remote.label();
        String name = label.isEmpty() ? RouterBlock.machineName(snap.machineState().getBlock()).getString() : label;
        BlockPos pos = remote != null ? remote.pos() : snap.pos();
        ResourceKey<Level> dimension = snap.dimension();
        ItemStack icon = snap.machine().isEmpty() ? new ItemStack(snap.machineState().getBlock().asItem())
                : snap.machine();
        RouterTier tier = snap.tier();
        header.set(font, icon, name, tier, MachineHeader.distance(pos, dimension));
    }

    // ------------------------------------------------------------------ entrada

    public boolean mouseClicked(double mx, double my, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return false;
        }
        Entry entry = list.entryAt(mx, my);
        if (entry == null) {
            return false;
        }
        activate(entry);
        return true;
    }

    /** O clique ou o Enter na linha: escolhe a máquina, ou, no "… mais N", mostra todas as do bloco. */
    private void activate(Entry entry) {
        if (entry.machine()) {
            select.choose(entry);
        } else {
            send.showAll(entry.block());
        }
    }

    public void mouseScrolled(double mx, double my, double delta) {
        list.mouseScrolled(mx, my, delta);
    }

    /** ↑ ↓ andam o cursor pelas linhas, Enter escolhe a do cursor e Esc limpa a busca; o resto fica com o campo. */
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && list.searching()) {
            list.clearSearch();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_DOWN) {
            list.moveCursor(keyCode == GLFW.GLFW_KEY_DOWN ? 1 : -1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            Entry entry = list.cursorEntry();
            if (entry != null) {
                activate(entry);
                return true;
            }
        }
        return false;
    }

    public @Nullable Component tooltip(int mx, int my) {
        return list.tooltip(mx, my);
    }

    // ------------------------------------------------------------------ desenho

    public void render(GuiGraphics g, Font font, int mx, int my) {
        sync(font);
        list.render(g, font, mx, my);
        if (header.present()) {
            header.render(g, font);
        } else {
            int x0 = layout.left + TabletLayout.PANEL_X0;
            GuiText.wrapCentered(g, font, pick, (x0 + layout.left + layout.panelX1()) / 2, layout.top + layout.h / 2 - 4,
                    layout.panelX1() - TabletLayout.PANEL_X0, 2, GuiPaint.MUTED);
        }
    }

    // ------------------------------------------------------------------ ganchos do e2e

    /** Os ganchos de clique do e2e (centros na tela) e o estado que eles conferem. */
    public MachineProbe probe() {
        return probe;
    }

    List<Entry> entries() {
        return entries;
    }

    MachineSelect.@Nullable Choice choice() {
        return select.current();
    }
}
