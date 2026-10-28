package io.github.matheusanbs.wirelessautomate.client.filter;

import static io.github.matheusanbs.wirelessautomate.client.filter.FilterText.tr;

import io.github.matheusanbs.wirelessautomate.client.filter.TextSuggest.Suggestion;
import io.github.matheusanbs.wirelessautomate.filter.ComponentCondition;
import io.github.matheusanbs.wirelessautomate.filter.ComponentQuery;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

/**
 * O que as listas de sugestões do "Só em" ({@code #tag} e {@code @mod}) e do "Componente"
 * ({@code id=valor}) oferecem, tudo lido do jogo (registros e itens), sem nome de mod no código. Os
 * componentes que estão nos itens do inventário vêm primeiro; os valores de um componente vêm do inventário
 * e das abas do modo criativo (as gemas de todos os tiers, por exemplo). Uma instância por tela, com as
 * listas grandes lidas só quando alguém pede.
 */
final class RuleSuggestions {
    private static final int CAP = 150;

    private List<String> mods;
    private List<String> tags;
    private List<DataComponentType<?>> componentTypes;
    private List<ItemStack> creative;
    private List<ItemStack> catalog;
    private Map<String, Form> forms;
    private final Map<String, List<Object>> samples = new HashMap<>();

    /** "Só em": {@code #tag} ou {@code @mod}; sem prefixo, os dois. */
    List<Suggestion> scope(String text) {
        String t = text.strip();
        char prefix = t.startsWith("#") || t.startsWith("@") ? t.charAt(0) : 0;
        String query = prefix != 0 ? t.substring(1) : t;
        List<Suggestion> out = new ArrayList<>();
        if (prefix != '#') {
            for (String mod : cap(EnchantSearch.search(mods(), m -> m, m -> m, query))) {
                out.add(new Suggestion("@" + mod, "@" + mod, modName(mod), false));
            }
        }
        if (prefix != '@') {
            for (String tag : cap(EnchantSearch.search(tags(), s -> s, s -> s, query))) {
                out.add(new Suggestion("#" + tag, "#" + tag, tr("rule.suggest.tag").getString(), false));
            }
        }
        return out;
    }

    /** Como um componente aparece nas sugestões: o que escolher o id leva a digitar depois. */
    private enum Form { SCALAR, OBJECT, LIST }

    /**
     * "Componente" por etapas, pelo que já foi digitado: o id (os do inventário primeiro, com a forma do valor), depois
     * da seta o campo (com o tipo e a faixa vistos nos itens), depois o operador e por fim o valor (os vistos nos itens).
     * Tudo vem dos itens do jogo, sem nome de mod no código; o texto livre continua valendo.
     */
    List<Suggestion> component(String text) {
        ComponentQuery.Parts parts = ComponentQuery.parts(text);
        if (!parts.arrow() && parts.op().isEmpty()) {
            return componentIds(parts.id());
        }
        List<Object> trees = samples(parts.id());
        if (parts.op().isEmpty()) {
            return pathStage(parts.id(), parts.path(), trees);
        }
        return valueStage(parts, trees);
    }

    private List<Suggestion> componentIds(String text) {
        Set<String> inventory = inventoryComponents();
        Map<String, Form> forms = forms();
        List<String> ids = new ArrayList<>();
        for (DataComponentType<?> type : componentTypes()) {
            ids.add(String.valueOf(BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(type)));
        }
        List<String> found = EnchantSearch.search(ids, s -> s, s -> s, text);
        List<String> ordered = new ArrayList<>();
        found.stream().filter(inventory::contains).forEach(ordered::add);
        found.stream().filter(id -> !inventory.contains(id)).forEach(ordered::add);
        List<Suggestion> out = new ArrayList<>();
        for (String id : cap(ordered)) {
            // sem nenhum item com o componente, a forma é desconhecida: o id entra sem sufixo e quem digita escolhe
            Form form = forms.get(id);
            String where = inventory.contains(id) ? tr("rule.suggest.inventory").getString()
                    : modName(id.substring(0, Math.max(0, id.indexOf(':'))));
            String note = form == null ? where
                    : tr("rule.form." + form.name().toLowerCase(java.util.Locale.ROOT)).getString() + " · " + where;
            out.add(new Suggestion(form == null ? id : id + (form == Form.SCALAR ? "=" : "->"), id, note, form != null));
        }
        return out;
    }

    /** Depois da seta: os campos do componente; com o campo inteiro digitado, os operadores. */
    private List<Suggestion> pathStage(String id, String typed, List<Object> trees) {
        Set<String> paths = new LinkedHashSet<>();
        for (Object tree : trees) {
            paths.addAll(ComponentQuery.leafPaths(tree, 5, 60));
            if (paths.size() >= 120) {
                break;
            }
        }
        List<String> all = new ArrayList<>(paths);
        List<Suggestion> out = new ArrayList<>();
        if (all.contains(typed)) {
            boolean numeric = !leaf(trees, typed, 60).isEmpty() && leaf(trees, typed, 60).stream().allMatch(RuleSuggestions::isNumber);
            for (ComponentQuery.Op op : ComponentQuery.Op.values()) {
                if (op != ComponentQuery.Op.EXISTS && (numeric ? op != ComponentQuery.Op.CONTAINS : true)) {
                    out.add(new Suggestion(id + "->" + typed + op.token(), op.symbol() + "  "
                            + tr("rule.op." + op.name().toLowerCase(java.util.Locale.ROOT)).getString(), null, true));
                }
            }
            return out;
        }
        for (String path : cap(EnchantSearch.search(all, s -> s, s -> s, typed))) {
            out.add(new Suggestion(id + "->" + path, path, pathNote(trees, path), true));
        }
        return out;
    }

    /** Depois do operador: os valores vistos nos itens, do mais comum ao menos. */
    private List<Suggestion> valueStage(ComponentQuery.Parts parts, List<Object> trees) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Object tree : trees) {
            for (String value : ComponentQuery.leafValues(tree, pathOf(parts.path()), 20)) {
                counts.merge(value, 1, Integer::sum);
            }
        }
        List<String> values = new ArrayList<>(counts.keySet());
        values.sort((a, b) -> counts.get(b) - counts.get(a));
        String base = parts.id() + (parts.arrow() ? "->" + parts.path() : "") + parts.op();
        List<Suggestion> out = new ArrayList<>();
        for (String value : cap(EnchantSearch.search(values, s -> s, s -> s, parts.value()))) {
            int n = counts.get(value);
            out.add(new Suggestion(base + value, value, tr(n == 1 ? "rule.suggest.item" : "rule.suggest.items", n).getString(),
                    false));
        }
        return out;
    }

    private static List<String> pathOf(String path) {
        return path.isEmpty() ? List.of() : List.of(path.split("\\."));
    }

    private static List<String> leaf(List<Object> trees, String path, int cap) {
        Set<String> values = new LinkedHashSet<>();
        for (Object tree : trees) {
            values.addAll(ComponentQuery.leafValues(tree, pathOf(path), cap));
            if (values.size() >= cap) {
                break;
            }
        }
        return new ArrayList<>(values);
    }

    private static boolean isNumber(String text) {
        try {
            return Double.isFinite(Double.parseDouble(text));
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /** "number · 15 to 100" ou "text · high, low": o tipo e a faixa que os itens mostram no campo. */
    private static String pathNote(List<Object> trees, String path) {
        List<String> values = leaf(trees, path, 40);
        if (values.isEmpty()) {
            return "";
        }
        if (values.stream().allMatch(RuleSuggestions::isNumber)) {
            double min = Double.MAX_VALUE;
            double max = -Double.MAX_VALUE;
            for (String value : values) {
                double d = Double.parseDouble(value);
                min = Math.min(min, d);
                max = Math.max(max, d);
            }
            return tr("rule.suggest.number", ComponentQuery.text(min), ComponentQuery.text(max)).getString();
        }
        return tr("rule.suggest.text", String.join(", ", values.subList(0, Math.min(3, values.size())))).getString();
    }

    /** Captura: amostras de exemplo de um componente, no lugar das lidas dos itens (a captura não tem mundo). */
    void previewSamples(String id, List<Object> trees, boolean structured) {
        samples.put(id, trees);
        forms().put(id, structured ? Form.OBJECT : Form.SCALAR);
    }

    /** O que a forma de cada componente do inventário e das abas do criativo é (lido uma vez). */
    private Map<String, Form> forms() {
        if (forms == null) {
            Map<String, Form> map = new HashMap<>();
            List<ItemStack> all = sources();
            int budget = 20000;
            for (ItemStack stack : all) {
                if (budget-- <= 0) {
                    break;
                }
                for (var typed : stack.getComponents()) {
                    String id = String.valueOf(BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(typed.type()));
                    Form form = formOf(typed.value());
                    map.merge(id, form, (a, b) -> a == Form.SCALAR ? b : a);
                }
            }
            forms = map;
        }
        return forms;
    }

    private static Form formOf(Object value) {
        if (value instanceof CharSequence || value instanceof Number || value instanceof Boolean || value instanceof Enum<?>
                || value instanceof ResourceLocation || value instanceof net.minecraft.resources.ResourceKey<?>) {
            return Form.SCALAR;
        }
        return value instanceof java.util.Collection<?> ? Form.LIST : Form.OBJECT;
    }

    /** Os valores serializados do componente {@code id} nos itens do inventário e das abas do criativo (até 400). */
    private List<Object> samples(String id) {
        return samples.computeIfAbsent(id, key -> {
            ResourceLocation rl = ResourceLocation.tryParse(key);
            DataComponentType<?> type = rl == null ? null : BuiltInRegistries.DATA_COMPONENT_TYPE.get(rl);
            if (type == null) {
                return List.of();
            }
            List<Object> out = new ArrayList<>();
            for (ItemStack stack : sources()) {
                Object value = stack.get(type);
                Object tree = value == null ? null : ComponentCondition.tree(type, value);
                if (tree != null) {
                    out.add(tree);
                }
                if (out.size() >= 400) {
                    break;
                }
            }
            return out;
        });
    }

    private static <T> List<T> cap(List<T> list) {
        return list.size() > CAP ? list.subList(0, CAP) : list;
    }

    private static String modName(String namespace) {
        return ModList.get().getModContainerById(namespace).map(c -> c.getModInfo().getDisplayName()).orElse(namespace);
    }

    private List<String> mods() {
        if (mods == null) {
            Set<String> set = new TreeSet<>();
            for (ResourceLocation id : BuiltInRegistries.ITEM.keySet()) {
                set.add(id.getNamespace());
            }
            mods = new ArrayList<>(set);
        }
        return mods;
    }

    private List<String> tags() {
        if (tags == null) {
            Set<String> set = new TreeSet<>();
            BuiltInRegistries.ITEM.getTagNames().forEach(tag -> set.add(tag.location().toString()));
            tags = new ArrayList<>(set);
        }
        return tags;
    }

    private List<DataComponentType<?>> componentTypes() {
        if (componentTypes == null) {
            List<DataComponentType<?>> list = new ArrayList<>();
            for (DataComponentType<?> type : BuiltInRegistries.DATA_COMPONENT_TYPE) {
                if (type.codec() != null) {
                    list.add(type);
                }
            }
            componentTypes = list;
        }
        return componentTypes;
    }

    private static List<ItemStack> inventoryStacks() {
        Player player = Minecraft.getInstance().player;
        List<ItemStack> list = new ArrayList<>();
        if (player != null) {
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack stack = player.getInventory().getItem(i);
                if (!stack.isEmpty()) {
                    list.add(stack);
                }
            }
        }
        return list;
    }

    private Set<String> inventoryComponents() {
        Set<String> ids = new HashSet<>();
        for (ItemStack stack : inventoryStacks()) {
            for (var typed : stack.getComponents()) {
                ids.add(String.valueOf(BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(typed.type())));
            }
        }
        return ids;
    }

    /** De onde vêm as amostras, sem o jogador precisar ter o item: inventário, abas do criativo e o catálogo. */
    private List<ItemStack> sources() {
        List<ItemStack> all = new ArrayList<>(inventoryStacks());
        all.addAll(creative());
        all.addAll(catalog());
        return all;
    }

    /**
     * Os itens que o jogo e o JEI (quando instalado) conhecem: o item padrão de todo registro e, do JEI, as
     * pilhas com dados (variações que só ele lista). Lido uma vez.
     */
    private List<ItemStack> catalog() {
        if (catalog == null) {
            List<ItemStack> list = new ArrayList<>();
            for (var item : BuiltInRegistries.ITEM) {
                ItemStack stack = item.getDefaultInstance();
                if (!stack.isEmpty() && !stack.getComponents().isEmpty()) {
                    list.add(stack);
                }
            }
            try {
                list.addAll(extraStacks.get());
            } catch (RuntimeException ignored) {
                // sem o catálogo do JEI, ficam os itens do registro
            }
            catalog = list;
        }
        return catalog;
    }

    /** As pilhas que outra fonte oferece (o plugin do JEI a troca quando o JEI liga); sem tipos do JEI aqui. */
    static volatile java.util.function.Supplier<List<ItemStack>> extraStacks = List::of;

    /** As pilhas das abas do modo criativo, lidas uma vez; vazia se o jogo não as monta. */
    private List<ItemStack> creative() {
        if (creative == null) {
            List<ItemStack> list = new ArrayList<>();
            try {
                Minecraft mc = Minecraft.getInstance();
                ClientPacketListener connection = mc.getConnection();
                if (mc.player != null && mc.level != null && connection != null) {
                    CreativeModeTabs.tryRebuildTabContents(connection.enabledFeatures(), mc.player.canUseGameMasterBlocks(),
                            mc.level.registryAccess());
                    for (CreativeModeTab tab : CreativeModeTabs.allTabs()) {
                        if (tab.getType() == CreativeModeTab.Type.CATEGORY) {
                            list.addAll(tab.getDisplayItems());
                        }
                    }
                }
            } catch (RuntimeException ignored) {
                // sem as abas, só o inventário alimenta os valores
            }
            creative = list;
        }
        return creative;
    }
}
