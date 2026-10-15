package io.github.matheusanbs.wirelessautomate.gametest;

import static io.github.matheusanbs.wirelessautomate.gametest.ResonatorTestSupport.crystal;
import static io.github.matheusanbs.wirelessautomate.gametest.ResonatorTestSupport.formed;
import static io.github.matheusanbs.wirelessautomate.gametest.ResonatorTestSupport.survivor;

import io.github.matheusanbs.wirelessautomate.WirelessAutomate;
import io.github.matheusanbs.wirelessautomate.block.RouterTier;
import io.github.matheusanbs.wirelessautomate.menu.ResonatorMenu;
import io.github.matheusanbs.wirelessautomate.resonator.CrystalCharge;
import io.github.matheusanbs.wirelessautomate.resonator.ResonanceCoreBlock;
import io.github.matheusanbs.wirelessautomate.resonator.ResonanceCoreBlockEntity;
import io.github.matheusanbs.wirelessautomate.resonator.ResonatorPartBlock;
import io.github.matheusanbs.wirelessautomate.resonator.SignalCrystalItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Os slots do menu do Núcleo pelos caminhos reais do vanilla ({@code clicked}: troca por tecla, clique com o cursor,
 * Shift + clique e arrasto), conferindo que a redstone e os cristais não duplicam nem somem: o total (jogador, cursor,
 * Núcleo e o que caiu no chão) é o mesmo antes e depois. Também o {@code Container.setItem} direto, o fechamento
 * quando o tier muda e dois jogadores com a tela aberta.
 */
@GameTestHolder(WirelessAutomate.MODID)
@PrefixGameTestTemplate(false)
public final class ResonatorSlotGameTests {
    private static final int CONTAINER_ID = 44;

    private ResonatorSlotGameTests() {
    }

    private static ServerPlayer near(GameTestHelper helper, ResonanceCoreBlockEntity core) {
        ServerPlayer player = survivor(helper);
        player.moveTo(Vec3.atBottomCenterOf(core.getBlockPos().east()));
        return player;
    }

    private static ResonatorMenu open(ServerPlayer player, ResonanceCoreBlockEntity core) {
        ResonatorMenu menu = new ResonatorMenu(CONTAINER_ID, player.getInventory(), core);
        player.containerMenu = menu;
        return menu;
    }

    private static void close(ServerPlayer player) {
        player.containerMenu.setCarried(ItemStack.EMPTY);
        player.containerMenu = player.inventoryMenu;
    }

    /** Um tick com a tela aberta: o vanilla relê todos os slots. */
    private static void tick(AbstractContainerMenu menu) {
        menu.broadcastChanges();
    }

    private static int redstoneOf(ServerPlayer player) {
        int total = player.containerMenu.getCarried().is(Items.REDSTONE) ? player.containerMenu.getCarried().getCount()
                : 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(Items.REDSTONE)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static int crystalsOf(ServerPlayer player, ResonanceCoreBlockEntity core) {
        int total = player.containerMenu.getCarried().getItem() instanceof SignalCrystalItem
                ? player.containerMenu.getCarried().getCount() : 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.getItem() instanceof SignalCrystalItem) {
                total += stack.getCount();
            }
        }
        for (int i = 0; i < core.chambers(); i++) {
            total += core.crystals().get(i).getCount();
        }
        return total;
    }

    /** Itens de {@code item} no chão em volta do Núcleo (o {@code setItem} joga a sobra à frente dele). */
    private static int dropped(GameTestHelper helper, ResonanceCoreBlockEntity core, boolean crystal) {
        int total = 0;
        for (ItemEntity entity : helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                new AABB(core.getBlockPos()).inflate(3))) {
            ItemStack stack = entity.getItem();
            if (crystal ? stack.getItem() instanceof SignalCrystalItem : stack.is(Items.REDSTONE)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static ItemStack renamed(int count) {
        ItemStack stack = new ItemStack(Items.REDSTONE, count);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Pó nomeado"));
        return stack;
    }

    @GameTest(template = "empty7")
    public static void trocaPorTeclaNaRedstoneNaoDuplica(GameTestHelper helper) {
        formed(helper, RouterTier.BASIC, core -> {
            ServerPlayer player = near(helper, core);
            ResonatorMenu menu = open(player, core);
            try {
                core.setRedstoneForTest(1);
                player.getInventory().setItem(0, new ItemStack(Items.REDSTONE, 64));
                menu.clicked(menu.redstoneSlot(), 0, ClickType.SWAP, player);
                tick(menu);
                tick(menu);
                helper.assertValueEqual(core.redstone(), 64, "redstone no Núcleo depois da troca");
                helper.assertValueEqual(redstoneOf(player), 1, "redstone do jogador depois da troca");

                menu.clicked(menu.redstoneSlot(), 1, ClickType.SWAP, player);
                tick(menu);
                helper.assertValueEqual(core.redstone() + redstoneOf(player), 65, "total depois da segunda troca");
                helper.assertValueEqual(core.redstone(), 0, "o Núcleo não esvaziou na troca com a barra vazia");

                player.getInventory().getItem(1).shrink(10);
                tick(menu);
                helper.assertValueEqual(core.redstone(), 0, "gastar a pilha do jogador mexeu no Núcleo");
                helper.assertValueEqual(redstoneOf(player), 55, "redstone do jogador depois de gastar");
            } finally {
                close(player);
            }
        });
    }

    @GameTest(template = "empty7")
    public static void redstoneNomeadaNoCursorNaoEntraNemTroca(GameTestHelper helper) {
        formed(helper, RouterTier.BASIC, core -> {
            ServerPlayer player = near(helper, core);
            ResonatorMenu menu = open(player, core);
            try {
                core.setRedstoneForTest(5);
                menu.setCarried(renamed(3));
                menu.clicked(menu.redstoneSlot(), 0, ClickType.PICKUP, player);
                tick(menu);
                helper.assertValueEqual(core.redstone(), 5, "redstone no Núcleo");
                helper.assertTrue(menu.getCarried().has(DataComponents.CUSTOM_NAME) && menu.getCarried().getCount() == 3,
                        "o cursor mudou: " + menu.getCarried());
                helper.assertTrue(!menu.slots.get(menu.redstoneSlot()).mayPlace(renamed(1)),
                        "o slot aceita redstone com componentes");

                core.setRedstoneForTest(0);
                menu.clicked(menu.redstoneSlot(), 0, ClickType.PICKUP, player);
                helper.assertValueEqual(core.redstone(), 0, "a redstone nomeada entrou no slot vazio");
                helper.assertValueEqual(menu.getCarried().getCount(), 3, "o cursor perdeu redstone");

                menu.setCarried(new ItemStack(Items.REDSTONE, 7));
                core.setRedstoneForTest(60);
                menu.clicked(menu.redstoneSlot(), 0, ClickType.PICKUP, player);
                tick(menu);
                helper.assertValueEqual(core.redstone(), 64, "o clique não completou o slot");
                helper.assertValueEqual(menu.getCarried().getCount(), 3, "sobra no cursor");
                menu.setCarried(ItemStack.EMPTY);
                menu.clicked(menu.redstoneSlot(), 1, ClickType.PICKUP, player);
                tick(menu);
                helper.assertValueEqual(menu.getCarried().getCount(), 32, "metade no cursor");
                helper.assertValueEqual(core.redstone(), 32, "metade no Núcleo");
            } finally {
                close(player);
            }
        });
    }

    @GameTest(template = "empty7")
    public static void shiftCliqueDaRedstoneComInventarioQuaseCheio(GameTestHelper helper) {
        formed(helper, RouterTier.BASIC, core -> {
            ServerPlayer player = near(helper, core);
            ResonatorMenu menu = open(player, core);
            try {
                for (int i = 0; i < 36; i++) {
                    player.getInventory().setItem(i, new ItemStack(Items.STONE, 64));
                }
                player.getInventory().setItem(0, new ItemStack(Items.REDSTONE, 34));
                core.setRedstoneForTest(64);
                menu.clicked(menu.redstoneSlot(), 0, ClickType.QUICK_MOVE, player);
                tick(menu);
                helper.assertValueEqual(redstoneOf(player), 64, "redstone do jogador");
                helper.assertValueEqual(core.redstone(), 34, "o que não coube ficou no Núcleo");

                player.getInventory().setItem(0, new ItemStack(Items.REDSTONE, 50));
                menu.clicked(menu.inventoryStart() + 27, 0, ClickType.QUICK_MOVE, player);
                tick(menu);
                helper.assertValueEqual(core.redstone(), 64, "o Núcleo não encheu");
                helper.assertValueEqual(redstoneOf(player), 20, "a sobra não ficou na barra");
            } finally {
                close(player);
            }
        });
    }

    @GameTest(template = "empty7")
    public static void camarasPelosCaminhosDoMenu(GameTestHelper helper) {
        formed(helper, RouterTier.ELITE, core -> {
            ServerPlayer player = near(helper, core);
            ResonatorMenu menu = open(player, core);
            try {
                // Troca por tecla: 5 cristais na barra com a câmara 0 ocupada.
                core.setCrystalAt(0, crystal(true, CrystalCharge.FULL));
                player.getInventory().setItem(0, crystal(false, CrystalCharge.FULL).copyWithCount(5));
                int before = crystalsOf(player, core);
                menu.clicked(0, 0, ClickType.SWAP, player);
                tick(menu);
                helper.assertValueEqual(crystalsOf(player, core), before, "cristais depois da troca");
                helper.assertValueEqual(core.crystals().get(0).getCount(), 1, "a câmara ficou com mais de um");

                // Clique com uma pilha no cursor numa câmara vazia: entra um.
                core.setCrystalAt(1, ItemStack.EMPTY);
                menu.setCarried(crystal(true, CrystalCharge.FULL).copyWithCount(4));
                before = crystalsOf(player, core);
                menu.clicked(1, 0, ClickType.PICKUP, player);
                tick(menu);
                helper.assertValueEqual(crystalsOf(player, core), before, "cristais depois do clique");
                helper.assertValueEqual(core.crystals().get(1).getCount(), 1, "a câmara 1");
                helper.assertValueEqual(menu.getCarried().getCount(), 3, "sobra no cursor");

                // Arrasto pelas três câmaras (a 2 está vazia).
                core.setCrystalAt(0, ItemStack.EMPTY);
                core.setCrystalAt(1, ItemStack.EMPTY);
                before = crystalsOf(player, core);
                menu.clicked(-999, AbstractContainerMenu.getQuickcraftMask(0, 0), ClickType.QUICK_CRAFT, player);
                for (int i = 0; i < 3; i++) {
                    menu.clicked(i, AbstractContainerMenu.getQuickcraftMask(1, 0), ClickType.QUICK_CRAFT, player);
                }
                menu.clicked(-999, AbstractContainerMenu.getQuickcraftMask(2, 0), ClickType.QUICK_CRAFT, player);
                tick(menu);
                helper.assertValueEqual(crystalsOf(player, core), before, "cristais depois do arrasto");
                for (int i = 0; i < 3; i++) {
                    helper.assertValueEqual(core.crystals().get(i).getCount(), 1, "câmara " + i + " no arrasto");
                }

                // Shift + clique de volta e de novo para dentro.
                menu.setCarried(ItemStack.EMPTY);
                before = crystalsOf(player, core);
                for (int i = 0; i < 3; i++) {
                    menu.clicked(i, 0, ClickType.QUICK_MOVE, player);
                }
                tick(menu);
                helper.assertValueEqual(crystalsOf(player, core), before, "cristais depois do Shift de volta");
                for (int i = 0; i < 3; i++) {
                    helper.assertTrue(core.crystals().get(i).isEmpty(), "a câmara " + i + " não esvaziou");
                }
                player.getInventory().setItem(9, crystal(true, CrystalCharge.FULL).copyWithCount(5));
                menu.clicked(menu.inventoryStart(), 0, ClickType.QUICK_MOVE, player);
                tick(menu);
                helper.assertValueEqual(crystalsOf(player, core), before + 5, "cristais depois do Shift para dentro");
                helper.assertValueEqual(player.getInventory().getItem(9).getCount(), 2, "a sobra saiu do lugar");
            } finally {
                close(player);
            }
        });
    }

    @GameTest(template = "empty7")
    public static void setItemDiretoNaoApagaItens(GameTestHelper helper) {
        formed(helper, RouterTier.ELITE, core -> {
            core.inventory().setItem(0, crystal(false, CrystalCharge.FULL).copyWithCount(3));
            helper.assertValueEqual(core.crystals().get(0).getCount(), 1, "a câmara ficou com um");
            core.inventory().setItem(1, new ItemStack(Items.STONE, 2));
            helper.assertTrue(core.crystals().get(1).isEmpty(), "a pedra entrou na câmara");
            core.inventory().setItem(core.chambers(), renamed(4));
            helper.assertValueEqual(core.redstone(), 0, "a redstone nomeada entrou");
            helper.assertValueEqual(dropped(helper, core, true), 2, "cristais que sobraram no chão");
            helper.assertValueEqual(dropped(helper, core, false), 4, "redstone nomeada no chão");
            helper.assertValueEqual(core.inventory().getMaxStackSize(), 1, "limite das câmaras no Container");
            helper.assertTrue(!core.inventory().canPlaceItem(core.chambers(), renamed(1)),
                    "o Container aceita redstone nomeada");
            BlockPos front = core.getBlockPos().relative(core.getBlockState().getValue(ResonatorPartBlock.FACING));
            helper.assertTrue(helper.getLevel().getBlockState(front).isAir(), "a frente do Núcleo não está livre");
        });
    }

    @GameTest(template = "empty7")
    public static void tierQueMudaFechaATela(GameTestHelper helper) {
        formed(helper, RouterTier.BASIC, core -> {
            ServerPlayer player = near(helper, core);
            ResonatorMenu menu = open(player, core);
            try {
                helper.assertTrue(menu.stillValid(player), "a tela não vale antes do upgrade");
                helper.assertTrue(ResonanceCoreBlock.tryUpgrade(helper.getLevel(), core.getBlockPos(),
                        RouterTier.ADVANCED, RouterTier.BASIC), "o upgrade foi recusado");
                helper.assertTrue(!menu.stillValid(player), "a tela continua valendo com as câmaras novas");
            } finally {
                close(player);
            }
        });
    }

    @GameTest(template = "empty7")
    public static void doisJogadoresComATelaAberta(GameTestHelper helper) {
        formed(helper, RouterTier.BASIC, core -> {
            ServerPlayer a = near(helper, core);
            ServerPlayer b = near(helper, core);
            ResonatorMenu menuA = open(a, core);
            ResonatorMenu menuB = open(b, core);
            try {
                a.getInventory().setItem(0, new ItemStack(Items.REDSTONE, 10));
                menuA.clicked(menuA.inventoryStart() + 27, 0, ClickType.QUICK_MOVE, a);
                tick(menuA);
                tick(menuB);
                helper.assertValueEqual(core.redstone(), 10, "redstone no Núcleo");
                // O lote gasta uma entre o tick e o Shift + clique do outro jogador.
                core.setRedstoneForTest(9);
                menuB.clicked(menuB.redstoneSlot(), 0, ClickType.QUICK_MOVE, b);
                tick(menuA);
                tick(menuB);
                helper.assertValueEqual(redstoneOf(b), 9, "redstone de B");
                helper.assertValueEqual(redstoneOf(a), 0, "redstone de A");
                helper.assertValueEqual(core.redstone(), 0, "redstone no Núcleo depois de B");
                b.getInventory().getItem(8).shrink(4);
                tick(menuA);
                tick(menuB);
                helper.assertValueEqual(core.redstone(), 0, "gastar a pilha de B mexeu no Núcleo");
            } finally {
                close(a);
                close(b);
            }
        });
    }
}
