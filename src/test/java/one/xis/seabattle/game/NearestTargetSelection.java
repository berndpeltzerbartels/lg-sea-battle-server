package one.xis.seabattle.game;

/** Minimal alternative ranking, independent of navigation and perception. */
public final class NearestTargetSelection implements BotTargetSelection {
    @Override
    public String select(BotTargetSituation situation, long tickId) {
        ShipSnapshot observer = situation.observer();
        ShipSnapshot nearest = null;
        double best = Double.POSITIVE_INFINITY;
        for (ShipSnapshot candidate : situation.candidates()) {
            double dx = candidate.x() - observer.x(), dz = candidate.z() - observer.z();
            double distanceSquared = dx * dx + dz * dz;
            if (distanceSquared < best) {
                best = distanceSquared;
                nearest = candidate;
            }
        }
        return nearest == null ? null : nearest.id();
    }
}
