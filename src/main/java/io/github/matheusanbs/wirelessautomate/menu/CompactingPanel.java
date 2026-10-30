package io.github.matheusanbs.wirelessautomate.menu;

import io.github.matheusanbs.wirelessautomate.item.ChestUpgradeItem;
import io.github.matheusanbs.wirelessautomate.packet.CompactingRulePayload;
import io.github.matheusanbs.wirelessautomate.packet.CompactingStatePayload;
import io.github.matheusanbs.wirelessautomate.registry.ModDataComponents;
import io.github.matheusanbs.wirelessautomate.storage.StorageChestBlockEntity;
import io.github.matheusanbs.wirelessautomate.storage.upgrade.ChestCompacting;
import io.github.matheusanbs.wirelessautomate.storage.upgrade.ChestUpgrades;
import io.github.matheusanbs.wirelessautomate.storage.upgrade.CompactingChain;
import io.github.matheusanbs.wirelessautomate.storage.upgrade.CompactingChains;
import io.github.matheusanbs.wirelessautomate.storage.upgrade.CompactingMode;
import io.github.matheusanbs.wirelessautomate.storage.upgrade.CompactingRules;
import io.github.matheusanbs.wirelessautomate.storage.upgrade.VariantGroup;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;

/**
 * O painel da Compactação do menu do Baú. Servidor: o cliente avisa abrir e fechar ({@code WATCH}/{@code UNWATCH}); só
 * com o painel aberto o {@link #broadcast()} monta e manda o {@link CompactingStatePayload}, e só quando a versão do
 * {@link ChestCompacting} muda (as regras ou as cadeias presentes). As mudanças do cliente chegam como
 * {@link CompactingRulePayload}, e o servidor as valida ({@link #handle}). Cliente: guarda o último estado recebido
 * ({@link #state()}), que some com o menu; o servidor guarda o último montado.
 */
public final class CompactingPanel {
    /** Chave de envio de "sem Upgrade de Compactação": diferente de qualquer versão. */
    private static final int INACTIVE = -2;
    /** Nada enviado ainda: o primeiro envio do painel aberto sai sempre. */
    private static final int UNSENT = -1;

    /** Estados montados desde a subida, para os GameTests. */
    private static int sentStates;

    private final StorageListMenu<?> menu;
    private final @Nullable StorageChestBlockEntity chest;
    private final @Nullable ServerPlayer viewer;
    private boolean watching;
    private int sentKey = INACTIVE;
    private @Nullable CompactingStatePayload state;

    CompactingPanel(StorageListMenu<?> menu, Player player, @Nullable StorageChestBlockEntity chest) {
        this.menu = menu;
        this.chest = chest;
        this.viewer = chest != null && player instanceof ServerPlayer server ? server : null;
    }

    /** Quantos estados o servidor montou; só para os testes. */
    public static int sentStates() {
        return sentStates;
    }

    /** Cliente: o último estado recebido; servidor: o último montado (para os GameTests); ou {@code null}. */
    public @Nullable CompactingStatePayload state() {
        return state;
    }

    /** O servidor está mandando o estado deste menu (o painel está aberto). */
    public boolean watching() {
        return watching;
    }

    /** Servidor, no {@code broadcastChanges} do menu: manda o estado se o painel está aberto e algo mudou. */
    void broadcast() {
        if (!watching || chest == null || viewer == null) {
            return;
        }
        ChestCompacting compacting = chest.compacting();
        boolean active = chest.upgrades().has(ChestUpgradeItem.Kind.COMPACTING);
        int key = active ? compacting.version() : INACTIVE;
        if (key == sentKey) {
            return;
        }
        boolean nothingToClear = key == INACTIVE && sentKey == UNSENT;
        sentKey = key;
        if (nothingToClear) {
            return;
        }
        sentStates++;
        CompactingStatePayload payload = active ? build(compacting)
                : new CompactingStatePayload(menu.containerId, CompactingMode.OFF, List.of());
        state = payload;
        // Jogadores falsos (GameTests, mods de automação) não negociam os canais do mod.
        if (viewer.connection != null && viewer.connection.hasChannel(payload)) {
            PacketDistributor.sendToPlayer(viewer, payload);
        }
    }

    private CompactingStatePayload build(ChestCompacting compacting) {
        CompactingRules rules = compacting.rules() == null ? CompactingRules.DEFAULT : compacting.rules();
        CompactingChains chains = CompactingChains.get();
        IntList present = compacting.presentChains();
        List<CompactingStatePayload.Row> rows = new ArrayList<>(Math.min(present.size(), CompactingStatePayload.MAX_ROWS));
        // Um grupo de variantes é uma linha só, a da preferida, na posição da primeira variante presente.
        Set<CompactingChain> shown = new HashSet<>();
        for (int i = 0; i < present.size() && rows.size() < CompactingStatePayload.MAX_ROWS; i++) {
            CompactingChain chain = chains.byId(present.getInt(i));
            if (chain == null) {
                continue;
            }
            VariantGroup group = chains.groupOf(chain);
            CompactingChain row = group == null ? chain : rules.preferredOf(group);
            if (!shown.add(row)) {
                continue;
            }
            List<Item> variants = group == null ? List.of() : variantBases(group, row);
            rows.add(new CompactingStatePayload.Row(List.of(row.forms()), rules.targetLevel(row), rules.hasOwn(row),
                    variants, Math.max(0, variants.indexOf(row.base()))));
        }
        addAbsent(rules, chains, shown, rows);
        return new CompactingStatePayload(menu.containerId, rules.mode(), rows);
    }

    /**
     * Depois das presentes, as cadeias com regra própria sem item no Baú, apagadas, para o jogador ver e mudar: a que
     * tem forma nos alvos e o grupo cuja variante escolhida não é a padrão (a primeira do grupo). Ids que nenhuma
     * cadeia tem (mod removido) são ignorados. Uma linha por cadeia ou grupo, na ordem do id da cadeia.
     */
    private static void addAbsent(CompactingRules rules, CompactingChains chains, Set<CompactingChain> shown,
            List<CompactingStatePayload.Row> rows) {
        if (rows.size() >= CompactingStatePayload.MAX_ROWS || rules.entries() == 0) {
            return;
        }
        List<CompactingChain> absent = new ArrayList<>();
        for (ResourceLocation id : rules.targets()) {
            CompactingChain chain = BuiltInRegistries.ITEM.getOptional(id).map(chains::chainOf).orElse(null);
            addAbsentRow(chain, rules, chains, shown, absent, false);
        }
        for (ResourceLocation id : rules.preferred()) {
            CompactingChain chain = BuiltInRegistries.ITEM.getOptional(id).map(chains::chainOf).orElse(null);
            addAbsentRow(chain, rules, chains, shown, absent, true);
        }
        absent.sort(Comparator.comparingInt(CompactingChain::id));
        for (int i = 0; i < absent.size() && rows.size() < CompactingStatePayload.MAX_ROWS; i++) {
            CompactingChain row = absent.get(i);
            VariantGroup group = chains.groupOf(row);
            List<Item> variants = group == null ? List.of() : variantBases(group, row);
            rows.add(new CompactingStatePayload.Row(List.of(row.forms()), rules.targetLevel(row), rules.hasOwn(row),
                    variants, Math.max(0, variants.indexOf(row.base())), false));
        }
    }

    private static void addAbsentRow(@Nullable CompactingChain chain, CompactingRules rules, CompactingChains chains,
            Set<CompactingChain> shown, List<CompactingChain> absent, boolean fromPreferred) {
        if (chain == null) {
            return;
        }
        VariantGroup group = chains.groupOf(chain);
        CompactingChain row = group == null ? chain : rules.preferredOf(group);
        if (fromPreferred && (group == null || row == group.chains().get(0))) {
            return;
        }
        if (shown.add(row)) {
            absent.add(row);
        }
    }

    /**
     * As bases das variantes do grupo, na ordem dele, até {@link CompactingStatePayload.Row#MAX_VARIANTS}; a preferida
     * entra sempre, mesmo depois do teto (no lugar da última).
     */
    private static List<Item> variantBases(VariantGroup group, CompactingChain preferred) {
        List<CompactingChain> chains = group.chains();
        int max = CompactingStatePayload.Row.MAX_VARIANTS;
        List<Item> bases = new ArrayList<>(Math.min(chains.size(), max));
        boolean hasPreferred = false;
        for (int i = 0; i < chains.size() && bases.size() < max; i++) {
            CompactingChain chain = chains.get(i);
            if (chain == preferred || hasPreferred || bases.size() < max - 1) {
                bases.add(chain.base());
                hasPreferred |= chain == preferred;
            }
        }
        return bases;
    }

    /** Cliente: o pacote do servidor; vale só para o menu aberto com esse {@code containerId}. */
    public static void onState(CompactingStatePayload payload, IPayloadContext context) {
        if (context.player().containerMenu instanceof StorageListMenu<?> open && open.containerId == payload.containerId()
                && open.upgradeSlots() != null) {
            open.upgradeSlots().compactingPanel().state = payload;
        }
    }

    // ------------------------------------------------------------------ regras

    /**
     * Servidor: aplica a mudança do cliente. Recusa (e não muda nada) sem o menu do Baú aberto com esse
     * {@code containerId}, com o menu fora de alcance, sem o Upgrade de Compactação, com o modo fora do alcance do
     * enum, com um item que não pertence a uma cadeia do índice atual, com uma preferida que não é a forma base de uma
     * cadeia com variantes ou com um alvo ou preferida que passaria do {@link CompactingRules#MAX_TARGETS} (os dois
     * somados).
     *
     * @return se foi aceita
     */
    public static boolean handle(@Nullable ServerPlayer player, CompactingRulePayload payload) {
        if (player == null || !(player.containerMenu instanceof StorageListMenu<?> open)
                || open.containerId != payload.containerId() || !open.stillValid(player)
                || open.upgradeSlots() == null || !(open.storage() instanceof StorageChestBlockEntity chest)) {
            return false;
        }
        CompactingPanel panel = open.upgradeSlots().compactingPanel();
        switch (payload.kind()) {
            case WATCH -> {
                // Recomeça: o estado enviado antes pode ter se perdido com o painel fechado.
                panel.watching = true;
                panel.sentKey = UNSENT;
                return true;
            }
            case UNWATCH -> {
                panel.watching = false;
                return true;
            }
            default -> {
                return panel.change(chest, payload);
            }
        }
    }

    private boolean change(StorageChestBlockEntity chest, CompactingRulePayload payload) {
        ChestUpgrades upgrades = chest.upgrades();
        int slot = compactingSlot(upgrades);
        if (slot < 0) {
            return false;
        }
        ItemStack stack = upgrades.upgrade(slot);
        CompactingRules rules = stack.getOrDefault(ModDataComponents.COMPACTING_RULES.get(), CompactingRules.DEFAULT);
        CompactingRules next;
        if (payload.kind() == CompactingRulePayload.Kind.MODE) {
            if (payload.mode() < 0 || payload.mode() >= CompactingMode.ALL.length) {
                return false;
            }
            next = rules.withMode(CompactingMode.ALL[payload.mode()]);
        } else if (payload.kind() == CompactingRulePayload.Kind.PREFER) {
            Item base = payload.item().orElse(null);
            CompactingChain chain = base == null ? null : CompactingChains.get().chainOf(base);
            VariantGroup group = chain == null || chain.base() != base ? null : CompactingChains.get().groupOf(chain);
            if (group == null) {
                return false;
            }
            next = rules.withPreferred(group, chain);
            if (next.entries() > CompactingRules.MAX_TARGETS) {
                return false;
            }
        } else {
            Item item = payload.item().orElse(null);
            CompactingChain chain = item == null ? null : CompactingChains.get().chainOf(item);
            if (chain == null) {
                return false;
            }
            next = rules.withTarget(chain, payload.kind() == CompactingRulePayload.Kind.TARGET ? item : null);
            if (next.entries() > CompactingRules.MAX_TARGETS) {
                return false;
            }
        }
        if (!next.equals(rules)) {
            // Uma pilha nova no slot: o aviso do Baú reaplica as regras e marca as cadeias.
            ItemStack changed = stack.copy();
            changed.set(ModDataComponents.COMPACTING_RULES.get(), next);
            upgrades.setUpgrade(slot, changed);
        }
        return true;
    }

    private static int compactingSlot(ChestUpgrades upgrades) {
        for (int i = 0; i < ChestUpgrades.SLOTS; i++) {
            if (upgrades.upgrade(i).getItem() instanceof ChestUpgradeItem item
                    && item.kind() == ChestUpgradeItem.Kind.COMPACTING) {
                return i;
            }
        }
        return -1;
    }
}
