package one.xis.seabattle.game;

import java.lang.reflect.Method;
import java.util.*;

/** Legacy scenario probes now invoke the extracted implementations, not duplicate session logic. */
final class BotTestAccess {
  private final GameSession session;

  BotTestAccess(GameSession session) {
    this.session = session;
  }

  private Object call(Object strategy, String name, Class<?>[] types, Object... arguments)
      throws Exception {
    Method method = strategy.getClass().getDeclaredMethod(name, types);
    method.setAccessible(true);
    return method.invoke(strategy, arguments);
  }

  private Optional<Vehicle> scoutPlaneAttackHuman(Vehicle plane) throws Exception {
    return (Optional<Vehicle>)
        call(
            new ScoutPlaneBotTargetSelection(),
            "scoutPlaneAttackHuman",
            new Class[] {GameSession.class, ScoutPlane.class},
            session,
            (ScoutPlane) plane);
  }

  private boolean shouldForceHumanScoutPlaneTarget(Vehicle plane, Optional<Vehicle> human)
      throws Exception {
    return (boolean)
        call(
            new ScoutPlaneBotTargetSelection(),
            "shouldForceHumanScoutPlaneTarget",
            new Class[] {GameSession.class, ScoutPlane.class, Optional.class},
            session,
            (ScoutPlane) plane,
            human);
  }

  private Optional<Vehicle> chooseBotTarget(Vehicle vehicle, List<Vehicle> targets)
      throws Exception {
    return (Optional<Vehicle>)
        call(
            new WaterBotTargetSelection(),
            "chooseBotTarget",
            new Class[] {SurfaceVehicle.class, List.class},
            (SurfaceVehicle) vehicle,
            targets);
  }

  private Optional<Vehicle> selectBotScoutPlaneTarget(
      Vehicle plane, List<Vehicle> all, Map<String, Integer> reservations) {
    List<Vehicle> candidates =
        all.stream()
            .filter(v -> !v.teamId().equals(plane.teamId()))
            .filter(v -> !v.isScoutPlane() && !v.isFullySubmerged() && !v.isAtPeriscopeDepth())
            .toList();
    var cache = new RadarService().visibilityCache(session.worldMap(), all);
    return Optional.ofNullable(
        new StandardBotTargetSelection()
            .select(new BotTargetSituation(session, plane, candidates, cache, reservations), 0));
  }

  private BotNavigation.Course desiredBotCourse(Vehicle vehicle, Vehicle target, WorldMap world)
      throws Exception {
    var context =
        new BotTargetSituation(
            session,
            vehicle,
            List.of(),
            new RadarService().visibilityCache(world, session.calculationShips()),
            Map.of());
    BotGoal goal = new StandardBotTargetSelection().goal(context, target, true);
    WaterBotNavigation navigation = new WaterBotNavigation(session);
    if (vehicle instanceof TorpedoBoat torpedoBoat) {
      return navigation.courseForGoal(
          torpedoBoat, target, goal, TorpedoBoatBotCalculation::attackEngineOrder);
    }
    return navigation.courseForGoal(
        (Submarine) vehicle, target, goal, SubmarineBotCalculation::attackEngineOrder);
  }

  private int botAttackEngineOrder(
      Vehicle vehicle, Vehicle target, double distance, double bearing) {
    return vehicle instanceof TorpedoBoat boat
        ? TorpedoBoatBotCalculation.attackEngineOrder(boat, target, distance, bearing)
        : SubmarineBotCalculation.attackEngineOrder((Submarine) vehicle, target, distance, bearing);
  }

  private void commandBot(
      Vehicle vehicle,
      RadarService.VisibilityCache cache,
      NavigationService navigation,
      WorldMap world,
      List<Vehicle> vehicles)
      throws Exception {
    var intent =
        (GameSession.BotIntent)
            call(
                session,
                "prepareBotIntent",
                new Class[] {Vehicle.class, RadarService.VisibilityCache.class},
                vehicle,
                cache);
    BotGoal goal =
        new StandardBotTargetSelection()
            .goal(intent.situation(), intent.contact(), intent.routeUpdated());
    var round =
        new GameSession.BotRound(
            vehicles,
            vehicles,
            cache,
            Map.of(vehicle, intent),
            Map.of(),
            Map.of(vehicle.id(), goal));
    var situation = new BotSituation(session, new RadarService(), navigation, world, round);
    long tick = (long) Math.floor(session.calculationTime() * 10 + 1e-6);
    if (vehicle instanceof TorpedoBoat torpedoBoat)
      new TorpedoBoatBotCalculation().calculate(torpedoBoat, situation, tick, goal);
    else new SubmarineBotCalculation().calculate((Submarine) vehicle, situation, tick, goal);
  }
}
