package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.network.EngineVisitor;
import io.github.matheusanbs.wirelessautomate.network.NetworkManager;
import io.github.matheusanbs.wirelessautomate.range.RemoteIndex;
import io.github.matheusanbs.wirelessautomate.range.RemoteListener;
import io.github.matheusanbs.wirelessautomate.range.RemoteMachine;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * As costuras do motor para a Doca de Padrões: os visitantes do laço de transferência ({@link EngineVisitor}) e um
 * dono de remotas que não é roteador no índice do mixin ({@link RemoteListener}).
 */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class DockGameTests {
    /** Quantos ticks o visitante de teste dorme depois da primeira visita. */
    private static final int NAP = 10;

    /**
     * Visitante de teste: na primeira visita dorme {@link #NAP} ticks; na segunda, até o {@code wakeVisitor}. Passado
     * de três visitas, sai do laço sozinho (o teste falhou no meio e não o tirou).
     */
    private static final class TestVisitor implements EngineVisitor {
        int visits;
        long firstAt = -1;
        long secondAt = -1;
        long thirdAt = -1;
        long wakeAt;

        @Override
        public int visit(long now) {
            visits++;
            if (visits == 1) {
                firstAt = now;
                wakeAt = now + NAP;
            } else if (visits == 2) {
                secondAt = now;
                wakeAt = Long.MAX_VALUE;
            } else if (visits == 3) {
                thirdAt = now;
                wakeAt = Long.MAX_VALUE;
            } else {
                NetworkManager.get().removeVisitor(this);
            }
            return 0;
        }

        @Override
        public long wakeAt() {
            return wakeAt;
        }
    }

    /** Dono de remotas de teste: guarda o último aviso. */
    private static final class TestListener implements RemoteListener {
        private final Level level;
        private final BlockPos pos;
        int changes;
        @Nullable RemoteMachine lastMachine;
        @Nullable BlockState lastState;

        TestListener(Level level, BlockPos pos) {
            this.level = level;
            this.pos = pos;
        }

        @Override
        public Level listenerLevel() {
            return level;
        }

        @Override
        public BlockPos listenerPos() {
            return pos;
        }

        @Override
        public void remoteMachineChanged(RemoteMachine machine, @Nullable BlockState state) {
            changes++;
            lastMachine = machine;
            lastState = state;
        }
    }

    /** O visitante é visitado, dorme até o {@code wakeAt}, não é visitado dormindo e acorda pelo {@code wakeVisitor}. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void visitorSleepsAndWakes(GameTestHelper helper) {
        TestVisitor visitor = new TestVisitor();
        NetworkManager manager = NetworkManager.get();
        manager.addVisitor(visitor);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(visitor.visits >= 1, "o visitante não foi visitado"))
                .thenWaitUntil(() -> helper.assertTrue(visitor.visits >= 2, "o visitante não acordou pelo wakeAt"))
                .thenExecute(() -> helper.assertTrue(visitor.secondAt >= visitor.firstAt + NAP,
                        "visitado dormindo: " + visitor.firstAt + " e " + visitor.secondAt))
                .thenIdle(NAP + 5)
                .thenExecute(() -> {
                    helper.assertTrue(visitor.visits == 2, "visitado com wakeAt no fim dos tempos: " + visitor.visits);
                    manager.wakeVisitor(visitor);
                })
                .thenWaitUntil(() -> helper.assertTrue(visitor.visits >= 3, "o wakeVisitor não acordou o visitante"))
                .thenExecute(() -> {
                    manager.removeVisitor(visitor);
                    helper.assertFalse(manager.hasVisitor(visitor), "o visitante ficou no laço");
                })
                .thenSucceed();
    }

    /**
     * Um dono que não é roteador, indexado para a posição de um baú, recebe o aviso do mixin quando um item entra no
     * baú ({@code setChanged}), com o estado do bloco; fora do índice, não recebe mais.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void remoteListenerHearsChest(GameTestHelper helper) {
        BlockPos chest = new BlockPos(2, 1, 2);
        helper.setBlock(chest, Blocks.CHEST);
        BlockPos absolute = helper.absolutePos(chest);
        TestListener listener = new TestListener(helper.getLevel(), helper.absolutePos(new BlockPos(0, 1, 0)));
        RemoteMachine machine = new RemoteMachine(absolute, 0, null, 0);
        RemoteIndex index = RemoteIndex.get();
        int[] before = {0};
        helper.startSequence()
                .thenExecute(() -> {
                    IItemHandler handler = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, absolute,
                            Direction.UP);
                    helper.assertTrue(handler != null, "baú sem IItemHandler");
                    index.update(listener, List.of(machine));
                    // O índice é do servidor todo: o dono sai mesmo com o teste falhando no meio.
                    try {
                        helper.assertTrue(index.isIndexed(listener, absolute), "o dono não entrou no índice");
                        helper.assertTrue(index.listenersAt(helper.getLevel(), absolute).contains(listener),
                                "listenersAt sem o dono");
                        helper.assertTrue(index.routersAt(helper.getLevel(), absolute).isEmpty(),
                                "routersAt devolveu um dono que não é roteador");
                        before[0] = listener.changes;
                        handler.insertItem(0, new ItemStack(Items.COBBLESTONE, 3), false);
                        helper.assertTrue(listener.changes > before[0], "o setChanged do baú não chegou ao dono");
                        helper.assertTrue(listener.lastMachine == machine, "aviso com outra máquina");
                        helper.assertTrue(listener.lastState != null && listener.lastState.is(Blocks.CHEST),
                                "aviso sem o estado do baú");
                    } finally {
                        index.remove(listener);
                    }
                    helper.assertFalse(index.isIndexed(listener, absolute), "o dono ficou no índice");
                    before[0] = listener.changes;
                    handler.insertItem(1, new ItemStack(Items.COBBLESTONE, 3), false);
                    helper.assertTrue(listener.changes == before[0], "aviso depois de sair do índice");
                })
                .thenSucceed();
    }
}
