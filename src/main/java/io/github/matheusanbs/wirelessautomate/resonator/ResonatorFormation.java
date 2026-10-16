package io.github.matheusanbs.wirelessautomate.resonator;

import io.github.matheusanbs.wirelessautomate.resonator.ResonatorLayout.Part;
import java.util.ArrayList;
import java.util.Arrays;
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
import org.jetbrains.annotations.Nullable;

/**
 * Montar e desmontar o Ressonador: lê a caixa em volta do Núcleo, passa pelo {@link ResonatorLayout} e grava
 * nos blocos os estados conectados (ou os devolve a soltos), com o índice dos membros e o das Portas. Só roda por
 * evento de bloco (o tick agendado do Núcleo), nunca por tick.
 */
public final class ResonatorFormation {
    /** Jogadores a até 16 blocos do Núcleo veem o aviso de montado. */
    private static final double NOTICE_RADIUS_SQR = 16 * 16;
    private static final int SPAN = 2 * ResonatorLayout.SIZE - 1;

    private ResonatorFormation() {
    }

    /** Confere a estrutura e monta ou desmonta; o tempo entra no {@link ResonatorProfile}. */
    public static void revalidate(ServerLevel level, ResonanceCoreBlockEntity core) {
        long started = System.nanoTime();
        try {
            apply(level, core);
        } finally {
            ResonatorProfile.revalidated(System.nanoTime() - started);
        }
    }

    private static void apply(ServerLevel level, ResonanceCoreBlockEntity core) {
        BlockPos at = core.getBlockPos();
        if (core.isRemoved() || level.getBlockEntity(at) != core) {
            return;
        }
        ResonatorLayout.Result result = evaluate(level, at);
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

    /**
     * Confere a estrutura em volta do Núcleo em {@code core}, sem gravar nada; a caixa da frente do Núcleo (a da
     * montagem anterior e a da projeção) é a primeira tentada.
     */
    static ResonatorLayout.Result check(ServerLevel level, BlockPos core) {
        BlockState state = level.getBlockState(core);
        if (!(state.getBlock() instanceof ResonanceCoreBlock)) {
            return ResonatorLayout.check(core.getX(), core.getY(), core.getZ(), probe(level, core));
        }
        // As faces do ResonatorLayout seguem a ordem do Direction.
        ResonatorLayout.Face front =
                ResonatorLayout.Face.values()[state.getValue(ResonatorPartBlock.FACING).get3DDataValue()];
        return ResonatorLayout.check(core.getX(), core.getY(), core.getZ(), front, probe(level, core));
    }

    /**
     * Confere a estrutura e, se ela fecha, se algum membro já é de outro Ressonador montado: a parede dividida não
     * monta ({@link ResonatorLayout.Problem#SHARED_WALL}, com a posição do Núcleo dono). Sem gravar nada.
     */
    static ResonatorLayout.Result evaluate(ServerLevel level, BlockPos core) {
        ResonatorLayout.Result result = check(level, core);
        if (result instanceof ResonatorLayout.Formed formed) {
            BlockPos owner = sharedWallOwner(level, core, formed);
            if (owner != null) {
                return new ResonatorLayout.Broken(ResonatorLayout.Problem.SHARED_WALL, owner.getX(), owner.getY(),
                        owner.getZ());
            }
        }
        return result;
    }

    /**
     * O Núcleo de outro Ressonador montado dono de algum membro, ou {@code null}. Um dono carregado só vale com o
     * Núcleo ainda lá e montado (pelo estado do bloco, sem criar block entity); um descarregado continua dono.
     */
    private static @Nullable BlockPos sharedWallOwner(ServerLevel level, BlockPos core, ResonatorLayout.Formed formed) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (ResonatorLayout.Member member : formed.members()) {
            BlockPos owner = ResonatorMembers.owner(level.dimension(), cursor.set(member.x(), member.y(), member.z()));
            if (owner == null || owner.equals(core)) {
                continue;
            }
            if (!level.isLoaded(owner)) {
                return owner;
            }
            BlockState state = level.getBlockState(owner);
            if (state.getBlock() instanceof ResonanceCoreBlock && state.getValue(ResonatorPartBlock.FORMED)) {
                return owner;
            }
        }
        return null;
    }

    /**
     * Desmonta: cada membro ainda presente e deste Núcleo volta a solto, as Portas saem do índice e os Núcleos
     * que esperavam por uma parede dele revalidam.
     */
    public static void disassemble(ServerLevel level, ResonanceCoreBlockEntity core) {
        BlockPos at = core.getBlockPos();
        boolean released = false;
        for (BlockPos pos : core.members()) {
            if (ResonatorMembers.ownedOrFree(level.dimension(), pos, at)) {
                loosen(level, pos);
            }
            released |= ResonatorMembers.release(level.dimension(), pos, at);
        }
        updatePorts(level, core, List.of());
        core.setFormed(false, List.of());
        if (released) {
            ResonatorManager.wakeBlockedBy(level, at);
        }
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

    /** A peça que o bloco é na estrutura; a projeção do cliente lê o mundo por aqui também. */
    public static Part partOf(BlockState state) {
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
        BlockPos at = core.getBlockPos();
        List<BlockPos> members = new ArrayList<>(formed.members().size());
        List<BlockPos> ports = new ArrayList<>();
        for (ResonatorLayout.Member member : formed.members()) {
            BlockPos pos = new BlockPos(member.x(), member.y(), member.z());
            members.add(pos);
            ResonatorMembers.claim(level.dimension(), pos, at);
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
        boolean released = false;
        for (BlockPos old : core.members()) {
            if (!current.contains(old) && ResonatorMembers.ownedOrFree(level.dimension(), old, at)) {
                loosen(level, old);
                released |= ResonatorMembers.release(level.dimension(), old, at);
            }
        }
        updatePorts(level, core, ports);
        structure(level, core, formed);
        core.setFormed(true, members);
        core.syncIfChanged();
        if (released) {
            ResonatorManager.wakeBlockedBy(level, at);
        }
        if (!wasFormed) {
            announce(level, core);
        }
    }

    /**
     * Passa ao Núcleo o que a casca tem: o Sintonizador, as Portas e a montagem reconhecida. Vem antes do
     * {@code setFormed}, para o Núcleo saber se a estrutura estava desmontada; a câmara vai ao cliente depois dos
     * dois, numa update tag só (o {@code setFormed} manda se a montagem mudou, e o {@code syncIfChanged} seguinte
     * cobre a estrutura que mudou com os mesmos membros).
     */
    private static void structure(ServerLevel level, ResonanceCoreBlockEntity core, ResonatorLayout.Formed formed) {
        int size = ResonatorLayout.SIZE;
        Part[][][] local = new Part[size][size][size];
        for (Part[][] plane : local) {
            for (Part[] row : plane) {
                Arrays.fill(row, Part.AIR);
            }
        }
        boolean tuner = false;
        int energyPorts = 0;
        int crystalPorts = 0;
        for (ResonatorLayout.Member member : formed.members()) {
            local[member.x() - formed.minX()][member.y() - formed.minY()][member.z() - formed.minZ()] = member.part();
            switch (member.part()) {
                case TUNER -> tuner = true;
                case ENERGY_PORT -> energyPorts++;
                case CRYSTAL_PORT -> crystalPorts++;
                default -> {
                }
            }
        }
        core.setStructure(tuner, energyPorts, crystalPorts, ResonatorBlueprint.layoutOf(local), level.getGameTime());
    }

    /** O estado montado do bloco para o membro; a projeção desenha os fantasmas já assim. */
    public static BlockState formedState(BlockState state, ResonatorLayout.Member member) {
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
