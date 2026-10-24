package ut99;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

final class FavoriteStore {
    private final Path file;

    FavoriteStore(Path file) {
        this.file = file;
    }

    List<ServerEntry> load() throws IOException {
        Properties properties = new Properties();
        if (Files.exists(file)) {
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                properties.load(reader);
            }
        }

        Map<String, Map<String, Object>> entries = new LinkedHashMap<>();
        for (String property : properties.stringPropertyNames()) {
            int separator = property.indexOf('.');
            if (separator <= 0 || separator == property.length() - 1) {
                throw new IOException("Invalid favorite entry in " + file + ".");
            }
            String encodedAddress = property.substring(0, separator);
            String field = property.substring(separator + 1);
            String address;
            try {
                address = new String(Base64.getUrlDecoder().decode(encodedAddress), StandardCharsets.UTF_8);
            } catch (IllegalArgumentException e) {
                throw new IOException("Invalid favorite address in " + file + ".", e);
            }
            entries.computeIfAbsent(address, ignored -> new LinkedHashMap<>())
                    .put(field, properties.getProperty(property));
        }

        List<ServerEntry> favorites = new ArrayList<>();
        for (Map<String, Object> fields : entries.values()) {
            favorites.add(new ServerEntry(fields));
        }
        return favorites;
    }

    static List<ServerEntry> combineFavorites(
            List<ServerEntry> existing, List<ServerEntry> imported, boolean merge) {
        List<ServerEntry> combined = new ArrayList<>();
        if (merge) {
            addUniqueFavorites(combined, existing);
        }
        addUniqueFavorites(combined, imported);
        return combined;
    }

    private static void addUniqueFavorites(List<ServerEntry> target, List<ServerEntry> candidates) {
        for (ServerEntry candidate : candidates) {
            boolean duplicate = false;
            for (ServerEntry favorite : target) {
                if (favorite.address().equalsIgnoreCase(candidate.address())) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) {
                target.add(candidate);
            }
        }
    }

    void save(List<ServerEntry> favorites) throws IOException {
        Properties properties = new Properties();
        for (ServerEntry favorite : favorites) {
            String address = favorite.address();
            String encodedAddress = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(address.getBytes(StandardCharsets.UTF_8));
            for (Map.Entry<String, Object> field : favorite.fields.entrySet()) {
                if (field.getValue() != null) {
                    properties.setProperty(encodedAddress + "." + field.getKey(),
                            String.valueOf(field.getValue()));
                }
            }
        }

        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path temporaryFile = Files.createTempFile(parent, "favorites-", ".tmp");
        try {
            try (Writer writer = Files.newBufferedWriter(temporaryFile, StandardCharsets.UTF_8)) {
                properties.store(writer, "UT99 Server Browser favorites");
            }
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
}
