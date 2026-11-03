package io.github.matheusanbs.wirelessautomate.network;

import io.github.matheusanbs.wirelessautomate.network.Verdict.Action;
import io.github.matheusanbs.wirelessautomate.network.Verdict.Problem;
import io.github.matheusanbs.wirelessautomate.network.Verdict.ProblemKind;
import io.github.matheusanbs.wirelessautomate.network.Verdict.Reason;
import io.github.matheusanbs.wirelessautomate.network.VerdictFacts.FaceFact;
import io.github.matheusanbs.wirelessautomate.network.VerdictFacts.Reach;
import java.util.ArrayList;
import java.util.List;

/** A regra que transforma os fatos de uma máquina no veredito: a primeira que bate decide o motivo. */
public final class VerdictRules {
    private VerdictRules() {
    }

    public static Verdict decide(VerdictFacts facts) {
        List<FaceFact> used = new ArrayList<>(facts.faces().size());
        for (FaceFact f : facts.faces()) {
            if (f.inUse()) {
                used.add(f);
            }
        }
        FaceFact first = used.isEmpty() ? null : used.get(0);
        ResourceType type = first == null ? ResourceType.ITEM : first.type();
        FaceSide face = first == null ? FaceSide.UP : first.face();

        Reason reach = reachReason(facts.reach());
        if (reach != null) {
            return plain(reach, type, face);
        }
        if (first == null) {
            return plain(Reason.NOT_IN_USE, type, face);
        }

        int noNetwork = 0;
        for (FaceFact f : used) {
            if (f.network() == null) {
                noNetwork++;
            }
        }
        if (noNetwork == used.size()) {
            return new Verdict(Reason.NO_NETWORK, type, face, null, noNetwork, Action.CHOOSE_NETWORK, List.of(), List.of());
        }

        FaceFact firstWithNetwork = null;
        FaceFact pausedFace = null;
        FaceFact liveFace = null;
        FaceFact movingFace = null;
        FaceFact fullSource = null;
        FaceFact selfFullFace = null;
        FaceFact asleepFace = null;
        boolean allStopped = true;
        for (FaceFact f : used) {
            if (f.network() == null) {
                continue;
            }
            if (firstWithNetwork == null) {
                firstWithNetwork = f;
            }
            if (f.groupPaused()) {
                if (pausedFace == null) {
                    pausedFace = f;
                }
                continue;
            }
            if (liveFace == null) {
                liveFace = f;
            }
            if (f.stopped()) {
                continue;
            }
            allStopped = false;
            if (f.moving() && movingFace == null) {
                movingFace = f;
            }
            if (f.source() && f.sourceAsleep()) {
                if (f.waitsDestination() && f.fullDestinations() > 0) {
                    if (fullSource == null) {
                        fullSource = f;
                    }
                } else if (!f.waitsDestination() && asleepFace == null) {
                    asleepFace = f;
                }
            }
            if (f.destination() && f.selfFull() && selfFullFace == null) {
                selfFullFace = f;
            }
        }

        if (liveFace == null) {
            return withProblems(Reason.PAUSED, pausedFace, 0, Action.RESUME, List.of(), used);
        }
        if (allStopped) {
            return withProblems(Reason.REDSTONE, liveFace, 0, Action.NONE, List.of(), used);
        }
        if (movingFace != null) {
            return withProblems(Reason.MOVING, movingFace, 0, Action.NONE, List.of(), used);
        }
        if (fullSource != null) {
            List<Long> positions = fullSource.fullPositions();
            if (positions.size() > Verdict.MAX_POSITIONS) {
                positions = List.copyOf(positions.subList(0, Verdict.MAX_POSITIONS));
            }
            return withProblems(Reason.DESTINATIONS_FULL, fullSource, fullSource.fullDestinations(),
                    Action.SEE_DESTINATIONS, positions, used);
        }
        if (selfFullFace != null) {
            return withProblems(Reason.SELF_FULL, selfFullFace, 0, Action.NONE, List.of(), used);
        }
        if (asleepFace != null) {
            return withProblems(Reason.NOTHING_TO_SEND, asleepFace, 0, Action.NONE, List.of(), used);
        }
        return withProblems(Reason.IDLE, firstWithNetwork, 0, Action.NONE, List.of(), used);
    }

    private static Verdict withProblems(Reason reason, FaceFact at, int count, Action action, List<Long> positions,
            List<FaceFact> used) {
        return new Verdict(reason, at.type(), at.face(), at.network(), count, action, problems(used, reason), positions);
    }

    private static Verdict plain(Reason reason, ResourceType type, FaceSide face) {
        return new Verdict(reason, type, face, null, 0, Action.NONE, List.of(), List.of());
    }

    private static Reason reachReason(Reach reach) {
        return switch (reach) {
            case OK -> null;
            case UNLOADED -> Reason.UNLOADED;
            case NO_UPGRADE -> Reason.NO_UPGRADE;
            case OUT_OF_RANGE -> Reason.OUT_OF_RANGE;
            case OVER_LIMIT -> Reason.OVER_LIMIT;
            case REPLACED -> Reason.REPLACED;
            case MISSING -> Reason.MISSING;
        };
    }

    /** Os outros problemas, um por linha, depois do motivo; no máximo {@link Verdict#MAX_PROBLEMS}. */
    private static List<Problem> problems(List<FaceFact> used, Reason reason) {
        List<Problem> out = null;
        for (FaceFact f : used) {
            Problem p = null;
            if (f.network() == null) {
                p = new Problem(ProblemKind.NO_NETWORK, f.type(), f.face(), Action.CHOOSE_NETWORK);
            } else if (f.stopped() && !f.groupPaused() && reason != Reason.REDSTONE) {
                p = new Problem(ProblemKind.REDSTONE, f.type(), f.face(), Action.NONE);
            } else if (f.source() && f.filtered()
                    && (reason == Reason.DESTINATIONS_FULL || reason == Reason.NOTHING_TO_SEND)) {
                p = new Problem(ProblemKind.FILTER, f.type(), f.face(), Action.OPEN_FILTER);
            }
            if (p != null) {
                if (out == null) {
                    out = new ArrayList<>(Verdict.MAX_PROBLEMS);
                }
                out.add(p);
                if (out.size() >= Verdict.MAX_PROBLEMS) {
                    break;
                }
            }
        }
        return out == null ? List.of() : out;
    }
}
