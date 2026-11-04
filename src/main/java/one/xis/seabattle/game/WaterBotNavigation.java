package one.xis.seabattle.game;

import static one.xis.seabattle.game.GameRules.*;

import java.util.*;
import one.xis.seabattle.game.GameSession.BotIntent;

/** Shared collision avoidance, helm, escape manoeuvres and torpedo fire for water vehicles. */
final class WaterBotNavigation {
  private GameSession session;
  private Map<SurfaceVehicle, BotCommand> plannedEscapes;
  private List<BotCourseRequest> pendingCourses;

  @FunctionalInterface
  interface AttackSpeed<V extends SurfaceVehicle> {
    int engineOrder(V vehicle, Vehicle target, double distance, double bearing);
  }

  private record BotCommand(int engineOrder, int rudder) {
    void apply(SurfaceVehicle vehicle) {
      vehicle.applyCommand(engineOrder, rudder);
    }
  }

  private record BotCourseRequest(SurfaceVehicle vehicle, int engineOrder, int rudder) {}

  WaterBotNavigation() {}

  WaterBotNavigation(GameSession session) {
    this.session = session;
  }

  void beginRound(BotSituation situation) {
    session = situation.session();
    if (!session.parallelBotNavigation) return;
    List<SurfaceVehicle> vehicles =
        situation.round().waterVehicles().stream()
            .filter(Vehicle::isBotControlled)
            .map(SurfaceVehicle.class::cast)
            .toList();
    List<BotCommand> commands =
        BotNavigationStream.map(
            vehicles, vehicle -> escapeCommand(vehicle, situation.navigation(), situation.world()));
    plannedEscapes = new HashMap<>();
    for (int i = 0; i < vehicles.size(); i++) plannedEscapes.put(vehicles.get(i), commands.get(i));
    pendingCourses = new ArrayList<>();
  }

  void finishRound(BotSituation situation) {
    if (pendingCourses == null) return;
    List<BotCommand> commands =
        BotNavigationStream.map(
            pendingCourses,
            request ->
                courseCommand(
                    request.vehicle(),
                    request.engineOrder(),
                    request.rudder(),
                    situation.navigation(),
                    situation.world()));
    for (int i = 0; i < commands.size(); i++)
      commands.get(i).apply(pendingCourses.get(i).vehicle());
  }

  void clearRound() {
    plannedEscapes = null;
    pendingCourses = null;
  }

  <V extends SurfaceVehicle> void calculate(
      V vehicle,
      BotSituation situation,
      long tick,
      BotGoal goal,
      Vector2 escapeFromThreat,
      AttackSpeed<V> attackSpeed) {
    session = situation.session();
    NavigationService navigationService = situation.navigation();
    WorldMap worldMap = situation.world();
    List<Vehicle> waterVehicles = situation.round().waterVehicles();
    BotIntent intent = situation.round().boats().get(vehicle);
    BotNavigation plan = vehicle.botNavigation;
    Vehicle contact = goal.kind() == BotGoal.Kind.ENEMY ? goal.target() : null;
    boolean routeUpdated = intent.routeUpdated();
    if (routeUpdated) {
      BotNavigation.Course desired = courseForGoal(vehicle, contact, goal, attackSpeed);
      plan.route =
          BotNavigation.freeRoute(
              vehicle, desired, BotNavigation.ROUTE_RANGE, navigationService, worldMap);
    }
    if (plan.encounterDue(tick))
      plan.planEncounter(vehicle, waterVehicles, navigationService, worldMap);
    if (plan.forwardDue(tick)) plan.checkForward(vehicle, navigationService, worldMap);
    plan.checkNear(vehicle, waterVehicles, navigationService, worldMap);

    // Emergency rules have highest priority. They never overwrite a stored route/encounter plan.
    if (vehicle.hasGlancingRamBackoff(session.nowSeconds)) {
      boolean rearBlocked =
          navigationService.isShipMovementBlocked(
                  vehicle.position(), vehicle.heading(), -1, worldMap)
              || !BotNavigation.reverseTrafficClear(vehicle, waterVehicles);
      if (rearBlocked) {
        vehicle.cancelGlancingRamBackoff();
        vehicle.applyCommand(ENGINE_STOP, 0);
      } else vehicle.applyGlancingRamBackoff(session.nowSeconds);
      return;
    }
    if (escapeBlockedWater(vehicle, waterVehicles, navigationService, worldMap)) return;
    Optional<Torpedo> threat = visibleIncomingTorpedo(vehicle);
    if (threat.isPresent()) {
      evadeTorpedo(vehicle, threat.get(), navigationService, worldMap);
      return;
    }
    BotNavigation.Course urgent =
        escapeFromThreat == null
            ? null
            : new BotNavigation.Course(
                bearingTo(
                    vehicle, vehicle.position().add(vehicle.position().subtract(escapeFromThreat))),
                ENGINE_FULL);
    BotNavigation.Course aircraft =
        plan.aircraftEvasion == null
            ? null
            : plan.aircraftEvasion.course(vehicle, session.nowSeconds, navigationService, worldMap);
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
              && goal.kind() != BotGoal.Kind.SEARCH
              && goal.kind() != BotGoal.Kind.LAST_KNOWN_COURSE;
      int order =
          plan.acceleration.order(
              selected.engineOrder(), vehicle.speed(), session.nowSeconds, ordinary);
      vehicle.applyCommand(
          order,
          BotHelm.rudder(
              vehicle.heading(), vehicle.turnVelocity(), vehicle.speed(), selected.heading()));
    }
    // Reuse the staggered one-second route slot; blocked shots must not rescan terrain every tick.
    if (contact != null && routeUpdated) fireBotWeapons(vehicle, contact);
  }

  private double bearingTo(SurfaceVehicle vehicle, Vector2 position) {
    Vector2 offset = position.subtract(vehicle.position());
    return offset.x() == 0 && offset.z() == 0
        ? vehicle.heading()
        : Math.atan2(offset.x(), offset.z());
  }

  <V extends SurfaceVehicle> BotNavigation.Course courseForGoal(
      V vehicle, Vehicle contact, BotGoal goal, AttackSpeed<V> attackSpeed) {
    Vector2 destination = new Vector2(goal.x(), goal.z());
    int order =
        switch (goal.kind()) {
          case ENEMY ->
              attackSpeed.engineOrder(
                  vehicle,
                  contact,
                  vehicle.position().distanceTo(destination),
                  session.relativeBearing(vehicle, destination));
          case LAST_KNOWN_COURSE, SEARCH -> ENGINE_FLANK;
          case RETAINED_COURSE -> vehicle.botNavigation.route.engineOrder();
          case PATROL, NONE ->
              attackSpeed.engineOrder(
                  vehicle,
                  null,
                  vehicle.position().distanceTo(destination),
                  session.relativeBearing(vehicle, destination));
        };
    return new BotNavigation.Course(bearingTo(vehicle, destination), order);
  }

  private boolean escapeBlockedWater(
      SurfaceVehicle vehicle,
      List<Vehicle> waterVehicles,
      NavigationService navigationService,
      WorldMap worldMap) {
    BotCommand command =
        plannedEscapes == null
            ? escapeCommand(vehicle, navigationService, worldMap)
            : plannedEscapes.get(vehicle);
    if (command == null) return false;
    if (command.engineOrder() < ENGINE_STOP
        && !BotNavigation.reverseTrafficClear(vehicle, waterVehicles)) {
      vehicle.applyCommand(ENGINE_STOP, 0);
      return true;
    }
    command.apply(vehicle);
    return true;
  }

  private BotCommand escapeCommand(
      SurfaceVehicle vehicle, NavigationService navigationService, WorldMap worldMap) {
    Vector2 forward = Vector2.fromHeading(vehicle.heading());
    double lookAheadDistance =
        MathSupport.clamp(Math.abs(vehicle.speed()) * 6.0 + 22.0, 24.0, 78.0);
    Vector2 nearLookAhead = vehicle.position().add(forward.scale(4));
    Vector2 farLookAhead = vehicle.position().add(forward.scale(lookAheadDistance));
    boolean blockedHere =
        navigationService.isShipBlocked(vehicle.position(), vehicle.heading(), worldMap);
    boolean blockedNear =
        navigationService.isShipBlocked(nearLookAhead, vehicle.heading(), worldMap);
    boolean blockedAhead =
        blockedNear
            || navigationService.isShipBlocked(
                vehicle.position().add(forward.scale(14)), vehicle.heading(), worldMap)
            || navigationService.isShipBlocked(farLookAhead, vehicle.heading(), worldMap);

    if (session.nowSeconds < vehicle.botReverseUntil && !blockedHere && !blockedNear) {
      vehicle.botReverseUntil = 0;
      vehicle.botEscapeUntil = session.nowSeconds + 4;
    }

    if (session.nowSeconds < vehicle.botForwardEscapeUntil) {
      return new BotCommand(
          ENGINE_ONE_THIRD, rudderTowardHeading(vehicle, vehicle.botEscapeHeading));
    }
    // A failed reverse is a new situation: discard the old heading and plan farther once.
    if ((session.nowSeconds < vehicle.botReverseUntil || vehicle.speed() < -.5)
        && navigationService.isShipMovementBlocked(
            vehicle.position(), vehicle.heading(), -1, worldMap)) {
      vehicle.botReverseUntil = 0;
      vehicle.botEscapeHeading =
          chooseSafeEscapeHeading(vehicle, navigationService, worldMap, true);
      vehicle.botEscapeUntil = session.nowSeconds + 12;
      vehicle.botForwardEscapeUntil = session.nowSeconds + 6;
      return new BotCommand(
          ENGINE_ONE_THIRD, rudderTowardHeading(vehicle, vehicle.botEscapeHeading));
    }

    // Collision rollback leaves the current position clear even when the next step is blocked.
    // Back away before attempting the turn, and finish that manoeuvre before chasing again.
    if ((session.nowSeconds < vehicle.botReverseUntil
            || (blockedNear && Math.abs(vehicle.speed()) < .5))
        && !navigationService.isShipMovementBlocked(
            vehicle.position(), vehicle.heading(), -1, worldMap)) {
      double safeHeading = retainedEscapeHeading(vehicle, navigationService, worldMap);
      if (session.nowSeconds >= vehicle.botReverseUntil)
        vehicle.botReverseUntil = session.nowSeconds + 2;
      return new BotCommand(ENGINE_ASTERN, -rudderTowardHeading(vehicle, safeHeading));
    }

    if (blockedHere) {
      boolean forwardClear =
          !navigationService.isShipMovementBlocked(
              vehicle.position(), vehicle.heading(), EngineOrders.speedFor(ENGINE_SLOW), worldMap);
      boolean asternClear =
          !navigationService.isShipMovementBlocked(
              vehicle.position(),
              vehicle.heading(),
              EngineOrders.speedFor(ENGINE_FULL_ASTERN),
              worldMap);
      if (vehicle.speed() >= 0 && forwardClear) {
        return new BotCommand(ENGINE_SLOW, 0);
      }
      if (asternClear) {
        double safeHeading = retainedEscapeHeading(vehicle, navigationService, worldMap);
        if (session.nowSeconds >= vehicle.botReverseUntil)
          vehicle.botReverseUntil = session.nowSeconds + 2;
        return new BotCommand(ENGINE_ASTERN, -rudderTowardHeading(vehicle, safeHeading));
      }
      if (forwardClear) {
        return new BotCommand(ENGINE_SLOW, 0);
      }
      double safeHeading = retainedEscapeHeading(vehicle, navigationService, worldMap);
      return new BotCommand(ENGINE_SLOW, rudderTowardHeading(vehicle, safeHeading));
    }

    if (blockedAhead) {
      double safeHeading = retainedEscapeHeading(vehicle, navigationService, worldMap);
      int rudder = rudderTowardHeading(vehicle, safeHeading);
      return new BotCommand(ENGINE_ONE_THIRD, rudder);
    }

    if (session.nowSeconds < vehicle.botEscapeUntil) {
      return new BotCommand(
          ENGINE_ONE_THIRD, rudderTowardHeading(vehicle, vehicle.botEscapeHeading));
    }

    return null;
  }

  private double retainedEscapeHeading(
      SurfaceVehicle vehicle, NavigationService navigationService, WorldMap worldMap) {
    if (session.nowSeconds >= vehicle.botEscapeUntil) {
      vehicle.botEscapeHeading = chooseSafeEscapeHeading(vehicle, navigationService, worldMap);
      vehicle.botEscapeUntil = session.nowSeconds + 12;
    }
    return vehicle.botEscapeHeading;
  }

  private double chooseSafeEscapeHeading(
      SurfaceVehicle vehicle, NavigationService navigationService, WorldMap worldMap) {
    return chooseSafeEscapeHeading(vehicle, navigationService, worldMap, false);
  }

  private double chooseSafeEscapeHeading(
      SurfaceVehicle vehicle,
      NavigationService navigationService,
      WorldMap worldMap,
      boolean farther) {
    double bestHeading = MathSupport.normalizeAngle(vehicle.heading() + Math.PI);
    double bestScore = Double.NEGATIVE_INFINITY;
    for (int step = -12; step <= 12; step += 1) {
      double offset = step * (Math.PI / 12.0);
      double heading = MathSupport.normalizeAngle(vehicle.heading() + offset);
      double score = escapeCourseScore(vehicle, heading, navigationService, worldMap, farther);
      if (score > bestScore) {
        bestScore = score;
        bestHeading = heading;
      }
    }
    return bestHeading;
  }

  private double escapeCourseScore(
      SurfaceVehicle vehicle,
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
      Vector2 sample = vehicle.position().add(forward.scale(distances[index]));
      if (navigationService.isShipBlocked(sample, heading, worldMap)) {
        // A clear point beyond an obstruction is not a reachable escape route.
        return -5000 + index * 250;
      }
      score += distances[index] * (1.0 + index * 0.18);
    }

    double turn = Math.abs(MathSupport.normalizeAngle(heading - vehicle.heading()));
    score -= turn * 18;
    if (turn > Math.PI * 0.82) {
      score -= 35;
    }
    score += Math.sin(session.stablePhase(vehicle.id()) + heading * 1.7) * 2.5;
    return score;
  }

  private int rudderTowardHeading(SurfaceVehicle vehicle, double heading) {
    return BotHelm.rudder(vehicle.heading(), vehicle.turnVelocity(), 1, heading);
  }

  private void applyBotCommand(
      SurfaceVehicle vehicle,
      int engineOrder,
      int rudder,
      NavigationService navigationService,
      WorldMap worldMap) {
    if (pendingCourses != null) {
      pendingCourses.add(new BotCourseRequest(vehicle, engineOrder, rudder));
    } else {
      courseCommand(vehicle, engineOrder, rudder, navigationService, worldMap).apply(vehicle);
    }
  }

  private BotCommand courseCommand(
      SurfaceVehicle vehicle,
      int engineOrder,
      int rudder,
      NavigationService navigationService,
      WorldMap worldMap) {
    if (EngineOrders.speedFor(engineOrder) <= 0) {
      return new BotCommand(engineOrder, rudder);
    }

    int cruisingEngineOrder = Math.max(engineOrder, ENGINE_HALF);
    double plannedHeading = plannedBotCourseHeading(vehicle, rudder);
    if (isBotCourseSafe(vehicle, plannedHeading, navigationService, worldMap)) {
      return new BotCommand(cruisingEngineOrder, rudder);
    }

    double escapeHeading = retainedEscapeHeading(vehicle, navigationService, worldMap);
    return new BotCommand(
        Math.min(engineOrder, ENGINE_SLOW), rudderTowardHeading(vehicle, escapeHeading));
  }

  private double plannedBotCourseHeading(SurfaceVehicle vehicle, int rudder) {
    double turn = MathSupport.clamp(rudder / 35.0, -1, 1);
    return MathSupport.normalizeAngle(vehicle.heading() + turn * 0.48);
  }

  private boolean isBotCourseSafe(
      SurfaceVehicle vehicle,
      double heading,
      NavigationService navigationService,
      WorldMap worldMap) {
    Vector2 forward = Vector2.fromHeading(heading);
    double[] distances = {28, 55, 92, 138};
    for (double distance : distances) {
      Vector2 sample = vehicle.position().add(forward.scale(distance));
      if (navigationService.isShipBlocked(sample, heading, worldMap)) {
        return false;
      }
    }
    return true;
  }

  private Optional<Torpedo> visibleIncomingTorpedo(SurfaceVehicle vehicle) {
    return session.torpedoes.stream()
        .filter(torpedo -> "running".equals(torpedo.state()))
        .filter(torpedo -> botCanSeeIncomingTorpedo(vehicle, torpedo))
        .min(
            (left, right) ->
                Double.compare(
                    vehicle.position().distanceTo(left.position()),
                    vehicle.position().distanceTo(right.position())));
  }

  private boolean botCanSeeIncomingTorpedo(SurfaceVehicle vehicle, Torpedo torpedo) {
    if (!session.torpedoCanDamageShip(torpedo, vehicle)) return false;
    Vector2 lookoutPosition =
        vehicle
            .position()
            .add(Vector2.fromHeading(vehicle.heading()).scale(BOT_TORPEDO_LOOKOUT_FORWARD_OFFSET));
    if (lookoutPosition.distanceTo(torpedo.position()) > BOT_TORPEDO_EVADE_RANGE) {
      return false;
    }

    double bearingFromLookout =
        MathSupport.normalizeAngle(
            session.angleTo(torpedo.position(), lookoutPosition) - vehicle.heading());
    if (Math.abs(bearingFromLookout) > BOT_TORPEDO_LOOKOUT_ARC) {
      return false;
    }

    double incomingAngle =
        session.angularDistance(
            session.angleTo(vehicle.position(), torpedo.position()), torpedo.heading());
    if (incomingAngle > BOT_TORPEDO_INCOMING_ARC) {
      return false;
    }

    return torpedoTrackDistanceToShip(torpedo, vehicle.position()) <= BOT_TORPEDO_THREAT_CORRIDOR;
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
      SurfaceVehicle vehicle,
      Torpedo torpedo,
      NavigationService navigationService,
      WorldMap worldMap) {
    double side = session.relativeBearing(vehicle, torpedo.position()) >= 0 ? -1 : 1;
    int rudder = (int) (side * 35);
    applyBotCommand(vehicle, ENGINE_FULL, rudder, navigationService, worldMap);
  }

  private void fireBotWeapons(SurfaceVehicle vehicle, Vehicle target) {
    double distance = vehicle.position().distanceTo(target.position());
    double targetBearing = session.relativeBearing(vehicle, target.position());
    double aimError =
        Math.sin(session.nowSeconds * .11 + session.stablePhase(vehicle.id())) * BOT_AIM_ERROR;
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
          vehicle,
          target,
          10.5 + Math.abs(Math.sin(session.stablePhase(vehicle.id()))) * 3.0,
          aimError * 0.65);
    }
  }
}
