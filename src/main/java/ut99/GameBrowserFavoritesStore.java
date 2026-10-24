package ut99;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class GameBrowserFavoritesStore {
    private static final String SECTION = "[UBrowser.UBrowserFavoritesFact]";
    private static final int MAX_FAVORITES = 100;
    private static final Pattern FAVORITE_KEY = Pattern.compile(
            "^\\s*Favorites\\[(\\d+)]\\s*=\\s*(.*?)\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern FAVORITE_COUNT_KEY = Pattern.compile(
            "^\\s*FavoriteCount\\s*=.*$", Pattern.CASE_INSENSITIVE);
    private final Path file;

    GameBrowserFavoritesStore(Path file) {
        this.file = file;
    }

    List<ServerEntry> load() throws IOException {
        IniContent ini = readIni();
        String[] lines = splitLines(ini.text);
        int[] section = findSection(lines);
        Map<Integer, ServerEntry> entries = new TreeMap<>();
        for (int index = section[0] + 1; index < section[1]; index++) {
            Matcher matcher = FAVORITE_KEY.matcher(lines[index]);
            if (!matcher.matches() || matcher.group(2).isEmpty()) {
                continue;
            }
            int favoriteIndex;
            try {
                favoriteIndex = Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException e) {
                throw new IOException("Invalid favorite index in " + file + ".", e);
            }
            if (favoriteIndex < 0 || favoriteIndex >= MAX_FAVORITES) {
                throw new IOException("Favorite index exceeds the supported range in " + file + ".");
            }
            ServerEntry favorite = parseFavorite(matcher.group(2));
            if (entries.put(favoriteIndex, favorite) != null) {
                throw new IOException("Duplicate favorite index " + favoriteIndex + " in " + file + ".");
            }
        }
        return FavoriteStore.combineFavorites(new ArrayList<>(),
                new ArrayList<>(entries.values()), false);
    }

    void save(List<ServerEntry> favorites) throws IOException {
        List<ServerEntry> uniqueFavorites = FavoriteStore.combineFavorites(
                new ArrayList<>(), favorites, false);
        if (uniqueFavorites.size() > MAX_FAVORITES) {
            throw new IOException("The UT99 game browser supports at most "
                    + MAX_FAVORITES + " favorites.");
        }
        List<String> records = new ArrayList<>();
        for (ServerEntry favorite : uniqueFavorites) {
            records.add(formatFavorite(favorite));
        }

        IniContent ini = readIni();
        String[] lines = splitLines(ini.text);
        int[] section = findSection(lines);
        List<String> updatedLines = new ArrayList<>();
        int insertionIndex = -1;
        for (int index = 0; index < lines.length; index++) {
            if (index > section[0] && index < section[1]
                    && isFavoriteSetting(lines[index])) {
                if (insertionIndex < 0) {
                    insertionIndex = updatedLines.size();
                }
                continue;
            }
            updatedLines.add(lines[index]);
        }

        if (insertionIndex < 0) {
            insertionIndex = section[0] + 1;
        }
        List<String> favoriteLines = new ArrayList<>(MAX_FAVORITES + 1);
        favoriteLines.add("FavoriteCount=" + records.size());
        for (int index = 0; index < MAX_FAVORITES; index++) {
            String record = index < records.size() ? records.get(index) : "";
            favoriteLines.add("Favorites[" + index + "]=" + record);
        }
        updatedLines.addAll(insertionIndex, favoriteLines);
        writeIni(ini, String.join(ini.newline, updatedLines));
    }

    private IniContent readIni() throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        boolean utf8Bom = bytes.length >= 3
                && (bytes[0] & 0xff) == 0xef && (bytes[1] & 0xff) == 0xbb
                && (bytes[2] & 0xff) == 0xbf;
        byte[] content = utf8Bom ? java.util.Arrays.copyOfRange(bytes, 3, bytes.length) : bytes;
        Charset charset;
        try {
            StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content));
            charset = StandardCharsets.UTF_8;
        } catch (CharacterCodingException e) {
            charset = StandardCharsets.ISO_8859_1;
        }
        String text = new String(content, charset);
        String newline = text.contains("\r\n") ? "\r\n" : text.contains("\r") ? "\r" : "\n";
        return new IniContent(text, charset, utf8Bom, newline);
    }

    private void writeIni(IniContent ini, String text) throws IOException {
        byte[] content = text.getBytes(ini.charset);
        byte[] bytes;
        if (ini.utf8Bom) {
            bytes = new byte[content.length + 3];
            bytes[0] = (byte) 0xef;
            bytes[1] = (byte) 0xbb;
            bytes[2] = (byte) 0xbf;
            System.arraycopy(content, 0, bytes, 3, content.length);
        } else {
            bytes = content;
        }
        Path parent = file.toAbsolutePath().getParent();
        Path temporaryFile = Files.createTempFile(parent, "ut99-favorites-", ".tmp");
        try {
            Files.write(temporaryFile, bytes);
            try {
                Files.move(temporaryFile, file, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporaryFile, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporaryFile);
        }
    }

    private int[] findSection(String[] lines) throws IOException {
        int start = -1;
        int end = lines.length;
        for (int index = 0; index < lines.length; index++) {
            if (!lines[index].trim().equalsIgnoreCase(SECTION)) {
                continue;
            }
            if (start >= 0) {
                throw new IOException("The favorites section appears more than once in " + file + ".");
            }
            start = index;
            for (int next = index + 1; next < lines.length; next++) {
                String line = lines[next].trim();
                if (line.startsWith("[") && line.endsWith("]")) {
                    end = next;
                    break;
                }
            }
        }
        if (start < 0) {
            throw new IOException("Could not find " + SECTION + " in " + file + ".");
        }
        return new int[]{start, end};
    }

    private static boolean isFavoriteSetting(String line) {
        return FAVORITE_COUNT_KEY.matcher(line).matches()
                || FAVORITE_KEY.matcher(line).matches();
    }

    private ServerEntry parseFavorite(String value) throws IOException {
        int flagSeparator = value.lastIndexOf('\\');
        int portSeparator = flagSeparator > 0 ? value.lastIndexOf('\\', flagSeparator - 1) : -1;
        int addressSeparator = portSeparator > 0 ? value.lastIndexOf('\\', portSeparator - 1) : -1;
        if (addressSeparator <= 0 || portSeparator <= addressSeparator + 1
                || flagSeparator <= portSeparator + 1 || flagSeparator == value.length() - 1) {
            throw new IOException("Invalid favorite entry in " + file + ".");
        }
        String description = value.substring(0, addressSeparator);
        String ip = value.substring(addressSeparator + 1, portSeparator);
        String queryPort = value.substring(portSeparator + 1, flagSeparator);
        if (!isValidIpv4(ip) || !isValidPort(queryPort) || Integer.parseInt(queryPort) < 2) {
            throw new IOException("Invalid favorite query address in " + file + ": "
                    + ip + "\\" + queryPort);
        }

        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("ip", ip);
        fields.put("hostport", Integer.toString(Integer.parseInt(queryPort) - 1));
        fields.put("queryport", Integer.toString(Integer.parseInt(queryPort)));
        fields.put("hostname", description);
        fields.put("favoriteDescription", description);
        fields.put("autoUpdateDescription", "true");
        return new ServerEntry(fields);
    }

    private static String formatFavorite(ServerEntry favorite) throws IOException {
        String description = favorite.value("favoriteDescription",
                favorite.value("hostname", "Unnamed server"));
        String ip = favorite.value("ip", "");
        String queryPort = favorite.value("queryport", "");
        if (queryPort.isEmpty()) {
            try {
                int gamePort = Integer.parseInt(favorite.value("hostport", ""));
                queryPort = Integer.toString(gamePort + 1);
            } catch (NumberFormatException e) {
                throw new IOException("Cannot determine query port for favorite: "
                        + favorite.address(), e);
            }
        }
        if (description.trim().isEmpty() || description.contains("\r") || description.contains("\n")) {
            throw new IOException("Favorite names must not be blank or contain line breaks.");
        }
        if (!isValidIpv4(ip) || !isValidPort(queryPort) || Integer.parseInt(queryPort) < 2) {
            throw new IOException("Cannot sync favorite with invalid address: " + favorite.address());
        }
        return description + "\\" + ip + "\\" + queryPort + "\\False";
    }

    private static boolean isValidIpv4(String value) {
        String[] octets = value.split("\\.", -1);
        if (octets.length != 4) {
            return false;
        }
        for (String octet : octets) {
            if (!octet.matches("\\d{1,3}") || Integer.parseInt(octet) > 255) {
                return false;
            }
        }
        return true;
    }

    private static boolean isValidPort(String value) {
        if (!value.matches("\\d{1,5}")) {
            return false;
        }
        int port = Integer.parseInt(value);
        return port >= 1 && port <= 65535;
    }

    private static String[] splitLines(String text) {
        return text.split("\\r\\n|\\n|\\r", -1);
    }

    private static final class IniContent {
        private final String text;
        private final Charset charset;
        private final boolean utf8Bom;
        private final String newline;

        IniContent(String text, Charset charset, boolean utf8Bom, String newline) {
            this.text = text;
            this.charset = charset;
            this.utf8Bom = utf8Bom;
            this.newline = newline;
        }
    }
}
