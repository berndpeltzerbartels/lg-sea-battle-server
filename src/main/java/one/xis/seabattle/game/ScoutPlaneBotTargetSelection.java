package one.xis.seabattle.game;

import static one.xis.seabattle.game.GameRules.*;

import java.util.*;

/** Aircraft target ranking: approach geometry, reservations and human attack rotation. */
public final class ScoutPlaneBotTargetSelection implements VehicleBotTargetSelection<ScoutPlane> {
  @Override
  public Vehicle select(ScoutPlane plane, BotTargetSituation situation, long tickId) {
    return chooseScoutPlaneTarget(
            situation.session(), plane, situation.candidates(), situation.reservations())
        .orElse(null);
  }

  private Optional<Vehicle> chooseScoutPlaneTarget(
      GameSession session,
      ScoutPlane plane,
      List<Vehicle> candidates,
      Map<String, Integer> targetReservations) {
    if (candidates.isEmpty()) return Optional.empty();
    List<Vehicle> preferredCandidates =
        candidates.stream().filter(target -> !target.isHumanControlled()).toList();
    if (preferredCandidates.isEmpty()) {
      return candidates.stream()
          .min(
              (left, right) ->
                  Double.compare(
                      botScoutPlaneTargetScore(session, plane, left, targetReservations),
                      botScoutPlaneTargetScore(session, plane, right, targetReservations)));
    }

    Optional<Vehicle> attackHuman = scoutPlaneAttackHuman(session, plane);
    if (shouldForceHumanScoutPlaneTarget(session, plane, attackHuman)
        && candidates.stream()
            .anyMatch(candidate -> candidate.id().equals(attackHuman.get().id()))) {
      return attackHuman;
    }

    return preferredCandidates.stream()
        .min(
            (left, right) ->
                Double.compare(
                    botScoutPlaneTargetScore(session, plane, left, targetReservations),
                    botScoutPlaneTargetScore(session, plane, right, targetReservations)));
  }

  private double botScoutPlaneTargetScore(
      GameSession session,
      ScoutPlane plane,
      Vehicle target,
      Map<String, Integer> targetReservations) {
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
        + stablePlaneTargetBias(session, plane, target);
  }

  private double stablePlaneTargetBias(GameSession session, ScoutPlane plane, Vehicle target) {
    double phase = session.stablePhase(plane.id() + ":" + target.id());
    return Math.sin(phase * 2.3) * BOT_SCOUT_PLANE_TARGET_TIE_BREAKER;
  }

  private boolean shouldForceHumanScoutPlaneTarget(
      GameSession session, ScoutPlane plane, Optional<Vehicle> attackHuman) {
    if (attackHuman.isEmpty()) {
      return false;
    }
    int threshold = botScoutPlaneForceHumanAttackThreshold(session, plane.teamId());
    if (threshold == Integer.MAX_VALUE) {
      return false;
    }
    return session.botScoutPlaneNonHumanAttackStreakByHumanTarget.getOrDefault(
            session.scoutPlaneHumanAttackStreakKey(plane.teamId(), attackHuman.get().id()), 0)
        >= threshold;
  }

  private int botScoutPlaneForceHumanAttackThreshold(GameSession session, String attackingTeamId) {
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

  private Optional<Vehicle> scoutPlaneAttackHuman(GameSession session, ScoutPlane plane) {
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
}
