package ut99;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

final class GameServerQuery {
    private static final Pattern IPV4 = Pattern.compile("^(\\d{1,3}\\.){3}\\d{1,3}$");
    private static final byte[][] STATUS_QUERIES = {
            "\\status\\xserverquery".getBytes(StandardCharsets.US_ASCII),
            "\\status\\".getBytes(StandardCharsets.US_ASCII)
    };

    static final class Result {
        final Map<String, String> fields;
        final long roundTripMillis;

        Result(Map<String, String> fields, long roundTripMillis) {
            this.fields = fields;
            this.roundTripMillis = roundTripMillis;
        }
    }

    private GameServerQuery() {
    }

    static Result query(ServerEntry server, int timeoutMillis) throws IOException {
        String ip = server.value("ip", "");
        if (!IPV4.matcher(ip).matches()) {
            throw new IOException("The server has an invalid IPv4 address: " + ip);
        }
        String[] octets = ip.split("\\.");
        byte[] addressBytes = new byte[octets.length];
        for (int i = 0; i < octets.length; i++) {
            int octet = Integer.parseInt(octets[i]);
            if (octet > 255) {
                throw new IOException("The server has an invalid IPv4 address: " + ip);
            }
            addressBytes[i] = (byte) octet;
        }
        int queryPort;
        try {
            String advertisedQueryPort = server.value("queryport", "");
            queryPort = Integer.parseInt(advertisedQueryPort.isEmpty()
                    ? String.valueOf(Integer.parseInt(server.value("hostport", "")) + 1)
                    : advertisedQueryPort);
        } catch (NumberFormatException e) {
            throw new IOException("The server has an invalid query port: "
                    + server.value("queryport", server.value("hostport", "")));
        }
        if (queryPort < 1 || queryPort > 65535) {
            throw new IOException("The server has an invalid query port: " + queryPort);
        }
        if (timeoutMillis < 1) {
            throw new IOException("The query timeout must be greater than zero.");
        }

        InetAddress address = InetAddress.getByAddress(addressBytes);
        IOException lastFailure = null;
        long start = System.nanoTime();
        long timeoutNanos = TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        for (int queryIndex = 0; queryIndex < STATUS_QUERIES.length; queryIndex++) {
            byte[] statusQuery = STATUS_QUERIES[queryIndex];
            long deadline = start + timeoutNanos * (queryIndex + 1) / STATUS_QUERIES.length;
            try (DatagramSocket socket = new DatagramSocket()) {
                socket.connect(new InetSocketAddress(address, queryPort));
                socket.send(new DatagramPacket(statusQuery, statusQuery.length));
                byte[] buffer = new byte[8192];
                Map<String, String> fields = new LinkedHashMap<>();
                while (true) {
                    long remainingNanos = deadline - System.nanoTime();
                    if (remainingNanos <= 0) {
                        throw new SocketTimeoutException();
                    }
                    socket.setSoTimeout((int) Math.max(1,
                            TimeUnit.NANOSECONDS.toMillis(remainingNanos)));
                    DatagramPacket response = new DatagramPacket(buffer, buffer.length);
                    socket.receive(response);
                    Map<String, String> packetFields = parseResponse(new String(
                            response.getData(), response.getOffset(), response.getLength(),
                            StandardCharsets.ISO_8859_1));
                    fields.putAll(packetFields);
                    if (packetFields.containsKey("final")) {
                        fields.remove("final");
                        if (fields.isEmpty()) {
                            throw new IOException("The game server returned an empty status response.");
                        }
                        return new Result(fields, (System.nanoTime() - start) / 1_000_000);
                    }
                }
            } catch (SocketTimeoutException e) {
                lastFailure = e;
            }
        }
        throw new IOException("The game server did not answer its status query at "
                + ip + ":" + queryPort + " using either supported query format.", lastFailure);
    }

    static Map<String, String> parseResponse(String response) throws IOException {
        String trimmed = response.replace("\0", "").trim();
        if (trimmed.startsWith("\\")) {
            trimmed = trimmed.substring(1);
        }
        String[] pairs = trimmed.split("\\\\", -1);
        if (pairs.length % 2 != 0 && "final".equalsIgnoreCase(pairs[pairs.length - 1])) {
            pairs = java.util.Arrays.copyOf(pairs, pairs.length + 1);
        }
        if (pairs.length < 2 || pairs.length % 2 != 0) {
            throw new IOException("The game server returned a malformed status response.");
        }
        Map<String, String> fields = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            if (!pairs[i].isEmpty()) {
                fields.put(pairs[i].toLowerCase(java.util.Locale.ROOT), pairs[i + 1]);
            }
        }
        if (fields.isEmpty()) {
            throw new IOException("The game server returned an empty status response.");
        }
        return fields;
    }
}
