package io.github.matheusanbs.wirelessautomate.menu;

import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.AccessView;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.Facet;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.GroupView;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.NetworkView;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.Page;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.ProfileSummary;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.QuickCounts;
import io.github.matheusanbs.wirelessautomate.menu.TabletSnapshot.TypeCount;
import io.github.matheusanbs.wirelessautomate.network.NodeIndex.NodeKey;
import io.github.matheusanbs.wirelessautomate.network.ResourceType;
import io.github.matheusanbs.wirelessautomate.preset.ProfileSource;
import io.netty.handler.codec.DecoderException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

/**
 * O codec do cabeçalho do {@link TabletSnapshot}: tudo menos a página de nós (redes, grupos, facetas, contagens dos
 * filtros rápidos, perfil e aviso). Decodifica com {@link Page#EMPTY} no lugar da página.
 */
final class TabletHeaderCodec {
    /** O formato de fio do cabeçalho; {@link TabletSnapshot#HEADER_CODEC} aponta para ele. */
    static final StreamCodec<RegistryFriendlyByteBuf, TabletSnapshot> CODEC =
            StreamCodec.of(TabletHeaderCodec::encode, TabletHeaderCodec::decode);

    private static final int TYPE_COUNT = ResourceType.values().length;

    private TabletHeaderCodec() {
    }

    private static void encode(RegistryFriendlyByteBuf buf, TabletSnapshot s) {
        buf.writeBoolean(s.operator());
        buf.writeResourceLocation(s.dimension());
        buf.writeOptional(s.activeNetwork(), (b, id) -> b.writeUUID(id));
        buf.writeVarInt(s.networks().size());
        for (NetworkView n : s.networks()) {
            buf.writeUUID(n.id());
            buf.writeUtf(n.name(), 64);
            buf.writeInt(n.color());
            buf.writeUtf(n.owner(), 64);
            buf.writeBoolean(n.owned());
            buf.writeBoolean(n.manageable());
            buf.writeBoolean(n.isPublic());
            buf.writeBoolean(n.paused());
            buf.writeVarInt(n.nodes());
            buf.writeVarInt(n.unloaded());
            n.access().write(buf);
            buf.writeVarInt(n.machines());
            buf.writeVarInt(n.types().size());
            for (TypeCount count : n.types()) {
                buf.writeEnum(count.type());
                buf.writeVarInt(count.givers());
                buf.writeVarInt(count.receivers());
            }
        }
        buf.writeVarInt(s.groups().size());
        for (GroupView g : s.groups()) {
            buf.writeUUID(g.id());
            buf.writeUtf(g.name(), 64);
            buf.writeUtf(g.owner(), 64);
            buf.writeBoolean(g.manageable());
            buf.writeBoolean(g.paused());
            buf.writeVarInt(g.networks().size());
            for (UUID id : g.networks()) {
                buf.writeUUID(id);
            }
            buf.writeOptional(g.icon(), (b, id) -> b.writeResourceLocation(id));
            buf.writeOptional(g.machine(), (b, id) -> b.writeResourceLocation(id));
        }
        buf.writeVarInt(s.machineFacets().size());
        for (Facet<ResourceLocation> facet : s.machineFacets()) {
            buf.writeResourceLocation(facet.id());
            buf.writeVarInt(facet.count());
        }
        buf.writeVarInt(s.networkFacets().size());
        for (Facet<UUID> facet : s.networkFacets()) {
            buf.writeUUID(facet.id());
            buf.writeVarInt(facet.count());
        }
        buf.writeVarInt(s.quickCounts().problem());
        buf.writeVarInt(s.quickCounts().noNetwork());
        buf.writeVarInt(s.quickCounts().unloaded());
        buf.writeVarInt(s.machines());
        buf.writeOptional(s.profile(), (b, p) -> {
            b.writeUtf(ProfileSource.clip(p.sourceName()), ProfileSource.MAX_NAME);
            b.writeResourceLocation(p.machine());
            NodeKey.STREAM_CODEC.encode(b, p.origin());
            b.writeVarInt(p.types().size());
            p.types().forEach(type -> b.writeEnum(type));
            b.writeVarInt(p.faces());
        });
        ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, s.notice());
        buf.writeVarInt(s.noticeId());
    }

    private static TabletSnapshot decode(RegistryFriendlyByteBuf buf) {
        boolean operator = buf.readBoolean();
        ResourceLocation dimension = buf.readResourceLocation();
        Optional<UUID> active = buf.readOptional(b -> b.readUUID());
        int networkCount = buf.readVarInt();
        List<NetworkView> networks = new ArrayList<>(networkCount);
        for (int i = 0; i < networkCount; i++) {
            UUID id = buf.readUUID();
            String name = buf.readUtf(64);
            int color = buf.readInt();
            String owner = buf.readUtf(64);
            boolean owned = buf.readBoolean();
            boolean manageable = buf.readBoolean();
            boolean isPublic = buf.readBoolean();
            boolean paused = buf.readBoolean();
            int nodes = buf.readVarInt();
            int unloaded = buf.readVarInt();
            AccessView access = AccessView.read(buf);
            int machines = buf.readVarInt();
            networks.add(new NetworkView(id, name, color, owner, owned, manageable, isPublic, paused, nodes,
                    unloaded, access, machines, readTypeCounts(buf)));
        }
        int groupCount = buf.readVarInt();
        List<GroupView> groups = new ArrayList<>(groupCount);
        for (int i = 0; i < groupCount; i++) {
            UUID id = buf.readUUID();
            String name = buf.readUtf(64);
            String owner = buf.readUtf(64);
            boolean manageable = buf.readBoolean();
            boolean paused = buf.readBoolean();
            int count = buf.readVarInt();
            List<UUID> members = new ArrayList<>(count);
            for (int j = 0; j < count; j++) {
                members.add(buf.readUUID());
            }
            Optional<ResourceLocation> icon = buf.readOptional(b -> b.readResourceLocation());
            Optional<ResourceLocation> machine = buf.readOptional(b -> b.readResourceLocation());
            groups.add(new GroupView(id, name, owner, manageable, paused, List.copyOf(members), icon, machine));
        }
        int machineCount = buf.readVarInt();
        List<Facet<ResourceLocation>> machineFacets = new ArrayList<>(machineCount);
        for (int i = 0; i < machineCount; i++) {
            machineFacets.add(new Facet<>(buf.readResourceLocation(), buf.readVarInt()));
        }
        int facetCount = buf.readVarInt();
        List<Facet<UUID>> networkFacets = new ArrayList<>(facetCount);
        for (int i = 0; i < facetCount; i++) {
            networkFacets.add(new Facet<>(buf.readUUID(), buf.readVarInt()));
        }
        QuickCounts quickCounts = new QuickCounts(buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
        int machines = buf.readVarInt();
        Optional<ProfileSummary> profile = buf.readOptional(b -> {
            String sourceName = b.readUtf(ProfileSource.MAX_NAME);
            ResourceLocation machine = b.readResourceLocation();
            NodeKey origin = NodeKey.STREAM_CODEC.decode(b);
            int typeCount = b.readVarInt();
            List<ResourceType> types = new ArrayList<>(typeCount);
            for (int i = 0; i < typeCount; i++) {
                types.add(b.readEnum(ResourceType.class));
            }
            return new ProfileSummary(sourceName, machine, origin, List.copyOf(types), b.readVarInt());
        });
        Component notice = ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf);
        int noticeId = buf.readVarInt();
        return new TabletSnapshot(operator, dimension, active, Page.EMPTY.query(),
                0, 0, List.copyOf(networks), List.copyOf(groups), List.copyOf(machineFacets),
                List.copyOf(networkFacets), quickCounts, machines, profile, List.of(), TabletRows.EMPTY,
                TabletMachineRows.EMPTY, notice, noticeId);
    }

    /** As contas por tipo de uma rede; recusa mais tipos do que o mod tem. */
    private static List<TypeCount> readTypeCounts(RegistryFriendlyByteBuf buf) {
        int count = buf.readVarInt();
        if (count > TYPE_COUNT) {
            throw new DecoderException("tipos " + count);
        }
        List<TypeCount> types = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            types.add(new TypeCount(buf.readEnum(ResourceType.class), buf.readVarInt(), buf.readVarInt()));
        }
        return types;
    }
}
