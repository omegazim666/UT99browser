package ut99;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class GameBrowserFavoritesStoreTest {
    public static void main(String[] args) throws Exception {
        loadsFavoritesAndIgnoresEmptySlots();
        savesFavoritesWithoutChangingOtherSections();
        rejectsMalformedFavorites();
        rejectsFilesWithoutFavoritesSection();
        System.out.println("GameBrowserFavoritesStore tests passed.");
    }

    private static void loadsFavoritesAndIgnoresEmptySlots() throws Exception {
        Path file = createIni(
                "[OtherSection]\r\nValue=keep\r\n\r\n"
                        + "[UBrowser.UBrowserFavoritesFact]\r\n"
                        + "FavoriteCount=2\r\n"
                        + "Favorites[0]=Green's Lair Ω\\45.61.54.168\\7778\\False\r\n"
                        + "Favorites[1]=\r\n"
                        + "Favorites[2]=Second server\\192.0.2.2\\65535\\False\r\n"
                        + "Favorites[3]=Duplicate address\\45.61.54.168\\7778\\False\r\n");
        try {
            List<ServerEntry> favorites = new GameBrowserFavoritesStore(file).load();
            require(favorites.size() == 2, "empty slots ignored");
            require("45.61.54.168:7777".equals(favorites.get(0).address()),
                    "INI query port maps to expected game port");
            require("7777".equals(favorites.get(0).value("hostport", "")), "game port from INI query port");
            require("Green's Lair Ω".equals(
                    favorites.get(0).value("favoriteDescription", "")), "unicode favorite name");
            require("7778".equals(favorites.get(0).value("queryport", "")), "query port from INI");
            require("65534".equals(favorites.get(1).value("hostport", "")),
                    "game port derived from maximum query port");
            require("65535".equals(favorites.get(1).value("queryport", "")),
                    "maximum query port preserved");
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(file.getParent());
        }
    }

    private static void savesFavoritesWithoutChangingOtherSections() throws Exception {
        Path file = createIni(
                "[OtherSection]\r\nValue=keep\r\n\r\n"
                        + "[UBrowser.UBrowserFavoritesFact]\r\n"
                        + "FavoriteCount=2\r\n"
                        + "Favorites[0]=Old server\\192.0.2.1\\7778\\False\r\n"
                        + "Favorites[1]=Stale server\\192.0.2.2\\7778\\False\r\n"
                        + "OtherSetting=preserve\r\n\r\n"
                        + "[NextSection]\r\nValue=also keep\r\n");
        try {
            List<ServerEntry> favorites = List.of(
                    server("203.0.113.10", 7780, "New server"),
                    server("203.0.113.11", 7781, "Server with \\ separator"),
                    server("203.000.113.010", "07780", "Duplicate server"));
            GameBrowserFavoritesStore store = new GameBrowserFavoritesStore(file);
            store.save(favorites);

            String contents = Files.readString(file, StandardCharsets.UTF_8);
            require(contents.contains("[OtherSection]\r\nValue=keep"), "preceding section preserved");
            require(contents.contains("OtherSetting=preserve"), "other favorites-section setting preserved");
            require(contents.contains("[NextSection]\r\nValue=also keep"), "following section preserved");
            require(contents.contains("FavoriteCount=2"), "duplicate favorite removed before writing");
            require(contents.contains("Favorites[0]=New server\\203.0.113.10\\7781\\False"),
                    "first favorite query port written");
            require(contents.contains("Favorites[1]=Server with \\ separator\\203.0.113.11\\7782\\False"),
                    "name containing separator and query port written");
            require(!contents.contains("Stale server"), "stale favorites removed");
            require(store.load().size() == 2, "written favorites can be loaded");
            List<ServerEntry> loaded = store.load();
            require(loaded.size() == 2, "duplicate favorites load only once");
            require("203.0.113.10:7780".equals(loaded.get(0).address()),
                    "sync round trip retains game port");
            require("7781".equals(loaded.get(0).value("queryport", "")),
                    "sync round trip retains query port");
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(file.getParent());
        }
    }

    private static void rejectsMalformedFavorites() throws Exception {
        Path file = createIni("[UBrowser.UBrowserFavoritesFact]\n"
                + "FavoriteCount=1\nFavorites[0]=Broken\\not-an-ip\\7777\\False\n");
        try {
            try {
                new GameBrowserFavoritesStore(file).load();
                throw new AssertionError("Accepted malformed favorite");
            } catch (IOException expected) {
                require(expected.getMessage().contains("Invalid favorite query address"),
                        "malformed address reports an error");
            }
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(file.getParent());
        }
    }

    private static void rejectsFilesWithoutFavoritesSection() throws Exception {
        Path file = createIni("[OtherSection]\nValue=keep\n");
        try {
            byte[] original = Files.readAllBytes(file);
            try {
                new GameBrowserFavoritesStore(file).save(List.of(server("192.0.2.1", 7777, "Server")));
                throw new AssertionError("Wrote favorites without the game browser section");
            } catch (IOException expected) {
                require(java.util.Arrays.equals(original, Files.readAllBytes(file)),
                        "INI remains unchanged when favorites section is missing");
            }
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(file.getParent());
        }
    }

    private static Path createIni(String contents) throws IOException {
        Path directory = Files.createTempDirectory("ut99-game-favorites-test");
        return Files.writeString(directory.resolve("UnrealTournament.ini"),
                contents, StandardCharsets.UTF_8);
    }

    private static ServerEntry server(String ip, int port, String description) {
        return server(ip, Integer.toString(port), description);
    }

    private static ServerEntry server(String ip, String port, String description) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("ip", ip);
        fields.put("hostport", port);
        fields.put("queryport", Integer.toString(Integer.parseInt(port) + 1));
        fields.put("hostname", description);
        fields.put("favoriteDescription", description);
        return new ServerEntry(fields);
    }

    private static void require(boolean condition, String name) {
        if (!condition) {
            throw new AssertionError("Failed check: " + name);
        }
    }
}
