package io.github.matheusanbs.wirelessautomate.resonator;

import io.github.matheusanbs.wirelessautomate.resonator.ResonatorLayout.Part;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Montar e desmontar o Ressonador: lê a caixa em volta do Núcleo, passa pelo {@link ResonatorLayout} e grava
 * nos blocos os estados conectados (ou os devolve a soltos), com o índice das Portas. Só roda por evento de
 * bloco (o tick agendado do Núcleo), nunca por tick.
 */
public final class ResonatorFormation {
    /** Jogadores a até 16 blocos do Núcleo veem o aviso de montado. */
    private static final double NOTICE_RADIUS_SQR = 16 * 16;
    private static final int SPAN = 2 * ResonatorLayout.SIZE - 1;

    private ResonatorFormation() {
    }

    public static void revalidate(ServerLevel level, ResonanceCoreBlockEntity core) {
        BlockPos at = core.getBlockPos();
        if (core.isRemoved() || level.getBlockEntity(at) != core) {
            return;
        }
        ResonatorLayout.Result result = ResonatorLayout.check(at.getX(), at.getY(), at.getZ(), probe(level, at));
        if (result instanceof ResonatorLayout.Formed formed) {
            core.setLastProblem(null);
            form(level, core, formed);
        } else if (result instanceof ResonatorLayout.Broken broken) {
            core.setLastProblem(broken);
            if (core.formed()) {
                disassemble(level, core);
            }
        }
    }

    /** Desmonta: cada membro ainda presente volta a solto e as Portas saem do índice. */
    public static void disassemble(ServerLevel level, ResonanceCoreBlockEntity core) {
        for (BlockPos pos : core.members()) {
            loosen(level, pos);
        }
        updatePorts(level, core, List.of());
        core.setFormed(false, List.of());
    }

    /** As leituras ficam guardadas: as caixas candidatas se cruzam, e cada posição é lida uma vez só. */
    private static ResonatorLayout.Probe probe(ServerLevel level, BlockPos core) {
        Part[] seen = new Part[SPAN * SPAN * SPAN];
        int baseX = core.getX() - ResonatorLayout.SIZE + 1;
        int baseY = core.getY() - ResonatorLayout.SIZE + 1;
        int baseZ = core.getZ() - ResonatorLayout.SIZE + 1;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        return (x, y, z) -> {
            int index = ((x - baseX) * SPAN + (y - baseY)) * SPAN + (z - baseZ);
            Part part = seen[index];
            if (part == null) {
                cursor.set(x, y, z);
                part = level.isLoaded(cursor) ? partOf(level.getBlockState(cursor)) : Part.OTHER;
                seen[index] = part;
            }
            return part;
        };
    }

    static Part partOf(BlockState state) {
        Block block = state.getBlock();
        if (block instanceof ResonatorCasingBlock) {
            return Part.CASING;
        }
        if (block instanceof ResonatorGlassBlock) {
            return Part.GLASS;
        }
        if (block instanceof ResonanceCoreBlock) {
            return Part.CORE;
        }
        if (block instanceof ResonatorPartBlock part) {
            return switch (part.kind()) {
                case ENERGY_PORT -> Part.ENERGY_PORT;
                case CRYSTAL_PORT -> Part.CRYSTAL_PORT;
                case TUNER -> Part.TUNER;
            };
        }
        return state.isAir() ? Part.AIR : Part.OTHER;
    }

    private static void form(ServerLevel level, ResonanceCoreBlockEntity core, ResonatorLayout.Formed formed) {
        boolean wasFormed = core.formed();
        List<BlockPos> members = new ArrayList<>(formed.members().size());
        List<BlockPos> ports = new ArrayList<>();
        for (ResonatorLayout.Member member : formed.members()) {
            BlockPos pos = new BlockPos(member.x(), member.y(), member.z());
            members.add(pos);
            if (member.part() == Part.ENERGY_PORT || member.part() == Part.CRYSTAL_PORT) {
                ports.add(pos);
            }
            BlockState state = level.getBlockState(pos);
            BlockState next = formedState(state, member);
            if (next != state) {
                level.setBlock(pos, next, Block.UPDATE_CLIENTS);
            }
        }
        Set<BlockPos> current = new HashSet<>(members);
        for (BlockPos old : core.members()) {
            if (!current.contains(old)) {
                loosen(level, old);
            }
        }
        updatePorts(level, core, ports);
        core.setFormed(true, members);
        if (!wasFormed) {
            announce(level, core);
        }
    }

    private static BlockState formedState(BlockState state, ResonatorLayout.Member member) {
        Block block = state.getBlock();
        if (block instanceof ResonatorCasingBlock) {
            return state.setValue(ResonatorCasingBlock.POSITION, CasingPosition.byName(member.casing()));
        }
        if (block instanceof ResonatorGlassBlock) {
            return state.setValue(ResonatorGlassBlock.FACE, GlassFace.of(member.face()))
                    .setValue(ResonatorGlassBlock.CONNECTIONS, member.glassBits());
        }
        if (block instanceof ResonatorPartBlock || block instanceof ResonanceCoreBlock) {
            return state.setValue(ResonatorPartBlock.FACING, outward(member.face()))
                    .setValue(ResonatorPartBlock.FORMED, true);
        }
        return state;
    }

    private static Direction outward(ResonatorLayout.Face face) {
        return switch (face) {
            case DOWN -> Direction.DOWN;
            case UP -> Direction.UP;
            case NORTH -> Direction.NORTH;
            case SOUTH -> Direction.SOUTH;
            case WEST -> Direction.WEST;
            case EAST -> Direction.EAST;
        };
    }

    /** Volta o bloco em {@code pos} ao estado solto, se ainda for do Ressonador e estiver carregado. */
    private static void loosen(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return;
        }
        BlockState state = level.getBlockState(pos);
        BlockState next = state;
        Block block = state.getBlock();
        if (block instanceof ResonatorCasingBlock) {
            next = state.setValue(ResonatorCasingBlock.POSITION, CasingPosition.LOOSE);
        } else if (block instanceof ResonatorGlassBlock) {
            next = state.setValue(ResonatorGlassBlock.FACE, GlassFace.LOOSE).setValue(ResonatorGlassBlock.CONNECTIONS, 0);
        } else if (block instanceof ResonatorPartBlock || block instanceof ResonanceCoreBlock) {
            next = state.setValue(ResonatorPartBlock.FORMED, false);
        }
        if (next != state) {
            level.setBlock(pos, next, Block.UPDATE_CLIENTS);
        }
    }

    /** Põe no índice as Portas novas e tira as que saíram; a capability de cada uma que mudou é invalidada. */
    private static void updatePorts(ServerLevel level, ResonanceCoreBlockEntity core, List<BlockPos> ports) {
        Set<BlockPos> next = new HashSet<>(ports);
        for (BlockPos old : core.ports()) {
            if (!next.contains(old)) {
                ResonatorPorts.unregister(level.dimension(), old, core.getBlockPos());
                level.invalidateCapabilities(old);
            }
        }
        for (BlockPos port : ports) {
            if (ResonatorPorts.register(level.dimension(), port, core.getBlockPos())) {
                level.invalidateCapabilities(port);
            }
        }
        core.setPorts(ports);
    }

    /** Primeira montagem desta vez: estação (se ainda não tem), som e aviso aos jogadores perto. */
    private static void announce(ServerLevel level, ResonanceCoreBlockEntity core) {
        if (!core.hasStation()) {
            core.initStation(Tuning.initialStation(level.random.nextDouble()));
        }
        BlockPos at = core.getBlockPos();
        level.playSound(null, at, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.0f, 1.0f);
        Component notice = Component.translatable("block.wirelessautomate.resonance_core.formed");
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(at.getCenter()) <= NOTICE_RADIUS_SQR) {
                player.displayClientMessage(notice, true);
            }
        }
    }
}
