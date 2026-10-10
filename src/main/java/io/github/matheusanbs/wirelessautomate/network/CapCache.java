package io.github.matheusanbs.wirelessautomate.network;

import java.lang.ref.WeakReference;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.common.util.NonNullConsumer;
import org.jetbrains.annotations.Nullable;

/**
 * Cache de uma capability de um bloco vizinho, no lugar do {@code BlockCapabilityCache} do NeoForge (porte
 * 1.20.1). Uma consulta com o cache limpo é um campo lido, sem busca no mundo: nada de {@code getCapability}
 * por tick.
 *
 * <p><b>Quando o cache se refaz (fica sujo):</b>
 * <ul>
 *   <li>quando o {@link LazyOptional} guardado é invalidado (o block entity do alvo foi removido, trocou de
 *       capability ou chamou {@code invalidateCaps}): o cache se registra nele com {@code addListener};</li>
 *   <li>quando o block entity guardado foi removido ({@link BlockEntity#isRemoved()}, checado a cada
 *       consulta; cobre quem não invalida as capabilities);</li>
 *   <li>quando o dono chama {@link #invalidate()}. O Forge 1.20.1 não avisa quando aparece um block entity
 *       onde não havia nenhum, nem quando o chunk do alvo carrega: o roteador chama {@link #invalidate()} no
 *       {@code neighborChanged}/{@code onNeighborChange} do bloco e quando a face muda.</li>
 * </ul>
 * Sujo, a próxima {@link #get()} busca de novo: {@code level.getBlockEntity(pos)} e
 * {@code getCapability(cap, side)}, só se o chunk do alvo estiver carregado ({@code level.isLoaded(pos)}); com
 * o chunk descarregado devolve {@code null} e continua sujo.
 *
 * <p><b>Aviso de invalidação:</b> o {@code onInvalidate} (o mesmo do {@code BlockCapabilityCache.create}) roda
 * quando o cache fica sujo depois de ter entregado um valor (inclusive {@code null}) numa {@link #get()}, e só
 * se {@code isValid} ainda for verdade. Vários avisos seguidos sem consulta no meio viram um só. O
 * {@code LazyOptional} guarda o cache por referência fraca: um roteador removido não fica preso na máquina.
 *
 * <p>Só no servidor e na thread do servidor.
 */
public final class CapCache<T> {
    private static final BooleanSupplier ALWAYS = () -> true;
    private static final Runnable NOTHING = () -> {
    };

    private final ServerLevel level;
    private final BlockPos pos;
    private final @Nullable Direction side;
    private final @Nullable Capability<T> capability;
    private final @Nullable Function<BlockEntity, @Nullable T> lookup;
    private final BooleanSupplier isValid;
    private final Runnable onInvalidate;

    private boolean dirty = true;
    /** Uma {@link #get()} entregou um valor desde o último aviso. */
    private boolean handedOut;
    private @Nullable BlockEntity blockEntity;
    private @Nullable LazyOptional<T> optional;
    private @Nullable T value;

    /** Cache sem aviso de invalidação. */
    public CapCache(ServerLevel level, BlockPos pos, @Nullable Direction side, Capability<T> capability) {
        this(level, pos, side, capability, ALWAYS, NOTHING);
    }

    public CapCache(ServerLevel level, BlockPos pos, @Nullable Direction side, Capability<T> capability,
            BooleanSupplier isValid, Runnable onInvalidate) {
        this(level, pos, side, capability, null, isValid, onInvalidate);
    }

    private CapCache(ServerLevel level, BlockPos pos, @Nullable Direction side, @Nullable Capability<T> capability,
            @Nullable Function<BlockEntity, @Nullable T> lookup, BooleanSupplier isValid, Runnable onInvalidate) {
        this.level = level;
        this.pos = pos.immutable();
        this.side = side;
        this.capability = capability;
        this.lookup = lookup;
        this.isValid = isValid;
        this.onInvalidate = onInvalidate;
    }

    /**
     * Mesma ordem de argumentos do {@code BlockCapabilityCache.create(capability, level, pos, context, isValid,
     * invalidationListener)} do NeoForge, para o {@code RouterBlockEntity} trocar pouco.
     */
    public static <T> CapCache<T> create(Capability<T> capability, ServerLevel level, BlockPos pos,
            @Nullable Direction side, BooleanSupplier isValid, Runnable onInvalidate) {
        return new CapCache<>(level, pos, side, capability, isValid, onInvalidate);
    }

    /**
     * Cache de algo que o block entity do alvo dá sem capability (como o {@code ISourceTile} do Ars 4.12):
     * {@code lookup} recebe o block entity e devolve o valor ou {@code null}. Fica sujo pelas mesmas regras,
     * menos a do {@link LazyOptional}.
     */
    public static <T> CapCache<T> ofBlockEntity(ServerLevel level, BlockPos pos, Function<BlockEntity, @Nullable T> lookup,
            BooleanSupplier isValid, Runnable onInvalidate) {
        return new CapCache<>(level, pos, null, null, lookup, isValid, onInvalidate);
    }

    public ServerLevel level() {
        return level;
    }

    public BlockPos pos() {
        return pos;
    }

    /** O lado consultado (o {@code context()} do {@code BlockCapabilityCache}). */
    public @Nullable Direction context() {
        return side;
    }

    /** A capability, ou {@code null} se o alvo não tem (ou se o chunk dele não está carregado). */
    public @Nullable T get() {
        handedOut = true;
        if (!dirty && !stale()) {
            return value;
        }
        if (!level.isLoaded(pos)) {
            forget();
            return null;
        }
        BlockEntity be = level.getBlockEntity(pos);
        LazyOptional<T> found = null;
        T result = null;
        if (be != null) {
            if (capability != null) {
                found = be.getCapability(capability, side);
                if (!found.isPresent()) {
                    found = null;
                } else {
                    result = found.resolve().orElse(null);
                }
            } else if (lookup != null) {
                result = lookup.apply(be);
            }
        }
        // Um listener por LazyOptional: o mesmo optional ainda válido já tem o nosso.
        if (found != null && found != optional) {
            found.addListener(new Listener<>(this));
        }
        blockEntity = be;
        optional = found;
        value = result;
        dirty = false;
        return result;
    }

    /** Igual a {@link #get()}; o nome do {@code BlockCapabilityCache}. */
    public @Nullable T getCapability() {
        return get();
    }

    /**
     * Marca o cache sujo (a próxima {@link #get()} busca de novo) e avisa o dono se ele já tinha recebido um
     * valor. Chamado pelo dono quando o vizinho pode ter mudado e pelo {@link LazyOptional} invalidado.
     */
    public void invalidate() {
        dirty = true;
        value = null;
        if (handedOut && isValid.getAsBoolean()) {
            handedOut = false;
            onInvalidate.run();
        }
    }

    /** O valor guardado já não vale: o block entity saiu ou o optional foi invalidado sem avisar. */
    private boolean stale() {
        return (blockEntity != null && blockEntity.isRemoved()) || (optional != null && !optional.isPresent());
    }

    private void forget() {
        blockEntity = null;
        optional = null;
        value = null;
        dirty = true;
    }

    /** Ouve a invalidação de um {@link LazyOptional}; só age se ele ainda for o guardado pelo cache. */
    private static final class Listener<T> implements NonNullConsumer<LazyOptional<T>> {
        private final WeakReference<CapCache<T>> cache;

        Listener(CapCache<T> cache) {
            this.cache = new WeakReference<>(cache);
        }

        @Override
        public void accept(LazyOptional<T> invalidated) {
            CapCache<T> owner = cache.get();
            if (owner != null && owner.optional == invalidated) {
                owner.invalidate();
            }
        }
    }
}
