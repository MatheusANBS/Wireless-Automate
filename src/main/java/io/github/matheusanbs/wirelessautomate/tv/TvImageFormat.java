package io.github.matheusanbs.wirelessautomate.tv;

import org.jetbrains.annotations.Nullable;

/**
 * O formato de uma imagem enviada à Tela de Sinal (puro): PNG, JPG ou GIF, os três que o leitor STB do cliente abre.
 * O nome é a extensão do arquivo no armazém ({@code <resumo>.<ext>}) e a chave no NBT.
 */
public enum TvImageFormat {
    PNG("png"),
    JPG("jpg"),
    GIF("gif");

    private static final TvImageFormat[] FORMATS = values();

    private final String ext;

    TvImageFormat(String ext) {
        this.ext = ext;
    }

    public String ext() {
        return ext;
    }

    /** O formato pela extensão (a do armazém), ou {@code null}. */
    public static @Nullable TvImageFormat byExt(String ext) {
        for (TvImageFormat format : FORMATS) {
            if (format.ext.equals(ext)) {
                return format;
            }
        }
        return null;
    }

    /** O formato pela posição (a do pacote), ou {@code null} fora da faixa. */
    public static @Nullable TvImageFormat byId(int id) {
        return id >= 0 && id < FORMATS.length ? FORMATS[id] : null;
    }
}
