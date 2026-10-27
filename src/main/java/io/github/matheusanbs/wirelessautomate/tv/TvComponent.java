package io.github.matheusanbs.wirelessautomate.tv;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;

/**
 * Um componente: um nome e um grupo de peças prontas para inserir numa Tela. As caixas são relativas ao canto de
 * cima à esquerda do componente. Os componentes prontos do mod guardam como nome a chave de tradução.
 *
 * <p>Com {@link #layout()} presente, é um <b>layout salvo</b>: a Tela inteira do jogador, com as caixas na área de
 * origem. Fica na mesma biblioteca (mesmos tetos e cota) e aparece no seletor de layouts, não na lista de
 * Componentes; ao aplicar, as peças são escaladas para a área da Tela ({@link TvSavedLayouts#scale}).
 */
public record TvComponent(String name, List<TvPiece> pieces, Optional<Layout> layout) {
    public static final int MAX_NAME = 32;

    /** O que um layout salvo guarda além das peças: a área de origem em pixels de desenho, o fundo e as linhas. */
    public record Layout(int width, int height, TvBackground background, boolean scanlines) {
        public static final Codec<Layout> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("width").forGetter(Layout::width),
                Codec.INT.fieldOf("height").forGetter(Layout::height),
                TvBackground.CODEC.optionalFieldOf("background", TvBackground.DEFAULT).forGetter(Layout::background),
                Codec.BOOL.optionalFieldOf("scanlines", false).forGetter(Layout::scanlines)).apply(i, Layout::new));

        public Layout {
            width = Math.max(1, width);
            height = Math.max(1, height);
        }
    }

    public static final Codec<TvComponent> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("name").forGetter(TvComponent::name),
            TvPiece.CODEC.listOf().fieldOf("pieces").forGetter(TvComponent::pieces),
            Layout.CODEC.optionalFieldOf("layout").forGetter(TvComponent::layout)).apply(i, TvComponent::new));

    public TvComponent {
        name = TvText.cut(name, MAX_NAME);
        pieces = List.copyOf(pieces);
    }

    /** Um componente comum, sem layout. */
    public TvComponent(String name, List<TvPiece> pieces) {
        this(name, pieces, Optional.empty());
    }

    public boolean isLayout() {
        return layout.isPresent();
    }
}
