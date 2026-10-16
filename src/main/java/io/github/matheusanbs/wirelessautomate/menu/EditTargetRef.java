package io.github.matheusanbs.wirelessautomate.menu;

import io.github.matheusanbs.wirelessautomate.block.RouterBlockEntity;
import io.github.matheusanbs.wirelessautomate.range.EditTarget;
import io.github.matheusanbs.wirelessautomate.range.MachineGroup;
import io.github.matheusanbs.wirelessautomate.range.RemoteMachine;
import io.github.matheusanbs.wirelessautomate.range.RemoteMachines;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * O alvo de edição da tela do roteador ({@link EditTarget}) com o que o identifica de verdade: a
 * posição da máquina ({@link EditTarget.Kind#MACHINE}) ou o id do bloco do grupo
 * ({@link EditTarget.Kind#GROUP}). O índice de uma máquina muda quando outra é desvinculada, e o id
 * de um grupo pode ser reaproveitado depois de recarregar o mundo; por isso o menu e o filtro de face
 * guardam esta referência e a conferem ({@link #refresh}) antes de cada ação. O bloco só confere o que a
 * tela mandou ({@link #resolve}): com o mundo aberto os ids de grupo não se repetem, e um grupo que trocou
 * de bloco (o Atualizar) continua o mesmo. Só no servidor.
 */
public record EditTargetRef(EditTarget target, @Nullable BlockPos pos, @Nullable ResourceLocation block) {
    public static final EditTargetRef ATTACHED = new EditTargetRef(EditTarget.ATTACHED_TARGET, null, null);

    /**
     * A referência ao alvo como ele está agora no roteador, conferindo a posição ({@code pos}, para
     * uma máquina) ou o bloco ({@code block}, para um grupo) que a tela mandou; {@code null} se o alvo
     * não existe, não bate ou o upgrade de alcance não vale. A presa sempre vale.
     */
    public static @Nullable EditTargetRef resolve(RouterBlockEntity router, EditTarget target, @Nullable BlockPos pos,
            @Nullable ResourceLocation block) {
        if (target.attached()) {
            return ATTACHED;
        }
        if (!router.rangeActive()) {
            return null;
        }
        RemoteMachines remotes = router.remoteMachines();
        if (target.kind() == EditTarget.Kind.GROUP) {
            MachineGroup group = remotes.group(target.id());
            return group == null || block == null || !group.block().equals(block)
                    ? null
                    : new EditTargetRef(target, null, group.block());
        }
        RemoteMachine machine = remotes.machine(target.id());
        return machine == null || pos == null || !machine.pos().equals(pos)
                ? null
                : new EditTargetRef(target, machine.pos(), null);
    }

    /**
     * A mesma referência, conferida no roteador: uma máquina que mudou de índice (outra antes dela
     * foi desvinculada) é achada pela posição; um grupo que trocou de bloco segue pelo id, com o bloco
     * novo; um alvo que não existe mais, ou sem o upgrade valendo, volta para a presa ({@link #ATTACHED}).
     */
    public EditTargetRef refresh(RouterBlockEntity router) {
        if (target.attached()) {
            return this;
        }
        if (!router.rangeActive()) {
            return ATTACHED;
        }
        RemoteMachines remotes = router.remoteMachines();
        if (target.kind() == EditTarget.Kind.GROUP) {
            MachineGroup group = remotes.group(target.id());
            if (group == null) {
                return ATTACHED;
            }
            return group.block().equals(block) ? this : new EditTargetRef(target, null, group.block());
        }
        RemoteMachine machine = remotes.machine(target.id());
        if (machine != null && machine.pos().equals(pos)) {
            return this;
        }
        int index = pos == null ? -1 : remotes.index(pos);
        return index < 0 ? ATTACHED : new EditTargetRef(EditTarget.machine(index), pos, null);
    }

    /** O alvo, se ele ainda existe ({@link #refresh}) e não caiu para a presa; senão {@code null}. */
    public @Nullable EditTarget current(RouterBlockEntity router) {
        EditTargetRef fresh = refresh(router);
        return fresh.target.attached() && !target.attached() ? null : fresh.target;
    }
}
