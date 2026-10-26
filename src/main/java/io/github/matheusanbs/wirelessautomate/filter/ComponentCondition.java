package io.github.matheusanbs.wirelessautomate.filter;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * A condição de componente de dados de uma {@link ItemRule}, valendo para o componente de qualquer mod sem
 * depender dele: o componente é achado pelo id no registro, serializado pelo codec dele e a {@link ComponentQuery}
 * avalia a árvore (campo interno, operadores e várias condições com {@code ;}). Um componente que não existe
 * (mod fora do jogo) nunca casa. Cada condição lembra o último valor visto (por referência), então uma pilha igual
 * à anterior não serializa de novo.
 */
public final class ComponentCondition {
    private final Single[] all;

    private ComponentCondition(Single[] all) {
        this.all = all;
    }

    /** As condições de {@code text} ({@link ComponentQuery#parseAll}); mal escritas, nunca casa. */
    static ComponentCondition of(String text) {
        List<ComponentQuery> queries = ComponentQuery.parseAll(text);
        if (queries == null) {
            return new ComponentCondition(new Single[] {new Single(null, null)});
        }
        Single[] singles = new Single[queries.size()];
        for (int i = 0; i < singles.length; i++) {
            ComponentQuery query = queries.get(i);
            ResourceLocation id = ResourceLocation.tryParse(query.id());
            singles[i] = new Single(id == null ? null : BuiltInRegistries.DATA_COMPONENT_TYPE.get(id), query);
        }
        return new ComponentCondition(singles);
    }

    boolean test(ItemStack stack) {
        for (Single single : all) {
            if (!single.test(stack)) {
                return false;
            }
        }
        return true;
    }

    private static final class Single {
        private final @Nullable DataComponentType<?> type;
        private final @Nullable ComponentQuery query;
        private @Nullable Object lastValue;
        private boolean lastResult;

        private Single(@Nullable DataComponentType<?> type, @Nullable ComponentQuery query) {
            this.type = type;
            this.query = query;
        }

        boolean test(ItemStack stack) {
            if (type == null || query == null) {
                return false;
            }
            Object value = stack.get(type);
            if (value == null) {
                return false;
            }
            if (query.op() == ComponentQuery.Op.EXISTS && query.path().isEmpty()) {
                return true;
            }
            synchronized (this) {
                if (value != lastValue) {
                    Object tree = tree(type, value);
                    lastResult = tree != null && query.test(tree);
                    lastValue = value;
                }
                return lastResult;
            }
        }
    }

    /**
     * O valor de um componente serializado pelo codec dele numa árvore simples (mapas, listas, textos, números e
     * booleanos), a que a {@link ComponentQuery} lê; {@code null} se não serializa.
     */
    @SuppressWarnings("unchecked")
    public static @Nullable Object tree(DataComponentType<?> type, Object value) {
        Codec<Object> codec = (Codec<Object>) type.codec();
        if (codec == null) {
            return null;
        }
        JsonElement json = codec.encodeStart(JsonOps.INSTANCE, value).result().orElse(null);
        return json == null ? null : plain(json);
    }

    private static @Nullable Object plain(JsonElement json) {
        if (json instanceof JsonObject object) {
            Map<String, Object> map = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                Object child = plain(entry.getValue());
                if (child != null) {
                    map.put(entry.getKey(), child);
                }
            }
            return map;
        }
        if (json instanceof JsonArray array) {
            List<Object> list = new ArrayList<>(array.size());
            for (JsonElement element : array) {
                Object child = plain(element);
                if (child != null) {
                    list.add(child);
                }
            }
            return list;
        }
        if (json instanceof JsonPrimitive p) {
            if (p.isBoolean()) {
                return p.getAsBoolean();
            }
            return p.isNumber() ? (Object) p.getAsNumber() : p.getAsString();
        }
        return null;
    }
}
