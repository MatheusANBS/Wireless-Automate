package io.github.matheusanbs.wirelessautomate.menu;

import io.github.matheusanbs.wirelessautomate.net.GameCodecs;
import io.github.matheusanbs.wirelessautomate.net.RegistryFriendlyByteBuf;
import io.github.matheusanbs.wirelessautomate.net.StreamCodec;
import io.github.matheusanbs.wirelessautomate.storage.ChemicalStorage;
import io.github.matheusanbs.wirelessautomate.storage.FluidStorage;
import io.github.matheusanbs.wirelessautomate.storage.StorageKind;
import it.unimi.dsi.fastutil.Hash;
import java.util.function.UnaryOperator;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackLinkedSet;
import net.minecraftforge.fluids.FluidStack;

/**
 * Como a tela em lista trata a chave de cada armazenamento por tipo: como ela viaja na rede, como
 * se compara e como se copia. Itens no Baú, fluidos no Tanque, ids de químico no Tanque Químico.
 */
public final class ListKind<K> {
    public static final ListKind<ItemStack> ITEMS = new ListKind<>(StorageKind.CHEST, GameCodecs.EXACT_ITEM_STACK,
            ItemStackLinkedSet.TYPE_AND_TAG, ItemStack::copy);
    public static final ListKind<FluidStack> FLUIDS = new ListKind<>(StorageKind.TANK, GameCodecs.FLUID_STACK,
            FluidStorage.FLUID_AND_COMPONENTS, FluidStack::copy);
    public static final ListKind<ResourceLocation> CHEMICALS = new ListKind<>(StorageKind.CHEMICAL_TANK,
            GameCodecs.RESOURCE_LOCATION, ChemicalStorage.IDS, id -> id);

    public final StorageKind storage;
    public final StreamCodec<? super RegistryFriendlyByteBuf, K> codec;
    public final Hash.Strategy<? super K> strategy;
    private final UnaryOperator<K> copy;

    private ListKind(StorageKind storage, StreamCodec<? super RegistryFriendlyByteBuf, K> codec,
            Hash.Strategy<? super K> strategy, UnaryOperator<K> copy) {
        this.storage = storage;
        this.codec = codec;
        this.strategy = strategy;
        this.copy = copy;
    }

    public K copy(K key) {
        return copy.apply(key);
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
