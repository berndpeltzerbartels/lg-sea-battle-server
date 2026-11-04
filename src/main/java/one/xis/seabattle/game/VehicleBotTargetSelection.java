package one.xis.seabattle.game;

import java.util.Map;

/** One vehicle type's target and destination policy. One instance per game. */
@FunctionalInterface
public interface VehicleBotTargetSelection<V extends Vehicle> {
  Vehicle select(V vehicle, BotTargetSituation situation, long tickId);

  default void observe(V vehicle, BotTargetSituation situation, long tickId) {}

  default void depthChargeContact(
      V vehicle, BotTargetSituation situation, Submarine target, long tickId) {}

  default Map<String, BotSubmarineHunt.Detection> submarineDetections(double now) {
    return Map.of();
  }

  default BotGoal goal(
      V vehicle, BotTargetSituation situation, Vehicle contact, boolean advanceSearch) {
    if (contact != null) return BotGoal.enemy(contact);
    Vector2 destination =
        vehicle instanceof ScoutPlane ? vehicle.position() : situation.patrolCenter();
    return new BotGoal(
        vehicle instanceof ScoutPlane ? BotGoal.Kind.NONE : BotGoal.Kind.PATROL,
        null,
        -1,
        destination.x(),
        destination.z());
  }
}
