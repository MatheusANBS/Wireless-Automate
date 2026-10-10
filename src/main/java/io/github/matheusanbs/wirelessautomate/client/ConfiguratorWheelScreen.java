package io.github.matheusanbs.wirelessautomate.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import io.github.matheusanbs.wirelessautomate.client.WheelLayout.Hit;
import io.github.matheusanbs.wirelessautomate.client.WheelLayout.Ring;
import io.github.matheusanbs.wirelessautomate.item.ConfiguratorItem;
import io.github.matheusanbs.wirelessautomate.network.LoadedTypes;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.packet.ConfiguratorWheelPayload;
import io.github.matheusanbs.wirelessautomate.preset.PasteMode;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

/**
 * Roda radial do Configurador (segurar a tecla {@link ConfiguratorWheelKeys#WHEEL}): o anel de dentro
 * escolhe o modo de colar, o de fora o tipo colado (Todos e os tipos carregados). Soltar a tecla
 * escolhe a fatia sob o mouse e fecha; clique esquerdo escolhe sem fechar; Esc fecha. Não pausa o jogo
 * nem escurece a tela inteira: só um disco translúcido atrás da roda. Visual do mockup aprovado: fatias
 * na cor do painel com contorno de tinta, a atual de cada anel com contorno de destaque mais grosso,
 * a sob o mouse cresce 6 % e clareia; o centro mostra o nome e a descrição dela.
 */
public final class ConfiguratorWheelScreen extends Screen {
    private static final String KEY = "gui.wirelessautomate.configurator_wheel.";
    private static final PasteMode[] MODES = PasteMode.values();
    private static final long OPEN_MS = 150;
    /** Raio externo da roda no desenho, antes da escala para a tela. */
    private static final double DESIGN_RADIUS = 150;
    /** Parte da menor dimensão da tela que o raio externo pode ocupar. */
    private static final double SCREEN_FRACTION = 0.45;
    /** Disco escuro atrás da roda: aparece na borda e nos vãos entre as fatias. */
    private static final int DISC = 0xD81B1920;
    /** Fatia sob o mouse: um tom claro do laranja de destaque. */
    private static final int HOT_FILL = 0xFFFDE3D6;
    /** Contorno das fatias (por dentro): tinta fina, a opção atual de cada anel em destaque mais grosso. */
    private static final int BORDER = 1;
    private static final int CURRENT_BORDER = 2;
    /** Ícone (16 a 18 px), espaço e nome (texto do jogo, 8 px) empilhados no raio de cada fatia. */
    private static final int ICON = 18;
    private static final int LABEL_GAP = 3;
    private static final int LABEL_HEIGHT = 8;
    private static final int CONTENT_HEIGHT = ICON + LABEL_GAP + LABEL_HEIGHT;
    /** Altura de uma linha de texto no centro. */
    private static final int LINE = 10;
    /** Linhas máximas do nome e da descrição no centro. */
    private static final int TITLE_LINES = 2;
    private static final int DESC_LINES = 3;
    /** Cores misturadas do ícone "qualquer máquina", como no mockup. */
    private static final int[] ANY_COLORS = {0xFFB57A3A, 0xFF6E7480, 0xFF2F9D5C, 0xFF8E4FC9, 0xFFB57A3A, 0xFFD59A1E};

    private final List<@Nullable ResourceType> types;
    private final ItemStack brushIcon = new ItemStack(ModItems.CONFIGURATOR.get());
    private final long openedAt = Util.getMillis();
    private PasteMode mode;
    private @Nullable ResourceType pasteType;
    /**
     * A roda é desenhada sempre no mesmo tamanho ({@link #DESIGN_RADIUS}) e escalada para a tela por
     * {@link #wheelScale}, numa escala nítida (um número inteiro de pixels da janela por pixel do
     * desenho): assim as proporções e o texto são os mesmos em qualquer escala de GUI.
     */
    private final WheelLayout layout = WheelLayout.forRadius(DESIGN_RADIUS);
    private float wheelScale = 1;
    private int centerX;
    private int centerY;
    private double lastMouseX = -1;
    private double lastMouseY = -1;
    /** Aberta pela tecla (não pelo e2e): o {@link #tick()} confere se a tecla ainda está apertada. */
    private boolean openedByKey;
    /** Já chegou o {@code keyReleased}/{@code mouseReleased} da tecla da roda. */
    private boolean releaseSeen;

    /**
     * A roda aberta pela tecla: se a tecla já foi solta antes de a tela abrir (toque rápido), o
     * soltar não chega à tela e ela fecha sozinha no primeiro tick, sem escolher nada.
     */
    public static ConfiguratorWheelScreen fromKey() {
        ConfiguratorWheelScreen screen = new ConfiguratorWheelScreen();
        screen.openedByKey = true;
        return screen;
    }

    public ConfiguratorWheelScreen() {
        super(Component.translatable("key.wirelessautomate.configurator_wheel"));
        List<@Nullable ResourceType> list = new ArrayList<>();
        list.add(null);
        list.addAll(LoadedTypes.LIST);
        this.types = Collections.unmodifiableList(list);
        Player player = Minecraft.getInstance().player;
        ItemStack stack = player == null ? ItemStack.EMPTY : player.getMainHandItem();
        this.mode = ConfiguratorItem.pasteMode(stack);
        this.pasteType = ConfiguratorItem.type(stack);
    }

    @Override
    protected void init() {
        centerX = width / 2;
        centerY = height / 2;
        wheelScale = crispScale(SCREEN_FRACTION * Math.min(width, height) / DESIGN_RADIUS);
        if (lastMouseX < 0) {
            lastMouseX = centerX;
            lastMouseY = centerY;
        }
    }

    // ------------------------------------------------------------------ estado (também para o e2e)

    /** Modo escolhido agora (começa no do item). */
    public PasteMode mode() {
        return mode;
    }

    /** Tipo colado escolhido agora; {@code null} = Todos. */
    public @Nullable ResourceType pasteType() {
        return pasteType;
    }

    /** Os itens do anel de fora: Todos ({@code null}) e os tipos carregados. */
    public List<@Nullable ResourceType> types() {
        return types;
    }

    public WheelLayout layout() {
        return layout;
    }

    /** A fatia do modo {@code mode}. */
    public Hit hitOf(PasteMode mode) {
        return new Hit(Ring.INNER, mode.ordinal());
    }

    /** A fatia do tipo {@code type} ({@code null} = Todos), ou {@code null} se ele não está na roda. */
    public @Nullable Hit hitOf(@Nullable ResourceType type) {
        int index = types.indexOf(type);
        return index < 0 ? null : new Hit(Ring.OUTER, index);
    }

    /** Escala da roda na tela (o desenho tem raio externo {@link #DESIGN_RADIUS}). */
    public float wheelScale() {
        return wheelScale;
    }

    /** A fatia no ponto da tela, ou {@code null} no centro e fora da roda. */
    public @Nullable Hit hitAt(double x, double y) {
        return layout.hit((x - centerX) / wheelScale, (y - centerY) / wheelScale, MODES.length, types.size());
    }

    /** Ponto da tela no meio da fatia {@code hit} (para mover o mouse até ela no e2e e nas capturas). */
    public int[] pointOf(Hit hit) {
        double radius = layout.ringMiddle(hit.ring()) * wheelScale;
        double angle = WheelLayout.sliceMiddle(hit.index(), count(hit.ring()));
        return new int[] {(int) Math.round(centerX + radius * Math.sin(angle)),
                (int) Math.round(centerY - radius * Math.cos(angle))};
    }

    /**
     * Aplica a escolha da fatia {@code hit}: muda o estado da tela na hora e manda o estado completo ao
     * servidor (que confere e grava no item). {@code null} não muda nada e devolve {@code false}.
     */
    public boolean choose(@Nullable Hit hit) {
        if (hit == null) {
            return false;
        }
        if (hit.ring() == Ring.INNER) {
            if (hit.index() < 0 || hit.index() >= MODES.length) {
                return false;
            }
            mode = MODES[hit.index()];
        } else {
            if (hit.index() < 0 || hit.index() >= types.size()) {
                return false;
            }
            pasteType = types.get(hit.index());
        }
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection != null && connection.hasChannel(ConfiguratorWheelPayload.TYPE)) {
            PacketDistributor.sendToServer(new ConfiguratorWheelPayload(mode, Optional.ofNullable(pasteType)));
        }
        return true;
    }

    // ------------------------------------------------------------------ entrada

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        Player player = Minecraft.getInstance().player;
        if (player == null || !player.getMainHandItem().is(ModItems.CONFIGURATOR.get())) {
            onClose();
            return;
        }
        if (openedByKey && !releaseSeen && !wheelKeyDown()) {
            // toque rápido: a tecla foi solta antes de a tela abrir, o soltar não chegou aqui
            onClose();
        }
    }

    /** Se a tecla da roda está fisicamente apertada agora (teclado ou botão do mouse). */
    private static boolean wheelKeyDown() {
        InputConstants.Key key = ConfiguratorWheelKeys.WHEEL.getKey();
        if (key.getValue() == InputConstants.UNKNOWN.getValue()) {
            return true;
        }
        long window = Minecraft.getInstance().getWindow().getWindow();
        if (key.getType() == InputConstants.Type.MOUSE) {
            return GLFW.glfwGetMouseButton(window, key.getValue()) == GLFW.GLFW_PRESS;
        }
        return InputConstants.isKeyDown(window, key.getValue());
    }

    @Override
    public void mouseMoved(double x, double y) {
        lastMouseX = x;
        lastMouseY = y;
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (ConfiguratorWheelKeys.WHEEL.matches(keyCode, scanCode)) {
            releaseSeen = true;
            choose(hitAt(lastMouseX, lastMouseY));
            onClose();
            return true;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        lastMouseX = x;
        lastMouseY = y;
        if (ConfiguratorWheelKeys.WHEEL.matchesMouse(button)) {
            return true;
        }
        if (button == 0) {
            choose(hitAt(x, y));
            return true;
        }
        return super.mouseClicked(x, y, button);
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        if (ConfiguratorWheelKeys.WHEEL.matchesMouse(button)) {
            releaseSeen = true;
            choose(hitAt(x, y));
            onClose();
            return true;
        }
        return super.mouseReleased(x, y, button);
    }

    // ------------------------------------------------------------------ desenho

    /** Sem o fundo padrão (blur e escurecido da tela inteira): só o disco da roda. */
    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        GuiText.beginFrame();
        Hit hot = hitAt(mouseX, mouseY);
        float eased = easeOutBack(Mth.clamp((Util.getMillis() - openedAt) / (float) OPEN_MS, 0, 1));
        float scale = wheelScale * (0.6f + 0.4f * eased);
        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(centerX, centerY, 0);
        pose.mulPose(Axis.ZP.rotationDegrees(-20 * (1 - eased)));
        pose.scale(scale, scale, 1);
        // daqui em diante, coordenadas relativas ao centro da roda
        drawShapes(g, hot);
        drawRing(g, Ring.INNER);
        drawRing(g, Ring.OUTER);
        drawCenter(g, hot);
        pose.popPose();
    }

    /** Ease-out-back: chega a 1 passando um pouco além. */
    private static float easeOutBack(float t) {
        float c1 = 1.70158f;
        float c3 = c1 + 1;
        float u = t - 1;
        return 1 + c3 * u * u * u + c1 * u * u;
    }

    // ------------------------------------------------------------------ medidas

    /**
     * A maior escala nítida até {@code fit}: {@code k / gui} com {@code k} inteiro (na escala de GUI 3:
     * 1/3, 2/3, 1, 4/3...), no mínimo {@code 1 / gui} e no máximo 2.
     */
    private static float crispScale(double fit) {
        int gui = Math.max(1, (int) Math.round(Minecraft.getInstance().getWindow().getGuiScale()));
        int k = Mth.clamp((int) Math.floor(fit * gui + 1e-6), 1, 2 * gui);
        return (float) k / gui;
    }

    private Component label(Ring ring, int index) {
        return ring == Ring.INNER
                ? Component.translatable(KEY + "short." + MODES[index].key())
                : ConfiguratorItem.typeName(types.get(index));
    }

    // ------------------------------------------------------------------ formas

    /** Disco escuro, fatias com contorno por dentro (a sob o mouse por último) e o centro, num só lote de triângulos. */
    private void drawShapes(GuiGraphics g, @Nullable Hit hot) {
        g.flush();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES,
                DefaultVertexFormat.POSITION_COLOR);
        Matrix4f matrix = g.pose().last().pose();

        sector(buffer, matrix, 0, layout.outerRadius() + layout.gap() + 1, 0, 2 * Math.PI, 0, DISC, 64);
        for (Ring ring : Ring.values()) {
            int count = count(ring);
            for (int i = 0; i < count; i++) {
                if (!isHit(hot, ring, i)) {
                    slice(buffer, matrix, ring, i, false);
                }
            }
        }
        if (hot != null) {
            slice(buffer, matrix, hot.ring(), hot.index(), true);
        }
        double r = layout.centerRadius();
        sector(buffer, matrix, 0, r, 0, 2 * Math.PI, 0, GuiPaint.FG, 48);
        sector(buffer, matrix, 0, r - BORDER, 0, 2 * Math.PI, 0, hot != null ? GuiPaint.FG : GuiPaint.PANEL, 48);

        BufferUploader.drawWithShader(buffer.buildOrThrow());
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    /**
     * Uma fatia: a forma inteira na cor do contorno e o preenchimento recuado pela grossura dele (o
     * contorno fica por dentro e nunca invade o vão). As bordas laterais ficam a meio vão da linha que
     * divide as fatias, em pixels, então o vão tem a mesma largura perto e longe do centro. A sob o
     * mouse cresce um pouco para dentro do vão, sem fechá-lo.
     */
    private void slice(BufferBuilder buffer, Matrix4f matrix, Ring ring, int index, boolean hot) {
        int count = count(ring);
        double grow = hot ? layout.gap() / 4 : 0;
        double r1 = layout.ringStart(ring) - grow;
        double r2 = layout.ringEnd(ring) + grow;
        double a0 = WheelLayout.sliceStart(index, count);
        double a1 = WheelLayout.sliceEnd(index, count);
        double side = layout.gap() / 2 - grow;
        boolean current = isCurrent(ring, index);
        int border = current ? CURRENT_BORDER : BORDER;
        int segments = Math.max(12, (int) Math.ceil((a1 - a0) / (Math.PI / 48)));
        sector(buffer, matrix, r1, r2, a0, a1, side, current ? GuiPaint.ACCENT : GuiPaint.FG, segments);
        sector(buffer, matrix, r1 + border, r2 - border, a0, a1, side + border, hot ? HOT_FILL : GuiPaint.PANEL,
                segments);
    }

    /**
     * Setor de anel de {@code r1} a {@code r2} e de {@code a0} a {@code a1} (0 no topo, horário), com as
     * bordas laterais recuadas {@code inset} (em pixels, na perpendicular, não em ângulo) para dentro.
     */
    private static void sector(BufferBuilder buffer, Matrix4f matrix, double r1, double r2, double a0, double a1,
            double inset, int color, int segments) {
        double inner = Math.max(0, r1);
        for (int s = 0; s < segments; s++) {
            double f0 = (double) s / segments;
            double f1 = (double) (s + 1) / segments;
            double[] i0 = edgePoint(inner, f0, a0, a1, inset);
            double[] i1 = edgePoint(inner, f1, a0, a1, inset);
            double[] o0 = edgePoint(r2, f0, a0, a1, inset);
            double[] o1 = edgePoint(r2, f1, a0, a1, inset);
            vertex(buffer, matrix, i0, color);
            vertex(buffer, matrix, o0, color);
            vertex(buffer, matrix, o1, color);
            vertex(buffer, matrix, i0, color);
            vertex(buffer, matrix, o1, color);
            vertex(buffer, matrix, i1, color);
        }
    }

    /**
     * Ponto do arco de raio {@code radius} na fração {@code f} do caminho entre as bordas, que ficam
     * recuadas {@code inset} na perpendicular: no raio {@code radius}, o recuo em ângulo é
     * {@code asin(inset / radius)}, maior perto do centro.
     */
    private static double[] edgePoint(double radius, double f, double a0, double a1, double inset) {
        double start = a0;
        double end = a1;
        if (inset != 0 && radius > 0 && a1 - a0 < 2 * Math.PI - 1e-6) {
            double shift = Math.asin(Mth.clamp(inset / radius, -1, 1));
            start = a0 + shift;
            end = a1 - shift;
            if (end < start) {
                start = end = (a0 + a1) / 2;
            }
        }
        double angle = Mth.lerp(f, start, end);
        return new double[] {radius * Math.sin(angle), -radius * Math.cos(angle)};
    }

    private static void vertex(BufferBuilder buffer, Matrix4f matrix, double[] point, int color) {
        buffer.addVertex(matrix, (float) point[0], (float) point[1], 0).setColor(color);
    }

    // ------------------------------------------------------------------ conteúdo

    /**
     * Ícones e nomes curtos de um anel. O ícone fica reto, no ângulo do meio da fatia; o nome acompanha
     * a curva do anel, centrado no mesmo ângulo. Ícone e nome ocupam juntos a altura
     * {@link #CONTENT_HEIGHT}, centrada no raio do meio do anel, com o ícone sempre "acima" do nome na
     * tela: na metade de cima o ícone vai para fora e o nome para dentro; na de baixo o nome é virado e
     * os dois trocam de lado ({@link WheelLayout#flipped}).
     */
    private void drawRing(GuiGraphics g, Ring ring) {
        int count = count(ring);
        double middleRadius = layout.ringMiddle(ring);
        double iconOffset = CONTENT_HEIGHT / 2.0 - ICON / 2.0;
        double labelOffset = CONTENT_HEIGHT / 2.0 - LABEL_HEIGHT / 2.0;
        for (int i = 0; i < count; i++) {
            double angle = WheelLayout.sliceMiddle(i, count);
            boolean flipped = WheelLayout.flipped(angle);
            double iconRadius = middleRadius + (flipped ? -iconOffset : iconOffset);
            int x = (int) Math.round(iconRadius * Math.sin(angle));
            int y = (int) Math.round(-iconRadius * Math.cos(angle));
            if (ring == Ring.INNER) {
                modeIcon(g, MODES[i], x, y);
            } else {
                typeIcon(g, types.get(i), x, y);
            }
            double labelRadius = middleRadius + (flipped ? labelOffset : -labelOffset);
            arcText(g, label(ring, i), labelRadius, angle, flipped,
                    layout.labelArc(labelRadius, count, CURRENT_BORDER + 2));
        }
    }

    /**
     * Texto que acompanha a curva: cada letra no seu ângulo do arco de raio {@code radius}, girada para
     * ficar tangente a ele, com o texto centrado em {@code middle}. Virado, segue no sentido anti-horário
     * e cada letra gira meia volta a mais (lido da esquerda para a direita na metade de baixo). Maior que
     * {@code maxArc}, é abreviado com "…" (não acontece com os nomes de hoje).
     */
    private void arcText(GuiGraphics g, Component text, double radius, double middle, boolean flipped, double maxArc) {
        String string = text.getString();
        if (font.width(string) > maxArc) {
            string = font.plainSubstrByWidth(string, Math.max(0, (int) maxArc - font.width("…"))) + "…";
        }
        double direction = flipped ? -1 : 1;
        double position = -font.width(string) / 2.0;
        PoseStack pose = g.pose();
        for (int offset = 0; offset < string.length(); ) {
            int codePoint = string.codePointAt(offset);
            String letter = new String(Character.toChars(codePoint));
            offset += Character.charCount(codePoint);
            int advance = font.width(letter);
            double angle = middle + direction * (position + advance / 2.0) / radius;
            position += advance;
            pose.pushPose();
            pose.translate(radius * Math.sin(angle), -radius * Math.cos(angle), 0);
            pose.mulPose(Axis.ZP.rotation((float) (flipped ? angle + Math.PI : angle)));
            // a letra centrada no ponto do arco (a largura sem o espaço da direita, a altura sem a sombra)
            g.drawString(font, letter, -(advance - 1) / 2, -LABEL_HEIGHT / 2, GuiPaint.FG, false);
            pose.popPose();
        }
    }

    /** Ícone de modo centrado em ({@code x}, {@code y}): o Configurador no pincel, grades nos de área. */
    private void modeIcon(GuiGraphics g, PasteMode mode, int x, int y) {
        if (mode == PasteMode.BRUSH) {
            g.renderItem(brushIcon, x - 8, y - 8);
            return;
        }
        int left = x - 10;
        int top = y - 7;
        int same = ResourceStyle.color(ResourceType.ITEM);
        for (int i = 0; i < 6; i++) {
            int bx = left + (i % 3) * 7;
            int by = top + (i / 3) * 8;
            int fill = mode.anyMachine() ? ANY_COLORS[i] : same;
            g.fill(bx, by, bx + 6, by + 6, GuiPaint.FG);
            g.fill(bx + 1, by + 1, bx + 5, by + 5, fill);
        }
    }

    /** Ícone de tipo centrado em ({@code x}, {@code y}), em dobro (18 × 18); Todos é um quadrado neutro. */
    private void typeIcon(GuiGraphics g, @Nullable ResourceType type, int x, int y) {
        int size = ResourceStyle.ICON;
        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(x - size, y - size, 0);
        pose.scale(2, 2, 1);
        if (type == null) {
            g.fill(0, 0, size, size, GuiPaint.FG);
            g.fill(1, 1, size - 1, size - 1, GuiPaint.MUTED);
        } else {
            ResourceStyle.drawIcon(g, type, 0, 0);
        }
        pose.popPose();
    }

    /**
     * Nome e descrição da fatia sob o mouse, ou "Soltar aqui fecha", em texto de escala 1, centrados no
     * disco central. A largura das linhas é a do disco menos uma margem (a corda no alto e embaixo do
     * bloco de até cinco linhas ainda cabe); texto maior que isso o {@link GuiText} abrevia.
     */
    private void drawCenter(GuiGraphics g, @Nullable Hit hot) {
        Component title;
        Component desc;
        if (hot == null) {
            title = Component.translatable(KEY + "cancel");
            desc = null;
        } else if (hot.ring() == Ring.INNER) {
            PasteMode m = MODES[hot.index()];
            title = ConfiguratorItem.modeName(m);
            desc = Component.translatable(KEY + "desc." + m.key());
        } else {
            ResourceType type = types.get(hot.index());
            title = ConfiguratorItem.typeName(type);
            desc = type == null ? Component.translatable(KEY + "all_tabs") : capitalized(ConfiguratorItem.onlyTab(type));
        }
        int width = (int) (layout.centerRadius() * 1.6);
        int titleLines = GuiText.lineCount(font, title, width, TITLE_LINES);
        int descLines = desc == null ? 0 : GuiText.lineCount(font, desc, width, DESC_LINES);
        int height = (titleLines + descLines) * LINE - 2 + (descLines > 0 ? 3 : 0);
        int y = -height / 2;
        y += GuiText.wrapCentered(g, font, title, 0, y, width, TITLE_LINES,
                hot != null ? GuiPaint.SELECTED_TEXT : GuiPaint.FG);
        if (desc != null) {
            GuiText.wrapCentered(g, font, desc, 0, y + 3, width, DESC_LINES, GuiPaint.TOOLTIP_MUTED);
        }
    }

    private static Component capitalized(@Nullable Component text) {
        String s = text == null ? "" : text.getString();
        return Component.literal(s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1));
    }

    private int count(Ring ring) {
        return ring == Ring.INNER ? MODES.length : types.size();
    }

    private boolean isCurrent(Ring ring, int index) {
        return ring == Ring.INNER ? MODES[index] == mode : types.get(index) == pasteType;
    }

    private static boolean isHit(@Nullable Hit hit, Ring ring, int index) {
        return hit != null && hit.ring() == ring && hit.index() == index;
    }
}
