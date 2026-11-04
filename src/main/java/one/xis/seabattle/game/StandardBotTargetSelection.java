package one.xis.seabattle.game;

import static one.xis.seabattle.game.GameRules.*;

import java.util.*;

/** Existing target priorities and remembered/search destinations. No snapshot conversion. */
public final class StandardBotTargetSelection implements BotTargetSelection {
  private GameSession session;

  public StandardBotTargetSelection() {}

  StandardBotTargetSelection(GameSession session) {
    this.session = session;
  }

  @Override
  public Vehicle select(BotTargetSituation situation, long tickId) {
    session = situation.session();
    if (situation.lastSubmarineCourse() != null
        && situation.lastSubmarineCourse().until() > situation.timeSeconds()
        && situation.currentTarget() != null
        && "active".equals(situation.currentTarget().state())
        && !situation.isVisible(situation.currentTarget())
        && !situation.isStrategicContact(situation.currentTarget()))
      return situation.currentTarget();
    return situation.observer().isScoutPlane()
        ? chooseScoutPlaneTarget(
                situation.observer(), situation.candidates(), situation.reservations())
            .orElse(null)
        : chooseBotTarget(situation.observer(), situation.candidates()).orElse(null);
  }

  @Override
  public BotGoal goal(BotTargetSituation situation, Vehicle contact, boolean advanceSearch) {
    session = situation.session();
    if (situation.observer().isScoutPlane())
      return BotTargetSelection.super.goal(situation, contact, advanceSearch);
    return navigationGoal(situation.observer(), contact, advanceSearch);
  }

  private Optional<Vehicle> chooseScoutPlaneTarget(
      Vehicle plane, List<Vehicle> candidates, Map<String, Integer> targetReservations) {
    if (candidates.isEmpty()) return Optional.empty();
    List<Vehicle> preferredCandidates =
        candidates.stream().filter(ship -> !session.isHumanControlled(ship)).toList();
    if (preferredCandidates.isEmpty()) {
      return candidates.stream()
          .min(
              (left, right) ->
                  Double.compare(
                      botScoutPlaneTargetScore(plane, left, targetReservations),
                      botScoutPlaneTargetScore(plane, right, targetReservations)));
    }

    Optional<Vehicle> attackHuman = scoutPlaneAttackHuman(plane);
    if (shouldForceHumanScoutPlaneTarget(plane, attackHuman)
        && candidates.stream()
            .anyMatch(candidate -> candidate.id().equals(attackHuman.get().id()))) {
      return attackHuman;
    }

    return preferredCandidates.stream()
        .min(
            (left, right) ->
                Double.compare(
                    botScoutPlaneTargetScore(plane, left, targetReservations),
                    botScoutPlaneTargetScore(plane, right, targetReservations)));
  }

  private double botScoutPlaneTargetScore(
      Vehicle plane, Vehicle target, Map<String, Integer> targetReservations) {
    double distance = plane.position().distanceTo(target.position());
    double bearing =
        Math.abs(
            session.relativeBearing(
                plane,
                target
                    .position()
                    .add(
                        Vector2.fromHeading(target.heading())
                            .scale(Math.max(0, target.speed()) * 1.5))));
    return distance
        + targetReservations.getOrDefault(target.id(), 0)
            * BOT_SCOUT_PLANE_TARGET_RESERVATION_PENALTY
        + bearing * BOT_SCOUT_PLANE_TARGET_BEARING_PENALTY
        + (distance < BOT_SCOUT_PLANE_CLOSE_REALIGN_RANGE
                && bearing > BOT_SCOUT_PLANE_CLOSE_REALIGN_ARC
            ? BOT_SCOUT_PLANE_CLOSE_REALIGN_PENALTY
            : 0)
        + stablePlaneTargetBias(plane, target);
  }

  private double stablePlaneTargetBias(Vehicle plane, Vehicle target) {
    double phase = session.stablePhase(plane.id() + ":" + target.id());
    return Math.sin(phase * 2.3) * BOT_SCOUT_PLANE_TARGET_TIE_BREAKER;
  }

  private boolean shouldForceHumanScoutPlaneTarget(Vehicle plane, Optional<Vehicle> attackHuman) {
    if (attackHuman.isEmpty()) {
      return false;
    }
    int threshold = botScoutPlaneForceHumanAttackThreshold(plane.teamId());
    if (threshold == Integer.MAX_VALUE) {
      return false;
    }
    return session.botScoutPlaneNonHumanAttackStreakByHumanTarget.getOrDefault(
            session.scoutPlaneHumanAttackStreakKey(plane.teamId(), attackHuman.get().id()), 0)
        >= threshold;
  }

  private int botScoutPlaneForceHumanAttackThreshold(String attackingTeamId) {
    int humanTargetCount = session.activeHumanTargetsForScoutPlaneTeam(attackingTeamId).size();
    if (humanTargetCount <= 0) {
      return Integer.MAX_VALUE;
    }
    if (humanTargetCount == 1) {
      return BOT_SCOUT_PLANE_FORCE_HUMAN_SINGLE_TARGET_STREAK;
    }
    if (humanTargetCount == 2) {
      return BOT_SCOUT_PLANE_FORCE_HUMAN_TWO_TARGET_STREAK;
    }
    return BOT_SCOUT_PLANE_FORCE_HUMAN_MANY_TARGET_STREAK;
  }

  private Optional<Vehicle> scoutPlaneAttackHuman(Vehicle plane) {
    if (!plane.isScoutPlane() || !"bot".equals(plane.controlledBy())) {
      return Optional.empty();
    }

    List<Vehicle> availablePlanes =
        session.activeTeamShips(plane.teamId()).stream()
            .filter(candidate -> candidate.isScoutPlane() && "bot".equals(candidate.controlledBy()))
            .toList();
    List<Vehicle> humans =
        session.calculationShips().stream()
            .filter(candidate -> "active".equals(candidate.state()))
            .filter(candidate -> !candidate.teamId().equals(plane.teamId()))
            .filter(candidate -> !candidate.isScoutPlane())
            .filter(session::isHumanControlled)
            .sorted((left, right) -> left.id().compareTo(right.id()))
            .toList();
    List<String> assignedPlaneIds = new ArrayList<>();
    for (Vehicle human : humans) {
      Optional<Vehicle> attacker =
          availablePlanes.stream()
              .filter(candidate -> !assignedPlaneIds.contains(candidate.id()))
              .min(
                  (left, right) ->
                      Double.compare(
                          left.position().distanceTo(human.position()),
                          right.position().distanceTo(human.position())));
      if (attacker.isEmpty()) {
        return Optional.empty();
      }
      assignedPlaneIds.add(attacker.get().id());
      if (attacker.get().id().equals(plane.id())) {
        return Optional.of(human);
      }
    }
    return Optional.empty();
  }

  private Optional<Vehicle> chooseBotTarget(Vehicle ship, List<Vehicle> targets) {
    if (targets.isEmpty()) {
      return Optional.empty();
    }

    ShipDistance nearestHumanTarget = null;
    ShipDistance nearestBotTarget = null;
    Vehicle bestTarget = null;
    double bestTargetScore = Double.POSITIVE_INFINITY;
    for (Vehicle target : targets) {
      ShipDistance candidate =
          new ShipDistance(target, ship.position().distanceTo(target.position()));
      double targetScore = botTargetScore(ship, target);
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
      return Optional.of(nearestHumanTarget.ship());
    }

    return Optional.ofNullable(bestTarget);
  }

  private double botTargetScore(Vehicle ship, Vehicle target) {
    return ship.position().distanceTo(target.position());
  }

  private BotGoal navigationGoal(Vehicle ship, Vehicle contact, boolean advanceSearch) {
    var search = ship.surface().botNavigation.submarineSearch;
    boolean localContact =
        contact != null
            && (ship.position().distanceTo(contact.position()) <= BotNavigation.ENCOUNTER_RANGE
                || contact.isSubmarine() && session.isHumanControlled(contact));
    if (contact != null && (search == null || localContact)) {
      return BotGoal.enemy(contact);
    }
    var attack = ship.surface().botSubmarineAttackCourse;
    if (attack != null) {
      Vector2 forward = Vector2.fromHeading(attack.heading());
      Vector2 offset = ship.position().subtract(attack.position());
      double along = offset.x() * forward.x() + offset.z() * forward.z();
      Vector2 point = attack.position().add(forward.scale(Math.max(0, along) + 60));
      return new BotGoal(
          BotGoal.Kind.LAST_KNOWN_COURSE,
          attack.targetId(),
          ship.surface().botNavigation.targetLife,
          point.x(),
          point.z());
    }
    if (search != null) {
      Vector2 point = advanceSearch ? search.destination(ship) : search.currentDestination();
      return new BotGoal(BotGoal.Kind.SEARCH, null, -1, point.x(), point.z());
    }
    // No hidden live position: represent the retained heading by a point straight ahead on that
    // heading.
    if (ship.surface().botNavigation.target != null && ship.surface().botNavigation.route != null) {
      Vector2 point =
          ship.position()
              .add(Vector2.fromHeading(ship.surface().botNavigation.route.heading()).scale(100));
      return new BotGoal(BotGoal.Kind.RETAINED_COURSE, null, -1, point.x(), point.z());
    }
    return new BotGoal(
        BotGoal.Kind.PATROL, null, -1, session.patrolCenter.x(), session.patrolCenter.z());
  }

  private record ShipDistance(Vehicle ship, double distance) {}
}
