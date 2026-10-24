package ut99;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class UpdateChecker {
    private static final URI LATEST_RELEASE_API = URI.create(
            "https://api.github.com/repos/omegazim666/UT99browser/releases/latest");
    private static final Pattern VERSION_PATTERN = Pattern.compile("(?i)^B(\\d+)\\.(\\d+)$");

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
        Object url = fields.get("html_url");
        if (!(tag instanceof String) || !(url instanceof String)) {
            throw new IOException("GitHub's latest release is missing its version or URL.");
        }
        String version = (String) tag;
        if (!VERSION_PATTERN.matcher(version).matches()) {
            throw new IOException("GitHub returned an unsupported release version: " + version);
        }

        URI releaseUri;
        try {
            releaseUri = URI.create((String) url);
        } catch (IllegalArgumentException e) {
            throw new IOException("GitHub returned an invalid release URL.", e);
        }
        if (!"https".equalsIgnoreCase(releaseUri.getScheme())
                || !"github.com".equalsIgnoreCase(releaseUri.getHost())
                || releaseUri.getUserInfo() != null) {
            throw new IOException("GitHub returned an untrusted release URL.");
        }
        return new Release(version, releaseUri);
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

    static final class Release {
        final String version;
        final URI url;

        Release(String version, URI url) {
            this.version = version;
            this.url = url;
        }
    }
}
