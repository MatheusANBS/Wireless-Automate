package io.github.matheusanbs.wirelessautomate.tv;

import org.jetbrains.annotations.Nullable;

/**
 * Os tetos das imagens enviadas, lidos da config {@code tv.images} a cada envio (puro, com JUnit no {@link #check}).
 * {@code maxWorldBytes} já vem 0 (sem teto) no servidor integrado, o single-player e a LAN.
 *
 * @param enabled         {@code tv.images.enabled}: desligado, nada é aceito e as Telas mostram o quadro neutro
 * @param opsOnly         {@code tv.images.uploaders = ops}
 * @param bytesPerMinute  {@code tv.images.uploadBytesPerMinute}, por jogador
 * @param maxFileBytes    {@code tv.images.maxFileBytes}
 * @param maxSize         {@code tv.images.maxSize}, o maior lado
 * @param maxFrames       {@code tv.images.maxFrames}, num GIF
 * @param maxWorldBytes   {@code tv.images.maxWorldBytes} (0 = sem teto)
 * @param maxPlayerBytes  {@code tv.images.maxBytesPerPlayer} (0 = sem cota)
 * @param maxPerScreen    {@code tv.images.maxPerScreen}, imagens distintas num layout
 * @param cooldownMinutes {@code tv.images.abuseCooldownMinutes} (0 = sem trava)
 */
public record TvImageLimits(boolean enabled, boolean opsOnly, long bytesPerMinute, int maxFileBytes, int maxSize,
        int maxFrames, long maxWorldBytes, long maxPlayerBytes, int maxPerScreen, int cooldownMinutes) {

    /** Os padrões da config. */
    public static final TvImageLimits DEFAULT = new TvImageLimits(true, false, 32L << 20, 8 << 20, 1024, 240,
            4L << 30, 0, 8, 60);

    /**
     * O que o cabeçalho declarado no começo de um envio fere nestes tetos, ou {@code null} se cabe: o tamanho do
     * arquivo, o maior lado e os quadros (só o GIF tem mais de um).
     */
    public @Nullable TvImageRefusal check(TvImageFormat format, int width, int height, int frames, long bytes) {
        if (bytes <= 0 || bytes > maxFileBytes) {
            return TvImageRefusal.FILE_TOO_BIG;
        }
        if (width < 1 || height < 1 || width > maxSize || height > maxSize) {
            return TvImageRefusal.TOO_LARGE;
        }
        if (frames < 1 || frames > (format == TvImageFormat.GIF ? maxFrames : 1)) {
            return TvImageRefusal.TOO_MANY_FRAMES;
        }
        return null;
    }

    /** O teto do mundo e a cota do jogador, com {@code size} bytes novos; {@code null} se cabe. */
    public @Nullable TvImageRefusal checkSpace(long worldBytes, long playerBytes, long size) {
        if (maxWorldBytes > 0 && worldBytes + size > maxWorldBytes) {
            return TvImageRefusal.WORLD_FULL;
        }
        if (maxPlayerBytes > 0 && playerBytes + size > maxPlayerBytes) {
            return TvImageRefusal.QUOTA;
        }
        return null;
    }

    /** Os mesmos tetos sem o do mundo (o servidor integrado). */
    public TvImageLimits withoutWorldCap() {
        return new TvImageLimits(enabled, opsOnly, bytesPerMinute, maxFileBytes, maxSize, maxFrames, 0,
                maxPlayerBytes, maxPerScreen, cooldownMinutes);
    }
}
