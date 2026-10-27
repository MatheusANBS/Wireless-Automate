package io.github.matheusanbs.wirelessautomate.client.tv.editor;

import io.github.matheusanbs.wirelessautomate.tv.TvArea;
import io.github.matheusanbs.wirelessautomate.tv.TvBlockEntity;
import io.github.matheusanbs.wirelessautomate.tv.TvLayout;
import io.github.matheusanbs.wirelessautomate.tv.TvPiece;
import io.github.matheusanbs.wirelessautomate.tv.TvPieceType;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

/**
 * O documento que as partes do editor dividem: o {@link TvEditorModel} do rascunho, a Tela no cliente (área,
 * revisão e o layout salvo, relidos uma vez por tick, nunca por quadro) e as edições que as partes pedem, todas pelo
 * {@link TvEditOps}. Também acompanha o salvar: o layout mandado espera a revisão nova do servidor, que vira a base;
 * uma revisão nova que não é a nossa nem só a área cortando as peças vira o aviso "mudou no servidor".
 */
final class TvEditorDoc {
    private static final TvEditOps.Access<TvPiece> A = TvPieceAccess.INSTANCE;

    private final BlockPos pos;
    private final TvEditorModel<TvLayout> model;
    private TvArea area;
    private int revision;
    private @Nullable TvLayout sent;
    private @Nullable TvLayout changedOnServer;

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
        TvBlockEntity tv = tv();
        if (tv == null || tv.revision() == revision) {
            return;
        }
        revision = tv.revision();
        TvArea newArea = tv.area();
        TvLayout server = tv.layout();
        boolean areaChanged = !newArea.equals(area);
        area = newArea;
        if (sent != null) {
            boolean unchanged = model.draft() == sent;
            sent = null;
            model.rebase(server);
            if (unchanged) {
                model.edit(server, model.selection().below(server.pieces().size()));
            }
        } else if (server.equals(model.saved())) {
            return;
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

    /** O salvar saiu: o rascunho de agora espera a revisão do servidor. */
    void sent() {
        sent = model.draft();
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
        model.edit(pieces == pieces() ? model.draft() : model.draft().withPieces(pieces), selection);
    }

    void select(TvSelection selection, int focusGroup) {
        model.select(selection, focusGroup);
    }

    void insert(TvPieceType type, String text) {
        apply(TvEditOps.insert(A, pieces(), TvPieceAccess.create(type, text), area.pixelWidth(), area.pixelHeight()));
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

    void moveTo(TvSelection block, int before) {
        apply(TvEditOps.moveTo(pieces(), block, before));
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
