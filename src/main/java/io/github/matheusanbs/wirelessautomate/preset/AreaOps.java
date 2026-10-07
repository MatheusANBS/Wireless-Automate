package io.github.matheusanbs.wirelessautomate.preset;

import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.network.RouterPreset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * As operações de área do Configurador, no servidor: achar os roteadores de uma área, copiar,
 * colar numa origem e aplicar um preset em todos.
 *
 * <p>Custo: a busca não visita bloco a bloco; percorre os block entities dos chunks carregados que
 * tocam a área (chunk descarregado fica de fora, sem carregar nada). Limites: área de até
 * {@value #MAX_VOLUME} blocos e {@value #MAX_SIDE} de lado, a até {@value #MAX_REACH} blocos do
 * jogador, e no máximo {@value #MAX_ROUTERS} roteadores por operação.
 */
public final class AreaOps {
    public static final long MAX_VOLUME = 65_536;
    public static final int MAX_SIDE = 256;
    public static final int MAX_REACH = 64;
    public static final int MAX_ROUTERS = 1024;

    /** Por que uma área não serve; vira a chave {@code ...area.problem.<chave>}. */
    public enum Problem {
        INCOMPLETE("incomplete"),
        OTHER_DIMENSION("other_dimension"),
        TOO_LARGE("too_large"),
        TOO_FAR("too_far");

        public final String key;

        Problem(String key) {
            this.key = key;
        }
    }

    /** Um roteador achado na área e o bloco da máquina onde ele está preso. */
    public record Found(RouterBlockEntity router, ResourceLocation machine) {
    }

    /** O que há de errado com a área para este jogador, ou {@code null} se ela serve. */
    public static @Nullable Problem check(ServerPlayer player, AreaSelection selection) {
        BoundingBox box = selection.box();
        if (box == null) {
            return Problem.INCOMPLETE;
        }
        if (!selection.corner1().get().dimension().equals(player.level().dimension())) {
            return Problem.OTHER_DIMENSION;
        }
        if (tooLarge(box)) {
            return Problem.TOO_LARGE;
        }
        return inReach(player, box) ? null : Problem.TOO_FAR;
    }

    public static boolean tooLarge(BoundingBox box) {
        return AreaSelection.volume(box) > MAX_VOLUME || box.getXSpan() > MAX_SIDE || box.getYSpan() > MAX_SIDE
                || box.getZSpan() > MAX_SIDE;
    }

    /** O ponto da caixa mais perto dos olhos do jogador está a até {@link #MAX_REACH} blocos. */
    public static boolean inReach(ServerPlayer player, BoundingBox box) {
        AABB aabb = AABB.of(box);
        Vec3 eye = player.getEyePosition();
        double dx = Math.max(Math.max(aabb.minX - eye.x, 0), eye.x - aabb.maxX);
        double dy = Math.max(Math.max(aabb.minY - eye.y, 0), eye.y - aabb.maxY);
        double dz = Math.max(Math.max(aabb.minZ - eye.z, 0), eye.z - aabb.maxZ);
        return dx * dx + dy * dy + dz * dz <= (double) MAX_REACH * MAX_REACH;
    }

    /** Roteadores da caixa em chunks carregados, em ordem (y, z, x); no máximo {@link #MAX_ROUTERS}. */
    public static List<Found> scan(ServerLevel level, BoundingBox box) {
        List<Found> found = new ArrayList<>();
        for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) {
            for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity entity : chunk.getBlockEntities().values()) {
                    if (entity instanceof RouterBlockEntity router && !router.isRemoved()
                            && box.isInside(router.getBlockPos())) {
                        found.add(new Found(router, machine(level, router)));
                    }
                }
            }
        }
        found.sort(Comparator.comparingInt((Found f) -> f.router().getBlockPos().getY())
                .thenComparingInt(f -> f.router().getBlockPos().getZ())
                .thenComparingInt(f -> f.router().getBlockPos().getX()));
        return found.size() > MAX_ROUTERS ? List.copyOf(found.subList(0, MAX_ROUTERS)) : found;
    }

    /** Id do bloco da máquina do roteador ({@code minecraft:air} sem máquina). */
    public static ResourceLocation machine(ServerLevel level, RouterBlockEntity router) {
        return BuiltInRegistries.BLOCK.getKey(level.getBlockState(router.machinePos()).getBlock());
    }

    /** Copia os roteadores achados, com posição relativa a {@code origin}, sem as redes que o jogador não pode usar. */
    public static AreaClipboard copy(ServerPlayer player, List<Found> found, BlockPos origin) {
        List<BlockPos> offsets = new ArrayList<>();
        List<RouterPreset> presets = new ArrayList<>();
        for (Found f : found) {
            if (offsets.size() >= AreaClipboard.MAX_ROUTERS) {
                break;
            }
            offsets.add(f.router().getBlockPos().subtract(origin));
            presets.add(PresetApplier.usable(player, RouterPreset.copyOf(f.router())));
        }
        return AreaClipboard.of(offsets, presets);
    }

    /** A caixa que a cópia ocupa colada em {@code anchor}. */
    public static @Nullable BoundingBox pasteBox(AreaClipboard clipboard, BlockPos anchor) {
        BoundingBox box = null;
        for (AreaClipboard.Entry entry : clipboard.entries()) {
            BlockPos pos = anchor.offset(entry.offset());
            box = box == null ? new BoundingBox(pos) : box.encapsulate(pos);
        }
        return box;
    }

    /** Quantas posições da cópia colada em {@code anchor} têm roteador (carregado) agora. */
    public static int hits(ServerLevel level, AreaClipboard clipboard, BlockPos anchor) {
        int hits = 0;
        for (AreaClipboard.Entry entry : clipboard.entries()) {
            BlockPos pos = anchor.offset(entry.offset());
            if (level.isLoaded(pos) && level.getBlockEntity(pos) instanceof RouterBlockEntity) {
                hits++;
            }
        }
        return hits;
    }

    /** Resultado de colar ou aplicar: roteadores configurados, posições sem roteador e abas que ficaram sem a rede. */
    public record Outcome(int applied, int missing, int protectedCount, boolean droppedNetworks) {
    }

    /**
     * Cola a cópia em {@code anchor}: cada preset vai para o roteador que já existe na posição
     * correspondente (nenhum bloco é colocado). Valida dimensão e alcance antes; devolve
     * {@code null} se a cópia colada ficaria longe demais.
     */
    public static @Nullable Outcome paste(ServerPlayer player, AreaClipboard clipboard, GlobalPos anchor) {
        ServerLevel level = player.serverLevel();
        BoundingBox box = pasteBox(clipboard, anchor.pos());
        if (box == null || !anchor.dimension().equals(level.dimension()) || !inReach(player, box)) {
            return null;
        }
        List<RouterPreset> usable = new ArrayList<>(clipboard.presets().size());
        boolean dropped = false;
        for (RouterPreset preset : clipboard.presets()) {
            PresetApplier.Checked checked = PresetApplier.check(player, preset);
            usable.add(checked.preset());
            dropped |= checked.droppedAny();
        }
        int applied = 0;
        int missing = 0;
        int protectedCount = 0;
        for (AreaClipboard.Entry entry : clipboard.entries()) {
            BlockPos pos = anchor.pos().offset(entry.offset());
            if (!level.isLoaded(pos) || !(level.getBlockEntity(pos) instanceof RouterBlockEntity router)) {
                missing++;
            } else if (!level.mayInteract(player, pos)) {
                protectedCount++;
            } else {
                usable.get(entry.preset()).applyTo(router);
                applied++;
            }
        }
        return new Outcome(applied, missing, protectedCount, dropped);
    }

    /**
     * Aplica um preset em todos os roteadores achados; com {@code machine}, só nos presos a esse
     * bloco. Quem chama já validou a área com {@link #check}.
     */
    public static Outcome apply(ServerPlayer player, List<Found> found, RouterPreset preset,
            @Nullable ResourceLocation machine) {
        PresetApplier.Checked checked = PresetApplier.check(player, preset);
        ServerLevel level = player.serverLevel();
        int applied = 0;
        int protectedCount = 0;
        for (Found f : found) {
            if (machine != null && !machine.equals(f.machine())) {
                continue;
            }
            if (!level.mayInteract(player, f.router().getBlockPos())) {
                protectedCount++;
                continue;
            }
            checked.preset().applyTo(f.router());
            applied++;
        }
        return new Outcome(applied, 0, protectedCount, checked.droppedAny());
    }

    private AreaOps() {
    }
}
