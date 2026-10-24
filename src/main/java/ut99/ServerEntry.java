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
}
