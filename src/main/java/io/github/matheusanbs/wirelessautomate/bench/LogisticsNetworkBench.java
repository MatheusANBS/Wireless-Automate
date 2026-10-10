package io.github.matheusanbs.wirelessautomate.bench;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraftforge.fml.ModList;
import org.jetbrains.annotations.Nullable;

/**
 * Adaptador do benchmark comparativo para o Logistics Network (docs/benchmark-logistics-network.md), só por
 * reflexão: nenhum tipo do mod no nosso código e nenhuma dependência no build (o mod é All Rights
 * Reserved; o jar só existe em {@code run/bench/mods}, posto pelo {@code scripts/bench.sh}).
 *
 * <p>Usa a API pública dele, a mesma que a tela, o colar e a colocação em massa usam (versão 1.17.2):
 * {@code NodePlacementHelper.placeNode} põe o nó, {@code loadNodeState} aplica canais e upgrades pelo
 * codec do nó, e {@code NodeClipboardConfig.joinNetwork} põe o nó numa rede do {@code NetworkRegistry}.
 * O codec dele é leniente (um campo errado vira o padrão em silêncio), então {@link #place} confere o
 * canal gravado de volta e falha alto se não bater. Qualquer falha de reflexão lança
 * {@link IllegalStateException}: o cenário aborta em vez de medir pela metade.
 *
 * <p>TODO etapa 3 (porte 1.20.1): a versão 1.20.1 do Logistics Network (1.3.0) não tem {@code loadNodeState},
 * {@code saveNodeState}, {@code NodeClipboardConfig.joinNetwork} nem {@code Config.asyncPlanning}; com ela, o
 * construtor falha na reflexão e o cenário {@code ln} aborta com {@link IllegalStateException} (nada é medido pela
 * metade). O adaptador por setters dessa versão fica para a etapa 3; {@link LnChannelPlan} não muda.
 */
final class LogisticsNetworkBench {
    static final String MOD_ID = "logisticsnetworks";
    private static final String PKG = "me.almana.logisticsnetworks.";
    private static final String NODE_KEY = "logisticsnetworks:node";
    /** Padrões do canal no codec dele ({@code ClipboardSnapshot.ChannelState}), já normalizados. */
    private static final Map<String, String> CHANNEL_DEFAULTS = Map.of("enabled", "0", "mode", "import",
            "batch_size", "8", "tick_delay", "20", "direction", "all", "distribution_mode", "priority");

    private static @Nullable LogisticsNetworkBench instance;

    private final Method placeNode;
    private final Method loadNodeState;
    private final Method saveNodeState;
    private final Method registryGet;
    private final Method createNetwork;
    private final Method deleteNetwork;
    private final Method networkId;
    private final Method joinNetwork;
    private final Field asyncPlanning;

    private LogisticsNetworkBench() throws ReflectiveOperationException {
        Class<?> helper = Class.forName(PKG + "logic.NodePlacementHelper");
        Class<?> node = Class.forName(PKG + "entity.LogisticsNodeEntity");
        Class<?> registry = Class.forName(PKG + "data.NetworkRegistry");
        Class<?> network = Class.forName(PKG + "data.LogisticsNetwork");
        Class<?> clipboard = Class.forName(PKG + "data.NodeClipboardConfig");
        Class<?> config = Class.forName(PKG + "Config");
        placeNode = helper.getMethod("placeNode", Level.class, BlockPos.class, UUID.class);
        loadNodeState = node.getMethod("loadNodeState", CompoundTag.class);
        saveNodeState = node.getMethod("saveNodeState");
        registryGet = registry.getMethod("get", ServerLevel.class);
        createNetwork = registry.getMethod("createNetwork", String.class, UUID.class);
        deleteNetwork = registry.getMethod("deleteNetwork", UUID.class);
        networkId = network.getMethod("getId");
        joinNetwork = clipboard.getMethod("joinNetwork", node, registry, network);
        asyncPlanning = config.getField("asyncPlanning");
    }

    static boolean loaded() {
        return ModList.get().isLoaded(MOD_ID);
    }

    static String version() {
        return ModList.get().getModContainerById(MOD_ID).map(c -> c.getModInfo().getVersion().toString()).orElse("?");
    }

    static LogisticsNetworkBench get() {
        if (instance == null) {
            if (!loaded()) {
                throw new IllegalStateException("o Logistics Network não está carregado");
            }
            try {
                instance = new LogisticsNetworkBench();
            } catch (ReflectiveOperationException | LinkageError e) {
                throw new IllegalStateException("API do Logistics Network diferente da 1.17.2: " + e, e);
            }
        }
        return instance;
    }

    /**
     * Um canal: tipo ({@code item}, {@code fluid}, {@code energy}), sender (export) ou receiver (import),
     * lote e modo de distribuição ({@code priority} ou {@code round_robin}).
     */
    record Channel(String type, boolean export, int batch, String distribution) {
    }

    /**
     * Põe um nó sem rede no bloco {@code machine}, com o canal 0 configurado pelo lado de cima (o mesmo
     * lado que o roteador usa) e o upgrade no slot 0. Devolve a entidade.
     */
    Entity place(ServerLevel level, BlockPos machine, UUID owner, Channel channel, String upgrade) {
        Entity node = (Entity) invoke(placeNode, null, level, machine, owner);
        if (node == null) {
            throw new IllegalStateException("o Logistics Network recusou o nó em " + machine.toShortString()
                    + " (bloco sem capability ou já com nó)");
        }
        CompoundTag state = new CompoundTag();
        state.put("attached_pos", new IntArrayTag(new int[] {machine.getX(), machine.getY(), machine.getZ()}));
        state.putBoolean("valid", true);
        CompoundTag ch = new CompoundTag();
        ch.putBoolean("enabled", true);
        ch.putString("mode", channel.export() ? "export" : "import");
        ch.putString("type", channel.type());
        ch.putInt("batch_size", channel.batch());
        ch.putInt("tick_delay", LnChannelPlan.TICK_DELAY);
        ch.putString("direction", "up");
        ch.putString("redstone_mode", "ignored");
        ch.putString("distribution_mode", channel.distribution());
        ListTag channels = new ListTag();
        channels.add(ch);
        state.put("channels", channels);
        CompoundTag item = new CompoundTag();
        item.putString("id", upgrade);
        item.putInt("count", 1);
        CompoundTag slot = new CompoundTag();
        slot.putInt("slot", 0);
        slot.put("item", item);
        ListTag upgrades = new ListTag();
        upgrades.add(slot);
        state.put("upgrades", upgrades);
        CompoundTag root = new CompoundTag();
        root.put(NODE_KEY, state);
        invoke(loadNodeState, node, root);
        check(node, ch, upgrade);
        return node;
    }

    /** Lê o estado gravado do nó e confere o canal 0 e o upgrade (o codec dele ignora erros em silêncio). */
    private void check(Entity node, CompoundTag expected, String upgrade) {
        CompoundTag saved = ((CompoundTag) invoke(saveNodeState, node)).getCompound(NODE_KEY);
        ListTag channels = saved.getList("channels", Tag.TAG_COMPOUND);
        CompoundTag first = channels.isEmpty() ? new CompoundTag() : channels.getCompound(0);
        for (String key : List.of("enabled", "mode", "type", "batch_size", "tick_delay", "direction",
                "distribution_mode")) {
            Tag want = expected.get(key);
            // O codec omite o campo igual ao padrão (o modo "import", por exemplo).
            String got = first.contains(key) ? normalize(first.get(key)) : CHANNEL_DEFAULTS.get(key);
            if (!normalize(want).equals(got)) {
                throw new IllegalStateException("canal do Logistics Network não aplicado: " + key + " = " + got
                        + ", esperado " + want);
            }
        }
        ListTag upgrades = saved.getList("upgrades", Tag.TAG_COMPOUND);
        String got = upgrades.isEmpty() ? "" : upgrades.getCompound(0).getCompound("item").getString("id");
        if (!got.equals(upgrade)) {
            throw new IllegalStateException("upgrade do Logistics Network não aplicado: " + got + ", esperado " + upgrade);
        }
    }

    /** Booleano gravado como byte: compara pelo texto ({@code 1b} e {@code true} viram o mesmo). */
    private static String normalize(Tag tag) {
        String text = tag.getAsString().toLowerCase(Locale.ROOT);
        return text.equals("true") ? "1" : text.equals("false") ? "0" : text;
    }

    /** Cria uma rede e põe os nós nela, como a tela faz. Devolve o id da rede. */
    UUID attach(ServerLevel level, String name, UUID owner, List<Entity> nodes) {
        Object registry = invoke(registryGet, null, level);
        Object network = invoke(createNetwork, registry, name, owner);
        for (Entity node : nodes) {
            invoke(joinNetwork, null, node, registry, network);
        }
        return (UUID) invoke(networkId, network);
    }

    /** Tira os nós (sem soltar itens, como o {@code /ln removeNodes} faz depois de esvaziar) e apaga as redes. */
    void teardown(ServerLevel level, List<Entity> nodes, List<UUID> networks) {
        for (Entity node : nodes) {
            node.discard();
        }
        Object registry = invoke(registryGet, null, level);
        for (UUID network : networks) {
            invoke(deleteNetwork, registry, network);
        }
    }

    /** Liga ou desliga o planejamento assíncrono dele; o agendador relê o campo a cada tick. */
    void setAsync(boolean enabled) {
        try {
            asyncPlanning.setBoolean(null, enabled);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("não deu para mudar o asyncPlanning do Logistics Network", e);
        }
    }

    boolean async() {
        try {
            return asyncPlanning.getBoolean(null);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("não deu para ler o asyncPlanning do Logistics Network", e);
        }
    }

    private static Object invoke(Method method, @Nullable Object target, Object... args) {
        try {
            return method.invoke(target, args);
        } catch (ReflectiveOperationException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            throw new IllegalStateException("Logistics Network: " + method.getName() + " falhou: " + cause, cause);
        }
    }
}
