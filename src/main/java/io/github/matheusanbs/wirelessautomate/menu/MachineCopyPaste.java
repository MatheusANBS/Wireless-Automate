package io.github.matheusanbs.wirelessautomate.menu;

import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.network.FaceConfig;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.packet.PanelNoticePayload;
import io.github.matheusanbs.wirelessautomate.preset.PresetApplier;
import io.github.matheusanbs.wirelessautomate.preset.PresetPaste;
import io.github.matheusanbs.wirelessautomate.range.AttachedMember;
import io.github.matheusanbs.wirelessautomate.range.EditTarget;
import io.github.matheusanbs.wirelessautomate.range.MachineConfig;
import io.github.matheusanbs.wirelessautomate.range.MachineGroup;
import io.github.matheusanbs.wirelessautomate.range.MachineNames;
import io.github.matheusanbs.wirelessautomate.range.RemoteMachine;
import io.github.matheusanbs.wirelessautomate.range.RemoteMachines;
import io.github.matheusanbs.wirelessautomate.range.RemoteSide;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import org.jetbrains.annotations.Nullable;

/**
 * Copiar e colar o perfil de uma máquina na tela do roteador (ou em outro {@link RouterEditHost}), no servidor: a
 * cópia vai para o {@link MachineClipboard} do jogador; o colar vai no alvo de edição (a exceção de uma máquina, a
 * presa pela regra de uma máquina do grupo, ou o perfil do grupo), pela regra das redes do Tablet
 * ({@link PresetApplier#usable(ServerPlayer)} e {@link PresetPaste#canLeave}); o Desfazer pelo
 * {@link MachinePasteUndo}. Os lados são {@link RemoteSide} dos dois lados: colar não converte nada. Os cartões não
 * entram (são do grupo).
 */
public final class MachineCopyPaste {
    private static final ResourceType[] TYPES = ResourceType.values();
    private static final RemoteSide[] SIDES = RemoteSide.values();
    private static final Direction[] DIRECTIONS = Direction.values();
    private static final String NOTICE = "gui.wirelessautomate.panel.notice.";

    /**
     * O que o colar fez.
     *
     * @param changed        algo mudou
     * @param exceptionsKept exceções que continuam no grupo colado (só no perfil do grupo)
     * @param keptFaces      faces que ficaram com a rede que tinham: a rede copiada o jogador não pode usar, ou a
     *                       de agora ele não edita
     * @param source         o nome de origem da cópia
     * @param where          o nome do alvo ("Fornalha #3", "presa", "Perfil: Fornalha")
     * @param undo           o Desfazer ficou guardado
     * @param noMachine      a presa não recebeu nada: não há máquina na frente do roteador (ar, ou o chunk dela
     *                       descarregado)
     */
    public record Pasted(boolean changed, int exceptionsKept, int keptFaces, Component source, Component where,
            boolean undo, boolean noMachine) {
        /**
         * O aviso do painel: "Colado de X em Y", com "· N exceções mantidas" e "· redes de N faces ficaram" quando
         * houve, e o Desfazer se ficou guardado; sem máquina na frente, a recusa.
         */
        public PanelNoticePayload notice(int containerId) {
            if (noMachine) {
                return PanelNoticePayload.plain(Component.translatable(NOTICE + "range.paste_no_machine"));
            }
            MutableComponent message = Component.translatable(NOTICE + "range.pasted", source, where);
            if (exceptionsKept > 0) {
                message.append(" · ").append(Component.translatable(
                        NOTICE + (exceptionsKept == 1 ? "exceptions_kept.one" : "exceptions_kept"), exceptionsKept));
            }
            if (keptFaces > 0) {
                message.append(" · ").append(Component.translatable(
                        NOTICE + (keptFaces == 1 ? "range.kept_networks.one" : "range.kept_networks"), keptFaces));
            }
            return new PanelNoticePayload(message, undo ? PanelNoticePayload.Undo.UNDO_MACHINE_PASTE
                    : PanelNoticePayload.Undo.NONE, containerId, ResourceType.ITEM, Direction.UP);
        }
    }

    private MachineCopyPaste() {
    }

    /**
     * Copia a configuração que vale para o alvo de edição: numa máquina (remota ou a presa), a exceção ou o perfil;
     * num grupo, o perfil. Um alvo que deixou de existir não copia nada. Devolve se copiou.
     */
    public static boolean copy(ServerPlayer player, RouterEditHost host) {
        EditTarget target = host.actionTarget();
        RouterBlockEntity router = host.editedRouter();
        if (target == null || router == null) {
            return false;
        }
        RemoteMachines remotes = router.remoteMachines();
        switch (target.kind()) {
            case MACHINE -> {
                RemoteMachine machine = remotes.machine(target.id());
                if (machine == null) {
                    return false;
                }
                MachineClipboard.put(player.getUUID(), remotes.effective(machine).copy(), remotes.groupOf(machine).block(),
                        MachineNames.machine(remotes, machine.pos()));
            }
            case GROUP -> {
                MachineGroup group = remotes.group(target.id());
                if (group == null) {
                    return false;
                }
                MachineClipboard.put(player.getUUID(), group.profile().copy(), group.block(), profileName(group));
            }
            case ATTACHED -> {
                MachineConfig config = remotes.effectiveAttached();
                MachineClipboard.put(player.getUUID(), config == null ? readAttached(router) : config.copy(),
                        attachedBlock(router), attachedName(router));
            }
        }
        host.editApplied();
        return true;
    }

    /** Esquece a cópia do jogador (o ✕ do rodapé). */
    public static void forget(ServerPlayer player, RouterEditHost host) {
        MachineClipboard.forget(player.getUUID());
        host.editApplied();
    }

    /**
     * Cola a cópia do jogador no alvo de edição: numa máquina remota, como exceção (some se fica igual ao perfil); na
     * presa, pela regra de uma máquina do grupo ({@link RouterBlockEntity#editAttachedConfig}); num grupo, ou numa
     * máquina com {@code wholeGroup}, no perfil do grupo dela (as exceções que ficam iguais somem, as outras são
     * contadas). Cada face recebe modo, prioridade, redstone e filtro; a rede só se a cópia leva uma que o jogador pode
     * usar e a face pode sair da que tem ({@link #merge}). Na presa sem membro, ele nasce antes do colar (no grupo do
     * bloco da frente, ou do rascunho de um mundo antigo), para o Desfazer ter o estado de antes; sem máquina na
     * frente, nada muda ({@link Pasted#noMachine}). Com mudança, guarda o Desfazer (o anterior sai sempre).
     * {@code null} sem cópia ou com o alvo que deixou de existir.
     */
    public static @Nullable Pasted paste(ServerPlayer player, RouterEditHost host, boolean wholeGroup) {
        EditTarget target = host.actionTarget();
        RouterBlockEntity router = host.editedRouter();
        MachineClipboard.Entry copy = MachineClipboard.get(player.getUUID());
        if (target == null || router == null || copy == null) {
            return null;
        }
        RemoteMachines remotes = router.remoteMachines();
        if (target.attached()) {
            if (!router.attachedCanHold()) {
                MachinePasteUndo.forget(player.getUUID());
                return new Pasted(false, 0, 0, copy.source(), attachedName(router), false, true);
            }
            router.ensureAttachedMember();
        }
        MachineGroup group = wholeGroup || target.kind() == EditTarget.Kind.GROUP ? groupOf(remotes, target) : null;
        RemoteMachine machine = target.kind() == EditTarget.Kind.MACHINE ? remotes.machine(target.id()) : null;
        if ((target.kind() == EditTarget.Kind.GROUP && group == null)
                || (target.kind() == EditTarget.Kind.MACHINE && machine == null)) {
            return null;
        }
        MachinePasteUndo.forget(player.getUUID());
        Predicate<UUID> usable = PresetApplier.usable(player);
        Predicate<UUID> canLeave = PresetPaste.canLeave(player);
        int[] kept = new int[1];
        PasteUndo.State before = PasteUndo.State.of(router, false, true);
        boolean changed;
        int exceptions = 0;
        Component where;
        if (group != null) {
            MachineConfig config = group.profile().copy();
            kept[0] = merge(config, copy.config(), usable, canLeave);
            changed = router.setGroupProfile(group.id(), config);
            exceptions = exceptionsIn(remotes, group.id());
            where = profileName(group);
        } else if (machine != null) {
            MachineConfig config = remotes.effective(machine).copy();
            kept[0] = merge(config, copy.config(), usable, canLeave);
            changed = router.setMachineException(target.id(), config);
            where = MachineNames.machine(remotes, machine.pos());
        } else {
            changed = router.editAttachedConfig(config -> kept[0] = merge(config, copy.config(), usable, canLeave));
            where = attachedName(router);
        }
        PasteUndo change = changed ? PasteUndo.between(before, PasteUndo.State.of(router, false, true), false) : null;
        if (change != null) {
            MachinePasteUndo.remember(player, host, change);
        }
        host.editApplied();
        return new Pasted(changed, exceptions, kept[0], copy.source(), where, change != null, false);
    }

    /**
     * O Desfazer do último colar: volta o perfil ou a exceção de antes, só no que ainda está como o colar deixou, se
     * o jogador está no mesmo menu e roteador ({@link MachinePasteUndo#take}). Vale uma vez. Devolve o aviso, ou
     * {@code null} sem nada guardado.
     */
    public static @Nullable PanelNoticePayload undo(ServerPlayer player, RouterEditHost host) {
        PasteUndo change = MachinePasteUndo.take(player, host);
        RouterBlockEntity router = host.editedRouter();
        if (change == null || router == null) {
            return null;
        }
        int restored = change.restore(router);
        host.editApplied();
        return PanelNoticePayload.plain(Component.translatable(
                NOTICE + (restored > 0 ? "range.paste_undone" : "range.paste_undo_refused")));
    }

    /**
     * Põe em {@code into} as faces de {@code from}, lado a lado: modo, prioridade, redstone e filtro sempre; a rede
     * só quando a de {@code from} existe e o jogador pode usá-la ({@code usable}) e a face pode sair da que tem
     * ({@code canLeave}; sem rede, sempre). Uma face sem rede na cópia fica com a que tem, como no colar do Tablet.
     * Devolve quantas faces ficaram com a rede de antes por causa da regra.
     */
    static int merge(MachineConfig into, MachineConfig from, Predicate<UUID> usable, Predicate<UUID> canLeave) {
        int kept = 0;
        for (ResourceType type : TYPES) {
            for (RemoteSide side : SIDES) {
                FaceConfig source = from.face(type, side);
                FaceConfig face = into.face(type, side);
                face.copySettingsFrom(source);
                UUID network = source.network();
                UUID current = face.network();
                if (network == null || network.equals(current)) {
                    continue;
                }
                if (usable.test(network) && (current == null || canLeave.test(current))) {
                    face.setNetwork(network);
                } else {
                    kept++;
                }
            }
        }
        return kept;
    }

    /** O grupo do alvo: o próprio, ou o da máquina ou da presa; {@code null} se não há. */
    private static @Nullable MachineGroup groupOf(RemoteMachines remotes, EditTarget target) {
        return switch (target.kind()) {
            case ATTACHED -> remotes.attachedGroup();
            case GROUP -> remotes.group(target.id());
            case MACHINE -> {
                RemoteMachine machine = remotes.machine(target.id());
                yield machine == null ? null : remotes.groupOf(machine);
            }
        };
    }

    /** As máquinas do grupo com exceção, a presa inclusive. */
    private static int exceptionsIn(RemoteMachines remotes, int groupId) {
        AttachedMember attached = remotes.attached();
        int count = remotes.groupHasAttached(groupId) && attached.hasException() ? 1 : 0;
        for (RemoteMachine machine : remotes.machines()) {
            if (machine.group() == groupId && machine.hasException()) {
                count++;
            }
        }
        return count;
    }

    /** "Perfil: Fornalha": o perfil de um grupo como origem ou destino. */
    private static Component profileName(MachineGroup group) {
        return Component.translatable("gui.wirelessautomate.router.range.copy.group", MachineNames.group(group));
    }

    /**
     * A configuração da presa sem membro, lida pela fachada (o rascunho de um mundo antigo, ou a vazia), nos lados da
     * máquina pela frente dela.
     */
    private static MachineConfig readAttached(RouterBlockEntity router) {
        MachineConfig config = new MachineConfig();
        int front = router.attachedFront();
        for (ResourceType type : TYPES) {
            for (Direction face : DIRECTIONS) {
                config.face(type, RemoteSide.fromAbsolute(front, face.ordinal())).copyFrom(router.face(type, face));
            }
        }
        return config;
    }

    /** O nome da presa: o dado pelo jogador, ou "presa". */
    private static Component attachedName(RouterBlockEntity router) {
        return router.attachedLabel().isEmpty() ? Component.translatable("gui.wirelessautomate.router.range.attached.row")
                : Component.literal(router.attachedLabel());
    }

    /** O bloco da presa: o do grupo dela, ou o da frente (ar com o chunk descarregado). */
    private static ResourceLocation attachedBlock(RouterBlockEntity router) {
        MachineGroup group = router.remoteMachines().attachedGroup();
        if (group != null) {
            return group.block();
        }
        Level level = router.getLevel();
        BlockPos pos = router.machinePos();
        return BuiltInRegistries.BLOCK.getKey(level != null && level.isLoaded(pos)
                ? level.getBlockState(pos).getBlock() : Blocks.AIR);
    }
}
