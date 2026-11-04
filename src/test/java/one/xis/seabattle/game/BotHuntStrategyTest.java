package one.xis.seabattle.game;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class BotHuntStrategyTest {
  private final RadarService radar = new RadarService();
  private final NavigationService navigation = new NavigationService();

  private GameSession session(BotTargetSelection selection, String depth) {
    var setup =
        new GameSetup(
            "hunt-strategy",
            new WorldMap(1, List.of()),
            List.of(
                new FleetSetup(
                    "light",
                    List.of(
                        boat("observer", 0, -30),
                        boat("escort-a", 200, -200),
                        boat("escort-b", -200, -200))),
                new FleetSetup(
                    "dark",
                    List.of(
                        new VehicleSetup(
                            "target",
                            "dark",
                            new Vector2(0, 0),
                            0,
                            "human",
                            2,
                            0,
                            9999,
                            "submarine",
                            0)))),
            List.of());
    GameSession session = new GameSession(setup, 1, false, BotCalculation.standard(), selection);
    setDepth(session, 0, depth);
    return session;
  }

  private VehicleSetup boat(String id, double x, double z) {
    return new VehicleSetup(
        id, "light", new Vector2(x, z), 0, "bot", 2, 0, 9999, "torpedo-boat", 0);
  }

  private void setDepth(GameSession session, double z, String depth) {
    Vehicle target = session.calculationShips().get(3);
    target.applyPlayerState(
        new PlayerStateUpdate(
            "human",
            "dark",
            0,
            z,
            0,
            0,
            0,
            2,
            0,
            0,
            true,
            "submarine",
            depth.equals("periscope") ? -5.58 : -19.26,
            0,
            null,
            null,
            null,
            null,
            depth),
        navigation,
        session.worldMap());
  }

  private void tick(GameSession session) {
    session.update(.1, radar, navigation, session.worldMap());
  }

  @Test
  void standardSelectionRecruitsHuntersFromVisualPerception() {
    GameSession session = session(BotTargetSelection.standard(), "periscope");
    tick(session);
    assertEquals(
        3, session.calculationShips().stream().filter(v -> v.submarineSearch() != null).count());
    assertTrue(session.snapshot().submarineDetections().containsKey("target"));
  }

  @Test
  void customRankingDoesNotImplicitlyRecruitEvenWhenItExplicitlyReusesStandardRanking() {
    AtomicInteger visibleSubmarines = new AtomicInteger();
    GameSession session =
        session(
            (situation, tick) -> {
              for (Vehicle candidate : situation.candidates()) {
                if (candidate instanceof Submarine && situation.isVisible(candidate))
                  visibleSubmarines.incrementAndGet();
              }
              return situation.standardTarget();
            },
            "periscope");
    tick(session);
    assertTrue(visibleSubmarines.get() > 0, "The custom selector really received the submarine");
    assertTrue(session.calculationShips().stream().allMatch(v -> v.submarineSearch() == null));
    assertNull(((SurfaceVehicle) session.calculationShips().get(0)).botSubmarineAttackCourse);
    assertTrue(session.snapshot().submarineDetections().isEmpty());
  }

  @Test
  void confirmedSonarContactIsDeliveredToCustomStrategyWithoutStartingAStandardHunt()
      throws Exception {
    AtomicInteger reports = new AtomicInteger();
    BotTargetSelection selection =
        new BotTargetSelection() {
          @Override
          public Vehicle select(BotTargetSituation situation, long tick) {
            return null;
          }

          @Override
          public void depthChargeContact(
              BotTargetSituation situation, Submarine target, long tick) {
            assertEquals("observer", situation.observerId());
            assertEquals("target", target.id());
            assertTrue(situation.hasSonarContact(target));
            assertFalse(situation.isVisible(target));
            reports.incrementAndGet();
          }
        };
    GameSession session = session(selection, "submerged");
    Method drop =
        GameSession.class.getDeclaredMethod(
            "tryBotDepthCharges", Vehicle.class, RadarService.VisibilityCache.class);
    drop.setAccessible(true);
    Vehicle observer = session.calculationShips().get(0);
    for (double distance : new double[] {100, 60, 20, 20, 23, 24}) {
      setDepth(session, observer.position().z() + distance, "submerged");
      drop.invoke(
          session, observer, radar.visibilityCache(session.worldMap(), session.calculationShips()));
    }
    assertEquals(
        1, reports.get(), "Report only the actual salvo, not every weaker echo while reloading");
    assertFalse(session.snapshot().depthCharges().isEmpty());
    assertTrue(session.snapshot().submarineDetections().isEmpty());
    assertTrue(session.calculationShips().stream().allMatch(v -> v.submarineSearch() == null));
  }

  @Test
  void navigationIgnoresStoredHuntOrderWhenCustomStrategyChoosesPatrol() {
    GameSession session = session((situation, tick) -> null, "submerged");
    TorpedoBoat observer = (TorpedoBoat) session.calculationShips().get(0);
    observer.submarineSearch(
        new BotSubmarineHunt.Search(
            new BotSubmarineHunt.Area(new Vector2(1000, 1000), 60), observer.position()));
    tick(session);
    assertEquals(
        4,
        observer.engineOrder(),
        "Patrol uses normal one-third startup despite an ignored old hunt order");
  }

  @Test
  void searchGoalUsesFullSpeedWithoutRequiringAnyHiddenHuntState() {
    BotTargetSelection selection =
        new BotTargetSelection() {
          @Override
          public Vehicle select(BotTargetSituation situation, long tick) {
            return null;
          }

          @Override
          public BotGoal goal(
              BotTargetSituation situation, Vehicle contact, boolean advanceSearch) {
            return new BotGoal(
                BotGoal.Kind.SEARCH, null, -1, situation.observer().position().x(), 1000);
          }
        };
    GameSession session = session(selection, "submerged");
    tick(session);
    TorpedoBoat observer = (TorpedoBoat) session.calculationShips().get(0);
    assertNull(observer.submarineSearch());
    assertEquals(8, observer.engineOrder(), "The explicit search goal determines pursuit speed");
  }

  @Test
  void labWrapperForwardsObservationHooksAndKeepsStandardHuntingEnabled() {
    String script =
        """
        cell: 25
        map:
        ..1..
        ..2..
        .....
        .....
        .3...
        objects:
        1: boat light bot
        2: submarine dark human [depth: periscope]
        3: boat light bot
        """;
    AtomicInteger searchGoals = new AtomicInteger();
    BotScenario.parse(script)
        .run(
            () ->
                (situation, tick, goals) -> {
                  assertNotNull(
                      situation.vehicles().stream()
                          .filter(v -> v.id().equals("light-S1"))
                          .findFirst()
                          .orElseThrow()
                          .submarineSearch());
                  searchGoals.addAndGet(
                      (int)
                          goals.values().stream()
                              .filter(g -> g.kind() == BotGoal.Kind.SEARCH)
                              .count());
                },
            1);
    assertTrue(searchGoals.get() > 0);
  }
}
