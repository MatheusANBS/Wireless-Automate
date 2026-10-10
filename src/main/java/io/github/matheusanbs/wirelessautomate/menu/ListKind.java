package io.github.matheusanbs.wirelessautomate.menu;

import io.github.matheusanbs.wirelessautomate.net.GameCodecs;
import io.github.matheusanbs.wirelessautomate.net.RegistryFriendlyByteBuf;
import io.github.matheusanbs.wirelessautomate.net.StreamCodec;
import io.github.matheusanbs.wirelessautomate.storage.ChemicalStorage;
import io.github.matheusanbs.wirelessautomate.storage.FluidStorage;
import io.github.matheusanbs.wirelessautomate.storage.StorageKind;
import it.unimi.dsi.fastutil.Hash;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackLinkedSet;
import net.minecraftforge.fluids.FluidStack;

/**
 * Como a tela em lista trata a chave de cada armazenamento por tipo: como ela viaja na rede, como
 * se compara e como se copia. Itens no Baú, fluidos no Tanque, ids de químico no Tanque Químico.
 *
 * <p>Porte 1.20.1: uma chave grande demais para a tela (NBT enorme, {@code StorageEntriesPayload#MAX_KEY_BYTES})
 * vai como um {@linkplain #truncated substituto}: o mesmo item ou fluido sem o NBT, com uma marca
 * ({@link #TRUNCATED_TAG}) que leva a referência da chave no menu do servidor. A marca deixa cada substituto único na
 * lista do cliente e faz a tela mostrar que o NBT não veio; as ações vão pela referência.
 */
public final class ListKind<K> {
    /** Marca do substituto de uma chave grande demais: a referência dela no menu do servidor. */
    public static final String TRUNCATED_TAG = "wirelessautomate:truncated";

    public static final ListKind<ItemStack> ITEMS = new ListKind<>(StorageKind.CHEST, GameCodecs.EXACT_ITEM_STACK,
            ItemStackLinkedSet.TYPE_AND_TAG, ItemStack::copy,
            (stack, ref) -> {
                ItemStack stub = new ItemStack(stack.getItem());
                stub.setTag(marker(ref));
                return stub;
            },
            stack -> stack.getTag() != null && stack.getTag().contains(TRUNCATED_TAG));
    public static final ListKind<FluidStack> FLUIDS = new ListKind<>(StorageKind.TANK, GameCodecs.FLUID_STACK,
            FluidStorage.FLUID_AND_COMPONENTS, FluidStack::copy,
            (fluid, ref) -> new FluidStack(fluid.getFluid(), 1, marker(ref)),
            fluid -> fluid.getTag() != null && fluid.getTag().contains(TRUNCATED_TAG));
    /** Ids de químico são pequenos: nunca viram substituto. */
    public static final ListKind<ResourceLocation> CHEMICALS = new ListKind<>(StorageKind.CHEMICAL_TANK,
            GameCodecs.RESOURCE_LOCATION, ChemicalStorage.IDS, id -> id, (id, ref) -> id, id -> false);

    public final StorageKind storage;
    public final StreamCodec<? super RegistryFriendlyByteBuf, K> codec;
    public final Hash.Strategy<? super K> strategy;
    private final UnaryOperator<K> copy;
    private final BiFunction<K, Integer, K> truncate;
    private final Predicate<K> truncated;

    private ListKind(StorageKind storage, StreamCodec<? super RegistryFriendlyByteBuf, K> codec,
            Hash.Strategy<? super K> strategy, UnaryOperator<K> copy, BiFunction<K, Integer, K> truncate,
            Predicate<K> truncated) {
        this.storage = storage;
        this.codec = codec;
        this.strategy = strategy;
        this.copy = copy;
        this.truncate = truncate;
        this.truncated = truncated;
    }

    public K copy(K key) {
        return copy.apply(key);
    }

    /** O substituto de {@code key} na tela: sem o NBT, com a marca da referência {@code ref} no menu. */
    public K truncated(K key, int ref) {
        return truncate.apply(key, ref);
    }

    /** {@code key} é o substituto de uma chave grande demais (a tela mostra uma marca; o NBT não veio). */
    public boolean isTruncated(K key) {
        return truncated.test(key);
    }

    private static CompoundTag marker(int ref) {
        CompoundTag tag = new CompoundTag();
        tag.putInt(TRUNCATED_TAG, ref);
        return tag;
    }

    /** A lista de um armazenamento por tipo (o Baú, os Tanques); a Bateria e o Tanque de Source não têm. */
    @SuppressWarnings("unchecked")
    public static <K> ListKind<K> of(StorageKind kind) {
        return (ListKind<K>) switch (kind) {
            case CHEST -> ITEMS;
            case TANK -> FLUIDS;
            case CHEMICAL_TANK -> CHEMICALS;
            case BATTERY, SOURCE_TANK -> throw new IllegalArgumentException(kind + " não tem lista");
        };
    }
}
