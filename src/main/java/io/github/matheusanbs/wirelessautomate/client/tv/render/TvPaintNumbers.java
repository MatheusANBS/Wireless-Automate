package io.github.matheusanbs.wirelessautomate.client.tv.render;

import io.github.matheusanbs.wirelessautomate.client.tv.TvClientValues;
import io.github.matheusanbs.wirelessautomate.client.tv.TvPreviewValues;
import io.github.matheusanbs.wirelessautomate.tv.TvBinding;
import io.github.matheusanbs.wirelessautomate.tv.TvLayout;

/**
 * De onde a pintura tira o número de uma ligação. Na parede, o que o servidor mandou para a Tela (indexado pelas
 * ligações do layout). Na miniatura do editor ({@code preview}), primeiro a prévia do rascunho
 * ({@link TvPreviewValues}), que cobre as ligações que a Tela salva ainda não tem, e, se ela não tem número (ainda
 * não chegou ou sem acesso), o da Tela. Sem alocar.
 */
final class TvPaintNumbers {
    private TvPaintNumbers() {
    }

    static double of(TvBinding binding, TvLayout values, long pos, int revision, boolean preview) {
        if (preview) {
            double previewed = TvPreviewValues.value(binding);
            if (!Double.isNaN(previewed)) {
                return previewed;
            }
        }
        return TvClientValues.value(pos, revision, values.bindingIndex(binding));
    }
}
