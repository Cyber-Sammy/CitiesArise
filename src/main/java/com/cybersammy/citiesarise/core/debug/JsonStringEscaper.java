package com.cybersammy.citiesarise.core.debug;

public final class JsonStringEscaper {
    private JsonStringEscaper() {
    }

    public static void appendQuoted(StringBuilder output, String value) {
        output.append('"');
        appendEscaped(output, value);
        output.append('"');
    }

    private static void appendEscaped(StringBuilder output, String value) {
        for (int index = 0; index < value.length(); index++) {
            appendEscaped(output, value.charAt(index));
        }
    }

    private static void appendEscaped(StringBuilder output, char value) {
        if (value == '"') {
            output.append("\\\"");
            return;
        }

        if (value == '\\') {
            output.append("\\\\");
            return;
        }

        if (value == '\n') {
            output.append("\\n");
            return;
        }

        if (value == '\r') {
            output.append("\\r");
            return;
        }

        if (value == '\t') {
            output.append("\\t");
            return;
        }

        if (value < ' ') {
            output.append(String.format("\\u%04x", (int) value));
            return;
        }

        output.append(value);
    }
}
