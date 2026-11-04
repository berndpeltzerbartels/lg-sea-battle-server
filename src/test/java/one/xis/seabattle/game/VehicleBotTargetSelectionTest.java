package one.xis.seabattle.game;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class VehicleBotTargetSelectionTest {
  private static final class Policy<V extends Vehicle> implements VehicleBotTargetSelection<V> {
    final Class<V> type;
    int observations, selections, goals, sonarContacts;
    V original;

    Policy(Class<V> type) {
      this.type = type;
    }

    private void check(V vehicle, BotTargetSituation situation) {
      assertInstanceOf(type, vehicle);
      assertSame(situation.observer(), vehicle);
      if (original == null) original = vehicle;
      else assertSame(original, vehicle);
    }

    @Override
    public Vehicle select(V vehicle, BotTargetSituation situation, long tickId) {
      check(vehicle, situation);
      selections++;
      return null;
    }

    @Override
    public void observe(V vehicle, BotTargetSituation situation, long tickId) {
      check(vehicle, situation);
      observations++;
    }

    @Override
    public BotGoal goal(V vehicle, BotTargetSituation situation, Vehicle contact, boolean advance) {
      check(vehicle, situation);
      goals++;
      return new BotGoal(BotGoal.Kind.SEARCH, null, -1, 123, 456);
    }

    @Override
    public void depthChargeContact(
        V vehicle, BotTargetSituation situation, Submarine target, long tickId) {
      check(vehicle, situation);
      sonarContacts++;
    }
  }

  private VehicleSetup vehicle(String id, String type, double x, String controller) {
    return new VehicleSetup(id, "light", new Vector2(x, 0), 0, controller, 2, 0, 9999, type, 0);
  }

  private GameSetup setup() {
    return new GameSetup(
        "typed-targets",
        new WorldMap(1, List.of()),
        List.of(
            new FleetSetup(
                "light",
                List.of(
                    vehicle("boat", "torpedo-boat", 0, "bot"),
                    vehicle("submarine", "submarine", 500, "bot"),
                    vehicle("plane", "scout-plane", 1000, "bot"),
                    vehicle("human", "torpedo-boat", 1500, "captain")))),
        List.of());
  }

  @Test
  void productionDispatchesOriginalTypesAndPassesTheirOwnGoalsToMovement() {
    var boats = new Policy<>(TorpedoBoat.class);
    var submarines = new Policy<>(Submarine.class);
    var planes = new Policy<>(ScoutPlane.class);
    var selection = new StandardBotTargetSelection(boats, submarines, planes);
    int[] rounds = {0};
    BotCalculation movement =
        (situation, tick, goals) -> {
          assertEquals(3, goals.size(), "Human vehicles are not sent through bot policies");
          for (BotGoal goal : goals.values()) {
            assertEquals(BotGoal.Kind.SEARCH, goal.kind());
            assertEquals(123, goal.x());
            assertEquals(456, goal.z());
          }
          rounds[0]++;
        };
    var session = new GameSession(setup(), 1, false, movement, selection);
    var radar = new RadarService();
    var navigation = new NavigationService();
    for (int tick = 0; tick < 25; tick++) session.update(.1, radar, navigation, session.worldMap());
    assertEquals(25, rounds[0]);
    for (Policy<?> policy : List.of(boats, submarines, planes)) {
      assertTrue(policy.selections > 0);
      assertTrue(policy.observations > 0);
      assertEquals(25, policy.goals);
      assertTrue(session.calculationShips().stream().anyMatch(v -> v == policy.original));
    }
    var cache = radar.visibilityCache(session.worldMap(), session.calculationShips());
    selection.depthChargeContact(
        new BotTargetSituation(session, boats.original, List.of(), cache, Map.of()),
        submarines.original,
        26);
    assertEquals(1, boats.sonarContacts);
    assertEquals(0, submarines.sonarContacts);
    assertEquals(0, planes.sonarContacts);
  }

  @Test
  void replacementAircraftPolicyKeepsStandardWaterTargetsAndTheirPriorities() {
    var boats = new TorpedoBoatBotTargetSelection();
    var submarines = new SubmarineBotTargetSelection();
    var ownPlanes = new Policy<>(ScoutPlane.class);
    var selection = new StandardBotTargetSelection(boats, submarines, ownPlanes);
    var session = new GameSession(setup());
    var target = new TorpedoBoat("enemy-human", "dark", new Vector2(2000, 0), 0, "captain");
    var cache = new RadarService().visibilityCache(session.worldMap(), session.calculationShips());
    for (Vehicle vehicle : session.calculationShips().subList(0, 3)) {
      var situation = new BotTargetSituation(session, vehicle, List.of(target), cache, Map.of());
      Vehicle selected = selection.select(situation, 1);
      if (vehicle instanceof ScoutPlane) {
        assertNull(selected);
        assertEquals(BotGoal.Kind.SEARCH, selection.goal(situation, selected, false).kind());
      } else {
        assertSame(target, selected);
        assertSame(target, selection.goal(situation, selected, false).target());
      }
    }
    assertEquals(1, ownPlanes.selections);
  }

  @Test
  void submarineSightingsShareHuntReportsWithTheTorpedoBoatPolicy() {
    var session = new GameSession(setup());
    var vehicles = session.calculationShips();
    var observer = (Submarine) vehicles.get(1);
    var hunter = (TorpedoBoat) vehicles.get(0);
    var target =
        new Submarine(
            "enemy-sub", "dark", observer.position().add(new Vector2(0, 30)), Math.PI, "captain");
    List<Vehicle> all = List.of(observer, hunter, target);
    var cache = new RadarService().visibilityCache(session.worldMap(), all);
    var selection = new StandardBotTargetSelection();
    var situation = new BotTargetSituation(session, observer, List.of(target), cache, Map.of());
    assertTrue(situation.isVisible(target));
    selection.observe(situation, 1);
    assertNotNull(hunter.submarineSearch());
    assertEquals(1, selection.submarineDetections(0).size());
    assertTrue(selection.submarineDetections(0).containsKey(target.id()));
    assertNull(observer.submarineSearch());
  }
}
