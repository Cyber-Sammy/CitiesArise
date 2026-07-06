package com.cybersammy.citiesarise.core.model;

final class PlanText {
    private PlanText() {
    }

    static String requireIdentifier(String value, String name) {
        String normalized = requireText(value, name);

        if (containsWhitespace(normalized)) {
            throw new IllegalArgumentException(name + " must not contain whitespace");
        }

        return normalized;
    }

    static String requireIdentifierPart(String value, String name) {
        String normalized = requireIdentifier(value, name);

        if (normalized.contains("/")) {
            throw new IllegalArgumentException(name + " must not contain '/'");
        }

        return normalized;
    }

    private static String requireText(String value, String name) {
        if (value == null) {
            throw new NullPointerException(name);
        }

        String normalized = value.trim();

        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }

        return normalized;
    }

    private static boolean containsWhitespace(String value) {
        for (int index = 0; index < value.length(); index++) {
            if (Character.isWhitespace(value.charAt(index))) {
                return true;
            }
        }

        return false;
    }
}
