package io.github.matheusanbs.wirelessautomate.tv;

import io.github.matheusanbs.wirelessautomate.packet.TvStorageContentsPayload;
import io.github.matheusanbs.wirelessautomate.packet.TvStorageContentsRequestPayload;
import io.github.matheusanbs.wirelessautomate.storage.StorageChestBlockEntity;
import it.unimi.dsi.fastutil.objects.Reference2LongOpenHashMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * Os itens de um Baú ligado à Tela, para a grade de itens da aba Dado (decisão 5 da spec): só com o menu do editor
 * aberto, no máximo 10 pedidos por segundo, só de um armazenamento da própria Tela e só se o chunk dele está carregado
 * (nunca o carrega). A resposta soma por item (sem os componentes), filtra pela busca do pedido e vai do mais numeroso
 * ao menos, até {@link TvStorageContentsPayload#MAX_ITEMS}, com o total que casou.
 */
public final class TvStorageContents {
    private TvStorageContents() {
    }

    /** Trata o pedido; devolve a resposta (ou {@code null} se o pedido foi recusado), mandada a quem tem o canal. */
    public static @Nullable TvStorageContentsPayload handle(@Nullable ServerPlayer player,
            TvStorageContentsRequestPayload request) {
        TvBlockEntity tv = TvEditing.open(player, request.containerId());
        if (tv == null || !TvEditing.allowAction(player) || !tv.hasLink(request.pos())) {
            return null;
        }
        TvStorageContentsPayload payload = read(player, request);
        if (player.connection != null && player.connection.hasChannel(payload)) {
            PacketDistributor.sendToPlayer(player, payload);
        }
        return payload;
    }

    private static TvStorageContentsPayload read(ServerPlayer player, TvStorageContentsRequestPayload request) {
        ServerLevel level = player.server.getLevel(request.pos().dimension());
        if (level == null || !level.isLoaded(request.pos().pos())
                || !(level.getBlockEntity(request.pos().pos()) instanceof StorageChestBlockEntity chest)
                || chest.isRemoved()) {
            return new TvStorageContentsPayload(request.containerId(), request.pos(), false, 0, List.of());
        }
        Result result = list(chest, request.query());
        return new TvStorageContentsPayload(request.containerId(), request.pos(), true, result.total(), result.items());
    }

    /** Os itens que casam e quantos casam no total. */
    public record Result(int total, List<TvStorageContentsPayload.Entry> items) {
    }

    /**
     * Os tipos do Baú que casam com a busca ({@link TvItemSearch}, pelo nome em inglês do servidor e pelo id), do mais
     * numeroso ao menos, até {@link TvStorageContentsPayload#MAX_ITEMS}, com o total que casa. Uma passada pelos
     * slots, só aqui, a cada pedido.
     */
    public static Result list(StorageChestBlockEntity chest, String query) {
        TvItemSearch search = TvItemSearch.parse(query);
        var storage = chest.storage();
        Reference2LongOpenHashMap<Item> totals = new Reference2LongOpenHashMap<>();
        for (int slot = 0; slot < storage.slots(); slot++) {
            long count = storage.count(slot);
            if (count > 0) {
                totals.addTo(storage.key(slot).getItem(), count);
            }
        }
        List<TvStorageContentsPayload.Entry> items = new ArrayList<>(totals.size());
        for (var entry : totals.reference2LongEntrySet()) {
            Item item = entry.getKey();
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            if (search.isEmpty() || search.matches(item.getDescription().getString(), id.getNamespace(), id.getPath())) {
                items.add(new TvStorageContentsPayload.Entry(id, entry.getLongValue()));
            }
        }
        items.sort(Comparator.comparingLong(TvStorageContentsPayload.Entry::count).reversed()
                .thenComparing(entry -> entry.item().toString()));
        int total = items.size();
        if (total > TvStorageContentsPayload.MAX_ITEMS) {
            items = new ArrayList<>(items.subList(0, TvStorageContentsPayload.MAX_ITEMS));
        }
        return new Result(total, items);
    }
}
