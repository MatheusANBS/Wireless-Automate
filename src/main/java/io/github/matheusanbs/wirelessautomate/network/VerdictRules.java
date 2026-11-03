package io.github.matheusanbs.wirelessautomate.network;

import io.github.matheusanbs.wirelessautomate.network.Verdict.Action;
import io.github.matheusanbs.wirelessautomate.network.Verdict.Problem;
import io.github.matheusanbs.wirelessautomate.network.Verdict.ProblemKind;
import io.github.matheusanbs.wirelessautomate.network.Verdict.Reason;
import io.github.matheusanbs.wirelessautomate.network.VerdictFacts.FaceFact;
import io.github.matheusanbs.wirelessautomate.network.VerdictFacts.Reach;
import java.util.ArrayList;
import java.util.List;

/**
 * A regra que transforma os fatos de uma máquina no veredito: a primeira que bate decide o motivo. A linha de filtro
 * (e a de redstone, quando não é o motivo) não aparece numa face parada por redstone nem numa de grupo pausado: o
 * redstone ou a pausa é que explica a face, o filtro não é a causa.
 */
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
        Scan scan = scan(used);
        if (scan.noNetwork == used.size()) {
            return new Verdict(Reason.NO_NETWORK, type, face, null, scan.noNetwork, Action.CHOOSE_NETWORK, List.of(),
                    List.of());
        }
        return choose(scan, used);
    }

    /** O que a varredura das faces em uso achou: a primeira face de cada caso, e se toda face viva está parada. */
    private static final class Scan {
        int noNetwork;
        boolean allStopped = true;
        FaceFact firstWithNetwork;
        FaceFact paused;
        FaceFact live;
        FaceFact moving;
        FaceFact fullSource;
        FaceFact selfFull;
        FaceFact asleep;
    }

    private static Scan scan(List<FaceFact> used) {
        Scan s = new Scan();
        for (FaceFact f : used) {
            if (f.network() == null) {
                s.noNetwork++;
                continue;
            }
            if (s.firstWithNetwork == null) {
                s.firstWithNetwork = f;
            }
            if (f.groupPaused()) {
                if (s.paused == null) {
                    s.paused = f;
                }
                continue;
            }
            if (s.live == null) {
                s.live = f;
            }
            if (f.stopped()) {
                continue;
            }
            s.allStopped = false;
            if (f.moving() && s.moving == null) {
                s.moving = f;
            }
            if (f.source() && f.sourceAsleep()) {
                if (f.waitsDestination() && f.fullDestinations() > 0) {
                    if (s.fullSource == null) {
                        s.fullSource = f;
                    }
                } else if (!f.waitsDestination() && s.asleep == null) {
                    s.asleep = f;
                }
            }
            if (f.destination() && f.selfFull() && s.selfFull == null) {
                s.selfFull = f;
            }
        }
        return s;
    }

    /** Os casos 4 a 10 da regra, na ordem em que valem. */
    private static Verdict choose(Scan s, List<FaceFact> used) {
        if (s.live == null) {
            return withProblems(Reason.PAUSED, s.paused, 0, Action.RESUME, List.of(), used);
        }
        if (s.allStopped) {
            return withProblems(Reason.REDSTONE, s.live, 0, Action.NONE, List.of(), used);
        }
        if (s.moving != null) {
            return withProblems(Reason.MOVING, s.moving, 0, Action.NONE, List.of(), used);
        }
        if (s.fullSource != null) {
            List<Long> positions = s.fullSource.fullPositions();
            if (positions.size() > Verdict.MAX_POSITIONS) {
                positions = List.copyOf(positions.subList(0, Verdict.MAX_POSITIONS));
            }
            return withProblems(Reason.DESTINATIONS_FULL, s.fullSource, s.fullSource.fullDestinations(),
                    Action.SEE_DESTINATIONS, positions, used);
        }
        if (s.selfFull != null) {
            return withProblems(Reason.SELF_FULL, s.selfFull, 0, Action.NONE, List.of(), used);
        }
        if (s.asleep != null) {
            return withProblems(Reason.NOTHING_TO_SEND, s.asleep, 0, Action.NONE, List.of(), used);
        }
        return withProblems(Reason.IDLE, s.firstWithNetwork, 0, Action.NONE, List.of(), used);
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
            } else if (!f.stopped() && !f.groupPaused() && f.source() && f.filtered()
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
