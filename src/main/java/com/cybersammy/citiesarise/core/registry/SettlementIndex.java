package com.cybersammy.citiesarise.core.registry;

import java.nio.charset.StandardCharsets;
import java.util.*;

/** Content-independent metadata and placement progress. Owned by one execution thread. */
public final class SettlementIndex {
    private final Map<UUID, Entry> entries = new HashMap<>();

    public record Metadata(String dimension, String profile, String planId, int centerX, int centerZ,
                           int minX, int minZ, int maxX, int maxZ, Set<Long> placementChunks) {
        public Metadata {
            Objects.requireNonNull(dimension); Objects.requireNonNull(profile); Objects.requireNonNull(planId);
            placementChunks = Set.copyOf(placementChunks);
            if (dimension.isBlank() || profile.isBlank() || planId.isBlank() || minX > maxX || minZ > maxZ
                    || placementChunks.isEmpty()) throw new IllegalArgumentException("Invalid settlement metadata");
        }
        public UUID id() {
            // Content/profile changes must not change the identity of an existing physical settlement.
            return UUID.nameUUIDFromBytes((dimension + ":" + centerX + ":" + centerZ).getBytes(StandardCharsets.UTF_8));
        }
    }

    public record Entry(Metadata metadata, Set<Long> placedChunks, long placementNanos) {
        public Entry {
            Objects.requireNonNull(metadata);
            placedChunks = Set.copyOf(placedChunks);
            if (!metadata.placementChunks().containsAll(placedChunks) || placementNanos < 0)
                throw new IllegalArgumentException("Invalid placement progress");
        }
        public String state() {
            if (placedChunks.isEmpty()) return "START_KNOWN";
            return placedChunks.containsAll(metadata.placementChunks()) ? "PLACEMENT_COMPLETE" : "PARTIALLY_PLACED";
        }
    }

    public boolean observe(Metadata metadata) {
        return entries.putIfAbsent(metadata.id(), new Entry(metadata, Set.of(), 0)) == null;
    }

    public boolean placed(Metadata metadata, long chunk, long nanos) {
        if (!metadata.placementChunks().contains(chunk) || nanos < 0) throw new IllegalArgumentException("Invalid chunk report");
        Entry previous = entries.get(metadata.id());
        if (previous != null && !previous.metadata().equals(metadata))
            throw new IllegalArgumentException("Conflicting settlement metadata for " + metadata.id());
        Set<Long> completed = new HashSet<>(previous == null ? Set.of() : previous.placedChunks());
        if (!completed.add(chunk)) return false;
        entries.put(metadata.id(), new Entry(metadata, completed,
                Math.addExact(previous == null ? 0 : previous.placementNanos(), nanos)));
        return true;
    }

    public Optional<Entry> nearest(int x, int z) {
        return entries.values().stream().min(Comparator
                .comparingDouble((Entry e) -> Math.hypot((double)e.metadata().centerX() - x, (double)e.metadata().centerZ() - z))
                .thenComparing(e -> e.metadata().id()));
    }

    public int size() { return entries.size(); }

    public Collection<Entry> entries() { return List.copyOf(entries.values()); }

    public void restore(Entry entry) {
        if (entries.putIfAbsent(entry.metadata().id(), entry) != null)
            throw new IllegalArgumentException("Duplicate settlement id");
    }
}
