package one.xis.seabattle.game;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BotProductionStrategiesTest {
  public static final class Selection implements BotTargetSelection {
    static int instances;
    static Vehicle observer, target;

    public Selection() {
      instances++;
    }

    @Override
    public Vehicle select(BotTargetSituation situation, long tick) {
      observer = situation.observer();
      target = situation.candidates().get(0);
      assertInstanceOf(Submarine.class, target);
      assertEquals(.1234567, observer.position().x(), 1e-12);
      assertTrue(situation.isVisible(target));
      return target;
    }

    @Override
    public BotGoal goal(BotTargetSituation situation, Vehicle contact, boolean advance) {
      assertSame(target, contact);
      // A custom destination must reach navigation without being replaced by a hidden policy.
      return new BotGoal(BotGoal.Kind.SEARCH, null, -1, 1234.56789, -321.12345);
    }
  }

  public static final class Movement implements BotCalculation {
    static int instances, calls;

    public Movement() {
      instances++;
    }

    @Override
    public void calculate(BotSituation situation, long tick, Map<String, BotGoal> goals) {
      calls++;
      Vehicle observer =
          situation.ships().stream()
              .filter(v -> v.id().equals("observer"))
              .findFirst()
              .orElseThrow();
      Vehicle target =
          situation.ships().stream().filter(v -> v.id().equals("target")).findFirst().orElseThrow();
      assertSame(Selection.observer, observer);
      assertSame(Selection.target, target);
      assertEquals(1234.56789, goals.get(observer.id()).x(), 1e-12);
      assertEquals(BotGoal.Kind.SEARCH, goals.get(observer.id()).kind());
      situation.steer(observer, 8, 12);
    }
  }

  private GameSetup setup() {
    return new GameSetup(
        "strategy-integration",
        new WorldMap(1, List.of()),
        List.of(
            new FleetSetup(
                "light",
                List.of(
                    new VehicleSetup(
                        "observer",
                        "light",
                        new Vector2(.1234567, 0),
                        0,
                        "bot",
                        2,
                        0,
                        999,
                        "torpedo-boat",
                        0))),
            new FleetSetup(
                "dark",
                List.of(
                    new VehicleSetup(
                        "target",
                        "dark",
                        new Vector2(0, 120),
                        0,
                        "human",
                        2,
                        0,
                        999,
                        "submarine",
                        0)))),
        List.of());
  }

  @Test
  void normalGameConstructionUsesConfiguredImplementationsAndDirectUnroundedVehicles() {
    String calculation = System.getProperty("seaBattle.botCalculation");
    String selection = System.getProperty("seaBattle.botTargetSelection");
    Selection.instances = Movement.instances = Movement.calls = 0;
    try {
      System.setProperty("seaBattle.botCalculation", Movement.class.getName());
      System.setProperty("seaBattle.botTargetSelection", Selection.class.getName());
      for (int restart = 0; restart < 2; restart++) {
        GameSession session =
            new GameSession(setup()); // The exact constructor used by GameStateService.
        session.update(.1, new RadarService(), new NavigationService(), session.worldMap());
        Vehicle boat = session.calculationShips().get(0);
        assertSame(boat, Selection.observer);
        assertEquals(8, boat.engineOrder());
        assertEquals(12, boat.rudderDegrees());
      }
      assertEquals(2, Selection.instances);
      assertEquals(2, Movement.instances);
      assertEquals(2, Movement.calls);
    } finally {
      restore("seaBattle.botCalculation", calculation);
      restore("seaBattle.botTargetSelection", selection);
    }
  }

  private void restore(String key, String value) {
    if (value == null) System.clearProperty(key);
    else System.setProperty(key, value);
  }

  @Test
  void invalidConfiguredImplementationFailsInsteadOfSilentlyUsingTheStandard() {
    String previous = System.getProperty("seaBattle.botCalculation");
    try {
      System.setProperty("seaBattle.botCalculation", String.class.getName());
      assertThrows(IllegalArgumentException.class, () -> new GameSession(setup()));
    } finally {
      restore("seaBattle.botCalculation", previous);
    }
  }

  @Test
  void perceptionExposesRealSubmarineAndSeparatesSonarFromVisualContact() {
    GameSession session = new GameSession(setup());
    Vehicle observer = session.calculationShips().get(0);
    Submarine sub = (Submarine) session.calculationShips().get(1);
    sub.y = -19.26;
    sub.copyRecordedDepth(deepSubmarine());
    var cache = new RadarService().visibilityCache(session.worldMap(), session.calculationShips());
    var situation = new BotTargetSituation(session, observer, List.of(), cache, Map.of());
    assertSame(sub, situation.vehicles().get(1));
    assertTrue(situation.nearbyVehicles(150).contains(sub));
    assertFalse(situation.isVisible(sub));
    assertTrue(situation.hasSonarContact(sub));
    observer.position = new Vector2(1000, 1000);
    assertFalse(situation.hasSonarContact(sub));
  }

  private Submarine deepSubmarine() {
    Submarine sub = new Submarine("deep", "dark", new Vector2(0, 20), 0, "bot");
    sub.botDepth("submerged");
    return sub;
  }

  @Test
  void huntingInformationIsAvailableAndUsesRememberedAreaInsteadOfHiddenPosition() {
    GameSession session = new GameSession(setup());
    SurfaceVehicle hunter = (SurfaceVehicle) session.calculationShips().get(0);
    var search =
        new BotSubmarineHunt.Search(
            new BotSubmarineHunt.Area(new Vector2(100, 200), 60), hunter.position());
    hunter.botNavigation.submarineSearch = search;
    var cache = new RadarService().visibilityCache(session.worldMap(), session.calculationShips());
    var situation = new BotTargetSituation(session, hunter, List.of(), cache, Map.of());
    assertSame(search, situation.submarineSearch());
    assertEquals(60, situation.submarineSearch().area().until());
    StandardBotTargetSelection selector = new StandardBotTargetSelection();
    BotGoal before = selector.goal(situation, null, false);
    session.calculationShips().get(1).position = new Vector2(9000, 9000);
    BotGoal after = selector.goal(situation, null, false);
    assertEquals(BotGoal.Kind.SEARCH, after.kind());
    assertEquals(before, after);
    assertNull(after.target());
  }

  @Test
  void replacingMovementAlsoReplacesTacticalDiving() {
    GameSetup setup =
        new GameSetup(
            "own-submarine-controller",
            new WorldMap(1, List.of()),
            List.of(
                new FleetSetup(
                    "light",
                    List.of(
                        new VehicleSetup(
                            "observer",
                            "light",
                            new Vector2(0, 0),
                            0,
                            "bot",
                            2,
                            0,
                            999,
                            "submarine",
                            0))),
                new FleetSetup(
                    "dark",
                    List.of(
                        new VehicleSetup(
                            "target",
                            "dark",
                            new Vector2(0, 100),
                            0,
                            "human",
                            2,
                            0,
                            999,
                            "torpedo-boat",
                            0)))),
            List.of());
    GameSession session =
        new GameSession(
            setup,
            1,
            false,
            (s, tick, goals) -> {
              assertInstanceOf(Submarine.class, s.ships().get(0));
              assertEquals("surface", s.ships().get(0).depthState());
              assertSame(s.ships().get(1), goals.get("observer").target());
            },
            BotTargetSelection.standard());
    session.update(.1, new RadarService(), new NavigationService(), session.worldMap());
    assertEquals("surface", session.calculationShips().get(0).depthState());
  }

  @Test
  void simulationDoesNotCreateVehicleSnapshotsBeforeOutput(
      @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
    java.util.Set<String> classpath = new java.util.LinkedHashSet<>();
    for (ClassLoader loader = getClass().getClassLoader();
        loader != null;
        loader = loader.getParent()) {
      if (loader instanceof java.net.URLClassLoader urls) {
        for (java.net.URL url : urls.getURLs())
          classpath.add(java.nio.file.Path.of(url.toURI()).toString());
      }
    }
    classpath.addAll(
        List.of(System.getProperty("java.class.path").split(java.io.File.pathSeparator)));
    java.nio.file.Path log = directory.resolve("model-allocation.log");
    Process process =
        new ProcessBuilder(
                java.nio.file.Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-Xlog:class+init=info",
                "-cp",
                String.join(java.io.File.pathSeparator, classpath),
                BotModelAllocationProbe.class.getName(),
                "40")
            .redirectErrorStream(true)
            .redirectOutput(log.toFile())
            .start();
    try {
      assertTrue(
          process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS),
          "In-memory model probe timed out");
      String output = java.nio.file.Files.readString(log);
      assertEquals(0, process.exitValue(), output);
      int modelFinished = output.indexOf("MODEL_FINISHED");
      int firstVehicleSnapshot =
          output.indexOf("Initializing 'one/xis/seabattle/game/VehicleSnapshot'");
      int firstWreckSnapshot =
          output.indexOf("Initializing 'one/xis/seabattle/game/ShipWreckSnapshot'");
      assertTrue(modelFinished >= 0, output);
      assertTrue(
          firstVehicleSnapshot > modelFinished,
          "Vehicle snapshots must first be constructed at output, not during calculation");
      assertTrue(
          firstWreckSnapshot > modelFinished,
          "Wreck output DTOs must not hold the simulation state");
    } finally {
      process.destroyForcibly();
    }
  }
}
