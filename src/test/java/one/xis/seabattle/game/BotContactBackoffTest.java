package one.xis.seabattle.game;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class BotContactBackoffTest {
  private final NavigationService navigation = new NavigationService();
  private final RadarService radar = new RadarService();

  private GameSession session(boolean rearLand, boolean rearFriend) {
    var land =
        new Landmass(
            "island", "rear", 0, -2, 2, 1.5, 2, 1.5, 2.4, 1.8, null, 1, null, null, null, List.of(),
            List.of(), List.of());
    var vehicles = new ArrayList<VehicleSetup>();
    vehicles.add(
        new VehicleSetup(
            "boat", "light", new Vector2(0, 0), 0, "bot", 2, 0, 9999, "torpedo-boat", 0));
    vehicles.add(
        new VehicleSetup(
            "contact", "light", new Vector2(0, 12), 0, "captain", 2, 0, 9999, "torpedo-boat", 0));
    if (rearFriend)
      vehicles.add(
          new VehicleSetup(
              "rear",
              "light",
              new Vector2(0, -12),
              0,
              "captain-rear",
              2,
              0,
              9999,
              "torpedo-boat",
              0));
    return new GameSession(
        new GameSetup(
            "short-contact-backoff",
            new WorldMap(1, rearLand ? List.of(land) : List.of()),
            List.of(new FleetSetup("light", vehicles)),
            List.of(new Vector2(1000, 1000))));
  }

  @Test
  void continuousContactGetsOneShortSlowBackoffAndCannotRenewItEveryTick() {
    var session = session(false, false);
    Vehicle boat = session.calculationShips().get(0), contact = session.calculationShips().get(1);
    Vector2 start = boat.position();
    int reverseTicks = 0;
    for (int tick = 0; tick < 40; tick++) {
      // Reproduce a persistent contact without letting the other boat escape and re-arm the rule.
      contact.position = boat.position().add(Vector2.fromHeading(boat.heading()).scale(12));
      boat.backOffAfterGlancingRam(tick * .1, GameRules.BOT_GLANCING_RAM_BACKOFF_SECONDS, contact);
      session.update(.1, radar, navigation, session.worldMap());
      assertNotEquals(GameRules.ENGINE_FULL_ASTERN, boat.engineOrder());
      if (boat.engineOrder() < GameRules.ENGINE_STOP) reverseTicks++;
      if (tick >= 9)
        assertTrue(
            boat.engineOrder() >= GameRules.ENGINE_STOP, "Persistent contact cannot renew reverse");
      if (tick == 8)
        assertTrue(boat.position().distanceTo(start) < 2, "Only a small clearance manoeuvre");
    }
    assertTrue(reverseTicks > 0 && reverseTicks <= 8);
  }

  @Test
  void rearLandOrFriendlyShipCancelsContactReverseWithoutRetryLoop() {
    for (boolean rearLand : List.of(true, false)) {
      var session = session(rearLand, !rearLand);
      var boat = (SurfaceVehicle) session.calculationShips().get(0);
      Vehicle contact = session.calculationShips().get(1);
      for (int tick = 0; tick < 30; tick++) {
        // Hold the observed blocked geometry fixed, checking repeated decision rounds.
        boat.position = new Vector2(0, 0);
        boat.heading = 0;
        boat.speed = 0;
        boat.backOffAfterGlancingRam(
            tick * .1, GameRules.BOT_GLANCING_RAM_BACKOFF_SECONDS, contact);
        session.update(.1, radar, navigation, session.worldMap());
        assertTrue(
            boat.engineOrder() >= GameRules.ENGINE_STOP,
            "No reversing into a blocked stern: land="
                + rearLand
                + " tick="
                + tick
                + " order="
                + boat.engineOrder());
        assertFalse(boat.hasGlancingRamBackoff((tick + 1) * .1), "Blocked attempt stays cancelled");
      }
    }
  }

  @Test
  void onlyAResolvedContactAllowsAnotherClearanceManoeuvre() {
    var session = session(false, false);
    var boat = (SurfaceVehicle) session.calculationShips().get(0);
    Vehicle contact = session.calculationShips().get(1);
    boat.backOffAfterGlancingRam(0, .8, contact);
    boat.cancelGlancingRamBackoff();
    boat.backOffAfterGlancingRam(10, .8, contact);
    assertFalse(boat.hasGlancingRamBackoff(10));
    contact.position = new Vector2(0, 100);
    assertFalse(boat.hasGlancingRamBackoff(11));
    contact.position = new Vector2(0, 12);
    boat.backOffAfterGlancingRam(12, .8, contact);
    assertTrue(boat.hasGlancingRamBackoff(12));
  }

  @Test
  void threeCloseFriendsCanClearTheGroupWithoutFullReverseOrRepeatedShunting() {
    var fleet = new ArrayList<VehicleSetup>();
    for (int i = 0; i < 3; i++)
      fleet.add(
          new VehicleSetup(
              "boat-" + i,
              "light",
              new Vector2(0, i * 18),
              0,
              "bot",
              GameRules.ENGINE_FULL,
              0,
              9999,
              "torpedo-boat",
              0));
    var session =
        new GameSession(
            new GameSetup(
                "three-close-friends",
                new WorldMap(1, List.of()),
                List.of(new FleetSetup("light", fleet)),
                List.of(new Vector2(1000, 1000))));
    int[] reverseStarts = new int[3];
    boolean[] reversed = new boolean[3];
    for (int tick = 0; tick < 400; tick++) {
      session.update(.1, radar, navigation, session.worldMap());
      for (int i = 0; i < 3; i++) {
        Vehicle vehicle = session.calculationShips().get(i);
        assertEquals("active", vehicle.state());
        assertNotEquals(GameRules.ENGINE_FULL_ASTERN, vehicle.engineOrder());
        boolean reverse = vehicle.engineOrder() < GameRules.ENGINE_STOP;
        if (reverse && !reversed[i]) reverseStarts[i]++;
        reversed[i] = reverse;
      }
    }
    for (int i = 0; i < 3; i++) {
      Vehicle vehicle = session.calculationShips().get(i);
      assertTrue(
          vehicle.position().distanceTo(new Vector2(0, i * 18)) > BotNavigation.SHIP_LENGTH,
          "Every boat must eventually leave the close group: " + vehicle.snapshot());
      assertTrue(reverseStarts[i] <= 2, "No forward/reverse cycle: " + reverseStarts[i]);
    }
  }
}
