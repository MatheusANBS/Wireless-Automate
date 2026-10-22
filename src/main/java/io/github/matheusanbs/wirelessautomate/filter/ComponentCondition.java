package io.github.matheusanbs.wirelessautomate.filter;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import java.util.Locale;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * A condição de componente de dados de uma {@link ItemRule}, valendo para o componente de qualquer mod sem
 * depender dele: o componente é achado pelo id no registro e o valor é comparado pela forma serializada
 * (o tier de uma gema do Apotheosis é {@code apotheosis:purity=perfect}). Um componente que não existe
 * (mod fora do jogo) nunca casa. Lembra o último valor visto (por referência), então uma pilha igual à
 * anterior não serializa de novo.
 */
public final class ComponentCondition {
    private final @Nullable DataComponentType<?> type;
    private final @Nullable String wanted;

    private @Nullable Object lastValue;
    private boolean lastResult;

    private ComponentCondition(@Nullable DataComponentType<?> type, @Nullable String wanted) {
        this.type = type;
        this.wanted = wanted == null ? null : wanted.toLowerCase(Locale.ROOT);
    }

    static ComponentCondition of(@Nullable ResourceLocation id, @Nullable String value) {
        DataComponentType<?> type = id == null ? null : BuiltInRegistries.DATA_COMPONENT_TYPE.get(id);
        return new ComponentCondition(type, value);
    }

    boolean test(ItemStack stack) {
        if (type == null) {
            return false;
        }
        Object value = stack.get(type);
        if (value == null) {
            return false;
        }
        if (wanted == null) {
            return true;
        }
        synchronized (this) {
            if (value != lastValue) {
                lastResult = matches(value);
                lastValue = value;
            }
            return lastResult;
        }
    }

    private boolean matches(Object value) {
        String text = valueText(type, value);
        return text != null && (text.equals(wanted) || text.endsWith(":" + wanted));
    }

    /** A forma serializada (em minúsculas) do valor de um componente, a que a regra compara; {@code null} se não serializa. */
    @SuppressWarnings("unchecked")
    public static @Nullable String valueText(DataComponentType<?> type, Object value) {
        Codec<Object> codec = (Codec<Object>) type.codec();
        if (codec == null) {
            return null;
        }
        JsonElement json = codec.encodeStart(JsonOps.INSTANCE, value).result().orElse(null);
        if (json == null) {
            return null;
        }
        return (json instanceof JsonPrimitive p ? p.getAsString() : json.toString()).toLowerCase(Locale.ROOT);
    }
}
