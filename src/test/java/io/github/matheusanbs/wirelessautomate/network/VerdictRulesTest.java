package io.github.matheusanbs.wirelessautomate.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.matheusanbs.wirelessautomate.network.Verdict.Action;
import io.github.matheusanbs.wirelessautomate.network.Verdict.Reason;
import io.github.matheusanbs.wirelessautomate.network.VerdictFacts.FaceFact;
import io.github.matheusanbs.wirelessautomate.network.VerdictFacts.Reach;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class VerdictRulesTest {
    private static final UUID NET = new UUID(1, 1);

    private static FaceFact.Builder face(FaceSide d) {
        return FaceFact.builder(ResourceType.ITEM, d);
    }

    private static Verdict decide(Reach reach, FaceFact... faces) {
        return VerdictRules.decide(new VerdictFacts(reach, List.of(faces)));
    }

    @Test
    void unloadedBeatsEverything() {
        Verdict v = decide(Reach.UNLOADED, face(FaceSide.UP).source().network(NET).build());
        assertEquals(Reason.UNLOADED, v.reason());
    }

    @Test
    void noFaceInUse() {
        assertEquals(Reason.NOT_IN_USE, decide(Reach.OK, face(FaceSide.UP).build()).reason());
    }

    @Test
    void faceWithoutNetworkSaysNothingLeaves() {
        Verdict v = decide(Reach.OK, face(FaceSide.UP).source().build(),
                face(FaceSide.DOWN).destination().network(NET).build());
        // a outra face tem rede: o motivo só é "sem rede" quando todas as faces em uso estão sem
        assertEquals(Reason.IDLE, v.reason());
        assertEquals(1, v.problems().size());
        assertEquals(Action.CHOOSE_NETWORK, v.problems().get(0).action());
        assertEquals(FaceSide.UP, v.problems().get(0).face());
    }

    @Test
    void allFacesWithoutNetwork() {
        Verdict v = decide(Reach.OK, face(FaceSide.UP).source().build());
        assertEquals(Reason.NO_NETWORK, v.reason());
        assertEquals(FaceSide.UP, v.face());
        assertEquals(Action.CHOOSE_NETWORK, v.action());
        assertTrue(v.problems().isEmpty());
    }

    @Test
    void noNetworkBeatsFull() {
        Verdict v = decide(Reach.OK, face(FaceSide.UP).source().asleepFull(2).build());
        assertEquals(Reason.NO_NETWORK, v.reason());
    }

    @Test
    void pausedGroup() {
        Verdict v = decide(Reach.OK, face(FaceSide.UP).source().network(NET).paused().build());
        assertEquals(Reason.PAUSED, v.reason());
        assertEquals(Action.RESUME, v.action());
        assertEquals(NET, v.network());
    }

    @Test
    void redstoneStopsEveryLiveFace() {
        Verdict v = decide(Reach.OK, face(FaceSide.UP).source().network(NET).stopped().build());
        assertEquals(Reason.REDSTONE, v.reason());
    }

    @Test
    void redstoneOnOneFaceIsAProblemNotTheReason() {
        Verdict v = decide(Reach.OK, face(FaceSide.UP).source().network(NET).stopped().build(),
                face(FaceSide.DOWN).source().network(NET).moving().build());
        assertEquals(Reason.MOVING, v.reason());
        assertEquals(1, v.problems().size());
        assertEquals(FaceSide.UP, v.problems().get(0).face());
    }

    @Test
    void destinationsFullCountsThemAndCarriesPositions() {
        Verdict v = decide(Reach.OK, face(FaceSide.UP).source().network(NET).asleepFull(2).build());
        assertEquals(Reason.DESTINATIONS_FULL, v.reason());
        assertEquals(2, v.count());
        assertEquals(NET, v.network());
        assertEquals(Action.SEE_DESTINATIONS, v.action());
        assertEquals(2, v.positions().size());
    }

    @Test
    void filterBecomesAProblemWhenFullOrNothingToSend() {
        Verdict full = decide(Reach.OK, face(FaceSide.DOWN).source().network(NET).asleepFull(1).filtered().build());
        assertEquals(Reason.DESTINATIONS_FULL, full.reason());
        assertEquals(Action.OPEN_FILTER, full.problems().get(0).action());
        Verdict empty = decide(Reach.OK, face(FaceSide.DOWN).source().network(NET).asleepEmpty().filtered().build());
        assertEquals(Reason.NOTHING_TO_SEND, empty.reason());
        assertEquals(Action.OPEN_FILTER, empty.problems().get(0).action());
    }

    @Test
    void nothingToSendWithoutFilterHasNoProblem() {
        Verdict v = decide(Reach.OK, face(FaceSide.DOWN).source().network(NET).asleepEmpty().build());
        assertEquals(Reason.NOTHING_TO_SEND, v.reason());
        assertTrue(v.problems().isEmpty());
    }

    @Test
    void movingWinsOverASleepingOtherFace() {
        Verdict v = decide(Reach.OK, face(FaceSide.UP).source().network(NET).moving().build(),
                face(FaceSide.DOWN).source().network(NET).asleepFull(1).build());
        assertEquals(Reason.MOVING, v.reason());
    }

    @Test
    void receiverThatIsFullIsWarnNotBad() {
        Verdict v = decide(Reach.OK, face(FaceSide.UP).destination().network(NET).selfFull().build());
        assertEquals(Reason.SELF_FULL, v.reason());
        assertEquals(Verdict.Tone.WARN, v.reason().tone());
    }

    @Test
    void idleWhenNothingElseApplies() {
        Verdict v = decide(Reach.OK, face(FaceSide.UP).destination().network(NET).build());
        assertEquals(Reason.IDLE, v.reason());
    }

    @Test
    void reachReasonsAreBad() {
        Object[][] cases = {{Reach.NO_UPGRADE, Reason.NO_UPGRADE}, {Reach.OUT_OF_RANGE, Reason.OUT_OF_RANGE},
                {Reach.OVER_LIMIT, Reason.OVER_LIMIT}, {Reach.REPLACED, Reason.REPLACED}, {Reach.MISSING, Reason.MISSING}};
        for (Object[] c : cases) {
            Verdict v = decide((Reach) c[0], face(FaceSide.UP).source().network(NET).build());
            assertEquals(c[1], v.reason());
            assertEquals(Verdict.Tone.BAD, v.reason().tone());
        }
        assertEquals(Verdict.Tone.MUTED, Reason.UNLOADED.tone());
    }

    @Test
    void reachBeatsNotInUse() {
        assertEquals(Reason.MISSING, decide(Reach.MISSING, face(FaceSide.UP).build()).reason());
    }

    @Test
    void oneFacePausedAndOneLiveIsNotPaused() {
        Verdict v = decide(Reach.OK, face(FaceSide.UP).source().network(NET).paused().build(),
                face(FaceSide.DOWN).destination().network(NET).build());
        assertEquals(Reason.IDLE, v.reason());
    }

    @Test
    void redstoneWithAStoppedFaceAndAPausedOne() {
        Verdict v = decide(Reach.OK, face(FaceSide.UP).source().network(NET).paused().build(),
                face(FaceSide.DOWN).source().network(NET).stopped().build());
        assertEquals(Reason.REDSTONE, v.reason());
        assertEquals(FaceSide.DOWN, v.face());
    }

    @Test
    void destinationsFullBeatsSelfFullAndSelfFullBeatsNothingToSend() {
        Verdict a = decide(Reach.OK, face(FaceSide.UP).destination().network(NET).selfFull().build(),
                face(FaceSide.DOWN).source().network(NET).asleepFull(1).build());
        assertEquals(Reason.DESTINATIONS_FULL, a.reason());
        Verdict b = decide(Reach.OK, face(FaceSide.UP).source().network(NET).asleepEmpty().build(),
                face(FaceSide.DOWN).destination().network(NET).selfFull().build());
        assertEquals(Reason.SELF_FULL, b.reason());
    }

    @Test
    void manyProblemsKeepOrderAndCap() {
        Verdict v = decide(Reach.OK,
                face(FaceSide.DOWN).source().build(),
                face(FaceSide.UP).source().network(NET).stopped().build(),
                face(FaceSide.NORTH).source().build(),
                face(FaceSide.SOUTH).source().network(NET).stopped().build(),
                face(FaceSide.WEST).source().build(),
                face(FaceSide.EAST).source().network(NET).asleepFull(1).filtered().build());
        assertEquals(Reason.DESTINATIONS_FULL, v.reason());
        assertEquals(Verdict.MAX_PROBLEMS, v.problems().size());
        assertEquals(Verdict.ProblemKind.NO_NETWORK, v.problems().get(0).kind());
        assertEquals(Verdict.ProblemKind.REDSTONE, v.problems().get(1).kind());
        assertEquals(FaceSide.UP, v.problems().get(1).face());
        assertEquals(FaceSide.NORTH, v.problems().get(2).face());
        assertEquals(FaceSide.SOUTH, v.problems().get(3).face());
    }

    @Test
    void redstoneNoNetworkAndFilterTogether() {
        Verdict v = decide(Reach.OK,
                face(FaceSide.DOWN).source().build(),
                face(FaceSide.UP).source().network(NET).stopped().build(),
                face(FaceSide.NORTH).source().network(NET).asleepFull(1).filtered().build());
        assertEquals(3, v.problems().size());
        assertEquals(Verdict.ProblemKind.NO_NETWORK, v.problems().get(0).kind());
        assertEquals(Verdict.ProblemKind.REDSTONE, v.problems().get(1).kind());
        assertEquals(Verdict.ProblemKind.FILTER, v.problems().get(2).kind());
    }

    @Test
    void filterLineHiddenOnPausedFace() {
        Verdict v = decide(Reach.OK,
                face(FaceSide.UP).source().network(NET).paused().filtered().build(),
                face(FaceSide.DOWN).source().network(NET).asleepEmpty().build());
        assertEquals(Reason.NOTHING_TO_SEND, v.reason());
        assertTrue(v.problems().isEmpty());
    }

    @Test
    void stoppedFaceDoesNotCountAsMoving() {
        Verdict v = decide(Reach.OK, face(FaceSide.UP).source().network(NET).stopped().moving().build(),
                face(FaceSide.DOWN).destination().network(NET).build());
        assertEquals(Reason.IDLE, v.reason());
    }

    @Test
    void positionsAreCappedAtEight() {
        List<Long> twelve = new java.util.ArrayList<>();
        for (long i = 0; i < 12; i++) {
            twelve.add(i);
        }
        FaceFact f = new FaceFact(ResourceType.ITEM, FaceSide.UP, true, false, NET, false, false, false, true, true, 12,
                twelve, false, false);
        Verdict v = decide(Reach.OK, f);
        assertEquals(12, v.count());
        assertEquals(Verdict.MAX_POSITIONS, v.positions().size());
    }
}
