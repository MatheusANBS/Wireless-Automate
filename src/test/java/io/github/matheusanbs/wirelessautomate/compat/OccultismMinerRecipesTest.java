package io.github.matheusanbs.wirelessautomate.compat;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class OccultismMinerRecipesTest {
    private static final Path DIR = Path.of("src/main/resources/data/occultism/recipe/miner");

    @ParameterizedTest
    @CsvSource({"ores,300", "eldritch,90"})
    void theMinerRecipeReadsTheOreTagAndOnlyLoadsWithBothTagsFilled(String miner, int weight) throws Exception {
        String json = Files.readString(DIR.resolve(miner).resolve("signal_crystal.json"));
        assertTrue(json.contains("\"type\": \"occultism:miner\""));
        assertTrue(json.contains("\"tag\": \"occultism:miners/" + miner + "\""));
        assertTrue(json.contains("\"tag\": \"c:gems/signal_quartz\""));
        assertTrue(json.contains("\"weight\": " + weight));
        assertTrue(json.contains("\"count\": 2"));
        assertTrue(json.split("neoforge:tag_empty", -1).length == 3, "uma condição para cada tag");
    }

    @Test
    void theGemTagTheRecipesReadHoldsTheCrystal() throws Exception {
        String tag = Files.readString(Path.of("src/main/resources/data/c/tags/item/gems/signal_quartz.json"));
        assertTrue(tag.contains("\"wirelessautomate:signal_crystal\""));
    }
}
