package io.github.matheusanbs.wirelessautomate.recipe.edit;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Conversão entre o JSON padrão de uma receita de bancada e o {@link RecipeDraft}. Sem estado. */
public final class RecipeJson {
    public static final String SHAPED = "minecraft:crafting_shaped";
    public static final String SHAPELESS = "minecraft:crafting_shapeless";
    /** O anel de subir tier do mod: {@code tier}, {@code pattern} 3×3 com o centro vazio, {@code key} e {@code step}. */
    public static final String TIER_UPGRADE = "wirelessautomate:tier_upgrade";
    public static final String CONDITIONS = "neoforge:conditions";
    public static final String FALSE_CONDITION = "neoforge:false";

    private RecipeJson() {
    }

    /** Vazio quando a receita não é editável (outro tipo, ingrediente com alternativas, count fora da faixa...). */
    public static Optional<RecipeDraft> toDraft(JsonObject json) {
        try {
            String type = string(json, "type");
            if (type == null) {
                return Optional.empty();
            }
            RecipeDraft draft;
            if (type.equals(TIER_UPGRADE)) {
                // sem result: o cartão sai do tier
                Optional<List<String>> pattern = pattern(json);
                Optional<Map<Character, RecipeSlot>> key = key(json);
                if (pattern.isEmpty() || key.isEmpty() || string(json, "tier") == null) {
                    return Optional.empty();
                }
                draft = RecipeDraft.fromRing(pattern.get(), key.get());
                return Optional.of(draft.withDisabled(hasFalseCondition(json)));
            }
            int count = 1;
            JsonElement result = json.get("result");
            if (result == null || !result.isJsonObject()) {
                return Optional.empty();
            }
            JsonElement countEl = result.getAsJsonObject().get("count");
            if (countEl != null) {
                count = countEl.getAsInt();
            }
            if (type.equals(SHAPED)) {
                Optional<List<String>> pattern = pattern(json);
                Optional<Map<Character, RecipeSlot>> key = key(json);
                if (pattern.isEmpty() || key.isEmpty()) {
                    return Optional.empty();
                }
                draft = RecipeDraft.fromShaped(pattern.get(), key.get(), count);
            } else if (type.equals(SHAPELESS)) {
                JsonElement ingEl = json.get("ingredients");
                if (ingEl == null || !ingEl.isJsonArray()) {
                    return Optional.empty();
                }
                List<RecipeSlot> slots = new ArrayList<>();
                for (JsonElement el : ingEl.getAsJsonArray()) {
                    Optional<RecipeSlot> slot = slot(el);
                    if (slot.isEmpty()) {
                        return Optional.empty();
                    }
                    slots.add(slot.get());
                }
                draft = RecipeDraft.fromShapeless(slots, count);
            } else {
                return Optional.empty();
            }
            return Optional.of(draft.withDisabled(hasFalseCondition(json)));
        } catch (RuntimeException e) {
            // IllegalArgumentException do rascunho, ClassCast/IllegalState/NumberFormat do Gson: não editável.
            return Optional.empty();
        }
    }

    private static Optional<List<String>> pattern(JsonObject json) {
        JsonElement patternEl = json.get("pattern");
        if (patternEl == null || !patternEl.isJsonArray()) {
            return Optional.empty();
        }
        List<String> pattern = new ArrayList<>();
        for (JsonElement line : patternEl.getAsJsonArray()) {
            pattern.add(line.getAsString());
        }
        return Optional.of(pattern);
    }

    private static Optional<Map<Character, RecipeSlot>> key(JsonObject json) {
        JsonElement keyEl = json.get("key");
        if (keyEl == null || !keyEl.isJsonObject()) {
            return Optional.empty();
        }
        Map<Character, RecipeSlot> key = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> e : keyEl.getAsJsonObject().entrySet()) {
            if (e.getKey().length() != 1) {
                return Optional.empty();
            }
            Optional<RecipeSlot> slot = slot(e.getValue());
            if (slot.isEmpty()) {
                return Optional.empty();
            }
            key.put(e.getKey().charAt(0), slot.get());
        }
        return Optional.of(key);
    }

    /**
     * O JSON do override: o padrão com grade, quantidade e condições do rascunho. No anel, o resto do padrão
     * ({@code type}, {@code category}, {@code tier}, {@code step}) fica como está e não há {@code result}.
     */
    public static JsonObject toOverride(JsonObject defaultJson, RecipeDraft draft) {
        JsonObject out = defaultJson.deepCopy();
        out.remove("pattern");
        out.remove("key");
        out.remove("ingredients");
        if (draft.shape() != RecipeDraft.Shape.SHAPELESS) {
            RecipeDraft.ShapedLayout layout = draft.layout();
            JsonArray pattern = new JsonArray();
            layout.pattern().forEach(pattern::add);
            JsonObject key = new JsonObject();
            for (Map.Entry<Character, RecipeSlot> e : layout.key().entrySet()) {
                key.add(String.valueOf(e.getKey()), ingredient(e.getValue()));
            }
            out.add("pattern", pattern);
            out.add("key", key);
        } else {
            JsonArray ingredients = new JsonArray();
            for (RecipeSlot s : draft.ingredients()) {
                ingredients.add(ingredient(s));
            }
            out.add("ingredients", ingredients);
        }
        if (draft.shape() != RecipeDraft.Shape.RING) {
            JsonElement resultEl = out.get("result");
            JsonObject result = resultEl != null && resultEl.isJsonObject() ? resultEl.getAsJsonObject() : new JsonObject();
            result.addProperty("count", draft.count());
            out.add("result", result);
        }

        JsonArray conditions = new JsonArray();
        JsonElement old = out.get(CONDITIONS);
        if (old != null && old.isJsonArray()) {
            old.getAsJsonArray().forEach(conditions::add);
        }
        if (draft.disabled() && !hasFalseCondition(out)) {
            JsonObject falseCondition = new JsonObject();
            falseCondition.addProperty("type", FALSE_CONDITION);
            conditions.add(falseCondition);
        }
        if (conditions.isEmpty()) {
            out.remove(CONDITIONS);
        } else {
            out.add(CONDITIONS, conditions);
        }
        return out;
    }

    /** O id do item do resultado ({@code result.id}; no anel, o Cartão de Upgrade do {@code tier}), se houver. */
    public static Optional<String> resultId(JsonObject json) {
        if (TIER_UPGRADE.equals(string(json, "type"))) {
            String tier = string(json, "tier");
            return tier == null ? Optional.empty() : Optional.of("wirelessautomate:tier_core_" + tier);
        }
        JsonElement result = json.get("result");
        if (result == null || !result.isJsonObject()) {
            return Optional.empty();
        }
        return Optional.ofNullable(string(result.getAsJsonObject(), "id"));
    }

    private static boolean hasFalseCondition(JsonObject json) {
        JsonElement conditions = json.get(CONDITIONS);
        if (conditions == null || !conditions.isJsonArray()) {
            return false;
        }
        for (JsonElement c : conditions.getAsJsonArray()) {
            if (c.isJsonObject() && FALSE_CONDITION.equals(string(c.getAsJsonObject(), "type"))) {
                return true;
            }
        }
        return false;
    }

    private static Optional<RecipeSlot> slot(JsonElement el) {
        if (!el.isJsonObject()) {
            return Optional.empty();
        }
        JsonObject o = el.getAsJsonObject();
        if (o.has("type") || o.size() != 1) {
            return Optional.empty();
        }
        String item = string(o, "item");
        if (item != null) {
            return Optional.of(RecipeSlot.item(item));
        }
        String tag = string(o, "tag");
        return tag != null ? Optional.of(RecipeSlot.tag(tag)) : Optional.empty();
    }

    private static JsonObject ingredient(RecipeSlot slot) {
        JsonObject o = new JsonObject();
        o.addProperty(slot.kind() == RecipeSlot.Kind.TAG ? "tag" : "item", slot.id());
        return o;
    }

    private static String string(JsonObject o, String name) {
        JsonElement el = o.get(name);
        if (el instanceof JsonPrimitive p && p.isString()) {
            return p.getAsString();
        }
        return null;
    }
}
