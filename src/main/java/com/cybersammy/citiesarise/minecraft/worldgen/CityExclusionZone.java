package com.cybersammy.citiesarise.minecraft.worldgen;

public record CityExclusionZone(int x, int z, int radius) {
        public CityExclusionZone { if (radius < 0 || radius > 60000000) throw new IllegalArgumentException("Invalid city radius"); }
        public boolean excludes(int otherX, int otherZ, int otherRadius) {
            long dx = (long)x - otherX, dz = (long)z - otherZ;
            long distance = Math.max(radius, otherRadius);
            return dx == 0 && dz == 0 || dx * dx + dz * dz < distance * distance;
        }
    }
