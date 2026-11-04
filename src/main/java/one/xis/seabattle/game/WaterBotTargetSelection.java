package one.xis.seabattle.game;

import static one.xis.seabattle.game.GameRules.*;

import java.util.*;

/** Shared water-contact priorities and search orders; no aircraft decisions. */
final class WaterBotTargetSelection {
  private BotSubmarineHunt submarineHunts;

  public void observe(BotTargetSituation situation, long tickId) {
    if (!BotSubmarineHunt.canReport(situation.observer())) return;
    for (Vehicle target : situation.candidates()) {
      if (target instanceof Submarine
          && target.isHumanControlled()
          && !target.teamId().equals(situation.observer().teamId())
          && situation.isVisible(target)) {
        report(situation, target);
      }
    }
  }

  public void sonarContact(BotTargetSituation situation, Submarine target, long tickId) {
    if (!BotSubmarineHunt.canReport(situation.observer())
        || target.teamId().equals(situation.observer().teamId())
        || !situation.hasSonarContact(target)) return;
    if (submarineHunts == null) submarineHunts = new BotSubmarineHunt();
    double radius = BotSubmarineHunt.RECRUIT_RADIUS
        + situation.observer().position().distanceTo(target.position());
    submarineHunts.sonarContact(situation.observer(), target, situation.timeSeconds(),
        () -> situation.nearbyVehicles(radius));
  }

  public Map<String, BotSubmarineHunt.Detection> submarineDetections(double now) {
    return submarineHunts == null ? Map.of() : submarineHunts.detections(now);
  }

  private void report(BotTargetSituation situation, Vehicle target) {
    double radius =
        BotSubmarineHunt.RECRUIT_RADIUS
            + situation.observer().position().distanceTo(target.position());
    if (submarineHunts == null) submarineHunts = new BotSubmarineHunt();
    submarineHunts.sighting(
        situation.observer(),
        target,
        situation.timeSeconds(),
        () -> situation.nearbyVehicles(radius));
  }

  Vehicle select(SurfaceVehicle vehicle, BotTargetSituation situation, long tickId) {
    if (situation.lastSubmarineCourse() != null
        && situation.lastSubmarineCourse().until() > situation.timeSeconds()
        && situation.currentTarget() != null
        && "active".equals(situation.currentTarget().state())
        && !situation.isVisible(situation.currentTarget())
        && !situation.isStrategicContact(situation.currentTarget()))
      return situation.currentTarget();
    Vehicle preferred = chooseBotTarget(vehicle, situation.candidates()).orElse(null);
    // Hunt/search and remembered approaches retain their existing priorities and group size.
    if (preferred == null
        || situation.submarineSearch() != null
        || situation.lastSubmarineCourse() != null
            && situation.lastSubmarineCourse().until() > situation.timeSeconds()
        || situation.otherAssignedWaterAttackers(preferred) < BOT_WATER_TARGET_PREFERRED_ATTACKERS)
      return preferred;
    return lessCrowdedAlternative(vehicle, situation, preferred);
  }

  private Vehicle lessCrowdedAlternative(
      SurfaceVehicle vehicle, BotTargetSituation situation, Vehicle preferred) {
    Vector2 offset = preferred.position().subtract(vehicle.position());
    double limitSquared =
        (offset.x() * offset.x() + offset.z() * offset.z())
            * BOT_WATER_TARGET_ALTERNATIVE_DISTANCE_FACTOR
            * BOT_WATER_TARGET_ALTERNATIVE_DISTANCE_FACTOR;
    Vehicle alternative = null;
    double nearestSquared = Double.POSITIVE_INFINITY;
    for (Vehicle candidate : situation.candidates()) {
      if (candidate == preferred || preferred.isHumanControlled() && !candidate.isHumanControlled())
        continue;
      double dx = candidate.position().x() - vehicle.position().x();
      double dz = candidate.position().z() - vehicle.position().z();
      double distanceSquared = dx * dx + dz * dz;
      if (distanceSquared > limitSquared
          || distanceSquared >= nearestSquared
          || situation.otherAssignedWaterAttackers(candidate)
              >= BOT_WATER_TARGET_PREFERRED_ATTACKERS) continue;
      alternative = candidate;
      nearestSquared = distanceSquared;
    }
    return alternative == null ? preferred : alternative;
  }

  private Optional<Vehicle> chooseBotTarget(SurfaceVehicle vehicle, List<Vehicle> targets) {
    if (targets.isEmpty()) {
      return Optional.empty();
    }

    VehicleDistance nearestHumanTarget = null;
    VehicleDistance nearestBotTarget = null;
    Vehicle bestTarget = null;
    double bestTargetScore = Double.POSITIVE_INFINITY;
    for (Vehicle target : targets) {
      VehicleDistance candidate =
          new VehicleDistance(target, vehicle.position().distanceTo(target.position()));
      double targetScore = botTargetScore(vehicle, target);
      if (bestTarget == null || targetScore < bestTargetScore) {
        bestTarget = target;
        bestTargetScore = targetScore;
      }
      if ("bot".equals(target.controlledBy())) {
        if (nearestBotTarget == null || candidate.distance() < nearestBotTarget.distance()) {
          nearestBotTarget = candidate;
        }
      } else if (nearestHumanTarget == null
          || candidate.distance() < nearestHumanTarget.distance()) {
        nearestHumanTarget = candidate;
      }
    }

    if (nearestHumanTarget != null
        && (nearestBotTarget == null
            || nearestBotTarget.distance() > BOT_HUMAN_TARGET_PRIORITY_CLEARANCE)) {
      return Optional.of(nearestHumanTarget.vehicle());
    }

    return Optional.ofNullable(bestTarget);
  }

  private double botTargetScore(SurfaceVehicle vehicle, Vehicle target) {
    return vehicle.position().distanceTo(target.position());
  }

  BotGoal fallbackGoal(SurfaceVehicle vehicle, BotTargetSituation situation) {
    if (vehicle.botNavigation.target != null && vehicle.botNavigation.route != null) {
      Vector2 point =
          vehicle
              .position()
              .add(Vector2.fromHeading(vehicle.botNavigation.route.heading()).scale(100));
      return new BotGoal(BotGoal.Kind.RETAINED_COURSE, null, -1, point.x(), point.z());
    }
    return new BotGoal(
        BotGoal.Kind.PATROL, null, -1, situation.patrolCenter().x(), situation.patrolCenter().z());
  }

  private record VehicleDistance(Vehicle vehicle, double distance) {}
}
