package io.github.matheusanbs.wirelessautomate.client.tv.pixel;

import io.github.matheusanbs.wirelessautomate.tv.TvPixels;

/**
 * Os quadros da animação no editor de pixel art, os comandos da faixa de quadros sobre a {@link TvPixelSession}.
 * Escolher um quadro não é um passo do desfazer (o pedaço solto assenta no quadro de antes, e a seleção marcada e a
 * área de cópia valem entre quadros); adicionar, duplicar, remover, mudar a ordem, a velocidade e o modo são. Os
 * quadros somados não passam de {@link #maxPixels} (o teto de pixels de uma Tela, que conta os quadros) nem de
 * {@link TvPixels#MAX_FRAMES}. O tocar e pausar da prévia e o papel vegetal são só da vista, fora do desfazer. Lógica
 * pura, com JUnit.
 */
public final class TvPixelFrames {
    private final TvPixelSession session;
    private int maxPixels = Integer.MAX_VALUE;
    private boolean playing = true;
    private boolean onion = true;

    TvPixelFrames(TvPixelSession session) {
        this.session = session;
    }

    private TvPixelCanvas canvas() {
        return session.canvas();
    }

    /** O teto de pixels somados dos quadros ({@code tv.maxImagePixels}, o que uma Tela aceita). */
    public int maxPixels() {
        return maxPixels;
    }

    public void maxPixels(int max) {
        maxPixels = max;
    }

    /** Se cabe mais um quadro: até {@link TvPixels#MAX_FRAMES} e dentro do {@link #maxPixels}. */
    public boolean canAdd() {
        TvPixelCanvas canvas = canvas();
        return canvas.frames() < TvPixels.MAX_FRAMES
                && (long) canvas.width() * canvas.height() * (canvas.frames() + 1) <= maxPixels;
    }

    /** Escolhe o quadro que se desenha; o pedaço solto assenta no de antes e a seleção marcada fica. */
    public void select(int index) {
        TvPixelCanvas canvas = canvas();
        if (index == canvas.current() || index < 0 || index >= canvas.frames()) {
            return;
        }
        session.selection().commit(canvas);
        canvas.select(index);
    }

    /** O quadro anterior ({@code -1}) ou o seguinte ({@code 1}), dando a volta. */
    public void step(int delta) {
        select(Math.floorMod(canvas().current() + delta, canvas().frames()));
    }

    /** Um quadro novo e vazio depois do escolhido, que passa a ser o escolhido, num passo. */
    public boolean add() {
        if (!canAdd()) {
            return false;
        }
        session.beginStep();
        canvas().insertAfterCurrent(new byte[canvas().width() * canvas().height()]);
        return true;
    }

    /** Uma cópia do quadro escolhido logo depois dele, que passa a ser o escolhido, num passo. */
    public boolean duplicate() {
        if (!canAdd()) {
            return false;
        }
        session.beginStep();
        canvas().insertAfterCurrent(canvas().pixels().clone());
        return true;
    }

    /** Tira o quadro escolhido (fica pelo menos um), num passo; a seleção sai. */
    public boolean remove() {
        if (canvas().frames() <= 1) {
            return false;
        }
        session.selection().clear();
        session.beginStep();
        canvas().removeCurrent();
        return true;
    }

    /** Leva o quadro {@code from} para a posição {@code to}, num passo. */
    public void move(int from, int to) {
        int count = canvas().frames();
        if (from == to || from < 0 || to < 0 || from >= count || to >= count) {
            return;
        }
        session.beginStep();
        canvas().move(from, to);
        session.history().dropIfUnchanged(canvas());
    }

    /** A velocidade em quadros por segundo ({@link TvPixels#MIN_FPS} a {@link TvPixels#MAX_FPS}), num passo. */
    public void setFps(int value) {
        int fps = Math.max(TvPixels.MIN_FPS, Math.min(TvPixels.MAX_FPS, value));
        if (fps != canvas().fps()) {
            session.beginStep();
            canvas().setFps(fps);
        }
    }

    /** O modo da animação, num passo. */
    public void setMode(TvPixels.Mode value) {
        if (value != canvas().mode()) {
            session.beginStep();
            canvas().setMode(value);
        }
    }

    public boolean playing() {
        return playing;
    }

    /** Toca ou pausa a prévia (pausada, ela mostra o quadro escolhido). */
    public void togglePlaying() {
        playing = !playing;
    }

    /** O papel vegetal: o quadro anterior apagado por baixo do escolhido. */
    public boolean onion() {
        return onion;
    }

    public void toggleOnion() {
        onion = !onion;
    }

    /** O quadro que fica por baixo no papel vegetal: o anterior ao escolhido, dando a volta; -1 com um quadro só. */
    public int onionFrame() {
        TvPixelCanvas canvas = canvas();
        return onion && canvas.frames() > 1 ? Math.floorMod(canvas.current() - 1, canvas.frames()) : -1;
    }

    /** O quadro que a prévia mostra no instante {@code nowMs}: o do relógio tocando, o escolhido pausada. */
    public int previewFrame(long nowMs) {
        TvPixelCanvas canvas = canvas();
        return playing ? TvPixels.frameAt(nowMs, canvas.frames(), canvas.fps(), canvas.mode()) : canvas.current();
    }
}
