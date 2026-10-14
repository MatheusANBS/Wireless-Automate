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

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("gui");
        THEME = builder
                .comment("Tema das telas do mod: LIGHT (claro) ou DARK (escuro). Também troca pelo botão no cabeçalho das telas.")
                .defineEnum("theme", GuiTheme.LIGHT);
        builder.pop();
        SPEC = builder.build();
    }

    private ClientConfig() {
    }
}
