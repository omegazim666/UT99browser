package ut99;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class JsonParser {
    private final String input;
    private int position;

    private JsonParser(String input) {
        this.input = input;
    }

    static Object parse(String input) {
        JsonParser parser = new JsonParser(input);
        Object value = parser.readValue();
        parser.skipWhitespace();
        if (parser.position != input.length()) {
            throw parser.error("Unexpected content after JSON value");
        }
        return value;
    }

    private Object readValue() {
        skipWhitespace();
        if (position >= input.length()) {
            throw error("Unexpected end of JSON");
        }
        char next = input.charAt(position);
        if (next == '{') {
            return readObject();
        }
        if (next == '[') {
            return readArray();
        }
        if (next == '"') {
            return readString();
        }
        if (next == 't') {
            return readLiteral("true", Boolean.TRUE);
        }
        if (next == 'f') {
            return readLiteral("false", Boolean.FALSE);
        }
        if (next == 'n') {
            return readLiteral("null", null);
        }
        if (next == '-' || Character.isDigit(next)) {
            return readNumber();
        }
        throw error("Invalid JSON value");
    }

    private Map<String, Object> readObject() {
        Map<String, Object> object = new LinkedHashMap<>();
        position++;
        skipWhitespace();
        if (consume('}')) {
            return object;
        }
        while (true) {
            skipWhitespace();
            if (position >= input.length() || input.charAt(position) != '"') {
                throw error("Expected an object key");
            }
            String key = readString();
            skipWhitespace();
            require(':');
            if (object.containsKey(key)) {
                throw error("Duplicate object key: " + key);
            }
            object.put(key, readValue());
            skipWhitespace();
            if (consume('}')) {
                return object;
            }
            require(',');
        }
    }

    private List<Object> readArray() {
        List<Object> array = new ArrayList<>();
        position++;
        skipWhitespace();
        if (consume(']')) {
            return array;
        }
        while (true) {
            array.add(readValue());
            skipWhitespace();
            if (consume(']')) {
                return array;
            }
            require(',');
        }
    }

    private String readString() {
        require('"');
        StringBuilder result = new StringBuilder();
        while (position < input.length()) {
            char character = input.charAt(position++);
            if (character == '"') {
                return result.toString();
            }
            if (character == '\\') {
                if (position >= input.length()) {
                    throw error("Incomplete string escape");
                }
                char escape = input.charAt(position++);
                switch (escape) {
                    case '"': result.append('"'); break;
                    case '\\': result.append('\\'); break;
                    case '/': result.append('/'); break;
                    case 'b': result.append('\b'); break;
                    case 'f': result.append('\f'); break;
                    case 'n': result.append('\n'); break;
                    case 'r': result.append('\r'); break;
                    case 't': result.append('\t'); break;
                    case 'u': result.append(readUnicodeEscape()); break;
                    default: throw error("Invalid string escape");
                }
            } else {
                if (character < 0x20) {
                    throw error("Unescaped control character in string");
                }
                result.append(character);
            }
        }
        throw error("Unterminated string");
    }

    private char readUnicodeEscape() {
        if (position + 4 > input.length()) {
            throw error("Incomplete Unicode escape");
        }
        String hex = input.substring(position, position + 4);
        position += 4;
        try {
            return (char) Integer.parseInt(hex, 16);
        } catch (NumberFormatException e) {
            throw error("Invalid Unicode escape");
        }
    }

    private Object readNumber() {
        int start = position;
        consume('-');
        if (consume('0')) {
            if (position < input.length() && Character.isDigit(input.charAt(position))) {
                throw error("Leading zero in number");
            }
        } else {
            readDigits();
        }
        if (consume('.')) {
            readDigits();
        }
        if (position < input.length() && (input.charAt(position) == 'e' || input.charAt(position) == 'E')) {
            position++;
            if (!consume('+')) {
                consume('-');
            }
            readDigits();
        }
        String number = input.substring(start, position);
        try {
            if (number.contains(".") || number.contains("e") || number.contains("E")) {
                return Double.valueOf(number);
            }
            return Long.valueOf(number);
        } catch (NumberFormatException e) {
            throw error("Invalid number");
        }
    }

    private void readDigits() {
        int start = position;
        while (position < input.length() && Character.isDigit(input.charAt(position))) {
            position++;
        }
        if (start == position) {
            throw error("Expected a digit");
        }
    }

    private Object readLiteral(String literal, Object value) {
        if (!input.startsWith(literal, position)) {
            throw error("Invalid JSON literal");
        }
        position += literal.length();
        return value;
    }

    private void skipWhitespace() {
        while (position < input.length()) {
            char character = input.charAt(position);
            if (character != ' ' && character != '\n' && character != '\r' && character != '\t') {
                return;
            }
            position++;
        }
    }

    private boolean consume(char expected) {
        if (position < input.length() && input.charAt(position) == expected) {
            position++;
            return true;
        }
        return false;
    }

    private void require(char expected) {
        if (!consume(expected)) {
            throw error("Expected '" + expected + "'");
        }
    }

    private IllegalArgumentException error(String message) {
        return new IllegalArgumentException(message + " at character " + position);
    }
}
