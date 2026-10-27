package io.github.matheusanbs.wirelessautomate.tv;

/**
 * A regra de um clique do Vinculador num bloco com uma Tela escolhida, pura (com JUnit): o que o clique faz, na ordem
 * das recusas. Quem não edita a Tela nem o bloco não muda nada; um armazenamento já ligado se desliga (mesmo com a
 * lista cheia); o resto só liga um armazenamento do mod, até o teto.
 */
public final class TvLinkRule {
    /** O que o clique faz. */
    public enum Action {
        LINK,
        UNLINK,
        NO_PERMISSION,
        NOT_A_STORAGE,
        FULL
    }

    private TvLinkRule() {
    }

    public static Action decide(boolean allowed, boolean linked, boolean storage, int links, int max) {
        if (!allowed) {
            return Action.NO_PERMISSION;
        }
        if (linked) {
            return Action.UNLINK;
        }
        if (!storage) {
            return Action.NOT_A_STORAGE;
        }
        return links >= max ? Action.FULL : Action.LINK;
    }
}
