package io.github.matheusanbs.wirelessautomate.net;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

/**
 * Os codecs de rede dos tipos do jogo, que no 1.21 são constantes nas próprias classes e no 1.20.1 não existem.
 * Mapeamento para as próximas tarefas do porte (troque a constante, mantenha o resto da linha):
 *
 * <table>
 *   <tr><th>1.21 ({@code main})</th><th>1.20.1</th></tr>
 *   <tr><td>{@code ItemStack.STREAM_CODEC}</td><td>{@link #ITEM_STACK}</td></tr>
 *   <tr><td>{@code ItemStack.OPTIONAL_STREAM_CODEC}</td><td>{@link #OPTIONAL_ITEM_STACK}</td></tr>
 *   <tr><td>{@code FluidStack.STREAM_CODEC}</td><td>{@link #FLUID_STACK}</td></tr>
 *   <tr><td>{@code FluidStack.OPTIONAL_STREAM_CODEC}</td><td>{@link #OPTIONAL_FLUID_STACK}</td></tr>
 *   <tr><td>{@code ResourceLocation.STREAM_CODEC}</td><td>{@link #RESOURCE_LOCATION}</td></tr>
 *   <tr><td>{@code BlockPos.STREAM_CODEC}</td><td>{@link #BLOCK_POS}</td></tr>
 *   <tr><td>{@code Direction.STREAM_CODEC}</td><td>{@link #DIRECTION}</td></tr>
 *   <tr><td>{@code UUIDUtil.STREAM_CODEC}</td><td>{@link #UUID}</td></tr>
 *   <tr><td>{@code ComponentSerialization.STREAM_CODEC}</td><td>{@link #COMPONENT}</td></tr>
 * </table>
 *
 * <p>Os tipos de buffer são os do 1.21 ({@link ByteBuf} ou {@link RegistryFriendlyByteBuf}), para o
 * {@code StreamCodec.composite} inferir igual.
 *
 * <p><b>Pilhas grandes:</b> o {@code FriendlyByteBuf.writeItem} do 1.20.1 grava a contagem num byte (até
 * 127). {@link #ITEM_STACK} grava como o 1.21: a contagem em VarInt, o item pelo id do registro e o NBT de
 * compartilhamento ({@code getShareTag}/{@code readShareTag}, o mesmo do {@code writeItem}); serve para as
 * pilhas do Baú com contagem acima de 127.
 */
public final class GameCodecs {
    /** Pilha não vazia (vazia é erro, como no 1.21). */
    public static final StreamCodec<RegistryFriendlyByteBuf, ItemStack> ITEM_STACK = new StreamCodec<>() {
        @Override
        public ItemStack decode(RegistryFriendlyByteBuf buffer) {
            ItemStack stack = readItem(buffer);
            if (stack.isEmpty()) {
                throw new DecoderException("Empty ItemStack not allowed");
            }
            return stack;
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, ItemStack value) {
            if (value.isEmpty()) {
                throw new EncoderException("Empty ItemStack not allowed");
            }
            writeItem(buffer, value);
        }
    };

    /** Pilha que pode ser vazia (contagem 0 no fio). */
    public static final StreamCodec<RegistryFriendlyByteBuf, ItemStack> OPTIONAL_ITEM_STACK =
            StreamCodec.of(GameCodecs::writeItem, GameCodecs::readItem);

    /** Fluido não vazio (vazio é erro, como no NeoForge 1.21), pelo {@link FluidStack#writeToPacket}. */
    public static final StreamCodec<RegistryFriendlyByteBuf, FluidStack> FLUID_STACK = new StreamCodec<>() {
        @Override
        public FluidStack decode(RegistryFriendlyByteBuf buffer) {
            FluidStack stack = FluidStack.readFromPacket(buffer);
            if (stack.isEmpty()) {
                throw new DecoderException("Empty FluidStack not allowed");
            }
            return stack;
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, FluidStack value) {
            if (value.isEmpty()) {
                throw new EncoderException("Empty FluidStack not allowed");
            }
            value.writeToPacket(buffer);
        }
    };

    /** Fluido que pode ser vazio. */
    public static final StreamCodec<RegistryFriendlyByteBuf, FluidStack> OPTIONAL_FLUID_STACK =
            StreamCodec.of((buf, value) -> value.writeToPacket(buf), FluidStack::readFromPacket);

    public static final StreamCodec<ByteBuf, ResourceLocation> RESOURCE_LOCATION = StreamCodec.of(
            (buf, value) -> ByteBufCodecs.Bufs.friendly(buf).writeResourceLocation(value),
            buf -> ByteBufCodecs.Bufs.friendly(buf).readResourceLocation());

    public static final StreamCodec<ByteBuf, BlockPos> BLOCK_POS = StreamCodec.of(
            (buf, value) -> buf.writeLong(value.asLong()), buf -> BlockPos.of(buf.readLong()));

    public static final StreamCodec<ByteBuf, Direction> DIRECTION = ByteBufCodecs.idMapper(Direction::from3DDataValue,
            Direction::get3DDataValue);

    public static final StreamCodec<ByteBuf, UUID> UUID = StreamCodec.of(
            (buf, value) -> {
                buf.writeLong(value.getMostSignificantBits());
                buf.writeLong(value.getLeastSignificantBits());
            },
            buf -> new UUID(buf.readLong(), buf.readLong()));

    /** Texto pelo JSON ({@link FriendlyByteBuf#writeComponent}). */
    public static final StreamCodec<RegistryFriendlyByteBuf, Component> COMPONENT =
            StreamCodec.of(FriendlyByteBuf::writeComponent, FriendlyByteBuf::readComponent);

    private GameCodecs() {
    }

    private static void writeItem(FriendlyByteBuf buf, ItemStack stack) {
        if (stack.isEmpty()) {
            ByteBufCodecs.Bufs.writeVarInt(buf, 0);
            return;
        }
        ByteBufCodecs.Bufs.writeVarInt(buf, stack.getCount());
        buf.writeId(BuiltInRegistries.ITEM, stack.getItem());
        buf.writeNbt(stack.getShareTag());
    }

    private static ItemStack readItem(FriendlyByteBuf buf) {
        int count = ByteBufCodecs.Bufs.readVarInt(buf);
        if (count <= 0) {
            return ItemStack.EMPTY;
        }
        Item item = buf.readById(BuiltInRegistries.ITEM);
        ItemStack stack = new ItemStack(item, count);
        stack.readShareTag(buf.readNbt());
        return stack;
    }
}
