package io.github.matheusanbs.wirelessautomate.resonator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.matheusanbs.wirelessautomate.resonator.ResonatorBlueprint.Layout;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorLayout.Member;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/**
 * A cena do guia (a Vitrine montada, em linhas {@code <Block>} do GuideME) fica em
 * {@code scripts/guide/resonator_scene.txt}; este teste a confere com o {@link ResonatorBill}, para o Python não
 * repetir a regra das texturas conectadas, e falha se o arquivo faltar. Só escreve com
 * {@code ./gradlew test --tests "*ResonatorGuideSceneTest" -Dwa.regen=true}. Que as propriedades são as do estado
 * montado de verdade, o {@code ResonatorGuideSceneGameTests} confere.
 */
class ResonatorGuideSceneTest {
    private static final Path FILE = Path.of(System.getProperty("user.dir"), "scripts", "guide", "resonator_scene.txt");

    @Test
    void cenaDoGuiaConfereComOArquivo() throws IOException {
        String scene = String.join("\n", lines()) + "\n";
        if (Boolean.getBoolean("wa.regen")) {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, scene, StandardCharsets.UTF_8);
            return;
        }
        assertTrue(Files.exists(FILE), "Falta " + FILE
                + ": rode ./gradlew test --tests \"*ResonatorGuideSceneTest\" -Dwa.regen=true");
        String saved = Files.readString(FILE, StandardCharsets.UTF_8).replace("\r\n", "\n");
        assertEquals(scene, saved,
                "A cena mudou: rode ./gradlew test --tests \"*ResonatorGuideSceneTest\" -Dwa.regen=true");
    }

    @Test
    void temUmaLinhaPorMembro() {
        assertEquals(98, lines().size());
        assertTrue(lines().stream().allMatch(line -> line.startsWith("<Block id=\"wirelessautomate:")));
    }

    private static List<String> lines() {
        List<String> lines = new ArrayList<>();
        for (Member member : ResonatorBill.of(Layout.VITRINE).members()) {
            lines.add(line(member));
        }
        return lines;
    }

    /**
     * A linha de um membro: a peça, a posição e as propriedades montadas, com a frente para fora ({@code face}). O
     * {@code ResonatorGuideSceneGameTests} compara cada linha do arquivo com o {@code ResonatorFormation.formedState}.
     */
    private static String line(Member member) {
        String facing = member.face() == null ? null : member.face().name().toLowerCase(Locale.ROOT);
        String id;
        String props;
        switch (member.part()) {
            case CASING -> {
                id = "resonator_casing";
                props = " p:position=\"" + member.casing() + "\"";
            }
            case GLASS -> {
                id = "resonator_glass";
                props = " p:face=\"" + facing + "\" p:connections=\"" + member.glassBits() + "\"";
            }
            case CORE -> {
                id = "resonance_core";
                props = " p:facing=\"" + facing + "\" p:formed=\"true\" p:tier=\"emerald\"";
            }
            case ENERGY_PORT -> {
                id = "resonator_energy_port";
                props = " p:facing=\"" + facing + "\" p:formed=\"true\"";
            }
            case CRYSTAL_PORT -> {
                id = "resonator_crystal_port";
                props = " p:facing=\"" + facing + "\" p:formed=\"true\"";
            }
            case TUNER -> {
                id = "resonator_tuner";
                props = " p:facing=\"" + facing + "\" p:formed=\"true\"";
            }
            default -> throw new IllegalStateException("Peça fora da estrutura: " + member.part());
        }
        return "<Block id=\"wirelessautomate:" + id + "\" x=\"" + member.x() + "\" y=\"" + member.y() + "\" z=\""
                + member.z() + "\"" + props + " />";
    }
}
