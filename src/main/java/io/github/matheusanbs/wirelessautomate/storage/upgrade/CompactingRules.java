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
 * As regras de um Upgrade de Compactação: o modo global e, por cadeia, o item alvo. Os alvos são ids em texto, não
 * {@link Item}: um mod removido não pode fazer o componente falhar ao ler (isso apagaria o item do upgrade), e a regra
 * continua achando a cadeia se uma forma nova entrar abaixo da base num {@code /reload}. Um id que nenhuma cadeia
 * tem é ignorado.
 */
public record CompactingRules(CompactingMode mode, Set<ResourceLocation> targets) {
    /** O upgrade novo não muda nada até o jogador escolher. */
    public static final CompactingRules DEFAULT = new CompactingRules(CompactingMode.OFF, Set.of());

    public static final Codec<CompactingRules> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            CompactingMode.CODEC.fieldOf("mode").forGetter(CompactingRules::mode),
            ResourceLocation.CODEC.listOf().optionalFieldOf("targets", List.of())
                    .forGetter(rules -> rules.targets.stream().sorted().toList())
    ).apply(instance, (mode, targets) -> new CompactingRules(mode, Set.copyOf(targets))));

    public static final StreamCodec<ByteBuf, CompactingRules> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.idMapper(index -> CompactingMode.ALL[index], CompactingMode::ordinal),
            CompactingRules::mode,
            ByteBufCodecs.collection(HashSet<ResourceLocation>::new, ResourceLocation.STREAM_CODEC, 256)
                    .map(Set::copyOf, HashSet::new),
            CompactingRules::targets,
            CompactingRules::new);

    public CompactingRules withMode(CompactingMode newMode) {
        return new CompactingRules(newMode, targets);
    }

    /** Troca o alvo da cadeia: tira do conjunto todas as formas dela e põe a nova; {@code null} volta ao modo global. */
    public CompactingRules withTarget(CompactingChain chain, @Nullable Item target) {
        Set<ResourceLocation> next = new HashSet<>(targets);
        for (Item form : chain.forms()) {
            next.remove(BuiltInRegistries.ITEM.getKey(form));
        }
        if (target != null) {
            next.add(BuiltInRegistries.ITEM.getKey(target));
        }
        return new CompactingRules(mode, Set.copyOf(next));
    }

    /** A cadeia tem alvo próprio (alguma forma dela no conjunto). */
    public boolean hasOwn(CompactingChain chain) {
        return ownLevel(chain) >= 0;
    }

    /**
     * O nível de destino da cadeia: o da primeira forma dela que está nos alvos; sem nenhuma, o do modo global
     * (Compactar vai ao topo, Descompactar à base); {@code -1} é não mexer.
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
        Item[] forms = chain.forms();
        for (int level = 0; level < forms.length; level++) {
            if (targets.contains(BuiltInRegistries.ITEM.getKey(forms[level]))) {
                return level;
            }
        }
        return -1;
    }
}
