package io.github.matheusanbs.wirelessautomate.client.dev.vitrine;

import io.github.matheusanbs.wirelessautomate.client.dev.DevEndToEnd.Step;
import io.github.matheusanbs.wirelessautomate.client.dev.E2eUi;
import io.github.matheusanbs.wirelessautomate.client.filter.FilterScreen;
import io.github.matheusanbs.wirelessautomate.client.router.RouterScreen;
import io.github.matheusanbs.wirelessautomate.filter.ItemRule;
import io.github.matheusanbs.wirelessautomate.network.NetworkSavedData;
import io.github.matheusanbs.wirelessautomate.network.PortMode;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * A tela do Filtro: três itens do inventário entram na lista (Shift + clique, pelo mesmo clique rápido do menu), a aba
 * Tags marca a tag do minério cru e a aba Regra monta "durabilidade abaixo de 50%". A câmera chega à fornalha, o
 * roteador abre e o cursor entra no filtro; o fundo é a fornalha com o roteador, ao fim da tarde.
 */
final class CenaFiltro {
    static final String NOME = "filtro";
    private static final String CHAVE = "gui.wirelessautomate.filter.";
    private static final long IR_MS = 300;
    private static final long DEPOIS_MS = 200;

    private CenaFiltro() {
    }

    static void passos(List<Step> lista, int indice) {
        Supplier<BlockPos> base = () -> Palco.base(indice);
        Supplier<BlockPos> roteador = () -> base.get().offset(0, 1, 0);
        lista.add(Palco.montar(NOME, nivel -> montar(nivel, base.get()), roteador));
        Palco.chao(lista, NOME, () -> base.get().offset(0, -1, 2), 24);
        double[] mira = new double[3];
        Supplier<double[]> olho = () -> new double[] {base.get().getX() + 3.4, base.get().getY() + 2.4,
                base.get().getZ() + 3.6};
        Supplier<double[]> alvo = () -> {
            mira[0] = base.get().getX() + 0.5;
            mira[1] = base.get().getY() + 1.2;
            mira[2] = base.get().getZ() + 0.5;
            return mira;
        };
        Supplier<double[]> longe = () -> new double[] {base.get().getX() + 5.0, base.get().getY() + 3.0, base.get().getZ() + 5.4};
        lista.add(Passos.ate("câmera na fornalha", () -> Camera.saltar(longe.get(), alvo.get()), () -> Camera.em(longe.get())));
        lista.add(Passos.esperar(1200));
        lista.add(Gravador.iniciar(NOME));
        lista.add(Camera.mover(1800, t -> Camera.entre(new double[][] {longe.get(), alvo.get()}, new double[][] {olho.get(), alvo.get()}, t)));
        lista.add(Passos.esperar(1900));
        lista.add(Palco.usar("abrir o roteador", roteador, () -> Minecraft.getInstance().screen instanceof RouterScreen));
        long[] ultimo = new long[1];
        lista.add(Passos.ate("abrir o filtro", () -> {
            if (Minecraft.getInstance().screen instanceof FilterScreen) {
                return true;
            }
            if (System.nanoTime() - ultimo[0] > 1_500_000_000L && Minecraft.getInstance().screen instanceof RouterScreen tela) {
                ultimo[0] = System.nanoTime();
                tela.syncNow();
                int[] editar = tela.panel().editCenter();
                if (editar != null) {
                    E2eUi.click(tela, editar[0], editar[1]);
                }
            }
            return false;
        }));
        lista.add(Passos.esperar(400));
        lista.add(Cursor.centralizar());
        for (int i = 0; i < 3; i++) {
            int slot = i;
            lista.add(Cursor.ir(() -> inventario(slot), IR_MS));
            lista.add(Passos.esperar(100));
            lista.add(Passos.agir("Shift + clique", () -> {
                var jogo = Minecraft.getInstance();
                jogo.gameMode.handleInventoryMouseClick(tela().getMenu().containerId, indiceDo(slot), 0,
                        ClickType.QUICK_MOVE, jogo.player);
            }));
            lista.add(Passos.esperar(DEPOIS_MS));
        }
        Cursor.tocar(lista, () -> botao("tab.tags"), IR_MS, DEPOIS_MS);
        lista.add(Passos.ate("aba Tags", () -> tela().tab() == FilterScreen.Tab.TAGS));
        lista.add(Cursor.ir(() -> inventario(0), IR_MS));
        lista.add(Passos.agir("inspecionar o minério", () -> tela().previewInspect(new ItemStack(Items.RAW_IRON))));
        lista.add(Passos.ate("tags do minério", () -> tela().candidateLabels().contains("#c:raw_materials")));
        Cursor.tocar(lista, () -> tela().candidateCenter(tela().candidateLabels().indexOf("#c:raw_materials")), IR_MS, DEPOIS_MS);
        Cursor.tocar(lista, () -> botao("tags.add.action"), IR_MS, 500);
        Cursor.tocar(lista, () -> botao("tab.rule"), IR_MS, DEPOIS_MS);
        lista.add(Passos.agir("regra: durabilidade abaixo de 50%", () -> tela().previewRule(ItemRule.EMPTY
                .withDurability(Optional.of(new ItemRule.Durability(false, 50))))));
        lista.add(Passos.esperar(900));
        lista.add(Gravador.parar());
    }

    private static FilterScreen tela() {
        return (FilterScreen) Minecraft.getInstance().screen;
    }

    /** O centro do botão da tela cuja mensagem é a chave {@code gui.wirelessautomate.filter.<sufixo>}. */
    private static int[] botao(String sufixo) {
        for (GuiEventListener filho : tela().children()) {
            if (filho instanceof AbstractWidget w && w.visible && w.getMessage().getContents() instanceof TranslatableContents c
                    && c.getKey().equals(CHAVE + sufixo)) {
                return new int[] {w.getX() + w.getWidth() / 2, w.getY() + w.getHeight() / 2};
            }
        }
        throw new IllegalStateException("sem o botão " + sufixo);
    }

    /** O slot {@code 9 + n} do inventário, no menu. */
    private static Slot slotDo(int n) {
        for (Slot slot : tela().getMenu().slots) {
            if (slot.container instanceof Inventory && slot.getContainerSlot() == 9 + n) {
                return slot;
            }
        }
        throw new IllegalStateException("sem o slot " + n);
    }

    private static int indiceDo(int n) {
        return tela().getMenu().slots.indexOf(slotDo(n));
    }

    private static int[] inventario(int n) {
        Slot slot = slotDo(n);
        return new int[] {tela().getGuiLeft() + slot.x + 8, tela().getGuiTop() + slot.y + 8};
    }

    /** A fornalha com o roteador na rede (Extrai), o inventário do jogador com minério cru e a praça. */
    private static void montar(net.minecraft.server.level.ServerLevel nivel, BlockPos b) {
        var servidor = nivel.getServer();
        Palco.praca(nivel, b.offset(0, -1, 2), 9, 8);
        UUID dono = servidor.getPlayerList().getPlayers().get(0).getUUID();
        UUID rede = NetworkSavedData.get(servidor).create(dono, "Smelting Line").id();
        Fabrica.fornalha(nivel, b);
        Fabrica.roteador(servidor, b, rede, "Furnace", PortMode.EXTRACT);
        var inventario = servidor.getPlayerList().getPlayer(dono).getInventory();
        inventario.clearContent();
        inventario.setItem(9, new ItemStack(Items.RAW_IRON, 64));
        inventario.setItem(10, new ItemStack(Items.RAW_GOLD, 64));
        inventario.setItem(11, new ItemStack(Items.RAW_COPPER, 64));
        ItemStack picareta = new ItemStack(Items.DIAMOND_PICKAXE);
        picareta.setDamageValue(picareta.getMaxDamage() * 7 / 10);
        inventario.setItem(12, picareta);
        inventario.setItem(13, new ItemStack(Items.DIAMOND_SWORD));
    }
}
