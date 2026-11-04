package one.xis.seabattle.game;

import static one.xis.seabattle.game.GameRules.*;

import java.util.*;
import one.xis.seabattle.game.GameSession.BotIntent;
import one.xis.seabattle.game.GameSession.BotRound;

/**
 * Existing navigation and tactical controls, operating directly on server vehicles. One instance
 * per game.
 */
public final class StandardBotCalculation implements BotCalculation {
  private GameSession session;
  private boolean parallelBotNavigation;
  private Map<Vehicle, BotCommand> plannedBotEscapes;
  private List<BotCourseRequest> pendingBotCourses;

  private record BotCommand(int engineOrder, int rudder) {
    void apply(Vehicle ship) {
      ship.applyCommand(engineOrder, rudder);
    }
  }

  private record BotCourseRequest(Vehicle ship, int engineOrder, int rudder) {}

  public StandardBotCalculation() {}

  StandardBotCalculation(GameSession session) {
    this.session = session;
  }

  @Override
  public void calculate(BotSituation situation, long tickId, Map<String, BotGoal> goals) {
    session = situation.session();
    parallelBotNavigation = session.parallelBotNavigation;
    calculateStandardBots(
        situation.radar(), situation.navigation(), situation.world(), situation.round(), goals);
  }

  private void calculateStandardBots(
      RadarService radarService,
      NavigationService navigationService,
      WorldMap worldMap,
      BotRound round,
      Map<String, BotGoal> goals) {
    List<Vehicle> surfaceShips = round.surfaceShips();
    if (parallelBotNavigation) {
      List<Vehicle> boats = surfaceShips.stream().filter(Vehicle::isBotControlled).toList();
      List<BotCommand> escapes =
          BotNavigationStream.map(boats, ship -> escapeCommand(ship, navigationService, worldMap));
      plannedBotEscapes = new HashMap<>();
      for (int i = 0; i < boats.size(); i++) plannedBotEscapes.put(boats.get(i), escapes.get(i));
      pendingBotCourses = new ArrayList<>();
    }
    try {
      round.activeShips().stream()
          .filter(ship -> "bot".equals(ship.controlledBy()))
          .forEach(
              ship -> {
                if (ship.isScoutPlane()) {
                  BotGoal goal = Objects.requireNonNull(goals.get(ship.id()), "Missing bot goal");
                  if (goal.kind() == BotGoal.Kind.ENEMY)
                    commandScoutPlaneBot(ship, Optional.ofNullable(goal.target()), worldMap);
                  else if (goal.kind() == BotGoal.Kind.NONE)
                    commandScoutPlaneBot(ship, Optional.empty(), worldMap);
                  else
                    applyScoutPlaneBotCommand(
                        ship, bearingTo(ship, new Vector2(goal.x(), goal.z())));
                } else {
                  commandBot(
                      ship,
                      round.cache(),
                      navigationService,
                      worldMap,
                      surfaceShips,
                      round.boats().get(ship),
                      Objects.requireNonNull(goals.get(ship.id()), "Missing bot goal"));
                }
              });
      if (parallelBotNavigation) {
        // Decisions keep their order; each terrain-planning worker owns one ship's escape plan.
        List<BotCommand> courses =
            BotNavigationStream.map(
                pendingBotCourses,
                request ->
                    courseCommand(
                        request.ship(),
                        request.engineOrder(),
                        request.rudder(),
                        navigationService,
                        worldMap));
        for (int i = 0; i < courses.size(); i++)
          courses.get(i).apply(pendingBotCourses.get(i).ship());
      }
    } finally {
      plannedBotEscapes = null;
      pendingBotCourses = null;
    }
  }

  private void commandScoutPlaneBot(Vehicle plane, Optional<Vehicle> target, WorldMap worldMap) {
    if (target.isEmpty()) {
      clearBotScoutPlaneFlyThrough(plane);
      patrolScoutPlane(plane);
      return;
    }

    Vehicle ship = target.get();
    BotTorpedoSolution torpedoSolution = botScoutPlaneTorpedoSolution(plane, ship);
    Vector2 torpedoTarget = torpedoSolution.targetPosition();
    BotBombingSolution bombSolution = botScoutPlaneBombingSolution(plane, ship);
    Vector2 bombTarget = bombSolution.targetPosition();
    double distance = plane.position().distanceTo(ship.position());
    BotAircraftEvasion.observe(ship, plane, session.nowSeconds);
    boolean inBombWindow =
        distance >= BOT_SCOUT_PLANE_BOMB_MIN_RANGE && distance <= BOT_SCOUT_PLANE_BOMB_RANGE;
    boolean inTorpedoReleaseWindow =
        distance >= BOT_SCOUT_PLANE_TORPEDO_MIN_RANGE
            && distance <= BOT_SCOUT_PLANE_TORPEDO_RELEASE_RANGE;
    boolean mayUseAirTorpedo = mayUseAirTorpedoAgainst(ship);
    boolean preferTorpedoAttack =
        mayUseAirTorpedo
            && distance >= BOT_SCOUT_PLANE_TORPEDO_MIN_RANGE
            && distance <= BOT_SCOUT_PLANE_TORPEDO_APPROACH_RANGE;
    Optional<Vector2> flyThroughTarget = botScoutPlaneFlyThroughTarget(plane, distance);
    if (flyThroughTarget.isPresent()) {
      plane.botScoutPlaneTargetY(BOT_SCOUT_PLANE_BOMB_ATTACK_Y);
      applyScoutPlaneBotCommand(plane, session.angleTo(flyThroughTarget.get(), plane.position()));
      return;
    }
    if (distance < BOT_SCOUT_PLANE_TORPEDO_MIN_RANGE && !inBombWindow) {
      Vector2 flyThrough = startBotScoutPlaneFlyThrough(plane);
      plane.botScoutPlaneTargetY(BOT_SCOUT_PLANE_BOMB_ATTACK_Y);
      applyScoutPlaneBotCommand(plane, session.angleTo(flyThrough, plane.position()));
      return;
    }
    Vector2 aimTarget = preferTorpedoAttack ? torpedoTarget : bombTarget;
    double targetBearing = session.relativeBearing(plane, aimTarget);
    if (distance < BOT_SCOUT_PLANE_CLOSE_REALIGN_RANGE
        && Math.abs(targetBearing) > BOT_SCOUT_PLANE_CLOSE_REALIGN_ARC) {
      Vector2 flyThrough = startBotScoutPlaneFlyThrough(plane);
      plane.botScoutPlaneTargetY(BOT_SCOUT_PLANE_BOMB_ATTACK_Y);
      applyScoutPlaneBotCommand(plane, session.angleTo(flyThrough, plane.position()));
      return;
    }
    if (distance > BOT_SCOUT_PLANE_ATTACK_RANGE) {
      plane.botScoutPlaneTargetY(BOT_SCOUT_PLANE_CRUISE_Y);
      applyScoutPlaneBotCommand(plane, session.angleTo(torpedoTarget, plane.position()));
      return;
    }

    plane.botScoutPlaneTargetY(
        preferTorpedoAttack ? BOT_SCOUT_PLANE_TORPEDO_ATTACK_Y : BOT_SCOUT_PLANE_BOMB_ATTACK_Y);
    applyScoutPlaneBotCommand(plane, MathSupport.normalizeAngle(plane.heading() + targetBearing));
    if (Math.abs(targetBearing) > BOT_SCOUT_PLANE_ATTACK_ARC) {
      return;
    }
    if (session.isHumanControlled(ship)
        && "active".equals(ship.state())
        && !ship.teamId().equals(plane.teamId())
        && (!ship.isSubmarine()
            || ship.y() + SubmarinePeriscopeGeometry.topAboveRoot(ship.y()) > 0)) {
      session.aircraftWarnings.observe(session.nowSeconds, plane, ship);
    }

    if (preferTorpedoAttack
        && inTorpedoReleaseWindow
        && plane.y() <= BOT_SCOUT_PLANE_TORPEDO_RELEASE_MAX_Y
        && torpedoSolution.canRelease()
        && session.nowSeconds >= session.nextBotScoutPlaneTorpedoTime
        && plane.canDropBomb(session.nowSeconds)) {
      session.fireAirTorpedo(
          plane, BOT_SCOUT_PLANE_TORPEDO_COOLDOWN_SECONDS, torpedoSolution.heading());
      session.recordBotScoutPlaneAttack(plane, ship);
      return;
    }
    if (inBombWindow
        && (!preferTorpedoAttack || distance < BOT_SCOUT_PLANE_TORPEDO_MIN_RANGE)
        && bombSolution.canRelease()
        && plane.canDropBomb(session.nowSeconds)) {
      session.dropBombsFromScoutPlane(plane, BOT_SCOUT_PLANE_BOMB_COOLDOWN_SECONDS);
      session.recordBotScoutPlaneAttack(plane, ship);
      return;
    }
  }

  private Vector2 predictedShipPosition(Vehicle ship, double leadSeconds) {
    return ship.position()
        .add(Vector2.fromHeading(ship.heading()).scale(Math.max(0, ship.speed()) * leadSeconds));
  }

  private BotTorpedoSolution botScoutPlaneTorpedoSolution(Vehicle plane, Vehicle target) {
    double torpedoSpeed = session.airTorpedoSpeed(plane);
    Vector2 releasePosition =
        plane
            .position()
            .add(
                Vector2.fromHeading(plane.heading())
                    .scale(BOT_SCOUT_PLANE_AIR_TORPEDO_RELEASE_OFFSET));
    Vector2 targetPosition = predictedShipPosition(target, BOT_SCOUT_PLANE_TORPEDO_LEAD_SECONDS);
    for (int iteration = 0; iteration < 3; iteration += 1) {
      double runSeconds = releasePosition.distanceTo(targetPosition) / Math.max(1, torpedoSpeed);
      targetPosition = predictedShipPosition(target, runSeconds);
    }
    double heading = session.angleTo(targetPosition, releasePosition);
    boolean canRelease =
        Math.abs(session.relativeBearing(plane, targetPosition)) <= BOT_SCOUT_PLANE_TORPEDO_ARC
            && session.angularDistance(plane.heading(), heading) <= BOT_SCOUT_PLANE_TORPEDO_ARC
            && Math.abs(plane.turnVelocity()) <= BOT_SCOUT_PLANE_STABLE_ATTACK_TURN_RATE;
    return new BotTorpedoSolution(targetPosition, heading, canRelease);
  }

  private boolean mayUseAirTorpedoAgainst(Vehicle target) {
    return !target.isSubmarine() || target.isOnSurface();
  }

  private BotBombingSolution botScoutPlaneBombingSolution(Vehicle plane, Vehicle target) {
    double fallSeconds = estimatedBombFallSeconds(plane);
    double centerReleaseDelaySeconds = (BOMBS_PER_DROP - 1) * BOMB_RELEASE_INTERVAL_SECONDS * 0.5;
    double leadSeconds = fallSeconds + centerReleaseDelaySeconds;
    Vector2 targetPosition = predictedShipPosition(target, leadSeconds);
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

  private static double estimatedBombFallSeconds(Vehicle plane) {
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

  private Optional<Vector2> botScoutPlaneFlyThroughTarget(Vehicle plane, double distanceToTarget) {
    Vector2 target = session.botScoutPlaneFlyThroughTargets.get(plane.id());
    if (target == null) {
      return Optional.empty();
    }
    if (session.nowSeconds >= session.botScoutPlaneFlyThroughUntil.getOrDefault(plane.id(), 0.0)
        || plane.position().distanceTo(target) <= BOT_SCOUT_PLANE_FLY_THROUGH_COMPLETE_DISTANCE
        || distanceToTarget >= BOT_SCOUT_PLANE_TORPEDO_APPROACH_RANGE + 40.0) {
      clearBotScoutPlaneFlyThrough(plane);
      return Optional.empty();
    }
    return Optional.of(target);
  }

  private Vector2 startBotScoutPlaneFlyThrough(Vehicle plane) {
    Vector2 target =
        plane
            .position()
            .add(Vector2.fromHeading(plane.heading()).scale(BOT_SCOUT_PLANE_FLY_THROUGH_DISTANCE));
    session.botScoutPlaneFlyThroughTargets.put(plane.id(), target);
    session.botScoutPlaneFlyThroughUntil.put(
        plane.id(), session.nowSeconds + BOT_SCOUT_PLANE_FLY_THROUGH_SECONDS);
    return target;
  }

  private void clearBotScoutPlaneFlyThrough(Vehicle plane) {
    session.botScoutPlaneFlyThroughTargets.remove(plane.id());
    session.botScoutPlaneFlyThroughUntil.remove(plane.id());
  }

  private void applyScoutPlaneBotCommand(Vehicle plane, double desiredHeading) {
    double heading = scoutPlaneTerrainSafeHeading(plane, desiredHeading);
    plane.applyCommand(7, rudderTowardHeading(plane, heading));
  }

  private double scoutPlaneTerrainSafeHeading(Vehicle plane, double desiredHeading) {
    if (isScoutPlaneCourseTerrainSafe(plane, desiredHeading)) {
      return desiredHeading;
    }

    double bestHeading = MathSupport.normalizeAngle(plane.heading() + Math.PI);
    double bestScore = Double.NEGATIVE_INFINITY;
    for (int step = -8; step <= 8; step += 1) {
      double heading = MathSupport.normalizeAngle(plane.heading() + step * (Math.PI / 10.0));
      double score = scoutPlaneTerrainCourseScore(plane, heading, desiredHeading);
      if (score > bestScore) {
        bestScore = score;
        bestHeading = heading;
      }
    }
    return bestHeading;
  }

  private double scoutPlaneTerrainCourseScore(
      Vehicle plane, double heading, double desiredHeading) {
    double score = 0;
    Vector2 forward = Vector2.fromHeading(heading);
    double[] distances = {32, 64, 104, 150, 205};
    for (int index = 0; index < distances.length; index += 1) {
      Vector2 sample = plane.position().add(forward.scale(distances[index]));
      if (isScoutPlaneTerrainObstacle(sample)) {
        score -= 4000 - index * 300;
        continue;
      }
      score += distances[index] * (1.0 + index * 0.12);
    }
    score -= session.angularDistance(heading, desiredHeading) * 45;
    score -= session.angularDistance(heading, plane.heading()) * 12;
    return score;
  }

  private boolean isScoutPlaneCourseTerrainSafe(Vehicle plane, double heading) {
    Vector2 forward = Vector2.fromHeading(heading);
    double[] distances = {34, 72, 118, 170};
    for (double distance : distances) {
      if (isScoutPlaneTerrainObstacle(plane.position().add(forward.scale(distance)))) {
        return false;
      }
    }
    return true;
  }

  private boolean isScoutPlaneTerrainObstacle(Vector2 position) {
    return LandGeometry.isBlocked(position, session.scoutPlaneObstacleMap);
  }

  private void patrolScoutPlane(Vehicle plane) {
    double patrolHeading =
        MathSupport.normalizeAngle(
            session.stablePhase(plane.id())
                + Math.sin(session.nowSeconds * 0.08 + session.stablePhase(plane.id())) * 0.9);
    plane.applyCommand(7, rudderTowardHeading(plane, patrolHeading));
  }

  private void commandBot(
      Vehicle ship,
      RadarService.VisibilityCache visibilityCache,
      NavigationService navigationService,
      WorldMap worldMap,
      List<Vehicle> surfaceShips,
      BotIntent intent,
      BotGoal goal) {
    BotNavigation plan = ship.surface().botNavigation;
    long tick = (long) Math.floor(session.nowSeconds * 10 + 1e-6);
    Vehicle contact = goal.kind() == BotGoal.Kind.ENEMY ? goal.target() : null;
    Vector2 retreatFrom = null;
    if (ship instanceof Submarine submarine) {
      List<Vehicle> contacts =
          ship.isFullySubmerged()
              ? visibilityCache.candidates(ship, SubmarineBot.UNDERWATER_RANGE)
              : visibilityCache.candidates(ship, RadarService.RADAR_RANGE).stream()
                  .filter(
                      other ->
                          session.isUnderwaterBotContact(ship, other)
                              || visibilityCache.isVisible(ship, other, RadarService.RADAR_RANGE))
                  .toList();
      boolean ascentShot =
          ship.isFullySubmerged()
              && contacts.stream()
                  .filter(other -> !other.teamId().equals(ship.teamId()))
                  .filter(
                      other ->
                          "active".equals(other.state())
                              && other.isOnSurface()
                              && !other.isScoutPlane())
                  .anyMatch(other -> session.submarineHasAscentShot(ship, other));
      retreatFrom = submarine.bot().update(ship, contacts, session.nowSeconds, ascentShot);
    }
    boolean routeUpdated = intent.routeUpdated();
    if (routeUpdated) {
      BotNavigation.Course desired = courseForGoal(ship, contact, goal);
      plan.route =
          BotNavigation.freeRoute(
              ship, desired, BotNavigation.ROUTE_RANGE, navigationService, worldMap);
    }
    if (plan.encounterDue(tick))
      plan.planEncounter(ship, surfaceShips, navigationService, worldMap);
    if (plan.forwardDue(tick)) plan.checkForward(ship, navigationService, worldMap);
    plan.checkNear(ship, surfaceShips, navigationService, worldMap);

    // Emergency rules have highest priority. They never overwrite a stored route/encounter plan.
    if (escapeBlockedWater(ship, navigationService, worldMap)) return;
    Optional<Torpedo> threat = visibleIncomingTorpedo(ship);
    if (threat.isPresent()) {
      evadeTorpedo(ship, threat.get(), navigationService, worldMap);
      return;
    }
    if (ship.applyGlancingRamBackoff(session.nowSeconds)) return;
    BotNavigation.Course urgent =
        retreatFrom == null
            ? null
            : new BotNavigation.Course(
                bearingTo(ship, ship.position().add(ship.position().subtract(retreatFrom))),
                ENGINE_FULL);
    BotNavigation.Course aircraft =
        plan.aircraftEvasion == null
            ? null
            : plan.aircraftEvasion.course(ship, session.nowSeconds, navigationService, worldMap);
    BotNavigation.Course selected = plan.selected(urgent, aircraft);
    if (selected != null) {
      boolean ordinary =
          contact == null
              && urgent == null
              && aircraft == null
              && plan.near == null
              && plan.forward == null
              && plan.encounter == null
              && !plan.nearbyTraffic
              && plan.submarineSearch == null
              && ship.surface().botSubmarineAttackCourse == null;
      int order =
          plan.acceleration.order(
              selected.engineOrder(), ship.speed(), session.nowSeconds, ordinary);
      ship.applyCommand(
          order,
          BotHelm.rudder(ship.heading(), ship.turnVelocity(), ship.speed(), selected.heading()));
    }
    // Reuse the staggered one-second route slot; blocked shots must not rescan terrain every tick.
    if (contact != null && routeUpdated) fireBotWeapons(ship, contact);
  }

  private double bearingTo(Vehicle ship, Vector2 position) {
    Vector2 offset = position.subtract(ship.position());
    return offset.x() == 0 && offset.z() == 0 ? ship.heading() : Math.atan2(offset.x(), offset.z());
  }

  private BotNavigation.Course courseForGoal(Vehicle ship, Vehicle contact, BotGoal goal) {
    Vector2 destination = new Vector2(goal.x(), goal.z());
    int order =
        switch (goal.kind()) {
          case ENEMY ->
              botAttackEngineOrder(
                  ship,
                  contact,
                  ship.position().distanceTo(destination),
                  session.relativeBearing(ship, destination));
          case LAST_KNOWN_COURSE, SEARCH -> ENGINE_FLANK;
          case RETAINED_COURSE -> ship.surface().botNavigation.route.engineOrder();
          case PATROL, NONE ->
              botAttackEngineOrder(
                  ship,
                  null,
                  ship.position().distanceTo(destination),
                  session.relativeBearing(ship, destination));
        };
    return new BotNavigation.Course(bearingTo(ship, destination), order);
  }

  private boolean escapeBlockedWater(
      Vehicle ship, NavigationService navigationService, WorldMap worldMap) {
    BotCommand command =
        plannedBotEscapes == null
            ? escapeCommand(ship, navigationService, worldMap)
            : plannedBotEscapes.get(ship);
    if (command == null) return false;
    command.apply(ship);
    return true;
  }

  private BotCommand escapeCommand(
      Vehicle ship, NavigationService navigationService, WorldMap worldMap) {
    Vector2 forward = Vector2.fromHeading(ship.heading());
    double lookAheadDistance = MathSupport.clamp(Math.abs(ship.speed()) * 6.0 + 22.0, 24.0, 78.0);
    Vector2 nearLookAhead = ship.position().add(forward.scale(4));
    Vector2 farLookAhead = ship.position().add(forward.scale(lookAheadDistance));
    boolean blockedHere =
        navigationService.isShipBlocked(ship.position(), ship.heading(), worldMap);
    boolean blockedNear = navigationService.isShipBlocked(nearLookAhead, ship.heading(), worldMap);
    boolean blockedAhead =
        blockedNear
            || navigationService.isShipBlocked(
                ship.position().add(forward.scale(14)), ship.heading(), worldMap)
            || navigationService.isShipBlocked(farLookAhead, ship.heading(), worldMap);

    if (session.nowSeconds < ship.surface().botForwardEscapeUntil) {
      return new BotCommand(
          ENGINE_ONE_THIRD, rudderTowardHeading(ship, ship.surface().botEscapeHeading));
    }
    // A failed reverse is a new situation: discard the old heading and plan farther once.
    if ((session.nowSeconds < ship.surface().botReverseUntil || ship.speed() < -.5)
        && navigationService.isShipMovementBlocked(ship.position(), ship.heading(), -1, worldMap)) {
      ship.surface().botReverseUntil = 0;
      ship.surface().botEscapeHeading =
          chooseSafeEscapeHeading(ship, navigationService, worldMap, true);
      ship.surface().botEscapeUntil = session.nowSeconds + 12;
      ship.surface().botForwardEscapeUntil = session.nowSeconds + 6;
      return new BotCommand(
          ENGINE_ONE_THIRD, rudderTowardHeading(ship, ship.surface().botEscapeHeading));
    }

    // Collision rollback leaves the current position clear even when the next step is blocked.
    // Back away before attempting the turn, and finish that manoeuvre before chasing again.
    if ((session.nowSeconds < ship.surface().botReverseUntil
            || (blockedNear && Math.abs(ship.speed()) < .5))
        && !navigationService.isShipMovementBlocked(
            ship.position(), ship.heading(), -1, worldMap)) {
      double safeHeading = retainedEscapeHeading(ship, navigationService, worldMap);
      if (session.nowSeconds >= ship.surface().botReverseUntil)
        ship.surface().botReverseUntil = session.nowSeconds + 6;
      return new BotCommand(ENGINE_FULL_ASTERN, -rudderTowardHeading(ship, safeHeading));
    }

    if (blockedHere) {
      boolean forwardClear =
          !navigationService.isShipMovementBlocked(
              ship.position(), ship.heading(), EngineOrders.speedFor(ENGINE_SLOW), worldMap);
      boolean asternClear =
          !navigationService.isShipMovementBlocked(
              ship.position(), ship.heading(), EngineOrders.speedFor(ENGINE_FULL_ASTERN), worldMap);
      if (ship.speed() >= 0 && forwardClear) {
        return new BotCommand(ENGINE_SLOW, 0);
      }
      if (asternClear) {
        double safeHeading = retainedEscapeHeading(ship, navigationService, worldMap);
        if (session.nowSeconds >= ship.surface().botReverseUntil)
          ship.surface().botReverseUntil = session.nowSeconds + 6;
        return new BotCommand(ENGINE_FULL_ASTERN, -rudderTowardHeading(ship, safeHeading));
      }
      if (forwardClear) {
        return new BotCommand(ENGINE_SLOW, 0);
      }
      double safeHeading = retainedEscapeHeading(ship, navigationService, worldMap);
      return new BotCommand(ENGINE_SLOW, rudderTowardHeading(ship, safeHeading));
    }

    if (blockedAhead) {
      double safeHeading = retainedEscapeHeading(ship, navigationService, worldMap);
      int rudder = rudderTowardHeading(ship, safeHeading);
      return new BotCommand(ENGINE_ONE_THIRD, rudder);
    }

    if (session.nowSeconds < ship.surface().botEscapeUntil) {
      return new BotCommand(
          ENGINE_ONE_THIRD, rudderTowardHeading(ship, ship.surface().botEscapeHeading));
    }

    return null;
  }

  private double retainedEscapeHeading(
      Vehicle ship, NavigationService navigationService, WorldMap worldMap) {
    if (session.nowSeconds >= ship.surface().botEscapeUntil) {
      ship.surface().botEscapeHeading = chooseSafeEscapeHeading(ship, navigationService, worldMap);
      ship.surface().botEscapeUntil = session.nowSeconds + 12;
    }
    return ship.surface().botEscapeHeading;
  }

  private double chooseSafeEscapeHeading(
      Vehicle ship, NavigationService navigationService, WorldMap worldMap) {
    return chooseSafeEscapeHeading(ship, navigationService, worldMap, false);
  }

  private double chooseSafeEscapeHeading(
      Vehicle ship, NavigationService navigationService, WorldMap worldMap, boolean farther) {
    double bestHeading = MathSupport.normalizeAngle(ship.heading() + Math.PI);
    double bestScore = Double.NEGATIVE_INFINITY;
    for (int step = -12; step <= 12; step += 1) {
      double offset = step * (Math.PI / 12.0);
      double heading = MathSupport.normalizeAngle(ship.heading() + offset);
      double score = escapeCourseScore(ship, heading, navigationService, worldMap, farther);
      if (score > bestScore) {
        bestScore = score;
        bestHeading = heading;
      }
    }
    return bestHeading;
  }

  private double escapeCourseScore(
      Vehicle ship,
      double heading,
      NavigationService navigationService,
      WorldMap worldMap,
      boolean farther) {
    double score = 0;
    Vector2 forward = Vector2.fromHeading(heading);
    double[] distances =
        farther
            ? new double[] {4, 8, 12, 18, 34, 58, 88, 128, 170, 240, 320, BotNavigation.ROUTE_RANGE}
            : new double[] {4, 8, 12, 18, 34, 58, 88, 128, 170};
    for (int index = 0; index < distances.length; index += 1) {
      Vector2 sample = ship.position().add(forward.scale(distances[index]));
      if (navigationService.isShipBlocked(sample, heading, worldMap)) {
        // A clear point beyond an obstruction is not a reachable escape route.
        return -5000 + index * 250;
      }
      score += distances[index] * (1.0 + index * 0.18);
    }

    double turn = Math.abs(MathSupport.normalizeAngle(heading - ship.heading()));
    score -= turn * 18;
    if (turn > Math.PI * 0.82) {
      score -= 35;
    }
    score += Math.sin(session.stablePhase(ship.id()) + heading * 1.7) * 2.5;
    return score;
  }

  private int rudderTowardHeading(Vehicle ship, double heading) {
    return BotHelm.rudder(ship.heading(), ship.turnVelocity(), 1, heading);
  }

  private void applyBotCommand(
      Vehicle ship,
      int engineOrder,
      int rudder,
      NavigationService navigationService,
      WorldMap worldMap) {
    if (pendingBotCourses != null) {
      pendingBotCourses.add(new BotCourseRequest(ship, engineOrder, rudder));
    } else {
      courseCommand(ship, engineOrder, rudder, navigationService, worldMap).apply(ship);
    }
  }

  private BotCommand courseCommand(
      Vehicle ship,
      int engineOrder,
      int rudder,
      NavigationService navigationService,
      WorldMap worldMap) {
    if (EngineOrders.speedFor(engineOrder) <= 0) {
      return new BotCommand(engineOrder, rudder);
    }

    int cruisingEngineOrder = Math.max(engineOrder, ENGINE_HALF);
    double plannedHeading = plannedBotCourseHeading(ship, rudder);
    if (isBotCourseSafe(ship, plannedHeading, navigationService, worldMap)) {
      return new BotCommand(cruisingEngineOrder, rudder);
    }

    double escapeHeading = retainedEscapeHeading(ship, navigationService, worldMap);
    return new BotCommand(
        Math.min(engineOrder, ENGINE_SLOW), rudderTowardHeading(ship, escapeHeading));
  }

  private double plannedBotCourseHeading(Vehicle ship, int rudder) {
    double turn = MathSupport.clamp(rudder / 35.0, -1, 1);
    return MathSupport.normalizeAngle(ship.heading() + turn * 0.48);
  }

  private boolean isBotCourseSafe(
      Vehicle ship, double heading, NavigationService navigationService, WorldMap worldMap) {
    Vector2 forward = Vector2.fromHeading(heading);
    double[] distances = {28, 55, 92, 138};
    for (double distance : distances) {
      Vector2 sample = ship.position().add(forward.scale(distance));
      if (navigationService.isShipBlocked(sample, heading, worldMap)) {
        return false;
      }
    }
    return true;
  }

  private Optional<Torpedo> visibleIncomingTorpedo(Vehicle ship) {
    return session.torpedoes.stream()
        .filter(torpedo -> "running".equals(torpedo.state()))
        .filter(torpedo -> botCanSeeIncomingTorpedo(ship, torpedo))
        .min(
            (left, right) ->
                Double.compare(
                    ship.position().distanceTo(left.position()),
                    ship.position().distanceTo(right.position())));
  }

  private boolean botCanSeeIncomingTorpedo(Vehicle ship, Torpedo torpedo) {
    if (!session.torpedoCanDamageShip(torpedo, ship)) return false;
    Vector2 lookoutPosition =
        ship.position()
            .add(Vector2.fromHeading(ship.heading()).scale(BOT_TORPEDO_LOOKOUT_FORWARD_OFFSET));
    if (lookoutPosition.distanceTo(torpedo.position()) > BOT_TORPEDO_EVADE_RANGE) {
      return false;
    }

    double bearingFromLookout =
        MathSupport.normalizeAngle(
            session.angleTo(torpedo.position(), lookoutPosition) - ship.heading());
    if (Math.abs(bearingFromLookout) > BOT_TORPEDO_LOOKOUT_ARC) {
      return false;
    }

    double incomingAngle =
        session.angularDistance(
            session.angleTo(ship.position(), torpedo.position()), torpedo.heading());
    if (incomingAngle > BOT_TORPEDO_INCOMING_ARC) {
      return false;
    }

    return torpedoTrackDistanceToShip(torpedo, ship.position()) <= BOT_TORPEDO_THREAT_CORRIDOR;
  }

  private double torpedoTrackDistanceToShip(Torpedo torpedo, Vector2 shipPosition) {
    Vector2 forward = Vector2.fromHeading(torpedo.heading());
    Vector2 toShip = shipPosition.subtract(torpedo.position());
    double alongTrack = toShip.x() * forward.x() + toShip.z() * forward.z();
    if (alongTrack < 0) {
      return Double.POSITIVE_INFINITY;
    }
    Vector2 closestPoint = torpedo.position().add(forward.scale(alongTrack));
    return closestPoint.distanceTo(shipPosition);
  }

  private void evadeTorpedo(
      Vehicle ship, Torpedo torpedo, NavigationService navigationService, WorldMap worldMap) {
    double side = session.relativeBearing(ship, torpedo.position()) >= 0 ? -1 : 1;
    int rudder = (int) (side * 35);
    applyBotCommand(ship, ENGINE_FULL, rudder, navigationService, worldMap);
  }

  private Optional<Vector2> nearestLandCenter(Vector2 position, WorldMap worldMap) {
    return worldMap.landmasses().stream()
        .map(landmass -> new Vector2(landmass.x(), landmass.z()))
        .min(
            (left, right) -> Double.compare(position.distanceTo(left), position.distanceTo(right)));
  }

  private void fireBotWeapons(Vehicle ship, Vehicle target) {
    double distance = ship.position().distanceTo(target.position());
    double targetBearing = session.relativeBearing(ship, target.position());
    double aimError =
        Math.sin(session.nowSeconds * .11 + session.stablePhase(ship.id())) * BOT_AIM_ERROR;
    double steerError = MathSupport.normalizeAngle(targetBearing + aimError);
    boolean closeInFront =
        distance <= BOT_CLOSE_FIRE_RANGE && Math.abs(targetBearing) <= BOT_CLOSE_FIRE_ARC;
    boolean aimedShot =
        distance >= BOT_FIRE_MIN_RANGE
            && distance <= BOT_FIRE_MAX_RANGE
            && Math.abs(steerError) <= BOT_FIRE_ARC;
    if (!SCOUT_PLANE_EXPERIMENT_PEACEFUL_BOTS
        && !target.isAtPeriscopeDepth()
        && (closeInFront || aimedShot)) {
      session.fireTorpedoAtTarget(
          ship,
          target,
          10.5 + Math.abs(Math.sin(session.stablePhase(ship.id()))) * 3.0,
          aimError * 0.65);
    }
  }

  private int botAttackEngineOrder(
      Vehicle ship, Vehicle target, double distance, double targetBearing) {
    boolean humanTarget = target != null && session.isHumanControlled(target);
    if (target != null && ship.isSubmarine() && !ship.isOnSurface()) {
      return ship.submarineBot().attackEngineOrder(ship, target, distance, targetBearing);
    }
    if (target == null) return ENGINE_HALF; // Map-centre patrol stays relaxed.
    if (!humanTarget) {
      if (distance <= BOT_APPROACH_SLOW_RANGE) return ENGINE_HALF;
      return distance <= BotNavigation.RADAR_RING ? ENGINE_TWO_THIRDS : ENGINE_FULL;
    }
    if (target.isAtPeriscopeDepth()) {
      return ship.isSubmarine() ? ENGINE_FULL : ENGINE_FLANK;
    }
    // Distance alone determines when a surface boat must use its full pursuit speed.
    if (!ship.isSubmarine() && distance >= BotNavigation.RADAR_RING) {
      return ENGINE_FLANK;
    }
    double absoluteBearing = Math.abs(targetBearing);
    if (distance < BOT_RAM_RANGE) {
      return target != null && target.isSubmarine() && !ship.isSubmarine()
          ? ENGINE_FLANK
          : absoluteBearing <= Math.toRadians(30) ? ENGINE_HALF : ENGINE_FULL;
    }
    if (humanTarget && Math.abs(ship.speed()) < 0.8 && distance <= BOT_APPROACH_SLOW_RANGE) {
      return ENGINE_FULL;
    }
    if (distance < BOT_CLOSE_MANEUVER_RANGE) {
      if (absoluteBearing <= Math.toRadians(30)) {
        return ENGINE_HALF;
      }
      if (absoluteBearing <= BOT_CLOSE_FIRE_ARC) {
        return ENGINE_HALF;
      }
      return ENGINE_FULL;
    }
    if (distance > BOT_RADAR_INTERCEPT_RANGE) {
      return ENGINE_FULL;
    }
    if (distance > BOT_APPROACH_SLOW_RANGE) {
      return ENGINE_TWO_THIRDS;
    }
    return ENGINE_HALF;
  }

  private record BotTorpedoSolution(Vector2 targetPosition, double heading, boolean canRelease) {}

  private record BotBombingSolution(Vector2 targetPosition, boolean canRelease) {}
}
