package one.xis.seabattle;

import one.xis.context.Service;

@Service
final class RadarService {

    static final double RADAR_RANGE = 945.0;
    private static final ThreadLocal<VisibilityMetrics> VISIBILITY_METRICS = new ThreadLocal<>();

    boolean isVisible(Ship observer, Ship contact, WorldMap worldMap) {
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
            if (observer.position().distanceTo(contact.position()) > RADAR_RANGE) {
                result = "range";
                return false;
            }
            if (LandGeometry.lineIntersectsRadarBlockingLand(observer.position(), contact.position(), worldMap)) {
                result = "land";
                return false;
            }
            return true;
        } finally {
            if (metrics != null) {
                metrics.record(result, System.nanoTime() - started);
            }
        }
    }

    boolean isVisible(ShipSnapshot observer, ShipSnapshot contact, WorldMap worldMap) {
        if (observer.id().equals(contact.id())) {
            return false;
        }
        if (!"active".equals(observer.state()) || !"active".equals(contact.state())) {
            return false;
        }
        Vector2 observerPosition = new Vector2(observer.x(), observer.z());
        Vector2 contactPosition = new Vector2(contact.x(), contact.z());
        if (observerPosition.distanceTo(contactPosition) > RADAR_RANGE) {
            return false;
        }
        return !LandGeometry.lineIntersectsRadarBlockingLand(observerPosition, contactPosition, worldMap);
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
        private long self;
        private long inactive;
        private long range;
        private long land;
        private long visible;

        void record(String result, long elapsedNanos) {
            calls += 1;
            nanos += elapsedNanos;
            switch (result) {
                case "self" -> self += 1;
                case "inactive" -> inactive += 1;
                case "range" -> range += 1;
                case "land" -> land += 1;
                case "visible" -> visible += 1;
                default -> throw new IllegalArgumentException("Unknown visibility result: " + result);
            }
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

        long range() {
            return range;
        }

        long land() {
            return land;
        }

        long visible() {
            return visible;
        }
    }
}
