package io.github.matheusanbs.wirelessautomate;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Config do cliente ({@code config/wirelessautomate-client.toml}): preferências de cada jogador,
 * que não vão para o servidor. Sem nada do pacote {@code client}: a classe é carregada também no
 * servidor dedicado, que só ignora a config do cliente.
 */
public final class ClientConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.EnumValue<GuiTheme> THEME;
    public static final ModConfigSpec.ConfigValue<Integer> TV_PIXELS_PER_BLOCK;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("gui");
        THEME = builder
                .comment("Tema das telas do mod: LIGHT (claro) ou DARK (escuro). Também troca pelo botão no cabeçalho das telas.")
                .defineEnum("theme", GuiTheme.LIGHT);
        builder.pop();
        builder.push("tv");
        TV_PIXELS_PER_BLOCK = builder
                .comment("Qualidade da textura da Tela de Sinal: 16 = metade, 32 = normal, 64 = dobro.",
                        "Na qualidade normal, as Telas de até 16 blocos no maior lado desenham a 256 px por bloco;",
                        "as maiores dividem 4096 px pelo maior lado (64 px por bloco numa de 64 blocos).",
                        "A textura nunca passa de 4096 px no maior lado nem fica abaixo de 32 px por bloco.")
                .defineInList("pixelsPerBlock", 32, java.util.Arrays.asList(16, 32, 64));
        builder.pop();
        SPEC = builder.build();
    }

    private ClientConfig() {
    }
}
