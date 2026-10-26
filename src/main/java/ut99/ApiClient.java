package ut99;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ApiClient {
    private static final String API_ROOT = "https://master.333networks.com/json/ut";
    private static final long QUERY_COOLDOWN_MILLIS = Duration.ofSeconds(7 * 60 + 30).toMillis();
    private static final Pattern IPV4_MAPPED_PREFIX = Pattern.compile("(?i)^::ffff:(\\d+\\.\\d+\\.\\d+\\.\\d+)$");
    private static final Pattern IPV4 = Pattern.compile("^(\\d{1,3}\\.){3}\\d{1,3}$");
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private final Map<String, Long> lastRequestTimes = new HashMap<>();
    private final Map<String, Object> responseCache = new HashMap<>();

    static final class ServerPage {
        final List<ServerEntry> servers;
        final int total;
        final int players;

        ServerPage(List<ServerEntry> servers, int total, int players) {
            this.servers = servers;
            this.total = total;
            this.players = players;
        }
    }

    ServerPage fetchServers(String query, int resultLimit) throws Exception {
        return fetchServers(query, resultLimit, false);
    }

    ServerPage fetchServers(String query, int resultLimit, boolean forceRefresh) throws Exception {
        StringBuilder url = new StringBuilder(API_ROOT)
                .append("?r=").append(resultLimit)
                .append("&p=1");
        if (!query.isEmpty()) {
            url.append("&q=").append(URLEncoder.encode(query, StandardCharsets.UTF_8.name()));
        }
        Object response = request(url.toString(), forceRefresh);
        if (!(response instanceof List) || ((List<?>) response).size() < 2) {
            throw apiError(response);
        }

        List<?> topLevel = (List<?>) response;
        if (!(topLevel.get(0) instanceof List) || !(topLevel.get(1) instanceof Map)) {
            throw apiError(response);
        }
        List<ServerEntry> servers = new ArrayList<>();
        for (Object item : (List<?>) topLevel.get(0)) {
            if (!(item instanceof Map)) {
                throw new IOException("The API returned a malformed server entry.");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> fields = (Map<String, Object>) item;
            servers.add(new ServerEntry(fields));
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) topLevel.get(1);
        int total = number(metadata.get("total"), servers.size());
        int players = number(metadata.get("players"), 0);
        return new ServerPage(servers, total, players);
    }

    Map<String, Object> fetchDetails(ServerEntry server) throws Exception {
        String ip = server.value("ip", "");
        Matcher mappedAddress = IPV4_MAPPED_PREFIX.matcher(ip);
        if (mappedAddress.matches()) {
            ip = mappedAddress.group(1);
        }
        if (!IPV4.matcher(ip).matches()) {
            throw new IOException("The 333networks detail endpoint requires an IPv4 address. "
                    + "This server uses: " + server.value("ip", "unknown"));
        }
        for (String octet : ip.split("\\.")) {
            if (Integer.parseInt(octet) > 255) {
                throw new IOException("The server has an invalid IPv4 address: " + ip);
            }
        }
        String port = server.value("hostport", "");
        if (!port.matches("\\d{1,5}") || Integer.parseInt(port) > 65535) {
            throw new IOException("The server has an invalid port: " + port);
        }

        String url = API_ROOT + "/" + ip + ":" + port;
        Object response = request(url);
        if (!(response instanceof Map)) {
            throw apiError(response);
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> details = (Map<String, Object>) response;
        if (isApiError(details)) {
            throw apiError(response);
        }
        return details;
    }

    private synchronized Object request(String url) throws Exception {
        return request(url, false);
    }

    private synchronized Object request(String url, boolean forceRefresh) throws Exception {
        long now = System.currentTimeMillis();
        Long lastRequest = lastRequestTimes.get(url);
        Object cachedResponse = responseCache.get(url);
        if (!forceRefresh && lastRequest != null && now - lastRequest < QUERY_COOLDOWN_MILLIS) {
            if (cachedResponse != null) {
                return cachedResponse;
            }
            long remaining = QUERY_COOLDOWN_MILLIS - (now - lastRequest);
            long minutes = (remaining + 59_999) / 60_000;
            throw new IOException("This exact API request was already made recently. "
                    + "Try again in about " + minutes + " minute(s).");
        }

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json")
                .header("User-Agent", "UT99ServerBrowser/1.0 (333networks JSON API)")
                .GET()
                .build();
        lastRequestTimes.put(url, now);
        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("The API request was interrupted.", e);
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("333networks returned HTTP " + response.statusCode() + ".");
        }

        Object parsed;
        try {
            parsed = JsonParser.parse(response.body());
        } catch (IllegalArgumentException e) {
            throw new IOException("333networks returned invalid JSON.", e);
        }
        if (parsed instanceof Map && isApiError(castMap(parsed))) {
            throw apiError(parsed);
        }
        responseCache.put(url, parsed);
        return parsed;
    }

    private static IOException apiError(Object response) {
        if (response instanceof Map) {
            Map<String, Object> error = castMap(response);
            if (isApiError(error)) {
                return new IOException("333networks API error (" + error.getOrDefault("in", "unknown")
                        + "): " + error.getOrDefault("message", error.getOrDefault("error", "request failed")));
            }
        }
        return new IOException("333networks returned a response in an unexpected format.");
    }

    private static boolean isApiError(Map<String, Object> response) {
        return response.containsKey("error") && response.get("error") != null
                && !Boolean.FALSE.equals(response.get("error"))
                && !"0".equals(String.valueOf(response.get("error")));
    }

    private static Map<String, Object> castMap(Object value) {
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) value;
        return map;
    }

    private static int number(Object value, int fallback) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
