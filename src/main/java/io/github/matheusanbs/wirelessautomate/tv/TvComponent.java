package io.github.matheusanbs.wirelessautomate.tv;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;

/**
 * Um componente: um nome e um grupo de peças prontas para inserir numa Tela. As caixas são relativas ao canto de
 * cima à esquerda do componente. Os componentes prontos do mod guardam como nome a chave de tradução.
 */
public record TvComponent(String name, List<TvPiece> pieces) {
    public static final int MAX_NAME = 32;

    public static final Codec<TvComponent> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("name").forGetter(TvComponent::name),
            TvPiece.CODEC.listOf().fieldOf("pieces").forGetter(TvComponent::pieces)).apply(i, TvComponent::new));

    public TvComponent {
        name = TvText.cut(name, MAX_NAME);
        pieces = List.copyOf(pieces);
    }
}
