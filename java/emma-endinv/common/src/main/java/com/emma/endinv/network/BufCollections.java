package com.emma.endinv.network;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamDecoder;
import net.minecraft.network.codec.StreamEncoder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;

/**
 * Collection helpers that {@link FriendlyByteBuf} dropped in 26.3 (readList, writeCollection,
 * readMap, writeMap, readIntIdList, writeIntIdList). Same wire format: a VarInt size, then the elements.
 */
public final class BufCollections {

    private BufCollections() {}

    public static <T> void writeCollection(FriendlyByteBuf buf, Collection<T> collection,
                                           StreamEncoder<? super FriendlyByteBuf, T> encoder) {
        buf.writeVarInt(collection.size());
        for (T element : collection) {
            encoder.encode(buf, element);
        }
    }

    public static <T> List<T> readList(FriendlyByteBuf buf, StreamDecoder<? super FriendlyByteBuf, T> decoder) {
        int size = buf.readVarInt();
        List<T> list = new ArrayList<>(Math.min(size, 65536));
        for (int i = 0; i < size; i++) {
            list.add(decoder.decode(buf));
        }
        return list;
    }

    public static <K, V> void writeMap(FriendlyByteBuf buf, Map<K, V> map,
                                       StreamEncoder<? super FriendlyByteBuf, K> keyEncoder,
                                       StreamEncoder<? super FriendlyByteBuf, V> valueEncoder) {
        buf.writeVarInt(map.size());
        map.forEach((k, v) -> {
            keyEncoder.encode(buf, k);
            valueEncoder.encode(buf, v);
        });
    }

    public static <K, V, M extends Map<K, V>> M readMap(FriendlyByteBuf buf, IntFunction<M> factory,
                                                        StreamDecoder<? super FriendlyByteBuf, K> keyDecoder,
                                                        StreamDecoder<? super FriendlyByteBuf, V> valueDecoder) {
        int size = buf.readVarInt();
        M map = factory.apply(Math.min(size, 65536));
        for (int i = 0; i < size; i++) {
            K k = keyDecoder.decode(buf);
            V v = valueDecoder.decode(buf);
            map.put(k, v);
        }
        return map;
    }

    public static void writeIntIdList(FriendlyByteBuf buf, IntList ids) {
        buf.writeVarInt(ids.size());
        ids.forEach(buf::writeVarInt);
    }

    public static IntList readIntIdList(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        IntList ids = new IntArrayList(Math.min(size, 65536));
        for (int i = 0; i < size; i++) {
            ids.add(buf.readVarInt());
        }
        return ids;
    }
}
