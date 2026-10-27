package io.github.matheusanbs.wirelessautomate.client.tv.editor;

import io.github.matheusanbs.wirelessautomate.menu.TvMenu;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.storage.StorageKind;
import io.github.matheusanbs.wirelessautomate.tv.TvBinding;
import java.util.List;
import net.minecraft.core.GlobalPos;
import org.jetbrains.annotations.Nullable;

/**
 * O que a aba Dado lembra entre uma montagem e outra: os armazenamentos ligados à Tela (da abertura do menu), a fonte
 * que o jogador está olhando quando a peça ainda não tem ligação (a segmentada Rede, Armazenamento, Relógio) e o Baú
 * cujos itens a grade mostra (o do dado da peça ou, no Ícone, o que o jogador escolheu na lista). Zera ao escolher
 * outra peça.
 */
final class TvDataState {
    private final List<TvMenu.LinkChoice> links;
    private @Nullable TvBinding.Source viewed;
    private @Nullable GlobalPos browse;
    private @Nullable GlobalPos requested;
    private String query = "";

    TvDataState(List<TvMenu.LinkChoice> links) {
        this.links = links;
    }

    List<TvMenu.LinkChoice> links() {
        return links;
    }

    /** A fonte que a aba mostra: a da ligação da peça ou, sem ligação, a escolhida na segmentada (Rede por padrão). */
    TvBinding.Source source(@Nullable TvBinding binding) {
        if (binding != null) {
            return binding.source();
        }
        return viewed == null ? TvBinding.Source.NETWORK : viewed;
    }

    void view(TvBinding.Source source) {
        viewed = source;
    }

    @Nullable GlobalPos browse() {
        return browse;
    }

    void browse(@Nullable GlobalPos pos) {
        browse = pos;
    }

    /** Se os itens do Baú em {@code pos} ainda não foram pedidos nesta escolha de peça (pede uma vez). */
    boolean shouldRequest(GlobalPos pos) {
        if (pos.equals(requested)) {
            return false;
        }
        requested = pos;
        return true;
    }

    /** O texto da busca da grade de itens (a grade é refeita com a aba, o texto fica). */
    String query() {
        return query;
    }

    void query(String text) {
        query = text;
    }

    void reset() {
        query = "";
        viewed = null;
        browse = null;
        requested = null;
    }

    /** O tipo de recurso do armazenamento em {@code pos}, pelo que o servidor disse na abertura; Itens se não se sabe. */
    ResourceType typeOf(GlobalPos pos) {
        StorageKind kind = kindOf(pos);
        return kind == null ? ResourceType.ITEM : kind.resource;
    }

    /** O tipo do armazenamento em {@code pos}, ou {@code null} se o servidor não o conhecia (chunk descarregado). */
    @Nullable StorageKind kindOf(GlobalPos pos) {
        for (TvMenu.LinkChoice link : links) {
            if (link.pos().equals(pos)) {
                for (StorageKind kind : StorageKind.values()) {
                    if (kind.getSerializedName().equals(link.kind())) {
                        return kind;
                    }
                }
            }
        }
        return null;
    }
}
