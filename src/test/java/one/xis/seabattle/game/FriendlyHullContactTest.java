package one.xis.seabattle.game;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;

class FriendlyHullContactTest {
  private VehicleSetup boat(String id, double x, double z, double heading) {
    return new VehicleSetup(
        id, "light", new Vector2(x, z), heading, "bot", 2, 0, 9999, "torpedo-boat", 0);
  }

  private boolean overlap(GameSession session, Vehicle left, Vehicle right) throws Exception {
    Method method =
        GameSession.class.getDeclaredMethod("hullsOverlap", Vehicle.class, Vehicle.class);
    method.setAccessible(true);
    return (boolean) method.invoke(session, left, right);
  }

  @Test
  void incomingFriendlyBoatCannotDriveThroughAStationaryFullSizeHull() throws Exception {
    for (boolean parallel : List.of(false, true)) {
      BotCalculation controls =
          (situation, tick, goals) -> {
            for (Vehicle vehicle : situation.vehicles())
              situation.steer(vehicle, vehicle.id().equals("incoming") ? 8 : 2, 0);
          };
      var setup =
          new GameSetup(
              "solid-friendly-hull",
              new WorldMap(1, List.of()),
              List.of(
                  new FleetSetup(
                      "light",
                      List.of(boat("incoming", -50, 0, Math.PI / 2), boat("stationary", 0, 0, 0)))),
              List.of());
      var session = new GameSession(setup, 1, parallel, controls, BotTargetSelection.standard());
      Vehicle incoming = session.calculationShips().get(0),
          stationary = session.calculationShips().get(1);
      for (int tick = 0; tick < 220; tick++) {
        session.update(.1, new RadarService(), new NavigationService(), session.worldMap());
        assertFalse(
            overlap(session, incoming, stationary), "No visible penetration at tick " + tick);
      }
      assertTrue(incoming.position().x() > -30, "Boat must actually reach the other hull");
      assertTrue(incoming.position().x() < -10, "Full-size bow must stop before the broadside");
      assertEquals(
          new Vector2(0, 0), stationary.position(), "Do not push the stationary ship aside");
    }
  }

  @Test
  void rejectingAnIncomingStepPreservesTheOtherBoatsEscapeMovement() throws Exception {
    BotCalculation controls =
        (situation, tick, goals) -> {
          for (Vehicle vehicle : situation.vehicles())
            situation.steer(vehicle, vehicle.id().equals("following") ? 8 : 5, 0);
        };
    var setup =
        new GameSetup(
            "escaping-friend",
            new WorldMap(1, List.of()),
            List.of(
                new FleetSetup(
                    "light", List.of(boat("following", 0, -50, 0), boat("escaping", 0, 0, 0)))),
            List.of());
    var session = new GameSession(setup, 1, false, controls, BotTargetSelection.standard());
    Vehicle following = session.calculationShips().get(0),
        escaping = session.calculationShips().get(1);
    for (int tick = 0; tick < 250; tick++) {
      session.update(.1, new RadarService(), new NavigationService(), session.worldMap());
      assertFalse(
          overlap(session, following, escaping), "Following boat must not pass into the stern");
    }
    assertTrue(escaping.position().z() > 100, "Escape movement must remain free");
    assertTrue(following.position().z() > 50, "Follower can resume as space opens");
  }

  @Test
  void hullCollisionAlreadyUsesTheCurrentThreeTimesVisualScale() throws Exception {
    var session =
        new GameSession(
            new GameSetup("dimensions", new WorldMap(1, List.of()), List.of(), List.of()));
    var hull = new TorpedoBoat("hull", "light", new Vector2(0, 0), 0, "bot");
    Method contains =
        GameSession.class.getDeclaredMethod(
            "pointInsideShipHull", Vector2.class, Vehicle.class, double.class);
    contains.setAccessible(true);
    assertEquals(true, contains.invoke(session, new Vector2(0, 10), hull, 0.0));
    assertEquals(true, contains.invoke(session, new Vector2(0, -12), hull, 0.0));
    assertEquals(false, contains.invoke(session, new Vector2(0, 12), hull, 0.0));
    assertEquals(false, contains.invoke(session, new Vector2(0, -14), hull, 0.0));
    assertEquals(true, contains.invoke(session, new Vector2(2, 0), hull, 0.0));
    assertEquals(false, contains.invoke(session, new Vector2(3, 0), hull, 0.0));
  }
}
