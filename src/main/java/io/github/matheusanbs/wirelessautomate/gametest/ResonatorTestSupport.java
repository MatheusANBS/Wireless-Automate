package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import io.github.matheusanbs.wirelessautomate.resonator.ResonanceCoreBlockEntity;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorBatch;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorBlueprint;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorGeneration;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorLayout;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorLayout.Face;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorPartBlock;
import io.github.matheusanbs.wirelessautomate.resonator.SignalCrystalItem;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Monta um Ressonador de Sinal inteiro (a Armilar de 35 blocos) num GameTest do template {@code empty7}. */
final class ResonatorTestSupport {
    /** O canto da caixa nos testes que montam um Ressonador só no {@code empty7}. */
    static final BlockPos ORIGIN = new BlockPos(1, 1, 1);

    /** O Núcleo, relativo à {@link #ORIGIN}, com a frente sul. */
    static final BlockPos CORE = ORIGIN.offset(2, 0, 4);
    /** A Porta de Energia, relativa à {@link #ORIGIN}, com a frente sul. */
    static final BlockPos ENERGY_PORT = ORIGIN.offset(4, 0, 2);
    /** A Porta de Cristais, relativa à {@link #ORIGIN}, com a frente sul. */
    static final BlockPos CRYSTAL_PORT = ORIGIN.offset(0, 0, 2);
    /** O Sintonizador (ou a Laje que o substitui), relativo à {@link #ORIGIN}, com a frente sul. */
    static final BlockPos TUNER = ORIGIN.offset(2, 0, 0);

    /** Qual Armilar monta. */
    enum Layout {
        /** A Armilar inteira, com o Sintonizador. */
        ARMILAR,
        /** Sem o Sintonizador: a Laje ocupa a casa dele (2, 0, 0). */
        SEM_SINTONIZADOR
    }

    private ResonatorTestSupport() {
    }

    /** Monta a Armilar com a frente sul: veja {@link #build(GameTestHelper, BlockPos, Face, Layout, RouterTier)}. */
    static BlockPos build(GameTestHelper helper, BlockPos origin, Layout layout, RouterTier tier) {
        return build(helper, origin, Face.SOUTH, layout, tier);
    }

    /**
     * Põe os 35 blocos com a caixa 5×5×5 a partir do canto de menor coordenada {@code origin} (relativo ao teste) e a
     * frente {@code front} (o Núcleo vira para ela), e devolve a posição relativa do Núcleo.
     */
    static BlockPos build(GameTestHelper helper, BlockPos origin, Face front, Layout layout, RouterTier tier) {
        return build(helper, origin, front, layout, tier, pos -> false);
    }

    /** Como {@link #build(GameTestHelper, BlockPos, Face, Layout, RouterTier)}, sem pôr o que {@code skip} recusa. */
    static BlockPos build(GameTestHelper helper, BlockPos origin, Face front, Layout layout, RouterTier tier,
            Predicate<BlockPos> skip) {
        BlockPos corePos = coreOf(origin, front);
        for (int x = 0; x < ResonatorLayout.SIZE; x++) {
            for (int y = 0; y < ResonatorLayout.SIZE; y++) {
                for (int z = 0; z < ResonatorLayout.SIZE; z++) {
                    if (!skip.test(at(corePos, front, x, y, z))) {
                        place(helper, corePos, front, layout, tier, x, y, z);
                    }
                }
            }
        }
        return corePos;
    }

    /** O Núcleo de uma caixa de canto mínimo {@code origin} e frente {@code front}. */
    static BlockPos coreOf(BlockPos origin, Face front) {
        int[] min = ResonatorBlueprint.origin(0, 0, 0, front);
        return new BlockPos(origin.getX() - min[0], origin.getY(), origin.getZ() - min[2]);
    }

    /** A posição relativa da casa local (lx, ly, lz) da Armilar de Núcleo {@code corePos} e frente {@code front}. */
    static BlockPos at(BlockPos corePos, Face front, int lx, int ly, int lz) {
        int[] w = ResonatorLayout.world(front, corePos.getX(), corePos.getY(), corePos.getZ(), lx, ly, lz);
        return new BlockPos(w[0], w[1], w[2]);
    }

    private static void place(GameTestHelper helper, BlockPos corePos, Face front, Layout layout, RouterTier tier,
            int lx, int ly, int lz) {
        BlockPos pos = at(corePos, front, lx, ly, lz);
        switch (ResonatorLayout.expected(lx, ly, lz)) {
            case CASING -> helper.setBlock(pos, ModBlocks.RESONATOR_CASING.get());
            case COLUMN -> helper.setBlock(pos, ModBlocks.RESONATOR_COLUMN.get());
            case CRADLE -> helper.setBlock(pos, ModBlocks.RESONATOR_CRADLE.get());
            case CORE -> helper.setBlock(pos, ModBlocks.RESONANCE_CORE.get().defaultBlockState()
                    .setValue(RouterBlock.TIER, tier).setValue(ResonatorPartBlock.FACING, direction(front, Face.SOUTH)));
            case ENERGY_PORT -> part(helper, pos, ModBlocks.RESONATOR_ENERGY_PORT.get(), direction(front, Face.EAST));
            case CRYSTAL_PORT -> part(helper, pos, ModBlocks.RESONATOR_CRYSTAL_PORT.get(), direction(front, Face.WEST));
            case TUNER -> {
                if (layout == Layout.ARMILAR) {
                    part(helper, pos, ModBlocks.RESONATOR_TUNER.get(), direction(front, Face.NORTH));
                } else {
                    helper.setBlock(pos, ModBlocks.RESONATOR_CASING.get());
                }
            }
            default -> {
            }
        }
    }

    /** A direção local {@code local} levada ao mundo, para a frente {@code front}. */
    static Direction direction(Face front, Face local) {
        return Direction.values()[ResonatorLayout.toWorld(front, local).ordinal()];
    }

    private static void part(GameTestHelper helper, BlockPos pos, Block block, Direction facing) {
        BlockState state = block.defaultBlockState().setValue(ResonatorPartBlock.FACING, facing);
        helper.setBlock(pos, state);
    }

    static ResonanceCoreBlockEntity core(GameTestHelper helper, BlockPos corePos) {
        return helper.getBlockEntity(corePos) instanceof ResonanceCoreBlockEntity core ? core : null;
    }

    /** Monta a Armilar no tier a partir de {@link #ORIGIN}, espera montar e roda {@code body} num só tick. */
    static void formed(GameTestHelper helper, RouterTier tier, Consumer<ResonanceCoreBlockEntity> body) {
        formed(helper, Layout.ARMILAR, tier, body);
    }

    /** Monta {@code layout} no tier a partir de {@link #ORIGIN}, espera montar e roda {@code body} num só tick. */
    static void formed(GameTestHelper helper, Layout layout, RouterTier tier, Consumer<ResonanceCoreBlockEntity> body) {
        BlockPos corePos = build(helper, ORIGIN, layout, tier);
        helper.startSequence().thenWaitUntil(() -> assertFormed(helper, corePos, true))
                .thenExecute(() -> body.accept(core(helper, corePos))).thenSucceed();
    }

    /**
     * Roda {@code ticks} ticks do Núcleo como o gerenciador: a geração de cada tick (com o aviso às Portas) e o lote a
     * cada 20 ticks, mais um lote com o resto no fim se {@code ticks} não é múltiplo de 20. Substitui o antigo lote
     * único de 20 ticks: a energia por segundo e o desgaste dos cristais são os mesmos.
     */
    static void run(ServerLevel level, ResonanceCoreBlockEntity core, int ticks) {
        for (int done = 1; done <= ticks; done++) {
            ResonatorGeneration.tickAndNotify(core);
            if (done % ResonatorBatch.BATCH_TICKS == 0) {
                ResonatorBatch.run(level, core, ResonatorBatch.BATCH_TICKS);
            }
        }
        if (ticks % ResonatorBatch.BATCH_TICKS != 0) {
            ResonatorBatch.run(level, core, ticks % ResonatorBatch.BATCH_TICKS);
        }
    }

    static void assertFormed(GameTestHelper helper, BlockPos corePos, boolean formed) {
        ResonanceCoreBlockEntity core = core(helper, corePos);
        helper.assertTrue(core != null && core.formed() == formed, formed ? "não montou" : "não desmontou");
    }

    static ServerPlayer survivor(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.inLevel(helper);
        player.setGameMode(GameType.SURVIVAL);
        return player;
    }

    /**
     * Põe no inventário de {@code player} o Coração do Ressonador de {@code tier}, que subir o Núcleo pede: fora da
     * barra de atalhos, para a mão onde o teste põe o cartão não o tirar de lá.
     */
    static <P extends Player> P withHeart(P player, RouterTier tier) {
        var inventory = player.getInventory();
        for (int slot = 9; slot < inventory.items.size(); slot++) {
            if (inventory.items.get(slot).isEmpty()) {
                inventory.items.set(slot, new ItemStack(ModItems.RESONATOR_HEARTS.get(tier).get()));
                return player;
            }
        }
        throw new IllegalStateException("inventário cheio");
    }

    static BlockHitResult hit(BlockPos absolute) {
        return new BlockHitResult(Vec3.atCenterOf(absolute), Direction.EAST, absolute, false);
    }

    static ItemStack crystal(boolean cut, int charge) {
        ItemStack stack = new ItemStack(cut ? ModItems.CUT_SIGNAL_CRYSTAL.get() : ModItems.SIGNAL_CRYSTAL.get());
        SignalCrystalItem.setCharge(stack, charge);
        return stack;
    }

    /** Roda {@code command} como operador e devolve as linhas que ele mandou. */
    static List<Component> commandLines(GameTestHelper helper, String command) {
        List<Component> lines = new ArrayList<>();
        CommandSource sink = new CommandSource() {
            @Override
            public void sendSystemMessage(Component message) {
                lines.add(message);
            }

            @Override
            public boolean acceptsSuccess() {
                return true;
            }

            @Override
            public boolean acceptsFailure() {
                return true;
            }

            @Override
            public boolean shouldInformAdmins() {
                return false;
            }
        };
        CommandSourceStack source = helper.getLevel().getServer().createCommandSourceStack().withSource(sink)
                .withPermission(2).withLevel(helper.getLevel());
        helper.getLevel().getServer().getCommands().performPrefixedCommand(source, command);
        return lines;
    }

    /** A linha traduzível de chave {@code key} entre {@code lines}, ou {@code null}. */
    static TranslatableContents line(List<Component> lines, String key) {
        for (Component component : lines) {
            if (component.getContents() instanceof TranslatableContents contents && contents.getKey().equals(key)) {
                return contents;
            }
        }
        return null;
    }

    static void assertInserted(GameTestHelper helper, ResonanceCoreBlockEntity core, ItemStack stack) {
        helper.assertTrue(core.insertCrystal(stack).isEmpty(), "o cristal não entrou");
    }
}
