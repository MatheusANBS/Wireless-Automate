package io.github.matheusanbs.wirelessautomate.linker;

import io.github.matheusanbs.wirelessautomate.range.RemoteSide;
import java.util.ArrayList;
import java.util.List;

/**
 * Faces que o Vinculador vincula ou desvincula (lógica pura): os lados relativos à máquina presa do
 * roteador ({@link RemoteSide}, Esquerda e Direita vistas de frente), guardados como máscara de 6 bits
 * pelo {@code ordinal} do lado. {@link #ALL} (63) é "Todas"; vazio é recusado na hora de vincular.
 *
 * <p>O roteador guarda as faces em lados absolutos: {@link #absoluteMask(int)} converte pela frente
 * da máquina (−1 ou vertical = Norte, como no {@link RemoteSide}).
 */
public record LinkerFaces(int mask) {
    private static final RemoteSide[] SIDES = RemoteSide.values();
    private static final int FULL = (1 << SIDES.length) - 1;

    /** As seis faces (o padrão). */
    public static final LinkerFaces ALL = new LinkerFaces(FULL);
    /** Nenhuma face. */
    public static final LinkerFaces NONE = new LinkerFaces(0);

    public LinkerFaces {
        mask &= FULL;
    }

    /** Da máscara gravada ou recebida; bits acima dos seis lados são descartados. */
    public static LinkerFaces of(int mask) {
        return new LinkerFaces(mask);
    }

    public boolean isAll() {
        return mask == FULL;
    }

    public boolean isEmpty() {
        return mask == 0;
    }

    public boolean contains(RemoteSide side) {
        return (mask & bit(side)) != 0;
    }

    /** Marca ou desmarca o lado. */
    public LinkerFaces toggle(RemoteSide side) {
        return new LinkerFaces(mask ^ bit(side));
    }

    /** Os lados marcados, na ordem dos botões (Frente, Trás, Esquerda, Direita, Cima, Baixo). */
    public List<RemoteSide> marked() {
        List<RemoteSide> list = new ArrayList<>();
        for (RemoteSide side : SIDES) {
            if (contains(side)) {
                list.add(side);
            }
        }
        return list;
    }

    /**
     * Os lados marcados como faces absolutas de uma máquina virada para {@code front} (índice de
     * {@code Direction}; −1 = sem frente): bit = índice da {@code Direction}.
     */
    public int absoluteMask(int front) {
        int absolute = 0;
        for (RemoteSide side : marked()) {
            absolute |= 1 << side.toAbsolute(front);
        }
        return absolute;
    }

    private static int bit(RemoteSide side) {
        return 1 << side.ordinal();
    }
}
