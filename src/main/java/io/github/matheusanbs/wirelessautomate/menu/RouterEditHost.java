package io.github.matheusanbs.wirelessautomate.menu;

import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.range.EditTarget;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * Quem edita as faces de um roteador pelos pacotes do cliente: a tela do roteador ({@link RouterMenu}) ou
 * outro menu sem slots de cartão, como o Tablet. Os handlers de {@code ModPayloads} acham o host pelo
 * {@code containerId} do menu aberto do jogador ({@code ModPayloads.host}) e só agem se {@link #canEdit}
 * confirma. Os slots de cartão e a face selecionada ficam no {@link RouterMenu}, fora desta interface.
 */
public interface RouterEditHost {
    /** O id do menu aberto, o que os pacotes citam. */
    int containerId();

    /** O roteador em edição, ou {@code null} se o host não aponta nenhum (ou é o lado do cliente). */
    @Nullable RouterBlockEntity editedRouter();

    /** O jogador ainda pode editar o roteador: a tela perto dele, ou o nó visível, carregado e ao alcance. */
    boolean canEdit(ServerPlayer player);

    /** A referência ao alvo de edição (a presa, um grupo ou uma máquina), para o filtro de face lembrar dele. */
    EditTargetRef targetRef();

    /**
     * O alvo para uma ação, conferido; {@code null} se ele deixou de existir e voltou para a presa (a
     * ação é ignorada, porque o painel pensava editar outra coisa).
     */
    @Nullable EditTarget actionTarget();

    /** Confere o alvo no roteador e devolve o que vale agora (um alvo que sumiu volta para a presa). */
    EditTarget validTarget();

    /**
     * Troca o alvo de edição, se ele existe e bate com o que o painel viu (a posição da máquina ou o bloco do
     * grupo). Devolve se trocou.
     */
    boolean selectTarget(EditTarget target, @Nullable BlockPos pos, @Nullable ResourceLocation block);

    /** Um pacote mudou o roteador ou o alvo; o host reenvia o que o painel mostra, se precisar. */
    default void editApplied() {
    }
}
