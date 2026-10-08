package io.github.matheusanbs.wirelessautomate.gametest;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Source do Ars Nouveau: Source Jars de verdade, um roteador em cima de cada (facing=UP, face
 * configurada {@link net.minecraft.core.Direction#UP}). Rodam só na run {@code runGameTestServerSource},
 * que tem o Ars na pasta mods e liga o namespace {@value #NAMESPACE} (o template é
 * {@code data/wirelessautomate_source/structure/empty.nbt}). Sem o Ars, só passam.
 *
 * <p>Nenhum tipo do Ars nas assinaturas: o NeoForge inspeciona esta classe por reflexão mesmo sem o
 * Ars. O que usa a API fica em {@code SourceTestSupport}.
 */
@GameTestHolder(SourceGameTests.NAMESPACE)
@PrefixGameTestTemplate(false)
public final class SourceGameTests {
    static final String NAMESPACE = "wirelessautomate_source";
    static final ResourceLocation JAR = ResourceLocation.fromNamespaceAndPath("ars_nouveau", "source_jar");

    /** A run de Source está ligada e o Ars está presente. */
    static boolean enabled() {
        return Boolean.getBoolean("wirelessautomate.sourceTests") && ModList.get().isLoaded("ars_nouveau");
    }

    /** A run de Source carrega o Ars, e a Source Jar existe no registro. */
    @GameTest(template = "empty")
    public static void arsNouveauIsLoaded(GameTestHelper helper) {
        if (!Boolean.getBoolean("wirelessautomate.sourceTests")) {
            helper.succeed();
            return;
        }
        helper.assertTrue(ModList.get().isLoaded("ars_nouveau"), "Ars Nouveau não carregou na run de Source");
        helper.assertTrue(BuiltInRegistries.BLOCK.get(JAR) != Blocks.AIR, "sem a Source Jar no registro");
        helper.succeed();
    }

    private SourceGameTests() {
    }
}
