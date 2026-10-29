package io.github.matheusanbs.wirelessautomate.tv;

import io.github.matheusanbs.wirelessautomate.Config;
import java.util.Arrays;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * A seção {@code tv.images} da config do servidor: as imagens enviadas pelos jogadores à Tela de Sinal. Definida pelo
 * {@link Config} dentro da seção {@code tv} ({@link #define}) e lida a cada envio ({@link #limits}).
 */
public final class TvImageConfig {
    private static ModConfigSpec.BooleanValue enabled;
    private static ModConfigSpec.ConfigValue<String> uploaders;
    private static ModConfigSpec.LongValue bytesPerMinute;
    private static ModConfigSpec.IntValue maxFileBytes;
    private static ModConfigSpec.IntValue maxSize;
    private static ModConfigSpec.IntValue maxFrames;
    private static ModConfigSpec.LongValue maxWorldBytes;
    private static ModConfigSpec.LongValue maxBytesPerPlayer;
    private static ModConfigSpec.IntValue maxPerScreen;
    private static ModConfigSpec.IntValue abuseCooldownMinutes;

    private TvImageConfig() {
    }

    /** Define a seção {@code images} no {@code builder}, que está dentro da {@code tv}. */
    public static void define(ModConfigSpec.Builder builder) {
        builder.push("images");
        enabled = builder
                .comment("Imagens enviadas pelos jogadores (PNG, JPG e GIF) na peça Imagem e no fundo em mosaico da Tela "
                        + "de Sinal. Desligado, nenhum envio é aceito, nenhuma imagem vai aos clientes e as Telas mostram "
                        + "um quadro neutro no lugar delas. A imagem nunca vem de um link: é sempre um arquivo enviado e "
                        + "guardado no mundo (data/wirelessautomate/images).")
                .define("enabled", true);
        uploaders = builder
                .comment("Quem pode enviar imagens: all (todos) ou ops (só operadores).")
                // Arrays.asList e não List.of: a correção do arquivo testa contains(null), que o List.of não aceita.
                .defineInList("uploaders", "all", Arrays.asList("all", "ops"));
        bytesPerMinute = builder
                .comment("Bytes de imagem que cada jogador pode enviar por minuto (o arquivo inteiro conta no começo do "
                        + "envio). Quem bate no limite 3 vezes em 10 minutos fica abuseCooldownMinutes sem enviar.")
                .defineInRange("uploadBytesPerMinute", 32L << 20, 1L << 20, 1L << 30);
        maxFileBytes = builder
                .comment("Tamanho máximo de um arquivo de imagem, em bytes.")
                .defineInRange("maxFileBytes", 8 << 20, 64 << 10, 32 << 20);
        maxSize = builder
                .comment("Maior lado de uma imagem, em pixels. Uma imagem de 1024 × 1024 ocupa uns 5,3 MB de memória de "
                        + "vídeo em cada cliente que a vê.")
                .defineInRange("maxSize", 1024, 64, TvPicture.HARD_SIZE);
        maxFrames = builder
                .comment("Quadros de um GIF animado, no máximo.")
                .defineInRange("maxFrames", 240, 1, TvPicture.HARD_FRAMES);
        maxWorldBytes = builder
                .comment("Teto, em bytes, de todas as imagens do mundo somadas, num servidor dedicado (0 = sem teto). No "
                        + "single-player e em LAN não há teto. Acima dele, envio novo é recusado com aviso.")
                .defineInRange("maxWorldBytes", 4L << 30, 0L, 1L << 40);
        maxBytesPerPlayer = builder
                .comment("Cota, em bytes, das imagens que cada jogador enviou (0 = sem cota, o padrão).")
                .defineInRange("maxBytesPerPlayer", 0L, 0L, 1L << 40);
        maxPerScreen = builder
                .comment("Imagens enviadas distintas num layout de Tela, no máximo.")
                .defineInRange("maxPerScreen", 8, 1, TvLayout.MAX_PICTURES);
        abuseCooldownMinutes = builder
                .comment("Minutos sem enviar imagens para quem bate no limite por minuto 3 vezes em 10 minutos (0 desliga "
                        + "a trava). Os operadores online recebem um aviso e o log ganha uma linha; ninguém é expulso.")
                .defineInRange("abuseCooldownMinutes", 60, 0, 10_080);
        builder.pop();
    }

    /** Os tetos de agora; no servidor integrado, sem o teto do mundo. */
    public static TvImageLimits limits(MinecraftServer server) {
        TvImageLimits limits = new TvImageLimits(Config.read(enabled), "ops".equals(Config.read(uploaders)),
                Config.read(bytesPerMinute), Config.read(maxFileBytes), Config.read(maxSize), Config.read(maxFrames),
                Config.read(maxWorldBytes), Config.read(maxBytesPerPlayer), Config.read(maxPerScreen),
                Config.read(abuseCooldownMinutes));
        return server.isDedicatedServer() || TvImageStore.dedicatedForTests ? limits : limits.withoutWorldCap();
    }

    /** Se as imagens estão ligadas ({@code tv.images.enabled}). */
    public static boolean enabled() {
        return Config.read(enabled);
    }

    public static ModConfigSpec.BooleanValue enabledValue() {
        return enabled;
    }

    public static ModConfigSpec.ConfigValue<String> uploadersValue() {
        return uploaders;
    }

    public static ModConfigSpec.LongValue bytesPerMinuteValue() {
        return bytesPerMinute;
    }

    public static ModConfigSpec.LongValue maxWorldBytesValue() {
        return maxWorldBytes;
    }

    public static ModConfigSpec.LongValue maxBytesPerPlayerValue() {
        return maxBytesPerPlayer;
    }
}
