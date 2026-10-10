package io.github.matheusanbs.wirelessautomate.network;

import java.lang.ref.WeakReference;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
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
 *   <li>quando o dono chama {@link #invalidate()} ou {@link #revalidate} (este só se o block entity no alvo
 *       não é mais o da última consulta, ou se um cache negativo agora acha a capability). O Forge 1.20.1 não
 *       avisa quando aparece um block entity onde não havia nenhum: o roteador chama {@link #revalidate} no
 *       {@code neighborChanged} da máquina e {@link #invalidate()} quando o bloco dela muda;</li>
 *   <li>quando o chunk do alvo carrega ou descarrega ({@link CapCacheChunks}).</li>
 * </ul>
 * <b>Cache negativo:</b> sem block entity no alvo, ou com um block entity sem a capability, não há
 * {@link LazyOptional} para ouvir. Uma máquina que passa a oferecer a capability sem trocar de block entity (lado
 * do Mekanism mudado de "nenhum" para saída) avisa os vizinhos ({@code neighborChanged}; o Mekanism 10.4 chama
 * {@code WorldUtils.notifyNeighborOfChange} depois de invalidar a capability do lado): o dono chama
 * {@link #revalidate}, que, com o mesmo block entity, pergunta de novo só aos caches nulos e invalida os que agora
 * acham algo. Sem custo por tick; uma mudança só de estado que não traz capability nova não acorda ninguém. Para
 * quem não avisa os vizinhos (só {@code invalidateCaps} e {@code setChanged}), um cache nulo busca de novo depois de
 * {@link #NEGATIVE_RECHECK_TICKS} (a porta que dormiu até o teto pergunta outra vez).
 *
 * <p><b>Chunks:</b> cada cache entra no índice do {@link CapCacheChunks} (pelo chunk do alvo) na primeira
 * {@link #get()} e sai no {@link #close()}; o chunk do alvo carregar ou descarregar invalida o cache, como o
 * {@code invalidateCapabilities(ChunkPos)} do NeoForge.
 *
 * <p><b>Busca por bloco:</b> criado com um {@link BlockLookup} ({@link VanillaBlockHandlers}: caldeirão e
 * compostor, sem block entity), a busca suja lê o estado do bloco e pergunta a ele antes do block entity; o handler
 * achado fica guardado até o dono invalidar (o roteador invalida quando o bloco da máquina muda) ou o chunk mudar;
 * a consulta confere o bloco guardado (trocado sem aviso aos vizinhos, refaz).
 *
 * <p>Sujo, a próxima {@link #get()} busca de novo: {@code level.getBlockEntity(pos)} e
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
    /**
     * Rede de segurança do cache negativo: um cache que guardou {@code null} busca de novo na primeira consulta
     * depois deste intervalo (o teto do sono das portas), para a máquina que passa a oferecer a capability sem
     * avisar os vizinhos. Custa no máximo uma busca por teto de sono por cache nulo consultado; nunca por tick.
     */
    static final int NEGATIVE_RECHECK_TICKS = NetworkManager.MAX_SLEEP_TICKS;
    private static final BooleanSupplier ALWAYS = () -> true;
    private static final Runnable NOTHING = () -> {
    };

    private final ServerLevel level;
    private final BlockPos pos;
    private final @Nullable Direction side;
    private final @Nullable Capability<T> capability;
    private final @Nullable Function<BlockEntity, @Nullable T> lookup;
    private final @Nullable BlockLookup<T> blockLookup;
    private final BooleanSupplier isValid;
    private final Runnable onInvalidate;

    private boolean dirty = true;
    /** Uma {@link #get()} entregou um valor desde o último aviso. */
    private boolean handedOut;
    private @Nullable BlockEntity blockEntity;
    private @Nullable LazyOptional<T> optional;
    /** O listener posto em {@link #optional}, para tirar ao trocar de optional ou no {@link #close()}. */
    private @Nullable Listener<T> listener;
    private @Nullable T value;
    /** Está no índice do {@link CapCacheChunks}. */
    private boolean indexed;
    /** O bloco que deu o valor na busca por bloco ({@link BlockLookup}), ou {@code null} se o valor veio do block entity. */
    private @Nullable Block valueBlock;
    /** O {@code getGameTime} da última busca no mundo (para a rede de segurança do cache negativo). */
    private long checkedAt;

    /** Cache sem aviso de invalidação. */
    public CapCache(ServerLevel level, BlockPos pos, @Nullable Direction side, Capability<T> capability) {
        this(level, pos, side, capability, ALWAYS, NOTHING);
    }

    public CapCache(ServerLevel level, BlockPos pos, @Nullable Direction side, Capability<T> capability,
            BooleanSupplier isValid, Runnable onInvalidate) {
        this(level, pos, side, capability, null, null, isValid, onInvalidate);
    }

    private CapCache(ServerLevel level, BlockPos pos, @Nullable Direction side, @Nullable Capability<T> capability,
            @Nullable Function<BlockEntity, @Nullable T> lookup, @Nullable BlockLookup<T> blockLookup,
            BooleanSupplier isValid, Runnable onInvalidate) {
        this.level = level;
        this.pos = pos.immutable();
        this.side = side;
        this.capability = capability;
        this.lookup = lookup;
        this.blockLookup = blockLookup;
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
     * Como {@link #create(Capability, ServerLevel, BlockPos, Direction, BooleanSupplier, Runnable)}, mas a busca
     * suja pergunta antes ao bloco ({@code blockLookup}, com o estado lido na hora); sem resposta dele, segue
     * pela capability do block entity. Para os blocos vanilla sem block entity ({@link VanillaBlockHandlers}).
     */
    public static <T> CapCache<T> create(Capability<T> capability, ServerLevel level, BlockPos pos,
            @Nullable Direction side, BlockLookup<T> blockLookup, BooleanSupplier isValid, Runnable onInvalidate) {
        return new CapCache<>(level, pos, side, capability, null, blockLookup, isValid, onInvalidate);
    }

    /**
     * Cache de algo que o block entity do alvo dá sem capability (como o {@code ISourceTile} do Ars 4.12):
     * {@code lookup} recebe o block entity e devolve o valor ou {@code null}. Fica sujo pelas mesmas regras,
     * menos a do {@link LazyOptional}.
     */
    public static <T> CapCache<T> ofBlockEntity(ServerLevel level, BlockPos pos, Function<BlockEntity, @Nullable T> lookup,
            BooleanSupplier isValid, Runnable onInvalidate) {
        return new CapCache<>(level, pos, null, null, lookup, null, isValid, onInvalidate);
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
        if (!dirty && !stale() && (value != null || level.getGameTime() - checkedAt < NEGATIVE_RECHECK_TICKS)) {
            return value;
        }
        if (!indexed) {
            CapCacheChunks.add(this);
            indexed = true;
        }
        if (!level.isLoaded(pos)) {
            forget();
            return null;
        }
        checkedAt = level.getGameTime();
        if (blockLookup != null) {
            BlockState state = level.getBlockState(pos);
            T fromBlock = blockLookup.find(level, pos, state, side);
            if (fromBlock != null) {
                dropListener();
                blockEntity = null;
                optional = null;
                valueBlock = state.getBlock();
                value = fromBlock;
                dirty = false;
                return fromBlock;
            }
        }
        valueBlock = null;
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
        // Um listener por LazyOptional: o mesmo optional ainda válido já tem o nosso; ao trocar, sai do antigo.
        if (found != optional) {
            dropListener();
            if (found != null) {
                listener = new Listener<>(this);
                found.addListener(listener);
            }
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

    /**
     * O vizinho avisou mudança ({@code current} é o block entity no alvo agora). Fica sujo se o block entity não
     * é o da última consulta (apareceu, sumiu ou foi trocado). Com o mesmo block entity, um cache negativo (que
     * guardou {@code null}) pergunta de novo, sem guardar nada, e só fica sujo se agora acha a capability (a
     * máquina passou a oferecer sem trocar de block entity). Mudança só de estado, sem capability nova, não faz
     * nada (nem avisa o dono). Um cache já sujo fica como está.
     */
    public void revalidate(@Nullable BlockEntity current) {
        if (dirty) {
            return;
        }
        if (blockEntity != current) {
            invalidate();
        } else if (value == null && level.isLoaded(pos) && presentNow(current)) {
            invalidate();
        }
    }

    /** O alvo oferece agora o que este cache procura? Sem efeito no cache (nem listener). */
    private boolean presentNow(@Nullable BlockEntity be) {
        if (blockLookup != null && blockLookup.find(level, pos, level.getBlockState(pos), side) != null) {
            return true;
        }
        if (be == null) {
            return false;
        }
        if (capability != null) {
            return be.getCapability(capability, side).isPresent();
        }
        return lookup != null && lookup.apply(be) != null;
    }

    /** O valor guardado já não vale: o block entity saiu ou o optional foi invalidado sem avisar. */
    private boolean stale() {
        if (valueBlock != null) {
            // Handler de bloco: o bloco foi trocado sem aviso aos vizinhos (flag 2, ferramenta de edição)?
            return !level.isLoaded(pos) || !VanillaBlockHandlers.sameHandler(valueBlock, level.getBlockState(pos).getBlock());
        }
        return (blockEntity != null && blockEntity.isRemoved()) || (optional != null && !optional.isPresent());
    }

    /**
     * Descarta o cache (o dono não vai mais usá-lo: roteador removido ou girado): tira o listener do
     * {@link LazyOptional} guardado, que senão ficaria preso nele. Não avisa o dono. Uma {@link #get()}
     * depois disso volta a funcionar normalmente.
     */
    public void close() {
        forget();
        handedOut = false;
        if (indexed) {
            CapCacheChunks.remove(this);
            indexed = false;
        }
    }

    private void dropListener() {
        if (optional != null && listener != null) {
            optional.removeListener(listener);
        }
        listener = null;
    }

    private void forget() {
        dropListener();
        valueBlock = null;
        blockEntity = null;
        optional = null;
        value = null;
        dirty = true;
    }

    /** Busca por bloco: o valor que o bloco em {@code state} dá pelo lado, ou {@code null}. */
    @FunctionalInterface
    public interface BlockLookup<T> {
        @Nullable T find(ServerLevel level, BlockPos pos, BlockState state, @Nullable Direction side);
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
