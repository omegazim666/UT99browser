package ut99;

import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.security.DigestInputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class UpdateChecker {
    private static final URI LATEST_RELEASE_API = URI.create(
            "https://api.github.com/repos/omegazim666/UT99browser/releases/latest");
    private static final Pattern VERSION_PATTERN = Pattern.compile("(?i)^B(\\d+)\\.(\\d+)$");
    private static final Pattern SHA256_PATTERN = Pattern.compile("(?i)^sha256:([0-9a-f]{64})$");
    private static final long MAX_UPDATE_SIZE = 100L * 1024 * 1024;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    Optional<Release> checkForUpdate(String currentVersion) throws IOException {
        HttpRequest request = HttpRequest.newBuilder(LATEST_RELEASE_API)
                .timeout(Duration.ofSeconds(8))
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "UT99Browser")
                .GET()
                .build();
        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("The GitHub update check was interrupted.", e);
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("GitHub returned HTTP " + response.statusCode()
                    + " while checking for updates.");
        }

        Release release = parseRelease(response.body());
        return isNewerVersion(currentVersion, release.version)
                ? Optional.of(release)
                : Optional.empty();
    }

    Path downloadUpdate(Release release) throws IOException {
        Path archive = Files.createTempFile("ut99-update-", ".zip");
        try {
            HttpRequest request = HttpRequest.newBuilder(release.downloadUrl)
                    .timeout(Duration.ofMinutes(5))
                    .header("Accept", "application/octet-stream")
                    .header("User-Agent", "UT99Browser")
                    .GET()
                    .build();
            HttpResponse<InputStream> response;
            try {
                response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("The update download was interrupted.", e);
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                try (InputStream body = response.body()) {
                    throw new IOException("GitHub returned HTTP " + response.statusCode()
                            + " while downloading the update.");
                }
            }

            MessageDigest digest = sha256();
            long bytesWritten = 0;
            try (InputStream body = new DigestInputStream(response.body(), digest);
                 OutputStream output = Files.newOutputStream(archive, StandardOpenOption.WRITE,
                         StandardOpenOption.TRUNCATE_EXISTING)) {
                byte[] buffer = new byte[16 * 1024];
                int count;
                while ((count = body.read(buffer)) != -1) {
                    bytesWritten += count;
                    if (bytesWritten > MAX_UPDATE_SIZE || bytesWritten > release.assetSize) {
                        throw new IOException("The downloaded update is larger than expected.");
                    }
                    output.write(buffer, 0, count);
                }
            }
            if (bytesWritten != release.assetSize) {
                throw new IOException("The downloaded update size does not match GitHub's release metadata.");
            }
            if (!toHex(digest.digest()).equalsIgnoreCase(release.sha256)) {
                throw new IOException("The downloaded update failed its SHA-256 integrity check.");
            }
            return archive;
        } catch (IOException e) {
            Files.deleteIfExists(archive);
            throw e;
        }
    }

    static Release parseRelease(String json) throws IOException {
        Object parsed;
        try {
            parsed = JsonParser.parse(json);
        } catch (IllegalArgumentException e) {
            throw new IOException("GitHub returned invalid release data.", e);
        }
        if (!(parsed instanceof Map)) {
            throw new IOException("GitHub returned malformed release data.");
        }

        Map<?, ?> fields = (Map<?, ?>) parsed;
        Object tag = fields.get("tag_name");
        Object assetsValue = fields.get("assets");
        if (!(tag instanceof String) || !(assetsValue instanceof List)) {
            throw new IOException("GitHub's latest release is missing its version or downloadable assets.");
        }
        String version = (String) tag;
        if (!VERSION_PATTERN.matcher(version).matches()) {
            throw new IOException("GitHub returned an unsupported release version: " + version);
        }

        String expectedAssetName = "UT99_Browser-" + version + ".zip";
        Map<?, ?> updateAsset = null;
        for (Object assetValue : (List<?>) assetsValue) {
            if (assetValue instanceof Map
                    && expectedAssetName.equals(((Map<?, ?>) assetValue).get("name"))) {
                updateAsset = (Map<?, ?>) assetValue;
                break;
            }
        }
        if (updateAsset == null
                || !(updateAsset.get("browser_download_url") instanceof String)
                || !(updateAsset.get("digest") instanceof String)
                || !(updateAsset.get("size") instanceof Number)) {
            throw new IOException("GitHub's latest release is missing its standalone Windows update package.");
        }
        URI downloadUri;
        try {
            downloadUri = URI.create((String) updateAsset.get("browser_download_url"));
        } catch (IllegalArgumentException e) {
            throw new IOException("GitHub returned an invalid update download URL.", e);
        }
        if (!"https".equalsIgnoreCase(downloadUri.getScheme())
                || !"github.com".equalsIgnoreCase(downloadUri.getHost())
                || downloadUri.getUserInfo() != null
                || !downloadUri.getPath().endsWith("/" + expectedAssetName)) {
            throw new IOException("GitHub returned an untrusted update download URL.");
        }
        Matcher checksum = SHA256_PATTERN.matcher((String) updateAsset.get("digest"));
        if (!checksum.matches()) {
            throw new IOException("GitHub's update package is missing a valid SHA-256 digest.");
        }
        long assetSize = ((Number) updateAsset.get("size")).longValue();
        if (assetSize < 1 || assetSize > MAX_UPDATE_SIZE) {
            throw new IOException("GitHub's update package has an unsupported size.");
        }
        return new Release(version, downloadUri, checksum.group(1).toLowerCase(java.util.Locale.ROOT), assetSize);
    }

    static boolean isNewerVersion(String currentVersion, String latestVersion) throws IOException {
        long[] current = versionNumbers(currentVersion);
        long[] latest = versionNumbers(latestVersion);
        return latest[0] > current[0] || latest[0] == current[0] && latest[1] > current[1];
    }

    private static long[] versionNumbers(String version) throws IOException {
        Matcher matcher = VERSION_PATTERN.matcher(version);
        if (!matcher.matches()) {
            throw new IOException("Unsupported version format: " + version);
        }
        try {
            return new long[]{Long.parseLong(matcher.group(1)), Long.parseLong(matcher.group(2))};
        } catch (NumberFormatException e) {
            throw new IOException("Version number is too large: " + version, e);
        }
    }

    private static MessageDigest sha256() throws IOException {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 is not available on this system.", e);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder hex = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            hex.append(String.format("%02x", value & 0xff));
        }
        return hex.toString();
    }

    static final class Release {
        final String version;
        final URI downloadUrl;
        final String sha256;
        final long assetSize;

        Release(String version, URI downloadUrl, String sha256, long assetSize) {
            this.version = version;
            this.downloadUrl = downloadUrl;
            this.sha256 = sha256;
            this.assetSize = assetSize;
        }
    }
}
