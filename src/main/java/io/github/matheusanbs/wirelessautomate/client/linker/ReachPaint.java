package io.github.matheusanbs.wirelessautomate.client.linker;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.client.GuiPaint;
import io.github.matheusanbs.wirelessautomate.client.highlight.HighlightRenderTypes;
import io.github.matheusanbs.wirelessautomate.dock.DockChoice;
import io.github.matheusanbs.wirelessautomate.dock.DockColors;
import io.github.matheusanbs.wirelessautomate.dock.PatternDockBlock;
import io.github.matheusanbs.wirelessautomate.item.LinkerItem;
import io.github.matheusanbs.wirelessautomate.packet.LinkerReachPayload;
import io.github.matheusanbs.wirelessautomate.packet.LinkerReachRequestPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * O cubo do alcance de um roteador ou de uma Doca de Padrões no mundo ({@link LinkerReachPayload}): as arestas do
 * cubo na cor do tier, recortadas na altura do mundo ({@link ReachBox}), e cada máquina vinculada com moldura e um véu
 * leve na cor do grupo ou do lado dela ({@link DockColors}; o cinza fixo {@link #GRAY} para as inativas), desenhados
 * através dos blocos, como o destaque do Olho. Sem o raio ({@link LinkerReachPayload#NO_CUBE}), só as máquinas. Só o
 * que está a menos de {@link #REACH} blocos da câmera. Um cubo por vez: o pacote de qualquer dono substitui o guardado.
 *
 * <p>Os vértices (relativos ao canto do dono) são montados quando o pacote chega; o quadro só soma a câmera e desenha.
 * O cubo de colocar a Doca some quando o prazo vence. O do Vinculador fica guardado e aparece sempre que o Vinculador
 * com este dono escolhido está numa das mãos; sai quando um Vinculador na mão escolhe outra coisa, quando o dono some,
 * ao trocar de mundo e no logout. Sem cubo guardado, o tick só lê os itens das mãos e, com um roteador ou uma Doca
 * escolhidos, pede o cubo ao servidor ({@link LinkerReachRequestPayload}); o quadro volta na primeira linha. Um pedido
 * sem resposta dobra o intervalo até o próximo (de 1 s até 10 s), e depois do de 10 s os pedidos param até o
 * Vinculador sair da mão.
 */
@EventBusSubscriber(modid = WirelessAutomate.MODID, value = Dist.CLIENT)
public final class ReachPaint {
    /** Longe disso da câmera, nem a máquina nem o cubo são desenhados. */
    private static final double REACH = 128.0;
    private static final double REACH_SQR = REACH * REACH;
    private static final RouterTier[] TIERS = RouterTier.values();
    /**
     * Ticks em que o Vinculador ainda pode não ter o dono: o pacote sai no clique, e o item com a escolha nova chega
     * ao cliente na sincronização do inventário, no fim do tick do servidor.
     */
    private static final int HOLD_GRACE_TICKS = 20;
    /** O intervalo depois do primeiro pedido do cubo ao servidor; cada pedido sem resposta o dobra. */
    private static final int REQUEST_INTERVAL_TICKS = 20;
    /** O maior intervalo entre dois pedidos; o pedido feito com ele é o último até o Vinculador sair da mão. */
    private static final int MAX_REQUEST_INTERVAL_TICKS = 200;
    private static final long NO_OWNER = Long.MIN_VALUE;
    /** A cor das máquinas fora do raio ou além do limite ({@link LinkerReachPayload#INACTIVE}). */
    private static final int GRAY = 0x8A8378;
    /** A moldura sai um pouco para fora do bloco. */
    private static final float INFLATE = 0.006f;
    private static final int CUBE_ALPHA = 0xFF;
    private static final int FRAME_ALPHA = 0xFF;
    private static final int VEIL_ALPHA = 0x26;
    /** Números por máquina: as 12 arestas da moldura (duas pontas cada) e as seis faces do véu (quatro cantos). */
    private static final int FRAME_FLOATS = 72;
    private static final int VEIL_FLOATS = 72;

    /** O dono do cubo guardado (o roteador ou a Doca); {@code null} sem cubo. */
    private static @Nullable BlockPos owner;
    /** {@link LinkerReachPayload#ROUTER} ou {@link LinkerReachPayload#DOCK}: de quem é o cubo guardado. */
    private static byte kind;
    /** O cubo guardado tem as arestas (o raio não é {@link LinkerReachPayload#NO_CUBE}). */
    private static boolean hasCube;
    private static @Nullable ClientLevel level;
    /** Prazo restante em ticks; 0: enquanto o Vinculador com este dono está na mão. */
    private static int ticksLeft;
    private static int grace;
    /** O cubo guardado está à mostra: o prazo corre, ou o Vinculador com este dono está numa das mãos. */
    private static boolean visible;
    // Os pedidos do cubo ao servidor ({@link LinkerReachRequestPayload}): o dono pedido (BlockPos.asLong) e o tipo
    // dele, o tempo do mundo em que o próximo pode sair, o intervalo depois dele, se ele é o último e se os pedidos
    // pararam.
    private static long requestedOwner = NO_OWNER;
    private static byte requestedKind;
    private static long nextRequest;
    private static int requestInterval = REQUEST_INTERVAL_TICKS;
    private static boolean lastTry;
    private static boolean requestStopped;
    /** O cubo: limites (para a distância) e arestas, cada uma com as duas pontas (vazias sem o raio). */
    private static float[] box = new float[6];
    private static float[] cubeEdges = new float[0];
    private static int cubeColor;
    /** As máquinas: o centro de cada uma (x, y, z, para a distância) e a cor do grupo ou do lado. */
    private static float[] centers = new float[0];
    private static int[] machineColors = new int[0];
    /** As 12 arestas da moldura de cada máquina ({@link #FRAME_FLOATS} números por máquina). */
    private static float[] frames = new float[0];
    /** As seis faces do véu de cada máquina ({@link #VEIL_FLOATS} números por máquina). */
    private static float[] veils = new float[0];

    private ReachPaint() {
    }

    /** Guarda o cubo do pacote e monta os vértices; chamado na thread do cliente. */
    public static void receive(LinkerReachPayload payload) {
        if (payload.kind() != LinkerReachPayload.ROUTER && payload.kind() != LinkerReachPayload.DOCK) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel here = minecraft.level;
        if (here == null) {
            return;
        }
        int tier = Math.clamp(payload.tier(), 0, TIERS.length - 1);
        BlockPos at = payload.origin();
        hasCube = payload.radius() >= 0;
        if (hasCube) {
            box = ReachBox.bounds(payload.radius(), at.getY(), here.getMinBuildHeight(), here.getMaxBuildHeight());
            cubeEdges = ReachBox.edges(box);
        } else {
            cubeEdges = new float[0];
        }
        cubeColor = CUBE_ALPHA << 24 | GuiPaint.tierColor(TIERS[tier]) & 0xFFFFFF;
        int count = Math.min(payload.machines().length, payload.colors().length);
        float[] middle = new float[count * 3];
        int[] colors = new int[count];
        float[] frameVertices = new float[count * FRAME_FLOATS];
        float[] veilVertices = new float[count * VEIL_FLOATS];
        for (int i = 0; i < count; i++) {
            long m = payload.machines()[i];
            float x = BlockPos.getX(m) - at.getX();
            float y = BlockPos.getY(m) - at.getY();
            float z = BlockPos.getZ(m) - at.getZ();
            middle[i * 3] = x + 0.5f;
            middle[i * 3 + 1] = y + 0.5f;
            middle[i * 3 + 2] = z + 0.5f;
            byte index = payload.colors()[i];
            colors[i] = (index == LinkerReachPayload.INACTIVE ? GRAY : DockColors.side(Math.clamp(index, 0, 5)))
                    & 0xFFFFFF;
            float[] edges = ReachBox.edges(new float[] {x - INFLATE, y - INFLATE, z - INFLATE, x + 1 + INFLATE,
                    y + 1 + INFLATE, z + 1 + INFLATE});
            System.arraycopy(edges, 0, frameVertices, i * FRAME_FLOATS, FRAME_FLOATS);
            veil(veilVertices, i * VEIL_FLOATS, x, y, z);
        }
        centers = middle;
        machineColors = colors;
        frames = frameVertices;
        veils = veilVertices;
        ticksLeft = Math.max(payload.ticks(), 0);
        grace = HOLD_GRACE_TICKS;
        visible = true;
        level = here;
        owner = at;
        kind = payload.kind();
        resetRequests();
    }

    /** O cubo à mostra agora, para o e2e. */
    public static boolean shown() {
        return owner != null && visible;
    }

    /** Há um cubo guardado, à mostra ou não, para o e2e. */
    public static boolean stored() {
        return owner != null;
    }

    /** Quantas máquinas o cubo acende, para o e2e. */
    public static int machineCount() {
        return owner == null ? 0 : machineColors.length;
    }

    /** O cubo guardado é o de um roteador, para o e2e. */
    public static boolean isRouter() {
        return owner != null && kind == LinkerReachPayload.ROUTER;
    }

    public static void clear() {
        owner = null;
        visible = false;
        level = null;
        cubeEdges = new float[0];
        centers = new float[0];
        machineColors = new int[0];
        frames = new float[0];
        veils = new float[0];
    }

    // ------------------------------------------------------------------ eventos

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        BlockPos at = owner;
        if (at == null) {
            request(minecraft);
            return;
        }
        ClientLevel here = minecraft.level;
        if (here == null || here != level || minecraft.player == null
                || here.isLoaded(at) && !isOwnerBlock(here, at)) {
            clear();
            return;
        }
        if (ticksLeft > 0) {
            if (--ticksLeft == 0) {
                clear();
            }
            return;
        }
        switch (hands(minecraft.player, here, at)) {
            case THIS_OWNER -> {
                visible = true;
                grace = 0;
            }
            case OTHER_CHOICE -> {
                // logo depois do pacote o item ainda pode não ter a escolha nova
                if (grace > 0) {
                    grace--;
                } else {
                    clear();
                }
            }
            case NO_LINKER -> {
                visible = false;
                if (grace > 0) {
                    grace--;
                }
            }
        }
    }

    /** O bloco do dono guardado ainda está no lugar: o roteador ou a Doca, pelo tipo do cubo. */
    private static boolean isOwnerBlock(ClientLevel here, BlockPos at) {
        Block block = here.getBlockState(at).getBlock();
        return kind == LinkerReachPayload.ROUTER ? block instanceof RouterBlock : block instanceof PatternDockBlock;
    }

    private enum Hands {
        /** O Vinculador numa das mãos tem este dono escolhido. */
        THIS_OWNER,
        /** Um Vinculador na mão escolheu outra coisa (ou nada): o cubo guardado não vale mais. */
        OTHER_CHOICE,
        /** Nenhum Vinculador nas mãos: o cubo fica guardado, sem desenhar. */
        NO_LINKER
    }

    private static Hands hands(LocalPlayer player, ClientLevel here, BlockPos at) {
        ItemStack main = player.getMainHandItem();
        ItemStack off = player.getOffhandItem();
        if (chose(main, here, at) || chose(off, here, at)) {
            return Hands.THIS_OWNER;
        }
        return main.getItem() instanceof LinkerItem || off.getItem() instanceof LinkerItem ? Hands.OTHER_CHOICE
                : Hands.NO_LINKER;
    }

    /**
     * Sem cubo guardado: se o Vinculador numa das mãos tem um roteador ou uma Doca desta dimensão escolhidos (entrou no
     * mundo de novo, ou o prazo de colocar venceu), pede o cubo ao servidor. O servidor não responde por um dono
     * quebrado, descarregado ou sem acesso: cada pedido sem resposta dobra a espera até o próximo (1 s, 2 s, 4 s, 8 s, 10 s), e
     * depois do de 10 s os pedidos param até o Vinculador sair da mão ou escolher outro dono. Sem Vinculador, só as
     * duas leituras do item.
     */
    private static void request(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        ClientLevel here = minecraft.level;
        if (player == null || here == null) {
            return;
        }
        ItemStack main = player.getMainHandItem();
        ItemStack off = player.getOffhandItem();
        byte wanted = LinkerReachPayload.ROUTER;
        BlockPos chosen = chosen(main, here, wanted);
        if (chosen == null) {
            wanted = LinkerReachPayload.DOCK;
            chosen = chosen(main, here, wanted);
        }
        if (chosen == null) {
            wanted = LinkerReachPayload.ROUTER;
            chosen = chosen(off, here, wanted);
        }
        if (chosen == null) {
            wanted = LinkerReachPayload.DOCK;
            chosen = chosen(off, here, wanted);
        }
        if (chosen == null) {
            if (requestedOwner != NO_OWNER) {
                resetRequests();
            }
            return;
        }
        long key = chosen.asLong();
        if (key != requestedOwner || wanted != requestedKind) {
            resetRequests();
            requestedOwner = key;
            requestedKind = wanted;
        }
        long now = here.getGameTime();
        // o tempo do mundo volta ao trocar de mundo: um prazo longe demais no futuro não vale
        if (requestStopped || now < nextRequest && nextRequest - now <= MAX_REQUEST_INTERVAL_TICKS) {
            return;
        }
        if (lastTry) {
            requestStopped = true;
            return;
        }
        nextRequest = now + requestInterval;
        lastTry = requestInterval == MAX_REQUEST_INTERVAL_TICKS;
        requestInterval = Math.min(requestInterval * 2, MAX_REQUEST_INTERVAL_TICKS);
        PacketDistributor.sendToServer(new LinkerReachRequestPayload(wanted, chosen));
    }

    /** Os pedidos voltam ao começo: o cubo chegou, o Vinculador saiu da mão ou escolheu outro dono, ou o logout. */
    private static void resetRequests() {
        requestedOwner = NO_OWNER;
        nextRequest = 0;
        requestInterval = REQUEST_INTERVAL_TICKS;
        lastTry = false;
        requestStopped = false;
    }

    /**
     * O roteador ({@code kind} {@link LinkerReachPayload#ROUTER}) ou a Doca (o outro) que o Vinculador {@code stack}
     * tem escolhido, se é desta dimensão; {@code null} sem escolha ou sem Vinculador.
     */
    private static @Nullable BlockPos chosen(ItemStack stack, ClientLevel here, byte kind) {
        if (!(stack.getItem() instanceof LinkerItem)) {
            return null;
        }
        GlobalPos pos;
        if (kind == LinkerReachPayload.ROUTER) {
            pos = LinkerItem.router(stack);
        } else {
            DockChoice choice = LinkerItem.dock(stack);
            pos = choice == null ? null : choice.pos();
        }
        return pos != null && pos.dimension().equals(here.dimension()) ? pos.pos() : null;
    }

    private static boolean chose(ItemStack stack, ClientLevel here, BlockPos at) {
        return at.equals(chosen(stack, here, kind));
    }

    @SubscribeEvent
    static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
        resetRequests();
    }

    @SubscribeEvent
    static void onRender(RenderLevelStageEvent event) {
        BlockPos at = owner;
        if (at == null || !visible || event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        Vec3 camera = event.getCamera().getPosition();
        // a câmera relativa ao canto do dono, e o dono relativo à câmera
        double cx = camera.x - at.getX();
        double cy = camera.y - at.getY();
        double cz = camera.z - at.getZ();
        float ox = (float) -cx;
        float oy = (float) -cy;
        float oz = (float) -cz;
        boolean cube = hasCube && ReachBox.distanceSqr(box, cx, cy, cz) <= REACH_SQR;
        PoseStack.Pose pose = event.getPoseStack().last();
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        float[] c = centers;
        int[] colors = machineColors;
        int shown = 0;
        VertexConsumer veil = null;
        for (int i = 0; i < colors.length; i++) {
            if (near(c, i, cx, cy, cz)) {
                if (veil == null) {
                    veil = buffers.getBuffer(HighlightRenderTypes.highlightVeil());
                }
                int argb = VEIL_ALPHA << 24 | colors[i];
                float[] v = veils;
                for (int k = i * VEIL_FLOATS, last = k + VEIL_FLOATS; k < last; k += 3) {
                    veil.addVertex(pose, ox + v[k], oy + v[k + 1], oz + v[k + 2]).setColor(argb);
                }
                shown++;
            }
        }
        if (veil != null) {
            buffers.endBatch(HighlightRenderTypes.highlightVeil());
        }
        if (!cube && shown == 0) {
            return;
        }
        VertexConsumer lines = buffers.getBuffer(HighlightRenderTypes.highlightLines());
        if (cube) {
            lines(lines, pose, cubeEdges, 0, cubeEdges.length, ox, oy, oz, cubeColor);
        }
        for (int i = 0; i < colors.length; i++) {
            if (near(c, i, cx, cy, cz)) {
                lines(lines, pose, frames, i * FRAME_FLOATS, FRAME_FLOATS, ox, oy, oz, FRAME_ALPHA << 24 | colors[i]);
            }
        }
        buffers.endBatch(HighlightRenderTypes.highlightLines());
    }

    /** A máquina {@code i} está a menos de {@link #REACH} da câmera (todos relativos ao canto do dono). */
    private static boolean near(float[] c, int i, double cx, double cy, double cz) {
        double dx = c[i * 3] - cx;
        double dy = c[i * 3 + 1] - cy;
        double dz = c[i * 3 + 2] - cz;
        return dx * dx + dy * dy + dz * dz <= REACH_SQR;
    }

    /**
     * As arestas de {@code e} (seis números por aresta, as duas pontas), de {@code from} a {@code from + length},
     * deslocadas por ({@code ox}, {@code oy}, {@code oz}). Arestas paralelas a um eixo: a normal é o próprio eixo.
     */
    private static void lines(VertexConsumer lines, PoseStack.Pose pose, float[] e, int from, int length, float ox,
            float oy, float oz, int argb) {
        for (int k = from, last = from + length; k < last; k += 6) {
            float nx = e[k + 3] != e[k] ? 1 : 0;
            float ny = e[k + 4] != e[k + 1] ? 1 : 0;
            float nz = nx == 0 && ny == 0 ? 1 : 0;
            lines.addVertex(pose, ox + e[k], oy + e[k + 1], oz + e[k + 2]).setColor(argb).setNormal(pose, nx, ny, nz);
            lines.addVertex(pose, ox + e[k + 3], oy + e[k + 4], oz + e[k + 5]).setColor(argb)
                    .setNormal(pose, nx, ny, nz);
        }
    }

    /** As seis faces do bloco no canto ({@code x}, {@code y}, {@code z}), quatro cantos cada, em {@code out}. */
    private static void veil(float[] out, int at, float x, float y, float z) {
        float x1 = x + 1;
        float y1 = y + 1;
        float z1 = z + 1;
        float[] corners = {
            x, y, z, x1, y, z, x1, y, z1, x, y, z1,
            x, y1, z, x, y1, z1, x1, y1, z1, x1, y1, z,
            x, y, z, x, y1, z, x1, y1, z, x1, y, z,
            x, y, z1, x1, y, z1, x1, y1, z1, x, y1, z1,
            x, y, z, x, y, z1, x, y1, z1, x, y1, z,
            x1, y, z, x1, y1, z, x1, y1, z1, x1, y, z1,
        };
        System.arraycopy(corners, 0, out, at, VEIL_FLOATS);
    }
}
