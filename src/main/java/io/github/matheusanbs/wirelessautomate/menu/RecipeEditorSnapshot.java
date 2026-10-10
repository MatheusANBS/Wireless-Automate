package io.github.matheusanbs.wirelessautomate.menu;

import io.github.matheusanbs.wirelessautomate.recipe.edit.RecipeDraft;
import io.github.matheusanbs.wirelessautomate.recipe.edit.RecipeEditor;
import io.github.matheusanbs.wirelessautomate.recipe.edit.RecipeSlot;
import io.netty.handler.codec.DecoderException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

/** O estado do editor de receitas que o servidor manda à tela aberta: as receitas e as pendências. */
public record RecipeEditorSnapshot(List<Row> rows, int pending) {
    /** Limite de receitas num pacote (o mod tem poucas dezenas). */
    private static final int MAX_ROWS = 4096;
    private static final int MAX_ID = 256;

    public record Row(ResourceLocation id, ResourceLocation resultItem, RecipeDraft defaults, RecipeDraft current,
            RecipeEditor.State state, boolean divergent) {
    }

    public RecipeEditorSnapshot {
        rows = List.copyOf(rows);
    }

    public static RecipeEditorSnapshot of(MinecraftServer server) {
        List<Row> rows = RecipeEditor.entries(server).stream()
                .map(e -> new Row(e.id(), e.resultItem(), e.defaults(), e.current(), e.state(), e.divergent()))
                .toList();
        return new RecipeEditorSnapshot(rows, RecipeEditor.pending());
    }

    // ------------------------------------------------------------------ codecs

    /** Rascunho: forma, 9 slots, quantidade (presa entre 1 e 64 ao ler) e se está desativado. */
    public static final StreamCodec<RegistryFriendlyByteBuf, RecipeDraft> DRAFT_CODEC = StreamCodec.of(
            RecipeEditorSnapshot::writeDraft, RecipeEditorSnapshot::readDraft);

    public static final StreamCodec<RegistryFriendlyByteBuf, RecipeEditorSnapshot> STREAM_CODEC = StreamCodec.of(
            RecipeEditorSnapshot::write, RecipeEditorSnapshot::read);

    private static void writeSlot(RegistryFriendlyByteBuf buf, RecipeSlot slot) {
        buf.writeByte(slot.kind().ordinal());
        buf.writeUtf(slot.id(), MAX_ID);
    }

    private static RecipeSlot readSlot(RegistryFriendlyByteBuf buf) {
        RecipeSlot.Kind kind = ordinal(RecipeSlot.Kind.values(), buf.readUnsignedByte());
        String id = buf.readUtf(MAX_ID);
        return new RecipeSlot(kind, kind == RecipeSlot.Kind.EMPTY ? "" : id);
    }

    private static void writeDraft(RegistryFriendlyByteBuf buf, RecipeDraft draft) {
        buf.writeByte(draft.shape().ordinal());
        for (RecipeSlot slot : draft.slots()) {
            writeSlot(buf, slot);
        }
        buf.writeVarInt(draft.count());
        buf.writeBoolean(draft.disabled());
    }

    private static RecipeDraft readDraft(RegistryFriendlyByteBuf buf) {
        RecipeDraft.Shape shape = ordinal(RecipeDraft.Shape.values(), buf.readUnsignedByte());
        List<RecipeSlot> slots = new ArrayList<>(RecipeDraft.SLOTS);
        for (int i = 0; i < RecipeDraft.SLOTS; i++) {
            slots.add(readSlot(buf));
        }
        int count = Math.max(RecipeDraft.MIN_COUNT, Math.min(RecipeDraft.MAX_COUNT, buf.readVarInt()));
        boolean disabled = buf.readBoolean();
        try {
            return new RecipeDraft(shape, slots, count, disabled);
        } catch (IllegalArgumentException e) {
            throw new DecoderException("rascunho inválido", e);
        }
    }

    private static <E extends Enum<E>> E ordinal(E[] values, int index) {
        if (index < 0 || index >= values.length) {
            throw new DecoderException("valor de enum fora do intervalo: " + index);
        }
        return values[index];
    }

    private static void write(RegistryFriendlyByteBuf buf, RecipeEditorSnapshot snapshot) {
        buf.writeVarInt(snapshot.pending());
        buf.writeVarInt(snapshot.rows().size());
        for (Row row : snapshot.rows()) {
            buf.writeResourceLocation(row.id());
            buf.writeResourceLocation(row.resultItem());
            writeDraft(buf, row.defaults());
            writeDraft(buf, row.current());
            buf.writeByte(row.state().ordinal());
            buf.writeBoolean(row.divergent());
        }
    }

    private static RecipeEditorSnapshot read(RegistryFriendlyByteBuf buf) {
        int pending = Math.max(0, buf.readVarInt());
        int size = buf.readVarInt();
        if (size < 0 || size > MAX_ROWS) {
            throw new DecoderException("receitas demais no pacote: " + size);
        }
        List<Row> rows = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            ResourceLocation id = buf.readResourceLocation();
            ResourceLocation result = buf.readResourceLocation();
            RecipeDraft defaults = readDraft(buf);
            RecipeDraft current = readDraft(buf);
            RecipeEditor.State state = ordinal(RecipeEditor.State.values(), buf.readUnsignedByte());
            rows.add(new Row(id, result, defaults, current, state, buf.readBoolean()));
        }
        return new RecipeEditorSnapshot(rows, pending);
    }
}
