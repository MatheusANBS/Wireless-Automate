package io.github.matheusanbs.wirelessautomate.recipe.edit;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.AddPackFindersEvent;

/**
 * O datapack global dos overrides de receita: uma pasta na config do jogo (vale para todos os mundos),
 * sempre ligada e no topo, para as receitas editadas vencerem as do jar.
 */
public final class RecipeOverridePack {
    public static final String PACK_ID = "wirelessautomate:recipe_overrides";
    private static final String MCMETA =
            "{\"pack\":{\"pack_format\":48,\"description\":\"Wireless Automate: receitas editadas\"}}";

    private RecipeOverridePack() {
    }

    public static Path root() {
        return FMLPaths.CONFIGDIR.get().resolve("wirelessautomate/recipes");
    }

    /**
     * {@code root/data/<ns>/recipe/<path>.json}. Um {@link ResourceLocation} aceita {@code ..} no caminho:
     * se o arquivo sair da pasta {@code recipe} do namespace, lança {@link IllegalArgumentException}.
     */
    public static Path recipeFile(ResourceLocation id) {
        Path root = root().toAbsolutePath().normalize();
        Path dir = root.resolve("data").resolve(id.getNamespace()).resolve("recipe").normalize();
        Path file = dir
                .resolve(id.getPath() + ".json").normalize();
        if (!dir.startsWith(root) || !file.startsWith(dir)) {
            throw new IllegalArgumentException("id fora da pasta recipe do pack de receitas editadas: " + id);
        }
        return file;
    }

    /** Cria a pasta e o {@code pack.mcmeta} se faltarem; erros de disco vão para o log. */
    public static void ensureFolder() {
        try {
            Files.createDirectories(root());
            Path meta = root().resolve("pack.mcmeta");
            if (!Files.exists(meta)) {
                Files.writeString(meta, MCMETA, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            WirelessAutomate.LOGGER.error("Não foi possível preparar a pasta de receitas editadas {}", root(), e);
        }
    }

    public static void onAddPackFinders(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.SERVER_DATA) {
            return;
        }
        ensureFolder();
        Component title = Component.literal("Wireless Automate: receitas editadas");
        Pack pack = Pack.readMetaAndCreate(
                new PackLocationInfo(PACK_ID, title, PackSource.BUILT_IN, Optional.empty()),
                new PathPackResources.PathResourcesSupplier(root()),
                PackType.SERVER_DATA,
                new PackSelectionConfig(true, Pack.Position.TOP, false));
        if (pack == null) {
            WirelessAutomate.LOGGER.error("O pack de receitas editadas ({}) não pôde ser lido", root());
            return;
        }
        event.addRepositorySource(consumer -> consumer.accept(pack));
    }
}
