package com.cybersammy.citiesarise.core.profile;

public record SettlementProfileId(String value) {
    public SettlementProfileId {
        value = requireIdentifier(value, "value");
    }

    private static String requireIdentifier(String value, String name) {
        if (value == null) {
            throw new NullPointerException(name);
        }

        String normalized = value.trim();

        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }

        if (containsWhitespace(normalized)) {
            throw new IllegalArgumentException(name + " must not contain whitespace");
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
