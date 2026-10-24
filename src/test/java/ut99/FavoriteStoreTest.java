package ut99;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class FavoriteStoreTest {
    public static void main(String[] args) throws Exception {
        roundTripsFavorites();
        rejectsMalformedFavoriteKeys();
        combinesFavoritesWithoutDuplicates();
        System.out.println("FavoriteStore tests passed.");
    }

    private static void roundTripsFavorites() throws Exception {
        Path directory = Files.createTempDirectory("ut99-favorites-test");
        try {
            Path file = directory.resolve("favorites.lst");
            FavoriteStore store = new FavoriteStore(file);
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("ip", "82.165.208.134");
            fields.put("hostport", 7777);
            fields.put("hostname", "Green's Lair Ω");
            fields.put("numplayers", 2);
            fields.put("mapname", "AS-Revolution][-091");
            fields.put("favoriteDescription", "My custom server label");
            fields.put("autoUpdateDescription", false);
            fields.put("queryport", 7779);
            store.save(List.of(new ServerEntry(fields)));

            List<ServerEntry> loaded = new FavoriteStore(file).load();
            require(loaded.size() == 1, "favorite count");
            require("82.165.208.134:7777".equals(loaded.get(0).address()), "favorite address");
            require("Green's Lair Ω".equals(loaded.get(0).value("hostname", "")), "unicode hostname");
            require("2".equals(loaded.get(0).value("numplayers", "")), "player count");
            require("My custom server label".equals(
                    loaded.get(0).value("favoriteDescription", "")), "custom favorite description");
            require("false".equals(loaded.get(0).value("autoUpdateDescription", "")),
                    "favorite auto-update setting");
            require("7779".equals(loaded.get(0).value("queryport", "")), "custom query port");

            store.save(java.util.Collections.emptyList());
            require(new FavoriteStore(file).load().isEmpty(), "removing all favorites");
        } finally {
            Files.deleteIfExists(directory.resolve("favorites.lst"));
            Files.deleteIfExists(directory);
        }
    }

    private static void rejectsMalformedFavoriteKeys() throws Exception {
        Path directory = Files.createTempDirectory("ut99-favorites-invalid-test");
        try {
            Path file = directory.resolve("favorites.lst");
            Files.write(file, List.of("*.hostname=Bad server"));
            try {
                new FavoriteStore(file).load();
                throw new AssertionError("Accepted malformed favorite key");
            } catch (IOException expected) {
                // Invalid persisted data should be surfaced as an error.
            }
        } finally {
            Files.deleteIfExists(directory.resolve("favorites.lst"));
            Files.deleteIfExists(directory);
        }
    }

    private static void combinesFavoritesWithoutDuplicates() {
        ServerEntry existing = server("192.0.2.1", 7777, "Existing");
        ServerEntry duplicateExisting = server("192.0.2.1", 7777, "Duplicate existing");
        ServerEntry duplicateImported = server("192.0.2.1", 7777, "Imported duplicate");
        ServerEntry newImported = server("192.0.2.2", 7777, "New server");

        List<ServerEntry> merged = FavoriteStore.combineFavorites(
                List.of(existing, duplicateExisting),
                List.of(duplicateImported, newImported), true);
        require(merged.size() == 2, "merge removes existing and imported duplicates");
        require(merged.get(0) == existing, "merge keeps existing favorite");
        require(merged.get(1) == newImported, "merge adds new favorite");

        List<ServerEntry> replaced = FavoriteStore.combineFavorites(
                List.of(existing), List.of(duplicateImported, newImported), false);
        require(replaced.size() == 2, "replace removes imported duplicates");
        require(replaced.get(0) == duplicateImported, "replace uses imported favorite");
    }

    private static ServerEntry server(String ip, int port, String hostname) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("ip", ip);
        fields.put("hostport", port);
        fields.put("hostname", hostname);
        return new ServerEntry(fields);
    }

    private static void require(boolean condition, String name) {
        if (!condition) {
            throw new AssertionError("Failed check: " + name);
        }
    }
}
