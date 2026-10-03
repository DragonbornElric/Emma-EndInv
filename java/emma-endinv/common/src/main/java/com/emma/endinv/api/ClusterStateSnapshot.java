package com.emma.endinv.api;

import com.emma.endinv.ServerLevelEndInv;
import com.emma.endinv.data.EndlessInventoryData;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;

/** A detached diagnostic snapshot of the real mod codec. Not a per-player inventory copy. */
public final class ClusterStateSnapshot {
    private ClusterStateSnapshot() {}
    public static CompoundTag capture(MinecraftServer server) {
        if (!server.isSameThread()) throw new IllegalStateException("Capture on server thread after world workers finish");
        if (ServerLevelEndInv.levelEndInvData == null) throw new IllegalStateException("Real EndInv data not initialized");
        var ops = server.registryAccess().createSerializationContext(NbtOps.INSTANCE);
        Tag encoded = EndlessInventoryData.DATA_TYPE.codec().encodeStart(ops, ServerLevelEndInv.levelEndInvData).getOrThrow();
        // Decode and re-encode detached objects to exercise the mod's actual persisted schema.
        var detached = EndlessInventoryData.DATA_TYPE.codec().parse(ops, encoded).getOrThrow();
        Tag roundTrip = EndlessInventoryData.DATA_TYPE.codec().encodeStart(ops, detached).getOrThrow();
        if (!encoded.equals(roundTrip)) throw new IllegalStateException("EndInv snapshot codec did not round-trip");
        CompoundTag envelope = new CompoundTag();
        envelope.putInt("mtmc_schema", 1);
        envelope.put("data", roundTrip);
        NbtUtils.addCurrentDataVersion(envelope);
        return envelope;
    }
}
