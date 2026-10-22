package io.github.matheusanbs.wirelessautomate.client.filter;

import static io.github.matheusanbs.wirelessautomate.client.filter.FilterText.tr;

import io.github.matheusanbs.wirelessautomate.client.filter.TextSuggest.Suggestion;
import io.github.matheusanbs.wirelessautomate.filter.ComponentCondition;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
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
    private final Map<String, List<String>> values = new HashMap<>();

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

    /** "Componente": antes do {@code =} os ids (os do inventário primeiro); depois, os valores do componente. */
    List<Suggestion> component(String text) {
        int eq = text.indexOf('=');
        List<Suggestion> out = new ArrayList<>();
        if (eq < 0) {
            Set<String> inventory = inventoryComponents();
            List<String> ids = new ArrayList<>();
            for (DataComponentType<?> type : componentTypes()) {
                ids.add(String.valueOf(BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(type)));
            }
            List<String> found = EnchantSearch.search(ids, s -> s, s -> s, text);
            List<String> ordered = new ArrayList<>();
            found.stream().filter(inventory::contains).forEach(ordered::add);
            found.stream().filter(id -> !inventory.contains(id)).forEach(ordered::add);
            for (String id : cap(ordered)) {
                out.add(new Suggestion(id + "=", id, inventory.contains(id) ? tr("rule.suggest.inventory").getString()
                        : modName(id.substring(0, Math.max(0, id.indexOf(':')))), true));
            }
            return out;
        }
        String id = text.substring(0, eq).strip();
        for (String value : cap(EnchantSearch.search(valuesOf(id), s -> s, s -> s, text.substring(eq + 1).strip()))) {
            out.add(new Suggestion(id + "=" + value, value, null, false));
        }
        return out;
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

    private List<String> valuesOf(String id) {
        return values.computeIfAbsent(id, key -> {
            ResourceLocation rl = ResourceLocation.tryParse(key);
            DataComponentType<?> type = rl == null ? null : BuiltInRegistries.DATA_COMPONENT_TYPE.get(rl);
            if (type == null) {
                return List.of();
            }
            Set<String> found = new LinkedHashSet<>();
            List<ItemStack> all = new ArrayList<>(inventoryStacks());
            all.addAll(creative());
            for (ItemStack stack : all) {
                Object value = stack.get(type);
                if (value != null) {
                    String text = ComponentCondition.valueText(type, value);
                    if (text != null && text.length() <= 64) {
                        found.add(text);
                    }
                }
                if (found.size() >= 300) {
                    break;
                }
            }
            return new ArrayList<>(found);
        });
    }
}
