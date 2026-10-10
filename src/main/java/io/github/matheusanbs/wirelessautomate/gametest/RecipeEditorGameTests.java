package io.github.matheusanbs.wirelessautomate.gametest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.recipe.edit.RecipeDraft;
import io.github.matheusanbs.wirelessautomate.recipe.edit.RecipeEditor;
import io.github.matheusanbs.wirelessautomate.recipe.edit.RecipeJson;
import io.github.matheusanbs.wirelessautomate.recipe.edit.RecipeOverridePack;
import io.github.matheusanbs.wirelessautomate.recipe.edit.RecipeSlot;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import io.github.matheusanbs.wirelessautomate.menu.RecipeEditorMenu;
import io.github.matheusanbs.wirelessautomate.menu.RecipeEditorSnapshot;
import io.github.matheusanbs.wirelessautomate.packet.RecipeEditorActionPayload;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.mojang.authlib.GameProfile;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Serviço do editor de receitas ({@link RecipeEditor}) e o pack global. Cada teste usa uma receita própria e
 * apaga o arquivo que criar; nenhum recarrega os recursos (os testes rodam em paralelo).
 */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class RecipeEditorGameTests {
    private static ResourceLocation id(String path) {
        return WirelessAutomate.id(path);
    }

    private static Optional<RecipeEditor.Entry> entry(MinecraftServer server, ResourceLocation id) {
        return RecipeEditor.entries(server).stream().filter(e -> e.id().equals(id)).findFirst();
    }

    private static RecipeEditor.Entry require(MinecraftServer server, ResourceLocation id) {
        return entry(server, id).orElseThrow(() -> new GameTestAssertException("sem entrada para " + id));
    }

    private static JsonObject readFile(ResourceLocation id) throws IOException {
        try (Reader reader = Files.newBufferedReader(RecipeOverridePack.recipeFile(id), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private static void clean(ResourceLocation id) {
        try {
            Files.deleteIfExists(RecipeOverridePack.recipeFile(id));
        } catch (IOException e) {
            WirelessAutomate.LOGGER.warn("Não foi possível apagar o arquivo de teste de {}", id, e);
        }
    }

    @GameTest(template = "empty")
    public static void packSelected(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        helper.assertTrue(server.getPackRepository().getSelectedIds().contains(RecipeOverridePack.PACK_ID),
                "o pack de overrides deve estar selecionado: " + server.getPackRepository().getSelectedIds());
        helper.assertTrue(Files.isRegularFile(RecipeOverridePack.root().resolve("pack.mcmeta")),
                "a pasta do pack deve ter o pack.mcmeta");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void listsModRecipes(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        List<RecipeEditor.Entry> entries = RecipeEditor.entries(server);
        RecipeEditor.Entry router = entries.stream().filter(e -> e.id().equals(id("router"))).findFirst()
                .orElseThrow(() -> new GameTestAssertException("o roteador deve ser editável"));
        helper.assertValueEqual(router.defaults().shape(), RecipeDraft.Shape.SHAPED, "forma do roteador");
        helper.assertTrue(entries.stream().noneMatch(e -> e.id().equals(id("filter_card_copy"))),
                "filter_card_copy (serializador próprio) não é editável");
        helper.assertTrue(entries.stream().noneMatch(e -> e.id().equals(id("router_upgrade"))),
                "router_upgrade (serializador próprio) não é editável");
        boolean guide = entries.stream().anyMatch(e -> e.id().equals(id("guide")));
        helper.assertValueEqual(guide, ModList.get().isLoaded("guideme"), "guide só com o GuideME");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void saveWritesValidRecipe(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ResourceLocation id = id("storage_tank");
        try {
            RecipeEditor.Entry before = require(server, id);
            helper.assertValueEqual(before.state(), RecipeEditor.State.DEFAULT, "estado inicial");
            Optional<?> error = RecipeEditor.save(server, id, before.current().withCount(3));
            helper.assertTrue(error.isEmpty(), "salvar deve dar certo: " + error);
            helper.assertTrue(Files.isRegularFile(RecipeOverridePack.recipeFile(id)), "o arquivo deve existir");
            JsonObject json = readFile(id);
            helper.assertFalse(json.has(RecipeJson.CONDITIONS), "sem condições no JSON gravado");
            Recipe<?> recipe = Recipe.CODEC.parse(RegistryOps.create(JsonOps.INSTANCE, server.registryAccess()), json)
                    .getOrThrow(msg -> new GameTestAssertException("o JSON não decodifica: " + msg));
            helper.assertTrue(recipe instanceof ShapedRecipe, "deve ser uma receita com forma");
            ItemStack result = recipe.getResultItem(server.registryAccess());
            helper.assertValueEqual(BuiltInRegistries.ITEM.getKey(result.getItem()), id, "item do resultado");
            helper.assertValueEqual(result.getCount(), 3, "quantidade do resultado");
            RecipeEditor.Entry after = require(server, id);
            helper.assertValueEqual(after.state(), RecipeEditor.State.EDITED, "estado depois de salvar");
            helper.assertValueEqual(after.current().count(), 3, "count atual");
            helper.assertTrue(after.divergent(), "sem recarga, a carregada (do jar) diverge do override");
        } catch (IOException e) {
            throw new GameTestAssertException("erro de disco: " + e);
        } finally {
            clean(id);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void disableAndEnable(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ResourceLocation id = id("linker");
        try {
            helper.assertTrue(RecipeEditor.setDisabled(server, id, true).isEmpty(), "desativar deve dar certo");
            helper.assertTrue(hasFalseCondition(readFile(id)), "o JSON deve ter neoforge:false");
            helper.assertValueEqual(require(server, id).state(), RecipeEditor.State.DISABLED, "estado desativada");
            helper.assertTrue(RecipeEditor.setDisabled(server, id, false).isEmpty(), "reativar deve dar certo");
            helper.assertFalse(hasFalseCondition(readFile(id)), "o JSON não deve mais ter neoforge:false");
            helper.assertValueEqual(require(server, id).state(), RecipeEditor.State.EDITED, "estado reativada");
        } catch (IOException e) {
            throw new GameTestAssertException("erro de disco: " + e);
        } finally {
            clean(id);
        }
        helper.succeed();
    }

    private static boolean hasFalseCondition(JsonObject json) {
        return json.has(RecipeJson.CONDITIONS) && json.getAsJsonArray(RecipeJson.CONDITIONS).toString()
                .contains(RecipeJson.FALSE_CONDITION);
    }

    @GameTest(template = "empty")
    public static void restoreDeletes(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ResourceLocation id = id("network_tablet");
        try {
            RecipeEditor.Entry before = require(server, id);
            helper.assertTrue(RecipeEditor.save(server, id, before.current().withCount(1)).isEmpty(), "salvar");
            helper.assertTrue(Files.isRegularFile(RecipeOverridePack.recipeFile(id)), "o arquivo deve existir");
            helper.assertValueEqual(require(server, id).state(), RecipeEditor.State.EDITED, "editada");
            helper.assertTrue(RecipeEditor.restore(server, id).isEmpty(), "restaurar deve dar certo");
            helper.assertFalse(Files.exists(RecipeOverridePack.recipeFile(id)), "o arquivo deve sumir");
            helper.assertValueEqual(require(server, id).state(), RecipeEditor.State.DEFAULT, "padrão de novo");
        } finally {
            clean(id);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void rejectsBadDrafts(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ResourceLocation id = id("storage_chest");
        ResourceLocation special = id("filter_card_copy");
        ResourceLocation configurator = id("configurator");
        try {
            RecipeDraft base = require(server, id).current();

            RecipeDraft empty = base;
            for (int i = 0; i < RecipeDraft.SLOTS; i++) {
                empty = empty.withSlot(i, RecipeSlot.EMPTY);
            }
            helper.assertTrue(RecipeEditor.save(server, id, empty).isPresent(), "grade vazia deve dar erro");

            RecipeDraft unknown = base.withSlot(0, RecipeSlot.item("wirelessautomate:nao_existe"));
            helper.assertTrue(RecipeEditor.save(server, id, unknown).isPresent(), "item inexistente deve dar erro");

            RecipeDraft badTag = base.withSlot(0, RecipeSlot.tag("c:nao_existe_wa"));
            helper.assertTrue(RecipeEditor.save(server, id, badTag).isPresent(), "tag inexistente deve dar erro");

            RecipeDraft swapped = RecipeDraft.fromShapeless(List.of(RecipeSlot.item("minecraft:stick")), 1);
            helper.assertTrue(RecipeEditor.save(server, id, swapped).isPresent(), "forma trocada deve dar erro");

            RecipeDraft tooMany = require(server, configurator).current().withCount(2);
            helper.assertTrue(RecipeEditor.save(server, configurator, tooMany).isPresent(),
                    "quantidade acima da pilha do resultado (Configurador empilha 1) deve dar erro");

            helper.assertTrue(RecipeEditor.save(server, special, base).isPresent(), "id especial deve dar erro");
            helper.assertTrue(RecipeEditor.setDisabled(server, special, true).isPresent(),
                    "desativar id especial deve dar erro");

            ResourceLocation escape = ResourceLocation.fromNamespaceAndPath(WirelessAutomate.MODID, "../../escape");
            boolean threw = false;
            try {
                RecipeOverridePack.recipeFile(escape);
            } catch (IllegalArgumentException e) {
                threw = true;
            }
            helper.assertTrue(threw, "recipeFile deve recusar um id que sai da pasta");
            helper.assertTrue(RecipeEditor.save(server, escape, base).isPresent(), "id fora da pasta deve dar erro");
            helper.assertTrue(RecipeEditor.setDisabled(server, escape, true).isPresent(), "desativar id fora da pasta deve dar erro");
            helper.assertTrue(RecipeEditor.restore(server, escape).isPresent(), "restaurar id fora da pasta deve dar erro");
            helper.assertFalse(Files.exists(RecipeOverridePack.recipeFile(id)), "nada gravado para " + id);
            helper.assertFalse(Files.exists(RecipeOverridePack.recipeFile(special)), "nada gravado para " + special);
            helper.assertFalse(Files.exists(RecipeOverridePack.recipeFile(configurator)), "nada gravado para " + configurator);
        } finally {
            clean(id);
            clean(special);
            clean(configurator);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void keepsConditions(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ResourceLocation file = ResourceLocation.fromNamespaceAndPath(WirelessAutomate.MODID,
                "recipe/tier_core_allthemodium.json");
        Resource resource = server.getResourceManager().getResource(file)
                .orElseThrow(() -> new GameTestAssertException("o jar deve ter o recurso " + file));
        JsonObject json;
        try (Reader reader = resource.openAsReader()) {
            json = JsonParser.parseReader(reader).getAsJsonObject();
        } catch (IOException e) {
            throw new GameTestAssertException("erro lendo o recurso: " + e);
        }
        RecipeDraft draft = RecipeJson.toDraft(json)
                .orElseThrow(() -> new GameTestAssertException("o padrão deve virar rascunho"));
        JsonObject out = RecipeJson.toOverride(json, draft.withCount(2));
        String conditions = out.getAsJsonArray(RecipeJson.CONDITIONS).toString();
        helper.assertTrue(conditions.contains("neoforge:mod_loaded") && conditions.contains("allthemodium"),
                "a condição mod_loaded deve ser mantida: " + conditions);
        helper.assertFalse(conditions.contains(RecipeJson.FALSE_CONDITION), "sem neoforge:false");
        helper.assertValueEqual(out.getAsJsonObject("result").get("count").getAsInt(), 2, "count do override");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void actionNeedsOperator(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ResourceLocation id = id("chunk_loader_upgrade");
        ResourceLocation opId = id("storage_battery");
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        // Operador fora da lista de jogadores (sem conexão): só o handle o vê.
        ServerPlayer operator = new ServerPlayer(server, helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "test-mock-operator"), ClientInformation.createDefault()) {
            @Override
            protected int getPermissionLevel() {
                return 2;
            }
        };
        try {
            helper.assertFalse(player.hasPermissions(2), "o jogador de teste não deve ser operador");
            RecipeDraft draft = require(server, id).current().withCount(2);
            RecipeEditorActionPayload save = RecipeEditorActionPayload.save(id, draft);

            // Sem o editor aberto.
            RecipeEditorActionPayload.handle(player, save);
            helper.assertFalse(Files.exists(RecipeOverridePack.recipeFile(id)), "sem menu aberto nada é gravado");

            // Com o editor aberto, mas sem permissão.
            player.containerMenu = new RecipeEditorMenu(1, player.getInventory(), RecipeEditorSnapshot.of(server));
            RecipeEditorActionPayload.handle(player, save);
            RecipeEditorActionPayload.handle(player, RecipeEditorActionPayload.of(
                    RecipeEditorActionPayload.Action.DISABLE, id));
            helper.assertFalse(Files.exists(RecipeOverridePack.recipeFile(id)), "sem permissão nada é gravado");
            helper.assertFalse(new RecipeEditorMenu(2, player.getInventory(), RecipeEditorSnapshot.of(server))
                    .stillValid(player), "o menu não vale sem permissão");
            player.containerMenu = player.inventoryMenu;

            // Controle positivo: um operador com o editor aberto grava.
            helper.assertTrue(operator.hasPermissions(2), "o operador de teste deve ter permissão 2");
            RecipeDraft opDraft = require(server, opId).current().withCount(2);
            operator.containerMenu = new RecipeEditorMenu(3, operator.getInventory(), RecipeEditorSnapshot.of(server));
            helper.assertTrue(operator.containerMenu.stillValid(operator), "o menu vale com permissão");
            RecipeEditorActionPayload.handle(operator, RecipeEditorActionPayload.save(opId, opDraft));
            helper.assertTrue(Files.isRegularFile(RecipeOverridePack.recipeFile(opId)), "o operador grava");
        } finally {
            // Um RecipeEditorMenu esquecido aberto falharia no stillValid e recarregaria os recursos ao fechar.
            player.containerMenu = player.inventoryMenu;
            operator.containerMenu = operator.inventoryMenu;
            server.getPlayerList().remove(player);
            clean(id);
            clean(opId);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void snapshotRoundTrip(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        RecipeEditorSnapshot snapshot = RecipeEditorSnapshot.of(server);
        helper.assertFalse(snapshot.rows().isEmpty(), "o snapshot deve ter receitas");
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), server.registryAccess());
        RecipeEditorSnapshot.STREAM_CODEC.encode(buf, snapshot);
        helper.assertValueEqual(RecipeEditorSnapshot.STREAM_CODEC.decode(buf), snapshot, "snapshot decodificado");
        helper.assertValueEqual(buf.readableBytes(), 0, "bytes que sobraram");

        RecipeDraft draft = snapshot.rows().get(0).current();
        RegistryFriendlyByteBuf draftBuf = new RegistryFriendlyByteBuf(Unpooled.buffer(), server.registryAccess());
        RecipeEditorActionPayload.STREAM_CODEC.encode(draftBuf,
                RecipeEditorActionPayload.save(snapshot.rows().get(0).id(), draft));
        RecipeEditorActionPayload back = RecipeEditorActionPayload.STREAM_CODEC.decode(draftBuf);
        helper.assertValueEqual(back.draft(), draft, "rascunho da ação");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void snapshotClampsCount(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), server.registryAccess());
        RecipeEditorSnapshot.DRAFT_CODEC.encode(buf, RecipeDraft.fromShapeless(List.of(RecipeSlot.item("minecraft:stick")), 5));
        // Troca o count (penúltimo varint, antes do boolean) por um valor fora do limite.
        byte[] bytes = new byte[buf.readableBytes()];
        buf.getBytes(buf.readerIndex(), bytes);
        bytes[bytes.length - 2] = 100;
        RegistryFriendlyByteBuf edited = new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(bytes), server.registryAccess());
        helper.assertValueEqual(RecipeEditorSnapshot.DRAFT_CODEC.decode(edited).count(), RecipeDraft.MAX_COUNT,
                "count preso a 64");
        byte[] bad = bytes.clone();
        bad[0] = 9;
        boolean threw = false;
        try {
            RecipeEditorSnapshot.DRAFT_CODEC.decode(new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(bad),
                    server.registryAccess()));
        } catch (DecoderException e) {
            threw = true;
        }
        helper.assertTrue(threw, "forma inválida deve dar DecoderException");
        helper.succeed();
    }
}
