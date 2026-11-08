package io.github.matheusanbs.wirelessautomate.client.guide;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.matheusanbs.wirelessautomate.client.guide.GuideInk.Ink;
import io.github.matheusanbs.wirelessautomate.client.guide.layout.Sheet;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
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
    /** Uma folha preparada: a tinta e o que não coube nela. */
    private record Prepared(List<Ink> inks, List<String> problems) {
    }

    private final Map<Sheet, Prepared> prepared = new IdentityHashMap<>();
    /** O que não coube no par à vista (folhas, folha de rosto e o que fica em volta), montado no {@link #refresh}. */
    private final List<String> problems = new ArrayList<>();
    private BookScale scale;
    private GuideIndex index;
    private GuideChrome chrome;
    private GuideCover cover;
    private GuideSearchBox search;
    private @Nullable String page;
    private int spreads = 1;
    private boolean leftIsCover;
    private List<Ink> left = List.of();
    private List<Ink> right = List.of();
    /** Os gestos nas cenas à vista (girar, zoom, voltar); a vista de cada cena vive na tinta dela. */
    private final SceneInput scenes = new SceneInput();
    /** A tela de onde a tecla abriu o livro (um baú, o JEI), para onde ele volta ao fechar; {@code null} = o mundo. */
    private final @Nullable Screen parent;

    private GuideScreen(@Nullable Screen parent) {
        super(Component.translatable("item.wirelessautomate.guide"));
        this.parent = parent;
    }

    /** Abre o livro na página pedida ({@code router.md} ou {@code router}) ou, com {@code null}, onde parou. */
    public static void open(@Nullable String target) {
        open(target, null);
    }

    /**
     * Abre o livro por cima de {@code parent} e volta a ela ao fechar. Numa tela de contêiner, o menu continua aberto
     * nos dois lados (o {@code setScreen} pula o {@code closeContainer}): voltar à tela o mantém válido, e o Esc nela o
     * fecha do jeito normal, como a tela de receitas do JEI faz.
     */
    public static void open(@Nullable String target, @Nullable Screen parent) {
        GuidePages.ensure();
        if (target != null) {
            String id = GuidePages.resolve(target);
            if (id != null) {
                GuideNav.go(id, 0);
            }
        }
        Minecraft.getInstance().setScreen(new GuideScreen(parent));
    }

    /** Abre na folha de rosto e no começo do índice, guardando o lugar de antes no histórico. */
    public static void openIndex() {
        GuidePages.ensure();
        GuideNav.go(null, 0);
        Minecraft.getInstance().setScreen(new GuideScreen(null));
    }

    /** Volta à tela de onde veio; a de um contêiner só se o menu dela ainda é o aberto (o servidor pode tê-lo fechado). */
    @Override
    public void onClose() {
        Minecraft mc = Minecraft.getInstance();
        boolean stale = parent instanceof AbstractContainerScreen<?> c
                && (mc.player == null || mc.player.containerMenu != c.getMenu());
        mc.setScreen(stale ? null : parent);
    }

    /** A tela para onde o livro volta ao fechar (o e2e confere). */
    public @Nullable Screen parent() {
        return parent;
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
            search = new GuideSearchBox(font);
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
        problems.clear();
        if (leftIsCover) {
            left = List.of();
            note("folha de rosto", cover.problems());
        } else {
            left = ink(sheets, l - offset, "folha " + (l + 1));
        }
        if (showsResults()) {
            right = search.inks();
            note("resultados da busca", search.problems());
        } else {
            right = ink(sheets, r - offset, "folha " + (r + 1));
        }
        int group;
        if (page == null) {
            int s = Math.clamp(leftIsCover ? 0 : l - offset, 0, Math.max(0, index.sheetGroup.length - 1));
            group = index.sheetGroup.length == 0 ? -1 : index.sheetGroup[s];
        } else {
            group = GuideIndex.groupOrder(GuidePages.group(page));
        }
        String ln = GuideChrome.number(l, slots, page == null);
        String rn = GuideChrome.number(r, slots, page == null);
        boolean pagedResults = showsResults() && search.pages() > 1;
        boolean prev = pagedResults ? search.resultPage() > 0 : spread > 0;
        boolean next = pagedResults ? search.resultPage() < search.pages() - 1 : spread < spreads - 1;
        chrome.update(font, prev, next, page != null, ln, rn, group);
        note("em volta das folhas", chrome.problems());
        scenes.show(scale, left, right);
    }

    /** A folha direita mostra os resultados no lugar do índice: folha de rosto à vista e a busca com texto. */
    private boolean showsResults() {
        return page == null && GuideNav.spread() == 0 && !search.isEmpty();
    }

    /** A folha de rosto à vista, para a busca (digitar de qualquer folha volta para ela). */
    private void toCover() {
        if (page != null || GuideNav.spread() != 0) {
            GuideNav.go(null, 0);
        }
        search.focus();
        refresh();
    }

    private List<Ink> ink(List<Sheet> sheets, int i, String where) {
        if (i < 0 || i >= sheets.size()) {
            return List.of();
        }
        Prepared p = prepared.computeIfAbsent(sheets.get(i), s -> {
            List<String> found = new ArrayList<>();
            return new Prepared(GuideInk.prepare(s, page, font, GuidePages.measure(), found), List.copyOf(found));
        });
        note(where, p.problems());
        return p.inks();
    }

    private void note(String where, List<String> found) {
        for (String f : found) {
            problems.add((page == null ? "índice" : page) + ", " + where + ": " + f);
        }
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

    /** Vira as folhas ({@code delta} ±1); com os resultados em mais de uma página, viram as páginas deles. */
    private void turnBy(int delta) {
        if (showsResults() && search.pages() > 1) {
            search.turnResults(search.resultPage() + delta);
            refresh();
        } else {
            turnTo(GuideNav.spread() + delta);
        }
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

    /** O texto da busca agora. */
    public String searchText() {
        return search.text();
    }

    /** Quantos resultados a busca achou, de todas as páginas de resultados. */
    public int resultCount() {
        return search.total();
    }

    /** A página marcada nos resultados (o e2e confere que o 9º está à vista e marcado). */
    public @Nullable String markedResult() {
        return showsResults() ? search.selectedPage() : null;
    }

    /** As páginas dos resultados à vista, na ordem em que aparecem (vazio se a folha mostra o índice). */
    public List<String> resultPages() {
        List<String> out = new ArrayList<>();
        if (showsResults()) {
            for (Ink ink : right) {
                if (ink instanceof GuideInk.Link l) {
                    out.add(l.page());
                }
            }
        }
        return out;
    }

    /** Os gestos das cenas à vista (o e2e lê a vista e o centro da primeira). */
    public SceneInput scenes() {
        return scenes;
    }

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

    /**
     * O que não coube no par à vista: texto ou operação que sai da folha, linha descartada, rótulo que passa do
     * espaço; cada um com a página e a folha. Vazio quando tudo cabe.
     */
    public List<String> problems() {
        return problems;
    }

    /** Quantas folhas de conteúdo o par à vista tem (a folha de rosto conta), para o e2e somar as conferidas. */
    public int visibleSheets() {
        return (leftIsCover || !left.isEmpty() ? 1 : 0) + (right.isEmpty() ? 0 : 1);
    }

    /** O centro, em coordenadas de interface, do primeiro link à vista para a página, ou {@code null}. */
    public int @Nullable [] linkCenter(String target) {
        int[] c = center(left, BookScale.LEFT_X, target, false);
        return c != null ? c : center(right, BookScale.RIGHT_X, target, false);
    }

    /** O centro do primeiro ícone à vista que leva à página (no índice, o da linha dela), ou {@code null}. */
    public int @Nullable [] iconCenter(String target) {
        int[] c = center(left, BookScale.LEFT_X, target, true);
        return c != null ? c : center(right, BookScale.RIGHT_X, target, true);
    }

    private int @Nullable [] center(List<Ink> inks, int originX, String target, boolean icon) {
        for (Ink ink : inks) {
            if (!icon && ink instanceof GuideInk.Link l && l.page().equals(target)) {
                return gui(originX, l.x() + l.w() / 2.0, l.y() + l.h() / 2.0);
            }
            if (icon && ink instanceof GuideInk.Item it && target.equals(it.page())) {
                return gui(originX, it.x() + it.size() / 2.0, it.y() + it.size() / 2.0);
            }
        }
        return null;
    }

    private int[] gui(int originX, double ux, double uy) {
        return new int[] {(int) scale.guiX(originX + ux / 2), (int) scale.guiY(BookScale.CONTENT_Y + uy / 2)};
    }

    // ---- desenho

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        scenes.hover(mouseX, mouseY);
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
            cover.draw(g, font, search);
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
        if (scenes.tooltip(g, font, mouseX, mouseY)) {
            return;
        }
        Ink ink = sheetHit(tx, ty);
        if (ink instanceof GuideInk.Item it) {
            g.renderComponentTooltip(font, it.tip(), mouseX, mouseY);
        } else if (ink instanceof GuideInk.Frame f) {
            g.renderTooltip(font, f.full(), mouseX, mouseY);
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
        if (scenes.press(mouseX, mouseY)) {
            return true;
        }
        double tx = scale.texX(mouseX);
        double ty = scale.texY(mouseY);
        GuideChrome.Hit hit = chrome.hit(tx, ty);
        if (hit != null) {
            switch (hit) {
                case PREV -> turnBy(-1);
                case NEXT -> turnBy(1);
                case BACK -> go(null, indexSpread(GuideIndex.groupOrder(GuidePages.group(page))));
                case RIBBON -> go(null, indexSpread(chrome.hitGroup()));
            }
            return true;
        }
        if (leftIsCover && GuideCover.onField(BookScale.unit(tx, BookScale.LEFT_X),
                BookScale.unit(ty, BookScale.CONTENT_Y))) {
            search.focus();
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
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return scenes.drag(mouseX, mouseY) || super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        scenes.release();
        return super.mouseReleased(mouseX, mouseY, button);
    }

    /** A roda numa cena dá zoom nela; fora, vira as folhas. */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0) {
            if (!scenes.scroll(mouseX, mouseY, scrollY)) {
                turnBy(scrollY > 0 ? -1 : 1);
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (!search.type(codePoint)) {
            return super.charTyped(codePoint, modifiers);
        }
        toCover();
        return true;
    }

    /** A busca com o livro na folha de rosto: o que Backspace, Esc, Enter e as setas fazem ali. */
    private boolean searchKey(int keyCode) {
        boolean onCover = page == null && GuideNav.spread() == 0;
        if (!onCover || search.isEmpty()) {
            return false;
        }
        switch (keyCode) {
            case GLFW.GLFW_KEY_BACKSPACE -> search.backspace();
            case GLFW.GLFW_KEY_ESCAPE -> search.clear();
            case GLFW.GLFW_KEY_UP -> search.move(-1);
            case GLFW.GLFW_KEY_DOWN -> search.move(1);
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                String to = search.selectedPage();
                if (to != null) {
                    go(to, 0);
                }
                return true;
            }
            default -> {
                return false;
            }
        }
        refresh();
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (hasControlDown() && keyCode == GLFW.GLFW_KEY_F) {
            toCover();
            return true;
        }
        if (hasControlDown() && keyCode == GLFW.GLFW_KEY_V) {
            search.paste(Minecraft.getInstance().keyboardHandler.getClipboard());
            toCover();
            return true;
        }
        if (searchKey(keyCode)) {
            return true;
        }
        switch (keyCode) {
            case GLFW.GLFW_KEY_LEFT -> turnBy(-1);
            case GLFW.GLFW_KEY_RIGHT -> turnBy(1);
            case GLFW.GLFW_KEY_BACKSPACE -> back();
            default -> {
                return super.keyPressed(keyCode, scanCode, modifiers);
            }
        }
        return true;
    }
}
