package io.github.matheusanbs.wirelessautomate.registry;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Um dado do mod guardado num item: a fachada do porte 1.20.1 para os componentes de item do 1.21
 * ({@code DataComponentType}), que o 1.20.1 não tem. Grava o valor pelo {@link Codec} com {@link NbtOps} no
 * tag do item ({@link ItemStack#getOrCreateTag}), sob a chave {@link #name} (o nome do componente no
 * {@code main}: {@code preset}, {@code storage_contents}...). O NBT do item já vai ao cliente, então não há
 * codec de rede.
 *
 * <p>Troca: {@code stack.get(ModDataComponents.X.get())} vira {@code ModDataComponents.X.get(stack)}, e o
 * mesmo para {@code getOrDefault}, {@code has}, {@code set} e {@code remove}.
 *
 * <p><b>Mesma instância enquanto o tag não muda:</b> como no 1.21, ler duas vezes o mesmo item sem gravar
 * nada no meio devolve o mesmo objeto (o código compara por {@code ==} para ver se mudou, como o
 * {@code CardFilterTarget}). Para isso cada {@code ItemData} guarda o valor lido por identidade do tag
 * (chaves fracas). Os valores precisam ser imutáveis, como os componentes do 1.21.
 */
public final class ItemData<T> {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final String name;
    private final Codec<T> codec;
    /** Valor lido por tag (identidade, chaves fracas): o mesmo tag devolve o mesmo valor sem decodificar. */
    private final Cache<Tag, Object> decoded = CacheBuilder.newBuilder().weakKeys().maximumSize(4096).build();

    public ItemData(String name, Codec<T> codec) {
        this.name = name;
        this.codec = codec;
    }

    /** A chave no tag do item. */
    public String name() {
        return name;
    }

    public Codec<T> codec() {
        return codec;
    }

    /** O valor gravado, ou {@code null} se não há (ou se o NBT não decodifica; registra um aviso). */
    @SuppressWarnings("unchecked")
    public @Nullable T get(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        CompoundTag root = stack.getTag();
        Tag tag = root == null ? null : root.get(name);
        if (tag == null) {
            return null;
        }
        Object cached = decoded.getIfPresent(tag);
        if (cached != null) {
            return (T) cached;
        }
        T value = codec.parse(NbtOps.INSTANCE, tag)
                .resultOrPartial(error -> LOGGER.warn("Dado {} inválido no item {}: {}", name, stack, error))
                .orElse(null);
        if (value != null) {
            decoded.put(tag, value);
        }
        return value;
    }

    public T getOrDefault(ItemStack stack, T defaultValue) {
        T value = get(stack);
        return value != null ? value : defaultValue;
    }

    public boolean has(ItemStack stack) {
        CompoundTag root = stack.getTag();
        return !stack.isEmpty() && root != null && root.contains(name);
    }

    /** Grava {@code value} (nulo = {@link #remove}). Num item vazio não faz nada. */
    public void set(ItemStack stack, @Nullable T value) {
        if (value == null) {
            remove(stack);
            return;
        }
        if (stack.isEmpty()) {
            return;
        }
        Tag tag = codec.encodeStart(NbtOps.INSTANCE, value)
                .resultOrPartial(error -> LOGGER.error("Não foi possível gravar o dado {} = {}: {}", name, value, error))
                .orElse(null);
        if (tag == null) {
            return;
        }
        stack.getOrCreateTag().put(name, tag);
        decoded.put(tag, value);
    }

    /** Apaga o dado; se o tag do item ficar vazio, some também (o item volta a empilhar com um novo). */
    public void remove(ItemStack stack) {
        CompoundTag root = stack.getTag();
        if (root == null || !root.contains(name)) {
            return;
        }
        root.remove(name);
        if (root.isEmpty()) {
            stack.setTag(null);
        }
    }

    @Override
    public String toString() {
        return "ItemData[" + name + "]";
    }
}
