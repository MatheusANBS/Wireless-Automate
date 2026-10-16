package io.github.matheusanbs.wirelessautomate.resonator;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * O índice do servidor membro → Núcleo, por dimensão: cada bloco da casca de um Ressonador montado aponta para o
 * Núcleo dele, e um segundo Ressonador não monta com uma parede que já tem dono ({@code Problem.SHARED_WALL}).
 * Preenchido pela {@link ResonatorFormation} ao montar e pelo {@code onLoad} do Núcleo montado (antes da
 * revalidação, para a ordem da carga não trocar o dono); só o dono tira. Só a thread do servidor mexe.
 */
public final class ResonatorMembers {
    private static final Map<ResourceKey<Level>, Map<BlockPos, BlockPos>> MEMBERS = new HashMap<>();

    private ResonatorMembers() {
    }

    /** O Núcleo dono do bloco em {@code pos}, ou {@code null}. */
    public static @Nullable BlockPos owner(ResourceKey<Level> dimension, BlockPos pos) {
        Map<BlockPos, BlockPos> members = MEMBERS.get(dimension);
        return members == null ? null : members.get(pos);
    }

    static void claim(ResourceKey<Level> dimension, BlockPos pos, BlockPos core) {
        MEMBERS.computeIfAbsent(dimension, key -> new HashMap<>()).put(pos.immutable(), core.immutable());
    }

    /** Liga o bloco ao Núcleo só se ele ainda não tem dono: a carga não tira a parede de quem já a tinha. */
    static void claimIfFree(ResourceKey<Level> dimension, BlockPos pos, BlockPos core) {
        MEMBERS.computeIfAbsent(dimension, key -> new HashMap<>()).putIfAbsent(pos.immutable(), core.immutable());
    }

    /** Tira o bloco só se ele ainda for deste Núcleo; devolve se tirou. */
    static boolean release(ResourceKey<Level> dimension, BlockPos pos, BlockPos core) {
        Map<BlockPos, BlockPos> members = MEMBERS.get(dimension);
        return members != null && members.remove(pos, core);
    }

    /** O bloco é deste Núcleo ou não tem dono: só assim a desmontagem o devolve a solto. */
    static boolean ownedOrFree(ResourceKey<Level> dimension, BlockPos pos, BlockPos core) {
        BlockPos owner = owner(dimension, pos);
        return owner == null || owner.equals(core);
    }

    /** Parada do servidor: o próximo mundo começa sem membros. */
    public static void reset() {
        MEMBERS.clear();
    }
}
