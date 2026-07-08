package com.cybersammy.citiesarise.core.transform;

final class StableHash {
    private static final long OFFSET_BASIS = 0xcbf29ce484222325L;
    private static final long PRIME = 0x100000001b3L;

    private StableHash() {
    }

    static long seeded(long seed) {
        return mixLong(OFFSET_BASIS, seed);
    }

    static long mixString(long hash, String value) {
        long mixedHash = hash;

        for (int index = 0; index < value.length(); index++) {
            mixedHash = mixChar(mixedHash, value.charAt(index));
        }

        return mixedHash;
    }

    private static long mixChar(long hash, char value) {
        long mixedHash = hash ^ value;
        return mixedHash * PRIME;
    }

    private static long mixLong(long hash, long value) {
        long mixedHash = hash;

        for (int shift = 0; shift < Long.SIZE; shift += Byte.SIZE) {
            mixedHash = mixByte(mixedHash, value, shift);
        }

        return mixedHash;
    }

    private static long mixByte(long hash, long value, int shift) {
        long mixedHash = hash ^ ((value >>> shift) & 0xFFL);
        return mixedHash * PRIME;
    }
}
