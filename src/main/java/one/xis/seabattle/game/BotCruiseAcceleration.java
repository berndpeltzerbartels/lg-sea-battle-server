package one.xis.seabattle.game;

/** A short gear ramp from rest, exclusively for ordinary navigation. */
final class BotCruiseAcceleration {
    private double startedAt = Double.NaN;

    int order(int requested, double speed, double now, boolean ordinary) {
        if (!ordinary || requested <= 2) {
            startedAt = Double.NaN;
            return requested;
        }
        if (!Double.isNaN(startedAt) && now - startedAt >= 3 - 1e-6) startedAt = Double.NaN;
        if (Double.isNaN(startedAt)) {
            if (speed > .5) return requested; // Already underway: do not introduce a slowdown.
            startedAt = now;
        }
        double elapsed = now - startedAt + 1e-6;
        int limit = elapsed < 1 ? 4 : elapsed < 2 ? 5 : elapsed < 3 ? 7 : 8;
        return Math.min(requested, limit);
    }
}
