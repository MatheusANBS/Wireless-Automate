package io.github.matheusanbs.wirelessautomate.client.tv.editor;

import io.github.matheusanbs.wirelessautomate.packet.TvLayoutAckPayload;
import io.github.matheusanbs.wirelessautomate.tv.TvArea;
import io.github.matheusanbs.wirelessautomate.tv.TvBlockEntity;
import io.github.matheusanbs.wirelessautomate.tv.TvComponent;
import io.github.matheusanbs.wirelessautomate.tv.TvLayout;
import io.github.matheusanbs.wirelessautomate.tv.TvPiece;
import io.github.matheusanbs.wirelessautomate.tv.TvPieceType;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

/**
 * O documento que as partes do editor dividem: o {@link TvEditorModel} do rascunho, a Tela no cliente (área,
 * revisão e o layout salvo, relidos uma vez por tick, nunca por quadro) e as edições que as partes pedem, todas pelo
 * {@link TvEditOps}. Também acompanha o salvar: o layout mandado espera a revisão nova do servidor, que vira a base;
 * uma revisão nova que não é a nossa nem só a área cortando as peças vira o aviso "mudou no servidor". O servidor
 * responde a todo salvar ({@code acked}) e há um prazo ({@link TvPendingSave}): um salvar recusado ou perdido nunca
 * fica pendente, senão a mudança de outro operador seria tomada como nossa e salva por cima sem aviso.
 */
final class TvEditorDoc {
    private static final TvEditOps.Access<TvPiece> A = TvPieceAccess.INSTANCE;

    /** Por que o último salvar não valeu; o aviso fica até o rascunho mudar. */
    enum Failure { TOO_BIG, DENIED, THROTTLED, TIMEOUT }

    private final BlockPos pos;
    private final TvEditorModel<TvLayout> model;
    private TvArea area;
    private int revision;
    private final TvPendingSave<TvLayout> pending = new TvPendingSave<>();
    private @Nullable Failure failure;
    private int failureVersion;
    private @Nullable TvLayout changedOnServer;
    private boolean grid = true;
    private String themeName = "";

    TvEditorDoc(BlockPos pos, TvArea area) {
        this.pos = pos;
        this.area = area;
        TvBlockEntity tv = tv();
        TvLayout start = tv == null ? TvLayout.EMPTY : tv.layout();
        if (tv != null) {
            this.area = tv.area();
            this.revision = tv.revision();
        }
        this.model = new TvEditorModel<>(start);
    }

    private @Nullable TvBlockEntity tv() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level != null && minecraft.level.getBlockEntity(pos) instanceof TvBlockEntity tv ? tv : null;
    }

    /** Uma vez por tick: a área e a revisão da Tela no cliente, e o que fazer com uma revisão nova. */
    void tick() {
        if (pending.tick()) {
            fail(Failure.TIMEOUT);
        }
        TvBlockEntity tv = tv();
        if (tv == null || tv.revision() == revision) {
            return;
        }
        revision = tv.revision();
        TvArea newArea = tv.area();
        TvLayout server = tv.layout();
        boolean areaChanged = !newArea.equals(area);
        area = newArea;
        if (pending.pending()) {
            boolean unchanged = model.draft() == pending.release();
            model.rebase(server);
            if (unchanged) {
                model.edit(server, model.selection().below(server.pieces().size()));
            }
        } else if (server.equals(model.saved())) {
            return;
        } else if (!model.dirty()) {
            // sem mudança local, o layout novo entra sozinho, sem aviso
            model.rebase(server);
            model.adopt(server, model.selection().below(server.pieces().size()));
        } else if (areaChanged && model.saved().clampedTo(newArea).equals(server)) {
            model.rebase(server);
            TvLayout clamped = model.draft().clampedTo(newArea);
            if (!clamped.equals(model.draft())) {
                model.edit(clamped, model.selection());
            }
        } else {
            changedOnServer = server;
        }
    }

    /** O layout salvo mudou no servidor por outro caminho e o rascunho não foi trocado. */
    boolean changedOnServer() {
        return changedOnServer != null;
    }

    /** O Recarregar do aviso: o rascunho volta ao que está na Tela. */
    void reload() {
        TvBlockEntity tv = tv();
        TvLayout layout = tv != null ? tv.layout() : changedOnServer;
        changedOnServer = null;
        if (layout != null) {
            model.load(layout);
        }
    }

    /** O salvar saiu: o rascunho de agora espera a resposta e a revisão do servidor. */
    void sent() {
        failure = null;
        pending.begin(model.draft());
    }

    /**
     * A resposta do servidor ao salvar. {@code SAVED} segue esperando a revisão nova, que vem pelo update tag do
     * bloco (o prazo cobre o que se perder); qualquer outra solta o pendente na hora, e a recusa vira aviso.
     */
    void acked(TvLayoutAckPayload.Result result) {
        if (!pending.pending() || result == TvLayoutAckPayload.Result.SAVED) {
            return;
        }
        pending.release();
        switch (result) {
            case TOO_BIG -> fail(Failure.TOO_BIG);
            case DENIED -> fail(Failure.DENIED);
            case THROTTLED -> fail(Failure.THROTTLED);
            default -> {
            }
        }
    }

    /** O salvar não valeu: o aviso fica até o rascunho mudar. */
    void fail(Failure why) {
        failure = why;
        failureVersion = model.version();
    }

    /** O aviso do último salvar que não valeu, ou nulo (some com a próxima mudança do rascunho). */
    @Nullable Failure failure() {
        if (failure != null && failureVersion != model.version()) {
            failure = null;
        }
        return failure;
    }

    /** Na captura nada vai ao servidor: o rascunho vira a base na hora. */
    void savedLocally() {
        model.rebase(model.draft());
    }

    TvEditorModel<TvLayout> model() {
        return model;
    }

    TvLayout draft() {
        return model.draft();
    }

    List<TvPiece> pieces() {
        return model.draft().pieces();
    }

    TvSelection selection() {
        return model.selection();
    }

    TvArea area() {
        return area;
    }

    BlockPos pos() {
        return pos;
    }

    int revision() {
        return revision;
    }

    void apply(TvEditOps.Edit<TvPiece> edit) {
        apply(edit.pieces(), edit.selection());
    }

    void apply(List<TvPiece> pieces, TvSelection selection) {
        apply(pieces, selection, null);
    }

    /** As peças novas e a escolha depois delas; a {@code key} junta mudanças seguidas no desfazer. */
    void apply(List<TvPiece> pieces, TvSelection selection, @Nullable Object key) {
        model.edit(pieces == pieces() ? model.draft() : model.draft().withPieces(pieces), selection, key);
    }

    /** O arrastar começa e termina: tudo no meio é uma mudança só no desfazer. */
    void beginGesture() {
        model.history().beginGesture();
    }

    void endGesture() {
        model.history().endGesture();
    }

    /**
     * Troca cada peça escolhida por {@code change} aplicada a ela (a aba de propriedades); nada muda se nenhuma troca.
     * A {@code key} junta o digitar e o girar da roda num campo como uma mudança só.
     */
    void changeSelected(UnaryOperator<TvPiece> change, Object key) {
        List<TvPiece> pieces = pieces();
        TvSelection selection = selection();
        List<TvPiece> out = null;
        for (int k = 0; k < selection.size(); k++) {
            int i = selection.get(k);
            if (i >= pieces.size()) {
                continue;
            }
            TvPiece changed = change.apply(pieces.get(i));
            if (!changed.equals(pieces.get(i))) {
                if (out == null) {
                    out = new ArrayList<>(pieces);
                }
                out.set(i, changed);
            }
        }
        if (out != null) {
            apply(out, selection, key);
        }
    }

    boolean undo() {
        return model.undo();
    }

    boolean redo() {
        return model.redo();
    }

    void copy() {
        if (hasSelection()) {
            TvClipboard.put(TvEditOps.copy(A, pieces(), selection()));
        }
    }

    void cut() {
        if (hasSelection()) {
            copy();
            remove();
        }
    }

    void paste() {
        TvEditOps.Clip<TvPiece> clip = TvClipboard.get();
        if (clip != null && !clip.pieces().isEmpty()) {
            apply(TvEditOps.paste(A, pieces(), clip, area.pixelWidth(), area.pixelHeight()));
            model.select(model.selection(), 0);
        }
    }

    void selectAll() {
        model.select(TvSelection.range(0, pieces().size()), 0);
    }

    /** As setas: a escolha anda {@code dx}, {@code dy} px; os toques seguidos viram uma mudança só. */
    void nudge(int dx, int dy) {
        if (hasSelection()) {
            apply(TvEditOps.move(A, pieces(), selection(), dx, dy, area.pixelWidth(), area.pixelHeight()),
                    selection(), "nudge");
        }
    }

    void raise() {
        apply(TvEditOps.raise(A, pieces(), selection()));
    }

    void lower() {
        apply(TvEditOps.lower(A, pieces(), selection()));
    }

    /** Leva a escolha para a camada de cima (dentro do grupo, se ela é de um). */
    void toFront() {
        apply(TvEditOps.moveTo(A, pieces(), selection(), pieces().size()));
    }

    /** Leva a escolha para a camada de baixo (dentro do grupo, se ela é de um). */
    void toBack() {
        apply(TvEditOps.moveTo(A, pieces(), selection(), 0));
    }

    /** Esconde as peças escolhidas (o olho das Camadas as mostra de novo). */
    void hideSelection() {
        List<TvPiece> pieces = new ArrayList<>(pieces());
        TvSelection selection = selection();
        for (int k = 0; k < selection.size(); k++) {
            int i = selection.get(k);
            if (i < pieces.size()) {
                pieces.set(i, pieces.get(i).withHidden(true));
            }
        }
        apply(pieces, selection);
    }

    void select(TvSelection selection, int focusGroup) {
        model.history().breakKey();
        model.select(selection, focusGroup);
    }

    void insert(TvPieceType type, String text) {
        apply(TvEditOps.insert(A, pieces(), TvPieceAccess.create(type, text), area.pixelWidth(), area.pixelHeight()));
        model.select(model.selection(), 0);
    }

    /** Põe uma peça nova do tipo com o centro em ({@code cx}, {@code cy}), presa à área, e escolhe só ela. */
    void insertAt(TvPieceType type, String text, int cx, int cy) {
        TvPiece piece = TvPieceAccess.create(type, text);
        int w = Math.min(piece.width(), area.pixelWidth());
        int h = Math.min(piece.height(), area.pixelHeight());
        int x = Math.max(0, Math.min(area.pixelWidth() - w, cx - w / 2));
        int y = Math.max(0, Math.min(area.pixelHeight() - h, cy - h / 2));
        List<TvPiece> out = new ArrayList<>(pieces());
        out.add(piece.withBox(x, y, w, h));
        apply(new TvEditOps.Edit<>(out, TvSelection.of(out.size() - 1)));
        model.select(model.selection(), 0);
    }

    void group() {
        apply(TvEditOps.group(A, pieces(), selection()));
        model.select(model.selection(), 0);
    }

    void ungroup() {
        apply(TvEditOps.ungroup(A, pieces(), selection()));
        model.select(model.selection(), 0);
    }

    void duplicate() {
        apply(TvEditOps.duplicate(A, pieces(), selection(), area.pixelWidth(), area.pixelHeight()));
        model.select(model.selection(), 0);
    }

    void remove() {
        apply(TvEditOps.remove(pieces(), selection()));
        model.select(TvSelection.EMPTY, 0);
    }

    /** O nome que o jogador digitou para o próximo "Salvar tema"; é do editor, não do layout. */
    String themeName() {
        return themeName;
    }

    void setThemeName(String name) {
        themeName = name;
    }

    /** Se a miniatura mostra a grade; é do editor, não do layout. */
    boolean gridVisible() {
        return grid;
    }

    void setGrid(boolean visible) {
        grid = visible;
    }

    /** Muda o layout inteiro (tema, fundo, linhas); a {@code key} junta mudanças seguidas de um controle. */
    void changeLayout(UnaryOperator<TvLayout> change, Object key) {
        TvLayout now = model.draft();
        TvLayout next = change.apply(now);
        if (!next.equals(now)) {
            model.edit(next, selection(), key);
        }
    }

    /** A escolha como componente de nome {@code name}: posições relativas ao canto e grupos renumerados. */
    TvComponent selectionAsComponent(String name) {
        return new TvComponent(name, TvEditOps.copy(A, pieces(), selection()).pieces());
    }

    /**
     * Insere o componente como peças soltas num grupo novo, no primeiro lugar livre (o mesmo do colar). Um componente
     * de peças soltas ganha um grupo só; um que já tem grupos os mantém, renumerados.
     */
    void insertComponent(TvComponent component) {
        List<TvPiece> parts = new ArrayList<>(component.pieces());
        if (parts.isEmpty()) {
            return;
        }
        if (parts.size() > 1 && parts.stream().allMatch(p -> p.group() == 0)) {
            parts.replaceAll(p -> p.withGroup(1));
        }
        int[] b = TvEditOps.bounds(A, parts, TvSelection.range(0, parts.size()));
        apply(TvEditOps.paste(A, pieces(), new TvEditOps.Clip<>(parts, 0, 0, b[2], b[3]), area.pixelWidth(),
                area.pixelHeight()));
        model.select(model.selection(), 0);
    }

    void moveTo(TvSelection block, int before) {
        apply(TvEditOps.moveTo(A, pieces(), block, before));
    }

    void toggleHidden(int index) {
        List<TvPiece> pieces = new ArrayList<>(pieces());
        pieces.set(index, pieces.get(index).withHidden(!pieces.get(index).hidden()));
        apply(pieces, selection());
    }

    boolean canGroup() {
        return selection().size() >= 2;
    }

    boolean canUngroup() {
        return TvEditOps.anyGrouped(A, pieces(), selection());
    }

    boolean hasSelection() {
        return !selection().isEmpty();
    }
}
