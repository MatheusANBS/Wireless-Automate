package io.github.matheusanbs.wirelessautomate.storage;

import io.github.matheusanbs.wirelessautomate.storage.upgrade.ChestCompacting;
import java.util.function.LongSupplier;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackLinkedSet;
import org.jetbrains.annotations.Nullable;

/**
 * Conteúdo do Baú: itens (item e componentes) com quantidade {@code long}. As regras estão no
 * {@link KeyedStorage}; aqui só como comparar (item e componentes), copiar (pilha de 1) e salvar
 * a chave. É também a {@link BulkItems} que o roteador usa.
 *
 * <p>Com o Upgrade de Compactação, o {@link ChestCompacting} é o {@link InsertListener}: cada inserção de fora
 * avisa a cadeia do item, e o limite de estoque conta a cadeia ({@link #stockCount}). Sem ele, o campo é nulo e a
 * inserção paga só esse teste.
 */
public final class ItemStorage extends KeyedStorage<ItemStack> implements BulkItems {
    /** Quem quer saber de cada inserção de fora que guardou algo. */
    public interface InsertListener {
        /** @param key a chave guardada (pilha de 1, não alterar) */
        void inserted(ItemStack key);
    }

    private @Nullable InsertListener listener;

    public ItemStorage(Runnable onChange, LongSupplier capacity) {
        super(ItemStackLinkedSet.TYPE_AND_TAG, onChange, capacity, "Baú");
    }

    @Override
    protected boolean isEmptyKey(ItemStack key) {
        return key.isEmpty();
    }

    @Override
    protected ItemStack normalize(ItemStack key) {
        return key.copyWithCount(1);
    }

    @Override
    protected Tag saveKey(ItemStack key, HolderLookup.Provider registries) {
        return key.save(registries);
    }

    @Override
    protected @Nullable ItemStack loadKey(Tag tag, HolderLookup.Provider registries) {
        return ItemStack.parse(registries, tag).orElse(null);
    }

    @Override
    protected ItemStack withAmount(ItemStack key, int amount) {
        return key.copyWithCount(amount);
    }

    @Override
    protected int amountOf(ItemStack view) {
        return view.getCount();
    }

    /** O grupo do estoque sem componentes: o item. */
    @Override
    protected Object groupOf(ItemStack key) {
        return key.getItem();
    }

    @Override
    public long countItem(ItemStack key) {
        return countGroup(key);
    }

    public void setInsertListener(@Nullable InsertListener listener) {
        this.listener = listener;
    }

    @Override
    protected void inserted(ItemStack key) {
        if (listener != null) {
            listener.inserted(key);
        }
    }

    /**
     * Com a Compactação convertendo a cadeia da chave, conta a cadeia inteira na unidade da chave (11 blocos e 1
     * lingote valem 100 lingotes), mais, sem {@code components}, as variantes do item com componentes, que não
     * compactam. Sem isso, "manter 1000 lingotes" nunca seria atingido: os lingotes viram blocos.
     */
    @Override
    public long stockCount(ItemStack key, boolean components) {
        if (listener instanceof ChestCompacting compacting) {
            long chain = compacting.stockCount(key);
            if (chain >= 0) {
                return components ? chain : chain + countItem(key) - count(key);
            }
        }
        return components ? count(key) : countItem(key);
    }
}
