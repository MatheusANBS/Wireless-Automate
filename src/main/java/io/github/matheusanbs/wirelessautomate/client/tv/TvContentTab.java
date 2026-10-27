package io.github.matheusanbs.wirelessautomate.client.tv;

import static io.github.matheusanbs.wirelessautomate.client.tv.TvScreenLayout.*;

import io.github.matheusanbs.wirelessautomate.client.ArmedClick;
import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import io.github.matheusanbs.wirelessautomate.client.ResourceStyle;
import io.github.matheusanbs.wirelessautomate.client.tv.TvButtons.Label;
import io.github.matheusanbs.wirelessautomate.menu.TvMenu;
import io.github.matheusanbs.wirelessautomate.network.LoadedTypes;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.tv.TvBlockEntity;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.jetbrains.annotations.Nullable;

/**
 * A aba Conteúdo: a lista das redes com a busca ({@link TvNetworkList}, com ✓ na rede que a Tela mostra), os tipos
 * carregados, o Aplicar o Painel de rede e o Limpar em dois cliques. A rede atual vem do bloco no cliente e só é lida
 * de novo quando a revisão do layout muda; os textos são medidos uma vez, na abertura.
 */
final class TvContentTab {
    private static final List<ResourceType> TYPES = LoadedTypes.LIST;

    private final TvMenu menu;
    private final TvScreenActions actions;
    private final TvNetworkList list;
    private final ArmedClick clearClick = new ArmedClick();
    private ResourceType type;
    private @Nullable UUID current;
    private int seenRevision = -1;

    private Font font;
    private GuiText.Fitted networksLabel;
    private GuiText.Fitted typeLabel;
    private GuiText.Fitted[] typeNames = new GuiText.Fitted[0];
    private Label template;
    private Label clear;
    private Label clearArmed;
    private TvScreenText.Wrapped note;

    TvContentTab(TvMenu menu, TvScreenActions actions) {
        this.menu = menu;
        this.actions = actions;
        this.list = new TvNetworkList(menu);
        current = menu.currentNetwork();
        ResourceType opened = ResourceType.byKey(menu.currentType());
        type = opened != null && TYPES.contains(opened) ? opened : TYPES.get(0);
    }

    void layout(Font font) {
        this.font = font;
        networksLabel = GuiText.fit(font, TvScreenText.key("networks"), NETWORKS_LABEL.w());
        typeLabel = GuiText.fit(font, TvScreenText.key("type"), TYPE_LABEL.w());
        typeNames = new GuiText.Fitted[TYPES.size()];
        for (int i = 0; i < typeNames.length; i++) {
            typeNames[i] = GuiText.fit(font, ResourceStyle.name(TYPES.get(i)), TYPE_W - 6 - ResourceStyle.ICON - 4);
        }
        template = TvButtons.label(font, TvScreenText.key("template.apply"), TEMPLATE.w() - 6);
        clear = TvButtons.label(font, TvScreenText.key("clear"), CLEAR.w() - 6);
        clearArmed = TvButtons.label(font, TvScreenText.key("clear.armed"), CLEAR.w() - 6);
        note = TvScreenText.wrap(font, TvScreenText.key("editor_soon"), NOTE.w(), 2);
        list.layout(font);
    }

    /** A rede que a Tela mostra, lida do bloco no cliente só quando a revisão do layout mudou. */
    void tick() {
        if (Minecraft.getInstance().level == null
                || !(Minecraft.getInstance().level.getBlockEntity(menu.pos()) instanceof TvBlockEntity tv)
                || tv.revision() == seenRevision) {
            return;
        }
        seenRevision = tv.revision();
        current = tv.layout().bindings().isEmpty() ? null : tv.layout().bindings().get(0).network();
    }

    void render(GuiGraphics g, int left, int top, int mouseX, int mouseY) {
        GuiText.draw(g, font, networksLabel, left + NETWORKS_LABEL.x(), top + NETWORKS_LABEL.y(), GuiPaint.MUTED);
        list.render(g, left, top, mouseX, mouseY, current);
        GuiText.draw(g, font, typeLabel, left + TYPE_LABEL.x(), top + TYPE_LABEL.y(), GuiPaint.MUTED);
        for (int i = 0; i < TYPES.size() && i < TYPE_BUTTONS.length; i++) {
            renderType(g, left, top, i, mouseX, mouseY);
        }
        TvButtons.primary(g, font, TEMPLATE, left, top, TEMPLATE.contains(left, top, mouseX, mouseY),
                list.selected() != null, GuiPaint.ACCENT, template);
        if (clearClick.armed(System.currentTimeMillis())) {
            TvButtons.primary(g, font, CLEAR, left, top, CLEAR.contains(left, top, mouseX, mouseY), true,
                    GuiPaint.DANGER, clearArmed);
        } else {
            TvButtons.plain(g, font, CLEAR, left, top, CLEAR.contains(left, top, mouseX, mouseY), clear, true);
        }
        note.draw(g, font, left + NOTE.x(), top + NOTE.y(), GuiPaint.MUTED);
    }

    private void renderType(GuiGraphics g, int left, int top, int i, int mouseX, int mouseY) {
        Rect r = TYPE_BUTTONS[i];
        int x = left + r.x();
        int y = top + r.y();
        boolean on = TYPES.get(i) == type;
        if (on) {
            GuiPaint.box(g, x, y, r.w(), r.h(), GuiPaint.SELECTED, GuiPaint.SELECTED);
        } else {
            GuiPaint.box(g, x, y, r.w(), r.h(), GuiPaint.BUTTON, r.contains(left, top, mouseX, mouseY)
                    ? GuiPaint.BUTTON_HOVER_BORDER : GuiPaint.BUTTON_BORDER);
        }
        ResourceStyle.drawIcon(g, TYPES.get(i), x + 3, y + (r.h() - ResourceStyle.ICON) / 2);
        GuiText.draw(g, font, typeNames[i], x + 4 + ResourceStyle.ICON + 3, y + 3,
                on ? GuiPaint.SELECTED_TEXT : GuiPaint.FG);
    }

    boolean mouseClicked(int left, int top, double mouseX, double mouseY) {
        if (TEMPLATE.contains(left, top, mouseX, mouseY)) {
            if (list.selected() != null) {
                actions.template(list.selected(), type);
            }
            return true;
        }
        if (CLEAR.contains(left, top, mouseX, mouseY)) {
            if (clearClick.click(System.currentTimeMillis())) {
                actions.clear();
            }
            return true;
        }
        for (int i = 0; i < TYPES.size() && i < TYPE_BUTTONS.length; i++) {
            if (TYPE_BUTTONS[i].contains(left, top, mouseX, mouseY)) {
                type = TYPES.get(i);
                return true;
            }
        }
        return list.mouseClicked(left, top, mouseX, mouseY);
    }

    boolean mouseScrolled(int left, int top, double mouseX, double mouseY, double delta) {
        return list.mouseScrolled(left, top, mouseX, mouseY, delta);
    }

    boolean charTyped(char c) {
        return list.charTyped(c);
    }

    boolean keyPressed(int keyCode) {
        return list.keyPressed(keyCode);
    }

    /** Sair da aba desarma o Limpar. */
    void leave() {
        clearClick.disarm();
    }

    int @Nullable [] networkCenter(int left, int top, UUID id) {
        return list.center(left, top, id);
    }

    int @Nullable [] typeCenter(int left, int top, ResourceType wanted) {
        int index = TYPES.indexOf(wanted);
        if (index < 0 || index >= TYPE_BUTTONS.length) {
            return null;
        }
        return new int[] {TYPE_BUTTONS[index].centerX(left), TYPE_BUTTONS[index].centerY(top)};
    }

    TvNetworkList list() {
        return list;
    }

    ResourceType type() {
        return type;
    }

    @Nullable UUID current() {
        return current;
    }
}
