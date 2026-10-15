package io.github.matheusanbs.wirelessautomate.gametest;

import io.github.matheusanbs.wirelessautomate.block.RouterBlock;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.registry.ModBlocks;
import io.github.matheusanbs.wirelessautomate.registry.ModItems;
import io.github.matheusanbs.wirelessautomate.resonator.ResonanceCoreBlockEntity;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorPartBlock;
import io.github.matheusanbs.wirelessautomate.resonator.SignalCrystalItem;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Monta um Ressonador de Sinal inteiro (os 98 blocos) num GameTest do template {@code empty7}. */
final class ResonatorTestSupport {
    /** O canto da caixa nos testes que montam um Ressonador só no {@code empty7}. */
    static final BlockPos ORIGIN = new BlockPos(1, 1, 1);

    /** Onde ficam as peças; a origem é o canto de menor coordenada da caixa 5×5×5, relativa ao teste. */
    enum Layout {
        /** Janela 3×3 ao sul, Núcleo no centro da face leste, Energia na oeste, Cristais na norte e Sintonizador em cima. */
        VITRINE,
        /** Como a Vitrine, mas a Porta de Energia no centro da face de cima e sem Sintonizador. */
        ENERGY_ON_TOP
    }

    private ResonatorTestSupport() {
    }

    /** Põe os 98 blocos com a caixa a partir de {@code origin} e devolve a posição relativa do Núcleo. */
    static BlockPos build(GameTestHelper helper, BlockPos origin, Layout layout, RouterTier tier) {
        for (int x = 0; x < 5; x++) {
            for (int y = 0; y < 5; y++) {
                for (int z = 0; z < 5; z++) {
                    boolean inside = x > 0 && x < 4 && y > 0 && y < 4 && z > 0 && z < 4;
                    if (!inside) {
                        helper.setBlock(origin.offset(x, y, z), ModBlocks.RESONATOR_CASING.get());
                    }
                }
            }
        }
        for (int x = 1; x < 4; x++) {
            for (int y = 1; y < 4; y++) {
                helper.setBlock(origin.offset(x, y, 4), ModBlocks.RESONATOR_GLASS.get());
            }
        }
        BlockPos core = origin.offset(4, 2, 2);
        helper.setBlock(core, ModBlocks.RESONANCE_CORE.get().defaultBlockState()
                .setValue(RouterBlock.TIER, tier).setValue(ResonatorPartBlock.FACING, Direction.EAST));
        part(helper, origin.offset(2, 2, 0), ModBlocks.RESONATOR_CRYSTAL_PORT.get(), Direction.NORTH);
        if (layout == Layout.VITRINE) {
            part(helper, origin.offset(0, 2, 2), ModBlocks.RESONATOR_ENERGY_PORT.get(), Direction.WEST);
            part(helper, origin.offset(2, 4, 2), ModBlocks.RESONATOR_TUNER.get(), Direction.UP);
        } else {
            part(helper, origin.offset(2, 4, 2), ModBlocks.RESONATOR_ENERGY_PORT.get(), Direction.UP);
        }
        return core;
    }

    private static void part(GameTestHelper helper, BlockPos pos, Block block, Direction facing) {
        BlockState state = block.defaultBlockState().setValue(ResonatorPartBlock.FACING, facing);
        helper.setBlock(pos, state);
    }

    static ResonanceCoreBlockEntity core(GameTestHelper helper, BlockPos corePos) {
        return helper.getBlockEntity(corePos) instanceof ResonanceCoreBlockEntity core ? core : null;
    }

    /** Monta a Vitrine no tier a partir de {@link #ORIGIN}, espera montar e roda {@code body} num só tick. */
    static void formed(GameTestHelper helper, RouterTier tier, Consumer<ResonanceCoreBlockEntity> body) {
        formed(helper, Layout.VITRINE, tier, body);
    }

    /** Monta {@code layout} no tier a partir de {@link #ORIGIN}, espera montar e roda {@code body} num só tick. */
    static void formed(GameTestHelper helper, Layout layout, RouterTier tier, Consumer<ResonanceCoreBlockEntity> body) {
        BlockPos corePos = build(helper, ORIGIN, layout, tier);
        helper.startSequence().thenWaitUntil(() -> assertFormed(helper, corePos, true))
                .thenExecute(() -> body.accept(core(helper, corePos))).thenSucceed();
    }

    static void assertFormed(GameTestHelper helper, BlockPos corePos, boolean formed) {
        ResonanceCoreBlockEntity core = core(helper, corePos);
        helper.assertTrue(core != null && core.formed() == formed, formed ? "não montou" : "não desmontou");
    }

    static ServerPlayer survivor(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        return player;
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
