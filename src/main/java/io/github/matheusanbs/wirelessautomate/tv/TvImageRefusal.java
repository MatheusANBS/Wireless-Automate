package io.github.matheusanbs.wirelessautomate.tv;

import java.util.Locale;
import org.jetbrains.annotations.Nullable;

/**
 * Por que um envio de imagem foi recusado (puro). O aviso de cada um é
 * {@code message.wirelessautomate.tv.image.<nome em minúsculas>}, nos dois {@code lang}; a resposta ao cliente leva o
 * nome.
 */
public enum TvImageRefusal {
    /** {@code tv.images.enabled} desligado. */
    DISABLED,
    /** {@code tv.images.uploaders = ops} e o jogador não é operador. */
    NOT_ALLOWED,
    /** Na lista de banidos ({@code /wa screen image ban}). */
    BANNED,
    /** Travado pela trava automática. */
    COOLDOWN,
    /** Passaria do limite de bytes por minuto. */
    RATE,
    FILE_TOO_BIG,
    TOO_LARGE,
    TOO_MANY_FRAMES,
    /** Não é PNG, JPG nem GIF que o leitor abra, ou o cabeçalho está quebrado. */
    BAD_FORMAT,
    WORLD_FULL,
    QUOTA,
    LIBRARY_FULL,
    /** Envios demais em curso no servidor. */
    BUSY,
    /** O que chegou não bate com o que foi declarado (resumo, tamanho, cabeçalho ou ordem dos pedaços). */
    MISMATCH,
    /** 30 s sem pedaço. */
    TIMEOUT,
    /** O arquivo não pôde ser gravado no armazém. */
    FAILED;

    private static final TvImageRefusal[] VALUES = values();

    public String key() {
        return "message.wirelessautomate.tv.image." + name().toLowerCase(Locale.ROOT);
    }

    /** Pelo nome (o da resposta), ou {@code null}. */
    public static @Nullable TvImageRefusal byName(String name) {
        for (TvImageRefusal refusal : VALUES) {
            if (refusal.name().equals(name)) {
                return refusal;
            }
        }
        return null;
    }
}
