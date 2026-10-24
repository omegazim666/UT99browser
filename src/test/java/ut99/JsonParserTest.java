package ut99;

import java.util.List;
import java.util.Map;

public final class JsonParserTest {
    public static void main(String[] args) {
        parsesNestedApiResponse();
        rejectsMalformedJson();
        System.out.println("JsonParser tests passed.");
    }

    private static void parsesNestedApiResponse() {
        String json = "[[{\"hostname\":\"UT \\u03a9 server\",\"numplayers\":2,"
                + "\"player_0\":{\"name\":\"Player\",\"ping\":42}}],"
                + "{\"players\":2,\"total\":1,\"error\":null}]";
        Object parsed = JsonParser.parse(json);
        List<?> response = (List<?>) parsed;
        List<?> servers = (List<?>) response.get(0);
        Map<?, ?> server = (Map<?, ?>) servers.get(0);
        Map<?, ?> player = (Map<?, ?>) server.get("player_0");
        Map<?, ?> metadata = (Map<?, ?>) response.get(1);
        require("UT Ω server".equals(server.get("hostname")), "unicode string");
        require(Long.valueOf(2).equals(server.get("numplayers")), "integer");
        require(Long.valueOf(42).equals(player.get("ping")), "nested player");
        require(metadata.containsKey("error") && metadata.get("error") == null, "null value");

        Object number = JsonParser.parse("[-1, 2.5, 1e2, true, false]");
        require(((List<?>) number).size() == 5, "array values");
    }

    private static void rejectsMalformedJson() {
        expectInvalid("{\"a\":1,\"a\":2}");
        expectInvalid("{\"a\":01}");
        expectInvalid("[1,]");
        expectInvalid("\"unfinished");
        expectInvalid("true false");
    }

    private static void expectInvalid(String json) {
        try {
            JsonParser.parse(json);
            throw new AssertionError("Accepted malformed JSON: " + json);
        } catch (IllegalArgumentException expected) {
            // Expected invalid input.
        }
    }

    private static void require(boolean condition, String name) {
        if (!condition) {
            throw new AssertionError("Failed check: " + name);
        }
    }
}
