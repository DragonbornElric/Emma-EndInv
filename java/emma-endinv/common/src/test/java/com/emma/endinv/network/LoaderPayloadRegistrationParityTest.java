package com.emma.endinv.network;

import com.emma.endinv.network.payloads.ModPacketPayload;
import com.emma.endinv.network.payloads.SyncedConfig;
import com.emma.endinv.network.payloads.toClient.EndInvContent;
import com.emma.endinv.network.payloads.toClient.EndInvMetadata;
import com.emma.endinv.network.payloads.toClient.ItemPickedUpPayload;
import com.emma.endinv.network.payloads.toClient.MenuAttachabilityPayload;
import com.emma.endinv.network.payloads.toClient.SetItemDisplayContentPayload;
import com.emma.endinv.network.payloads.toClient.SetStarredPagePayload;
import com.emma.endinv.network.payloads.toServer.BulkQuickMoveFromPagePayload;
import com.emma.endinv.network.payloads.toServer.CreativeItemModPayload;
import com.emma.endinv.network.payloads.toServer.ItemClickPayload;
import com.emma.endinv.network.payloads.toServer.ItemPageContext;
import com.emma.endinv.network.payloads.toServer.OpenEndInvPayload;
import com.emma.endinv.network.payloads.toServer.QuickMoveToPagePayload;
import com.emma.endinv.network.payloads.toServer.SetActiveStationPayload;
import com.emma.endinv.network.payloads.toServer.StarItemPayload;
import com.emma.endinv.network.payloads.toServer.SwapMenuSlotPayload;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Guards against a loader silently dropping one side of the shared raw
 * custom-payload protocol.
 */
public class LoaderPayloadRegistrationParityTest {

    private static final Set<String> EXPECTED_C2S = Set.of(
            "item_click",
            "item_modify",
            "page_context",
            "open_endinv",
            "quick_move_page",
            "bulk_quick_move_from_page",
            "star_item",
            "set_active_station",
            "endinv_settings",
            "swap_menu_slot"
    );

    private static final Set<String> EXPECTED_S2C = Set.of(
            "endinv_content",
            "endinv_meta",
            "auto_picked",
            "itemdisplay_content",
            "starred_item",
            "menu_attachability",
            "endinv_settings"
    );

    @Test
    public void commonPayloadIdsMatchTheCompleteDirectionalSurface() {
        List<ModPacketPayload> c2s = List.of(
                new ItemClickPayload(null, 0, null),
                new CreativeItemModPayload(null, false),
                new ItemPageContext(0, 0, null),
                new OpenEndInvPayload(),
                new QuickMoveToPagePayload((it.unimi.dsi.fastutil.ints.IntList) null),
                new BulkQuickMoveFromPagePayload(null),
                new StarItemPayload(null, false),
                new SetActiveStationPayload(null),
                SyncedConfig.DEFAULT,
                new SwapMenuSlotPayload(null, 0)
        );
        List<ModPacketPayload> s2c = List.of(
                new EndInvContent(null),
                new EndInvMetadata(0, 0, false, null),
                new ItemPickedUpPayload(null),
                new SetItemDisplayContentPayload(null),
                new SetStarredPagePayload(null),
                new MenuAttachabilityPayload(false, false, Map.of()),
                SyncedConfig.DEFAULT
        );

        assertPayloadIds("common C2S", EXPECTED_C2S, c2s);
        assertPayloadIds("common S2C", EXPECTED_S2C, s2c);
        assertProtocolShape();
    }

    @Test
    public void fabricRegistersTheCompleteDirectionalSurface() throws IOException {
        String source = readProjectSource(
                "fabric/src/main/java/com/emma/endinv/network/FabricNetworking.java"
        );

        assertEquals(
                EXPECTED_C2S,
                captureIds(source, "registerServerbound\\s*\\([^;]*?\"([a-z0-9_]+)\"\\s*\\);")
        );
        assertEquals(
                EXPECTED_S2C,
                captureIds(source, "registerClientbound\\s*\\([^;]*?\"([a-z0-9_]+)\"\\s*\\);")
        );
    }

    @Test
    public void legacyNeoForgeRegistersTheCompleteDirectionalSurface() throws IOException {
        String source = readProjectSource(
                "neoforge/src/main/java/com/emma/endinv/network/NeoForgeNetworking.java"
        );

        Set<String> c2s = captureIds(
                source,
                "registerToServer\\s*\\(\\s*\"([a-z0-9_]+)\""
        );
        Set<String> s2c = captureIds(
                source,
                "registerToClient\\s*\\(\\s*\"([a-z0-9_]+)\""
        );

        assertTrue(
                "NeoForge must create the bidirectional settings channel",
                source.contains("createChannel(\"endinv_settings\")")
        );
        assertTrue(
                "NeoForge settings must accept client-to-server traffic",
                source.contains("NetworkEvent.ClientCustomPayloadEvent")
        );
        assertTrue(
                "NeoForge settings must accept server-to-client traffic",
                source.contains("NetworkEvent.ServerCustomPayloadEvent")
        );
        c2s.add("endinv_settings");
        s2c.add("endinv_settings");

        assertEquals(EXPECTED_C2S, c2s);
        assertEquals(EXPECTED_S2C, s2c);
    }

    @Test
    public void foliaRegistersC2SAndEmitsEveryS2CPayloadId() throws IOException {
        String incoming = readProjectSource(
                "folia/src/main/java/com/emma/endinv/folia/FoliaIncomingPayloadBridge.java"
        );
        Set<String> c2s = captureIds(
                incoming,
                "Map\\.entry\\s*\\(\\s*channel\\(\"([a-z0-9_]+)\"\\)"
        );
        assertEquals(EXPECTED_C2S, c2s);

        Path project = findProjectDirectory();
        Path toClient = project.resolve(
                "folia/src/main/java/com/emma/endinv/network/payloads/toClient"
        );
        Set<String> s2c = new LinkedHashSet<>();
        try (Stream<Path> files = Files.list(toClient)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                if (source.contains("implements ModPacketPayload")) {
                    s2c.addAll(captureIds(
                            source,
                            "return\\s+\"([a-z0-9_]+)\"\\s*;"
                    ));
                }
            }
        }
        // SyncedConfig is supplied by commonServerJava rather than duplicated in Folia.
        s2c.add(SyncedConfig.DEFAULT.id());

        String distributor = readProjectSource(
                "folia/src/main/java/com/emma/endinv/folia/FoliaPacketDistributor.java"
        );
        assertTrue(
                "Folia S2C must emit each common payload's own raw channel id",
                distributor.contains("payload.payloadId()")
        );
        assertEquals(EXPECTED_S2C, s2c);
    }

    private static void assertPayloadIds(
            String label,
            Set<String> expected,
            List<ModPacketPayload> payloads
    ) {
        List<String> ids = payloads.stream().map(ModPacketPayload::id).toList();
        assertEquals(label + " direction count", expected.size(), ids.size());
        assertEquals(label + " contains duplicate ids", ids.size(), new LinkedHashSet<>(ids).size());
        assertEquals(label + " ids", expected, new LinkedHashSet<>(ids));
    }

    private static void assertProtocolShape() {
        assertEquals("C2S direction count", 10, EXPECTED_C2S.size());
        assertEquals("S2C direction count", 7, EXPECTED_S2C.size());

        Set<String> overlap = new LinkedHashSet<>(EXPECTED_C2S);
        overlap.retainAll(EXPECTED_S2C);
        assertEquals(Set.of("endinv_settings"), overlap);

        Set<String> unique = new LinkedHashSet<>(EXPECTED_C2S);
        unique.addAll(EXPECTED_S2C);
        assertEquals("unique channel count", 16, unique.size());
    }

    private static Set<String> captureIds(String source, String expression) {
        Matcher matcher = Pattern.compile(expression, Pattern.DOTALL).matcher(source);
        Set<String> ids = new LinkedHashSet<>();
        while (matcher.find()) {
            ids.add(matcher.group(1));
        }
        return ids;
    }

    private static String readProjectSource(String relativePath) throws IOException {
        return Files.readString(findProjectDirectory().resolve(relativePath));
    }

    private static Path findProjectDirectory() {
        Path current = Path.of("").toAbsolutePath().normalize();
        List<Path> searched = new ArrayList<>();
        while (current != null) {
            searched.add(current);
            if (Files.isRegularFile(current.resolve("settings.gradle"))
                    && Files.isDirectory(current.resolve("common"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new AssertionError(
                "Could not locate emma-endinv project from " + searched
        );
    }
}
