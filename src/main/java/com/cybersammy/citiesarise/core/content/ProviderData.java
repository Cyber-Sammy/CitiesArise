package com.cybersammy.citiesarise.core.content;

import java.util.Map;

/** Bounded immutable annotations interpreted only by the owning content adapter. */
public final class ProviderData {
    private ProviderData() { }

    public static Map<String, String> copy(Map<String, String> data) {
        var result = Map.copyOf(data);
        long size = 0;
        for (var entry : result.entrySet()) {
            if (!entry.getKey().matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))
                throw new IllegalArgumentException("Invalid provider data key");
            size += entry.getKey().length() + entry.getValue().length();
        }
        if (result.size() > 16 || size > 16384)
            throw new IllegalArgumentException("Provider data exceeds cell budget");
        return result;
    }
}
