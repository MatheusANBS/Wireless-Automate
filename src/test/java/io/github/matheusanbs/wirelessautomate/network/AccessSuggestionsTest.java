package io.github.matheusanbs.wirelessautomate.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.matheusanbs.wirelessautomate.network.AccessSuggestions.Candidate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AccessSuggestionsTest {
    private static Candidate c(String name, boolean online, boolean taken) {
        return new Candidate(UUID.nameUUIDFromBytes(name.getBytes()), name, online, taken);
    }

    private static List<String> names(List<Candidate> list) {
        return list.stream().map(Candidate::name).toList();
    }

    @Test
    void emptyTextTakesEveryoneUpToTheLimit() {
        List<Candidate> all = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            all.add(c("jogador" + (char) ('a' + i), false, false));
        }
        assertEquals(AccessSuggestions.MAX, AccessSuggestions.pick(all, "", AccessSuggestions.MAX).size());
        assertEquals(3, AccessSuggestions.pick(all, "  ", 3).size());
    }

    @Test
    void textMustBeContainedIgnoringCaseAndEdgeSpaces() {
        List<Candidate> all = List.of(c("Bruna", false, false), c("Carla", false, false), c("Ambrósio", false, false));
        assertEquals(List.of("Bruna", "Ambrósio"), names(AccessSuggestions.pick(all, " BR ", 8)));
        assertTrue(AccessSuggestions.pick(all, "zzz", 8).isEmpty());
    }

    @Test
    void startsWithBeatsContainsThenOnlineBeatsOffline() {
        List<Candidate> all = List.of(c("Ambrósio", true, false), c("Bruno", false, false), c("Brenno", true, false),
                c("Abraão", false, false));
        assertEquals(List.of("Brenno", "Bruno", "Ambrósio", "Abraão"), names(AccessSuggestions.pick(all, "br", 8)));
    }

    @Test
    void whoIsAlreadyInTheNetworkComesLastEvenIfOnlineAndPrefixed() {
        List<Candidate> all = List.of(c("Bruno", true, true), c("Zebr", false, false), c("Brenda", false, false));
        assertEquals(List.of("Brenda", "Zebr", "Bruno"), names(AccessSuggestions.pick(all, "br", 8)));
    }

    @Test
    void sameNameFallsBackToTheId() {
        Candidate a = new Candidate(new UUID(0, 1), "Ana", false, false);
        Candidate b = new Candidate(new UUID(0, 2), "ana", false, false);
        assertEquals(List.of(a, b), AccessSuggestions.pick(List.of(b, a), "an", 8));
    }
}
