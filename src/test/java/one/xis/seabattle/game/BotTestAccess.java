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
            new StandardBotTargetSelection(session),
            "scoutPlaneAttackHuman",
            new Class[] {Vehicle.class},
            plane);
  }

  private boolean shouldForceHumanScoutPlaneTarget(Vehicle plane, Optional<Vehicle> human)
      throws Exception {
    return (boolean)
        call(
            new StandardBotTargetSelection(session),
            "shouldForceHumanScoutPlaneTarget",
            new Class[] {Vehicle.class, Optional.class},
            plane,
            human);
  }

  private Optional<Vehicle> chooseBotTarget(Vehicle boat, List<Vehicle> targets) throws Exception {
    return (Optional<Vehicle>)
        call(
            new StandardBotTargetSelection(session),
            "chooseBotTarget",
            new Class[] {Vehicle.class, List.class},
            boat,
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

  private BotNavigation.Course desiredBotCourse(Vehicle boat, Vehicle target, WorldMap world)
      throws Exception {
    var context =
        new BotTargetSituation(
            session,
            boat,
            List.of(),
            new RadarService().visibilityCache(world, session.calculationShips()),
            Map.of());
    BotGoal goal = new StandardBotTargetSelection().goal(context, target, true);
    return (BotNavigation.Course)
        call(
            new StandardBotCalculation(session),
            "courseForGoal",
            new Class[] {Vehicle.class, Vehicle.class, BotGoal.class},
            boat,
            target,
            goal);
  }

  private int botAttackEngineOrder(Vehicle boat, Vehicle target, double distance, double bearing)
      throws Exception {
    return (int)
        call(
            new StandardBotCalculation(session),
            "botAttackEngineOrder",
            new Class[] {Vehicle.class, Vehicle.class, double.class, double.class},
            boat,
            target,
            distance,
            bearing);
  }

  private void commandBot(
      Vehicle boat,
      RadarService.VisibilityCache cache,
      NavigationService navigation,
      WorldMap world,
      List<Vehicle> ships)
      throws Exception {
    var intent =
        (GameSession.BotIntent)
            call(
                session,
                "prepareBotIntent",
                new Class[] {Vehicle.class, RadarService.VisibilityCache.class},
                boat,
                cache);
    BotGoal goal =
        new StandardBotTargetSelection()
            .goal(intent.situation(), intent.contact(), intent.routeUpdated());
    call(
        new StandardBotCalculation(session),
        "commandBot",
        new Class[] {
          Vehicle.class,
          RadarService.VisibilityCache.class,
          NavigationService.class,
          WorldMap.class,
          List.class,
          GameSession.BotIntent.class,
          BotGoal.class
        },
        boat,
        cache,
        navigation,
        world,
        ships,
        intent,
        goal);
  }
}
