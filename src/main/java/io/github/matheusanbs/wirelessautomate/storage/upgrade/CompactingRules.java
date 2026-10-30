package io.github.matheusanbs.wirelessautomate.storage.upgrade;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import org.jetbrains.annotations.Nullable;

/**
 * As regras de um Upgrade de Compactação: o modo global, por cadeia o item alvo e, por grupo de variantes, a variante
 * preferida (pela forma base dela). Os ids são texto, não {@link Item}: um mod removido não pode fazer o componente
 * falhar ao ler (isso apagaria o item do upgrade), e a regra continua achando a cadeia se uma forma nova entrar abaixo
 * da base num {@code /reload}. Um id que nenhuma cadeia tem é ignorado.
 *
 * <p>Numa cadeia com variantes, o alvo vale pela forma: o de qualquer variante do grupo dá o nível de todas
 * ({@link #targetLevel}), então trocar a preferida mantém o alvo.
 */
public record CompactingRules(CompactingMode mode, Set<ResourceLocation> targets, Set<ResourceLocation> preferred) {
    /**
     * O teto de alvos próprios e preferidas somados: o {@link #STREAM_CODEC} não codifica mais que isso em cada
     * conjunto, e um item que passasse dele derrubaria quem o recebe. O painel recusa a mudança que passaria do teto e
     * a leitura para nele.
     */
    public static final int MAX_TARGETS = 256;

    /** O upgrade novo não muda nada até o jogador escolher. */
    public static final CompactingRules DEFAULT = new CompactingRules(CompactingMode.OFF, Set.of(), Set.of());

    public static final Codec<CompactingRules> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            CompactingMode.CODEC.optionalFieldOf("mode", CompactingMode.OFF).forGetter(CompactingRules::mode),
            Codec.STRING.listOf().optionalFieldOf("targets", List.of()).forGetter(rules -> sorted(rules.targets)),
            Codec.STRING.listOf().optionalFieldOf("preferred", List.of()).forGetter(rules -> sorted(rules.preferred))
    ).apply(instance, (mode, targets, preferred) -> {
        Set<ResourceLocation> parsedTargets = parse(targets, MAX_TARGETS);
        return new CompactingRules(mode, parsedTargets, parse(preferred, MAX_TARGETS - parsedTargets.size()));
    }));

    public static final StreamCodec<ByteBuf, CompactingRules> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.idMapper(index -> CompactingMode.ALL[index], CompactingMode::ordinal),
            CompactingRules::mode,
            idSet(),
            CompactingRules::targets,
            idSet(),
            CompactingRules::preferred,
            CompactingRules::new);

    /** As regras sem preferida escolhida. */
    public CompactingRules(CompactingMode mode, Set<ResourceLocation> targets) {
        this(mode, targets, Set.of());
    }

    private static StreamCodec<ByteBuf, Set<ResourceLocation>> idSet() {
        return ByteBufCodecs.collection(HashSet<ResourceLocation>::new, ResourceLocation.STREAM_CODEC, MAX_TARGETS)
                .map(Set::copyOf, HashSet::new);
    }

    private static List<String> sorted(Set<ResourceLocation> ids) {
        return ids.stream().map(ResourceLocation::toString).sorted().toList();
    }

    /**
     * Os ids válidos, até {@code limit}; os de sintaxe inválida e os acima do teto (dados antigos, um {@code /give})
     * somem, para o componente nunca falhar ao ler nem ao mandar.
     */
    private static Set<ResourceLocation> parse(List<String> names, int limit) {
        Set<ResourceLocation> parsed = new HashSet<>();
        for (String name : names) {
            if (parsed.size() >= limit) {
                break;
            }
            ResourceLocation id = ResourceLocation.tryParse(name);
            if (id != null) {
                parsed.add(id);
            }
        }
        return Set.copyOf(parsed);
    }

    /** Quantas entradas contam no {@link #MAX_TARGETS}: os alvos e as preferidas. */
    public int entries() {
        return targets.size() + preferred.size();
    }

    public CompactingRules withMode(CompactingMode newMode) {
        return new CompactingRules(newMode, targets, preferred);
    }

    /**
     * Troca o alvo da cadeia: tira do conjunto todas as formas dela (e das variantes do grupo dela) e põe a nova;
     * {@code null} volta ao modo global.
     */
    public CompactingRules withTarget(CompactingChain chain, @Nullable Item target) {
        Set<ResourceLocation> next = new HashSet<>(targets);
        VariantGroup group = CompactingChains.get().groupOf(chain);
        for (CompactingChain member : group == null ? List.of(chain) : group.chains()) {
            for (Item form : member.forms()) {
                next.remove(BuiltInRegistries.ITEM.getKey(form));
            }
        }
        if (target != null) {
            next.add(BuiltInRegistries.ITEM.getKey(target));
        }
        return new CompactingRules(mode, Set.copyOf(next), preferred);
    }

    /** Escolhe a variante preferida do grupo: tira as bases das outras e põe a da cadeia. */
    public CompactingRules withPreferred(VariantGroup group, CompactingChain chain) {
        Set<ResourceLocation> next = new HashSet<>(preferred);
        for (CompactingChain member : group.chains()) {
            next.remove(BuiltInRegistries.ITEM.getKey(member.base()));
        }
        next.add(BuiltInRegistries.ITEM.getKey(chain.base()));
        return new CompactingRules(mode, targets, Set.copyOf(next));
    }

    /**
     * A variante preferida do grupo: a escolhida pelo jogador (a primeira do grupo cuja base está nas preferidas) ou,
     * sem escolha, a primeira do grupo (a base em ordem alfabética).
     */
    public CompactingChain preferredOf(VariantGroup group) {
        List<CompactingChain> chains = group.chains();
        if (!preferred.isEmpty()) {
            for (int i = 0; i < chains.size(); i++) {
                if (preferred.contains(BuiltInRegistries.ITEM.getKey(chains.get(i).base()))) {
                    return chains.get(i);
                }
            }
        }
        return chains.get(0);
    }

    /** A cadeia (ou alguma variante do grupo dela) tem alvo próprio. */
    public boolean hasOwn(CompactingChain chain) {
        return ownLevel(chain) >= 0;
    }

    /**
     * O nível de destino da cadeia: o da primeira forma dela (ou de uma variante do grupo dela) que está nos alvos;
     * sem nenhuma, o do modo global (Compactar vai ao topo, Descompactar à base); {@code -1} é não mexer.
     */
    public int targetLevel(CompactingChain chain) {
        int own = ownLevel(chain);
        if (own >= 0) {
            return own;
        }
        return switch (mode) {
            case OFF -> -1;
            case COMPRESS -> chain.forms().length - 1;
            case DECOMPRESS -> 0;
        };
    }

    private int ownLevel(CompactingChain chain) {
        if (targets.isEmpty()) {
            return -1;
        }
        VariantGroup group = CompactingChains.get().groupOf(chain);
        if (group == null) {
            return ownLevelIn(chain);
        }
        List<CompactingChain> chains = group.chains();
        for (int i = 0; i < chains.size(); i++) {
            int level = ownLevelIn(chains.get(i));
            if (level >= 0) {
                return level;
            }
        }
        return -1;
    }

    private int ownLevelIn(CompactingChain chain) {
        Item[] forms = chain.forms();
        for (int level = 0; level < forms.length; level++) {
            if (targets.contains(BuiltInRegistries.ITEM.getKey(forms[level]))) {
                return level;
            }
        }
        return -1;
    }
}
