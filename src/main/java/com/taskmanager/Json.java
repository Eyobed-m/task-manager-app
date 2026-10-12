package com.taskmanager;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class Json {
    private final String text;
    private int position;

    private Json(String text) {
        this.text = text;
    }

    static Object parse(String text) {
        Json parser = new Json(text);
        Object value = parser.readValue();
        parser.skipWhitespace();
        if (parser.position != text.length()) {
            throw parser.error("Unexpected trailing data");
        }
        return value;
    }

    private Object readValue() {
        skipWhitespace();
        if (position >= text.length()) throw error("Unexpected end of input");
        return switch (text.charAt(position)) {
            case '{' -> readObject();
            case '[' -> readArray();
            case '"' -> readString();
            case 't' -> readLiteral("true", Boolean.TRUE);
            case 'f' -> readLiteral("false", Boolean.FALSE);
            case 'n' -> readLiteral("null", null);
            default -> readNumber();
        };
    }

    private Map<String, Object> readObject() {
        position++;
        Map<String, Object> object = new LinkedHashMap<>();
        skipWhitespace();
        if (take('}')) return object;
        do {
            skipWhitespace();
            if (position >= text.length() || text.charAt(position) != '"') {
                throw error("Expected an object key");
            }
            String key = readString();
            skipWhitespace();
            require(':');
            object.put(key, readValue());
            skipWhitespace();
            if (take('}')) return object;
            require(',');
        } while (true);
    }

    private List<Object> readArray() {
        position++;
        List<Object> values = new ArrayList<>();
        skipWhitespace();
        if (take(']')) return values;
        do {
            values.add(readValue());
            skipWhitespace();
            if (take(']')) return values;
            require(',');
        } while (true);
    }

    private String readString() {
        require('"');
        StringBuilder value = new StringBuilder();
        while (position < text.length()) {
            char character = text.charAt(position++);
            if (character == '"') return value.toString();
            if (character < 0x20) throw error("Unescaped control character");
            if (character != '\\') {
                value.append(character);
                continue;
            }
            if (position >= text.length()) throw error("Incomplete escape");
            char escaped = text.charAt(position++);
            switch (escaped) {
                case '"', '\\', '/' -> value.append(escaped);
                case 'b' -> value.append('\b');
                case 'f' -> value.append('\f');
                case 'n' -> value.append('\n');
                case 'r' -> value.append('\r');
                case 't' -> value.append('\t');
                case 'u' -> value.append(readUnicodeEscape());
                default -> throw error("Invalid escape");
            }
        }
        throw error("Unterminated string");
    }

    private char readUnicodeEscape() {
        if (position + 4 > text.length()) throw error("Incomplete unicode escape");
        try {
            char decoded = (char) Integer.parseInt(text.substring(position, position + 4), 16);
            position += 4;
            return decoded;
        } catch (NumberFormatException exception) {
            throw error("Invalid unicode escape");
        }
    }

    private Object readNumber() {
        int start = position;
        if (take('-') && position == text.length()) throw error("Incomplete number");
        if (take('0')) {
            if (position < text.length() && Character.isDigit(text.charAt(position))) {
                throw error("Leading zero in number");
            }
        } else {
            readDigits();
        }
        if (take('.')) readDigits();
        if (take('e') || take('E')) {
            if (!take('+')) take('-');
            readDigits();
        }
        if (start == position) throw error("Expected a JSON value");
        try {
            String number = text.substring(start, position);
            if (number.indexOf('.') < 0 && number.indexOf('e') < 0 && number.indexOf('E') < 0) {
                return Long.valueOf(number);
            }
            return Double.valueOf(number);
        } catch (NumberFormatException exception) {
            throw error("Invalid number");
        }
    }

    private void readDigits() {
        int start = position;
        while (position < text.length() && Character.isDigit(text.charAt(position))) position++;
        if (start == position) throw error("Expected a digit");
    }

    private Object readLiteral(String literal, Object value) {
        if (!text.startsWith(literal, position)) throw error("Invalid JSON value");
        position += literal.length();
        return value;
    }

    private boolean take(char expected) {
        if (position < text.length() && text.charAt(position) == expected) {
            position++;
            return true;
        }
        return false;
    }

    private void require(char expected) {
        if (!take(expected)) throw error("Expected '" + expected + "'");
    }

    private void skipWhitespace() {
        while (position < text.length()) {
            char character = text.charAt(position);
            if (character != ' ' && character != '\t' && character != '\n' && character != '\r') return;
            position++;
        }
    }

    private IllegalArgumentException error(String message) {
        return new IllegalArgumentException(message + " at character " + position);
    }
}
