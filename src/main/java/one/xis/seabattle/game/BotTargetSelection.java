package one.xis.seabattle.game;

import java.util.Map;

/**
 * One strategy instance per game. Selection returns an actual eligible vehicle, or null. goal()
 * determines the navigation destination, including search/last-known-position policies. Neither
 * method needs frontend snapshots. Scheduling and perception are shared services.
 */
@FunctionalInterface
public interface BotTargetSelection {
  Vehicle select(BotTargetSituation situation, long tickId);

  /** New perception candidates at the observer's staggered selection tick. No implicit policy. */
  default void observe(BotTargetSituation situation, long tickId) {}

  /** Confirmed hostile sonar contact when a depth-charge salvo actually starts. */
  default void depthChargeContact(BotTargetSituation situation, Submarine target, long tickId) {}

  /** Optional short-lived detection notices for output. The default has no hunt state. */
  default Map<String, BotSubmarineHunt.Detection> submarineDetections(double now) {
    return Map.of();
  }

  /**
   * Called each round with the currently observed selected target (null after contact loss).
   * Override this for an independent search/jagd policy as well as independent ranking.
   */
  default BotGoal goal(BotTargetSituation situation, Vehicle contact, boolean advanceSearch) {
    if (contact != null) return BotGoal.enemy(contact);
    Vehicle observer = situation.observer();
    Vector2 destination = observer.isScoutPlane() ? observer.position() : situation.patrolCenter();
    return new BotGoal(
        observer.isScoutPlane() ? BotGoal.Kind.NONE : BotGoal.Kind.PATROL,
        null,
        -1,
        destination.x(),
        destination.z());
  }

  static BotTargetSelection standard() {
    return new StandardBotTargetSelection();
  }
}
