package io.github.matheusanbs.wirelessautomate.packet;

import com.mojang.serialization.Codec;
import io.netty.handler.codec.DecoderException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;

/** Um valor do editor da Tela de Sinal como NBT em bytes, para os payloads com tamanho conferido antes de decodificar. */
final class TvNbtBytes {
    private TvNbtBytes() {
    }

    static <T> byte[] encode(Codec<T> codec, T value) {
        Tag tag = codec.encodeStart(NbtOps.INSTANCE, value).getOrThrow();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (DataOutputStream data = new DataOutputStream(out)) {
            NbtIo.writeAnyTag(tag, data);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return out.toByteArray();
    }

    static <T> T decode(Codec<T> codec, byte[] bytes, int maxBytes) {
        try (DataInputStream data = new DataInputStream(new ByteArrayInputStream(bytes))) {
            Tag tag = NbtIo.readAnyTag(data, NbtAccounter.create(maxBytes * 4L));
            if (!(tag instanceof CompoundTag)) {
                throw new DecoderException("valor da Tela de Sinal não é um composto");
            }
            return codec.parse(NbtOps.INSTANCE, tag).getOrThrow(DecoderException::new);
        } catch (IOException e) {
            throw new DecoderException(e);
        }
    }
}
