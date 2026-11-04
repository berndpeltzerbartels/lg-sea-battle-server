package one.xis.seabattle.game;

import one.xis.context.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
final class RadarService {

    static final double RADAR_RANGE = 945.0;
    static final double HUMAN_TARGET_RANGE = 12000.0;
    static final double TORPEDO_RANGE = 1300.0;
    private static final double MAX_CANDIDATE_RANGE = Math.max(RADAR_RANGE, HUMAN_TARGET_RANGE);
    private static final ThreadLocal<VisibilityMetrics> VISIBILITY_METRICS = new ThreadLocal<>();

    boolean isVisible(Vehicle observer, Vehicle contact, WorldMap worldMap) {
        return isVisible(observer, contact, worldMap, RADAR_RANGE);
    }

    boolean isVisible(Vehicle observer, Vehicle contact, WorldMap worldMap, double range) {
        VisibilityMetrics metrics = VISIBILITY_METRICS.get();
        long started = metrics == null ? 0 : System.nanoTime();
        String result = "visible";
        try {
            if (observer.id().equals(contact.id())) {
                result = "self";
                return false;
            }
            if (!"active".equals(observer.state()) || !"active".equals(contact.state())) {
                result = "inactive";
                return false;
            }
            if (!canObserveByDepth(observer, contact)) {
                result = "depth";
                return false;
            }
            if (observer.position().distanceTo(contact.position()) > range) {
                result = "range";
                return false;
            }
            return true;
        } finally {
            if (metrics != null) {
                metrics.record(result, System.nanoTime() - started);
            }
        }
    }

    private boolean canObserveByDepth(Vehicle observer, Vehicle contact) {
        if (observer.isFullySubmerged()) {
            return false;
        }
        if (contact.isFullySubmerged()) {
            return false;
        }
        if (contact.isAtPeriscopeDepth()) {
            return false;
        }
        return true;
    }

    VisibilityCache visibilityCache(WorldMap worldMap, List<Vehicle> activeShips) {
        return new VisibilityCache(this, worldMap, activeShips);
    }

    double range() {
        return RADAR_RANGE;
    }

    static void collectVisibilityMetrics(VisibilityMetrics metrics) {
        VISIBILITY_METRICS.set(metrics);
    }

    static void clearVisibilityMetrics() {
        VISIBILITY_METRICS.remove();
    }

    static final class VisibilityMetrics {
        private long calls;
        private long nanos;
        private long cacheHits;
        private long cacheMisses;
        private long self;
        private long inactive;
        private long depth;
        private long range;
        private long visible;

        void record(String result, long elapsedNanos) {
            calls += 1;
            nanos += elapsedNanos;
            switch (result) {
                case "self" -> self += 1;
                case "inactive" -> inactive += 1;
                case "depth" -> depth += 1;
                case "range" -> range += 1;
                case "visible" -> visible += 1;
                default -> throw new IllegalArgumentException("Unknown visibility result: " + result);
            }
        }

        void recordCacheHit(long elapsedNanos) {
            calls += 1;
            nanos += elapsedNanos;
            cacheHits += 1;
        }

        void recordCacheMiss() {
            cacheMisses += 1;
        }

        long calls() {
            return calls;
        }

        double millis() {
            return nanos / 1_000_000.0;
        }

        long self() {
            return self;
        }

        long inactive() {
            return inactive;
        }

        long cacheHits() {
            return cacheHits;
        }

        long cacheMisses() {
            return cacheMisses;
        }

        long range() {
            return range;
        }

        long depth() {
            return depth;
        }

        long visible() {
            return visible;
        }
    }

    static final class VisibilityCache {
        private final RadarService radarService;
        private final WorldMap worldMap;
        private final SpatialIndex<Vehicle> shipsByRadarCell;
        private final List<Vehicle> vehicles;
        private final Map<VisibilityKey, Boolean> visibleByPair = new HashMap<>();

        private VisibilityCache(RadarService radarService, WorldMap worldMap, List<Vehicle> activeShips) {
            this.radarService = radarService;
            this.vehicles = activeShips;
            this.worldMap = worldMap;
            this.shipsByRadarCell = SpatialIndex.from(activeShips, Vehicle::position, MAX_CANDIDATE_RANGE);
        }

        List<Vehicle> vehicles() { return vehicles; }

        List<Vehicle> candidates(Vehicle observer) {
            return candidates(observer, RADAR_RANGE);
        }

        List<Vehicle> candidates(Vehicle observer, double range) {
            return shipsByRadarCell.near(observer.position(), range);
        }

        boolean isVisible(Vehicle observer, Vehicle contact) {
            return isVisible(observer, contact, RADAR_RANGE);
        }

        boolean isVisible(Vehicle observer, Vehicle contact, double range) {
            long started = System.nanoTime();
            VisibilityKey key = VisibilityKey.of(observer.id(), contact.id(), range);
            Boolean cached = visibleByPair.get(key);
            if (cached != null) {
                VisibilityMetrics metrics = VISIBILITY_METRICS.get();
                if (metrics != null) {
                    metrics.recordCacheHit(System.nanoTime() - started);
                }
                return cached;
            }
            VisibilityMetrics metrics = VISIBILITY_METRICS.get();
            if (metrics != null) {
                metrics.recordCacheMiss();
            }
            boolean visible = radarService.isVisible(observer, contact, worldMap, range);
            visibleByPair.put(key, visible);
            return visible;
        }
    }

    private record VisibilityKey(String left, String right, double range) {

        static VisibilityKey of(String first, String second, double range) {
            return new VisibilityKey(first, second, range);
        }
    }
}
