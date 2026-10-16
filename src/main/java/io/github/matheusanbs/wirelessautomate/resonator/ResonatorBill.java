package io.github.matheusanbs.wirelessautomate.resonator;

import io.github.matheusanbs.wirelessautomate.resonator.ResonatorLayout.Face;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorLayout.Member;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorLayout.Part;
import java.util.Comparator;
import java.util.List;

/**
 * A montagem de exemplo que o JEI e o guia mostram: o Núcleo na face SUL, com a Vitrine ou a Faixa, as peças por
 * posição local, a contagem por peça e os membros já montados pelo {@link ResonatorLayout}. Lógica pura.
 */
public final class ResonatorBill {
    /** O Núcleo no centro da face SUL: posição local (2, 2, 4), com a caixa começando em (0, 0, 0). */
    private static final int CORE_X = 2;
    private static final int CORE_Y = 2;
    private static final int CORE_Z = ResonatorLayout.SIZE - 1;

    private final Part[][][] parts;
    private final List<Member> members;

    private ResonatorBill(Part[][][] parts, List<Member> members) {
        this.parts = parts;
        this.members = members;
    }

    public static ResonatorBill of(ResonatorBlueprint.Layout layout) {
        Part[][][] parts = ResonatorBlueprint.expected(Face.SOUTH, layout);
        int size = ResonatorLayout.SIZE;
        ResonatorLayout.Probe probe = (x, y, z) ->
                x >= 0 && x < size && y >= 0 && y < size && z >= 0 && z < size ? parts[x][y][z] : Part.AIR;
        if (!(ResonatorLayout.check(CORE_X, CORE_Y, CORE_Z, Face.SOUTH, probe) instanceof ResonatorLayout.Formed formed)) {
            throw new IllegalStateException("A montagem de exemplo não monta: " + layout);
        }
        List<Member> sorted = formed.members().stream()
                .sorted(Comparator.comparingInt(Member::x).thenComparingInt(Member::y).thenComparingInt(Member::z))
                .toList();
        return new ResonatorBill(parts, sorted);
    }

    public Part at(int x, int y, int z) {
        return parts[x][y][z];
    }

    public int count(Part part) {
        int total = 0;
        for (Member member : members) {
            if (member.part() == part) {
                total++;
            }
        }
        return total;
    }

    /** Todos os membros, em ordem estável (x, y, z). */
    public List<Member> members() {
        return members;
    }

    /** Os membros das primeiras {@code layer} camadas (y de baixo para cima); {@code layer} 5 ou mais é tudo. */
    public List<Member> upTo(int layer) {
        return members.stream().filter(member -> member.y() < layer).toList();
    }
}
