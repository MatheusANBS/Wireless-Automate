package io.github.matheusanbs.wirelessautomate.client.linker;

import static io.github.matheusanbs.wirelessautomate.client.linker.LinkerLayout.BODY_Y;
import static io.github.matheusanbs.wirelessautomate.client.linker.LinkerLayout.LW;
import static io.github.matheusanbs.wirelessautomate.client.linker.LinkerLayout.X0;
import static io.github.matheusanbs.wirelessautomate.client.linker.LinkerText.tr;

import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import io.github.matheusanbs.wirelessautomate.linker.LinkerActions;
import io.github.matheusanbs.wirelessautomate.menu.LinkerSnapshot;
import io.github.matheusanbs.wirelessautomate.menu.LinkerSnapshot.RouterInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * A coluna da esquerda da tela do Vinculador: o roteador escolhido (nome, posição, vagas ou o que impede de
 * vincular) e, embaixo, a ajuda dos gestos.
 */
final class RouterPanel {
    private static final int BOX_H = 52;
    private static final String[] HELP = {"help.click", "help.area", "help.mode"};

    void render(GuiGraphics g, LinkerSnapshot s, int x, int y) {
        Font font = Minecraft.getInstance().font;
        int left = x + X0;
        int top = y + BODY_Y;
        GuiPaint.text(g, font, tr("router.label"), left, top, GuiPaint.MUTED);
        int boxY = top + 11;
        GuiPaint.inset(g, left, boxY, LW, BOX_H);
        int textX = left + 5;
        int textW = LW - 10;
        int textY = boxY + 5;
        RouterInfo router = s.router();
        switch (router.status()) {
            case NONE -> GuiText.wrap(g, font, tr("router.none"), textX, textY, textW, 4, GuiPaint.MUTED);
            case MISSING -> {
                GuiText.wrap(g, font, tr("router.missing"), textX, textY, textW, 3, GuiPaint.WARN);
                GuiText.draw(g, font, LinkerActions.position(router.pos()), textX, textY + 32, textW, GuiPaint.MUTED);
            }
            case READY -> {
                GuiText.draw(g, font, Component.literal(router.name()), textX, textY, textW, GuiPaint.FG);
                GuiText.draw(g, font, LinkerActions.position(router.pos()), textX, textY + 11, textW, GuiPaint.MUTED);
                if (router.links()) {
                    GuiText.draw(g, font, tr("router.slots", router.used(), router.max()), textX, textY + 22, textW,
                            router.used() >= router.max() ? GuiPaint.WARN : GuiPaint.OK);
                } else {
                    GuiText.wrap(g, font, LinkerText.refusal(router), textX, textY + 22, textW, 2, GuiPaint.WARN);
                }
            }
        }
        int helpY = boxY + BOX_H + 8;
        GuiPaint.text(g, font, tr("help.label"), left, helpY, GuiPaint.MUTED);
        int lineY = helpY + 11;
        for (String key : HELP) {
            lineY += GuiText.wrap(g, font, tr(key), left, lineY, LW, 2, GuiPaint.FG) + 3;
        }
    }
}
