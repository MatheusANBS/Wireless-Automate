package io.github.matheusanbs.wirelessautomate.client.guide;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.matheusanbs.wirelessautomate.client.GuiText;
import io.github.matheusanbs.wirelessautomate.client.guide.GuideInk.Ink;
import io.github.matheusanbs.wirelessautomate.client.guide.layout.Sheet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * O livro aberto: a anfitriã. Calcula a escala ({@link BookScale}) no {@code init}, monta as duas folhas à vista
 * quando o lugar muda ({@link GuideInk}, guardadas por folha) e repassa o desenho ao {@link GuideChrome}, à
 * {@link GuideCover} e ao {@link GuidePainter}. Na abertura, a folha de rosto fica à esquerda e o índice corre a
 * partir da direita; uma página corre pelas duas folhas. O lugar e o histórico ficam no {@link GuideNav}.
 */
public final class GuideScreen extends Screen {
    private final Map<Sheet, List<Ink>> prepared = new IdentityHashMap<>();
    private BookScale scale;
    private GuideIndex index;
    private GuideChrome chrome;
    private GuideCover cover;
    private @Nullable String page;
    private int spreads = 1;
    private boolean leftIsCover;
    private List<Ink> left = List.of();
    private List<Ink> right = List.of();

    private GuideScreen() {
        super(Component.translatable("item.wirelessautomate.guide"));
    }

    /** Abre o livro na página pedida ({@code router.md} ou {@code router}) ou, com {@code null}, onde parou. */
    public static void open(@Nullable String target) {
        GuidePages.ensure();
        if (target != null) {
            String id = GuidePages.resolve(target);
            if (id != null) {
                GuideNav.go(id, 0);
            }
        }
        Minecraft.getInstance().setScreen(new GuideScreen());
    }

    /** Abre na folha de rosto e no começo do índice, guardando o lugar de antes no histórico. */
    public static void openIndex() {
        GuidePages.ensure();
        GuideNav.go(null, 0);
        Minecraft.getInstance().setScreen(new GuideScreen());
    }

    @Override
    protected void init() {
        GuidePages.ensure();
        Window w = Minecraft.getInstance().getWindow();
        scale = BookScale.of(w.getWidth(), w.getHeight(), w.getGuiScale());
        if (index == null) {
            index = GuidePages.index();
            chrome = new GuideChrome(font, index);
            cover = new GuideCover(font);
        }
        refresh();
    }

    // ---- o lugar

    /** Remonta o que depende do lugar: as duas folhas, os números, as setas e a fita comprida. */
    private void refresh() {
        page = GuideNav.page();
        if (page != null && !GuidePages.exists(page)) {
            GuideNav.reset();
            page = null;
        }
        List<Sheet> sheets = page == null ? index.sheets : GuidePages.sheets(page);
        int slots = page == null ? sheets.size() + 1 : sheets.size();
        spreads = Math.max(1, (slots + 1) / 2);
        int spread = Math.clamp(GuideNav.spread(), 0, spreads - 1);
        GuideNav.turnTo(spread);
        int l = spread * 2;
        int r = l + 1;
        int offset = page == null ? 1 : 0;
        leftIsCover = page == null && l == 0;
        left = leftIsCover ? List.of() : ink(sheets, l - offset);
        right = ink(sheets, r - offset);
        int group;
        if (page == null) {
            int s = Math.clamp(leftIsCover ? 0 : l - offset, 0, Math.max(0, index.sheetGroup.length - 1));
            group = index.sheetGroup.length == 0 ? -1 : index.sheetGroup[s];
        } else {
            group = GuideIndex.groupOrder(GuidePages.group(page));
        }
        String ln = number(l, slots);
        String rn = number(r, slots);
        chrome.update(font, spread > 0, spread < spreads - 1, page != null, ln, rn, group);
    }

    private List<Ink> ink(List<Sheet> sheets, int i) {
        if (i < 0 || i >= sheets.size()) {
            return List.of();
        }
        return prepared.computeIfAbsent(sheets.get(i),
                s -> GuideInk.prepare(s, page, font, GuidePages.measure()));
    }

    /** O número da folha: romano na abertura e no índice, arábico nas páginas; vazio se a folha não existe. */
    private String number(int slot, int slots) {
        if (slot >= slots) {
            return "";
        }
        return page == null ? roman(slot + 1) : Integer.toString(slot + 1);
    }

    private static final String[] TENS = {"", "x", "xx", "xxx"};
    private static final String[] ONES = {"", "i", "ii", "iii", "iv", "v", "vi", "vii", "viii", "ix"};

    private static String roman(int n) {
        return n >= 40 ? Integer.toString(n) : TENS[n / 10] + ONES[n % 10];
    }

    private void go(@Nullable String to, int spread) {
        GuideNav.go(to, spread);
        refresh();
    }

    /** Vira para o par {@code spread}, dentro dos limites. */
    public void turnTo(int spread) {
        GuideNav.turnTo(Math.clamp(spread, 0, spreads - 1));
        refresh();
    }

    private void back() {
        if (GuideNav.back()) {
            refresh();
        }
    }

    /** O par do índice onde o grupo começa (a folha de rosto conta como a primeira folha). */
    private int indexSpread(int group) {
        if (group < 0 || index.groupSheet[group] < 0) {
            return 0;
        }
        return (index.groupSheet[group] + 1) / 2;
    }

    // ---- ganchos do e2e

    /** A página aberta ({@code null} = folha de rosto e índice). */
    public @Nullable String page() {
        return page;
    }

    public int spread() {
        return GuideNav.spread();
    }

    public int spreads() {
        return spreads;
    }

    /** O centro, em coordenadas de interface, do primeiro link à vista para a página, ou {@code null}. */
    public int @Nullable [] linkCenter(String target) {
        int[] c = linkCenter(left, BookScale.LEFT_X, target);
        return c != null ? c : linkCenter(right, BookScale.RIGHT_X, target);
    }

    private int @Nullable [] linkCenter(List<Ink> inks, int originX, String target) {
        for (Ink ink : inks) {
            if (ink instanceof GuideInk.Link l && l.page().equals(target)) {
                double tx = originX + (l.x() + l.w() / 2.0) / 2;
                double ty = BookScale.CONTENT_Y + (l.y() + l.h() / 2.0) / 2;
                return new int[] {(int) scale.guiX(tx), (int) scale.guiY(ty)};
            }
        }
        return null;
    }

    // ---- desenho

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        GuiText.beginFrame();
        super.render(g, mouseX, mouseY, partialTick);
        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(scale.originGuiX(), scale.originGuiY(), 0);
        float k = scale.guiPerTexel();
        pose.scale(k, k, k);
        chrome.drawBook(g);
        sheet(g, BookScale.LEFT_X, left, leftIsCover);
        sheet(g, BookScale.RIGHT_X, right, false);
        chrome.drawFront(g, font);
        pose.popPose();
        tooltip(g, mouseX, mouseY);
    }

    private void sheet(GuiGraphics g, int x, List<Ink> inks, boolean isCover) {
        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(x, BookScale.CONTENT_Y, 0);
        pose.scale(0.5f, 0.5f, 0.5f);
        if (isCover) {
            cover.draw(g, font);
        } else {
            GuidePainter.draw(g, font, inks);
        }
        pose.popPose();
    }

    private void tooltip(GuiGraphics g, int mouseX, int mouseY) {
        double tx = scale.texX(mouseX);
        double ty = scale.texY(mouseY);
        GuideChrome.Hit hit = chrome.hit(tx, ty);
        if (hit == GuideChrome.Hit.RIBBON) {
            g.renderTooltip(font, chrome.groupName(chrome.hitGroup()), mouseX, mouseY);
            return;
        }
        Ink ink = sheetHit(tx, ty);
        if (ink instanceof GuideInk.Item it) {
            g.renderTooltip(font, it.name(), mouseX, mouseY);
        } else if (ink instanceof GuideInk.Link l) {
            g.renderTooltip(font, l.title(), mouseX, mouseY);
        }
    }

    private @Nullable Ink sheetHit(double tx, double ty) {
        double uy = BookScale.unit(ty, BookScale.CONTENT_Y);
        if (tx < BookScale.RIGHT_X - 4) {
            return leftIsCover ? null : GuidePainter.hit(left, BookScale.unit(tx, BookScale.LEFT_X), uy);
        }
        return GuidePainter.hit(right, BookScale.unit(tx, BookScale.RIGHT_X), uy);
    }

    // ---- mouse e teclado

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_4) {
            back();
            return true;
        }
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        double tx = scale.texX(mouseX);
        double ty = scale.texY(mouseY);
        GuideChrome.Hit hit = chrome.hit(tx, ty);
        if (hit != null) {
            switch (hit) {
                case PREV -> turnTo(GuideNav.spread() - 1);
                case NEXT -> turnTo(GuideNav.spread() + 1);
                case BACK -> go(null, indexSpread(GuideIndex.groupOrder(GuidePages.group(page))));
                case RIBBON -> go(null, indexSpread(chrome.hitGroup()));
            }
            return true;
        }
        Ink ink = sheetHit(tx, ty);
        if (ink instanceof GuideInk.Link l) {
            go(l.page(), 0);
            return true;
        }
        if (ink instanceof GuideInk.Item it && it.page() != null) {
            go(it.page(), 0);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0) {
            turnTo(GuideNav.spread() + (scrollY > 0 ? -1 : 1));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        switch (keyCode) {
            case GLFW.GLFW_KEY_LEFT -> turnTo(GuideNav.spread() - 1);
            case GLFW.GLFW_KEY_RIGHT -> turnTo(GuideNav.spread() + 1);
            case GLFW.GLFW_KEY_BACKSPACE -> back();
            default -> {
                return super.keyPressed(keyCode, scanCode, modifiers);
            }
        }
        return true;
    }
}
