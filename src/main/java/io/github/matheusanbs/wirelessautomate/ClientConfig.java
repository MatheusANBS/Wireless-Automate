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
    public static final ModConfigSpec.IntValue TV_VRAM_BUDGET_MB;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("gui");
        THEME = builder
                .comment("Tema das telas do mod: LIGHT (claro) ou DARK (escuro). Também troca pelo botão no cabeçalho das telas.")
                .defineEnum("theme", GuiTheme.LIGHT);
        builder.pop();
        builder.push("tv");
        TV_PIXELS_PER_BLOCK = builder
                .comment("Qualidade da imagem da Tela de Sinal: 16 = metade, 32 = normal, 64 = dobro.",
                        "A resolução segue o tamanho em que a Tela aparece na sua janela: na qualidade normal, de 32 a",
                        "512 px por bloco, mais de perto e menos de longe, nunca mais de 4096 px no maior lado.")
                .defineInList("pixelsPerBlock", 32, java.util.Arrays.asList(16, 32, 64));
        TV_VRAM_BUDGET_MB = builder
                .comment("Memória de vídeo máxima das imagens das Telas de Sinal, em MB (64 a 4096).",
                        "Quando as Telas à vista pedem mais que isso, as que aparecem menores na janela perdem",
                        "resolução primeiro.")
                .defineInRange("vramBudgetMb", 1024, 64, 4096);
        builder.pop();
        SPEC = builder.build();
    }

    private ClientConfig() {
    }
}
