package io.github.matheusanbs.wirelessautomate.client;

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
    /** Raio externo do mockup, a unidade das medidas abaixo. */
    private static final double MOCKUP = 182;
    private static final double GAP = 0.035;
    private static final long OPEN_MS = 150;
    private static final float HOT_SCALE = 1.06f;
    private static final int DISC = 0x991B1920;
    private static final int HOT_FILL = 0xFFFFF8EA;
    /** Cores misturadas do ícone "qualquer máquina", como no mockup. */
    private static final int[] ANY_COLORS = {0xFFB57A3A, 0xFF6E7480, 0xFF2F9D5C, 0xFF8E4FC9, 0xFFB57A3A, 0xFFD59A1E};

    private final List<@Nullable ResourceType> types;
    private final ItemStack brushIcon = new ItemStack(ModItems.CONFIGURATOR.get());
    private final long openedAt = Util.getMillis();
    private PasteMode mode;
    private @Nullable ResourceType pasteType;
    private WheelLayout layout = WheelLayout.forRadius(90);
    private int centerX;
    private int centerY;
    private double lastMouseX = -1;
    private double lastMouseY = -1;

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
        layout = WheelLayout.forRadius(Mth.clamp(0.38 * Math.min(width, height), 90, 180));
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

    /** A fatia no ponto da tela, ou {@code null} no centro e fora da roda. */
    public @Nullable Hit hitAt(double x, double y) {
        return layout.hit(x - centerX, y - centerY, MODES.length, types.size());
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
        }
    }

    @Override
    public void mouseMoved(double x, double y) {
        lastMouseX = x;
        lastMouseY = y;
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (ConfiguratorWheelKeys.WHEEL.matches(keyCode, scanCode)) {
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
        float scale = 0.6f + 0.4f * eased;
        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(centerX, centerY, 0);
        pose.mulPose(Axis.ZP.rotationDegrees(-20 * (1 - eased)));
        pose.scale(scale, scale, 1);
        // daqui em diante, coordenadas relativas ao centro da roda
        drawShapes(g, hot);
        drawRing(g, Ring.INNER, hot);
        drawRing(g, Ring.OUTER, hot);
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

    private double unit() {
        return layout.outerRadius() / MOCKUP;
    }

    /** Disco translúcido, fatias com contorno (a sob o mouse por último) e o centro, num só lote de triângulos. */
    private void drawShapes(GuiGraphics g, @Nullable Hit hot) {
        g.flush();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES,
                DefaultVertexFormat.POSITION_COLOR);
        Matrix4f matrix = g.pose().last().pose();
        double u = unit();

        sector(buffer, matrix, 0, layout.outerRadius() + 6 * u, 0, 2 * Math.PI, 0, DISC, 0, 0, 1, 48);
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
        double r = centerRadius();
        sector(buffer, matrix, 0, r + 1, 0, 2 * Math.PI, 0, GuiPaint.FG, 0, 0, 1, 32);
        sector(buffer, matrix, 0, r, 0, 2 * Math.PI, 0, hot != null ? GuiPaint.FG : GuiPaint.PANEL, 0, 0, 1, 32);

        BufferUploader.drawWithShader(buffer.buildOrThrow());
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    /** Uma fatia: o contorno (um pouco maior, por trás) e o preenchimento. */
    private void slice(BufferBuilder buffer, Matrix4f matrix, Ring ring, int index, boolean hot) {
        int count = count(ring);
        double r1 = ring == Ring.INNER ? layout.innerRadius() : layout.outerRingStart();
        double r2 = ring == Ring.INNER ? layout.innerRingEnd() : layout.outerRadius();
        double a0 = WheelLayout.sliceStart(index, count) + GAP;
        double a1 = WheelLayout.sliceEnd(index, count) - GAP;
        boolean current = isCurrent(ring, index);
        double border = current ? 2 : 1;
        double middle = WheelLayout.sliceMiddle(index, count);
        double rm = (r1 + r2) / 2;
        double gx = rm * Math.sin(middle);
        double gy = -rm * Math.cos(middle);
        double grow = hot ? HOT_SCALE : 1;
        int segments = Math.max(12, (int) Math.ceil((a1 - a0) / (Math.PI / 32)));
        sector(buffer, matrix, r1 - border, r2 + border, a0, a1, border,
                current ? GuiPaint.ACCENT : GuiPaint.FG, gx, gy, grow, segments);
        sector(buffer, matrix, r1, r2, a0, a1, 0, hot ? HOT_FILL : GuiPaint.PANEL, gx, gy, grow, segments);
    }

    /**
     * Setor de anel de {@code r1} a {@code r2} e de {@code a0} a {@code a1} (0 no topo, horário), com as
     * bordas laterais afastadas {@code pad} (em comprimento) e escalado {@code grow} em torno de
     * ({@code gx}, {@code gy}).
     */
    private static void sector(BufferBuilder buffer, Matrix4f matrix, double r1, double r2, double a0, double a1,
            double pad, int color, double gx, double gy, double grow, int segments) {
        double inner = Math.max(0, r1);
        double padIn = inner > 0 ? pad / inner : 0;
        double padOut = r2 > 0 ? pad / r2 : 0;
        double in0 = a0 - padIn;
        double in1 = a1 + padIn;
        double out0 = a0 - padOut;
        double out1 = a1 + padOut;
        for (int s = 0; s < segments; s++) {
            double f0 = (double) s / segments;
            double f1 = (double) (s + 1) / segments;
            double ai0 = Mth.lerp(f0, in0, in1);
            double ai1 = Mth.lerp(f1, in0, in1);
            double ao0 = Mth.lerp(f0, out0, out1);
            double ao1 = Mth.lerp(f1, out0, out1);
            vertex(buffer, matrix, inner, ai0, color, gx, gy, grow);
            vertex(buffer, matrix, r2, ao0, color, gx, gy, grow);
            vertex(buffer, matrix, r2, ao1, color, gx, gy, grow);
            vertex(buffer, matrix, inner, ai0, color, gx, gy, grow);
            vertex(buffer, matrix, r2, ao1, color, gx, gy, grow);
            vertex(buffer, matrix, inner, ai1, color, gx, gy, grow);
        }
    }

    private static void vertex(BufferBuilder buffer, Matrix4f matrix, double radius, double angle, int color,
            double gx, double gy, double grow) {
        double x = radius * Math.sin(angle);
        double y = -radius * Math.cos(angle);
        x = gx + (x - gx) * grow;
        y = gy + (y - gy) * grow;
        buffer.addVertex(matrix, (float) x, (float) y, 0).setColor(color);
    }

    /** Ícones e rótulos curtos de um anel; a fatia sob o mouse cresce junto. */
    private void drawRing(GuiGraphics g, Ring ring, @Nullable Hit hot) {
        int count = count(ring);
        double r1 = ring == Ring.INNER ? layout.innerRadius() : layout.outerRingStart();
        double r2 = ring == Ring.INNER ? layout.innerRingEnd() : layout.outerRadius();
        double rm = (r1 + r2) / 2;
        double span = 2 * Math.PI / count - 2 * GAP;
        int labelWidth = (int) Math.min(Math.min(2 * rm * Math.sin(Math.min(span, Math.PI) / 2) * 0.85, 80),
                (r2 - r1) * 2.2);
        boolean labels = labelWidth >= 24 && r2 - r1 >= 26;
        PoseStack pose = g.pose();
        for (int i = 0; i < count; i++) {
            double middle = WheelLayout.sliceMiddle(i, count);
            int x = (int) Math.round(rm * Math.sin(middle));
            int y = (int) Math.round(-rm * Math.cos(middle));
            boolean isHot = isHit(hot, ring, i);
            pose.pushPose();
            if (isHot) {
                pose.translate(x, y, 0);
                pose.scale(HOT_SCALE, HOT_SCALE, 1);
                pose.translate(-x, -y, 0);
            }
            int iconY = labels ? y - 5 : y;
            if (ring == Ring.INNER) {
                modeIcon(g, MODES[i], x, iconY);
            } else {
                typeIcon(g, types.get(i), x, iconY);
            }
            if (labels) {
                Component label = ring == Ring.INNER
                        ? Component.translatable(KEY + "short." + MODES[i].key())
                        : ConfiguratorItem.typeName(types.get(i));
                int w = Math.min(font.width(label), labelWidth);
                GuiText.draw(g, font, label, x - w / 2, iconY + 10, labelWidth, GuiPaint.FG);
            }
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

    /** Ícone de tipo centrado em ({@code x}, {@code y}); Todos é um quadrado neutro. */
    private void typeIcon(GuiGraphics g, @Nullable ResourceType type, int x, int y) {
        int scale = layout.outerRadius() >= 130 ? 2 : 1;
        int size = ResourceStyle.ICON;
        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(x - size * scale / 2f, y - size * scale / 2f, 0);
        pose.scale(scale, scale, 1);
        if (type == null) {
            g.fill(0, 0, size, size, GuiPaint.FG);
            g.fill(1, 1, size - 1, size - 1, GuiPaint.MUTED);
        } else {
            ResourceStyle.drawIcon(g, type, 0, 0);
        }
        pose.popPose();
    }

    private double centerRadius() {
        return layout.innerRadius() - 4 * unit();
    }

    /** Nome e descrição da fatia sob o mouse, ou "Soltar aqui fecha", em texto reduzido para caber. */
    private void drawCenter(GuiGraphics g, @Nullable Hit hot) {
        double r = centerRadius();
        float scale = (float) Mth.clamp(r / 52, 0.5, 1);
        int width = (int) (1.6 * r / scale);
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
        int titleLines = GuiText.lineCount(font, title, width, 2);
        int descLines = desc == null ? 0 : GuiText.lineCount(font, desc, width, 4);
        int textHeight = (titleLines + descLines) * 10 - 2 + (desc == null ? 0 : 2);
        PoseStack pose = g.pose();
        pose.pushPose();
        pose.scale(scale, scale, 1);
        int y = -textHeight / 2;
        y += GuiText.wrapCentered(g, font, title, 0, y, width, 2, hot != null ? GuiPaint.SELECTED_TEXT : GuiPaint.FG);
        if (desc != null) {
            GuiText.wrapCentered(g, font, desc, 0, y + 2, width, 4, GuiPaint.TOOLTIP_MUTED);
        }
        pose.popPose();
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
