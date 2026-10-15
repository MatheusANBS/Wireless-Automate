package io.github.matheusanbs.wirelessautomate.client.router;

import io.github.matheusanbs.wirelessautomate.client.FlatButton;
import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import io.github.matheusanbs.wirelessautomate.client.ResourceStyle;
import io.github.matheusanbs.wirelessautomate.client.ThemeToggle;
import io.github.matheusanbs.wirelessautomate.menu.RouterMenu;
import io.github.matheusanbs.wirelessautomate.menu.RouterSnapshot;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.packet.RenameRouterPayload;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * O cabeçalho da tela do roteador: o nome (clicar renomeia, num campo no mesmo lugar), a vazão da aba e
 * a pílula do tier. O botão do tema e os slots de upgrade ficam no canto, pela tela.
 */
final class RouterHeader {
    private final RouterMenu menu;
    private final RouterLayout layout;
    private final boolean preview;
    private final Supplier<ResourceType> type;
    private final Consumer<@Nullable GuiEventListener> focus;
    private final Runnable refresh;

    private boolean renaming;
    private String renameDraft = "";
    private FlatButton titleButton;
    private EditBox renameBox;

    /**
     * @param preview sem servidor: o nome novo vale só no snapshot local
     * @param focus dá o foco da tela a um widget ({@code null} tira)
     * @param refresh refaz as posições da tela depois de começar ou terminar a renomeação
     */
    RouterHeader(RouterMenu menu, RouterLayout layout, boolean preview, Supplier<ResourceType> type,
            Consumer<@Nullable GuiEventListener> focus, Runnable refresh) {
        this.menu = menu;
        this.layout = layout;
        this.preview = preview;
        this.type = type;
        this.focus = focus;
        this.refresh = refresh;
    }

    private static Component tr(String key, Object... args) {
        return Component.translatable("gui.wirelessautomate.router." + key, args);
    }

    private static Font font() {
        return Minecraft.getInstance().font;
    }

    private RouterSnapshot snapshot() {
        return menu.snapshot();
    }

    Component displayName() {
        RouterSnapshot s = snapshot();
        if (!s.name().isEmpty()) {
            return Component.literal(s.name());
        }
        return s.machine().isEmpty() ? Component.translatable("block.wirelessautomate.router") : s.machine().getHoverName();
    }

    private Component rate() {
        long[] perType = menu.throughput();
        ResourceType type = this.type.get();
        long value = type.ordinal() < perType.length ? perType[type.ordinal()] : 0L;
        return ResourceStyle.rate(type, value);
    }

    private int pillWidth(Component text) {
        return 6 + 5 + 4 + font().width(text) + 6;
    }

    // ------------------------------------------------------------------ montagem

    /** O nome, que renomeia ao clicar (a tela o põe entre os widgets). */
    FlatButton createTitle(int left, int top) {
        titleButton = new FlatButton(left + RouterLayout.X0, top + RouterLayout.HEAD_Y, 80, RouterLayout.PILL_H,
                Component.empty(), this::paintTitle, this::startRename).tooltip(() -> tr("rename.tooltip"));
        return titleButton;
    }

    /** O campo do nome, no lugar do título, visível só ao renomear. */
    EditBox createRenameBox(Font font, int left, int top) {
        renameBox = new EditBox(font, left + RouterLayout.X0 + 4, top + RouterLayout.HEAD_Y + 3, 80, 9,
                tr("rename.narration"));
        renameBox.setBordered(false);
        renameBox.setMaxLength(RenameRouterPayload.MAX_LENGTH);
        renameBox.setTextShadow(false); // tinta sobre porcelana, sem sombra
        renameBox.setValue(renameDraft);
        renameBox.setResponder(value -> renameDraft = value);
        renameBox.visible = renaming;
        return renameBox;
    }

    EditBox renameBox() {
        return renameBox;
    }

    boolean renaming() {
        return renaming;
    }

    /** Larguras e visibilidade do título e do campo pelo snapshot atual. */
    void refresh(int left) {
        Font font = font();
        Component tierText = Component.translatable(snapshot().tier().translationKey());
        int tierW = pillWidth(tierText);
        int rateX = left + layout.headEnd() - tierW - 6 - font.width(rate());
        int titleW = rateX - (left + RouterLayout.X0) - 8;
        titleButton.setWidth(Math.min(titleW, font.width(displayName()) + 4));
        titleButton.setMessage(displayName());
        titleButton.visible = !renaming;
        renameBox.setWidth(titleW - 8);
        renameBox.visible = renaming;
        GuiPaint.ink(renameBox, displayName()); // a cor do EditBox é do widget: segue o tema a cada quadro
    }

    // ------------------------------------------------------------------ renomear

    void startRename() {
        renaming = true;
        renameDraft = snapshot().name();
        renameBox.setValue(renameDraft);
        renameBox.moveCursorToEnd(false);
        focus.accept(renameBox);
        refresh.run();
    }

    void finishRename(boolean commit) {
        if (!renaming) {
            return;
        }
        renaming = false;
        focus.accept(null);
        String name = renameBox.getValue().strip();
        if (commit && !name.equals(snapshot().name())) {
            if (preview) {
                RouterSnapshot s = snapshot();
                menu.applySnapshot(new RouterSnapshot(s.pos(), name, s.tier(), s.facing(), s.attachedFront(), s.networks(),
                        s.powered(), s.machine(), s.machineState(), s.faces(), s.chunkLoad()));
            } else {
                PacketDistributor.sendToServer(new RenameRouterPayload(menu.containerId, name));
            }
        }
        refresh.run();
    }

    /** Fecha a renomeação sem gravar (a tela vai refazer os widgets). */
    void cancelRename() {
        if (renaming) {
            renaming = false;
            focus.accept(null);
        }
    }

    /** Um clique fora do campo grava o nome em edição. */
    void clickOutside(double mouseX, double mouseY) {
        if (renaming && !renameBox.isMouseOver(mouseX, mouseY)) {
            finishRename(true);
        }
    }

    /** Uma tecla durante a renomeação: Esc desiste, Enter grava, o resto vai para o campo. */
    void keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            finishRename(false);
        } else if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            finishRename(true);
        } else {
            renameBox.keyPressed(keyCode, scanCode, modifiers);
        }
    }

    // ------------------------------------------------------------------ desenho

    /**
     * A moldura do campo ao renomear, a pílula do tier, o botão do tema (em {@code themeX}, {@code themeY})
     * e a vazão da aba, no espaço entre o título e a pílula.
     */
    void render(GuiGraphics g, int x, int y, int trim, int themeX, int themeY, int mouseX, int mouseY) {
        Font font = font();
        if (renaming) {
            GuiPaint.box(g, x + RouterLayout.X0, y + RouterLayout.HEAD_Y, renameBox.getWidth() + 8, RouterLayout.PILL_H,
                    GuiPaint.INSET, GuiPaint.ACCENT);
        }
        Component tierText = Component.translatable(snapshot().tier().translationKey());
        int tierW = pillWidth(tierText);
        int tierX = x + layout.headEnd() - tierW;
        GuiPaint.pill(g, tierX, y + RouterLayout.HEAD_Y, tierW, RouterLayout.PILL_H, GuiPaint.PANEL, trim);
        GuiPaint.eye(g, tierX + 6, y + RouterLayout.HEAD_Y + 4, trim);
        GuiPaint.text(g, font, tierText, tierX + 15, y + RouterLayout.HEAD_Y + 3, GuiPaint.FG);

        ThemeToggle.render(g, themeX, themeY, mouseX, mouseY);

        Component rate = rate();
        int rateSpace = tierX - 6 - (x + RouterLayout.X0 + titleButton.getWidth() + 8);
        int rateW = Math.min(font.width(rate), Math.max(0, rateSpace));
        GuiText.draw(g, font, rate, tierX - 6 - rateW, y + RouterLayout.HEAD_Y + 3, rateW, GuiPaint.MUTED);
    }

    private void paintTitle(GuiGraphics g, FlatButton b, boolean hovered) {
        Font font = font();
        int color = hovered ? GuiPaint.ACCENT : GuiPaint.FG;
        GuiText.draw(g, font, displayName(), b.getX(), b.getY() + 3, b.getWidth() - 2, color);
        if (hovered) {
            int width = Math.min(font.width(displayName()), b.getWidth() - 2);
            for (int i = 0; i < width; i += 2) {
                g.fill(b.getX() + i, b.getY() + 12, b.getX() + i + 1, b.getY() + 13, color);
            }
        }
    }

    /** Captura: começa a renomear com {@code draft} no campo. */
    void previewRename(String draft) {
        startRename();
        renameBox.setValue(draft);
    }
}
