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
        String json = Files.readString(DIR.resolve(miner).resolve("signal_quartz_ore.json"));
        assertTrue(json.contains("\"type\": \"occultism:miner\""));
        assertTrue(json.contains("\"tag\": \"occultism:miners/" + miner + "\""));
        assertTrue(json.contains("\"tag\": \"c:ores/signal_quartz\""));
        assertTrue(json.contains("\"weight\": " + weight));
        assertTrue(json.split("neoforge:tag_empty", -1).length == 3, "uma condição para cada tag");
    }

    @Test
    void theOreTagTheRecipesReadExists() {
        assertTrue(Files.exists(Path.of("src/main/resources/data/c/tags/block/ores/signal_quartz.json")));
        assertTrue(Files.exists(Path.of("src/main/resources/data/c/tags/item/ores/signal_quartz.json")));
    }
}
