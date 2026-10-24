package ut99;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class GameServerQueryTest {
    public static void main(String[] args) throws Exception {
        parsesStatusResponse();
        sortsPlayersByDescendingFrags();
        sortsServerRowsNumericallyByPlayerCount();
        queriesAdvertisedPortDirectly();
        fallsBackToLegacyStatusQuery();
        rejectsMalformedStatusResponse();
        rejectsInvalidAddress();
        System.out.println("GameServerQuery tests passed.");
    }

    private static void sortsServerRowsNumericallyByPlayerCount() {
        require(ServerBrowserApp.playerCount("12 / 24") > ServerBrowserApp.playerCount("9 / 24"),
                "player count comparison is numeric");
        require(ServerBrowserApp.playerCount("invalid") == 0, "malformed player count");
    }

    private static void sortsPlayersByDescendingFrags() {
        Map<String, String> lowerScore = new LinkedHashMap<>();
        lowerScore.put("playername", "Alice");
        lowerScore.put("frags", "3");
        Map<String, String> higherScore = new LinkedHashMap<>();
        higherScore.put("playername", "Bob");
        higherScore.put("frags", "15");
        Map<String, String> missingScore = new LinkedHashMap<>();
        missingScore.put("playername", "No score");

        List<Map<String, String>> sorted = ServerBrowserApp.sortPlayersByScore(
                new ArrayList<>(List.of(lowerScore, missingScore, higherScore)));
        require(sorted.get(0) == higherScore, "highest frags first");
        require(sorted.get(1) == lowerScore, "next highest frags");
        require(sorted.get(2) == missingScore, "missing score last");
    }

    private static void queriesAdvertisedPortDirectly() throws Exception {
        try (DatagramSocket server = new DatagramSocket(new InetSocketAddress(
                InetAddress.getByName("127.0.0.1"), 0))) {
            server.setSoTimeout(3000);
            int queryPort = server.getLocalPort();
            Thread responder = new Thread(() -> {
                try {
                    byte[] requestBuffer = new byte[128];
                    DatagramPacket request = new DatagramPacket(requestBuffer, requestBuffer.length);
                    server.receive(request);
                    byte[] response = "\\hostname\\Live server\\mapname\\DM-Current"
                            .concat("\\numplayers\\2\\maxplayers\\8\\final\\")
                            .getBytes(StandardCharsets.ISO_8859_1);
                    server.send(new DatagramPacket(response, response.length,
                            request.getAddress(), request.getPort()));
                } catch (IOException e) {
                    throw new AssertionError("Could not answer direct server query", e);
                }
            });
            responder.start();
            ServerEntry entry = new ServerEntry(Map.of(
                    "ip", "127.0.0.1",
                    "hostport", 1,
                    "queryport", queryPort));

            GameServerQuery.Result result = GameServerQuery.query(entry, 1000);
            responder.join(3000);
            require(!responder.isAlive(), "query responder completed");
            require("Live server".equals(result.fields.get("hostname")), "live hostname");
            require("DM-Current".equals(result.fields.get("mapname")), "live map");
            require("2".equals(result.fields.get("numplayers")), "live player count");
        }
    }

    private static void parsesStatusResponse() throws Exception {
        Map<String, String> fields = GameServerQuery.parseResponse(
                "\\hostname\\Test server\\mapname\\DM-Test\\numplayers\\1\\playername_0\\Alice"
                        + "\\frags_0\\25\\ping_0\\42\\final\\");
        require("Test server".equals(fields.get("hostname")), "hostname");
        require("DM-Test".equals(fields.get("mapname")), "map");
        require("1".equals(fields.get("numplayers")), "player count");
        require("Alice".equals(fields.get("playername_0")), "player name");
        require("25".equals(fields.get("frags_0")), "live player score");
        require("42".equals(fields.get("ping_0")), "live player ping");
        require(fields.containsKey("final"), "final packet marker");
        Map<String, String> responseWithoutTrailingSlash = GameServerQuery.parseResponse(
                "\\hostname\\Test server\\final");
        require("Test server".equals(responseWithoutTrailingSlash.get("hostname")),
                "final packet without trailing slash");
    }

    private static void fallsBackToLegacyStatusQuery() throws Exception {
        try (DatagramSocket server = new DatagramSocket(new InetSocketAddress(
                InetAddress.getByName("127.0.0.1"), 0))) {
            server.setSoTimeout(3000);
            int queryPort = server.getLocalPort();
            Thread responder = new Thread(() -> {
                try {
                    byte[] requestBuffer = new byte[128];
                    DatagramPacket request = new DatagramPacket(requestBuffer, requestBuffer.length);
                    server.receive(request);
                    request.setLength(requestBuffer.length);
                    server.receive(request);
                    byte[] response = "\\hostname\\Legacy server\\final\\"
                            .getBytes(StandardCharsets.ISO_8859_1);
                    server.send(new DatagramPacket(response, response.length,
                            request.getAddress(), request.getPort()));
                } catch (IOException e) {
                    throw new AssertionError("Could not answer legacy status query", e);
                }
            });
            responder.start();
            ServerEntry entry = new ServerEntry(Map.of(
                    "ip", "127.0.0.1",
                    "hostport", 1,
                    "queryport", queryPort));

            GameServerQuery.Result result = GameServerQuery.query(entry, 1000);
            responder.join(3000);
            require(!responder.isAlive(), "legacy query responder completed");
            require("Legacy server".equals(result.fields.get("hostname")), "legacy query fallback");
        }
    }

    private static void rejectsMalformedStatusResponse() {
        try {
            GameServerQuery.parseResponse("\\hostname\\Only key\\unpaired");
            throw new AssertionError("Accepted malformed status response");
        } catch (IOException expected) {
            // Invalid response data should be surfaced as an error.
        }
    }

    private static void rejectsInvalidAddress() {
        try {
            GameServerQuery.query(new ServerEntry(Map.of("ip", "256.0.0.1", "hostport", 7777)), 100);
            throw new AssertionError("Accepted invalid IPv4 address");
        } catch (IOException expected) {
            // Invalid targets are rejected before a network request is made.
        }
    }

    private static void require(boolean condition, String name) {
        if (!condition) {
            throw new AssertionError("Failed check: " + name);
        }
    }
}
