package io.github.matheusanbs.wirelessautomate.recipe.edit;

/**
 * Quando o editor de receitas pode recarregar os recursos. Uma recarga por vez: duas sobrepostas quebram mods
 * como o KubeJS e dobram a memória. As pendências saem ao começar e voltam se a recarga falhar; depois de uma
 * falha, fechar a tela não tenta de novo (só o botão). Sem classes do Minecraft.
 */
public final class ReloadGate {
    private int pending;
    private int taken;
    private boolean running;
    private boolean failed;

    /** Uma receita gravada ou apagada. */
    public synchronized void changed() {
        pending++;
        failed = false;
    }

    /** Começa uma recarga do editor; {@code false} se já há uma rodando. */
    public synchronized boolean tryStart() {
        if (running) {
            return false;
        }
        running = true;
        taken = pending;
        pending = 0;
        return true;
    }

    /** Fim da recarga começada por {@link #tryStart}. */
    public synchronized void finish(boolean ok) {
        running = false;
        if (!ok) {
            pending += taken;
        }
        failed = !ok;
        taken = 0;
    }

    /**
     * Fim de uma recarga qualquer do servidor (também a do {@code /reload}). Durante uma recarga do editor, o
     * {@link #finish} decide; fora dela, tudo o que estava gravado foi aplicado.
     */
    public synchronized void serverReloaded() {
        if (!running) {
            pending = 0;
            failed = false;
        }
    }

    /** Fechar a tela recarrega: há pendências, nada rodando e a última recarga não falhou. */
    public synchronized boolean reloadOnClose() {
        return pending > 0 && !running && !failed;
    }

    public synchronized int pending() {
        return pending;
    }

    public synchronized boolean running() {
        return running;
    }

    public synchronized void reset() {
        pending = 0;
        taken = 0;
        running = false;
        failed = false;
    }
}
