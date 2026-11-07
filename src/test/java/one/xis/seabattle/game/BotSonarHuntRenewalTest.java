package one.xis.seabattle.game;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class BotSonarHuntRenewalTest {
  private final WorldMap water = new WorldMap(1, List.of());
  private final NavigationService navigation = new NavigationService();
  private final RadarService radar = new RadarService();

  private GameSession session(String enemyController) {
    var session = new GameSession(new GameSetup("sonar-renewal", water, List.of(
        new FleetSetup("light", List.of(
            new VehicleSetup("detector", "light", new Vector2(0, 0), 0, "bot", 2, 0, 9999, "torpedo-boat", 0),
            new VehicleSetup("friend", "light", new Vector2(50, 0), 0, "friend-captain", 2, 0, 9999,
                "submarine", SeaBattleGameConfig.SUBMARINE_DEEP_Y))),
        new FleetSetup("dark", List.of(new VehicleSetup("enemy", "dark", new Vector2(100, 0),
            0, enemyController, 2, 0, 9999, "submarine", SeaBattleGameConfig.SUBMARINE_DEEP_Y)))), List.of()));
    for (String id : List.of("enemy", "friend")) {
      Vehicle sub = vehicle(session, id);
      sub.applyPlayerState(new PlayerStateUpdate(sub.controlledBy(), sub.teamId(), sub.position().x(),
          sub.position().z(), 0, 0, 0, 2, 0, 0, false, "submarine",
          SeaBattleGameConfig.SUBMARINE_DEEP_Y, 0, null, null, null, null, "submerged"), navigation, water);
    }
    return session;
  }

  private Vehicle vehicle(GameSession s, String id) {
    return s.calculationShips().stream().filter(v -> v.id().equals(id)).findFirst().orElseThrow();
  }

  private void checkAfter(GameSession s, double seconds) {
    s.updateIdle(seconds, radar, navigation, water);
    s.update(0, radar, navigation, water);
  }

  @Test void steadyEnemyEchoStartsAndRenewsTheHuntWithoutAnyDepthChargeDrop() {
    for (String controller : List.of("enemy-captain", "bot")) {
      var s = session(controller);
      s.update(0, radar, navigation, water);
      Vehicle detector = vehicle(s, "detector"), enemy = vehicle(s, "enemy");
      var search = detector.submarineSearch();
      assertNotNull(search, "Both human and bot enemy submarines are valid sonar contacts");
      assertEquals(60, search.area().until());
      assertEquals(enemy.position(), search.area().center(), "A closer friendly sub is ignored");
      assertTrue(s.snapshot().depthCharges().isEmpty());
      // Keep the bot-controlled target deep; this test observes sonar, not its dive policy.
      enemy.y = SeaBattleGameConfig.SUBMARINE_DEEP_Y;
      enemy.botDepth("submerged");
      checkAfter(s, 20);
      assertSame(search, detector.submarineSearch(), "Renew the assigned hunt instead of resetting its route");
      assertEquals(80, search.area().until());
      assertTrue(s.snapshot().depthCharges().isEmpty(), "A steady signal must never cause a drop");
      assertEquals(80, s.snapshot().submarineDetections().get("enemy").until(), "Eye follows the renewed hunt deadline");
    }
  }

  @Test void lostEnemyContactIsNotRenewedByAFriendlySubmarineAndNeverStartsADrop() {
    var s = session("enemy-captain");
    s.update(0, radar, navigation, water);
    var search = vehicle(s, "detector").submarineSearch();
    vehicle(s, "enemy").position = new Vector2(3000, 3000);
    checkAfter(s, 20);
    assertEquals(60, search.area().until());
    assertEquals(new Vector2(100, 0), search.area().center(), "Do not follow an unobserved target");
    assertTrue(s.snapshot().depthCharges().isEmpty(), "Signal loss is not a weakening positive echo");
    checkAfter(s, 41);
    assertNull(vehicle(s, "detector").submarineSearch(), "Only friends remain: let the hunt expire");
    assertTrue(s.snapshot().depthCharges().isEmpty());
  }

  @Test void onlyFriendlySonarContactsNeverStartAHuntOrSalvo() {
    var s = session("enemy-captain");
    vehicle(s, "enemy").position = new Vector2(3000, 3000);
    s.update(0, radar, navigation, water);
    vehicle(s, "friend").position = new Vector2(60, 0);
    checkAfter(s, .5);
    assertNull(vehicle(s, "detector").submarineSearch());
    assertTrue(s.snapshot().depthCharges().isEmpty());
  }

  @Test void renewalUpdatesAssignedHuntersInConstantTimeAndOnlyAtConfirmedPositions() {
    var hunt = new BotSubmarineHunt();
    var detector = new TorpedoBoat("detector", "light", new Vector2(0, 0), 0, "bot");
    var enemy = new Submarine("enemy", "dark", new Vector2(100, 0), 0, "enemy-captain");
    var friend = new Submarine("friend", "light", new Vector2(5, 0), 0, "friend-captain");
    var scans = new AtomicInteger();
    java.util.function.Supplier<List<Vehicle>> neighbors = () -> { scans.incrementAndGet(); return List.of(detector); };
    hunt.sonarContact(detector, enemy, 0, neighbors);
    var search = detector.submarineSearch();
    Vector2 initialPoint = search.currentDestination();
    enemy.position = new Vector2(120, 40);
    hunt.sonarContact(detector, enemy, 30, neighbors);
    assertEquals(90, search.area().until());
    assertEquals(enemy.position(), search.area().center());
    assertNotEquals(initialPoint, search.currentDestination());
    assertEquals(1, scans.get(), "Existing hunters share the renewed area, with no fleet rescan");
    hunt.sonarContact(detector, friend, 50, neighbors);
    assertEquals(90, search.area().until());
    assertEquals(enemy.position(), search.area().center());
    enemy.position = new Vector2(3000, 3000); // No contact callback after losing sonar.
    assertEquals(new Vector2(120, 40), search.area().center());
    assertTrue(search.active(89.9));
    assertFalse(search.active(90));
    assertEquals(90, hunt.detections(89.9).get("enemy").until());
    assertTrue(hunt.detections(90).isEmpty(), "Eye expires with the renewed hunt");
    assertEquals(1, scans.get());
  }
}
