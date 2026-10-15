package io.github.matheusanbs.wirelessautomate.client.resonator;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import io.github.matheusanbs.wirelessautomate.resonator.ProjectionDiff;
import io.github.matheusanbs.wirelessautomate.resonator.ResonanceCoreBlock;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorBlueprint;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorBlueprint.Layout;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorFormation;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorLayout;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorLayout.Face;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorLayout.Part;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorPartBlock;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import org.jetbrains.annotations.Nullable;

/**
 * A projeção da estrutura do Ressonador em volta de um Núcleo solto, só no cliente: o cliente já tem os blocos em
 * volta, então compara sozinho (a cada 10 ticks) o desenho do {@link ResonatorBlueprint} com o mundo e escreve o que
 * falta na action bar. Uma projeção por vez; desliga sozinha ao montar, sem o Núcleo, longe ou em outra dimensão.
 * Shift + roda, perto do Núcleo, troca o layout. O desenho fica no {@link ProjectionRenderer}.
 */
@EventBusSubscriber(modid = WirelessAutomate.MODID, value = Dist.CLIENT)
public final class ResonatorProjection {
    private static final int SIZE = ResonatorLayout.SIZE;
    private static final int REFRESH_TICKS = 10;
    /** Ticks em que a dica ou o layout novo ficam na action bar antes da contagem voltar. */
    private static final int HOLD_TICKS = 40;
    private static final double MAX_DISTANCE_SQR = 64 * 64;
    private static final double SCROLL_DISTANCE_SQR = 8 * 8;

    /**
     * A projeção ligada: o desenho na origem dada, o estado montado de cada peça esperada ({@code ghosts}, nulo
     * onde é ar ou o Núcleo) e a última comparação.
     */
    record Active(BlockPos core, ResourceKey<Level> dimension, Face coreFace, Layout layout, Part[][][] expected,
                  int[] origin, BlockState[][][] ghosts, @Nullable ProjectionDiff.Result result) {
        Active withResult(ProjectionDiff.Result next) {
            return new Active(core, dimension, coreFace, layout, expected, origin, ghosts, next);
        }
    }

    private static @Nullable Active active;
    /** O layout escolhido por último, que a próxima projeção usa. */
    private static Layout lastLayout = Layout.VITRINE;
    private static int ticks;
    private static int hold;

    private ResonatorProjection() {
    }

    /** Liga a projeção para este Núcleo, ou desliga se ela já era dele. */
    public static void toggle(BlockPos core) {
        Minecraft minecraft = Minecraft.getInstance();
        if (active != null && active.core().equals(core)) {
            active = null;
            return;
        }
        ClientLevel level = minecraft.level;
        if (level == null || !(level.getBlockState(core).getBlock() instanceof ResonanceCoreBlock)) {
            return;
        }
        active = build(core, level.dimension(), faceOf(level.getBlockState(core)), lastLayout);
        hold = HOLD_TICKS;
        minecraft.gui.setOverlayMessage(Component.translatable(ProjectionText.HINT), false);
        refresh(minecraft);
    }

    public static boolean isOn() {
        return active != null;
    }

    public static Layout layout() {
        return lastLayout;
    }

    /** A última comparação da projeção ligada, ou nulo. */
    public static @Nullable ProjectionDiff.Result result() {
        return active == null ? null : active.result();
    }

    static @Nullable Active active() {
        return active;
    }

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        if (active == null) {
            return;
        }
        if (hold > 0) {
            hold--;
        }
        if (++ticks % REFRESH_TICKS == 0) {
            refresh(Minecraft.getInstance());
        }
    }

    /** Shift + roda com a projeção ligada e o jogador perto do Núcleo: troca Vitrine e Faixa. */
    @SubscribeEvent
    static void onScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (active == null || player == null || minecraft.screen != null || event.getScrollDeltaY() == 0
                || !minecraft.options.keyShift.isDown()
                || player.distanceToSqr(Vec3.atCenterOf(active.core())) > SCROLL_DISTANCE_SQR) {
            return;
        }
        lastLayout = active.layout().other();
        active = build(active.core(), active.dimension(), active.coreFace(), lastLayout);
        hold = HOLD_TICKS;
        minecraft.gui.setOverlayMessage(ProjectionText.layout(lastLayout), false);
        refresh(minecraft);
        event.setCanceled(true);
    }

    /** Relê o mundo; desliga a projeção se ela não vale mais. */
    private static void refresh(Minecraft minecraft) {
        Active current = active;
        ClientLevel level = minecraft.level;
        LocalPlayer player = minecraft.player;
        if (current == null) {
            return;
        }
        if (level == null || player == null || !level.dimension().equals(current.dimension())
                || player.distanceToSqr(Vec3.atCenterOf(current.core())) > MAX_DISTANCE_SQR) {
            active = null;
            return;
        }
        BlockState coreState = level.getBlockState(current.core());
        if (!(coreState.getBlock() instanceof ResonanceCoreBlock)) {
            active = null;
            return;
        }
        if (coreState.getValue(ResonatorPartBlock.FORMED)) {
            active = null;
            minecraft.gui.setOverlayMessage(Component.translatable("block.wirelessautomate.resonance_core.formed")
                    .withStyle(ChatFormatting.GREEN), false);
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0f));
            return;
        }
        Face face = faceOf(coreState);
        if (face != current.coreFace()) {
            current = build(current.core(), current.dimension(), face, current.layout());
        }
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int[] o = current.origin();
        ProjectionDiff.Result result = ProjectionDiff.compare(current.expected(), o[0], o[1], o[2],
                (x, y, z) -> ResonatorFormation.partOf(level.getBlockState(cursor.set(x, y, z))));
        active = current.withResult(result);
        if (hold == 0) {
            minecraft.gui.setOverlayMessage(ProjectionText.status(result), false);
        }
    }

    private static Active build(BlockPos core, ResourceKey<Level> dimension, Face coreFace, Layout layout) {
        Part[][][] expected = ResonatorBlueprint.expected(coreFace, layout);
        int[] origin = ResonatorBlueprint.origin(core.getX(), core.getY(), core.getZ(), coreFace);
        return new Active(core, dimension, coreFace, layout, expected, origin, ghosts(core, expected, origin), null);
    }

    /**
     * O estado montado de cada peça esperada: o {@code ResonatorLayout.check} sobre o próprio desenho dá a posição da
     * carcaça, a face e os bits do vidro, como a montagem de verdade os gravaria.
     */
    private static BlockState[][][] ghosts(BlockPos core, Part[][][] expected, int[] o) {
        BlockState[][][] ghosts = new BlockState[SIZE][SIZE][SIZE];
        ResonatorLayout.Result result = ResonatorLayout.check(core.getX(), core.getY(), core.getZ(), (x, y, z) -> {
            int lx = x - o[0];
            int ly = y - o[1];
            int lz = z - o[2];
            boolean inside = lx >= 0 && lx < SIZE && ly >= 0 && ly < SIZE && lz >= 0 && lz < SIZE;
            return inside ? expected[lx][ly][lz] : Part.OTHER;
        });
        if (!(result instanceof ResonatorLayout.Formed formed)) {
            return ghosts;
        }
        for (ResonatorLayout.Member member : formed.members()) {
            Block block = blockOf(member.part());
            if (block != null) {
                ghosts[member.x() - o[0]][member.y() - o[1]][member.z() - o[2]] =
                        ResonatorFormation.formedState(block.defaultBlockState(), member);
            }
        }
        return ghosts;
    }

    private static @Nullable Block blockOf(Part part) {
        return switch (part) {
            case CASING -> ModBlocks.RESONATOR_CASING.get();
            case GLASS -> ModBlocks.RESONATOR_GLASS.get();
            case ENERGY_PORT -> ModBlocks.RESONATOR_ENERGY_PORT.get();
            case CRYSTAL_PORT -> ModBlocks.RESONATOR_CRYSTAL_PORT.get();
            case TUNER -> ModBlocks.RESONATOR_TUNER.get();
            case CORE, AIR, OTHER -> null;
        };
    }

    /** A face do Núcleo: o {@code FACING} dele, na mesma ordem do {@code Direction}. */
    private static Face faceOf(BlockState core) {
        return Face.values()[core.getValue(ResonatorPartBlock.FACING).get3DDataValue()];
    }
}
