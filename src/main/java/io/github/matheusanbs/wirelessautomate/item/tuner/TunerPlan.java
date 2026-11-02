package io.github.matheusanbs.wirelessautomate.item.tuner;

import io.github.matheusanbs.wirelessautomate.network.PortMode;
import io.github.matheusanbs.wirelessautomate.range.RemoteSide;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A decisão do Afinador do Configurador para um tipo de recurso: dos papéis lidos em cada face da máquina, os modos
 * que a proposta põe (spec {@code 2026-11-02-configurador-afinador-design.md}, "Decisão dos modos"). Lógica pura, sem
 * classes do Minecraft, com JUnit; quem lê as capabilities é o {@link TunerProbe}.
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
     * ou numa bateria; uma máquina com slots de entrada e de saída na mesma face não é) e a assinatura, que diz quando
     * duas faces mostram os mesmos slots (número, limites e o que cada um aceita); comparada por {@code equals}.
     */
    public record Face(Role role, boolean open, Object signature) {
        /** A face sem nada. */
        public static final Face NOTHING = new Face(Role.NONE, false, List.of());

        public boolean any() {
            return role != Role.NONE;
        }
    }

    /**
     * Os modos de cada face (índice absoluto) para um tipo; {@code null} numa posição deixa a face como está, e
     * {@code null} no lugar do vetor diz que nenhuma face tem algo deste tipo (o tipo fica de fora).
     *
     * <ol>
     * <li>Armazenamento: todas as faces com algo são abertas, AMBOS e com a mesma assinatura: só a preferida recebe
     * Armazém ({@link PortMode#BOTH}); as outras ficam como estão.</li>
     * <li>Faces SAÍDA recebem Extrai, uma por assinatura, pela preferência de Extrai.</li>
     * <li>Sem SAÍDA, a primeira AMBOS pela mesma preferência recebe Extrai.</li>
     * <li>ENTRADA e as AMBOS que sobraram recebem Insere, uma por assinatura, pela preferência de Insere.</li>
     * <li>O resto fica em Nenhum.</li>
     * </ol>
     */
    public static PortMode[] decide(Face[] faces, int front) {
        if (faces.length != FACES) {
            throw new IllegalArgumentException("Uma máquina tem " + FACES + " faces: " + faces.length);
        }
        boolean any = false;
        boolean storage = true;
        Object signature = null;
        for (Face face : faces) {
            if (!face.any()) {
                continue;
            }
            any = true;
            if (face.role() != Role.BOTH || !face.open()) {
                storage = false;
            } else if (signature == null) {
                signature = face.signature();
            } else if (!signature.equals(face.signature())) {
                storage = false;
            }
        }
        if (!any) {
            return null;
        }
        PortMode[] modes = new PortMode[FACES];
        if (storage) {
            modes[first(faces, front, INSERT_ORDER, Role.BOTH)] = PortMode.BOTH;
            return modes;
        }
        Arrays.fill(modes, PortMode.NONE);
        List<Object> extracted = new ArrayList<>(FACES);
        boolean outputs = false;
        for (RemoteSide side : EXTRACT_ORDER) {
            int face = side.toAbsolute(front);
            if (faces[face].role() == Role.OUT) {
                outputs = true;
                if (!extracted.contains(faces[face].signature())) {
                    extracted.add(faces[face].signature());
                    modes[face] = PortMode.EXTRACT;
                }
            }
        }
        if (!outputs) {
            int both = first(faces, front, EXTRACT_ORDER, Role.BOTH);
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

    /** A primeira face com o papel {@code role} pela ordem {@code order}; {@code -1} sem nenhuma. */
    private static int first(Face[] faces, int front, RemoteSide[] order, Role role) {
        for (RemoteSide side : order) {
            int face = side.toAbsolute(front);
            if (faces[face].role() == role) {
                return face;
            }
        }
        return -1;
    }
}
