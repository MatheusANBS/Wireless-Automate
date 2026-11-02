package io.github.matheusanbs.wirelessautomate.item.tuner;

import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.range.RemoteSide;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A decisão do Afinador do Configurador: dos papéis lidos em cada face da máquina, por tipo, os modos que a proposta
 * põe (spec {@code 2026-11-02-configurador-afinador-design.md}, "Decisão dos modos"). Lógica pura, sem classes do
 * Minecraft, com JUnit; quem lê as capabilities é o {@link TunerProbe}.
 *
 * <p>As faces vêm pelo índice de {@code Direction.ordinal()} (DOWN 0, UP 1, NORTH 2, SOUTH 3, WEST 4, EAST 5) e as
 * preferências são pelos lados da máquina ({@link RemoteSide}), a partir da frente dela ({@code -1}: sem frente,
 * Norte).
 */
public final class TunerPlan {
    /** Faces de uma máquina. */
    public static final int FACES = 6;

    /** Preferência de Armazém e de Insere: Cima, Trás, Esquerda, Direita, Frente, Baixo. */
    private static final RemoteSide[] INSERT_ORDER = {RemoteSide.TOP, RemoteSide.BACK, RemoteSide.LEFT,
            RemoteSide.RIGHT, RemoteSide.FRONT, RemoteSide.BOTTOM};
    /** Preferência de Extrai: Baixo, Trás, Esquerda, Direita, Frente, Cima. */
    private static final RemoteSide[] EXTRACT_ORDER = {RemoteSide.BOTTOM, RemoteSide.BACK, RemoteSide.LEFT,
            RemoteSide.RIGHT, RemoteSide.FRONT, RemoteSide.TOP};

    private TunerPlan() {
    }

    /** O papel de uma face para um tipo: o que ela aceita e o que ela dá. */
    public enum Role {
        /** Sem a capability, ou sem nada que entra nem sai. */
        NONE,
        /** Só entradas. */
        IN,
        /** Só saídas. */
        OUT,
        /** Entradas e saídas. */
        BOTH
    }

    /**
     * Uma face lida: o papel, se ela é <b>aberta</b> (todo slot ou tanque dela aceita e dá, como num baú, num tanque
     * ou numa bateria; uma máquina com slots de entrada e de saída na mesma face não é), a assinatura, que diz quando
     * duas faces mostram os mesmos slots (número, limites e o que cada um aceita), comparada por {@code equals}, e se a
     * saída dela é só <b>fraca</b> (slots vazios que recusam tudo, sem nada que de fato sai agora).
     *
     * @param present a face tem a capability do tipo (mesmo sem papel): no armazenamento ela vai para Nenhum
     */
    public record Face(Role role, boolean open, Object signature, boolean weakOut, boolean present) {
        /** A face sem a capability. */
        public static final Face NOTHING = new Face(Role.NONE, false, List.of(), false, false);

        public Face(Role role, boolean open, Object signature) {
            this(role, open, signature, false, true);
        }

        public boolean any() {
            return role != Role.NONE;
        }
    }

    /** Como {@link #decide(Face[][], boolean[], int)} para uma máquina de um tipo só, que não é escalar. */
    public static PortMode[] decide(Face[] faces, int front) {
        return decide(new Face[][] {faces}, new boolean[] {false}, front)[0];
    }

    /**
     * Os modos de cada face (índice absoluto), por tipo, na ordem de {@code byType}; {@code null} numa posição deixa
     * a face como está, e {@code null} no lugar do vetor de um tipo diz que nenhuma face tem algo dele (o tipo fica de
     * fora).
     *
     * <ol>
     * <li>Armazenamento: em <b>todo</b> tipo achado, as faces com algo são abertas, AMBOS e com a mesma assinatura (um
     * baú, um tanque, uma bateria): em cada tipo só a face preferida recebe Armazém ({@link PortMode#BOTH}) e as outras
     * faces com a capability dele vão para Nenhum (Armazém e Extrai na mesma máquina a esvaziariam nela mesma).</li>
     * <li>Fora do armazenamento, uma face AMBOS de um tipo escalar ({@code scalar}: energia e Source, em que um
     * consumidor pode dizer que também dá) conta como ENTRADA: escalar nunca recebe Extrai por AMBOS.</li>
     * <li>Faces SAÍDA recebem Extrai, uma por assinatura, pela preferência de Extrai, as de saída de verdade antes das
     * de saída fraca.</li>
     * <li>Sem SAÍDA, a primeira AMBOS pela mesma preferência (de novo a de saída de verdade antes) recebe Extrai.</li>
     * <li>ENTRADA e as AMBOS que sobraram recebem Insere, uma por assinatura, pela preferência de Insere.</li>
     * <li>O resto fica em Nenhum.</li>
     * </ol>
     */
    public static PortMode[][] decide(Face[][] byType, boolean[] scalar, int front) {
        PortMode[][] modes = new PortMode[byType.length][];
        boolean anyType = false;
        boolean storage = true;
        for (Face[] faces : byType) {
            if (faces.length != FACES) {
                throw new IllegalArgumentException("Uma máquina tem " + FACES + " faces: " + faces.length);
            }
            if (has(faces)) {
                anyType = true;
                storage &= storage(faces);
            }
        }
        if (!anyType) {
            return modes;
        }
        for (int type = 0; type < byType.length; type++) {
            Face[] faces = byType[type];
            if (!has(faces)) {
                continue;
            }
            modes[type] = storage ? storageModes(faces, front) : machineModes(scalar[type] ? asInputs(faces) : faces,
                    front);
        }
        return modes;
    }

    private static boolean has(Face[] faces) {
        for (Face face : faces) {
            if (face.any()) {
                return true;
            }
        }
        return false;
    }

    /** As faces com algo são abertas, AMBOS e da mesma assinatura. */
    private static boolean storage(Face[] faces) {
        Object signature = null;
        for (Face face : faces) {
            if (!face.any()) {
                continue;
            }
            if (face.role() != Role.BOTH || !face.open()) {
                return false;
            }
            if (signature == null) {
                signature = face.signature();
            } else if (!signature.equals(face.signature())) {
                return false;
            }
        }
        return true;
    }

    private static PortMode[] storageModes(Face[] faces, int front) {
        PortMode[] modes = new PortMode[FACES];
        for (int face = 0; face < FACES; face++) {
            if (faces[face].present()) {
                modes[face] = PortMode.NONE;
            }
        }
        modes[first(faces, front, INSERT_ORDER, Role.BOTH, false)] = PortMode.BOTH;
        return modes;
    }

    /** As faces AMBOS de um tipo escalar como ENTRADA. */
    private static Face[] asInputs(Face[] faces) {
        Face[] inputs = faces.clone();
        for (int face = 0; face < FACES; face++) {
            Face read = inputs[face];
            if (read.role() == Role.BOTH) {
                inputs[face] = new Face(Role.IN, read.open(), read.signature(), false, read.present());
            }
        }
        return inputs;
    }

    private static PortMode[] machineModes(Face[] faces, int front) {
        PortMode[] modes = new PortMode[FACES];
        Arrays.fill(modes, PortMode.NONE);
        List<Object> extracted = new ArrayList<>(FACES);
        boolean outputs = false;
        for (boolean weak : new boolean[] {false, true}) {
            for (RemoteSide side : EXTRACT_ORDER) {
                int face = side.toAbsolute(front);
                Face read = faces[face];
                if (read.role() == Role.OUT && read.weakOut() == weak) {
                    outputs = true;
                    if (!extracted.contains(read.signature())) {
                        extracted.add(read.signature());
                        modes[face] = PortMode.EXTRACT;
                    }
                }
            }
        }
        if (!outputs) {
            int both = first(faces, front, EXTRACT_ORDER, Role.BOTH, true);
            if (both >= 0) {
                modes[both] = PortMode.EXTRACT;
            }
        }
        List<Object> inserted = new ArrayList<>(FACES);
        for (RemoteSide side : INSERT_ORDER) {
            int face = side.toAbsolute(front);
            Role role = faces[face].role();
            if ((role == Role.IN || role == Role.BOTH) && modes[face] == PortMode.NONE
                    && !inserted.contains(faces[face].signature())) {
                inserted.add(faces[face].signature());
                modes[face] = PortMode.INSERT;
            }
        }
        return modes;
    }

    /**
     * A primeira face com o papel {@code role} pela ordem {@code order}; com {@code realFirst}, as de saída de verdade
     * antes das de saída fraca. {@code -1} sem nenhuma.
     */
    private static int first(Face[] faces, int front, RemoteSide[] order, Role role, boolean realFirst) {
        for (boolean weak : realFirst ? new boolean[] {false, true} : new boolean[] {false}) {
            for (RemoteSide side : order) {
                int face = side.toAbsolute(front);
                if (faces[face].role() == role && (!realFirst || faces[face].weakOut() == weak)) {
                    return face;
                }
            }
        }
        return -1;
    }
}
