package io.github.matheusanbs.wirelessautomate.client.linker;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import io.github.matheusanbs.wirelessautomate.linker.LinkerOpen;
import io.github.matheusanbs.wirelessautomate.packet.LinkerOpenPickPayload;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * A lista curta do modo Abrir do Vinculador: vários roteadores chegam na máquina clicada (preso nela ou
 * vinculado pelo alcance). Cada linha mostra o nome, como chega e a distância; o clique escolhe, manda o
 * pedido ao servidor e fecha. Esc fecha. As linhas não mudam com a tela aberta, então são montadas uma vez.
 */
public final class LinkerOpenScreen extends Screen {
    private static final int W = 230;
    private static final int HEAD = 24;
    private static final int ROW = 16;
    private static final int PAD = 8;
    private static final int MAX_ROWS = 8;

    private final BlockPos machine;
    private final List<LinkerOpen.Choice> choices;
    private final boolean preview;
    private int scroll;
    private int left;
    private int top;
    private int rows;

    public LinkerOpenScreen(BlockPos machine, List<LinkerOpen.Choice> choices) {
        this(machine, choices, false);
    }

    /** @param preview captura de desenvolvimento: o clique não manda nada */
    public LinkerOpenScreen(BlockPos machine, List<LinkerOpen.Choice> choices, boolean preview) {
        super(Component.translatable("gui.wirelessautomate.linker.open.title"));
        this.machine = machine;
        this.choices = choices;
        this.preview = preview;
    }

    @Override
    protected void init() {
        rows = Math.min(choices.size(), MAX_ROWS);
        scroll = Mth.clamp(scroll, 0, Math.max(0, choices.size() - rows));
        left = (width - W) / 2;
        top = (height - panelH()) / 2;
    }

    private int panelH() {
        return HEAD + rows * ROW + PAD + 14;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int rowAt(double mouseX, double mouseY) {
        if (mouseX < left + PAD || mouseX >= left + W - PAD || mouseY < top + HEAD) {
            return -1;
        }
        int row = (int) (mouseY - top - HEAD) / ROW;
        return row >= 0 && row < rows ? scroll + row : -1;
    }

    /** Ponto da tela no meio da linha {@code index} visível (para o e2e e as capturas clicarem nela). */
    public int[] rowCenter(int index) {
        return new int[] {left + W / 2, top + HEAD + index * ROW + ROW / 2};
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int index = button == 0 ? rowAt(mouseX, mouseY) : -1;
        if (index >= 0) {
            if (!preview) {
                PacketDistributor.sendToServer(new LinkerOpenPickPayload(machine, choices.get(index).router()));
            }
            onClose();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll = Mth.clamp(scroll - (int) Math.signum(scrollY), 0, Math.max(0, choices.size() - rows));
        return true;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        GuiText.beginFrame();
        GuiPaint.panel(g, left, top, W, panelH(), GuiPaint.ACCENT);
        GuiText.draw(g, font, title, left + PAD, top + 8, W - 2 * PAD, GuiPaint.FG);
        g.fill(left + PAD, top + 20, left + W - PAD, top + 21, GuiPaint.LINE);
        int hovered = rowAt(mouseX, mouseY);
        for (int i = 0; i < rows; i++) {
            int index = scroll + i;
            LinkerOpen.Choice choice = choices.get(index);
            int y = top + HEAD + i * ROW;
            if (index == hovered) {
                GuiPaint.box(g, left + PAD, y, W - 2 * PAD, ROW - 1, GuiPaint.BUTTON, GuiPaint.BUTTON_HOVER_BORDER);
            }
            boolean attached = choice.kind() == LinkerOpen.Kind.ATTACHED;
            g.fill(left + PAD + 4, y + 4, left + PAD + 9, y + 9, attached ? GuiPaint.ACCENT : GuiPaint.RING);
            Component kind = Component.translatable("gui.wirelessautomate.linker.open." + (attached ? "attached" : "remote"));
            Component distance = Component.translatable("gui.wirelessautomate.linker.open.distance", choice.distance());
            int distanceW = font.width(distance);
            int kindW = font.width(kind);
            GuiText.draw(g, font, Component.literal(choice.name()), left + PAD + 14, y + 4,
                    W - 2 * PAD - 14 - kindW - distanceW - 14, GuiPaint.FG);
            GuiPaint.text(g, font, kind, left + W - PAD - 4 - distanceW - 6 - kindW, y + 4, GuiPaint.MUTED);
            GuiPaint.text(g, font, distance, left + W - PAD - 4 - distanceW, y + 4, GuiPaint.MUTED);
        }
        GuiPaint.text(g, font, Component.translatable("gui.wirelessautomate.linker.open.hint"), left + PAD,
                top + HEAD + rows * ROW + 4, GuiPaint.DISABLED);
        Component clipped = GuiText.clipAt(mouseX, mouseY);
        if (clipped != null) {
            setTooltipForNextRenderPass(clipped);
        }
    }

    /** Abre a lista (o handler do pacote). */
    static void show(BlockPos machine, List<LinkerOpen.Choice> choices) {
        Minecraft.getInstance().setScreen(new LinkerOpenScreen(machine, choices));
    }
}
