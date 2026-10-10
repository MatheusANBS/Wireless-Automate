package io.github.matheusanbs.wirelessautomate.net;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.IntFunction;
import java.util.function.ToIntFunction;
import net.minecraft.Util;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;

/**
 * Codecs de rede prontos: primitivos, texto, coleções e a ponte com os {@link Codec} do DFU. Imita o
 * {@code net.minecraft.network.codec.ByteBufCodecs} do 1.21 (mesmos nomes e assinaturas genéricas), para os
 * payloads do {@code main} mudarem só os imports no porte 1.20.1. Só depende do jogo comum, nunca de cliente.
 *
 * <p>O formato no fio é o do 1.21 onde dá (VarInt, VarLong, UTF-8 com o tamanho em VarInt, coleções com o
 * tamanho em VarInt e teto). {@link #fromCodec} grava o valor como NBT ({@code {"v": tag}}, até 2 MiB).
 */
public interface ByteBufCodecs {
    int MAX_INITIAL_COLLECTION_SIZE = 65536;

    StreamCodec<ByteBuf, Boolean> BOOL = StreamCodec.of(ByteBuf::writeBoolean, ByteBuf::readBoolean);
    StreamCodec<ByteBuf, Byte> BYTE = StreamCodec.of((buf, value) -> buf.writeByte(value), ByteBuf::readByte);
    StreamCodec<ByteBuf, Short> SHORT = StreamCodec.of((buf, value) -> buf.writeShort(value), ByteBuf::readShort);
    StreamCodec<ByteBuf, Integer> INT = StreamCodec.of(ByteBuf::writeInt, ByteBuf::readInt);
    StreamCodec<ByteBuf, Integer> VAR_INT = StreamCodec.of(Bufs::writeVarInt, Bufs::readVarInt);
    StreamCodec<ByteBuf, Long> LONG = StreamCodec.of(ByteBuf::writeLong, ByteBuf::readLong);
    StreamCodec<ByteBuf, Long> VAR_LONG = StreamCodec.of(Bufs::writeVarLong, Bufs::readVarLong);
    StreamCodec<ByteBuf, Float> FLOAT = StreamCodec.of(ByteBuf::writeFloat, ByteBuf::readFloat);
    StreamCodec<ByteBuf, Double> DOUBLE = StreamCodec.of(ByteBuf::writeDouble, ByteBuf::readDouble);
    StreamCodec<ByteBuf, byte[]> BYTE_ARRAY = StreamCodec.of(
            (buf, value) -> Bufs.friendly(buf).writeByteArray(value), buf -> Bufs.friendly(buf).readByteArray());
    StreamCodec<ByteBuf, String> STRING_UTF8 = stringUtf8(32767);
    /** Um {@link CompoundTag} (nulo não), até 2 MiB na leitura. */
    StreamCodec<ByteBuf, CompoundTag> COMPOUND_TAG = StreamCodec.of(
            (buf, value) -> Bufs.friendly(buf).writeNbt(value),
            buf -> {
                CompoundTag tag = Bufs.friendly(buf).readNbt();
                if (tag == null) {
                    throw new DecoderException("Expected non-null compound tag");
                }
                return tag;
            });

    /** Texto UTF-8 de até {@code maxLength} caracteres (como o {@code FriendlyByteBuf.writeUtf}). */
    static StreamCodec<ByteBuf, String> stringUtf8(int maxLength) {
        return StreamCodec.of((buf, value) -> Bufs.friendly(buf).writeUtf(value, maxLength),
                buf -> Bufs.friendly(buf).readUtf(maxLength));
    }

    /** Um booleano de presença e o valor. */
    static <B extends ByteBuf, V> StreamCodec<B, Optional<V>> optional(StreamCodec<B, V> codec) {
        return new StreamCodec<>() {
            @Override
            public Optional<V> decode(B buffer) {
                return buffer.readBoolean() ? Optional.of(codec.decode(buffer)) : Optional.empty();
            }

            @Override
            public void encode(B buffer, Optional<V> value) {
                if (value.isPresent()) {
                    buffer.writeBoolean(true);
                    codec.encode(buffer, value.get());
                } else {
                    buffer.writeBoolean(false);
                }
            }
        };
    }

    /** O tamanho em VarInt (no máximo {@code maxSize}, senão erro) e os elementos. */
    static <B extends ByteBuf, V, C extends Collection<V>> StreamCodec<B, C> collection(
            IntFunction<C> factory, StreamCodec<? super B, V> codec, int maxSize) {
        return new StreamCodec<>() {
            @Override
            public C decode(B buffer) {
                int size = Bufs.readCount(buffer, maxSize);
                C result = factory.apply(Math.min(size, MAX_INITIAL_COLLECTION_SIZE));
                for (int i = 0; i < size; i++) {
                    result.add(codec.decode(buffer));
                }
                return result;
            }

            @Override
            public void encode(B buffer, C value) {
                Bufs.writeCount(buffer, value.size(), maxSize);
                for (V element : value) {
                    codec.encode(buffer, element);
                }
            }
        };
    }

    static <B extends ByteBuf, V, C extends Collection<V>> StreamCodec<B, C> collection(
            IntFunction<C> factory, StreamCodec<? super B, V> codec) {
        return collection(factory, codec, Integer.MAX_VALUE);
    }

    static <B extends ByteBuf, V, C extends Collection<V>> StreamCodec.CodecOperation<B, V, C> collection(
            IntFunction<C> factory) {
        return codec -> collection(factory, codec);
    }

    static <B extends ByteBuf, V> StreamCodec.CodecOperation<B, V, List<V>> list() {
        return codec -> collection(ArrayList::new, codec);
    }

    static <B extends ByteBuf, V> StreamCodec.CodecOperation<B, V, List<V>> list(int maxSize) {
        return codec -> collection(ArrayList::new, codec, maxSize);
    }

    /** O tamanho em VarInt (no máximo {@code maxSize}) e os pares chave, valor. */
    static <B extends ByteBuf, K, V, M extends Map<K, V>> StreamCodec<B, M> map(IntFunction<? extends M> factory,
            StreamCodec<? super B, K> keyCodec, StreamCodec<? super B, V> valueCodec, int maxSize) {
        return new StreamCodec<>() {
            @Override
            public M decode(B buffer) {
                int size = Bufs.readCount(buffer, maxSize);
                M result = factory.apply(Math.min(size, MAX_INITIAL_COLLECTION_SIZE));
                for (int i = 0; i < size; i++) {
                    K key = keyCodec.decode(buffer);
                    V value = valueCodec.decode(buffer);
                    result.put(key, value);
                }
                return result;
            }

            @Override
            public void encode(B buffer, M value) {
                Bufs.writeCount(buffer, value.size(), maxSize);
                value.forEach((k, v) -> {
                    keyCodec.encode(buffer, k);
                    valueCodec.encode(buffer, v);
                });
            }
        };
    }

    static <B extends ByteBuf, K, V, M extends Map<K, V>> StreamCodec<B, M> map(IntFunction<? extends M> factory,
            StreamCodec<? super B, K> keyCodec, StreamCodec<? super B, V> valueCodec) {
        return map(factory, keyCodec, valueCodec, Integer.MAX_VALUE);
    }

    /** Um id em VarInt, traduzido nos dois sentidos. */
    static <T> StreamCodec<ByteBuf, T> idMapper(IntFunction<T> idLookup, ToIntFunction<T> idGetter) {
        return new StreamCodec<>() {
            @Override
            public T decode(ByteBuf buffer) {
                return idLookup.apply(Bufs.readVarInt(buffer));
            }

            @Override
            public void encode(ByteBuf buffer, T value) {
                Bufs.writeVarInt(buffer, idGetter.applyAsInt(value));
            }
        };
    }

    /**
     * O valor pelo {@link Codec} em NBT ({@link NbtOps}), dentro de um {@code {"v": tag}} (o
     * {@code FriendlyByteBuf.writeNbt} só aceita compostos), até 2 MiB na leitura.
     */
    static <T> StreamCodec<ByteBuf, T> fromCodec(Codec<T> codec) {
        return new StreamCodec<>() {
            @Override
            public T decode(ByteBuf buffer) {
                CompoundTag wrapper = Bufs.friendly(buffer).readNbt();
                Tag tag = wrapper == null ? null : wrapper.get("v");
                if (tag == null) {
                    throw new DecoderException("Missing value tag");
                }
                return Util.getOrThrow(codec.parse(NbtOps.INSTANCE, tag),
                        error -> new DecoderException("Failed to decode: " + error + " " + tag));
            }

            @Override
            public void encode(ByteBuf buffer, T value) {
                Tag tag = Util.getOrThrow(codec.encodeStart(NbtOps.INSTANCE, value),
                        error -> new EncoderException("Failed to encode: " + error + " " + value));
                CompoundTag wrapper = new CompoundTag();
                wrapper.put("v", tag);
                Bufs.friendly(buffer).writeNbt(wrapper);
            }
        };
    }

    /**
     * Igual a {@link #fromCodec}: no 1.20.1 não há registros dinâmicos no buffer, então os codecs que no 1.21
     * pediam {@code RegistryOps} usam {@link NbtOps} puro (os itens e encantamentos do 1.20.1 não precisam).
     */
    static <T> StreamCodec<RegistryFriendlyByteBuf, T> fromCodecWithRegistries(Codec<T> codec) {
        return fromCodec(codec).cast();
    }

    /** Ajudas de buffer da camada (VarInt, contagens e a visão {@link FriendlyByteBuf} de um {@link ByteBuf}). */
    final class Bufs {
        private Bufs() {
        }

        /** O próprio buffer, se já for um {@link FriendlyByteBuf}; senão uma visão por cima (sem cópia). */
        static FriendlyByteBuf friendly(ByteBuf buf) {
            return buf instanceof FriendlyByteBuf friendly ? friendly : new FriendlyByteBuf(buf);
        }

        static void writeVarInt(ByteBuf buf, int value) {
            while ((value & -128) != 0) {
                buf.writeByte(value & 127 | 128);
                value >>>= 7;
            }
            buf.writeByte(value);
        }

        static int readVarInt(ByteBuf buf) {
            int result = 0;
            int shift = 0;
            byte b;
            do {
                b = buf.readByte();
                result |= (b & 127) << shift;
                shift += 7;
                if (shift > 35) {
                    throw new DecoderException("VarInt too big");
                }
            } while ((b & 128) == 128);
            return result;
        }

        static void writeVarLong(ByteBuf buf, long value) {
            while ((value & -128L) != 0L) {
                buf.writeByte((int) (value & 127L) | 128);
                value >>>= 7;
            }
            buf.writeByte((int) value);
        }

        static long readVarLong(ByteBuf buf) {
            long result = 0L;
            int shift = 0;
            byte b;
            do {
                b = buf.readByte();
                result |= (long) (b & 127) << shift;
                shift += 7;
                if (shift > 70) {
                    throw new DecoderException("VarLong too big");
                }
            } while ((b & 128) == 128);
            return result;
        }

        static int readCount(ByteBuf buf, int maxSize) {
            int count = readVarInt(buf);
            if (count < 0 || count > maxSize) {
                throw new DecoderException(count + " elements exceeded max size of: " + maxSize);
            }
            return count;
        }

        static void writeCount(ByteBuf buf, int count, int maxSize) {
            if (count > maxSize) {
                throw new EncoderException(count + " elements exceeded max size of: " + maxSize);
            }
            writeVarInt(buf, count);
        }
    }
}
