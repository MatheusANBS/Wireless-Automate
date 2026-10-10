package io.github.matheusanbs.wirelessautomate.recipe.edit;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.menu.RecipeEditorMenu;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;

/**
 * Serviço do servidor do editor de receitas: lista as receitas editáveis do mod, grava e apaga os overrides
 * no pack global e recarrega os recursos só por ação explícita. Métodos estáticos, na thread do servidor.
 */
public final class RecipeEditor {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final AtomicInteger PENDING = new AtomicInteger();

    public enum State { DEFAULT, EDITED, DISABLED }

    public record Entry(ResourceLocation id, ResourceLocation resultItem, RecipeDraft defaults, RecipeDraft current,
            State state, boolean divergent) {
    }

    /** O padrão (do jar) de uma receita editável. */
    private record Defaults(JsonObject json, RecipeDraft draft, ResourceLocation resultItem) {
    }

    private RecipeEditor() {
    }

    public static int pending() {
        return PENDING.get();
    }

    public static void reset() {
        PENDING.set(0);
    }

    /**
     * Recarrega os recursos com os packs selecionados. Só o sucesso zera as pendências (o fim de toda recarga,
     * inclusive a do {@code /reload}, também zera, por {@link #onDatapackSync}); a falha vai ao log.
     */
    public static CompletableFuture<Void> reload(MinecraftServer server) {
        return server.reloadResources(server.getPackRepository().getSelectedIds()).whenComplete((ignored, error) -> {
            if (error != null) {
                WirelessAutomate.LOGGER.error("A recarga das receitas falhou", error);
            } else {
                PENDING.set(0);
            }
        });
    }

    /**
     * Fim de uma recarga do servidor (o evento vem sem jogador uma vez por recarga, pelo botão ou pelo
     * {@code /reload}): zera as pendências e manda o estado novo aos editores abertos.
     */
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        if (event.getPlayer() != null) {
            return;
        }
        PENDING.set(0);
        RecipeEditorMenu.broadcast(event.getPlayerList().getServer(), false);
    }

    // ---------------------------------------------------------------- leitura

    public static List<Entry> entries(MinecraftServer server) {
        Set<ResourceLocation> ids = new LinkedHashSet<>();
        for (RecipeHolder<?> holder : server.getRecipeManager().getRecipes()) {
            if (holder.id().getNamespace().equals(WirelessAutomate.MODID) && isCraftingSerializer(holder.value())) {
                ids.add(holder.id());
            }
        }
        ids.addAll(overridesOnDisk());
        List<Entry> out = new ArrayList<>();
        for (ResourceLocation id : ids) {
            entry(server, id).ifPresent(out::add);
        }
        out.sort(Comparator.comparing(e -> e.id().getPath()));
        return out;
    }

    private static Optional<Entry> entry(MinecraftServer server, ResourceLocation id) {
        Optional<Defaults> defaults = readDefaults(server, id);
        if (defaults.isEmpty()) {
            return Optional.empty();
        }
        Optional<RecipeDraft> override = readOverride(id);
        RecipeDraft current = override.orElse(defaults.get().draft());
        State state = override.isEmpty() ? State.DEFAULT : (current.disabled() ? State.DISABLED : State.EDITED);
        boolean divergent = override.isPresent() && diverges(server, id, defaults.get(), current);
        return Optional.of(new Entry(id, defaults.get().resultItem(), defaults.get().draft(), current, state, divergent));
    }

    private static boolean isCraftingSerializer(Recipe<?> recipe) {
        return recipe.getSerializer() == RecipeSerializer.SHAPED_RECIPE
                || recipe.getSerializer() == RecipeSerializer.SHAPELESS_RECIPE;
    }

    private static List<ResourceLocation> overridesOnDisk() {
        Path dir = RecipeOverridePack.root().resolve("data").resolve(WirelessAutomate.MODID).resolve("recipe");
        List<ResourceLocation> out = new ArrayList<>();
        if (!Files.isDirectory(dir)) {
            return out;
        }
        try (Stream<Path> files = Files.list(dir)) {
            files.forEach(p -> {
                String name = p.getFileName().toString();
                if (name.endsWith(".json") && Files.isRegularFile(p)) {
                    ResourceLocation id = ResourceLocation.tryBuild(WirelessAutomate.MODID,
                            name.substring(0, name.length() - ".json".length()));
                    if (id != null) {
                        out.add(id);
                    }
                }
            });
        } catch (IOException e) {
            WirelessAutomate.LOGGER.warn("Não foi possível listar {}", dir, e);
        }
        return out;
    }

    /**
     * Id do pack do jar do mod: o NeoForge ({@code ResourcePackLoader}) dá a cada jar de mod o pack
     * {@code mod/<ids do jar>}, filho do {@code mod_data} e expandido na pilha com esse id próprio.
     */
    private static final String MOD_PACK_ID = "mod/" + WirelessAutomate.MODID;

    /** O JSON do próprio jar do mod (nunca o de outro datapack ou do pack de overrides), se for uma receita editável. */
    private static Optional<Defaults> readDefaults(MinecraftServer server, ResourceLocation id) {
        ResourceLocation file = ResourceLocation.fromNamespaceAndPath(id.getNamespace(), "recipe/" + id.getPath() + ".json");
        List<Resource> stack = server.getResourceManager().getResourceStack(file);
        for (int i = stack.size() - 1; i >= 0; i--) {
            Resource resource = stack.get(i);
            if (!MOD_PACK_ID.equals(resource.sourcePackId())) {
                continue;
            }
            try (Reader reader = resource.openAsReader()) {
                if (!(JsonParser.parseReader(reader) instanceof JsonObject json)) {
                    return Optional.empty();
                }
                Optional<RecipeDraft> draft = RecipeJson.toDraft(json);
                ResourceLocation result = RecipeJson.resultId(json).map(ResourceLocation::tryParse).orElse(null);
                if (draft.isEmpty() || result == null) {
                    return Optional.empty();
                }
                return Optional.of(new Defaults(json, draft.get(), result));
            } catch (IOException | RuntimeException e) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    private static Optional<RecipeDraft> readOverride(ResourceLocation id) {
        Path file = RecipeOverridePack.recipeFile(id);
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            if (JsonParser.parseReader(reader) instanceof JsonObject json) {
                return RecipeJson.toDraft(json);
            }
        } catch (IOException | RuntimeException e) {
            WirelessAutomate.LOGGER.warn("Override de receita ilegível: {}", file, e);
        }
        return Optional.empty();
    }

    // -------------------------------------------------------------- divergência

    private static boolean diverges(MinecraftServer server, ResourceLocation id, Defaults defaults, RecipeDraft current) {
        ResourceLocation resultItem = defaults.resultItem();
        Optional<RecipeHolder<?>> loaded = server.getRecipeManager().byKey(id);
        if (current.disabled()) {
            return loaded.isPresent();
        }
        if (loaded.isEmpty()) {
            // O override guarda as condições do padrão: se elas não passam (mod_loaded de um mod ausente), não carregar é o certo.
            return conditionsPass(server, defaults.json());
        }
        Recipe<?> recipe = loaded.get().value();
        ItemStack result = recipe.getResultItem(server.registryAccess());
        if (!BuiltInRegistries.ITEM.getKey(result.getItem()).equals(resultItem) || result.getCount() != current.count()) {
            return true;
        }
        List<Ingredient> ingredients = recipe.getIngredients().stream().filter(i -> !i.isEmpty()).toList();
        List<RecipeSlot> slots = current.ingredients();
        if (ingredients.size() != slots.size()) {
            return true;
        }
        for (RecipeSlot slot : slots) {
            Optional<Item> sample = sampleItem(slot);
            if (sample.isEmpty()) {
                return true;
            }
            ItemStack stack = new ItemStack(sample.get());
            if (ingredients.stream().noneMatch(i -> i.test(stack))) {
                return true;
            }
        }
        return false;
    }

    /** As condições do NeoForge no JSON, avaliadas no contexto da última recarga; sem condições ou ilegíveis, passa. */
    private static boolean conditionsPass(MinecraftServer server, JsonObject json) {
        if (!json.has(RecipeJson.CONDITIONS)) {
            return true;
        }
        try {
            ICondition.IContext context = server.getServerResources().managers().getConditionContext();
            List<ICondition> conditions = ICondition.LIST_CODEC
                    .parse(RegistryOps.create(JsonOps.INSTANCE, server.registryAccess()), json.get(RecipeJson.CONDITIONS))
                    .getOrThrow();
            return conditions.stream().allMatch(c -> c.test(context));
        } catch (RuntimeException e) {
            return true;
        }
    }

    /** O item do slot, ou o primeiro item da tag. */
    private static Optional<Item> sampleItem(RecipeSlot slot) {
        ResourceLocation id = ResourceLocation.tryParse(slot.id());
        if (id == null) {
            return Optional.empty();
        }
        if (slot.kind() == RecipeSlot.Kind.ITEM) {
            return BuiltInRegistries.ITEM.getOptional(id);
        }
        if (slot.kind() == RecipeSlot.Kind.TAG) {
            Optional<HolderSet.Named<Item>> tag = BuiltInRegistries.ITEM.getTag(TagKey.create(Registries.ITEM, id));
            return tag.flatMap(t -> t.stream().findFirst()).map(h -> h.value());
        }
        return Optional.empty();
    }

    // -------------------------------------------------------------------- ações

    public static Optional<Component> save(MinecraftServer server, ResourceLocation id, RecipeDraft draft) {
        Optional<Defaults> defaults = editable(server, id);
        if (defaults.isEmpty() || defaults.get().draft().shape() != draft.shape()) {
            return Optional.of(error("not_editable"));
        }
        Optional<Component> invalid = validate(draft);
        if (invalid.isEmpty()) {
            invalid = validateCount(defaults.get().resultItem(), draft.count());
        }
        if (invalid.isPresent()) {
            return invalid;
        }
        return write(id, RecipeJson.toOverride(defaults.get().json(), draft));
    }

    public static Optional<Component> setDisabled(MinecraftServer server, ResourceLocation id, boolean disabled) {
        Optional<Defaults> defaults = editable(server, id);
        if (defaults.isEmpty()) {
            return Optional.of(error("not_editable"));
        }
        Optional<RecipeDraft> override = readOverride(id);
        if (override.isEmpty() && !disabled) {
            return Optional.empty();
        }
        RecipeDraft base = override.orElse(defaults.get().draft());
        return write(id, RecipeJson.toOverride(defaults.get().json(), base.withDisabled(disabled)));
    }

    public static Optional<Component> restore(MinecraftServer server, ResourceLocation id) {
        if (editable(server, id).isEmpty()) {
            return Optional.of(error("not_editable"));
        }
        try {
            if (Files.deleteIfExists(RecipeOverridePack.recipeFile(id))) {
                PENDING.incrementAndGet();
            }
            return Optional.empty();
        } catch (IOException e) {
            return Optional.of(error("disk", String.valueOf(e.getMessage())));
        }
    }

    /** Editável: do mod, carregada como receita de bancada (ou com override em disco) e com padrão legível. */
    private static Optional<Defaults> editable(MinecraftServer server, ResourceLocation id) {
        if (!id.getNamespace().equals(WirelessAutomate.MODID)) {
            return Optional.empty();
        }
        try {
            RecipeOverridePack.recipeFile(id);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        Optional<RecipeHolder<?>> loaded = server.getRecipeManager().byKey(id);
        if (loaded.isPresent()) {
            if (!isCraftingSerializer(loaded.get().value())) {
                return Optional.empty();
            }
        } else if (!Files.isRegularFile(RecipeOverridePack.recipeFile(id))) {
            return Optional.empty();
        }
        return readDefaults(server, id);
    }

    private static Optional<Component> validate(RecipeDraft draft) {
        if (draft.isEmpty()) {
            return Optional.of(error("empty"));
        }
        for (RecipeSlot slot : draft.slots()) {
            if (slot.isEmpty()) {
                continue;
            }
            ResourceLocation id = ResourceLocation.tryParse(slot.id());
            if (slot.kind() == RecipeSlot.Kind.ITEM) {
                if (id == null || !BuiltInRegistries.ITEM.containsKey(id) || BuiltInRegistries.ITEM.get(id) == Items.AIR) {
                    return Optional.of(error("unknown_item", slot.id()));
                }
            } else if (id == null || BuiltInRegistries.ITEM.getTag(TagKey.create(Registries.ITEM, id))
                    .map(tag -> tag.size() == 0).orElse(true)) {
                // Uma tag que não existe ou vazia deixaria a receita sem ingrediente válido.
                return Optional.of(error("bad_tag", slot.id()));
            }
        }
        return Optional.empty();
    }

    /** Uma quantidade acima da pilha do resultado faz a receita falhar ao decodificar na recarga. */
    private static Optional<Component> validateCount(ResourceLocation resultItem, int count) {
        int max = BuiltInRegistries.ITEM.getOptional(resultItem).map(i -> new ItemStack(i).getMaxStackSize()).orElse(RecipeDraft.MAX_COUNT);
        return count > max ? Optional.of(error("bad_count", max)) : Optional.empty();
    }

    private static Optional<Component> write(ResourceLocation id, JsonObject json) {
        Path file = RecipeOverridePack.recipeFile(id);
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(json) + "\n", StandardCharsets.UTF_8);
            PENDING.incrementAndGet();
            return Optional.empty();
        } catch (IOException e) {
            return Optional.of(error("disk", String.valueOf(e.getMessage())));
        }
    }

    private static Component error(String key, Object... args) {
        return Component.translatable("gui.wirelessautomate.recipes.error." + key, args);
    }
}
