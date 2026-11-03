package io.github.matheusanbs.wirelessautomate.client.tablet.networks;

import io.github.matheusanbs.wirelessautomate.client.tablet.kit.DropdownModal;
import io.github.matheusanbs.wirelessautomate.client.tablet.kit.ModalLayer;
import java.util.UUID;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * Os ganchos do e2e na aba Redes: o centro na tela de cada coisa clicável (ou {@code null} fora da vista) e o estado
 * que os passos conferem. Só lê as peças da {@link NetworksPanel}; nada aqui muda a tela, fora a rolagem que traz uma
 * linha da coluna à vista.
 */
public final class NetworksProbe {
    private final NetworksPanel panel;
    private final ModalLayer modals;
    private final NetworkColumn column;
    private final NetworkDetail detail;
    private final GroupPanel groupPanel;

    NetworksProbe(NetworksPanel panel, ModalLayer modals, NetworkColumn column, NetworkDetail detail,
            GroupPanel groupPanel) {
        this.panel = panel;
        this.modals = modals;
        this.column = column;
        this.detail = detail;
        this.groupPanel = groupPanel;
    }

    public @Nullable UUID selectedNetwork() {
        return panel.network();
    }

    public @Nullable UUID selectedGroup() {
        return panel.network() == null ? panel.group() : null;
    }

    public boolean modalOpen() {
        return modals.isOpen();
    }

    /** Quantos snapshots montaram a aba desde que a tela abriu (só os de cabeçalho novo). */
    public int snapshotBuilds() {
        return panel.snapshotBuilds();
    }

    public @Nullable EditBox titleBox() {
        return panel.titleBox();
    }

    private boolean onNetwork() {
        return panel.network() != null;
    }

    private boolean onGroup() {
        return panel.network() == null && panel.group() != null;
    }

    public int @Nullable [] networkRowCenter(UUID id) {
        return column.rowCenter(NetworkTree.Kind.NETWORK, id);
    }

    public int @Nullable [] groupRowCenter(UUID id) {
        return column.rowCenter(NetworkTree.Kind.GROUP, id);
    }

    public int @Nullable [] newNetworkCenter() {
        return column.newNetworkCenter();
    }

    public int @Nullable [] newGroupCenter() {
        return column.newGroupCenter();
    }

    public int @Nullable [] titleCenter() {
        return onNetwork() || onGroup() ? detail.titleCenter() : null;
    }

    public int @Nullable [] groupPickerCenter() {
        return onNetwork() ? detail.groupPickerCenter() : null;
    }

    /** Centro da linha {@code id} da lista aberta (o seletor Grupo ou o "+ pôr uma rede"), ou {@code null}. */
    public int @Nullable [] pickerRowCenter(@Nullable UUID id) {
        return modals.current() instanceof DropdownModal open ? open.rowCenter(id) : null;
    }

    public int @Nullable [] accessCenter(boolean guests) {
        return onNetwork() ? detail.accessCenter(guests) : null;
    }

    public int @Nullable [] inviteCenter() {
        return onNetwork() ? detail.inviteCenter() : null;
    }

    public int @Nullable [] chipRemoveCenter(int index) {
        return onNetwork() ? detail.chipRemoveCenter(index) : null;
    }

    public int @Nullable [] pauseCenter() {
        return onNetwork() ? detail.pauseCenter() : groupPanel.pauseCenter();
    }

    public int @Nullable [] removeCenter() {
        return onNetwork() ? detail.removeCenter() : onGroup() ? groupPanel.removeCenter() : null;
    }

    public int @Nullable [] addToGroupCenter() {
        return onGroup() ? groupPanel.addToGroupCenter() : null;
    }

    public int @Nullable [] colorSlotCenter() {
        return onNetwork() ? detail.colorSlotCenter() : null;
    }

    public boolean colorOpen() {
        return modals.current() instanceof ColorModal;
    }

    public int @Nullable [] colorSquareCenter() {
        return modals.current() instanceof ColorModal open ? open.popup().svCenter() : null;
    }

    /** A cor (ARGB) que o seletor aberto mostra, ou 0 sem seletor. */
    public int colorValue() {
        return modals.current() instanceof ColorModal open ? open.current() : 0;
    }

    public void colorHexFocus() {
        if (modals.current() instanceof ColorModal open) {
            open.popup().focusHex();
        }
    }

    public int @Nullable [] iconSlotCenter() {
        return onGroup() ? groupPanel.iconSlotCenter() : null;
    }

    public boolean iconOpen() {
        return modals.current() instanceof IconModal;
    }

    /** Dá o foco ao campo de busca da grade aberta (ele já abre com o foco). */
    public void iconSearchFocus() {
        if (modals.current() instanceof IconModal open) {
            open.searchFocus();
        }
    }

    /** Centro da célula {@code index} da grade de ícones aberta, ou {@code null}. */
    public int @Nullable [] iconCellCenter(int index) {
        return modals.current() instanceof IconModal open ? open.cellCenter(index) : null;
    }

    /** O item da célula {@code index} da grade aberta, ou {@code null} (também enquanto a busca espera o texto). */
    public @Nullable ResourceLocation iconCellItem(int index) {
        return modals.current() instanceof IconModal open ? open.cellItem(index) : null;
    }

    /** Quanto levou a última consulta da grade aberta ao registro, em nanossegundos (0 sem consulta). */
    public long iconSearchNanos() {
        return modals.current() instanceof IconModal open ? open.lastSearchNanos() : 0;
    }
}
