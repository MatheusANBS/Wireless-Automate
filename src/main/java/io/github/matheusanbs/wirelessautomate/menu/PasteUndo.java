package io.github.matheusanbs.wirelessautomate.menu;

import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.network.RouterPreset;
import io.github.matheusanbs.wirelessautomate.range.MachineConfig;
import io.github.matheusanbs.wirelessautomate.range.MachineGroup;
import io.github.matheusanbs.wirelessautomate.range.RemoteMachine;
import io.github.matheusanbs.wirelessautomate.range.RemoteMachines;
import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

/**
 * O que o último colar do Tablet mudou num roteador, para o Desfazer: o antes e o depois da presa, de cada perfil de
 * grupo e de cada exceção que mudaram (inclusive a que não existia ou sumiu). Desfaz só o que ainda está como o
 * colar deixou: o que alguém mudou depois fica. Só na memória, até fechar o menu.
 */
final class PasteUndo {
    /**
     * Um roteador num instante: com {@code attached}, a presa (senão {@code null}); com {@code remotes}, os perfis por
     * id do grupo e as exceções por posição (só as máquinas que têm).
     */
    record State(@Nullable RouterPreset attached, Int2ObjectMap<MachineConfig> profiles,
            Long2ObjectMap<MachineConfig> exceptions) {
        static State of(RouterBlockEntity router, boolean attached, boolean remotes) {
            Int2ObjectMap<MachineConfig> profiles = new Int2ObjectLinkedOpenHashMap<>();
            Long2ObjectMap<MachineConfig> exceptions = new Long2ObjectLinkedOpenHashMap<>();
            if (remotes) {
                RemoteMachines machines = router.remoteMachines();
                for (MachineGroup group : machines.groups()) {
                    profiles.put(group.id(), group.profile().copy());
                }
                for (RemoteMachine machine : machines.machines()) {
                    if (machine.exception() != null) {
                        exceptions.put(machine.pos().asLong(), machine.exception().copy());
                    }
                }
            }
            return new State(attached ? RouterPreset.copyOf(router) : null, profiles, exceptions);
        }
    }

    private record ProfileChange(int group, MachineConfig before, MachineConfig after) {
    }

    private record ExceptionChange(long pos, @Nullable MachineConfig before, @Nullable MachineConfig after) {
    }

    /** A presa antes e depois; {@code null} se o colar não a atingiu. */
    private final @Nullable RouterPreset attachedBefore;
    private final @Nullable RouterPreset attachedAfter;
    private final List<ProfileChange> profiles;
    private final List<ExceptionChange> exceptions;

    private PasteUndo(@Nullable RouterPreset attachedBefore, @Nullable RouterPreset attachedAfter,
            List<ProfileChange> profiles, List<ExceptionChange> exceptions) {
        this.attachedBefore = attachedBefore;
        this.attachedAfter = attachedAfter;
        this.profiles = profiles;
        this.exceptions = exceptions;
    }

    /**
     * O que mudou de {@code before} para {@code after}; a presa vai sempre que o colar a atingiu ({@code attached},
     * e os dois estados a leram), mesmo igual. {@code null} se não há o que desfazer.
     */
    static @Nullable PasteUndo between(State before, State after, boolean attached) {
        List<ProfileChange> profiles = new ArrayList<>();
        for (Int2ObjectMap.Entry<MachineConfig> entry : after.profiles().int2ObjectEntrySet()) {
            MachineConfig was = before.profiles().get(entry.getIntKey());
            if (was != null && !was.sameAs(entry.getValue())) {
                profiles.add(new ProfileChange(entry.getIntKey(), was, entry.getValue()));
            }
        }
        LongLinkedOpenHashSet positions = new LongLinkedOpenHashSet(before.exceptions().keySet());
        positions.addAll(after.exceptions().keySet());
        List<ExceptionChange> exceptions = new ArrayList<>();
        for (long pos : positions) {
            MachineConfig was = before.exceptions().get(pos);
            MachineConfig now = after.exceptions().get(pos);
            if (!same(was, now)) {
                exceptions.add(new ExceptionChange(pos, was, now));
            }
        }
        if (!attached && profiles.isEmpty() && exceptions.isEmpty()) {
            return null;
        }
        return new PasteUndo(attached ? before.attached() : null, attached ? after.attached() : null,
                List.copyOf(profiles), List.copyOf(exceptions));
    }

    /**
     * Volta ao que era, num lote só: a presa, os perfis e depois as exceções, cada um só se ainda está como o colar
     * deixou. Devolve quantos voltaram.
     */
    int restore(RouterBlockEntity router) {
        int[] restored = new int[1];
        router.batchChanges(() -> {
            if (attachedBefore != null && RouterPreset.copyOf(router).equals(attachedAfter)) {
                attachedBefore.restoreTo(router);
                restored[0]++;
            }
            RemoteMachines remotes = router.remoteMachines();
            for (ProfileChange change : profiles) {
                MachineGroup group = remotes.group(change.group());
                if (group != null && group.profile().sameAs(change.after())
                        && router.setGroupProfile(change.group(), change.before())) {
                    restored[0]++;
                }
            }
            for (ExceptionChange change : exceptions) {
                int index = remotes.index(BlockPos.of(change.pos()));
                RemoteMachine machine = remotes.machine(index);
                if (machine != null && same(machine.exception(), change.after())) {
                    router.setMachineException(index, change.before());
                    restored[0]++;
                }
            }
        });
        return restored[0];
    }

    private static boolean same(@Nullable MachineConfig a, @Nullable MachineConfig b) {
        return a == null ? b == null : b != null && a.sameAs(b);
    }
}
