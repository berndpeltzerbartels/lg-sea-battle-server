package one.xis.seabattle.game;

import static one.xis.seabattle.game.GameRules.*;

import java.util.Optional;

/** Aircraft approach, terrain clearance, torpedo attacks and bombing. One instance per game. */
public final class ScoutPlaneBotCalculation implements VehicleBotCalculation<ScoutPlane> {
  private GameSession session;

  @Override
  public void calculate(ScoutPlane plane, BotSituation situation, long tickId, BotGoal goal) {
    session = situation.session();
    switch (goal.kind()) {
      case ENEMY -> attackTarget(plane, Optional.ofNullable(goal.target()));
      case NONE -> attackTarget(plane, Optional.empty());
      default -> applyCourse(plane, bearingTo(plane, new Vector2(goal.x(), goal.z())));
    }
  }

  private void attackTarget(ScoutPlane plane, Optional<Vehicle> contact) {
    if (contact.isEmpty()) {
      clearFlyThrough(plane);
      patrol(plane);
      return;
    }

    Vehicle targetVehicle = contact.get();
    BotTorpedoSolution torpedoSolution = torpedoSolution(plane, targetVehicle);
    Vector2 torpedoTarget = torpedoSolution.targetPosition();
    BotBombingSolution bombSolution = bombingSolution(plane, targetVehicle);
    Vector2 bombTarget = bombSolution.targetPosition();
    double distance = plane.position().distanceTo(targetVehicle.position());
    BotAircraftEvasion.observe(targetVehicle, plane, session.nowSeconds);
    boolean inBombWindow =
        distance >= BOT_SCOUT_PLANE_BOMB_MIN_RANGE && distance <= BOT_SCOUT_PLANE_BOMB_RANGE;
    boolean inTorpedoReleaseWindow =
        distance >= BOT_SCOUT_PLANE_TORPEDO_MIN_RANGE
            && distance <= BOT_SCOUT_PLANE_TORPEDO_RELEASE_RANGE;
    boolean mayUseAirTorpedo = canUseTorpedoAgainst(targetVehicle);
    boolean preferTorpedoAttack =
        mayUseAirTorpedo
            && distance >= BOT_SCOUT_PLANE_TORPEDO_MIN_RANGE
            && distance <= BOT_SCOUT_PLANE_TORPEDO_APPROACH_RANGE;
    Optional<Vector2> flyThroughTarget = flyThroughTarget(plane, distance);
    if (flyThroughTarget.isPresent()) {
      plane.botScoutPlaneTargetY(BOT_SCOUT_PLANE_BOMB_ATTACK_Y);
      applyCourse(plane, session.angleTo(flyThroughTarget.get(), plane.position()));
      return;
    }
    if (distance < BOT_SCOUT_PLANE_TORPEDO_MIN_RANGE && !inBombWindow) {
      Vector2 flyThrough = startFlyThrough(plane);
      plane.botScoutPlaneTargetY(BOT_SCOUT_PLANE_BOMB_ATTACK_Y);
      applyCourse(plane, session.angleTo(flyThrough, plane.position()));
      return;
    }
    Vector2 aimTarget = preferTorpedoAttack ? torpedoTarget : bombTarget;
    double targetBearing = session.relativeBearing(plane, aimTarget);
    if (distance < BOT_SCOUT_PLANE_CLOSE_REALIGN_RANGE
        && Math.abs(targetBearing) > BOT_SCOUT_PLANE_CLOSE_REALIGN_ARC) {
      Vector2 flyThrough = startFlyThrough(plane);
      plane.botScoutPlaneTargetY(BOT_SCOUT_PLANE_BOMB_ATTACK_Y);
      applyCourse(plane, session.angleTo(flyThrough, plane.position()));
      return;
    }
    if (distance > BOT_SCOUT_PLANE_ATTACK_RANGE) {
      plane.botScoutPlaneTargetY(BOT_SCOUT_PLANE_CRUISE_Y);
      applyCourse(plane, session.angleTo(torpedoTarget, plane.position()));
      return;
    }

    plane.botScoutPlaneTargetY(
        preferTorpedoAttack ? BOT_SCOUT_PLANE_TORPEDO_ATTACK_Y : BOT_SCOUT_PLANE_BOMB_ATTACK_Y);
    applyCourse(plane, MathSupport.normalizeAngle(plane.heading() + targetBearing));
    if (Math.abs(targetBearing) > BOT_SCOUT_PLANE_ATTACK_ARC) {
      return;
    }
    if (session.isHumanControlled(targetVehicle)
        && "active".equals(targetVehicle.state())
        && !targetVehicle.teamId().equals(plane.teamId())
        && (!targetVehicle.isSubmarine()
            || targetVehicle.y() + SubmarinePeriscopeGeometry.topAboveRoot(targetVehicle.y())
                > 0)) {
      session.aircraftWarnings.observe(session.nowSeconds, plane, targetVehicle);
    }

    if (preferTorpedoAttack
        && inTorpedoReleaseWindow
        && plane.y() <= BOT_SCOUT_PLANE_TORPEDO_RELEASE_MAX_Y
        && torpedoSolution.canRelease()
        && session.nowSeconds >= session.nextBotScoutPlaneTorpedoTime
        && plane.canDropBomb(session.nowSeconds)) {
      session.fireAirTorpedo(
          plane, BOT_SCOUT_PLANE_TORPEDO_COOLDOWN_SECONDS, torpedoSolution.heading());
      session.recordBotScoutPlaneAttack(plane, targetVehicle);
      return;
    }
    if (inBombWindow
        && (!preferTorpedoAttack || distance < BOT_SCOUT_PLANE_TORPEDO_MIN_RANGE)
        && bombSolution.canRelease()
        && plane.canDropBomb(session.nowSeconds)) {
      session.dropBombsFromScoutPlane(plane, BOT_SCOUT_PLANE_BOMB_COOLDOWN_SECONDS);
      session.recordBotScoutPlaneAttack(plane, targetVehicle);
      return;
    }
  }

  private Vector2 predictedTargetPosition(Vehicle targetVehicle, double leadSeconds) {
    return targetVehicle
        .position()
        .add(
            Vector2.fromHeading(targetVehicle.heading())
                .scale(Math.max(0, targetVehicle.speed()) * leadSeconds));
  }

  private BotTorpedoSolution torpedoSolution(ScoutPlane plane, Vehicle target) {
    double torpedoSpeed = session.airTorpedoSpeed(plane);
    Vector2 releasePosition =
        plane
            .position()
            .add(
                Vector2.fromHeading(plane.heading())
                    .scale(BOT_SCOUT_PLANE_AIR_TORPEDO_RELEASE_OFFSET));
    Vector2 targetPosition = predictedTargetPosition(target, BOT_SCOUT_PLANE_TORPEDO_LEAD_SECONDS);
    for (int iteration = 0; iteration < 3; iteration += 1) {
      double runSeconds = releasePosition.distanceTo(targetPosition) / Math.max(1, torpedoSpeed);
      targetPosition = predictedTargetPosition(target, runSeconds);
    }
    double heading = session.angleTo(targetPosition, releasePosition);
    boolean canRelease =
        Math.abs(session.relativeBearing(plane, targetPosition)) <= BOT_SCOUT_PLANE_TORPEDO_ARC
            && session.angularDistance(plane.heading(), heading) <= BOT_SCOUT_PLANE_TORPEDO_ARC
            && Math.abs(plane.turnVelocity()) <= BOT_SCOUT_PLANE_STABLE_ATTACK_TURN_RATE;
    return new BotTorpedoSolution(targetPosition, heading, canRelease);
  }

  private boolean canUseTorpedoAgainst(Vehicle target) {
    return !target.isSubmarine() || target.isOnSurface();
  }

  private BotBombingSolution bombingSolution(ScoutPlane plane, Vehicle target) {
    double fallSeconds = estimatedBombFallSeconds(plane);
    double centerReleaseDelaySeconds = (BOMBS_PER_DROP - 1) * BOMB_RELEASE_INTERVAL_SECONDS * 0.5;
    double leadSeconds = fallSeconds + centerReleaseDelaySeconds;
    Vector2 targetPosition = predictedTargetPosition(target, leadSeconds);
    Vector2 toTarget = targetPosition.subtract(plane.position());
    Vector2 forward = Vector2.fromHeading(plane.heading());
    Vector2 right = session.rightFromHeading(plane.heading());
    double forwardDistance = dot(toTarget, forward);
    double lateralDistance = Math.abs(dot(toTarget, right));
    double bombSpeed =
        Math.min(SCOUT_PLANE_MAX_BOMB_HORIZONTAL_SPEED, Math.max(4, plane.speed() * 0.92));
    double patternCenterForwardDistance =
        bombSpeed * fallSeconds + Math.max(0, plane.speed()) * centerReleaseDelaySeconds;
    double forwardError = forwardDistance - patternCenterForwardDistance;
    boolean canRelease =
        forwardDistance > 0
            && forwardError <= BOT_SCOUT_PLANE_BOMB_FORWARD_TOLERANCE * 0.35
            && forwardError >= -BOT_SCOUT_PLANE_BOMB_FORWARD_TOLERANCE
            && lateralDistance <= BOT_SCOUT_PLANE_BOMB_LATERAL_TOLERANCE
            && Math.abs(plane.turnVelocity()) <= BOT_SCOUT_PLANE_STABLE_ATTACK_TURN_RATE;
    return new BotBombingSolution(targetPosition, canRelease);
  }

  private static double estimatedBombFallSeconds(ScoutPlane plane) {
    double altitude =
        Math.max(SCOUT_PLANE_MIN_BOMB_ALTITUDE, plane.y() - BOMB_DROP_VERTICAL_OFFSET);
    double initialVerticalSpeed =
        MathSupport.clamp(
            -plane.verticalSpeed(),
            -SCOUT_PLANE_MAX_BOMB_INITIAL_UP_SPEED,
            SCOUT_PLANE_MAX_BOMB_INITIAL_DOWN_SPEED);
    double gravity = 14.0;
    double discriminant = initialVerticalSpeed * initialVerticalSpeed + 2.0 * gravity * altitude;
    return Math.max(0.2, (-initialVerticalSpeed + Math.sqrt(Math.max(0, discriminant))) / gravity);
  }

  private static double dot(Vector2 left, Vector2 right) {
    return left.x() * right.x() + left.z() * right.z();
  }

  private Optional<Vector2> flyThroughTarget(ScoutPlane plane, double distanceToTarget) {
    Vector2 target = session.botScoutPlaneFlyThroughTargets.get(plane.id());
    if (target == null) {
      return Optional.empty();
    }
    if (session.nowSeconds >= session.botScoutPlaneFlyThroughUntil.getOrDefault(plane.id(), 0.0)
        || plane.position().distanceTo(target) <= BOT_SCOUT_PLANE_FLY_THROUGH_COMPLETE_DISTANCE
        || distanceToTarget >= BOT_SCOUT_PLANE_TORPEDO_APPROACH_RANGE + 40.0) {
      clearFlyThrough(plane);
      return Optional.empty();
    }
    return Optional.of(target);
  }

  private Vector2 startFlyThrough(ScoutPlane plane) {
    Vector2 target =
        plane
            .position()
            .add(Vector2.fromHeading(plane.heading()).scale(BOT_SCOUT_PLANE_FLY_THROUGH_DISTANCE));
    session.botScoutPlaneFlyThroughTargets.put(plane.id(), target);
    session.botScoutPlaneFlyThroughUntil.put(
        plane.id(), session.nowSeconds + BOT_SCOUT_PLANE_FLY_THROUGH_SECONDS);
    return target;
  }

  private void clearFlyThrough(ScoutPlane plane) {
    session.botScoutPlaneFlyThroughTargets.remove(plane.id());
    session.botScoutPlaneFlyThroughUntil.remove(plane.id());
  }

  private void applyCourse(ScoutPlane plane, double desiredHeading) {
    double heading = terrainSafeHeading(plane, desiredHeading);
    plane.applyCommand(7, rudderTowardHeading(plane, heading));
  }

  private double terrainSafeHeading(ScoutPlane plane, double desiredHeading) {
    if (isTerrainCourseSafe(plane, desiredHeading)) {
      return desiredHeading;
    }

    double bestHeading = MathSupport.normalizeAngle(plane.heading() + Math.PI);
    double bestScore = Double.NEGATIVE_INFINITY;
    for (int step = -8; step <= 8; step += 1) {
      double heading = MathSupport.normalizeAngle(plane.heading() + step * (Math.PI / 10.0));
      double score = terrainCourseScore(plane, heading, desiredHeading);
      if (score > bestScore) {
        bestScore = score;
        bestHeading = heading;
      }
    }
    return bestHeading;
  }

  private double terrainCourseScore(ScoutPlane plane, double heading, double desiredHeading) {
    double score = 0;
    Vector2 forward = Vector2.fromHeading(heading);
    double[] distances = {32, 64, 104, 150, 205};
    for (int index = 0; index < distances.length; index += 1) {
      Vector2 sample = plane.position().add(forward.scale(distances[index]));
      if (isTerrainObstacle(sample)) {
        score -= 4000 - index * 300;
        continue;
      }
      score += distances[index] * (1.0 + index * 0.12);
    }
    score -= session.angularDistance(heading, desiredHeading) * 45;
    score -= session.angularDistance(heading, plane.heading()) * 12;
    return score;
  }

  private boolean isTerrainCourseSafe(ScoutPlane plane, double heading) {
    Vector2 forward = Vector2.fromHeading(heading);
    double[] distances = {34, 72, 118, 170};
    for (double distance : distances) {
      if (isTerrainObstacle(plane.position().add(forward.scale(distance)))) {
        return false;
      }
    }
    return true;
  }

  private boolean isTerrainObstacle(Vector2 position) {
    return LandGeometry.isBlocked(position, session.scoutPlaneObstacleMap);
  }

  private void patrol(ScoutPlane plane) {
    double patrolHeading =
        MathSupport.normalizeAngle(
            session.stablePhase(plane.id())
                + Math.sin(session.nowSeconds * 0.08 + session.stablePhase(plane.id())) * 0.9);
    plane.applyCommand(7, rudderTowardHeading(plane, patrolHeading));
  }

  private double bearingTo(ScoutPlane plane, Vector2 position) {
    Vector2 offset = position.subtract(plane.position());
    return offset.x() == 0 && offset.z() == 0
        ? plane.heading()
        : Math.atan2(offset.x(), offset.z());
  }

  private int rudderTowardHeading(ScoutPlane plane, double heading) {
    return BotHelm.rudder(plane.heading(), plane.turnVelocity(), 1, heading);
  }

  private record BotTorpedoSolution(Vector2 targetPosition, double heading, boolean canRelease) {}

  private record BotBombingSolution(Vector2 targetPosition, boolean canRelease) {}
}
