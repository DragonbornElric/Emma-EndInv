package com.emma.endinv.network;

import com.emma.endinv.menu.Station;
import com.emma.endinv.network.payloads.ModPacketPayload;
import com.emma.endinv.network.payloads.PageData;
import com.emma.endinv.network.payloads.SyncedConfig;
import com.emma.endinv.network.payloads.toClient.EndInvConfig;
import com.emma.endinv.network.payloads.toClient.EndInvContent;
import com.emma.endinv.network.payloads.toClient.EndInvMetadata;
import com.emma.endinv.network.payloads.toClient.MenuAttachabilityPayload;
import com.emma.endinv.network.payloads.toClient.SetItemDisplayContentPayload;
import com.emma.endinv.network.payloads.toServer.ItemPageContext;
import com.emma.endinv.network.payloads.toServer.OpenEndInvPayload;
import com.emma.endinv.network.payloads.toServer.QuickMoveToPagePayload;
import com.emma.endinv.network.payloads.toServer.SetActiveStationPayload;
import com.emma.endinv.util.Accessibility;
import com.emma.endinv.util.SortType;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Locks down the raw FriendlyByteBuf wire format used by every 1.20.1 loader.
 *
 * <p>Minecraft 1.20.1 predates the typed payload APIs used by the 26.1.2
 * implementation, so these codecs are the cross-loader protocol boundary.</p>
 */
public class PayloadCodec1201Test {

    @BeforeClass
    public static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void syncedConfigRoundTripsEveryFlagCombination() {
        for (boolean attaching : new boolean[]{false, true}) {
            for (boolean autoPicking : new boolean[]{false, true}) {
                SyncedConfig original = new SyncedConfig(attaching, autoPicking);
                assertEquals(original, roundTrip(original, SyncedConfig::decode));
            }
        }
    }

    @Test
    public void pageContextRoundTripsAllPageStateFields() {
        PageData pageData = new PageData(
                "bookmark",
                4,
                7,
                SortType.LAST_MODIFIED,
                true,
                "diamond pickaxe"
        );
        ItemPageContext original = new ItemPageContext(123, 29, pageData);

        assertEquals(original, roundTrip(original, ItemPageContext::decode));
    }

    @Test
    public void primitiveServerboundPayloadsRoundTrip() {
        OpenEndInvPayload open = new OpenEndInvPayload(true, 6, 11);
        assertEquals(open, roundTrip(open, OpenEndInvPayload::decode));

        QuickMoveToPagePayload quickMove =
                new QuickMoveToPagePayload(IntList.of(0, 5, 127, 512));
        assertEquals(quickMove, roundTrip(quickMove, QuickMoveToPagePayload::decode));

        for (Station station : Station.values()) {
            SetActiveStationPayload original = new SetActiveStationPayload(station);
            assertEquals(original, roundTrip(original, SetActiveStationPayload::decode));
        }
    }

    @Test
    public void invalidStationOrdinalFallsBackToNone() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeByte(255);
            assertEquals(
                    Station.NONE,
                    SetActiveStationPayload.decode(buffer).station()
            );
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }

    @Test
    public void nestedClientboundMetadataRoundTrips() {
        EndInvConfig config = new EndInvConfig(
                Accessibility.RESTRICTED,
                UUID.fromString("11111111-2222-3333-4444-555555555555"),
                List.of(
                        UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"),
                        UUID.fromString("01234567-89ab-cdef-0123-456789abcdef")
                )
        );
        EndInvMetadata original =
                new EndInvMetadata(321, 64, true, config);

        assertEquals(original, roundTrip(original, EndInvMetadata::decode));
    }

    @Test
    public void emptyCollectionPayloadsRoundTripWithoutRegistryBootstrap() {
        EndInvContent content = new EndInvContent(Map.of());
        assertTrue(roundTrip(content, EndInvContent::decode).itemMap().isEmpty());

        SetItemDisplayContentPayload display =
                new SetItemDisplayContentPayload(List.of());
        assertTrue(roundTrip(display, SetItemDisplayContentPayload::decode)
                .stacks()
                .isEmpty());

        MenuAttachabilityPayload attachability =
                new MenuAttachabilityPayload(false, true, Map.of());
        assertEquals(
                attachability,
                roundTrip(attachability, MenuAttachabilityPayload::decode)
        );
    }

    private static <T> T roundTrip(
            ModPacketPayload original,
            Function<FriendlyByteBuf, T> decoder
    ) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            original.write(buffer);
            T decoded = decoder.apply(buffer);
            assertEquals("Codec left unread bytes", 0, buffer.readableBytes());
            return decoded;
        } finally {
            buffer.release();
        }
    }
}
