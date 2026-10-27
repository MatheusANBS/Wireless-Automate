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
                .comment("Qualidade da imagem da Tela de Sinal: 16 (metade), 32 (normal) ou 64 (dobro). A resolução sai do tamanho",
                        "da Tela (128 px por bloco nas pequenas, até 2048 px no maior lado) vezes esta qualidade.")
                .defineInList("pixelsPerBlock", 32, java.util.Arrays.asList(16, 32, 64));
        builder.pop();
        SPEC = builder.build();
    }

    private ClientConfig() {
    }
}
