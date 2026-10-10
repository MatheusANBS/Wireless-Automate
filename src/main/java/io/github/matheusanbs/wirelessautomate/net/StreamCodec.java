package io.github.matheusanbs.wirelessautomate.net;

import com.mojang.datafixers.util.Function3;
import com.mojang.datafixers.util.Function4;
import com.mojang.datafixers.util.Function5;
import com.mojang.datafixers.util.Function6;
import com.mojang.datafixers.util.Function7;
import com.mojang.datafixers.util.Function8;
import io.netty.buffer.ByteBuf;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Codec de rede: lê e escreve um valor num buffer. Imita o {@code net.minecraft.network.codec.StreamCodec}
 * do 1.21, com as mesmas assinaturas genéricas, para que os payloads, snapshots e codecs do {@code main}
 * mudem só os imports no porte 1.20.1 (que não tem essa API). Só depende do jogo comum, nunca de cliente.
 *
 * <p>Tem {@link #of}, {@link #ofMember}, {@link #unit}, {@link #map}, {@link #apply}, {@link #mapStream},
 * {@link #cast} e {@code composite} de 1 a 8 campos. Os codecs prontos ficam em {@link ByteBufCodecs}
 * (primitivos e coleções) e em {@link GameCodecs} (itens, fluidos, ids, posições, textos).
 */
public interface StreamCodec<B, V> extends StreamDecoder<B, V>, StreamEncoder<B, V> {
    static <B, V> StreamCodec<B, V> of(StreamEncoder<B, V> encoder, StreamDecoder<B, V> decoder) {
        return new StreamCodec<>() {
            @Override
            public V decode(B buffer) {
                return decoder.decode(buffer);
            }

            @Override
            public void encode(B buffer, V value) {
                encoder.encode(buffer, value);
            }
        };
    }

    static <B, V> StreamCodec<B, V> ofMember(StreamMemberEncoder<B, V> encoder, StreamDecoder<B, V> decoder) {
        return new StreamCodec<>() {
            @Override
            public V decode(B buffer) {
                return decoder.decode(buffer);
            }

            @Override
            public void encode(B buffer, V value) {
                encoder.encode(value, buffer);
            }
        };
    }

    /** Não escreve nada e lê sempre {@code expectedValue}; escrever outro valor é erro. */
    static <B, V> StreamCodec<B, V> unit(V expectedValue) {
        return new StreamCodec<>() {
            @Override
            public V decode(B buffer) {
                return expectedValue;
            }

            @Override
            public void encode(B buffer, V value) {
                if (!value.equals(expectedValue)) {
                    throw new IllegalStateException("Can't encode '" + value + "', expected '" + expectedValue + "'");
                }
            }
        };
    }

    default <O> StreamCodec<B, O> apply(CodecOperation<B, V, O> operation) {
        return operation.apply(this);
    }

    default <O> StreamCodec<B, O> map(Function<? super V, ? extends O> factory, Function<? super O, ? extends V> getter) {
        StreamCodec<B, V> self = this;
        return new StreamCodec<>() {
            @Override
            public O decode(B buffer) {
                return factory.apply(self.decode(buffer));
            }

            @Override
            public void encode(B buffer, O value) {
                self.encode(buffer, getter.apply(value));
            }
        };
    }

    default <O extends ByteBuf> StreamCodec<O, V> mapStream(Function<O, ? extends B> bufferFactory) {
        StreamCodec<B, V> self = this;
        return new StreamCodec<>() {
            @Override
            public V decode(O buffer) {
                return self.decode(bufferFactory.apply(buffer));
            }

            @Override
            public void encode(O buffer, V value) {
                self.encode(bufferFactory.apply(buffer), value);
            }
        };
    }

    @SuppressWarnings("unchecked")
    default <S extends B> StreamCodec<S, V> cast() {
        return (StreamCodec<S, V>) this;
    }

    static <B, C, T1> StreamCodec<B, C> composite(
            StreamCodec<? super B, T1> codec1, Function<C, T1> getter1,
            Function<T1, C> factory) {
        return new StreamCodec<>() {
            @Override
            public C decode(B buffer) {
                return factory.apply(codec1.decode(buffer));
            }

            @Override
            public void encode(B buffer, C value) {
                codec1.encode(buffer, getter1.apply(value));
            }
        };
    }

    static <B, C, T1, T2> StreamCodec<B, C> composite(
            StreamCodec<? super B, T1> codec1, Function<C, T1> getter1,
            StreamCodec<? super B, T2> codec2, Function<C, T2> getter2,
            BiFunction<T1, T2, C> factory) {
        return new StreamCodec<>() {
            @Override
            public C decode(B buffer) {
                T1 v1 = codec1.decode(buffer);
                T2 v2 = codec2.decode(buffer);
                return factory.apply(v1, v2);
            }

            @Override
            public void encode(B buffer, C value) {
                codec1.encode(buffer, getter1.apply(value));
                codec2.encode(buffer, getter2.apply(value));
            }
        };
    }

    static <B, C, T1, T2, T3> StreamCodec<B, C> composite(
            StreamCodec<? super B, T1> codec1, Function<C, T1> getter1,
            StreamCodec<? super B, T2> codec2, Function<C, T2> getter2,
            StreamCodec<? super B, T3> codec3, Function<C, T3> getter3,
            Function3<T1, T2, T3, C> factory) {
        return new StreamCodec<>() {
            @Override
            public C decode(B buffer) {
                T1 v1 = codec1.decode(buffer);
                T2 v2 = codec2.decode(buffer);
                T3 v3 = codec3.decode(buffer);
                return factory.apply(v1, v2, v3);
            }

            @Override
            public void encode(B buffer, C value) {
                codec1.encode(buffer, getter1.apply(value));
                codec2.encode(buffer, getter2.apply(value));
                codec3.encode(buffer, getter3.apply(value));
            }
        };
    }

    static <B, C, T1, T2, T3, T4> StreamCodec<B, C> composite(
            StreamCodec<? super B, T1> codec1, Function<C, T1> getter1,
            StreamCodec<? super B, T2> codec2, Function<C, T2> getter2,
            StreamCodec<? super B, T3> codec3, Function<C, T3> getter3,
            StreamCodec<? super B, T4> codec4, Function<C, T4> getter4,
            Function4<T1, T2, T3, T4, C> factory) {
        return new StreamCodec<>() {
            @Override
            public C decode(B buffer) {
                T1 v1 = codec1.decode(buffer);
                T2 v2 = codec2.decode(buffer);
                T3 v3 = codec3.decode(buffer);
                T4 v4 = codec4.decode(buffer);
                return factory.apply(v1, v2, v3, v4);
            }

            @Override
            public void encode(B buffer, C value) {
                codec1.encode(buffer, getter1.apply(value));
                codec2.encode(buffer, getter2.apply(value));
                codec3.encode(buffer, getter3.apply(value));
                codec4.encode(buffer, getter4.apply(value));
            }
        };
    }

    static <B, C, T1, T2, T3, T4, T5> StreamCodec<B, C> composite(
            StreamCodec<? super B, T1> codec1, Function<C, T1> getter1,
            StreamCodec<? super B, T2> codec2, Function<C, T2> getter2,
            StreamCodec<? super B, T3> codec3, Function<C, T3> getter3,
            StreamCodec<? super B, T4> codec4, Function<C, T4> getter4,
            StreamCodec<? super B, T5> codec5, Function<C, T5> getter5,
            Function5<T1, T2, T3, T4, T5, C> factory) {
        return new StreamCodec<>() {
            @Override
            public C decode(B buffer) {
                T1 v1 = codec1.decode(buffer);
                T2 v2 = codec2.decode(buffer);
                T3 v3 = codec3.decode(buffer);
                T4 v4 = codec4.decode(buffer);
                T5 v5 = codec5.decode(buffer);
                return factory.apply(v1, v2, v3, v4, v5);
            }

            @Override
            public void encode(B buffer, C value) {
                codec1.encode(buffer, getter1.apply(value));
                codec2.encode(buffer, getter2.apply(value));
                codec3.encode(buffer, getter3.apply(value));
                codec4.encode(buffer, getter4.apply(value));
                codec5.encode(buffer, getter5.apply(value));
            }
        };
    }

    static <B, C, T1, T2, T3, T4, T5, T6> StreamCodec<B, C> composite(
            StreamCodec<? super B, T1> codec1, Function<C, T1> getter1,
            StreamCodec<? super B, T2> codec2, Function<C, T2> getter2,
            StreamCodec<? super B, T3> codec3, Function<C, T3> getter3,
            StreamCodec<? super B, T4> codec4, Function<C, T4> getter4,
            StreamCodec<? super B, T5> codec5, Function<C, T5> getter5,
            StreamCodec<? super B, T6> codec6, Function<C, T6> getter6,
            Function6<T1, T2, T3, T4, T5, T6, C> factory) {
        return new StreamCodec<>() {
            @Override
            public C decode(B buffer) {
                T1 v1 = codec1.decode(buffer);
                T2 v2 = codec2.decode(buffer);
                T3 v3 = codec3.decode(buffer);
                T4 v4 = codec4.decode(buffer);
                T5 v5 = codec5.decode(buffer);
                T6 v6 = codec6.decode(buffer);
                return factory.apply(v1, v2, v3, v4, v5, v6);
            }

            @Override
            public void encode(B buffer, C value) {
                codec1.encode(buffer, getter1.apply(value));
                codec2.encode(buffer, getter2.apply(value));
                codec3.encode(buffer, getter3.apply(value));
                codec4.encode(buffer, getter4.apply(value));
                codec5.encode(buffer, getter5.apply(value));
                codec6.encode(buffer, getter6.apply(value));
            }
        };
    }

    static <B, C, T1, T2, T3, T4, T5, T6, T7> StreamCodec<B, C> composite(
            StreamCodec<? super B, T1> codec1, Function<C, T1> getter1,
            StreamCodec<? super B, T2> codec2, Function<C, T2> getter2,
            StreamCodec<? super B, T3> codec3, Function<C, T3> getter3,
            StreamCodec<? super B, T4> codec4, Function<C, T4> getter4,
            StreamCodec<? super B, T5> codec5, Function<C, T5> getter5,
            StreamCodec<? super B, T6> codec6, Function<C, T6> getter6,
            StreamCodec<? super B, T7> codec7, Function<C, T7> getter7,
            Function7<T1, T2, T3, T4, T5, T6, T7, C> factory) {
        return new StreamCodec<>() {
            @Override
            public C decode(B buffer) {
                T1 v1 = codec1.decode(buffer);
                T2 v2 = codec2.decode(buffer);
                T3 v3 = codec3.decode(buffer);
                T4 v4 = codec4.decode(buffer);
                T5 v5 = codec5.decode(buffer);
                T6 v6 = codec6.decode(buffer);
                T7 v7 = codec7.decode(buffer);
                return factory.apply(v1, v2, v3, v4, v5, v6, v7);
            }

            @Override
            public void encode(B buffer, C value) {
                codec1.encode(buffer, getter1.apply(value));
                codec2.encode(buffer, getter2.apply(value));
                codec3.encode(buffer, getter3.apply(value));
                codec4.encode(buffer, getter4.apply(value));
                codec5.encode(buffer, getter5.apply(value));
                codec6.encode(buffer, getter6.apply(value));
                codec7.encode(buffer, getter7.apply(value));
            }
        };
    }

    static <B, C, T1, T2, T3, T4, T5, T6, T7, T8> StreamCodec<B, C> composite(
            StreamCodec<? super B, T1> codec1, Function<C, T1> getter1,
            StreamCodec<? super B, T2> codec2, Function<C, T2> getter2,
            StreamCodec<? super B, T3> codec3, Function<C, T3> getter3,
            StreamCodec<? super B, T4> codec4, Function<C, T4> getter4,
            StreamCodec<? super B, T5> codec5, Function<C, T5> getter5,
            StreamCodec<? super B, T6> codec6, Function<C, T6> getter6,
            StreamCodec<? super B, T7> codec7, Function<C, T7> getter7,
            StreamCodec<? super B, T8> codec8, Function<C, T8> getter8,
            Function8<T1, T2, T3, T4, T5, T6, T7, T8, C> factory) {
        return new StreamCodec<>() {
            @Override
            public C decode(B buffer) {
                T1 v1 = codec1.decode(buffer);
                T2 v2 = codec2.decode(buffer);
                T3 v3 = codec3.decode(buffer);
                T4 v4 = codec4.decode(buffer);
                T5 v5 = codec5.decode(buffer);
                T6 v6 = codec6.decode(buffer);
                T7 v7 = codec7.decode(buffer);
                T8 v8 = codec8.decode(buffer);
                return factory.apply(v1, v2, v3, v4, v5, v6, v7, v8);
            }

            @Override
            public void encode(B buffer, C value) {
                codec1.encode(buffer, getter1.apply(value));
                codec2.encode(buffer, getter2.apply(value));
                codec3.encode(buffer, getter3.apply(value));
                codec4.encode(buffer, getter4.apply(value));
                codec5.encode(buffer, getter5.apply(value));
                codec6.encode(buffer, getter6.apply(value));
                codec7.encode(buffer, getter7.apply(value));
                codec8.encode(buffer, getter8.apply(value));
            }
        };
    }

    /** Transforma um codec em outro (como {@link ByteBufCodecs#list(int)}); o argumento de {@link #apply}. */
    @FunctionalInterface
    interface CodecOperation<B, S, T> {
        StreamCodec<B, T> apply(StreamCodec<B, S> codec);
    }
}
