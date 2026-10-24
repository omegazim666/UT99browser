package ut99;

import java.util.Map;

final class ServerEntry {
    final Map<String, Object> fields;

    ServerEntry(Map<String, Object> fields) {
        this.fields = fields;
    }

    String value(String key, String fallback) {
        Object value = fields.get(key);
        return value == null ? fallback : String.valueOf(value);
    }

    String address() {
        return value("ip", "?") + ":" + value("hostport", "?");
    }

    String normalizedAddress() {
        return normalizedAddress(value("ip", ""), value("hostport", ""));
    }

    static String normalizedAddress(String ip, String port) {
        String[] octets = ip.trim().split("\\.", -1);
        if (octets.length == 4 && port.trim().matches("\\d{1,5}")) {
            StringBuilder normalizedIp = new StringBuilder();
            for (String octet : octets) {
                if (!octet.matches("\\d{1,3}")) {
                    return (ip.trim() + ":" + port.trim()).toLowerCase(java.util.Locale.ROOT);
                }
                int value = Integer.parseInt(octet);
                if (value > 255) {
                    return (ip.trim() + ":" + port.trim()).toLowerCase(java.util.Locale.ROOT);
                }
                if (normalizedIp.length() > 0) {
                    normalizedIp.append('.');
                }
                normalizedIp.append(value);
            }
            int numericPort = Integer.parseInt(port.trim());
            if (numericPort >= 1 && numericPort <= 65535) {
                return normalizedIp + ":" + numericPort;
            }
        }
        return (ip.trim() + ":" + port.trim()).toLowerCase(java.util.Locale.ROOT);
    }
}
