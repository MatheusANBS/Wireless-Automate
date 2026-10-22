package io.github.matheusanbs.wirelessautomate.linker;

import java.util.Locale;

/**
 * Modo do Vinculador: vincular máquinas a um roteador de alcance, ou abrir a tela do roteador que chega
 * numa máquina. Shift + clique no ar alterna. Lógica pura, com JUnit; os codecs do componente ficam no
 * {@link LinkerModeCodecs}, que carrega as classes do Minecraft.
 */
public enum LinkerMode {
    /** Clique num roteador o escolhe; num bloco vincula ou desvincula a máquina; Shift + clique marca a área. */
    LINK,
    /** Clique numa máquina abre a tela do roteador que chega nela. */
    OPEN;

    /** O modo pelo nome gravado; os nomes dos modos antigos (single, area e machines) viram {@link #LINK}. */
    public static LinkerMode byName(String name) {
        return "open".equals(name) ? OPEN : LINK;
    }

    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** O ciclo do Shift + clique no ar. */
    public LinkerMode next() {
        return this == LINK ? OPEN : LINK;
    }

    /** Shift + clique num bloco marca um canto da área (só ao vincular). */
    public boolean marksArea() {
        return this == LINK;
    }
}
