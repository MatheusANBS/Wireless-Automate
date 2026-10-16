package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.resonator.ResonanceCoreBlock;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorBill;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorBlueprint.Layout;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorFormation;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorLayout.Member;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * A cena do guia ({@code scripts/guide/resonator_scene.txt}) contra o estado montado de verdade: para cada membro da
 * Vitrine de exemplo, o bloco, a posição e cada propriedade da linha têm de ser os do
 * {@link ResonatorFormation#formedState} (o Núcleo Esmeralda, como na cena). Assim o guia, que lê o arquivo, e o JEI,
 * que usa o {@code formedState}, não divergem calados.
 */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class ResonatorGuideSceneGameTests {
    private static final Path SCENE = Path.of("scripts", "guide", "resonator_scene.txt");
    private static final Pattern LINE = Pattern.compile(
            "<Block id=\"([a-z0-9_.:-]+)\" x=\"(\\d+)\" y=\"(\\d+)\" z=\"(\\d+)\"((?: p:[a-z_]+=\"[^\"]*\")*) />");
    private static final Pattern PROPERTY = Pattern.compile("p:([a-z_]+)=\"([^\"]*)\"");

    private ResonatorGuideSceneGameTests() {
    }

    @GameTest(template = "empty")
    public static void cenaDoGuiaUsaOEstadoMontado(GameTestHelper helper) {
        List<String> lines = sceneLines(helper);
        List<Member> members = ResonatorBill.of(Layout.VITRINE).members();
        helper.assertTrue(lines.size() == members.size(),
                "a cena tem " + lines.size() + " linhas e a Vitrine " + members.size() + " membros");
        List<String> errors = new ArrayList<>();
        for (int i = 0; i < members.size(); i++) {
            compare(members.get(i), lines.get(i), errors);
        }
        helper.assertTrue(errors.isEmpty(), "a cena do guia difere do formedState: " + String.join("; ", errors));
        helper.succeed();
    }

    private static void compare(Member member, String line, List<String> errors) {
        Matcher matcher = LINE.matcher(line);
        if (!matcher.matches()) {
            errors.add("linha fora do formato: " + line);
            return;
        }
        String where = member.part() + " (" + member.x() + ", " + member.y() + ", " + member.z() + ")";
        if (Integer.parseInt(matcher.group(2)) != member.x() || Integer.parseInt(matcher.group(3)) != member.y()
                || Integer.parseInt(matcher.group(4)) != member.z()) {
            errors.add(where + ": a linha está em outra posição: " + line);
            return;
        }
        Block block = BuiltInRegistries.BLOCK.get(ResourceLocation.parse(matcher.group(1)));
        BlockState state = ResonatorFormation.formedState(sceneDefault(block), member);
        Map<String, String> expected = new LinkedHashMap<>();
        for (Property<?> property : state.getProperties()) {
            expected.put(property.getName(), valueName(state, property));
        }
        Map<String, String> written = new LinkedHashMap<>();
        Matcher properties = PROPERTY.matcher(matcher.group(5));
        while (properties.find()) {
            written.put(properties.group(1), properties.group(2));
        }
        if (!expected.equals(written)) {
            errors.add(where + " " + matcher.group(1) + ": formedState " + expected + ", cena " + written);
        }
    }

    /** O estado solto como a cena o parte: o Núcleo Esmeralda, o resto no padrão do bloco. */
    private static BlockState sceneDefault(Block block) {
        BlockState state = block.defaultBlockState();
        return block instanceof ResonanceCoreBlock ? state.setValue(RouterBlock.TIER, RouterTier.EMERALD) : state;
    }

    private static <T extends Comparable<T>> String valueName(BlockState state, Property<T> property) {
        return property.getName(state.getValue(property));
    }

    /** As linhas do arquivo, achado subindo os diretórios a partir de onde o servidor roda ({@code run/gametest}). */
    private static List<String> sceneLines(GameTestHelper helper) {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null && !Files.isRegularFile(dir.resolve(SCENE))) {
            dir = dir.getParent();
        }
        if (dir == null) {
            helper.fail("não achei " + SCENE + " subindo a partir de " + Path.of("").toAbsolutePath());
            return List.of();
        }
        try {
            return Files.readAllLines(dir.resolve(SCENE), StandardCharsets.UTF_8).stream()
                    .filter(line -> !line.isBlank())
                    .toList();
        } catch (IOException e) {
            helper.fail("não consegui ler " + dir.resolve(SCENE) + ": " + e);
            return List.of();
        }
    }
}
