package io.github.matheusanbs.wirelessautomate.dock;

import io.github.matheusanbs.wirelessautomate.Config;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.item.LinkerItem;
import io.github.matheusanbs.wirelessautomate.item.tuner.ConfiguratorTuner;
import io.github.matheusanbs.wirelessautomate.item.tuner.TunerProbe;
import io.github.matheusanbs.wirelessautomate.linker.LinkerActions;
import io.github.matheusanbs.wirelessautomate.network.LoadedTypes;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.range.MachineConfig;
import io.github.matheusanbs.wirelessautomate.range.RangeLinks;
import io.github.matheusanbs.wirelessautomate.range.RangeRules;
import io.github.matheusanbs.wirelessautomate.range.RemoteIndex;
import io.github.matheusanbs.wirelessautomate.range.RemoteListener;
import io.github.matheusanbs.wirelessautomate.range.RemoteMachines;
import io.github.matheusanbs.wirelessautomate.range.RemoteSide;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * O Vinculador na Doca de Padrões (servidor): escolher a Doca e o lado ({@code linker_dock}, clique numa face dela) e
 * vincular ou desvincular uma máquina daquele lado com um clique. O Ctrl + clique fica no {@link DockSameBlock}. Tudo
 * validado aqui, a cada pedido; o item só pede.
 *
 * <p>Ordem das recusas de uma máquina: a Doca escolhida presente e na dimensão do jogador; {@code mayBuild} e
 * {@code mayInteract} na máquina e na Doca ({@link Refusal#PROTECTED}); a máquina já é deste lado (desvincula) ou de
 * outro lado desta Doca; não é máquina (um roteador, uma Doca, um bloco sem capability de um tipo carregado); o lado é
 * de outro bloco; a máquina é de outra Doca carregada (o {@link RemoteIndex}); fora do cubo do tier; o limite do tier.
 * Uma máquina vinculada a um roteador pode entrar num lado, e vice-versa (decisão do dono, 10/11/2026).
 *
 * <p>O primeiro vínculo de um lado preenche o perfil dele pelo Afinador ({@link #tune}); as outras máquinas usam o
 * mesmo perfil pela frente de cada uma.
 */
public final class DockLinks {
    private static final String KEY = "item.wirelessautomate.linker.dock.";
    private static final String RANGE_KEY = "item.wirelessautomate.linker.";
    private static final Direction[] DIRECTIONS = Direction.values();

    private DockLinks() {
    }

    /** O que um clique fez. */
    public enum Status {
        /** Escolheu a Doca e o lado (clique numa face da Doca). */
        CHOSEN,
        /** Soltou a Doca (clique de novo no lado escolhido). */
        RELEASED,
        LINKED,
        UNLINKED,
        /** Ctrl + clique aplicado ({@link DockSameBlock}), mesmo com zero vinculadas. */
        SAME_BLOCK_DONE,
        /** Ctrl + clique numa máquina do lado aplicado ({@link DockSameBlock}). */
        SAME_BLOCK_UNLINKED,
        /** A Doca escolhida está descarregada, quebrada ou em outra dimensão. */
        DOCK_MISSING,
        /** Recusado pela regra {@link Result#refusal()}. */
        REFUSED
    }

    /** Por que uma máquina não entra no lado. */
    public enum Refusal {
        NONE,
        PROTECTED,
        /** Nada aqui que o mod transporte. */
        NOT_A_MACHINE,
        /** Um roteador não vira máquina. */
        ROUTER,
        /** Uma Doca não vira máquina. */
        DOCK,
        /** O lado já é de outro bloco. */
        OTHER_BLOCK,
        /** A máquina já é de outro lado desta Doca. */
        OTHER_SIDE,
        /** A máquina já é de um lado de outra Doca carregada. */
        OTHER_DOCK,
        OUT_OF_RANGE,
        LIMIT
    }

    /**
     * Resultado de um pedido. Os números além de {@code linked} só valem no Ctrl + clique ({@link DockSameBlock}).
     *
     * @param linked as vinculadas (ou desvinculadas, no {@code SAME_BLOCK_UNLINKED})
     */
    public record Result(Status status, Refusal refusal, int linked, int already, int overLimit, int protectedCount,
            int otherDock, int unloadedChunks) {
        static Result of(Status status) {
            return new Result(status, Refusal.NONE, 0, 0, 0, 0, 0, 0);
        }

        static Result refused(Refusal refusal) {
            return new Result(Status.REFUSED, refusal, 0, 0, 0, 0, 0, 0);
        }
    }

    // ------------------------------------------------------------------ Doca escolhida

    /**
     * A Doca escolhida no Vinculador, se está carregada, inteira e na dimensão do jogador; senão {@code null}. Nunca
     * carrega o chunk.
     */
    public static @Nullable PatternDockBlockEntity chosen(ServerPlayer player, ItemStack stack) {
        DockChoice choice = LinkerItem.dock(stack);
        if (choice == null || !choice.pos().dimension().equals(player.level().dimension())) {
            return null;
        }
        ServerLevel level = player.serverLevel();
        BlockPos pos = choice.pos().pos();
        if (!level.isLoaded(pos)) {
            return null;
        }
        return level.getBlockEntity(pos) instanceof PatternDockBlockEntity dock && !dock.isRemoved() ? dock : null;
    }

    /**
     * Clique numa face da Doca: ela e o lado clicado passam a ser os escolhidos (o roteador e a Tela escolhidos saem).
     * Clicar de novo no lado escolhido solta a Doca. A action bar diz
     * {@code Doca de Padrões · lado Norte (Fornalha) · 30/32}.
     */
    public static Result choose(ServerPlayer player, ItemStack stack, PatternDockBlockEntity dock, int side) {
        DockChoice current = LinkerItem.dock(stack);
        GlobalPos here = GlobalPos.of(player.level().dimension(), dock.getBlockPos());
        if (current != null && current.side() == side && current.pos().equals(here)) {
            LinkerItem.setDock(stack, null);
            player.displayClientMessage(Component.translatable(KEY + "released", dockName(dock), sideName(side)),
                    true);
            return Result.of(Status.RELEASED);
        }
        if (!RangeLinks.mayEdit(player, dock.getBlockPos())) {
            return refuse(player, Result.refused(Refusal.PROTECTED), dock, side, null, 0);
        }
        LinkerItem.setDock(stack, new DockChoice(here, side));
        LinkerItem.setRouter(stack, null);
        LinkerItem.setTv(stack, null);
        player.displayClientMessage(chosenText(dock, side), true);
        return Result.of(Status.CHOSEN);
    }

    /** {@code Doca de Padrões · lado Norte (Fornalha) · 30/32}, ou {@code (livre)} num lado sem bloco. */
    public static Component chosenText(PatternDockBlockEntity dock, int side) {
        DockSide dockSide = dock.side(side);
        ResourceLocation block = dockSide == null ? null : dockSide.block();
        Component machine = block == null ? Component.translatable(KEY + "free")
                : RouterBlock.machineName(BuiltInRegistries.BLOCK.get(block));
        return Component.translatable(KEY + "chosen", dockName(dock), sideName(side), machine, dock.activeCount(),
                max(dock));
    }

    /** O clique no ar sem Shift com a Doca escolhida: o mesmo texto da escolha, ou que ela não está aqui. */
    public static void showChosen(ServerPlayer player, ItemStack stack) {
        DockChoice choice = LinkerItem.dock(stack);
        PatternDockBlockEntity dock = chosen(player, stack);
        if (choice == null || dock == null) {
            missing(player, stack);
            return;
        }
        player.displayClientMessage(chosenText(dock, choice.side()), true);
    }

    // ------------------------------------------------------------------ uma máquina

    /**
     * Clique numa máquina com a Doca escolhida: vincula ao lado escolhido ou, se já é desse lado, desvincula. O
     * primeiro vínculo do lado preenche o perfil pelo Afinador. Mostra o resultado na action bar.
     */
    public static Result toggle(ServerPlayer player, ItemStack stack, BlockPos pos) {
        DockChoice choice = LinkerItem.dock(stack);
        PatternDockBlockEntity dock = chosen(player, stack);
        if (choice == null || dock == null) {
            return missing(player, stack);
        }
        int side = choice.side();
        ServerLevel level = player.serverLevel();
        if (!RangeLinks.mayEdit(player, pos) || !RangeLinks.mayEdit(player, dock.getBlockPos())) {
            return refuse(player, Result.refused(Refusal.PROTECTED), dock, side, pos, 0);
        }
        DockMachine owned = dock.machineAt(pos);
        if (owned != null && owned.side() == side) {
            Component machine = RouterBlock.machineName(level.getBlockState(pos).getBlock());
            dock.unlink(side, pos);
            player.displayClientMessage(Component.translatable(KEY + "unlinked", machine, sideName(side),
                    dock.activeCount(), max(dock)), true);
            level.playSound(null, pos, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.BLOCKS, 0.5F, 0.8F);
            return Result.of(Status.UNLINKED);
        }
        Refusal refusal = machineRefusal(level, dock, side, pos);
        int distance = distance(dock, pos);
        if (refusal == Refusal.NONE) {
            refusal = reachRefusal(dock, distance, dock.activeCount());
        }
        if (refusal != Refusal.NONE) {
            return refuse(player, Result.refused(refusal), dock, side, pos, distance);
        }
        BlockState state = level.getBlockState(pos);
        boolean first = isEmpty(dock.side(side));
        if (!dock.link(side, pos, BuiltInRegistries.BLOCK.getKey(state.getBlock()), RemoteMachines.frontOf(state))) {
            return refuse(player, Result.refused(Refusal.NOT_A_MACHINE), dock, side, pos, distance);
        }
        Component machine = RouterBlock.machineName(state.getBlock());
        player.displayClientMessage(first ? tune(player, dock, side, pos)
                : Component.translatable(KEY + "linked", machine, sideName(side), dock.activeCount(), max(dock)), true);
        level.playSound(null, pos, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.BLOCKS, 0.5F, 1.4F);
        return Result.of(Status.LINKED);
    }

    // ------------------------------------------------------------------ o perfil pelo Afinador

    /**
     * O perfil do lado {@code side} pela análise do Afinador na máquina em {@code pos} (a primeira do lado): o perfil
     * volta ao padrão e recebe os modos propostos, cada face absoluta convertida no lado relativo à frente da máquina.
     * Devolve o texto da action bar ({@code Lado Norte: Fornalha · Cima Insere · Baixo Extrai}). Uma exceção do outro
     * mod na análise vai ao log, e o lado fica sem perfil, com a mensagem {@code failed} do Afinador.
     */
    static Component tune(ServerPlayer player, PatternDockBlockEntity dock, int side, BlockPos pos) {
        DockSide dockSide = dock.side(side);
        DockMachine machine = dock.machineAt(pos);
        ServerLevel level = player.serverLevel();
        Block block = level.getBlockState(pos).getBlock();
        Component name = RouterBlock.machineName(block);
        if (dockSide == null || machine == null) {
            return Component.translatable(KEY + "profile", sideName(side), name,
                    Component.translatable(KEY + "profile.none"));
        }
        MachineConfig profile = dockSide.profile();
        profile.copyFrom(new MachineConfig());
        int front = machine.front();
        Map<ResourceType, PortMode[]> modes;
        try {
            modes = TunerProbe.analyze(level, pos, front, player);
        } catch (RuntimeException e) {
            // A análise chama as capabilities de outro mod com provas que ele não espera: uma exceção dele derruba
            // só esta análise, não o servidor; o lado fica sem perfil.
            WirelessAutomate.LOGGER.warn("Doca: a análise do Afinador de {} em {} falhou",
                    BuiltInRegistries.BLOCK.getKey(block), pos.toShortString(), e);
            dock.saveChanged();
            return Component.translatable(KEY + "profile", sideName(side), name,
                    Component.translatable("item.wirelessautomate.configurator.tuner.failed"));
        }
        applyModes(profile, modes, front);
        dock.saveChanged();
        Component faces = modes.isEmpty() ? Component.translatable(KEY + "profile.none")
                : ConfiguratorTuner.describe(modes, front);
        return Component.translatable(KEY + "profile", sideName(side), name, faces);
    }

    /**
     * Os modos do Afinador ({@code null}: a face fica como está) no perfil {@code profile}, cada face absoluta
     * convertida no lado relativo à frente {@code front}. Devolve as faces absolutas que mudaram, um bit por face.
     */
    static int applyModes(MachineConfig profile, Map<ResourceType, PortMode[]> modes, int front) {
        int changed = 0;
        for (Map.Entry<ResourceType, PortMode[]> entry : modes.entrySet()) {
            PortMode[] faces = entry.getValue();
            for (int face = 0; face < faces.length; face++) {
                if (faces[face] != null
                        && profile.face(entry.getKey(), RemoteSide.fromAbsolute(front, face)).setMode(faces[face])) {
                    changed |= 1 << face;
                }
            }
        }
        return changed;
    }

    // ------------------------------------------------------------------ regras

    /**
     * As recusas que não dependem do alcance nem do limite: outro lado desta Doca, não é máquina (um roteador, uma
     * Doca, um bloco sem capability de um tipo carregado), o lado é de outro bloco, a máquina é de outra Doca
     * carregada. {@code NONE} se pode entrar no lado {@code side}.
     */
    static Refusal machineRefusal(Level level, PatternDockBlockEntity dock, int side, BlockPos pos) {
        DockMachine owned = dock.machineAt(pos);
        if (owned != null && owned.side() != side) {
            return Refusal.OTHER_SIDE;
        }
        if (!level.isLoaded(pos)) {
            // consultar um chunk descarregado o carregaria à força
            return Refusal.NOT_A_MACHINE;
        }
        BlockEntity entity = level.getBlockEntity(pos);
        if (entity instanceof RouterBlockEntity) {
            return Refusal.ROUTER;
        }
        if (entity instanceof PatternDockBlockEntity) {
            return Refusal.DOCK;
        }
        if (!RangeLinks.isMachine(level, pos, LoadedTypes.LIST)) {
            return Refusal.NOT_A_MACHINE;
        }
        if (!sameBlock(dock.side(side), level.getBlockState(pos).getBlock())) {
            return Refusal.OTHER_BLOCK;
        }
        return otherDock(level, dock, pos) != null ? Refusal.OTHER_DOCK : Refusal.NONE;
    }

    /** O lado aceita {@code block}: sem bloco ainda, ou com o mesmo. */
    static boolean sameBlock(@Nullable DockSide side, Block block) {
        ResourceLocation id = side == null ? null : side.block();
        return id == null || id.equals(BuiltInRegistries.BLOCK.getKey(block));
    }

    /**
     * Outra Doca carregada que tem a máquina em {@code pos} no índice das remotas, ou {@code null}. Uma Doca num chunk
     * descarregado não é vista (limite aceito pelo dono: sem índice salvo).
     */
    static @Nullable PatternDockBlockEntity otherDock(Level level, PatternDockBlockEntity dock, BlockPos pos) {
        for (RemoteListener listener : RemoteIndex.get().listenersAt(level, pos)) {
            if (listener instanceof PatternDockBlockEntity other && other != dock && other.machineAt(pos) != null) {
                return other;
            }
        }
        return null;
    }

    /** Fora do cubo do tier ou sem vaga ({@code used} ativas de {@link #max}). */
    static Refusal reachRefusal(PatternDockBlockEntity dock, int distance, int used) {
        if (distance > radius(dock)) {
            return Refusal.OUT_OF_RANGE;
        }
        return used >= max(dock) ? Refusal.LIMIT : Refusal.NONE;
    }

    /** O lado não existe ou não tem máquina: o próximo vínculo é o primeiro, que faz o perfil. */
    static boolean isEmpty(@Nullable DockSide side) {
        return side == null || side.machines().isEmpty();
    }

    public static int distance(PatternDockBlockEntity dock, BlockPos pos) {
        BlockPos origin = dock.getBlockPos();
        return RangeRules.chebyshev(origin.getX(), origin.getY(), origin.getZ(), pos.getX(), pos.getY(), pos.getZ());
    }

    public static int radius(PatternDockBlockEntity dock) {
        return Config.dockRadius(dock.tier());
    }

    public static int max(PatternDockBlockEntity dock) {
        return Config.dockMaxMachines(dock.tier());
    }

    // ------------------------------------------------------------------ mensagens

    /** O nome do bloco da Doca. */
    public static Component dockName(PatternDockBlockEntity dock) {
        return dock.getBlockState().getBlock().getName();
    }

    /** O nome do lado ({@code Direction.ordinal()}): Norte, Sul, Leste, Oeste, Cima, Baixo. */
    public static Component sideName(int side) {
        return Component.translatable("gui.wirelessautomate.pattern_dock.side." + DIRECTIONS[side].getName());
    }

    /**
     * O texto de uma recusa: o bloco do lado, o lado e a Doca de quem tem a máquina, a distância e o raio, o tier e o
     * limite. As recusas comuns ao roteador usam o texto dele.
     */
    public static Component refusalText(Refusal refusal, PatternDockBlockEntity dock, int side,
            @Nullable BlockPos pos, int distance) {
        Level level = dock.getLevel();
        return switch (refusal) {
            case NONE -> Component.translatable(RANGE_KEY + "range.refusal.none");
            case PROTECTED -> Component.translatable(RANGE_KEY + "range.refusal.protected");
            case NOT_A_MACHINE -> Component.translatable(RANGE_KEY + "range.refusal.not_a_machine");
            case ROUTER -> Component.translatable(RANGE_KEY + "range.refusal.router");
            case DOCK -> Component.translatable(KEY + "refusal.dock");
            case OUT_OF_RANGE -> Component.translatable(RANGE_KEY + "range.refusal.out_of_range", distance,
                    radius(dock));
            case LIMIT -> Component.translatable(RANGE_KEY + "range.refusal.limit",
                    Component.translatable(dock.tier().translationKey()), max(dock));
            case OTHER_BLOCK -> {
                DockSide dockSide = dock.side(side);
                ResourceLocation block = dockSide == null ? null : dockSide.block();
                yield Component.translatable(KEY + "refusal.other_block", block == null
                        ? Component.empty() : RouterBlock.machineName(BuiltInRegistries.BLOCK.get(block)));
            }
            case OTHER_SIDE -> {
                DockMachine owned = pos == null ? null : dock.machineAt(pos);
                yield Component.translatable(KEY + "refusal.other_side", machineName(level, pos),
                        sideName(owned == null ? 0 : owned.side()));
            }
            case OTHER_DOCK -> {
                PatternDockBlockEntity other = level == null || pos == null ? null : otherDock(level, dock, pos);
                DockMachine owned = other == null ? null : other.machineAt(pos);
                yield Component.translatable(KEY + "refusal.other_dock", machineName(level, pos),
                        sideName(owned == null ? 0 : owned.side()),
                        LinkerActions.position(other == null ? dock.getBlockPos() : other.getBlockPos()));
            }
        };
    }

    private static Component machineName(@Nullable Level level, @Nullable BlockPos pos) {
        return level == null || pos == null || !level.isLoaded(pos) ? Component.empty()
                : RouterBlock.machineName(level.getBlockState(pos).getBlock());
    }

    static Result refuse(ServerPlayer player, Result result, PatternDockBlockEntity dock, int side,
            @Nullable BlockPos pos, int distance) {
        player.displayClientMessage(refusalText(result.refusal(), dock, side, pos, distance), true);
        return result;
    }

    /** A Doca escolhida não está aqui: em outra dimensão, ou descarregada ou quebrada. */
    static Result missing(ServerPlayer player, ItemStack stack) {
        DockChoice choice = LinkerItem.dock(stack);
        boolean otherDimension = choice != null && !choice.pos().dimension().equals(player.level().dimension());
        player.displayClientMessage(Component.translatable(KEY + (otherDimension ? "other_dimension" : "missing")),
                true);
        return Result.of(Status.DOCK_MISSING);
    }

    /** {@code text}, mais {@code · N <chave>} quando {@code count} é maior que zero. */
    static void appendCount(MutableComponent text, String key, int count) {
        if (count > 0) {
            text.append(" · ").append(Component.translatable(KEY + key, count));
        }
    }
}
