package one.xis.seabattle.game;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class SubmarineBotCalculationTest {
  @Test
  void distanceControlBacksAwayOnlyAtPeriscopeDepthEvenAfterChangingDepth() {
    var submarine = new Submarine("submarine", "light", new Vector2(0, 0), 0, "bot");
    for (String controller : new String[] {"bot", "captain"}) {
      var target = new TorpedoBoat("target", "dark", new Vector2(0, 240), 0, controller);
      submarine.botDepth("periscope");
      int periscopeOrder = SubmarineBotCalculation.attackEngineOrder(submarine, target, 240, 0);
      assertTrue(
          EngineOrders.speedFor(periscopeOrder) < 0, "Too-close periscope attack backs away");
      for (String depth : new String[] {"submerged", "surface"}) {
        submarine.botDepth(depth);
        for (int distance = 0; distance <= 650; distance += 50) {
          int order = SubmarineBotCalculation.attackEngineOrder(submarine, target, distance, 0);
          assertTrue(
              EngineOrders.speedFor(order) > 0,
              "Distance control must not reverse at depth " + depth + " and distance " + distance);
        }
      }
    }
  }

  @Test
  void closeSurfaceThreatTriggersDeepEscapeWithForwardPowerInsteadOfDistanceReversal() {
    var setup =
        new GameSetup(
            "submarine-escape",
            new WorldMap(1, List.of()),
            List.of(
                new FleetSetup(
                    "light",
                    List.of(
                        new VehicleSetup(
                            "submarine",
                            "light",
                            new Vector2(0, 0),
                            0,
                            "bot",
                            2,
                            0,
                            9999,
                            "submarine",
                            0))),
                new FleetSetup(
                    "dark",
                    List.of(
                        new VehicleSetup(
                            "threat",
                            "dark",
                            new Vector2(0, 100),
                            Math.PI,
                            "captain",
                            2,
                            0,
                            9999,
                            "torpedo-boat",
                            0)))),
            List.of());
    var session = new GameSession(setup);
    session.update(.1, new RadarService(), new NavigationService(), session.worldMap());
    var submarine = (Submarine) session.calculationShips().get(0);
    assertTrue(submarine.isFullySubmerged(), "Close surface threat requires a deep dive");
    assertEquals(GameRules.ENGINE_FULL, submarine.engineOrder(), "Escape uses forward power");
    assertNotEquals(0, submarine.rudderDegrees(), "Boat turns away from the threat ahead");
  }

  @Test
  void tacticalMemoryIsPerSubmarineAndResetWhenTheControllerChanges() {
    var first = new Submarine("first", "light", new Vector2(0, 0), 0, "bot");
    var second = new Submarine("second", "light", new Vector2(0, 0), 0, "bot");
    var oldState = first.botState();
    oldState.holdingDistanceAstern = true;
    oldState.aircraftMemoryUntil = 50;
    assertFalse(second.botState().holdingDistanceAstern);
    first.controlledBy("captain");
    assertNotSame(oldState, first.botState());
    assertFalse(first.botState().holdingDistanceAstern);
    assertEquals(0, first.botState().aircraftMemoryUntil);
  }
}
