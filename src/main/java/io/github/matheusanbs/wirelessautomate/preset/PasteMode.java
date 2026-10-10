package io.github.matheusanbs.wirelessautomate.preset;

import io.github.matheusanbs.wirelessautomate.linker.LinkerMode;

/**
 * Modo de colar do Configurador: o {@link LinkerMode} do item (componente {@code configurator_mode})
 * junto com o {@code configurator_any_machine}. Lógica pura, com JUnit; o JUnit roda sem as classes do
 * Minecraft, e o {@link LinkerMode} carrega o {@code StringRepresentable}, então o núcleo é por
 * booleanos ({@link #of(boolean, boolean)}, {@link #area()}) e os métodos com {@link LinkerMode} só
 * os traduzem (cobertos pelos GameTests do Configurador).
 * <ul>
 *   <li>{@link #BRUSH}: clique num roteador cola nele (a máquina não importa).</li>
 *   <li>{@link #AREA_SAME}: cola nos roteadores da área presos à mesma máquina da cópia.</li>
 *   <li>{@link #AREA_ANY}: cola em todos os roteadores da área.</li>
 * </ul>
 */
public enum PasteMode {
    BRUSH("brush"),
    AREA_SAME("area_same"),
    AREA_ANY("area_any");

    private final String key;

    PasteMode(String key) {
        this.key = key;
    }

    /** No {@link LinkerMode#SINGLE} é sempre o pincel, com ou sem {@code anyMachine}. */
    public static PasteMode of(LinkerMode mode, boolean anyMachine) {
        return of(mode == LinkerMode.AREA, anyMachine);
    }

    /** Sem área é sempre o pincel, com ou sem {@code anyMachine}. */
    public static PasteMode of(boolean area, boolean anyMachine) {
        if (!area) {
            return BRUSH;
        }
        return anyMachine ? AREA_ANY : AREA_SAME;
    }

    public LinkerMode linkerMode() {
        return area() ? LinkerMode.AREA : LinkerMode.SINGLE;
    }

    /** Se é um dos modos de Área ({@link LinkerMode#AREA}). */
    public boolean area() {
        return this != BRUSH;
    }

    public boolean anyMachine() {
        return this == AREA_ANY;
    }

    /** O ciclo do Shift + clique no ar: Pincel → Área (mesma máquina) → Área (qualquer máquina) → Pincel. */
    public PasteMode next() {
        PasteMode[] all = values();
        return all[(ordinal() + 1) % all.length];
    }

    /** Sufixo das chaves de tradução ({@code brush}, {@code area_same}, {@code area_any}). */
    public String key() {
        return key;
    }
}
