package io.github.matheusanbs.wirelessautomate.block;

import io.github.matheusanbs.wirelessautomate.filter.FilterSet;
import io.github.matheusanbs.wirelessautomate.network.FaceConfig;
import io.github.matheusanbs.wirelessautomate.network.NetworkManager;
import io.github.matheusanbs.wirelessautomate.network.RelativeSide;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.range.AttachedMember;
import io.github.matheusanbs.wirelessautomate.range.MachineConfig;
import io.github.matheusanbs.wirelessautomate.range.MachineGroup;
import io.github.matheusanbs.wirelessautomate.range.RemoteMachines;
import io.github.matheusanbs.wirelessautomate.range.RemoteSide;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * A máquina presa de um {@link RouterBlockEntity} vista como membro do grupo do bloco dela ({@link AttachedMember}
 * em {@link RemoteMachines}): a configuração que vale (a exceção ou o perfil), nos lados da máquina pela frente
 * dela; a edição com a regra de uma máquina do grupo; os cartões do grupo; o acompanhamento do bloco da frente;
 * e o rascunho de um mundo antigo ({@link AttachedDraft}) até a conversão.
 *
 * <p>As faces chegam e saem absolutas ({@link Direction}). Sem membro, a leitura dá o rascunho, se houver, ou a
 * configuração vazia; a primeira escrita que muda algo cria o membro, se o bloco da frente está carregado e não é
 * ar. Nada aqui roda por tick: só na montagem das rotas, na tela, nas edições e nos avisos do bloco.
 */
final class RouterAttached {
    private static final Direction[] DIRECTIONS = Direction.values();
    /** A face de uma presa sem membro. Só leitura: ninguém muda o {@code FaceConfig} devolvido por {@link #face}. */
    private static final FaceConfig EMPTY = new FaceConfig();

    private final RouterBlockEntity router;
    private final RemoteMachines remotes;
    /** A presa de um mundo antigo, ainda sem membro; {@code null} sem nada a converter. */
    private @Nullable AttachedDraft draft;

    RouterAttached(RouterBlockEntity router, RemoteMachines remotes) {
        this.router = router;
        this.remotes = remotes;
    }

    // ------------------------------------------------------------------ leitura

    /** O lado da máquina presa na face absoluta {@code face}, pela última frente conhecida. */
    private static RemoteSide side(AttachedMember member, Direction face) {
        return RemoteSide.fromAbsolute(member.front(), face.ordinal());
    }

    /** Ver {@link RouterBlockEntity#face(ResourceType, Direction)}. */
    FaceConfig face(ResourceType type, Direction face) {
        AttachedMember member = remotes.attached();
        MachineConfig config = remotes.effectiveAttached();
        if (config != null) {
            return config.face(type, side(member, face));
        }
        return draft == null ? EMPTY : draft.face(type, relative(face));
    }

    /** O lado relativo do roteador na face absoluta (o quadro do rascunho). */
    private RelativeSide relative(Direction face) {
        return RelativeSide.fromAbsolute(router.facing(), router.spin(), face);
    }

    /** O rascunho de um mundo antigo, ainda sem membro; {@code null} sem nada a converter. */
    @Nullable AttachedDraft draft() {
        return draft;
    }

    /** A rede comum às seis faces do tipo, ou {@code null} se nenhuma tem rede ou se estão em redes diferentes. */
    @Nullable UUID commonNetwork(ResourceType type) {
        UUID network = face(type, DIRECTIONS[0]).network();
        for (int i = 1; i < DIRECTIONS.length; i++) {
            if (!Objects.equals(network, face(type, DIRECTIONS[i]).network())) {
                return null;
            }
        }
        return network;
    }

    /** Ver {@link RouterBlockEntity#attachedFacesIn}. */
    boolean facesIn(Collection<ResourceType> types, int absoluteFaceMask, @Nullable UUID network) {
        for (ResourceType type : types) {
            for (Direction face : DIRECTIONS) {
                if ((absoluteFaceMask & (1 << face.ordinal())) != 0
                        && !Objects.equals(network, face(type, face).network())) {
                    return false;
                }
            }
        }
        return true;
    }

    /** O cartão do grupo da presa no slot da face; vazio sem membro. */
    ItemStack card(ResourceType type, Direction face, int slot) {
        MachineGroup group = remotes.attachedGroup();
        if (group != null) {
            return group.card(type, side(remotes.attached(), face), slot);
        }
        return draft == null ? ItemStack.EMPTY : draft.card(type, relative(face), slot);
    }

    /** Cartões nos slots da face para o tipo. */
    int cardCount(ResourceType type, Direction face) {
        int count = 0;
        for (int slot = 0; slot < RouterBlockEntity.CARD_SLOTS; slot++) {
            if (!card(type, face, slot).isEmpty()) {
                count++;
            }
        }
        return count;
    }

    /** Ver {@link RouterBlockEntity#filterSet(ResourceType, Direction)}. */
    FilterSet filterSet(ResourceType type, Direction face) {
        MachineGroup group = remotes.attachedGroup();
        if (group == null) {
            // O rascunho só monta portas sem os cartões: a conversão vem antes, na montagem, com o bloco à mão.
            return face(type, face).filter().asSet();
        }
        RemoteSide side = side(remotes.attached(), face);
        return RouterCards.groupFilterSet(group, remotes.effectiveAttached().face(type, side).filter(), type, side);
    }

    // ------------------------------------------------------------------ escrita

    /**
     * Muda uma face da presa com a regra de uma máquina do grupo: única máquina do grupo, muda o perfil (uma
     * exceção que ela tenha passa antes para o perfil); senão muda a exceção, criada como cópia do perfil, que
     * some se fica igual a ele. Um aviso ao motor, com o tipo, se algo mudou. Sem membro, {@code edit} roda antes
     * numa cópia solta da face que se lê: só a escrita que muda algo cria o membro (se o bloco da frente deixa;
     * senão a escrita é ignorada e devolve {@code false}). O membro criado conta como mudança (com todos os tipos),
     * mesmo que o grupo em que ele entrou já tivesse a face assim.
     */
    boolean edit(ResourceType type, Direction face, Predicate<FaceConfig> edit) {
        boolean created = false;
        if (remotes.attached() == null) {
            FaceConfig probe = new FaceConfig();
            probe.copyFrom(face(type, face));
            if (!edit.test(probe) || !(created = ensureMember())) {
                return false;
            }
        }
        AttachedMember member = remotes.attached();
        MachineGroup group = remotes.attachedGroup();
        RemoteSide side = side(member, face);
        MachineConfig exception = member.exception();
        if (remotes.memberCount(group.id()) == 1) {
            boolean folded = exception != null;
            if (folded) {
                group.profile().copyFrom(exception);
                member.setException(null);
            }
            if (!edit.test(group.profile().face(type, side)) && !folded && !created) {
                return false;
            }
        } else {
            MachineConfig config = exception != null ? exception : group.profile().copy();
            if (!edit.test(config.face(type, side)) && !created) {
                return false;
            }
            member.setException(config.sameAs(group.profile()) ? null : config);
        }
        router.changed(created ? NetworkManager.ALL_TYPES : NetworkManager.typeBit(type));
        return true;
    }

    /** Põe o cartão no slot da face, no grupo da presa; sem membro, só um cartão de verdade cria o membro. */
    void setCard(ResourceType type, Direction face, int slot, ItemStack stack) {
        boolean created = false;
        if (remotes.attached() == null && (stack.isEmpty() || !(created = ensureMember()))) {
            return;
        }
        MachineGroup group = remotes.attachedGroup();
        if (group.setCard(type, side(remotes.attached(), face), slot, stack) || created) {
            router.changed(created ? NetworkManager.ALL_TYPES : NetworkManager.typeBit(type));
        }
    }

    /**
     * Põe as faces absolutas marcadas em {@code absoluteFaceMask} dos tipos na rede, menos as que o
     * {@code canLeave} segura (contadas em {@code kept}). Devolve quantas mudaram.
     */
    int setNetwork(Collection<ResourceType> types, int absoluteFaceMask, @Nullable UUID network,
            Predicate<UUID> canLeave, int[] kept) {
        int count = 0;
        for (ResourceType type : types) {
            for (Direction face : DIRECTIONS) {
                if ((absoluteFaceMask & (1 << face.ordinal())) != 0
                        && edit(type, face, config -> RouterFaces.moveFace(config, network, canLeave, kept))) {
                    count++;
                }
            }
        }
        return count;
    }

    /** Tira a exceção da presa: ela volta a seguir o perfil do grupo. Devolve se tinha. */
    boolean revertToProfile() {
        AttachedMember member = remotes.attached();
        if (member == null || !member.hasException()) {
            return false;
        }
        member.setException(null);
        router.changed(NetworkManager.ALL_TYPES);
        return true;
    }

    /**
     * Troca a exceção da presa por uma cópia de {@code config}; {@code null}, ou igual ao perfil do grupo, tira a
     * exceção. Sem membro, nada. Devolve se mudou.
     */
    boolean setException(@Nullable MachineConfig config) {
        AttachedMember member = remotes.attached();
        MachineGroup group = remotes.attachedGroup();
        if (member == null || group == null) {
            return false;
        }
        MachineConfig next = config == null || config.sameAs(group.profile()) ? null : config.copy();
        MachineConfig now = member.exception();
        if (now == null ? next == null : next != null && now.sameAs(next)) {
            return false;
        }
        member.setException(next);
        router.changed(NetworkManager.ALL_TYPES);
        return true;
    }

    /**
     * Garante o membro presa: se não há, converte o rascunho ou entra no grupo do bloco da frente. Só no
     * servidor, com o chunk da máquina carregado e um bloco que não é ar. Devolve se o membro nasceu agora (quem
     * chama avisa o motor de todos os tipos: o grupo em que ela entrou pode já estar configurado).
     */
    boolean ensureMember() {
        if (remotes.attached() != null) {
            return false;
        }
        BlockState state = machineState();
        if (state == null || state.isAir()) {
            return false;
        }
        if (draft != null) {
            convert(state);
        } else {
            remotes.attach(BuiltInRegistries.BLOCK.getKey(state.getBlock()), RemoteMachines.frontOf(state));
        }
        return true;
    }

    /** Ver {@link RouterBlockEntity#attachedCanHold()}. */
    boolean canHold() {
        BlockState state = machineState();
        return remotes.attached() != null || (state != null && !state.isAir());
    }

    /** O estado do bloco da frente, ou {@code null} fora do servidor ou com o chunk dele descarregado. */
    private @Nullable BlockState machineState() {
        Level level = router.getLevel();
        if (level == null || level.isClientSide) {
            return null;
        }
        BlockPos pos = router.machinePos();
        return level.isLoaded(pos) ? level.getBlockState(pos) : null;
    }

    // ------------------------------------------------------------------ o bloco da frente

    /**
     * Confere o bloco da frente, lido do mundo (só com o chunk carregado). Ver {@link #sync(BlockState)}.
     */
    boolean sync() {
        if (draft == null && remotes.attached() == null) {
            return false;
        }
        BlockState state = machineState();
        return state != null && sync(state);
    }

    /**
     * Acompanha o bloco da frente em {@code state}: converte o rascunho; com outro bloco (que não é ar), passa a
     * presa para o grupo dele, sem a exceção; com outra frente, guarda a frente. Ar não muda nada. Devolve se a
     * configuração da presa mudou (aí salva e refaz a tela; o motor fica com quem chamou).
     */
    boolean sync(BlockState state) {
        AttachedMember member = remotes.attached();
        if (state.isAir() || (draft == null && member == null)) {
            return false;
        }
        int front = RemoteMachines.frontOf(state);
        boolean changed;
        if (draft != null) {
            convert(state);
            changed = true;
        } else {
            int before = member.front();
            changed = remotes.attach(BuiltInRegistries.BLOCK.getKey(state.getBlock()), front) || before != front;
        }
        if (changed) {
            router.bumpChangeVersion();
            router.setChanged();
        }
        return changed;
    }

    /** A frente de {@code state} mudou: guarda (uma troca só de estado, como girar a máquina). Devolve se mudou. */
    boolean frontChanged(BlockState state) {
        AttachedMember member = remotes.attached();
        if (member == null || state.isAir()) {
            return false;
        }
        int front = RemoteMachines.frontOf(state);
        if (front == member.front()) {
            return false;
        }
        member.setFront(front);
        router.bumpChangeVersion();
        router.setChanged();
        return true;
    }

    /**
     * O rascunho vira membro (spec, "Mundos antigos"): sem grupo do bloco, ou com um grupo de cartões diferentes,
     * num grupo novo com o rascunho como perfil e os cartões dele; senão no primeiro grupo do bloco, com a
     * exceção se a configuração difere do perfil.
     */
    private void convert(BlockState state) {
        AttachedDraft old = draft;
        draft = null;
        Direction facing = router.facing();
        int spin = router.spin();
        int front = RemoteMachines.frontOf(state);
        MachineConfig config = old.toConfig(facing, spin, front);
        MachineGroup group = remotes.firstGroupOf(BuiltInRegistries.BLOCK.getKey(state.getBlock()));
        if (group == null || (old.hasCards() && !old.sameCards(group, facing, spin, front))) {
            group = remotes.newGroup(BuiltInRegistries.BLOCK.getKey(state.getBlock()), config);
            old.cardsInto(group, facing, spin, front);
            remotes.attachTo(group.id(), null, front);
        } else {
            remotes.attachTo(group.id(), config.sameAs(group.profile()) ? null : config, front);
            // Os cartões do rascunho são iguais aos do grupo (ou não há): as pilhas físicas dele sobram e caem no
            // roteador, para nenhum item sumir.
            dropDraftCards(old);
        }
    }

    private void dropDraftCards(AttachedDraft old) {
        Level level = router.getLevel();
        if (level == null || !old.hasCards()) {
            return;
        }
        List<ItemStack> removed = new ArrayList<>();
        old.removeAllCards(removed);
        for (ItemStack stack : removed) {
            Block.popResource(level, router.getBlockPos(), stack);
        }
    }

    /** O roteador girou ({@code spin}) com o rascunho pendente: ele gira junto, sem mudar as faces absolutas. */
    void spun(int oldSpin, int newSpin) {
        if (draft != null) {
            draft.rotate(router.facing(), oldSpin, newSpin);
        }
    }

    // ------------------------------------------------------------------ NBT

    /** Lê o rascunho das chaves antigas (com a rede antiga da aba); só sem o membro já salvo. */
    void load(CompoundTag tag, HolderLookup.Provider registries, UUID[] legacy) {
        draft = remotes.attached() == null ? AttachedDraft.load(tag, registries, legacy) : null;
    }

    /** Grava o rascunho nas chaves antigas, enquanto houver. */
    void save(CompoundTag tag, HolderLookup.Provider registries) {
        if (draft != null) {
            draft.save(tag, registries);
        }
    }

    /** Tira os cartões do rascunho (os do grupo saem com os grupos). */
    void removeDraftCards(List<ItemStack> removed) {
        if (draft != null) {
            draft.removeAllCards(removed);
        }
    }
}
