package io.github.matheusanbs.wirelessautomate.tv;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Um componente: um nome e um grupo de peças prontas para inserir numa Tela. As caixas são relativas ao canto de
 * cima à esquerda do componente. Os componentes prontos do mod guardam como nome a chave de tradução.
 *
 * <p>Com {@link #layout()} presente, é um <b>layout salvo</b>: a Tela inteira do jogador, com as caixas na área de
 * origem. Fica na mesma biblioteca (mesmos tetos e cota) e aparece no seletor de layouts, não na lista de
 * Componentes; ao aplicar, as peças são escaladas para a área da Tela ({@link TvSavedLayouts#scale}).
 *
 * <p>As imagens enviadas que as peças e o fundo apontam vão junto em {@link #pictures()} (só a referência, como no
 * layout; chave opcional, um componente antigo lê vazio): o armazém conta o componente como uso delas, e aplicá-lo
 * noutra Tela leva as imagens.
 */
public record TvComponent(String name, List<TvPiece> pieces, Optional<Layout> layout, List<TvPicture> pictures) {
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
            Layout.CODEC.optionalFieldOf("layout").forGetter(TvComponent::layout),
            TvPicture.CODEC.listOf().lenientOptionalFieldOf("pictures", List.of()).forGetter(TvComponent::pictures))
            .apply(i, TvComponent::new));

    public TvComponent {
        name = TvText.cut(name, MAX_NAME);
        pieces = List.copyOf(pieces);
        pictures = pictures.size() > TvLayout.MAX_PICTURES ? List.copyOf(pictures.subList(0, TvLayout.MAX_PICTURES))
                : List.copyOf(pictures);
    }

    /** Um componente sem imagem enviada. */
    public TvComponent(String name, List<TvPiece> pieces, Optional<Layout> layout) {
        this(name, pieces, layout, List.of());
    }

    /** Um componente comum, sem layout. */
    public TvComponent(String name, List<TvPiece> pieces) {
        this(name, pieces, Optional.empty());
    }

    /** O mesmo componente com as imagens trocadas. */
    public TvComponent withPictures(List<TvPicture> next) {
        return new TvComponent(name, pieces, layout, next);
    }

    /**
     * As imagens de {@code from} que as peças ou o fundo apontam, sem repetir: o que um componente ou um layout salvo
     * feito dessas peças leva.
     */
    public static List<TvPicture> picturesOf(List<TvPiece> pieces, Optional<UUID> background, List<TvPicture> from) {
        if (from.isEmpty()) {
            return List.of();
        }
        List<TvPicture> out = new ArrayList<>();
        for (TvPicture picture : from) {
            UUID id = picture.id();
            boolean used = background.filter(id::equals).isPresent();
            for (int i = 0; !used && i < pieces.size(); i++) {
                TvPiece piece = pieces.get(i);
                used = piece.type() == TvPieceType.IMAGE && piece.style().part().image().filter(id::equals).isPresent();
            }
            if (used && !out.contains(picture)) {
                out.add(picture);
            }
        }
        return out;
    }

    /** Os resumos das imagens. */
    public List<String> pictureHashes() {
        List<String> out = new ArrayList<>(pictures.size());
        for (TvPicture picture : pictures) {
            out.add(picture.hash());
        }
        return out;
    }

    public boolean isLayout() {
        return layout.isPresent();
    }
}
