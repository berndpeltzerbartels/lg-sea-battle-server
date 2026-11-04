package one.xis.seabattle.game;

/** Minimal alternative ranking, independent of navigation and perception. */
public final class NearestTargetSelection implements BotTargetSelection {
  @Override
  public Vehicle select(BotTargetSituation situation, long tickId) {
    Vehicle observer = situation.observer();
    Vehicle nearest = null;
    double best = Double.POSITIVE_INFINITY;
    for (Vehicle candidate : situation.candidates()) {
      double dx = candidate.position().x() - observer.position().x(),
          dz = candidate.position().z() - observer.position().z();
      double distanceSquared = dx * dx + dz * dz;
      if (distanceSquared < best) {
        best = distanceSquared;
        nearest = candidate;
      }
    }
    return nearest;
  }
}
